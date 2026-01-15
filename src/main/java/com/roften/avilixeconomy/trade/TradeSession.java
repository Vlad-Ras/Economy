package com.roften.avilixeconomy.trade;

import com.roften.avilixeconomy.EconomyData;
import com.roften.avilixeconomy.network.NetworkRegistration;
import com.roften.avilixeconomy.trade.menu.TradeMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Серверная сессия трейда. Один объект разделяется между двумя меню.
 */
public final class TradeSession {

    private final int sessionId;
    private final ServerPlayer left;
    private final ServerPlayer right;

    private final TradeOfferContainer leftOffer;
    private final TradeOfferContainer rightOffer;

    private double leftMoney;
    private double rightMoney;

    private boolean leftReady;
    private boolean rightReady;

    private boolean ended;

    public TradeSession(int sessionId, ServerPlayer left, ServerPlayer right) {
        this.sessionId = sessionId;
        this.left = Objects.requireNonNull(left);
        this.right = Objects.requireNonNull(right);
        // 9x4 = 36, как инвентарь игрока + хотбар
        this.leftOffer = new TradeOfferContainer(36, this::onOfferChanged);
        this.rightOffer = new TradeOfferContainer(36, this::onOfferChanged);
    }

    public int sessionId() {
        return sessionId;
    }

    public ServerPlayer left() {
        return left;
    }

    public ServerPlayer right() {
        return right;
    }

    public Container leftOffer() {
        return leftOffer;
    }

    public Container rightOffer() {
        return rightOffer;
    }

    public MenuProvider createMenuProvider(TradeMenu.Side side) {
        return new SimpleMenuProvider(
                (containerId, inv, player) -> new TradeMenu(containerId, inv, this, side),
                Component.literal("Trade")
        );
    }

    public synchronized void updateMoney(ServerPlayer player, double amount) {
        if (ended) return;
        if (amount < 0) amount = 0.0; amount = com.roften.avilixeconomy.util.MoneyUtils.round2(amount);

        if (player.getUUID().equals(left.getUUID())) {
            leftMoney = amount;
        } else if (player.getUUID().equals(right.getUUID())) {
            rightMoney = amount;
        } else {
            return;
        }

        onOfferChanged();
    }

    public synchronized void toggleReady(ServerPlayer player) {
        if (ended) return;
        if (player.getUUID().equals(left.getUUID())) {
            leftReady = !leftReady;
        } else if (player.getUUID().equals(right.getUUID())) {
            rightReady = !rightReady;
        } else {
            return;
        }

        broadcastState();

        if (leftReady && rightReady) {
            execute();
        }
    }

    public synchronized void onMenuClosed(ServerPlayer player) {
        if (ended) return;
        cancel("Трейд отменён (окно закрыто).");
    }

    public synchronized void cancel(String reason) {
        if (ended) return;
        ended = true;

        // возвращаем предметы
        returnOffer(left, leftOffer);
        returnOffer(right, rightOffer);

        left.closeContainer();
        right.closeContainer();

        left.sendSystemMessage(Component.literal(reason));
        right.sendSystemMessage(Component.literal(reason));

        TradeManager.onSessionEnded(this);
    }

    private void returnOffer(ServerPlayer owner, Container offer) {
        for (int i = 0; i < offer.getContainerSize(); i++) {
            ItemStack stack = offer.getItem(i);
            if (stack.isEmpty()) continue;

            ItemStack toGive = stack.copy();
            offer.setItem(i, ItemStack.EMPTY);
            giveOrDrop(owner, toGive);
        }
    }

    private void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * Строгая проверка: сможет ли игрок принять все предметы из incoming,
     * не проливая их на землю.
     */
    private boolean canFitAll(ServerPlayer receiver, Container incoming) {
        Inventory sim = new Inventory(receiver);
        copyInventory(receiver.getInventory(), sim);

        for (int i = 0; i < incoming.getContainerSize(); i++) {
            ItemStack st = incoming.getItem(i);
            if (st.isEmpty()) continue;
            ItemStack add = st.copy();
            if (!sim.add(add)) {
                return false;
            }
        }
        return true;
    }

    private static void copyInventory(Inventory from, Inventory to) {
        for (int i = 0; i < from.items.size(); i++) {
            to.items.set(i, from.items.get(i).copy());
        }
        for (int i = 0; i < from.armor.size(); i++) {
            to.armor.set(i, from.armor.get(i).copy());
        }
        for (int i = 0; i < from.offhand.size(); i++) {
            to.offhand.set(i, from.offhand.get(i).copy());
        }
        to.selected = from.selected;
    }

    private synchronized void execute() {
        if (ended) return;

        // проверяем онлайн
        if (!left.isAlive() || !right.isAlive()) {
            cancel("Трейд отменён: игрок недоступен.");
            return;
        }

        // Проверка инвентарей: не выполняем трейд, если получатель не сможет принять предметы.
        if (!canFitAll(right, leftOffer)) {
            cancel("Трейд отменён: у " + right.getName().getString() + " недостаточно места в инвентаре.");
            return;
        }
        if (!canFitAll(left, rightOffer)) {
            cancel("Трейд отменён: у " + left.getName().getString() + " недостаточно места в инвентаре.");
            return;
        }

        // Проверка балансов (без доверия к клиенту)
        double leftBal = EconomyData.getBalance(left.getUUID());
        double rightBal = EconomyData.getBalance(right.getUUID());
        if (leftBal + 1e-9 < leftMoney) {
            cancel("Трейд отменён: у " + left.getName().getString() + " недостаточно средств.");
            return;
        }
        if (rightBal + 1e-9 < rightMoney) {
            cancel("Трейд отменён: у " + right.getName().getString() + " недостаточно средств.");
            return;
        }

        // Переводим только разницу (чтобы не было 2-х транзакций)
        double net = com.roften.avilixeconomy.util.MoneyUtils.round2(leftMoney - rightMoney);
        if (net > 0.0) {
            if (!EconomyData.pay(left.getUUID(), right.getUUID(), net)) {
                cancel("Трейд отменён: не удалось перевести валюту.");
                return;
            }
        } else if (net < 0.0) {
            if (!EconomyData.pay(right.getUUID(), left.getUUID(), -net)) {
                cancel("Трейд отменён: не удалось перевести валюту.");
                return;
            }
        }

        // Обмен предметами
        transferOffer(leftOffer, right);
        transferOffer(rightOffer, left);

        ended = true;

        left.closeContainer();
        right.closeContainer();

        left.sendSystemMessage(Component.literal("Трейд завершён успешно!"));
        right.sendSystemMessage(Component.literal("Трейд завершён успешно!"));

        TradeManager.onSessionEnded(this);
    }

    private void transferOffer(Container offer, ServerPlayer receiver) {
        for (int i = 0; i < offer.getContainerSize(); i++) {
            ItemStack stack = offer.getItem(i);
            if (stack.isEmpty()) continue;

            ItemStack toGive = stack.copy();
            offer.setItem(i, ItemStack.EMPTY);
            giveOrDrop(receiver, toGive);
        }
    }

    private synchronized void onOfferChanged() {
        if (ended) return;
        // любое изменение сбрасывает подтверждения
        if (leftReady || rightReady) {
            leftReady = false;
            rightReady = false;
        }
        broadcastState();
    }

    public synchronized void broadcastState() {
        if (ended) return;

        NetworkRegistration.sendTradeState(
                left,
                sessionId,
                left.getName().getString(),
                right.getName().getString(),
                leftMoney,
                rightMoney,
                leftReady,
                rightReady
        );

        NetworkRegistration.sendTradeState(
                right,
                sessionId,
                left.getName().getString(),
                right.getName().getString(),
                leftMoney,
                rightMoney,
                leftReady,
                rightReady
        );
    }

    /**
     * Контейнер предложения, который сигналит сессии об изменениях.
     */
    public static final class TradeOfferContainer extends SimpleContainer {

        private final Runnable onChanged;

        public TradeOfferContainer(int size, Runnable onChanged) {
            super(size);
            this.onChanged = onChanged;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (onChanged != null) onChanged.run();
        }
    }
}

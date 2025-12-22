package com.roften.avilixeconomy.trade.menu;

import com.roften.avilixeconomy.registry.ModMenus;
import com.roften.avilixeconomy.trade.TradeSession;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Меню трейда (сервер/клиент). На сервере оба TradeMenu смотрят на один TradeSession.
 */
public class TradeMenu extends AbstractContainerMenu {

    public enum Side {
        LEFT(0),
        RIGHT(1);

        private final int id;

        Side(int id) {
            this.id = id;
        }

        public int id() {
            return id;
        }

        public static Side fromId(int id) {
            return id == 1 ? RIGHT : LEFT;
        }
    }

    /**
     * Размер оффера каждого игрока.
     * Делаем как у инвентаря + хотбара игрока: 9x4 = 36 слотов.
     */
    public static final int OFFER_COLS = 9;
    public static final int OFFER_ROWS = 4;
    public static final int OFFER_SIZE = OFFER_COLS * OFFER_ROWS; // 36

    /**
     * GUI размеры.
     *
     * Делаем шире ванильного, чтобы уместить два оффера 9x4 рядом:
     *  - всё, что относится к "Вы", всегда слева
     *  - всё, что относится к партнёру, всегда справа
     */
    public static final int GUI_WIDTH = 356;

    /** Координаты слотов (относительно leftPos/topPos экрана). */
    public static final int MY_OFFER_X = 8;
    public static final int THEIR_OFFER_X = MY_OFFER_X + OFFER_COLS * 18 + 14; // gap
    // Make the screen fit comfortably even on the largest Minecraft GUI scale.
    // (Minecraft limits the maximum GUI scale so that the scaled resolution stays >= ~320x240.
    // Keeping our layout compact avoids forced downscaling that makes the trade UI look smaller
    // than the rest of the game's UI.)
    public static final int OFFER_Y = 24;

    /** Высота информационного блока под офферами (деньги/статусы). */
    public static final int INFO_BLOCK_HEIGHT = 46;

    /** Максимальная дистанция между игроками для активного трейда (в блоках). */
    public static final double MAX_TRADE_DISTANCE = 5.0D;

    /** Верхний Y инфо-блока (сразу под офферами). */
    public static final int INFO_Y = OFFER_Y + OFFER_ROWS * 18 + 6;

    /** Ряд кнопок под инфо-блоком. */
    public static final int BUTTONS_Y = INFO_Y + INFO_BLOCK_HEIGHT + 4;

    /** Инвентарь игрока под кнопками (по центру). */
    public static final int INV_X = (GUI_WIDTH - (9 * 18)) / 2;
    /** Высота шапки секции инвентаря (полоска + подпись). */
    public static final int INV_HEADER_H = 9;
    /** Y шапки секции инвентаря. */
    public static final int INV_HEADER_Y = BUTTONS_Y + 20 + 4;
    /** Инвентарь игрока под шапкой.
     *  +4px gap prevents the header text from visually touching the first slot row at any GUI scale.
     */
    public static final int INV_Y = INV_HEADER_Y + INV_HEADER_H + 4;
    public static final int HOTBAR_Y = INV_Y + 58;

    public static final int GUI_HEIGHT = HOTBAR_Y + 18 + 6;


    private final int sessionId;
    private final Side side;

    private final Container myOffer;
    private final Container theirOffer;

    @Nullable
    private final TradeSession session; // только на сервере

    /**
     * Client-side constructor (called by MenuType factory).
     *
     * Extra data layout:
     *  - int sessionId
     *  - int side
     */
    public TradeMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        super(ModMenus.TRADE_MENU.get(), containerId);

        this.sessionId = buf.readInt();
        this.side = Side.fromId(buf.readInt());
        this.session = null;

        // На клиенте используем простые контейнеры — сервер будет присылать обновления слотов.
        Container left = new SimpleContainer(OFFER_SIZE);
        Container right = new SimpleContainer(OFFER_SIZE);
        if (side == Side.LEFT) {
            this.myOffer = left;
            this.theirOffer = right;
        } else {
            this.myOffer = right;
            this.theirOffer = left;
        }

        addSlots(playerInv);
    }

    /**
     * Server-side constructor.
     */
    public TradeMenu(int containerId, Inventory playerInv, TradeSession session, Side side) {
        super(ModMenus.TRADE_MENU.get(), containerId);
        this.session = session;
        this.sessionId = session.sessionId();
        this.side = side;

        if (side == Side.LEFT) {
            this.myOffer = session.leftOffer();
            this.theirOffer = session.rightOffer();
        } else {
            this.myOffer = session.rightOffer();
            this.theirOffer = session.leftOffer();
        }

        addSlots(playerInv);
    }

    private void addSlots(Inventory playerInv) {
        // ===== OFFERS (side-by-side) =====
        // ВАЖНО: myOffer/theirOffer уже подменены в конструкторе по side,
        // поэтому для каждого игрока "я" всегда слева, а партнёр всегда справа.
        final int offerCols = OFFER_COLS;
        final int offerRows = OFFER_ROWS;

        // ===== MY OFFER (left) =====
        for (int row = 0; row < offerRows; row++) {
            for (int col = 0; col < offerCols; col++) {
                int idx = col + row * offerCols;
                addSlot(new Slot(myOffer, idx, MY_OFFER_X + col * 18, OFFER_Y + row * 18));
            }
        }

        // ===== THEIR OFFER (right, read-only) =====
        for (int row = 0; row < offerRows; row++) {
            for (int col = 0; col < offerCols; col++) {
                int idx = col + row * offerCols;
                addSlot(new ReadOnlySlot(theirOffer, idx, THEIR_OFFER_X + col * 18, OFFER_Y + row * 18));
            }
        }

        // ===== PLAYER INV (below trade, centered) =====
        int invY = INV_Y;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, INV_X + col * 18, invY + row * 18));
            }
        }

        // ===== HOTBAR =====
        int hotbarY = HOTBAR_Y;
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, INV_X + col * 18, hotbarY));
        }
    }

    public int getSessionId() {
        return sessionId;
    }

    public Side getSide() {
        return side;
    }

    @Override
    public boolean stillValid(Player player) {
        // Клиентская сторона: сервер сам закроет контейнер, если условие нарушено.
        if (player.level().isClientSide) {
            return true;
        }

        // Серверная сторона: ограничиваем дистанцию до партнёра.
        if (session != null && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.minecraft.server.level.ServerPlayer other = (side == Side.LEFT) ? session.right() : session.left();

            // Разные измерения = сразу отменяем.
            if (other == null || other.level() != sp.level()) {
                session.cancel("Трейд отменён: игрок слишком далеко.");
                return false;
            }

            double max = MAX_TRADE_DISTANCE;
            if (sp.distanceToSqr(other) > max * max) {
                session.cancel("Трейд отменён: игрок слишком далеко (" + (int) max + " блоков)." );
                return false;
            }
        }

        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide && session != null && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            session.onMenuClosed(sp);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copied = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stackInSlot = slot.getItem();
        copied = stackInSlot.copy();

        int offerSlots = OFFER_SIZE * 2;
        int playerInvStart = offerSlots;
        int playerInvEnd = playerInvStart + 36; // 27 + 9

        if (index < OFFER_SIZE) {
            // из моего оффера -> в инвентарь
            if (!this.moveItemStackTo(stackInSlot, playerInvStart, playerInvEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < offerSlots) {
            // из чужого оффера нельзя
            return ItemStack.EMPTY;
        } else {
            // из инвентаря -> в мой оффер
            if (!this.moveItemStackTo(stackInSlot, 0, OFFER_SIZE, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stackInSlot.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return copied;
    }

    private static final class ReadOnlySlot extends Slot {

        private ReadOnlySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}

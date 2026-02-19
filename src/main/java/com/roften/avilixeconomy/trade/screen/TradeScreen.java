package com.roften.avilixeconomy.trade.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.roften.avilixeconomy.client.ui.ResponsiveContainerScreen;
import com.roften.avilixeconomy.compat.JeiCompat;
import com.roften.avilixeconomy.network.NetworkRegistration;
import com.roften.avilixeconomy.trade.menu.TradeMenu;
import com.roften.avilixeconomy.util.MoneyUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Клиентский экран трейда: слоты (из меню) + ввод валюты + кнопки подтверждения.
 *
 * IMPORTANT:
 *  - Всё оформление (панели/рамки/сетка слотов) рисуем вручную в scaled-pass
 *    (см. {@link ResponsiveContainerScreen}), чтобы оно не "пропадало" на некоторых GPU.
 */
public class TradeScreen extends ResponsiveContainerScreen<TradeMenu> {

    private static final int GUI_WIDTH = TradeMenu.GUI_WIDTH;
    private static final int GUI_HEIGHT = TradeMenu.GUI_HEIGHT;

    // Должно совпадать с координатами в TradeMenu
    private static final int MY_OFFER_X = TradeMenu.MY_OFFER_X;
    private static final int THEIR_OFFER_X = TradeMenu.THEIR_OFFER_X;
    private static final int OFFER_Y = TradeMenu.OFFER_Y;
    private static final int INFO_Y = TradeMenu.INFO_Y;
    private static final int INFO_H = TradeMenu.INFO_BLOCK_HEIGHT;
    private static final int INV_X = TradeMenu.INV_X;
    private static final int INV_HEADER_Y = TradeMenu.INV_HEADER_Y;
    private static final int INV_HEADER_H = TradeMenu.INV_HEADER_H;
    private static final int INV_Y = TradeMenu.INV_Y;
    private static final int HOTBAR_Y = TradeMenu.HOTBAR_Y;

    private static final int OFFER_COLS = 9;
    private static final int OFFER_ROWS = 4;

    // header bars (purely visual; do not change slot grid sizes)
    private static final int HEADER_H = 9;
    private static final int OFFER_HEADER_Y = OFFER_Y - HEADER_H - 1;

    // Visual constants
    private static final int OUTER_PAD = 4;
    private static final int PANEL_PAD = 4;

    private EditBox moneyBox;
    private FlatTextButton readyButton;
    private FlatTextButton cancelButton;

    private String leftName = "";
    private String rightName = "";
    private double leftMoney;
    private double rightMoney;
    private boolean leftReady;
    private boolean rightReady;

    private double lastSentMoney = Double.NaN;
    private int sendCooldownTicks;
    private int lastStateHash;

    public TradeScreen(TradeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected int baseWidth() {
        return GUI_WIDTH;
    }

    @Override
    protected int baseHeight() {
        return GUI_HEIGHT;
    }

    @Override
    protected int reservedRightPixels() {
        // Leave room for JEI overlay when present.
        return JeiCompat.reservedRightPixels();
    }

    @Override
    protected void init() {
        super.init();

        
        // FTB Library can inject sidebar buttons into any Screen; purge them.
        stripFtbSidebarWidgets();
// попытка подтянуть последнее состояние, если уже пришло
        TradeClientState state = TradeClientState.get(menu.getSessionId());
        if (state != null) {
            applyState(state);
            this.lastStateHash = state.hashCode();
        }
    }

    @Override
    protected void layoutWidgets() {
        // ВАЖНО: координаты виджетов задаём в базовой системе (0..baseWidth/baseHeight).

        // ===== money input (всегда слева: "Вы") =====
        int moneyX = MY_OFFER_X + 56;
        int moneyY = INFO_Y + 16;
        int moneyW = (OFFER_COLS * 18) - 56 - 6;

        if (this.moneyBox == null) {
            this.moneyBox = new EditBox(this.font, moneyX, moneyY, moneyW, 14,
                    Component.translatable("screen.avilixeconomy.trade.money_input"));
            this.moneyBox.setMaxLength(18);
            this.moneyBox.setBordered(true);
            this.moneyBox.setResponder(s -> this.sendCooldownTicks = 5);
            addRenderableWidget(this.moneyBox);
        } else {
            this.moneyBox.setPosition(moneyX, moneyY);
            this.moneyBox.setWidth(moneyW);
        }

        // ===== buttons =====
        int center = (this.imageWidth / 2);
        int btnY = TradeMenu.BUTTONS_Y;

        int readyW = 140;
        int cancelW = 80;
        int readyX = center - readyW - 4;
        int cancelX = center + 4;

        if (this.readyButton == null) {
            this.readyButton = new FlatTextButton(readyX, btnY, readyW, 20,
                    Component.translatable("screen.avilixeconomy.trade.confirm"),
                    b -> PacketDistributor.sendToServer(new NetworkRegistration.TradeToggleReadyPayload(menu.getSessionId()))
            );
            addRenderableWidget(this.readyButton);
        } else {
            this.readyButton.setX(readyX);
            this.readyButton.setY(btnY);
            this.readyButton.setWidth(readyW);
        }

        if (this.cancelButton == null) {
            this.cancelButton = new FlatTextButton(cancelX, btnY, cancelW, 20,
                    Component.translatable("screen.avilixeconomy.trade.cancel"),
                    b -> PacketDistributor.sendToServer(new NetworkRegistration.TradeCancelPayload(menu.getSessionId()))
            );
            addRenderableWidget(this.cancelButton);
        } else {
            this.cancelButton.setX(cancelX);
            this.cancelButton.setY(btnY);
            this.cancelButton.setWidth(cancelW);
        }

        updateReadyButton();
    }

    @Override
    protected void drawChrome(GuiGraphics gfx, float partialTick, int uiMouseX, int uiMouseY) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        boolean myReady = menu.getSide() == TradeMenu.Side.LEFT ? leftReady : rightReady;
        boolean theirReady = menu.getSide() == TradeMenu.Side.LEFT ? rightReady : leftReady;

        // ===== Main framed background (opaque, not transparent) =====
        drawMainPanel(gfx, OUTER_PAD, OUTER_PAD, this.imageWidth - OUTER_PAD * 2, this.imageHeight - OUTER_PAD * 2);

        int offerW = OFFER_COLS * 18;
        int offerH = OFFER_ROWS * 18;

        // Header bars
        int myAccent = myReady ? 0xFF2DAA4F : 0;
        int theirAccent = theirReady ? 0xFF2DAA4F : 0;
        drawHeaderBar(gfx, MY_OFFER_X - 2, OFFER_HEADER_Y - 1, offerW + 4, HEADER_H + 1, myAccent, false);
        drawHeaderBar(gfx, THEIR_OFFER_X - 2, OFFER_HEADER_Y - 1, offerW + 4, HEADER_H + 1, theirAccent, true);
        drawHeaderBar(gfx, INV_X - 2, INV_HEADER_Y - 1, 9 * 18 + 4, INV_HEADER_H + 1, 0, false);

        // Offer grids
        drawSlotGrid(gfx, MY_OFFER_X, OFFER_Y, OFFER_COLS, OFFER_ROWS, false);
        drawSlotGrid(gfx, THEIR_OFFER_X, OFFER_Y, OFFER_COLS, OFFER_ROWS, true);

        // Inventory + hotbar
        drawSlotGrid(gfx, INV_X, INV_Y, 9, 3, false);
        drawSlotGrid(gfx, INV_X, HOTBAR_Y, 9, 1, false);

        // Thin section frames (+ green when ready)
        int myBorder = myReady ? 0xFF2DAA4F : 0x88000000;
        int theirBorder = theirReady ? 0xFF2DAA4F : 0x88000000;
        drawThinRect(gfx, MY_OFFER_X - 1, OFFER_Y - 1, offerW + 2, offerH + 2, myBorder);
        drawThinRect(gfx, THEIR_OFFER_X - 1, OFFER_Y - 1, offerW + 2, offerH + 2, theirBorder);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        // Chrome is drawn via drawChrome() inside ResponsiveContainerScreen.
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        // ===== title + names (top) =====
        gfx.drawCenteredString(this.font, Component.translatable("screen.avilixeconomy.trade.title"), this.imageWidth / 2, 4, 0xFFFFFF);

        String myName = menu.getSide() == TradeMenu.Side.LEFT ? leftName : rightName;
        String theirName = menu.getSide() == TradeMenu.Side.LEFT ? rightName : leftName;

        Component leftNameLine = Component.translatable("screen.avilixeconomy.trade.you", myName);
        Component rightNameLine = Component.translatable("screen.avilixeconomy.trade.partner", theirName);

        int topY = 4;
        gfx.drawString(this.font, leftNameLine, 8, topY, 0xE6E6E6, false);
        int rx = this.imageWidth - 8 - this.font.width(rightNameLine);
        gfx.drawString(this.font, rightNameLine, rx, topY, 0xE6E6E6, false);

        // ===== section headers (bars are drawn in chrome) =====
        int offerW = OFFER_COLS * 18;

        Component yourOffer = Component.translatable("screen.avilixeconomy.trade.your_offer");
        drawTrimmed(gfx, this.font, yourOffer.getString(), MY_OFFER_X + 4, OFFER_HEADER_Y + 1, offerW - 8, 0xFFFFFF);

        Component partnerOffer = Component.translatable("screen.avilixeconomy.trade.partner_offer");
        String partnerTxt = partnerOffer.getString();
        String partnerTrim = trimToWidth(this.font, partnerTxt, offerW - 8);
        int px = THEIR_OFFER_X + offerW - 4 - this.font.width(partnerTrim);
        gfx.drawString(this.font, partnerTrim, px, OFFER_HEADER_Y + 1, 0xFFFFFF, false);

        Component inv = Component.translatable("screen.avilixeconomy.trade.inventory");
        gfx.drawCenteredString(this.font, inv, this.imageWidth / 2, INV_HEADER_Y + 1, 0xFFFFFF);

        // ===== info block (left: you, right: partner) =====
        int infoLeftX = MY_OFFER_X + 4;
        int infoRightX = THEIR_OFFER_X + 4;
        int infoTopY = INFO_Y;

        double myMoney = menu.getSide() == TradeMenu.Side.LEFT ? leftMoney : rightMoney;
        double theirMoney = menu.getSide() == TradeMenu.Side.LEFT ? rightMoney : leftMoney;

        gfx.drawString(this.font, Component.translatable("screen.avilixeconomy.trade.your_balance", MoneyUtils.formatNoks(myMoney)), infoLeftX, infoTopY + 6, 0xFFFFFF, false);
        gfx.drawString(this.font, Component.translatable("screen.avilixeconomy.trade.partner_balance", MoneyUtils.formatNoks(theirMoney)), infoRightX, infoTopY + 6, 0xFFFFFF, false);

        // Label for the money input box (in the left info panel)
        gfx.drawString(this.font, Component.translatable("screen.avilixeconomy.trade.amount_label"), infoLeftX, infoTopY + 18, 0xCFCFCF, false);

        boolean myReady = menu.getSide() == TradeMenu.Side.LEFT ? leftReady : rightReady;
        boolean theirReady = menu.getSide() == TradeMenu.Side.LEFT ? rightReady : leftReady;

        Component myState = Component.translatable(myReady ? "screen.avilixeconomy.trade.ready" : "screen.avilixeconomy.trade.not_ready");
        Component theirState = Component.translatable(theirReady ? "screen.avilixeconomy.trade.ready" : "screen.avilixeconomy.trade.not_ready");

        Component mySt = Component.translatable("screen.avilixeconomy.trade.status", myState);
        Component theirSt = Component.translatable("screen.avilixeconomy.trade.status", theirState);

        gfx.drawString(this.font, mySt, infoLeftX, infoTopY + 34, myReady ? 0xA0FFA0 : 0xFFFFFF, false);
        gfx.drawString(this.font, theirSt, infoRightX, infoTopY + 34, theirReady ? 0xA0FFA0 : 0xFFFFFF, false);
    }

    @Override
    public void containerTick() {
        super.containerTick();

        // обновление состояния с сервера
        TradeClientState state = TradeClientState.get(menu.getSessionId());
        if (state != null) {
            int h = state.hashCode();
            if (h != lastStateHash) {
                lastStateHash = h;
                applyState(state);
            }
        }

        // отправка денег с небольшой задержкой
        if (sendCooldownTicks > 0) {
            sendCooldownTicks--;
            if (sendCooldownTicks == 0) {
                double val = MoneyUtils.parseSmart(this.moneyBox.getValue());
                if (Double.compare(val, lastSentMoney) != 0) {
                    lastSentMoney = val;
                    PacketDistributor.sendToServer(new NetworkRegistration.TradeUpdateMoneyPayload(menu.getSessionId(), val));
                }
            }
        }

        updateReadyButton();
    }

    private void updateReadyButton() {
        if (this.readyButton == null) return;
        boolean myReady = menu.getSide() == TradeMenu.Side.LEFT ? leftReady : rightReady;
        this.readyButton.setMessage(Component.translatable(myReady ? "screen.avilixeconomy.trade.unconfirm" : "screen.avilixeconomy.trade.confirm"));
    }

    private void applyState(TradeClientState state) {
        if (state == null) return;
        this.leftName = state.leftName();
        this.rightName = state.rightName();
        this.leftMoney = state.leftMoney();
        this.rightMoney = state.rightMoney();
        this.leftReady = state.leftReady();
        this.rightReady = state.rightReady();

        // синхронизируем поле денег только если игрок не печатает
        if (this.moneyBox != null && !moneyBox.isFocused()) {
            double myMoney = menu.getSide() == TradeMenu.Side.LEFT ? leftMoney : rightMoney;
            this.moneyBox.setValue(MoneyUtils.formatNoks(myMoney));
            this.lastSentMoney = myMoney;
        }

        updateReadyButton();
    }

    // ===== Drawing helpers =====

    private static void drawThinRect(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int x1 = x;
        int y1 = y;
        int x2 = x + w - 1;
        int y2 = y + h - 1;
        gfx.hLine(x1, x2, y1, color);
        gfx.hLine(x1, x2, y2, color);
        gfx.vLine(x1, y1, y2, color);
        gfx.vLine(x2, y1, y2, color);
    }

    /**
     * Шапка секции: полоска + рамка.
     * dim=true делает шапку чуть темнее (например, для read-only стороны).
     */
    private static void drawHeaderBar(GuiGraphics gfx, int x, int y, int w, int h, int accentColor, boolean dim) {
        int baseTop = dim ? 0xFF2A2A2A : 0xFF303030;
        int baseBot = dim ? 0xFF1F1F1F : 0xFF252525;

        gfx.fillGradient(x, y, x + w, y + h, baseTop, baseBot);

        int light = dim ? 0xFF3A3A3A : 0xFF4A4A4A;
        int dark = 0xFF141414;
        gfx.hLine(x, x + w - 1, y, light);
        gfx.hLine(x, x + w - 1, y + h - 1, dark);
        gfx.vLine(x, y, y + h - 1, light);
        gfx.vLine(x + w - 1, y, y + h - 1, dark);

        if (accentColor != 0) {
            gfx.hLine(x + 2, x + w - 3, y + h - 2, accentColor);
        }
    }

    /** Рисует фон и рамки под сетку слотов (18x18). */
    private static void drawSlotGrid(GuiGraphics gfx, int startX, int startY, int cols, int rows, boolean dim) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                drawSlot(gfx, startX + c * 18, startY + r * 18, dim);
            }
        }
    }

    /** Ванильно-подобный слот (18x18). */
    private static void drawSlot(GuiGraphics gfx, int x, int y, boolean dim) {
        int inner = dim ? 0xFF202020 : 0xFF303030;
        gfx.fill(x, y, x + 18, y + 18, inner);

        int light = dim ? 0xFF3C3C3C : 0xFF5A5A5A;
        int dark = dim ? 0xFF0F0F0F : 0xFF171717;
        gfx.hLine(x, x + 17, y, light);
        gfx.vLine(x, y, y + 17, light);
        gfx.hLine(x, x + 17, y + 17, dark);
        gfx.vLine(x + 17, y, y + 17, dark);

        int hl = dim ? 0x22FFFFFF : 0x33FFFFFF;
        gfx.hLine(x + 1, x + 16, y + 1, hl);
        gfx.vLine(x + 1, y + 1, y + 16, hl);
    }

    private static void drawMainPanel(GuiGraphics gfx, int x, int y, int w, int h) {
        int top = 0xFF2B2B2B;
        int bot = 0xFF161616;
        gfx.fillGradient(x, y, x + w, y + h, top, bot);

        int light = 0xFF5A5A5A;
        int dark = 0xFF0B0B0B;
        gfx.hLine(x, x + w - 1, y, light);
        gfx.vLine(x, y, y + h - 1, light);
        gfx.hLine(x, x + w - 1, y + h - 1, dark);
        gfx.vLine(x + w - 1, y, y + h - 1, dark);
    }

    private static void drawPanel(GuiGraphics gfx, int x, int y, int w, int h, boolean dim) {
        if (w <= 0 || h <= 0) return;
        int top = dim ? 0xFF222222 : 0xFF2A2A2A;
        int bot = dim ? 0xFF141414 : 0xFF1A1A1A;
        gfx.fillGradient(x, y, x + w, y + h, top, bot);

        int light = dim ? 0xFF3E3E3E : 0xFF4E4E4E;
        int dark = 0xFF0F0F0F;
        gfx.hLine(x, x + w - 1, y, light);
        gfx.vLine(x, y, y + h - 1, light);
        gfx.hLine(x, x + w - 1, y + h - 1, dark);
        gfx.vLine(x + w - 1, y, y + h - 1, dark);
    }

    private static String trimToWidth(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;

        final String ell = "\u2026"; // …
        int ellW = font.width(ell);
        if (ellW >= maxWidth) return ell;

        String cut = font.plainSubstrByWidth(text, maxWidth - ellW);
        return cut + ell;
    }

    private static void drawTrimmed(GuiGraphics gfx, Font font, String text, int x, int y, int maxWidth, int color) {
        String t = trimToWidth(font, text, maxWidth);
        gfx.drawString(font, t, x, y, color, false);
    }

    
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Remove FTB sidebar widgets injected into this Screen.
        stripFtbSidebarWidgets();
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    // --- FTB Library / Quests sidebar suppression ---
    private void stripFtbSidebarWidgets() {
        try {
            this.children().removeIf(TradeScreen::isFtbSidebarObject);
        } catch (Throwable ignored) {}
        try {
            this.renderables.removeIf(TradeScreen::isFtbSidebarObject);
        } catch (Throwable ignored) {}
    }

    private static boolean isFtbSidebarObject(Object o) {
        if (o == null) return false;
        String n = o.getClass().getName();
        String l = n.toLowerCase(java.util.Locale.ROOT);
        return n.startsWith("dev.ftb.")
                || n.startsWith("com.feed_the_beast.")
                || l.contains("ftblibrary")
                || l.contains("ftbquests");
    }

}

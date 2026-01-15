package com.roften.avilixeconomy.shop.screen;

import com.roften.avilixeconomy.client.ui.ShopUi;
import com.roften.avilixeconomy.client.ui.UiKit;
import com.roften.avilixeconomy.AvilixEconomy;
import com.roften.avilixeconomy.compat.JeiCompat;
import com.roften.avilixeconomy.network.NetworkRegistration;
import com.roften.avilixeconomy.shop.menu.ShopConfigMenu;
import com.roften.avilixeconomy.util.MoneyUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Shop config screen (owner-only).
 *
 * Responsive goals:
 * - Works at any Minecraft GUI scale and any window size (including custom window sizes)
 * - Never overlaps widgets/slots; if the window is too small, the UI scales down to fit
 * - Uses extra width on large screens by widening the right sidebar instead of scaling everything up
 */
public class ShopConfigScreen extends AbstractContainerScreen<ShopConfigMenu> {

    private static final int LEFT_W = ShopConfigMenu.LEFT_W; // slot area width (fixed)
    private static final int GAP_W = 10;

    private static final int GUI_H = ShopConfigMenu.GUI_HEIGHT;

    private static final int TITLEBAR_H = 18;
    private static final int SECTION_H = 14;

    private static final int RIGHT_MIN_W = 168;
    private static final int RIGHT_MAX_W = 320;

    private static final int RIGHT_INNER_PAD = 10;

    private static final int TAB_H = 16;
    private static final int TAB_PAD_X = 6;
    private static final int TAB_PAD_Y = 5;
    private static final int TAB_GAP = 2;

    private static final int BOX_H = 14;
    private static final int BTN_H = 18;
    private static final int NAV_BTN = 16;

    private static final DateTimeFormatter SALES_TIME_FMT =
            DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());

    private enum RightTab {
        SETTINGS,
        SALES
    }

    private RightTab rightTab = RightTab.SETTINGS;

    private EditBox priceBox;
    private Button applyButton;
    private Button modeButton;

    // Sales history
    private Button salesPrev;
    private Button salesNext;
    private boolean salesLoading = false;
    private int salesOffset = 0;
    private int salesLimit = 12;
    private boolean salesHasMore = false;
    private List<ShopClientState.SalesEntry> salesRows = List.of();
    private long lastSalesVersion = -1L;

    // Layout (dynamic)
    private int rightW = 220;

    // --- UI transform (fit & center) ---
    private float uiScale = 1.0f;
    private int uiLeft = 0;
    private int uiTop = 0;
    private boolean suppressTooltip = false;

    /**
     * Some GPU/driver combos can cause geometry drawn via {@link #renderBg} (fill/lines/gradients)
     * to not appear, while widgets and item icons still render. To make the UI deterministic, we
     * draw the full chrome (panels/frames/slot backgrounds/labels) manually inside {@link #render}
     * before calling {@code super.render(...)}.
     */
    private boolean manualChromeDrawn = false;

    private int lastGuiW = -1;
    private int lastGuiH = -1;
    private boolean needsRelayout = true;

    public ShopConfigScreen(ShopConfigMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        // imageWidth is dynamic; set a sane default for the initial init()
        this.imageHeight = GUI_H;
        this.imageWidth = LEFT_W + GAP_W + this.rightW;
    }

    private int rightX() {
        return LEFT_W + GAP_W;
    }

    private int rightPanelY() {
        return TITLEBAR_H + 6;
    }

    private int rightPanelH() {
        return this.imageHeight - rightPanelY() - 6;
    }

    private int rightContentY() {
        return rightPanelY() + TAB_PAD_Y + TAB_H + 8;
    }

    private int rightFooterY() {
        return rightPanelY() + rightPanelH() - (NAV_BTN + 6);
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void updateLayoutDimensions() {
        // Use available window width to widen the right sidebar.
        int margin = 12;
        int reserved = JeiCompat.reservedRightPixels();
        int available = Math.max(0, (this.width - reserved) - margin * 2 - LEFT_W - GAP_W);
        this.rightW = clamp(available, RIGHT_MIN_W, RIGHT_MAX_W);
        this.imageWidth = LEFT_W + GAP_W + this.rightW;
        this.imageHeight = GUI_H;
    }

    @Override
    protected void init() {
        super.init();

        // Diagnostic stamp: helps confirm runClient is using latest sources.
        AvilixEconomy.LOGGER.info(UiKit.GUI_BUILD_STAMP);

        // We render in UI-space starting at 0,0 and translate/scale during render().
        this.leftPos = 0;
        this.topPos = 0;

        // Widgets are placed in relayoutWidgets().
        this.priceBox = new EditBox(this.font, 0, 0, 120, BOX_H,
                Component.translatable("screen.avilixeconomy.shop.price_label"));
        this.priceBox.setMaxLength(19);
        this.priceBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(ch -> (ch >= '0' && ch <= '9') || ch == '.' || ch == ','));
        this.priceBox.setHint(Component.literal("0"));
        this.priceBox.setValue(MoneyUtils.formatSmart(Math.max(0.0, this.menu.getActivePriceSynced())));
        this.addRenderableWidget(this.priceBox);

        this.applyButton = Button.builder(Component.translatable("screen.avilixeconomy.shop.apply"), b -> {
            double price = MoneyUtils.parseSmart(this.priceBox.getValue());
            PacketDistributor.sendToServer(new NetworkRegistration.ShopSetPricePayload(this.menu.getPos(), this.menu.getMode(), price));
        }).bounds(0, 0, 120, BTN_H).build();
        this.addRenderableWidget(this.applyButton);

        this.modeButton = Button.builder(Component.literal(""), b -> {
            boolean wantBuy = this.menu.getMode() == 0; // 0=SELL -> switch to BUY
            PacketDistributor.sendToServer(new NetworkRegistration.ShopSetModePayload(this.menu.getPos(), wantBuy));
        }).bounds(0, 0, 120, BTN_H).build();
        this.addRenderableWidget(this.modeButton);

        this.salesPrev = Button.builder(Component.literal("<"), b -> {
            if (this.salesLoading) return;
            this.salesOffset = Math.max(0, this.salesOffset - this.salesLimit);
            requestSales();
        }).bounds(0, 0, NAV_BTN, NAV_BTN).build();
        this.addRenderableWidget(this.salesPrev);

        this.salesNext = Button.builder(Component.literal(">"), b -> {
            if (this.salesLoading) return;
            this.salesOffset = this.salesOffset + this.salesLimit;
            requestSales();
        }).bounds(0, 0, NAV_BTN, NAV_BTN).build();
        this.addRenderableWidget(this.salesNext);

        // Layout
        this.needsRelayout = true;
        updateUiTransform();

        // Initial data
        requestSales();
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        this.leftPos = 0;
        this.topPos = 0;
        this.needsRelayout = true;
        updateUiTransform();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        this.needsRelayout = true;
        updateUiTransform();
    }

    private void updateUiTransform() {
        int guiW = this.width;
        int guiH = this.height;

        if (!needsRelayout && guiW == this.lastGuiW && guiH == this.lastGuiH) return;

        this.lastGuiW = guiW;
        this.lastGuiH = guiH;

        updateLayoutDimensions();

        int margin = 12;
        int reserved = JeiCompat.reservedRightPixels();
        float sx = ((guiW - reserved) - margin * 2) / (float) this.imageWidth;
        float sy = (guiH - margin * 2) / (float) this.imageHeight;

        float s = Math.min(1.0f, Math.min(sx, sy));
        if (!Float.isFinite(s) || s <= 0.0f) s = 1.0f;
        this.uiScale = s;

        int scaledW = Math.round(this.imageWidth * this.uiScale);
        int scaledH = Math.round(this.imageHeight * this.uiScale);

        // Keep the UI inside the left area when JEI overlay is present.
        int reservedForJei = JeiCompat.reservedRightPixels();
        this.uiLeft = ((guiW - reservedForJei) - scaledW) / 2;
        this.uiTop = (guiH - scaledH) / 2;

        this.leftPos = 0;
        this.topPos = 0;

        relayoutWidgets();
        this.needsRelayout = false;
    }

    private void relayoutWidgets() {
        if (this.modeButton == null || this.priceBox == null || this.applyButton == null || this.salesPrev == null || this.salesNext == null) return;

        int rx = rightX();
        int panelY = rightPanelY();
        int panelH = rightPanelH();

        int innerW = this.rightW - (RIGHT_INNER_PAD * 2);
        int controlW = Math.max(110, Math.min(170, innerW));

        int innerX = rx + (this.rightW - controlW) / 2;

        int y = rightContentY() + 6;

        this.modeButton.setX(innerX);
        this.modeButton.setY(y);
        this.modeButton.setWidth(controlW);

        y += 26;
        this.priceBox.setX(innerX);
        this.priceBox.setY(y);
        this.priceBox.setWidth(controlW);

        y += 20;
        this.applyButton.setX(innerX);
        this.applyButton.setY(y);
        this.applyButton.setWidth(controlW);

        // Sales paging buttons in footer
        int footerY = panelY + panelH - (NAV_BTN + 6);

        this.salesPrev.setX(rx + RIGHT_INNER_PAD);
        this.salesPrev.setY(footerY);
        this.salesPrev.setWidth(NAV_BTN);

        this.salesNext.setX(rx + this.rightW - RIGHT_INNER_PAD - NAV_BTN);
        this.salesNext.setY(footerY);
        this.salesNext.setWidth(NAV_BTN);

        // Dynamic page size based on visible list height (makes big screens actually useful).
        int listY = rightContentY();
        int listBottom = footerY - 6;
        int listH = Math.max(0, listBottom - listY);
        int desiredLimit = Math.max(6, (listH - 12) / 34);
        if (desiredLimit != this.salesLimit) {
            this.salesLimit = desiredLimit;
            // Keep offset aligned to the new page size
            this.salesOffset = (this.salesOffset / this.salesLimit) * this.salesLimit;
            requestSales();
        }

        updateRightTabWidgets();
    }

    private void updateRightTabWidgets() {
        boolean settings = this.rightTab == RightTab.SETTINGS;
        boolean sales = this.rightTab == RightTab.SALES;

        this.modeButton.visible = settings;
        this.modeButton.active = settings;

        this.priceBox.visible = settings;
        this.priceBox.active = settings;

        this.applyButton.visible = settings;
        this.applyButton.active = settings;

        this.salesPrev.visible = sales;
        this.salesPrev.active = sales && this.salesOffset > 0 && !this.salesLoading;

        this.salesNext.visible = sales;
        this.salesNext.active = sales && this.salesHasMore && !this.salesLoading;
    }

    private void setRightTab(RightTab tab) {
        if (tab == null || tab == this.rightTab) return;
        this.rightTab = tab;
        updateRightTabWidgets();
    }

    private boolean handleTabClick(int uiMouseX, int uiMouseY) {
        int panelX = rightX();
        int panelY = rightPanelY();
        int tabY = panelY + TAB_PAD_Y;
        if (uiMouseY < tabY || uiMouseY >= tabY + TAB_H) return false;

        int tabW = (this.rightW - TAB_PAD_X * 2 - TAB_GAP) / 2;
        int x1 = panelX + TAB_PAD_X;
        int x2 = x1 + tabW + TAB_GAP;

        if (uiMouseX >= x1 && uiMouseX < x1 + tabW) {
            setRightTab(RightTab.SETTINGS);
            return true;
        }
        if (uiMouseX >= x2 && uiMouseX < x2 + tabW) {
            setRightTab(RightTab.SALES);
            return true;
        }
        return false;
    }

    private boolean isInSalesList(int uiMouseX, int uiMouseY) {
        if (this.rightTab != RightTab.SALES) return false;

        int listX = rightX() + RIGHT_INNER_PAD;
        int listW = this.rightW - (RIGHT_INNER_PAD * 2);

        int listY = rightContentY();
        int listBottom = rightFooterY() - 6;

        return uiMouseX >= listX && uiMouseX < listX + listW && uiMouseY >= listY && uiMouseY < listBottom;
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        // Keep price in sync if player didn't type anything yet.
        if (this.priceBox != null && !this.priceBox.isFocused() && (this.priceBox.getValue() == null || this.priceBox.getValue().isBlank())) {
            this.priceBox.setValue(MoneyUtils.formatSmart(Math.max(0.0, this.menu.getActivePriceSynced())));
        }

        var page = ShopClientState.getSales(this.menu.getPos());
        if (page != null && page.version() != this.lastSalesVersion) {
            this.lastSalesVersion = page.version();
            this.salesRows = page.rows();
            this.salesHasMore = page.hasMore();
            this.salesOffset = page.offset();
            this.salesLimit = page.limit();
            this.salesLoading = false;
        }

        updateRightTabWidgets();

        if (this.modeButton != null) {
            this.modeButton.setMessage(this.menu.getMode() == 1
                    ? Component.translatable("screen.avilixeconomy.shop.mode_buy_short")
                    : Component.translatable("screen.avilixeconomy.shop.mode_sell_short"));
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        updateUiTransform();


        int uiMouseX = (int) ((mouseX - this.uiLeft) / this.uiScale);
        int uiMouseY = (int) ((mouseY - this.uiTop) / this.uiScale);

        this.suppressTooltip = true;

        gfx.pose().pushPose();
        gfx.pose().translate(this.uiLeft, this.uiTop, 0);
        gfx.pose().scale(this.uiScale, this.uiScale, 1.0f);

        this.manualChromeDrawn = true;

        // Draw chrome BEFORE slots/items/widgets.
        drawChrome(gfx, partialTick, uiMouseX, uiMouseY);
        super.render(gfx, uiMouseX, uiMouseY, partialTick);

        this.manualChromeDrawn = false;

        gfx.pose().popPose();

        this.suppressTooltip = false;

        // Tooltips render in real screen coords, but use hoveredSlot computed during scaled pass.
        super.renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty: keep the world fully visible behind the shop UI.
        // (Vanilla container background can also trigger an unscaled render pass on some setups.)
    }

    @Override
    public void renderTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        if (this.suppressTooltip) return;
        super.renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateUiTransform();
        double ux = (mouseX - this.uiLeft) / this.uiScale;
        double uy = (mouseY - this.uiTop) / this.uiScale;

        if (button == 0 && handleTabClick((int) ux, (int) uy)) return true;
        return super.mouseClicked(ux, uy, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        updateUiTransform();
        return super.mouseReleased((mouseX - this.uiLeft) / this.uiScale, (mouseY - this.uiTop) / this.uiScale, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        updateUiTransform();
        return super.mouseDragged(
                (mouseX - this.uiLeft) / this.uiScale,
                (mouseY - this.uiTop) / this.uiScale,
                button,
                dragX / this.uiScale,
                dragY / this.uiScale
        );
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        updateUiTransform();
        double ux = (mouseX - this.uiLeft) / this.uiScale;
        double uy = (mouseY - this.uiTop) / this.uiScale;

        // Scroll the sales list by pages
        if (this.rightTab == RightTab.SALES && deltaY != 0.0D && isInSalesList((int) ux, (int) uy) && !this.salesLoading) {
            if (deltaY > 0.0D && this.salesOffset > 0) {
                this.salesOffset = Math.max(0, this.salesOffset - this.salesLimit);
                requestSales();
                return true;
            }
            if (deltaY < 0.0D && this.salesHasMore) {
                this.salesOffset = this.salesOffset + this.salesLimit;
                requestSales();
                return true;
            }
        }

        return super.mouseScrolled(ux, uy, deltaX, deltaY);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        if (this.manualChromeDrawn) return;
        drawChrome(gfx, partialTick, mouseX, mouseY);
    }

    private void drawChrome(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        // IMPORTANT (1.21+): depth test may be enabled in container rendering.
        // Geometry-only UI (fill/lines/gradients) should be drawn with depth disabled,
        // otherwise it can become invisible while item icons/widgets still render.
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // All rendering happens inside the translated/scaled pose; leftPos/topPos must stay at 0.
        int x = this.leftPos;
        int y = this.topPos;

        ShopUi.drawDropShadow(gfx, x, y, this.imageWidth, this.imageHeight);
        ShopUi.drawWindowFrame(gfx, x, y, this.imageWidth, this.imageHeight);

        ShopUi.drawTitleBar(gfx, x, y, this.imageWidth, TITLEBAR_H);
        gfx.drawCenteredString(this.font,
                Component.translatable("screen.avilixeconomy.shop.config.title"),
                this.imageWidth / 2, 5, 0xFFFFFF);

        // ===== left column =====
        int contentY = TITLEBAR_H + 6;
        int contentH = this.imageHeight - contentY - 6;
        ShopUi.drawPanel(gfx, x + 2, y + contentY, LEFT_W - 4, contentH);

        boolean buyingFromPlayers = this.menu.getMode() == 1;
        Component lotLabel = buyingFromPlayers
                ? Component.translatable("screen.avilixeconomy.shop.lot_buy")
                : Component.translatable("screen.avilixeconomy.shop.lot_sell");

        // Place section titles safely above slot grids (no overlap at any GUI scale).
        int hdrPad = this.font.lineHeight + 8;

        // Template 3x3
        ShopUi.drawPanel(gfx, x + ShopConfigMenu.TEMPLATE_X - 1, y + ShopConfigMenu.TEMPLATE_Y - 1, 3 * 18 + 2, 3 * 18 + 2);
        ShopUi.drawSubHeader(gfx, this.font, x + ShopConfigMenu.TEMPLATE_X - 1, y + ShopConfigMenu.TEMPLATE_Y - hdrPad, 3 * 18 + 2, SECTION_H, lotLabel);
        ShopUi.drawSlotGrid(gfx, x + ShopConfigMenu.TEMPLATE_X, y + ShopConfigMenu.TEMPLATE_Y, 3, 3);

        // Stock 6x9
        ShopUi.drawPanel(gfx, x + ShopConfigMenu.STOCK_X - 1, y + ShopConfigMenu.STOCK_Y - 1, 9 * 18 + 2, 6 * 18 + 2);
        ShopUi.drawSubHeader(gfx, this.font, x + ShopConfigMenu.STOCK_X - 1, y + ShopConfigMenu.STOCK_Y - hdrPad, 9 * 18 + 2, SECTION_H,
                Component.translatable("screen.avilixeconomy.shop.shop_inventory"));
        ShopUi.drawSlotGrid(gfx, x + ShopConfigMenu.STOCK_X, y + ShopConfigMenu.STOCK_Y, 9, 6);

        // Player inventory + hotbar
        ShopUi.drawPanel(gfx, x + ShopConfigMenu.INV_X - 1, y + ShopConfigMenu.INV_Y - 1, 9 * 18 + 2, 3 * 18 + 2);
        ShopUi.drawSlotGrid(gfx, x + ShopConfigMenu.INV_X, y + ShopConfigMenu.INV_Y, 9, 3);

        ShopUi.drawPanel(gfx, x + ShopConfigMenu.INV_X - 1, y + ShopConfigMenu.HOTBAR_Y - 1, 9 * 18 + 2, 18 + 2);
        ShopUi.drawSlotGrid(gfx, x + ShopConfigMenu.INV_X, y + ShopConfigMenu.HOTBAR_Y, 9, 1);

        ShopUi.drawSubHeader(gfx, this.font, x + ShopConfigMenu.INV_X - 1, y + ShopConfigMenu.INV_Y - hdrPad, 9 * 18 + 2, SECTION_H, this.playerInventoryTitle);

        // ===== right column (tabs) =====
        int rx = rightX();
        int rY = rightPanelY();
        int rH = rightPanelH();
        ShopUi.drawPanel(gfx, x + rx, y + rY, this.rightW - 2, rH);

        drawRightTabs(gfx, x + rx, y + rY);

        if (this.rightTab == RightTab.SETTINGS) {
            int labelX = x + rx + RIGHT_INNER_PAD;

            int modeLabelY = (this.modeButton != null) ? (this.modeButton.getY() - 10) : (y + rightContentY() - 2);
            gfx.drawString(this.font, Component.translatable("screen.avilixeconomy.shop.mode"), labelX, modeLabelY, 0xCFCFCF, false);

            int priceLabelY = (this.priceBox != null) ? (this.priceBox.getY() - 10) : (modeLabelY + 24);
            gfx.drawString(this.font, Component.translatable("screen.avilixeconomy.shop.price_label"), labelX, priceLabelY, 0xCFCFCF, false);

            // Commission info (variant B): affects payout, not the listed price.
            int sellBps = this.menu.getCommissionSellBps();
            int buyBps = this.menu.getCommissionBuyBps();
            String sellPct = String.format(java.util.Locale.ROOT, "%.2f", sellBps / 100.0d);
            String buyPct = String.format(java.util.Locale.ROOT, "%.2f", buyBps / 100.0d);
            int commY = (this.applyButton != null) ? (this.applyButton.getY() + BTN_H + 8) : (priceLabelY + 34);
            gfx.drawString(this.font,
                    Component.translatable("screen.avilixeconomy.shop.commission_config_value", sellPct, buyPct),
                    labelX, commY, 0xCFCFCF, false);
        } else {
            drawSalesList(gfx, x + rx, y + rY);
        }

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        // All labels are drawn in renderBg().
    }

    private void drawRightTabs(GuiGraphics gfx, int panelX, int panelY) {
        int tabW = (this.rightW - TAB_PAD_X * 2 - TAB_GAP) / 2;
        int tabY = panelY + TAB_PAD_Y;
        int x1 = panelX + TAB_PAD_X;
        int x2 = x1 + tabW + TAB_GAP;

        ShopUi.drawTab(gfx, this.font, x1, tabY, tabW, TAB_H,
                Component.translatable("screen.avilixeconomy.shop.settings"),
                this.rightTab == RightTab.SETTINGS);

        ShopUi.drawTab(gfx, this.font, x2, tabY, tabW, TAB_H,
                Component.translatable("screen.avilixeconomy.shop.sales_title"),
                this.rightTab == RightTab.SALES);
    }

    private void drawSalesList(GuiGraphics gfx, int panelX, int panelY) {
        int listX = panelX + RIGHT_INNER_PAD;
        int listW = this.rightW - (RIGHT_INNER_PAD * 2);

        int listY = panelY + (rightContentY() - rightPanelY());
        int footerY = panelY + (rightFooterY() - rightPanelY());
        int listBottom = footerY - 6;
        int listH = Math.max(24, listBottom - listY);

        gfx.fill(listX, listY, listX + listW, listY + listH, 0xFF1A1A1A);
        gfx.hLine(listX, listX + listW - 1, listY, 0xFF0F0F0F);
        gfx.hLine(listX, listX + listW - 1, listY + listH - 1, 0xFF2A2A2A);

        if (this.salesLoading) {
            gfx.drawString(this.font, Component.translatable("screen.avilixeconomy.shop.sales_loading"), listX + 6, listY + 6, 0xCFCFCF, false);
            return;
        }

        if (this.salesRows == null || this.salesRows.isEmpty()) {
            gfx.drawString(this.font, Component.translatable("screen.avilixeconomy.shop.sales_empty"), listX + 6, listY + 6, 0xCFCFCF, false);
            return;
        }

        int y = listY + 6;
        int maxY = listY + listH - 6;

        for (ShopClientState.SalesEntry row : this.salesRows) {
            // Reserve at least the timestamp + total lines.
            if (y + 24 > maxY) break;

            String time = SALES_TIME_FMT.format(java.time.Instant.ofEpochMilli(row.createdAtMillis()));
            gfx.drawString(this.font, time, listX + 4, y, 0x9A9A9A, false);

            gfx.drawString(this.font,
                    Component.translatable("screen.avilixeconomy.shop.sales_total_short", MoneyUtils.formatSmart(row.totalPrice())),
                    listX + 4, y + 10, 0x80FF80, false);

            int lineY = y + 20;
            String summary = row.itemsSummary();
            if (summary != null && !summary.isBlank()) {
                // Wrap the items summary so long lines don't get cut off.
                List<net.minecraft.util.FormattedCharSequence> lines = this.font.split(Component.literal(summary), listW - 8);
                int maxLines = Math.min(3, lines.size()); // keep list readable
                for (int i = 0; i < maxLines; i++) {
                    if (lineY + this.font.lineHeight > maxY) break;
                    gfx.drawString(this.font, lines.get(i), listX + 4, lineY, 0xCFCFCF, false);
                    lineY += this.font.lineHeight;
                }
            }

            // Spacing between entries.
            y = Math.max(lineY + 6, y + 34);
        }
    }

    private void requestSales() {
        this.salesLoading = true;
        PacketDistributor.sendToServer(new NetworkRegistration.ShopRequestSalesPayload(this.menu.getPos(), this.salesLimit, this.salesOffset));
    }

    private static long parseLongDigits(String s) {
        if (s == null || s.isBlank()) return 0L;
        long v = 0L;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') continue;
            int d = c - '0';
            if (v > (Long.MAX_VALUE - d) / 10L) return Long.MAX_VALUE;
            v = v * 10L + d;
        }
        return v;
    }
}

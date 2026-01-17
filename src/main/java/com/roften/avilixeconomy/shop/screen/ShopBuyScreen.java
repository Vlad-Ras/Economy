package com.roften.avilixeconomy.shop.screen;

import com.roften.avilixeconomy.client.ui.ShopUi;
import com.roften.avilixeconomy.client.ui.UiKit;
import com.roften.avilixeconomy.AvilixEconomy;
import com.roften.avilixeconomy.compat.JeiCompat;
import com.roften.avilixeconomy.network.NetworkRegistration;
import com.roften.avilixeconomy.shop.menu.ShopBuyMenu;
import com.roften.avilixeconomy.util.MoneyUtils;
import com.roften.avilixeconomy.commission.CommissionManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Shop buy/sell screen.
 *
 * Responsive goals:
 * - Works at any Minecraft GUI scale and any window size (including custom window sizes)
 * - No overlaps; if the window is too small, the UI scales down to fit
 * - Uses extra width on large screens by widening the right sidebar instead of scaling everything up
 */
public class ShopBuyScreen extends AbstractContainerScreen<ShopBuyMenu> {

    private static final int LEFT_W = ShopBuyMenu.GUI_WIDTH;   // 176 (slots)
    private static final int LEFT_H = ShopBuyMenu.GUI_HEIGHT;  // 166

    private static final int GAP_W = 10;

    private static final int TITLEBAR_H = 18;
    private static final int SECTION_H = 14;

    private static final int RIGHT_MIN_W = 168;
    private static final int RIGHT_MAX_W = 300;

    private static final int RIGHT_INNER_PAD = 10;

    private static final int BOX_H = 14;
    private static final int BTN_H = 18;

    private EditBox qtyBox;
    private Button actionButton;

    // Layout (dynamic)
    private int rightW = 220;

    // --- UI transform (fit & center) ---
    private float uiScale = 1.0f;
    private int uiLeft = 0;
    private int uiTop = 0;
    private boolean suppressTooltip = false;

    /**
     * Some GPU/driver combos can cause geometry drawn via {@link #renderBg} (fill/lines/gradients)
     * to not appear, while widgets and item icons still render.
     *
     * To make the UI deterministic, we draw the full chrome (panels/frames/slot backgrounds/labels)
     * manually inside {@link #render} before calling {@code super.render(...)}.
     */
    private boolean manualChromeDrawn = false;

    /** Hovered ghost item from the lot preview (rendered manually, not as slots). */
    private ItemStack hoveredGhost = ItemStack.EMPTY;

    private int lastGuiW = -1;
    private int lastGuiH = -1;
    private boolean needsRelayout = true;

    // --- JEI compatibility (computed screen-space bounds of the scaled UI) ---
    public int getUiLeftScreen() { return uiLeft; }
    public int getUiTopScreen() { return uiTop; }
    public float getUiScale() { return uiScale; }
    public int getScaledUiWidth() { return Math.round(this.imageWidth * this.uiScale); }
    public int getScaledUiHeight() { return Math.round(this.imageHeight * this.uiScale); }

    public ShopBuyScreen(ShopBuyMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageHeight = LEFT_H;
        this.imageWidth = LEFT_W + GAP_W + this.rightW;
    }

    private int rightX() {
        return LEFT_W + GAP_W;
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void updateLayoutDimensions() {
        int margin = 12;
        int reserved = JeiCompat.reservedRightPixels();
        int available = Math.max(0, (this.width - reserved) - margin * 2 - LEFT_W - GAP_W);
        this.rightW = clamp(available, RIGHT_MIN_W, RIGHT_MAX_W);
        this.imageWidth = LEFT_W + GAP_W + this.rightW;
        this.imageHeight = LEFT_H;
    }

    @Override
    protected void init() {
        super.init();

        // Diagnostic stamp: helps confirm runClient is using latest sources.
        AvilixEconomy.LOGGER.info(UiKit.GUI_BUILD_STAMP);

        // We render in UI space starting at 0,0 and then translate/scale in render().
        this.leftPos = 0;
        this.topPos = 0;

        this.qtyBox = new EditBox(this.font, 0, 0, 120, BOX_H,
                Component.translatable("screen.avilixeconomy.shop.qty"));
        this.qtyBox.setMaxLength(6);
        this.qtyBox.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        this.qtyBox.setHint(Component.literal("1"));
        if (this.qtyBox.getValue().isEmpty()) this.qtyBox.setValue("1");
        this.addRenderableWidget(this.qtyBox);

        this.actionButton = Button.builder(Component.literal(""), b -> onAction())
                .bounds(0, 0, 120, BTN_H)
                .build();
        this.addRenderableWidget(this.actionButton);

        this.needsRelayout = true;
        updateUiTransform();
        updateActionStateAndText();
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

        // If JEI is present, keep the UI inside the left part of the screen so it doesn't overlap
        // JEI overlay on the right.
        int reservedForJei = JeiCompat.reservedRightPixels();
        this.uiLeft = ((guiW - reservedForJei) - scaledW) / 2;
        this.uiTop = (guiH - scaledH) / 2;

        this.leftPos = 0;
        this.topPos = 0;

        relayoutWidgets();
        this.needsRelayout = false;
    }

    private void relayoutWidgets() {
        if (this.qtyBox == null || this.actionButton == null) return;

        int rx = rightX();
        int innerW = this.rightW - (RIGHT_INNER_PAD * 2);
        int controlW = Math.max(110, Math.min(170, innerW));
        int innerX = rx + (this.rightW - controlW) / 2;

        int contentY = TITLEBAR_H + 6;
        int contentH = this.imageHeight - contentY - 6;
        int bottom = contentY + contentH;

        int gap = 4;
        int buttonY = bottom - BTN_H;
        int boxY = buttonY - gap - BOX_H;

        this.qtyBox.setX(innerX);
        this.qtyBox.setY(boxY);
        this.qtyBox.setWidth(controlW);

        this.actionButton.setX(innerX);
        this.actionButton.setY(buttonY);
        this.actionButton.setWidth(controlW);
    }

    private int parseLots() {
        if (this.qtyBox == null) return 0;
        String s = this.qtyBox.getValue();
        if (s == null || s.isBlank()) return 0;
        try {
            return Math.max(0, Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void onAction() {
        int lots = parseLots();
        if (lots <= 0) return;

        if (this.menu.getMode() == 1) {
            // shop is buying, player sells
            PacketDistributor.sendToServer(new NetworkRegistration.ShopSellPayload(this.menu.getPos(), lots));
        } else {
            // shop is selling, player buys
            PacketDistributor.sendToServer(new NetworkRegistration.ShopBuyPayload(this.menu.getPos(), lots));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ShopToastState.tick();
        updateActionStateAndText();
    }

    private void updateActionStateAndText() {
        if (this.actionButton == null) return;

        boolean sellingToShop = this.menu.getMode() == 1;
        this.actionButton.setMessage(sellingToShop
                ? Component.translatable("screen.avilixeconomy.shop.sell")
                : Component.translatable("screen.avilixeconomy.shop.buy"));

        int lots = parseLots();
        int max = this.menu.getAvailableLots();
        if (sellingToShop) {
            max = Math.min(max, computePlayerLots());
        }

        this.actionButton.active = lots > 0 && lots <= max;
    }

    private int computePlayerLots() {
        if (this.minecraft == null || this.minecraft.player == null) return 0;

        // Read template from the first 9 slots of the menu (always present).
        List<ItemStack> samples = new ArrayList<>();
        List<Integer> required = new ArrayList<>();

        for (int i = 0; i < 9 && i < this.menu.slots.size(); i++) {
            ItemStack t = this.menu.slots.get(i).getItem();
            if (t == null || t.isEmpty()) continue;

            boolean merged = false;
            for (int j = 0; j < samples.size(); j++) {
                if (ItemStack.isSameItemSameComponents(samples.get(j), t)) {
                    required.set(j, required.get(j) + t.getCount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                samples.add(t.copyWithCount(1));
                required.add(Math.max(1, t.getCount()));
            }
        }

        if (samples.isEmpty()) return 0;

        Inventory inv = this.minecraft.player.getInventory();
        int lots = Integer.MAX_VALUE;

        for (int j = 0; j < samples.size(); j++) {
            ItemStack sample = samples.get(j);
            int need = required.get(j);

            int have = 0;
            for (ItemStack s : inv.items) {
                if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, sample)) {
                    have += s.getCount();
                }
            }
            for (ItemStack s : inv.armor) {
                if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, sample)) {
                    have += s.getCount();
                }
            }
            for (ItemStack s : inv.offhand) {
                if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, sample)) {
                    have += s.getCount();
                }
            }

            lots = Math.min(lots, have / need);
        }

        return Math.max(0, lots);
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

        // Render shop toast ABOVE the UI (in screen space, so it is never hidden behind the window).
        renderShopToast(gfx);

        this.suppressTooltip = false;

        super.renderTooltip(gfx, mouseX, mouseY);

        // Manual tooltip for the ghost lot preview.
        if (!this.hoveredGhost.isEmpty()) {
            gfx.renderTooltip(this.font, this.hoveredGhost, mouseX, mouseY);
        }
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

    private void renderShopToast(GuiGraphics gfx) {
        ShopToastState.Toast toast = ShopToastState.getCurrent();
        if (toast == null) return;

        Component msg = toast.message();
        if (msg == null) return;

        String s = msg.getString();
        if (s.isEmpty()) return;

        int textW = this.font.width(s);
        int padX = 6;
        int padY = 3;
        int boxW = textW + padX * 2;
        int boxH = this.font.lineHeight + padY * 2;

        int scaledW = Math.round(this.imageWidth * this.uiScale);
        int x = this.uiLeft + Math.max(0, (scaledW - boxW) / 2);
        int y = this.uiTop - boxH - 4;

        // clamp to screen
        if (y < 2) y = 2;

        int bg = 0xB0000000;
        int fg = toast.success() ? 0x55FF55 : 0xFF5555;

        gfx.fill(x, y, x + boxW, y + boxH, bg);
        gfx.drawString(this.font, s, x + padX, y + padY, fg, false);
    }

    @Override
    public void removed() {
        super.removed();
        ShopToastState.clear();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateUiTransform();
        return super.mouseClicked((mouseX - this.uiLeft) / this.uiScale, (mouseY - this.uiTop) / this.uiScale, button);
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
        return super.mouseScrolled(
                (mouseX - this.uiLeft) / this.uiScale,
                (mouseY - this.uiTop) / this.uiScale,
                deltaX,
                deltaY
        );
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        if (this.manualChromeDrawn) return;
        drawChrome(gfx, partialTick, mouseX, mouseY);
    }

    private void drawChrome(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        this.hoveredGhost = ItemStack.EMPTY;

        // IMPORTANT (1.21+): when container screens render, depth test may be enabled.
        // Geometry-only UI (fill/lines/gradients) must be drawn with depth disabled,
        // otherwise it can become fully invisible on some drivers / GPU configs.
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int x = this.leftPos;
        int y = this.topPos;

        // Reset hovered ghost each frame.
        this.hoveredGhost = ItemStack.EMPTY;

        ShopUi.drawDropShadow(gfx, x, y, this.imageWidth, this.imageHeight);
        ShopUi.drawWindowFrame(gfx, x, y, this.imageWidth, this.imageHeight);

        ShopUi.drawTitleBar(gfx, x, y, this.imageWidth, TITLEBAR_H);
        gfx.drawCenteredString(this.font,
                Component.translatable("screen.avilixeconomy.shop.buy.title"),
                this.imageWidth / 2, 5, 0xFFFFFF);

        boolean sellingToShop = this.menu.getMode() == 1;
        Component lotLabel = sellingToShop
                ? Component.translatable("screen.avilixeconomy.shop.lot_buy")
                : Component.translatable("screen.avilixeconomy.shop.lot_sell");

        // Content panels
        int contentY = TITLEBAR_H + 6;
        int contentH = this.imageHeight - contentY - 6;

        // left slots panel
        ShopUi.drawPanel(gfx, x + 2, y + contentY, LEFT_W - 4, contentH);

        // right info/actions panel
        int rx = rightX();
        ShopUi.drawPanel(gfx, x + rx, y + contentY, this.rightW - 2, contentH);
        ShopUi.drawSubHeader(gfx, this.font, x + rx + 1, y + contentY + 1, this.rightW - 4, SECTION_H,
                Component.translatable("screen.avilixeconomy.shop.transaction"));

        // Slot separators + section headers
        // Place section titles safely above slot grids (no overlap at any GUI scale).
        int hdrPad = this.font.lineHeight + 8;

        int templateX = ShopBuyMenu.TEMPLATE_X;
        int templateY = ShopBuyMenu.TEMPLATE_Y;

        int invX = ShopBuyMenu.INV_X;
        int invY = ShopBuyMenu.INV_Y;
        int hotbarY = ShopBuyMenu.HOTBAR_Y;

        // Template 3x3 (ghost preview)
        ShopUi.drawPanel(gfx, x + templateX - 1, y + templateY - 1, 3 * 18 + 2, 3 * 18 + 2);
        // Header spans the left panel, so the long text does not overlap the right panel.
        ShopUi.drawSubHeader(gfx, this.font, x + 10, y + templateY - hdrPad, LEFT_W - 20, SECTION_H, lotLabel);
        ShopUi.drawSlotGrid(gfx, x + templateX, y + templateY, 3, 3);

        // Draw the lot items as a non-interactive preview.
        // IMPORTANT: show the required count per lot as the stack count overlay.
        // The actual container slots for the template are kept off-screen (see ShopBuyMenu).
        for (int i = 0; i < 9 && i < this.menu.slots.size(); i++) {
            ItemStack stack = this.menu.slots.get(i).getItem();
            if (stack == null || stack.isEmpty()) continue;

            int c = i % 3;
            int r = i / 3;
            int sx = x + templateX + c * 18 + 1;
            int sy = y + templateY + r * 18 + 1;

            gfx.renderItem(stack, sx, sy);
            // Show the required item count per lot (e.g. "16") on the ghost preview.
            gfx.renderItemDecorations(this.font, stack, sx, sy);

            // Hover tooltip for the ghost slot.
            int hx = x + templateX + c * 18;
            int hy = y + templateY + r * 18;
            if (mouseX >= hx && mouseX < hx + 18 && mouseY >= hy && mouseY < hy + 18) {
                this.hoveredGhost = stack;
            }
        }

        // Player inventory
        ShopUi.drawSubHeader(gfx, this.font, x + invX - 1, y + invY - hdrPad, 9 * 18 + 2, SECTION_H, this.playerInventoryTitle);
        ShopUi.drawSlotGrid(gfx, x + invX, y + invY, 9, 3);
        ShopUi.drawSlotGrid(gfx, x + invX, y + hotbarY, 9, 1);

        // Right panel info text
        int textX = x + rx + RIGHT_INNER_PAD;
        int textY = y + contentY + SECTION_H + 10;

        Component mode = sellingToShop
                ? Component.translatable("screen.avilixeconomy.shop.mode_buy_short")
                : Component.translatable("screen.avilixeconomy.shop.mode_sell_short");

        gfx.drawString(this.font, Component.translatable("screen.avilixeconomy.shop.mode"), textX, textY, 0xFFFFFF, false);
        textY += 12;
        gfx.drawString(this.font, mode, textX, textY, 0xCFCFCF, false);
        textY += 14;

        gfx.drawString(this.font,
                Component.translatable("screen.avilixeconomy.shop.price_value", MoneyUtils.formatSmart(this.menu.getPricePerLot())),
                textX, textY, 0xCFCFCF, false);
        textY += 12;

        int avail = this.menu.getAvailableLots();
        gfx.drawString(this.font,
                Component.translatable("screen.avilixeconomy.shop.available_value", Integer.toString(avail)),
                textX, textY, 0xCFCFCF, false);
        textY += 12;

        int max = avail;
        if (sellingToShop) {
            int yourLots = computePlayerLots();
            max = Math.min(avail, yourLots);
            gfx.drawString(this.font,
                    Component.translatable("screen.avilixeconomy.shop.your_lots_value", Integer.toString(yourLots)),
                    textX, textY, 0xCFCFCF, false);
            textY += 12;
        }

        int lots = parseLots();
        double total = MoneyUtils.round2((double) lots * this.menu.getPricePerLot());
        gfx.drawString(this.font,
                Component.translatable("screen.avilixeconomy.shop.total_value", MoneyUtils.formatSmart(total)),
                textX, textY, 0x80FF80, false);
        textY += 12;

        int commBps = this.menu.getCommissionBps();
        if (commBps > 0) {
            double pct = commBps / 100.0d;
            double fee = CommissionManager.computeFee(total, commBps);
            double net = Math.max(0.0, MoneyUtils.round2(total - fee));

            String pctStr = String.format(java.util.Locale.ROOT, "%.2f", pct);
            gfx.drawString(this.font,
                    Component.translatable("screen.avilixeconomy.shop.commission_value", pctStr),
                    textX, textY, 0xCFCFCF, false);
            textY += 12;

            Component netLine = sellingToShop
                    ? Component.translatable("screen.avilixeconomy.shop.net_you_value", MoneyUtils.formatSmart(net))
                    : Component.translatable("screen.avilixeconomy.shop.net_seller_value", MoneyUtils.formatSmart(net));
            gfx.drawString(this.font, netLine, textX, textY, 0xCFCFCF, false);
            textY += 12;
        }

        // qty label (EditBox is a widget, positioned in relayoutWidgets)
        // Keep label safely above the EditBox (no clipping/overlap on any GUI scale).
        int qtyLabelY = this.qtyBox != null ? (this.qtyBox.getY() - 14) : (contentY + contentH - (BTN_H + 4 + BOX_H + 14));

        // "MAX" line should never overlap the qty label, even on small windows / large GUI scales.
        int maxLineY = Math.min(textY, qtyLabelY - 12);
        gfx.drawString(this.font,
                Component.translatable("screen.avilixeconomy.shop.max_value", Integer.toString(max)),
                textX, maxLineY, 0x9A9A9A, false);

        gfx.drawString(this.font, Component.translatable("screen.avilixeconomy.shop.qty"), textX, qtyLabelY, 0xFFFFFF, false);

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        // All labels are drawn in renderBg().
    }
}

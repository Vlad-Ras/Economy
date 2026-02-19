package com.roften.avilixeconomy.shop.screen.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.roften.avilixeconomy.network.NetworkRegistration;
import com.roften.avilixeconomy.shop.client.ShopItemRenderType;
import com.roften.avilixeconomy.shop.client.ShopRenderOverridesClient;
import com.roften.avilixeconomy.shop.menu.ShopConfigMenu;
import com.roften.avilixeconomy.shop.render.RenderOverrideScope;
import com.roften.avilixeconomy.shop.render.RenderTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Admin-only UI: tune shelf item transforms in real time.
 *
 * This screen uses a temporary "session" layer on the client.
 * Press Save to persist into DB (server-side).
 */
public class ShopRenderTunerScreen extends Screen {

    private final Screen parent;
    private final ShopConfigMenu menu;

    private final List<ItemStack> preview = new ArrayList<>(9);

    private int selectedSlot = 0;
    private RenderOverrideScope scope = RenderOverrideScope.ITEM;
    private ShopItemRenderType selectedType = ShopItemRenderType.NORMAL;

    private Step step = Step.NORMAL;
    private AxisEdit axis = AxisEdit.Y;
    private Mode mode = Mode.OFFSET;

    private Button showActiveSourceBtn;
    private Button clearSessionBtn;
    private Button saveBtn;
    private Button deleteBtn;
    private Button closeBtn;

    private Button minusBtn;
    private Button plusBtn;

    private CycleButton<RenderOverrideScope> scopeBtn;
    private CycleButton<ShopItemRenderType> typeBtn;
    private CycleButton<Step> stepBtn;
    private CycleButton<AxisEdit> axisBtn;
    private CycleButton<Mode> modeBtn;

    // Layout computed in init/render
    private int gridX;
    private int gridY;
    private int controlsX;
    private int panelX1, panelY1, panelX2, panelY2;

    public ShopRenderTunerScreen(Screen parent, ShopConfigMenu menu) {
        super(Component.translatable("gui.avilixeconomy.shop.render_tuner.title"));
        this.parent = parent;
        this.menu = menu;
    }

    @Override
    protected void init() {
        super.init();

        ShopRenderOverridesClient.beginSession();

        // Capture a static preview list from the current menu slots.
        this.preview.clear();
        for (int i = 0; i < 9; i++) {
            ItemStack st = ItemStack.EMPTY;
            try {
                st = menu.getTemplateInventory().getStackInSlot(i);
            } catch (Throwable ignored) {
            }
            this.preview.add(st == null ? ItemStack.EMPTY : st.copy());
        }
        // Find first non-empty
        for (int i = 0; i < preview.size(); i++) {
            if (!preview.get(i).isEmpty()) {
                selectedSlot = i;
                break;
            }
        }

        // Single-column layout: controls on top, shelf selector grid under the buttons.
        // IMPORTANT: keep the world + shop clearly visible behind by docking the panel to the RIGHT side.
        int w = 260;
        int margin = 14;
        this.controlsX = Math.max(margin, this.width - w - margin);
        int cx = this.controlsX + w / 2;

        int y = 24;

        this.scopeBtn = this.addRenderableWidget(CycleButton.<RenderOverrideScope>builder(s -> Component.literal(s.name()))
                .withValues(RenderOverrideScope.ITEM, RenderOverrideScope.TYPE)
                .withInitialValue(scope)
                .create(controlsX, y, w, 20, Component.translatable("gui.avilixeconomy.shop.render_tuner.scope"), (b, v) -> scope = v));
        y += 22;

        this.typeBtn = this.addRenderableWidget(CycleButton.<ShopItemRenderType>builder(t -> Component.literal(t.id()))
                .withValues(Arrays.asList(ShopItemRenderType.values()))
                .withInitialValue(selectedType)
                .create(controlsX, y, w, 20, Component.translatable("gui.avilixeconomy.shop.render_tuner.type"), (b, v) -> selectedType = v));
        y += 22;

        this.modeBtn = this.addRenderableWidget(CycleButton.builder(Mode::label)
                .withValues(Mode.values())
                .withInitialValue(mode)
                .create(controlsX, y, w, 20, Component.translatable("gui.avilixeconomy.shop.render_tuner.mode"), (b, v) -> mode = v));
        y += 22;

        this.axisBtn = this.addRenderableWidget(CycleButton.builder(AxisEdit::label)
                .withValues(AxisEdit.values())
                .withInitialValue(axis)
                .create(controlsX, y, w, 20, Component.translatable("gui.avilixeconomy.shop.render_tuner.axis"), (b, v) -> axis = v));
        y += 22;

        this.stepBtn = this.addRenderableWidget(CycleButton.builder(Step::label)
                .withValues(Step.values())
                .withInitialValue(step)
                .create(controlsX, y, w, 20, Component.translatable("gui.avilixeconomy.shop.render_tuner.step"), (b, v) -> step = v));
        y += 26;

        this.minusBtn = this.addRenderableWidget(Button.builder(Component.literal("-"), b -> adjust(-1)).bounds(controlsX, y, 60, 20).build());
        this.plusBtn = this.addRenderableWidget(Button.builder(Component.literal("+"), b -> adjust(+1)).bounds(controlsX + w - 60, y, 60, 20).build());
        y += 24;

        this.showActiveSourceBtn = this.addRenderableWidget(Button.builder(Component.translatable("gui.avilixeconomy.shop.render_tuner.show_active"), b -> showActiveSource()).bounds(controlsX, y, w, 20).build());
        y += 22;
        this.clearSessionBtn = this.addRenderableWidget(Button.builder(Component.translatable("gui.avilixeconomy.shop.render_tuner.clear_session"), b -> clearSession()).bounds(controlsX, y, w, 20).build());
        y += 26;

        this.saveBtn = this.addRenderableWidget(Button.builder(Component.translatable("gui.avilixeconomy.shop.render_tuner.save"), b -> saveCurrent()).bounds(controlsX, y, 108, 20).build());
        this.deleteBtn = this.addRenderableWidget(Button.builder(Component.translatable("gui.avilixeconomy.shop.render_tuner.delete"), b -> deleteCurrent()).bounds(controlsX + w - 108, y, 108, 20).build());
        y += 26;

        this.closeBtn = this.addRenderableWidget(Button.builder(Component.translatable("gui.avilixeconomy.shop.render_tuner.close"), b -> onClose()).bounds(controlsX, y, w, 20).build());

        // Shelf selector grid goes under the buttons.
        int gridW = 108;
        this.gridX = this.controlsX + (w - gridW) / 2;
        this.gridY = y + 34;

        // Panel bounds (small translucent panel only under UI; do NOT blur/dim the whole game)
        this.panelX1 = this.controlsX - 12;
        this.panelY1 = 12;
        this.panelX2 = this.controlsX + w + 12;
        this.panelY2 = this.gridY + 36 * 3 + 40;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ShopRenderOverridesClient.endSession();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private ItemStack selectedStack() {
        if (selectedSlot < 0 || selectedSlot >= preview.size()) return ItemStack.EMPTY;
        ItemStack st = preview.get(selectedSlot);
        return st == null ? ItemStack.EMPTY : st;
    }

    private String currentKey() {
        if (scope == RenderOverrideScope.TYPE) return selectedType.id();
        ItemStack st = selectedStack();
        if (st.isEmpty()) return "";
        return ShopRenderOverridesClient.itemKey(st);
    }

    private ShopItemRenderType detectedTypeForSelected() {
        ItemStack st = selectedStack();
        if (st.isEmpty()) return ShopItemRenderType.NORMAL;
        return ShopItemRenderType.detect(st);
    }

    private void adjust(int sign) {
        String key = currentKey();
        if (key.isBlank()) return;

        RenderTransform base = ShopRenderOverridesClient.getSession(scope, key);
        if (base == null) base = ShopRenderOverridesClient.getPersistent(scope, key);
        if (base == null) base = RenderTransform.IDENTITY;

        float posD = step.posDelta * sign;
        float rotD = step.rotDelta * sign;
        float scaleD = step.scaleDelta * sign;
        float liftD = step.liftDelta * sign;

        RenderTransform next = switch (mode) {
            case OFFSET -> base.withOff(
                    base.offX() + (axis == AxisEdit.X ? posD : 0),
                    base.offY() + (axis == AxisEdit.Y ? posD : 0),
                    base.offZ() + (axis == AxisEdit.Z ? posD : 0)
            );
            case ROTATION -> base.withRot(
                    base.rotX() + (axis == AxisEdit.X ? rotD : 0),
                    base.rotY() + (axis == AxisEdit.Y ? rotD : 0),
                    base.rotZ() + (axis == AxisEdit.Z ? rotD : 0)
            );
            case SCALE -> base.withScale(clamp(base.scaleMul() + scaleD, 0.05f, 3.0f));
            case LIFT -> base.withLift(clamp(base.extraLift() + liftD, -0.5f, 0.5f));
        };

        ShopRenderOverridesClient.setSession(scope, key, next);
    }

    private void showActiveSource() {
        ItemStack st = selectedStack();
        if (st.isEmpty()) {
            toast(Component.translatable("gui.avilixeconomy.shop.render_tuner.no_item"));
            return;
        }
        var detected = detectedTypeForSelected();
        var resolved = ShopRenderOverridesClient.resolve(st, detected);
        ShopRenderOverridesClient.ActiveSource src = resolved.source();

        String srcLabel = switch (src) {
            case SESSION_ITEM -> "SESSION:ITEM";
            case SESSION_TYPE -> "SESSION:TYPE";
            case ITEM -> "ITEM";
            case TYPE -> "TYPE";
            case DEFAULT -> "DEFAULT";
        };

        String key = currentKey();
        String shortK = shortKey(key, 32);

        // Actionbar: short + single line.
        Component action = Component.translatable("gui.avilixeconomy.shop.render_tuner.active_source_full", shortK, detected.id(), srcLabel);
        // Chat: full key on 2 lines for readability.
        Component chat = Component.translatable("gui.avilixeconomy.shop.render_tuner.info_key", key)
                .append(Component.literal("\n"))
                .append(Component.translatable("gui.avilixeconomy.shop.render_tuner.info_state", detected.id(), srcLabel));

        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(action, true);
            minecraft.player.displayClientMessage(chat, false);
        }
    }

    private void clearSession() {
        ShopRenderOverridesClient.clearSession();
        toast(Component.translatable("gui.avilixeconomy.shop.render_tuner.session_cleared"));
    }

    private void saveCurrent() {
        String key = currentKey();
        if (key.isBlank()) return;
        RenderTransform t = ShopRenderOverridesClient.getSession(scope, key);
        if (t == null) {
            toast(Component.translatable("gui.avilixeconomy.shop.render_tuner.nothing_to_save"));
            return;
        }
        // Send to server (DB)
        try {
            PacketDistributor.sendToServer(new NetworkRegistration.ShopRenderOverrideSetC2SPayload(scope, key, t));
            toast(Component.translatable("gui.avilixeconomy.shop.render_tuner.saved"));
        } catch (Throwable ignored) {
        }
    }

    private void deleteCurrent() {
        String key = currentKey();
        if (key.isBlank()) return;
        try {
            PacketDistributor.sendToServer(new NetworkRegistration.ShopRenderOverrideDeleteC2SPayload(scope, key));
            // Also clear session layer for this key
            ShopRenderOverridesClient.removeSession(scope, key);
            toast(Component.translatable("gui.avilixeconomy.shop.render_tuner.deleted"));
        } catch (Throwable ignored) {
        }
    }

    private void toast(Component msg) {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.displayClientMessage(msg, false);
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String trimToWidth(net.minecraft.client.gui.Font font, String s, int maxWidth) {
        if (s == null) return "";
        if (font.width(s) <= maxWidth) return s;

        final String dots = "...";
        int dotsW = font.width(dots);
        if (dotsW >= maxWidth) return dots;

        String cut = font.plainSubstrByWidth(s, Math.max(0, maxWidth - dotsW));
        return cut + dots;
    }

    private static String shortKey(String key, int maxLen) {
        if (key == null) return "null";
        if (key.length() <= maxLen) return key;

        int keep = Math.max(8, maxLen - 3);
        return "..." + key.substring(key.length() - keep);
    }

@Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // IMPORTANT: do NOT blur/darken the whole game.
        // Do NOT call super.render(...) because Screen#render will render the blurred/dim background.
        // We only draw a small translucent panel behind our widgets.

        // Panel background behind our UI.
        g.fill(panelX1, panelY1, panelX2, panelY2, 0x66000000);
        g.fill(panelX1, panelY1, panelX2, panelY1 + 1, 0xAAFFFFFF);
        g.fill(panelX1, panelY2 - 1, panelX2, panelY2, 0xAAFFFFFF);

        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 6, 0xFFFFFF);

        // simple 3x3 selector grid
        int gridX = this.gridX;
        int gridY = this.gridY;

        g.drawString(this.font, Component.translatable("gui.avilixeconomy.shop.render_tuner.shelf"), gridX, gridY - 12, 0xFFFFFF);
        for (int i = 0; i < 9; i++) {
            int gx = gridX + (i % 3) * 36;
            int gy = gridY + (i / 3) * 36;
            boolean sel = (i == selectedSlot);
            g.fill(gx, gy, gx + 34, gy + 34, sel ? 0xAAFFFFFF : 0x55000000);
            ItemStack st = preview.get(i);
            if (st != null && !st.isEmpty()) {
                g.renderItem(st, gx + 9, gy + 9);
            }
        }

        // Hint: selected item label
        ItemStack st = selectedStack();
        if (!st.isEmpty()) {
            g.drawString(this.font, st.getHoverName(), gridX, gridY + 112, 0xFFFFFF);
        } else {
            g.drawString(this.font, Component.translatable("gui.avilixeconomy.shop.render_tuner.no_item"), gridX, gridY + 112, 0xAAAAAA);
        }

        // footer info (avoid overlap with selected item label; split into 2 lines; trim long keys)
        String key = currentKey();
        ShopItemRenderType det = detectedTypeForSelected();
        var resolved = ShopRenderOverridesClient.resolve(st, det);
        String src = resolved.source().name();

        int infoY = gridY + 112 + this.font.lineHeight + 4; // below selected item label
        int infoX = this.controlsX;

        int maxW = (this.panelX2 - this.panelX1) - 24;
        String keyLine = trimToWidth(this.font, Component.translatable("gui.avilixeconomy.shop.render_tuner.info_key", key).getString(), maxW);
        Component stateLine = Component.translatable("gui.avilixeconomy.shop.render_tuner.info_state", det.id(), src);

        g.drawString(this.font, keyLine, infoX, infoY, 0xCCCCCC, false);
        infoY += this.font.lineHeight + 2;
        g.drawString(this.font, stateLine, infoX, infoY, 0xCCCCCC, false);
// Render widgets manually (skip Screen background rendering).
        for (var r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int gridX = this.gridX;
        int gridY = this.gridY;
        for (int i = 0; i < 9; i++) {
            int gx = gridX + (i % 3) * 36;
            int gy = gridY + (i / 3) * 36;
            if (mouseX >= gx && mouseX <= gx + 34 && mouseY >= gy && mouseY <= gy + 34) {
                this.selectedSlot = i;
                // auto-sync selected type from detected
                this.selectedType = detectedTypeForSelected();
                if (this.typeBtn != null) {
                    this.typeBtn.setValue(this.selectedType);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private enum Step {
        FINE(0.0025f, 1.0f, 0.01f, 0.0025f, Component.translatable("gui.avilixeconomy.shop.render_tuner.step.fine")),
        NORMAL(0.01f, 5.0f, 0.05f, 0.01f, Component.translatable("gui.avilixeconomy.shop.render_tuner.step.normal")),
        COARSE(0.05f, 15.0f, 0.10f, 0.05f, Component.translatable("gui.avilixeconomy.shop.render_tuner.step.coarse"));

        final float posDelta;
        final float rotDelta;
        final float scaleDelta;
        final float liftDelta;
        final Component label;

        Step(float posDelta, float rotDelta, float scaleDelta, float liftDelta, Component label) {
            this.posDelta = posDelta;
            this.rotDelta = rotDelta;
            this.scaleDelta = scaleDelta;
            this.liftDelta = liftDelta;
            this.label = label;
        }
        Component label() { return label; }
    }

    private enum AxisEdit {
        X(Component.literal("X")),
        Y(Component.literal("Y")),
        Z(Component.literal("Z"));
        final Component label;
        AxisEdit(Component label) { this.label = label; }
        Component label() { return label; }
    }

    private enum Mode {
        OFFSET(Component.translatable("gui.avilixeconomy.shop.render_tuner.mode.offset")),
        ROTATION(Component.translatable("gui.avilixeconomy.shop.render_tuner.mode.rotation")),
        SCALE(Component.translatable("gui.avilixeconomy.shop.render_tuner.mode.scale")),
        LIFT(Component.translatable("gui.avilixeconomy.shop.render_tuner.mode.lift"));

        final Component label;
        Mode(Component label) { this.label = label; }
        Component label() { return label; }
    }
}

package com.roften.avilixeconomy.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Base screen that renders its UI in a fixed "UI coordinate space" (baseWidth/baseHeight)
 * and scales/translates it to fit any window size and any Minecraft GUI Scale.
 *
 * This is intentionally minimal and matches the template you provided.
 */
public abstract class ResponsiveContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    protected float uiScale = 1.0f;
    protected int uiLeft = 0;
    protected int uiTop = 0;

    private boolean inScaledPass = false;
    private boolean suppressBackground = false;
    private boolean suppressTooltip = false;

    protected ResponsiveContainerScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    /** Базовые “логические” размеры твоего интерфейса (в UI-пикселях). */
    protected abstract int baseWidth();

    /** Базовые “логические” размеры твоего интерфейса (в UI-пикселях). */
    protected abstract int baseHeight();

    /** Тут ты рисуешь фон/рамки/панели/сетки — в UI-координатах (0..baseWidth/baseHeight). */
    protected abstract void drawChrome(GuiGraphics gfx, float partialTick, int uiMouseX, int uiMouseY);

    /** Тут ты раскладываешь кнопки/поля ввода — в UI-координатах. Вызывай при init и при resize. */
    protected abstract void layoutWidgets();

    /**
     * Some overlays (e.g. JEI) can occupy the right side of the screen.
     * Subclasses can reserve pixels so the UI is scaled/positioned to avoid overlap.
     */
    protected int reservedRightPixels() {
        return 0;
    }

    protected void updateUiTransform() {
        // gui size (учитывает Gui Scale)
        int gw = this.minecraft.getWindow().getGuiScaledWidth();
        int gh = this.minecraft.getWindow().getGuiScaledHeight();

        int reservedRight = Math.max(0, reservedRightPixels());
        int gwAvailable = Math.max(0, gw - reservedRight);

        float sx = (float) gwAvailable / (float) baseWidth();
        float sy = (float) gh / (float) baseHeight();
        uiScale = Math.min(1.0f, Math.min(sx, sy)); // не увеличиваем больше 1.0
        uiScale = Math.max(0.55f, uiScale);        // нижний предел (чтобы не слипалось)

        int scaledW = Math.round(baseWidth() * uiScale);
        int scaledH = Math.round(baseHeight() * uiScale);

        // Center within the available area (excluding reserved right overlay).
        uiLeft = (gwAvailable - scaledW) / 2;
        uiTop = (gh - scaledH) / 2;

        // ВАЖНО: leftPos/topPos в UI-координатах — обычно 0, если ты рисуешь через translate+scale
        this.leftPos = 0;
        this.topPos = 0;

        // Для vanilla-логики размеров:
        this.imageWidth = baseWidth();
        this.imageHeight = baseHeight();
    }

    @Override
    protected void init() {
        super.init();
        updateUiTransform();
        layoutWidgets();
    }

    @Override
    public void resize(Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        updateUiTransform();
        layoutWidgets();
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        updateUiTransform();

        // Полупрозрачная подложка на весь экран — чтобы HUD/scoreboard не "лез" в интерфейс.
        // (Потом поверх будет нарисован сам UI).
        gfx.fill(0, 0, this.width, this.height, 0xAA000000);

        // 1) фон в реальных координатах (полный экран)
        suppressBackground = false;
        this.renderBackground(gfx, mouseX, mouseY, partialTick);

        // 2) перевод мыши в UI-координаты
        int uiMouseX = (int) ((mouseX - uiLeft) / uiScale);
        int uiMouseY = (int) ((mouseY - uiTop) / uiScale);

        suppressBackground = true;
        suppressTooltip = true;

        // 3) UI-pass (всё рисуем в UI-координатах)
        gfx.pose().pushPose();
        gfx.pose().translate(uiLeft, uiTop, 0);
        gfx.pose().scale(uiScale, uiScale, 1.0f);

        inScaledPass = true;

        // рисуем “хром” сами (панели/рамки/сетки)
        drawChrome(gfx, partialTick, uiMouseX, uiMouseY);

        // рисуем слоты/айтемы/виджеты
        super.render(gfx, uiMouseX, uiMouseY, partialTick);

        inScaledPass = false;
        gfx.pose().popPose();

        suppressBackground = false;
        suppressTooltip = false;

        // tooltips в реальных координатах
        super.renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        if (inScaledPass || suppressBackground) return;
        super.renderBackground(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        if (suppressTooltip) return;
        super.renderTooltip(gfx, mouseX, mouseY);
    }

    // мышь/колёсико — переводим в UI-координаты
    @Override
    public boolean mouseClicked(double x, double y, int b) {
        updateUiTransform();
        return super.mouseClicked((x - uiLeft) / uiScale, (y - uiTop) / uiScale, b);
    }

    @Override
    public boolean mouseReleased(double x, double y, int b) {
        updateUiTransform();
        return super.mouseReleased((x - uiLeft) / uiScale, (y - uiTop) / uiScale, b);
    }

    @Override
    public boolean mouseDragged(double x, double y, int b, double dx, double dy) {
        updateUiTransform();
        return super.mouseDragged((x - uiLeft) / uiScale, (y - uiTop) / uiScale, b, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        updateUiTransform();
        return super.mouseScrolled((x - uiLeft) / uiScale, (y - uiTop) / uiScale, dx, dy);
    }
}

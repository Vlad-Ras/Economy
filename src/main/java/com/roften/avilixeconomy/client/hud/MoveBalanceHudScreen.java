package com.roften.avilixeconomy.client.hud;

import com.roften.avilixeconomy.client.ClientBalanceData;
import com.roften.avilixeconomy.client.HudPositionConfig;
import com.roften.avilixeconomy.util.MoneyUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Экран для перетаскивания HUD баланса.
 * Открывается по горячей клавише.
 */
public class MoveBalanceHudScreen extends Screen {

    private int hudX;
    private int hudY;
    private boolean dragging = false;
    private int dragOffX;
    private int dragOffY;

    public MoveBalanceHudScreen() {
        super(Component.literal("HUD"));
        this.hudX = HudPositionConfig.getX();
        this.hudY = HudPositionConfig.getY();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        HudPositionConfig.set(hudX, hudY);
        HudPositionConfig.save();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Важно: в 1.21+ базовая реализация Screen может включать пост-эффекты (в т.ч. blur)
        // в процессе super.render(...). Если вызвать super.render() в конце, он может
        // применить эффект поверх уже отрисованного HUD/текста.
        // Поэтому сначала даём ваниле отрисоваться, а затем рисуем наш HUD поверх.
        super.render(g, mouseX, mouseY, partialTick);

        Minecraft mc = Minecraft.getInstance();

        // В 1.21+ renderBackground() может включать blur-проход, из-за чего HUD становится "замыленным".
        // Поэтому рисуем затемнение сами, без блюра.
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        g.fill(0, 0, sw, sh, 0xAA000000);
        Font font = mc.font;

        String text = "Баланс: " + MoneyUtils.formatSmart(ClientBalanceData.getBalance());
        int w = font.width(text);
        int h = font.lineHeight;

        // clamp
        if (hudX < 0) hudX = 0;
        if (hudY < 0) hudY = 0;
        if (hudX > sw - w) hudX = Math.max(0, sw - w);
        if (hudY > sh - h) hudY = Math.max(0, sh - h);

        // подсказки
        g.drawCenteredString(font, "Перетащи баланс мышью", sw / 2, 18, 0xFFFFFF);
        g.drawCenteredString(font, "R — сброс, ESC — выйти", sw / 2, 18 + font.lineHeight + 2, 0xFFFFFF);

        // рамка + текст
        int pad = 3;
        g.fill(hudX - pad, hudY - pad, hudX + w + pad, hudY + h + pad, 0x66000000);
        // Текст без shadow — так он не "мылится" на некоторых рендерах.
        g.drawString(font, text, hudX, hudY, 0xFFFFFF, false);

        // super.render(...) уже был вызван в начале, чтобы избежать "замыливания" текста.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        String text = "Баланс: " + MoneyUtils.formatSmart(ClientBalanceData.getBalance());
        int w = font.width(text);
        int h = font.lineHeight;

        if (mouseX >= hudX && mouseX <= hudX + w && mouseY >= hudY && mouseY <= hudY + h) {
            dragging = true;
            dragOffX = (int) mouseX - hudX;
            dragOffY = (int) mouseY - hudY;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            hudX = (int) mouseX - dragOffX;
            hudY = (int) mouseY - dragOffY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // R = reset
        if (keyCode == InputConstants.KEY_R) {
            HudPositionConfig.resetDefaults();
            hudX = HudPositionConfig.getX();
            hudY = HudPositionConfig.getY();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

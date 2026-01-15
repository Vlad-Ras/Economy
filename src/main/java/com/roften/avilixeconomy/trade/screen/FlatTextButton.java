package com.roften.avilixeconomy.trade.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Button without vanilla background: renders only text (centered) with hover highlight.
 * Also trims long text with ellipsis.
 */
public class FlatTextButton extends Button {

    public FlatTextButton(int x, int y, int w, int h, Component message, OnPress onPress) {
        super(x, y, w, h, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        int color;
        if (!this.active) color = 0xFF6F6F6F;
        else if (this.isHoveredOrFocused()) color = 0xFFFFFFFF;
        else color = 0xFFE0E0E0;

        // underline on hover
        if (this.isHoveredOrFocused() && this.active) {
            gfx.hLine(this.getX() + 2, this.getX() + this.getWidth() - 3, this.getY() + this.getHeight() - 2, 0x66FFFFFF);
        }

        String raw = this.getMessage().getString();
        int maxTextW = Math.max(0, this.getWidth() - 6);
        String txt = raw;
        if (font.width(raw) > maxTextW) {
            String cut = font.plainSubstrByWidth(raw, Math.max(0, maxTextW - font.width("…")));
            txt = cut + "…";
        }

        int x = this.getX() + (this.getWidth() - font.width(txt)) / 2;
        int y = this.getY() + (this.getHeight() - 8) / 2;

        gfx.drawString(font, txt, x, y, color, false);
    }
}

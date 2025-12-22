package com.roften.avilixeconomy.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Shared painter for shop screens.
 *
 * Pure-geometry (no textures):
 * - works with any resource pack
 * - stable at any GUI scale and window size
 */
public final class ShopUi {

    private ShopUi() {
    }

    public static void drawDropShadow(GuiGraphics gfx, int x, int y, int w, int h) {
        // Simple layered shadow for depth (works well at any GUI scale).
        gfx.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x55000000);
        gfx.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x33000000);
        gfx.fill(x + 1, y + 1, x + w + 1, y + h + 1, 0x22000000);
    }

    public static void drawWindowFrame(GuiGraphics gfx, int x, int y, int w, int h) {
        // Main body (subtle vertical gradient).
        gfx.fillGradient(x, y, x + w, y + h, 0xFF1A1A1A, 0xFF111111);

        // Outer border (light top/left, dark bottom/right).
        int light = 0xFF707070;
        int dark = 0xFF0A0A0A;
        gfx.hLine(x, x + w - 1, y, light);
        gfx.vLine(x, y, y + h - 1, light);
        gfx.hLine(x, x + w - 1, y + h - 1, dark);
        gfx.vLine(x + w - 1, y, y + h - 1, dark);

        // Inner border (soft highlight).
        int inner = 0xFF2B2B2B;
        gfx.hLine(x + 1, x + w - 2, y + 1, inner);
        gfx.vLine(x + 1, y + 1, y + h - 2, inner);
        gfx.hLine(x + 1, x + w - 2, y + h - 2, 0xFF0F0F0F);
        gfx.vLine(x + w - 2, y + 1, y + h - 2, 0xFF0F0F0F);

        // Tiny inner highlight for a slightly "vanilla-like" sheen.
        gfx.hLine(x + 2, x + w - 3, y + 2, 0x22FFFFFF);
        gfx.vLine(x + 2, y + 2, y + h - 3, 0x22FFFFFF);
    }

    public static void drawTitleBar(GuiGraphics gfx, int x, int y, int w, int barH) {
        // Title bar gradient and separator line.
        gfx.fillGradient(x + 1, y + 1, x + w - 1, y + barH, 0xFF2A2A2A, 0xFF1D1D1D);
        gfx.hLine(x + 1, x + w - 2, y + barH, 0xFF0C0C0C);
        gfx.hLine(x + 1, x + w - 2, y + 2, 0x22FFFFFF);
    }

    public static void drawPanel(GuiGraphics gfx, int x, int y, int w, int h) {
        gfx.fill(x, y, x + w, y + h, 0xFF1C1C1C);
        int light = 0xFF3A3A3A;
        int dark = 0xFF0E0E0E;
        gfx.hLine(x, x + w - 1, y, light);
        gfx.hLine(x, x + w - 1, y + h - 1, dark);
        gfx.vLine(x, y, y + h - 1, light);
        gfx.vLine(x + w - 1, y, y + h - 1, dark);
        // subtle inner highlight
        gfx.hLine(x + 1, x + w - 2, y + 1, 0x22FFFFFF);
        gfx.vLine(x + 1, y + 1, y + h - 2, 0x22FFFFFF);
    }

    public static void drawSubHeader(GuiGraphics gfx, Font font, int x, int y, int w, int h, Component title) {
        // Minimal header: no background behind text (requested).
        // More padding and a slightly higher baseline so the label never touches slot borders.
        int tx = x + 10;
        int ty = y + 1;
        gfx.drawString(font, title, tx, ty, 0xFFFFFF, false);
    }

    public static void drawSlotGrid(GuiGraphics gfx, int x, int y, int cols, int rows) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                drawSlot(gfx, x + c * 18, y + r * 18);
            }
        }
    }

    public static void drawSlot(GuiGraphics gfx, int x, int y) {
        gfx.fill(x, y, x + 18, y + 18, 0xFF242424);
        int light = 0xFF5A5A5A;
        int dark = 0xFF171717;
        gfx.hLine(x, x + 17, y, light);
        gfx.vLine(x, y, y + 17, light);
        gfx.hLine(x, x + 17, y + 17, dark);
        gfx.vLine(x + 17, y, y + 17, dark);
        gfx.hLine(x + 1, x + 16, y + 1, 0x22FFFFFF);
        gfx.vLine(x + 1, y + 1, y + 16, 0x22FFFFFF);
    }

    public static void drawTab(GuiGraphics gfx, Font font, int x, int y, int w, int h, Component title, boolean active) {
        int top = active ? 0xFF3A3A3A : 0xFF252525;
        int bot = active ? 0xFF2A2A2A : 0xFF1A1A1A;
        gfx.fillGradient(x, y, x + w, y + h, top, bot);

        int light = active ? 0xFF6A6A6A : 0xFF444444;
        int dark = 0xFF0F0F0F;
        gfx.hLine(x, x + w - 1, y, light);
        gfx.vLine(x, y, y + h - 1, light);
        gfx.hLine(x, x + w - 1, y + h - 1, dark);
        gfx.vLine(x + w - 1, y, y + h - 1, dark);

        int tx = x + (w - font.width(title)) / 2;
        int ty = y + (h - 8) / 2;
        int color = active ? 0xFFFFFF : 0xCFCFCF;
        gfx.drawString(font, title, tx, ty, color, false);
    }
}

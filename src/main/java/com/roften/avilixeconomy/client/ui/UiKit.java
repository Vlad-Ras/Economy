package com.roften.avilixeconomy.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Small UI helper toolkit.
 *
 * Design goals:
 * - No texture dependencies (works with any resource pack)
 * - High-contrast and readable
 * - Deterministic rendering at any Minecraft GUI scale and any window size
 */
public final class UiKit {

    /**
     * Bump this string whenever you want to verify the client is running the latest GUI code.
     * It is rendered on-screen by shop screens and also logged once on screen init.
     */
    public static final String GUI_BUILD_STAMP = "AvilixEconomy GUI BUILD render-tuner 2026-02-02";

    private UiKit() {
    }

    /**
     * Fullscreen backdrop for container-like screens.
     * Draw in real screen coordinates (0..width/height).
     */
    public static void drawBackdrop(GuiGraphics gfx, int width, int height) {
        // Neutral dark gradient (no blue tint).
        gfx.fillGradient(0, 0, width, height, 0xFF101010, 0xFF050505);

        // Soft vignette (edge darkening). Cheap and visible.
        int edge = Math.min(36, Math.min(width, height) / 6);
        int a1 = 0x3A000000;
        int a2 = 0x24000000;
        int a3 = 0x16000000;

        // top
        gfx.fill(0, 0, width, edge, a1);
        gfx.fill(0, edge, width, edge * 2, a2);
        gfx.fill(0, edge * 2, width, edge * 3, a3);
        // bottom
        gfx.fill(0, height - edge, width, height, a1);
        gfx.fill(0, height - edge * 2, width, height - edge, a2);
        gfx.fill(0, height - edge * 3, width, height - edge * 2, a3);
        // left
        gfx.fill(0, 0, edge, height, a1);
        gfx.fill(edge, 0, edge * 2, height, a2);
        gfx.fill(edge * 2, 0, edge * 3, height, a3);
        // right
        gfx.fill(width - edge, 0, width, height, a1);
        gfx.fill(width - edge * 2, 0, width - edge, height, a2);
        gfx.fill(width - edge * 3, 0, width - edge * 2, height, a3);

        // Subtle center lift, so the window reads as "separate" on dark monitors.
        int cx1 = width / 2 - width / 6;
        int cx2 = width / 2 + width / 6;
        int cy1 = height / 2 - height / 6;
        int cy2 = height / 2 + height / 6;
        gfx.fillGradient(cx1, cy1, cx2, cy2, 0x12000000, 0x00000000);
    }
}

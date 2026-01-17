package com.roften.avilixeconomy.compat;

import net.neoforged.fml.ModList;

/**
 * Client-side compatibility helpers for JEI.
 *
 * We intentionally do not compile against JEI API (no hard dependency). This class only checks
 * whether JEI is loaded and provides a conservative "reserved width" so our GUIs can avoid
 * overlapping JEI's right-side item list.
 */
public final class JeiCompat {

    private JeiCompat() {
    }

    public static boolean isJeiLoaded() {
        return ModList.get().isLoaded("jei");
    }

    /**
     * JEI's overlay width depends on config and UI scale. We use a conservative default.
     * If JEI is not present, returns 0.
     */
    public static int reservedRightPixels() {
        // IMPORTANT:
        // We do NOT shift our screens to the left anymore.
        // Proper JEI handling is done via a JEI plugin (extra GUI areas), so JEI moves instead of our UI.
        return 0;
    }
}

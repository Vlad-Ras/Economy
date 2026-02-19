package com.roften.avilixeconomy.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Utilities for handling money values with 2 decimal digits.
 * Internal representation in the codebase uses double, but every persisted / displayed value
 * should be rounded to 2 digits.
 */
public final class MoneyUtils {
    private MoneyUtils() {}

    /**
     * Currency name used across the mod.
     * Requirements: currency is always called "Нокс".
     */
    public static final String CURRENCY_SINGULAR = "Нокс";
    public static final String CURRENCY_GEN_SINGULAR = "Нокса";
    public static final String CURRENCY_GEN_PLURAL = "Ноксов";

    public static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** Formats money up to 2 decimals, without trailing zeros: 100 -> "100", 100.5 -> "100.5", 100.25 -> "100.25". */
    public static String formatSmart(double v) {
        BigDecimal bd = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        // Avoid "-0"
        if (bd.compareTo(BigDecimal.ZERO) == 0) return "0";
        return bd.toPlainString();
    }

    /**
     * Formats amount with currency suffix, e.g. "100 Ноксов".
     * Uses basic Russian declension for integer values; for decimals falls back to "Нокс".
     */
    public static String formatNoks(double v) {
        return formatSmart(v) + " " + currencyWord(v);
    }

    /** Same as {@link #formatNoks(double)} but ensures a + sign for positive values. */
    public static String formatNoksSigned(double v) {
        String sign = v > 0 ? "+" : "";
        return sign + formatNoks(v);
    }

    /**
     * Returns a currency word for the given amount.
     * 1 -> Нокс, 2-4 -> Нокса, 5+ -> Ноксов. Decimals -> Нокс.
     */
    public static String currencyWord(double v) {
        double av = Math.abs(v);
        // If not an integer (within epsilon), avoid tricky declension.
        // We intentionally use a slightly larger epsilon to tolerate typical double noise
        // (e.g. 128.0000000001) coming from calculations and DB round-trips.
        if (Math.abs(av - Math.rint(av)) > 1e-6) {
            return CURRENCY_SINGULAR;
        }
        long n = (long) Math.abs(Math.round(av));
        long mod10 = n % 10;
        long mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) return CURRENCY_SINGULAR;
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return CURRENCY_GEN_SINGULAR;
        return CURRENCY_GEN_PLURAL;
    }

    /** Parse user input like "1.25" or "1,25" into a rounded value (2 decimals). */
    public static double parseSmart(String s) {
        if (s == null) return 0.0;
        s = s.trim();
        if (s.isEmpty()) return 0.0;
        // Allow comma as decimal separator
        s = s.replace(',', '.');
        // keep only digits and single dot
        StringBuilder out = new StringBuilder();
        boolean dot = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') {
                out.append(ch);
            } else if (ch == '.' && !dot) {
                out.append('.');
                dot = true;
            }
        }
        if (out.length() == 0) return 0.0;
        try {
            return round2(Double.parseDouble(out.toString()));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static BigDecimal toDb(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    public static double fromDb(BigDecimal bd) {
        if (bd == null) return 0.0;
        return bd.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}

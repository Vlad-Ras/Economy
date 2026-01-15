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

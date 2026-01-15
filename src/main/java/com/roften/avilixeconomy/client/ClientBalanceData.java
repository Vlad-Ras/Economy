package com.roften.avilixeconomy.client;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Простой клиентский кеш: HUD читает значение отсюда.
 * Обновляется обработчиком payload'а.
 *
 * Храним double в AtomicLong через raw long bits (без бокса).
 */
public final class ClientBalanceData {
    private static final AtomicLong BAL_BITS = new AtomicLong(Double.doubleToRawLongBits(0.0));

    private ClientBalanceData() {}

    public static double getBalance() {
        return Double.longBitsToDouble(BAL_BITS.get());
    }

    public static void setBalance(double value) {
        BAL_BITS.set(Double.doubleToRawLongBits(value));
    }
}

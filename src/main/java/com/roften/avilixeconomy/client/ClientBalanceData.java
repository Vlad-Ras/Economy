package com.roften.avilixeconomy.client;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Простой клиентский кеш: HUD читает значение отсюда.
 * Обновляется обработчиком payload'а.
 */
public final class ClientBalanceData {
    private static final AtomicLong BAL = new AtomicLong(0L);

    private ClientBalanceData() {}

    public static long getBalance() {
        return BAL.get();
    }

    public static void setBalance(long value) {
        BAL.set(value);
    }
}

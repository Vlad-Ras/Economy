package com.roften.avilixeconomy.trade.screen;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Небольшое клиентское хранилище состояния трейда.
 * Нужно, чтобы при открытии GUI оно сразу могло отрисовать текущее состояние,
 * даже если пакет уже пришёл до открытия экрана.
 */
public record TradeClientState(
        int sessionId,
        String leftName,
        String rightName,
        long leftMoney,
        long rightMoney,
        boolean leftReady,
        boolean rightReady
) {

    private static final Map<Integer, TradeClientState> STATES = new ConcurrentHashMap<>();

    public static void put(int sessionId, TradeClientState state) {
        if (state == null) {
            STATES.remove(sessionId);
        } else {
            STATES.put(sessionId, state);
        }
    }

    public static TradeClientState get(int sessionId) {
        return STATES.get(sessionId);
    }
}

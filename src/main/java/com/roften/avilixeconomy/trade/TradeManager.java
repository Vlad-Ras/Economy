package com.roften.avilixeconomy.trade;

import com.roften.avilixeconomy.AvilixEconomy;
import com.roften.avilixeconomy.trade.menu.TradeMenu;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Центральный менеджер трейдов: запросы + активные сессии.
 */
public final class TradeManager {

    private TradeManager() {}

    private static final long REQUEST_TTL_MS = 60_000L;

    private static final AtomicInteger NEXT_SESSION_ID = new AtomicInteger(1);

    // targetUuid -> request
    private static final Map<UUID, TradeRequest> INCOMING = new ConcurrentHashMap<>();

    // sessionId -> session
    private static final Map<Integer, TradeSession> SESSIONS = new ConcurrentHashMap<>();

    // playerUuid -> sessionId
    private static final Map<UUID, Integer> ACTIVE_BY_PLAYER = new ConcurrentHashMap<>();

    public static void sendRequest(ServerPlayer from, ServerPlayer to) {
        if (from == null || to == null) return;
        if (from.getUUID().equals(to.getUUID())) {
            from.sendSystemMessage(Component.literal("Нельзя трейдиться с самим собой."));
            return;
        }

        if (isInTrade(from) || isInTrade(to)) {
            from.sendSystemMessage(Component.literal("Один из игроков уже находится в трейде."));
            return;
        }

        // очистка протухшего запроса
        cleanupExpired(to.getUUID());

        INCOMING.put(to.getUUID(), new TradeRequest(from.getUUID(), System.currentTimeMillis()));

        from.sendSystemMessage(Component.literal("Запрос на трейд отправлен игроку ").append(to.getName()));

        MutableComponent base = Component.literal("Игрок ")
                .append(from.getName())
                .append(Component.literal(" хочет обмен. "));

        MutableComponent accept = Component.literal("[ПРИНЯТЬ]")
                .withStyle(s -> s.withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/trade accept " + from.getName().getString())));

        MutableComponent deny = Component.literal("[ОТКЛОНИТЬ]")
                .withStyle(s -> s.withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/trade deny " + from.getName().getString())));

        to.sendSystemMessage(base.append(accept).append(Component.literal(" ")).append(deny));
    }

    public static void acceptRequest(ServerPlayer receiver, ServerPlayer sender) {
        if (receiver == null || sender == null) return;

        cleanupExpired(receiver.getUUID());

        TradeRequest req = INCOMING.get(receiver.getUUID());
        if (req == null || !req.fromUuid().equals(sender.getUUID())) {
            receiver.sendSystemMessage(Component.literal("Нет активного запроса на трейд от этого игрока."));
            return;
        }
        INCOMING.remove(receiver.getUUID());

        if (isInTrade(receiver) || isInTrade(sender)) {
            receiver.sendSystemMessage(Component.literal("Один из игроков уже находится в трейде."));
            sender.sendSystemMessage(Component.literal("Один из игроков уже находится в трейде."));
            return;
        }

        // Ограничение на дистанцию открытия трейда
        if (sender.level() != receiver.level()
                || sender.distanceToSqr(receiver) > TradeMenu.MAX_TRADE_DISTANCE * TradeMenu.MAX_TRADE_DISTANCE) {
            receiver.sendSystemMessage(Component.literal("Слишком далеко для трейда (нужно ≤ ")
                    .append(Component.literal(Integer.toString((int) TradeMenu.MAX_TRADE_DISTANCE)))
                    .append(Component.literal(" блоков).")));
            sender.sendSystemMessage(Component.literal("Слишком далеко для трейда (нужно ≤ ")
                    .append(Component.literal(Integer.toString((int) TradeMenu.MAX_TRADE_DISTANCE)))
                    .append(Component.literal(" блоков).")));
            return;
        }

        int sessionId = NEXT_SESSION_ID.getAndIncrement();
        TradeSession session = new TradeSession(sessionId, sender, receiver);
        SESSIONS.put(sessionId, session);
        ACTIVE_BY_PLAYER.put(sender.getUUID(), sessionId);
        ACTIVE_BY_PLAYER.put(receiver.getUUID(), sessionId);

        // Открываем GUI обоим игрокам (NeoForge patched ServerPlayer.openMenu supports extra data)
        sender.openMenu(session.createMenuProvider(TradeMenu.Side.LEFT), buf -> {
            buf.writeInt(sessionId);
            buf.writeInt(TradeMenu.Side.LEFT.id());
        });
        receiver.openMenu(session.createMenuProvider(TradeMenu.Side.RIGHT), buf -> {
            buf.writeInt(sessionId);
            buf.writeInt(TradeMenu.Side.RIGHT.id());
        });

        session.broadcastState();
    }

    public static void denyRequest(ServerPlayer receiver, ServerPlayer sender) {
        if (receiver == null || sender == null) return;

        cleanupExpired(receiver.getUUID());

        TradeRequest req = INCOMING.get(receiver.getUUID());
        if (req == null || !req.fromUuid().equals(sender.getUUID())) {
            receiver.sendSystemMessage(Component.literal("Нет активного запроса на трейд от этого игрока."));
            return;
        }
        INCOMING.remove(receiver.getUUID());

        receiver.sendSystemMessage(Component.literal("Вы отклонили трейд от ").append(sender.getName()));
        sender.sendSystemMessage(Component.literal("Игрок ").append(receiver.getName())
                .append(Component.literal(" отклонил ваш трейд.")));
    }

    public static void cancelActiveTrade(ServerPlayer player) {
        if (player == null) return;
        TradeSession session = getActiveSession(player);
        if (session == null) {
            player.sendSystemMessage(Component.literal("У вас нет активного трейда."));
            return;
        }
        session.cancel("Трейд отменён.");
    }

    public static TradeSession getSession(int sessionId) {
        return SESSIONS.get(sessionId);
    }

    public static TradeSession getActiveSession(ServerPlayer player) {
        Integer id = ACTIVE_BY_PLAYER.get(player.getUUID());
        return id == null ? null : SESSIONS.get(id);
    }

    public static boolean isInTrade(ServerPlayer player) {
        return player != null && ACTIVE_BY_PLAYER.containsKey(player.getUUID());
    }

    public static void onSessionEnded(TradeSession session) {
        if (session == null) return;
        SESSIONS.remove(session.sessionId());
        ACTIVE_BY_PLAYER.remove(session.left().getUUID());
        ACTIVE_BY_PLAYER.remove(session.right().getUUID());
        AvilixEconomy.LOGGER.info("[Trade] Session {} ended", session.sessionId());
    }

    private static void cleanupExpired(UUID targetUuid) {
        TradeRequest req = INCOMING.get(targetUuid);
        if (req == null) return;
        if (System.currentTimeMillis() - req.createdAtMs() > REQUEST_TTL_MS) {
            INCOMING.remove(targetUuid);
        }
    }

    public record TradeRequest(UUID fromUuid, long createdAtMs) {}
}

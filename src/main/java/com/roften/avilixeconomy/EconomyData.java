package com.roften.avilixeconomy;

import com.roften.avilixeconomy.database.DatabaseManager;
import com.roften.avilixeconomy.network.NetworkUtils;
import com.roften.avilixeconomy.util.MoneyUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Основной класс для работы с балансами игроков.
 * Обеспечивает:
 *  - безопасный SQL
 *  - кэширование
 *  - атомарные операции
 *  - асинхронные версии
 */
public final class EconomyData {

    private EconomyData() {}

    // Кэш балансов, чтобы HUD не долбил SQL 20 раз в секунду
    private static final ConcurrentHashMap<UUID, Double> cache = new ConcurrentHashMap<>();

    /** Возвращает значение из кэша (для HUD). */
    public static double getCachedBalance(UUID uuid) {
        return cache.getOrDefault(uuid, 0.0);
    }

    /** True if the UUID already has a cached balance (to avoid hitting SQL every tick). */
    public static boolean isCached(UUID uuid) {
        return cache.containsKey(uuid);
    }

    /** Warm up cache once (calls DB only if missing). */
    public static void warmupBalance(UUID uuid) {
        if (!cache.containsKey(uuid)) {
            getBalance(uuid);
        }
    }

    /** Force-send balance to a specific online player using the current cached value. */
    public static void sendBalanceUpdateToPlayer(ServerPlayer player) {
        if (player == null) return;
        try {
            double bal = cache.getOrDefault(player.getUUID(), 0.0);
            NetworkUtils.sendBalanceToPlayer(player, bal);
        } catch (Exception ignored) {}
    }

    /** Прямое чтение из БД (без создания записи). */
    public static double getBalance(UUID uuid) {
        try {
            double bal = DatabaseManager.getBalanceDirect(uuid);
            cache.put(uuid, bal);
            return bal;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** Создание записи игрока, только если её не существует. */
    public static boolean createIfMissing(UUID uuid, String name, double startBalance) {
        try {
            if (DatabaseManager.recordExists(uuid)) {
                DatabaseManager.updatePlayerName(uuid, name);
                return false;
            } else {
                DatabaseManager.createPlayerRecord(uuid, name, startBalance);
                cache.put(uuid, startBalance);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Установка баланса (INSERT OR UPDATE). */
    public static boolean setBalance(UUID uuid, double amount, String nameIfNew) {
        return setBalance(uuid, amount, nameIfNew, "SET", null, null, null);
    }

    /** Установка баланса с логированием в историю. */
    public static boolean setBalance(UUID uuid, double amount, String nameIfNew,
                                     String reason, UUID actorUuid, String actorName, String metaJson) {
        if (uuid == null) return false;
        double target = MoneyUtils.round2(amount);
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);

            double before = 0.0;
            String knownName = null;
            boolean exists;

            try (PreparedStatement sel = c.prepareStatement(
                    "SELECT name, balance FROM economy WHERE uuid = ? FOR UPDATE"
            )) {
                sel.setString(1, uuid.toString());
                try (ResultSet rs = sel.executeQuery()) {
                    exists = rs.next();
                    if (exists) {
                        knownName = rs.getString("name");
                        before = MoneyUtils.fromDb(rs.getBigDecimal("balance"));
                    }
                }
            }

            String name = (nameIfNew != null && !nameIfNew.isBlank()) ? nameIfNew : (knownName != null ? knownName : "unknown");
            double after = Math.max(0.0, target);

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO economy (uuid, name, balance) VALUES (?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE balance = VALUES(balance), name = VALUES(name)"
            )) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setBigDecimal(3, MoneyUtils.toDb(after));
                ps.executeUpdate();
            }

            c.commit();

            cache.put(uuid, after);
            sendBalanceUpdateToPlayer(uuid);

            double delta = MoneyUtils.round2(after - before);
            if (delta != 0.0) {
                DatabaseManager.insertBalanceHistoryAsync(uuid, name, delta, before, after, reason, metaJson, actorUuid, actorName);
            }
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Добавление суммы с защитой от отрицательных значений. */
    public static boolean addBalance(UUID uuid, double amount) {
        return addBalance(uuid, amount, amount >= 0 ? "ADD" : "REMOVE", null, null, null);
    }

    /** Добавление суммы с логированием в историю. */
    public static boolean addBalance(UUID uuid, double amount,
                                     String reason, UUID actorUuid, String actorName, String metaJson) {
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);

            double current = 0.0;
            boolean exists;

            try (PreparedStatement sel = c.prepareStatement(
                    "SELECT balance FROM economy WHERE uuid = ? FOR UPDATE"
            )) {
                sel.setString(1, uuid.toString());
                try (ResultSet rs = sel.executeQuery()) {
                    exists = rs.next();
                    if (exists) current = MoneyUtils.fromDb(rs.getBigDecimal("balance"));
                }
            }

            double next = Math.max(0.0, MoneyUtils.round2(current + amount));

            if (exists) {
                try (PreparedStatement upd = c.prepareStatement(
                        "UPDATE economy SET balance = ? WHERE uuid = ?"
                )) {
                    upd.setBigDecimal(1, MoneyUtils.toDb(next));
                    upd.setString(2, uuid.toString());
                    upd.executeUpdate();
                }
            } else {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO economy (uuid, name, balance) VALUES (?, ?, ?)"
                )) {
                    ins.setString(1, uuid.toString());
                    ins.setString(2, "unknown");
                    ins.setBigDecimal(3, MoneyUtils.toDb(next));
                    ins.executeUpdate();
                }
            }

            c.commit();
            cache.put(uuid, next);
            sendBalanceUpdateToPlayer(uuid);

            // history
            String playerName = DatabaseManager.getPlayerNameDirect(uuid);
            if (playerName == null) playerName = "unknown";
            double delta = MoneyUtils.round2(next - current);
            if (delta != 0.0) {
                DatabaseManager.insertBalanceHistoryAsync(uuid, playerName, delta, current, next, reason, metaJson, actorUuid, actorName);
            }
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Уменьшение баланса (нельзя уйти в минус). */
    public static boolean removeBalance(UUID uuid, double amount) {
        return addBalance(uuid, -Math.abs(amount));
    }

    /** Перевод между игроками. */
    public static boolean pay(UUID from, UUID to, double amount) {
        if (amount <= 0) return false;

        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);

            double balFrom = 0.0;
            boolean existsFrom;

            try (PreparedStatement sel = c.prepareStatement(
                    "SELECT balance FROM economy WHERE uuid = ? FOR UPDATE"
            )) {
                sel.setString(1, from.toString());
                try (ResultSet rs = sel.executeQuery()) {
                    existsFrom = rs.next();
                    if (existsFrom) balFrom = MoneyUtils.fromDb(rs.getBigDecimal("balance"));
                }
            }

            if (balFrom < amount) {
                c.rollback();
                return false;
            }

            double balTo = 0.0;
            boolean existsTo;

            try (PreparedStatement sel2 = c.prepareStatement(
                    "SELECT balance FROM economy WHERE uuid = ? FOR UPDATE"
            )) {
                sel2.setString(1, to.toString());
                try (ResultSet rs2 = sel2.executeQuery()) {
                    existsTo = rs2.next();
                    if (existsTo) balTo = MoneyUtils.fromDb(rs2.getBigDecimal("balance"));
                }
            }

            // списываем
            try (PreparedStatement upd = c.prepareStatement(
                    "UPDATE economy SET balance = balance - ? WHERE uuid = ?"
            )) {
                upd.setBigDecimal(1, MoneyUtils.toDb(MoneyUtils.round2(amount)));
                upd.setString(2, from.toString());
                upd.executeUpdate();
            }

            // начисляем
            if (existsTo) {
                try (PreparedStatement upd = c.prepareStatement(
                        "UPDATE economy SET balance = balance + ? WHERE uuid = ?"
                )) {
                    upd.setBigDecimal(1, MoneyUtils.toDb(MoneyUtils.round2(amount)));
                    upd.setString(2, to.toString());
                    upd.executeUpdate();
                }
            } else {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO economy (uuid, name, balance) VALUES (?, ?, ?)"
                )) {
                    ins.setString(1, to.toString());
                    ins.setString(2, "unknown");
                    ins.setBigDecimal(3, MoneyUtils.toDb(MoneyUtils.round2(amount)));
                    ins.executeUpdate();
                }
            }

            c.commit();

            cache.put(from, MoneyUtils.round2(balFrom - amount));
            cache.put(to, MoneyUtils.round2(balTo + amount));
            sendBalanceUpdateToPlayer(from);
            sendBalanceUpdateToPlayer(to);

            // history
            String fromName = DatabaseManager.getPlayerNameDirect(from);
            if (fromName == null) fromName = "unknown";
            String toName = DatabaseManager.getPlayerNameDirect(to);
            if (toName == null) toName = "unknown";
            DatabaseManager.insertBalanceHistoryAsync(from, fromName, -MoneyUtils.round2(amount), balFrom, MoneyUtils.round2(balFrom - amount),
                    "PAY_OUT", null, from, fromName);
            DatabaseManager.insertBalanceHistoryAsync(to, toName, MoneyUtils.round2(amount), balTo, MoneyUtils.round2(balTo + amount),
                    "PAY_IN", null, from, fromName);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Atomic payment that splits the credited amount into two recipients.
     * Total debited from {@code from} is {@code amountToA + amountToB}.
     *
     * Used for commissions (variant B): buyer pays the listed total, receiver gets (total - fee), server gets fee.
     */
    public static boolean paySplit(UUID from, UUID toA, double amountToA, UUID toB, double amountToB) {
        if (amountToA < 0 || amountToB < 0) return false;
        double total = MoneyUtils.round2(amountToA + amountToB);
        if (total <= 0) return false;

        // Merge recipients if same UUID
        if (toA.equals(toB)) {
            return pay(from, toA, total);
        }

        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);

            double balFrom = 0.0;
            boolean existsFrom;

            try (PreparedStatement sel = c.prepareStatement(
                    "SELECT balance FROM economy WHERE uuid = ? FOR UPDATE"
            )) {
                sel.setString(1, from.toString());
                try (ResultSet rs = sel.executeQuery()) {
                    existsFrom = rs.next();
                    if (existsFrom) balFrom = MoneyUtils.fromDb(rs.getBigDecimal("balance"));
                }
            }

            if (!existsFrom || balFrom < total) {
                c.rollback();
                return false;
            }

            // Lock recipients (if exist)
            boolean existsA;
            double balA = 0.0;
            try (PreparedStatement sel = c.prepareStatement(
                    "SELECT balance FROM economy WHERE uuid = ? FOR UPDATE"
            )) {
                sel.setString(1, toA.toString());
                try (ResultSet rs = sel.executeQuery()) {
                    existsA = rs.next();
                    if (existsA) balA = MoneyUtils.fromDb(rs.getBigDecimal("balance"));
                }
            }

            boolean existsB;
            double balB = 0.0;
            try (PreparedStatement sel = c.prepareStatement(
                    "SELECT balance FROM economy WHERE uuid = ? FOR UPDATE"
            )) {
                sel.setString(1, toB.toString());
                try (ResultSet rs = sel.executeQuery()) {
                    existsB = rs.next();
                    if (existsB) balB = MoneyUtils.fromDb(rs.getBigDecimal("balance"));
                }
            }

            // debit from
            try (PreparedStatement upd = c.prepareStatement(
                    "UPDATE economy SET balance = balance - ? WHERE uuid = ?"
            )) {
                upd.setBigDecimal(1, MoneyUtils.toDb(total));
                upd.setString(2, from.toString());
                upd.executeUpdate();
            }

            // credit A
            if (amountToA > 0) {
                if (existsA) {
                    try (PreparedStatement upd = c.prepareStatement(
                            "UPDATE economy SET balance = balance + ? WHERE uuid = ?"
                    )) {
                        upd.setBigDecimal(1, MoneyUtils.toDb(MoneyUtils.round2(amountToA)));
                        upd.setString(2, toA.toString());
                        upd.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ins = c.prepareStatement(
                            "INSERT INTO economy (uuid, name, balance) VALUES (?, ?, ?)"
                    )) {
                        ins.setString(1, toA.toString());
                        ins.setString(2, "unknown");
                        ins.setBigDecimal(3, MoneyUtils.toDb(MoneyUtils.round2(amountToA)));
                        ins.executeUpdate();
                    }
                }
            }

            // credit B
            if (amountToB > 0) {
                if (existsB) {
                    try (PreparedStatement upd = c.prepareStatement(
                            "UPDATE economy SET balance = balance + ? WHERE uuid = ?"
                    )) {
                        upd.setBigDecimal(1, MoneyUtils.toDb(MoneyUtils.round2(amountToB)));
                        upd.setString(2, toB.toString());
                        upd.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ins = c.prepareStatement(
                            "INSERT INTO economy (uuid, name, balance) VALUES (?, ?, ?)"
                    )) {
                        ins.setString(1, toB.toString());
                        ins.setString(2, "unknown");
                        ins.setBigDecimal(3, MoneyUtils.toDb(MoneyUtils.round2(amountToB)));
                        ins.executeUpdate();
                    }
                }
            }

            c.commit();

            // update cache (best-effort) + notify online players
            cache.put(from, MoneyUtils.round2(balFrom - total));
            cache.put(toA, MoneyUtils.round2((existsA ? balA : 0.0) + amountToA));
            cache.put(toB, MoneyUtils.round2((existsB ? balB : 0.0) + amountToB));

            sendBalanceUpdateToPlayer(from);
            sendBalanceUpdateToPlayer(toA);
            sendBalanceUpdateToPlayer(toB);

            // history
            String fromName = DatabaseManager.getPlayerNameDirect(from);
            if (fromName == null) fromName = "unknown";
            String toAName = DatabaseManager.getPlayerNameDirect(toA);
            if (toAName == null) toAName = "unknown";
            String toBName = DatabaseManager.getPlayerNameDirect(toB);
            if (toBName == null) toBName = "unknown";

            DatabaseManager.insertBalanceHistoryAsync(from, fromName, -total, balFrom, MoneyUtils.round2(balFrom - total),
                    "PAY_SPLIT_OUT", null, from, fromName);
            if (amountToA > 0) {
                DatabaseManager.insertBalanceHistoryAsync(toA, toAName, MoneyUtils.round2(amountToA), balA, MoneyUtils.round2((existsA ? balA : 0.0) + amountToA),
                        "PAY_SPLIT_IN", null, from, fromName);
            }
            if (amountToB > 0) {
                DatabaseManager.insertBalanceHistoryAsync(toB, toBName, MoneyUtils.round2(amountToB), balB, MoneyUtils.round2((existsB ? balB : 0.0) + amountToB),
                        "PAY_SPLIT_IN", null, from, fromName);
            }

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void sendBalanceUpdateToPlayer(UUID uuid) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;

            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                double bal = cache.getOrDefault(uuid, 0.0);
                NetworkUtils.sendBalanceToPlayer(p, bal);
            }
        } catch (Exception ignored) {}
    }

    public static void updateCache(UUID uuid, double balance) {
        cache.put(uuid, MoneyUtils.round2(balance));
    }
    // ========= Асинхронные версии =========

    public static CompletableFuture<Double> getBalanceAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> getBalance(uuid));
    }

    public static CompletableFuture<Boolean> addBalanceAsync(UUID uuid, double amount) {
        return CompletableFuture.supplyAsync(() -> addBalance(uuid, amount));
    }

    public static CompletableFuture<Boolean> payAsync(UUID from, UUID to, double amount) {
        return CompletableFuture.supplyAsync(() -> pay(from, to, amount));
    }
}

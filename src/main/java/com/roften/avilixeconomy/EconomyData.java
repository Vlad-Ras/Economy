package com.roften.avilixeconomy;

import com.roften.avilixeconomy.database.DatabaseManager;
import com.roften.avilixeconomy.network.NetworkUtils;
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
    private static final ConcurrentHashMap<UUID, Long> cache = new ConcurrentHashMap<>();

    /** Возвращает значение из кэша (для HUD). */
    public static long getCachedBalance(UUID uuid) {
        return cache.getOrDefault(uuid, 0L);
    }

    /** Прямое чтение из БД (без создания записи). */
    public static long getBalance(UUID uuid) {
        try {
            long bal = DatabaseManager.getBalanceDirect(uuid);
            cache.put(uuid, bal);
            return bal;
        } catch (Exception e) {
            return 0L;
        }
    }

    /** Создание записи игрока, только если её не существует. */
    public static boolean createIfMissing(UUID uuid, String name, long startBalance) {
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
    public static boolean setBalance(UUID uuid, long amount, String nameIfNew) {
        try (Connection c = DatabaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO economy (uuid, name, balance) VALUES (?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE balance = VALUES(balance), name = VALUES(name)"
             )) {

            ps.setString(1, uuid.toString());
            ps.setString(2, nameIfNew != null ? nameIfNew : "unknown");
            ps.setLong(3, amount);
            ps.executeUpdate();

            cache.put(uuid, amount);
            sendBalanceUpdateToPlayer(uuid);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Добавление суммы с защитой от отрицательных значений. */
    public static boolean addBalance(UUID uuid, long amount) {
        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);

            long current = 0;
            boolean exists;

            try (PreparedStatement sel = c.prepareStatement(
                    "SELECT balance FROM economy WHERE uuid = ? FOR UPDATE"
            )) {
                sel.setString(1, uuid.toString());
                try (ResultSet rs = sel.executeQuery()) {
                    exists = rs.next();
                    if (exists) current = rs.getLong("balance");
                }
            }

            long next = Math.max(0L, current + amount);

            if (exists) {
                try (PreparedStatement upd = c.prepareStatement(
                        "UPDATE economy SET balance = ? WHERE uuid = ?"
                )) {
                    upd.setLong(1, next);
                    upd.setString(2, uuid.toString());
                    upd.executeUpdate();
                }
            } else {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO economy (uuid, name, balance) VALUES (?, ?, ?)"
                )) {
                    ins.setString(1, uuid.toString());
                    ins.setString(2, "unknown");
                    ins.setLong(3, next);
                    ins.executeUpdate();
                }
            }

            c.commit();
            cache.put(uuid, next);
            sendBalanceUpdateToPlayer(uuid);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Уменьшение баланса (нельзя уйти в минус). */
    public static boolean removeBalance(UUID uuid, long amount) {
        return addBalance(uuid, -Math.abs(amount));
    }

    /** Перевод между игроками. */
    public static boolean pay(UUID from, UUID to, long amount) {
        if (amount <= 0) return false;

        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);

            long balFrom = 0;
            boolean existsFrom;

            try (PreparedStatement sel = c.prepareStatement(
                    "SELECT balance FROM economy WHERE uuid = ? FOR UPDATE"
            )) {
                sel.setString(1, from.toString());
                try (ResultSet rs = sel.executeQuery()) {
                    existsFrom = rs.next();
                    if (existsFrom) balFrom = rs.getLong("balance");
                }
            }

            if (balFrom < amount) {
                c.rollback();
                return false;
            }

            long balTo = 0;
            boolean existsTo;

            try (PreparedStatement sel2 = c.prepareStatement(
                    "SELECT balance FROM economy WHERE uuid = ? FOR UPDATE"
            )) {
                sel2.setString(1, to.toString());
                try (ResultSet rs2 = sel2.executeQuery()) {
                    existsTo = rs2.next();
                    if (existsTo) balTo = rs2.getLong("balance");
                }
            }

            // списываем
            try (PreparedStatement upd = c.prepareStatement(
                    "UPDATE economy SET balance = balance - ? WHERE uuid = ?"
            )) {
                upd.setLong(1, amount);
                upd.setString(2, from.toString());
                upd.executeUpdate();
            }

            // начисляем
            if (existsTo) {
                try (PreparedStatement upd = c.prepareStatement(
                        "UPDATE economy SET balance = balance + ? WHERE uuid = ?"
                )) {
                    upd.setLong(1, amount);
                    upd.setString(2, to.toString());
                    upd.executeUpdate();
                }
            } else {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO economy (uuid, name, balance) VALUES (?, ?, ?)"
                )) {
                    ins.setString(1, to.toString());
                    ins.setString(2, "unknown");
                    ins.setLong(3, amount);
                    ins.executeUpdate();
                }
            }

            c.commit();

            cache.put(from, balFrom - amount);
            cache.put(to, balTo + amount);
            sendBalanceUpdateToPlayer(from);
            sendBalanceUpdateToPlayer(to);
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
    public static boolean paySplit(UUID from, UUID toA, long amountToA, UUID toB, long amountToB) {
        if (amountToA < 0 || amountToB < 0) return false;
        long total = amountToA + amountToB;
        if (total <= 0) return false;

        // Merge recipients if same UUID
        if (toA.equals(toB)) {
            return pay(from, toA, total);
        }

        try (Connection c = DatabaseManager.getConnection()) {
            c.setAutoCommit(false);

            long balFrom = 0;
            boolean existsFrom;

            try (PreparedStatement sel = c.prepareStatement(
                    "SELECT balance FROM economy WHERE uuid = ? FOR UPDATE"
            )) {
                sel.setString(1, from.toString());
                try (ResultSet rs = sel.executeQuery()) {
                    existsFrom = rs.next();
                    if (existsFrom) balFrom = rs.getLong("balance");
                }
            }

            if (!existsFrom || balFrom < total) {
                c.rollback();
                return false;
            }

            // Lock recipients (if exist)
            boolean existsA;
            long balA = 0;
            try (PreparedStatement sel = c.prepareStatement(
                    "SELECT balance FROM economy WHERE uuid = ? FOR UPDATE"
            )) {
                sel.setString(1, toA.toString());
                try (ResultSet rs = sel.executeQuery()) {
                    existsA = rs.next();
                    if (existsA) balA = rs.getLong("balance");
                }
            }

            boolean existsB;
            long balB = 0;
            try (PreparedStatement sel = c.prepareStatement(
                    "SELECT balance FROM economy WHERE uuid = ? FOR UPDATE"
            )) {
                sel.setString(1, toB.toString());
                try (ResultSet rs = sel.executeQuery()) {
                    existsB = rs.next();
                    if (existsB) balB = rs.getLong("balance");
                }
            }

            // debit from
            try (PreparedStatement upd = c.prepareStatement(
                    "UPDATE economy SET balance = balance - ? WHERE uuid = ?"
            )) {
                upd.setLong(1, total);
                upd.setString(2, from.toString());
                upd.executeUpdate();
            }

            // credit A
            if (amountToA > 0) {
                if (existsA) {
                    try (PreparedStatement upd = c.prepareStatement(
                            "UPDATE economy SET balance = balance + ? WHERE uuid = ?"
                    )) {
                        upd.setLong(1, amountToA);
                        upd.setString(2, toA.toString());
                        upd.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ins = c.prepareStatement(
                            "INSERT INTO economy (uuid, name, balance) VALUES (?, ?, ?)"
                    )) {
                        ins.setString(1, toA.toString());
                        ins.setString(2, "unknown");
                        ins.setLong(3, amountToA);
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
                        upd.setLong(1, amountToB);
                        upd.setString(2, toB.toString());
                        upd.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ins = c.prepareStatement(
                            "INSERT INTO economy (uuid, name, balance) VALUES (?, ?, ?)"
                    )) {
                        ins.setString(1, toB.toString());
                        ins.setString(2, "unknown");
                        ins.setLong(3, amountToB);
                        ins.executeUpdate();
                    }
                }
            }

            c.commit();

            // update cache (best-effort) + notify online players
            cache.put(from, balFrom - total);
            cache.put(toA, (existsA ? balA : 0L) + amountToA);
            cache.put(toB, (existsB ? balB : 0L) + amountToB);

            sendBalanceUpdateToPlayer(from);
            sendBalanceUpdateToPlayer(toA);
            sendBalanceUpdateToPlayer(toB);

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
                long bal = cache.getOrDefault(uuid, 0L);
                NetworkUtils.sendBalanceToPlayer(p, bal);
            }
        } catch (Exception ignored) {}
    }

    public static void updateCache(UUID uuid, long balance) {
        cache.put(uuid, balance);
    }
    // ========= Асинхронные версии =========

    public static CompletableFuture<Long> getBalanceAsync(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> getBalance(uuid));
    }

    public static CompletableFuture<Boolean> addBalanceAsync(UUID uuid, long amount) {
        return CompletableFuture.supplyAsync(() -> addBalance(uuid, amount));
    }

    public static CompletableFuture<Boolean> payAsync(UUID from, UUID to, long amount) {
        return CompletableFuture.supplyAsync(() -> pay(from, to, amount));
    }
}

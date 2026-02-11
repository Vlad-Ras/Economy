package com.roften.avilixeconomy.database;

import com.roften.avilixeconomy.config.AvilixEconomyCommonConfig;
import com.roften.avilixeconomy.util.MoneyUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatabaseManager {

    private static HikariDataSource dataSource;

    public static void init() {
        if (!AvilixEconomyCommonConfig.DATABASE.enabled.get()) {
            throw new IllegalStateException("AvilixEconomy database is disabled in config.");
        }

        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(AvilixEconomyCommonConfig.DATABASE.jdbcUrl.get());
            config.setUsername(AvilixEconomyCommonConfig.DATABASE.username.get());
            config.setPassword(AvilixEconomyCommonConfig.DATABASE.password.get());
            config.setMaximumPoolSize(AvilixEconomyCommonConfig.DATABASE.poolMaxSize.get());
            config.setMinimumIdle(AvilixEconomyCommonConfig.DATABASE.poolMinIdle.get());
            config.setIdleTimeout(AvilixEconomyCommonConfig.DATABASE.idleTimeoutMs.get());
            config.setConnectionTimeout(AvilixEconomyCommonConfig.DATABASE.connectionTimeoutMs.get());
            config.setMaxLifetime(AvilixEconomyCommonConfig.DATABASE.maxLifetimeMs.get());

            dataSource = new HikariDataSource(config);

            createTables();
            System.out.println("[Economy] HikariCP успешно инициализирован");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private static void createTables() {
        try (Connection c = getConnection()) {
            // Балансы игроков
            try (PreparedStatement st = c.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS economy (
                        uuid VARCHAR(36) PRIMARY KEY,
                        name VARCHAR(32) NOT NULL,
                        balance DECIMAL(18,2) NOT NULL DEFAULT 500.00
                    )
                """)) {
                st.executeUpdate();
            }

            // История изменения баланса (аудит)
            try (PreparedStatement st = c.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS economy_balance_history (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        player_uuid VARCHAR(36) NOT NULL,
                        player_name VARCHAR(32) NOT NULL,
                        delta DECIMAL(18,2) NOT NULL,
                        balance_before DECIMAL(18,2) NOT NULL,
                        balance_after DECIMAL(18,2) NOT NULL,
                        reason VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
                        meta_json MEDIUMTEXT NULL,
                        actor_uuid VARCHAR(36) NULL,
                        actor_name VARCHAR(32) NULL,
                        PRIMARY KEY (id),
                        INDEX idx_player_time (player_uuid, created_at),
                        INDEX idx_reason_time (reason, created_at)
                    )
                """)) {
                st.executeUpdate();
            }

            // История продаж магазина (ShopBlockEntity)
            try (PreparedStatement st = c.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS shop_sales (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        world VARCHAR(64) NOT NULL,
                        x INT NOT NULL,
                        y INT NOT NULL,
                        z INT NOT NULL,
                        block_id VARCHAR(128) NOT NULL,
                        owner_uuid VARCHAR(36) NOT NULL,
                        owner_name VARCHAR(32) NOT NULL,
                        buyer_uuid VARCHAR(36) NOT NULL,
                        buyer_name VARCHAR(32) NOT NULL,
                        trade_type VARCHAR(8) NOT NULL DEFAULT 'SELL',
                        price_per_lot DECIMAL(18,2) NOT NULL,
                        lots INT NOT NULL,
                        total_price DECIMAL(18,2) NOT NULL,
                        items_json MEDIUMTEXT NOT NULL,
                        PRIMARY KEY (id),
                        INDEX idx_owner_uuid_time (owner_uuid, created_at),
                        INDEX idx_owner_name_time (owner_name, created_at),
                        INDEX idx_shop_pos (world, x, y, z, created_at)
                    )
                """)) {
                st.executeUpdate();
            }

            // Render overrides for items displayed on the shop shelf (admin-tunable)
            try (PreparedStatement st = c.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS shop_render_overrides (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        scope VARCHAR(8) NOT NULL,
                        override_key VARCHAR(128) NOT NULL,
                        off_x FLOAT NOT NULL DEFAULT 0,
                        off_y FLOAT NOT NULL DEFAULT 0,
                        off_z FLOAT NOT NULL DEFAULT 0,
                        rot_x FLOAT NOT NULL DEFAULT 0,
                        rot_y FLOAT NOT NULL DEFAULT 0,
                        rot_z FLOAT NOT NULL DEFAULT 0,
                        scale_mul FLOAT NOT NULL DEFAULT 1,
                        extra_lift FLOAT NOT NULL DEFAULT 0,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_scope_key (scope, override_key)
                    )
                """)) {
                st.executeUpdate();
            }

            // Миграции для уже существующих таблиц
            ensureShopSalesColumns(c);
            ensureMoneyColumnTypes(c);

            // Индексы (ускоряет VIEW и фильтрацию по trade_type)
            ensureIndexes(c);

            // Удобный "лог" без UUID: можно SELECT * FROM shop_sales_log;
            try (PreparedStatement st = c.prepareStatement("""
                    CREATE OR REPLACE VIEW shop_sales_log AS
                    SELECT
                        id,
                        created_at,
                        world,
                        x, y, z,
                        block_id AS block_name,
                        owner_name AS owner,
                        buyer_name AS buyer,
                        price_per_lot,
                        lots,
                        total_price,
                        items_json
                    FROM shop_sales
                """)) {
                st.executeUpdate();
            }

            // Отдельные view для логов: продажи и скупки (структура как у shop_sales_log)
            try (PreparedStatement st = c.prepareStatement("""
                    CREATE OR REPLACE VIEW shop_sales_log_sell AS
                    SELECT
                        id,
                        created_at,
                        world,
                        x, y, z,
                        block_id AS block_name,
                        owner_name AS owner,
                        buyer_name AS buyer,
                        price_per_lot,
                        lots,
                        total_price,
                        items_json
                    FROM shop_sales
                    WHERE trade_type = 'SELL'
                """)) {
                st.executeUpdate();
            }

            try (PreparedStatement st = c.prepareStatement("""
                    CREATE OR REPLACE VIEW shop_sales_log_buy AS
                    SELECT
                        id,
                        created_at,
                        world,
                        x, y, z,
                        block_id AS block_name,
                        owner_name AS owner,
                        buyer_name AS buyer,
                        price_per_lot,
                        lots,
                        total_price,
                        items_json
                    FROM shop_sales
                    WHERE trade_type = 'BUY'
                """)) {
                st.executeUpdate();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void ensureShopSalesColumns(Connection c) throws SQLException {
        ensureColumn(c, "shop_sales", "block_id",
                "ALTER TABLE shop_sales ADD COLUMN block_id VARCHAR(128) NOT NULL DEFAULT ''");
        ensureColumn(c, "shop_sales", "owner_name",
                "ALTER TABLE shop_sales ADD COLUMN owner_name VARCHAR(32) NOT NULL DEFAULT ''");
        ensureColumn(c, "shop_sales", "buyer_name",
                "ALTER TABLE shop_sales ADD COLUMN buyer_name VARCHAR(32) NOT NULL DEFAULT ''");
        ensureColumn(c, "shop_sales", "trade_type",
                "ALTER TABLE shop_sales ADD COLUMN trade_type VARCHAR(8) NOT NULL DEFAULT 'SELL'");

    }


    /**
     * Ensure DECIMAL(18,2) for money columns in existing installations.
     * Safe to run on every startup.
     */
    private static void ensureMoneyColumnTypes(Connection c) throws SQLException {
        // economy.balance
        try (PreparedStatement st = c.prepareStatement("ALTER TABLE economy MODIFY COLUMN balance DECIMAL(18,2) NOT NULL DEFAULT 500.00")) {
            st.executeUpdate();
        } catch (Exception ignored) {}

        // shop_sales money columns
        try (PreparedStatement st = c.prepareStatement("ALTER TABLE shop_sales MODIFY COLUMN price_per_lot DECIMAL(18,2) NOT NULL")) {
            st.executeUpdate();
        } catch (Exception ignored) {}
        try (PreparedStatement st = c.prepareStatement("ALTER TABLE shop_sales MODIFY COLUMN total_price DECIMAL(18,2) NOT NULL")) {
            st.executeUpdate();
        } catch (Exception ignored) {}
    }

    private static void ensureIndexes(Connection c) throws SQLException {
        // Для запросов вида: WHERE trade_type='SELL' ORDER BY created_at DESC
        // Один индекс покрывает оба view (shop_sales_log_sell / shop_sales_log_buy).
        ensureIndex(c, "shop_sales", "idx_trade_type_time",
                "CREATE INDEX idx_trade_type_time ON shop_sales (trade_type, created_at)");
    }

    private static void ensureIndex(Connection c, String table, String indexName, String createSql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM information_schema.STATISTICS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ? LIMIT 1"
        )) {
            ps.setString(1, table);
            ps.setString(2, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        }
        try (PreparedStatement st = c.prepareStatement(createSql)) {
            st.executeUpdate();
        }
    }

    private static void ensureColumn(Connection c, String table, String column, String alterSql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? LIMIT 1"
        )) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        }
        try (PreparedStatement st = c.prepareStatement(alterSql)) {
            st.executeUpdate();
        }
    }

    /**
     * Лог успешной покупки в магазине.
     */
    public static void logShopSale(
            long createdAtEpochMillis,
            String worldId,
            int x,
            int y,
            int z,
            String blockId,
            UUID ownerUuid,
            String ownerName,
            UUID buyerUuid,
            String buyerName,
            String tradeType,
            double pricePerLot,
            int lots,
            double totalPrice,
            String itemsJson
    ) {
        String sql = "INSERT INTO shop_sales(created_at, world, x, y, z, block_id, owner_uuid, owner_name, buyer_uuid, buyer_name, trade_type, price_per_lot, lots, total_price, items_json) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setTimestamp(1, new java.sql.Timestamp(Math.max(0L, createdAtEpochMillis)));
            ps.setString(2, worldId);
            ps.setInt(3, x);
            ps.setInt(4, y);
            ps.setInt(5, z);
            ps.setString(6, blockId != null ? blockId : "");
            ps.setString(7, ownerUuid != null ? ownerUuid.toString() : "");
            ps.setString(8, ownerName != null ? ownerName : "");
            ps.setString(9, buyerUuid != null ? buyerUuid.toString() : "");
            ps.setString(10, buyerName != null ? buyerName : "");
            ps.setString(11, tradeType != null ? tradeType : "SELL");
            ps.setBigDecimal(12, MoneyUtils.toDb(pricePerLot));
            ps.setInt(13, lots);
            ps.setBigDecimal(14, MoneyUtils.toDb(totalPrice));
            ps.setString(15, itemsJson != null ? itemsJson : "[]");
            ps.executeUpdate();

        } catch (Exception e) {
            // не валим сервер из-за лога
            e.printStackTrace();
        }
    }

    public static void logShopSaleNow(
            String worldId,
            int x,
            int y,
            int z,
            String blockId,
            UUID ownerUuid,
            String ownerName,
            UUID buyerUuid,
            String buyerName,
            String tradeType,
            double pricePerLot,
            int lots,
            double totalPrice,
            String itemsJson
    ) {
        logShopSale(Instant.now().toEpochMilli(), worldId, x, y, z, blockId, ownerUuid, ownerName, buyerUuid, buyerName, tradeType,
                pricePerLot, lots, totalPrice, itemsJson);
    }

    public static boolean recordExists(UUID uuid) throws SQLException {
        String sql = "SELECT uuid FROM economy WHERE uuid = ?";

        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(sql)) {

            st.setString(1, uuid.toString());
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static double getBalanceDirect(UUID uuid) {
        String sql = "SELECT balance FROM economy WHERE uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return MoneyUtils.fromDb(rs.getBigDecimal("balance"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public static String getPlayerNameDirect(UUID uuid) {
        String sql = "SELECT name FROM economy WHERE uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getString("name");
            }

        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Finds UUID by last known player name stored in economy table.
     * This allows admin commands to work for offline players.
     * Matching is case-insensitive.
     */
    public static UUID getUuidByName(String name) {
        if (name == null || name.isBlank()) return null;
        String sql = "SELECT uuid FROM economy WHERE LOWER(name) = LOWER(?) LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    try {
                        return UUID.fromString(rs.getString("uuid"));
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ======== TOP BALANCES (for /eco top) ========
    public record BalanceRow(String uuid, String name, double balance) {}
    public record BalancesPage(List<BalanceRow> rows, boolean hasMore) {}

    public record BalanceHistoryRow(
            long id,
            java.sql.Timestamp createdAt,
            String playerName,
            double delta,
            double balanceBefore,
            double balanceAfter,
            String reason,
            String actorName
    ) {}

    /**
     * Returns a page of players ordered by balance DESC.
     * Uses limit+1 strategy to determine if there are more rows.
     */
    public static BalancesPage getBalancesTopPage(int limit, int offset, UUID excludeUuid) {
        int safeLimit = Math.max(1, Math.min(50, limit));
        int safeOffset = Math.max(0, offset);

        if (dataSource == null) return new BalancesPage(List.of(), false);

        String sql = "SELECT uuid, name, balance FROM economy " +
                (excludeUuid != null ? "WHERE uuid <> ? " : "") +
                "ORDER BY balance DESC LIMIT ? OFFSET ?";

        List<BalanceRow> rows = new ArrayList<>();
        boolean hasMore = false;

        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {

            int idx = 1;
            if (excludeUuid != null) {
                ps.setString(idx++, excludeUuid.toString());
            }
            ps.setInt(idx++, safeLimit + 1);
            ps.setInt(idx, safeOffset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rows.size() >= safeLimit) {
                        hasMore = true;
                        break;
                    }
                    rows.add(new BalanceRow(
                            rs.getString("uuid"),
                            rs.getString("name"),
                            MoneyUtils.fromDb(rs.getBigDecimal("balance"))
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new BalancesPage(rows, hasMore);
    }

    /**
     * Adds amount to all existing economy records (offline included).
     * Returns number of affected rows.
     */
    public static int addBalanceToAll(double amount, UUID excludeUuid) {
        if (dataSource == null) return 0;
        double a = MoneyUtils.round2(amount);
        if (Math.abs(a) < 0.000001) return 0;

        String sql = "UPDATE economy SET balance = GREATEST(0, balance + ?)" +
                (excludeUuid != null ? " WHERE uuid <> ?" : "");

        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int idx = 1;
            ps.setBigDecimal(idx++, MoneyUtils.toDb(a));
            if (excludeUuid != null) {
                ps.setString(idx, excludeUuid.toString());
            }
            return ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static void createPlayerRecord(UUID uuid, String name, double startBalance) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "INSERT INTO economy(uuid, name, balance) VALUES (?, ?, ?)"
             )) {

            st.setString(1, uuid.toString());
            st.setString(2, name);
            st.setBigDecimal(3, MoneyUtils.toDb(startBalance));
            st.executeUpdate();
        }
    }

    public static void updatePlayerName(UUID uuid, String name) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "UPDATE economy SET name = ? WHERE uuid = ?"
             )) {

            st.setString(1, name);
            st.setString(2, uuid.toString());
            st.executeUpdate();
        }
    }

    
    /**
     * Ensures an economy record exists for the given UUID (used for server commission account).
     */
    public static void ensurePlayerRecord(UUID uuid, String name, double initialBalance) {
        try {
            if (!recordExists(uuid)) {
                createPlayerRecord(uuid, name, initialBalance);
            } else {
                updatePlayerName(uuid, name);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

public static void shutdown() {
        if (dataSource != null) {
            dataSource.close();
            System.out.println("[DB] HikariCP stopped");
        }
    }


// ======== SHOP SALES QUERY (for GUI history) ========
public record ShopSaleRow(long createdAtMillis, String tradeType, String counterpartyName, int lots, double totalPrice, double pricePerLot, String itemsSummary) {}

public record ShopSalesPage(List<ShopSaleRow> rows, boolean hasMore) {}

/**
 * Fetch last shop sales for a specific shop block (world + pos), ordered newest first.
 * Uses limit+1 strategy to determine hasMore for pagination.
 */
public static ShopSalesPage getShopSalesPage(String worldId, int x, int y, int z, int limit, int offset) {
    int safeLimit = Math.max(1, Math.min(50, limit));
    int safeOffset = Math.max(0, offset);

    if (dataSource == null) {
        return new ShopSalesPage(List.of(), false);
    }

    String sql = """
            SELECT created_at, trade_type, buyer_name, lots, total_price, price_per_lot, items_json
            FROM shop_sales
            WHERE world = ? AND x = ? AND y = ? AND z = ?
            ORDER BY id DESC
            LIMIT ? OFFSET ?
            """;

    List<ShopSaleRow> rows = new ArrayList<>(safeLimit);

    try (Connection conn = getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setString(1, worldId);
        ps.setInt(2, x);
        ps.setInt(3, y);
        ps.setInt(4, z);
        ps.setInt(5, safeLimit + 1);
        ps.setInt(6, safeOffset);

        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long createdMs = rs.getTimestamp("created_at").toInstant().toEpochMilli();
                String tradeType = rs.getString("trade_type");
                String counterparty = rs.getString("buyer_name");
                int lots = rs.getInt("lots");
                double total = MoneyUtils.fromDb(rs.getBigDecimal("total_price"));
                double ppl = MoneyUtils.fromDb(rs.getBigDecimal("price_per_lot"));
                String itemsJson = rs.getString("items_json");
                String summary = summarizeItems(itemsJson);

                rows.add(new ShopSaleRow(createdMs, tradeType, counterparty, lots, total, ppl, summary));

                if (rows.size() >= safeLimit + 1) break;
            }
        }

    } catch (Exception e) {
        // Do not crash server if DB is down — just return empty page
        return new ShopSalesPage(List.of(), false);
    }

    boolean hasMore = rows.size() > safeLimit;
    if (hasMore) {
        rows = new ArrayList<>(rows.subList(0, safeLimit));
    }

    return new ShopSalesPage(rows, hasMore);
}

	private static final Pattern ITEMS_SUMMARY_PATTERN =
	        // Example items_json: [{"item":"minecraft:stone","perLot":64,"total":128}, ...]
	        // Note: in Java strings, backslashes must be escaped, so \" in the string matches a literal quote.
	        Pattern.compile("\\\"item\\\":\\\"([^\\\\\"]+)\\\".*?\\\"total\\\":(\\d+)", Pattern.DOTALL);

private static String summarizeItems(String itemsJson) {
    if (itemsJson == null || itemsJson.isBlank() || itemsJson.equals("[]")) return "-";
    Matcher m = ITEMS_SUMMARY_PATTERN.matcher(itemsJson);
    StringBuilder sb = new StringBuilder(64);
    int count = 0;
    while (m.find()) {
        if (count > 0) sb.append(", ");
        sb.append(m.group(1)).append("×").append(m.group(2));
        count++;
        if (count >= 3) { // keep it short for GUI
            if (m.find()) sb.append(", ...");
            break;
        }
    }
    return count == 0 ? "-" : sb.toString();
}

    // =============================
    // Shop shelf render overrides
    // =============================

    public static List<com.roften.avilixeconomy.shop.render.RenderOverrideEntry> loadRenderOverrides() {
        if (dataSource == null) return List.of();
        String sql = """
                SELECT scope, override_key, off_x, off_y, off_z, rot_x, rot_y, rot_z, scale_mul, extra_lift
                FROM shop_render_overrides
                """;

        List<com.roften.avilixeconomy.shop.render.RenderOverrideEntry> out = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String scope = rs.getString("scope");
                String key = rs.getString("override_key");
                float ox = rs.getFloat("off_x");
                float oy = rs.getFloat("off_y");
                float oz = rs.getFloat("off_z");
                float rx = rs.getFloat("rot_x");
                float ry = rs.getFloat("rot_y");
                float rz = rs.getFloat("rot_z");
                float sm = rs.getFloat("scale_mul");
                float el = rs.getFloat("extra_lift");

                var sc = "TYPE".equalsIgnoreCase(scope)
                        ? com.roften.avilixeconomy.shop.render.RenderOverrideScope.TYPE
                        : com.roften.avilixeconomy.shop.render.RenderOverrideScope.ITEM;

                var tr = new com.roften.avilixeconomy.shop.render.RenderTransform(ox, oy, oz, rx, ry, rz, sm, el);
                out.add(new com.roften.avilixeconomy.shop.render.RenderOverrideEntry(sc, key, tr));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
        return out;
    }

    public static void upsertRenderOverride(
            com.roften.avilixeconomy.shop.render.RenderOverrideScope scope,
            String key,
            com.roften.avilixeconomy.shop.render.RenderTransform t
    ) {
        if (dataSource == null) return;
        String sql = """
                INSERT INTO shop_render_overrides
                    (scope, override_key, off_x, off_y, off_z, rot_x, rot_y, rot_z, scale_mul, extra_lift)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    off_x=VALUES(off_x), off_y=VALUES(off_y), off_z=VALUES(off_z),
                    rot_x=VALUES(rot_x), rot_y=VALUES(rot_y), rot_z=VALUES(rot_z),
                    scale_mul=VALUES(scale_mul), extra_lift=VALUES(extra_lift)
                """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, scope.name());
            ps.setString(2, key);
            ps.setFloat(3, t.offX());
            ps.setFloat(4, t.offY());
            ps.setFloat(5, t.offZ());
            ps.setFloat(6, t.rotX());
            ps.setFloat(7, t.rotY());
            ps.setFloat(8, t.rotZ());
            ps.setFloat(9, t.scaleMul());
            ps.setFloat(10, t.extraLift());
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deleteRenderOverride(
            com.roften.avilixeconomy.shop.render.RenderOverrideScope scope,
            String key
    ) {
        if (dataSource == null) return;
        String sql = "DELETE FROM shop_render_overrides WHERE scope = ? AND override_key = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, scope.name());
            ps.setString(2, key);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= Balance history =================

    public static void insertBalanceHistory(
            java.util.UUID playerUuid,
            String playerName,
            double delta,
            double balanceBefore,
            double balanceAfter,
            String reason,
            String metaJson,
            java.util.UUID actorUuid,
            String actorName
    ) {
        if (dataSource == null || playerUuid == null) return;
        String sql = """
                INSERT INTO economy_balance_history
                (player_uuid, player_name, delta, balance_before, balance_after, reason, meta_json, actor_uuid, actor_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, playerName == null ? "unknown" : playerName);
            ps.setBigDecimal(3, com.roften.avilixeconomy.util.MoneyUtils.toDb(com.roften.avilixeconomy.util.MoneyUtils.round2(delta)));
            ps.setBigDecimal(4, com.roften.avilixeconomy.util.MoneyUtils.toDb(com.roften.avilixeconomy.util.MoneyUtils.round2(balanceBefore)));
            ps.setBigDecimal(5, com.roften.avilixeconomy.util.MoneyUtils.toDb(com.roften.avilixeconomy.util.MoneyUtils.round2(balanceAfter)));
            ps.setString(6, (reason == null || reason.isBlank()) ? "UNKNOWN" : reason);
            ps.setString(7, metaJson);
            ps.setString(8, actorUuid == null ? null : actorUuid.toString());
            ps.setString(9, actorName);
            ps.executeUpdate();
        } catch (Exception e) {
            // history must never break economy logic
            com.roften.avilixeconomy.AvilixEconomy.LOGGER.warn("Failed to insert balance history", e);
        }
    }

    public static java.util.concurrent.CompletableFuture<Void> insertBalanceHistoryAsync(
            java.util.UUID playerUuid,
            String playerName,
            double delta,
            double balanceBefore,
            double balanceAfter,
            String reason,
            String metaJson,
            java.util.UUID actorUuid,
            String actorName
    ) {
        return java.util.concurrent.CompletableFuture.runAsync(() -> insertBalanceHistory(
                playerUuid, playerName, delta, balanceBefore, balanceAfter, reason, metaJson, actorUuid, actorName
        ));
    }

    public static java.util.List<BalanceHistoryRow> getBalanceHistory(java.util.UUID playerUuid, int limit, int offset) {
        if (dataSource == null || playerUuid == null) return java.util.Collections.emptyList();
        limit = Math.max(1, Math.min(100, limit));
        offset = Math.max(0, offset);

        String sql = """
                SELECT id, created_at, player_name, delta, balance_before, balance_after, reason, actor_name
                FROM economy_balance_history
                WHERE player_uuid = ?
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """;
        java.util.ArrayList<BalanceHistoryRow> out = new java.util.ArrayList<>();
        try (Connection c = getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new BalanceHistoryRow(
                            rs.getLong("id"),
                            rs.getTimestamp("created_at"),
                            rs.getString("player_name"),
                            com.roften.avilixeconomy.util.MoneyUtils.fromDb(rs.getBigDecimal("delta")),
                            com.roften.avilixeconomy.util.MoneyUtils.fromDb(rs.getBigDecimal("balance_before")),
                            com.roften.avilixeconomy.util.MoneyUtils.fromDb(rs.getBigDecimal("balance_after")),
                            rs.getString("reason"),
                            rs.getString("actor_name")
                    ));
                }
            }
        } catch (Exception e) {
            com.roften.avilixeconomy.AvilixEconomy.LOGGER.warn("Failed to load balance history", e);
        }
        return out;
    }
}
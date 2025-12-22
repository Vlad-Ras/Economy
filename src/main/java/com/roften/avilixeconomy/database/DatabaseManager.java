package com.roften.avilixeconomy.database;

import com.roften.avilixeconomy.config.AvilixEconomyCommonConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
                        balance BIGINT NOT NULL DEFAULT 500
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
                        price_per_lot BIGINT NOT NULL,
                        lots INT NOT NULL,
                        total_price BIGINT NOT NULL,
                        items_json MEDIUMTEXT NOT NULL,
                        PRIMARY KEY (id),
                        INDEX idx_owner_uuid_time (owner_uuid, created_at),
                        INDEX idx_owner_name_time (owner_name, created_at),
                        INDEX idx_shop_pos (world, x, y, z, created_at)
                    )
                """)) {
                st.executeUpdate();
            }

            // Миграции для уже существующих таблиц
            ensureShopSalesColumns(c);

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
            long pricePerLot,
            int lots,
            long totalPrice,
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
            ps.setLong(12, pricePerLot);
            ps.setInt(13, lots);
            ps.setLong(14, totalPrice);
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
            long pricePerLot,
            int lots,
            long totalPrice,
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

    public static long getBalanceDirect(UUID uuid) {
        String sql = "SELECT balance FROM economy WHERE uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getLong("balance");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0L;
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

    public static void createPlayerRecord(UUID uuid, String name, long startBalance) throws SQLException {
        try (Connection c = getConnection();
             PreparedStatement st = c.prepareStatement(
                     "INSERT INTO economy(uuid, name, balance) VALUES (?, ?, ?)"
             )) {

            st.setString(1, uuid.toString());
            st.setString(2, name);
            st.setLong(3, startBalance);
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
    public static void ensurePlayerRecord(UUID uuid, String name, long initialBalance) {
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
public record ShopSaleRow(long createdAtMillis, String tradeType, String counterpartyName, int lots, long totalPrice, long pricePerLot, String itemsSummary) {}

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
                long total = rs.getLong("total_price");
                long ppl = rs.getLong("price_per_lot");
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
}
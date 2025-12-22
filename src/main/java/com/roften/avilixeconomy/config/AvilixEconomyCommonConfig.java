package com.roften.avilixeconomy.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.nio.file.Path;
import java.util.List;

/**
 * Common (server) configuration for AvilixEconomy.
 *
 * File: config/avilixeconomy-common.toml
 */
public final class AvilixEconomyCommonConfig {

    public static final ModConfigSpec SPEC;

    public static final Database DATABASE;
    public static final Economy ECONOMY;
    public static final Commission COMMISSION;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("database");
        DATABASE = new Database(b);
        b.pop();

        b.push("economy");
        ECONOMY = new Economy(b);
        b.pop();

        b.push("commission");
        COMMISSION = new Commission(b);
        b.pop();

        SPEC = b.build();
    }

    private AvilixEconomyCommonConfig() {}

    public static final class Database {
        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.ConfigValue<String> jdbcUrl;
        public final ModConfigSpec.ConfigValue<String> username;
        public final ModConfigSpec.ConfigValue<String> password;

        public final ModConfigSpec.IntValue poolMaxSize;
        public final ModConfigSpec.IntValue poolMinIdle;
        public final ModConfigSpec.IntValue connectionTimeoutMs;
        public final ModConfigSpec.IntValue idleTimeoutMs;
        public final ModConfigSpec.IntValue maxLifetimeMs;

        private Database(ModConfigSpec.Builder b) {
            enabled = b.comment("Enable MySQL/MariaDB storage via HikariCP").define("enabled", true);
            jdbcUrl = b.comment("JDBC URL. Example: jdbc:mariadb://127.0.0.1:3306/avilixeconomy?useSSL=false")
                    .define("jdbc_url", "jdbc:mysql://localhost:3306/avilix?useSSL=false");
            username = b.define("username", "economy");
            password = b.define("password", "");

            poolMaxSize = b.comment("HikariCP maximum pool size").defineInRange("pool_max_size", 10, 1, 64);
            poolMinIdle = b.comment("HikariCP minimum idle connections").defineInRange("pool_min_idle", 2, 0, 64);
            connectionTimeoutMs = b.comment("HikariCP connection timeout (ms)")
                    .defineInRange("connection_timeout_ms", 10000, 250, 120000);
            idleTimeoutMs = b.comment("HikariCP idle timeout (ms)")
                    .defineInRange("idle_timeout_ms", 600000, 0, 3600000);
            maxLifetimeMs = b.comment("HikariCP max lifetime (ms)")
                    .defineInRange("max_lifetime_ms", 1800000, 0, 7200000);
        }
    }

    public static final class Economy {
        public final ModConfigSpec.LongValue startBalance;

        public final ModConfigSpec.ConfigValue<String> serverAccountUuid;
        public final ModConfigSpec.ConfigValue<String> serverAccountName;
        public final ModConfigSpec.LongValue serverAccountStartBalance;

        private Economy(ModConfigSpec.Builder b) {
            startBalance = b.comment("Starting balance for NEW players")
                    .defineInRange("start_balance", 100L, 0L, Long.MAX_VALUE);

            serverAccountUuid = b.comment("UUID of server account (used as commission recipient)")
                    .define("server_account_uuid", "00000000-0000-0000-0000-000000000000");
            serverAccountName = b.comment("Name of server account record in DB")
                    .define("server_account_name", "SERVER");
            serverAccountStartBalance = b.comment("Initial balance for server account record (only used if record does not exist)")
                    .defineInRange("server_account_start_balance", 0L, 0L, Long.MAX_VALUE);
        }
    }

    public static final class Commission {
        public final ModConfigSpec.IntValue defaultSellBps;
        public final ModConfigSpec.IntValue defaultBuyBps;

        public final ModConfigSpec.ConfigValue<List<? extends String>> ownerOverrides;

        private Commission(ModConfigSpec.Builder b) {
            defaultSellBps = b.comment("Default commission for SELL-mode (shop sells to player). In bps: 100 = 1%")
                    .defineInRange("default_sell_bps", 0, 0, 10000);
            defaultBuyBps = b.comment("Default commission for BUY-mode (shop buys from player). In bps: 100 = 1%")
                    .defineInRange("default_buy_bps", 0, 0, 10000);

            ownerOverrides = b.comment("Per-owner override list. Format: <owner_uuid>:<sell_bps>:<buy_bps>")
                    .defineListAllowEmpty("owner_overrides", List.of(), o -> o instanceof String);
        }
    }

    /**
     * Persists the in-memory config values to config/avilixeconomy-common.toml.
     * Commands call this so changes survive restart.
     */
    public static void saveToDisk() {
        Path path = FMLPaths.CONFIGDIR.get().resolve("avilixeconomy-common.toml");
        CommentedFileConfig cfg = CommentedFileConfig.builder(path)
                .sync()
                .autosave()
                .writingMode(WritingMode.REPLACE)
                .build();
        cfg.load();
        // NeoForge ModConfigSpec does not expose a public "bind" API.
        // So we persist values explicitly to the NightConfig file.
        // (Categories are written with dot-separated keys.)

        // database
        cfg.set("database.enabled", DATABASE.enabled.get());
        cfg.set("database.jdbc_url", DATABASE.jdbcUrl.get());
        cfg.set("database.username", DATABASE.username.get());
        cfg.set("database.password", DATABASE.password.get());
        cfg.set("database.pool_max_size", DATABASE.poolMaxSize.get());
        cfg.set("database.pool_min_idle", DATABASE.poolMinIdle.get());
        cfg.set("database.connection_timeout_ms", DATABASE.connectionTimeoutMs.get());
        cfg.set("database.idle_timeout_ms", DATABASE.idleTimeoutMs.get());
        cfg.set("database.max_lifetime_ms", DATABASE.maxLifetimeMs.get());

        // economy
        cfg.set("economy.start_balance", ECONOMY.startBalance.get());
        cfg.set("economy.server_account_uuid", ECONOMY.serverAccountUuid.get());
        cfg.set("economy.server_account_name", ECONOMY.serverAccountName.get());
        cfg.set("economy.server_account_start_balance", ECONOMY.serverAccountStartBalance.get());

        // commission
        cfg.set("commission.default_sell_bps", COMMISSION.defaultSellBps.get());
        cfg.set("commission.default_buy_bps", COMMISSION.defaultBuyBps.get());
        cfg.set("commission.owner_overrides", COMMISSION.ownerOverrides.get());
        cfg.save();
        cfg.close();
    }
}

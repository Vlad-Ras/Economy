package com.roften.avilixeconomy.commission;

import com.roften.avilixeconomy.config.AvilixEconomyCommonConfig;
import com.roften.avilixeconomy.util.MoneyUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Commission logic (variant B):
 * - Buyer always pays the listed total.
 * - Commission is taken from the receiver (owner or seller), and sent to server account.
 *
 * Rates are in bps: 100 = 1%.
 */
public final class CommissionManager {

    private CommissionManager() {}

    public static int getSellBpsForOwner(UUID ownerUuid) {
        OwnerOverride ov = findOverride(ownerUuid);
        if (ov != null && ov.sellBps >= 0) return ov.sellBps;
        return AvilixEconomyCommonConfig.COMMISSION.defaultSellBps.get();
    }

    public static int getBuyBpsForOwner(UUID ownerUuid) {
        OwnerOverride ov = findOverride(ownerUuid);
        if (ov != null && ov.buyBps >= 0) return ov.buyBps;
        return AvilixEconomyCommonConfig.COMMISSION.defaultBuyBps.get();
    }

    public static void setGlobalSellBps(int bps) {
        AvilixEconomyCommonConfig.COMMISSION.defaultSellBps.set(clampBps(bps));
        AvilixEconomyCommonConfig.saveToDisk();
    }

    public static void setGlobalBuyBps(int bps) {
        AvilixEconomyCommonConfig.COMMISSION.defaultBuyBps.set(clampBps(bps));
        AvilixEconomyCommonConfig.saveToDisk();
    }

    public static void setOwnerOverride(UUID owner, Integer sellBps, Integer buyBps) {
        int sell = sellBps == null ? -1 : clampBps(sellBps);
        int buy = buyBps == null ? -1 : clampBps(buyBps);

        List<String> cur = new ArrayList<>();
        for (String s : AvilixEconomyCommonConfig.COMMISSION.ownerOverrides.get()) {
            if (s == null) continue;
            if (!s.trim().isEmpty()) cur.add(s.trim());
        }

        // Remove existing entry for this owner
        cur.removeIf(s -> {
            OwnerOverride o = parseOverride(s);
            return o != null && o.ownerUuid.equals(owner);
        });

        cur.add(owner.toString() + ":" + sell + ":" + buy);

        AvilixEconomyCommonConfig.COMMISSION.ownerOverrides.set(cur);
        AvilixEconomyCommonConfig.saveToDisk();
    }

    public static boolean clearOwnerOverride(UUID owner) {
        List<String> cur = new ArrayList<>();
        boolean removed = false;
        for (String s : AvilixEconomyCommonConfig.COMMISSION.ownerOverrides.get()) {
            if (s == null) continue;
            OwnerOverride o = parseOverride(s);
            if (o != null && o.ownerUuid.equals(owner)) {
                removed = true;
                continue;
            }
            cur.add(s.trim());
        }
        if (removed) {
            AvilixEconomyCommonConfig.COMMISSION.ownerOverrides.set(cur);
            AvilixEconomyCommonConfig.saveToDisk();
        }
        return removed;
    }

    public static double computeFee(double total, int bps) {
        if (total <= 0) return 0.0;
        if (bps <= 0) return 0.0;
        // total * bps / 10000 (2 decimals)
        double fee = MoneyUtils.round2(total * (double) bps / 10000.0);
        if (fee < 0) return 0.0;
        if (fee > total) return total;
        return fee;
    }

    private static int clampBps(int bps) {
        if (bps < 0) return 0;
        if (bps > 10000) return 10000;
        return bps;
    }

    private static OwnerOverride findOverride(UUID owner) {
        for (String s : AvilixEconomyCommonConfig.COMMISSION.ownerOverrides.get()) {
            OwnerOverride ov = parseOverride(s);
            if (ov != null && ov.ownerUuid.equals(owner)) return ov;
        }
        return null;
    }

    private static OwnerOverride parseOverride(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        // allow separators: uuid:sell:buy OR uuid=sell:buy OR uuid=sell,buy
        String uuidPart;
        String rest;
        int eq = t.indexOf('=');
        if (eq >= 0) {
            uuidPart = t.substring(0, eq).trim();
            rest = t.substring(eq + 1).trim();
        } else {
            int colon = t.indexOf(':');
            if (colon < 0) return null;
            uuidPart = t.substring(0, colon).trim();
            rest = t.substring(colon + 1).trim();
        }

        String[] parts = rest.split("[: ,]+");
        if (parts.length < 2) return null;

        try {
            UUID uuid = UUID.fromString(uuidPart);
            int sell = Integer.parseInt(parts[0].trim());
            int buy = Integer.parseInt(parts[1].trim());
            return new OwnerOverride(uuid, sell, buy);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class OwnerOverride {
        final UUID ownerUuid;
        final int sellBps;
        final int buyBps;

        OwnerOverride(UUID ownerUuid, int sellBps, int buyBps) {
            this.ownerUuid = ownerUuid;
            this.sellBps = sellBps;
            this.buyBps = buyBps;
        }
    }
}

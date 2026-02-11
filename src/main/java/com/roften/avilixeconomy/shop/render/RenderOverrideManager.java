package com.roften.avilixeconomy.shop.render;

import com.roften.avilixeconomy.database.DatabaseManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side cache for shelf render overrides.
 *
 * Persistence lives in MySQL; this class keeps a hot cache and provides
 * helpers for sync/broadcast.
 */
public final class RenderOverrideManager {

    private RenderOverrideManager() {}

    private static final Map<String, RenderTransform> ITEM = new ConcurrentHashMap<>();
    private static final Map<String, RenderTransform> TYPE = new ConcurrentHashMap<>();

    public static void reloadFromDb() {
        ITEM.clear();
        TYPE.clear();
        try {
            List<RenderOverrideEntry> all = DatabaseManager.loadRenderOverrides();
            for (RenderOverrideEntry e : all) {
                if (e.scope() == RenderOverrideScope.ITEM) {
                    ITEM.put(e.key(), e.transform());
                } else {
                    TYPE.put(e.key(), e.transform());
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void upsert(RenderOverrideScope scope, String key, RenderTransform t) {
        if (scope == RenderOverrideScope.ITEM) ITEM.put(key, t);
        else TYPE.put(key, t);
        DatabaseManager.upsertRenderOverride(scope, key, t);
    }

    public static void delete(RenderOverrideScope scope, String key) {
        if (scope == RenderOverrideScope.ITEM) ITEM.remove(key);
        else TYPE.remove(key);
        DatabaseManager.deleteRenderOverride(scope, key);
    }

    public static List<RenderOverrideEntry> snapshotAll() {
        List<RenderOverrideEntry> out = new ArrayList<>(ITEM.size() + TYPE.size());
        for (var e : ITEM.entrySet()) out.add(new RenderOverrideEntry(RenderOverrideScope.ITEM, e.getKey(), e.getValue()));
        for (var e : TYPE.entrySet()) out.add(new RenderOverrideEntry(RenderOverrideScope.TYPE, e.getKey(), e.getValue()));
        return out;
    }

    public static void broadcast(MinecraftServer server, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            try {
                p.connection.send(payload);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Broadcast a single UPSERT to all players (client cache update). */
    public static void broadcastUpsert(MinecraftServer server, RenderOverrideScope scope, String key, RenderTransform t) {
        if (server == null) return;
        broadcast(server, new com.roften.avilixeconomy.network.NetworkRegistration.ShopRenderOverrideUpsertPayload(scope, key, t));
    }

    /** Broadcast a single REMOVE to all players (client cache update). */
    public static void broadcastRemove(MinecraftServer server, RenderOverrideScope scope, String key) {
        if (server == null) return;
        broadcast(server, new com.roften.avilixeconomy.network.NetworkRegistration.ShopRenderOverrideRemovePayload(scope, key));
    }
}

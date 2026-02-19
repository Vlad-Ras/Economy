package com.roften.avilixeconomy.shop.client;

import com.roften.avilixeconomy.shop.render.RenderOverrideEntry;
import com.roften.avilixeconomy.shop.render.RenderOverrideScope;
import com.roften.avilixeconomy.shop.render.RenderTransform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache for shelf render overrides.
 *
 * There are two layers:
 *  - Persistent cache synced from server (DB-backed)
 *  - Session (temporary) overrides used by the tuning UI (not saved unless admin presses Save)
 */
public final class ShopRenderOverridesClient {

    private ShopRenderOverridesClient() {}

    public enum ActiveSource {
        SESSION_ITEM,
        SESSION_TYPE,
        ITEM,
        TYPE,
        DEFAULT
    }

    public record Resolved(RenderTransform transform, ActiveSource source) {}

    private static final Map<String, RenderTransform> PERSIST_ITEM = new ConcurrentHashMap<>();
    private static final Map<String, RenderTransform> PERSIST_TYPE = new ConcurrentHashMap<>();

    private static final Map<String, RenderTransform> SESSION_ITEM = new ConcurrentHashMap<>();
    private static final Map<String, RenderTransform> SESSION_TYPE = new ConcurrentHashMap<>();

    private static volatile boolean SESSION_ENABLED = false;

    public static void beginSession() {
        SESSION_ITEM.clear();
        SESSION_TYPE.clear();
        SESSION_ENABLED = true;
    }

    public static void clearSession() {
        SESSION_ITEM.clear();
        SESSION_TYPE.clear();
        SESSION_ENABLED = true; // keep enabled; just wipe deltas
    }

    public static void endSession() {
        SESSION_ITEM.clear();
        SESSION_TYPE.clear();
        SESSION_ENABLED = false;
    }

    public static boolean isSessionEnabled() {
        return SESSION_ENABLED;
    }

    public static void replaceAll(List<RenderOverrideEntry> entries) {
        PERSIST_ITEM.clear();
        PERSIST_TYPE.clear();
        if (entries == null) return;
        for (RenderOverrideEntry e : entries) {
            if (e == null || e.key() == null || e.transform() == null) continue;
            if (e.scope() == RenderOverrideScope.ITEM) {
                PERSIST_ITEM.put(e.key(), e.transform());
            } else {
                PERSIST_TYPE.put(e.key(), e.transform());
            }
        }
    }

    public static void upsert(RenderOverrideScope scope, String key, RenderTransform t) {
        if (scope == RenderOverrideScope.ITEM) PERSIST_ITEM.put(key, t);
        else PERSIST_TYPE.put(key, t);
    }

    public static void remove(RenderOverrideScope scope, String key) {
        if (scope == RenderOverrideScope.ITEM) PERSIST_ITEM.remove(key);
        else PERSIST_TYPE.remove(key);
    }

    public static void setSession(RenderOverrideScope scope, String key, RenderTransform t) {
        if (!SESSION_ENABLED) return;
        if (scope == RenderOverrideScope.ITEM) SESSION_ITEM.put(key, t);
        else SESSION_TYPE.put(key, t);
    }

    public static void removeSession(RenderOverrideScope scope, String key) {
        if (!SESSION_ENABLED) return;
        if (scope == RenderOverrideScope.ITEM) SESSION_ITEM.remove(key);
        else SESSION_TYPE.remove(key);
    }

    public static RenderTransform getPersistent(RenderOverrideScope scope, String key) {
        return scope == RenderOverrideScope.ITEM ? PERSIST_ITEM.get(key) : PERSIST_TYPE.get(key);
    }

    public static RenderTransform getSession(RenderOverrideScope scope, String key) {
        return scope == RenderOverrideScope.ITEM ? SESSION_ITEM.get(key) : SESSION_TYPE.get(key);
    }

    /** Resolve final transform for a specific stack using precedence SESSION->ITEM->TYPE->DEFAULT. */
    public static Resolved resolve(ItemStack stack, ShopItemRenderType detectedType) {
        if (stack == null || stack.isEmpty()) return new Resolved(RenderTransform.IDENTITY, ActiveSource.DEFAULT);

        String itemKey = itemKey(stack);

        if (SESSION_ENABLED) {
            RenderTransform si = SESSION_ITEM.get(itemKey);
            if (si != null) return new Resolved(si, ActiveSource.SESSION_ITEM);
            if (detectedType != null) {
                RenderTransform st = SESSION_TYPE.get(detectedType.id());
                if (st != null) return new Resolved(st, ActiveSource.SESSION_TYPE);
            }
        }

        RenderTransform pi = PERSIST_ITEM.get(itemKey);
        if (pi != null) return new Resolved(pi, ActiveSource.ITEM);
        if (detectedType != null) {
            RenderTransform pt = PERSIST_TYPE.get(detectedType.id());
            if (pt != null) return new Resolved(pt, ActiveSource.TYPE);
        }

        return new Resolved(RenderTransform.IDENTITY, ActiveSource.DEFAULT);
    }

    public static String itemKey(ItemStack stack) {
        try {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id == null ? "minecraft:air" : id.toString();
        } catch (Throwable ignored) {
            return "minecraft:air";
        }
    }
}

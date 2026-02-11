package com.roften.avilixeconomy.util;

import com.roften.avilixeconomy.AvilixEconomy;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

/**
 * Central place for PermissionAPI nodes.
 *
 * LuckPerms can grant these nodes (e.g. via lp group ... permission set ... true).
 */
public final class Permissions {

    private Permissions() {}

    /** Admin bypass: open/configure ANY shop (ignores ownership checks). */
    public static final PermissionNode<Boolean> SHOP_OPEN_ANY = new PermissionNode<>(
            AvilixEconomy.MODID,
            "shop.open_any",
            PermissionTypes.BOOLEAN,
            // Signature in NeoForge 21.x: (player, playerUUID, context) -> defaultValue
            // If no external permission provider is installed (e.g., LuckPerms),
            // fall back to vanilla OP level checks so admins can still use tools.
            (player, playerUUID, ctx) -> player != null && player.hasPermissions(2)
    );

    /** Admin: allow real-time tuning of item render transforms on shop shelves. */
    public static final PermissionNode<Boolean> SHOP_RENDER_EDIT = new PermissionNode<>(
            AvilixEconomy.MODID,
            "shop.render_edit",
            PermissionTypes.BOOLEAN,
            // Same rationale as SHOP_OPEN_ANY: without a permission provider the node resolver
            // would return the default. We want server ops to be able to tune renders by default.
            (player, playerUUID, ctx) -> player != null && player.hasPermissions(2)
    );

    /** NeoForge hook: declare nodes so permission providers (LuckPerms) can see them. */
    public static void gatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(SHOP_OPEN_ANY, SHOP_RENDER_EDIT);
    }

    public static boolean canOpenAnyShop(ServerPlayer player) {
        try {
            return PermissionAPI.getPermission(player, SHOP_OPEN_ANY);
        } catch (Throwable ignored) {
            // If PermissionAPI isn't fully available for some reason, keep vanilla OP fallback.
            return player != null && player.hasPermissions(2);
        }
    }

    public static boolean canEditShopRender(ServerPlayer player) {
        try {
            return PermissionAPI.getPermission(player, SHOP_RENDER_EDIT);
        } catch (Throwable ignored) {
            return player != null && player.hasPermissions(2);
        }
    }
}

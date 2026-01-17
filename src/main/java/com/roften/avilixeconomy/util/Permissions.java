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
            (player, playerUUID, ctx) -> false
    );

    /** NeoForge hook: declare nodes so permission providers (LuckPerms) can see them. */
    public static void gatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(SHOP_OPEN_ANY);
    }

    public static boolean canOpenAnyShop(ServerPlayer player) {
        try {
            return PermissionAPI.getPermission(player, SHOP_OPEN_ANY);
        } catch (Throwable ignored) {
            return false;
        }
    }
}

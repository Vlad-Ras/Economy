package com.roften.avilixeconomy.compat;

import com.roften.avilixeconomy.AvilixEconomy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Optional compatibility layer for "Open Parties and Claims".
 *
 * Uses reflection so the mod stays optional at compile/runtime.
 */
public final class OpenPacCompat {

    private OpenPacCompat() {
    }

    private static boolean loggedFailure;

    public static boolean isLoaded() {
        return ModList.get().isLoaded("openpartiesandclaims");
    }

    /**
     * @return true if OPAC says this interaction should be protected/blocked.
     */
    public static boolean isInteractionProtected(ServerPlayer player, ServerLevel level, BlockPos pos, Direction direction, InteractionHand hand) {
        if (!isLoaded()) return false;

        try {
            // OpenPACServerAPI.get(MinecraftServer)
            Class<?> apiClz = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Method getApi = apiClz.getMethod("get", net.minecraft.server.MinecraftServer.class);
            Object api = getApi.invoke(null, level.getServer());

            // IChunkProtectionAPI protection = api.getChunkProtection();
            Method getChunkProtection = apiClz.getMethod("getChunkProtection");
            Object protection = getChunkProtection.invoke(api);

            // boolean onBlockInteraction(Entity entity, InteractionHand hand, ItemStack heldItem, ServerLevel world,
            //                            BlockPos pos, Direction direction, boolean breaking, boolean messages, boolean targetExceptions)
            Method onBlockInteraction = protection.getClass().getMethod(
                    "onBlockInteraction",
                    net.minecraft.world.entity.Entity.class,
                    net.minecraft.world.InteractionHand.class,
                    net.minecraft.world.item.ItemStack.class,
                    net.minecraft.server.level.ServerLevel.class,
                    net.minecraft.core.BlockPos.class,
                    net.minecraft.core.Direction.class,
                    boolean.class,
                    boolean.class,
                    boolean.class
            );

            ItemStack held = player.getItemInHand(hand);
            Object res = onBlockInteraction.invoke(
                    protection,
                    player,
                    hand,
                    held,
                    level,
                    pos,
                    direction,
                    false,
                    true,
                    true
            );
            return (res instanceof Boolean b) && b;
        } catch (Throwable t) {
            if (!loggedFailure) {
                loggedFailure = true;
                AvilixEconomy.LOGGER.warn("OpenPAC detected but API call failed; falling back to allow interaction.", t);
            }
            return false;
        }
    }

    public static boolean canInteract(ServerPlayer player, ServerLevel level, BlockPos pos, Direction direction, InteractionHand hand) {
        return !isInteractionProtected(player, level, pos, direction, hand);
    }
}

package com.roften.avilixeconomy.shop;

import com.roften.avilixeconomy.registry.ModBlocks;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.UUID;

/**
 * Prevents accidental breaking of the shop block in Creative mode.
 * In Creative, the shop can only be broken while sneaking (Shift).
 */
public final class ShopBreakProtectionEvents {

    private static final long MSG_COOLDOWN_MS = 800L;
    private static final Object2LongOpenHashMap<UUID> LAST_MSG = new Object2LongOpenHashMap<>();

    private static boolean isCreativeNoSneak(Player player) {
        return player != null && player.getAbilities().instabuild && !player.isShiftKeyDown();
    }

    private static boolean isShop(Level level, BlockPos pos, BlockState state) {
        return state != null && state.is(ModBlocks.SHOP.get());
    }

    private static void maybeNotify(Player player) {
        long now = System.currentTimeMillis();
        UUID id = player.getUUID();
        long last = LAST_MSG.getOrDefault(id, 0L);
        if (now - last < MSG_COOLDOWN_MS) return;
        LAST_MSG.put(id, now);
        player.displayClientMessage(Component.translatable("msg.avilixeconomy.shop.break_protected"), true);
    }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;

        Player player = event.getEntity();
        if (!isCreativeNoSneak(player)) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!isShop(level, pos, state)) return;

        maybeNotify(player);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // server-side only
        if (player.level().isClientSide()) return;

        if (!isCreativeNoSneak(player)) return;
        if (!event.getState().is(ModBlocks.SHOP.get())) return;

        maybeNotify(player);
        event.setCanceled(true);
    }
}

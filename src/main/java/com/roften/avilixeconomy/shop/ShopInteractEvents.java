package com.roften.avilixeconomy.shop;

import com.roften.avilixeconomy.compat.OpenPacCompat;
import com.roften.avilixeconomy.shop.blockentity.ShopBlockEntity;
import com.roften.avilixeconomy.shop.menu.ShopConfigMenu;
import com.roften.avilixeconomy.util.Permissions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Handles interactions that must work even when the player holds an item.
 */
public final class ShopInteractEvents {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!event.getEntity().isShiftKeyDown()) return;

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        if (!(serverLevel.getBlockEntity(pos) instanceof ShopBlockEntity shop)) return;

        // Owner or admin bypass can open the config UI.
        if (!shop.isOwner(player) && !Permissions.canOpenAnyShop(player)) {
            player.displayClientMessage(Component.translatable("msg.avilixeconomy.shop.not_owner"), true);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }

        // Respect OpenPac/parties protection unless admin bypass.
        if (!Permissions.canOpenAnyShop(player)
                && !OpenPacCompat.canInteract(player, serverLevel, pos, event.getFace(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }

        MenuProvider provider = new SimpleMenuProvider(
                (id, inv, p) -> ShopConfigMenu.create(id, inv, shop),
                Component.translatable("screen.avilixeconomy.shop.config.title")
        );
        player.openMenu(provider, buf -> buf.writeBlockPos(pos));

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }
}

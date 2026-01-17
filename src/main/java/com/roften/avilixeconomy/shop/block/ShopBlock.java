package com.roften.avilixeconomy.shop.block;

import com.mojang.serialization.MapCodec;
import com.roften.avilixeconomy.EconomyData;
import com.roften.avilixeconomy.compat.OpenPacCompat;
import com.roften.avilixeconomy.shop.blockentity.ShopBlockEntity;
import com.roften.avilixeconomy.shop.menu.ShopBuyMenu;
import com.roften.avilixeconomy.shop.menu.ShopConfigMenu;
import com.roften.avilixeconomy.util.Permissions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;


public class ShopBlock extends BaseEntityBlock {

    public static final MapCodec<ShopBlock> CODEC = simpleCodec(ShopBlock::new);

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public ShopBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShopBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // no ticking needed
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        if (!(placer instanceof ServerPlayer player)) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ShopBlockEntity shop) {
            shop.setOwner(player.getUUID());
            shop.setOwnerName(player.getGameProfile().getName());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Empty-hand interaction opens either config (shift) or buy/sell screen.
        // Shift+RightClick with an item in hand is handled via PlayerInteractEvent (see ShopInteractEvents).
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }

        // Admin bypass (LuckPerms / PermissionAPI)
        if (!Permissions.canOpenAnyShop(serverPlayer)
                && !OpenPacCompat.canInteract(serverPlayer, serverLevel, pos, hit.getDirection(), InteractionHand.MAIN_HAND)) {
            return InteractionResult.FAIL;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ShopBlockEntity shop)) {
            return InteractionResult.CONSUME;
        }

        boolean owner = shop.isOwner(serverPlayer) || Permissions.canOpenAnyShop(serverPlayer);
        boolean wantsConfig = serverPlayer.isShiftKeyDown();

        if (wantsConfig) {
            if (!owner) {
                serverPlayer.displayClientMessage(Component.translatable("msg.avilixeconomy.shop.not_owner"), true);
                return InteractionResult.CONSUME;
            }

            MenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> ShopConfigMenu.create(id, inv, shop),
                    Component.translatable("screen.avilixeconomy.shop.config.title")
            );
            serverPlayer.openMenu(provider, buf -> buf.writeBlockPos(pos));
            return InteractionResult.CONSUME;
        }

                // Warm up owner balance cache once to avoid SQL spam from menu sync (available lots).
        if (shop.getOwner() != null) {
            EconomyData.warmupBalance(shop.getOwner());
        }

MenuProvider provider = new SimpleMenuProvider(
                (id, inv, p) -> ShopBuyMenu.create(id, inv, shop),
                Component.translatable("screen.avilixeconomy.shop.buy.title")
        );
        serverPlayer.openMenu(provider, buf -> buf.writeBlockPos(pos));
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Force-drop the shop block item when a player breaks it (recipe/loot-table independent).
        // Vanilla axes can still mine it faster through the standard mineable/axe tag.
        if (!level.isClientSide && !player.isCreative()) {
            Containers.dropItemStack(level,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    new ItemStack(this));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ShopBlockEntity shop) {
                // Drop stock when the block is broken.
                if (!level.isClientSide) {
                    for (int i = 0; i < shop.getTemplate().getSlots(); i++) {
                        ItemStack s = shop.getTemplate().getStackInSlot(i);
                        if (!s.isEmpty()) {
                            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s.copy());
                        }
                    }
                    for (int i = 0; i < shop.getStock().getSlots(); i++) {
                        ItemStack s = shop.getStock().getStackInSlot(i);
                        if (!s.isEmpty()) {
                            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s.copy());
                        }
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}

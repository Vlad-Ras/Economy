package com.roften.avilixeconomy.compat.jade;

import com.roften.avilixeconomy.shop.blockentity.ShopBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Adds a line to the Jade tooltip showing whether the shop is selling or buying.
 */
public enum ShopModeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String KEY_BUY_MODE = "avilixeconomy_buy_mode";

    @Override
    public ResourceLocation getUid() {
        return AvilixEconomyJadePlugin.SHOP_MODE_UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        boolean buy = false;
        CompoundTag data = accessor.getServerData();
        if (data != null && data.contains(KEY_BUY_MODE)) {
            buy = data.getBoolean(KEY_BUY_MODE);
        } else if (accessor.getBlockEntity() instanceof ShopBlockEntity shop) {
            // Fallback (client-side) in case Jade isn't installed on the server.
            buy = shop.isBuyMode();
        }

        tooltip.add(buy
                ? Component.translatable("avilixeconomy.jade.shop.buy")
                : Component.translatable("avilixeconomy.jade.shop.sell"));
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ShopBlockEntity shop) {
            data.putBoolean(KEY_BUY_MODE, shop.isBuyMode());
        }
    }
}

package com.roften.avilixeconomy.compat.jade;

import com.roften.avilixeconomy.shop.block.ShopBlock;
import com.roften.avilixeconomy.shop.blockentity.ShopBlockEntity;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade integration.
 *
 * Shows whether the shop is in "sell" or "buy" mode.
 *
 * Jade docs: https://jademc.readthedocs.io/en/latest/ (plugins: 1.20-1.21.5)
 */
@WailaPlugin
public class AvilixEconomyJadePlugin implements IWailaPlugin {

    // In 1.21.x the ResourceLocation constructor is not public; use the factory.
    public static final ResourceLocation SHOP_MODE_UID = ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_mode");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ShopModeProvider.INSTANCE, ShopBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // Register for the shop block so the tooltip shows when looking at the block.
        registration.registerBlockComponent(ShopModeProvider.INSTANCE, ShopBlock.class);
    }
}

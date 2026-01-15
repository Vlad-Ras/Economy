package com.roften.avilixeconomy.client;

import com.roften.avilixeconomy.registry.ModBlockEntities;
import com.roften.avilixeconomy.registry.ModBlocks;
import com.roften.avilixeconomy.registry.ModMenus;
import com.roften.avilixeconomy.shop.client.ShopBlockEntityRenderer;
import com.roften.avilixeconomy.trade.screen.TradeScreen;
import com.roften.avilixeconomy.shop.screen.ShopBuyScreen;
import com.roften.avilixeconomy.shop.screen.ShopConfigScreen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Клиентские ModBus-события. Регистрируются только на CLIENT.
 */
public final class ClientModEvents {

    private ClientModEvents() {
    }

    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.TRADE_MENU.get(), TradeScreen::new);
        event.register(ModMenus.SHOP_BUY_MENU.get(), ShopBuyScreen::new);
        event.register(ModMenus.SHOP_CONFIG_MENU.get(), ShopConfigScreen::new);
    }

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SHOP.get(), ShopBlockEntityRenderer::new);
    }

    /**
     * Client setup hook.
     * We need CUTOUT layer for the shop side texture which contains transparent "window" pixels.
     */
    public static void onClientSetup(FMLClientSetupEvent event) {
        // cutoutMipped is more stable with mipmaps for textures that contain transparent pixels
        // (prevents "black" artifacts on some settings).
        event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModBlocks.SHOP.get(), RenderType.cutoutMipped()));
    }
}

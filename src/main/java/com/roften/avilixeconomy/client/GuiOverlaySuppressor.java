package com.roften.avilixeconomy.client;

import com.roften.avilixeconomy.shop.screen.ShopBuyScreen;
import com.roften.avilixeconomy.shop.screen.ShopConfigScreen;
import com.roften.avilixeconomy.trade.screen.TradeScreen;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Скрывает сторонние HUD-оверлеи (например FTB Quests), пока открыты наши экраны магазина/трейда.
 * Мы НЕ трогаем рендер самого GUI — только глобальный HUD слой.
 */
public class GuiOverlaySuppressor {

    private static boolean shouldSuppress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return false;
        return mc.screen instanceof ShopBuyScreen
                || mc.screen instanceof ShopConfigScreen
                || mc.screen instanceof TradeScreen;
    }

    @SubscribeEvent
    public void onRenderGuiPre(RenderGuiEvent.Pre event) {
        // RenderGuiEvent.Pre срабатывает для HUD-слоя. Иногда сторонние моды рисуют HUD
        // даже поверх открытых экранов — режем это здесь.
        if (shouldSuppress()) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onRenderLayerPre(RenderGuiLayerEvent.Pre event) {
        // В NeoForge 1.21.x HUD/оверлеи рисуются как «GUI layers». FTB Quests pinned icons
        // находятся именно там, поэтому глушим слой на время, пока открыт наш экран.
        // Наш Screen (магазин/трейд) при этом продолжает рисоваться.
        if (shouldSuppress()) event.setCanceled(true);
    }
}

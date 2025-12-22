package com.roften.avilixeconomy.client;

import com.roften.avilixeconomy.registry.ModMenus;
import com.roften.avilixeconomy.trade.screen.TradeScreen;
import com.roften.avilixeconomy.shop.screen.ShopBuyScreen;
import com.roften.avilixeconomy.shop.screen.ShopConfigScreen;
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
}

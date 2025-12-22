package com.roften.avilixeconomy.registry;

import com.roften.avilixeconomy.AvilixEconomy;
import com.roften.avilixeconomy.shop.menu.ShopBuyMenu;
import com.roften.avilixeconomy.shop.menu.ShopConfigMenu;
import com.roften.avilixeconomy.trade.menu.TradeMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModMenus {

    private ModMenus() {
    }

    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, AvilixEconomy.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<TradeMenu>> TRADE_MENU =
            MENUS.register("trade", () -> IMenuTypeExtension.create(TradeMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ShopBuyMenu>> SHOP_BUY_MENU =
            MENUS.register("shop_buy", () -> IMenuTypeExtension.create(ShopBuyMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ShopConfigMenu>> SHOP_CONFIG_MENU =
            MENUS.register("shop_config", () -> IMenuTypeExtension.create(ShopConfigMenu::new));

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}

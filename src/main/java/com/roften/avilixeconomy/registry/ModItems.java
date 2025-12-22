package com.roften.avilixeconomy.registry;

import com.roften.avilixeconomy.AvilixEconomy;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item registry. */
public final class ModItems {

    private ModItems() {
    }

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, AvilixEconomy.MODID);

    public static final DeferredHolder<Item, Item> SHOP =
            ITEMS.register("shop", () -> new BlockItem(ModBlocks.SHOP.get(), new Item.Properties()));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}

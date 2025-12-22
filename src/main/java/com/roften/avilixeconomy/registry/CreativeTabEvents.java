package com.roften.avilixeconomy.registry;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Adds mod items to vanilla creative tabs.
 */
public final class CreativeTabEvents {

    private CreativeTabEvents() {
    }

    public static void buildTabContents(BuildCreativeModeTabContentsEvent event) {
        // Put the Shop block into the Functional Blocks tab.
        if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) {
            event.accept(ModItems.SHOP.get());
        }

        // No custom tool item: vanilla axes are the preferred way to break shop blocks faster.
    }
}

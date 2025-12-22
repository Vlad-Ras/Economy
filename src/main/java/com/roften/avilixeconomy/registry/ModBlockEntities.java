package com.roften.avilixeconomy.registry;

import com.roften.avilixeconomy.AvilixEconomy;
import com.roften.avilixeconomy.shop.blockentity.ShopBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {

    private ModBlockEntities() {
    }

    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AvilixEconomy.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShopBlockEntity>> SHOP =
            BLOCK_ENTITIES.register("shop", () -> BlockEntityType.Builder.of(ShopBlockEntity::new, ModBlocks.SHOP.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Provide item handler capability to support hoppers / Create funnels.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SHOP.get(), (be, side) -> be.getAutomationItemHandler(side));
    }
}

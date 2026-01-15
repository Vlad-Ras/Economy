package com.roften.avilixeconomy.registry;

import com.roften.avilixeconomy.AvilixEconomy;
import com.roften.avilixeconomy.shop.block.ShopBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block registry. */
public final class ModBlocks {

    private ModBlocks() {
    }

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, AvilixEconomy.MODID);

    public static final DeferredHolder<Block, ShopBlock> SHOP =
            BLOCKS.register("shop", () -> new ShopBlock(
                    BlockBehaviour.Properties.of()
                            .strength(2.5F)
                            .sound(SoundType.WOOD)
                            // The shop model is not a full cube (it has "legs").
                            // If we keep occlusion enabled, Minecraft will cull the top face
                            // of the block below, which looks like an "invisible" block under the shop.
                            .noOcclusion()
            ));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}

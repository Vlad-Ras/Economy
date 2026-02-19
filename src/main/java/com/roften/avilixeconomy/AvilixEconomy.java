package com.roften.avilixeconomy;

import com.mojang.logging.LogUtils;
import com.roften.avilixeconomy.network.NetworkRegistration;
import com.roften.avilixeconomy.config.AvilixEconomyCommonConfig;
import com.roften.avilixeconomy.registry.ModBlockEntities;
import com.roften.avilixeconomy.registry.ModBlocks;
import com.roften.avilixeconomy.registry.ModItems;
import com.roften.avilixeconomy.registry.ModMenus;
import com.roften.avilixeconomy.registry.CreativeTabEvents;
import com.roften.avilixeconomy.shop.ShopInteractEvents;
import com.roften.avilixeconomy.shop.ShopBreakProtectionEvents;
import com.roften.avilixeconomy.pricing.MinPriceManager;
import com.roften.avilixeconomy.util.Permissions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;

@Mod(AvilixEconomy.MODID)
public class AvilixEconomy {

    public static final String MODID = "avilixeconomy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AvilixEconomy(IEventBus modBus, ModContainer container) {
        // -------- CONFIG --------
        container.registerConfig(ModConfig.Type.COMMON, AvilixEconomyCommonConfig.SPEC);


        // -------- REGISTRIES --------
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        modBus.addListener(CreativeTabEvents::buildTabContents);

        // capabilities (hopper/Create compatibility for item storage)
        modBus.addListener(this::registerCapabilities);


        // -------- SERVER EVENTS --------
        NeoForge.EVENT_BUS.register(new EconomyEvents());
        NeoForge.EVENT_BUS.register(new ShopInteractEvents());
        NeoForge.EVENT_BUS.register(new ShopBreakProtectionEvents());
        NeoForge.EVENT_BUS.addListener(EconomyCommands::register);
        NeoForge.EVENT_BUS.addListener(this::reloadMinPrices);

        // LuckPerms / PermissionAPI nodes
        // LuckPerms (and any permission provider) can grant this node to bypass shop ownership.
        // PermissionGatherEvent.Nodes is fired on the NeoForge bus, not the mod event bus.
        NeoForge.EVENT_BUS.addListener(Permissions::gatherNodes);

        // -------- CLIENT --------
        if (FMLEnvironment.dist.isClient()) {
            ClientInit.init(modBus);
        }

        // -------- NETWORK --------
        modBus.addListener(NetworkRegistration::register);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        ModBlockEntities.registerCapabilities(event);
    }


    private void reloadMinPrices(ServerStartingEvent event) {
        try {
            MinPriceManager.reload();
            String err = MinPriceManager.getLastError();
            if (err != null) {
                LOGGER.warn("Failed to load min_prices.json: {}", err);
            } else {
                LOGGER.info("Min prices loaded: {} entries", MinPriceManager.snapshot().size());
            }
        } catch (Exception ex) {
            LOGGER.warn("Failed to load min_prices.json", ex);
        }
    }
}

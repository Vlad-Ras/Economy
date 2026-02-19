package com.roften.avilixeconomy;

import com.roften.avilixeconomy.client.ClientModEvents;
import com.roften.avilixeconomy.client.EconomyHud;
import com.roften.avilixeconomy.client.HudPositionConfig;
import com.roften.avilixeconomy.client.GuiOverlaySuppressor;
import com.roften.avilixeconomy.client.hud.HudMoveController;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Клиентская инициализация. Вызывается из основного класса мода, но только на CLIENT.
 */
public final class ClientInit {

    private ClientInit() {
    }

    public static void init(IEventBus modBus) {
        // Register client-only ModBus events
        modBus.addListener(ClientModEvents::onRegisterScreens);
        modBus.addListener(ClientModEvents::onRegisterRenderers);
        modBus.addListener(ClientModEvents::onClientSetup);

        // Keybinds (ModBus)
        modBus.addListener(HudMoveController::onRegisterKeyMappings);

        // Register HUD (game event bus)
        NeoForge.EVENT_BUS.register(new EconomyHud());

        // Hide external HUD overlays while our GUI is open (FTB Quests, etc.)
        NeoForge.EVENT_BUS.register(new GuiOverlaySuppressor());

        // HUD position controller + load persisted coords
        HudPositionConfig.load();
        NeoForge.EVENT_BUS.register(new HudMoveController());
    }
}

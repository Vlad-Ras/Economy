package com.roften.avilixeconomy;

import com.roften.avilixeconomy.client.ClientModEvents;
import com.roften.avilixeconomy.client.EconomyHud;
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

        // Register HUD (game event bus)
        NeoForge.EVENT_BUS.register(new EconomyHud());
    }
}

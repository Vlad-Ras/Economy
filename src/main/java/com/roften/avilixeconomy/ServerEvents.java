package com.roften.avilixeconomy;

import com.roften.avilixeconomy.database.DatabaseManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.*;
import com.roften.avilixeconomy.AvilixEconomy;

public class ServerEvents {

    public ServerEvents() {
        NeoForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
    }

    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        AvilixEconomy.LOGGER.info("ServerAboutToStart — сервер создаётся");
    }

    private void onServerStarting(ServerStartingEvent event) {
        AvilixEconomy.LOGGER.info("ServerStarting — запускаем миры");
    }

    private void onServerStarted(ServerStartedEvent event) {
        AvilixEconomy.LOGGER.info("ServerStarted — сервер полностью запущен");
    }

    private void onServerStopping(ServerStoppingEvent event) {
        AvilixEconomy.LOGGER.info("ServerStopping — сервер начинает выключение");
        DatabaseManager.shutdown(); // <-- здесь корректно закрывать соединение
    }

    private void onServerStopped(ServerStoppedEvent event) {
        AvilixEconomy.LOGGER.info("ServerStopped — сервер выключен");
    }
}

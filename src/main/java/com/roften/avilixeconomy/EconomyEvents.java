package com.roften.avilixeconomy;

import com.roften.avilixeconomy.database.DatabaseManager;
import com.roften.avilixeconomy.config.AvilixEconomyCommonConfig;
import com.roften.avilixeconomy.util.MoneyUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.UUID;

public class EconomyEvents {
    

    public EconomyEvents() {
        NeoForge.EVENT_BUS.register(this);
    }

    // =============================
    // РАННИЙ запуск сервера → ИНИЦ СУБД
    // =============================
    @SubscribeEvent
    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        System.out.println("[Economy] Инициализация DatabaseManager...");
        DatabaseManager.init();

        // Load shelf render overrides (admin tuning)
        com.roften.avilixeconomy.shop.render.RenderOverrideManager.reloadFromDb();

        // Ensure server commission account exists (after DB init)
        try {
            java.util.UUID serverUuid = java.util.UUID.fromString(AvilixEconomyCommonConfig.ECONOMY.serverAccountUuid.get());
            String serverName = AvilixEconomyCommonConfig.ECONOMY.serverAccountName.get();
            double serverBal = MoneyUtils.round2(AvilixEconomyCommonConfig.ECONOMY.serverAccountStartBalance.get());
            DatabaseManager.ensurePlayerRecord(serverUuid, serverName, serverBal);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        DatabaseManager.shutdown();
    }

    // =============================
    // Игрок зашёл → создаём запись + кэш
    // =============================
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent e) {
        var player = e.getEntity();
        UUID uuid = player.getUUID();
        String name = player.getName().getString();

        try {

            boolean exists = DatabaseManager.recordExists(uuid);

            if (!exists) {
                // создаём
                DatabaseManager.createPlayerRecord(uuid, name, MoneyUtils.round2(AvilixEconomyCommonConfig.ECONOMY.startBalance.get()));
                EconomyData.updateCache(uuid, MoneyUtils.round2(AvilixEconomyCommonConfig.ECONOMY.startBalance.get()));

            } else {
                // обновляем имя
                DatabaseManager.updatePlayerName(uuid, name);

                double bal = DatabaseManager.getBalanceDirect(uuid);
                EconomyData.updateCache(uuid, bal);
            }

            // ОБЯЗАТЕЛЬНО отправить баланс на клиент
            EconomyData.sendBalanceUpdateToPlayer(uuid);

            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                // Also send render override snapshot (client cache)
                try {
                    var entries = com.roften.avilixeconomy.shop.render.RenderOverrideManager.snapshotAll();
                    sp.connection.send(new com.roften.avilixeconomy.network.NetworkRegistration.ShopRenderOverridesSyncPayload(entries));
                } catch (Throwable ignored) {
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

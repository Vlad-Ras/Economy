package com.roften.avilixeconomy;

import com.roften.avilixeconomy.database.DatabaseManager;
import com.roften.avilixeconomy.config.AvilixEconomyCommonConfig;
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
        // Ensure server commission account exists
        try {
            java.util.UUID serverUuid = java.util.UUID.fromString(AvilixEconomyCommonConfig.ECONOMY.serverAccountUuid.get());
            String serverName = AvilixEconomyCommonConfig.ECONOMY.serverAccountName.get();
            long serverBal = AvilixEconomyCommonConfig.ECONOMY.serverAccountStartBalance.get();
            DatabaseManager.ensurePlayerRecord(serverUuid, serverName, serverBal);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("[Economy] Инициализация DatabaseManager...");
        DatabaseManager.init();
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
                DatabaseManager.createPlayerRecord(uuid, name, AvilixEconomyCommonConfig.ECONOMY.startBalance.get());
                EconomyData.updateCache(uuid, AvilixEconomyCommonConfig.ECONOMY.startBalance.get());

            } else {
                // обновляем имя
                DatabaseManager.updatePlayerName(uuid, name);

                long bal = DatabaseManager.getBalanceDirect(uuid);
                EconomyData.updateCache(uuid, bal);
            }

            // ОБЯЗАТЕЛЬНО отправить баланс на клиент
            EconomyData.sendBalanceUpdateToPlayer(uuid);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

package com.roften.avilixeconomy.network;

import com.roften.avilixeconomy.AvilixEconomy;
import com.roften.avilixeconomy.client.ClientBalanceData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NetworkUtils {

    private NetworkUtils() {}

    // Идентификатор канала (НЕ пакет!)
    public static final ResourceLocation CHANNEL_ID =
            ResourceLocation.fromNamespaceAndPath(AvilixEconomy.MODID, "main");

    /**
     * Регистрируем все payload-пакеты NeoForge 1.21.x
     */
    public static void register(RegisterPayloadHandlersEvent event) {

        event.registrar(CHANNEL_ID.getNamespace()) // = "avilixeconomy"
                .playToClient(
                        NetworkRegistration.BalancePayload.TYPE,      // тип пакета
                        NetworkRegistration.BalancePayload.CODEC,     // наш StreamCodec
                        (payload, context) -> {
                            // --- ОБРАБОТКА НА КЛИЕНТЕ ---
                            ClientBalanceData.setBalance(payload.balance());
                        }
                );

        AvilixEconomy.LOGGER.info("[Network] Payload handlers registered!");
    }

    public static void sendBalanceToPlayer(ServerPlayer player, long balance) {
        try {
            NetworkRegistration.BalancePayload payload = new NetworkRegistration.BalancePayload(balance);
            player.connection.send(payload);
        } catch (Exception e) {
            AvilixEconomy.LOGGER.error("[Network] Failed to send balance payload", e);
        }
    }

}

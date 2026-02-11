package com.roften.avilixeconomy.network;

import com.roften.avilixeconomy.client.ClientBalanceData;
import com.roften.avilixeconomy.trade.TradeManager;
import com.roften.avilixeconomy.trade.TradeSession;
import com.roften.avilixeconomy.trade.screen.TradeClientState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import com.roften.avilixeconomy.EconomyData;
import com.roften.avilixeconomy.shop.blockentity.ShopBlockEntity;
import com.roften.avilixeconomy.shop.menu.ShopBuyMenu;
import com.roften.avilixeconomy.shop.menu.ShopConfigMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import com.roften.avilixeconomy.database.DatabaseManager;
import com.roften.avilixeconomy.shop.screen.ShopClientState;
import com.roften.avilixeconomy.shop.screen.ShopToastState;
import com.roften.avilixeconomy.shop.client.ShopRenderOverridesClient;
import com.roften.avilixeconomy.shop.render.RenderOverrideEntry;
import com.roften.avilixeconomy.shop.render.RenderOverrideManager;
import com.roften.avilixeconomy.shop.render.RenderOverrideScope;
import com.roften.avilixeconomy.shop.render.RenderTransform;
import com.roften.avilixeconomy.util.Permissions;
import java.util.ArrayList;
import java.util.List;

public class NetworkRegistration {

    public static final String MODID = "avilixeconomy";



    public static final ResourceLocation BALANCE_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "balance");

    public static final ResourceLocation TRADE_STATE_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "trade_state");
    public static final ResourceLocation TRADE_UPDATE_MONEY_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "trade_update_money");
    public static final ResourceLocation TRADE_TOGGLE_READY_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "trade_toggle_ready");
    public static final ResourceLocation TRADE_CANCEL_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "trade_cancel");

    public static final ResourceLocation SHOP_BUY_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_buy");

    public static final ResourceLocation SHOP_SET_PRICE_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_set_price");

    public static final ResourceLocation SHOP_SET_SLOT_PRICE_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_set_slot_price");

    public static final ResourceLocation SHOP_SLOT_TRADE_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_slot_trade");

    public static final ResourceLocation SHOP_SET_MODE_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_set_mode");

    public static final ResourceLocation SHOP_SELL_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_sell");

    public static final ResourceLocation SHOP_REQUEST_SALES_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_request_sales");

    public static final ResourceLocation SHOP_SALES_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_sales");

    public static final ResourceLocation SHOP_RENDER_OVERRIDES_SYNC_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_render_overrides_sync");
    public static final ResourceLocation SHOP_RENDER_OVERRIDE_UPSERT_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_render_override_upsert");
    public static final ResourceLocation SHOP_RENDER_OVERRIDE_REMOVE_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_render_override_remove");

    public static final ResourceLocation SHOP_RENDER_OVERRIDE_SET_C2S_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_render_override_set");
    public static final ResourceLocation SHOP_RENDER_OVERRIDE_DELETE_C2S_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_render_override_delete");

    public static final ResourceLocation SHOP_TOAST_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_toast");

    // === P A Y L O A D ===
    
public record ShopSetModePayload(BlockPos pos, boolean buyMode) implements CustomPacketPayload {
    public static final Type<ShopSetModePayload> TYPE = new Type<>(SHOP_SET_MODE_PACKET_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopSetModePayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos());
                        buf.writeBoolean(p.buyMode());
                    },
                    buf -> new ShopSetModePayload(buf.readBlockPos(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

public record ShopSellPayload(BlockPos pos, int lots) implements CustomPacketPayload {
    public static final Type<ShopSellPayload> TYPE = new Type<>(SHOP_SELL_PACKET_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopSellPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos());
                        buf.writeInt(p.lots());
                    },
                    buf -> new ShopSellPayload(buf.readBlockPos(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

/**
 * S->C toast-style messages that are rendered above the Shop GUI (not behind it).
 * We intentionally send the component as JSON to avoid relying on internal packet codecs.
 */
public record ShopToastPayload(String messageJson, boolean success) implements CustomPacketPayload {
    public static final Type<ShopToastPayload> TYPE = new Type<>(SHOP_TOAST_PACKET_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopToastPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.messageJson());
                        buf.writeBoolean(p.success());
                    },
                    buf -> new ShopToastPayload(buf.readUtf(), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

public record BalancePayload(double balance) implements CustomPacketPayload {
        public static final Type<BalancePayload> TYPE = new Type<>(BALANCE_PACKET_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, BalancePayload> CODEC =
                StreamCodec.of(
                        (buf, payload) -> buf.writeDouble(payload.balance),
                        buf -> new BalancePayload(buf.readDouble())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ===== TRADE STATE (S->C) =====
    public record TradeStatePayload(
            int sessionId,
            String leftName,
            String rightName,
            double leftMoney,
            double rightMoney,
            boolean leftReady,
            boolean rightReady
    ) implements CustomPacketPayload {
        public static final Type<TradeStatePayload> TYPE = new Type<>(TRADE_STATE_PACKET_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, TradeStatePayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeInt(p.sessionId);
                            buf.writeUtf(p.leftName);
                            buf.writeUtf(p.rightName);
                            buf.writeDouble(p.leftMoney);
                            buf.writeDouble(p.rightMoney);
                            buf.writeBoolean(p.leftReady);
                            buf.writeBoolean(p.rightReady);
                        },
                        buf -> new TradeStatePayload(
                                buf.readInt(),
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readDouble(),
                                buf.readDouble(),
                                buf.readBoolean(),
                                buf.readBoolean()
                        )
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ===== TRADE UPDATE MONEY (C->S) =====
    public record TradeUpdateMoneyPayload(int sessionId, double money) implements CustomPacketPayload {
        public static final Type<TradeUpdateMoneyPayload> TYPE = new Type<>(TRADE_UPDATE_MONEY_PACKET_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, TradeUpdateMoneyPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeInt(p.sessionId);
                            buf.writeDouble(p.money);
                        },
                        buf -> new TradeUpdateMoneyPayload(buf.readInt(), buf.readDouble())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ===== TRADE TOGGLE READY (C->S) =====
    public record TradeToggleReadyPayload(int sessionId) implements CustomPacketPayload {
        public static final Type<TradeToggleReadyPayload> TYPE = new Type<>(TRADE_TOGGLE_READY_PACKET_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, TradeToggleReadyPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeInt(p.sessionId),
                        buf -> new TradeToggleReadyPayload(buf.readInt())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ===== TRADE CANCEL (C->S) =====
    public record TradeCancelPayload(int sessionId) implements CustomPacketPayload {
        public static final Type<TradeCancelPayload> TYPE = new Type<>(TRADE_CANCEL_PACKET_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, TradeCancelPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> buf.writeInt(p.sessionId),
                        buf -> new TradeCancelPayload(buf.readInt())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }


    // ===== SHOP BUY (C->S) =====
    public record ShopBuyPayload(BlockPos pos, int lots) implements CustomPacketPayload {
        public static final Type<ShopBuyPayload> TYPE = new Type<>(SHOP_BUY_PACKET_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, ShopBuyPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeInt(p.lots());
                        },
                        buf -> new ShopBuyPayload(buf.readBlockPos(), buf.readInt())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ===== SHOP SET PRICE (C->S) =====
    public record ShopSetPricePayload(BlockPos pos, int mode, double price) implements CustomPacketPayload {
        public static final Type<ShopSetPricePayload> TYPE = new Type<>(SHOP_SET_PRICE_PACKET_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, ShopSetPricePayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeInt(p.mode());
                            buf.writeDouble(p.price());
                        },
                        buf -> new ShopSetPricePayload(buf.readBlockPos(), buf.readInt(), buf.readDouble())
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    
public static record ShopSetSlotPricePayload(BlockPos pos, int mode, int templateSlot, double price) implements CustomPacketPayload {
    public static final Type<ShopSetSlotPricePayload> TYPE = new Type<>(SHOP_SET_SLOT_PRICE_PACKET_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopSetSlotPricePayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos());
                        buf.writeInt(p.mode());
                        buf.writeVarInt(p.templateSlot());
                        buf.writeDouble(p.price());
                    },
                    buf -> new ShopSetSlotPricePayload(buf.readBlockPos(), buf.readInt(), buf.readVarInt(), buf.readDouble())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

public static record ShopSlotTradePayload(BlockPos pos, int templateSlot, int units) implements CustomPacketPayload {
    public static final Type<ShopSlotTradePayload> TYPE = new Type<>(SHOP_SLOT_TRADE_PACKET_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopSlotTradePayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos());
                        buf.writeVarInt(p.templateSlot());
                        buf.writeVarInt(p.units());
                    },
                    buf -> new ShopSlotTradePayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

// === Register ===
    public static void register(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1.0.0");

        registrar.playToClient(
                BalancePayload.TYPE,
                BalancePayload.CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> ClientBalanceData.setBalance(payload.balance())
                )
        );

        registrar.playToClient(
                TradeStatePayload.TYPE,
                TradeStatePayload.CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> TradeClientState.put(
                                payload.sessionId(),
                                new TradeClientState(
                                        payload.sessionId(),
                                        payload.leftName(),
                                        payload.rightName(),
                                        payload.leftMoney(),
                                        payload.rightMoney(),
                                        payload.leftReady(),
                                        payload.rightReady()
                                )
                        )
                )
        );

        registrar.playToClient(
                ShopToastPayload.TYPE,
                ShopToastPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
	                    // 1.21+ serializer requires a HolderLookup.Provider.
	                    // Use the player's registry access when available; otherwise fall back to EMPTY.
	                    var lookup = (context.player() != null) ? context.player().registryAccess() : net.minecraft.core.RegistryAccess.EMPTY;
                    Component c;
                    try {
	                        c = Component.Serializer.fromJson(payload.messageJson(), lookup);
                    } catch (Throwable t) {
                        c = Component.literal(payload.messageJson());
                    }
                    ShopToastState.push(c, payload.success());
                })
        );

        

registrar.playToClient(
        ShopSalesPayload.TYPE,
        ShopSalesPayload.CODEC,
        (payload, context) -> context.enqueueWork(() -> {
            List<ShopClientState.SalesEntry> rows = new ArrayList<>(payload.rows().size());
            for (var e : payload.rows()) {
                rows.add(new ShopClientState.SalesEntry(
                        e.createdAtMillis(),
                        e.tradeType(),
                        e.counterpartyName(),
                        e.lots(),
                        e.totalPrice(),
                        e.pricePerLot(),
                        e.itemsSummary()
                ));
            }
            ShopClientState.putSales(payload.pos(), payload.offset(), payload.limit(), payload.hasMore(), rows);
        })
);

        // Render overrides: sync + updates
        registrar.playToClient(
                ShopRenderOverridesSyncPayload.TYPE,
                ShopRenderOverridesSyncPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ShopRenderOverridesClient.replaceAll(payload.entries())
                )
        );

        registrar.playToClient(
                ShopRenderOverrideUpsertPayload.TYPE,
                ShopRenderOverrideUpsertPayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ShopRenderOverridesClient.upsert(payload.scope(), payload.key(), payload.transform())
                )
        );

        registrar.playToClient(
                ShopRenderOverrideRemovePayload.TYPE,
                ShopRenderOverrideRemovePayload.CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ShopRenderOverridesClient.remove(payload.scope(), payload.key())
                )
        );

        registrar.playToServer(
                TradeUpdateMoneyPayload.TYPE,
                TradeUpdateMoneyPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer sp)) return;
                    TradeSession session = TradeManager.getSession(payload.sessionId());
                    if (session == null) return;
                    session.updateMoney(sp, payload.money());
                })
        );

        registrar.playToServer(
                TradeToggleReadyPayload.TYPE,
                TradeToggleReadyPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer sp)) return;
                    TradeSession session = TradeManager.getSession(payload.sessionId());
                    if (session == null) return;
                    session.toggleReady(sp);
                })
        );

        registrar.playToServer(
                TradeCancelPayload.TYPE,
                TradeCancelPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer sp)) return;
                    TradeSession session = TradeManager.getSession(payload.sessionId());
                    if (session == null) return;
                    session.cancel("Трейд отменён.");
                })
        );

        

registrar.playToServer(
        ShopRequestSalesPayload.TYPE,
        ShopRequestSalesPayload.CODEC,
        (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof ShopConfigMenu menu)) return;
            if (!menu.getPos().equals(payload.pos())) return;
            if (!(sp.level().getBlockEntity(payload.pos()) instanceof ShopBlockEntity shop)) return;
            if (!shop.isOwner(sp) && !com.roften.avilixeconomy.util.Permissions.canOpenAnyShop(sp)) return;

            int limit = Math.max(1, Math.min(20, payload.limit()));
            int offset = Math.max(0, payload.offset());

            String worldId = sp.level().dimension().location().toString();
            var page = DatabaseManager.getShopSalesPage(worldId,
                    payload.pos().getX(),
                    payload.pos().getY(),
                    payload.pos().getZ(),
                    limit,
                    offset
            );

            List<ShopSalesEntryPayload> rows = new ArrayList<>(page.rows().size());
            for (var r : page.rows()) {
                rows.add(new ShopSalesEntryPayload(
                        r.createdAtMillis(),
                        r.tradeType(),
                        r.counterpartyName(),
                        r.lots(),
                        r.totalPrice(),
                        r.pricePerLot(),
                        r.itemsSummary()
                ));
            }

            try {
                sp.connection.send(new ShopSalesPayload(payload.pos(), limit, offset, page.hasMore(), rows));
            } catch (Exception ignored) {
            }
        })
);

registrar.playToServer(
                ShopSetPricePayload.TYPE,
                ShopSetPricePayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer sp)) return;
                    if (!(sp.containerMenu instanceof ShopConfigMenu menu)) return;
                    if (!menu.getPos().equals(payload.pos())) return;

                    if (!(sp.level().getBlockEntity(payload.pos()) instanceof ShopBlockEntity shop)) return;
                    if (!shop.isOwner(sp) && !com.roften.avilixeconomy.util.Permissions.canOpenAnyShop(sp)) return;

                    double price = Math.max(0.0, payload.price());
                    shop.setPriceForMode(payload.mode(), price);
                    sendShopToast(sp, Component.translatable("msg.avilixeconomy.shop.price_set", com.roften.avilixeconomy.util.MoneyUtils.formatSmart(price)), true);
                })
        );


registrar.playToServer(
        ShopSetSlotPricePayload.TYPE,
        ShopSetSlotPricePayload.CODEC,
        (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof ShopConfigMenu menu)) return;
            if (!menu.getPos().equals(payload.pos())) return;

            if (!(sp.level().getBlockEntity(payload.pos()) instanceof ShopBlockEntity shop)) return;
            if (!shop.isOwner(sp) && !com.roften.avilixeconomy.util.Permissions.canOpenAnyShop(sp)) return;

            double price = Math.max(0.0, payload.price());
            int slot = payload.templateSlot();
            shop.setSlotPriceForMode(payload.mode(), slot, price);
            sendShopToast(sp, Component.translatable("msg.avilixeconomy.shop.price_set", com.roften.avilixeconomy.util.MoneyUtils.formatSmart(price)), true);
        })
);


        registrar.playToServer(
                ShopSetModePayload.TYPE,
                ShopSetModePayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer sp)) return;
                    if (!(sp.containerMenu instanceof ShopConfigMenu menu)) return;
                    if (!menu.getPos().equals(payload.pos())) return;

                    if (!(sp.level().getBlockEntity(payload.pos()) instanceof ShopBlockEntity shop)) return;
                    if (!shop.isOwner(sp) && !com.roften.avilixeconomy.util.Permissions.canOpenAnyShop(sp)) return;

                    shop.setBuyMode(payload.buyMode());
                })
        );

        registrar.playToServer(
                ShopSellPayload.TYPE,
                ShopSellPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer sp)) return;
                    if (!(sp.containerMenu instanceof ShopBuyMenu menu)) return;
                    if (!menu.getPos().equals(payload.pos())) return;

                    if (!(sp.level().getBlockEntity(payload.pos()) instanceof ShopBlockEntity shop)) return;
                    if (!shop.isBuyMode()) return;

                    int lots = payload.lots();
                    if (lots <= 0) return;

                    shop.trySellToShop(sp, lots);
                })
        );


registrar.playToServer(
        ShopSlotTradePayload.TYPE,
        ShopSlotTradePayload.CODEC,
        (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            if (!(sp.containerMenu instanceof ShopBuyMenu menu)) return;
            if (!menu.getPos().equals(payload.pos())) return;

            if (!(sp.level().getBlockEntity(payload.pos()) instanceof ShopBlockEntity shop)) return;

            int slot = payload.templateSlot();
            int units = payload.units();
            if (slot < 0 || slot >= 9 || units <= 0) {
                sendShopToast(sp, Component.translatable("msg.avilixeconomy.shop.invalid_qty"), false);
                return;
            }

            boolean ok;
            if (shop.isBuyMode()) {
                ok = shop.trySellSlotToShop(sp, slot, units);
            } else {
                ok = shop.tryBuySlot(sp, slot, units);
            }

            if (!ok) {
                // server-side methods already toast on most errors; keep silent to avoid spam
            }
        })
);


        registrar.playToServer(
                ShopBuyPayload.TYPE,
                ShopBuyPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer sp)) return;
                    if (!(sp.containerMenu instanceof ShopBuyMenu menu)) return;
                    if (!menu.getPos().equals(payload.pos())) return;

                    if (!(sp.level().getBlockEntity(payload.pos()) instanceof ShopBlockEntity shop)) return;
                    if (shop.isBuyMode()) return; // in buy-mode you sell to shop via ShopSellPayload

                    int lots = payload.lots();
                    if (lots <= 0) {
                        sendShopToast(sp, Component.translatable("msg.avilixeconomy.shop.invalid_qty"), false);
                        return;
                    }
                    if (shop.isTemplateEmpty()) {
                        sendShopToast(sp, Component.translatable("msg.avilixeconomy.shop.not_configured"), false);
                        return;
                    }

                    int available = shop.getAvailableLots();
                    if (available <= 0) {
                        sendShopToast(sp, Component.translatable("msg.avilixeconomy.shop.out_of_stock"), false);
                        return;
                    }
                    if (lots > available) {
                        sendShopToast(sp, Component.translatable("msg.avilixeconomy.shop.too_many", available), false);
                        return;
                    }

                    double pricePer = shop.getActivePricePerLot();
                    double total = com.roften.avilixeconomy.util.MoneyUtils.round2(pricePer * (double) lots);
                    double bal = EconomyData.getBalance(sp.getUUID());
                    if (bal + 1e-9 < total) {
                        sendShopToast(sp, Component.translatable("msg.avilixeconomy.shop.not_enough_money", com.roften.avilixeconomy.util.MoneyUtils.formatSmart(total)), false);
                        return;
                    }

                    boolean ok = shop.tryBuy(sp, lots);
                    if (!ok) {
                        sendShopToast(sp, Component.translatable("msg.avilixeconomy.shop.purchase_failed"), false);
                    } else {
                        sendShopToast(sp, Component.translatable("msg.avilixeconomy.shop.purchased", lots, com.roften.avilixeconomy.util.MoneyUtils.formatSmart(total)), true);
                    }
                })
        );

        // Admin: persist/remove shelf render overrides
        registrar.playToServer(
                ShopRenderOverrideSetC2SPayload.TYPE,
                ShopRenderOverrideSetC2SPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer sp)) return;
                    if (!Permissions.canEditShopRender(sp)) return;
                    if (payload.key() == null || payload.key().isBlank()) return;
                    if (payload.transform() == null) return;

                    RenderOverrideManager.upsert(payload.scope(), payload.key(), payload.transform());
                    RenderOverrideManager.broadcastUpsert(sp.server, payload.scope(), payload.key(), payload.transform());
                })
        );

        registrar.playToServer(
                ShopRenderOverrideDeleteC2SPayload.TYPE,
                ShopRenderOverrideDeleteC2SPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer sp)) return;
                    if (!Permissions.canEditShopRender(sp)) return;
                    if (payload.key() == null || payload.key().isBlank()) return;

                    RenderOverrideManager.delete(payload.scope(), payload.key());
                    RenderOverrideManager.broadcastRemove(sp.server, payload.scope(), payload.key());
                })
        );

    }

    // === Utils ===
    public static void sendBalanceTo(ServerPlayer player, double balance) {
        player.connection.send(new BalancePayload(balance));
    }

    public static void sendTradeState(
            ServerPlayer player,
            int sessionId,
            String leftName,
            String rightName,
            double leftMoney,
            double rightMoney,
            boolean leftReady,
            boolean rightReady
    ) {
        player.connection.send(new TradeStatePayload(
                sessionId,
                leftName,
                rightName,
                leftMoney,
                rightMoney,
                leftReady,
                rightReady
        ));
    }

    /**
     * Send a toast-style message to be rendered above the Shop GUI.
     */
    public static void sendShopToast(ServerPlayer player, Component message, boolean success) {
        if (player == null || message == null) return;
        try {
	            String json = Component.Serializer.toJson(message, player.registryAccess());
            player.connection.send(new ShopToastPayload(json, success));
        } catch (Throwable t) {
            // fallback: best effort
            try {
                player.connection.send(new ShopToastPayload(message.getString(), success));
            } catch (Throwable ignored) {
            }
        }
    }


// ===== SHOP SALES HISTORY (C->S request, S->C response) =====
public record ShopRequestSalesPayload(BlockPos pos, int limit, int offset) implements CustomPacketPayload {
    public static final Type<ShopRequestSalesPayload> TYPE = new Type<>(SHOP_REQUEST_SALES_PACKET_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopRequestSalesPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos());
                        buf.writeInt(p.limit());
                        buf.writeInt(p.offset());
                    },
                    buf -> new ShopRequestSalesPayload(buf.readBlockPos(), buf.readInt(), buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

public record ShopSalesEntryPayload(long createdAtMillis, String tradeType, String counterpartyName, int lots, double totalPrice, double pricePerLot, String itemsSummary) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopSalesEntryPayload> CODEC =
        StreamCodec.of(
                (buf, e) -> {
                    buf.writeLong(e.createdAtMillis());
                        buf.writeUtf(e.tradeType(), 8);
                        buf.writeUtf(e.counterpartyName(), 32);
                        buf.writeInt(e.lots());
                        buf.writeDouble(e.totalPrice());
                        buf.writeDouble(e.pricePerLot());
                        buf.writeUtf(e.itemsSummary(), 512);
                    },
                    buf -> new ShopSalesEntryPayload(
                            buf.readLong(),
                            buf.readUtf(8),
                            buf.readUtf(32),
                            buf.readInt(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readUtf(512)
                    )
            );
}

public record ShopSalesPayload(BlockPos pos, int limit, int offset, boolean hasMore, List<ShopSalesEntryPayload> rows) implements CustomPacketPayload {
    public static final Type<ShopSalesPayload> TYPE = new Type<>(SHOP_SALES_PACKET_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopSalesPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos());
                        buf.writeInt(p.limit());
                        buf.writeInt(p.offset());
                        buf.writeBoolean(p.hasMore());
                        buf.writeInt(p.rows().size());
                        for (var e : p.rows()) {
                            ShopSalesEntryPayload.CODEC.encode(buf, e);
                        }
                    },
                    buf -> {
                        BlockPos pos = buf.readBlockPos();
                        int limit = buf.readInt();
                        int offset = buf.readInt();
                        boolean hasMore = buf.readBoolean();
                        int size = buf.readInt();
                        List<ShopSalesEntryPayload> rows = new ArrayList<>(Math.max(0, size));
                        for (int i = 0; i < size; i++) {
                            rows.add(ShopSalesEntryPayload.CODEC.decode(buf));
                        }
                        return new ShopSalesPayload(pos, limit, offset, hasMore, rows);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

    // =============================
    // SHOP SHELF RENDER OVERRIDES (admin tuning)
    // =============================

    public record ShopRenderOverridesSyncPayload(List<RenderOverrideEntry> entries) implements CustomPacketPayload {
        public static final Type<ShopRenderOverridesSyncPayload> TYPE = new Type<>(SHOP_RENDER_OVERRIDES_SYNC_PACKET_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, ShopRenderOverridesSyncPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    List<RenderOverrideEntry> list = p.entries() == null ? List.of() : p.entries();
                    buf.writeVarInt(list.size());
                    for (RenderOverrideEntry e : list) {
                        buf.writeByte(e.scope().toByte());
                        buf.writeUtf(e.key());
                        RenderTransform t = e.transform();
                        buf.writeFloat(t.offX());
                        buf.writeFloat(t.offY());
                        buf.writeFloat(t.offZ());
                        buf.writeFloat(t.rotX());
                        buf.writeFloat(t.rotY());
                        buf.writeFloat(t.rotZ());
                        buf.writeFloat(t.scaleMul());
                        buf.writeFloat(t.extraLift());
                    }
                },
                (buf) -> {
                    int n = buf.readVarInt();
                    List<RenderOverrideEntry> out = new ArrayList<>(Math.max(0, n));
                    for (int i = 0; i < n; i++) {
                        RenderOverrideScope scope = RenderOverrideScope.fromByte(buf.readByte());
                        String key = buf.readUtf(128);
                        RenderTransform t = new RenderTransform(
                                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                                buf.readFloat(), buf.readFloat()
                        );
                        out.add(new RenderOverrideEntry(scope, key, t));
                    }
                    return new ShopRenderOverridesSyncPayload(out);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ShopRenderOverrideUpsertPayload(RenderOverrideScope scope, String key, RenderTransform transform) implements CustomPacketPayload {
        public static final Type<ShopRenderOverrideUpsertPayload> TYPE = new Type<>(SHOP_RENDER_OVERRIDE_UPSERT_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopRenderOverrideUpsertPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.scope().toByte());
                    buf.writeUtf(p.key());
                    RenderTransform t = p.transform();
                    buf.writeFloat(t.offX());
                    buf.writeFloat(t.offY());
                    buf.writeFloat(t.offZ());
                    buf.writeFloat(t.rotX());
                    buf.writeFloat(t.rotY());
                    buf.writeFloat(t.rotZ());
                    buf.writeFloat(t.scaleMul());
                    buf.writeFloat(t.extraLift());
                },
                (buf) -> {
                    RenderOverrideScope scope = RenderOverrideScope.fromByte(buf.readByte());
                    String key = buf.readUtf(128);
                    RenderTransform t = new RenderTransform(
                            buf.readFloat(), buf.readFloat(), buf.readFloat(),
                            buf.readFloat(), buf.readFloat(), buf.readFloat(),
                            buf.readFloat(), buf.readFloat()
                    );
                    return new ShopRenderOverrideUpsertPayload(scope, key, t);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ShopRenderOverrideRemovePayload(RenderOverrideScope scope, String key) implements CustomPacketPayload {
        public static final Type<ShopRenderOverrideRemovePayload> TYPE = new Type<>(SHOP_RENDER_OVERRIDE_REMOVE_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopRenderOverrideRemovePayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.scope().toByte());
                    buf.writeUtf(p.key());
                },
                (buf) -> new ShopRenderOverrideRemovePayload(RenderOverrideScope.fromByte(buf.readByte()), buf.readUtf(128))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // Client -> Server (admin)
    public record ShopRenderOverrideSetC2SPayload(RenderOverrideScope scope, String key, RenderTransform transform) implements CustomPacketPayload {
        public static final Type<ShopRenderOverrideSetC2SPayload> TYPE = new Type<>(SHOP_RENDER_OVERRIDE_SET_C2S_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopRenderOverrideSetC2SPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.scope().toByte());
                    buf.writeUtf(p.key());
                    RenderTransform t = p.transform();
                    buf.writeFloat(t.offX());
                    buf.writeFloat(t.offY());
                    buf.writeFloat(t.offZ());
                    buf.writeFloat(t.rotX());
                    buf.writeFloat(t.rotY());
                    buf.writeFloat(t.rotZ());
                    buf.writeFloat(t.scaleMul());
                    buf.writeFloat(t.extraLift());
                },
                (buf) -> {
                    RenderOverrideScope scope = RenderOverrideScope.fromByte(buf.readByte());
                    String key = buf.readUtf(128);
                    RenderTransform t = new RenderTransform(
                            buf.readFloat(), buf.readFloat(), buf.readFloat(),
                            buf.readFloat(), buf.readFloat(), buf.readFloat(),
                            buf.readFloat(), buf.readFloat()
                    );
                    return new ShopRenderOverrideSetC2SPayload(scope, key, t);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ShopRenderOverrideDeleteC2SPayload(RenderOverrideScope scope, String key) implements CustomPacketPayload {
        public static final Type<ShopRenderOverrideDeleteC2SPayload> TYPE = new Type<>(SHOP_RENDER_OVERRIDE_DELETE_C2S_PACKET_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ShopRenderOverrideDeleteC2SPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.scope().toByte());
                    buf.writeUtf(p.key());
                },
                (buf) -> new ShopRenderOverrideDeleteC2SPayload(RenderOverrideScope.fromByte(buf.readByte()), buf.readUtf(128))
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

}
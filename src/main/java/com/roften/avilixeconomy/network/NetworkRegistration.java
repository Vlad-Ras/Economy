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

    public static final ResourceLocation SHOP_SET_MODE_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_set_mode");

    public static final ResourceLocation SHOP_SELL_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_sell");

    public static final ResourceLocation SHOP_REQUEST_SALES_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_request_sales");

    public static final ResourceLocation SHOP_SALES_PACKET_ID =
            ResourceLocation.fromNamespaceAndPath("avilixeconomy", "shop_sales");

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

public record BalancePayload(long balance) implements CustomPacketPayload {
        public static final Type<BalancePayload> TYPE = new Type<>(BALANCE_PACKET_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, BalancePayload> CODEC =
                StreamCodec.of(
                        (buf, payload) -> buf.writeLong(payload.balance),
                        buf -> new BalancePayload(buf.readLong())
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
            long leftMoney,
            long rightMoney,
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
                            buf.writeLong(p.leftMoney);
                            buf.writeLong(p.rightMoney);
                            buf.writeBoolean(p.leftReady);
                            buf.writeBoolean(p.rightReady);
                        },
                        buf -> new TradeStatePayload(
                                buf.readInt(),
                                buf.readUtf(),
                                buf.readUtf(),
                                buf.readLong(),
                                buf.readLong(),
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
    public record TradeUpdateMoneyPayload(int sessionId, long money) implements CustomPacketPayload {
        public static final Type<TradeUpdateMoneyPayload> TYPE = new Type<>(TRADE_UPDATE_MONEY_PACKET_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, TradeUpdateMoneyPayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeInt(p.sessionId);
                            buf.writeLong(p.money);
                        },
                        buf -> new TradeUpdateMoneyPayload(buf.readInt(), buf.readLong())
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
    public record ShopSetPricePayload(BlockPos pos, int mode, long price) implements CustomPacketPayload {
        public static final Type<ShopSetPricePayload> TYPE = new Type<>(SHOP_SET_PRICE_PACKET_ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, ShopSetPricePayload> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBlockPos(p.pos());
                            buf.writeInt(p.mode());
                            buf.writeLong(p.price());
                        },
                        buf -> new ShopSetPricePayload(buf.readBlockPos(), buf.readInt(), buf.readLong())
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
            if (!shop.isOwner(sp)) return;

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
                    if (!shop.isOwner(sp)) return;

                    long price = Math.max(0L, payload.price());
                    shop.setPriceForMode(payload.mode(), price);
                    sp.displayClientMessage(Component.translatable("msg.avilixeconomy.shop.price_set", price), true);
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
                    if (!shop.isOwner(sp)) return;

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
                        sp.displayClientMessage(Component.translatable("msg.avilixeconomy.shop.invalid_qty"), true);
                        return;
                    }
                    if (shop.isTemplateEmpty()) {
                        sp.displayClientMessage(Component.translatable("msg.avilixeconomy.shop.not_configured"), true);
                        return;
                    }

                    int available = shop.getAvailableLots();
                    if (available <= 0) {
                        sp.displayClientMessage(Component.translatable("msg.avilixeconomy.shop.out_of_stock"), true);
                        return;
                    }
                    if (lots > available) {
                        sp.displayClientMessage(Component.translatable("msg.avilixeconomy.shop.too_many", available), true);
                        return;
                    }

                    long pricePer = shop.getActivePricePerLot();
                    long total;
                    try {
                        total = Math.multiplyExact(pricePer, (long) lots);
                    } catch (ArithmeticException ex) {
                        sp.displayClientMessage(Component.translatable("msg.avilixeconomy.shop.too_expensive"), true);
                        return;
                    }

                    long bal = EconomyData.getBalance(sp.getUUID());
                    if (bal < total) {
                        sp.displayClientMessage(Component.translatable("msg.avilixeconomy.shop.not_enough_money", total), true);
                        return;
                    }

                    boolean ok = shop.tryBuy(sp, lots);
                    if (!ok) {
                        sp.displayClientMessage(Component.translatable("msg.avilixeconomy.shop.purchase_failed"), true);
                    } else {
                        sp.displayClientMessage(Component.translatable("msg.avilixeconomy.shop.purchased", lots, total), true);
                    }
                })
        );

    }

    // === Utils ===
    public static void sendBalanceTo(ServerPlayer player, long balance) {
        player.connection.send(new BalancePayload(balance));
    }

    public static void sendTradeState(
            ServerPlayer player,
            int sessionId,
            String leftName,
            String rightName,
            long leftMoney,
            long rightMoney,
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

public record ShopSalesEntryPayload(long createdAtMillis, String tradeType, String counterpartyName, int lots, long totalPrice, long pricePerLot, String itemsSummary) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ShopSalesEntryPayload> CODEC =
        StreamCodec.of(
                (buf, e) -> {
                    buf.writeLong(e.createdAtMillis());
                        buf.writeUtf(e.tradeType(), 8);
                        buf.writeUtf(e.counterpartyName(), 32);
                        buf.writeInt(e.lots());
                        buf.writeLong(e.totalPrice());
                        buf.writeLong(e.pricePerLot());
                        buf.writeUtf(e.itemsSummary(), 512);
                    },
                    buf -> new ShopSalesEntryPayload(
                            buf.readLong(),
                            buf.readUtf(8),
                            buf.readUtf(32),
                            buf.readInt(),
                            buf.readLong(),
                            buf.readLong(),
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

}
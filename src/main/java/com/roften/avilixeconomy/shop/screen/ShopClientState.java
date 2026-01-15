package com.roften.avilixeconomy.shop.screen;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


/**
 * Client-side shared state for Shop screens.
 * Does NOT reference any client-only classes to stay safe on dedicated servers.
 */
public final class ShopClientState {

    private ShopClientState() {}

    public record SalesEntry(long createdAtMillis, String tradeType, String counterpartyName, int lots, double totalPrice, double pricePerLot, String itemsSummary) {}

    public record SalesPage(int offset, int limit, boolean hasMore, List<SalesEntry> rows, long version) {}

    private static final ConcurrentHashMap<BlockPos, SalesPage> SALES = new ConcurrentHashMap<>();
    private static final AtomicLong VERSION = new AtomicLong(1);

    public static void putSales(BlockPos pos, int offset, int limit, boolean hasMore, List<SalesEntry> rows) {
        long v = VERSION.getAndIncrement();
        SALES.put(pos, new SalesPage(offset, limit, hasMore, List.copyOf(rows), v));
    }

    public static @Nullable SalesPage getSales(BlockPos pos) {
        return SALES.get(pos);
    }
}

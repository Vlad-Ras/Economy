package com.roften.avilixeconomy.shop.blockentity;

import com.roften.avilixeconomy.EconomyData;
import com.roften.avilixeconomy.commission.CommissionManager;
import com.roften.avilixeconomy.config.AvilixEconomyCommonConfig;
import com.roften.avilixeconomy.database.DatabaseManager;
import com.roften.avilixeconomy.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.UUID;

public class ShopBlockEntity extends BlockEntity {

    public static final int TEMPLATE_SLOTS = 9;
    public static final int STOCK_SLOTS = 54;

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_OWNER_NAME = "OwnerName";
    private static final String TAG_TEMPLATE = "Template";
    private static final String TAG_STOCK = "Stock";
    private static final String TAG_PRICE = "Price";
    private static final String TAG_PRICE_BUY = "PriceBuy";
    private static final String TAG_MODE = "ModeBuy";

    @Nullable
    private UUID owner;

    @Nullable
    private String ownerName;

    private long priceSellPerLot;
    private long priceBuyPerLot;
    private boolean buyMode;

    private final ItemStackHandler template = new ItemStackHandler(TEMPLATE_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    private final ItemStackHandler stock = new ItemStackHandler(STOCK_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    // Automation (hoppers/Create): allow insertion AND extraction.
    private final IItemHandler automationHandler = stock;

    public ShopBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHOP.get(), pos, state);
    }

    public ItemStackHandler getTemplate() {
        return template;
    }

    public ItemStackHandler getStock() {
        return stock;
    }

    public long getPricePerLot() {
        return priceSellPerLot;
    }

    public void setPricePerLot(long priceSellPerLot) {
        this.priceSellPerLot = Math.max(0L, priceSellPerLot);
        setChanged();
    }

public long getPriceBuyPerLot() {
    return priceBuyPerLot;
}

public void setPriceBuyPerLot(long priceBuyPerLot) {
    this.priceBuyPerLot = Math.max(0L, priceBuyPerLot);
    setChanged();
}

public boolean isBuyMode() {
    return buyMode;
}

public void setBuyMode(boolean buyMode) {
    this.buyMode = buyMode;
    setChanged();
}

public long getActivePricePerLot() {
    return buyMode ? priceBuyPerLot : priceSellPerLot;
}

public int getActiveModeInt() {
    return buyMode ? 1 : 0;
}

public void setPriceForMode(int mode, long price) {
    if (mode == 1) setPriceBuyPerLot(price);
    else setPricePerLot(price);
}



    public void setOwnerName(@Nullable String ownerName) {
        this.ownerName = (ownerName == null || ownerName.isBlank()) ? null : ownerName;
        setChanged();
    }

    @Nullable
    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    public IItemHandler getAutomationItemHandler(@Nullable Direction side) {
        return automationHandler;
    }

    /**
     * @return how many full "lots" are available, based on template requirements and stock.
     */
    public int getAvailableLots() {
        int minLots = Integer.MAX_VALUE;
        boolean hasAny = false;

        for (int i = 0; i < template.getSlots(); i++) {
            var req = template.getStackInSlot(i);
            if (req.isEmpty()) continue;
            hasAny = true;
            int need = req.getCount();
            if (need <= 0) continue;
            int have = countInStock(req);
            int lots = have / need;
            if (lots < minLots) minLots = lots;
        }

        if (!hasAny) return 0;
        return Math.max(0, minLots == Integer.MAX_VALUE ? 0 : minLots);
    }

    public boolean isTemplateEmpty() {
        return templateEmpty();
    }

    private int countInStock(net.minecraft.world.item.ItemStack sample) {
        int total = 0;
        for (int i = 0; i < stock.getSlots(); i++) {
            var s = stock.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, sample)) {
                total += s.getCount();
            }
        }
        return total;
    }

    
    public int getSellCommissionBps() {
        if (owner == null) return AvilixEconomyCommonConfig.COMMISSION.defaultSellBps.get();
        return CommissionManager.getSellBpsForOwner(owner);
    }

    public int getBuyCommissionBps() {
        if (owner == null) return AvilixEconomyCommonConfig.COMMISSION.defaultBuyBps.get();
        return CommissionManager.getBuyBpsForOwner(owner);
    }

/**
     * Tries to buy N lots from this shop.
     *
     * @return true if the purchase succeeded.
     */
    public boolean tryBuy(ServerPlayer buyer, int lots) {
        if (buyMode) {
            if (buyer != null) buyer.displayClientMessage(net.minecraft.network.chat.Component.literal("Магазин сейчас в режиме скупки."), true);
            return false;
        }

        if (lots <= 0) return false;
        if (templateEmpty()) return false;

        int available = getAvailableLots();
        if (available <= 0) return false;
        if (lots > available) return false;

        long usedPricePerLot = buyMode ? priceBuyPerLot : priceSellPerLot;
        long totalPrice = usedPricePerLot * (long) lots;
        if (totalPrice < 0) totalPrice = Long.MAX_VALUE; // overflow guard

        if (owner == null) return false;

        // Prevent buying from your own shop.
        if (owner.equals(buyer.getUUID())) {
            buyer.displayClientMessage(net.minecraft.network.chat.Component.literal("Нельзя покупать у самого себя."), true);
            return false;
        }

        long balance = EconomyData.getBalance(buyer.getUUID());
        if (balance < totalPrice) return false;

        // First, reserve/remove items from stock.
        net.minecraft.world.item.ItemStack[] toGive = new net.minecraft.world.item.ItemStack[template.getSlots()];
        for (int i = 0; i < template.getSlots(); i++) {
            var req = template.getStackInSlot(i);
            if (req.isEmpty()) {
                toGive[i] = net.minecraft.world.item.ItemStack.EMPTY;
                continue;
            }
            int amount = req.getCount() * lots;
            toGive[i] = extractMatching(req, amount);
            if (toGive[i].isEmpty() || toGive[i].getCount() != amount) {
                // rollback (put back extracted)
                for (int j = 0; j <= i; j++) {
                    var r = toGive[j];
                    if (r != null && !r.isEmpty()) {
                        insertToStock(r);
                    }
                }
                return false;
            }
        }

        // Transfer money.
        UUID serverUuid;
        try {
            serverUuid = UUID.fromString(AvilixEconomyCommonConfig.ECONOMY.serverAccountUuid.get());
        } catch (Exception e) {
            serverUuid = new UUID(0L, 0L);
        }
        int commBps = getSellCommissionBps();
        long fee = CommissionManager.computeFee(totalPrice, commBps);
        long ownerNet = Math.max(0L, totalPrice - fee);
        if (!EconomyData.paySplit(buyer.getUUID(), owner, ownerNet, serverUuid, fee)) {
            // rollback items
            for (var st : toGive) {
                if (st != null && !st.isEmpty()) insertToStock(st);
            }
            return false;
        }

        // Give items to buyer (spill if inventory full).
        for (var st : toGive) {
            if (st == null || st.isEmpty()) continue;
            net.neoforged.neoforge.items.ItemHandlerHelper.giveItemToPlayer(buyer, st);
        }

        // DB: лог успешной покупки (асинхронно, чтобы не лагать тред сервера)
        if (level != null && owner != null) {
            final UUID ownerUuid = owner;
            final UUID buyerUuid = buyer.getUUID();
            final String ownerNameFinal = resolveOwnerName(level, ownerUuid);
            final String buyerNameFinal = buyer.getGameProfile().getName();
            final String worldId = level.dimension().location().toString();
            final int sx = worldPosition.getX();
            final int sy = worldPosition.getY();
            final int sz = worldPosition.getZ();
            final String blockIdFinal = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).toString();
            final String itemsJson = buildItemsJson(toGive, lots);

            // Capture values for async lambda (must be final / effectively final)
            final long pricePerLotFinal = usedPricePerLot;
            final int lotsFinal = lots;
            final long totalPriceFinal = totalPrice;

                        CompletableFuture.runAsync(() -> DatabaseManager.logShopSaleNow(
                    worldId, sx, sy, sz, blockIdFinal,
                    ownerUuid, ownerNameFinal,
                    buyerUuid, buyerNameFinal,
                    "SELL",
                    pricePerLotFinal, lotsFinal, totalPriceFinal, itemsJson
            ));
        }

        setChanged();
        return true;
    }

/**
 * BUY-mode: player sells items to the shop. Owner pays the seller, items go into stock.
 * Returns true if transaction succeeded.
 */
public boolean trySellToShop(ServerPlayer seller, int lots) {
    if (lots <= 0) return false;
    if (!buyMode) {
        seller.displayClientMessage(net.minecraft.network.chat.Component.literal("Магазин сейчас в режиме продажи."), true);
        return false;
    }
    if (owner == null) {
        seller.displayClientMessage(net.minecraft.network.chat.Component.literal("У магазина нет владельца."), true);
        return false;
    }
	    // Prevent selling to your own shop.
	    if (owner.equals(seller.getUUID())) {
	        seller.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.avilixeconomy.shop.self_trade"), true);
	        return false;
	    }
    long pricePerLot = priceBuyPerLot;
    if (pricePerLot <= 0) {
        seller.displayClientMessage(net.minecraft.network.chat.Component.literal("Цена скупки не установлена."), true);
        return false;
    }
    // Validate template
    java.util.List<net.minecraft.world.item.ItemStack> lot = getTemplateStacks();
    if (lot.isEmpty()) {
        seller.displayClientMessage(net.minecraft.network.chat.Component.literal("Лот не настроен."), true);
        return false;
    }
    // Check seller has required items
    if (!playerHasItems(seller, lot, lots)) {
        seller.displayClientMessage(net.minecraft.network.chat.Component.literal("У вас нет нужных предметов для продажи."), true);
        return false;
    }
    // Check stock has enough space (strict rule: if not enough space -> reject)
    int maxLotsBySpace = getAvailableLotsBySpace(lot);
    if (lots > maxLotsBySpace) {
        seller.displayClientMessage(net.minecraft.network.chat.Component.literal("Недостаточно места на складе. Доступно лотов: " + maxLotsBySpace), true);
        return false;
    }
    // Check owner has money
    long totalPrice;
    try {
        totalPrice = Math.multiplyExact(pricePerLot, (long) lots);
    } catch (ArithmeticException ex) {
        totalPrice = Long.MAX_VALUE;
    }
    long ownerBal = EconomyData.getBalance(owner);
    long maxLotsByMoney = ownerBal / pricePerLot;
    if (lots > maxLotsByMoney) {
        seller.displayClientMessage(net.minecraft.network.chat.Component.literal("У владельца недостаточно средств. Доступно лотов: " + maxLotsByMoney), true);
        return false;
    }

    // Remove items from seller inventory
    if (!removeItemsFromPlayer(seller, lot, lots)) {
        seller.displayClientMessage(net.minecraft.network.chat.Component.literal("Не удалось забрать предметы из инвентаря."), true);
        return false;
    }

    // Insert into stock (should succeed due to pre-check)
    if (!insertLotIntoStock(lot, lots)) {
        // rollback: return items
        giveItemsToPlayer(seller, lot, lots);
        seller.displayClientMessage(net.minecraft.network.chat.Component.literal("Склад переполнен. Продажа отменена."), true);
        return false;
    }

    // Pay seller from owner
    UUID serverUuid;
        try {
            serverUuid = UUID.fromString(AvilixEconomyCommonConfig.ECONOMY.serverAccountUuid.get());
        } catch (Exception e) {
            serverUuid = new UUID(0L, 0L);
        }
        int commBps = getBuyCommissionBps();
        long fee = CommissionManager.computeFee(totalPrice, commBps);
        long sellerNet = Math.max(0L, totalPrice - fee);
        boolean paid = EconomyData.paySplit(owner, seller.getUUID(), sellerNet, serverUuid, fee);
    if (!paid) {
        // rollback: remove inserted and return, but safest: attempt to refund items to seller
        // Try to extract back and give seller (best-effort)
        extractLotFromStock(lot, lots);
        giveItemsToPlayer(seller, lot, lots);
        seller.displayClientMessage(net.minecraft.network.chat.Component.literal("Оплата не прошла. Продажа отменена."), true);
        return false;
    }

    // Log to DB as BUY (counterparty = seller)
    if (level != null) {
        final java.util.UUID ownerUuid = owner;
        final java.util.UUID sellerUuid = seller.getUUID();
        final String ownerNameFinal = resolveOwnerName(level, ownerUuid);
        final String sellerNameFinal = seller.getGameProfile().getName();
        final String worldId = level.dimension().location().toString();
        final int sx = worldPosition.getX();
        final int sy = worldPosition.getY();
        final int sz = worldPosition.getZ();
        final String blockIdFinal = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).toString();
        final String itemsJson = buildItemsJson(lot.toArray(new net.minecraft.world.item.ItemStack[0]), lots);
        final long pricePerLotFinal = pricePerLot;
        final int lotsFinal = lots;
        final long totalPriceFinal = totalPrice;
        java.util.concurrent.CompletableFuture.runAsync(() -> DatabaseManager.logShopSaleNow(
                worldId, sx, sy, sz, blockIdFinal,
                ownerUuid, ownerNameFinal,
                sellerUuid, sellerNameFinal,
                "BUY",
                pricePerLotFinal, lotsFinal, totalPriceFinal, itemsJson
        ));
    }

    setChanged();
    return true;
}

public int getAvailableBuyLots() {
    if (!buyMode) return 0;
    java.util.List<net.minecraft.world.item.ItemStack> lot = getTemplateStacks();
    if (lot.isEmpty()) return 0;
    long price = priceBuyPerLot;
    if (price <= 0 || owner == null) return 0;
    long ownerBal = EconomyData.getBalance(owner);
    long byMoney = ownerBal / price;
    int bySpace = getAvailableLotsBySpace(lot);
    long min = Math.min(byMoney, (long) bySpace);
    return (int) Math.min(Integer.MAX_VALUE, min);
}

private java.util.List<net.minecraft.world.item.ItemStack> getTemplateStacks() {
    java.util.ArrayList<net.minecraft.world.item.ItemStack> list = new java.util.ArrayList<>();
    for (int i = 0; i < template.getSlots(); i++) {
        net.minecraft.world.item.ItemStack s = template.getStackInSlot(i);
        if (!s.isEmpty()) list.add(s.copy());
    }
    return list;
}

private boolean playerHasItems(ServerPlayer player, java.util.List<net.minecraft.world.item.ItemStack> lot, int lots) {
    for (net.minecraft.world.item.ItemStack need : lot) {
        int required = need.getCount() * lots;
        int have = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, need)) {
                have += s.getCount();
                if (have >= required) break;
            }
        }
        if (have < required) return false;
    }
    return true;
}

private boolean removeItemsFromPlayer(ServerPlayer player, java.util.List<net.minecraft.world.item.ItemStack> lot, int lots) {
    var inv = player.getInventory();
    for (net.minecraft.world.item.ItemStack need : lot) {
        int remaining = need.getCount() * lots;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, need)) continue;
            int take = Math.min(remaining, s.getCount());
            s.shrink(take);
            inv.setItem(i, s);
            remaining -= take;
            if (remaining <= 0) break;
        }
        if (remaining > 0) return false;
    }
    return true;
}

private void giveItemsToPlayer(ServerPlayer player, java.util.List<net.minecraft.world.item.ItemStack> lot, int lots) {
    for (net.minecraft.world.item.ItemStack it : lot) {
        var give = it.copy();
        give.setCount(it.getCount() * lots);
        if (!player.getInventory().add(give)) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, give);
        }
    }
}

private boolean insertLotIntoStock(java.util.List<net.minecraft.world.item.ItemStack> lot, int lots) {
    for (net.minecraft.world.item.ItemStack it : lot) {
        var toInsert = it.copy();
        toInsert.setCount(it.getCount() * lots);
        for (int slot = 0; slot < stock.getSlots() && !toInsert.isEmpty(); slot++) {
            toInsert = stock.insertItem(slot, toInsert, false);
        }
        if (!toInsert.isEmpty()) return false;
    }
    return true;
}

private void extractLotFromStock(java.util.List<net.minecraft.world.item.ItemStack> lot, int lots) {
    for (net.minecraft.world.item.ItemStack it : lot) {
        int remaining = it.getCount() * lots;
        for (int slot = 0; slot < stock.getSlots() && remaining > 0; slot++) {
            var s = stock.getStackInSlot(slot);
            if (s.isEmpty()) continue;
            if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, it)) continue;
            int take = Math.min(remaining, s.getCount());
            stock.extractItem(slot, take, false);
            remaining -= take;
        }
    }
}

private int getAvailableLotsBySpace(java.util.List<net.minecraft.world.item.ItemStack> lot) {
    // Capacity-based approximation: for each required item, compute how many items can be inserted.
    int result = Integer.MAX_VALUE;
    for (net.minecraft.world.item.ItemStack req : lot) {
        if (req.isEmpty()) continue;
        int perLot = req.getCount();
        if (perLot <= 0) continue;
        long cap = 0;
        for (int slot = 0; slot < stock.getSlots(); slot++) {
            var s = stock.getStackInSlot(slot);
            if (s.isEmpty()) {
                cap += req.getMaxStackSize();
            } else if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, req)) {
                cap += (long) req.getMaxStackSize() - s.getCount();
            }
        }
        int lotsByThis = (int) Math.min(Integer.MAX_VALUE, cap / perLot);
        result = Math.min(result, lotsByThis);
    }
    return result == Integer.MAX_VALUE ? 0 : Math.max(0, result);
}


    private String resolveOwnerName(net.minecraft.world.level.Level level, UUID ownerUuid) {
        // 1) cached on block (best, works for offline)
        if (ownerName != null && !ownerName.isBlank()) return ownerName;

        // 2) online player
        if (level != null && !level.isClientSide && level.getServer() != null) {
            var p = level.getServer().getPlayerList().getPlayer(ownerUuid);
            if (p != null) return p.getGameProfile().getName();
        }

        // 3) fallback to economy DB name (may be empty)
        try {
            String fromDb = DatabaseManager.getPlayerNameDirect(ownerUuid);
            if (fromDb != null && !fromDb.isBlank()) return fromDb;
        } catch (Exception ignored) {
        }

        // 4) ultimate fallback
        return ownerUuid.toString();
    }

    private static String buildItemsJson(net.minecraft.world.item.ItemStack[] stacks, int lots) {
        if (stacks == null || stacks.length == 0) return "[]";
        int safeLots = Math.max(1, lots);

        StringBuilder sb = new StringBuilder(128);
        sb.append('[');
        boolean first = true;

        for (var st : stacks) {
            if (st == null || st.isEmpty()) continue;

            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem());
            int total = st.getCount();
            int perLot = total / safeLots;

            if (!first) sb.append(',');
            first = false;

            sb.append('{')
                    .append("\"item\":\"").append(id).append("\"")
                    .append(',')
                    .append("\"perLot\":").append(perLot)
                    .append(',')
                    .append("\"total\":").append(total)
                    .append('}');
        }

        sb.append(']');
        return sb.toString();
    }

    private boolean templateEmpty() {
        for (int i = 0; i < template.getSlots(); i++) {
            if (!template.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }

    private void insertToStock(net.minecraft.world.item.ItemStack stack) {
        net.minecraft.world.item.ItemStack remaining = stack;
        for (int i = 0; i < stock.getSlots(); i++) {
            remaining = stock.insertItem(i, remaining, false);
            if (remaining.isEmpty()) return;
        }
        // if still remaining - drop into world
        if (level != null && !level.isClientSide) {
            Containers.dropItemStack(level,
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5,
                    remaining);
        }
    }

    private net.minecraft.world.item.ItemStack extractMatching(net.minecraft.world.item.ItemStack sample, int amount) {
        int remaining = amount;
        net.minecraft.world.item.ItemStack out = sample.copy();
        out.setCount(0);

        for (int i = 0; i < stock.getSlots(); i++) {
            var s = stock.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, sample)) continue;

            int take = Math.min(remaining, s.getCount());
            var extracted = stock.extractItem(i, take, false);
            if (!extracted.isEmpty()) {
                out.grow(extracted.getCount());
                remaining -= extracted.getCount();
                if (remaining <= 0) break;
            }
        }

        if (out.getCount() != amount) {
            // failure - caller does rollback; return whatever we got
        }
        return out;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID(TAG_OWNER, owner);
        if (ownerName != null && !ownerName.isBlank()) tag.putString(TAG_OWNER_NAME, ownerName);
        tag.putLong(TAG_PRICE, priceSellPerLot);
        tag.putLong(TAG_PRICE_BUY, priceBuyPerLot);
        tag.putBoolean(TAG_MODE, buyMode);
        tag.put(TAG_TEMPLATE, template.serializeNBT(registries));
        tag.put(TAG_STOCK, stock.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        ownerName = tag.contains(TAG_OWNER_NAME) ? tag.getString(TAG_OWNER_NAME) : null;
        priceSellPerLot = tag.getLong(TAG_PRICE);
        priceBuyPerLot = tag.contains(TAG_PRICE_BUY) ? tag.getLong(TAG_PRICE_BUY) : priceSellPerLot;
        buyMode = tag.contains(TAG_MODE) && tag.getBoolean(TAG_MODE);
        if (tag.contains(TAG_TEMPLATE)) template.deserializeNBT(registries, tag.getCompound(TAG_TEMPLATE));
        if (tag.contains(TAG_STOCK)) stock.deserializeNBT(registries, tag.getCompound(TAG_STOCK));
    }

    /** Insert-only wrapper for automation to prevent theft via hoppers. */
    private static final class InsertOnlyHandler implements IItemHandler {
        private final ItemStackHandler delegate;

        private InsertOnlyHandler(ItemStackHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public net.minecraft.world.item.ItemStack insertItem(int slot, net.minecraft.world.item.ItemStack stack, boolean simulate) {
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return delegate.isItemValid(slot, stack);
        }
    }
}

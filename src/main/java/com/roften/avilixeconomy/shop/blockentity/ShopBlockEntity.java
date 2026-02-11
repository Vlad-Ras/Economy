package com.roften.avilixeconomy.shop.blockentity;

import com.roften.avilixeconomy.EconomyData;
import com.roften.avilixeconomy.commission.CommissionManager;
import com.roften.avilixeconomy.config.AvilixEconomyCommonConfig;
import com.roften.avilixeconomy.database.DatabaseManager;
import com.roften.avilixeconomy.pricing.MinPriceManager;
import com.roften.avilixeconomy.network.NetworkUtils;
import com.roften.avilixeconomy.util.MoneyUtils;
import com.roften.avilixeconomy.network.NetworkRegistration;
import com.roften.avilixeconomy.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
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
    // Legacy (single per-slot price)
    private static final String TAG_PRICE_SLOT = "PriceSlot";
    private static final String TAG_PRICE_BUY_SLOT = "PriceBuySlot";

    // New (per-template-slot prices)
    private static final String TAG_PRICE_SLOTS = "PriceSlots";
    private static final String TAG_PRICE_BUY_SLOTS = "PriceBuySlots";
    private static final String TAG_MODE = "ModeBuy";

    @Nullable
    private UUID owner;

    @Nullable
    private String ownerName;

    // Legacy: per-lot override prices (still supported)
    private double priceSellPerLot;
    private double priceBuyPerLot;

    /**
     * Per-template-slot prices.
     * "Lot" in this mod is a 3x3 template. Each template slot can have its own price.
     * When buying/selling the whole lot, the effective lot price is the sum of slot prices
     * (unless a legacy override price is set).
     */
    private final double[] priceSellPerTemplateSlot = new double[TEMPLATE_SLOTS];
    private final double[] priceBuyPerTemplateSlot = new double[TEMPLATE_SLOTS];

    // Backward compat: old single per-slot price fields; used only for migration when loading old NBT
    private double legacyPriceSellPerSlot;
    private double legacyPriceBuyPerSlot;
    private boolean buyMode;

    // ===== cached computed values (server-side) =====
    private long cachedAvailGameTime = -1L;
    private int cachedAvailSellLots = 0;
    private int cachedAvailBuyLots = 0;


    private final ItemStackHandler template = new ItemStackHandler(TEMPLATE_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncToClient();
        }
    };

    private final ItemStackHandler stock = new ItemStackHandler(STOCK_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncToClient();
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

    public double getPricePerLot() {
        return priceSellPerLot;
    }

    public void setPricePerLot(double priceSellPerLot) {
        double v = Math.max(0.0, MoneyUtils.round2(priceSellPerLot));
        // Min price is per item; for a full lot we use the sum of mins for all template stacks.
        double minLot = MinPriceManager.getMinForTemplate(template);
        if (minLot > 0.0 && v > 0.0 && v < minLot) v = MoneyUtils.round2(minLot);
        this.priceSellPerLot = v;
        setChanged();
        syncToClient();
    }

public double getPriceBuyPerLot() {
    return priceBuyPerLot;
}

public void setPriceBuyPerLot(double priceBuyPerLot) {
    double v = Math.max(0.0, MoneyUtils.round2(priceBuyPerLot));
    double minLot = MinPriceManager.getMinForTemplate(template);
    if (minLot > 0.0 && v > 0.0 && v < minLot) v = MoneyUtils.round2(minLot);
    this.priceBuyPerLot = v;
    setChanged();
    syncToClient();
}

public boolean isBuyMode() {
    return buyMode;
}

public void setBuyMode(boolean buyMode) {
    this.buyMode = buyMode;
    setChanged();
    syncToClient();
}

public double getActivePricePerLot() {
    int mode = buyMode ? 1 : 0;
    return getEffectiveLotPriceForMode(mode);
}

/**
 * Effective lot price for the given mode.
 * If legacy override price is set (>0), it wins. Otherwise the lot price is the sum of per-template-slot prices.
 */
public double getEffectiveLotPriceForMode(int mode) {
    double override = (mode == 1) ? priceBuyPerLot : priceSellPerLot;
    if (override > 0.0) return MoneyUtils.round2(override);
    double sum = 0.0;
    for (int i = 0; i < TEMPLATE_SLOTS; i++) {
        if (template.getStackInSlot(i).isEmpty()) continue;
        sum += getSlotPriceForMode(mode, i);
    }
    return MoneyUtils.round2(sum);
}

public int getActiveModeInt() {
    return buyMode ? 1 : 0;
}

public void setPriceForMode(int mode, double price) {
    if (mode == 1) setPriceBuyPerLot(price);
    else setPricePerLot(price);
}
    // ===== per-slot prices (for single-slot trades) =====
    /**
     * Backward-compat API. Previously the shop had a single "price per slot".
     * We keep these methods but they now act as a DEFAULT that is used only when per-slot
     * prices are not present in NBT (migration) or when a specific slot price is still 0.
     */
    public double getPricePerSlot() {
        return legacyPriceSellPerSlot;
    }

    public void setPricePerSlot(double priceSellPerSlot) {
        double base = Math.max(0.0, MoneyUtils.round2(priceSellPerSlot));

        // If min prices are configured, a single legacy "price per slot" must not be lower
        // than the highest min among template stacks (otherwise some slots would violate mins).
        double maxMin = 0.0;
        for (int i = 0; i < TEMPLATE_SLOTS; i++) {
            if (template.getStackInSlot(i).isEmpty()) continue;
            double min = MinPriceManager.getMinForStack(template.getStackInSlot(i));
            if (min > maxMin) maxMin = min;
        }
        if (maxMin > 0.0 && base > 0.0 && base < maxMin) base = MoneyUtils.round2(maxMin);

        this.legacyPriceSellPerSlot = base;

        // Apply to all non-empty template slots when user edits legacy value.
        for (int i = 0; i < TEMPLATE_SLOTS; i++) {
            if (template.getStackInSlot(i).isEmpty()) {
                priceSellPerTemplateSlot[i] = 0.0;
            } else {
                double v = base;
                double min = MinPriceManager.getMinForStack(template.getStackInSlot(i));
                if (min > 0.0 && v > 0.0 && v < min) v = MoneyUtils.round2(min);
                priceSellPerTemplateSlot[i] = v;
            }
        }
        setChanged();
        syncToClient();
    }

    public double getPriceBuyPerSlot() {
        return legacyPriceBuyPerSlot;
    }

    public void setPriceBuyPerSlot(double priceBuyPerSlot) {
        double base = Math.max(0.0, MoneyUtils.round2(priceBuyPerSlot));

        double maxMin = 0.0;
        for (int i = 0; i < TEMPLATE_SLOTS; i++) {
            if (template.getStackInSlot(i).isEmpty()) continue;
            double min = MinPriceManager.getMinForStack(template.getStackInSlot(i));
            if (min > maxMin) maxMin = min;
        }
        if (maxMin > 0.0 && base > 0.0 && base < maxMin) base = MoneyUtils.round2(maxMin);

        this.legacyPriceBuyPerSlot = base;

        for (int i = 0; i < TEMPLATE_SLOTS; i++) {
            if (template.getStackInSlot(i).isEmpty()) {
                priceBuyPerTemplateSlot[i] = 0.0;
            } else {
                double v = base;
                double min = MinPriceManager.getMinForStack(template.getStackInSlot(i));
                if (min > 0.0 && v > 0.0 && v < min) v = MoneyUtils.round2(min);
                priceBuyPerTemplateSlot[i] = v;
            }
        }
        setChanged();
        syncToClient();
    }

    public double getActivePricePerSlot() {
        return buyMode ? legacyPriceBuyPerSlot : legacyPriceSellPerSlot;
    }

    public void setSlotPriceForMode(int mode, double price) {
        if (mode == 1) setPriceBuyPerSlot(price);
        else setPricePerSlot(price);
    }

    // ===== new per-template-slot price API =====
    public double getSlotPriceForMode(int mode, int templateSlot) {
        if (templateSlot < 0 || templateSlot >= TEMPLATE_SLOTS) return 0.0;
        if (template.getStackInSlot(templateSlot).isEmpty()) return 0.0;
        double v = mode == 1 ? priceBuyPerTemplateSlot[templateSlot] : priceSellPerTemplateSlot[templateSlot];
        if (v <= 0.0) {
            // fallback to legacy for old data
            v = mode == 1 ? legacyPriceBuyPerSlot : legacyPriceSellPerSlot;
        }
        return MoneyUtils.round2(Math.max(0.0, v));
    }

    public void setSlotPriceForMode(int mode, int templateSlot, double price) {
        if (templateSlot < 0 || templateSlot >= TEMPLATE_SLOTS) return;
        double v = MoneyUtils.round2(Math.max(0.0, price));
        // Apply min price floor per item (as configured)
        double min = MinPriceManager.getMinForStack(template.getStackInSlot(templateSlot));
        if (min > 0.0 && v > 0.0 && v < min) v = min;

        if (mode == 1) priceBuyPerTemplateSlot[templateSlot] = v;
        else priceSellPerTemplateSlot[templateSlot] = v;

        setChanged();
        syncToClient();
    }

    /**
     * Enforces minimum price floors for BOTH lot and slot prices.
     * Called when template changes and from config UI.
     */
    public void enforceMinPriceFloors() {
        try {
            double minLot = MinPriceManager.getMinForTemplate(template);
            if (priceSellPerLot > 0.0 && priceSellPerLot < minLot) priceSellPerLot = MoneyUtils.round2(minLot);
            if (priceBuyPerLot > 0.0 && priceBuyPerLot < minLot) priceBuyPerLot = MoneyUtils.round2(minLot);

            // Each template slot price has its own min price (per item)
            for (int i = 0; i < TEMPLATE_SLOTS; i++) {
                if (template.getStackInSlot(i).isEmpty()) {
                    priceSellPerTemplateSlot[i] = 0.0;
                    priceBuyPerTemplateSlot[i] = 0.0;
                    continue;
                }
                double min = MinPriceManager.getMinForStack(template.getStackInSlot(i));
                if (min <= 0.0) continue;
                if (priceSellPerTemplateSlot[i] > 0.0 && priceSellPerTemplateSlot[i] < min) priceSellPerTemplateSlot[i] = MoneyUtils.round2(min);
                if (priceBuyPerTemplateSlot[i] > 0.0 && priceBuyPerTemplateSlot[i] < min) priceBuyPerTemplateSlot[i] = MoneyUtils.round2(min);
            }
        } catch (Exception ignored) {
        }
        setChanged();
        syncToClient();
    }
    public void setOwnerName(@Nullable String ownerName) {
        this.ownerName = (ownerName == null || ownerName.isBlank()) ? null : ownerName;
        setChanged();
        syncToClient();
    }

    @Nullable
    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
        syncToClient();
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


    /**
     * Cached variant to avoid heavy per-tick scans while a menu is open.
     * Recomputes at most once per 10 game ticks.
     */
    public int getAvailableLotsCached() {
        if (level == null || level.isClientSide) return getAvailableLots();
        refreshAvailableLotsCacheIfNeeded();
        return cachedAvailSellLots;
    }

    /**
     * Cached variant for BUY-mode (seller sells to shop): depends on owner balance and stock space.
     * Uses cached balance only (no SQL). Warmed up once on menu open.
     */
    public int getAvailableBuyLotsCached() {
        if (!buyMode) return 0;
        if (level == null || level.isClientSide) return getAvailableBuyLots();
        refreshAvailableLotsCacheIfNeeded();
        return cachedAvailBuyLots;
    }

    private void refreshAvailableLotsCacheIfNeeded() {
        long t = level.getGameTime();
        if (cachedAvailGameTime >= 0 && (t - cachedAvailGameTime) < 10) return;
        cachedAvailGameTime = t;

        // sell-mode cache
        cachedAvailSellLots = getAvailableLots();

        // buy-mode cache
        if (!buyMode) {
            cachedAvailBuyLots = 0;
            return;
        }
        java.util.List<net.minecraft.world.item.ItemStack> lot = getTemplateStacks();
        if (lot.isEmpty() || owner == null) {
            cachedAvailBuyLots = 0;
            return;
        }
        double price = getEffectiveLotPriceForMode(1);
        if (price <= 0.0) {
            cachedAvailBuyLots = 0;
            return;
        }
        double ownerBal = com.roften.avilixeconomy.EconomyData.getCachedBalance(owner);
        long byMoney = (long) Math.floor(ownerBal / price);
        int bySpace = getAvailableLotsBySpace(lot);
        long min = Math.min(byMoney, (long) bySpace);
        cachedAvailBuyLots = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, min));
    }
    public boolean isTemplateEmpty() {
        return templateEmpty();
    }

    /**
     * Counts how many items matching {@code sample} exist in the shop stock.
     * This is used both server-side (logic) and client-side (rendering overlay).
     */
    public int countInStock(net.minecraft.world.item.ItemStack sample) {
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
            if (buyer != null) NetworkRegistration.sendShopToast(buyer, net.minecraft.network.chat.Component.literal("Магазин сейчас в режиме скупки."), false);
            return false;
        }

        if (lots <= 0) return false;
        if (templateEmpty()) return false;

        int available = getAvailableLots();
        if (available <= 0) return false;
        if (lots > available) return false;

        double usedPricePerLot = getEffectiveLotPriceForMode(0);
        double totalPrice = MoneyUtils.round2(usedPricePerLot * (double) lots);

        if (owner == null) return false;

        // Prevent buying from your own shop.
        if (owner.equals(buyer.getUUID())) {
            NetworkRegistration.sendShopToast(buyer, net.minecraft.network.chat.Component.literal("Нельзя покупать у самого себя."), false);
            return false;
        }

        double balance = EconomyData.getBalance(buyer.getUUID());
        if (balance + 1e-9 < totalPrice) return false;

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
        double fee = CommissionManager.computeFee(totalPrice, commBps);
        double ownerNet = Math.max(0.0, MoneyUtils.round2(totalPrice - fee));
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
            final double pricePerLotFinal = usedPricePerLot;
            final int lotsFinal = lots;
            final double totalPriceFinal = totalPrice;

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
 * Slot-mode (SELL-mode): player buys only one template slot, repeated 'units' times.
 * Each unit gives templateStack.getCount() items of that slot.
 */
public boolean tryBuySlot(ServerPlayer buyer, int templateSlot, int units) {
    if (buyMode) {
        if (buyer != null) NetworkRegistration.sendShopToast(buyer, net.minecraft.network.chat.Component.literal("Магазин сейчас в режиме скупки."), false);
        return false;
    }
    if (units <= 0) return false;
    if (templateSlot < 0 || templateSlot >= template.getSlots()) return false;

    var req = template.getStackInSlot(templateSlot);
    if (req == null || req.isEmpty()) return false;

    if (owner == null) return false;
    if (owner.equals(buyer.getUUID())) {
        NetworkRegistration.sendShopToast(buyer, net.minecraft.network.chat.Component.literal("Нельзя покупать у самого себя."), false);
        return false;
    }

    double pricePerUnit = getSlotPriceForMode(0, templateSlot);
    if (pricePerUnit <= 0.0) {
        NetworkRegistration.sendShopToast(buyer, net.minecraft.network.chat.Component.literal("Цена слота не установлена."), false);
        return false;
    }

    // Compute max units by stock availability for this slot
    int needPerUnit = Math.max(1, req.getCount());
    int availableCount = countMatching(req);
    int maxUnitsByStock = availableCount / needPerUnit;
    if (maxUnitsByStock <= 0) return false;

    int want = units;
    if (want == Integer.MAX_VALUE) want = maxUnitsByStock;
    if (want > maxUnitsByStock) want = maxUnitsByStock;
    if (want <= 0) return false;

    double totalPrice = MoneyUtils.round2(pricePerUnit * (double) want);
    double balance = EconomyData.getBalance(buyer.getUUID());
    if (balance + 1e-9 < totalPrice) {
        NetworkRegistration.sendShopToast(buyer, net.minecraft.network.chat.Component.translatable("msg.avilixeconomy.shop.no_money"), false);
        return false;
    }

    // Extract items
    int amount = needPerUnit * want;
    var toGive = extractMatching(req, amount);
    if (toGive.isEmpty() || toGive.getCount() != amount) {
        if (!toGive.isEmpty()) insertToStock(toGive);
        return false;
    }

    // Pay (with commission)
    UUID serverUuid;
    try {
        serverUuid = UUID.fromString(AvilixEconomyCommonConfig.ECONOMY.serverAccountUuid.get());
    } catch (Exception e) {
        serverUuid = new UUID(0L, 0L);
    }
    int commBps = getSellCommissionBps();
    double fee = CommissionManager.computeFee(totalPrice, commBps);
    double ownerNet = Math.max(0.0, MoneyUtils.round2(totalPrice - fee));
    if (!EconomyData.paySplit(buyer.getUUID(), owner, ownerNet, serverUuid, fee)) {
        insertToStock(toGive);
        return false;
    }

    net.neoforged.neoforge.items.ItemHandlerHelper.giveItemToPlayer(buyer, toGive);

    // DB log (optional): we can reuse buildItemsJson for one-stack
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
        final String itemsJson = buildItemsJson(new net.minecraft.world.item.ItemStack[]{toGive}, want);
        final double pricePerUnitFinal = pricePerUnit;
        final int unitsFinal = want;
        final double totalPriceFinal = totalPrice;

        CompletableFuture.runAsync(() -> DatabaseManager.logShopSaleNow(
                worldId, sx, sy, sz, blockIdFinal,
                ownerUuid, ownerNameFinal,
                buyerUuid, buyerNameFinal,
                "SELL_SLOT",
                pricePerUnitFinal, unitsFinal, totalPriceFinal, itemsJson
        ));
    }

    setChanged();
    return true;
}

/**
 * Slot-mode (BUY-mode): player sells only one template slot, repeated 'units' times.
 */
public boolean trySellSlotToShop(ServerPlayer seller, int templateSlot, int units) {
    if (units <= 0) return false;
    if (!buyMode) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("Магазин сейчас в режиме продажи."), false);
        return false;
    }
    if (templateSlot < 0 || templateSlot >= template.getSlots()) return false;

    var req = template.getStackInSlot(templateSlot);
    if (req == null || req.isEmpty()) return false;

    if (owner == null) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("У магазина нет владельца."), false);
        return false;
    }
    if (owner.equals(seller.getUUID())) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.translatable("msg.avilixeconomy.shop.self_trade"), false);
        return false;
    }

    double pricePerUnit = getSlotPriceForMode(1, templateSlot);
    if (pricePerUnit <= 0.0) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("Цена слота не установлена."), false);
        return false;
    }

    int needPerUnit = Math.max(1, req.getCount());
    int want = units;

    // clamp by seller inventory
    int sellerCount = countInPlayer(seller, req);
    int maxUnitsByInv = sellerCount / needPerUnit;
    if (maxUnitsByInv <= 0) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("У вас нет нужных предметов."), false);
        return false;
    }

    // clamp by stock space (for the exact items being inserted)
    // conservative: check that stock can accept amount
    int maxUnitsBySpace = getAvailableUnitsBySpace(req);
    int maxUnits = Math.min(maxUnitsByInv, maxUnitsBySpace);
    if (maxUnits <= 0) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("Недостаточно места на складе."), false);
        return false;
    }

    if (want == Integer.MAX_VALUE) want = maxUnits;
    if (want > maxUnits) want = maxUnits;
    if (want <= 0) return false;

    double totalPrice = MoneyUtils.round2(pricePerUnit * (double) want);

    double ownerBal = EconomyData.isCached(owner) ? EconomyData.getCachedBalance(owner) : EconomyData.getBalance(owner);
    int maxUnitsByMoney = (pricePerUnit <= 0.0) ? 0 : (int) Math.floor(ownerBal / pricePerUnit);
    if (want > maxUnitsByMoney) want = maxUnitsByMoney;
    if (want <= 0) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("У владельца недостаточно средств."), false);
        return false;
    }
    totalPrice = MoneyUtils.round2(pricePerUnit * (double) want);

    // Remove items from seller
    int amount = needPerUnit * want;
    if (!removeExactFromPlayer(seller, req, amount)) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("Не удалось забрать предметы из инвентаря."), false);
        return false;
    }

    // Insert into stock
    var toInsert = req.copy();
    toInsert.setCount(amount);
    insertToStock(toInsert);

    // Pay seller (with commission)
    UUID serverUuid;
    try {
        serverUuid = UUID.fromString(AvilixEconomyCommonConfig.ECONOMY.serverAccountUuid.get());
    } catch (Exception e) {
        serverUuid = new UUID(0L, 0L);
    }
    int commBps = getBuyCommissionBps();
    double fee = CommissionManager.computeFee(totalPrice, commBps);
    double sellerNet = Math.max(0.0, MoneyUtils.round2(totalPrice - fee));
    boolean paid = EconomyData.paySplit(owner, seller.getUUID(), sellerNet, serverUuid, fee);
    if (!paid) {
        // rollback: remove inserted and return
        extractMatching(req, amount); // best-effort remove (may remove different slots but same item)
        net.neoforged.neoforge.items.ItemHandlerHelper.giveItemToPlayer(seller, toInsert);
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("Оплата не прошла. Продажа отменена."), false);
        return false;
    }

    NetworkUtils.sendBalanceToPlayer(seller, EconomyData.getBalance(seller.getUUID()));

    setChanged();
    return true;
}

private int countMatching(net.minecraft.world.item.ItemStack sample) {
    int sum = 0;
    for (int i = 0; i < stock.getSlots(); i++) {
        var s = stock.getStackInSlot(i);
        if (s.isEmpty()) continue;
        if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, sample)) continue;
        sum += s.getCount();
    }
    return sum;
}

private int countInPlayer(ServerPlayer player, net.minecraft.world.item.ItemStack sample) {
    int sum = 0;
    var inv = player.getInventory();
    for (int i = 0; i < inv.getContainerSize(); i++) {
        var s = inv.getItem(i);
        if (s.isEmpty()) continue;
        if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, sample)) continue;
        sum += s.getCount();
    }
    return sum;
}

private boolean removeExactFromPlayer(ServerPlayer player, net.minecraft.world.item.ItemStack sample, int amount) {
    int remaining = amount;
    var inv = player.getInventory();

    for (int i = 0; i < inv.getContainerSize(); i++) {
        var s = inv.getItem(i);
        if (s.isEmpty()) continue;
        if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, sample)) continue;
        int take = Math.min(remaining, s.getCount());
        s.shrink(take);
        if (s.getCount() <= 0) inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
        remaining -= take;
        if (remaining <= 0) break;
    }
    inv.setChanged();
    return remaining <= 0;
}

private int getAvailableUnitsBySpace(net.minecraft.world.item.ItemStack sample) {
    // Compute how many more items of this type we can insert, then divide by sample.count
    int freeItems = 0;
    for (int i = 0; i < stock.getSlots(); i++) {
        var s = stock.getStackInSlot(i);
        if (s.isEmpty()) {
            freeItems += sample.getMaxStackSize();
        } else if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(s, sample)) {
            freeItems += (s.getMaxStackSize() - s.getCount());
        }
    }
    int perUnit = Math.max(1, sample.getCount());
    return freeItems / perUnit;
}

/**
 * BUY-mode: player sells items to the shop. Owner pays the seller, items go into stock.
 * Returns true if transaction succeeded.
 */
public boolean trySellToShop(ServerPlayer seller, int lots) {
    if (lots <= 0) return false;
    if (!buyMode) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("Магазин сейчас в режиме продажи."), false);
        return false;
    }
    if (owner == null) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("У магазина нет владельца."), false);
        return false;
    }
	    // Prevent selling to your own shop.
	    if (owner.equals(seller.getUUID())) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.translatable("msg.avilixeconomy.shop.self_trade"), false);
	        return false;
	    }
    double pricePerLot = priceBuyPerLot;
    if (pricePerLot <= 0.0) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("Цена скупки не установлена."), false);
        return false;
    }
    // Validate template
    java.util.List<net.minecraft.world.item.ItemStack> lot = getTemplateStacks();
    if (lot.isEmpty()) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("Лот не настроен."), false);
        return false;
    }
    // Check seller has required items
    if (!playerHasItems(seller, lot, lots)) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("У вас нет нужных предметов для продажи."), false);
        return false;
    }
    // Check stock has enough space (strict rule: if not enough space -> reject)
    int maxLotsBySpace = getAvailableLotsBySpace(lot);
    if (lots > maxLotsBySpace) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("Недостаточно места на складе. Доступно лотов: " + maxLotsBySpace), false);
        return false;
    }
    // Check owner has money
    double totalPrice = MoneyUtils.round2(pricePerLot * (double) lots);
    double ownerBal = EconomyData.getCachedBalance(owner);
    if (!EconomyData.isCached(owner)) {
        ownerBal = EconomyData.getBalance(owner);
    }
    long maxLotsByMoney = (pricePerLot <= 0.0) ? 0L : (long) Math.floor(ownerBal / pricePerLot);
    if (lots > maxLotsByMoney) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("У владельца недостаточно средств. Доступно лотов: " + maxLotsByMoney), false);
        return false;
    }

    // Remove items from seller inventory
    if (!removeItemsFromPlayer(seller, lot, lots)) {
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("Не удалось забрать предметы из инвентаря."), false);
        return false;
    }

    // Insert into stock (should succeed due to pre-check)
    if (!insertLotIntoStock(lot, lots)) {
        // rollback: return items
        giveItemsToPlayer(seller, lot, lots);
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("Склад переполнен. Продажа отменена."), false);
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
        double fee = CommissionManager.computeFee(totalPrice, commBps);
        double sellerNet = Math.max(0.0, MoneyUtils.round2(totalPrice - fee));
        boolean paid = EconomyData.paySplit(owner, seller.getUUID(), sellerNet, serverUuid, fee);
    if (!paid) {
        // rollback: remove inserted and return, but safest: attempt to refund items to seller
        // Try to extract back and give seller (best-effort)
        extractLotFromStock(lot, lots);
        giveItemsToPlayer(seller, lot, lots);
        NetworkRegistration.sendShopToast(seller, net.minecraft.network.chat.Component.literal("Оплата не прошла. Продажа отменена."), false);
        return false;
    }

    // Force-send seller balance update using the live ServerPlayer connection.
    // (Some servers report that the generic UUID-based sync can be missed during menu-driven actions.)
    try {
        EconomyData.sendBalanceUpdateToPlayer(seller);
    } catch (Throwable ignored) {
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
        final double pricePerLotFinal = pricePerLot;
        final int lotsFinal = lots;
        final double totalPriceFinal = totalPrice;
        java.util.concurrent.CompletableFuture.runAsync(() -> DatabaseManager.logShopSaleNow(
                worldId, sx, sy, sz, blockIdFinal,
                ownerUuid, ownerNameFinal,
                sellerUuid, sellerNameFinal,
                "BUY",
                pricePerLotFinal, lotsFinal, totalPriceFinal, itemsJson
        ));
    }

    setChanged();
    NetworkRegistration.sendShopToast(seller,
            net.minecraft.network.chat.Component.translatable(
                    "msg.avilixeconomy.shop.sold",
                    lots,
                    MoneyUtils.formatSmart(totalPrice)
            ),
            true
    );
    return true;
}

public int getAvailableBuyLots() {
    if (!buyMode) return 0;
    java.util.List<net.minecraft.world.item.ItemStack> lot = getTemplateStacks();
    if (lot.isEmpty()) return 0;
    double price = priceBuyPerLot;
    if (price <= 0.0 || owner == null) return 0;
    double ownerBal = EconomyData.getCachedBalance(owner);
    if (!EconomyData.isCached(owner)) {
        ownerBal = EconomyData.getBalance(owner);
    }
    long byMoney = (price <= 0.0) ? 0L : (long) Math.floor(ownerBal / price);
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

    // ===== Client sync for in-world overlays (items/counts) =====

    private void syncToClient() {
        if (level == null) return;
        if (level.isClientSide) return;
        // Triggers a block entity data packet (see getUpdatePacket/getUpdateTag below)
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.loadAdditional(tag, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID(TAG_OWNER, owner);
        if (ownerName != null && !ownerName.isBlank()) tag.putString(TAG_OWNER_NAME, ownerName);
        tag.putDouble(TAG_PRICE, MoneyUtils.round2(priceSellPerLot));
        tag.putDouble(TAG_PRICE_BUY, MoneyUtils.round2(priceBuyPerLot));
        // legacy defaults (kept for older clients/old saves)
        tag.putDouble(TAG_PRICE_SLOT, MoneyUtils.round2(legacyPriceSellPerSlot));
        tag.putDouble(TAG_PRICE_BUY_SLOT, MoneyUtils.round2(legacyPriceBuyPerSlot));

        // per-template-slot prices
        tag.put(TAG_PRICE_SLOTS, writeDoubleList(priceSellPerTemplateSlot));
        tag.put(TAG_PRICE_BUY_SLOTS, writeDoubleList(priceBuyPerTemplateSlot));
        tag.putBoolean(TAG_MODE, buyMode);
        tag.put(TAG_TEMPLATE, template.serializeNBT(registries));
        tag.put(TAG_STOCK, stock.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        ownerName = tag.contains(TAG_OWNER_NAME) ? tag.getString(TAG_OWNER_NAME) : null;
        legacyPriceSellPerSlot = tag.contains(TAG_PRICE_SLOT) ? readMoneyTag(tag, TAG_PRICE_SLOT, 0.0) : 0.0;
        legacyPriceBuyPerSlot = tag.contains(TAG_PRICE_BUY_SLOT) ? readMoneyTag(tag, TAG_PRICE_BUY_SLOT, legacyPriceSellPerSlot) : legacyPriceSellPerSlot;
        priceSellPerLot = readMoneyTag(tag, TAG_PRICE, 0.0);
        priceBuyPerLot = tag.contains(TAG_PRICE_BUY) ? readMoneyTag(tag, TAG_PRICE_BUY, priceSellPerLot) : priceSellPerLot;
        buyMode = tag.contains(TAG_MODE) && tag.getBoolean(TAG_MODE);
        if (tag.contains(TAG_TEMPLATE)) template.deserializeNBT(registries, tag.getCompound(TAG_TEMPLATE));
        if (tag.contains(TAG_STOCK)) stock.deserializeNBT(registries, tag.getCompound(TAG_STOCK));

        // Load per-template-slot prices (if present). Otherwise migrate from legacy single-slot price.
        boolean hadSellSlots = readDoubleListInto(tag, TAG_PRICE_SLOTS, priceSellPerTemplateSlot);
        boolean hadBuySlots = readDoubleListInto(tag, TAG_PRICE_BUY_SLOTS, priceBuyPerTemplateSlot);
        if (!hadSellSlots) {
            for (int i = 0; i < TEMPLATE_SLOTS; i++) {
                priceSellPerTemplateSlot[i] = template.getStackInSlot(i).isEmpty() ? 0.0 : legacyPriceSellPerSlot;
            }
        }
        if (!hadBuySlots) {
            for (int i = 0; i < TEMPLATE_SLOTS; i++) {
                priceBuyPerTemplateSlot[i] = template.getStackInSlot(i).isEmpty() ? 0.0 : legacyPriceBuyPerSlot;
            }
        }
    }

    private static net.minecraft.nbt.ListTag writeDoubleList(double[] arr) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        if (arr == null) return list;
        for (double v : arr) list.add(net.minecraft.nbt.DoubleTag.valueOf(MoneyUtils.round2(v)));
        return list;
    }

    private static boolean readDoubleListInto(CompoundTag tag, String key, double[] out) {
        if (tag == null || key == null || out == null) return false;
        if (!tag.contains(key, Tag.TAG_LIST)) return false;
        net.minecraft.nbt.ListTag list = tag.getList(key, Tag.TAG_DOUBLE);
        if (list.isEmpty()) return false;
        int n = Math.min(out.length, list.size());
        for (int i = 0; i < n; i++) out[i] = MoneyUtils.round2(list.getDouble(i));
        for (int i = n; i < out.length; i++) out[i] = 0.0;
        return true;
    }

    /**
     * Backward compatible money reader:
     *  - new saves store money as double (TAG_DOUBLE)
     *  - old saves may store as long/int
     *  - some edge cases store as string
     */
    private static double readMoneyTag(CompoundTag tag, String key, double def) {
        if (tag == null || key == null) return MoneyUtils.round2(def);

        if (tag.contains(key, Tag.TAG_DOUBLE)) {
            return MoneyUtils.round2(tag.getDouble(key));
        }
        if (tag.contains(key, Tag.TAG_FLOAT)) {
            return MoneyUtils.round2(tag.getFloat(key));
        }
        if (tag.contains(key, Tag.TAG_LONG)) {
            return MoneyUtils.round2((double) tag.getLong(key));
        }
        if (tag.contains(key, Tag.TAG_INT)) {
            return MoneyUtils.round2((double) tag.getInt(key));
        }
        if (tag.contains(key, Tag.TAG_STRING)) {
            return MoneyUtils.parseSmart(tag.getString(key));
        }
        return MoneyUtils.round2(def);
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

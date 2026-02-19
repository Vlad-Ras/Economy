package com.roften.avilixeconomy.shop.menu;

import com.roften.avilixeconomy.registry.ModBlocks;
import com.roften.avilixeconomy.shop.blockentity.ShopBlockEntity;
import com.roften.avilixeconomy.util.Permissions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Owner config menu: template + stock + player inventory.
 */
public class ShopConfigMenu extends AbstractContainerMenu {

    public static final int LEFT_W = 176;
    public static final int RIGHT_W = 96;
    public static final int GUI_WIDTH = LEFT_W + RIGHT_W;

    /** Extra top padding so section labels (headers) don't collide with the first slot row. */
    public static final int TOP_PAD = 24;

    public static final int TEMPLATE_X = 8;
    public static final int TEMPLATE_Y = 18 + TOP_PAD;

    public static final int STOCK_X = 8;
    // Extra spacing so section headers never overlap slot rows
    public static final int STOCK_Y = 90 + TOP_PAD;

    public static final int INV_X = 8;
    // Leave room between stock grid and player inventory header
    public static final int INV_Y = 194 + TOP_PAD + 24;

    public static final int HOTBAR_Y = 252 + TOP_PAD + 24;

    public static final int GUI_HEIGHT = 276 + TOP_PAD + 24;

    /** Slot counts inside the shop menu. */
    private static final int TEMPLATE_SLOTS = 9;
    private static final int STOCK_SLOTS = 54;

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    @Nullable
    private final ShopBlockEntity shop;
    private int syncedMode;
    private int sellLo, sellHi;
    private int buyLo, buyHi;
    // Per-template-slot prices are synced via BE update tag; we read them from the client-side BE.
    private int selectedTemplateSlot = 0;

    private int priceLo;
    private int priceHi;
    private int availableLots;
    private int commissionSellBps;
    private int commissionBuyBps;

    /** Synced flag: is current player allowed to tune render transforms (admin-only). */
    private int renderEditFlag;

    // Client-side safety: when the BE isn't present, provide a stable empty handler for screens.
    private final ItemStackHandler emptyTemplate = new ItemStackHandler(9);

    public static ShopConfigMenu create(int id, Inventory inv, ShopBlockEntity shop) {
        return new ShopConfigMenu(id, inv, shop.getBlockPos(), shop);
    }

    public ShopConfigMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    private ShopConfigMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, getShop(inv, pos));
    }

    private static ShopBlockEntity getShop(Inventory inv, BlockPos pos) {
        if (inv.player.level().getBlockEntity(pos) instanceof ShopBlockEntity s) return s;
        return null;
    }

    private ShopConfigMenu(int id, Inventory inv, BlockPos pos, @Nullable ShopBlockEntity shop) {
        super(com.roften.avilixeconomy.registry.ModMenus.SHOP_CONFIG_MENU.get(), id);
        this.pos = pos;
        this.shop = shop;

        this.addDataSlot(new DataSlot(){
            @Override public int get(){ return shop != null && shop.isBuyMode()?1:0; }
            @Override public void set(int v){ syncedMode=v; }
        });
        // commission (effective for this shop owner)
        this.addDataSlot(new DataSlot(){
            @Override public int get(){ return shop != null ? shop.getSellCommissionBps() : 0; }
            @Override public void set(int v){ commissionSellBps = v; }
        });
        this.addDataSlot(new DataSlot(){
            @Override public int get(){ return shop != null ? shop.getBuyCommissionBps() : 0; }
            @Override public void set(int v){ commissionBuyBps = v; }
        });

        // admin flag: render tuning
        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (inv.player instanceof ServerPlayer sp) {
                    return Permissions.canEditShopRender(sp) ? 1 : 0;
                }
                return 0;
            }

            @Override
            public void set(int v) {
                renderEditFlag = v;
            }
        });

        // sell price
        this.addDataSlot(new DataSlot(){
            @Override public int get(){ long bits = Double.doubleToRawLongBits(shop != null ? shop.getPricePerLot() : 0.0); return (int)(bits & 0xFFFFFFFFL);} 
            @Override public void set(int v){ sellLo=v; }
        });
        this.addDataSlot(new DataSlot(){
            @Override public int get(){ long bits = Double.doubleToRawLongBits(shop != null ? shop.getPricePerLot() : 0.0); return (int)((bits>>>32)&0xFFFFFFFFL);} 
            @Override public void set(int v){ sellHi=v; }
        });
        // buy price
        this.addDataSlot(new DataSlot(){
            @Override public int get(){ long bits = Double.doubleToRawLongBits(shop != null ? shop.getPriceBuyPerLot() : 0.0); return (int)(bits & 0xFFFFFFFFL);} 
            @Override public void set(int v){ buyLo=v; }
        });
        this.addDataSlot(new DataSlot(){
            @Override public int get(){ long bits = Double.doubleToRawLongBits(shop != null ? shop.getPriceBuyPerLot() : 0.0); return (int)((bits>>>32)&0xFFFFFFFFL);} 
            @Override public void set(int v){ buyHi=v; }
        });



        this.access = ContainerLevelAccess.create(inv.player.level(), pos);

        // ===== template 3x3 (editable) + stock 6x9 (editable) =====
        if (shop != null) {
            int tX = TEMPLATE_X;
            int tY = TEMPLATE_Y;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int slot = r * 3 + c;
                    // Template is phantom-only: it never consumes real items from the player inventory.
                    this.addSlot(new GhostTemplateSlot(shop, slot, tX + c * 18, tY + r * 18));
                }
            }

            // ===== stock 6x9 (editable) =====
            int sX = STOCK_X;
            int sY = STOCK_Y;
            for (int r = 0; r < 6; r++) {
                for (int c = 0; c < 9; c++) {
                    int slot = r * 9 + c;
                    this.addSlot(new SlotItemHandler(shop.getStock(), slot, sX + c * 18, sY + r * 18));
                }
            }
        } else {
            // keep slot indices stable even if BE isn't available client-side
            for (int i = 0; i < 9 + 54; i++) {
                this.addSlot(new Slot(inv, 0, -10000, -10000));
            }
        }

        // ===== player inventory =====
        int invX = INV_X;
        int invY = INV_Y;
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, invX + col * 18, invY + row * 18));
            }
        }
        int hotbarY = HOTBAR_Y;
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inv, col, invX + col * 18, hotbarY));
        }

        // ===== data sync =====
        addDataSlot(new DataSlot() {
            @Override public int get() { return priceLo; }
            @Override public void set(int v) { priceLo = v; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return priceHi; }
            @Override public void set(int v) { priceHi = v; }
        });
        addDataSlot(new DataSlot() {
            @Override public int get() { return availableLots; }
            @Override public void set(int v) { availableLots = v; }
        });
    }

    /**
     * Template inventory (3x3) used by the shelf renderer. Exposed for the render tuning UI.
     * On the client, the BE might be missing during edge-cases; in that case returns an empty handler.
     */
    public IItemHandler getTemplateInventory() {
        return shop != null ? shop.getTemplate() : emptyTemplate;
    }

    @Override
    public void broadcastChanges() {
        if (shop != null) {
            long priceBits = Double.doubleToRawLongBits(shop.getPricePerLot());
            priceLo = (int) (priceBits & 0xFFFFFFFFL);
            priceHi = (int) ((priceBits >>> 32) & 0xFFFFFFFFL);
            availableLots = shop.getAvailableLots();
        }
        super.broadcastChanges();
    }

    public BlockPos getPos() {
        return pos;
    }

    public double getPricePerLot() {
        long bits = ((long) priceHi << 32) | (priceLo & 0xFFFFFFFFL);
        return Double.longBitsToDouble(bits);
    }

    public int getAvailableLots() {
        return availableLots;
    }

    @Nullable
    public ShopBlockEntity getShop() {
        return shop;
    }

    public boolean canEditRender() {
        return renderEditFlag != 0;
    }

    /**
     * Make template slots phantom-only: clicks copy the carried stack into the template,
     * without moving any real items between inventories.
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (shop != null && slotId >= 0 && slotId < TEMPLATE_SLOTS) {
            // Ignore drag / hotbar swaps / throw / shift-move etc. for ghost slots.
            if (clickType == ClickType.QUICK_CRAFT
                    || clickType == ClickType.QUICK_MOVE
                    || clickType == ClickType.SWAP
                    || clickType == ClickType.THROW
                    || clickType == ClickType.CLONE
                    || clickType == ClickType.PICKUP_ALL) {
                return;
            }

            net.minecraft.world.item.ItemStack carried = this.getCarried();
            if (carried.isEmpty()) {
                // Right-click with empty hand clears the ghost slot.
                if (button == 1) {
                    shop.getTemplate().setStackInSlot(slotId, net.minecraft.world.item.ItemStack.EMPTY);
                    markShopDirtyAndSync();
                }
                return;
            }

            // Left click = copy full carried count; Right click = set count to 1.
            net.minecraft.world.item.ItemStack ghost = carried.copy();
            int count = (button == 1) ? 1 : carried.getCount();
            if (count < 1) count = 1;
            if (count > ghost.getMaxStackSize()) count = ghost.getMaxStackSize();
            ghost.setCount(count);

            shop.getTemplate().setStackInSlot(slotId, ghost);
            markShopDirtyAndSync();
            return;
        }

        super.clicked(slotId, button, clickType, player);
    }

    private void markShopDirtyAndSync() {
        if (shop == null) return;
        shop.enforceMinPriceFloors();
        shop.setChanged();
        var lvl = shop.getLevel();
        if (lvl != null && !lvl.isClientSide) {
            lvl.sendBlockUpdated(shop.getBlockPos(), shop.getBlockState(), shop.getBlockState(), 3);
        }
    }

    

    public int getCommissionSellBps() { return commissionSellBps; }
    public int getCommissionBuyBps() { return commissionBuyBps; }

public int getMode(){ return syncedMode; }

public double getSellPriceSynced(){ long bits = ((long)sellHi<<32) | (sellLo & 0xFFFFFFFFL);
        return Double.longBitsToDouble(bits); }

public double getBuyPriceSynced(){ long bits = ((long)buyHi<<32) | (buyLo & 0xFFFFFFFFL);
        return Double.longBitsToDouble(bits); }


public void setSelectedTemplateSlot(int slot){
    this.selectedTemplateSlot = Math.max(0, Math.min(8, slot));
}

public int getSelectedTemplateSlot(){ return selectedTemplateSlot; }

public double getSellSlotPriceSynced(){
    return shop != null ? shop.getSlotPriceForMode(0, selectedTemplateSlot) : 0.0;
}

public double getBuySlotPriceSynced(){
    return shop != null ? shop.getSlotPriceForMode(1, selectedTemplateSlot) : 0.0;
}

public double getActiveSlotPriceSynced(){ return getMode()==1 ? getBuySlotPriceSynced() : getSellSlotPriceSynced(); }

public double getActivePriceSynced(){ return getMode()==1 ? getBuyPriceSynced() : getSellPriceSynced(); }
@Override
    public boolean stillValid(Player player) {
        // Owners can always use the config; admins with shop.open_any can inspect/configure any shop.
        if (shop != null && !shop.isOwner(player)) {
            if (player instanceof ServerPlayer sp && Permissions.canOpenAnyShop(sp)) {
                return stillValid(access, player, ModBlocks.SHOP.get());
            }
            return false;
        }
        return stillValid(access, player, ModBlocks.SHOP.get());
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        // Simple shift-click handling.
        net.minecraft.world.item.ItemStack empty = net.minecraft.world.item.ItemStack.EMPTY;

        // Never shift-move from phantom template.
        if (index >= 0 && index < TEMPLATE_SLOTS) return empty;

        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return empty;
        net.minecraft.world.item.ItemStack stack = slot.getItem();
        net.minecraft.world.item.ItemStack copy = stack.copy();

        // Shop slots: 0..(9+54-1)=62, player inv starts after that.
        int shopSlots = TEMPLATE_SLOTS + STOCK_SLOTS;
        if (index < shopSlots) {
            if (!this.moveItemStackTo(stack, shopSlots, this.slots.size(), true)) {
                return empty;
            }
        } else {
            // from player to stock only (not template)
            if (!this.moveItemStackTo(stack, TEMPLATE_SLOTS, shopSlots, false)) {
                return empty;
            }
        }

        if (stack.isEmpty()) {
            slot.set(empty);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    /** Non-interactive slot used to display template (ghost) items. */
    private static class GhostTemplateSlot extends SlotItemHandler {
        private GhostTemplateSlot(ShopBlockEntity shop, int index, int x, int y) {
            super(shop.getTemplate(), index, x, y);
        }

        @Override
        public boolean mayPlace(net.minecraft.world.item.ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
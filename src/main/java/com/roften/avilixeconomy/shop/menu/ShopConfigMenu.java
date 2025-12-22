package com.roften.avilixeconomy.shop.menu;

import com.roften.avilixeconomy.registry.ModBlocks;
import com.roften.avilixeconomy.shop.blockentity.ShopBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.items.SlotItemHandler;
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

    private final ContainerLevelAccess access;
    private final BlockPos pos;
    @Nullable
    private final ShopBlockEntity shop;
    private int syncedMode;
    private int sellLo, sellHi;
    private int buyLo, buyHi;

    private int priceLo;
    private int priceHi;
    private int availableLots;
    private int commissionSellBps;
    private int commissionBuyBps;

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

        // sell price
        this.addDataSlot(new DataSlot(){
            @Override public int get(){ long p=shop != null ? shop.getPricePerLot() : 0L; return (int)(p & 0xFFFFFFFFL);} 
            @Override public void set(int v){ sellLo=v; }
        });
        this.addDataSlot(new DataSlot(){
            @Override public int get(){ long p=shop != null ? shop.getPricePerLot() : 0L; return (int)((p>>>32)&0xFFFFFFFFL);} 
            @Override public void set(int v){ sellHi=v; }
        });
        // buy price
        this.addDataSlot(new DataSlot(){
            @Override public int get(){ long p=shop != null ? shop.getPriceBuyPerLot() : 0L; return (int)(p & 0xFFFFFFFFL);} 
            @Override public void set(int v){ buyLo=v; }
        });
        this.addDataSlot(new DataSlot(){
            @Override public int get(){ long p=shop != null ? shop.getPriceBuyPerLot() : 0L; return (int)((p>>>32)&0xFFFFFFFFL);} 
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
                    this.addSlot(new SlotItemHandler(shop.getTemplate(), slot, tX + c * 18, tY + r * 18));
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

    @Override
    public void broadcastChanges() {
        if (shop != null) {
            long price = shop.getPricePerLot();
            priceLo = (int) (price & 0xFFFFFFFFL);
            priceHi = (int) ((price >>> 32) & 0xFFFFFFFFL);
            availableLots = shop.getAvailableLots();
        }
        super.broadcastChanges();
    }

    public BlockPos getPos() {
        return pos;
    }

    public long getPricePerLot() {
        return ((long) priceHi << 32) | (priceLo & 0xFFFFFFFFL);
    }

    public int getAvailableLots() {
        return availableLots;
    }

    @Nullable
    public ShopBlockEntity getShop() {
        return shop;
    }

    

    public int getCommissionSellBps() { return commissionSellBps; }
    public int getCommissionBuyBps() { return commissionBuyBps; }

public int getMode(){ return syncedMode; }

public long getSellPriceSynced(){ return ((long)sellHi<<32) | (sellLo & 0xFFFFFFFFL); }

public long getBuyPriceSynced(){ return ((long)buyHi<<32) | (buyLo & 0xFFFFFFFFL); }

public long getActivePriceSynced(){ return getMode()==1 ? getBuyPriceSynced() : getSellPriceSynced(); }
@Override
    public boolean stillValid(Player player) {
        if (shop != null && !shop.isOwner(player)) return false;
        return stillValid(access, player, ModBlocks.SHOP.get());
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        // Simple shift-click handling.
        net.minecraft.world.item.ItemStack empty = net.minecraft.world.item.ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return empty;
        net.minecraft.world.item.ItemStack stack = slot.getItem();
        net.minecraft.world.item.ItemStack copy = stack.copy();

        // Shop slots: 0..(9+54-1)=62, player inv starts after that.
        int shopSlots = 9 + 54;
        if (index < shopSlots) {
            if (!this.moveItemStackTo(stack, shopSlots, this.slots.size(), true)) {
                return empty;
            }
        } else {
            // from player to stock only (not template)
            if (!this.moveItemStackTo(stack, 9, shopSlots, false)) {
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
}
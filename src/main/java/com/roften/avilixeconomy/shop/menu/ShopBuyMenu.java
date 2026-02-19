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
 * Buy-only menu: shows the template (read-only) and player inventory.
 */
public class ShopBuyMenu extends AbstractContainerMenu {

    public static final int GUI_WIDTH = 176;

    /**
     * Extra vertical padding so section headers and panels in the screen do not collide with slot rows.
     * This keeps the UI readable at different GUI scales.
     */
    public static final int TOP_PAD = 24;

    // Template is drawn by the screen as a "ghost" preview (non-interactive).
    // Keep it centered in the left panel.
    public static final int TEMPLATE_X = (GUI_WIDTH - 3 * 18) / 2;
    public static final int TEMPLATE_Y = 20 + TOP_PAD;

    public static final int INV_X = 8;
    /** Leave extra gap below template so the "Inventory" header fits. */
    public static final int INV_Y = 84 + TOP_PAD + 10;

    public static final int HOTBAR_Y = INV_Y + 58;

    /** Total GUI height including hotbar and bottom padding. */
    public static final int GUI_HEIGHT = HOTBAR_Y + 24;

    private final ContainerLevelAccess access;
    private final BlockPos pos;

    @Nullable
    private final ShopBlockEntity shop;

    // synced values (server -> client)
    private int syncedMode;
    private int priceLo;
    private int priceHi;
    private int slotPriceLo;
    private int slotPriceHi;
    private int availableLots;
    private int commissionBps;

    public static ShopBuyMenu create(int id, Inventory inv, ShopBlockEntity shop) {
        return new ShopBuyMenu(id, inv, shop.getBlockPos(), shop);
    }

    public ShopBuyMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    private ShopBuyMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, pos, getShop(inv, pos));
    }

    private static ShopBlockEntity getShop(Inventory inv, BlockPos pos) {
        if (inv.player.level().getBlockEntity(pos) instanceof ShopBlockEntity s) return s;
        return null;
    }

    private ShopBuyMenu(int id, Inventory inv, BlockPos pos, @Nullable ShopBlockEntity shop) {
        super(com.roften.avilixeconomy.registry.ModMenus.SHOP_BUY_MENU.get(), id);
        this.pos = pos;
        this.shop = shop;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);

        // ===== data sync (ints only) =====
        this.addDataSlot(new DataSlot() {
            @Override public int get() { return shop != null && shop.isBuyMode() ? 1 : 0; }
            @Override public void set(int value) { syncedMode = value; }
        });
        this.addDataSlot(new DataSlot() {
            @Override public int get() {
                long bits = Double.doubleToRawLongBits(shop != null ? shop.getActivePricePerLot() : 0.0);
                return (int) (bits & 0xFFFFFFFFL);
            }
            @Override public void set(int value) { priceLo = value; }
        });
        this.addDataSlot(new DataSlot() {
            @Override public int get() {
                long bits = Double.doubleToRawLongBits(shop != null ? shop.getActivePricePerLot() : 0.0);
                return (int) ((bits >>> 32) & 0xFFFFFFFFL);
            }
            @Override public void set(int value) { priceHi = value; }
        });

this.addDataSlot(new DataSlot() {
    @Override public int get() {
        long bits = Double.doubleToRawLongBits(shop != null ? shop.getActivePricePerSlot() : 0.0);
        return (int) (bits & 0xFFFFFFFFL);
    }
    @Override public void set(int value) { slotPriceLo = value; }
});
this.addDataSlot(new DataSlot() {
    @Override public int get() {
        long bits = Double.doubleToRawLongBits(shop != null ? shop.getActivePricePerSlot() : 0.0);
        return (int) ((bits >>> 32) & 0xFFFFFFFFL);
    }
    @Override public void set(int value) { slotPriceHi = value; }
});

        this.addDataSlot(new DataSlot() {
            @Override public int get() {
                return shop != null
                        ? (shop.isBuyMode() ? shop.getAvailableBuyLotsCached() : shop.getAvailableLotsCached())
                        : 0;
            }
            @Override public void set(int value) { availableLots = value; }
        });
        // commission bps (effective for current mode, based on owner override + global)
        this.addDataSlot(new DataSlot() {
            @Override public int get() {
                if (shop == null) return 0;
                return shop.isBuyMode() ? shop.getBuyCommissionBps() : shop.getSellCommissionBps();
            }
            @Override public void set(int v) { commissionBps = v; }
        });


        // ===== template (3x3) read-only =====
        if (shop != null) {
            // Keep the container slots so the server syncs the template contents,
            // but move them off-screen (the screen renders the preview manually).
            int startX = -10000;
            int startY = -10000;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int slot = r * 3 + c;
                    this.addSlot(new SlotItemHandler(shop.getTemplate(), slot, startX + c * 18, startY + r * 18) {
                        @Override
                        public boolean mayPlace(net.minecraft.world.item.ItemStack stack) {
                            return false;
                        }

                        @Override
                        public boolean mayPickup(Player player) {
                            return false;
                        }
                    });
                }
            }
        } else {
            // Still create dummy slots to keep layout stable
            for (int i = 0; i < 9; i++) {
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
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getMode() {
        return syncedMode;
    }

    /** Active (current mode) price-per-lot synced from server. */
    public double getPricePerLot() {
        long bits = ((long) priceHi << 32) | (priceLo & 0xFFFFFFFFL);
        return Double.longBitsToDouble(bits);
    }

    /** Active (current mode) price-per-slot synced from server. */
    public double getPricePerSlot() {
        long bits = ((long) slotPriceHi << 32) | (slotPriceLo & 0xFFFFFFFFL);
        return Double.longBitsToDouble(bits);
    }

    /** Alias used by some screens. */
    public double getActivePricePerLot() {
        return getPricePerLot();
    }

    public int getAvailableLots() {
        return availableLots;
    }
    public int getCommissionBps() {
        return commissionBps;
    }


    /** Alias used by older code. */
    public int getAvailableLotsSynced() {
        return availableLots;
    }

    @Nullable
    public ShopBlockEntity getShop() {
        return shop;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.SHOP.get());
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        // nothing special; no shop-side editable slots
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}
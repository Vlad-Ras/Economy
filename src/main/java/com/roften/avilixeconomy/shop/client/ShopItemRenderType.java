package com.roften.avilixeconomy.shop.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

/**
 * High-level render type bucket for shelf items.
 * Used for TYPE-level overrides.
 */
public enum ShopItemRenderType {
    NORMAL("normal"),
    BLOCK_ITEM("block_item"),
    HANDHELD("handheld"),
    TORCH("torch"),
    THIN("thin"),
    CREATE("create"),
    CUSTOM_RENDERER("custom_renderer");

    private final String id;

    ShopItemRenderType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ShopItemRenderType detect(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return NORMAL;

        Item item = stack.getItem();

        if (item instanceof BlockItem) {
            return BLOCK_ITEM;
        }

        if (isHandheldTool(item)) {
            return HANDHELD;
        }

        if (isTorch(stack)) {
            return TORCH;
        }

        if (isCreate(stack)) {
            return CREATE;
        }

        // Custom renderer detection requires a baked model on the client.
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                BakedModel model = mc.getItemRenderer().getModel(stack, null, null, 0);
                if (model != null && model.isCustomRenderer()) {
                    return CUSTOM_RENDERER;
                }
            }
        } catch (Throwable ignored) {
        }

        // "Thin" bucket: flat items that tend to sink into the stair geometry.
        // We keep it simple: books + paper + maps + "flesh" + "coil" by name.
        if (item instanceof BookItem || item instanceof MapItem || item instanceof WrittenBookItem
                || item instanceof WritableBookItem || item instanceof EmptyMapItem
                || item == Items.PAPER || item == Items.ROTTEN_FLESH) {
            return THIN;
        }
        String name = stack.getHoverName().getString().toLowerCase();
        if (name.contains("coil") || name.contains("катушка") || name.contains("book") || name.contains("книга")) {
            return THIN;
        }

        return NORMAL;
    }

    private static boolean isHandheldTool(Item item) {
        return item instanceof SwordItem
                || item instanceof DiggerItem
                || item instanceof TridentItem
                || item instanceof FishingRodItem
                || item instanceof ShearsItem;
    }

    private static boolean isTorch(ItemStack stack) {
        try {
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) return false;
            String path = id.getPath();
            return path.contains("torch") || path.contains("фак") || path.contains("lamp");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isCreate(ItemStack stack) {
        try {
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id != null && "create".equals(id.getNamespace());
        } catch (Throwable ignored) {
            return false;
        }
    }
}

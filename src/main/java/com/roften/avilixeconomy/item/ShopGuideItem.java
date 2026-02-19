package com.roften.avilixeconomy.item;

import com.roften.avilixeconomy.AvilixEconomy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Player-facing guide book item. Opens Patchouli book if Patchouli is present.
 * Safe to keep even if Patchouli is not installed (uses reflection).
 */
public class ShopGuideItem extends Item {

    public static final ResourceLocation BOOK_ID = ResourceLocation.fromNamespaceAndPath(AvilixEconomy.MODID, "shop_guide");

    public ShopGuideItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            boolean opened = tryOpenPatchouliBook();
            if (!opened) {
                player.displayClientMessage(Component.translatable("item.avilixeconomy.shop_guide.tooltip")
                        .withStyle(ChatFormatting.RED), true);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.avilixeconomy.shop_guide.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }

    private static boolean tryOpenPatchouliBook() {
        try {
            Class<?> apiCl = Class.forName("vazkii.patchouli.api.PatchouliAPI");
            Method get = apiCl.getMethod("get");
            Object api = get.invoke(null);
            Method open = api.getClass().getMethod("openBookGUI", ResourceLocation.class);
            open.invoke(api, BOOK_ID);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}

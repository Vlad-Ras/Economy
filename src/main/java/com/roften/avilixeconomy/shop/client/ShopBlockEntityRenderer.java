package com.roften.avilixeconomy.shop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.roften.avilixeconomy.shop.blockentity.ShopBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the shop lot (template items) on top of the shop block.
 *
 * Requirements from user:
 * - show items only (no numbers)
 * - show up to 9 items (3x3), laid flat (horizontal) on the block top
 */
public class ShopBlockEntityRenderer implements BlockEntityRenderer<ShopBlockEntity> {

    private final Minecraft mc;

    public ShopBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.mc = Minecraft.getInstance();
    }

    @Override
    public void render(ShopBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (be == null || be.getLevel() == null) return;

        // Use light from above so items don't look too dark.
        int light = LightTexture.FULL_BRIGHT;
        try {
            light = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());
        } catch (Throwable ignored) {
        }

        // Fast skip: if all 9 template slots are empty - do nothing.
        boolean any = false;
        for (int i = 0; i < Math.min(9, be.getTemplate().getSlots()); i++) {
            if (!be.getTemplate().getStackInSlot(i).isEmpty()) {
                any = true;
                break;
            }
        }
        if (!any) return;

        pose.pushPose();

        // Place items on the 3 "stairs" of the vitrina model (based on Vitrina.bbmodel geometry).
        // Coordinates are in block space (0..1). Our local reference assumes FACING = NORTH.
        pose.translate(0.5, 0.0, 0.5);

        // Rotate the whole layout by block facing so rows sit on the correct side.
        try {
            var facing = be.getBlockState().getValue(com.roften.avilixeconomy.shop.block.ShopBlock.FACING);
            float rotY = 180.0f - facing.toYRot(); // NORTH->0, EAST->-90, SOUTH->180, WEST->90
            pose.mulPose(Axis.YP.rotationDegrees(rotY));
        } catch (Throwable ignored) {
        }

        // 3 columns across the shelf (centers), in blocks.
        final float[] xCols = new float[]{-0.21875f, 0.0f, 0.21875f}; // ~(-3.5px, 0, +3.5px)

        // 3 stair top levels from Vitrina.bbmodel: y=4,8,12 px and z centers ~8.5,10,12.5 px
        final float[] yRows = new float[]{ 12f/16f - 0.5f/16f,
                8f/16f - 0.5f/16f,
                4f/16f - 0.5f/16f}; // top, middle, bottom stairs
        final float[] zRows = new float[]{12.5f / 16f - 0.5f,
                10f / 16f - 0.5f - 2f/16f,
                8.5f / 16f - 0.5f - 4f/16f}; // top, middle, bottom



        // User request: increase item size
        final float itemScale = 0.60f;

        // Lift items by 1px so they don't clip into the stair surface.
        final float lift = 0.0015f; // tiny offset to avoid z-fighting // sit on the stair surface (tiny offset to avoid z-fighting)

        for (int slot = 0; slot < Math.min(9, be.getTemplate().getSlots()); slot++) {
            ItemStack stack = be.getTemplate().getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            int row = slot / 3;
            int col = slot % 3;
row = Math.min(2, Math.max(0, row));
col = Math.min(2, Math.max(0, col));

            pose.pushPose();

            float x = xCols[col];
            float y = yRows[row] + lift;
            float z = zRows[row];

            pose.translate(x, y, z);

            pose.scale(itemScale, itemScale, itemScale);

            mc.getItemRenderer().renderStatic(
                    stack,
                    ItemDisplayContext.GROUND,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    pose,
                    buffer,
                    be.getLevel(),
                    0
            );
        pose.popPose();
        }

        pose.popPose();
    }
}

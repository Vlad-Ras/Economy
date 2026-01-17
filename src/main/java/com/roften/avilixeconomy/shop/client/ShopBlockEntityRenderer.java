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
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders the shop lot (template items) on top of the shop block.
 *
 * Requirements from user:
 * - show items only (no numbers)
 * - show up to 9 items (3x3), laid flat (horizontal) on the block top
 */
public class ShopBlockEntityRenderer implements BlockEntityRenderer<ShopBlockEntity> {

    private final Minecraft mc;
    private final Map<Item, FitParams> fitCache = new ConcurrentHashMap<>();

    /**
     * Per-item fitted scale + extra lift so thin/handheld models (pickaxes, swords, etc.)
     * don't sink into the stair geometry when rendered with GROUND transforms.
     */
    private record FitParams(float scale, float extraLift) {}

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



        // Base scales.
        // User request: keep normal items and block-items at the previous (bigger) size,
        // but *still* force huge/custom-rendered items (e.g. Create water wheel) to fit.
        final float baseScaleNormal = 0.60f;
        final float baseScaleBlock  = 0.60f;
        // Custom renderer items (often Create and similar) can render huge.
        // User request: baseline size = 40%.
        final float baseScaleCustom = 0.40f;

        // Tiny offset to avoid z-fighting with the stair surface.
        final float lift = 0.0015f;

        for (int slot = 0; slot < Math.min(9, be.getTemplate().getSlots()); slot++) {
            ItemStack stack = be.getTemplate().getStackInSlot(slot);
            if (stack.isEmpty()) continue;

            int row = Math.min(2, Math.max(0, slot / 3));
            int col = Math.min(2, Math.max(0, slot % 3));

            pose.pushPose();

            float x = xCols[col];
            FitParams fp = computeFitParams(stack, baseScaleNormal, baseScaleBlock, baseScaleCustom);

            // extraLift compensates for models that extend below their origin (common for tools).
            float y = yRows[row] + lift + fp.extraLift();
            float z = zRows[row];

            pose.translate(x, y, z);

            // Create wrench and similar tools can become nearly invisible when rendered fully flat.
            // We cancel the default GROUND "lay flat" rotation by pre-rotating 90 degrees,
            // which makes them stand upright and be visible.
            if (isCreateWrench(stack)) {
                pose.translate(0.0f, 0.010f, 0.0f);
                pose.mulPose(Axis.XP.rotationDegrees(90.0f));
            }

            pose.scale(fp.scale(), fp.scale(), fp.scale());

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

    private FitParams computeFitParams(ItemStack stack, float baseScaleNormal, float baseScaleBlock, float baseScaleCustom) {
        if (stack == null || stack.isEmpty()) return new FitParams(baseScaleNormal, 0.0f);

        Item item = stack.getItem();
        FitParams cached = fitCache.get(item);
        if (cached != null) return cached;

        final boolean isCreate = isCreateItem(stack);
        final boolean isHandheld = isHandheldTool(stack);
        final boolean isTorch = isTorchItem(stack);
        // Some items are "paper-thin" (books, rotten flesh, coils, etc.) and visually sink into
        // the stair texture even if their baked model minY is not negative. We detect them by
        // display name (RU/EN) so it also works for custom-rendered items that don't expose quads.
        final boolean needsThinLiftByName = needsThinLiftByDisplayName(stack);

        float baseScale = baseScaleNormal;
        // Block items are intentionally the same size as normal items per user request.
        try {
            if (stack.getItem() instanceof net.minecraft.world.item.BlockItem) {
                baseScale = baseScaleBlock;
            }
        } catch (Throwable ignored) {}

        float resultScale = baseScale;
        float extraLift = 0.0f;
        try {
            BakedModel model = mc.getItemRenderer().getModel(stack, null, null, 0);
            if (model != null) {
                // Items with custom renderers often ignore baked quads sizing; use dedicated baseline.
                boolean isCustom = false;
                try {
                    isCustom = model.isCustomRenderer();
                } catch (Throwable ignored) {
                }
                if (isCustom) {
                    baseScale = baseScaleCustom;
                    resultScale = baseScale;
                }

                // Create items: user wants them a bit bigger overall (but still fitting).
                // Do NOT boost handheld tools (they should be slightly smaller).
                if (isCreate && !isHandheld) {
                    baseScale = clamp(baseScale * 1.12f, 0.05f, 0.90f);
                }
                // 1) Some mods scale items up via the ItemTransforms (GROUND)
                float transformMax = 1.0f;
                try {
                    Object transforms = model.getTransforms();
                    if (transforms != null) {
                        Object transform = transforms.getClass()
                                .getMethod("getTransform", ItemDisplayContext.class)
                                .invoke(transforms, ItemDisplayContext.GROUND);
                        transformMax = extractMaxScale(transform);
                    }
                } catch (Throwable ignored) {
                }

                // 2) Other models are simply huge geometry (Create waterwheel, etc.)
                float extentMax = extractModelMaxExtent(model);

                // Some items (tools) have geometry that goes below their origin.
                // We lift them so they don't visually clip into the shelf steps.
                float minY = extractModelMinY(model);

                float denom = Math.max(1.0f, Math.max(transformMax, extentMax));
                float s = baseScale / denom;
                // never enlarge above baseScale, but allow shrinking down to 10%
                resultScale = clamp(s, baseScale * 0.10f, baseScale);

                // Handheld tools: slightly reduce size so they don't look too chunky on the stairs.
                if (isHandheld) {
                    resultScale = clamp(resultScale * 0.92f, baseScale * 0.10f, baseScale);
                }

                // Convert model-space minY to world-space lift (accounting for our external scale).
                if (minY < 0.0f) {
                    extraLift = clamp((-minY) * resultScale + 0.0025f, 0.0f, 0.12f);
                } else {
                    extraLift = 0.0025f; // subtle universal lift helps thin items
                }

                // Lift rules (in addition to minY compensation):
                // - handheld tools: +2px
                // - torches: +2px (same as tools)
                // - "paper-thin" items (books, rotten flesh, coils, etc.): +6px
                // NOTE: Create items (non-handheld) are NOT lifted by default, only by this thin-item rule.
                if (isHandheld || isTorch) {
                    extraLift = clamp(extraLift + (2.0f / 16.0f), 0.0f, 0.30f);
                }
                if (needsThinLiftByName) {
                    extraLift = clamp(extraLift + (6.0f / 16.0f), 0.0f, 0.45f);
                }

                // IMPORTANT: Do NOT apply the BlockItem lift to custom-rendered models (Create and similar),
                // because many of those are BlockItem + custom renderer at the same time.
                // Also: user requested to revert the last "BlockItem +4px" lift.
                // So we intentionally do nothing here for BlockItem.
            }
        } catch (Throwable ignored) {
        }

        FitParams fp = new FitParams(resultScale, extraLift);
        fitCache.put(item, fp);
        return fp;
    }

    private static boolean isHandheldTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        // Vanilla tool base classes (covers pickaxe/axe/shovel/hoe, swords, trident, fishing rod, shears).
        return item instanceof DiggerItem
                || item instanceof SwordItem
                || item instanceof TridentItem
                || item instanceof FishingRodItem
                || item instanceof ShearsItem
                || isCreateWrench(stack);
    }

    /**
     * Estimate the baked model size by scanning quad vertex positions.
     * For normal items this is ~1.0; for oversized item models it can be >1.
     */
    private static float extractModelMaxExtent(BakedModel model) {
        if (model == null) return 1.0f;
        RandomSource rand = RandomSource.create(42L);

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        boolean any = false;

        // null side + each direction
        for (Direction dir : new Direction[]{null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            try {
                var quads = model.getQuads(null, dir, rand);
                if (quads == null) continue;
                for (BakedQuad q : quads) {
                    int[] v = quadVertices(q);
                    if (v == null || v.length < 12) continue;
                    int stride = v.length / 4;
                    if (stride < 3) continue;
                    for (int i = 0; i < 4; i++) {
                        int base = i * stride;
                        float x = Float.intBitsToFloat(v[base]);
                        float y = Float.intBitsToFloat(v[base + 1]);
                        float z = Float.intBitsToFloat(v[base + 2]);
                        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) continue;
                        any = true;
                        if (x < minX) minX = x;
                        if (y < minY) minY = y;
                        if (z < minZ) minZ = z;
                        if (x > maxX) maxX = x;
                        if (y > maxY) maxY = y;
                        if (z > maxZ) maxZ = z;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        if (!any) return 1.0f;
        float extentRaw = Math.max(Math.max(maxX - minX, maxY - minY), maxZ - minZ);

        // Heuristic: Minecraft bakes some models in 0..1 space and some in 0..16.
        // If it's clearly in "pixels" space (>8), normalize to block-space by dividing by 16.
        float extent = extentRaw;
        if (extentRaw > 8.0f) extent = extentRaw / 16.0f;

        if (extent <= 0.0001f) return 1.0f;
        return Math.max(1.0f, extent);
    }

    /**
     * Returns the minimum Y coordinate found in baked quads (heuristic).
     * Used to lift items whose models extend below their origin (common for tools).
     */
    private static float extractModelMinY(BakedModel model) {
        if (model == null) return 0.0f;
        RandomSource rand = RandomSource.create(123L);

        float minY = Float.POSITIVE_INFINITY;
        boolean any = false;
        for (Direction dir : new Direction[]{null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            try {
                var quads = model.getQuads(null, dir, rand);
                if (quads == null) continue;
                for (BakedQuad q : quads) {
                    int[] v = quadVertices(q);
                    if (v == null || v.length < 12) continue;
                    int stride = v.length / 4;
                    if (stride < 3) continue;
                    for (int i = 0; i < 4; i++) {
                        int base = i * stride;
                        float y = Float.intBitsToFloat(v[base + 1]);
                        if (Float.isNaN(y)) continue;
                        any = true;
                        if (y < minY) minY = y;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (!any) return 0.0f;

        // Heuristic: normalize if baked in 0..16 space.
        if (Math.abs(minY) > 8.0f) minY = minY / 16.0f;
        return minY;
    }

    private static int[] quadVertices(BakedQuad quad) {
        if (quad == null) return null;
        try {
            return (int[]) quad.getClass().getMethod("getVertices").invoke(quad);
        } catch (Throwable ignored) {
        }
        try {
            return (int[]) quad.getClass().getMethod("vertices").invoke(quad);
        } catch (Throwable ignored) {
        }
        try {
            var f = quad.getClass().getDeclaredField("vertices");
            f.setAccessible(true);
            return (int[]) f.get(quad);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static float extractMaxScale(Object itemTransform) {
        if (itemTransform == null) return 1.0f;
        try {
            Object vec;
            try {
                vec = itemTransform.getClass().getMethod("scale").invoke(itemTransform);
            } catch (Throwable t) {
                var f = itemTransform.getClass().getDeclaredField("scale");
                f.setAccessible(true);
                vec = f.get(itemTransform);
            }
            if (vec == null) return 1.0f;

            float x = getVecComponent(vec, "x");
            float y = getVecComponent(vec, "y");
            float z = getVecComponent(vec, "z");
            return Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z));
        } catch (Throwable ignored) {
            return 1.0f;
        }
    }

    private static float getVecComponent(Object vec, String axis) {
        try {
            // org.joml.Vector3f: public float x/y/z
            try {
                var f = vec.getClass().getField(axis);
                return ((Number) f.get(vec)).floatValue();
            } catch (Throwable ignored) {
            }

            // some records: x()/y()/z()
            try {
                var m = vec.getClass().getMethod(axis);
                Object v = m.invoke(vec);
                if (v instanceof Number n) return n.floatValue();
            } catch (Throwable ignored) {
            }

            // getX()/getY()/getZ()
            try {
                String mname = "get" + axis.toUpperCase();
                var m = vec.getClass().getMethod(mname);
                Object v = m.invoke(vec);
                if (v instanceof Number n) return n.floatValue();
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        return 1.0f;
    }

    /**
     * Create's wrench becomes almost invisible when rendered perfectly flat (GROUND).
     * We render it "upright" by canceling the default lay-flat rotation.
     */
    private static boolean isCreateWrench(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key == null) return false;
            if (!"create".equals(key.getNamespace())) return false;
            String p = key.getPath();
            return p != null && p.contains("wrench");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isCreateItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            return key != null && "create".equals(key.getNamespace());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isTorchItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            // Treat torch-like BlockItems as "thin" and lift them like tools so they don't clip.
            if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem)) return false;
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key == null) return false;
            String p = key.getPath();
            return p != null && p.endsWith("torch");
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Detect "paper-thin" items that visually clip into the stair texture.
     *
     * We intentionally use the display name (RU/EN substrings), because some mod items are rendered
     * via custom renderers and don't expose useful baked quad bounds.
     */
    private static boolean needsThinLiftByDisplayName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            // Strong checks first (don't depend on language pack / custom name):
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key != null) {
                // Vanilla offenders (exact)
                if (key.equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "book"))) return true;
                if (key.equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "rotten_flesh"))) return true;

                // Create coils/spools often have "coil" in the id.
                String path = key.getPath();
                if (path != null && (path.contains("coil") || path.contains("spool") || path.contains("reel"))) return true;
            }

            String name = stack.getHoverName().getString();
            if (name == null || name.isBlank()) return false;
            String n = name.toLowerCase(java.util.Locale.ROOT);

            // Books
            if (n.contains("книга") || n.contains("book")) return true;
            // Rotten flesh
            if (n.contains("гнил") || n.contains("rotten") || n.contains("flesh")) return true;
            // Coils / spools / reels (катушка)
            if (n.contains("катуш") || n.contains("coil") || n.contains("spool") || n.contains("reel")) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}

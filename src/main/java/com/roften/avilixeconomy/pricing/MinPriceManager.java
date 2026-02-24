package com.roften.avilixeconomy.pricing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.roften.avilixeconomy.AvilixEconomy;
import com.roften.avilixeconomy.util.MoneyUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal price floor per item, loaded from config JSON.
 *
 * File: config/avilixeconomy/min_prices.json
 * Format (simple):
 * {
 *   "minecraft:diamond": 100.0,
 *   "modid:item": 12.5
 * }
 */
public final class MinPriceManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ConcurrentHashMap<ResourceLocation, Double> MIN = new ConcurrentHashMap<>();
    /**
     * Tag-based min price floor per item.
     * JSON key format: "#namespace:tag" (example: "#minecraft:planks")
     */
    private static final ConcurrentHashMap<ResourceLocation, Double> TAG_MIN = new ConcurrentHashMap<>();

    private static volatile String lastError = null;

    private MinPriceManager() {}

    public static Path getFilePath() {
        return FMLPaths.CONFIGDIR.get().resolve(AvilixEconomy.MODID).resolve("min_prices.json");
    }

    public static void reload() {
        lastError = null;
        MIN.clear();
        TAG_MIN.clear();

        Path file = getFilePath();
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                // Bootstrap default file from jar resources if present
                try (InputStream in = MinPriceManager.class.getClassLoader().getResourceAsStream("config/" + AvilixEconomy.MODID + "/min_prices.json")) {
                    if (in != null) {
                        Files.copy(in, file);
                    } else {
                        Files.writeString(file, "{}\n", StandardCharsets.UTF_8);
                    }
                }
            }

            String raw = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(raw.isBlank() ? "{}" : raw);
            if (!root.isJsonObject()) {
                lastError = "Root is not an object";
                return;
            }

            JsonObject obj = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                String key = e.getKey();
                if (key == null || key.isBlank()) continue;
                if (!e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber()) continue;

                double v = MoneyUtils.round2(e.getValue().getAsDouble());
                if (v < 0) v = 0;

                boolean isTag = key.startsWith("#");
                String rawId = isTag ? key.substring(1) : key;

                ResourceLocation id;
                try {
                    id = ResourceLocation.tryParse(rawId);
                } catch (Throwable t) {
                    id = null;
                }
                if (id == null) continue;

                if (isTag) {
                    TAG_MIN.put(id, v);
                } else {
                    // UX/backward compatibility: many admins naturally write tag ids without '#'
                    // (e.g. "create:seats") assuming it will behave like a tag.
                    // If the id is NOT a registered item, treat it as a tag rule.
                    if (!BuiltInRegistries.ITEM.containsKey(id)) {
                        TAG_MIN.put(id, v);
                    } else {
                        MIN.put(id, v);
                    }
                }
            }

        } catch (Exception ex) {
            lastError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            AvilixEconomy.LOGGER.warn("Failed to load min_prices.json", ex);
        }
    }

    public static String getLastError() {
        return lastError;
    }

    public static Map<ResourceLocation, Double> snapshot() {
        return Collections.unmodifiableMap(MIN);
    }

    public static Map<ResourceLocation, Double> snapshotTags() {
        return Collections.unmodifiableMap(TAG_MIN);
    }

    public static double getMinPerItem(ResourceLocation itemId) {
        if (itemId == null) return 0.0;
        Double v = MIN.get(itemId);
        return v == null ? 0.0 : v;
    }

    /**
     * Returns per-item min price for a stack based on:
     * 1) explicit item entry (minecraft:diamond)
     * 2) tag entry (#minecraft:planks) if no explicit item entry exists
     * If multiple tag rules match, the maximum value is used.
     */
    public static double getMinPerItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0;
        ResourceLocation itemId = stack.getItem().builtInRegistryHolder().key().location();

        Double direct = MIN.get(itemId);
        if (direct != null) return direct;

        if (TAG_MIN.isEmpty()) return 0.0;

        double best = 0.0;
        var holder = stack.getItem().builtInRegistryHolder();
        // Some mods (Create included) define certain groupings as *block* tags, not item tags.
        // Example: #create:seats is commonly a BLOCK tag.
        //
        // To make min_prices.json intuitive, we treat "#namespace:tag" as "match ITEM tag OR BLOCK tag".
        var asBlock = (stack.getItem() instanceof BlockItem bi) ? bi.getBlock().builtInRegistryHolder() : null;
        for (Map.Entry<ResourceLocation, Double> e : TAG_MIN.entrySet()) {
            if (e.getValue() == null) continue;
            ResourceLocation tagId = e.getKey();

            TagKey<net.minecraft.world.item.Item> itemTag = TagKey.create(Registries.ITEM, tagId);
            boolean match = holder.is(itemTag);

            if (!match && asBlock != null) {
                TagKey<net.minecraft.world.level.block.Block> blockTag = TagKey.create(Registries.BLOCK, tagId);
                match = asBlock.is(blockTag);
            }

            if (match) {
                double v = e.getValue();
                if (v > best) best = v;
            }
        }
        return best;
    }

    public static double getMinForStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0;
        double per = getMinPerItem(stack);
        if (per <= 0) return 0.0;
        // IMPORTANT: min price is configured per 1 item, so the floor for a stack is perItem * count.
        int count = Math.max(1, stack.getCount());
        return MoneyUtils.round2(per * count);
    }

    /**
     * For a shop lot: sum min prices for each template stack (with its count).
     */
    public static double getMinForTemplate(ItemStackHandler template) {
        if (template == null) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < template.getSlots(); i++) {
            ItemStack s = template.getStackInSlot(i);
            if (s == null || s.isEmpty()) continue;
            sum += getMinForStack(s);
        }
        return MoneyUtils.round2(sum);
    }

    /**
     * For "price per slot" mode: the per-slot price cannot be lower than the most expensive slot.
     */
    public static double getMinPerSlotForTemplate(ItemStackHandler template) {
        if (template == null) return 0.0;
        double max = 0.0;
        for (int i = 0; i < template.getSlots(); i++) {
            ItemStack s = template.getStackInSlot(i);
            if (s == null || s.isEmpty()) continue;
            double v = getMinForStack(s);
            if (v > max) max = v;
        }
        return MoneyUtils.round2(max);
    }

    public static void setMin(ResourceLocation itemId, double value) throws IOException {
        if (itemId == null) return;
        double v = MoneyUtils.round2(Math.max(0.0, value));
        MIN.put(itemId, v);
        save();
    }

    public static void setMinTag(ResourceLocation tagId, double value) throws IOException {
        if (tagId == null) return;
        double v = MoneyUtils.round2(Math.max(0.0, value));
        TAG_MIN.put(tagId, v);
        save();
    }

    public static void removeMin(ResourceLocation itemId) throws IOException {
        if (itemId == null) return;
        MIN.remove(itemId);
        save();
    }

    public static void removeMinTag(ResourceLocation tagId) throws IOException {
        if (tagId == null) return;
        TAG_MIN.remove(tagId);
        save();
    }

    public static void save() throws IOException {
        Path file = getFilePath();
        Files.createDirectories(file.getParent());

        JsonObject obj = new JsonObject();

        // Items first
        MIN.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((a, b) -> a.toString().compareToIgnoreCase(b.toString())))
                .forEach(e -> obj.addProperty(e.getKey().toString(), MoneyUtils.round2(e.getValue())));

        // Tags with leading '#'
        TAG_MIN.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((a, b) -> a.toString().compareToIgnoreCase(b.toString())))
                .forEach(e -> obj.addProperty("#" + e.getKey().toString(), MoneyUtils.round2(e.getValue())));

        Files.writeString(file, GSON.toJson(obj) + "\n", StandardCharsets.UTF_8);
    }
}

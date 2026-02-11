package com.roften.avilixeconomy.pricing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.roften.avilixeconomy.AvilixEconomy;
import com.roften.avilixeconomy.util.MoneyUtils;
import net.minecraft.resources.ResourceLocation;
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

    private static volatile String lastError = null;

    private MinPriceManager() {}

    public static Path getFilePath() {
        return FMLPaths.CONFIGDIR.get().resolve(AvilixEconomy.MODID).resolve("min_prices.json");
    }

    public static void reload() {
        lastError = null;
        MIN.clear();

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

                ResourceLocation id;
                try {
                    id = ResourceLocation.tryParse(key);
                } catch (Throwable t) {
                    id = null;
                }
                if (id == null) continue;

                MIN.put(id, v);
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

    public static double getMinPerItem(ResourceLocation itemId) {
        if (itemId == null) return 0.0;
        Double v = MIN.get(itemId);
        return v == null ? 0.0 : v;
    }

    public static double getMinForStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0;
        ResourceLocation id = stack.getItem().builtInRegistryHolder().key().location();
        double per = getMinPerItem(id);
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

    public static void removeMin(ResourceLocation itemId) throws IOException {
        if (itemId == null) return;
        MIN.remove(itemId);
        save();
    }

    public static void save() throws IOException {
        Path file = getFilePath();
        Files.createDirectories(file.getParent());

        JsonObject obj = new JsonObject();
        MIN.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((a, b) -> a.toString().compareToIgnoreCase(b.toString())))
                .forEach(e -> obj.addProperty(e.getKey().toString(), MoneyUtils.round2(e.getValue())));

        Files.writeString(file, GSON.toJson(obj) + "\n", StandardCharsets.UTF_8);
    }
}

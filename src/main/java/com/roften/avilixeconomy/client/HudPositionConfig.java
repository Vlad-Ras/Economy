package com.roften.avilixeconomy.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.roften.avilixeconomy.AvilixEconomy;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Простой клиентский конфиг позиции HUD (x/y в GUI-пикселях).
 * Хранится отдельно в config/avilixeconomy_hud.json — чтобы можно было
 * менять прямо из игры и гарантированно сохранять.
 */
public final class HudPositionConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("avilixeconomy_hud.json");

    private static int x = 10;
    private static int y = 10;
    private static boolean loaded = false;

    private HudPositionConfig() {}

    public static int getX() {
        ensureLoaded();
        return x;
    }

    public static int getY() {
        ensureLoaded();
        return y;
    }

    public static void set(int newX, int newY) {
        x = newX;
        y = newY;
    }

    public static void resetDefaults() {
        x = 10;
        y = 10;
    }

    public static void load() {
        loaded = true;
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Data d = GSON.fromJson(r, Data.class);
            if (d != null) {
                x = d.x;
                y = d.y;
            }
        } catch (JsonSyntaxException | IOException e) {
            AvilixEconomy.LOGGER.warn("Failed to read HUD config {}", FILE, e);
        }
    }

    public static void save() {
        ensureLoaded();
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(new Data(x, y), w);
            }
        } catch (IOException e) {
            AvilixEconomy.LOGGER.warn("Failed to write HUD config {}", FILE, e);
        }
    }

    private static void ensureLoaded() {
        if (!loaded) load();
    }

    private record Data(int x, int y) {}
}

package com.roften.avilixeconomy.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Горячая клавиша и обработка открытия экрана перетаскивания HUD.
 */
public final class HudMoveController {

    public static final String KEY_CATEGORY = "key.categories.avilixeconomy";
    public static final KeyMapping MOVE_HUD_KEY = new KeyMapping(
            "key.avilixeconomy.move_balance_hud",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_B,
            KEY_CATEGORY
    );

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(MOVE_HUD_KEY);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        if (MOVE_HUD_KEY.consumeClick()) {
            mc.setScreen(new MoveBalanceHudScreen());
        }
    }
}

package com.roften.avilixeconomy.client;

import com.roften.avilixeconomy.util.MoneyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * HUD — простой, рисует баланс из ClientBalanceData.
 * Регистрация: NeoForge.EVENT_BUS.register(new EconomyHud()); (на клиенте)
 */
public class EconomyHud {

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double bal = ClientBalanceData.getBalance();

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;

        String text = "Баланс: " + MoneyUtils.formatSmart(bal);

        int x = 10;
        int y = 10;

        g.drawString(font, text, x, y, 0xFFFFFF, true);
    }
}

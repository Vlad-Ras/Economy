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

        int x = HudPositionConfig.getX();
        int y = HudPositionConfig.getY();

        // safety clamp (чтобы не уехать за экран)
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int w = font.width(text);
        int h = font.lineHeight;
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x > sw - w) x = Math.max(0, sw - w);
        if (y > sh - h) y = Math.max(0, sh - h);

        g.drawString(font, text, x, y, 0xFFFFFF, true);
    }
}

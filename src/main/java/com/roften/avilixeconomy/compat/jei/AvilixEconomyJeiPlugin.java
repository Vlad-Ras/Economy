package com.roften.avilixeconomy.compat.jei;

import com.roften.avilixeconomy.shop.screen.ShopBuyScreen;
import com.roften.avilixeconomy.shop.screen.ShopConfigScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Optional JEI compatibility.
 *
 * Goal: if our shop screens overlap JEI overlay, JEI should move (not our UI).
 * We do this by telling JEI the real screen-space bounds of the scaled UI.
 */
@JeiPlugin
public class AvilixEconomyJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("avilixeconomy", "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(ShopBuyScreen.class, new IGuiContainerHandler<ShopBuyScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(ShopBuyScreen screen) {
                return List.of(new Rect2i(
                        screen.getUiLeftScreen(),
                        screen.getUiTopScreen(),
                        screen.getScaledUiWidth(),
                        screen.getScaledUiHeight()
                ));
            }
        });

        registration.addGuiContainerHandler(ShopConfigScreen.class, new IGuiContainerHandler<ShopConfigScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(ShopConfigScreen screen) {
                return List.of(new Rect2i(
                        screen.getUiLeftScreen(),
                        screen.getUiTopScreen(),
                        screen.getScaledUiWidth(),
                        screen.getScaledUiHeight()
                ));
            }
        });
    }
}

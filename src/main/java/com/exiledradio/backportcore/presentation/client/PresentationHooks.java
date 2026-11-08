package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Method;

/**
 * What Core's presentation patches call (docs/PATCHES.md, P6 and P7). Each hook calls vanilla's own method
 * whenever nothing installed changes that piece, so with no backport installed the game is 1.12.2's.
 */
@SideOnly(Side.CLIENT)
public final class PresentationHooks {

    private static final int GRADIENT_TOP = 0x80FFFFFF;

    private static Method renderSkybox;
    private static Method drawGradientRect;

    private PresentationHooks() {
    }

    /** P6: {@code GuiMainMenu.drawScreen}'s {@code renderSkybox(mouseX, mouseY, partialTicks)}. */
    public static void renderSkybox(GuiMainMenu menu, int mouseX, int mouseY, float partialTicks) {
        if (PanoramaRenderer.modern()) {
            PanoramaRenderer.render(menu);
            return;
        }
        try {
            if (renderSkybox == null) {
                renderSkybox = ReflectionHelper.findMethod(GuiMainMenu.class, "renderSkybox", "func_73971_c", int.class, int.class, float.class);
            }
            renderSkybox.invoke(menu, mouseX, mouseY, partialTicks);
        } catch (Exception e) {
            BackportCore.LOGGER.error("Title screen panorama: vanilla's renderSkybox failed", e);
        }
    }

    /**
     * P6: {@code GuiMainMenu.drawScreen}'s two {@code drawGradientRect} calls (a white fade from the top, a
     * dark fade to the bottom). Once a version with an overlay is installed, the first becomes 1.13's overlay
     * and the second is left out; otherwise both are 1.12.2's.
     */
    public static void drawGradient(GuiMainMenu menu, int left, int top, int right, int bottom, int startColor, int endColor) {
        ResourceLocation overlay = PanoramaRenderer.modern() ? Presentation.panoramaOverlay() : null;
        if (overlay != null) {
            if (startColor == GRADIENT_TOP) {
                PanoramaRenderer.drawOverlay(Minecraft.getMinecraft(), overlay, menu.width, menu.height);
            }
            return;
        }
        try {
            if (drawGradientRect == null) {
                drawGradientRect = ReflectionHelper.findMethod(Gui.class, "drawGradientRect", "func_73733_a",
                        int.class, int.class, int.class, int.class, int.class, int.class);
            }
            drawGradientRect.invoke(menu, left, top, right, bottom, startColor, endColor);
        } catch (Exception e) {
            BackportCore.LOGGER.error("Title screen: vanilla's drawGradientRect failed", e);
        }
    }
}

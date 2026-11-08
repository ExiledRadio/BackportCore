package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.DebugKeys;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * What Core's presentation patches call (docs/PATCHES.md, P6 and P7). Each hook calls vanilla's own method
 * whenever nothing installed changes that piece, so with no backport installed the game is 1.12.2's.
 */
@SideOnly(Side.CLIENT)
public final class PresentationHooks {

    private static final int GRADIENT_TOP = 0x80FFFFFF;

    private static Method renderSkybox;
    private static Method drawGradientRect;
    private static Method processKeyF3;
    private static boolean loggedClassic;

    private PresentationHooks() {
    }

    /** P6: {@code GuiMainMenu.drawScreen}'s {@code renderSkybox(mouseX, mouseY, partialTicks)}. */
    public static void renderSkybox(GuiMainMenu menu, int mouseX, int mouseY, float partialTicks) {
        if (PanoramaRenderer.modern()) {
            PanoramaRenderer.render(menu);
            return;
        }
        if (!loggedClassic) {
            loggedClassic = true;
            BackportCore.LOGGER.info("Presentation: no backport installed; the title screen is 1.12.2's");
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

    /**
     * P7: {@code Minecraft.runTickKeyboard}'s {@code processKeyF3(key)}, called while F3 is held with no screen
     * open. Until an installed version changes the key map, vanilla's own method. After that: a key the map
     * gives an action runs it; a 1.12.2 key the map keeps runs vanilla's; a key the map removed does nothing;
     * F3+Q lists the map's help lines.
     *
     * @return true when the key was handled (vanilla then does not also press that key's binding)
     */
    public static boolean processKeyF3(Minecraft mc, int key) {
        List<Contribution> installed = Presentation.installed();
        if (!DebugKeys.changed(installed)) {
            return vanillaF3(mc, key);
        }
        Map<Integer, DebugKeys.Key> map = DebugKeys.map(installed);
        DebugKeys.Key k = map.get(key);
        if (k == null) {
            return false;
        }
        if (k.action != null) {
            return k.action.run();
        }
        if (key == DebugKeys.KEY_Q) {
            debugFeedback("debug.help.message");
            for (DebugKeys.Key line : map.values()) {
                mc.ingameGUI.getChatGUI().printChatMessage(new TextComponentTranslation(line.helpKey));
            }
            return true;
        }
        return vanillaF3(mc, key);
    }

    private static boolean vanillaF3(Minecraft mc, int key) {
        try {
            if (processKeyF3 == null) {
                processKeyF3 = ReflectionHelper.findMethod(Minecraft.class, "processKeyF3", "func_184122_c", int.class);
            }
            return (Boolean) processKeyF3.invoke(mc, key);
        } catch (Exception e) {
            BackportCore.LOGGER.error("F3 keys: vanilla's processKeyF3 failed", e);
            return false;
        }
    }

    // ---- P12: button labels ----

    /** P12: a button label color 1.12.2's {@code GuiButton.drawButton} uses (normal or hovered). */
    public static int buttonTextColor(int vanilla) {
        return com.exiledradio.backportcore.presentation.VanillaRevisions.buttonTextColor(vanilla);
    }

    // ---- P13, P14: the chat ----

    /** P13: the chat's {@code drawChat(updateCounter)}: Core's from 1.14 on (its text options), vanilla's before. */
    public static void drawChat(net.minecraft.client.gui.GuiNewChat chat, int updateCounter) {
        if (ChatRenderer.active()) {
            ChatRenderer.draw(chat, updateCounter);
        } else {
            chat.drawChat(updateCounter);
        }
    }

    /** P14: the chat component under the mouse, with the line spacing Core's chat draws. */
    @javax.annotation.Nullable
    public static net.minecraft.util.text.ITextComponent chatComponent(net.minecraft.client.gui.GuiNewChat chat, int mouseX, int mouseY) {
        if (ChatRenderer.active()) {
            return ChatRenderer.componentAt(chat, mouseX, mouseY);
        }
        return chat.getChatComponent(mouseX, mouseY);
    }

    /** P14: the chat input box's color ({@code Integer.MIN_VALUE}, half black); 1.14's Text Background: Everywhere. */
    public static int chatBoxColor(int vanilla) {
        return ChatRenderer.boxColor(vanilla);
    }

    // ---- P15, P16: sky and clouds ----

    /** P15/P16: {@code world.getSkyColor(entity, partialTicks)}, then the fog pipeline's sky steps. */
    public static net.minecraft.util.math.Vec3d skyColor(net.minecraft.world.World world, net.minecraft.entity.Entity entity, float partialTicks) {
        net.minecraft.util.math.Vec3d vanilla = world.getSkyColor(entity, partialTicks);
        return FogPipeline.skyColor(world, entity, partialTicks, vanilla);
    }

    /** P15: {@code provider.getCloudHeight()}, then the fog pipeline's cloud steps. */
    public static float cloudHeight(net.minecraft.world.WorldProvider provider) {
        return FogPipeline.cloudHeight(provider, provider.getCloudHeight());
    }

    /**
     * P16: {@code renderWorldPass}'s 128.0 (whether the camera is below the clouds, which decides whether they draw
     * before or after the terrain): vanilla's constant until a step moves the clouds, then the height they draw at.
     */
    public static double cloudLayer(double vanilla) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.world == null || !FogPipeline.movesClouds()) {
            return vanilla;
        }
        return cloudHeight(mc.world.provider);
    }

    /**
     * Prints a line in chat as vanilla's F3 keys do: a bold yellow "[Debug]:" and the translated message.
     * For a backport's {@link DebugKeys.Action}.
     */
    public static void debugFeedback(String key, Object... args) {
        Minecraft.getMinecraft().ingameGUI.getChatGUI().printChatMessage(new TextComponentString("")
                .appendSibling(new TextComponentTranslation("debug.prefix").setStyle(new Style().setColor(TextFormatting.YELLOW).setBold(true)))
                .appendText(" ").appendSibling(new TextComponentTranslation(key, args)));
    }
}

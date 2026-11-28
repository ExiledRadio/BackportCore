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
    /** The screen the last F3 combination opened (1.16's game mode switcher), and that combination's key. */
    private static Object openedScreen;
    private static int openedKey = -1;
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
        // 1.21.11's Debug key binds: a rebound combination's key stands for its default.
        key = DebugBindings.translate(key);
        if (key < 0) {
            return false;
        }
        List<Contribution> installed = Presentation.installed(com.exiledradio.backportcore.presentation.LookArea.DEBUG);
        if (!DebugKeys.changed(installed)) {
            return vanillaF3(mc, key);
        }
        Map<Integer, DebugKeys.Key> map = DebugKeys.map(installed);
        DebugKeys.Key k = map.get(key);
        if (k == null) {
            return false;
        }
        if (k.action != null) {
            Object before = mc.currentScreen;
            boolean handled = k.action.run();
            if (mc.currentScreen != null && mc.currentScreen != before) {
                openedScreen = mc.currentScreen;
                openedKey = key;
            }
            return handled;
        }
        if (key == DebugKeys.KEY_Q) {
            debugFeedback("debug.help.message");
            for (DebugKeys.Key line : map.values()) {
                if (line.helpKey != null) { // a key with no help line (1.20.2's F3 + 1, 2 and 3)
                    mc.ingameGUI.getChatGUI().printChatMessage(new TextComponentTranslation(line.helpKey));
                }
            }
            return true;
        }
        return vanillaF3(mc, key);
    }

    /**
     * @return true when {@code screen} was opened by the F3 combination on {@code key} (after translation) and is
     *         still open: that key belongs to the screen then (F4 cycles 1.16's game mode switcher), so the in-menu
     *         combinations leave it alone instead of opening the screen again
     */
    static boolean ownedByOpenedScreen(Object screen, int key) {
        return screen != null && screen == openedScreen && key == openedKey;
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

    // ---- P26 to P30 (since Core 1.3.0) ----

    /** P26: at the head of {@code RenderGlobal.renderClouds}. */
    public static void cloudsBegin() {
        RenderOptions.cloudsBegin();
    }

    /**
     * P66: in {@code RecipeToast.draw}, right before the recipe's output is drawn: from 1.13 the crafting table small in
     * the corner behind it, as 1.13's toast draws a crafting recipe's icon (the smelting books' toast draws each book's
     * block). At (3, 3) in the scaled space, as 1.13.2 and 1.14.4 draw it.
     */
    public static void recipeToastIcon(net.minecraft.client.gui.toasts.GuiToast toasts) {
        if (!com.exiledradio.backportcore.presentation.VanillaRevisions.recipeToastIcon()) {
            return;
        }
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.scale(0.6F, 0.6F, 1.0F);
        toasts.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(null, CRAFTING_TABLE, 3, 3);
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        recipeToastIcons++;
    }

    /** Self-tests: how many times {@link #recipeToastIcon} has drawn the crafting table. */
    public static int recipeToastIcons;

    private static final net.minecraft.item.ItemStack CRAFTING_TABLE = new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.CRAFTING_TABLE);

    /** P26: right after {@link #cloudsBegin()}: true when 1.21.2's clouds were drawn instead of 1.12.2's. */
    public static boolean modernClouds(float partialTicks, double x, double y, double z) {
        return ModernClouds.render(partialTicks, x, y, z);
    }

    /** P65: at the head of {@code EntityRenderer.updateLightmap}: true when 26.1's lightmap was filled instead. */
    public static boolean modernLightmap(net.minecraft.client.renderer.EntityRenderer renderer, float partialTicks) {
        return ModernLightmap.update(renderer, partialTicks);
    }

    /** P26: the fast clouds' -256 and 256. */
    public static int cloudFast(int vanilla) {
        return RenderOptions.cloudFast(vanilla);
    }

    /** P26: the fancy clouds' cell bounds, -3 and 4. */
    public static int cloudFancy(int vanilla) {
        return RenderOptions.cloudFancy(vanilla);
    }

    /** P27: {@code renderRainSnow}'s 5 and 10. */
    public static int weatherRadius(int vanilla) {
        return RenderOptions.weatherRadius(vanilla);
    }

    /** P28: each chunk as {@code ChunkRenderContainer.preRenderChunk} draws it. */
    public static void chunkFadePre(net.minecraft.client.renderer.chunk.RenderChunk chunk) {
        RenderOptions.chunkPre(chunk);
    }

    /** P28: after each layer's chunks. */
    public static void chunkFadeEnd() {
        RenderOptions.chunkLayerEnd();
    }

    /** P29: {@code GuiMainMenu.drawScreen}'s logo pieces. */
    public static void titleLogoRect(Gui gui, int x, int y, int u, int v, int w, int h) {
        TitleLogo.rect(gui, x, y, u, v, w, h);
    }

    /** P29: {@code GuiMainMenu.drawScreen}'s "Java Edition". */
    public static void titleEdition(int x, int y, float u, float v, int w, int h, float tw, float th) {
        TitleLogo.edition(x, y, u, v, w, h, tw, th);
    }

    /** P29: the splash's offset from the middle. */
    public static int splashX(int vanilla) {
        return TitleLogo.splashX(vanilla);
    }

    /** P29: the splash's height. */
    public static float splashY(float vanilla) {
        return TitleLogo.splashY(vanilla);
    }

    /** P30: F3 as the modifier. */
    public static int debugModifierKey(int vanilla) {
        return DebugBindings.modifierKey(vanilla);
    }

    /** P30: F3 as the debug screen's toggle. */
    public static int debugOverlayKey(int vanilla) {
        return DebugBindings.overlayKey(vanilla);
    }

    /** P30: C in F3 + C's crash. */
    public static int debugCrashKey(int vanilla) {
        return DebugBindings.crashKey(vanilla);
    }

    // ---- P24, P25: the sky ----

    /**
     * P24: what {@code WorldProvider.calcSunriseSunsetColors} returned (or what another mod's redirect of that call
     * returned), through the fog pipeline's sunrise steps.
     */
    @javax.annotation.Nullable
    public static float[] sunriseColors(@javax.annotation.Nullable float[] vanilla, float partialTicks) {
        net.minecraft.world.World world = net.minecraft.client.Minecraft.getMinecraft().world;
        return world == null ? vanilla : FogPipeline.sunriseColors(world.provider, world.getCelestialAngle(partialTicks), partialTicks, vanilla);
    }

    /** P25: {@code RenderGlobal.cloudTickCounter} as the clouds read it. */
    public static int cloudTicks(int vanilla) {
        return FogPipeline.cloudTicks(vanilla);
    }

    // ---- P22: menus without a world ----

    /** P22: the frame rate of a menu without a world: 1.21.2's 60 once a backport of 1.21.2 or later is installed. */
    public static int menuFramerate(int vanilla) {
        return Presentation.isAtLeast(com.exiledradio.backportcore.presentation.LookArea.MENUS, "1.21.2") ? 60 : vanilla;
    }

    // ---- P23: the slot highlight ----

    /** For the client self-test: highlights drawn with 1.21.2's sprites. */
    public static volatile int spriteHighlights;

    /**
     * P23: {@code GuiContainer.drawScreen}'s hovered-slot square. From 1.21.2, when a backport supplies its sprites
     * ({@code textures/gui/sprites/container/slot_highlight_back.png} and {@code _front.png}, 24 by 24, drawn 4 pixels
     * out from the slot): the back one behind the item already drawn (depth-tested under it), the front one over it.
     * Otherwise 1.12.2's white square.
     */
    public static void slotHighlight(net.minecraft.client.gui.inventory.GuiContainer gui, int left, int top, int right, int bottom,
                                     int startColor, int endColor) {
        String back = "textures/gui/sprites/container/slot_highlight_back.png";
        String front = "textures/gui/sprites/container/slot_highlight_front.png";
        if (!Presentation.isAtLeast(com.exiledradio.backportcore.presentation.LookArea.INVENTORY, "1.21.2") || Presentation.asset(back) == null || Presentation.asset(front) == null) {
            net.minecraftforge.fml.client.config.GuiUtils.drawGradientRect(0, left, top, right, bottom, startColor, endColor);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        // vanilla turned depth testing off for its square; the back sprite is drawn at z 0, under the item's depth.
        net.minecraft.client.renderer.GlStateManager.enableDepth();
        net.minecraft.client.renderer.GlStateManager.depthMask(false);
        mc.getTextureManager().bindTexture(new ResourceLocation(back));
        Gui.drawModalRectWithCustomSizedTexture(left - 4, top - 4, 0.0F, 0.0F, 24, 24, 24.0F, 24.0F);
        net.minecraft.client.renderer.GlStateManager.depthMask(true);
        net.minecraft.client.renderer.GlStateManager.disableDepth();
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(0.0F, 0.0F, 300.0F);
        mc.getTextureManager().bindTexture(new ResourceLocation(front));
        Gui.drawModalRectWithCustomSizedTexture(left - 4, top - 4, 0.0F, 0.0F, 24, 24, 24.0F, 24.0F);
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        spriteHighlights++;
    }

    // ---- P12: button labels ----

    /** P12: a button label color 1.12.2's {@code GuiButton.drawButton} uses (normal or hovered). */
    public static int buttonTextColor(int vanilla) {
        return com.exiledradio.backportcore.presentation.VanillaRevisions.buttonTextColor(vanilla);
    }

    // ---- P61, P62: text fields and sliders ----

    /** P61: a text field's frame color; 1.16's white while it is focused. */
    public static int textFieldFrame(net.minecraft.client.gui.GuiTextField field, int vanilla) {
        return field.isFocused() && com.exiledradio.backportcore.presentation.VanillaRevisions.textFieldFocusFrame() ? 0xFFFFFFFF : vanilla;
    }

    /** P62: the texture row of a slider's handle in {@code widgets.png}; 1.14's hovered button row (86) under the mouse. */
    public static int sliderHandleRow(net.minecraft.client.gui.GuiButton slider, int vanilla) {
        return slider.isMouseOver() && com.exiledradio.backportcore.presentation.VanillaRevisions.sliderHandleHover() ? 86 : vanilla;
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
    public static net.minecraft.util.math.Vec3d skyColor(net.minecraft.util.math.Vec3d vanilla, float partialTicks) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        net.minecraft.entity.Entity entity = mc.getRenderViewEntity();
        return mc.world == null || entity == null ? vanilla : FogPipeline.skyColor(mc.world, entity, partialTicks, vanilla);
    }

    /** P15: {@code provider.getCloudHeight()}, then the fog pipeline's cloud steps. */
    public static float cloudHeight(float vanilla) {
        net.minecraft.world.World world = net.minecraft.client.Minecraft.getMinecraft().world;
        return world == null ? vanilla : FogPipeline.cloudHeight(world.provider, vanilla);
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
        return cloudHeight(mc.world.provider.getCloudHeight());
    }

    // ---- P31: 1.20.5's menu backgrounds (MenuBackground) ----

    /** P31: {@code GuiScreen.drawBackground}'s {@code tessellator.draw()} (the dirt). */
    public static void menuDirt(net.minecraft.client.renderer.Tessellator tessellator) {
        MenuBackground.dirt(tessellator);
    }

    /** P31: {@code GuiScreen.drawWorldBackground}'s {@code drawGradientRect} (the gradient over the world). */
    public static void menuWorldGradient(net.minecraft.client.gui.GuiScreen screen, int left, int top, int right, int bottom,
            int from, int to) {
        MenuBackground.worldGradient(screen, left, top, right, bottom, from, to);
    }

    /** P31: {@code GuiSlot.drawScreen}'s {@code drawContainerBackground(tessellator)}. */
    public static void listBackground(net.minecraft.client.gui.GuiSlot slot, net.minecraft.client.renderer.Tessellator tessellator) {
        MenuBackground.listBackground(slot, tessellator);
    }

    /** P31: {@code GuiSlot.drawScreen}'s two {@code overlayBackground} calls. */
    public static void listOverlay(net.minecraft.client.gui.GuiSlot slot, int startY, int endY, int startAlpha, int endAlpha) {
        MenuBackground.listOverlay(slot, startY, endY, startAlpha, endAlpha);
    }

    /** P31: {@code GuiSlot.drawScreen}'s {@code getMaxScroll()}. */
    public static int listMaxScroll(net.minecraft.client.gui.GuiSlot slot) {
        return MenuBackground.listMaxScroll(slot);
    }

    // ---- P32: 1.20.2's Narrator Hotkey ----

    /** For the client self-test: Ctrl + B presses the hotkey turned away. */
    public static volatile int narratorHotkeyBlocked;

    /**
     * P32: {@code Minecraft.dispatchKeypresses}'s one {@code GuiScreen.isCtrlKeyDown()}, the narrator's Ctrl + B test:
     * false while Narrator Hotkey (1.20.2) is shown and off.
     */
    public static boolean narratorCtrl() {
        boolean ctrl = net.minecraft.client.gui.GuiScreen.isCtrlKeyDown();
        if (ctrl && !narratorHotkeyOn()) {
            narratorHotkeyBlocked++;
            return false;
        }
        return ctrl;
    }

    /** @return true when Ctrl + B toggles the narrator: always before 1.20.2, then Narrator Hotkey's value */
    public static boolean narratorHotkeyOn() {
        return !Presentation.NARRATOR_HOTKEY.isShown() || Presentation.NARRATOR_HOTKEY.get();
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

    /**
     * P45: {@code GuiModList.Info.drawHeader}, before the logo. The in-world list gradient left OpenGL's color near
     * black while {@code GlStateManager}'s cache says white, so white is set past the cache.
     */
    public static void modListHeader() {
        net.minecraft.client.renderer.GlStateManager.resetColor();
        net.minecraft.client.renderer.GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ---- Since Core 1.4.5: P57 to P60 ----

    /** P57: {@code InventoryEffectRenderer.drawScreen}'s {@code drawActivePotionEffects()}. */
    public static void inventoryEffects(net.minecraft.client.renderer.InventoryEffectRenderer gui) {
        InventoryEffects.draw(gui);
    }

    // ---- Since Core 1.5.0: P78 ----

    /** P78: {@code GuiInventory.drawGuiContainerBackgroundLayer}'s {@code drawEntityOnScreen}, the same arguments. */
    public static void inventoryPreview(int posX, int posY, int scale, float mouseX, float mouseY, net.minecraft.entity.EntityLivingBase entity) {
        InventoryPreview.survival(posX, posY, scale, mouseX, mouseY, entity);
    }

    /** P78: {@code GuiContainerCreative.drawGuiContainerBackgroundLayer}'s {@code drawEntityOnScreen}, the same arguments. */
    public static void creativePreview(int posX, int posY, int scale, float mouseX, float mouseY, net.minecraft.entity.EntityLivingBase entity) {
        InventoryPreview.creative(posX, posY, scale, mouseX, mouseY, entity);
    }

    /** P58: {@code GuiRecipeBook.render}'s {@code searchBar.drawTextBox()}, with 1.16's hint and 1.20.5's position. */
    public static void recipeSearchBox(net.minecraft.client.gui.GuiTextField box) {
        RecipeBookLook.drawSearchBox(box);
    }

    /** For the client self-test: subtitle boxes drawn through P59. */
    public static volatile int subtitleBoxes;

    /** P59: {@code GuiSubtitleOverlay}'s box color ({@code 0xCC000000}); 1.14's Text Background: Everywhere. */
    public static int subtitleBoxColor(int vanilla) {
        subtitleBoxes++;
        return ChatRenderer.boxColor(vanilla);
    }

    /** P60: {@code Minecraft.runGameLoop}'s {@code displayDebugInfo(elapsed)}: 1.20.2's profiler pie, or 1.12.2's. */
    public static void profilerPie(Minecraft mc, long elapsed) {
        FrameCharts.profilerPie(mc, elapsed);
    }
}

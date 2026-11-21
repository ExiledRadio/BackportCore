package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.option.PresentationOption;
import com.exiledradio.backportcore.presentation.option.SliderOption;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.20.5's menu backgrounds (24w09a, {@code Screen.renderBackground} and {@code AbstractSelectionList.renderWidget}), on
 * every screen once {@link VanillaRevisions#menuBackgrounds} holds for the newest installed version. Patch P31.
 *
 * <ul>
 *   <li><b>Screens</b> (1.12.2's {@code drawBackground}, the dirt, and {@code drawWorldBackground}'s gradient): outside a
 *       world the panorama (and its overlay), blurred, then {@code menu_background.png} tiled at 32; inside one the world
 *       as drawn this frame, blurred, then {@code inworld_menu_background.png}. Container, book and sign screens keep the
 *       gradient, as 1.20.5's {@code renderTransparentBackground}. Once a frame, as vanilla blurs once a frame.</li>
 *   <li><b>Lists</b> ({@code GuiSlot.drawScreen}): the screen's background behind the whole screen when the screen did not
 *       draw one (1.12.2's list screens rely on the list's dirt), {@code menu_list_background.png} over the list's area,
 *       the entries clipped to it instead of covered by dirt above and below, the header and footer separators on the
 *       list's top and bottom edges in place of the 4-pixel shadows. The scroll bar is 1.12.2's, which 1.20.5's
 *       {@code scroller} sprites draw pixel for pixel.</li>
 *   <li><b>Blur</b>: 1.20.6's {@code post/blur.json} is six box-blur passes. Its {@code RadiusMultiplier}s sit inside
 *       the {@code BlurDir} uniform's object, where {@code PostChain} never reads them, so every pass runs at the full
 *       radius. Core's {@code backportcore:shaders/post/menu_blur.json} is those six passes on 1.12.2's own {@code blur}
 *       program. The radius is Menu Background Blurriness times 10 ({@code GameRenderer.processBlurEffect}): the option
 *       of that name any backport registers, else 1.20.5's default, 5; under 1 there is no blur. Where shaders or
 *       framebuffers cannot run, the background is drawn without it.</li>
 * </ul>
 *
 * <p>The textures are read as {@code minecraft:textures/gui/<name>.png} when a pack has them (a backport's
 * {@code replaceAsset}, or a player's resource pack). Without one Core draws the color 1.20.6's texture is: each is a
 * single color (black at 64 of 255 for the screens, 112 for lists; the separators a white line at 51 over a black one at
 * 191), so the look needs no Mojang file.
 */
@SideOnly(Side.CLIENT)
public final class MenuBackground {

    static final String MENU = "menu_background";
    static final String INWORLD_MENU = "inworld_menu_background";
    static final String LIST = "menu_list_background";
    static final String INWORLD_LIST = "inworld_menu_list_background";
    static final String HEADER = "header_separator";
    static final String FOOTER = "footer_separator";
    static final String INWORLD_HEADER = "inworld_header_separator";
    static final String INWORLD_FOOTER = "inworld_footer_separator";

    /** 1.20.6's flat textures as colors (ARGB), for when no pack supplies the file. */
    static final int MENU_COLOR = 0x40000000;
    static final int LIST_COLOR = 0x70000000;
    static final int SEPARATOR_LIGHT = 0x33FFFFFF;
    static final int SEPARATOR_DARK = 0xBF000000;

    /** 1.20.5's Menu Background Blurriness default, as a radius. */
    public static final float DEFAULT_RADIUS = 5.0F;

    private static final ResourceLocation BLUR = new ResourceLocation("backportcore", "shaders/post/menu_blur.json");

    private static final Map<String, Boolean> PRESENT = new HashMap<String, Boolean>();

    /** For the client self-test: screen backgrounds drawn outside and inside a world, lists, blurred frames. */
    public static volatile int menuFrames;
    public static volatile int worldFrames;
    public static volatile int listFrames;
    public static volatile int blurFrames;

    private static boolean drawnThisFrame;
    private static boolean blurredThisFrame;
    @Nullable
    private static GuiSlot list;
    private static int overlays;
    private static boolean scissor;
    private static boolean colorHidden;

    @Nullable
    private static ShaderGroup blur;
    @Nullable
    private static Framebuffer blurMain;
    private static int blurWidth;
    private static int blurHeight;
    private static boolean blurFailed;

    private static Method gradient;
    private static Method containerBackground;
    private static Method overlayBackground;
    private static final Map<Class<?>, Boolean> OWN_CONTAINER_BACKGROUND = new HashMap<Class<?>, Boolean>();

    private MenuBackground() {
    }

    /** @return true when screens and lists are drawn as 1.20.5 draws them */
    public static boolean active() {
        return VanillaRevisions.menuBackgrounds(Presentation.newest(com.exiledradio.backportcore.presentation.LookArea.MENUS));
    }

    /** @return true for the screens 1.20.5 draws over the transparent gradient (containers, books, signs) */
    public static boolean transparent(@Nullable GuiScreen screen) {
        return screen instanceof GuiContainer || screen instanceof GuiScreenBook || screen instanceof GuiEditSign;
    }

    /**
     * The blur radius: Menu Background Blurriness (the option any backport registers under that name, 0 to 100% or 0
     * to 10) times 10, as {@code GameRenderer.processBlurEffect}; 1.20.5's default (5) when none is registered.
     */
    public static float blurRadius() {
        for (PresentationOption o : Presentation.options()) {
            if (o instanceof SliderOption && "menu_background_blurriness".equals(o.path())) {
                SliderOption s = (SliderOption) o;
                return (float) (s.max() <= 1.0 ? s.get() * 10.0 : s.get());
            }
        }
        return DEFAULT_RADIUS;
    }

    // ---- P31 hooks (through PresentationHooks)

    /** {@code GuiScreen.drawBackground}'s {@code tessellator.draw()}: the dirt, or 1.20.5's background in its place. */
    static void dirt(Tessellator tessellator) {
        if (!active()) {
            tessellator.draw();
            return;
        }
        tessellator.getBuffer().finishDrawing();
        tessellator.getBuffer().reset();
        drawScreen(Minecraft.getMinecraft().currentScreen);
    }

    /** {@code GuiScreen.drawWorldBackground}'s {@code drawGradientRect}: the gradient, or 1.20.5's in-world background. */
    static void worldGradient(GuiScreen screen, int left, int top, int right, int bottom, int from, int to) {
        if (active() && !transparent(screen)) {
            drawScreen(screen);
            return;
        }
        try {
            if (gradient == null) {
                gradient = ReflectionHelper.findMethod(Gui.class, "drawGradientRect", "func_73733_a",
                        int.class, int.class, int.class, int.class, int.class, int.class);
            }
            gradient.invoke(screen, left, top, right, bottom, from, to);
        } catch (Exception e) {
            BackportCore.LOGGER.error("Menu background: vanilla's drawGradientRect failed", e);
        }
    }

    /** {@code GuiSlot.drawScreen}'s {@code drawContainerBackground(tessellator)}. */
    static void listBackground(GuiSlot slot, Tessellator tessellator) {
        if (!active()) {
            vanillaContainerBackground(slot, tessellator);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        drawScreen(mc.currentScreen);
        if (ownContainerBackground(slot.getClass())) {
            vanillaContainerBackground(slot, tessellator);
        } else {
            int scrolled = slot.getAmountScrolled();
            tile(mc.world == null ? LIST : INWORLD_LIST, LIST_COLOR, slot.left, slot.top, slot.right - slot.left,
                    slot.bottom - slot.top, slot.right, slot.bottom + scrolled);
        }
        list = slot;
        overlays = 0;
        beginScissor(mc, slot);
        listFrames++;
    }

    /** {@code GuiSlot.drawScreen}'s two {@code overlayBackground} calls (the dirt above and below the list). */
    static void listOverlay(GuiSlot slot, int startY, int endY, int startAlpha, int endAlpha) {
        if (list != slot) {
            vanillaOverlay(slot, startY, endY, startAlpha, endAlpha);
            return;
        }
        overlays++;
        if (overlays == 1) {
            endScissor();
            separators(slot);
        } else {
            // What follows is 1.12.2's two 4-pixel shadows, which 1.20.5 does not draw; the scroll bar turns color back on.
            GlStateManager.colorMask(false, false, false, false);
            colorHidden = true;
        }
    }

    /** {@code GuiSlot.drawScreen}'s {@code getMaxScroll()}, the first call after the shadows. */
    static int listMaxScroll(GuiSlot slot) {
        if (list == slot) {
            restore();
        }
        return slot.getMaxScroll();
    }

    // ---- drawing

    /** A screen's background, once a frame: the panorama outside a world, the blur, then the menu texture. */
    static void drawScreen(@Nullable GuiScreen screen) {
        if (drawnThisFrame) {
            return;
        }
        drawnThisFrame = true;
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc);
        int width = screen != null ? screen.width : res.getScaledWidth();
        int height = screen != null ? screen.height : res.getScaledHeight();
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        if (mc.world == null) {
            PanoramaRenderer.render(screen);
            ResourceLocation overlay = Presentation.panoramaOverlay();
            if (overlay != null) {
                PanoramaRenderer.drawOverlay(mc, overlay, width, height);
            }
            GlStateManager.disableDepth();
        }
        blur(mc, mc.getRenderPartialTicks());
        tile(mc.world == null ? MENU : INWORLD_MENU, MENU_COLOR, 0, 0, width, height, 0, 0);
        if (mc.world == null) {
            menuFrames++;
        } else {
            worldFrames++;
        }
    }

    /**
     * Blurs what the main framebuffer holds, in place ({@code GameRenderer.processBlurEffect}), at most once a frame.
     *
     * @return true if it blurred
     */
    static boolean blur(Minecraft mc, float partialTicks) {
        float radius = blurRadius();
        if (blurredThisFrame || radius < 1.0F || blurFailed || !OpenGlHelper.shadersSupported || !OpenGlHelper.isFramebufferEnabled()) {
            return false;
        }
        blurredThisFrame = true;
        Framebuffer main = mc.getFramebuffer();
        try {
            if (blur == null || blurMain != main || blurWidth != mc.displayWidth || blurHeight != mc.displayHeight) {
                if (blur != null) {
                    blur.deleteShaderGroup();
                }
                blur = new ShaderGroup(mc.getTextureManager(), mc.getResourceManager(), main, BLUR);
                blur.createBindFramebuffers(mc.displayWidth, mc.displayHeight);
                blurMain = main;
                blurWidth = mc.displayWidth;
                blurHeight = mc.displayHeight;
            }
            List<Shader> passes = ObfuscationReflectionHelper.getPrivateValue(ShaderGroup.class, blur, "field_148031_d");
            float r = Math.round(radius);
            for (Shader pass : passes) {
                ShaderUniform u = pass.getShaderManager().getShaderUniform("Radius");
                if (u != null) {
                    u.set(r);
                }
            }
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.pushMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            blur.render(partialTicks);
            main.bindFramebuffer(true);
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();
            GlStateManager.enableTexture2D();
            GlStateManager.disableDepth();
            // 1.12.2's Shader.render turns the alpha test off and leaves it off. Vanilla's gradient (which this
            // background replaces) leaves it on, and a screen that draws a texture with transparent parts right after
            // drawDefaultBackground (Village & Pillage's book editor) drew those parts opaque white without it.
            GlStateManager.enableAlpha();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            blurFrames++;
            return true;
        } catch (Exception e) {
            blurFailed = true;
            BackportCore.LOGGER.warn("Menu background: the blur is not available; menus are drawn without it.", e);
            main.bindFramebuffer(true);
            return false;
        }
    }

    /**
     * Draws one of 1.20.6's flat textures over a rectangle as {@code GuiGraphics.blit(texture, x, y, 0, u, v, w, h, 32, 32)},
     * or its color where no pack has it.
     */
    static void tile(String name, int color, int x, int y, int width, int height, float u, float v) {
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        ResourceLocation texture = texture(mc, name);
        if (texture != null) {
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(texture);
            Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, width, height, 32.0F, 32.0F);
        } else {
            Gui.drawRect(x, y, x + width, y + height, color);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /**
     * A {@code HeaderAndFooterLayout} screen's content frame, for a backport's own screens (26.3's World Options): the
     * in-world or title list background between {@code top} and {@code bottom}, scrolled by {@code scrolled}, and the
     * header and footer separators, while the newer menu backgrounds are on. Since Core 1.4.5.
     *
     * @return whether it drew (false: the caller keeps 1.12.2's look)
     */
    public static boolean contentFrame(int left, int top, int right, int bottom, int scrolled) {
        if (!active()) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        boolean world = mc.world != null;
        tile(world ? INWORLD_LIST : LIST, LIST_COLOR, left, top, right - left, bottom - top, right, bottom + scrolled);
        separator(mc, world ? INWORLD_HEADER : HEADER, left, top - 2, right - left, true);
        separator(mc, world ? INWORLD_FOOTER : FOOTER, left, bottom, right - left, false);
        return true;
    }

    /** 1.20.6's {@code renderListSeparators}: two pixels above the list's top, two below its bottom. */
    static void separators(GuiSlot slot) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean world = mc.world != null;
        separator(mc, world ? INWORLD_HEADER : HEADER, slot.left, slot.top - 2, slot.right - slot.left, true);
        separator(mc, world ? INWORLD_FOOTER : FOOTER, slot.left, slot.bottom, slot.right - slot.left, false);
    }

    private static void separator(Minecraft mc, String name, int x, int y, int width, boolean header) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        ResourceLocation texture = texture(mc, name);
        if (texture != null) {
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(texture);
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0.0F, 0.0F, width, 2, 32.0F, 2.0F);
        } else {
            Gui.drawRect(x, y, x + width, y + 1, header ? SEPARATOR_LIGHT : SEPARATOR_DARK);
            Gui.drawRect(x, y + 1, x + width, y + 2, header ? SEPARATOR_DARK : SEPARATOR_LIGHT);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /** @return {@code minecraft:textures/gui/<name>.png} if a pack has it, else null (asked once per resource load) */
    @Nullable
    static ResourceLocation texture(Minecraft mc, String name) {
        ResourceLocation rl = new ResourceLocation("textures/gui/" + name + ".png");
        Boolean present;
        synchronized (PRESENT) {
            present = PRESENT.get(name);
        }
        if (present == null) {
            present = exists(mc.getResourceManager(), rl);
            synchronized (PRESENT) {
                PRESENT.put(name, present);
            }
        }
        return present ? rl : null;
    }

    private static boolean exists(IResourceManager manager, ResourceLocation rl) {
        try {
            manager.getResource(rl).close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** From each resource load: which textures the packs have is asked again. */
    static void invalidate() {
        synchronized (PRESENT) {
            PRESENT.clear();
        }
        // The blur's programs and its json come from the packs too.
        if (blur != null) {
            blur.deleteShaderGroup();
            blur = null;
        }
        blurFailed = false;
    }

    private static void beginScissor(Minecraft mc, GuiSlot slot) {
        scissor = false;
        if (GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) {
            return; // the screen clips already; 1.12.2's own drawing is left to it
        }
        int scale = new ScaledResolution(mc).getScaleFactor();
        int x = slot.left * scale;
        int y = mc.displayHeight - slot.bottom * scale;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, y, Math.max(0, (slot.right - slot.left) * scale), Math.max(0, (slot.bottom - slot.top) * scale));
        scissor = true;
    }

    private static void endScissor() {
        if (scissor) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            scissor = false;
        }
    }

    /** Turns color back on and lets go of the list (also at the start of each frame, should a list's drawing throw). */
    private static void restore() {
        if (colorHidden) {
            GlStateManager.colorMask(true, true, true, true);
            colorHidden = false;
        }
        endScissor();
        list = null;
    }

    /** @return true if this list's class draws its own container background (Forge's overridable hook) */
    private static boolean ownContainerBackground(Class<?> cls) {
        Boolean own = OWN_CONTAINER_BACKGROUND.get(cls);
        if (own == null) {
            own = false;
            for (Class<?> c = cls; c != null && c != GuiSlot.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    // Forge's own method keeps its name in production.
                    if ("drawContainerBackground".equals(m.getName()) && m.getParameterTypes().length == 1 && m.getParameterTypes()[0] == Tessellator.class) {
                        own = true;
                    }
                }
            }
            OWN_CONTAINER_BACKGROUND.put(cls, own);
        }
        return own;
    }

    private static void vanillaContainerBackground(GuiSlot slot, Tessellator tessellator) {
        try {
            if (containerBackground == null) {
                // Forge's method: its name is the same in development and production.
                containerBackground = GuiSlot.class.getDeclaredMethod("drawContainerBackground", Tessellator.class);
                containerBackground.setAccessible(true);
            }
            containerBackground.invoke(slot, tessellator);
        } catch (Exception e) {
            BackportCore.LOGGER.error("Menu background: the list's drawContainerBackground failed", e);
        }
    }

    private static void vanillaOverlay(GuiSlot slot, int startY, int endY, int startAlpha, int endAlpha) {
        try {
            if (overlayBackground == null) {
                overlayBackground = ReflectionHelper.findMethod(GuiSlot.class, "overlayBackground", "func_148136_c",
                        int.class, int.class, int.class, int.class);
            }
            overlayBackground.invoke(slot, startY, endY, startAlpha, endAlpha);
        } catch (Exception e) {
            BackportCore.LOGGER.error("Menu background: the list's overlayBackground failed", e);
        }
    }

    /** Forge event handlers; public for the event bus. */
    public static final class Events {

        @SubscribeEvent
        public void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                drawnThisFrame = false;
                blurredThisFrame = false;
                restore();
            }
        }
    }
}

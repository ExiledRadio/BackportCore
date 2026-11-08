package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * Vanilla's startup (Mojang) screen at the newest installed version, as its {@code LoadingOverlay} draws it when a
 * resource load ends ({@link VanillaRevisions.Startup}): 1.14's white screen with the 256 by 256 {@code mojang.png}
 * and its bar, 1.16's red one with the Mojang Studios logo in two halves and a white bar, or black with 1.17's
 * Monochrome Logo. For one second it covers the screen with the bar full, then fades away over the next second
 * while the screen beneath (the title screen at startup) draws.
 *
 * <p>It runs at the end of every resource load after Core's init: the last one of the game's start (so the game
 * fades from it into the title screen, as 1.14's does) and every later one (F3+T, the resource packs screen).
 * 1.12.2 loads resources in one blocking call and Forge shows its own loading screen during the start, so the
 * screen appears as the load finishes, with the bar full, not during the load.
 */
@SideOnly(Side.CLIENT)
public final class StartupScreen implements IResourceManagerReloadListener {

    static final StartupScreen INSTANCE = new StartupScreen();
    private static final int BRAND_RED = 0xEF323D;

    private static long fadeOutStart = -1L;
    /** For the client self-test: frames drawn, and the revision last drawn. */
    public static volatile int frames;
    public static volatile VanillaRevisions.Startup lastDrawn;

    private StartupScreen() {
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        if (VanillaRevisions.startup() != VanillaRevisions.Startup.NONE) {
            fadeOutStart = Minecraft.getSystemTime();
        }
    }

    /** @return true while it covers the screen completely (its first second) */
    public static boolean opaque() {
        return fadeOutStart >= 0 && Minecraft.getSystemTime() - fadeOutStart < 1000L;
    }

    /** @return true while it is drawn at all */
    public static boolean showing() {
        return fadeOutStart >= 0;
    }

    /** @return the background color of the revision, 1.17's Monochrome Logo applied */
    static int background(VanillaRevisions.Startup rev) {
        if (rev == VanillaRevisions.Startup.STUDIOS_1_16) {
            boolean mono = Presentation.MONOCHROME_LOGO.isShown() && Presentation.MONOCHROME_LOGO.get();
            return mono ? 0x000000 : BRAND_RED;
        }
        return 0xFFFFFF;
    }

    /** Drawn over everything, after the frame. */
    public static final class Events {
        @SubscribeEvent
        public void onRender(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.END && fadeOutStart >= 0) {
                draw(Minecraft.getMinecraft());
            }
        }
    }

    static void draw(Minecraft mc) {
        VanillaRevisions.Startup rev = VanillaRevisions.startup();
        float f = (Minecraft.getSystemTime() - fadeOutStart) / 1000.0F;
        if (rev == VanillaRevisions.Startup.NONE || f >= 2.0F) {
            fadeOutStart = -1L;
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        int w = sr.getScaledWidth();
        int h = sr.getScaledHeight();
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, sr.getScaledWidth_double(), sr.getScaledHeight_double(), 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();

        int bgAlpha;
        float logoAlpha;
        if (f >= 1.0F) {
            bgAlpha = MathHelper.ceil((1.0F - MathHelper.clamp(f - 1.0F, 0.0F, 1.0F)) * 255.0F);
            logoAlpha = 1.0F - MathHelper.clamp(f - 1.0F, 0.0F, 1.0F);
        } else {
            bgAlpha = 255;
            logoAlpha = 1.0F;
        }
        Gui.drawRect(0, 0, w, h, background(rev) | bgAlpha << 24);
        ResourceLocation logo = new ResourceLocation(rev.logoPath());
        if (rev == VanillaRevisions.Startup.STUDIOS_1_16) {
            if (Presentation.asset(rev.logoPath()) != null) {
                // 1.16.5's LoadingOverlay.render: two halves of a 120 by 120 texture, added onto the background.
                double half = Math.min(w * 0.75, h) * 0.25;
                int halfH = (int) (half * 0.5);
                double logoW = half * 4.0;
                int halfW = (int) (logoW * 0.5);
                int cx = (int) (w * 0.5);
                int cy = (int) (h * 0.5);
                mc.getTextureManager().bindTexture(logo);
                GlStateManager.enableBlend();
                GL14.glBlendEquation(32774);
                GlStateManager.blendFunc(770, 1);
                GlStateManager.alphaFunc(516, 0.0F);
                GlStateManager.color(1.0F, 1.0F, 1.0F, logoAlpha);
                Gui.drawScaledCustomSizeModalRect(cx - halfW, cy - halfH, -0.0625F, 0.0F, 120, 60, halfW, (int) half, 120.0F, 120.0F);
                Gui.drawScaledCustomSizeModalRect(cx, cy - halfH, 0.0625F, 60.0F, 120, 60, halfW, (int) half, 120.0F, 120.0F);
                GlStateManager.blendFunc(770, 771);
                GlStateManager.alphaFunc(516, 0.1F);
                GlStateManager.disableBlend();
            }
            if (f < 1.0F) {
                bar116(w / 2 - (int) (Math.min(w * 0.75, h) * 0.25 * 2.0), (int) (h * 0.8325) - 5,
                        w / 2 + (int) (Math.min(w * 0.75, h) * 0.25 * 2.0), (int) (h * 0.8325) + 5, 1.0F - MathHelper.clamp(f, 0.0F, 1.0F));
            }
        } else {
            // 1.14.4's ResourceLoadProgressGui.render.
            mc.getTextureManager().bindTexture(logo);
            GlStateManager.enableBlend();
            GlStateManager.color(1.0F, 1.0F, 1.0F, logoAlpha);
            Gui.drawModalRectWithCustomSizedTexture((w - 256) / 2, (h - 256) / 2, 0.0F, 0.0F, 256, 256, 256.0F, 256.0F);
            GlStateManager.disableBlend();
            if (f < 1.0F) {
                bar114(w / 2 - 150, h / 4 * 3, w / 2 + 150, h / 4 * 3 + 10, 1.0F - MathHelper.clamp(f, 0.0F, 1.0F));
            }
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableDepth();
        frames++;
        lastDrawn = rev;
    }

    /** 1.14.4's {@code drawProgressBar}, at full progress. */
    private static void bar114(int x1, int y1, int x2, int y2, float alpha) {
        int fill = MathHelper.ceil((x2 - x1 - 2) * 1.0F);
        int border = Math.round((1.0F - alpha) * 255.0F);
        Gui.drawRect(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF000000 | border << 16 | border << 8 | border);
        Gui.drawRect(x1, y1, x2, y2, -1);
        float t = 1.0F - alpha;
        int r = (int) (226.0F + (255.0F - 226.0F) * t);
        int g = (int) (40.0F + (255.0F - 40.0F) * t);
        int b = (int) (55.0F + (255.0F - 55.0F) * t);
        Gui.drawRect(x1 + 1, y1 + 1, x1 + fill, y2 - 1, 0xFF000000 | r << 16 | g << 8 | b);
    }

    /** 1.16.5's {@code drawProgressBar}, at full progress: a white outline and bar. */
    private static void bar116(int x1, int y1, int x2, int y2, float alpha) {
        int fill = MathHelper.ceil((x2 - x1 - 2) * 1.0F);
        int a = Math.round(alpha * 255.0F);
        int color = a << 24 | 0xFFFFFF;
        Gui.drawRect(x1 + 1, y1, x2 - 1, y1 + 1, color);
        Gui.drawRect(x1 + 1, y2, x2 - 1, y2 - 1, color);
        Gui.drawRect(x1, y1, x1 + 1, y2, color);
        Gui.drawRect(x2, y1, x2 - 1, y2, color);
        Gui.drawRect(x1 + 2, y1 + 2, x1 + fill, y2 - 2, color);
    }

    /** For the self-test: starts the fade as a resource load's end would. */
    public static void startForTest() {
        fadeOutStart = Minecraft.getSystemTime();
    }
}

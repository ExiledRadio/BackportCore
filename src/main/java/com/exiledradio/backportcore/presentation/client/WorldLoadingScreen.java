package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.LoadingRevision;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.LoadingScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * The world-loading screen. 1.12.2 draws it through {@code Minecraft.loadingScreen} from inside
 * {@code launchIntegratedServer}'s wait loop (about five frames a second), not as a GUI screen, so Core puts
 * this subclass in that public field (no patch). While a singleplayer world loads, and only then, it draws the
 * newest installed version's revision ({@link LoadingScreens}); every other use of the loading screen (saving,
 * leaving a world, other mods' progress) is 1.12.2's, untouched.
 */
@SideOnly(Side.CLIENT)
public final class WorldLoadingScreen extends LoadingScreenRenderer {

    /** Frames drawn, by kind, for the client self-test. */
    public static volatile int frames;
    public static volatile int gridFrames;
    public static volatile int dataFrames;
    public static volatile LoadingRevision lastRevision;

    private static boolean warned;
    private static Field running;

    private final Minecraft mc;
    private String title = "";
    private String message = "";
    private Framebuffer framebuffer;
    private long lastDraw;

    public WorldLoadingScreen(Minecraft mc) {
        super(mc);
        this.mc = mc;
    }

    /** Puts this in place of vanilla's loading screen, which 1.12.2 makes again on every window resize. */
    static void install(Minecraft mc) {
        if (Presentation.loadingRevision() == LoadingRevision.CLASSIC || mc.loadingScreen == null) {
            return;
        }
        if (mc.loadingScreen.getClass() == LoadingScreenRenderer.class) {
            mc.loadingScreen = new WorldLoadingScreen(mc);
        } else if (!(mc.loadingScreen instanceof WorldLoadingScreen) && !warned) {
            warned = true;
            BackportCore.LOGGER.warn("Presentation: another mod's loading screen ({}) is in place; the world-loading screen stays theirs",
                    mc.loadingScreen.getClass().getName());
        }
    }

    @Override
    public void resetProgressAndMessage(String message) {
        this.title = message;
        super.resetProgressAndMessage(message);
    }

    @Override
    public void displaySavingString(String message) {
        this.title = message;
        super.displaySavingString(message);
    }

    @Override
    public void displayLoadingString(String message) {
        this.message = message;
        if (loadingWorld()) {
            draw();
            return;
        }
        super.displayLoadingString(message);
    }

    @Override
    public void setLoadingProgress(int progress) {
        if (loadingWorld()) {
            draw();
            return;
        }
        super.setLoadingProgress(progress);
    }

    /** @return true while {@code launchIntegratedServer} waits for the server it started */
    private boolean loadingWorld() {
        if (!isRunning()) {
            return false;
        }
        IntegratedServer server = this.mc.getIntegratedServer();
        return server != null && !server.serverIsInRunLoop() && this.mc.world == null
                && this.title.equals(I18n.format("menu.loadingLevel"))
                && LoadingScreens.drawer(Presentation.loadingRevision()) != null;
    }

    /** {@code Minecraft.running} is package-private; false while the game shuts down (vanilla's path then throws). */
    private boolean isRunning() {
        try {
            if (running == null) {
                running = ReflectionHelper.findField(Minecraft.class, "running", "field_71425_J");
            }
            return running.getBoolean(this.mc);
        } catch (Exception e) {
            return false;
        }
    }

    /** As 1.12.2's {@code setLoadingProgress} sets up a frame, with the revision's drawer in place of its text. */
    private void draw() {
        long now = Minecraft.getSystemTime();
        if (now - this.lastDraw < 50L) {
            return;
        }
        this.lastDraw = now;
        LoadingRevision revision = Presentation.loadingRevision();
        LoadingScreens.Drawer drawer = LoadingScreens.drawer(revision);
        ScaledResolution resolution = new ScaledResolution(this.mc);
        int scale = resolution.getScaleFactor();
        int width = resolution.getScaledWidth();
        int height = resolution.getScaledHeight();
        if (this.framebuffer == null || this.framebuffer.framebufferWidth != this.mc.displayWidth
                || this.framebuffer.framebufferHeight != this.mc.displayHeight) {
            if (this.framebuffer != null) {
                this.framebuffer.deleteFramebuffer();
            }
            this.framebuffer = new Framebuffer(this.mc.displayWidth, this.mc.displayHeight, false);
            this.framebuffer.setFramebufferFilter(9728);
        }
        if (OpenGlHelper.isFramebufferEnabled()) {
            this.framebuffer.framebufferClear();
        } else {
            GlStateManager.clear(256);
        }
        this.framebuffer.bindFramebuffer(false);
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, resolution.getScaledWidth_double(), resolution.getScaledHeight_double(), 0.0D, 100.0D, 300.0D);
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -200.0F);
        if (!OpenGlHelper.isFramebufferEnabled()) {
            GlStateManager.clear(16640);
        }
        try {
            if (!FMLClientHandler.instance().handleLoadingScreen(resolution)) {
                drawer.draw(this.mc, width, height, LoadingScreens.frame(this.title, this.message));
                frames++;
                lastRevision = revision;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.framebuffer.unbindFramebuffer();
        if (OpenGlHelper.isFramebufferEnabled()) {
            this.framebuffer.framebufferRender(width * scale, height * scale);
        }
        this.mc.updateDisplay();
        Thread.yield();
    }
}

package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.LoadingRevision;
import com.exiledradio.backportcore.presentation.LoadingStatus;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.SpawnProgress;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws the world-loading screen of the revision the newest installed version picks
 * ({@link Presentation#loadingRevision()}). Core draws 1.13's text, 1.14's chunk grid and 1.16's "Reading
 * world data..." then the grid; a backport of 1.20.5, 1.21 or 1.21.9 registers a {@link Drawer} for its
 * revision with {@link #register}, and until one does, Core draws the newest revision it has.
 */
@SideOnly(Side.CLIENT)
public final class LoadingScreens {

    /** Draws one frame of the world-loading screen; the projection is the GUI's scaled one, as in a screen. */
    public interface Drawer {
        void draw(Minecraft mc, int width, int height, Frame frame);
    }

    /** What the frame shows. */
    public static final class Frame {
        /** The title 1.12.2 shows ("Loading world"). */
        public final String title;
        /** The server's current step ("Building terrain"), translated. */
        public final String message;
        /** True once the server is preparing the spawn area (the grid has a center). */
        public final boolean preparingSpawn;
        /** The share of the spawn area loaded, 0 to 100. */
        public final int percent;

        Frame(String title, String message, boolean preparingSpawn, int percent) {
            this.title = title;
            this.message = message;
            this.preparingSpawn = preparingSpawn;
            this.percent = percent;
        }
    }

    private static final Map<LoadingRevision, Drawer> DRAWERS = new EnumMap<LoadingRevision, Drawer>(LoadingRevision.class);

    static {
        DRAWERS.put(LoadingRevision.TEXT_1_13, LoadingScreens::drawText113);
        DRAWERS.put(LoadingRevision.GRID_1_14, LoadingScreens::drawGrid114);
        DRAWERS.put(LoadingRevision.DATA_1_16, LoadingScreens::drawData116);
    }

    private LoadingScreens() {
    }

    /** Registers (or replaces) the drawer of a revision. */
    public static synchronized void register(LoadingRevision revision, Drawer drawer) {
        DRAWERS.put(revision, drawer);
    }

    /** @return the drawer for a revision: its own, or the newest older one there is */
    static synchronized Drawer drawer(LoadingRevision revision) {
        for (int i = revision.ordinal(); i > 0; i--) {
            Drawer d = DRAWERS.get(LoadingRevision.values()[i]);
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    /** @return the frame as it stands now */
    static Frame frame(String title, String message) {
        return new Frame(title, message, SpawnProgress.center() != null, SpawnProgress.percent());
    }

    // ---- 1.13 ----

    /** 1.13: 1.12.2's text screen, with "Preparing spawn area: N%" while the spawn area is prepared. */
    static void drawText113(Minecraft mc, int width, int height, Frame frame) {
        drawDirt(width, height);
        String message = frame.preparingSpawn ? I18n.format("menu.preparingSpawn", MathHelper.clamp(frame.percent, 0, 100)) : frame.message;
        FontRenderer font = mc.fontRenderer;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        font.drawStringWithShadow(frame.title, (width - font.getStringWidth(frame.title)) / 2.0F, height / 2 - 4 - 16, 0xFFFFFF);
        font.drawStringWithShadow(message, (width - font.getStringWidth(message)) / 2.0F, height / 2 - 4 + 8, 0xFFFFFF);
    }

    // ---- 1.14 ----

    /**
     * 1.14.4's {@code WorldLoadProgressScreen.render}: the dirt background, the percentage 30 above the middle,
     * and the 43 by 43 grid of 2 by 2 pixel cells centered 30 below it, with no spacing (so no border lines).
     */
    static void drawGrid114(Minecraft mc, int width, int height, Frame frame) {
        drawDirt(width, height);
        String percent = MathHelper.clamp(frame.percent, 0, 100) + "%";
        int x = width / 2;
        int y = height / 2;
        drawGrid(x, y + 30, 2, 0);
        mc.fontRenderer.drawStringWithShadow(percent, x - mc.fontRenderer.getStringWidth(percent) / 2.0F, y - 9 / 2 - 30, 0xFFFFFF);
        WorldLoadingScreen.gridFrames++;
    }

    /** 1.14.4's {@code WorldLoadProgressScreen.func_213466_a}: every cell of the grid, colored by status. */
    public static void drawGrid(int centerX, int centerY, int scale, int spacing) {
        int cell = scale + spacing;
        int side = SpawnProgress.GRID_RADIUS * 2 + 1;
        int total = side * cell - spacing;
        int x0 = centerX - total / 2;
        int y0 = centerY - total / 2;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        for (int gx = 0; gx < side; gx++) {
            for (int gz = 0; gz < side; gz++) {
                int argb = LoadingStatus.argb(SpawnProgress.status(gx - SpawnProgress.GRID_RADIUS, gz - SpawnProgress.GRID_RADIUS));
                int r = argb >> 16 & 255;
                int g = argb >> 8 & 255;
                int b = argb & 255;
                int left = x0 + gx * cell;
                int top = y0 + gz * cell;
                buffer.pos(left, top + scale, 0.0D).color(r, g, b, 255).endVertex();
                buffer.pos(left + scale, top + scale, 0.0D).color(r, g, b, 255).endVertex();
                buffer.pos(left + scale, top, 0.0D).color(r, g, b, 255).endVertex();
                buffer.pos(left, top, 0.0D).color(r, g, b, 255).endVertex();
            }
        }
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    // ---- 1.16 ----

    /**
     * 1.16: while the save is read (before the server starts on the spawn area), a dirt screen with
     * "Reading world data..." at y 70, as 1.16's {@code DirtMessageScreen}; then 1.14's grid.
     */
    static void drawData116(Minecraft mc, int width, int height, Frame frame) {
        if (frame.preparingSpawn) {
            drawGrid114(mc, width, height, frame);
            return;
        }
        drawDirt(width, height);
        String text = I18n.format("selectWorld.data_read");
        mc.fontRenderer.drawStringWithShadow(text, width / 2 - mc.fontRenderer.getStringWidth(text) / 2.0F, 70, 0xFFFFFF);
        WorldLoadingScreen.dataFrames++;
    }

    /** The options background tinted to 64, 64, 64, as both 1.12.2's loading screen and later dirt screens draw it. */
    public static void drawDirt(int width, int height) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        Minecraft.getMinecraft().getTextureManager().bindTexture(Gui.OPTIONS_BACKGROUND);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        buffer.pos(0.0D, height, 0.0D).tex(0.0D, height / 32.0F).color(64, 64, 64, 255).endVertex();
        buffer.pos(width, height, 0.0D).tex(width / 32.0F, height / 32.0F).color(64, 64, 64, 255).endVertex();
        buffer.pos(width, 0.0D, 0.0D).tex(width / 32.0F, 0.0D).color(64, 64, 64, 255).endVertex();
        buffer.pos(0.0D, 0.0D, 0.0D).tex(0.0D, 0.0D).color(64, 64, 64, 255).endVertex();
        tessellator.draw();
    }
}

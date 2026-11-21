package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.option.OptionStore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * 26.3's GUI Scale for the debug overlay (Debug Options' footer, {@code Options.debugGuiScale}): Unchanged (-1) draws F3
 * at the game's scale, Auto (0, the default) at half the largest scale the window fits, and 1, 2 ... at that scale. As
 * {@code DebugScreenOverlay.extractRenderState}, the overlay is only ever drawn smaller than the rest of the game, never
 * larger, and its columns reach the window's right edge at their own scale.
 *
 * <p>No patch: Forge draws F3's columns right after {@code RenderGameOverlayEvent.Text}, so at the end of that event
 * Core draws them itself at the overlay's scale, as Forge's {@code renderHUDText} would, and cancels Forge's drawing.
 * The lagometer (F3 + 2) is drawn between its own Pre and Post events with the game's GUI Scale set to the overlay's
 * for that moment, which is what {@code GuiOverlayDebug.renderLagometer}'s {@code ScaledResolution} reads. Core's
 * network charts draw at it too. The scale follows 1.12.2's own arithmetic ({@code ScaledResolution}), as the game's GUI
 * Scale does.
 */
@SideOnly(Side.CLIENT)
public final class DebugScale {

    /** The option's key in Core's options file. */
    public static final String KEY = "backportcore:debug_gui_scale";
    public static final int UNCHANGED = -1;
    public static final int AUTO = 0;

    /** For the client self-test: frames drawn at the overlay's own scale. */
    public static volatile int frames;

    private DebugScale() {
    }

    /** @return the chosen value: -1 Unchanged, 0 Auto, else a scale */
    public static int chosen() {
        String s = OptionStore.get(KEY);
        if (s != null) {
            try {
                return Math.max(UNCHANGED, Integer.parseInt(s.trim()));
            } catch (NumberFormatException e) {
                return AUTO;
            }
        }
        return AUTO;
    }

    public static void choose(int value) {
        OptionStore.put(KEY, Integer.toString(Math.max(UNCHANGED, value)));
    }

    /** 1.12.2's {@code ScaledResolution}: the scale for a GUI Scale of {@code want} (0 the largest) in a w by h window. */
    public static int scaleFor(int want, int w, int h, boolean unicode) {
        int limit = want == 0 ? 1000 : want;
        int f = 1;
        while (f < limit && w / (f + 1) >= 320 && h / (f + 1) >= 240) {
            f++;
        }
        if (unicode && f % 2 != 0 && f != 1) {
            f--;
        }
        return f;
    }

    /** @return the largest value the slider goes to: the largest scale the window fits */
    public static int max(Minecraft mc) {
        return scaleFor(0, mc.displayWidth, mc.displayHeight, mc.isUnicode());
    }

    /**
     * @return the scale the overlay is drawn at for {@code chosen} in a w by h window at game scale {@code game}, or
     *         {@code game} when it keeps the game's (Unchanged, or a scale not below the game's)
     */
    public static int overlayScale(int chosen, int game, int w, int h, boolean unicode) {
        int s;
        if (chosen == UNCHANGED) {
            s = game;
        } else if (chosen == AUTO) {
            int half = scaleFor(0, w, h, unicode) / 2;
            s = half > 0 ? scaleFor(half, w, h, unicode) : game;
        } else {
            s = scaleFor(chosen, w, h, unicode);
        }
        return s < game && s > 0 ? s : game;
    }

    /** @return the overlay's scale now, or 0 when it is drawn at the game's (before 26.3, or not smaller) */
    public static int now(Minecraft mc) {
        if (!VanillaRevisions.debugGuiScale()) {
            return 0;
        }
        int game = new ScaledResolution(mc).getScaleFactor();
        int s = overlayScale(chosen(), game, mc.displayWidth, mc.displayHeight, mc.isUnicode());
        return s < game ? s : 0;
    }

    /**
     * Starts drawing at the overlay's scale: {@code {width, height}} at that scale, with the matrix pushed (end with
     * {@link #end}), or null when the overlay keeps the game's scale (nothing pushed).
     */
    static int[] begin(Minecraft mc) {
        int s = now(mc);
        if (s <= 0) {
            return null;
        }
        float f = (float) s / (float) new ScaledResolution(mc).getScaleFactor();
        GlStateManager.pushMatrix();
        GlStateManager.scale(f, f, 1.0F);
        return new int[] {mc.displayWidth / s, mc.displayHeight / s, s};
    }

    static void end() {
        GlStateManager.popMatrix();
    }

    /** Forge's {@code renderHUDText} columns, line for line, in a {@code width} wide space. */
    static void drawColumns(FontRenderer font, List<String> left, List<String> right, int width) {
        int top = 2;
        for (String msg : left) {
            if (msg == null) {
                continue;
            }
            Gui.drawRect(1, top - 1, 2 + font.getStringWidth(msg) + 1, top + font.FONT_HEIGHT - 1, -1873784752);
            font.drawString(msg, 2, top, 14737632);
            top += font.FONT_HEIGHT;
        }
        top = 2;
        for (String msg : right) {
            if (msg == null) {
                continue;
            }
            int w = font.getStringWidth(msg);
            int x = width - 2 - w;
            Gui.drawRect(x - 1, top - 1, x + w + 1, top + font.FONT_HEIGHT - 1, -1873784752);
            font.drawString(msg, x, top, 14737632);
            top += font.FONT_HEIGHT;
        }
    }

    /** Forge event handlers; public for the event bus. */
    public static final class Events {
        private int savedGuiScale = -1;
        private boolean pushed;

        /** Last of every mod's {@code Text} handlers: F3's columns at the overlay's scale. */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onText(RenderGameOverlayEvent.Text event) {
            Minecraft mc = Minecraft.getMinecraft();
            if (!mc.gameSettings.showDebugInfo && !DebugScreen.alwaysShown) {
                return;
            }
            int[] size = begin(mc);
            if (size == null) {
                return;
            }
            try {
                drawColumns(mc.fontRenderer, event.getLeft(), event.getRight(), size[0]);
                frames++;
            } finally {
                end();
            }
            event.setCanceled(true);
        }

        /** The lagometer: its {@code ScaledResolution} at the overlay's scale, drawn smaller. */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onPre(RenderGameOverlayEvent.Pre event) {
            if (event.getType() != RenderGameOverlayEvent.ElementType.FPS_GRAPH) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            int[] size = begin(mc);
            if (size == null) {
                return;
            }
            this.pushed = true;
            this.savedGuiScale = mc.gameSettings.guiScale;
            mc.gameSettings.guiScale = size[2];
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onPost(RenderGameOverlayEvent.Post event) {
            if (event.getType() == RenderGameOverlayEvent.ElementType.FPS_GRAPH || event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
                restore();
            }
        }

        private void restore() {
            if (this.pushed) {
                this.pushed = false;
                Minecraft.getMinecraft().gameSettings.guiScale = this.savedGuiScale;
                end();
            }
        }
    }
}

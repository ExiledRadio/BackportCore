package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.option.PresentationOption;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1.13's Fullscreen Resolution, 26.3's Exclusive Fullscreen Mode ({@link Presentation#FULLSCREEN_RESOLUTION}): the
 * monitor modes Fullscreen can switch to, as vanilla's {@code Monitor} lists them (8 bits a color or more, GLFW's order:
 * color depth, area, width, refresh rate), and the mode the exclusive fullscreen uses. 1.12.2 always takes the desktop's
 * mode ({@code Minecraft.updateDisplayMode}); with a mode chosen, patch P42's hook enters fullscreen at that mode
 * instead ({@link BorderlessFullscreen#toggle}). A change while the game is fullscreen applies when Video Settings
 * closes, as vanilla's {@code VideoSettingsScreen.removed}.
 */
@SideOnly(Side.CLIENT)
public final class FullscreenModes {

    /** For the client self-test: exclusive fullscreens entered at a chosen mode. */
    public static volatile int entered;

    @Nullable
    private static List<DisplayMode> modes;
    /** The saved value when Video Settings opened, to see whether it changed. */
    @Nullable
    private static String atOpen;

    private FullscreenModes() {
    }

    /** @return true when Video Settings shows the slider: from 1.13, on LWJGL 2, without OptiFine (it has its own Fullscreen Mode) */
    public static boolean shown() {
        return Presentation.FULLSCREEN_RESOLUTION.isShown() && !FMLClientHandler.instance().hasOptifine();
    }

    /** @return the monitor's modes, lowest first (read once; empty when LWJGL lists none) */
    public static List<DisplayMode> modes() {
        if (modes == null) {
            List<DisplayMode> out = new ArrayList<DisplayMode>();
            try {
                for (DisplayMode m : Display.getAvailableDisplayModes()) {
                    if (m.isFullscreenCapable() && m.getBitsPerPixel() >= 24 && find(out, m) < 0) {
                        out.add(m);
                    }
                }
            } catch (LWJGLException | RuntimeException e) {
                BackportCore.LOGGER.warn("Presentation: the monitor's display modes could not be read ({})", e.toString());
            }
            Collections.sort(out, (a, b) -> {
                int c = Integer.compare(colorBits(a), colorBits(b));
                if (c == 0) {
                    c = Integer.compare(a.getWidth() * a.getHeight(), b.getWidth() * b.getHeight());
                }
                if (c == 0) {
                    c = Integer.compare(a.getWidth(), b.getWidth());
                }
                return c != 0 ? c : Integer.compare(a.getFrequency(), b.getFrequency());
            });
            modes = out;
        }
        return modes;
    }

    private static int find(List<DisplayMode> list, DisplayMode m) {
        for (int i = 0; i < list.size(); i++) {
            if (write(list.get(i)).equals(write(m))) {
                return i;
            }
        }
        return -1;
    }

    /** The color bits vanilla shows ({@code redBits + greenBits + blueBits}): LWJGL 2 counts the padding of a 32-bit mode. */
    static int colorBits(DisplayMode m) {
        return Math.min(24, m.getBitsPerPixel());
    }

    /** @return the mode as vanilla's options.txt saves it ({@code VideoMode.write}): {@code 1920x1080@60:24} */
    public static String write(DisplayMode m) {
        return m.getWidth() + "x" + m.getHeight() + "@" + m.getFrequency() + ":" + colorBits(m);
    }

    /** @return the index of the chosen mode in {@link #modes()}, or -1 for Current (or a mode the monitor no longer has) */
    public static int index() {
        String saved = Presentation.FULLSCREEN_RESOLUTION.get();
        if (saved.isEmpty()) {
            return -1;
        }
        List<DisplayMode> list = modes();
        for (int i = 0; i < list.size(); i++) {
            if (write(list.get(i)).equals(saved)) {
                return i;
            }
        }
        return -1;
    }

    /** Chooses the mode at {@code index} of {@link #modes()}, -1 for Current. */
    public static void choose(int index) {
        List<DisplayMode> list = modes();
        Presentation.FULLSCREEN_RESOLUTION.set(index < 0 || index >= list.size() ? "" : write(list.get(index)));
    }

    /** @return the mode exclusive fullscreen is to use, or null for 1.12.2's (the desktop's) */
    @Nullable
    public static DisplayMode chosen() {
        if (!shown()) {
            return null;
        }
        int i = index();
        return i < 0 ? null : modes().get(i);
    }

    /**
     * Enters 1.12.2's exclusive fullscreen at {@code mode}: what {@code Minecraft.toggleFullscreen} does on the way in,
     * with the mode in place of {@code updateDisplayMode}'s desktop mode. Leaving is vanilla's (the window's size again).
     */
    static void enter(Minecraft mc, DisplayMode mode) throws Exception {
        BorderlessFullscreen.setFullscreenField(mc, true);
        mc.gameSettings.fullScreen = true;
        Display.setDisplayMode(mode);
        Display.setFullscreen(true);
        BorderlessFullscreen.finish(mc, Math.max(1, mode.getWidth()), Math.max(1, mode.getHeight()));
        entered++;
        BackportCore.LOGGER.info("Fullscreen: exclusive at {}", write(mode));
    }

    /** Video Settings opened: remember the mode, to apply a change when it closes. */
    static void opened() {
        if (atOpen == null) {
            atOpen = Presentation.FULLSCREEN_RESOLUTION.get();
        }
    }

    /** Video Settings closed: a new mode while in exclusive fullscreen switches to it at once. */
    static void closed(Minecraft mc) {
        String before = atOpen;
        atOpen = null;
        if (before == null || before.equals(Presentation.FULLSCREEN_RESOLUTION.get()) || !mc.isFullScreen() || BorderlessFullscreen.active()) {
            return;
        }
        mc.toggleFullscreen();
        mc.toggleFullscreen();
    }

    /** @return the slider's text: "Fullscreen Resolution: Current", "Exclusive Fullscreen Mode: 1920x1080@60 (24bit)" */
    static String text() {
        String label = I18n.format(labelKey());
        List<DisplayMode> list = modes();
        if (list.isEmpty()) {
            return label + ": " + I18n.format("options.fullscreen.unavailable");
        }
        int i = index();
        if (i < 0) {
            return label + ": " + I18n.format("options.fullscreen.current");
        }
        DisplayMode m = list.get(i);
        return label + ": " + I18n.format("options.fullscreen.entry", m.getWidth(), m.getHeight(), m.getFrequency(), colorBits(m));
    }

    /** 26.3 renamed it Exclusive Fullscreen Mode and gave it a tooltip. */
    static String labelKey() {
        return VanillaRevisions.exclusiveFullscreenMode() ? "options.fullscreen.exclusive.mode" : "options.fullscreen.resolution";
    }

    /** 1.13's slider: 310 wide, a stop for Current and each mode; the value follows the knob and is saved when let go. */
    public static final class Slider extends GuiButton implements OptionWidgets.OptionButton {
        private boolean dragging;

        Slider(int x, int y) {
            super(OptionWidgets.nextId(), x, y, 310, 20, "");
            refresh();
        }

        @Override
        public PresentationOption option() {
            return Presentation.FULLSCREEN_RESOLUTION;
        }

        @Override
        public void refresh() {
            Presentation.FULLSCREEN_RESOLUTION.tooltip(VanillaRevisions.exclusiveFullscreenMode() ? "options.fullscreen.exclusive.mode.tooltip" : null);
            this.enabled = !modes().isEmpty();
            this.displayString = text();
        }

        private int stops() {
            return modes().size() + 1;
        }

        private int stopAt(int mouseX) {
            double f = Math.max(0.0, Math.min(1.0, (double) (mouseX - (this.x + 4)) / (double) (this.width - 8)));
            return (int) Math.round(f * (stops() - 1)) - 1;
        }

        @Override
        protected int getHoverState(boolean mouseOver) {
            return 0;
        }

        @Override
        protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
            if (!this.visible) {
                return;
            }
            if (this.dragging) {
                int i = stopAt(mouseX);
                if (i != index()) {
                    choose(i);
                    refresh();
                }
            }
            mc.getTextureManager().bindTexture(BUTTON_TEXTURES);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            int n = stops() - 1;
            double at = n <= 0 ? 0.0 : (index() + 1) / (double) n;
            int knob = this.x + (int) (at * (double) (this.width - 8));
            drawTexturedModalRect(knob, this.y, 0, 66, 4, 20);
            drawTexturedModalRect(knob + 4, this.y, 196, 66, 4, 20);
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                choose(stopAt(mouseX));
                refresh();
                this.dragging = true;
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            this.dragging = false;
        }
    }

    /** @return the slider for Video Settings, at the left edge of the buttons */
    static GuiButton slider(int left) {
        return new Slider(left, 0);
    }
}

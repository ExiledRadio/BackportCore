package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import java.lang.reflect.Field;

/**
 * 26.1's Exclusive Fullscreen turned off (26.3's default): Fullscreen is a window without a frame, the size of the
 * monitor, at its top left corner, instead of 1.12.2's change of the monitor's mode. Patch P42 starts
 * {@code Minecraft.toggleFullscreen} with {@code if (BorderlessFullscreen.borderlessToggle(this)) return;}.
 *
 * <p>LWJGL 2 makes a window undecorated only when it makes one ({@code org.lwjgl.opengl.Window.undecorated}, read by
 * each platform's {@code createWindow}). {@code Display.setDisplayMode} with another size destroys the native window and
 * makes a new one, keeping the GL context, so Core sets the property and the monitor's size together, and puts both
 * back on the way out. Nothing changes the monitor's own mode, so leaving is instant and other windows keep their
 * places. Where Cleanroom's LWJGL 3 draws the window, the option is not shown and the toggle is 1.12.2's.
 */
@SideOnly(Side.CLIENT)
public final class BorderlessFullscreen {

    /** The patch's id. */
    public static final String PATCH = "P42";
    static final String UNDECORATED = "org.lwjgl.opengl.Window.undecorated";

    private static Field fullscreen;
    private static Field tempWidth;
    private static Field tempHeight;
    /** True while the game is in Core's borderless window (leaving it is then Core's too). */
    private static boolean active;
    private static int windowX = -1;
    private static int windowY = -1;
    /** For the self-test: borderless windows entered and left. */
    public static volatile int entered;
    public static volatile int left;

    private BorderlessFullscreen() {
    }

    /** @return true while Fullscreen is to be borderless: from 26.1 with Exclusive Fullscreen off, where Core can do it */
    public static boolean wanted() {
        return VanillaRevisions.exclusiveFullscreenOption() && Presentation.EXCLUSIVE_FULLSCREEN.isShown()
                && !Presentation.EXCLUSIVE_FULLSCREEN.get() && "applied".equals(Asm.state(PATCH));
    }

    /** @return true while the game is in Core's borderless window */
    public static boolean active() {
        return active;
    }

    /**
     * P42: the head of {@code Minecraft.toggleFullscreen}. Going fullscreen when {@link #wanted()}, or leaving Core's
     * borderless window, is done here and true is returned; anything else (1.12.2's exclusive fullscreen, in and
     * out) returns false and vanilla's method runs.
     */
    public static boolean borderlessToggle(Minecraft mc) {
        return toggle(mc, wanted());
    }

    /**
     * Toggles fullscreen the borderless way when {@code borderless} (or when leaving Core's borderless window), and
     * returns true; otherwise returns false and leaves the toggle to vanilla's {@code toggleFullscreen}.
     */
    public static boolean toggle(Minecraft mc, boolean borderless) {
        try {
            if (!mc.isFullScreen()) {
                if (!borderless) {
                    // 1.12.2's exclusive fullscreen, at the mode Fullscreen Resolution names (else vanilla's, the desktop's).
                    DisplayMode mode = FullscreenModes.chosen();
                    if (mode == null) {
                        return false;
                    }
                    FullscreenModes.enter(mc, mode);
                    return true;
                }
                enter(mc);
                return true;
            }
            if (!active) {
                return false;
            }
            leave(mc);
            return true;
        } catch (Exception e) {
            BackportCore.LOGGER.error("Couldn't toggle borderless fullscreen", e);
            System.setProperty(UNDECORATED, "false");
            active = false;
            return true;
        }
    }

    /**
     * Exclusive Fullscreen changed while the game is fullscreen: out with the old kind and in with the new, as 26.1's
     * option does at once.
     */
    public static void optionChanged(Minecraft mc) {
        if (mc.isFullScreen()) {
            mc.toggleFullscreen();
            mc.toggleFullscreen();
        }
    }

    private static void enter(Minecraft mc) throws Exception {
        DisplayMode desktop = Display.getDesktopDisplayMode();
        int w = Math.max(1, desktop.getWidth());
        int h = Math.max(1, desktop.getHeight());
        windowX = Display.getX();
        windowY = Display.getY();
        setFullscreenField(mc, true);
        mc.gameSettings.fullScreen = true;
        System.setProperty(UNDECORATED, "true");
        // A frame on a window without a border: 1.12.2's resizable style would draw one.
        Display.setResizable(false);
        Display.setLocation(0, 0);
        remake(w, h);
        active = true;
        finish(mc, w, h);
        entered++;
        BackportCore.LOGGER.info("Fullscreen: borderless window {}x{} (Exclusive Fullscreen off)", w, h);
    }

    private static void leave(Minecraft mc) throws Exception {
        int w = Math.max(1, field(tempWidth(), mc));
        int h = Math.max(1, field(tempHeight(), mc));
        setFullscreenField(mc, false);
        mc.gameSettings.fullScreen = false;
        System.setProperty(UNDECORATED, "false");
        Display.setLocation(windowX, windowY);
        remake(w, h);
        active = false;
        // Forge's fix for MC-68754 (vanilla's toggleFullscreen does the same on the way out).
        Display.setResizable(false);
        Display.setResizable(true);
        finish(mc, w, h);
        left++;
        BackportCore.LOGGER.info("Fullscreen: back to a {}x{} window", w, h);
    }

    /**
     * Sets the window to {@code w} by {@code h}, making a new native window: LWJGL makes one only when the size (or
     * fullscreen) changes, so a window already that size is first made one pixel shorter.
     */
    private static void remake(int w, int h) throws Exception {
        DisplayMode target = new DisplayMode(w, h);
        if (target.equals(Display.getDisplayMode())) {
            Display.setDisplayMode(new DisplayMode(w, Math.max(1, h - 1)));
        }
        Display.setDisplayMode(target);
    }

    /** What vanilla's {@code toggleFullscreen} does after the mode: the game's size, the screen, VSync, a frame. */
    static void finish(Minecraft mc, int w, int h) {
        mc.resize(w, h);
        Display.setVSyncEnabled(mc.gameSettings.enableVsync);
        mc.updateDisplay();
    }

    static void setFullscreenField(Minecraft mc, boolean on) throws Exception {
        if (fullscreen == null) {
            fullscreen = ReflectionHelper.findField(Minecraft.class, "fullscreen", "field_71431_Q");
        }
        fullscreen.setBoolean(mc, on);
    }

    private static Field tempWidth() {
        if (tempWidth == null) {
            tempWidth = ReflectionHelper.findField(Minecraft.class, "tempDisplayWidth", "field_71436_X");
        }
        return tempWidth;
    }

    private static Field tempHeight() {
        if (tempHeight == null) {
            tempHeight = ReflectionHelper.findField(Minecraft.class, "tempDisplayHeight", "field_71435_Y");
        }
        return tempHeight;
    }

    private static int field(Field f, Minecraft mc) throws Exception {
        return f.getInt(mc);
    }
}

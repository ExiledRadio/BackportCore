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
 * places. Where Cleanroom's LWJGL 3 draws the window, the same window comes from lwjglx's own
 * {@code Display.setBorderless} (GLFW's windowed mode over the monitor: no mode change, so no black flash, no windows
 * moved to other monitors, and the game stays up when another window takes focus); an lwjglx without it leaves the
 * option hidden and the toggle 1.12.2's.
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
        if (VanillaRevisions.lwjglxBorderless() != null) {
            int[] monitor = Glfw.monitorRect();
            setFullscreenField(mc, true);
            mc.gameSettings.fullScreen = true;
            Glfw.enterBorderless(monitor);
            optifineFullscreenChecked();
            active = true;
            int[] fb = Glfw.framebufferSize();
            finish(mc, fb[0], fb[1]);
            entered++;
            BackportCore.LOGGER.info("Fullscreen: borderless window {}x{} at {},{} through GLFW (Exclusive Fullscreen off; framebuffer {}x{})",
                    monitor[2], monitor[3], monitor[0], monitor[1], fb[0], fb[1]);
            return;
        }
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
        optifineFullscreenChecked();
        active = true;
        finish(mc, w, h);
        entered++;
        BackportCore.LOGGER.info("Fullscreen: borderless window {}x{} (Exclusive Fullscreen off)", w, h);
    }

    private static void leave(Minecraft mc) throws Exception {
        int w = Math.max(1, field(tempWidth(), mc));
        int h = Math.max(1, field(tempHeight(), mc));
        if (VanillaRevisions.lwjglxBorderless() != null) {
            setFullscreenField(mc, false);
            mc.gameSettings.fullScreen = false;
            // The window's own frame, place and size, saved on the way in.
            Glfw.leaveBorderless();
            active = false;
            int[] fb = Glfw.framebufferSize();
            finish(mc, fb[0], fb[1]);
            left++;
            BackportCore.LOGGER.info("Fullscreen: back to a window (framebuffer {}x{})", fb[0], fb[1]);
            return;
        }
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
     * OptiFine's {@code Config.checkDisplayMode} runs each frame and, the first frame the game is fullscreen, makes it
     * exclusive fullscreen at its own Fullscreen Mode ({@code Display.setDisplayMode}, {@code Display.setFullscreen(true)}),
     * which replaced the borderless window (and an exclusive window minimizes when another window takes focus). Its
     * check is marked done here, as it marks it itself; leaving fullscreen resets it as before.
     */
    private static void optifineFullscreenChecked() {
        if (!net.minecraftforge.fml.client.FMLClientHandler.instance().hasOptifine()) {
            return;
        }
        try {
            Class<?> config = Class.forName("Config", false, BorderlessFullscreen.class.getClassLoader());
            Field checked = config.getDeclaredField("fullscreenModeChecked");
            checked.setAccessible(true);
            checked.setBoolean(null, true);
            Field desktop = config.getDeclaredField("desktopModeChecked");
            desktop.setAccessible(true);
            desktop.setBoolean(null, false);
        } catch (ReflectiveOperationException | RuntimeException e) {
            BackportCore.LOGGER.warn("Couldn't keep OptiFine from making the borderless window exclusive fullscreen", e);
        }
    }

    /**
     * Cleanroom (lwjglx over GLFW): every frame the game's size follows the window's real framebuffer. GLFW resizes a
     * window through the system's messages, so the size a toggle reads can still be the old one; 1.12.2's own
     * {@code checkWindowResize} only looks while not fullscreen.
     */
    public static final class Events {
        private int frames;
        private boolean syncFailed;

        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public void onRender(net.minecraftforge.fml.common.gameevent.TickEvent.RenderTickEvent event) {
            if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START || VanillaRevisions.lwjglxBorderless() == null
                    || (!active && left == 0)) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            try {
                if (active && ++this.frames % 20 == 0) {
                    Glfw.keepPlaced();
                }
                int[] fb = Glfw.framebufferSize();
                if (fb[0] > 0 && fb[1] > 0 && (fb[0] != mc.displayWidth || fb[1] != mc.displayHeight)) {
                    mc.resize(fb[0], fb[1]);
                }
                if (fb[0] > 0 && fb[1] > 0) {
                    try {
                        Glfw.syncLwjglxSize(fb);
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        if (!this.syncFailed) {
                            this.syncFailed = true;
                            BackportCore.LOGGER.warn("Fullscreen: couldn't keep lwjglx's stored size in step; the mouse may be offset", e);
                        }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                // Leave the size to the game.
            }
        }
    }

    /**
     * 26.3's borderless window on GLFW (its SDL3 desktop fullscreen): the window without its frame, no monitor attached
     * (so nothing changes the monitor's mode and nothing minimizes on focus loss), over the monitor it is on, one pixel
     * taller on Windows (Cleanroom's own fix: some drivers treat a window exactly the monitor's size as exclusive
     * fullscreen, which flickers when it loses focus). Reflection, since Core compiles against LWJGL 2.
     */
    static final class Glfw {
        private static final int DECORATED = 0x00020005, DONT_CARE = -1;
        private static int[] saved;

        private static Class<?> glfw() throws ClassNotFoundException {
            return Class.forName("org.lwjgl.glfw.GLFW", true, BorderlessFullscreen.class.getClassLoader());
        }

        static long window() throws ReflectiveOperationException {
            // Display.class, not the name: Cleanroom redirects this reference to the lwjglx class the game uses, where
            // a lookup by name can load a second, empty copy (window 0).
            Class<?> display = Display.class;
            long window = (Long) display.getMethod("getWindow").invoke(null);
            if (window == 0L) {
                throw new IllegalStateException("no GLFW window from " + display.getName());
            }
            return window;
        }

        /** @return x, y, width, height of the monitor the window is on (lwjglx's choice for fullscreen) */
        static int[] monitorRect() throws ReflectiveOperationException {
            // Display.class, not the name: Cleanroom redirects this reference to the lwjglx class the game uses, where
            // a lookup by name can load a second, empty copy (window 0).
            Class<?> display = Display.class;
            Object target = display.getMethod("getTargetFullscreenMonitor").invoke(null);
            Object mode = target.getClass().getMethod("vidMode").invoke(target);
            int x = ((Number) target.getClass().getMethod("x").invoke(target)).intValue();
            int y = ((Number) target.getClass().getMethod("y").invoke(target)).intValue();
            int w = ((Number) mode.getClass().getMethod("width").invoke(mode)).intValue();
            int h = ((Number) mode.getClass().getMethod("height").invoke(mode)).intValue();
            return new int[] {x, y, Math.max(1, w), Math.max(1, h)};
        }

        /** The window's place while borderless: x, y, width, height (height one more on Windows). */
        private static int[] placed;

        static void enterBorderless(int[] monitor) throws ReflectiveOperationException {
            Class<?> g = glfw();
            long window = window();
            int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
            g.getMethod("glfwGetWindowPos", long.class, int[].class, int[].class).invoke(null, window, x, y);
            g.getMethod("glfwGetWindowSize", long.class, int[].class, int[].class).invoke(null, window, w, h);
            saved = new int[] {x[0], y[0], w[0], h[0]};
            int extra = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? 1 : 0;
            placed = new int[] {monitor[0], monitor[1], monitor[2], monitor[3] + extra};
            place(g, window);
        }

        private static void place(Class<?> g, long window) throws ReflectiveOperationException {
            g.getMethod("glfwSetWindowAttrib", long.class, int.class, int.class).invoke(null, window, DECORATED, 0);
            g.getMethod("glfwSetWindowMonitor", long.class, long.class, int.class, int.class, int.class, int.class, int.class)
                    .invoke(null, window, 0L, placed[0], placed[1], placed[2], placed[3], DONT_CARE);
        }

        /**
         * A game started fullscreen toggles while its window is still being made; the launch then shows and places the
         * window, giving back its frame. Checked while borderless: framed again, or moved or resized, it is placed
         * again (what a second F11 did by hand).
         *
         * @return true if the window had to be placed again
         */
        static boolean keepPlaced() throws ReflectiveOperationException {
            if (placed == null) {
                return false;
            }
            Class<?> g = glfw();
            long window = window();
            int decorated = (Integer) g.getMethod("glfwGetWindowAttrib", long.class, int.class).invoke(null, window, DECORATED);
            int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
            g.getMethod("glfwGetWindowPos", long.class, int[].class, int[].class).invoke(null, window, x, y);
            g.getMethod("glfwGetWindowSize", long.class, int[].class, int[].class).invoke(null, window, w, h);
            if (decorated == 0 && x[0] == placed[0] && y[0] == placed[1] && w[0] == placed[2] && h[0] == placed[3]) {
                return false;
            }
            BackportCore.LOGGER.info("Fullscreen: the borderless window was changed (frame {}, {},{} {}x{}); placing it again", decorated,
                    x[0], y[0], w[0], h[0]);
            place(g, window);
            return true;
        }

        static void leaveBorderless() throws ReflectiveOperationException {
            Class<?> g = glfw();
            long window = window();
            g.getMethod("glfwSetWindowAttrib", long.class, int.class, int.class).invoke(null, window, DECORATED, 1);
            int[] s = saved != null ? saved : new int[] {100, 100, 854, 480};
            g.getMethod("glfwSetWindowMonitor", long.class, long.class, int.class, int.class, int.class, int.class, int.class)
                    .invoke(null, window, 0L, s[0], s[1], s[2], s[3], DONT_CARE);
        }

        private static java.lang.reflect.Field fbWidth, fbHeight;
        private static boolean loggedStale;

        /**
         * lwjglx's mouse turns GLFW's cursor into 1.12.2's bottom-up Y with {@code Display.getHeight()}, its stored
         * framebuffer height ({@code displayFramebufferHeight}, set by its framebuffer-size callback). A game started
         * fullscreen goes borderless while its window is still being made, and that stored size kept the first window's,
         * so the mouse sat far below the cursor until a second F11. Kept equal to GLFW's framebuffer every frame.
         */
        static void syncLwjglxSize(int[] fb) throws ReflectiveOperationException {
            if (fbWidth == null) {
                fbWidth = Display.class.getDeclaredField("displayFramebufferWidth");
                fbHeight = Display.class.getDeclaredField("displayFramebufferHeight");
                fbWidth.setAccessible(true);
                fbHeight.setAccessible(true);
            }
            int w = fbWidth.getInt(null), h = fbHeight.getInt(null);
            if (w == fb[0] && h == fb[1]) {
                return;
            }
            if (!loggedStale) {
                loggedStale = true;
                BackportCore.LOGGER.info("Fullscreen: lwjglx's stored size was {}x{}, the framebuffer is {}x{}; the mouse follows the framebuffer",
                        w, h, fb[0], fb[1]);
            }
            fbWidth.setInt(null, fb[0]);
            fbHeight.setInt(null, fb[1]);
        }

        static int[] framebufferSize() throws ReflectiveOperationException {
            int[] w = new int[1], h = new int[1];
            glfw().getMethod("glfwGetFramebufferSize", long.class, int[].class, int[].class).invoke(null, window(), w, h);
            return new int[] {w[0], h[0]};
        }
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

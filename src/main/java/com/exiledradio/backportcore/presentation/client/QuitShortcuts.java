package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

/**
 * 26.3's Quit Shortcuts ({@link Presentation#QUIT_SHORTCUTS}): what patch P51 calls. 26.3's {@code Window.onQuitRequested}
 * ignores a quit while the option is off and {@code InputQuirks.isQuitShortcutDown()}: Alt and F4 held (Windows and
 * Linux), Cmd and Q held (macOS). 1.12.2's window reports Alt + F4 and its close button alike
 * ({@code Display.isCloseRequested()}), so the keys decide here too, read from the keyboard state the same frame's
 * {@code Display.update} left:
 * <ul>
 * <li>LWJGL 2 (Windows): the window procedure sees F4 go down ({@code WM_SYSKEYDOWN}) before Windows turns it into
 * {@code WM_CLOSE}, and {@code isCloseRequested()} clears its flag when read, so an ignored request is gone.</li>
 * <li>Cleanroom's lwjglx: {@code isCloseRequested()} is GLFW's {@code glfwWindowShouldClose}, which stays set, so an
 * ignored request is cleared with {@code glfwSetWindowShouldClose(window, false)}.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class QuitShortcuts {

    /** For the client self-test: quits ignored. */
    public static volatile int ignored;
    private static boolean clearFailed;

    private QuitShortcuts() {
    }

    /** P51: {@code Display.isCloseRequested()}'s answer in {@code Minecraft.runGameLoop}. */
    public static boolean closeRequested(boolean requested) {
        if (!requested || !Presentation.QUIT_SHORTCUTS.isShown() || Presentation.QUIT_SHORTCUTS.get() || !shortcutDown()) {
            return requested;
        }
        ignored++;
        clearGlfw();
        BackportCore.LOGGER.info("Quit Shortcuts is off: {} ignored", VanillaRevisions.macOs() ? "Cmd + Q" : "Alt + F4");
        return false;
    }

    /** 26.3's {@code InputQuirks.isQuitShortcutDown()} in 1.12.2's key codes. */
    static boolean shortcutDown() {
        if (VanillaRevisions.macOs()) {
            return (Keyboard.isKeyDown(Keyboard.KEY_LMETA) || Keyboard.isKeyDown(Keyboard.KEY_RMETA)) && Keyboard.isKeyDown(Keyboard.KEY_Q);
        }
        return (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) && Keyboard.isKeyDown(Keyboard.KEY_F4);
    }

    /** Lets GLFW's "should close" go again where lwjglx draws the window (nothing to do on LWJGL 2). */
    private static void clearGlfw() {
        if (clearFailed) {
            return;
        }
        java.lang.reflect.Method getWindow;
        try {
            // Display.class, not the name: Cleanroom redirects this reference to the lwjglx class the game uses.
            getWindow = Display.class.getMethod("getWindow");
        } catch (NoSuchMethodException e) {
            return; // LWJGL 2: the flag is already gone.
        }
        try {
            long window = (Long) getWindow.invoke(null);
            Class.forName("org.lwjgl.glfw.GLFW", true, QuitShortcuts.class.getClassLoader())
                    .getMethod("glfwSetWindowShouldClose", long.class, boolean.class).invoke(null, window, false);
        } catch (ReflectiveOperationException | RuntimeException e) {
            clearFailed = true;
            BackportCore.LOGGER.warn("Quit Shortcuts: could not clear GLFW's close request ({}); the game will quit", e.toString());
        }
    }
}

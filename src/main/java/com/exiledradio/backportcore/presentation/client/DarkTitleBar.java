package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 26.3's window title bar on Windows: 26.3 makes its window through SDL3, which gives the title bar Windows' dark mode
 * when Windows' app mode is dark ({@code AppsUseLightTheme} 0) and follows it when it changes. 26.2 and before (GLFW)
 * keep the light one, as does 1.12.2. Set with {@code DwmSetWindowAttribute(DWMWA_USE_IMMERSIVE_DARK_MODE)} through
 * JNA's core classes (1.12.2's JNA 4.4 and Cleanroom's 5.x alike), on the window LWJGL 2 or lwjglx made. Checked once a
 * second, so a new window (borderless fullscreen remakes it on LWJGL 2) or a theme change is picked up.
 */
@SideOnly(Side.CLIENT)
public final class DarkTitleBar {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    /** {@code DWMWA_USE_IMMERSIVE_DARK_MODE}: 20 from Windows 10 20H1, 19 before. */
    private static final int ATTRIBUTE = 20, ATTRIBUTE_OLD = 19;
    /** {@code HKEY_CURRENT_USER}, sign-extended as the handle is on 64-bit Windows. */
    private static final long HKCU = (long) (int) 0x80000001;
    private static final int RRF_RT_REG_DWORD = 0x10;
    private static final int SWP_FLAGS = 0x0001 | 0x0002 | 0x0004 | 0x0010 | 0x0020;

    private long appliedHwnd;
    private int applied = -1;
    private int ticks;
    private boolean broken;
    private int noWindow;
    /** For the client self-test: the last value set (1 dark, 0 light, -1 none). */
    public static volatile int lastSet = -1;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (!WINDOWS || broken || event.phase != TickEvent.Phase.END || ++this.ticks % 20 != 1) {
            return;
        }
        try {
            long hwnd = hwnd();
            if (hwnd == 0L) {
                if (++this.noWindow == 10) {
                    BackportCore.LOGGER.info("Window title bar: no window handle found ({})", lwjglxWindow != null ? "lwjglx" : "LWJGL 2");
                }
                return;
            }
            int want = VanillaRevisions.darkTitleBar() && systemDark() ? 1 : 0;
            if (hwnd == this.appliedHwnd && want == this.applied) {
                return;
            }
            // A window Core never darkened is light already: nothing to set.
            if (want == 0 && hwnd != this.appliedHwnd) {
                if (this.appliedHwnd == 0L) {
                    BackportCore.LOGGER.info("Window title bar: light (26.3 look: {}, Windows dark app mode: {})", VanillaRevisions.darkTitleBar(),
                            systemDark());
                }
                this.appliedHwnd = hwnd;
                this.applied = 0;
                return;
            }
            set(hwnd, want);
            this.appliedHwnd = hwnd;
            this.applied = want;
            lastSet = want;
            BackportCore.LOGGER.info("Window title bar: {} (Windows app mode)", want == 1 ? "dark" : "light");
        } catch (Throwable t) {
            this.broken = true;
            BackportCore.LOGGER.warn("Couldn't set the window's dark title bar; it stays as it is.", t);
        }
    }

    private static void set(long hwnd, int dark) {
        Pointer window = new Pointer(hwnd);
        Function attribute = NativeLibrary.getInstance("dwmapi").getFunction("DwmSetWindowAttribute");
        IntByReference value = new IntByReference(dark);
        if (attribute.invokeInt(new Object[] {window, ATTRIBUTE, value, 4}) != 0) {
            attribute.invokeInt(new Object[] {window, ATTRIBUTE_OLD, value, 4});
        }
        // Repaint the frame now rather than at the next activation.
        NativeLibrary.getInstance("user32").getFunction("SetWindowPos")
                .invokeInt(new Object[] {window, null, 0, 0, 0, 0, SWP_FLAGS});
    }

    /** @return true when Windows' app mode is dark ({@code AppsUseLightTheme} is 0) */
    static boolean systemDark() {
        Function get = NativeLibrary.getInstance("advapi32").getFunction("RegGetValueW");
        IntByReference data = new IntByReference(1);
        IntByReference size = new IntByReference(4);
        int r = get.invokeInt(new Object[] {new Pointer(HKCU), new WString("Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"),
                new WString("AppsUseLightTheme"), RRF_RT_REG_DWORD, null, data, size});
        return r == 0 && data.getValue() == 0;
    }

    private static Method glfwWindow;
    private static Method lwjglxWindow;
    private static Field displayImpl;
    private static Method getHwnd;

    /** @return the game window's HWND: lwjglx's GLFW window on Cleanroom, LWJGL 2's {@code WindowsDisplay} otherwise; 0 if none yet */
    static long hwnd() throws ReflectiveOperationException {
        ClassLoader loader = DarkTitleBar.class.getClassLoader();
        if (VanillaRevisions.lwjglxBorderless() != null || lwjglxWindow != null) {
            if (lwjglxWindow == null) {
                lwjglxWindow = org.lwjgl.opengl.Display.class.getMethod("getWindow");
                glfwWindow = Class.forName("org.lwjgl.glfw.GLFWNativeWin32", true, loader).getMethod("glfwGetWin32Window", long.class);
            }
            long window = (Long) lwjglxWindow.invoke(null);
            return window == 0L ? 0L : (Long) glfwWindow.invoke(null, window);
        }
        if (displayImpl == null) {
            displayImpl = Class.forName("org.lwjgl.opengl.Display", false, loader).getDeclaredField("display_impl");
            displayImpl.setAccessible(true);
        }
        Object impl = displayImpl.get(null);
        if (impl == null) {
            return 0L;
        }
        if (getHwnd == null) {
            getHwnd = impl.getClass().getDeclaredMethod("getHwnd");
            getHwnd.setAccessible(true);
        }
        return (Long) getHwnd.invoke(impl);
    }
}

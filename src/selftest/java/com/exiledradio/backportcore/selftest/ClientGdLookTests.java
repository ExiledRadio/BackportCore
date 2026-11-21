package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.client.BorderlessFullscreen;
import com.exiledradio.backportcore.presentation.client.DebugOptionsScreen;
import com.exiledradio.backportcore.presentation.client.DebugScale;
import com.exiledradio.backportcore.presentation.option.OptionStore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * Core 1.4.4's answers to Game Drops Backport's look-and-feel requests, in the dev client: P42 and a real borderless
 * window (in and out again), the debug overlay's own GUI Scale (the arithmetic, the slider), and both left as 1.12.2's
 * below 26.1 and 26.3, where the dummy versions stop.
 */
@SideOnly(Side.CLIENT)
final class ClientGdLookTests {

    private ClientGdLookTests() {
    }

    static void title(Minecraft mc, GuiScreen menu) throws Exception {
        fullscreen(mc);
        debugScale(mc);
        mc.displayGuiScreen(menu);
    }

    private static void fullscreen(Minecraft mc) throws Exception {
        check("client: P42 applied to Minecraft.toggleFullscreen", "applied".equals(Asm.state("P42")), Asm.state("P42"));
        check("client: LWJGL 2 here, so the borderless window is possible", VanillaRevisions.borderlessPossible());
        check("client: below 26.1 no borderless: Fullscreen stays 1.12.2's", !BorderlessFullscreen.wanted()
                && !Presentation.EXCLUSIVE_FULLSCREEN.isShown());
        if (mc.isFullScreen()) {
            check("client: the borderless round trip needs a window (the game is fullscreen; skipped)", true);
            return;
        }
        check("client: windowed, a toggle that is not borderless is vanilla's", !BorderlessFullscreen.toggle(mc, false));
        int w0 = Display.getWidth();
        int h0 = Display.getHeight();
        DisplayMode desktop = Display.getDesktopDisplayMode();
        int entered = BorderlessFullscreen.entered;
        boolean in = BorderlessFullscreen.toggle(mc, true);
        check("client: borderless: in, the game fullscreen, LWJGL not exclusive", in && mc.isFullScreen() && mc.gameSettings.fullScreen
                && BorderlessFullscreen.active() && !Display.isFullscreen() && BorderlessFullscreen.entered == entered + 1,
                in + " " + mc.isFullScreen() + " " + Display.isFullscreen());
        check("client: borderless: an undecorated window the monitor's size at its corner",
                "true".equals(System.getProperty("org.lwjgl.opengl.Window.undecorated"))
                        && Display.getWidth() == desktop.getWidth() && Display.getHeight() == desktop.getHeight()
                        && mc.displayWidth == desktop.getWidth() && Display.getX() == 0 && Display.getY() == 0 && !Display.isResizable(),
                Display.getWidth() + "x" + Display.getHeight() + " at " + Display.getX() + "," + Display.getY() + " of " + desktop);
        boolean out = BorderlessFullscreen.toggle(mc, true);
        check("client: borderless: out again to a decorated, resizable window", out && !mc.isFullScreen() && !mc.gameSettings.fullScreen
                && !BorderlessFullscreen.active() && "false".equals(System.getProperty("org.lwjgl.opengl.Window.undecorated"))
                && Display.isResizable(), out + " " + mc.isFullScreen());
        check("client: the window has the game's size again", Display.getWidth() == mc.displayWidth && Display.getHeight() == mc.displayHeight,
                Display.getWidth() + "x" + Display.getHeight() + " was " + w0 + "x" + h0 + ", game " + mc.displayWidth + "x" + mc.displayHeight);
        mc.gameSettings.saveOptions();
    }

    private static void debugScale(Minecraft mc) throws Exception {
        check("client: the overlay's scale: 1080p fits 4, Auto draws F3 at 2",
                DebugScale.scaleFor(0, 1920, 1080, false) == 4 && DebugScale.overlayScale(DebugScale.AUTO, 4, 1920, 1080, false) == 2);
        check("client: Unchanged keeps the game's, a set scale is used", DebugScale.overlayScale(DebugScale.UNCHANGED, 4, 1920, 1080, false) == 4
                && DebugScale.overlayScale(3, 4, 1920, 1080, false) == 3 && DebugScale.overlayScale(1, 4, 1920, 1080, false) == 1);
        check("client: never larger than the game's", DebugScale.overlayScale(4, 2, 1920, 1080, false) == 2
                && DebugScale.overlayScale(DebugScale.AUTO, 1, 1920, 1080, false) == 1);
        check("client: a small window: Auto at 1 under the game's 2", DebugScale.overlayScale(DebugScale.AUTO, 2, 854, 480, false) == 1,
                DebugScale.overlayScale(DebugScale.AUTO, 2, 854, 480, false));
        check("client: 1.12.2's Unicode rule (an even scale)", DebugScale.scaleFor(3, 1920, 1080, true) == 2);
        check("client: below 26.3 F3 is drawn at the game's scale", !VanillaRevisions.debugGuiScale() && DebugScale.now(mc) == 0);
        boolean saved = OptionStore.get(DebugScale.KEY) != null;
        int before = DebugScale.chosen();
        check("client: GUI Scale is Auto until chosen", saved || before == DebugScale.AUTO, before);
        check("client: its labels", "GUI Scale: Unchanged".equals(DebugOptionsScreen.scaleLabel(-1))
                && "GUI Scale: Auto".equals(DebugOptionsScreen.scaleLabel(0)) && "GUI Scale: 2".equals(DebugOptionsScreen.scaleLabel(2)),
                DebugOptionsScreen.scaleLabel(-1) + " / " + DebugOptionsScreen.scaleLabel(0));
        DebugOptionsScreen screen = new DebugOptionsScreen();
        mc.displayGuiScreen(screen);
        check("client: below 26.3 Debug Options has no GUI Scale", screen.scaleSlider() == null);
        Class<?> cls = Class.forName(DebugOptionsScreen.class.getName() + "$ScaleSlider");
        Constructor<?> make = cls.getDeclaredConstructor(DebugOptionsScreen.class, int.class, int.class);
        make.setAccessible(true);
        GuiButton slider = (GuiButton) make.newInstance(screen, 0, 0);
        Method set = cls.getDeclaredMethod("setFraction", double.class);
        set.setAccessible(true);
        int max = DebugScale.max(mc);
        set.invoke(slider, 0.0);
        boolean unchanged = DebugScale.chosen() == DebugScale.UNCHANGED && slider.displayString.endsWith("Unchanged");
        set.invoke(slider, 1.0);
        boolean top = DebugScale.chosen() == max && slider.displayString.endsWith(": " + max);
        set.invoke(slider, 1.0 / (max + 1));
        boolean auto = DebugScale.chosen() == DebugScale.AUTO && slider.displayString.endsWith("Auto");
        check("client: the slider runs Unchanged, Auto, 1 ... the largest scale, 110 wide", unchanged && top && auto && slider.width == 110,
                unchanged + " " + top + " " + auto + " max " + max);
        DebugScale.choose(before);
        screen.drawScreen(0, 0, 0.0F);
    }
}

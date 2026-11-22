package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.client.AccessibilityOnboarding;
import com.exiledradio.backportcore.presentation.client.HighContrastPack;
import com.exiledradio.backportcore.presentation.client.NotificationTime;
import com.exiledradio.backportcore.presentation.client.OptionWidgets;
import com.exiledradio.backportcore.presentation.client.OptionsListScreen;
import com.exiledradio.backportcore.presentation.client.QuitShortcuts;
import com.exiledradio.backportcore.presentation.client.SoundDevices;
import com.exiledradio.backportcore.presentation.option.OptionStore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiOptionButton;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenOptionsSounds;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * The menu shots' part for Core 1.4.5's options rows (docs/SCREEN_AUDIT.md items 12, 14, 15, 16, 22): the accessibility
 * onboarding on the first title screen, Accessibility Settings (High Contrast, Notification Time, Rotate With Minecarts,
 * 26.3's order), Mouse Settings without Touchscreen (26.2), Controls' Quit Shortcuts, Music and Sounds' Device and
 * Directional Audio, the High Contrast pack, and patches P50 to P52. Checks of a later version's rows run only when the
 * newest installed version has them (Game Drops' dev jar on {@code -PwithMods=} for 26.3).
 */
@SideOnly(Side.CLIENT)
final class OptionsRowsShots {

    private OptionsRowsShots() {
    }

    /** From the self-test mod's construction: the onboarding is pending only for the menu shots. */
    static void onboardingWanted(boolean wanted) {
        OptionStore.put(AccessibilityOnboarding.STORE_KEY, wanted ? "true" : "false");
    }

    static boolean onboarding(Minecraft mc) {
        return mc.currentScreen instanceof AccessibilityOnboarding.Screen;
    }

    /** The first steps: the onboarding, then on to the title screen. */
    static void first(ClientMenuShots s, Minecraft mc) {
        s.add("onboarding", () -> {
            boolean due = VanillaRevisions.accessibilityOnboarding(Presentation.newest(LookArea.OPTIONS));
            s.check("1.19.4: the first title screen is the accessibility onboarding", onboarding(mc) == due,
                    mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getName());
            s.check("the onboarding opened once", AccessibilityOnboarding.shown == (due ? 1 : 0), AccessibilityOnboarding.shown);
            return true;
        });
        s.add(null, () -> {
            mc.displayGuiScreen(new GuiMainMenu());
            // 26.3's onboarding is over the blurred panorama; the title screen's own check counts from here.
            com.exiledradio.backportcore.presentation.client.MenuBackground.menuFrames = 0;
            s.check("leaving the onboarding ends it (saved)", !AccessibilityOnboarding.pending(), OptionStore.get(AccessibilityOnboarding.STORE_KEY));
            return true;
        });
    }

    static void steps(ClientMenuShots s, Minecraft mc) {
        s.add(null, () -> {
            try {
                // The toast instance class loads with the first toast: load it now, through the transformers.
                Class.forName("net.minecraft.client.gui.toasts.GuiToast$ToastInstance", true, Minecraft.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                s.failed("load GuiToast$ToastInstance", e);
            }
            for (String p : new String[] {"P50-toasts", "P50-subtitles", "P50-highlight", "P51", "P52"}) {
                s.check(p + " applied", "applied".equals(Asm.state(p)), Asm.state(p));
            }
            boolean shown = Presentation.NOTIFICATION_TIME.isShown();
            s.check("Notification Time shows from 1.19.4", shown == Presentation.isAtLeast(LookArea.OPTIONS, "1.19.4"), shown);
            Presentation.NOTIFICATION_TIME.set(2.0);
            if (shown) {
                s.check("Notification Time 2x: a toast's time runs at half speed", NotificationTime.toastTime(10000L) == 5000L,
                        NotificationTime.toastTime(10000L));
                s.check("Notification Time 2x: subtitles stay 6 s", NotificationTime.subtitleTime(3000L) == 6000L
                        && NotificationTime.subtitleFade(3000.0F) == 6000.0F, NotificationTime.subtitleTime(3000L));
                s.check("Notification Time 2x: the held item's name stays 80 ticks", NotificationTime.highlightTicks(40) == 80,
                        NotificationTime.highlightTicks(40));
            }
            Presentation.NOTIFICATION_TIME.set(1.0);
            s.check("Notification Time 1x: vanilla's times", NotificationTime.toastTime(1234L) == 1234L && NotificationTime.highlightTicks(40) == 40);
            Presentation.QUIT_SHORTCUTS.set(false);
            s.check("Quit Shortcuts off: the close button still closes (no Alt + F4 held)", QuitShortcuts.closeRequested(true)
                    && !QuitShortcuts.closeRequested(false));
            Presentation.QUIT_SHORTCUTS.set(true);
            List<String> devices = SoundDevices.devices();
            BackportCore.LOGGER.info("MENU sound devices: {}", devices);
            s.check("OpenAL lists at least one device", !devices.isEmpty(), devices);
            return true;
        });
        s.add("accessibility", () -> show(mc, OptionsListScreen.accessibility(new GuiMainMenu())));
        s.add(null, () -> {
            List<String> ids = ids(mc.currentScreen);
            BackportCore.LOGGER.info("MENU accessibility rows: {}", ids);
            boolean v1194 = Presentation.isAtLeast(LookArea.OPTIONS, "1.19.4");
            s.check("Accessibility: High Contrast and Notification Time from 1.19.4",
                    ids.contains(Presentation.HIGH_CONTRAST.id()) == v1194 && ids.contains(Presentation.NOTIFICATION_TIME.id()) == v1194, ids);
            GuiButton hc = button(mc.currentScreen, Presentation.HIGH_CONTRAST.id());
            if (hc != null) {
                s.check("High Contrast is greyed out exactly when no version has the pack", hc.enabled == (Presentation.highContrastPack() != null),
                        Presentation.highContrastPack());
            }
            boolean v1213 = Presentation.isAtLeast(LookArea.OPTIONS, "1.21.3");
            GuiButton rotate = button(mc.currentScreen, Presentation.ROTATE_WITH_MINECART.id());
            s.check("Accessibility: Rotate With Minecarts from 1.21.3, greyed out (no Minecart Improvements)",
                    (rotate != null) == v1213 && (rotate == null || !rotate.enabled), ids);
            if (VanillaRevisions.controlsLeaveAccessibility(Presentation.newest(LookArea.OPTIONS))) {
                int subtitles = ids.indexOf("SHOW_SUBTITLES");
                int delay = ids.indexOf("backportcore:chat_delay");
                s.check("26.3's order: High Contrast after Show Subtitles, Notification Time after Chat Delay",
                        ids.indexOf(Presentation.HIGH_CONTRAST.id()) == subtitles + 1
                                && ids.indexOf(Presentation.NOTIFICATION_TIME.id()) == delay + 1, ids);
            }
            return true;
        });
        s.add("accessibility-end", () -> {
            scroll(mc.currentScreen, 1000);
            return true;
        });
        s.add("mouse", () -> show(mc, OptionsListScreen.mouse(new GuiMainMenu())));
        s.add(null, () -> {
            boolean gone = VanillaRevisions.touchscreenRemoved(Presentation.newest(LookArea.OPTIONS));
            List<String> ids = ids(mc.currentScreen);
            s.check("Mouse Settings: Touchscreen gone from 26.2", ids.contains(GameSettings.Options.TOUCHSCREEN.name()) != gone, ids);
            return true;
        });
        s.add("controls", () -> show(mc, OptionsListScreen.controls(new GuiMainMenu())));
        s.add(null, () -> {
            List<String> ids = ids(mc.currentScreen);
            boolean v263 = Presentation.isAtLeast(LookArea.OPTIONS, "26.3");
            s.check("Controls: Quit Shortcuts from 26.3, before Operator Items Tab",
                    ids.contains(Presentation.QUIT_SHORTCUTS.id()) == v263 && (!v263 || ids.indexOf(Presentation.QUIT_SHORTCUTS.id())
                            < ids.indexOf(Presentation.OPERATOR_ITEMS_TAB.id())), ids);
            s.check("Controls: Right Click Emulation only on macOS",
                    ids.contains(Presentation.CTRL_CLICK_RIGHT_CLICK.id()) == (v263 && VanillaRevisions.macOs()), ids);
            return true;
        });
        s.add("sounds", () -> show(mc, new GuiScreenOptionsSounds(new GuiMainMenu(), mc.gameSettings)));
        s.add(null, () -> {
            boolean device = false;
            for (GuiButton b : buttons(mc.currentScreen)) {
                device |= b.width == 310 && b.displayString.startsWith(net.minecraft.client.resources.I18n.format("options.audioDevice") + ":");
            }
            s.check("Music and Sounds: the Device row from 1.18", device == Presentation.isAtLeast(LookArea.MUSIC, "1.18"),
                    Presentation.SOUND_DEVICE.get());
            List<String> ids = ids(mc.currentScreen);
            s.check("Music and Sounds: Directional Audio exactly when OpenAL has HRTF (1.19)",
                    ids.contains(Presentation.DIRECTIONAL_AUDIO.id()) == Presentation.DIRECTIONAL_AUDIO.isShown(), ids);
            BackportCore.LOGGER.info("MENU sounds rows: {} (HRTF possible: {})", ids, Presentation.DIRECTIONAL_AUDIO.isShown());
            return true;
        });
        s.add(null, () -> {
            List<String> devices = SoundDevices.devices();
            if (!devices.isEmpty() && SoundDevices.deviceShown()) {
                SoundDevices.cycle(false);
            }
            return true;
        });
        s.add(null, () -> true);
        s.add(null, () -> {
            if (SoundDevices.deviceShown() && !SoundDevices.lastListed.isEmpty()) {
                s.check("Device: the sound system restarted on the chosen device", SoundDevices.opened().equals(Presentation.SOUND_DEVICE.get())
                        && !SoundDevices.opened().isEmpty(), SoundDevices.opened() + " / " + Presentation.SOUND_DEVICE.get());
            }
            Presentation.SOUND_DEVICE.set("");
            SoundDevices.reload();
            return true;
        });
        s.add(null, () -> {
            Presentation.HIGH_CONTRAST.set(true);
            return true;
        });
        s.add("high-contrast", () -> show(mc, new GuiOptions(new GuiMainMenu(), mc.gameSettings)));
        s.add(null, () -> {
            boolean pack = Presentation.highContrastPack() != null;
            s.check("High Contrast on: widgets.png made from the pack's sprites", !pack || HighContrastPack.composed > 0, HighContrastPack.composed);
            s.check("High Contrast answers only with a pack", HighContrastPack.active() == (pack && Presentation.HIGH_CONTRAST.isShown()));
            Presentation.HIGH_CONTRAST.set(false);
            return true;
        });
        s.add("high-contrast-off", () -> show(mc, new GuiOptions(new GuiMainMenu(), mc.gameSettings)));
    }

    private static boolean show(Minecraft mc, GuiScreen screen) {
        mc.displayGuiScreen(screen);
        return true;
    }

    /** @return the screen's buttons, its scrolling list's included */
    @SuppressWarnings("unchecked")
    static List<GuiButton> buttons(GuiScreen gui) {
        List<GuiButton> out = new ArrayList<GuiButton>();
        try {
            out.addAll((List<GuiButton>) ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n").get(gui));
        } catch (ReflectiveOperationException e) {
            BackportCore.LOGGER.error("MENU could not read the buttons", e);
        }
        if (gui instanceof OptionsListScreen && ((OptionsListScreen) gui).rowButtons() != null) {
            out.addAll(((OptionsListScreen) gui).rowButtons());
        }
        return out;
    }

    /** @return the rows in order: vanilla's by option name, Core's by option id */
    static List<String> ids(GuiScreen gui) {
        List<String> out = new ArrayList<String>();
        for (GuiButton b : buttons(gui)) {
            if (b instanceof OptionWidgets.OptionButton) {
                out.add(((OptionWidgets.OptionButton) b).option().id());
            } else if (b instanceof GuiOptionButton) {
                out.add(((GuiOptionButton) b).getOption().name());
            } else if (b instanceof net.minecraft.client.gui.GuiOptionSlider) {
                GameSettings.Options o = GameSettings.Options.byOrdinal(b.id);
                out.add(o == null ? "?" : o.name());
            }
        }
        return out;
    }

    static GuiButton button(GuiScreen gui, String id) {
        for (GuiButton b : buttons(gui)) {
            if (b instanceof OptionWidgets.OptionButton && ((OptionWidgets.OptionButton) b).option().id().equals(id)) {
                return b;
            }
        }
        return null;
    }

    private static void scroll(GuiScreen gui, int by) {
        try {
            Object list = ReflectionHelper.findField(OptionsListScreen.class, "list").get(gui);
            if (list instanceof net.minecraft.client.gui.GuiSlot) {
                ((net.minecraft.client.gui.GuiSlot) list).scrollBy(by);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            BackportCore.LOGGER.error("MENU could not scroll", e);
        }
    }
}

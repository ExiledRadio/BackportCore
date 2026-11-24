package com.exiledradio.backportcore.presentation.versions.client;

import com.exiledradio.backportcore.presentation.LegacyCopies;
import com.exiledradio.backportcore.presentation.versions.Look115;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;

/**
 * 1.15's "Sneak: Toggle" and "Sprint: Toggle" ({@link Look115#TOGGLE_SNEAK}, {@link Look115#TOGGLE_SPRINT}): 1.15's
 * {@code ToggleableKeyBinding}. In toggle mode a press of the key flips the binding's held state and a release does
 * nothing. 1.12.2 sets a binding's held state straight from each key event and when a screen opens or closes, so the
 * toggled state is put back after each event for that key and at the start of every client tick. Everything that
 * reads the key (sneaking, sprinting, other mods) then sees it held. Unlike 1.21.9's Attack and Use toggles
 * ({@code presentation.client.ControlToggles}), a toggled sneak stays on through a screen, as 1.15's does.
 *
 * <p>Moved from Buzzy Bees Backport's {@code tweaks.client.ToggleKeys} in Core 1.4.7; off while an older Buzzy Bees
 * jar that still has that class runs its own.
 */
@SideOnly(Side.CLIENT)
public final class SneakSprintToggles {

    /** The older Buzzy Bees jar's own toggle. */
    static final String LEGACY = "com.exiledradio.buzzybeesbackport.tweaks.client.ToggleKeys";

    private static final Field PRESSED = ReflectionHelper.findField(KeyBinding.class, "pressed", "field_74513_e");

    /** For the client self-test. */
    public static volatile int toggles;

    private final Toggle sneak = new Toggle();
    private final Toggle sprint = new Toggle();

    private static final class Toggle {
        /** The toggled held state. */
        boolean down;
        /** Whether toggle mode was on at the last tick (to let go of the key once it is switched off). */
        boolean active;
    }

    private static boolean runs() {
        return LegacyCopies.coreRuns(LEGACY);
    }

    @SubscribeEvent
    public void key(InputEvent.KeyInputEvent event) {
        int code = Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
        handle(code, Keyboard.getEventKeyState() && !Keyboard.isRepeatEvent());
    }

    @SubscribeEvent
    public void mouse(InputEvent.MouseInputEvent event) {
        int button = Mouse.getEventButton();
        if (button >= 0) {
            handle(button - 100, Mouse.getEventButtonState());
        }
    }

    private void handle(int code, boolean press) {
        if (code == 0 || !runs()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        event(mc, mc.gameSettings.keyBindSneak, Look115.toggles(Look115.TOGGLE_SNEAK), this.sneak, code, press);
        event(mc, mc.gameSettings.keyBindSprint, Look115.toggles(Look115.TOGGLE_SPRINT), this.sprint, code, press);
    }

    private static void event(Minecraft mc, KeyBinding binding, boolean option, Toggle toggle, int code, boolean press) {
        if (!option || binding.getKeyCode() != code) {
            return;
        }
        if (press && mc.currentScreen == null && mc.player != null && binding.isActiveAndMatches(code)) {
            toggle.down = !toggle.down;
            toggles++;
        }
        set(binding, toggle.down);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !runs()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        tick(mc, mc.gameSettings.keyBindSneak, Look115.toggles(Look115.TOGGLE_SNEAK), this.sneak);
        tick(mc, mc.gameSettings.keyBindSprint, Look115.toggles(Look115.TOGGLE_SPRINT), this.sprint);
    }

    private static void tick(Minecraft mc, KeyBinding binding, boolean option, Toggle toggle) {
        if (option) {
            if (mc.player == null) {
                toggle.down = false; // a new world starts with the key let go
            }
            set(binding, toggle.down);
            toggle.active = true;
        } else if (toggle.active) {
            toggle.active = false;
            toggle.down = false;
            set(binding, false);
        }
    }

    private static void set(KeyBinding binding, boolean down) {
        try {
            PRESSED.setBoolean(binding, down);
        } catch (IllegalAccessException ignored) {
            // The key keeps vanilla's hold behavior.
        }
    }
}

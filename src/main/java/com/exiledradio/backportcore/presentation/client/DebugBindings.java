package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.DebugKeys;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.21.11's Debug key binds ({@code Options.keyDebug*}, category {@code key.category.minecraft.debug}): F3 itself (the
 * modifier and the overlay) and each F3 combination of the newest installed version's map are key binds on the Key
 * Binds screen, rebound there like any other. Patch P30 reads the modifier, overlay and crash keys from here, and
 * P7's hook turns a rebound combination's key back into the one the map knows.
 *
 * <p>The debug binds conflict with none (they are only read while F3 is held, and vanilla's own share keys: F3 for
 * the modifier and the overlay, C for the crash and Copy Location), so Strafe Left on A does not mark F3 + A red.
 */
@SideOnly(Side.CLIENT)
public final class DebugBindings {

    public static final String CATEGORY = "key.category.minecraft.debug";
    /** F3 + F6's key code. */
    public static final int OPTIONS = 64;

    /** Vanilla's names for the combinations' keys, by 1.12.2's key code of their default. */
    static final Map<Integer, String> NAMES = new LinkedHashMap<Integer, String>();

    static {
        NAMES.put(Keyboard.KEY_A, "key.debug.reloadChunk");
        NAMES.put(Keyboard.KEY_B, "key.debug.showHitboxes");
        NAMES.put(Keyboard.KEY_D, "key.debug.clearChat");
        NAMES.put(Keyboard.KEY_G, "key.debug.showChunkBorders");
        NAMES.put(Keyboard.KEY_H, "key.debug.showAdvancedTooltips");
        NAMES.put(Keyboard.KEY_I, "key.debug.copyRecreateCommand");
        NAMES.put(Keyboard.KEY_N, "key.debug.spectate");
        NAMES.put(Keyboard.KEY_F4, "key.debug.switchGameMode");
        NAMES.put(Keyboard.KEY_F6, "key.debug.debugOptions");
        NAMES.put(Keyboard.KEY_P, "key.debug.focusPause");
        NAMES.put(Keyboard.KEY_S, "key.debug.dumpDynamicTextures");
        NAMES.put(Keyboard.KEY_T, "key.debug.reloadResourcePacks");
        NAMES.put(Keyboard.KEY_L, "key.debug.profiling");
        NAMES.put(Keyboard.KEY_C, "key.debug.copyLocation");
        NAMES.put(Keyboard.KEY_V, "key.debug.dumpVersion");
        NAMES.put(Keyboard.KEY_1, "key.debug.profilingChart");
        NAMES.put(Keyboard.KEY_2, "key.debug.fpsCharts");
        NAMES.put(Keyboard.KEY_3, "key.debug.networkCharts");
    }

    private static KeyBinding modifier;
    private static KeyBinding overlay;
    private static KeyBinding crash;
    /** The combinations' binds by their default key code. */
    private static final Map<Integer, KeyBinding> COMBOS = new LinkedHashMap<Integer, KeyBinding>();

    private DebugBindings() {
    }

    /** @return true once the binds are registered (1.21.11 is installed) */
    public static boolean active() {
        return modifier != null;
    }

    /** From Core's client init, after every backport's pre-init: registers the binds when 1.21.11 is installed. */
    static void register() {
        if (!VanillaRevisions.debugKeyBinds(Presentation.newest()) || modifier != null) {
            return;
        }
        overlay = bind("key.debug.overlay", Keyboard.KEY_F3);
        modifier = bind("key.debug.modifier", Keyboard.KEY_F3);
        crash = bind("key.debug.crash", Keyboard.KEY_C);
        for (Integer code : DebugKeys.map(Presentation.installed()).keySet()) {
            String name = NAMES.get(code);
            if (name != null && !COMBOS.containsKey(code)) {
                COMBOS.put(code, bind(name, code));
            }
        }
    }

    private static KeyBinding bind(String name, int code) {
        KeyBinding k = new KeyBinding(name, DEBUG, code, CATEGORY);
        ClientRegistry.registerKeyBinding(k);
        return k;
    }

    /** @return the binds, for the client self-test */
    public static List<KeyBinding> binds() {
        List<KeyBinding> out = new ArrayList<KeyBinding>();
        if (modifier != null) {
            Collections.addAll(out, overlay, modifier, crash);
            out.addAll(COMBOS.values());
        }
        return out;
    }

    /** P30: the key F3 stands for as the modifier. */
    static int modifierKey(int vanilla) {
        return modifier == null ? vanilla : modifier.getKeyCode();
    }

    /** P30: the key F3 stands for as the overlay toggle. */
    static int overlayKey(int vanilla) {
        return overlay == null ? vanilla : overlay.getKeyCode();
    }

    /** P30: the key C stands for in F3 + C's crash. */
    static int crashKey(int vanilla) {
        return crash == null ? vanilla : crash.getKeyCode();
    }

    /**
     * @return the key the F3 map knows for a pressed key: the default of the bind now on it, the key itself when no
     *         bind uses it, or -1 when it is a bind's default that has been moved elsewhere
     */
    public static int translate(int pressed) {
        if (modifier == null) {
            return pressed;
        }
        for (Map.Entry<Integer, KeyBinding> e : COMBOS.entrySet()) {
            if (e.getValue().getKeyCode() == pressed) {
                return e.getKey();
            }
        }
        return COMBOS.containsKey(pressed) ? -1 : pressed;
    }

    /** @return "F3 + F6" as the binds stand ({@code [F3+F6]} in the debug screen's "To edit" line) */
    static String comboName(int defaultCode) {
        int mod = modifierKey(Keyboard.KEY_F3);
        KeyBinding b = COMBOS.get(defaultCode);
        int key = b == null ? defaultCode : b.getKeyCode();
        return GameSettings.getKeyDisplayString(mod) + "+" + GameSettings.getKeyDisplayString(key);
    }

    /** The debug binds' context: always active, conflicting with no other bind. */
    static final IKeyConflictContext DEBUG = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            // Never active: Forge's KeyBindingMap hands a press to the first active bind on that key, so an active F3 + 2
            // bind on 2 took the press from Hotbar Slot 2 (and F3 + 1 from slot 1). The binds are only read for their key
            // codes (P7, P30), never for being pressed, so they lose nothing.
            return false;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return false;
        }
    };
}

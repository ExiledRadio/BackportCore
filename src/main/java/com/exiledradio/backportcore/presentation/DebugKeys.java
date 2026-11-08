package com.exiledradio.backportcore.presentation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The F3 key combinations: 1.12.2's own, then each installed version's additions, changes and removals in
 * version order. One key map, the newest version's; a key a version removed stays removed unless a later
 * version brings it back.
 *
 * <p>Key codes are LWJGL 2's ({@code Keyboard.KEY_C} is 46). Core's patch P7 sends every F3 combination
 * through this map once any installed version changes it; until then 1.12.2's own handling runs untouched.
 */
public final class DebugKeys {

    /** What an F3 combination does. Runs on the client thread, with no screen open. */
    public interface Action {
        /** @return true when the key was handled (F3 then does not toggle the debug screen on release) */
        boolean run();
    }

    /** One combination: F3 + {@code keyCode}, its F3+Q help line, and what it does (null: 1.12.2's own). */
    public static final class Key {
        public final int keyCode;
        public final String helpKey;
        @Nullable
        public final Action action;

        public Key(int keyCode, String helpKey, @Nullable Action action) {
            this.keyCode = keyCode;
            this.helpKey = helpKey;
            this.action = action;
        }

        @Override
        public String toString() {
            return keyCode + "=" + helpKey + (action == null ? " (1.12.2)" : "");
        }
    }

    public static final int KEY_Q = 16;
    private static final String LETTERS = "QWERTYUIOPASDFGHJKLZXCVBNM";
    private static final int[] LETTER_CODES = {16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 30, 31, 32, 33, 34, 35, 36, 37, 38, 44, 45, 46, 47, 48, 49, 50};

    /** 1.12.2's combinations, as its {@code Minecraft.processKeyF3} has them (F3+C's crash is separate). */
    public static final List<Key> BASE = list(
            new Key(30, "debug.reload_chunks.help", null),
            new Key(48, "debug.show_hitboxes.help", null),
            new Key(32, "debug.clear_chat.help", null),
            new Key(33, "debug.cycle_renderdistance.help", null),
            new Key(34, "debug.chunk_boundaries.help", null),
            new Key(35, "debug.advanced_tooltips.help", null),
            new Key(49, "debug.creative_spectator.help", null),
            new Key(25, "debug.pause_focus.help", null),
            new Key(KEY_Q, "debug.help.help", null),
            new Key(20, "debug.reload_resourcepacks.help", null));

    private DebugKeys() {
    }

    private static List<Key> list(Key... keys) {
        List<Key> out = new ArrayList<Key>();
        for (Key k : keys) {
            out.add(k);
        }
        return out;
    }

    /** @return true when any installed version adds, changes or removes an F3 combination */
    public static boolean changed(List<Contribution> installed) {
        for (Contribution c : installed) {
            if (!c.debugAdds.isEmpty() || !c.debugRemoves.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** @return the key map at the newest installed version, by key code, in F3+Q's order ({@link #helpOrder}) */
    public static Map<Integer, Key> map(List<Contribution> installed) {
        Map<Integer, Key> map = new LinkedHashMap<Integer, Key>();
        for (Key k : BASE) {
            map.put(k.keyCode, k);
        }
        for (Contribution c : installed) {
            for (Integer removed : c.debugRemoves) {
                map.remove(removed);
            }
            for (Key added : c.debugAdds) {
                map.put(added.keyCode, added);
            }
        }
        Map<Integer, Key> ordered = new LinkedHashMap<Integer, Key>();
        for (Key k : helpOrder(new ArrayList<Key>(map.values()))) {
            ordered.put(k.keyCode, k);
        }
        return ordered;
    }

    /**
     * @return the keys in the order F3+Q lists them: letter keys alphabetically (as vanilla's list runs A, B, C,
     *         D, F ...), then the other keys (Esc, F4, numbers) in the order they were added
     */
    public static List<Key> helpOrder(List<Key> keys) {
        List<Key> out = new ArrayList<Key>();
        for (int i = 0; i < 26; i++) {
            int code = LETTER_CODES[LETTERS.indexOf((char) ('A' + i))];
            for (Key k : keys) {
                if (k.keyCode == code) {
                    out.add(k);
                }
            }
        }
        for (Key k : keys) {
            if (letterOf(k.keyCode) < 0) {
                out.add(k);
            }
        }
        return out;
    }

    /** @return the letter of a letter key's code ('A' to 'Z'), or -1 */
    public static int letterOf(int keyCode) {
        for (int i = 0; i < LETTER_CODES.length; i++) {
            if (LETTER_CODES[i] == keyCode) {
                return LETTERS.charAt(i);
            }
        }
        return -1;
    }
}

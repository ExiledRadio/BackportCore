package com.exiledradio.backportcore.presentation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 1.21.11's Video Settings ({@code VideoSettingsScreen.addOptions}): three sections, each a header and its entries in
 * vanilla's order. An entry is one of 1.12.2's own (its {@code GameSettings.Options} name) or an option registered
 * with Core, matched by its id's path ({@code ~inactivity_fps_limit} is any mod's {@code <modid>:inactivity_fps_limit}).
 * Entries a section does not name go at the end of the last section, in the order they came.
 */
public final class VideoSections {

    public static final String DISPLAY = "options.video.display.header";
    public static final String QUALITY = "options.video.quality.header";
    public static final String PREFERENCES = "options.video.preferences.header";

    /** The Preset slider, 310 wide on a row of its own at the top of Quality &amp; Performance. */
    public static final String PRESET = "~graphics_preset";

    /** Display: Fullscreen Resolution (not in 1.12.2), Max Framerate, VSync, Reduce FPS when, GUI Scale, Fullscreen, Brightness. */
    static final List<String> DISPLAY_ENTRIES = Arrays.asList("FRAMERATE_LIMIT", "ENABLE_VSYNC", "~inactivity_fps_limit", "GUI_SCALE",
            "USE_FULLSCREEN", "GAMMA");
    /** Quality &amp; Performance after the Preset, as {@code qualityOptions}. */
    static final List<String> QUALITY_ENTRIES = Arrays.asList("~biome_blend_radius", "RENDER_DISTANCE", "~prioritize_chunk_updates",
            "~simulation_distance", "AMBIENT_OCCLUSION", "RENDER_CLOUDS", "PARTICLES", "MIPMAP_LEVELS", "ENTITY_SHADOWS",
            "~entity_distance_scaling", "~menu_background_blurriness", "~cloud_range", "~cutout_leaves", "~improved_transparency",
            "~texture_filtering", "~max_anisotropy", "~weather_radius");
    /** Preferences, as {@code preferenceOptions}. */
    static final List<String> PREFERENCE_ENTRIES = Arrays.asList("~show_autosave_indicator", "~autosave_indicator", "~vignette",
            "ATTACK_INDICATOR", "~chunk_fade");
    /**
     * 1.12.2 entries 1.21.11's Video Settings no longer has: Graphics (its leaves are See-Through Leaves, the rest of
     * the preset is the other settings) and View Bobbing (on Accessibility).
     */
    public static final List<String> LEFT_OUT = Arrays.asList("GRAPHICS", "VIEW_BOBBING");

    private VideoSections() {
    }

    /** One section: its header's lang key and the indices of its entries into the list given. */
    public static final class Section {
        public final String header;
        public final List<Integer> entries = new ArrayList<Integer>();
        /** The index of the Preset entry, or -1 (Quality &amp; Performance only). */
        public int preset = -1;

        Section(String header) {
            this.header = header;
        }
    }

    /** @return the three sections over {@code keys} (entries' names or option ids); left-out entries are in none */
    public static List<Section> arrange(List<String> keys) {
        Section display = new Section(DISPLAY);
        Section quality = new Section(QUALITY);
        Section prefs = new Section(PREFERENCES);
        boolean[] used = new boolean[keys.size()];
        take(keys, used, DISPLAY_ENTRIES, display.entries);
        int preset = indexOf(keys, used, PRESET);
        if (preset >= 0) {
            used[preset] = true;
            quality.preset = preset;
        }
        take(keys, used, QUALITY_ENTRIES, quality.entries);
        take(keys, used, PREFERENCE_ENTRIES, prefs.entries);
        for (int i = 0; i < keys.size(); i++) {
            if (!used[i] && !LEFT_OUT.contains(keys.get(i))) {
                prefs.entries.add(i);
            }
        }
        return Arrays.asList(display, quality, prefs);
    }

    private static void take(List<String> keys, boolean[] used, List<String> names, List<Integer> into) {
        for (String name : names) {
            int i = indexOf(keys, used, name);
            if (i >= 0) {
                used[i] = true;
                into.add(i);
            }
        }
    }

    private static int indexOf(List<String> keys, boolean[] used, String name) {
        for (int i = 0; i < keys.size(); i++) {
            if (!used[i] && matches(keys.get(i), name)) {
                return i;
            }
        }
        return -1;
    }

    /** @return true when entry {@code key} is {@code name}: the same, or for {@code ~path} an option id with that path */
    static boolean matches(String key, String name) {
        if (name.startsWith("~")) {
            int colon = key.indexOf(':');
            return colon > 0 && key.substring(colon + 1).equals(name.substring(1));
        }
        return key.equals(name);
    }
}

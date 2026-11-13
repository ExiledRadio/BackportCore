package com.exiledradio.backportcore.presentation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 1.21.11's graphics presets ({@code GraphicsPreset.apply}): the Quality &amp; Performance settings each one sets, by
 * name. Vanilla's own settings use the names below; the backports' options use their id's path (the backports
 * name their copies of vanilla options after vanilla's), and a preset sets them only when one is registered.
 *
 * <p>1.12.2 has no Biome Blend, Prioritize Chunk Updates, Simulation Distance or Improved Transparency of its own, and
 * Texture Filtering has no RGSS: Fancy's RGSS is None here (Fabulous! keeps Anisotropic, as vanilla's does away from
 * AMD graphics).
 */
public final class GraphicsPreset {

    public static final String FAST = "fast";
    public static final String FANCY = "fancy";
    public static final String FABULOUS = "fabulous";
    public static final String CUSTOM = "custom";

    /** 1.12.2's Render Distance. */
    public static final String RENDER_DISTANCE = "render_distance";
    /** 1.12.2's Smooth Lighting (0 off, 2 maximum, as 1.13 made it a toggle). */
    public static final String AO = "ao";
    /** 1.12.2's Clouds: 0 off, 1 fast, 2 fancy. */
    public static final String CLOUDS = "clouds";
    /** 1.12.2's Particles: 0 all, 1 decreased, 2 minimal. */
    public static final String PARTICLES = "particles";
    public static final String MIPMAP_LEVELS = "mipmap_levels";
    public static final String ENTITY_SHADOWS = "entity_shadows";

    private GraphicsPreset() {
    }

    /** @return what the preset sets ({@code "fast"}, {@code "fancy"}, {@code "fabulous"}), as saved values; empty for custom */
    public static Map<String, String> values(String preset) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        if (FAST.equals(preset)) {
            m.put("biome_blend_radius", "1");
            m.put(RENDER_DISTANCE, "8");
            m.put("prioritize_chunk_updates", "none");
            m.put("simulation_distance", "6");
            m.put(AO, "false");
            m.put(CLOUDS, "1");
            m.put(PARTICLES, "1");
            m.put(MIPMAP_LEVELS, "2");
            m.put(ENTITY_SHADOWS, "false");
            m.put("entity_distance_scaling", "0.75");
            m.put("menu_background_blurriness", "2");
            m.put("cloud_range", "32");
            m.put("cutout_leaves", "false");
            m.put("improved_transparency", "false");
            m.put("weather_radius", "5");
            m.put("max_anisotropy", "1");
            m.put("texture_filtering", "none");
        } else if (FANCY.equals(preset) || FABULOUS.equals(preset)) {
            boolean fab = FABULOUS.equals(preset);
            m.put("biome_blend_radius", "2");
            m.put(RENDER_DISTANCE, fab ? "32" : "16");
            m.put("prioritize_chunk_updates", "player_affected");
            m.put("simulation_distance", "12");
            m.put(AO, "true");
            m.put(CLOUDS, "2");
            m.put(PARTICLES, "0");
            m.put(MIPMAP_LEVELS, "4");
            m.put(ENTITY_SHADOWS, "true");
            m.put("entity_distance_scaling", fab ? "1.25" : "1.0");
            m.put("menu_background_blurriness", "5");
            m.put("cloud_range", fab ? "128" : "64");
            m.put("cutout_leaves", "true");
            m.put("improved_transparency", fab ? "true" : "false");
            m.put("weather_radius", "10");
            m.put("max_anisotropy", fab ? "2" : "1");
            m.put("texture_filtering", fab ? "anisotropic" : "none");
        }
        return Collections.unmodifiableMap(m);
    }

    /**
     * @return true when every setting in {@code current} (by name, as saved) has the preset's value; settings the
     *         preset names but {@code current} lacks are not compared
     */
    public static boolean matches(String preset, Map<String, String> current) {
        Map<String, String> want = values(preset);
        if (want.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> e : want.entrySet()) {
            String have = current.get(e.getKey());
            if (have != null && !same(have, e.getValue())) {
                return false;
            }
        }
        return true;
    }

    /** Numbers compare as numbers ("1.0" is "1"), anything else as text. */
    static boolean same(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        try {
            return Double.parseDouble(a) == Double.parseDouble(b);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

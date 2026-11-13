package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.DebugKeys;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.GraphicsPreset;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.VideoSections;
import com.exiledradio.backportcore.presentation.option.CycleOption;
import com.exiledradio.backportcore.presentation.option.OptionScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Core 1.2.0's 1.18 to 1.21.11 look and feel, as far as a server can see it: the Video Settings sections, the
 * presets, the options' versions, the moves between Accessibility and Controls, the F3 map with Core's keys and the
 * title logo revisions.
 */
final class LookFeelTests {

    private LookFeelTests() {
    }

    private static GameVersion v(String s) {
        return GameVersion.parse(s);
    }

    static void schedule() {
        then(1, "1.21.11 video settings", world -> {
            List<String> keys = Arrays.asList("GRAPHICS", "RENDER_DISTANCE", "AMBIENT_OCCLUSION", "FRAMERATE_LIMIT", "VIEW_BOBBING",
                    "GUI_SCALE", "ATTACK_INDICATOR", "GAMMA", "RENDER_CLOUDS", "PARTICLES", "USE_FULLSCREEN", "ENABLE_VSYNC",
                    "MIPMAP_LEVELS", "ENTITY_SHADOWS", "trickytrialsbackport:inactivity_fps_limit", "backportcore:graphics_preset",
                    "backportcore:cloud_range", "backportcore:cutout_leaves", "backportcore:weather_radius", "backportcore:texture_filtering",
                    "backportcore:max_anisotropy", "backportcore:vignette", "backportcore:chunk_fade", "othermod:something");
            List<VideoSections.Section> s = VideoSections.arrange(keys);
            check("video: three sections", s.size() == 3 && VideoSections.DISPLAY.equals(s.get(0).header)
                    && VideoSections.QUALITY.equals(s.get(1).header) && VideoSections.PREFERENCES.equals(s.get(2).header));
            check("video: Display is 1.21.11's order", names(keys, s.get(0).entries).equals(Arrays.asList("FRAMERATE_LIMIT", "ENABLE_VSYNC",
                    "trickytrialsbackport:inactivity_fps_limit", "GUI_SCALE", "USE_FULLSCREEN", "GAMMA")), names(keys, s.get(0).entries));
            check("video: the preset heads Quality & Performance", s.get(1).preset == keys.indexOf("backportcore:graphics_preset"));
            check("video: Quality & Performance is 1.21.11's order", names(keys, s.get(1).entries).equals(Arrays.asList("RENDER_DISTANCE",
                    "AMBIENT_OCCLUSION", "RENDER_CLOUDS", "PARTICLES", "MIPMAP_LEVELS", "ENTITY_SHADOWS", "backportcore:cloud_range",
                    "backportcore:cutout_leaves", "backportcore:texture_filtering", "backportcore:max_anisotropy", "backportcore:weather_radius")),
                    names(keys, s.get(1).entries));
            check("video: Preferences, then other mods' entries", names(keys, s.get(2).entries).equals(Arrays.asList("backportcore:vignette",
                    "ATTACK_INDICATOR", "backportcore:chunk_fade", "othermod:something")), names(keys, s.get(2).entries));
            List<String> all = new ArrayList<String>();
            for (VideoSections.Section x : s) {
                all.addAll(names(keys, x.entries));
            }
            check("video: Graphics and View Bobbing are gone", !all.contains("GRAPHICS") && !all.contains("VIEW_BOBBING"));

            check("preset: Fast's numbers", "8".equals(GraphicsPreset.values("fast").get("render_distance"))
                    && "32".equals(GraphicsPreset.values("fast").get("cloud_range")) && "5".equals(GraphicsPreset.values("fast").get("weather_radius"))
                    && "false".equals(GraphicsPreset.values("fast").get("cutout_leaves")));
            check("preset: Fabulous! is 32 chunks, 128 cloud chunks, anisotropic 4x",
                    "32".equals(GraphicsPreset.values("fabulous").get("render_distance")) && "128".equals(GraphicsPreset.values("fabulous").get("cloud_range"))
                            && "anisotropic".equals(GraphicsPreset.values("fabulous").get("texture_filtering"))
                            && "2".equals(GraphicsPreset.values("fabulous").get("max_anisotropy")));
            check("preset: Custom sets nothing", GraphicsPreset.values("custom").isEmpty());
            Map<String, String> now = new HashMap<String, String>(GraphicsPreset.values("fancy"));
            now.put("entity_distance_scaling", "1");
            now.remove("biome_blend_radius");
            check("preset: Fancy matches its own values (numbers as numbers, missing ones skipped)", GraphicsPreset.matches("fancy", now));
            now.put("render_distance", "12");
            check("preset: a changed one is no longer Fancy", !GraphicsPreset.matches("fancy", now));

            check("options: Preset, leaves, weather, filtering, vignette and fade since 1.21.11; Cloud Distance since 1.21.6",
                    "1.21.11".equals(String.valueOf(Presentation.GRAPHICS_PRESET.since())) && "1.21.6".equals(String.valueOf(Presentation.CLOUD_RANGE.since()))
                            && "1.21.11".equals(String.valueOf(Presentation.CHUNK_FADE.since())) && "1.21.11".equals(String.valueOf(Presentation.VIGNETTE.since())));
            check("options: Cloud Distance defaults to 128 and goes 2 to 128", Presentation.CLOUD_RANGE.defaultValue() == 128.0
                    && Presentation.CLOUD_RANGE.min() == 2.0 && Presentation.CLOUD_RANGE.max() == 128.0);
            check("options: Anisotropic Filtering shows 2 to its value", "4".equals(Presentation.MAX_ANISOTROPY.valueText()), Presentation.MAX_ANISOTROPY.valueText());
            check("options: Chunk Fade shows two decimals", "0.75".equals(Presentation.CHUNK_FADE.valueText()), Presentation.CHUNK_FADE.valueText());
            check("options: none of them shows at the dummy 1.16", !Presentation.CLOUD_RANGE.isShown() && !Presentation.GRAPHICS_PRESET.isShown());
        });
        then(1, "1.18 to 1.21.11 controls, F3 and logo", world -> {
            CycleOption crouch = new CycleOption("selftestcopy:toggle_crouch", "key.sneak", OptionScreen.ACCESSIBILITY,
                    new String[] {"hold", "toggle"}, new String[] {"options.key.hold", "options.key.toggle"}, "hold");
            check("moves: Toggle Crouch on Accessibility before 1.18", crouch.isOn(OptionScreen.ACCESSIBILITY, v("1.17"))
                    && !crouch.isOn(OptionScreen.CONTROLS, v("1.17")));
            check("moves: on both from 1.18", crouch.isOn(OptionScreen.ACCESSIBILITY, v("1.18")) && crouch.isOn(OptionScreen.CONTROLS, v("1.18")));
            check("moves: on Controls only from 1.21.9", !crouch.isOn(OptionScreen.ACCESSIBILITY, v("1.21.9")) && crouch.isOn(OptionScreen.CONTROLS, v("1.21.9")));
            check("moves: others stay where registered", Presentation.TEXT_BACKGROUND.isOn(OptionScreen.ACCESSIBILITY, v("1.21.11")));
            check("revisions: Key Binds 1.18, Controls button 1.21.9, F3 options 1.21.9, debug binds and sections 1.21.11",
                    VanillaRevisions.keyBindsScreen(v("1.18")) && !VanillaRevisions.keyBindsScreen(v("1.17.1"))
                            && VanillaRevisions.controlsLeaveAccessibility(v("1.21.9")) && !VanillaRevisions.controlsLeaveAccessibility(v("1.21.8"))
                            && VanillaRevisions.debugOptions(v("1.21.9")) && !VanillaRevisions.debugKeyBinds(v("1.21.10"))
                            && VanillaRevisions.debugKeyBinds(v("1.21.11")) && VanillaRevisions.videoSections(v("1.21.11")));
            check("logo: 1.20's layout from 1.20", VanillaRevisions.TitleLogo.forVersion(v("1.20")) == VanillaRevisions.TitleLogo.LOGO_1_20
                    && VanillaRevisions.TitleLogo.forVersion(v("1.19.4")) == VanillaRevisions.TitleLogo.CLASSIC);

            DebugKeys.coreKey("1.30", new DebugKeys.Key(200, "selftest.help", () -> true));
            List<Contribution> at130 = Collections.singletonList(Presentation.detached("1.30"));
            List<Contribution> at116 = Collections.singletonList(Presentation.detached("1.16"));
            check("F3: a Core key joins the map from its version", DebugKeys.changed(at130) && DebugKeys.map(at130).containsKey(200)
                    && !DebugKeys.map(at116).containsKey(200));
            Contribution removing = Presentation.detached("1.31").removeDebugKey(200);
            check("F3: a backport can still remove it", !DebugKeys.map(Arrays.asList(Presentation.detached("1.30"), removing)).containsKey(200));
        });
    }

    private static List<String> names(List<String> keys, List<Integer> idx) {
        List<String> out = new ArrayList<String>();
        for (int i : idx) {
            out.add(keys.get(i));
        }
        return out;
    }
}

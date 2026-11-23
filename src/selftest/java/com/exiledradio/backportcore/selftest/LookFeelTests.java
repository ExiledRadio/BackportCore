package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.DebugKeys;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.GraphicsPreset;
import com.exiledradio.backportcore.presentation.LightmapColors;
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
        then(1, "26.1's lightmap against 26.3's lightmap.fsh", world -> lightmap());
    }

    /**
     * {@link LightmapColors} against a plain transcription of 26.3's {@code lightmap.fsh} (doubles, GLSL's order),
     * for 26.3's own inputs per dimension, plus a few values worked out by hand.
     */
    private static void lightmap() {
        check("lightmap: 26.1 is in the sky's versions",
                Arrays.asList(com.exiledradio.backportcore.presentation.LookArea.SKY.coreVersions()).contains(v("26.1")));
        Object[][] cases = {
                // label, dimension, day time, rain, thunder, flicker, night vision, darkness, boss, brightness
                {"Overworld noon", "overworld", 6000F, 0F, 0F, 0F, 0F, 0F, 0F, 0.5F},
                {"Overworld midnight", "overworld", 18000F, 0F, 0F, 0.03F, 0F, 0F, 0F, 0F},
                {"Overworld dusk, rain", "overworld", 12500F, 1F, 0F, -0.02F, 0F, 0F, 0F, 1F},
                {"Overworld dawn, thunder", "overworld", 200F, 1F, 1F, 0.05F, 0F, 0F, 0F, 0.5F},
                {"Overworld night, Night Vision", "overworld", 16000F, 0F, 0F, 0F, 1F, 0F, 0F, 0.5F},
                {"Overworld, Darkness pulse", "overworld", 6000F, 0F, 0F, 0F, 0F, 0.3F, 0F, 0.2F},
                {"Overworld, boss fog", "overworld", 6000F, 0F, 0F, 0F, 0F, 0F, 0.7F, 0.5F},
                {"Nether", "nether", 0F, 0F, 0F, 0.01F, 0F, 0F, 0F, 0.5F},
                {"End", "end", 0F, 0F, 0F, -0.01F, 0F, 0F, 0F, 0.5F},
        };
        int worst = 0;
        String where = "";
        boolean opaque = true;
        for (Object[] c : cases) {
            LightmapColors.Inputs in = new LightmapColors.Inputs();
            String dim = (String) c[1];
            if ("nether".equals(dim)) {
                LightmapColors.nether(in);
            } else if ("end".equals(dim)) {
                LightmapColors.end(in);
            } else {
                LightmapColors.overworld(in, (Float) c[2], (Float) c[3], (Float) c[4], false);
            }
            in.blockFactor = (Float) c[5] + 1.4F;
            in.nightVision = (Float) c[6];
            in.darknessScale = (Float) c[7];
            in.bossDarkening = (Float) c[8];
            in.brightness = (Float) c[9];
            int[] out = new int[256];
            LightmapColors.compute(in, null, out);
            double[] want = new double[3];
            for (int i = 0; i < 256; i++) {
                shader(in, i % 16, i / 16, want);
                for (int k = 0; k < 3; k++) {
                    int got = out[i] >> (16 - 8 * k) & 0xFF;
                    int expect = (int) Math.round(Math.max(0, Math.min(1, want[k])) * 255.0);
                    if (Math.abs(got - expect) > worst) {
                        worst = Math.abs(got - expect);
                        where = c[0] + " sky " + i / 16 + " block " + i % 16 + ": " + got + " vs " + expect;
                    }
                }
                opaque &= (out[i] >>> 24) == 0xFF;
            }
        }
        check("lightmap: every cell within 1/255 of lightmap.fsh (9 cases, 256 cells)", worst <= 1, worst + " " + where);
        check("lightmap: opaque", opaque);

        // By hand, from 26.3's attribute values.
        LightmapColors.Inputs in = new LightmapColors.Inputs();
        LightmapColors.overworld(in, 18000F, 0F, 0F, false);
        in.brightness = 0F;
        int[] out = new int[256];
        LightmapColors.compute(in, null, out);
        // Midnight, sky 15, block 0: #0a0a0a + #7a7aff * 0.24 = (0.154, 0.154, 0.279).
        check("lightmap: midnight sky light is blue (39, 39, 71)", (out[15 * 16] & 0xFFFFFF) == (39 << 16 | 39 << 8 | 71),
                Integer.toHexString(out[15 * 16]));
        check("lightmap: Overworld darkness is #0a0a0a", (out[0] & 0xFFFFFF) == 0x0A0A0A, Integer.toHexString(out[0]));
        LightmapColors.overworld(in, 6000F, 0F, 0F, false);
        LightmapColors.compute(in, null, out);
        check("lightmap: noon sky light 15 is white", (out[255] & 0xFFFFFF) == 0xFFFFFF, Integer.toHexString(out[255]));
        int torch = out[12];
        check("lightmap: torch light (block 12, sky 0) is warm: red > green > blue",
                (torch >> 16 & 0xFF) > (torch >> 8 & 0xFF) && (torch >> 8 & 0xFF) > (torch & 0xFF), Integer.toHexString(torch));
        LightmapColors.nether(in);
        LightmapColors.compute(in, null, out);
        check("lightmap: the Nether's darkness is #302821, its sky light none", (out[0] & 0xFFFFFF) == 0x302821
                && out[15 * 16] == out[0], Integer.toHexString(out[0]) + " " + Integer.toHexString(out[15 * 16]));
        LightmapColors.end(in);
        LightmapColors.compute(in, null, out);
        check("lightmap: the End's darkness is #3f473f", (out[0] & 0xFFFFFF) == 0x3F473F, Integer.toHexString(out[0]));

        // The day timeline (day.json): day 730 to 11270, night 13140 to 22860, linear between, wrapping at 24000.
        check("lightmap: day timeline", LightmapColors.dayAlpha(6000F) == 0F && LightmapColors.dayAlpha(18000F) == 1F
                        && Math.abs(LightmapColors.dayAlpha(12205F) - 0.5F) < 1e-4F && Math.abs(LightmapColors.dayAlpha(23795F) - 0.5F) < 1e-4F,
                LightmapColors.dayAlpha(12205F) + " " + LightmapColors.dayAlpha(23795F));
        float worstTime = 0F;
        for (int t = 0; t < 24000; t += 250) {
            // 1.12.2's WorldProvider.calculateCelestialAngle.
            float f = t / 24000.0F - 0.25F;
            if (f < 0.0F) {
                f++;
            }
            float f1 = f;
            f = 1.0F - (float) ((Math.cos(f * Math.PI) + 1.0D) / 2.0D);
            f = f1 + (f - f1) / 3.0F;
            float back = LightmapColors.dayTimeOfCelestialAngle(f);
            float d = Math.abs(back - t);
            worstTime = Math.max(worstTime, Math.min(d, 24000F - d));
        }
        check("lightmap: the sun's angle gives back the time of day (within 2 ticks)", worstTime <= 2F, worstTime);
    }

    /** 26.3's lightmap.fsh, line by line, in doubles. */
    private static void shader(LightmapColors.Inputs in, int blockIndex, int skyIndex, double[] out) {
        double blockLevel = blockIndex / 15.0;
        double skyLevel = skyIndex / 15.0;
        double blockBrightness = blockLevel / (4.0 - 3.0 * blockLevel) * in.blockFactor;
        double skyBrightness = skyLevel / (4.0 - 3.0 * skyLevel) * in.skyFactor;
        double parabolic = (2.0 * blockLevel - 1.0) * (2.0 * blockLevel - 1.0);
        double[] color = new double[3];
        for (int k = 0; k < 3; k++) {
            color[k] = Math.max(in.ambientColor[k], in.nightVisionColor[k] * in.nightVision);
            color[k] += in.skyLightColor[k] * skyBrightness;
            double tint = in.blockLightTint[k] + (1.0 - in.blockLightTint[k]) * 0.9 * parabolic;
            color[k] += tint * blockBrightness;
        }
        double[] boss = {0.7, 0.6, 0.6};
        for (int k = 0; k < 3; k++) {
            color[k] = color[k] + (color[k] * boss[k] - color[k]) * in.bossDarkening;
            color[k] = Math.max(0.0, Math.min(1.0, color[k] - in.darknessScale));
        }
        double max = Math.max(color[0], Math.max(color[1], color[2]));
        double inv = 1.0 - max;
        double scaled = 1.0 - inv * inv * inv * inv;
        for (int k = 0; k < 3; k++) {
            double notGamma = max == 0.0 ? 0.0 : color[k] * (scaled / max);
            out[k] = color[k] + (notGamma - color[k]) * in.brightness;
        }
    }

    private static List<String> names(List<String> keys, List<Integer> idx) {
        List<String> out = new ArrayList<String>();
        for (int i : idx) {
            out.add(keys.get(i));
        }
        return out;
    }
}

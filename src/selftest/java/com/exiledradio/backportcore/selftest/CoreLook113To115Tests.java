package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.PresentationOption;
import com.exiledradio.backportcore.presentation.versions.BiomeBlend;
import com.exiledradio.backportcore.presentation.versions.Look113;
import com.exiledradio.backportcore.presentation.versions.Look114;
import com.exiledradio.backportcore.presentation.versions.Look115;
import com.exiledradio.backportcore.presentation.versions.ScrollAccumulator;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Core 1.4.7's 1.13, 1.14 and 1.15 pieces moved out of Update Aquatic, Village &amp; Pillage and Buzzy Bees, as far as a
 * server can see them: the core contributions, the options under the backports' ids, the assets in the jar, the
 * scroll arithmetic, the Biome Blend square and the lang keys.
 */
final class CoreLook113To115Tests {

    private static final String[] LANGUAGES = {"de_de", "en_us", "es_ar", "es_es", "es_mx", "es_uy", "es_ve", "fr_fr", "it_it",
            "ja_jp", "ko_kr", "nl_nl", "pl_pl", "pt_br", "ru_ru", "tr_tr", "uk_ua", "zh_cn", "zh_tw"};

    private static final String[] KEYS = {"debug.copy_location.help", "debug.copy_location.message", "options.biomeBlendRadius",
            "debug.inspect.help", "debug.inspect.client.block", "debug.inspect.client.entity", "debug.inspect.server.block",
            "debug.inspect.server.entity", "options.mouseWheelSensitivity", "options.discrete_mouse_scroll", "menu.paused",
            "title.singleplayer", "title.multiplayer.lan", "title.multiplayer.other", "title.multiplayer.realms",
            "options.key.hold", "options.key.toggle"};

    private CoreLook113To115Tests() {
    }

    static void schedule() {
        then(1, "core look 1.13 to 1.15: contributions and options", world -> {
            for (String v : new String[] {"1.13", "1.14", "1.14.1", "1.15", "1.15.2"}) {
                Contribution c = Presentation.coreContribution(v);
                check("core " + v + " is Core's own", c != null && c.isCore() && c.owners().contains("backportcore"), c);
            }
            option(Look113.BIOME_BLEND, "updateaquaticbackport:biome_blend_radius", OptionScreen.VIDEO, "1.13", "ENTITY_SHADOWS");
            option(Look113.AUTO_SUGGEST, "updateaquaticbackport:auto_suggestions", OptionScreen.CHAT, "1.13", "REDUCED_DEBUG_INFO");
            option(Look114.SCROLL_SENSITIVITY, "villagerbackport:mouse_wheel_sensitivity", OptionScreen.MOUSE, "1.14", "INVERT_MOUSE");
            option(Look114.DISCRETE_SCROLLING, "villagerbackport:discrete_mouse_scroll", OptionScreen.MOUSE, "1.14",
                    "villagerbackport:mouse_wheel_sensitivity");
            option(Look115.TOGGLE_SNEAK, "buzzybeesbackport:toggle_crouch", OptionScreen.ACCESSIBILITY, "1.15", "AUTO_JUMP");
            option(Look115.TOGGLE_SPRINT, "buzzybeesbackport:toggle_sprint", OptionScreen.ACCESSIBILITY, "1.15",
                    "buzzybeesbackport:toggle_crouch");
            check("Biome Blend's values: OFF at 0, 5x5 at 2", "options.off".equals(Look113.BIOME_BLEND.zeroKey()), Look113.BIOME_BLEND.zeroKey());
            check("Biome Blend defaults to 5x5", Look113.BIOME_BLEND.defaultValue() == 2.0, Look113.BIOME_BLEND.defaultValue());
            check("furnace book registered exactly when a 1.13 backport is installed",
                    Look113.furnaceBook == Presentation.installedNewest().isAtLeast("1.13"), Look113.furnaceBook);
        });
        then(1, "core look 1.13 to 1.15: assets in Core's jar", world -> {
            Map<String, ResourceLocation> a113 = Presentation.coreContribution("1.13").replacedAssets();
            Map<String, ResourceLocation> a114 = Presentation.coreContribution("1.14").replacedAssets();
            Map<String, ResourceLocation> a115 = Presentation.coreContribution("1.15").replacedAssets();
            Map<String, ResourceLocation> a1152 = Presentation.coreContribution("1.15.2").replacedAssets();
            asset(a113, "textures/gui/recipe_book.png");
            asset(a113, "textures/gui/recipe_button.png");
            asset(a114, "textures/gui/title/mojang.png");
            asset(a115, "textures/gui/widgets.png");
            asset(a115, "textures/gui/title/mojang.png");
            asset(a1152, "textures/gui/checkbox.png");
            for (String p : new String[] {"look/1.14/textures/gui/accessibility.png", "look/1.15/textures/gui/accessibility.png",
                    "look/1.14/texts/splashes_added.txt", "look/1.14/texts/splashes_removed.txt"}) {
                check("in Core's jar: " + p, exists("/assets/backportcore/" + p));
            }
            for (String v : new String[] {"1.13", "1.14", "1.15"}) {
                for (String code : LANGUAGES) {
                    if (!exists("/assets/backportcore/look/" + v + "/vanilla_text/" + code + ".lang")) {
                        check(v + " vanilla text " + code, false);
                    }
                }
                check(v + "'s vanilla text has all 19 languages", true);
            }
        });
        then(1, "core look 1.13 to 1.15: arithmetic", world -> {
            ScrollAccumulator s = new ScrollAccumulator();
            check("scroll: half a notch moves nothing", s.add(0.5) == 0);
            check("scroll: the second half moves one", s.add(0.5) == 1);
            check("scroll: a change of direction starts again", s.add(0.7) == 0 && s.add(-0.7) == 0 && s.add(-0.5) == -1);
            check("scroll: LWJGL 2's notch is 120, lwjglx's 1", ScrollAccumulator.notch(false) == 120.0 && ScrollAccumulator.notch(true) == 1.0);
            int saved = BiomeBlend.radius;
            try {
                BiomeBlend.radius = 0;
                check("Biome Blend OFF: the block's own column", BiomeBlend.low() == 0 && BiomeBlend.high() == 0 && BiomeBlend.count() == 1);
                BiomeBlend.radius = 2;
                check("Biome Blend 5x5", BiomeBlend.low() == -2 && BiomeBlend.high() == 2 && BiomeBlend.count() == 25);
                BiomeBlend.radius = 7;
                check("Biome Blend 15x15", BiomeBlend.count() == 225);
            } finally {
                BiomeBlend.radius = saved;
            }
            check("a server keeps 1.12.2's 3x3", saved == 1, saved);
        });
        then(1, "core look 1.13 to 1.15: lang keys", world -> {
            for (String code : LANGUAGES) {
                Map<String, String> l = lang(code);
                int have = 0;
                StringBuilder missing = new StringBuilder();
                for (String k : KEYS) {
                    String v = l.get(k);
                    if (v != null && !v.isEmpty()) {
                        have++;
                    } else {
                        missing.append(k).append(' ');
                    }
                }
                check(code + " has the moved 1.13 to 1.15 keys", have == KEYS.length, missing);
            }
        });
    }

    private static void option(PresentationOption o, String id, OptionScreen screen, String since, String after) {
        check(id + ": id", id.equals(o.id()), o.id());
        check(id + ": Core's own", o.isCoreOwned());
        check(id + ": on " + screen, o.screen() == screen, o.screen());
        check(id + ": since " + since, GameVersion.parse(since).equals(o.since()), o.since());
        check(id + ": after " + after, after.equals(o.after()), o.after());
        check(id + ": registered", Presentation.option(id) == o, Presentation.option(id));
    }

    private static void asset(Map<String, ResourceLocation> assets, String vanilla) {
        ResourceLocation rl = assets.get(vanilla);
        check("core asset " + vanilla + ": " + rl, rl != null && "backportcore".equals(rl.getNamespace())
                && exists("/assets/backportcore/" + rl.getPath()), rl);
    }

    private static boolean exists(String path) {
        return CoreLook113To115Tests.class.getResource(path) != null;
    }

    private static Map<String, String> lang(String code) {
        Map<String, String> out = new HashMap<String, String>();
        InputStream in = CoreLook113To115Tests.class.getResourceAsStream("/assets/backportcore/lang/" + code + ".lang");
        if (in == null) {
            return out;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.startsWith("#")) {
                    out.put(line.substring(0, eq), line.substring(eq + 1));
                }
            }
        } catch (java.io.IOException e) {
            check("read " + code + ".lang", false, e);
        }
        return out;
    }
}

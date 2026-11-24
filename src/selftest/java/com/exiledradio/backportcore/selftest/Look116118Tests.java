package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.asm.DistortionTransformer;
import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.PresentationOption;
import com.exiledradio.backportcore.presentation.versions.Look116;
import com.exiledradio.backportcore.presentation.versions.Look118;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Core 1.4.7's own 1.16, 1.17 and 1.18 pieces (versions.Look116 and Look118, moved out of Nether Update and Caves &amp;
 * Cliffs Backport): Core's contributions and options, the assets, splashes, vanilla text and lang keys in the jar,
 * the hooks' numbers, and patches P70 and P71 on the dev and SRG classes.
 */
final class Look116118Tests {

    private static final String[] CODES = {"en_us", "de_de", "es_ar", "es_es", "es_mx", "es_uy", "es_ve", "fr_fr", "it_it",
            "ja_jp", "ko_kr", "nl_nl", "pl_pl", "pt_br", "ru_ru", "tr_tr", "uk_ua", "zh_cn", "zh_tw"};
    private static final String SRG_JAR = System.getProperty("user.home")
            + "/.gradle/caches/minecraft/net/minecraftforge/forge/1.12.2-14.23.5.2847/stable/39/forge-1.12.2-14.23.5.2847-srgBin.jar";

    private Look116118Tests() {
    }

    static void schedule() {
        then(1, "look 1.16/1.18: Core's contributions and options", world -> {
            for (String v : new String[] {"1.16", "1.16.2", "1.17", "1.18"}) {
                Contribution c = Presentation.coreContribution(v);
                check("look " + v + ": Core has its own contribution", c != null && c.isCore(), c);
            }
            Object[][] want = {
                    {Look116.ENTITY_DISTANCE, OptionScreen.VIDEO, "1.16", "netherupdatebackport:entity_distance_scaling"},
                    {Look116.DISTORTION, OptionScreen.ACCESSIBILITY, "1.16.2", "netherupdatebackport:screen_effect_scale"},
                    {Look116.FOV_EFFECTS, OptionScreen.ACCESSIBILITY, "1.16.2", "netherupdatebackport:fov_effect_scale"},
                    {Look118.HIDE_LIGHTNING_FLASHES, OptionScreen.ACCESSIBILITY, "1.18", "cavesandcliffsbackport:hide_lightning_flashes"},
                    {Look118.AUTOSAVE_INDICATOR, OptionScreen.VIDEO, "1.18", "cavesandcliffsbackport:autosave_indicator"},
            };
            for (Object[] w : want) {
                PresentationOption o = (PresentationOption) w[0];
                check("look: " + w[3] + " keeps the backport's id", o.id().equals(w[3]), o.id());
                check("look: " + o.id() + " is registered, Core's", Presentation.option(o.id()) == o && o.isCoreOwned());
                check("look: " + o.id() + "'s screen", o.screen() == w[1], o.screen());
                check("look: " + o.id() + "'s since", GameVersion.parse((String) w[2]).equals(o.since()), o.since());
            }
            check("look: Entity Distance 50% to 500% in 25% steps, default 100%", Look116.ENTITY_DISTANCE.min() == 0.5
                    && Look116.ENTITY_DISTANCE.max() == 5.0 && Look116.ENTITY_DISTANCE.snap(1.3) == 1.25 && Look116.ENTITY_DISTANCE.defaultValue() == 1.0);
            check("look: Distortion Effects follows Entity Shadows, Entity Distance it, FOV Effects that",
                    "ENTITY_SHADOWS".equals(Look116.DISTORTION.after()) && Look116.DISTORTION.id().equals(Look116.ENTITY_DISTANCE.after())
                            && Look116.ENTITY_DISTANCE.id().equals(Look116.FOV_EFFECTS.after()));
            check("look: Distortion and FOV Effects are also on Video Settings, read OFF at zero, have tooltips",
                    Look116.DISTORTION.alsoOn().contains(OptionScreen.VIDEO) && Look116.FOV_EFFECTS.alsoOn().contains(OptionScreen.VIDEO)
                            && "options.off".equals(Look116.DISTORTION.zeroKey()) && "options.off".equals(Look116.FOV_EFFECTS.zeroKey())
                            && "options.screenEffectScale.tooltip".equals(Look116.DISTORTION.tooltipKey())
                            && "options.fovEffectScale.tooltip".equals(Look116.FOV_EFFECTS.tooltipKey()));
            check("look: Hide Lightning Flashes follows Monochrome Logo, OFF by default, with its tooltip",
                    Presentation.MONOCHROME_LOGO.id().equals(Look118.HIDE_LIGHTNING_FLASHES.after())
                            && !Look118.HIDE_LIGHTNING_FLASHES.defaultValue()
                            && "options.hideLightningFlashes.tooltip".equals(Look118.HIDE_LIGHTNING_FLASHES.tooltipKey()));
            check("look: Autosave Indicator is ON by default", Look118.AUTOSAVE_INDICATOR.defaultValue());
            check("look: none of the 1.18 options shows before 1.18", !Look118.HIDE_LIGHTNING_FLASHES.isShownAt(GameVersion.parse("1.17.1"))
                    && !Look118.AUTOSAVE_INDICATOR.isShownAt(GameVersion.parse("1.17.1")));
            check("look: Distortion Effects does not show at 1.16", !Look116.DISTORTION.isShownAt(GameVersion.parse("1.16")));
        });
        then(1, "look 1.16/1.18: assets, splashes, vanilla text and lang", world -> {
            String[][] pngs = {
                    {"look/1.16/textures/gui/title/minecraft.png", "256x256"},
                    {"look/1.16/textures/gui/title/mojangstudios.png", null},
                    {"look/1.16/textures/gui/widgets.png", "256x256"},
                    {"look/1.16/textures/gui/checkbox.png", "64x64"},
                    {"look/1.16/textures/gui/accessibility.png", null},
                    {"look/1.16/textures/gui/container/gamemode_switcher.png", null},
                    {"look/1.16/textures/misc/nausea.png", null},
            };
            for (String[] p : pngs) {
                int[] size = pngSize("/assets/backportcore/" + p[0]);
                check("look: " + p[0] + " is in the jar", size != null && (p[1] == null || p[1].equals(size[0] + "x" + size[1])),
                        Arrays.toString(size));
            }
            Object[][] splashes = {
                    {"1.16/texts/splashes_added.txt", 41}, {"1.16/texts/splashes_removed.txt", 14},
                    {"1.18/texts/splashes_added_1_17.txt", 13}, {"1.18/texts/splashes_removed_1_17.txt", 3},
                    {"1.18/texts/splashes_added_1_18.txt", 2}, {"1.18/texts/splashes_removed_1_18.txt", 2},
            };
            for (Object[] s : splashes) {
                int n = lines("/assets/backportcore/look/" + s[0]).size();
                check("look: " + s[0] + " has " + s[1] + " lines", n == (Integer) s[1], n);
            }
            for (String v : new String[] {"1.16", "1.18"}) {
                Map<String, String> en = lang("/assets/backportcore/look/" + v + "/vanilla_text/en_us.lang");
                check("look: " + v + "'s vanilla text is in the jar", en.size() > 20, en.size());
                for (String code : CODES) {
                    Map<String, String> l = lang("/assets/backportcore/look/" + v + "/vanilla_text/" + code + ".lang");
                    check("look: " + v + " vanilla_text/" + code + " has en_us's keys", l.keySet().equals(en.keySet()), l.size());
                }
            }
            Map<String, String> v116 = lang("/assets/backportcore/look/1.16/vanilla_text/en_us.lang");
            Map<String, String> v118 = lang("/assets/backportcore/look/1.18/vanilla_text/en_us.lang");
            check("look: 1.16.5's \"You Died!\"", "You Died!".equals(v116.get("deathScreen.title")), v116.get("deathScreen.title"));
            check("look: 1.18.2's \"Hide Lightning Flashes\" and \"Saving world\"",
                    "Hide Lightning Flashes".equals(v118.get("options.hideLightningFlashes")) && "Saving world".equals(v118.get("menu.savingLevel")));
            String[] keys = {"options.entityDistanceScaling", "options.screenEffectScale", "options.fovEffectScale",
                    "options.screenEffectScale.tooltip", "options.fovEffectScale.tooltip", "debug.gamemodes.help",
                    "debug.gamemodes.error", "debug.gamemodes.press_f4", "debug.gamemodes.select_next"};
            for (String code : CODES) {
                Map<String, String> l = lang("/assets/backportcore/lang/" + code + ".lang");
                List<String> missing = new ArrayList<String>();
                for (String k : keys) {
                    if (l.get(k) == null || l.get(k).isEmpty()) {
                        missing.add(k);
                    }
                }
                check("look: Core's " + code + ".lang has the 1.16 option and F3 + F4 keys", missing.isEmpty(), missing);
            }
            check("look: en_us Entity Distance", "Entity Distance".equals(lang("/assets/backportcore/lang/en_us.lang").get("options.entityDistanceScaling")));
        });
        then(1, "look 1.16/1.18: the hooks' numbers", world -> {
            if (Look116.DISTORTION.isShown()) {
                check("look: full Distortion Effects keeps the wobble", Look116.portalWobble(2.0F) == 2.0F, Look116.portalWobble(2.0F));
                Look116.DISTORTION.set(0.5);
                check("look: Distortion Effects at 50% scales the wobble by its square", Look116.portalWobble(2.0F) == 0.5F, Look116.portalWobble(2.0F));
                Look116.DISTORTION.reset();
                Look116.FOV_EFFECTS.set(0.25);
                check("look: FOV Effects at 25% scales by its root, 0.5", Look116.fovEffectScale() == 0.5F, Look116.fovEffectScale());
                Look116.FOV_EFFECTS.reset();
            } else {
                check("look: below 1.16.2 the wobble and the FOV are 1.12.2's", Look116.portalWobble(2.0F) == 2.0F && Look116.fovEffectScale() == 1.0F);
            }
            if (Look116.ENTITY_DISTANCE.isShown()) {
                Look116.ENTITY_DISTANCE.set(2.0);
                check("look: Entity Distance at 200% doubles the weight", Look116.entityDistance(1.5) == 3.0, Look116.entityDistance(1.5));
                Look116.ENTITY_DISTANCE.reset();
            }
            check("look: Entity Distance at 100% keeps vanilla's weight", Look116.entityDistance(1.5) == 1.5);
            check("look: the overworld's clouds at 192, other dimensions and own heights kept", Look118.cloudHeight(0, 128.0F) == 192.0F
                    && Look118.cloudHeight(7, 128.0F) == 128.0F && Look118.cloudHeight(0, 150.0F) == 150.0F);
            float[][] fog = {{32, 28, 32}, {128, 115.2F, 128}, {192, 172.8F, 192}, {512, 460.8F, 512}, {1024, 960, 1024}, {16, 28, 32}};
            for (float[] f : fog) {
                check("look: 1.18.1 fog at " + f[0] + " from " + f[1] + " to " + f[2],
                        Math.abs(Look118.terrainFogStart(f[0]) - f[1]) < 1e-3 && Look118.terrainFogEnd(f[0]) == f[2], Look118.terrainFogStart(f[0]));
            }
            double[] sky = {0.47, 0.65, 1.0};
            float flash = Math.min(2 - 0.3F, 1.0F) * 0.45F;
            double[] flashed = {(float) sky[0] * (1 - flash) + 0.8F * flash, (float) sky[1] * (1 - flash) + 0.8F * flash,
                    (float) sky[2] * (1 - flash) + 1.0F * flash};
            Look118.undoLightningFlash(flashed, 2, 0.3F);
            check("look: Hide Lightning Flashes gives back the sky", Math.abs(flashed[0] - sky[0]) < 1e-5 && Math.abs(flashed[1] - sky[1]) < 1e-5
                    && Math.abs(flashed[2] - sky[2]) < 1e-5, Arrays.toString(flashed));
            check("look: 1.18.2's F3 Block row", "Block: -17 70 33 [15 6 1]".equals(Look118.blockRow(-17, 70, 33)), Look118.blockRow(-17, 70, 33));
            check("look: 1.18.2's F3 Chunk row", "Chunk: -2 4 2 [30 2 in r.-1.0.mca]".equals(Look118.chunkRow(-17, 70, 33))
                    && "Chunk: 40 -4 -33 [8 31 in r.1.-2.mca]".equals(Look118.chunkRow(640, -60, -520)), Look118.chunkRow(-17, 70, 33));
            float v = 0.0F;
            for (int i = 0; i < 3; i++) {
                v = Look118.autosaveStep(v, true);
            }
            check("look: the autosave indicator fades in by 20% of the rest each tick", Math.abs(v - 0.488F) < 1e-4, v);
            check("look: its alpha between ticks", Look118.autosaveAlpha(0.36F, 0.488F, 0.5F) == 108, Look118.autosaveAlpha(0.36F, 0.488F, 0.5F));
        });
        then(1, "look 1.16: P70 and P71 on the dev and SRG classes", world -> {
            String[][] targets = {
                    {"net.minecraft.client.renderer.EntityRenderer", "portalWobble", "setupCameraTransform", "func_78479_a"},
                    {"net.minecraft.client.renderer.RenderGlobal", "entityDistance", "setupTerrain", "func_174970_a"},
            };
            try {
                for (String[] t : targets) {
                    byte[] dev = readAll(Look116118Tests.class.getResourceAsStream("/" + t[0].replace('.', '/') + ".class"));
                    verify("dev " + t[0], dev == null ? null : DistortionTransformer.patchForTest(t[0], dev), t);
                }
                File srg = new File(SRG_JAR);
                if (!check("look: Forge's SRG jar is in the Gradle cache", srg.isFile(), srg)) {
                    return;
                }
                try (ZipFile zip = new ZipFile(srg)) {
                    for (String[] t : targets) {
                        ZipEntry entry = zip.getEntry(t[0].replace('.', '/') + ".class");
                        byte[] bytes = entry == null ? null : readAll(zip.getInputStream(entry));
                        verify("SRG " + t[0], bytes == null ? null : DistortionTransformer.patchForTest(t[0], bytes), t);
                    }
                }
            } catch (IOException e) {
                check("look: P70/P71 classes read", false, e);
            }
        });
    }

    private static void verify(String what, byte[] patched, String[] t) {
        if (!check("look: " + what + " was read", patched != null)) {
            return;
        }
        ClassNode node = new ClassNode();
        new ClassReader(patched).accept(node, 0);
        int hooks = 0;
        String bad = null;
        for (MethodNode m : node.methods) {
            int here = 0;
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i instanceof MethodInsnNode && t[1].equals(((MethodInsnNode) i).name)
                        && "com/exiledradio/backportcore/presentation/versions/Look116".equals(((MethodInsnNode) i).owner)) {
                    here++;
                }
            }
            if (here > 0 && !(m.name.equals(t[2]) || m.name.equals(t[3]))) {
                bad = "hook in " + m.name;
            }
            hooks += here;
            try {
                new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name, m);
            } catch (Exception e) {
                bad = m.name + m.desc + ": " + e.getMessage();
            }
        }
        check("look: " + what + ": one call to Look116." + t[1] + " in " + t[2], hooks == 1, hooks);
        check("look: " + what + ": every method verifies", bad == null, bad);
    }

    private static Map<String, String> lang(String path) {
        Map<String, String> out = new HashMap<String, String>();
        for (String line : lines(path)) {
            int eq = line.indexOf('=');
            if (eq > 0 && !line.startsWith("#")) {
                out.put(line.substring(0, eq), line.substring(eq + 1));
            }
        }
        return out;
    }

    private static List<String> lines(String path) {
        List<String> out = new ArrayList<String>();
        InputStream in = Look116118Tests.class.getResourceAsStream(path);
        if (in == null) {
            return out;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line; (line = r.readLine()) != null; ) {
                if (!line.trim().isEmpty()) {
                    out.add(line);
                }
            }
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    private static int[] pngSize(String path) {
        try {
            byte[] b = readAll(Look116118Tests.class.getResourceAsStream(path));
            if (b == null || b.length < 24 || b[1] != 'P' || b[2] != 'N' || b[3] != 'G') {
                return null;
            }
            return new int[] {readInt(b, 16), readInt(b, 20)};
        } catch (IOException e) {
            return null;
        }
    }

    private static int readInt(byte[] b, int at) {
        return (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | (b[at + 3] & 0xFF);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        if (in == null) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}

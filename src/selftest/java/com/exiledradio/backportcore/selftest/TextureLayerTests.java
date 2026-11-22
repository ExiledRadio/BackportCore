package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.client.TextureLayers;
import com.exiledradio.backportcore.presentation.GameVersion;

import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * The modern pack's texture layers (client.TextureLayers): which apply at each backport's newest version, which
 * layer a few textures come from, and that every listed file is in the jar and reads.
 */
final class TextureLayerTests {

    private static final String COAL_ORE = "assets/minecraft/textures/blocks/coal_ore.png";

    private TextureLayerTests() {
    }

    static void schedule() {
        then(1, "texture layers", world -> {
            // newest installed version -> the layers that apply
            Map<String, String> expected = new LinkedHashMap<>();
            expected.put("1.12.2", "[1.14]");             // Village & Pillage alone registers no version
            expected.put("1.13", "[1.14]");               // Update Aquatic
            expected.put("1.15.2", "[1.14, 1.15]");       // Buzzy Bees
            expected.put("1.16.4", "[1.14, 1.15, 1.16]"); // Nether Update
            expected.put("1.17", "[1.14, 1.15, 1.16, 1.17]");
            expected.put("1.18.2", "[1.14, 1.15, 1.16, 1.17, 1.18]");                         // Caves & Cliffs
            expected.put("1.19.3", "[1.14, 1.15, 1.16, 1.17, 1.18, 1.19]");                   // Wild Update
            expected.put("1.20.3", "[1.14, 1.15, 1.16, 1.17, 1.18, 1.19, 1.20]");             // Trails & Tales
            expected.put("1.21.4", "[1.14, 1.15, 1.16, 1.17, 1.18, 1.19, 1.20, 1.21]");
            expected.put("1.21.5", "[1.14, 1.15, 1.16, 1.17, 1.18, 1.19, 1.20, 1.21, 1.21.5]"); // the spawn eggs
            expected.put("1.21.11", "[1.14, 1.15, 1.16, 1.17, 1.18, 1.19, 1.20, 1.21, 1.21.5]"); // Tricky Trials
            expected.put("26.3", "[1.14, 1.15, 1.16, 1.17, 1.18, 1.19, 1.20, 1.21, 1.21.5, 26.3]");
            for (Map.Entry<String, String> e : expected.entrySet()) {
                String got = TextureLayers.active(GameVersion.parse(e.getKey())).toString();
                check("textures: layers at " + e.getKey(), got.equals(e.getValue()), got);
            }

            check("textures: coal ore stays 1.14's with only Village & Pillage", "1.14".equals(String.valueOf(
                    TextureLayers.layerFor(COAL_ORE, GameVersion.BASE))));
            check("textures: coal ore stays 1.14's at 1.16.4", "1.14".equals(String.valueOf(
                    TextureLayers.layerFor(COAL_ORE, GameVersion.parse("1.16.4")))));
            for (String ore : new String[] {"coal", "iron", "gold", "diamond", "emerald", "lapis", "redstone"}) {
                String f = "assets/minecraft/textures/blocks/" + ore + "_ore.png";
                check("textures: 1.17's " + ore + " ore with Caves & Cliffs", "1.17".equals(String.valueOf(
                        TextureLayers.layerFor(f, GameVersion.parse("1.18.2")))), TextureLayers.layerFor(f, GameVersion.parse("1.18.2")));
            }
            check("textures: 1.17's coal ore still with Tricky Trials", "1.17".equals(String.valueOf(
                    TextureLayers.layerFor(COAL_ORE, GameVersion.parse("1.21.11")))));
            check("textures: a 1.14 animation stays with its image",
                    "1.14".equals(String.valueOf(TextureLayers.layerFor("assets/minecraft/textures/blocks/magma.png.mcmeta",
                            GameVersion.parse("1.21.11")))));
            check("textures: no .mcmeta for an image whose layer has none", TextureLayers.layerFor(COAL_ORE + ".mcmeta",
                    GameVersion.parse("1.21.11")) == null);
            check("textures: the spawn egg layer has every 26.3 egg (88)", "1.21.5".equals(String.valueOf(TextureLayers.layerFor(
                    "assets/minecraft/textures/item/zombified_piglin_spawn_egg.png", GameVersion.parse("1.21.5"))))
                    && TextureLayers.all().get(TextureLayers.all().size() - 2).files().size() == 88,
                    TextureLayers.all().get(TextureLayers.all().size() - 2).files().size());
            check("textures: no egg textures before 1.21.5", TextureLayers.layerFor(
                    "assets/minecraft/textures/item/zombie_spawn_egg.png", GameVersion.parse("1.21.4")) == null);
            check("textures: 1.12.2 mobs' eggs by their names today", "minecraft:zombified_piglin_spawn_egg".equals(
                    com.exiledradio.backportcore.creative.CreativePlacement.modernEggId(new net.minecraft.util.ResourceLocation("zombie_pigman")))
                    && "minecraft:iron_golem_spawn_egg".equals(com.exiledradio.backportcore.creative.CreativePlacement.modernEggId(
                            new net.minecraft.util.ResourceLocation("villager_golem")))
                    && com.exiledradio.backportcore.creative.CreativePlacement.modernEggId(new net.minecraft.util.ResourceLocation("somemod", "fox")) == null);
            check("textures: water is never replaced", TextureLayers.layerFor("assets/minecraft/textures/blocks/water_still.png",
                    GameVersion.parse("26.3")) == null);

            // every listed file is in the jar and reads
            int images = 0;
            List<String> bad = new ArrayList<>();
            for (TextureLayers.Layer layer : TextureLayers.all()) {
                for (String f : layer.files()) {
                    try (InputStream in = TextureLayerTests.class.getResourceAsStream(layer.resource(f))) {
                        if (in == null) {
                            bad.add(layer + ":" + f + " missing");
                        } else if (f.endsWith(".png")) {
                            BufferedImage im = ImageIO.read(in);
                            if (im == null || im.getWidth() <= 0) {
                                bad.add(layer + ":" + f + " unreadable");
                            }
                            images++;
                        } else if (f.endsWith(".mcmeta")) {
                            new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8));
                        }
                    } catch (Exception ex) {
                        bad.add(layer + ":" + f + " " + ex);
                    }
                }
            }
            check("textures: every layer file is in the jar and reads", bad.isEmpty() && images > 800,
                    images + " images" + (bad.isEmpty() ? "" : ", " + bad.subList(0, Math.min(5, bad.size()))));
        });
    }
}

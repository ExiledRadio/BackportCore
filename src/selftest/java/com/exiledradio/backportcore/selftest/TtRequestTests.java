package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.advancement.AdventuringTime;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.GraphicsPreset;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.terrain.ModernGeneration;
import com.exiledradio.backportcore.water.WaterColors;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraftforge.fml.common.Loader;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Core 1.3.2's answers to Trails &amp; Tales Backport's requests 10.5, 10.6, 10.8 and 11.2 and Wild Update Backport's
 * request 10, as far as a server sees them: the version gates, Narrator Hotkey's option, the presets' blur scale,
 * Adventuring Time's lists (against the files in vanilla's jars) and its criterion granted to a player, and the water
 * colors.
 */
final class TtRequestTests {

    private TtRequestTests() {
    }

    private static GameVersion v(String s) {
        return GameVersion.parse(s);
    }

    /** 1.20.6's adventuring_time.json, its 53 criteria in its order. */
    static final List<String> VANILLA_1206 = Arrays.asList("badlands", "bamboo_jungle", "beach", "birch_forest", "cherry_grove", "cold_ocean",
            "dark_forest", "deep_cold_ocean", "deep_dark", "deep_frozen_ocean", "deep_lukewarm_ocean", "deep_ocean", "desert", "dripstone_caves",
            "eroded_badlands", "flower_forest", "forest", "frozen_ocean", "frozen_peaks", "frozen_river", "grove", "ice_spikes", "jagged_peaks",
            "jungle", "lukewarm_ocean", "lush_caves", "mangrove_swamp", "meadow", "mushroom_fields", "ocean", "old_growth_birch_forest",
            "old_growth_pine_taiga", "old_growth_spruce_taiga", "plains", "river", "savanna", "savanna_plateau", "snowy_beach", "snowy_plains",
            "snowy_slopes", "snowy_taiga", "sparse_jungle", "stony_peaks", "stony_shore", "sunflower_plains", "swamp", "taiga", "warm_ocean",
            "windswept_forest", "windswept_gravelly_hills", "windswept_hills", "windswept_savanna", "wooded_badlands");

    static void schedule() {
        then(1, "1.20.5 presentation gates", world -> {
            check("panorama: 1.20.4 still nods, 1.20.5 only turns", !VanillaRevisions.panoramaSpinOnly(v("1.20.4"))
                    && VanillaRevisions.panoramaSpinOnly(v("1.20.5")) && VanillaRevisions.panoramaSpinOnly(v("26.3")));
            check("menus: 1.20.5's backgrounds from 1.20.5", !VanillaRevisions.menuBackgrounds(v("1.20.4"))
                    && VanillaRevisions.menuBackgrounds(v("1.20.5")) && VanillaRevisions.menuBackgrounds(v("1.21.11")));
            check("menus: not at the self-test's newest (1.16)", !VanillaRevisions.menuBackgrounds(Presentation.newest()), Presentation.newest());
            check("narrator hotkey: Core's option from 1.20.2, on, Accessibility, after Hide Splash Texts",
                    Presentation.option("backportcore:narrator_hotkey") == Presentation.NARRATOR_HOTKEY
                            && v("1.20.2").equals(Presentation.NARRATOR_HOTKEY.since()) && Presentation.NARRATOR_HOTKEY.get()
                            && Presentation.NARRATOR_HOTKEY.screen() == OptionScreen.ACCESSIBILITY
                            && Presentation.HIDE_SPLASH_TEXTS.id().equals(Presentation.NARRATOR_HOTKEY.after())
                            && VanillaRevisions.narratorHotkeyOption(v("1.20.2")) && !VanillaRevisions.narratorHotkeyOption(v("1.20.1")));
            check("narrator hotkey: hidden below 1.20.2", !Presentation.NARRATOR_HOTKEY.isShown());
            check("presets: blur 0 to 10 for a 0 to 100% slider takes a tenth", GraphicsPreset.toSlider("menu_background_blurriness", 5, 1.0) == 0.5
                    && GraphicsPreset.toSlider("menu_background_blurriness", 2, 1.0) == 0.2
                    && GraphicsPreset.toSlider("menu_background_blurriness", 5, 10.0) == 5.0
                    && GraphicsPreset.toSlider("weather_radius", 5, 10.0) == 5.0);
            check("presets: and back, so Fancy is not taken for Custom", GraphicsPreset.fromSlider("menu_background_blurriness", 0.5, 1.0) == 5.0
                    && GraphicsPreset.fromSlider("menu_background_blurriness", 0.2, 1.0) == 2.0
                    && GraphicsPreset.fromSlider("menu_background_blurriness", 0.5, 1.0)
                    == Double.parseDouble(GraphicsPreset.values("fancy").get("menu_background_blurriness")));
        });

        then(1, "Adventuring Time lists", world -> {
            check("lists: none added before 1.13", AdventuringTime.listAt(v("1.12.2")).isEmpty());
            check("lists: 1.13's six oceans", AdventuringTime.listAt(v("1.13")).equals(Arrays.asList("cold_ocean", "deep_cold_ocean",
                    "deep_frozen_ocean", "deep_lukewarm_ocean", "lukewarm_ocean", "warm_ocean")), AdventuringTime.listAt(v("1.13")));
            check("lists: 1.14's bamboo jungle", AdventuringTime.listAt(v("1.17.1")).size() == 7 && AdventuringTime.listAt(v("1.16")).contains("bamboo_jungle"));
            check("lists: 1.18.2's 50", AdventuringTime.listAt(v("1.18.2")).size() == 50, AdventuringTime.listAt(v("1.18.2")).size());
            check("lists: 1.19's 52, with the deep dark and the mangrove swamp", AdventuringTime.listAt(v("1.19")).size() == 52
                    && AdventuringTime.listAt(v("1.19")).containsAll(Arrays.asList("deep_dark", "mangrove_swamp")));
            check("lists: 1.20.6's, exactly", AdventuringTime.listAt(v("1.20.6")).equals(VANILLA_1206), AdventuringTime.listAt(v("1.20.6")));
            check("lists: 1.21.4's 54, with the pale garden", AdventuringTime.listAt(v("1.21.4")).size() == 54
                    && AdventuringTime.listAt(v("1.21.4")).contains("pale_garden") && !AdventuringTime.listAt(v("1.21.3")).contains("pale_garden"));
            check("since: per biome", "1.20".equals(AdventuringTime.since("cherry_grove")) && "1.13".equals(AdventuringTime.since("warm_ocean"))
                    && "1.18".equals(AdventuringTime.since("meadow")) && AdventuringTime.since("plains") != null
                    && AdventuringTime.since("crimson_forest") == null);

            Set<String> base = new HashSet<String>(Arrays.asList("minecraft:plains", "minecraft:badlands", "minecraft:ocean"));
            Set<String> registered = new HashSet<String>(Arrays.asList("minecraft:cherry_grove", "minecraft:warm_ocean", "minecraft:mangrove_swamp",
                    "minecraft:pale_garden", "minecraft:ocean", "othermod:lavender_field"));
            check("1.12 terrain: the 1.20 backports' biomes are added, not another mod's", AdventuringTime.additions(v("1.20.5"), registered, base)
                    .equals(Arrays.asList("minecraft:cherry_grove", "minecraft:mangrove_swamp", "minecraft:warm_ocean")),
                    AdventuringTime.additions(v("1.20.5"), registered, base));
            check("1.12 terrain: the pale garden only from 1.21.4", AdventuringTime.additions(v("1.21.4"), registered, base).contains("minecraft:pale_garden")
                    && !AdventuringTime.additions(v("1.21.3"), registered, base).contains("minecraft:pale_garden"));
            check("1.12 terrain: below 1.13 nothing", AdventuringTime.additions(v("1.12.2"), registered, base).isEmpty());

            ModernGeneration noDeepDark = new ModernGeneration() {
                @Override
                public String generationVersion() {
                    return "1.18.2";
                }

                @Override
                public String biomeId(int x, int y, int z) {
                    return null;
                }

                @Override
                public boolean generates(String biomeId) {
                    return !"minecraft:deep_dark".equals(biomeId);
                }
            };
            Set<String> modern = new HashSet<String>(Arrays.asList("minecraft:deep_dark", "minecraft:mangrove_swamp", "minecraft:cherry_grove"));
            List<String> m = AdventuringTime.modernList(v("1.20.5"), noDeepDark, modern);
            check("1.18 terrain: 1.18.2's 50, then the later biomes the generator makes and a backport has", m.size() == 52
                    && m.contains("minecraft:meadow") && m.contains("minecraft:cherry_grove") && m.contains("minecraft:mangrove_swamp")
                    && !m.contains("minecraft:deep_dark") && !m.contains("minecraft:pale_garden"), m);
            check("1.18 terrain: none later without the backports", AdventuringTime.modernList(v("1.20.5"), noDeepDark,
                    Collections.<String>emptySet()).size() == 50);
        });

        then(1, "Adventuring Time in this world", world -> {
            Advancement adv = world.getMinecraftServer().getAdvancementManager().getAdvancement(AdventuringTime.ID);
            check("advancement: loaded", adv != null);
            check("trigger: backportcore:biome is registered", (Object) CriteriaTriggers.get(new net.minecraft.util.ResourceLocation("backportcore", "biome"))
                    == AdventuringTime.TRIGGER);
            String here = AdventuringTime.biomeAt(world, world.getSpawnPoint());
            check("trigger: the biome at spawn by today's id", here != null && here.startsWith("minecraft:"), here);
            if (adv == null) {
                return;
            }
            if (Loader.isModLoaded("trailsandtalesbackport")) {
                check("with Trails & Tales Backport: the cherry grove is a criterion", adv.getCriteria().containsKey("minecraft:cherry_grove"),
                        AdventuringTime.lastAdded);
            } else {
                check("no backport biome here: 1.12.2's 36 stay", adv.getCriteria().size() == 36 && AdventuringTime.lastAdded.isEmpty(),
                        adv.getCriteria().size() + " " + AdventuringTime.lastAdded);
                check("extend: the cherry grove added", AdventuringTime.extend(world.getMinecraftServer(), "minecraft:cherry_grove")
                        && adv.getCriteria().size() == 37 && adv.getRequirements().length == 37);
            }
            // A plain player with no connection: Forge's PlayerAdvancements grants a FakePlayer nothing.
            EntityPlayerMP player = new EntityPlayerMP(world.getMinecraftServer(), world,
                    new GameProfile(UUID.nameUUIDFromBytes("backportcore-selftest-adventurer".getBytes()), "adventurer"),
                    new PlayerInteractionManager(world));
            player.getAdvancements().reload();
            AdventuringTime.TRIGGER.trigger(player, "minecraft:mangrove_swamp");
            AdvancementProgress progress = player.getAdvancements().getProgress(adv);
            check("player: another biome does not meet it", progress.getCriterionProgress("minecraft:cherry_grove") != null
                    && !progress.getCriterionProgress("minecraft:cherry_grove").isObtained());
            AdventuringTime.TRIGGER.trigger(player, "minecraft:cherry_grove");
            check("player: standing in a cherry grove meets the criterion", progress.getCriterionProgress("minecraft:cherry_grove").isObtained());
            check("player: the advancement is not done by one biome", !progress.isDone());
        });

        then(1, "underwater fog colors", world -> {
            check("fog: the cherry grove's, by today's id", Arrays.equals(WaterColors.of("minecraft:cherry_grove"), new int[] {0x5DB7EF, 0x5DB7EF}));
            check("fog: the mangrove swamp's (Wild's request)", Arrays.equals(WaterColors.of("minecraft:mangrove_swamp"), new int[] {0x3A7A6A, 0x4D7A60}));
            check("fog: the pale garden's (1.21.4)", Arrays.equals(WaterColors.of("minecraft:pale_garden"), new int[] {0x76889D, 0x556980}));
            check("fog: 1.12.2's swamp is today's swamp", Arrays.equals(WaterColors.of(Biomes.SWAMPLAND), new int[] {0x617B64, 0x232317}));
            check("fog: 1.12.2's cold taiga is today's snowy taiga", Arrays.equals(WaterColors.of(Biomes.COLD_TAIGA), new int[] {0x3D57D6, 0x050533}));
            check("fog: plains and deserts are the defaults", Arrays.equals(WaterColors.of(Biomes.PLAINS), new int[] {0x3F76E4, 0x050533})
                    && WaterColors.fog(Biomes.DESERT_HILLS) == WaterColors.DEFAULT_FOG);
            check("fog: a vanilla id with the defaults", Arrays.equals(WaterColors.of("minecraft:deep_dark"), new int[] {0x3F76E4, 0x050533}));
            check("fog: another mod's id is not known", WaterColors.of("othermod:lavender_field") == null);
            Biome mesa = Biomes.MESA_CLEAR_ROCK;
            WaterColors.register(mesa, 0x123456, 0xABCDEF);
            check("fog: a registration wins", Arrays.equals(WaterColors.of(mesa), new int[] {0x123456, 0xABCDEF}));
            BlockPos spawn = world.getSpawnPoint();
            check("fog: at a block, its biome's", Arrays.equals(WaterColors.at(world, spawn), WaterColors.of(world.getBiome(spawn))),
                    world.getBiome(spawn).getRegistryName());
        });
    }
}

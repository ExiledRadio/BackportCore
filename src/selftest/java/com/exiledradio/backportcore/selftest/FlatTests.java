package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.locate.StructureLocate;
import com.exiledradio.backportcore.structure.ScatteredPlacement;
import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.structure.Structures;
import com.exiledradio.backportcore.worldgen.FlatWorlds;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Superflat worlds generate only what their preset lists ({@link FlatWorlds}).
 *
 * <p>{@link #schedule} checks the rules in the suite's own world by making its world info say superflat for a moment.
 * {@link #scheduleFlat} ({@code -PselftestOnly=flat}, from {@code tools/flat/selftest.sh}) runs in a real superflat
 * world: the test structure, which starts on every candidate chunk of any Overworld, has none there, and
 * {@code /locate} finds none.
 */
final class FlatTests {

    /** A kind asked for the first time while the world says superflat. */
    private static final String FAKE_KIND = "backportcore_selftest_flat";
    private static int factoryCalls;

    private FlatTests() {
    }

    static void init() {
        Structures.register("backportcore_selftest_flat_locator", (world, from, radius) -> from);
    }

    static void schedule() {
        then(1, "flat: rules", FlatTests::rules);
    }

    private static void rules(WorldServer world) throws Exception {
        check("flat: the suite's world is not superflat", !FlatWorlds.isFlat(world));
        check("flat: a world that is not superflat allows everything", FlatWorlds.decoration(world)
                && FlatWorlds.structure(world, Structures.TRIAL_CHAMBERS) && FlatWorlds.structureId(world, "minecraft:trial_chambers"));

        WorldInfo info = world.getWorldInfo();
        Field type = ReflectionHelper.findField(WorldInfo.class, "terrainType", "field_76098_b");
        Field options = ReflectionHelper.findField(WorldInfo.class, "generatorOptions", "field_82576_c");
        Field features = ReflectionHelper.findField(WorldInfo.class, "mapFeaturesEnabled", "field_76112_r");
        Object oldType = type.get(info);
        Object oldOptions = options.get(info);
        boolean oldFeatures = features.getBoolean(info);
        try {
            type.set(info, WorldType.FLAT);
            features.setBoolean(info, true);

            // The default preset: villages only.
            preset(world, options, info, "3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;village");
            check("flat: default preset is superflat", FlatWorlds.isFlat(world));
            check("flat: default preset has no decoration", !FlatWorlds.decoration(world));
            check("flat: default preset has villages", FlatWorlds.structure(world, Structures.VILLAGE)
                    && FlatWorlds.structureId(world, "minecraft:village_plains") && FlatWorlds.structureId(world, "#minecraft:village"));
            check("flat: default preset has no strongholds, mineshafts, temples or monuments",
                    !FlatWorlds.allows(world, FlatWorlds.STRONGHOLD) && !FlatWorlds.structure(world, "mineshaft")
                            && !FlatWorlds.structure(world, "desert_pyramid") && !FlatWorlds.structureId(world, "minecraft:monument"));
            String[] newer = {Structures.SHIPWRECK, Structures.OCEAN_RUIN, Structures.BURIED_TREASURE, Structures.RUINED_PORTAL,
                    Structures.ANCIENT_CITY, Structures.TRAIL_RUINS, Structures.TRIAL_CHAMBERS, Structures.PILLAGER_OUTPOST, "mansion"};
            boolean none = true;
            for (String kind : newer) {
                none &= !FlatWorlds.structure(world, kind) && !FlatWorlds.structureId(world, "minecraft:" + kind);
            }
            check("flat: no structure newer than 1.12 (outposts included), nor mansions", none);
            check("flat: other namespaces' structures are off", !FlatWorlds.structureId(world, "somemod:tower"));

            // Registered only now: Core's driver asks every kind in each chunk it populates.
            ScatteredStructure.register(FAKE_KIND, w -> {
                factoryCalls++;
                return new Never();
            });
            check("flat: a kind first asked in superflat has no instance", ScatteredStructure.of(world, FAKE_KIND) == null
                    && factoryCalls == 0, "factory calls " + factoryCalls);
            check("flat: a registered locator answers null", Structures.nearest(world, "backportcore_selftest_flat_locator",
                    BlockPos.ORIGIN, 8) == null);
            check("flat: /locate finds no test structure", StructureLocate.nearest(world, world.getSpawnPoint(), LocateTests.TEST) == null);

            // Everything listed, structures off: decoration yes, villages no (as 1.12's generator).
            preset(world, options, info, "3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;village,decoration,stronghold,biome_1,dungeon");
            check("flat: decoration listed", FlatWorlds.decoration(world) && FlatWorlds.allows(world, FlatWorlds.DUNGEON)
                    && !FlatWorlds.allows(world, FlatWorlds.LAKE));
            check("flat: stronghold and temples listed", FlatWorlds.structure(world, "stronghold")
                    && FlatWorlds.structure(world, "swamp_hut"));
            check("flat: a preset never lists newer structures", !FlatWorlds.structure(world, Structures.TRIAL_CHAMBERS));
            features.setBoolean(info, false);
            check("flat: structures off means no villages", !FlatWorlds.structure(world, Structures.VILLAGE)
                    && FlatWorlds.decoration(world));
            features.setBoolean(info, true);

            // A preset with no option section gets villages, as does an empty one (the default preset).
            preset(world, options, info, "3;minecraft:bedrock,minecraft:stone;1");
            check("flat: no options section means villages", FlatWorlds.structure(world, Structures.VILLAGE)
                    && !FlatWorlds.decoration(world));
            preset(world, options, info, "3;minecraft:air;1;decoration");
            check("flat: an air preset decorates nothing", !FlatWorlds.decoration(world));
            preset(world, options, info, "3;minecraft:air;127;decoration");
            check("flat: an air preset in The Void decorates", FlatWorlds.decoration(world));
            preset(world, options, info, "");
            check("flat: empty options read as the default preset", FlatWorlds.structure(world, Structures.VILLAGE)
                    && !FlatWorlds.decoration(world));
        } finally {
            type.set(info, oldType);
            options.set(info, oldOptions);
            features.setBoolean(info, oldFeatures);
            FlatWorlds.forget(world);
        }
        check("flat: the world is itself again", !FlatWorlds.isFlat(world) && FlatWorlds.decoration(world));
        check("flat: the Nether and End are never superflat", !FlatWorlds.isFlat(world.getMinecraftServer().getWorld(-1))
                && !FlatWorlds.isFlat(world.getMinecraftServer().getWorld(1)));
    }

    private static void preset(World world, Field options, WorldInfo info, String preset) throws Exception {
        options.set(info, preset);
        FlatWorlds.forget(world);
    }

    // ---- a real superflat world

    static void scheduleFlat() {
        then(1, "flat world: populate around spawn", world -> {
            check("flat world: superflat", FlatWorlds.isFlat(world), world.getWorldInfo().getGeneratorOptions());
            BlockPos spawn = world.getSpawnPoint();
            int cx = spawn.getX() >> 4;
            int cz = spawn.getZ() >> 4;
            for (int x = cx - 6; x <= cx + 6; x++) {
                for (int z = cz - 6; z <= cz + 6; z++) {
                    world.getChunkProvider().provideChunk(x, z);
                }
            }
        });
        then(5, "flat world: no structures", world -> {
            String options = world.getWorldInfo().getGeneratorOptions();
            check("flat world: decoration as the preset says", FlatWorlds.decoration(world) == options.contains("decoration"), options);
            check("flat world: the test structure has no instance", ScatteredStructure.of(world, TestStructure.KIND) == null);
            check("flat world: /locate finds no test structure",
                    StructureLocate.nearest(world, world.getSpawnPoint(), LocateTests.TEST) == null);
            check("flat world: the locator finds none", Structures.nearest(world, TestStructure.KIND, world.getSpawnPoint(), 64) == null);
        });
    }

    /** Never starts; only its factory call is counted. */
    private static final class Never extends ScatteredStructure {
        Never() {
            super("BCST_Flat", new ScatteredPlacement(8, 2, 5));
        }

        @Override
        protected boolean canStartAt(World world, int chunkX, int chunkZ) {
            return false;
        }

        @Override
        protected StructureStart getStructureStart(int chunkX, int chunkZ) {
            throw new IllegalStateException();
        }
    }
}

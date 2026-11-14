package com.exiledradio.backportcore.worldgen;

import com.exiledradio.backportcore.structure.Structures;

import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.FlatGeneratorInfo;
import net.minecraft.world.gen.FlatLayerInfo;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Superflat worlds generate only what their preset asks for, and the family follows it.
 *
 * <p>1.12.2's {@code ChunkGeneratorFlat} reads its preset's options ({@link FlatGeneratorInfo#getWorldFeatures}):
 * {@link #DECORATION} runs the biome's decorator (flowers, grass, trees, ores and every other feature),
 * {@link #VILLAGE}, {@link #STRONGHOLD}, {@link #MINESHAFT}, {@link #TEMPLES} ({@code biome_1}) and
 * {@link #MONUMENT} add those structures (only with structures turned on), and {@link #DUNGEON}, {@link #LAKE} and
 * {@link #LAVA_LAKE} add those. The default preset has {@code village} only.
 *
 * <p>Structures newer than 1.12 have no option. Today's superflat generates none of them unless its preset lists them
 * ({@code structure_overrides}), and every default preset lists villages at most, so here they never generate in a
 * superflat world. Pillager outposts are among them: today's default preset has villages and no outposts.
 *
 * <p>For a backport:
 * <pre>
 * // an IWorldGenerator or PopulateChunkEvent handler for a feature (DecorateBiomeEvent only fires with decoration)
 * if (!FlatWorlds.decoration(world)) return;
 * // a structure of its own
 * if (!FlatWorlds.structure(world, Structures.TRIAL_CHAMBERS)) return;
 * </pre>
 * {@link com.exiledradio.backportcore.structure.ScatteredStructure} kinds, {@link Structures} locators and
 * {@code /locate structure} already ask. Only the Overworld's own superflat type counts: other dimensions and other
 * world types (Caves &amp; Cliffs Backport's, a mod's flat-looking one) are never superflat here.
 */
public final class FlatWorlds {

    public static final String DECORATION = "decoration";
    public static final String VILLAGE = "village";
    public static final String STRONGHOLD = "stronghold";
    public static final String MINESHAFT = "mineshaft";
    /** Desert and jungle temples, witch huts and igloos. */
    public static final String TEMPLES = "biome_1";
    public static final String MONUMENT = "oceanmonument";
    public static final String DUNGEON = "dungeon";
    public static final String LAKE = "lake";
    public static final String LAVA_LAKE = "lava_lake";

    private static final Map<World, FlatGeneratorInfo> INFO = new WeakHashMap<World, FlatGeneratorInfo>();

    private FlatWorlds() {
    }

    /** @return true for the Overworld (or a copy of it) of a superflat world */
    public static boolean isFlat(@Nullable World world) {
        return world != null && world.provider != null && world.getWorldInfo() != null
                && world.getWorldInfo().getTerrainType() == WorldType.FLAT
                && world.provider.getDimensionType() == DimensionType.OVERWORLD;
    }

    /**
     * @return the superflat preset the world was made with, as its generator reads it; null for a world that is not
     * superflat
     */
    @Nullable
    public static synchronized FlatGeneratorInfo info(@Nullable World world) {
        if (!isFlat(world)) {
            return null;
        }
        FlatGeneratorInfo info = INFO.get(world);
        if (info == null) {
            info = FlatGeneratorInfo.createFlatGeneratorFromString(world.getWorldInfo().getGeneratorOptions());
            INFO.put(world, info);
        }
        return info;
    }

    /**
     * @param option one of the preset's option names ({@link #DECORATION}, {@link #VILLAGE}, ...)
     * @return false only in a superflat world whose preset does not have the option (for a structure option, or
     * that has structures turned off); true in every other world
     */
    public static boolean allows(@Nullable World world, String option) {
        FlatGeneratorInfo info = info(world);
        if (info == null) {
            return true;
        }
        if (!info.getWorldFeatures().containsKey(option)) {
            return false;
        }
        return !structureOption(option) || world.getWorldInfo().isMapFeaturesEnabled();
    }

    /**
     * @return false only in a superflat world without {@link #DECORATION}: no flowers, grass, trees, ores or other
     * features. As 1.12's generator, a preset of air only decorates nothing unless its biome is The Void.
     */
    public static boolean decoration(@Nullable World world) {
        if (!allows(world, DECORATION)) {
            return false;
        }
        FlatGeneratorInfo info = info(world);
        if (info == null) {
            return true;
        }
        for (FlatLayerInfo layer : info.getFlatLayers()) {
            if (layer.getLayerMaterial().getBlock() != Blocks.AIR) {
                return true;
            }
        }
        return info.getBiome() == Biome.getIdForBiome(Biomes.VOID);
    }

    /**
     * @param kind today's structure name ({@link Structures#VILLAGE}, {@link Structures#SHIPWRECK}, ...)
     * @return whether a structure of this kind may generate in this world: everywhere but superflat; in superflat
     * only 1.12's kinds the preset lists ({@link #optionFor}), never a newer one
     */
    public static boolean structure(@Nullable World world, String kind) {
        if (!isFlat(world)) {
            return true;
        }
        String option = optionFor(kind);
        return option != null && allows(world, option);
    }

    /**
     * @param id today's structure id or tag ({@code minecraft:village_plains}, {@code #minecraft:village},
     *           {@code minecraft:trial_chambers})
     * @return whether {@code /locate} may find it in this world, by {@link #structure}'s rule
     */
    public static boolean structureId(@Nullable World world, String id) {
        if (!isFlat(world)) {
            return true;
        }
        String bare = id.startsWith("#") ? id.substring(1) : id;
        bare = bare.toLowerCase(Locale.ROOT);
        if (bare.startsWith("minecraft:")) {
            bare = bare.substring("minecraft:".length());
        } else if (bare.indexOf(':') >= 0) {
            return false;
        }
        if (bare.equals("village") || bare.startsWith("village_")) {
            return structure(world, Structures.VILLAGE);
        }
        return structure(world, bare);
    }

    /**
     * @return the superflat option that turns a structure kind on, or null for a kind no preset can have (every
     * structure newer than 1.12, and woodland mansions)
     */
    @Nullable
    public static String optionFor(String kind) {
        switch (kind.toLowerCase(Locale.ROOT)) {
            case Structures.VILLAGE:
                return VILLAGE;
            case "stronghold":
                return STRONGHOLD;
            case "mineshaft":
            case "mineshaft_mesa":
                return MINESHAFT;
            case "monument":
            case "ocean_monument":
                return MONUMENT;
            case "desert_pyramid":
            case "jungle_pyramid":
            case "jungle_temple":
            case "swamp_hut":
            case "igloo":
                return TEMPLES;
            default:
                return null;
        }
    }

    private static boolean structureOption(String option) {
        return option.equals(VILLAGE) || option.equals(STRONGHOLD) || option.equals(MINESHAFT)
                || option.equals(TEMPLES) || option.equals(MONUMENT);
    }

    /** For tests that make a world's preset change under it. */
    public static synchronized void forget(World world) {
        INFO.remove(world);
    }
}

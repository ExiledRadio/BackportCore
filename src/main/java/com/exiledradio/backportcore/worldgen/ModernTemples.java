package com.exiledradio.backportcore.worldgen;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.structure.ScatteredPlacement;

import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * 1.12.2's temple generator on the grids of 1.13 and later: the desert pyramid, jungle temple, witch hut and
 * igloo each have their own grid where 1.12.2 shares one (salt 14357617) and picks the temple by biome.
 *
 * <p>1.13 to 1.16 ({@code RandomScatteredFeature.isFeatureChunk}, {@code ChunkGenerator.createStructures}): a
 * temple starts where the chunk is its region's candidate on that temple's grid and the biome at the version's
 * point ({@link StructurePlacements#biomeAt}) lists that temple. The biome lists are the same from 1.13 to
 * 1.16.5: desert and desert hills, jungle and jungle hills (and 1.14's bamboo jungles, which the backport that
 * adds them registers), swamp, snowy tundra and snowy taiga. The grids are each kind's newest registered one
 * ({@link StructurePlacements#grid}), else 32/8 on the kind's 1.13 salt. Saved and loaded as vanilla's
 * ("Temple"), so the temples of older chunks stay.
 */
public final class ModernTemples extends MapGenScatteredFeature {

    static final String[] KINDS = {StructurePlacements.DESERT_PYRAMID, StructurePlacements.IGLOO,
            StructurePlacements.JUNGLE_PYRAMID, StructurePlacements.SWAMP_HUT};
    /** 1.13's salts, in {@link #KINDS} order: the fallback when no installed version registered a kind. */
    private static final int[] SALTS = {14357617, 14357618, 14357619, 14357620};

    private static final Field SPACING = ReflectionHelper.findField(MapGenScatteredFeature.class,
            "maxDistanceBetweenScatteredFeatures", "field_82669_g");

    private final ScatteredPlacement[] grids = new ScatteredPlacement[KINDS.length];

    /** Reads the replaced generator's spacing (a customized world's temple spacing) for kinds nobody registered. */
    public ModernTemples(MapGenScatteredFeature replaced) {
        int spacing = 32;
        try {
            spacing = SPACING.getInt(replaced);
        } catch (IllegalAccessException e) {
            BackportCore.LOGGER.warn("Temples: could not read the temple spacing; using 32", e);
        }
        for (int i = 0; i < KINDS.length; i++) {
            this.grids[i] = StructurePlacements.placement(KINDS[i], new ScatteredPlacement(spacing, 8, SALTS[i]));
        }
    }

    /** @return the temple a biome lists from 1.13 on, as an index into {@link #KINDS}, or -1 for none */
    static int templeOf(@Nullable Biome biome) {
        if (biome == null) {
            return -1;
        }
        if (biome == Biomes.DESERT || biome == Biomes.DESERT_HILLS || StructurePlacements.isExtraBiome(KINDS[0], biome)) {
            return 0;
        }
        if (biome == Biomes.ICE_PLAINS || biome == Biomes.COLD_TAIGA || StructurePlacements.isExtraBiome(KINDS[1], biome)) {
            return 1;
        }
        if (biome == Biomes.JUNGLE || biome == Biomes.JUNGLE_HILLS || StructurePlacements.isExtraBiome(KINDS[2], biome)) {
            return 2;
        }
        if (biome == Biomes.SWAMPLAND || StructurePlacements.isExtraBiome(KINDS[3], biome)) {
            return 3;
        }
        return -1;
    }

    @Override
    protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
        return kindAt(this.world, chunkX, chunkZ) >= 0;
    }

    /**
     * Each temple's grid first, as vanilla asks, then the biome once.
     *
     * @return the index of the temple starting in the chunk, or -1
     */
    public int kindAt(World world, int chunkX, int chunkZ) {
        int biomeKind = -2;
        long seed = world.getSeed();
        for (int i = 0; i < KINDS.length; i++) {
            if (this.grids[i].isCandidate(seed, chunkX, chunkZ)) {
                if (biomeKind == -2) {
                    biomeKind = templeOf(StructurePlacements.biomeAt(world, chunkX, chunkZ));
                }
                if (biomeKind == i) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * The start builds the temple its grid was chosen for: 1.12.2's start picks by biome, so a biome added to a
     * kind (a bamboo jungle) is handed over as that kind's vanilla biome.
     */
    @Override
    protected StructureStart getStructureStart(int chunkX, int chunkZ) {
        Biome biome = StructurePlacements.biomeAt(this.world, chunkX, chunkZ);
        int kind = templeOf(biome);
        Biome as = kind == 0 ? Biomes.DESERT : kind == 1 ? Biomes.ICE_PLAINS : kind == 2 ? Biomes.JUNGLE : kind == 3 ? Biomes.SWAMPLAND : biome;
        return new MapGenScatteredFeature.Start(this.world, this.rand, chunkX, chunkZ, as);
    }

    /** Today's ids of {@link #KINDS}, in that order. */
    private static final String[] IDS = {"minecraft:desert_pyramid", "minecraft:igloo", "minecraft:jungle_pyramid",
            "minecraft:swamp_hut"};

    /** Set while {@link #of} asks the world's temple generator who it is. */
    private static final ThreadLocal<ModernTemples[]> PROBE = new ThreadLocal<ModernTemples[]>();

    /** @return today's structure ids of the four kinds, in the order {@link #kindAt} and {@link #grid} number them */
    public static String[] ids() {
        return IDS.clone();
    }

    /** @return a kind's grid in this world */
    public ScatteredPlacement grid(int kind) {
        return this.grids[kind];
    }

    /**
     * @return the world's temple generator if it is this class, else null (1.12.2's own, another mod's, or none
     *         where structures are off). Asks the world's own {@code /locate Temple} route, so whichever chunk
     *         generator the world has is asked; nothing is searched or loaded.
     */
    @javax.annotation.Nullable
    public static ModernTemples of(World world) {
        ModernTemples[] box = new ModernTemples[1];
        PROBE.set(box);
        try {
            world.findNearestStructure("Temple", BlockPos.ORIGIN, false);
        } finally {
            PROBE.remove();
        }
        return box[0];
    }

    /** {@code /locate Temple}: the nearest start of any of the four kinds. */
    @Override
    public BlockPos getNearestStructurePos(World world, BlockPos pos, boolean findUnexplored) {
        this.world = world;
        ModernTemples[] probe = PROBE.get();
        if (probe != null) {
            probe[0] = this;
            return null;
        }
        BlockPos best = null;
        for (int i = 0; i < KINDS.length; i++) {
            final int kind = i;
            ChunkPos found = this.grids[i].nearest(world.getSeed(), pos, this.grids[i].spacing() * 100,
                    c -> templeOf(StructurePlacements.biomeAt(world, c.x, c.z)) == kind);
            if (found != null) {
                BlockPos p = new BlockPos((found.x << 4) + 8, 64, (found.z << 4) + 8);
                if (best == null || p.distanceSq(pos) < best.distanceSq(pos)) {
                    best = p;
                }
            }
        }
        return best;
    }
}

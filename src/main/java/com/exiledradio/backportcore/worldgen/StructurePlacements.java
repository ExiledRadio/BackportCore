package com.exiledradio.backportcore.worldgen;

import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.structure.ScatteredPlacement;
import com.exiledradio.backportcore.terrain.RtgBiomes;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Where each structure goes on a seed map, at the newest installed backport's version: the per-structure
 * placement table of the family's seed parity (a world from seed X shows the structures Chunkbase shows for
 * seed X at that version).
 *
 * <p>Each backport registers its version's numbers, read from that version's decompiled code, in pre-init:
 * <pre>
 * StructurePlacements.version("1.13")
 *         .biomePoint(BiomePoint.BLOCK_9_9)
 *         .grid(Structures.SHIPWRECK, 16, 8, 165745295)
 *         .grid(StructurePlacements.DESERT_PYRAMID, 32, 8, 14357617)
 *         .strongholdBiome("minecraft:mushroom_island_shore", true);
 * </pre>
 * For every structure the newest installed version that names it wins: with Update Aquatic (1.13) and the
 * Nether backport (1.16) installed, shipwrecks use 1.16's 24/4 grid and 1.16's biome point. With no
 * backport installed nothing is registered, the newest version is 1.12.2, and every world generates exactly
 * as 1.12.2's.
 *
 * <p>Registering a version here also counts it for {@link #newest()}, beside the presentation module's
 * versions ({@link Presentation#newest()}): the shared 1.13+ worldgen rules Core applies (the biome search
 * of strongholds and spawn, the hills layer's seeding, 1.15's biome zoom, temples on four grids) follow the
 * newer of the two.
 */
public final class StructurePlacements {

    static final Logger LOG = LogManager.getLogger("Backport Core");

    /** Kinds of 1.12.2's own structures that have their own grid since 1.13 (today's names). */
    public static final String DESERT_PYRAMID = "desert_pyramid";
    public static final String JUNGLE_PYRAMID = "jungle_pyramid";
    public static final String SWAMP_HUT = "swamp_hut";
    public static final String IGLOO = "igloo";
    public static final String OCEAN_MONUMENT = "ocean_monument";
    public static final String MANSION = "mansion";

    /** Where a structure's start reads its one biome. */
    public enum BiomePoint {
        /** 1.12.2: each generator's own rule (temples the block biome at (8, 8), villages the 1:4 cell there). */
        LEGACY,
        /**
         * 1.13 to 1.15 ({@code ChunkGenerator.createStructures}): the block-scale biome at block (9, 0, 9) of
         * the chunk. From 1.15 on the block scale is 1.15's zoom (Core's {@code FuzzyZoomLayer}).
         */
        BLOCK_9_9,
        /** 1.16 on ({@code ChunkGenerator.createStructures}): the 1:4 cell at (4 x + 2, 4 z + 2) of the chunk. */
        QUART_CENTER;

        /** @return the block (on both axes) of the chunk this point reads; the middle of the 1:4 cell for 1.16's */
        public int blockOffset() {
            switch (this) {
                case QUART_CENTER:
                    return 10;
                case BLOCK_9_9:
                    return 9;
                default:
                    return 8;
            }
        }

        /** @return the biome this point reads for a start in chunk (chunkX, chunkZ); never loads a chunk */
        @Nullable
        public Biome read(BiomeProvider provider, int chunkX, int chunkZ) {
            switch (this) {
                case QUART_CENTER: {
                    Biome[] cell = provider.getBiomesForGeneration(null, (chunkX << 2) + 2, (chunkZ << 2) + 2, 1, 1);
                    return cell == null || cell.length == 0 ? null : cell[0];
                }
                case BLOCK_9_9:
                    return provider.getBiome(new BlockPos((chunkX << 4) + 9, 0, (chunkZ << 4) + 9));
                default:
                    return provider.getBiome(new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8));
            }
        }
    }

    /** One version's grid for one structure. */
    public static final class Grid {
        public final String kind;
        public final GameVersion version;
        public final ScatteredPlacement placement;

        Grid(String kind, GameVersion version, ScatteredPlacement placement) {
            this.kind = kind;
            this.version = version;
            this.placement = placement;
        }

        @Override
        public String toString() {
            return this.kind + " " + this.placement.spacing() + "/" + this.placement.separation() + " salt "
                    + this.placement.salt() + " (" + this.version + ")";
        }
    }

    /** What one version registers; every method returns this, for chaining. */
    public static final class Version {
        private final GameVersion version;

        Version(GameVersion version) {
            this.version = version;
        }

        public GameVersion version() {
            return this.version;
        }

        /** Where this version's structures read their biome (every single-biome check of the version). */
        public Version biomePoint(BiomePoint point) {
            synchronized (StructurePlacements.class) {
                POINTS.put(this.version, point);
            }
            return this;
        }

        /** A linear grid: {@code spacing} chunks per region, the candidate at least {@code separation} from the next. */
        public Version grid(String kind, int spacing, int separation, int salt) {
            return grid(kind, spacing, separation, salt, false);
        }

        /** @param triangular the candidate tends to the region's middle (monuments, mansions, end cities) */
        public Version grid(String kind, int spacing, int separation, int salt, boolean triangular) {
            Grid g = new Grid(kind, this.version, new ScatteredPlacement(spacing, separation, salt, triangular));
            synchronized (StructurePlacements.class) {
                TreeMap<GameVersion, Grid> byVersion = GRIDS.get(kind);
                if (byVersion == null) {
                    byVersion = new TreeMap<GameVersion, Grid>();
                    GRIDS.put(kind, byVersion);
                }
                byVersion.put(this.version, g);
            }
            return this;
        }

        /**
         * Whether strongholds may be placed in a biome at this version ({@code Biome.isValidStart(STRONGHOLD)},
         * and from 1.16 on only biomes the overworld's biome source lists as possible). Ruled by the newest
         * version that names the biome; biomes no version names keep 1.12.2's rule (base height above 0).
         *
         * @param biome a registry name ("minecraft:mushroom_island_shore"), or a bare path ("bamboo_jungle") for
         *              that biome whichever mod adds it
         */
        public Version strongholdBiome(String biome, boolean allowed) {
            synchronized (StructurePlacements.class) {
                TreeMap<GameVersion, Boolean> byVersion = STRONGHOLD.get(biome);
                if (byVersion == null) {
                    byVersion = new TreeMap<GameVersion, Boolean>();
                    STRONGHOLD.put(biome, byVersion);
                }
                byVersion.put(this.version, allowed);
            }
            return this;
        }

        /**
         * A biome a structure starts in from this version on, beside vanilla's own (1.14's bamboo jungle for
         * jungle temples). Same naming as {@link #strongholdBiome}.
         */
        public Version structureBiome(String kind, String biome) {
            synchronized (StructurePlacements.class) {
                Map<String, GameVersion> names = EXTRA_BIOMES.get(kind);
                if (names == null) {
                    names = new HashMap<String, GameVersion>();
                    EXTRA_BIOMES.put(kind, names);
                }
                GameVersion old = names.get(biome);
                if (old == null || this.version.compareTo(old) < 0) {
                    names.put(biome, this.version);
                }
            }
            return this;
        }
    }

    private static final TreeSet<GameVersion> VERSIONS = new TreeSet<GameVersion>();
    private static final TreeMap<GameVersion, BiomePoint> POINTS = new TreeMap<GameVersion, BiomePoint>();
    private static final Map<String, TreeMap<GameVersion, Grid>> GRIDS = new HashMap<String, TreeMap<GameVersion, Grid>>();
    private static final Map<String, TreeMap<GameVersion, Boolean>> STRONGHOLD = new HashMap<String, TreeMap<GameVersion, Boolean>>();
    private static final Map<String, Map<String, GameVersion>> EXTRA_BIOMES = new HashMap<String, Map<String, GameVersion>>();

    private StructurePlacements() {
    }

    /**
     * Declares that a backport bringing {@code version} is installed, and returns its part of the table to fill
     * in. Calling it again for the same version adds to the same part.
     */
    public static synchronized Version version(String version) {
        GameVersion v = GameVersion.parse(version);
        if (!v.isAtLeast("1.13")) {
            throw new IllegalArgumentException("a backport brings 1.13 or later, not " + version);
        }
        if (VERSIONS.add(v)) {
            ModContainer mod = Loader.instance().activeModContainer();
            LOG.info("Structure placement: {} registered by {}", v, mod == null ? "unknown" : mod.getModId());
        }
        return new Version(v);
    }

    /** @return the newest installed version: the newer of this table's and the presentation module's */
    public static GameVersion newest() {
        GameVersion p = Presentation.installedNewest();
        synchronized (StructurePlacements.class) {
            if (!VERSIONS.isEmpty() && VERSIONS.last().compareTo(p) > 0) {
                return VERSIONS.last();
            }
        }
        return p;
    }

    /** @return true when the newest installed version is {@code version} or later */
    public static boolean isAtLeast(String version) {
        return newest().isAtLeast(version);
    }

    /** @return the newest registered grid of a kind, or null when no installed version names it */
    @Nullable
    public static synchronized Grid grid(String kind) {
        TreeMap<GameVersion, Grid> byVersion = GRIDS.get(kind);
        return byVersion == null || byVersion.isEmpty() ? null : byVersion.lastEntry().getValue();
    }

    /** @return the newest registered grid of a kind, or {@code fallback} when no installed version names it */
    public static ScatteredPlacement placement(String kind, ScatteredPlacement fallback) {
        Grid g = grid(kind);
        return g == null ? fallback : g.placement;
    }

    /**
     * @return the newest installed version's biome point. A version installed without a point of its own (one
     *         that only registered with the presentation module) gets vanilla's: block (9, 9) from 1.13, the 1:4
     *         cell from 1.16; with nothing installed, {@link BiomePoint#LEGACY}
     */
    public static BiomePoint biomePoint() {
        GameVersion newest = newest();
        synchronized (StructurePlacements.class) {
            if (!POINTS.isEmpty() && POINTS.lastKey().compareTo(newest) >= 0) {
                return POINTS.lastEntry().getValue();
            }
        }
        return newest.isAtLeast("1.16") ? BiomePoint.QUART_CENTER : newest.isAtLeast("1.13") ? BiomePoint.BLOCK_9_9 : BiomePoint.LEGACY;
    }

    /** @return the biome a single-biome start check reads in this chunk, at the newest version's point */
    @Nullable
    public static Biome biomeAt(BiomeProvider provider, int chunkX, int chunkZ) {
        return biomePoint().read(provider, chunkX, chunkZ);
    }

    /**
     * @return the biome a single-biome start check reads in this chunk, at the newest version's point, as the chunk
     *         will really have it: in a Realistic Terrain Generation world the biome after RTG's per-chunk coast
     *         repair ({@link RtgBiomes#biomeAt}), which the biome provider does not know; elsewhere the provider's
     */
    @Nullable
    public static Biome biomeAt(World world, int chunkX, int chunkZ) {
        BiomePoint point = biomePoint();
        if (RtgBiomes.handles(world)) {
            int offset = point.blockOffset();
            Biome biome = RtgBiomes.biomeAt(world, (chunkX << 4) + offset, (chunkZ << 4) + offset);
            if (biome != null) {
                return biome;
            }
        }
        return point.read(world.getBiomeProvider(), chunkX, chunkZ);
    }

    /**
     * @return true, false, or null when no installed version names this biome for strongholds (keep 1.12.2's
     *         rule); a full registry name's ruling comes before its bare path's
     */
    @Nullable
    public static synchronized Boolean strongholdBiome(Biome biome) {
        ResourceLocation name = biome.getRegistryName();
        if (name == null) {
            return null;
        }
        TreeMap<GameVersion, Boolean> exact = STRONGHOLD.get(name.toString());
        if (exact != null && !exact.isEmpty()) {
            return exact.lastEntry().getValue();
        }
        TreeMap<GameVersion, Boolean> path = STRONGHOLD.get(name.getPath());
        return path == null || path.isEmpty() ? null : path.lastEntry().getValue();
    }

    /** @return true if a version added this biome to the kind's own (vanilla) biomes */
    public static synchronized boolean isExtraBiome(String kind, @Nullable Biome biome) {
        ResourceLocation name = biome == null ? null : biome.getRegistryName();
        Map<String, GameVersion> names = EXTRA_BIOMES.get(kind);
        if (name == null || names == null) {
            return false;
        }
        return names.containsKey(name.toString()) || names.containsKey(name.getPath());
    }

    /** @return a line per kind, for the log and the self-tests */
    public static synchronized String describe() {
        StringBuilder s = new StringBuilder("newest ").append(newest()).append(", biome point ").append(biomePoint());
        for (String kind : new TreeSet<String>(GRIDS.keySet())) {
            s.append("; ").append(grid(kind));
        }
        return s.toString();
    }
}

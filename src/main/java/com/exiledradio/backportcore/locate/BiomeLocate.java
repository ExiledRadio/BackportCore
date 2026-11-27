package com.exiledradio.backportcore.locate;

import com.exiledradio.backportcore.terrain.WorldHeights;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Today's {@code /locate biome}: biomes by today's ids, and today's search ({@code BiomeSource.findClosestBiome3d}).
 *
 * <h2>Ids</h2>
 * Each 1.12.2 biome answers to one of today's ids:
 * <ul>
 *   <li>vanilla's 1.12.2 biomes to the biome a world upgraded to today becomes (1.13's renames, then 1.18's merges:
 *       {@code extreme_hills} is {@code minecraft:windswept_hills}, {@code desert_hills} is {@code minecraft:desert});</li>
 *   <li>a biome another mod registers under one of today's vanilla names, in any namespace (the family's backports
 *       register theirs so), to that name in {@code minecraft:} ({@code trailsandtalesbackport:cherry_grove} is
 *       {@code minecraft:cherry_grove});</li>
 *   <li>a biome a backport names with {@link #biome}, to that id;</li>
 *   <li>any other biome to its own registry name.</li>
 * </ul>
 * Tags ({@link #tag}) are lists of ids the backports fill; Core fills none.
 *
 * <h2>Search</h2>
 * 26.x's: a square spiral of columns 32 blocks apart out to 6400 blocks, starting east of the origin and turning
 * south ({@code BlockPos.spiralAround}); in each column the heights from the origin's outward by 64 within the world
 * ({@code Mth.outFromOrigin}); the first column and height whose biome is wanted. The biome is the one at the
 * column's 1:4 cell, as today's is the noise biome at its quart: in 1.12.2's layers that is the biome map that shapes
 * terrain. A world whose generator knows better (Caves &amp; Cliffs Backport's 1.18 terrain, with its 3D biomes and
 * biomes 1.12.2 has no biome for) gives its own {@link Sampler} through {@link #source}.
 */
public final class BiomeLocate {

    /** 26.x's LocateCommand numbers. */
    public static final int RADIUS = 6400;
    public static final int HORIZONTAL = 32;
    public static final int VERTICAL = 64;

    /** Today's vanilla biomes (26.3's {@code worldgen/biome}). */
    public static final List<String> TODAY = Collections.unmodifiableList(Arrays.asList(
            "badlands", "bamboo_jungle", "basalt_deltas", "beach", "birch_forest", "cherry_grove", "cold_ocean",
            "crimson_forest", "dappled_forest", "dark_forest", "deep_cold_ocean", "deep_dark", "deep_frozen_ocean",
            "deep_lukewarm_ocean", "deep_ocean", "desert", "dripstone_caves", "end_barrens", "end_highlands", "end_midlands",
            "eroded_badlands", "flower_forest", "forest", "frozen_ocean", "frozen_peaks", "frozen_river", "grove",
            "ice_spikes", "jagged_peaks", "jungle", "lukewarm_ocean", "lush_caves", "mangrove_swamp", "meadow",
            "mushroom_fields", "nether_wastes", "ocean", "old_growth_birch_forest", "old_growth_pine_taiga",
            "old_growth_spruce_taiga", "pale_garden", "plains", "river", "savanna", "savanna_plateau", "small_end_islands",
            "snowy_beach", "snowy_plains", "snowy_slopes", "snowy_taiga", "soul_sand_valley", "sparse_jungle", "stony_peaks",
            "stony_shore", "sulfur_caves", "sunflower_plains", "swamp", "taiga", "the_end", "the_void", "warm_ocean",
            "warped_forest", "windswept_forest", "windswept_gravelly_hills", "windswept_hills", "windswept_savanna",
            "wooded_badlands"));

    /**
     * 1.12.2's biomes by registry name and the biome each is today: 1.13's renames, then where 1.18 merged the hills
     * and variants (its {@code LegacyBiomes} upgrade).
     */
    static final String[][] LEGACY = {
            {"ocean", "ocean"}, {"plains", "plains"}, {"desert", "desert"}, {"extreme_hills", "windswept_hills"},
            {"forest", "forest"}, {"taiga", "taiga"}, {"swampland", "swamp"}, {"river", "river"},
            {"hell", "nether_wastes"}, {"sky", "the_end"}, {"frozen_ocean", "frozen_ocean"}, {"frozen_river", "frozen_river"},
            {"ice_flats", "snowy_plains"}, {"ice_mountains", "snowy_plains"}, {"mushroom_island", "mushroom_fields"},
            {"mushroom_island_shore", "mushroom_fields"}, {"beaches", "beach"}, {"desert_hills", "desert"},
            {"forest_hills", "forest"}, {"taiga_hills", "taiga"}, {"smaller_extreme_hills", "windswept_hills"},
            {"jungle", "jungle"}, {"jungle_hills", "jungle"}, {"jungle_edge", "sparse_jungle"}, {"deep_ocean", "deep_ocean"},
            {"stone_beach", "stony_shore"}, {"cold_beach", "snowy_beach"}, {"birch_forest", "birch_forest"},
            {"birch_forest_hills", "birch_forest"}, {"roofed_forest", "dark_forest"}, {"taiga_cold", "snowy_taiga"},
            {"taiga_cold_hills", "snowy_taiga"}, {"redwood_taiga", "old_growth_pine_taiga"},
            {"redwood_taiga_hills", "old_growth_pine_taiga"}, {"extreme_hills_with_trees", "windswept_forest"},
            {"savanna", "savanna"}, {"savanna_rock", "savanna_plateau"}, {"mesa", "badlands"},
            {"mesa_rock", "wooded_badlands"}, {"mesa_clear_rock", "badlands"}, {"void", "the_void"},
            {"mutated_plains", "sunflower_plains"}, {"mutated_desert", "desert"},
            {"mutated_extreme_hills", "windswept_gravelly_hills"}, {"mutated_forest", "flower_forest"},
            {"mutated_taiga", "taiga"}, {"mutated_swampland", "swamp"}, {"mutated_ice_flats", "ice_spikes"},
            {"mutated_jungle", "jungle"}, {"mutated_jungle_edge", "sparse_jungle"},
            {"mutated_birch_forest", "old_growth_birch_forest"}, {"mutated_birch_forest_hills", "old_growth_birch_forest"},
            {"mutated_roofed_forest", "dark_forest"}, {"mutated_taiga_cold", "snowy_taiga"},
            {"mutated_redwood_taiga", "old_growth_spruce_taiga"}, {"mutated_redwood_taiga_hills", "old_growth_spruce_taiga"},
            {"mutated_extreme_hills_with_trees", "windswept_gravelly_hills"}, {"mutated_savanna", "windswept_savanna"},
            {"mutated_savanna_rock", "windswept_savanna"}, {"mutated_mesa", "eroded_badlands"},
            {"mutated_mesa_rock", "wooded_badlands"}, {"mutated_mesa_clear_rock", "badlands"}};

    /** What a world's biome is at a block, as today's id (null: none worth naming). */
    public interface Sampler {
        @Nullable
        String biome(int x, int y, int z);
    }

    /** A world's own sampler, or null for a world it does not generate. */
    public interface SamplerFactory {
        @Nullable
        Sampler forWorld(WorldServer world);
    }

    /** What the search found: where, and which id (a tag's member). */
    public static final class Found {
        public final BlockPos pos;
        public final String id;

        public Found(BlockPos pos, String id) {
            this.pos = pos;
            this.id = id;
        }
    }

    private static final Map<String, String> NAMED = new HashMap<String, String>();
    private static final Set<String> EXTRA_IDS = new LinkedHashSet<String>();
    private static final Map<String, Set<String>> TAGS = new LinkedHashMap<String, Set<String>>();
    private static final List<SamplerFactory> SOURCES = new ArrayList<SamplerFactory>();

    private BiomeLocate() {
    }

    // ---- registering

    /** Names a 1.12.2 biome's id for {@code /locate biome} (today's form, {@code minecraft:} if no namespace). */
    public static synchronized void biome(String id, Biome biome) {
        if (biome.getRegistryName() != null) {
            NAMED.put(biome.getRegistryName().toString(), StructureLocate.normalize(id));
        }
    }

    /** An id a world's own {@link Sampler} answers that no 1.12.2 biome has (1.18's meadow in Caves &amp; Cliffs' terrain). */
    public static synchronized void id(String id) {
        EXTRA_IDS.add(StructureLocate.normalize(id));
    }

    /** A tag of ids ({@code #minecraft:is_mountain}); adds to the tag if it exists. */
    public static synchronized void tag(String tag, String... ids) {
        String key = StructureLocate.normalize(tag.startsWith("#") ? tag.substring(1) : tag);
        Set<String> set = TAGS.get(key);
        if (set == null) {
            set = new LinkedHashSet<String>();
            TAGS.put(key, set);
        }
        for (String id : ids) {
            set.add(StructureLocate.normalize(id));
        }
    }

    /** A generator's own sampler; asked first, the newest registration first. */
    public static synchronized void source(SamplerFactory factory) {
        SOURCES.add(0, factory);
    }

    // ---- asking

    /** @return the id {@code /locate biome} knows a biome by */
    public static synchronized String id(Biome biome) {
        ResourceLocation name = biome.getRegistryName();
        if (name == null) {
            return "minecraft:plains";
        }
        String named = NAMED.get(name.toString());
        if (named != null) {
            return named;
        }
        if ("minecraft".equals(name.getNamespace())) {
            for (String[] row : LEGACY) {
                if (row[0].equals(name.getPath())) {
                    return "minecraft:" + row[1];
                }
            }
            return name.toString();
        }
        return TODAY.contains(name.getPath()) ? "minecraft:" + name.getPath() : name.toString();
    }

    /** @return every id that names a registered biome or a sampler's biome, sorted */
    public static synchronized List<String> ids() {
        Set<String> all = new TreeSet<String>(EXTRA_IDS);
        for (Biome biome : ForgeRegistries.BIOMES) {
            all.add(id(biome));
        }
        return new ArrayList<String>(all);
    }

    /** @return every tag with a member some biome answers to, as {@code #namespace:path}, sorted */
    public static synchronized List<String> tags() {
        Set<String> known = new HashSet<String>(ids());
        Set<String> all = new TreeSet<String>();
        for (Map.Entry<String, Set<String>> e : TAGS.entrySet()) {
            for (String member : e.getValue()) {
                if (known.contains(member)) {
                    all.add("#" + e.getKey());
                    break;
                }
            }
        }
        return new ArrayList<String>(all);
    }

    /** @return a tag's members (without '#'), empty for an unknown tag */
    public static synchronized Set<String> members(String tag) {
        Set<String> set = TAGS.get(StructureLocate.normalize(tag.startsWith("#") ? tag.substring(1) : tag));
        return set == null ? Collections.<String>emptySet() : new LinkedHashSet<String>(set);
    }

    /** @return true if this id names a biome, or this tag is registered */
    public static synchronized boolean known(String idOrTag) {
        String key = StructureLocate.normalize(idOrTag);
        if (key.startsWith("#")) {
            return TAGS.containsKey(key.substring(1));
        }
        return ids().contains(key);
    }

    /**
     * The nearest biome of an id or a tag's members, by 26.x's search.
     *
     * @return what was found, or null for none within 6400 blocks
     */
    @Nullable
    public static Found nearest(WorldServer world, BlockPos from, String idOrTag) {
        String key = StructureLocate.normalize(idOrTag);
        Set<String> wanted = key.startsWith("#") ? members(key) : Collections.singleton(key);
        Sampler sampler = samplerFor(world);
        if (sampler == null) {
            sampler = new LayerSampler(world.getBiomeProvider());
            // BiomeSource.possibleBiomes: in 1.12.2's layers, only a registered biome can turn up.
            Set<String> possible = new HashSet<String>();
            for (Biome biome : ForgeRegistries.BIOMES) {
                possible.add(id(biome));
            }
            wanted = new HashSet<String>(wanted);
            wanted.retainAll(possible);
        }
        if (wanted.isEmpty()) {
            return null;
        }
        return search(sampler, from, WorldHeights.minY(world), WorldHeights.maxYExclusive(world) - 1, wanted);
    }

    /**
     * @return the world's own biome sampler (a generator's, {@link #source}), or null where 1.12.2's biomes are the
     * answer; for a structure's biome check
     * @since 1.4.5
     */
    @Nullable
    public static Sampler sampler(WorldServer world) {
        return samplerFor(world);
    }

    /**
     * @return 1.12.2's own biomes as a sampler (the column's biome at its 1:4 cell, as the search reads it), for a
     * backport whose {@link #source} answers a few places itself and leaves the rest to the world's layers
     * @since 1.4.5
     */
    public static Sampler layers(WorldServer world) {
        return new LayerSampler(world.getBiomeProvider());
    }

    /**
     * @return the sampler the sources registered before {@code self} give this world, else 1.12.2's layers: for a source
     *     that answers a few places and leaves the rest to the others (two backports' underground biomes in one world)
     * @since 1.5.0
     */
    public static synchronized Sampler next(SamplerFactory self, WorldServer world) {
        int i = SOURCES.indexOf(self);
        for (int j = i + 1; j < SOURCES.size(); j++) {
            Sampler sampler = SOURCES.get(j).forWorld(world);
            if (sampler != null) {
                return sampler;
            }
        }
        return layers(world);
    }

    @Nullable
    private static synchronized Sampler samplerFor(WorldServer world) {
        for (SamplerFactory factory : SOURCES) {
            Sampler sampler = factory.forWorld(world);
            if (sampler != null) {
                return sampler;
            }
        }
        return null;
    }

    /** 26.x's {@code findClosestBiome3d(origin, 6400, 32, 64, ...)}, minY and maxY inclusive. */
    @Nullable
    static Found search(Sampler sampler, BlockPos from, int minY, int maxY, Set<String> wanted) {
        int[] ys = outFromOrigin(from.getY(), minY + 1, maxY + 1, VERTICAL);
        Spiral spiral = new Spiral(Math.floorDiv(RADIUS, HORIZONTAL));
        while (spiral.next()) {
            int bx = from.getX() + spiral.x * HORIZONTAL;
            int bz = from.getZ() + spiral.z * HORIZONTAL;
            for (int y : ys) {
                String id = sampler.biome(bx, y, bz);
                if (id != null && wanted.contains(id)) {
                    return new Found(new BlockPos(bx, y, bz), id);
                }
            }
        }
        return null;
    }

    /**
     * 26.x's {@code BlockPos.spiralAround(ZERO, radius, EAST, SOUTH)}: the origin, then legs east, south, west, north
     * of lengths 1, 1, 2, 2, 3, 3, ..., (2 radius + 1) squared columns in all.
     */
    public static final class Spiral {
        private static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        private final int legs;
        private int leg = -1;
        private int legSize;
        private int legIndex;
        public int x;
        public int z = 1;

        public Spiral(int radius) {
            this.legs = 4 * radius;
        }

        /** Moves to the next column; false once the square is done. */
        public boolean next() {
            int[] d = DIRS[(this.leg + 4) % 4];
            this.x += d[0];
            this.z += d[1];
            if (this.legIndex >= this.legSize) {
                if (this.leg >= this.legs) {
                    return false;
                }
                this.leg++;
                this.legIndex = 0;
                this.legSize = this.leg / 2 + 1;
            }
            this.legIndex++;
            return true;
        }
    }

    /** 26.x's {@code Mth.outFromOrigin(origin, lower, upper, step)}: the origin clamped, then out by step, alternating up and down. */
    public static int[] outFromOrigin(int origin, int lower, int upper, int step) {
        int clamped = Math.max(lower, Math.min(upper, origin));
        List<Integer> out = new ArrayList<Integer>();
        int cursor = clamped;
        while (true) {
            int distance = Math.abs(clamped - cursor);
            if (!(clamped - distance >= lower || clamped + distance <= upper)) {
                break;
            }
            out.add(cursor);
            boolean previousWasNegative = cursor <= clamped;
            boolean canMovePositive = clamped + distance + step <= upper;
            int next = Integer.MIN_VALUE;
            if (!previousWasNegative || !canMovePositive) {
                int attempted = clamped - distance - (previousWasNegative ? step : 0);
                if (attempted >= lower) {
                    next = attempted;
                }
            }
            cursor = next != Integer.MIN_VALUE ? next : clamped + distance + step;
        }
        int[] result = new int[out.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = out.get(i);
        }
        return result;
    }

    /**
     * 1.12.2's layers: the biome of the column's 1:4 cell (height ignored), read 32 x 32 cells at a time and kept, so
     * the spiral's 160,000 columns cost about 2,500 reads of the map.
     */
    static final class LayerSampler implements Sampler {
        private static final int TILE = 32;
        private final BiomeProvider provider;
        private final Map<Long, String[]> tiles = new HashMap<Long, String[]>();
        private final Map<Biome, String> names = new IdentityHashMap<Biome, String>();

        LayerSampler(BiomeProvider provider) {
            this.provider = provider;
        }

        @Override
        public String biome(int x, int y, int z) {
            int qx = x >> 2;
            int qz = z >> 2;
            int tx = Math.floorDiv(qx, TILE);
            int tz = Math.floorDiv(qz, TILE);
            long key = ((long) tx << 32) ^ (tz & 0xFFFFFFFFL);
            String[] tile = this.tiles.get(key);
            if (tile == null) {
                Biome[] biomes = this.provider.getBiomesForGeneration(null, tx * TILE, tz * TILE, TILE, TILE);
                tile = new String[TILE * TILE];
                for (int i = 0; i < tile.length; i++) {
                    String name = this.names.get(biomes[i]);
                    if (name == null) {
                        name = id(biomes[i]);
                        this.names.put(biomes[i], name);
                    }
                    tile[i] = name;
                }
                this.tiles.put(key, tile);
            }
            return tile[(qx - tx * TILE) + (qz - tz * TILE) * TILE];
        }
    }

}

package com.exiledradio.backportcore.worldgen;

import com.exiledradio.backportcore.terrain.RtgTerrain;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.IChunkGenerator;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The terrain half of the ocean monument's rule, for generators whose terrain does not follow the biome map.
 *
 * <p>Vanilla only checks biomes: deep ocean within 16 blocks of the chunk's block (9, 9), oceans and rivers within
 * 29. On vanilla's terrain that is enough, because a deep ocean biome always has a deep sea bed. Realistic Terrain
 * Generation builds its own terrain from the same biome map, and the two can disagree. A biome it has no realistic
 * biome for becomes its patch biome (plains), so Update Aquatic Backport's deep oceans are dry land in RTG 6.1. Its
 * blending also turns small deep ocean patches into lakes. The biome rule then puts a monument in a field or a
 * pond.
 *
 * <p>So in an RTG world, {@link #deepEnough} also reads RTG's landscape (the cached heights the chunk will be built
 * from) at the same 1:4 cells the biome check reads:
 * <ul>
 *   <li>within 16 blocks, at least {@value #DEEP} blocks of water over the ground: the depth of a deep ocean;</li>
 *   <li>within 29 blocks, water over the ground: an ocean or a river around it.</li>
 * </ul>
 * RTG's deep oceans have their highest ground within 16 blocks at y 36 to 47 (sea level 63), which passes. Its
 * ordinary oceans sit around y 50, and its lakes are shallower still, so neither does. In any other world this
 * always passes, and the rule stays vanilla's.
 *
 * <p>Generation, {@code /locate} and explorer maps all ask the generator's {@code canSpawnStructureAtCoords}, so a
 * monument generator that calls this there gets the same answer for all three.
 */
public final class MonumentTerrain {

    /** Blocks of water a deep ocean has over every column within 16 blocks of the monument's center. */
    public static final int DEEP = 15;

    private static final Map<World, RtgTerrain> RTG = new WeakHashMap<World, RtgTerrain>();
    private static final Map<World, Boolean> OTHER = new WeakHashMap<World, Boolean>();

    private MonumentTerrain() {
    }

    /**
     * @return whether the terrain at a monument in this chunk is deep water, as the biome rule assumes; true in
     *         worlds whose terrain follows the biome map
     */
    public static synchronized boolean deepEnough(World world, int chunkX, int chunkZ) {
        RtgTerrain rtg = rtg(world);
        if (rtg == null) {
            return true;
        }
        int x = chunkX * 16 + 9;
        int z = chunkZ * 16 + 9;
        int surface = world.getSeaLevel() - 1;
        return covered(rtg, x, z, 16, surface - DEEP) && covered(rtg, x, z, 29, surface - 1);
    }

    /** @return whether the ground in every 1:4 cell within this distance (as the biome check reads them) is at most this high */
    private static boolean covered(RtgTerrain rtg, int x, int z, int radius, int highest) {
        int minX = (x - radius) >> 2, maxX = (x + radius) >> 2;
        int minZ = (z - radius) >> 2, maxZ = (z + radius) >> 2;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (rtg.floor(cx * 4 + 2, cz * 4 + 2) - 1 > highest) {
                    return false;
                }
            }
        }
        return true;
    }

    private static RtgTerrain rtg(World world) {
        RtgTerrain terrain = RTG.get(world);
        if (terrain != null || OTHER.containsKey(world)) {
            return terrain;
        }
        IChunkGenerator generator = world instanceof WorldServer ? ((WorldServer) world).getChunkProvider().chunkGenerator : null;
        terrain = RtgTerrain.handles(generator) ? RtgTerrain.of(world, generator) : null;
        if (terrain != null) {
            RTG.put(world, terrain);
        } else {
            OTHER.put(world, Boolean.TRUE);
        }
        return terrain;
    }
}

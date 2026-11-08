package com.exiledradio.backportcore.worldgen;

import com.google.common.hash.Hashing;

import net.minecraft.world.gen.layer.GenLayer;
import net.minecraft.world.gen.layer.IntCache;

/**
 * 1.15's block-scale biome: what {@code world.getBiome(pos)} answers in the overworld from 1.15 on, in place of
 * 1.12.2's last layer ({@code GenLayerVoronoiZoom}).
 *
 * <p>1.15 dropped the Voronoi zoom from the layer stack. The 1:4 map ({@code getNoiseBiome}) is all the
 * generator keeps, and every block asks the server's {@code BiomeManager}, which the overworld builds with
 * {@code FuzzyOffsetConstantColumnBiomeZoomer} and the hashed seed ({@code LevelData.obfuscateSeed}: the first
 * eight bytes of the seed's SHA-256). The zoomer is the 3D {@code FuzzyOffsetBiomeZoomer} at y 0: it jitters
 * the eight 1:4 corners around the block (two of them in y, cells -1 and 0) by the hashed seed and takes the
 * nearest. 1.16.5 has the same code ({@code ColumnFuzzedBiomeMagnifier}, {@code FuzzedBiomeMagnifier}).
 *
 * <p>Ported line for line from 1.15.2's {@code FuzzyOffsetBiomeZoomer.getBiome} and {@code getFiddledDistance},
 * over the parent's ints rather than biome objects; the parent is the 1:4 map the Voronoi layer read.
 */
public final class FuzzyZoomLayer extends GenLayer {

    private long zoomSeed;

    public FuzzyZoomLayer(GenLayer quartMap) {
        super(0L);
        this.parent = quartMap;
    }

    /** {@code LevelData.obfuscateSeed}: {@code Hashing.sha256().hashLong(seed).asLong()}. */
    public static long obfuscateSeed(long seed) {
        return Hashing.sha256().hashLong(seed).asLong();
    }

    /** Takes the world seed. The parent is the provider's own 1:4 map, which is seeded already. */
    @Override
    public void initWorldGenSeed(long seed) {
        this.zoomSeed = obfuscateSeed(seed);
    }

    @Override
    public int[] getInts(int areaX, int areaY, int areaWidth, int areaHeight) {
        // The corners every block of the area may pick: cells (x - 2 >> 2) .. +1 on each axis.
        int px0 = areaX - 2 >> 2;
        int pz0 = areaY - 2 >> 2;
        int pw = (areaX + areaWidth - 1 - 2 >> 2) - px0 + 2;
        int ph = (areaY + areaHeight - 1 - 2 >> 2) - pz0 + 2;
        int[] quart = this.parent.getInts(px0, pz0, pw, ph);
        int[] out = IntCache.getIntCache(areaWidth * areaHeight);
        for (int z = 0; z < areaHeight; z++) {
            for (int x = 0; x < areaWidth; x++) {
                long cell = pick(this.zoomSeed, areaX + x, 0, areaY + z);
                int cx = (int) (cell >> 32);
                int cz = (int) cell;
                out[x + z * areaWidth] = quart[(cx - px0) + (cz - pz0) * pw];
            }
        }
        return out;
    }

    /**
     * {@code FuzzyOffsetBiomeZoomer.getBiome(seed, x, y, z, source)}: the 1:4 cell the block reads.
     *
     * @return the cell's x in the high 32 bits and its z in the low 32 (its y is not needed: the overworld's
     *         1:4 map has no height)
     */
    static long pick(long seed, int x, int y, int z) {
        int i = x - 2;
        int j = y - 2;
        int k = z - 2;
        int l = i >> 2;
        int i1 = j >> 2;
        int j1 = k >> 2;
        double d0 = (double) (i & 3) / 4.0D;
        double d1 = (double) (j & 3) / 4.0D;
        double d2 = (double) (k & 3) / 4.0D;
        double[] distances = new double[8];
        for (int c = 0; c < 8; ++c) {
            boolean fx = (c & 4) == 0;
            boolean fy = (c & 2) == 0;
            boolean fz = (c & 1) == 0;
            int cx = fx ? l : l + 1;
            int cy = fy ? i1 : i1 + 1;
            int cz = fz ? j1 : j1 + 1;
            double dx = fx ? d0 : d0 - 1.0D;
            double dy = fy ? d1 : d1 - 1.0D;
            double dz = fz ? d2 : d2 - 1.0D;
            distances[c] = fiddledDistance(seed, cx, cy, cz, dx, dy, dz);
        }
        int best = 0;
        double bestDistance = distances[0];
        for (int c = 1; c < 8; ++c) {
            if (bestDistance > distances[c]) {
                best = c;
                bestDistance = distances[c];
            }
        }
        int cx = (best & 4) == 0 ? l : l + 1;
        int cz = (best & 1) == 0 ? j1 : j1 + 1;
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static double fiddledDistance(long seed, int x, int y, int z, double dx, double dy, double dz) {
        long r = next(seed, x);
        r = next(r, y);
        r = next(r, z);
        r = next(r, x);
        r = next(r, y);
        r = next(r, z);
        double fx = fiddle(r);
        r = next(r, seed);
        double fy = fiddle(r);
        r = next(r, seed);
        double fz = fiddle(r);
        return sqr(dz + fz) + sqr(dy + fy) + sqr(dx + fx);
    }

    /** {@code LinearCongruentialGenerator.next}. */
    private static long next(long a, long b) {
        a *= a * 6364136223846793005L + 1442695040888963407L;
        return a + b;
    }

    private static double fiddle(long r) {
        double d = (double) ((int) Math.floorMod(r >> 24, 1024L)) / 1024.0D;
        return (d - 0.5D) * 0.9D;
    }

    private static double sqr(double d) {
        return d * d;
    }
}

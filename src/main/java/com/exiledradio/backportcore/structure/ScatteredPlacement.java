package com.exiledradio.backportcore.structure;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import javax.annotation.Nullable;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Today's {@code RandomSpreadStructurePlacement}: the world is cut into square regions of
 * {@code spacing} chunks, and each region has one candidate chunk, at least {@code separation}
 * chunks from the next region's. Shipwrecks are 24 / 4, ocean ruins 20 / 8, ruined portals 40 / 15,
 * bastions and fortresses share 27 / 4; each structure has its own salt.
 *
 * <p>Pure arithmetic on the world seed: nothing is loaded or generated, so a locator can scan
 * hundreds of regions cheaply.
 */
public final class ScatteredPlacement {

    private final int spacing;
    private final int separation;
    private final int salt;
    private final boolean triangular;

    /**
     * @param triangular today's {@code TRIANGULAR} spread (end cities, monuments, mansions): the
     *                   candidate tends to the middle of its region. False for {@code LINEAR}.
     */
    public ScatteredPlacement(int spacing, int separation, int salt, boolean triangular) {
        if (spacing <= separation) {
            throw new IllegalArgumentException("spacing " + spacing + " must be more than separation " + separation);
        }
        this.spacing = spacing;
        this.separation = separation;
        this.salt = salt;
        this.triangular = triangular;
    }

    public ScatteredPlacement(int spacing, int separation, int salt) {
        this(spacing, separation, salt, false);
    }

    public int spacing() {
        return this.spacing;
    }

    public int separation() {
        return this.separation;
    }

    public int salt() {
        return this.salt;
    }

    /** @return the candidate chunk of the region holding this chunk */
    public ChunkPos candidate(long worldSeed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, this.spacing);
        int regionZ = Math.floorDiv(chunkZ, this.spacing);
        return candidateOfRegion(worldSeed, regionX, regionZ);
    }

    /** @return true if this chunk is its region's candidate */
    public boolean isCandidate(long worldSeed, int chunkX, int chunkZ) {
        ChunkPos c = candidate(worldSeed, chunkX, chunkZ);
        return c.x == chunkX && c.z == chunkZ;
    }

    private ChunkPos candidateOfRegion(long worldSeed, int regionX, int regionZ) {
        // Today's WorldgenRandom.setLargeFeatureWithSalt, which is 1.12.2's World.setRandomSeed.
        Random random = new Random(regionX * 341873128712L + regionZ * 132897987541L + worldSeed + this.salt);
        int range = this.spacing - this.separation;
        int x;
        int z;
        if (this.triangular) {
            x = (random.nextInt(range) + random.nextInt(range)) / 2;
            z = (random.nextInt(range) + random.nextInt(range)) / 2;
        } else {
            x = random.nextInt(range);
            z = random.nextInt(range);
        }
        return new ChunkPos(regionX * this.spacing + x, regionZ * this.spacing + z);
    }

    /**
     * The nearest candidate chunk that {@code accept} takes (a biome check, say), by horizontal
     * distance from {@code from}, looking region by region outwards.
     *
     * @param accept asked only of candidate chunks; must not load chunks
     * @return the accepted candidate nearest {@code from} within {@code radiusChunks}, or null
     */
    @Nullable
    public ChunkPos nearest(long worldSeed, BlockPos from, int radiusChunks, Predicate<ChunkPos> accept) {
        int fromX = from.getX() >> 4;
        int fromZ = from.getZ() >> 4;
        int centerX = Math.floorDiv(fromX, this.spacing);
        int centerZ = Math.floorDiv(fromZ, this.spacing);
        int maxRing = radiusChunks / this.spacing + 1;
        ChunkPos best = null;
        long bestDistance = Long.MAX_VALUE;
        int foundRing = -1;
        for (int ring = 0; ring <= maxRing; ring++) {
            // A candidate one ring further out can still be nearer than one found in this ring.
            if (foundRing >= 0 && ring > foundRing + 1) {
                break;
            }
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.abs(dx) != ring && Math.abs(dz) != ring) {
                        continue;
                    }
                    ChunkPos c = candidateOfRegion(worldSeed, centerX + dx, centerZ + dz);
                    long ddx = c.x - fromX;
                    long ddz = c.z - fromZ;
                    long distance = ddx * ddx + ddz * ddz;
                    if (distance > (long) radiusChunks * radiusChunks || distance >= bestDistance) {
                        continue;
                    }
                    if (accept.test(c)) {
                        best = c;
                        bestDistance = distance;
                        if (foundRing < 0) {
                            foundRing = ring;
                        }
                    }
                }
            }
        }
        return best;
    }
}

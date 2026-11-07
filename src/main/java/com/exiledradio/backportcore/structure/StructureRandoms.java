package com.exiledradio.backportcore.structure;

import java.util.Random;

/**
 * The seeds 1.16's structure code draws from ({@code WorldgenRandom}), for layouts that match vanilla
 * seed for seed. 1.12.2's {@code MapGenStructure} hands a start a random that has already been used for
 * other things, so a start that wants 1.16's layout makes its own with {@link #start}.
 */
public final class StructureRandoms {

    private StructureRandoms() {
    }

    /**
     * 1.16's {@code setLargeFeatureSeed}: the random every {@code StructureStart} lays out with
     * ({@code StructureStart}'s constructor), so a ruined portal's variant, a bastion's pieces and a
     * fortress's pieces come out as vanilla's for the same seed.
     */
    public static Random start(long worldSeed, int chunkX, int chunkZ) {
        Random random = new Random(worldSeed);
        long a = random.nextLong();
        long b = random.nextLong();
        random.setSeed((long) chunkX * a ^ (long) chunkZ * b ^ worldSeed);
        return random;
    }

    /**
     * 1.16's {@code setLargeFeatureWithSalt}: the random of a placement region, as
     * {@link ScatteredPlacement} uses it to find the region's candidate chunk.
     */
    public static Random region(long worldSeed, int regionX, int regionZ, int salt) {
        return new Random((long) regionX * 341873128712L + (long) regionZ * 132897987541L + worldSeed + (long) salt);
    }
}

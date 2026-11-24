package com.exiledradio.backportcore.presentation.versions;

/**
 * 1.13's Biome Blend (Video Settings, {@link Look113#BIOME_BLEND}): how far the grass, foliage and water colors are
 * averaged over the biomes around a block. 1.13.2 averages a square of {@code (2r+1)x(2r+1)} columns at the block's
 * height, with r from 0 (OFF, the block's own biome) to 7 (15x15) and 2 (5x5) by default; 1.12.2 always averages 3x3
 * (r = 1), which stays while the option is hidden (no 1.13 or later backport, or Video Settings' look off).
 *
 * <p>Patch P68 ({@code asm/BiomeBlendTransformer}) makes 1.12.2's {@code BiomeColorHelper} ask these methods for its
 * square and its divisor. The client keeps {@link #radius} equal to the option
 * ({@code versions.client.Look113Client}). Moved from Update Aquatic Backport (its {@code water/BiomeBlend}, patch P6)
 * in Core 1.4.7. This class names no client type.
 */
public final class BiomeBlend {

    /** 1.12.2's 3x3 until the client sets it. Read by the chunk builder threads, set by the client thread. */
    public static volatile int radius = 1;

    private BiomeBlend() {
    }

    /** @return the square's low corner offset, for x and for z ({@code BiomeColorHelper}'s -1) */
    public static int low() {
        return -radius;
    }

    /** @return the square's high corner offset, for x and for z ({@code BiomeColorHelper}'s 1) */
    public static int high() {
        return radius;
    }

    /**
     * @return the number of columns averaged ({@code BiomeColorHelper}'s 9). Read once per channel, after the square;
     * a change between the two reads affects only chunks being built at that moment, which a change rebuilds anyway.
     */
    public static int count() {
        int side = 2 * radius + 1;
        return side * side;
    }
}

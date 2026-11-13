package com.exiledradio.backportcore.worldgen;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;

import javax.annotation.Nullable;

/**
 * A chunk generator that places a later Minecraft version's overworld features itself: Caves &amp; Cliffs Backport's
 * 1.18 generator places every 1.18.2 placed feature (sweet berry bushes, bamboo, cornflowers, bee nests, kelp ...),
 * with the backports' blocks where they are installed. A backport's own {@code IWorldGenerator} for a feature that
 * version already has steps aside in such a world, or the feature would be placed twice:
 *
 * <pre>
 * if (FeatureSource.places(world, "1.14")) {
 *     return;   // the world's generator places 1.14's sweet berry bushes itself
 * }
 * </pre>
 */
public interface FeatureSource {

    /**
     * @return the Minecraft version whose overworld features this generator places in this world ("1.18.2"), or null
     * where it places none of its own
     */
    @Nullable
    String featureVersion(World world);

    /**
     * @param since the version that added the feature ("1.14" for sweet berry bushes)
     * @return true if the world's chunk generator places the features of that version or a later one itself. False on
     * a client world, or before the world's chunk generator exists.
     */
    static boolean places(World world, String since) {
        if (world == null || world.isRemote || !(world instanceof WorldServer)) {
            return false;
        }
        ChunkProviderServer provider = ((WorldServer) world).getChunkProvider();
        IChunkGenerator generator = provider == null ? null : provider.chunkGenerator;
        if (!(generator instanceof FeatureSource)) {
            return false;
        }
        String version = ((FeatureSource) generator).featureVersion(world);
        return version != null && compare(version, since) >= 0;
    }

    /** Compares dotted version numbers ("1.18.2" against "1.14"): missing parts count as 0. */
    static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? parse(x[i]) : 0;
            int q = i < y.length ? parse(y[i]) : 0;
            if (p != q) {
                return Integer.compare(p, q);
            }
        }
        return 0;
    }

    static int parse(String part) {
        try {
            return Integer.parseInt(part.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

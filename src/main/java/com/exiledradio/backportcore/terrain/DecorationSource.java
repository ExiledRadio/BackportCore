package com.exiledradio.backportcore.terrain;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.IChunkGenerator;

import javax.annotation.Nullable;

/**
 * A chunk generator that places today's features itself (1.12's biome decorator does not run in its worlds), so a
 * backport's own 1.12 hook for a feature (a {@code PopulateChunkEvent} or {@code DecorateBiomeEvent} handler, an
 * {@code IWorldGenerator}) must not add it a second time. Caves &amp; Cliffs Backport's 1.18 generator implements it.
 *
 * <pre>
 * if (DecorationSource.places(world, "bee_nest")) return;   // the generator's trees already got their nests
 * </pre>
 *
 * Feature names are today's vanilla names without a namespace: a placed or configured feature ("trees_mangrove",
 * "patch_sugar_cane"), or the block a feature puts down when that says it better ("bee_nest", "bamboo").
 */
public interface DecorationSource {

    /**
     * @return true when this generator places the feature in this world itself. Pure: called from the server thread
     * at any time, answers the same for the world's life.
     */
    boolean placesFeature(World world, String feature);

    /** Whether the world's chunk generator places the feature itself; false for any other generator. */
    static boolean places(World world, String feature) {
        IChunkGenerator generator = generator(world);
        return generator instanceof DecorationSource && ((DecorationSource) generator).placesFeature(world, feature);
    }

    /** The same for the generator a populate event names ({@code PopulateChunkEvent.getGenerator()}). */
    static boolean places(World world, @Nullable IChunkGenerator generator, String feature) {
        return generator instanceof DecorationSource && ((DecorationSource) generator).placesFeature(world, feature);
    }

    @Nullable
    static IChunkGenerator generator(World world) {
        if (!(world instanceof WorldServer)) {
            return null;
        }
        return ((WorldServer) world).getChunkProvider().chunkGenerator;
    }
}

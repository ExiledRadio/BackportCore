package com.exiledradio.backportcore.terrain;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;

import javax.annotation.Nullable;

/**
 * A chunk generator that runs a newer vanilla version's world generation in full: its biomes, in three dimensions,
 * and every placed feature of that version (coral reefs, kelp, seagrass, sea pickles, icebergs, blue ice ...). Caves
 * &amp; Cliffs Backport's 1.18 generator implements it, for 1.18.2.
 *
 * <p>In such a world a backport's own generator of a feature that version places steps aside ({@link #of}), and a
 * rule that reads a biome reads it here ({@link #biomeId}): a lush cave under an ocean is a lush cave, and an ocean
 * 1.12 can only call {@code ocean} is the warm or cold one that version made.
 */
public interface ModernGeneration {

    /** @return the vanilla version whose world generation this generator runs, as {@code "1.18.2"} */
    String generationVersion();

    /**
     * @return today's id of the biome at a block, in three dimensions, as that version's mob spawner and features
     * read it ({@code "minecraft:warm_ocean"}), or null if this generator cannot say there. Server thread; loads and
     * generates nothing.
     */
    @Nullable
    String biomeId(int x, int y, int z);

    /** @return this world's chunk generator if it is one (server worlds), else null */
    @Nullable
    static ModernGeneration of(World world) {
        if (!(world instanceof WorldServer)) {
            return null;
        }
        ChunkProviderServer provider = ((WorldServer) world).getChunkProvider();
        return provider == null ? null : of(provider.chunkGenerator);
    }

    /** @return the generator if it is one, else null (an {@code IWorldGenerator} is handed it) */
    @Nullable
    static ModernGeneration of(@Nullable IChunkGenerator generator) {
        return generator instanceof ModernGeneration ? (ModernGeneration) generator : null;
    }

    /** @return today's biome id at a block of a world whose generator is one, else null */
    @Nullable
    static String biomeId(World world, BlockPos pos) {
        ModernGeneration generation = of(world);
        return generation == null ? null : generation.biomeId(pos.getX(), pos.getY(), pos.getZ());
    }
}

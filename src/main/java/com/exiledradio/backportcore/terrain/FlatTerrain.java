package com.exiledradio.backportcore.terrain;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.gen.ChunkGeneratorFlat;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenVillage;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Superflat worlds: the easiest terrain there is, and the one generator Forge's village event never
 * reaches.
 *
 * <p>Every column of a superflat world is the same stack of layers, which the generator keeps as one
 * block per height ({@code cachedBlockIDs}). The surface is one above the highest of them, wherever
 * the column is.
 *
 * <p>{@code ChunkGeneratorFlat} builds its structure generators with {@code new MapGenVillage(...)} and
 * the like straight into its own map ({@link #structures}), without the {@code InitMapGenEvent} every
 * other generator posts. A mod that replaces one of them has to swap it in that map itself, and has
 * to do it as the spawn search begins ({@code WorldEvent.CreateSpawnPosition}) as well as on load: a
 * new superflat world generates chunks before Forge says it has loaded, since vanilla looks for grass
 * at sea level to spawn on and superflat's ground is far below it, so the search wanders up to a
 * thousand chunks away, saving every structure it passes near under the old generator.
 */
public final class FlatTerrain implements ColumnTerrain {

    private final int height;
    private final boolean wet;

    private FlatTerrain(int height, boolean wet) {
        this.height = height;
        this.wet = wet;
    }

    /** Only vanilla's own flat generator: a subclass may lay its layers out some other way. */
    public static boolean handles(@Nullable IChunkGenerator generator) {
        return generator != null && generator.getClass() == ChunkGeneratorFlat.class;
    }

    /** @return the terrain, or null for a world with no ground at all (the void preset) */
    @Nullable
    public static FlatTerrain of(IChunkGenerator generator) {
        try {
            Field field = ReflectionHelper.findField(ChunkGeneratorFlat.class, "cachedBlockIDs", "field_82700_c");
            field.setAccessible(true);
            IBlockState[] layers = (IBlockState[]) field.get(generator);
            for (int y = layers.length - 1; y >= 0; y--) {
                IBlockState state = layers[y];
                // A snow layer on top (Snowy Kingdom) is not ground: houses stand on the grass under
                // it, as they do on snowy ground anywhere else. Water still counts.
                if (state != null && state.getMaterial() != Material.AIR
                        && (!state.getMaterial().isReplaceable() || state.getMaterial().isLiquid())) {
                    BackportCore.LOGGER.info("Planning 1.14 structures on a superflat world's ground, at y {}.", y + 1);
                    return new FlatTerrain(y + 1, state.getMaterial().isLiquid());
                }
            }
            return null;
        } catch (Exception e) {
            BackportCore.LOGGER.warn("A superflat world's layers could not be read, so structures planned ahead there stay 1.12.2's.", e);
            return null;
        }
    }

    @Override
    public int height(int x, int z) {
        return this.height;
    }

    @Override
    public boolean wet(int x, int z) {
        return this.wet;
    }

    /** @return the superflat generator's village generator, or null if it has none */
    @Nullable
    public static Object villageGenerator(IChunkGenerator generator) {
        Map<String, MapGenStructure> structures = structures(generator);
        return structures == null ? null : structures.get("Village");
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static Map<String, MapGenStructure> structures(IChunkGenerator generator) {
        try {
            Field field = ReflectionHelper.findField(ChunkGeneratorFlat.class, "structureGenerators", "field_82696_f");
            field.setAccessible(true);
            return (Map<String, MapGenStructure>) field.get(generator);
        } catch (Exception e) {
            return null;
        }
    }

}

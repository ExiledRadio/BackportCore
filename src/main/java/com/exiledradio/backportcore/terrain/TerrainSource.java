package com.exiledradio.backportcore.terrain;

import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * A chunk generator that can say where its own surface will be: {@link TerrainHeights#forWorld} asks it before
 * any other terrain adapter. Caves &amp; Cliffs Backport's 1.18 generator implements it.
 */
public interface TerrainSource {

    /**
     * @return the columns' surface for this world, or null if this generator cannot say. Called once per world on
     * the server thread; the result is kept for as long as the world lives.
     */
    @Nullable
    ColumnTerrain columnTerrain(World world);
}

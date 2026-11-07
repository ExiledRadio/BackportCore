package com.exiledradio.backportcore.terrain;

/**
 * Another mod's world generator that can say where a column's surface will be before the chunk
 * exists. See {@link TerrainHeights}, which picks one of these per world.
 */
public interface ColumnTerrain {

    /** 1.14's {@code WORLD_SURFACE_WG}: one above the top block of the bare terrain, water included. */
    int height(int x, int z);

    /** @return true if the column's surface is water; generators that cannot say answer false */
    default boolean wet(int x, int z) {
        return false;
    }
}

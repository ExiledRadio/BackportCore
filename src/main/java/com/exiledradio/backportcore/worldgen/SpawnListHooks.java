package com.exiledradio.backportcore.worldgen;

/**
 * Patch P36's hook (asm/SpawnListSwitchTransformer): the table lookup of {@code Biome.getSpawnableList}'s switch on the
 * creature type, safe for a type added after the table was made.
 */
public final class SpawnListHooks {

    private SpawnListHooks() {
    }

    /**
     * @return {@code table[ordinal]}, or 0 (the switch's default branch, Forge's list for modded types) when the type
     *         is newer than the table
     */
    public static int switchIndex(int[] table, int ordinal) {
        return ordinal >= 0 && ordinal < table.length ? table[ordinal] : 0;
    }
}

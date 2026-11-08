package com.exiledradio.backportcore.presentation;

/**
 * The chunk statuses the world-loading grid colors, with 1.14's colors (1.14.4's {@code LevelLoadingScreen}
 * table; every later revision reads the same table). This is the one table of statuses and colors; a later
 * revision that adds a status adds it here.
 *
 * <p>1.12.2 generates a chunk in fewer, larger steps than 1.14, so only some statuses are ever reached on it
 * ({@link SpawnProgress} does the mapping):
 * <ul>
 * <li>not loaded: no status, drawn black (vanilla's untracked cells, the black border of 1.14.1's grid);</li>
 * <li>generated (terrain, caves and structure starts, all one step in 1.12.2): {@link #LIQUID_CARVERS};</li>
 * <li>populated (ores, trees and other features): {@link #FEATURES};</li>
 * <li>populated, with every neighbor generated, so lighting can run: {@link #LIGHT};</li>
 * <li>populated, with every neighbor populated, ready to be sent to the player: {@link #FULL}.</li>
 * </ul>
 */
public enum LoadingStatus {
    EMPTY(0x545454),
    STRUCTURE_STARTS(0x999999),
    STRUCTURE_REFERENCES(0x5F6191),
    BIOMES(0x80B252),
    NOISE(0xD1D1D1),
    SURFACE(0x726809),
    CARVERS(0x6D665C),
    LIQUID_CARVERS(0x303572),
    FEATURES(0x21C600),
    LIGHT(0xCCCCCC),
    SPAWN(0xF26060),
    HEIGHTMAPS(0xEEEEEE),
    FULL(0xFFFFFF);

    /** The color of a cell no status was recorded for (vanilla's map default). */
    public static final int UNTRACKED_COLOR = 0x000000;

    private final int color;

    LoadingStatus(int color) {
        this.color = color;
    }

    /** @return the cell color, 0xRRGGBB */
    public int color() {
        return this.color;
    }

    /** @return the color of a cell with this status, or of an untracked cell for null, as opaque 0xAARRGGBB */
    public static int argb(LoadingStatus status) {
        return 0xFF000000 | (status == null ? UNTRACKED_COLOR : status.color);
    }
}

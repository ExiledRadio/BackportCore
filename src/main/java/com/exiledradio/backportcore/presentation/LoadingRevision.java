package com.exiledradio.backportcore.presentation;

import javax.annotation.Nullable;

/**
 * The revisions of the world-loading screen (the screen shown while a singleplayer world loads and its spawn
 * area is prepared). Each is vanilla's screen as of the version that introduced it; a later version keeps the
 * one before it until it brings its own.
 *
 * <p>Core picks the revision from the newest installed version ({@link #forVersion}), so the screen is the
 * one vanilla showed at that version even when the backport of the version that introduced it is not
 * installed. A backport can also declare a revision for its own version
 * ({@link Contribution#loadingScreen(LoadingRevision)}).
 *
 * <p>Core draws {@link #CLASSIC}, {@link #TEXT_1_13}, {@link #GRID_1_14} and {@link #DATA_1_16}. The later
 * revisions are hooks: a backport of 1.20.5 or 1.21.9 registers a drawer for its revision through
 * {@code presentation.client.LoadingScreens.register}, and until one does, Core draws the newest revision it
 * can ({@link #drawableFallback()}).
 */
public enum LoadingRevision {

    /** 1.12.2: "Loading world" and "Building terrain", no progress shown. */
    CLASSIC("1.12.2", true),
    /** 1.13: the same text screen, with "Preparing spawn area: N%". */
    TEXT_1_13("1.13", true),
    /** 1.14: the chunk-status grid (43 by 43 chunks from 1.14.1, one color per generation status) and a percentage. */
    GRID_1_14("1.14", true),
    /** 1.16: "Reading world data..." while the save is read, then 1.14's grid. */
    DATA_1_16("1.16", true),
    /** 1.20.5: the panorama behind the grid, and a smaller grid. Hook only. */
    PANORAMA_1_20_5("1.20.5", false),
    /** 1.21: the grid loses its black border. Hook only. */
    BORDERLESS_1_21("1.21", false),
    /** 1.21.9: the grid shows the chunks the player spawns into, with a progress bar. Hook only. */
    SPAWN_VIEW_1_21_9("1.21.9", false);

    private final GameVersion since;
    private final boolean coreDraws;

    LoadingRevision(String since, boolean coreDraws) {
        this.since = GameVersion.parse(since);
        this.coreDraws = coreDraws;
    }

    /** @return the version that introduced this revision */
    public GameVersion since() {
        return this.since;
    }

    /** @return true when Core has its own drawer for this revision */
    public boolean coreDraws() {
        return this.coreDraws;
    }

    /** @return the revision vanilla showed at {@code version}: the newest one introduced at or before it */
    public static LoadingRevision forVersion(GameVersion version) {
        LoadingRevision found = CLASSIC;
        for (LoadingRevision r : values()) {
            if (version.isAtLeast(r.since)) {
                found = r;
            }
        }
        return found;
    }

    /** @return the newest revision at or before this one that Core can draw itself */
    public LoadingRevision drawableFallback() {
        for (int i = ordinal(); i >= 0; i--) {
            if (values()[i].coreDraws) {
                return values()[i];
            }
        }
        return CLASSIC;
    }

    /** @return the revision with this name (case-insensitive), or null */
    @Nullable
    public static LoadingRevision byName(String name) {
        for (LoadingRevision r : values()) {
            if (r.name().equalsIgnoreCase(name)) {
                return r;
            }
        }
        return null;
    }
}

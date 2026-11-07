package com.exiledradio.backportcore.structure;

/**
 * Vanilla's own structure placement numbers, per version, from the decompiled game
 * ({@code DimensionStructuresSettings.DEFAULTS}). A backport uses the numbers of the version its
 * world is meant to match on seed maps, so the same seed puts structures in the same chunks.
 *
 * <p>Strongholds, mineshafts and buried treasure have no region grid (their entries in vanilla's
 * table are placeholders), so they are not here.
 */
public final class VanillaPlacements {

    private VanillaPlacements() {
    }

    /** 1.16.5 ({@code DimensionStructuresSettings.DEFAULTS}); every one linear. */
    public static final class V1_16_5 {
        private V1_16_5() {
        }

        public static final ScatteredPlacement VILLAGE = new ScatteredPlacement(32, 8, 10387312);
        public static final ScatteredPlacement DESERT_PYRAMID = new ScatteredPlacement(32, 8, 14357617);
        public static final ScatteredPlacement IGLOO = new ScatteredPlacement(32, 8, 14357618);
        public static final ScatteredPlacement JUNGLE_TEMPLE = new ScatteredPlacement(32, 8, 14357619);
        public static final ScatteredPlacement SWAMP_HUT = new ScatteredPlacement(32, 8, 14357620);
        public static final ScatteredPlacement PILLAGER_OUTPOST = new ScatteredPlacement(32, 8, 165745296);
        public static final ScatteredPlacement OCEAN_MONUMENT = new ScatteredPlacement(32, 5, 10387313);
        public static final ScatteredPlacement END_CITY = new ScatteredPlacement(20, 11, 10387313);
        public static final ScatteredPlacement WOODLAND_MANSION = new ScatteredPlacement(80, 20, 10387319);
        /** Every ruined portal, overworld and nether alike; the biome picks the variant. */
        public static final ScatteredPlacement RUINED_PORTAL = new ScatteredPlacement(40, 15, 34222645);
        public static final ScatteredPlacement SHIPWRECK = new ScatteredPlacement(24, 4, 165745295);
        public static final ScatteredPlacement OCEAN_RUIN = new ScatteredPlacement(20, 8, 14357621);
        /** Shared by bastions and nether fortresses: see {@link NetherStructureSlots}. */
        public static final ScatteredPlacement BASTION_REMNANT = new ScatteredPlacement(27, 4, 30084232);
        /** Shared by bastions and nether fortresses: see {@link NetherStructureSlots}. */
        public static final ScatteredPlacement NETHER_BRIDGE = new ScatteredPlacement(27, 4, 30084232);
        public static final ScatteredPlacement NETHER_FOSSIL = new ScatteredPlacement(2, 1, 14357921);
    }
}

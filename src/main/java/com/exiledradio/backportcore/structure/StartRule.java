package com.exiledradio.backportcore.structure;

import javax.annotation.Nullable;

/**
 * Where one structure kind starts in one world, as the world's chunk generator decides it ({@link StructureSource}).
 * Both methods are pure arithmetic on the seed and the generator's own noise: they never load or generate chunks,
 * so locators and {@code /locate} can ask hundreds of candidates.
 */
public interface StartRule {

    /** The grid the kind's candidate chunks lie on: locators and {@code /locate} walk its regions. */
    ScatteredPlacement grid();

    /**
     * @return today's id of the structure starting in this chunk, without the namespace ({@code "shipwreck_beached"},
     * {@code "ocean_ruin_warm"}, {@code "ruined_portal_desert"}, {@code "pillager_outpost"}), or null if none starts
     * there. Asked on the server thread.
     */
    @Nullable
    String startAt(int chunkX, int chunkZ);
}

package com.exiledradio.backportcore.structure;

import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * A chunk generator that decides where today's structures start in its worlds, for the kinds it knows: Caves &amp;
 * Cliffs Backport's 1.18 generator answers with 1.18.2's structure sets (placement, the biome check at the start's
 * height, the weighted choice between a set's structures), so shipwrecks, ocean ruins, buried treasure, ruined
 * portals and pillager outposts start where Chunkbase shows them for 1.18, whichever backport builds them.
 *
 * <p>The mod that builds a kind keeps building it (its pieces, loot and mobs); only where it starts, and which of
 * the kind's variants, come from the generator. {@link ScatteredStructure} asks on its own; a structure on a grid of
 * its own asks {@link StartRules#of}.
 */
public interface StructureSource {

    /**
     * @param kind today's structure name ({@link Structures#SHIPWRECK}, {@link Structures#PILLAGER_OUTPOST}, ...)
     * @return where the kind starts in this world, or null to leave the kind's own rule. Asked once per world and
     * kind, on the server thread; the answer is kept for as long as the world lives.
     */
    @Nullable
    StartRule startRule(World world, String kind);
}

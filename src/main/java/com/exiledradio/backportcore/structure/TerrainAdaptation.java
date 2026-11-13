package com.exiledradio.backportcore.structure;

import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Structures today's terrain shapes itself around ({@code terrain_adaptation}; 1.18.2's {@code adapt_noise}), for a
 * chunk generator whose terrain can do it: Caves &amp; Cliffs Backport's 1.18 generator adds the beardifier around the
 * pieces of every registered kind before it fills a chunk, as 1.18 does around villages, outposts and strongholds.
 *
 * <p>Such a generator fills chunks before 1.12.2 populates anything, so it needs the pieces first: each kind
 * registers a {@link Planner} that lays out, as its populate step later will, every start that can reach a chunk.
 * A start's layout must not depend on blocks in the world (lay out on
 * {@link com.exiledradio.backportcore.terrain.TerrainHeights}), or the terrain would depend on the order chunks
 * are made in.
 *
 * <pre>
 * // init: Village &amp; Pillage's outposts
 * TerrainAdaptation.register(Structures.PILLAGER_OUTPOST, TerrainAdaptation.Kind.BEARD_THIN,
 *         (world, chunkX, chunkZ, out) -&gt; { MapGenOutpost o = MapGenOutpost.of(world); if (o != null) o.startsNear(world, chunkX, chunkZ, out); });
 * </pre>
 *
 * A piece says how it meets the ground by implementing {@link AdaptingPiece} (today's jigsaw pieces: rigid or
 * terrain matching, the ground level delta, the junctions); any other piece is rigid, its ground at its box's floor.
 */
public final class TerrainAdaptation {

    /** Today's {@code TerrainAdjustment}. 1.18.2's {@code adapt_noise} is BEARD_THIN, its strongholds' pieces BURY. */
    public enum Kind {
        NONE, BURY, BEARD_THIN, BEARD_BOX, ENCAPSULATE
    }

    /** Lays out the starts of one kind near a chunk. */
    @FunctionalInterface
    public interface Planner {
        /**
         * Gives every start of this kind whose box, grown by 12 blocks, reaches chunk (chunkX, chunkZ), laying out
         * any not laid out yet exactly as the populate step would (and keeping it, so the populate step builds the
         * same). Server thread; must not load or generate chunks. Empty starts may be left out.
         */
        void startsNear(World world, int chunkX, int chunkZ, Consumer<StructureStart> out);
    }

    /** A piece that knows how it meets the ground: today's {@code PoolElementStructurePiece}. */
    public interface AdaptingPiece {
        /** RIGID projection (true) or TERRAIN_MATCHING; only rigid pieces get a beard of their own. */
        boolean rigid();

        /** The jigsaw element's ground level delta: the ground is this far above the box's floor. */
        int groundLevelDelta();

        /** The piece's jigsaw junctions: source x, source ground y, source z, three ints each (may be empty). */
        int[] junctions();
    }

    /** One registered kind. */
    public static final class Entry {
        public final String kind;
        public final Kind adaptation;
        public final Planner planner;

        Entry(String kind, Kind adaptation, Planner planner) {
            this.kind = kind;
            this.adaptation = adaptation;
            this.planner = planner;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<Entry>();

    private TerrainAdaptation() {
    }

    /** Registers a kind whose pieces the terrain adapts to, in init. A second registration of a kind replaces it. */
    public static synchronized void register(String kind, Kind adaptation, Planner planner) {
        for (int i = 0; i < ENTRIES.size(); i++) {
            if (ENTRIES.get(i).kind.equals(kind)) {
                ENTRIES.set(i, new Entry(kind, adaptation, planner));
                return;
            }
        }
        ENTRIES.add(new Entry(kind, adaptation, planner));
    }

    /** @return every registered kind, in registration order (the order a generator sums their pieces in) */
    public static synchronized List<Entry> registered() {
        return new ArrayList<Entry>(ENTRIES);
    }
}

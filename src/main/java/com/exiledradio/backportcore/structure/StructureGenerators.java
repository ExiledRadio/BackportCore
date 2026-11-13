package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.world.World;
import net.minecraft.world.gen.structure.MapGenStructure;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Replacing a world generator's own generator for one of 1.12.2's structure kinds with a backport's, in worlds whose
 * generator does not build its structure generators through Forge's {@code InitMapGenEvent}: Caves &amp; Cliffs
 * Backport's 1.18 generator offers its village generator here, and Village &amp; Pillage puts 1.14's villages in its
 * place.
 *
 * <pre>
 * // init, the backport that builds the kind
 * StructureGenerators.register(Structures.VILLAGE, (world, standIn) -&gt; new MyVillages(standIn));   // null: keep it
 * // the world generator, as it builds its structure generators
 * MapGenStructure villages = StructureGenerators.replace(world, Structures.VILLAGE, ownVillages);
 * </pre>
 *
 * <p>Only a generator that asks is affected: vanilla's overworld keeps Forge's event. The replacement is used for
 * everything the stand-in was (starts, populating, {@code /locate}, "inside a village" questions), and the world
 * generator no longer lays out or adapts its terrain to the stand-in's starts: a replacement whose structure the
 * terrain should adapt to registers with {@link TerrainAdaptation}. Where the world generator rules the kind's starts
 * ({@link StructureSource}), the replacement should ask {@link StartRules#of} for them.
 */
public final class StructureGenerators {

    /** Builds a backport's generator for one kind in place of a world generator's. */
    @FunctionalInterface
    public interface Replacer {
        /**
         * @param standIn the world generator's own generator for the kind, not yet used
         * @return the generator to use instead, or null to keep the stand-in. Called once per world, on the server
         * thread, while the world's chunk generator is being built (so {@code world}'s chunk provider may not be set).
         */
        @Nullable
        MapGenStructure replace(World world, MapGenStructure standIn);
    }

    private static final Map<String, Replacer> REPLACERS = new LinkedHashMap<String, Replacer>();

    private StructureGenerators() {
    }

    /** Registers the replacer for a kind, once per kind, in init. A second registration replaces the first. */
    public static synchronized void register(String kind, Replacer replacer) {
        REPLACERS.put(kind, replacer);
    }

    /**
     * @return the generator to use for the kind in this world: the registered replacement, or the stand-in where no
     * backport replaces it (or the replacer fails)
     */
    public static MapGenStructure replace(World world, String kind, MapGenStructure standIn) {
        Replacer replacer;
        synchronized (StructureGenerators.class) {
            replacer = REPLACERS.get(kind);
        }
        if (replacer == null) {
            return standIn;
        }
        try {
            MapGenStructure replaced = replacer.replace(world, standIn);
            if (replaced != null && replaced != standIn) {
                BackportCore.LOGGER.info("{} builds the {}s of {} in place of {}.", replaced.getClass().getSimpleName(), kind,
                        world.getWorldInfo().getWorldName(), standIn.getClass().getSimpleName());
                return replaced;
            }
        } catch (RuntimeException | LinkageError e) {
            BackportCore.LOGGER.error("Replacing the {} generator of {} failed; keeping {}.", kind,
                    world.getWorldInfo().getWorldName(), standIn.getClass().getSimpleName(), e);
        }
        return standIn;
    }

    /** @return true if a backport has registered a replacement for the kind */
    public static synchronized boolean replaced(String kind) {
        return REPLACERS.containsKey(kind);
    }
}

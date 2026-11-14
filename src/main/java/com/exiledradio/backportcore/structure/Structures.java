package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.worldgen.FlatWorlds;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Finding the nearest structure of a kind, whichever backport generates it: dolphins leading to a
 * shipwreck, explorer and treasure maps, a {@code /locate} that knows the family's structures.
 *
 * <p>Kinds are today's structure names, lower case ({@link #SHIPWRECK}, {@link #BASTION_REMNANT}).
 * The mod that generates a kind registers one locator for it in init; a mod that asks gets null
 * while nobody does. A {@link ScatteredStructure} registers its own through
 * {@link ScatteredStructure#register}.
 *
 * <p>The API is the one Update Aquatic Backport's dolphin stream defined ({@code Structures} with a
 * {@code Locator}), so that code moves over by changing its import.
 */
public final class Structures {

    public static final String SHIPWRECK = "shipwreck";
    public static final String OCEAN_RUIN = "ocean_ruin";
    public static final String BURIED_TREASURE = "buried_treasure";
    public static final String RUINED_PORTAL = "ruined_portal";
    public static final String BASTION_REMNANT = "bastion_remnant";
    public static final String NETHER_FOSSIL = "nether_fossil";
    public static final String ANCIENT_CITY = "ancient_city";
    public static final String TRAIL_RUINS = "trail_ruins";
    public static final String TRIAL_CHAMBERS = "trial_chambers";
    public static final String VILLAGE = "village";
    public static final String PILLAGER_OUTPOST = "pillager_outpost";

    /** Finds one kind of structure. */
    @FunctionalInterface
    public interface Locator {
        /**
         * Runs on the server thread. Must not generate anything, and should not load chunks it does
         * not need: it may be asked each time a dolphin is fed.
         *
         * @param radiusChunks how far to look, in chunks
         * @return a position in the nearest structure of this kind within the radius, or null
         */
        @Nullable
        BlockPos nearest(WorldServer world, BlockPos from, int radiusChunks);
    }

    private static final Map<String, Locator> LOCATORS = new LinkedHashMap<String, Locator>();

    private Structures() {
    }

    /** Registers the locator for a kind, once per kind, in init. A second registration replaces the first. */
    public static synchronized void register(String kind, Locator locator) {
        Locator old = LOCATORS.put(kind, locator);
        if (old != null && old != locator) {
            BackportCore.LOGGER.debug("A second locator was registered for structure kind {}; the newer one is used.", kind);
        }
    }

    /** For tests. */
    public static synchronized void unregister(String kind) {
        LOCATORS.remove(kind);
    }

    /** @return true if some mod finds this kind */
    public static synchronized boolean has(String kind) {
        return LOCATORS.containsKey(kind);
    }

    /**
     * @return a position in the nearest structure of this kind within the radius, or null; always null in a superflat
     * world whose preset cannot have the kind ({@link FlatWorlds#structure})
     */
    @Nullable
    public static BlockPos nearest(WorldServer world, String kind, BlockPos from, int radiusChunks) {
        Locator locator;
        synchronized (Structures.class) {
            locator = LOCATORS.get(kind);
        }
        if (locator == null || !FlatWorlds.structure(world, kind)) {
            // A superflat world without the kind in its preset has none, whoever registered a locator.
            return null;
        }
        try {
            return locator.nearest(world, from, radiusChunks);
        } catch (RuntimeException e) {
            BackportCore.LOGGER.error("Looking for the nearest {} failed.", kind, e);
            return null;
        }
    }

    /** @return the nearest of several kinds (by distance from {@code from}), or null if there is none */
    @Nullable
    public static BlockPos nearestOf(WorldServer world, BlockPos from, int radiusChunks, String... kinds) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (String kind : kinds) {
            BlockPos pos = nearest(world, kind, from, radiusChunks);
            if (pos != null) {
                double d = pos.distanceSq(from.getX(), pos.getY(), from.getZ());
                if (d < bestDistance) {
                    best = pos;
                    bestDistance = d;
                }
            }
        }
        return best;
    }
}

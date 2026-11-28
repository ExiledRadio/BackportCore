package com.exiledradio.backportcore.worldgen;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spawn rules checked before the natural spawner builds the mob, as current Java orders them (patch P90).
 *
 * <p>1.12.2's {@code WorldEntitySpawner.findChunksForSpawning} builds the mob first and only then asks its {@code
 * getCanSpawnHere} and Forge's {@code CheckSpawn}. Current Java's {@code NaturalSpawner} checks a mob type's spawn rules
 * and despawn distance at the position before it creates the mob. For a mob whose rules refuse most of the spots the
 * spawner tries (fish beyond 64 blocks of every player, glow squid above y 30), 1.12.2 built and threw away a mob for
 * almost every attempt: the larger part of an idle server's backport time.
 *
 * <p>A backport registers, for its mob class, a check of the position alone that its own spawn rules would refuse
 * anyway ({@code getCanSpawnHere}, or its own {@code CheckSpawn} denial). The spawner's attempt then fails before the
 * mob is built, the way it failed after. Only natural spawning in the spawning cycle goes through here; spawn eggs,
 * spawners, commands and chunk generation spawning are unchanged.
 *
 * <pre>
 * SpawnPrechecks.register(EntityGlowSquid.class, (world, pos) -&gt; pos.getY() &gt; world.getSeaLevel() - 33);
 * </pre>
 */
public final class SpawnPrechecks {

    /** A check of a spawn position alone. */
    public interface Precheck {
        /** @return true if the mob's own spawn rules would refuse a natural spawn at this position */
        boolean refuses(World world, BlockPos pos);
    }

    /** Copied on write: the spawner reads it every attempt, registration happens at startup. */
    private static volatile Map<Class<?>, Precheck[]> checks = new IdentityHashMap<Class<?>, Precheck[]>();

    private SpawnPrechecks() {
    }

    /**
     * Adds a check for natural spawns of exactly this class (not its subclasses). Several checks may be added for one
     * class; any of them refusing refuses.
     */
    public static synchronized void register(Class<? extends Entity> type, Precheck check) {
        Map<Class<?>, Precheck[]> next = new IdentityHashMap<Class<?>, Precheck[]>(checks);
        Precheck[] old = next.get(type);
        List<Precheck> all = new ArrayList<Precheck>();
        if (old != null) {
            for (Precheck p : old) {
                all.add(p);
            }
        }
        all.add(check);
        next.put(type, all.toArray(new Precheck[0]));
        checks = next;
    }

    /**
     * P90's hook, after {@code WorldServer.canCreatureTypeSpawnHere(type, entry, pos)} in the spawning cycle: that
     * answer, and false when a check registered for the entry's mob refuses the position.
     */
    public static boolean allowed(boolean allowed, World world, Biome.SpawnListEntry entry, BlockPos pos) {
        if (!allowed || entry == null) {
            return allowed;
        }
        Precheck[] found = checks.get(entry.entityClass);
        if (found != null) {
            for (Precheck p : found) {
                if (p.refuses(world, pos)) {
                    return false;
                }
            }
        }
        return true;
    }
}

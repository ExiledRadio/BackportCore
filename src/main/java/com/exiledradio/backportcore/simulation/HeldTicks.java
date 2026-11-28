package com.exiledradio.backportcore.simulation;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Scheduled ticks waiting outside the simulation distance, kept by chunk until it ticks again (patch P91, beside P46's
 * waiting ticks), as 26.3's {@code LevelTicks} keeps a chunk's ticks in the chunk.
 *
 * <p>1.12.2 puts a tick it cannot run back on the world's queue for the next tick ({@code scheduleUpdate(pos, block,
 * 0)}). Outside the distance that is every tick for every waiting tick: thousands of water and lava ticks over new
 * terrain or a melting frozen ocean, taken off and put back on a tree set each tick. Here such a tick waits in its
 * chunk's set instead, and goes back on the queue the same way (delay 0) at the start of the first world tick its chunk
 * is in range again, or when the distance is turned off. A chunk's save asks {@code getPendingBlockUpdates}, which
 * includes the waiting ticks in its area (P91 wraps its answer), so they are saved as before; a chunk that unloads
 * drops its set once saved, as the queue's own entries were dropped when they could not be put back.
 */
public final class HeldTicks {

    /** For the self-tests: ticks waiting now, and put back. */
    public static volatile int held;
    public static volatile long released;

    private static final Map<World, Long2ObjectOpenHashMap<ChunkTicks>> WORLDS = new WeakHashMap<World, Long2ObjectOpenHashMap<ChunkTicks>>();

    private static final class ChunkTicks {
        final int cx;
        final int cz;
        final LinkedHashSet<NextTickListEntry> ticks = new LinkedHashSet<NextTickListEntry>();

        ChunkTicks(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }
    }

    private HeldTicks() {
    }

    private static long key(int cx, int cz) {
        // Mixed: (x << 32 | z) alone hashes to x ^ z, which collides along every diagonal.
        return (((long) cx << 32) | (cz & 0xFFFFFFFFL)) * 0x9E3779B97F4A7C15L;
    }

    /**
     * P91, in place of {@code WorldServer.tickUpdates}' {@code scheduleUpdate(pos, block, 0)} for a tick that could not
     * run: outside the distance it waits in its chunk; anything else (an unloaded area) goes back on the queue as before.
     */
    public static void requeue(WorldServer world, BlockPos pos, Block block, int delay) {
        if (SimulationDistance.distance() > 0 && !SimulationDistance.entityTicking(world, pos)) {
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            Long2ObjectOpenHashMap<ChunkTicks> chunks = WORLDS.get(world);
            if (chunks == null) {
                chunks = new Long2ObjectOpenHashMap<ChunkTicks>();
                WORLDS.put(world, chunks);
            }
            long k = key(cx, cz);
            ChunkTicks c = chunks.get(k);
            if (c == null) {
                c = new ChunkTicks(cx, cz);
                chunks.put(k, c);
            }
            if (c.ticks.add(new NextTickListEntry(pos, block))) {
                held++;
            }
            return;
        }
        world.scheduleUpdate(pos, block, delay);
    }

    /**
     * P91, on {@code WorldServer.getPendingBlockUpdates(StructureBoundingBox, boolean)}'s answer: the waiting ticks in
     * the box added (taken out too when {@code remove}), as the queue's would be. 1.12.2 answers null for none.
     */
    public static List<NextTickListEntry> withHeld(List<NextTickListEntry> found, WorldServer world, StructureBoundingBox box, boolean remove) {
        Long2ObjectOpenHashMap<ChunkTicks> chunks = WORLDS.get(world);
        if (chunks == null || chunks.isEmpty() || box == null) {
            return found;
        }
        List<NextTickListEntry> out = found;
        long time = world.getTotalWorldTime();
        for (int cx = box.minX >> 4; cx <= box.maxX >> 4; cx++) {
            for (int cz = box.minZ >> 4; cz <= box.maxZ >> 4; cz++) {
                ChunkTicks c = chunks.get(key(cx, cz));
                if (c == null) {
                    continue;
                }
                for (Iterator<NextTickListEntry> it = c.ticks.iterator(); it.hasNext(); ) {
                    NextTickListEntry e = it.next();
                    BlockPos p = e.position;
                    // The same test as 1.12.2's for its queue: x and z inside the box's [min, max) on both axes.
                    if (p.getX() >= box.minX && p.getX() < box.maxX && p.getZ() >= box.minZ && p.getZ() < box.maxZ) {
                        if (out == null) {
                            out = new ArrayList<NextTickListEntry>();
                        }
                        NextTickListEntry copy = new NextTickListEntry(p, e.getBlock());
                        copy.setScheduledTime(time);
                        out.add(copy);
                        if (remove) {
                            it.remove();
                            held--;
                        }
                    }
                }
                if (c.ticks.isEmpty()) {
                    chunks.remove(key(cx, cz));
                }
            }
        }
        return out;
    }

    /** Puts a world's waiting ticks back on its queue where their chunk is in range again (all of them with no distance). */
    static void release(WorldServer world) {
        Long2ObjectOpenHashMap<ChunkTicks> chunks = WORLDS.get(world);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        boolean all = SimulationDistance.distance() <= 0;
        List<ChunkTicks> ready = null;
        for (ChunkTicks c : chunks.values()) {
            if (all || SimulationDistance.entityTicking(world, new BlockPos((c.cx << 4) + 8, 64, (c.cz << 4) + 8))) {
                if (ready == null) {
                    ready = new ArrayList<ChunkTicks>();
                }
                ready.add(c);
            }
        }
        if (ready == null) {
            return;
        }
        int budget = RELEASE_PER_TICK;
        for (ChunkTicks c : ready) {
            for (Iterator<NextTickListEntry> it = c.ticks.iterator(); it.hasNext() && budget > 0; budget--) {
                NextTickListEntry e = it.next();
                it.remove();
                held--;
                released++;
                world.scheduleUpdate(e.position, e.getBlock(), 0);
            }
            if (c.ticks.isEmpty()) {
                chunks.remove(key(c.cx, c.cz));
            }
            if (budget <= 0) {
                break;
            }
        }
    }

    /**
     * At most this many waiting ticks go back on the queue a world tick; the rest wait (and are saved) for the next.
     * A chunk can hold a large backlog (a user's frozen ocean: 76,620 in 2,219 chunks, 70,190 of them ocean-floor
     * gravel from 1.18's post-processing), and putting it all back at once fired it in one tick, a visible swath of
     * changes and a lag spike; 4,096 a tick clears that one in under a second.
     */
    static final int RELEASE_PER_TICK = 4096;

    /** Forge events: putting back at the start of each world tick, and forgetting what was saved or unloaded. */
    public static final class Events {

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void worldTick(TickEvent.WorldTickEvent event) {
            if (event.phase == TickEvent.Phase.START && event.world instanceof WorldServer) {
                release((WorldServer) event.world);
            }
        }

        /** After the chunk's save has its waiting ticks (written first): an unloading chunk's set goes with it. */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void chunkSaved(ChunkDataEvent.Save event) {
            if (event.getChunk().isLoaded() || !(event.getWorld() instanceof WorldServer)) {
                return;
            }
            Long2ObjectOpenHashMap<ChunkTicks> chunks = WORLDS.get(event.getWorld());
            if (chunks != null) {
                ChunkTicks c = chunks.remove(key(event.getChunk().x, event.getChunk().z));
                if (c != null) {
                    held -= c.ticks.size();
                }
            }
        }

        @SubscribeEvent
        public void worldUnload(WorldEvent.Unload event) {
            Long2ObjectOpenHashMap<ChunkTicks> chunks = WORLDS.remove(event.getWorld());
            if (chunks != null) {
                for (ChunkTicks c : chunks.values()) {
                    held -= c.ticks.size();
                }
            }
        }
    }
}

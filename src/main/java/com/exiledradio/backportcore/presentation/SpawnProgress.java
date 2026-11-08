package com.exiledradio.backportcore.presentation;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which overworld chunks the server has loaded, generated and populated while it prepares the spawn area at
 * startup: what the world-loading grid draws ({@link LoadingStatus} has the mapping from 1.12.2's steps to
 * 1.14's statuses).
 *
 * <p>It records from the moment the server is about to start until it has started, on the integrated and
 * the dedicated server alike, and then keeps a final {@link Snapshot} and lets the chunks go. The server
 * thread writes; the client's loading screen reads from its own thread, which is why everything here is
 * either a concurrent map or a volatile field.
 *
 * <p>1.12.2 prepares a 25 by 25 chunk square around the spawn chunk (1.14 prepares 23 by 23), and
 * the percentage is the share of that square loaded, as 1.12.2's own "Preparing spawn area" log line counts.
 */
public final class SpawnProgress {

    /** 1.12.2's spawn square: 12 chunks each side of the spawn chunk (192 blocks). */
    public static final int SPAWN_RADIUS = 12;
    /** 1.14.1's grid: 43 by 43 chunks, 21 each side of the spawn chunk. */
    public static final int GRID_RADIUS = 21;

    private static final Map<Long, Chunk> CHUNKS = new ConcurrentHashMap<Long, Chunk>();
    private static volatile boolean active;
    @Nullable
    private static volatile ChunkPos center;
    @Nullable
    private static volatile Snapshot last;

    private SpawnProgress() {
    }

    /** The server is about to start: forget the last world's chunks and record this one's. */
    public static void start() {
        CHUNKS.clear();
        center = null;
        active = true;
    }

    /** The server has started: keep the final picture, let the chunks go. */
    public static void finish() {
        if (active) {
            last = snapshot(GRID_RADIUS);
        }
        active = false;
        CHUNKS.clear();
    }

    /** @return true while the server is preparing its spawn area and this is recording */
    public static boolean isActive() {
        return active;
    }

    /** @return the spawn chunk, once the overworld has loaded and set its spawn; null before */
    @Nullable
    public static ChunkPos center() {
        return center;
    }

    /** @return the picture taken when the last server finished starting, or null */
    @Nullable
    public static Snapshot last() {
        return last;
    }

    /** @return the status of the chunk {@code dx, dz} chunks from the spawn chunk, or null if none recorded */
    @Nullable
    public static LoadingStatus status(int dx, int dz) {
        ChunkPos c = center;
        if (c == null) {
            return null;
        }
        return statusAt(c.x + dx, c.z + dz);
    }

    /** @return the percentage of 1.12.2's spawn square loaded so far, 0 to 100 */
    public static int percent() {
        ChunkPos c = center;
        if (c == null) {
            return 0;
        }
        int loaded = 0;
        for (int dx = -SPAWN_RADIUS; dx <= SPAWN_RADIUS; dx++) {
            for (int dz = -SPAWN_RADIUS; dz <= SPAWN_RADIUS; dz++) {
                if (CHUNKS.containsKey(ChunkPos.asLong(c.x + dx, c.z + dz))) {
                    loaded++;
                }
            }
        }
        int side = SPAWN_RADIUS * 2 + 1;
        return Math.min(100, loaded * 100 / (side * side));
    }

    /** @return every cell of a {@code (2 * radius + 1)} square around the spawn chunk, and the percentage */
    public static Snapshot snapshot(int radius) {
        int side = radius * 2 + 1;
        LoadingStatus[] cells = new LoadingStatus[side * side];
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                cells[z * side + x] = status(x - radius, z - radius);
            }
        }
        return new Snapshot(radius, cells, percent(), center);
    }

    @Nullable
    private static LoadingStatus statusAt(int x, int z) {
        Chunk chunk = CHUNKS.get(ChunkPos.asLong(x, z));
        if (chunk == null) {
            return null;
        }
        if (!chunk.isTerrainPopulated()) {
            return LoadingStatus.LIQUID_CARVERS;
        }
        boolean allGenerated = true;
        boolean allPopulated = true;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Chunk n = CHUNKS.get(ChunkPos.asLong(x + dx, z + dz));
                if (n == null) {
                    allGenerated = false;
                    allPopulated = false;
                } else if (!n.isTerrainPopulated()) {
                    allPopulated = false;
                }
            }
        }
        return allPopulated ? LoadingStatus.FULL : allGenerated ? LoadingStatus.LIGHT : LoadingStatus.FEATURES;
    }

    /** Registered on Forge's event bus by Core. */
    public static final class Events {

        @SubscribeEvent
        public void onChunkLoad(ChunkEvent.Load event) {
            World world = event.getWorld();
            if (active && !world.isRemote && world.provider.getDimension() == 0) {
                Chunk chunk = event.getChunk();
                CHUNKS.put(ChunkPos.asLong(chunk.x, chunk.z), chunk);
            }
        }

        @SubscribeEvent
        public void onChunkUnload(ChunkEvent.Unload event) {
            World world = event.getWorld();
            if (active && !world.isRemote && world.provider.getDimension() == 0) {
                CHUNKS.remove(ChunkPos.asLong(event.getChunk().x, event.getChunk().z));
            }
        }

        /** The overworld's spawn is final by now: it is what the spawn square is laid out around. */
        @SubscribeEvent
        public void onWorldLoad(WorldEvent.Load event) {
            World world = event.getWorld();
            if (active && !world.isRemote && world.provider.getDimension() == 0) {
                BlockPos spawn = world.getSpawnPoint();
                center = new ChunkPos(spawn.getX() >> 4, spawn.getZ() >> 4);
            }
        }
    }

    /** A picture of the grid: cells row by row (z, then x), null for chunks never loaded. */
    public static final class Snapshot {
        public final int radius;
        public final int side;
        public final LoadingStatus[] cells;
        public final int percent;
        @Nullable
        public final ChunkPos center;

        Snapshot(int radius, LoadingStatus[] cells, int percent, @Nullable ChunkPos center) {
            this.radius = radius;
            this.side = radius * 2 + 1;
            this.cells = cells;
            this.percent = percent;
            this.center = center;
        }

        /** @return the cell {@code dx, dz} chunks from the spawn chunk, or null outside the picture */
        @Nullable
        public LoadingStatus at(int dx, int dz) {
            if (Math.abs(dx) > this.radius || Math.abs(dz) > this.radius) {
                return null;
            }
            return this.cells[(dz + this.radius) * this.side + dx + this.radius];
        }
    }
}

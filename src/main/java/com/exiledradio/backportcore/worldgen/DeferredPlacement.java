package com.exiledradio.backportcore.worldgen;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature writes that 1.12's population window cannot hold, finished once the chunks they touch are ready. Since Core
 * 1.4.4.
 *
 * <p>Since 1.13 a chunk's features may write anywhere in the 3 by 3 chunks around it; 1.12 guarantees only the chunk and
 * its three neighbors to +x and +z (32 blocks square from the chunk's corner), so a backport of a newer feature that
 * reaches further (Game Drops' far sulfur spikes, the outer tuff of large sulfur springs) has to leave those writes
 * out. With this queue it hands them over instead: an id (a {@link Handler} it {@link #register registered}), the block
 * box the work reads and writes, and its own data. The work runs, once, at the end of the first server tick at which
 * every chunk the box touches is loaded and every chunk whose population could still write there has populated (a
 * chunk is written by its own population and those of its neighbors to -x, -z and -x -z), so it sees what 1.13's
 * region would have shown it and nothing later overwrites it. Until then it waits, saved with the dimension
 * ({@code data/backportcore_deferred.dat}), across restarts. It never loads or generates a chunk.
 *
 * <p>A handler gone at load (its mod removed) keeps its entries, which never run.
 */
public final class DeferredPlacement {

    /** The work: reads and writes inside the box it was queued with; every chunk there is loaded. Server thread. */
    public interface Handler {
        void place(WorldServer world, BlockPos min, BlockPos max, NBTTagCompound data);
    }

    private static final String NAME = "backportcore_deferred";
    private static final Map<ResourceLocation, Handler> HANDLERS = new ConcurrentHashMap<ResourceLocation, Handler>();

    private DeferredPlacement() {
    }

    /** Registers the handler for an id; any time before a world loads. */
    public static void register(ResourceLocation id, Handler handler) {
        HANDLERS.put(id, handler);
    }

    /**
     * Queues work over the block box from {@code min} to {@code max} (inclusive). It runs at the end of this tick if the
     * box is ready already, else when it is. Server worlds only (ignored on the client).
     */
    public static void queue(World world, ResourceLocation id, BlockPos min, BlockPos max, NBTTagCompound data) {
        if (!(world instanceof WorldServer)) {
            return;
        }
        BlockPos lo = new BlockPos(Math.min(min.getX(), max.getX()), Math.min(min.getY(), max.getY()), Math.min(min.getZ(), max.getZ()));
        BlockPos hi = new BlockPos(Math.max(min.getX(), max.getX()), Math.max(min.getY(), max.getY()), Math.max(min.getZ(), max.getZ()));
        Queue q = queue((WorldServer) world);
        Entry e = new Entry(id, lo, hi, data.copy());
        q.add(e);
        for (long key : e.chunks()) {
            q.touched.add(key);
        }
        q.markDirty();
    }

    /** @return how many entries wait in a world (tests, the integrator's checks) */
    public static int pending(World world) {
        return world instanceof WorldServer ? queue((WorldServer) world).entries.size() : 0;
    }

    /**
     * @return true if work over the columns from (minX, minZ) to (maxX, maxZ) could run now: each chunk there is loaded
     *     and it and its neighbors to -x, -z and -x -z have populated
     */
    public static boolean ready(World world, int minX, int minZ, int maxX, int maxZ) {
        IChunkProvider provider = world.getChunkProvider();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                for (int dx = -1; dx <= 0; dx++) {
                    for (int dz = -1; dz <= 0; dz++) {
                        Chunk c = provider.getLoadedChunk(cx + dx, cz + dz);
                        if (c == null && dx == 0 && dz == 0) {
                            return false;
                        }
                        if (c == null || !c.isTerrainPopulated()) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /** Runs every waiting entry that is ready; @return how many ran. The tick does this; tests call it directly. */
    public static int flush(WorldServer world) {
        Queue q = queue(world);
        Set<Entry> candidates = new LinkedHashSet<Entry>();
        if (q.touched.isEmpty()) {
            return 0;
        }
        for (long key : q.touched) {
            List<Entry> list = q.byChunk.get(key);
            if (list != null) {
                candidates.addAll(list);
            }
        }
        q.touched.clear();
        int ran = 0;
        for (Entry e : candidates) {
            if (!q.entries.contains(e) || !ready(world, e.min.getX(), e.min.getZ(), e.max.getX(), e.max.getZ())) {
                continue;
            }
            Handler h = HANDLERS.get(e.id);
            if (h == null) {
                continue;
            }
            q.remove(e);
            q.markDirty();
            try {
                h.place(world, e.min, e.max, e.data);
                ran++;
            } catch (RuntimeException ex) {
                BackportCore.LOGGER.error("Deferred placement {} at {} failed", e.id, e.min, ex);
            }
        }
        return ran;
    }

    private static Queue queue(WorldServer world) {
        MapStorage storage = world.getPerWorldStorage();
        Queue q = (Queue) storage.getOrLoadData(Queue.class, NAME);
        if (q == null) {
            q = new Queue(NAME);
            storage.setData(NAME, q);
        }
        return q;
    }

    static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    /** Marks the chunks whose readiness a chunk's population or load may have changed. */
    private static void touch(World world, int cx, int cz) {
        if (!(world instanceof WorldServer)) {
            return;
        }
        Queue q = queue((WorldServer) world);
        if (q.entries.isEmpty()) {
            return;
        }
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                q.touched.add(key(cx + dx, cz + dz));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------- storage

    static final class Entry {
        final ResourceLocation id;
        final BlockPos min, max;
        final NBTTagCompound data;

        Entry(ResourceLocation id, BlockPos min, BlockPos max, NBTTagCompound data) {
            this.id = id;
            this.min = min;
            this.max = max;
            this.data = data;
        }

        List<Long> chunks() {
            List<Long> out = new ArrayList<Long>();
            for (int cx = this.min.getX() >> 4; cx <= this.max.getX() >> 4; cx++) {
                for (int cz = this.min.getZ() >> 4; cz <= this.max.getZ() >> 4; cz++) {
                    out.add(key(cx, cz));
                }
            }
            return out;
        }
    }

    /** One dimension's queue. Public for {@code MapStorage}, which makes it by reflection. */
    public static final class Queue extends WorldSavedData {
        final Set<Entry> entries = new LinkedHashSet<Entry>();
        final Map<Long, List<Entry>> byChunk = new HashMap<Long, List<Entry>>();
        /** Chunks to look at this tick (not saved: every entry is looked at again after a load). */
        final Set<Long> touched = new HashSet<Long>();

        public Queue(String name) {
            super(name);
        }

        void add(Entry e) {
            this.entries.add(e);
            for (long key : e.chunks()) {
                List<Entry> list = this.byChunk.get(key);
                if (list == null) {
                    list = new ArrayList<Entry>(2);
                    this.byChunk.put(key, list);
                }
                list.add(e);
            }
        }

        void remove(Entry e) {
            this.entries.remove(e);
            for (long key : e.chunks()) {
                List<Entry> list = this.byChunk.get(key);
                if (list != null) {
                    list.remove(e);
                    if (list.isEmpty()) {
                        this.byChunk.remove(key);
                    }
                }
            }
        }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            this.entries.clear();
            this.byChunk.clear();
            NBTTagList list = tag.getTagList("Entries", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound t = list.getCompoundTagAt(i);
                int[] box = t.getIntArray("Box");
                if (box.length != 6) {
                    continue;
                }
                Entry e = new Entry(new ResourceLocation(t.getString("Id")), new BlockPos(box[0], box[1], box[2]),
                        new BlockPos(box[3], box[4], box[5]), t.getCompoundTag("Data"));
                this.add(e);
                this.touched.addAll(e.chunks());
            }
        }

        @Override
        public NBTTagCompound writeToNBT(NBTTagCompound tag) {
            NBTTagList list = new NBTTagList();
            for (Entry e : this.entries) {
                NBTTagCompound t = new NBTTagCompound();
                t.setString("Id", e.id.toString());
                t.setIntArray("Box", new int[] {e.min.getX(), e.min.getY(), e.min.getZ(), e.max.getX(), e.max.getY(), e.max.getZ()});
                t.setTag("Data", e.data);
                list.appendTag(t);
            }
            tag.setTag("Entries", list);
            return tag;
        }
    }

    // ------------------------------------------------------------------------------------------------- hooks

    /** Last of the world generators (the heaviest weight): marks the populated chunk's area to look at. */
    public static final class Generator implements IWorldGenerator {
        @Override
        public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator generator, IChunkProvider provider) {
            touch(world, chunkX, chunkZ);
        }
    }

    /** Forge's events; registered by Core. */
    public static final class Events {
        @SubscribeEvent
        public void onLoad(ChunkEvent.Load event) {
            if (!event.getWorld().isRemote) {
                touch(event.getWorld(), event.getChunk().x, event.getChunk().z);
            }
        }

        @SubscribeEvent
        public void onTick(TickEvent.WorldTickEvent event) {
            if (event.phase == TickEvent.Phase.END && event.world instanceof WorldServer) {
                flush((WorldServer) event.world);
            }
        }
    }
}

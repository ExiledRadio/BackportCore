package com.exiledradio.backportcore.waterlog;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * {@link Waterlogging#migrateStoredProperty}: turns a stored {@code waterlogged=true} into Fluidlogged
 * API water, once per chunk.
 *
 * <p>A chunk records which blocks it has been checked for (a list of names in its saved data,
 * {@value #TAG}), so adding a migration later checks every chunk again for that block only once.
 * Chunks are read on the chunk loading thread, so they are only queued there; the change itself
 * is made on the server thread at the end of the world tick, through Fluidlogged API.
 */
final class WaterlogMigration {

    static final String TAG = "backportcore:waterlogChecked";

    /** At most this many chunks a tick, so a big area loading at once does not stall the server. */
    private static final int CHUNKS_PER_TICK = 64;

    private static final Map<Block, IProperty<Boolean>> MIGRATIONS = new LinkedHashMap<Block, IProperty<Boolean>>();

    /** Chunks loaded from disk that still need a check, per world. */
    private static final Map<World, Queue<ChunkPos>> PENDING = Collections.synchronizedMap(new WeakHashMap<World, Queue<ChunkPos>>());

    /** Chunk positions waiting in {@link #PENDING}, so their next save does not mark them checked. */
    private static final Map<World, Set<Long>> WAITING = Collections.synchronizedMap(new WeakHashMap<World, Set<Long>>());

    private static boolean listening;

    private WaterlogMigration() {
    }

    static synchronized void register(Block block, IProperty<Boolean> waterlogged) {
        if (!block.getBlockState().getProperties().contains(waterlogged)) {
            throw new IllegalArgumentException(block.getRegistryName() + " has no property " + waterlogged.getName());
        }
        MIGRATIONS.put(block, waterlogged);
    }

    /**
     * Registers the chunk listeners, once, from Backport Core's pre-init.
     *
     * <p>Not from {@link #register}: backports call that while blocks are being registered, and adding a
     * listener to the event bus from inside a registry event left the overworld's decoration broken for
     * the whole session (Update Aquatic's kelp, seagrass and ocean ruins stopped generating). With no
     * migrations registered the listeners do nothing.
     */
    static synchronized void listen() {
        if (!listening) {
            listening = true;
            MinecraftForge.EVENT_BUS.register(new WaterlogMigration.Events());
        }
    }

    /** @return the names of every block being migrated, as stored in a checked chunk */
    private static synchronized List<String> names() {
        List<String> out = new ArrayList<String>();
        for (Block block : MIGRATIONS.keySet()) {
            ResourceLocation name = block.getRegistryName();
            if (name != null) {
                out.add(name.toString());
            }
        }
        return out;
    }

    private static Set<Long> waiting(World world) {
        synchronized (WAITING) {
            Set<Long> set = WAITING.get(world);
            if (set == null) {
                set = Collections.synchronizedSet(new HashSet<Long>());
                WAITING.put(world, set);
            }
            return set;
        }
    }

    /** @return true if a loaded chunk is still waiting for its check */
    static boolean isWaiting(World world, int chunkX, int chunkZ) {
        return waiting(world).contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    /**
     * @return how many states were converted in the chunk
     */
    static int convert(World world, Chunk chunk) {
        Map<Block, IProperty<Boolean>> migrations;
        synchronized (WaterlogMigration.class) {
            migrations = new LinkedHashMap<Block, IProperty<Boolean>>(MIGRATIONS);
        }
        List<BlockPos> found = new ArrayList<BlockPos>();
        for (ExtendedBlockStorage storage : chunk.getBlockStorageArray()) {
            if (storage == null || storage.isEmpty()) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        IBlockState state = storage.get(x, y, z);
                        IProperty<Boolean> property = migrations.get(state.getBlock());
                        if (property != null && state.getValue(property)) {
                            found.add(new BlockPos(chunk.x << 4 | x, storage.getYLocation() + y, chunk.z << 4 | z));
                        }
                    }
                }
            }
        }
        for (BlockPos pos : found) {
            IBlockState state = world.getBlockState(pos);
            IProperty<Boolean> property = migrations.get(state.getBlock());
            world.setBlockState(pos, state.withProperty(property, Boolean.FALSE), 2);
            if (!Waterlogging.setWater(world, pos, true, 2)) {
                BackportCore.LOGGER.debug("A waterlogged {} at {} could not keep its water.", state.getBlock().getRegistryName(), pos);
            }
        }
        return found.size();
    }

    public static final class Events {

        @SubscribeEvent
        public void onChunkLoad(ChunkDataEvent.Load event) {
            World world = event.getWorld();
            if (world == null || world.isRemote) {
                return;
            }
            NBTTagCompound level = event.getData().getCompoundTag("Level");
            Set<String> checked = new HashSet<String>();
            NBTTagList list = level.getTagList(TAG, 8);
            for (int i = 0; i < list.tagCount(); i++) {
                checked.add(list.getStringTagAt(i));
            }
            if (checked.containsAll(names())) {
                return;
            }
            ChunkPos pos = event.getChunk().getPos();
            waiting(world).add(ChunkPos.asLong(pos.x, pos.z));
            synchronized (PENDING) {
                Queue<ChunkPos> queue = PENDING.get(world);
                if (queue == null) {
                    queue = new ConcurrentLinkedQueue<ChunkPos>();
                    PENDING.put(world, queue);
                }
                queue.add(pos);
            }
        }

        @SubscribeEvent
        public void onChunkSave(ChunkDataEvent.Save event) {
            World world = event.getWorld();
            if (world == null || world.isRemote) {
                return;
            }
            ChunkPos pos = event.getChunk().getPos();
            if (waiting(world).contains(ChunkPos.asLong(pos.x, pos.z))) {
                return;
            }
            NBTTagList list = new NBTTagList();
            for (String name : names()) {
                list.appendTag(new NBTTagString(name));
            }
            event.getData().getCompoundTag("Level").setTag(TAG, list);
        }

        @SubscribeEvent
        public void onWorldTick(TickEvent.WorldTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)) {
                return;
            }
            Queue<ChunkPos> queue = PENDING.get(event.world);
            if (queue == null || queue.isEmpty()) {
                return;
            }
            WorldServer world = (WorldServer) event.world;
            for (int i = 0; i < CHUNKS_PER_TICK; i++) {
                ChunkPos pos = queue.poll();
                if (pos == null) {
                    break;
                }
                Chunk chunk = world.getChunkProvider().getLoadedChunk(pos.x, pos.z);
                if (chunk == null) {
                    // Unloaded before its turn: it was saved unchecked, so its next load queues it again.
                    waiting(world).remove(ChunkPos.asLong(pos.x, pos.z));
                    continue;
                }
                int converted = convert(world, chunk);
                if (converted > 0) {
                    BackportCore.LOGGER.debug("Moved {} waterlogged blocks of chunk {}, {} into Fluidlogged API water.",
                            converted, pos.x, pos.z);
                }
                waiting(world).remove(ChunkPos.asLong(pos.x, pos.z));
                // Saved once more either way, so the chunk records it was checked and is not read again.
                chunk.markDirty();
            }
        }
    }
}

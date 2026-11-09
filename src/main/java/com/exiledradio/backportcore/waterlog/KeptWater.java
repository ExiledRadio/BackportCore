package com.exiledradio.backportcore.waterlog;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Without Fluidlogged API: keeps the water an older world saved through it, so the world has its waterlogged
 * blocks back when Fluidlogged API is installed again.
 *
 * <p>Fluidlogged API saves a chunk's kept water as a Forge chunk capability ({@value #KEY} under the chunk's
 * {@value #CAPS}). Without the mod nothing reads that entry, and Forge leaves it out of the chunk's next save. This
 * copies it aside when a chunk is read and writes it back each time the chunk is saved, unchanged. Positions whose
 * block has since been broken or replaced keep their entry too (the entry is never decoded here). Only registered when Fluidlogged API is not installed (see {@link Waterlogging#init}).
 */
final class KeptWater {

    static final String CAPS = "ForgeCaps";
    static final String KEY = "fluidlogged_api:fluid_states";

    /** A world's kept entries, by {@link ChunkPos#asLong}. Chunks are read on the chunk loading thread. */
    private final Map<World, Map<Long, NBTBase>> kept = new WeakHashMap<World, Map<Long, NBTBase>>();

    private synchronized Map<Long, NBTBase> of(World world) {
        Map<Long, NBTBase> map = kept.get(world);
        if (map == null) {
            map = new HashMap<Long, NBTBase>();
            kept.put(world, map);
        }
        return map;
    }

    @SubscribeEvent
    public void load(ChunkDataEvent.Load event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }
        NBTTagCompound caps = event.getData().getCompoundTag("Level").getCompoundTag(CAPS);
        if (!caps.hasKey(KEY)) {
            return;
        }
        Chunk chunk = event.getChunk();
        Map<Long, NBTBase> map = of(world);
        synchronized (this) {
            map.put(ChunkPos.asLong(chunk.x, chunk.z), caps.getTag(KEY).copy());
        }
    }

    @SubscribeEvent
    public void save(ChunkDataEvent.Save event) {
        World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }
        Chunk chunk = event.getChunk();
        long key = ChunkPos.asLong(chunk.x, chunk.z);
        NBTBase data;
        Map<Long, NBTBase> map = of(world);
        synchronized (this) {
            data = map.get(key);
            // A chunk is unloaded before its last save; that save is the entry's last use.
            if (data != null && !chunk.isLoaded()) {
                map.remove(key);
            }
        }
        if (data == null) {
            return;
        }
        NBTTagCompound level = event.getData().getCompoundTag("Level");
        NBTTagCompound caps = level.getCompoundTag(CAPS);
        if (!caps.hasKey(KEY)) {
            caps.setTag(KEY, data.copy());
        }
        level.setTag(CAPS, caps);
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        synchronized (this) {
            kept.remove(event.getWorld());
        }
    }
}

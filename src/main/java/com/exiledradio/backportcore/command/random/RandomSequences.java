package com.exiledradio.backportcore.command.random;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 26.3's {@code RandomSequences}: named Xoroshiro sequences saved with the world (the overworld's
 * {@code data/backportcore_random_sequences.dat}), plus the defaults a new sequence is seeded with.
 */
public final class RandomSequences extends WorldSavedData {

    static final String NAME = "backportcore_random_sequences";

    private int salt;
    private boolean includeWorldSeed = true;
    private boolean includeSequenceId = true;
    private final Map<String, Xoroshiro> sequences = new LinkedHashMap<String, Xoroshiro>();

    public RandomSequences(String name) {
        super(name);
    }

    public static RandomSequences get(World overworld) {
        MapStorage storage = overworld.getMapStorage();
        RandomSequences data = (RandomSequences) storage.getOrLoadData(RandomSequences.class, NAME);
        if (data == null) {
            data = new RandomSequences(NAME);
            storage.setData(NAME, data);
        }
        return data;
    }

    /** {@code get(key, worldSeed)}: the sequence, created with the defaults on first use; using it marks the file dirty. */
    public Xoroshiro get(String key, long worldSeed) {
        Xoroshiro random = sequences.get(key);
        if (random == null) {
            random = create(key, worldSeed, salt, includeWorldSeed, includeSequenceId);
            sequences.put(key, random);
        }
        markDirty();
        return random;
    }

    public static Xoroshiro create(String key, long worldSeed, int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        long seed = (includeWorldSeed ? worldSeed : 0L) ^ (long) salt;
        return Xoroshiro.forSequence(seed, includeSequenceId ? key : null);
    }

    public Iterable<String> keys() {
        return sequences.keySet();
    }

    public void setSeedDefaults(int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        this.salt = salt;
        this.includeWorldSeed = includeWorldSeed;
        this.includeSequenceId = includeSequenceId;
        markDirty();
    }

    public int clear() {
        int count = sequences.size();
        sequences.clear();
        markDirty();
        return count;
    }

    public void reset(String key, long worldSeed) {
        sequences.put(key, create(key, worldSeed, salt, includeWorldSeed, includeSequenceId));
        markDirty();
    }

    public void reset(String key, long worldSeed, int salt, boolean includeWorldSeed, boolean includeSequenceId) {
        sequences.put(key, create(key, worldSeed, salt, includeWorldSeed, includeSequenceId));
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        salt = nbt.getInteger("salt");
        includeWorldSeed = !nbt.hasKey("include_world_seed") || nbt.getBoolean("include_world_seed");
        includeSequenceId = !nbt.hasKey("include_sequence_id") || nbt.getBoolean("include_sequence_id");
        sequences.clear();
        NBTTagCompound all = nbt.getCompoundTag("sequences");
        for (String key : all.getKeySet()) {
            NBTTagCompound source = all.getCompoundTag(key).getCompoundTag("source");
            sequences.put(key, new Xoroshiro(source.getLong("seed_lo"), source.getLong("seed_hi")));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("salt", salt);
        nbt.setBoolean("include_world_seed", includeWorldSeed);
        nbt.setBoolean("include_sequence_id", includeSequenceId);
        NBTTagCompound all = new NBTTagCompound();
        for (Map.Entry<String, Xoroshiro> e : sequences.entrySet()) {
            NBTTagCompound source = new NBTTagCompound();
            source.setLong("seed_lo", e.getValue().lo);
            source.setLong("seed_hi", e.getValue().hi);
            NBTTagCompound sequence = new NBTTagCompound();
            sequence.setTag("source", source);
            all.setTag(e.getKey(), sequence);
        }
        nbt.setTag("sequences", all);
        return nbt;
    }
}

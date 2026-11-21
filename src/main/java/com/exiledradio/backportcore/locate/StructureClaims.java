package com.exiledradio.backportcore.locate;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The structure starts a map already points at, per dimension: today's {@code StructureStart.addReference}, which
 * an exploration map with {@code skip_existing_chunks} sets and checks so that the next map leads elsewhere. 1.12.2's
 * starts carry no reference count, so the claims are a list of start chunks by structure id, saved as
 * {@code data/backportcore_structure_claims.dat} in the dimension's folder.
 */
public final class StructureClaims extends WorldSavedData {

    public static final String NAME = "backportcore_structure_claims";

    private final Set<String> claims = new LinkedHashSet<String>();

    public StructureClaims(String name) {
        super(name);
    }

    /** This dimension's claims. */
    public static StructureClaims of(World world) {
        MapStorage storage = world.getPerWorldStorage();
        StructureClaims data = (StructureClaims) storage.getOrLoadData(StructureClaims.class, NAME);
        if (data == null) {
            data = new StructureClaims(NAME);
            storage.setData(NAME, data);
        }
        return data;
    }

    private static String key(String id, int chunkX, int chunkZ) {
        return StructureLocate.normalize(id) + "@" + chunkX + "," + chunkZ;
    }

    public synchronized boolean claimed(String id, int chunkX, int chunkZ) {
        return this.claims.contains(key(id, chunkX, chunkZ));
    }

    /** Claims the start in this chunk; @return false if it already was */
    public synchronized boolean claim(String id, int chunkX, int chunkZ) {
        boolean added = this.claims.add(key(id, chunkX, chunkZ));
        if (added) {
            this.markDirty();
        }
        return added;
    }

    public synchronized int size() {
        return this.claims.size();
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound nbt) {
        this.claims.clear();
        NBTTagList list = nbt.getTagList("claims", Constants.NBT.TAG_STRING);
        for (int i = 0; i < list.tagCount(); i++) {
            this.claims.add(list.getStringTagAt(i));
        }
    }

    @Override
    public synchronized NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (String c : this.claims) {
            list.appendTag(new NBTTagString(c));
        }
        nbt.setTag("claims", list);
        return nbt;
    }
}

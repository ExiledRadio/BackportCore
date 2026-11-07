package com.exiledradio.backportcore.waterlog;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkDataEvent;

/** Self-test only: reaches the package-private migration. */
public final class MigrationAccess {

    private static final WaterlogMigration.Events EVENTS = new WaterlogMigration.Events();

    private MigrationAccess() {
    }

    public static int convert(World world, Chunk chunk) {
        return WaterlogMigration.convert(world, chunk);
    }

    public static void save(Chunk chunk, NBTTagCompound data) {
        EVENTS.onChunkSave(new ChunkDataEvent.Save(chunk, data));
    }

    public static void load(Chunk chunk, NBTTagCompound data) {
        EVENTS.onChunkLoad(new ChunkDataEvent.Load(chunk, data));
    }

    public static boolean waiting(World world, Chunk chunk) {
        return WaterlogMigration.isWaiting(world, chunk.x, chunk.z);
    }

    public static String tag() {
        return WaterlogMigration.TAG;
    }
}

package com.exiledradio.backportcore.gamerules;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;

/**
 * The Difficulty chosen on 1.19.4's Create New World (its Game tab). 1.12.2 gives a new world the Difficulty option's
 * value ({@code IntegratedServer.loadAllWorlds}); a different choice waits here from Create to the new world's first
 * tick, then is set as the in-game Difficulty button sets it ({@code setDifficultyForAllWorlds}: every dimension, the
 * mob spawning that goes with it, Hard in Hardcore). Joining then copies it into the Difficulty option, as 1.12.2
 * does for every world it joins. Since Core 1.4.5.
 */
public final class NewWorldDifficulty {

    @Nullable
    private static volatile String folder;
    @Nullable
    private static volatile EnumDifficulty pending;
    /** For the self-test: the difficulty the last new world took from Create World, or null. */
    @Nullable
    public static volatile EnumDifficulty lastApplied;

    /** Client side: the world in {@code saves/<folder>} is about to be created with this difficulty. */
    public static void expect(String folder, EnumDifficulty difficulty) {
        NewWorldDifficulty.folder = folder;
        NewWorldDifficulty.pending = difficulty;
    }

    public static void clear() {
        folder = null;
        pending = null;
    }

    @Nullable
    public static EnumDifficulty pending() {
        return pending;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        EnumDifficulty d = pending;
        String expected = folder;
        if (event.phase != TickEvent.Phase.START || d == null || expected == null) {
            return;
        }
        clear();
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        WorldServer overworld = server == null ? null : server.getWorld(0);
        if (overworld == null || !expected.equals(server.getFolderName()) || overworld.getTotalWorldTime() != 0L) {
            return;
        }
        server.setDifficultyForAllWorlds(d);
        lastApplied = overworld.getDifficulty();
        BackportCore.LOGGER.info("Create World: the new world {} took difficulty {}", expected, lastApplied);
    }
}

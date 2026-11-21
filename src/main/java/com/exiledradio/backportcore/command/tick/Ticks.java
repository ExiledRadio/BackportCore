package com.exiledradio.backportcore.command.tick;

import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Where each side's {@link TickRateManager} lives: the running server's (null when {@code /tick} is off or no
 * server runs) and the client's, which follows the server it is connected to and starts over on every
 * disconnect. A world asks the one of its own side, as 26.3's {@code Level.tickRateManager()} does.
 */
public final class Ticks {

    private static volatile ServerTickRateManager server;
    private static final TickRateManager CLIENT = new TickRateManager();

    private Ticks() {
    }

    @Nullable
    public static ServerTickRateManager server() {
        return server;
    }

    static void setServer(@Nullable ServerTickRateManager manager) {
        server = manager;
    }

    public static TickRateManager client() {
        return CLIENT;
    }

    /** @return the manager of the world's side, or null for a server world while {@code /tick} is off */
    @Nullable
    public static TickRateManager of(World world) {
        return world.isRemote ? CLIENT : server;
    }
}

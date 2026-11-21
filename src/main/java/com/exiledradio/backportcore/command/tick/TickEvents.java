package com.exiledradio.backportcore.command.tick;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * {@code /tick}'s server side: the manager exists while a server with the command on runs, ticks at the start of
 * every server tick (26.3's {@code tickServer}), and is sent to each player as 26.3's {@code sendLevelInfo} does.
 */
public final class TickEvents {

    /** Server start, with {@code commands.tick} on. */
    public static void serverStarting(FMLServerStartingEvent event) {
        TickCommand command = new TickCommand();
        Ticks.setServer(new ServerTickRateManager(event.getServer(), command));
        TickHooks.resetLoop();
        event.registerServerCommand(command);
    }

    public static void serverStopped() {
        Ticks.setServer(null);
        TickHooks.resetLoop();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        ServerTickRateManager manager = Ticks.server();
        if (event.phase == TickEvent.Phase.START && manager != null) {
            manager.tick();
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        join(event.player);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        join(event.player);
    }

    @SubscribeEvent
    public void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        join(event.player);
    }

    private static void join(net.minecraft.entity.player.EntityPlayer player) {
        ServerTickRateManager manager = Ticks.server();
        if (manager != null && player instanceof EntityPlayerMP) {
            manager.updateJoiningPlayer((EntityPlayerMP) player);
        }
    }
}

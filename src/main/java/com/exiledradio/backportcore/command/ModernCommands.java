package com.exiledradio.backportcore.command;

import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.command.random.RandomCommand;
import com.exiledradio.backportcore.command.tick.TickEvents;

import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

/**
 * Today's commands that 1.12.2 lacks, each behind its own setting ({@code commands.*}, read when a world loads).
 * Commands are shared infrastructure, so they live in Core; a command that only makes sense with one backport's
 * content belongs to that backport. The survey of the rest is {@code family-notes/COMMANDS.md}.
 */
public final class ModernCommands {

    private ModernCommands() {
    }

    public static void serverStarting(FMLServerStartingEvent event) {
        CoreConfig.Commands on = CoreConfig.commands;
        if (on.tick) {
            TickEvents.serverStarting(event);
        }
        if (on.random) {
            event.registerServerCommand(new RandomCommand());
        }
        if (on.ride) {
            event.registerServerCommand(new RideCommand());
        }
        if (on.rotate) {
            event.registerServerCommand(new RotateCommand());
        }
        if (on.spectate) {
            event.registerServerCommand(new SpectateCommand());
        }
        if (on.teamMsg) {
            event.registerServerCommand(new TeamMsgCommand());
        }
    }

    public static void serverStopped() {
        TickEvents.serverStopped();
    }
}

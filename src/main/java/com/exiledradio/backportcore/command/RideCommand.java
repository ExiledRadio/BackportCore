package com.exiledradio.backportcore.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * 1.19.4's {@code /ride <target> mount <vehicle>} and {@code /ride <target> dismount}, as 26.3 has them (permission
 * level 2, 26.3's messages and checks). {@code target} and {@code vehicle} are single entities: a name, a UUID or a
 * selector matching one.
 */
public final class RideCommand extends CommandBase {

    @Override
    public String getName() {
        return "ride";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.backportcore.ride.usage";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 3 && "mount".equals(args[1])) {
            mount(sender, getEntity(server, sender, args[0]), getEntity(server, sender, args[2]));
        } else if (args.length == 2 && "dismount".equals(args[1])) {
            Entity target = getEntity(server, sender, args[0]);
            Entity vehicle = target.getRidingEntity();
            if (vehicle == null) {
                throw new CommandException("commands.ride.not_riding", target.getDisplayName());
            }
            target.dismountRidingEntity();
            notifyCommandListener(sender, this, "commands.ride.dismount.success", target.getDisplayName(), vehicle.getDisplayName());
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
    }

    private void mount(ICommandSender sender, Entity target, Entity vehicle) throws CommandException {
        Entity current = target.getRidingEntity();
        if (current != null) {
            throw new CommandException("commands.ride.already_riding", target.getDisplayName(), current.getDisplayName());
        }
        if (vehicle instanceof EntityPlayer) {
            throw new CommandException("commands.ride.mount.failure.cant_ride_players");
        }
        if (vehicle == target || target.getRecursivePassengers().contains(vehicle)) {
            throw new CommandException("commands.ride.mount.failure.loop");
        }
        if (target.world != vehicle.world) {
            throw new CommandException("commands.ride.mount.failure.wrong_dimension");
        }
        if (!target.startRiding(vehicle, true)) {
            throw new CommandException("commands.ride.mount.failure.generic", target.getDisplayName(), vehicle.getDisplayName());
        }
        notifyCommandListener(sender, this, "commands.ride.mount.success", target.getDisplayName(), vehicle.getDisplayName());
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1 || args.length == 3 && "mount".equals(args[1])) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, "dismount", "mount");
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return index == 0;
    }
}

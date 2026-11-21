package com.exiledradio.backportcore.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.EntityWeatherEffect;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * 1.15's {@code /spectate [<target> [<player>]]}, as 26.3 has it (permission level 2): a player in spectator mode
 * watches through the target's eyes, as clicking it does; with no target, stops. Lightning (no client tracks it,
 * as 26.3's tracking range 0) cannot be spectated.
 */
public final class SpectateCommand extends CommandBase {

    @Override
    public String getName() {
        return "spectate";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.backportcore.spectate.usage";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length > 2) {
            throw new WrongUsageException(getUsage(sender));
        }
        Entity target = args.length >= 1 ? getEntity(server, sender, args[0]) : null;
        EntityPlayerMP player = args.length == 2 ? getPlayer(server, sender, args[1]) : getCommandSenderAsPlayer(sender);
        if (player == target) {
            throw new CommandException("commands.spectate.self");
        }
        if (!player.isSpectator()) {
            throw new CommandException("commands.spectate.not_spectator", player.getDisplayName());
        }
        if (target instanceof EntityWeatherEffect) {
            throw new CommandException("commands.spectate.cannot_spectate", target.getDisplayName());
        }
        player.setSpectatingEntity(target);
        sender.sendMessage(target != null ? new TextComponentTranslation("commands.spectate.success.started", target.getDisplayName())
                : new TextComponentTranslation("commands.spectate.success.stopped"));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1 || args.length == 2) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return index == 1;
    }
}

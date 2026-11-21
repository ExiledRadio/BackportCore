package com.exiledradio.backportcore.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1.14's {@code /teammsg <message>} and its alias {@code /tm}, as 26.3 has them: anyone on a team sends to every
 * online player on that team ("-> [team] <name> message" to themselves, "[team] <name> message" to the others).
 * The team's name suggests {@code /teammsg } when clicked. Selectors in the message resolve for permission level 2,
 * as 26.3's message argument does.
 */
public final class TeamMsgCommand extends CommandBase {

    @Override
    public String getName() {
        return "teammsg";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("tm");
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    /** No {@code requires}: 1.12.2 would otherwise let only operators run it. */
    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.backportcore.teammsg.usage";
    }

    /** 26.3's {@code PlayerTeam.getFormattedDisplayName()}, with {@code SUGGEST_STYLE}'s hover and click. */
    public static ITextComponent teamName(ScorePlayerTeam team) {
        ITextComponent bracketed = new TextComponentString("[" + team.getDisplayName() + "]");
        bracketed.getStyle().setColor(team.getColor())
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentTranslation("chat.type.team.hover")))
                .setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/teammsg "));
        return bracketed;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        Entity entity = sender.getCommandSenderEntity();
        if (entity == null) {
            throw new CommandException("permissions.requires.entity");
        }
        Team team = entity.getTeam();
        if (!(team instanceof ScorePlayerTeam)) {
            throw new CommandException("commands.teammsg.failed.noteam");
        }
        List<EntityPlayerMP> receivers = new ArrayList<EntityPlayerMP>();
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (player == entity || player.getTeam() == team) {
                receivers.add(player);
            }
        }
        if (receivers.isEmpty()) {
            return;
        }
        ITextComponent message = getChatComponentFromNthArg(sender, args, 0, sender.canUseCommand(2, "teammsg"));
        ITextComponent name = teamName((ScorePlayerTeam) team);
        for (EntityPlayerMP player : receivers) {
            player.sendMessage(new TextComponentTranslation(player == entity ? "chat.type.team.sent" : "chat.type.team.text",
                    name, sender.getDisplayName(), message));
        }
    }
}

package com.exiledradio.backportcore.command.swing;

import com.exiledradio.backportcore.command.Args;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 26.3's {@code /swing [<targets> [mainhand|offhand [<animation> [<duration>]]]]} (permission level 2): every living
 * entity among the targets swings an arm, seen by everyone tracking it and by a player swinging itself. With no
 * targets, the command's own entity swings its main hand. {@code animation} is {@code none}, {@code whack} (the
 * default) or {@code stab}; {@code duration} is a time of at least one tick (default 6), shortened by Haste and
 * lengthened by Mining Fatigue as 26.3's {@code getModifiedSwingDuration}. A player's attack strength is not reset.
 * 26.3's messages: "Made %s swing an arm", "Made %s entities swing their arms", and the error when none is living.
 */
public final class SwingCommand extends CommandBase {

    @Override
    public String getName() {
        return "swing";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.backportcore.swing.usage";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length > 4) {
            throw new WrongUsageException(getUsage(sender));
        }
        List<Entity> targets;
        if (args.length == 0) {
            Entity self = sender.getCommandSenderEntity();
            if (self == null) {
                throw new CommandException("permissions.requires.entity");
            }
            targets = Collections.singletonList(self);
        } else {
            targets = new ArrayList<Entity>(getEntityList(server, sender, args[0]));
        }
        EnumHand hand = EnumHand.MAIN_HAND;
        if (args.length >= 2) {
            if ("mainhand".equals(args[1])) {
                hand = EnumHand.MAIN_HAND;
            } else if ("offhand".equals(args[1])) {
                hand = EnumHand.OFF_HAND;
            } else {
                throw new WrongUsageException(getUsage(sender));
            }
        }
        SwingType type = SwingType.WHACK;
        if (args.length >= 3) {
            type = SwingType.byName(args[2]);
            if (type == null) {
                throw new CommandException("argument.swing_animation.invalid", args[2]);
            }
        }
        int duration = args.length == 4 ? Args.time(args[3], 1) : Swings.DEFAULT_DURATION;
        List<EntityLivingBase> swung = new ArrayList<EntityLivingBase>();
        for (Entity entity : targets) {
            if (entity instanceof EntityLivingBase) {
                Swings.swing((EntityLivingBase) entity, hand, type, duration);
                swung.add((EntityLivingBase) entity);
            }
        }
        if (swung.isEmpty()) {
            throw new CommandException("commands.swing.failed.notliving");
        }
        sender.setCommandStat(CommandResultStats.Type.AFFECTED_ENTITIES, swung.size());
        if (swung.size() == 1) {
            notifyCommandListener(sender, this, "commands.swing.success.single", swung.get(0).getDisplayName());
        } else {
            notifyCommandListener(sender, this, "commands.swing.success.multiple", swung.size());
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, "mainhand", "offhand");
        }
        if (args.length == 3) {
            return getListOfStringsMatchingLastWord(args, SwingType.names());
        }
        return Collections.emptyList();
    }

    /**
     * False: 1.12.2's handler would otherwise run the command once per entity a selector matches, and 26.3 answers once
     * for all of them ("Made 3 entities swing their arms"). {@code getEntityList} reads the selector instead.
     */
    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }
}

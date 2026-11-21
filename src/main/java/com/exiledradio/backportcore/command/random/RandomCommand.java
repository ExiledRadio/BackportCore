package com.exiledradio.backportcore.command.random;

import com.exiledradio.backportcore.command.Args;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 1.20.2's {@code /random}, as 26.3 has it. Everyone may use {@code random value <range>} and
 * {@code random roll <range>} (the roll is told to every player); a named sequence and {@code random reset} need
 * permission level 2. Sequences are 26.3's: Xoroshiro128++ seeded from the world seed, a salt and the sequence's
 * id, saved with the world, so the same world gives the same values as today's game.
 */
public final class RandomCommand extends CommandBase {

    @Override
    public String getName() {
        return "random";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    /** No {@code requires} on the root: 1.12.2 would otherwise let only operators run it. */
    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.backportcore.random.usage";
    }

    private static void requireGamemaster(ICommandSender sender) throws CommandException {
        if (!sender.canUseCommand(2, "random")) {
            throw new CommandException("commands.generic.permission");
        }
    }

    private static String id(String word) throws CommandException {
        try {
            return new ResourceLocation(word).toString();
        } catch (RuntimeException e) {
            throw new CommandException("argument.id.invalid");
        }
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        World overworld = server.getWorld(0);
        World level = sender.getEntityWorld() != null ? sender.getEntityWorld() : overworld;
        String sub = args[0];
        if (("value".equals(sub) || "roll".equals(sub)) && (args.length == 2 || args.length == 3)) {
            Integer[] range = Args.intRange(args[1]);
            String sequence = null;
            if (args.length == 3) {
                requireGamemaster(sender);
                sequence = id(args[2]);
            }
            int min = range[0] != null ? range[0] : Integer.MIN_VALUE;
            int max = range[1] != null ? range[1] : Integer.MAX_VALUE;
            long span = (long) max - (long) min;
            if (span == 0L) {
                throw new CommandException("commands.random.error.range_too_small");
            }
            if (span >= 2147483647L) {
                throw new CommandException("commands.random.error.range_too_large");
            }
            int value = sequence != null
                    ? RandomSequences.get(overworld).get(sequence, level.getSeed()).nextInt(max - min + 1) + min
                    : randomBetweenInclusive(level.rand, min, max);
            if ("roll".equals(sub)) {
                server.getPlayerList().sendMessage(new TextComponentTranslation("commands.random.roll", sender.getDisplayName(), value, min, max));
            } else {
                sender.sendMessage(new TextComponentTranslation("commands.random.sample.success", value));
            }
            sender.setCommandStat(net.minecraft.command.CommandResultStats.Type.QUERY_RESULT, value);
            return;
        }
        if ("reset".equals(sub) && args.length >= 2 && args.length <= 5) {
            requireGamemaster(sender);
            RandomSequences sequences = RandomSequences.get(overworld);
            boolean all = "*".equals(args[1]);
            String key = all ? null : id(args[1]);
            if (args.length == 2) {
                if (all) {
                    sender.sendMessage(new TextComponentTranslation("commands.random.reset.all.success", sequences.clear()));
                } else {
                    sequences.reset(key, level.getSeed());
                    sender.sendMessage(new TextComponentTranslation("commands.random.reset.success", key));
                }
                return;
            }
            int salt = Args.intArg(args[2]);
            boolean includeWorldSeed = args.length < 4 || Args.boolArg(args[3]);
            boolean includeSequenceId = args.length < 5 || Args.boolArg(args[4]);
            if (all) {
                sequences.setSeedDefaults(salt, includeWorldSeed, includeSequenceId);
                sender.sendMessage(new TextComponentTranslation("commands.random.reset.all.success", sequences.clear()));
            } else {
                sequences.reset(key, level.getSeed(), salt, includeWorldSeed, includeSequenceId);
                sender.sendMessage(new TextComponentTranslation("commands.random.reset.success", key));
            }
            return;
        }
        throw new WrongUsageException(getUsage(sender));
    }

    /** {@code Mth.randomBetweenInclusive} over the level's random. */
    static int randomBetweenInclusive(Random random, int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        boolean gamemaster = sender.canUseCommand(2, "random");
        if (args.length == 1) {
            return gamemaster ? getListOfStringsMatchingLastWord(args, "value", "roll", "reset")
                    : getListOfStringsMatchingLastWord(args, "value", "roll");
        }
        boolean sequenceSlot = gamemaster && (args.length == 3 && ("value".equals(args[0]) || "roll".equals(args[0]))
                || args.length == 2 && "reset".equals(args[0]));
        if (sequenceSlot) {
            List<String> names = new ArrayList<String>();
            if ("reset".equals(args[0])) {
                names.add("*");
            }
            for (String key : RandomSequences.get(server.getWorld(0)).keys()) {
                names.add(key);
            }
            return getListOfStringsMatchingLastWord(args, names);
        }
        if ("reset".equals(args[0]) && (args.length == 4 || args.length == 5)) {
            return getListOfStringsMatchingLastWord(args, "true", "false");
        }
        return Collections.emptyList();
    }
}

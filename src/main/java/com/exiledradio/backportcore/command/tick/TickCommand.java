package com.exiledradio.backportcore.command.tick;

import com.exiledradio.backportcore.command.Args;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 1.20.3's {@code /tick}, as 26.3 has it (permission level 3): {@code query}, {@code rate <rate>}, {@code freeze},
 * {@code unfreeze}, {@code step [<time>]}, {@code step stop}, {@code sprint <time>} and {@code sprint stop}. Each
 * message is 26.3's, and each change goes to operators as 26.3's {@code sendSuccess(.., true)} does.
 */
public final class TickCommand extends CommandBase {

    private static final float MAX_TICKRATE = 10000.0F;

    @Override
    public String getName() {
        return "tick";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 3;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.backportcore.tick.usage";
    }

    private static ServerTickRateManager manager() throws CommandException {
        ServerTickRateManager manager = Ticks.server();
        if (manager == null) {
            throw new CommandException("commands.generic.exception");
        }
        return manager;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        ServerTickRateManager manager = manager();
        String sub = args[0];
        if ("query".equals(sub) && args.length == 1) {
            query(server, sender, manager);
        } else if ("rate".equals(sub) && args.length == 2) {
            float rate = Args.floatArg(args[1], 1.0F, MAX_TICKRATE);
            manager.setTickRate(rate);
            notifyCommandListener(sender, this, "commands.tick.rate.success", String.format(Locale.ROOT, "%.1f", rate));
        } else if ("freeze".equals(sub) && args.length == 1) {
            setFreeze(sender, manager, true);
        } else if ("unfreeze".equals(sub) && args.length == 1) {
            setFreeze(sender, manager, false);
        } else if ("step".equals(sub) && args.length <= 2) {
            if (args.length == 2 && "stop".equals(args[1])) {
                if (!manager.stopStepping()) {
                    throw new CommandException("commands.tick.step.stop.fail");
                }
                notifyCommandListener(sender, this, "commands.tick.step.stop.success");
                return;
            }
            int advance = args.length == 2 ? Args.time(args[1], 1) : 1;
            if (!manager.stepGameIfPaused(advance)) {
                throw new CommandException("commands.tick.step.fail");
            }
            notifyCommandListener(sender, this, "commands.tick.step.success", advance);
        } else if ("sprint".equals(sub) && args.length == 2) {
            if ("stop".equals(args[1])) {
                if (!manager.stopSprinting()) {
                    throw new CommandException("commands.tick.sprint.stop.fail");
                }
                notifyCommandListener(sender, this, "commands.tick.sprint.stop.success");
                return;
            }
            int time = Args.time(args[1], 1);
            if (manager.requestGameToSprint(time)) {
                notifyCommandListener(sender, this, "commands.tick.sprint.stop.success");
            }
            notifyCommandListener(sender, this, "commands.tick.status.sprinting");
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
    }

    private void setFreeze(ICommandSender sender, ServerTickRateManager manager, boolean freeze) {
        if (freeze) {
            if (manager.isSprinting()) {
                manager.stopSprinting();
            }
            if (manager.isSteppingForward()) {
                manager.stopStepping();
            }
        }
        manager.setFrozen(freeze);
        notifyCommandListener(sender, this, freeze ? "commands.tick.status.frozen" : "commands.tick.status.running");
    }

    static String nanosToMillisString(long nanos) {
        return String.format(Locale.ROOT, "%.1f", (float) nanos / (float) TickRateManager.NANOS_PER_MILLI);
    }

    /** 26.3's {@code getAverageTickTimeNanos}: the mean of the last 100 ticks. */
    static long averageTickTimeNanos(MinecraftServer server) {
        long sum = 0L;
        for (long t : server.tickTimeArray) {
            sum += t;
        }
        return sum / server.tickTimeArray.length;
    }

    private static void query(MinecraftServer server, ICommandSender sender, ServerTickRateManager manager) {
        long average = averageTickTimeNanos(server);
        String busyTime = nanosToMillisString(average);
        String tickRate = String.format(Locale.ROOT, "%.1f", manager.tickrate());
        if (manager.isSprinting()) {
            sender.sendMessage(new TextComponentTranslation("commands.tick.status.sprinting"));
            sender.sendMessage(new TextComponentTranslation("commands.tick.query.rate.sprinting", tickRate, busyTime));
        } else {
            String status = manager.isFrozen() ? "commands.tick.status.frozen"
                    : manager.nanosecondsPerTick() < average ? "commands.tick.status.lagging" : "commands.tick.status.running";
            sender.sendMessage(new TextComponentTranslation(status));
            sender.sendMessage(new TextComponentTranslation("commands.tick.query.rate.running", tickRate, busyTime,
                    nanosToMillisString(manager.nanosecondsPerTick())));
        }
        long[] samples = Arrays.copyOf(server.tickTimeArray, server.tickTimeArray.length);
        Arrays.sort(samples);
        sender.sendMessage(new TextComponentTranslation("commands.tick.query.percentiles",
                nanosToMillisString(samples[samples.length / 2]),
                nanosToMillisString(samples[(int) ((double) samples.length * 0.95)]),
                nanosToMillisString(samples[(int) ((double) samples.length * 0.99)]),
                samples.length));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "query", "rate", "step", "sprint", "unfreeze", "freeze");
        }
        if (args.length == 2) {
            switch (args[0]) {
                case "rate": return getListOfStringsMatchingLastWord(args, "20");
                case "step": return getListOfStringsMatchingLastWord(args, "stop", "1t", "1s");
                case "sprint": return getListOfStringsMatchingLastWord(args, "stop", "60s", "1d", "3d");
                default: break;
            }
        }
        return Collections.emptyList();
    }
}

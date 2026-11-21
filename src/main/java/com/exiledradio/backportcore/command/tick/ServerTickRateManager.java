package com.exiledradio.backportcore.command.tick;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;

/**
 * 26.3's {@code ServerTickRateManager}: freezing, stepping and sprinting, each change sent to every client
 * ({@link TickNetwork}). The server loop asks it through {@link TickHooks}.
 */
public final class ServerTickRateManager extends TickRateManager {

    private final MinecraftServer server;
    private final ICommand reporter;
    private long remainingSprintTicks;
    private long sprintTickStartTime;
    private long sprintTimeSpend;
    private long scheduledCurrentSprintTicks;
    private boolean previousIsFrozen;

    ServerTickRateManager(MinecraftServer server, ICommand reporter) {
        this.server = server;
        this.reporter = reporter;
    }

    public boolean isSprinting() {
        return scheduledCurrentSprintTicks > 0L;
    }

    @Override
    public void setFrozen(boolean frozen) {
        super.setFrozen(frozen);
        updateStateToClients();
    }

    private void updateStateToClients() {
        TickNetwork.sendState(null, this);
    }

    private void updateStepTicks() {
        TickNetwork.sendStep(null, this);
    }

    public boolean stepGameIfPaused(int ticks) {
        if (!isFrozen()) {
            return false;
        }
        frozenTicksToRun = ticks;
        updateStepTicks();
        return true;
    }

    public boolean stopStepping() {
        if (frozenTicksToRun > 0) {
            frozenTicksToRun = 0;
            updateStepTicks();
            return true;
        }
        return false;
    }

    public boolean stopSprinting() {
        if (remainingSprintTicks > 0L) {
            finishTickSprint();
            return true;
        }
        return false;
    }

    public boolean requestGameToSprint(int time) {
        boolean interrupted = remainingSprintTicks > 0L;
        sprintTimeSpend = 0L;
        scheduledCurrentSprintTicks = time;
        remainingSprintTicks = time;
        previousIsFrozen = isFrozen();
        setFrozen(false);
        return interrupted;
    }

    private void finishTickSprint() {
        long completedTicks = scheduledCurrentSprintTicks - remainingSprintTicks;
        double millisecondsToComplete = Math.max(1.0, (double) sprintTimeSpend) / (double) NANOS_PER_MILLI;
        int ticksPerSecond = (int) ((double) (1000L * completedTicks) / millisecondsToComplete);
        String millisecondsPerTick = String.format(Locale.ROOT, "%.2f",
                completedTicks == 0L ? (double) millisecondsPerTick() : millisecondsToComplete / (double) completedTicks);
        scheduledCurrentSprintTicks = 0L;
        sprintTimeSpend = 0L;
        // createCommandSourceStack().sendSuccess(.., true): the server's console and every operator.
        CommandBase.notifyCommandListener(server, reporter, "commands.tick.sprint.report", ticksPerSecond, millisecondsPerTick);
        remainingSprintTicks = 0L;
        setFrozen(previousIsFrozen);
    }

    public boolean checkShouldSprintThisTick() {
        if (!runGameElements) {
            return false;
        }
        if (remainingSprintTicks > 0L) {
            sprintTickStartTime = System.nanoTime();
            remainingSprintTicks--;
            return true;
        }
        finishTickSprint();
        return false;
    }

    public void endTickWork() {
        sprintTimeSpend += System.nanoTime() - sprintTickStartTime;
    }

    @Override
    public void setTickRate(float rate) {
        super.setTickRate(rate);
        updateStateToClients();
    }

    public void updateJoiningPlayer(EntityPlayerMP player) {
        TickNetwork.sendState(player, this);
        TickNetwork.sendStep(player, this);
    }

    /** For the self-tests: the sprint's remaining ticks. */
    public long remainingSprintTicks() {
        return remainingSprintTicks;
    }
}

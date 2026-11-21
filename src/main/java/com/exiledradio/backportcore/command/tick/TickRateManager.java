package com.exiledradio.backportcore.command.tick;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 1.20.3's {@code TickRateManager}, as 26.3 has it: the target tick rate, whether the game is frozen and how many
 * frozen ticks a step still runs. The server's copy ({@link ServerTickRateManager}) decides; the client's copy
 * ({@link Ticks#client()}) follows the server's packets ({@code TickNetwork}).
 */
public class TickRateManager {

    public static final float MIN_TICKRATE = 1.0F;
    public static final long NANOS_PER_SECOND = 1_000_000_000L;
    public static final long NANOS_PER_MILLI = 1_000_000L;

    protected float tickrate = 20.0F;
    protected long nanosecondsPerTick = NANOS_PER_SECOND / 20L;
    protected int frozenTicksToRun;
    protected boolean runGameElements = true;
    protected boolean isFrozen;

    public void setTickRate(float rate) {
        tickrate = Math.max(rate, MIN_TICKRATE);
        nanosecondsPerTick = (long) ((double) NANOS_PER_SECOND / (double) tickrate);
    }

    public float tickrate() {
        return tickrate;
    }

    public float millisecondsPerTick() {
        return (float) nanosecondsPerTick / (float) NANOS_PER_MILLI;
    }

    public long nanosecondsPerTick() {
        return nanosecondsPerTick;
    }

    public boolean runsNormally() {
        return runGameElements;
    }

    public boolean isSteppingForward() {
        return frozenTicksToRun > 0;
    }

    public void setFrozenTicksToRun(int timeout) {
        frozenTicksToRun = timeout;
    }

    public int frozenTicksToRun() {
        return frozenTicksToRun;
    }

    public void setFrozen(boolean state) {
        isFrozen = state;
    }

    public boolean isFrozen() {
        return isFrozen;
    }

    /** Once at the start of every game tick (server tick, or unpaused client tick). */
    public void tick() {
        runGameElements = !isFrozen || frozenTicksToRun > 0;
        if (frozenTicksToRun > 0) {
            frozenTicksToRun--;
        }
    }

    /** Players and whatever carries a player keep ticking while the game is frozen. */
    public boolean isEntityFrozen(Entity entity) {
        return !runsNormally() && !(entity instanceof EntityPlayer) && !hasPlayerPassenger(entity);
    }

    /** {@code Entity.countPlayerPassengers() > 0}: any player among the recursive passengers. */
    static boolean hasPlayerPassenger(Entity entity) {
        if (!entity.isBeingRidden()) {
            return false;
        }
        for (Entity passenger : entity.getRecursivePassengers()) {
            if (passenger instanceof EntityPlayer) {
                return true;
            }
        }
        return false;
    }

    /** Back to a new world's state (20 per second, running). */
    public void reset() {
        setTickRate(20.0F);
        frozenTicksToRun = 0;
        runGameElements = true;
        isFrozen = false;
    }
}

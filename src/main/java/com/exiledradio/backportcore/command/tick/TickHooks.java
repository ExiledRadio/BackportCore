package com.exiledradio.backportcore.command.tick;

import net.minecraft.advancements.FunctionManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.function.BooleanSupplier;

/**
 * What patches P38 and P39 call (docs/PATCHES.md). P38 runs 1.12.2's server loop at the target tick rate and sprints;
 * P39 holds the frozen parts of the game still, on both sides. With {@code /tick} off, or the game running at 20
 * per second and not frozen, every hook does exactly what the vanilla code did.
 */
public final class TickHooks {

    /** Wall time a sprint runs before the loop gets control back (and the stop flag is checked). */
    private static final long SPRINT_BATCH_NANOS = 50L * TickRateManager.NANOS_PER_MILLI;

    /** Whether the integrated server is paused (the client sets it; a dedicated server never pauses). */
    public static volatile BooleanSupplier paused = () -> false;

    /** The loop's current tick length in whole milliseconds (vanilla's constant 50). */
    private static long slotMs = 50L;
    /** Nanoseconds of tick length the whole-millisecond slots have not used yet. */
    private static long carryNanos;

    private TickHooks() {
    }

    // ---- P38: MinecraftServer.run --------------------------------------------------------------------------------

    /** Every {@code 50L} of {@code MinecraftServer.run}: how long the tick the loop waits for lasts. */
    public static long msPerTick() {
        return slotMs;
    }

    /**
     * Every {@code this.tick()} of {@code MinecraftServer.run}. Runs ticks for one {@link #msPerTick()} slot: one tick
     * at 20 per second; several when a tick is shorter than a millisecond; and while sprinting, ticks back to back
     * for up to 50 ms (26.3's loop runs a sprint tick with no wait at all; 1.12.2's sleeps at least 1 ms between
     * slots, so a sprint batches them).
     */
    public static void runTick(MinecraftServer server) {
        ServerTickRateManager manager = Ticks.server();
        if (manager == null) {
            slotMs = 50L;
            carryNanos = 0L;
            server.tick();
            return;
        }
        if (manager.isSprinting() && !paused.getAsBoolean()) {
            long start = System.nanoTime();
            boolean ran = false;
            while (manager.isSprinting() && manager.checkShouldSprintThisTick()) {
                server.tick();
                manager.endTickWork();
                ran = true;
                if (System.nanoTime() - start >= SPRINT_BATCH_NANOS || !server.isServerRunning()) {
                    break;
                }
            }
            if (ran) {
                // The loop takes the batch's wall time off its budget, so nothing is caught up afterwards.
                slotMs = Math.max(1L, (System.nanoTime() - start) / TickRateManager.NANOS_PER_MILLI);
                carryNanos = 0L;
                return;
            }
        }
        long period = manager.nanosecondsPerTick();
        do {
            server.tick();
            carryNanos += period;
        } while (carryNanos < TickRateManager.NANOS_PER_MILLI && server.isServerRunning());
        slotMs = carryNanos / TickRateManager.NANOS_PER_MILLI;
        carryNanos -= slotMs * TickRateManager.NANOS_PER_MILLI;
    }

    /** {@code getFunctionManager().update()} in {@code updateTimeLightAndEntities}: functions wait while frozen. */
    public static void functions(FunctionManager functions) {
        ServerTickRateManager manager = Ticks.server();
        if (manager == null || manager.runsNormally()) {
            functions.update();
        }
    }

    // ---- P39: freezing ---------------------------------------------------------------------------------------------

    /**
     * The head of {@code WorldServer.tick}: while frozen, only what 26.3's {@code ServerLevel.tick} still does
     * runs (chunks load, unload and reach players); weather, time, spawning, block and fluid ticks, random ticks,
     * villages and block events wait.
     *
     * @return true when the rest of the method is skipped
     */
    public static boolean frozenWorldTick(WorldServer world) {
        ServerTickRateManager manager = Ticks.server();
        if (manager == null || manager.runsNormally()) {
            return false;
        }
        world.getChunkProvider().tick();
        world.getPlayerChunkMap().tick();
        return true;
    }

    /**
     * The head of {@code World.updateEntityWithOptionalForce}, both sides: a frozen entity (not a player, carrying
     * no player) skips its tick, with its passengers.
     */
    public static boolean skipEntity(World world, Entity entity) {
        TickRateManager manager = Ticks.of(world);
        return manager != null && manager.isEntityFrozen(entity);
    }

    /** {@code ((ITickable) tileentity).update()} in {@code World.updateEntities}: block entities wait while frozen. */
    public static void updateTile(ITickable tickable) {
        if (tickable instanceof TileEntity) {
            World world = ((TileEntity) tickable).getWorld();
            TickRateManager manager = world == null ? null : Ticks.of(world);
            if (manager != null && !manager.runsNormally()) {
                return;
            }
        }
        tickable.update();
    }

    /** Back to vanilla's loop (a new server). */
    static void resetLoop() {
        slotMs = 50L;
        carryNanos = 0L;
    }
}

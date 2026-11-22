package com.exiledradio.backportcore.command.tick.client;

import com.exiledradio.backportcore.command.tick.TickHooks;
import com.exiledradio.backportcore.command.tick.TickRateManager;
import com.exiledradio.backportcore.command.tick.Ticks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.crash.CrashReport;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ReportedException;
import net.minecraft.util.Timer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.lang.reflect.Field;

/**
 * {@code /tick} on the client, as 26.3's client follows the server: the client's {@link TickRateManager} ticks
 * with every unpaused client tick, the game's timer runs at the server's rate when it is slower than 20 per second
 * ({@code Minecraft.getTickTargetMillis}), and while frozen, what 26.3 holds still holds still: entities and block
 * entities (P39, shared with the server), particles, ambient particles, the clock and the day (P40), and each
 * frozen entity and every block entity draws at the end of its last tick ({@code DeltaTracker}'s partial tick
 * of 1 for them, P40), so nothing jitters between ticks.
 *
 * <p>26.3 asks for the tick length every frame ({@code DeltaTracker.Timer.advanceGameTime}), so with no world (the
 * title screen and its menus) the game is back at 20 per second on the very next frame; this does the same at the
 * start of every frame. And 26.3 gives a screen its mouse and keys every frame (GLFW callbacks), where 1.12.2 does
 * it once a tick ({@code GuiScreen.handleInput} in {@code runTick}): while the client ticks slower than 20 per
 * second, an open screen also gets its input every frame, so menus answer as fast as ever.
 */
public final class ClientTicks {

    private static final float DEFAULT_TICK_MILLIS = 50.0F;
    private static Field tickLength;
    private static Timer timer;

    private ClientTicks() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new ClientTicks.Events());
        TickHooks.paused = () -> Minecraft.getMinecraft().isGamePaused();
    }

    /** A packet's work, on the client thread. */
    public static void schedule(Runnable task) {
        Minecraft.getMinecraft().addScheduledTask(task);
    }

    /** 26.3's {@code Minecraft.getTickTargetMillis(50)}: never faster than 20 per second, and 50 ms while frozen. */
    public static float tickTargetMillis(boolean inWorld, TickRateManager manager) {
        if (inWorld && manager.runsNormally()) {
            return Math.max(DEFAULT_TICK_MILLIS, manager.millisecondsPerTick());
        }
        return DEFAULT_TICK_MILLIS;
    }

    /** The game timer's tick length now (for the client self-test). */
    public static float timerTickLength() {
        try {
            return tickLength().getFloat(timer());
        } catch (ReflectiveOperationException e) {
            return Float.NaN;
        }
    }

    private static Timer timer() {
        if (timer == null) {
            timer = ObfuscationReflectionHelper.getPrivateValue(Minecraft.class, Minecraft.getMinecraft(), "field_71428_T");
        }
        return timer;
    }

    private static Field tickLength() {
        if (tickLength == null) {
            tickLength = ObfuscationReflectionHelper.findField(Timer.class, "field_194149_e");
        }
        return tickLength;
    }

    /** Whether an open screen gets its input every frame: only while the client ticks slower than 20 per second. */
    public static boolean screenInputEachFrame(boolean inWorld, boolean screenOpen, TickRateManager manager) {
        return screenOpen && tickTargetMillis(inWorld, manager) > DEFAULT_TICK_MILLIS;
    }

    /**
     * The start of every frame: the timer's tick length for the state now, and while the client ticks slower than
     * 20 per second, the open screen's mouse and keys ({@code runTick}'s {@code handleInput}, with its crash report).
     */
    static void frame() {
        syncTimer();
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc.currentScreen;
        if (screenInputEachFrame(mc.world != null, screen != null, Ticks.client())) {
            try {
                screen.handleInput();
            } catch (Throwable t) {
                CrashReport report = CrashReport.makeCrashReport(t, "Updating screen events");
                report.makeCategory("Affected screen").addCrashSection("Screen name", screen.getClass().getCanonicalName());
                throw new ReportedException(report);
            }
            // A screen closed, or a world left, by that input: the tick length for the new state.
            syncTimer();
        }
    }

    static void syncTimer() {
        Minecraft mc = Minecraft.getMinecraft();
        float target = tickTargetMillis(mc.world != null, Ticks.client());
        try {
            Field field = tickLength();
            Timer t = timer();
            if (field.getFloat(t) != target) {
                field.setFloat(t, target);
            }
        } catch (ReflectiveOperationException ignored) {
            // A timer this mod cannot reach keeps vanilla's 20 per second.
        }
    }

    // ---- P40 hooks ---------------------------------------------------------------------------------------------

    private static boolean frozen() {
        return !Ticks.client().runsNormally();
    }

    /** {@code effectRenderer.updateEffects()} in {@code Minecraft.runTick}: particles wait while frozen. */
    public static void updateParticles(ParticleManager particles) {
        if (!frozen()) {
            particles.updateEffects();
        }
    }

    /** {@code world.doVoidFogParticles(x, y, z)} (26.3's {@code animateTick}): ambient particles wait while frozen. */
    public static void animateTick(WorldClient world, int x, int y, int z) {
        if (!frozen()) {
            world.doVoidFogParticles(x, y, z);
        }
    }

    /** {@code setTotalWorldTime} in {@code WorldClient.tick}: the clock waits while frozen (26.3's {@code tickTime}). */
    public static void setTotalWorldTime(World world, long time) {
        if (!frozen()) {
            world.setTotalWorldTime(time);
        }
    }

    /** {@code setWorldTime} in {@code WorldClient.tick}. */
    public static void setWorldTime(World world, long time) {
        if (!frozen()) {
            world.setWorldTime(time);
        }
    }

    /** The head of {@code RenderManager.renderEntityStatic}: a frozen entity draws where its last tick left it. */
    public static float entityPartialTicks(Entity entity, float partialTicks) {
        return Ticks.client().isEntityFrozen(entity) ? 1.0F : partialTicks;
    }

    /** The head of {@code TileEntityRendererDispatcher.render(te, partialTicks, destroyStage)}. */
    public static float tilePartialTicks(TileEntity tile, float partialTicks) {
        return frozen() ? 1.0F : partialTicks;
    }

    public static final class Events {

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.START) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.world == null) {
                // No level, no level's manager (26.3 makes a new one with each ClientLevel).
                if (Ticks.client().tickrate() != 20.0F || Ticks.client().isFrozen() || !Ticks.client().runsNormally()) {
                    Ticks.client().reset();
                }
            } else if (!mc.isGamePaused()) {
                Ticks.client().tick();
            }
            syncTimer();
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                frame();
            }
        }

        @SubscribeEvent
        public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            schedule(() -> {
                Ticks.client().reset();
                syncTimer();
            });
        }
    }
}

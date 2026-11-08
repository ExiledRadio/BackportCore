package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;

/** Runs the steps tick by tick, then stops the server. */
public final class SelfTestRunner {

    static int pass;
    static int fail;

    private static final List<Scheduled> STEPS = new ArrayList<Scheduled>();
    private int tick;
    private int next;
    private int lastRun;
    private boolean done;

    interface Step {
        void run(WorldServer world) throws Exception;
    }

    private static final class Scheduled {
        final int delay;
        final String name;
        final Step body;

        Scheduled(int delay, String name, Step body) {
            this.delay = delay;
            this.name = name;
            this.body = body;
        }
    }

    /** Adds a step that runs {@code delay} ticks after the one before it. */
    static void then(int delay, String name, Step step) {
        STEPS.add(new Scheduled(delay, name, step));
    }

    static boolean check(String name, boolean ok, Object detail) {
        if (ok) {
            pass++;
            BackportCore.LOGGER.info("SELFTEST PASS {} ({})", name, detail);
        } else {
            fail(name, detail);
        }
        return ok;
    }

    static boolean check(String name, boolean ok) {
        return check(name, ok, "");
    }

    static void fail(String name, Object detail) {
        fail++;
        BackportCore.LOGGER.error("SELFTEST FAIL {} ({})", name, detail);
    }

    SelfTestRunner() {
        STEPS.clear();
        WaterTests.schedule();
        WaterRuleTests.schedule();
        DuplicateTests.schedule();
        StructureTests.schedule();
        PatchTests.schedule();
        PoseTests.schedule();
        NetherTests.schedule();
        PresentationTests.schedule();
        PresentationTests.scheduleFinish();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.done) {
            return;
        }
        this.tick++;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        WorldServer world = server.getWorld(0);
        while (this.next < STEPS.size() && this.tick >= this.lastRun + STEPS.get(this.next).delay) {
            Scheduled step = STEPS.get(this.next++);
            this.lastRun = this.tick;
            try {
                step.body.run(world);
            } catch (Throwable t) {
                fail("step " + step.name + " threw", t.toString());
                BackportCore.LOGGER.error("SELFTEST step {} threw", step.name, t);
            }
        }
        if (this.next >= STEPS.size()) {
            this.done = true;
            BackportCore.LOGGER.info("SELFTEST DONE pass={} fail={}", pass, fail);
            server.initiateShutdown();
        }
    }
}

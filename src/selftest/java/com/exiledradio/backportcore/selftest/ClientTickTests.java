package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.command.tick.TickRateManager;
import com.exiledradio.backportcore.command.tick.Ticks;
import com.exiledradio.backportcore.command.tick.client.ClientTicks;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * {@code /tick} on a client (the integrated world): the player types the commands, and the client follows the
 * server: its manager gets the rate and the frozen state, the game timer slows down with the server but never runs
 * faster than 20 per second, and while frozen a pig and the clock hold still on the client, frozen things draw at
 * the end of their last tick (partial tick 1) and the player does not. A step runs on the client too; leaving the
 * world starts the client over.
 */
@SideOnly(Side.CLIENT)
final class ClientTickTests {

    static boolean done;
    private static int step;
    private static int wait;
    private static EntityPig serverPig;
    private static int pigId;
    private static int pigTicks;
    private static long clientTime;

    private ClientTickTests() {
    }

    private static void say(Minecraft mc, String command) {
        mc.player.sendChatMessage(command);
    }

    private static Entity clientPig(Minecraft mc) {
        return mc.world.getEntityByID(pigId);
    }

    /** Each client tick in the test world, after the camera and crouch tests. */
    static void tick(Minecraft mc) {
        if (done || ++wait < 10) {
            return;
        }
        wait = 0;
        TickRateManager client = Ticks.client();
        switch (step++) {
            case 0: {
                check("client tick: P40 applied (client freeze)", "applied".equals(Asm.state("P40")), Asm.state("P40"));
                check("client tick: the client starts at 20 per second, running", client.tickrate() == 20.0F && client.runsNormally(), client.tickrate());
                check("client tick: the timer at 50 ms", ClientTicks.timerTickLength() == 50.0F, ClientTicks.timerTickLength());
                MinecraftServer server = mc.getIntegratedServer();
                server.getPlayerList().setCommandsAllowedForAll(true);
                server.addScheduledTask(() -> {
                    WorldServer world = server.getWorld(mc.player.dimension);
                    serverPig = new EntityPig(world);
                    serverPig.setPosition(mc.player.posX + 2, mc.player.posY, mc.player.posZ);
                    serverPig.setNoAI(true);
                    world.spawnEntity(serverPig);
                });
                say(mc, "/tick rate 10");
                break;
            }
            case 1:
                pigId = serverPig.getEntityId();
                check("client tick: the client gets the server's rate (10)", client.tickrate() == 10.0F, client.tickrate());
                check("client tick: the timer follows a slower server (100 ms)", ClientTicks.timerTickLength() == 100.0F, ClientTicks.timerTickLength());
                say(mc, "/tick rate 40");
                break;
            case 2:
                check("client tick: the client gets 40", client.tickrate() == 40.0F, client.tickrate());
                check("client tick: but never runs faster than 20 per second (50 ms)", ClientTicks.timerTickLength() == 50.0F, ClientTicks.timerTickLength());
                say(mc, "/tick rate 20");
                say(mc, "/tick freeze");
                break;
            case 3: {
                Entity pig = clientPig(mc);
                check("client tick: the client knows the game is frozen", client.isFrozen() && !client.runsNormally(), client.isFrozen());
                check("client tick: the pig is on the client", pig != null, pigId);
                if (pig != null) {
                    pigTicks = pig.ticksExisted;
                    check("client tick: a frozen pig draws at its last tick (partial tick 1)", ClientTicks.entityPartialTicks(pig, 0.3F) == 1.0F, "");
                }
                check("client tick: the player draws between ticks as ever", ClientTicks.entityPartialTicks(mc.player, 0.3F) == 0.3F, "");
                check("client tick: block entities draw at their last tick", ClientTicks.tilePartialTicks(null, 0.3F) == 1.0F, "");
                check("client tick: frozen, the timer stays at 50 ms", ClientTicks.timerTickLength() == 50.0F, ClientTicks.timerTickLength());
                clientTime = mc.world.getTotalWorldTime();
                break;
            }
            case 4: {
                Entity pig = clientPig(mc);
                check("client tick: frozen: the pig holds still on the client", pig != null && pig.ticksExisted == pigTicks,
                        pig == null ? null : pig.ticksExisted - pigTicks);
                check("client tick: frozen: the client's clock holds", mc.world.getTotalWorldTime() == clientTime, mc.world.getTotalWorldTime() - clientTime);
                check("client tick: frozen: the player still ticks", mc.player.ticksExisted > 0, "");
                if (pig != null) {
                    pigTicks = pig.ticksExisted;
                }
                say(mc, "/tick step 5");
                break;
            }
            case 5: {
                Entity pig = clientPig(mc);
                int ran = pig == null ? -1 : pig.ticksExisted - pigTicks;
                check("client tick: a step of 5 runs about 5 client ticks", ran >= 4 && ran <= 6, ran);
                check("client tick: frozen again after the step", !client.runsNormally() && client.frozenTicksToRun() == 0, client.frozenTicksToRun());
                say(mc, "/tick unfreeze");
                break;
            }
            case 6:
                check("client tick: unfrozen on the client", !client.isFrozen() && client.runsNormally(), client.isFrozen());
                check("client tick: the pig draws between ticks again", clientPig(mc) == null || ClientTicks.entityPartialTicks(clientPig(mc), 0.3F) == 0.3F, "");
                say(mc, "/tick rate 10");
                break;
            case 7:
                // Left at 10 per second on purpose: leaving the world must start the client over (checked later).
                check("client tick: at 10 again before leaving", client.tickrate() == 10.0F, client.tickrate());
                if (serverPig != null) {
                    mc.getIntegratedServer().addScheduledTask(() -> serverPig.setDead());
                }
                done = true;
                break;
            default:
                done = true;
                break;
        }
    }

    /** On the title screen after leaving the world. */
    static void afterLeaving() {
        TickRateManager client = Ticks.client();
        check("client tick: leaving the world starts the client over (20, running)", client.tickrate() == 20.0F && client.runsNormally()
                && !client.isFrozen(), client.tickrate());
        check("client tick: the timer back at 50 ms", ClientTicks.timerTickLength() == 50.0F, ClientTicks.timerTickLength());
        check("client tick: no server manager once the integrated server stopped", Ticks.server() == null, "");
    }
}

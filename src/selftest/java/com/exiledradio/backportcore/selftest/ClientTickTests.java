package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.command.tick.TickRateManager;
import com.exiledradio.backportcore.command.tick.Ticks;
import com.exiledradio.backportcore.command.tick.client.ClientTicks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * {@code /tick} on a client (the integrated world): the player types the commands, and the client follows the
 * server: its manager gets the rate and the frozen state, the game timer slows down with the server but never runs
 * faster than 20 per second, and while frozen a pig and the clock hold still on the client, frozen things draw at
 * the end of their last tick (partial tick 1) and the player does not. A step runs on the client too; leaving the
 * world starts the client over, and the title screen is back at 20 per second on its first frame. While the
 * client ticks slower than 20 per second an open screen gets its input every frame, and while frozen the player
 * keeps ticking on both sides.
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
    private static int playerTicks;
    private static int serverPlayerTicks;
    private static CountingScreen screen;
    private static boolean pauseOnLostFocus = true;
    private static float leavingFrameTimer = Float.NaN;

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
                TickRateManager slowed = new TickRateManager();
                slowed.setTickRate(5.0F);
                check("client tick: no world (title screen, menus): 20 per second whatever the last server's rate",
                        ClientTicks.tickTargetMillis(false, slowed) == 50.0F, ClientTicks.tickTargetMillis(false, slowed));
                check("client tick: no world: screens get input once a tick, as ever", !ClientTicks.screenInputEachFrame(false, true, slowed), "");
                check("client tick: in a slowed world an open screen gets its input every frame",
                        ClientTicks.screenInputEachFrame(true, true, client), "");
                // The dev window is often not focused: closing the screen must not bring up the pause menu.
                pauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
                mc.gameSettings.pauseOnLostFocus = false;
                screen = new CountingScreen();
                mc.displayGuiScreen(screen);
                break;
            case 2:
                // 10 client ticks at 10 per second: about a second of frames with the screen open.
                check("client tick: slowed, a screen's input runs every frame, not once a tick (inputs per tick)",
                        screen.ticks > 0 && screen.inputs >= 2 * screen.ticks, screen.inputs + " inputs, " + screen.ticks + " ticks");
                mc.displayGuiScreen(null);
                screen = null;
                say(mc, "/tick rate 40");
                break;
            case 3:
                check("client tick: the client gets 40", client.tickrate() == 40.0F, client.tickrate());
                check("client tick: but never runs faster than 20 per second (50 ms)", ClientTicks.timerTickLength() == 50.0F, ClientTicks.timerTickLength());
                say(mc, "/tick rate 20");
                say(mc, "/tick freeze");
                break;
            case 4: {
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
                playerTicks = mc.player.ticksExisted;
                EntityPlayerMP serverPlayer = serverPlayer(mc);
                serverPlayerTicks = serverPlayer == null ? -1 : serverPlayer.ticksExisted;
                break;
            }
            case 5: {
                Entity pig = clientPig(mc);
                check("client tick: frozen: the pig holds still on the client", pig != null && pig.ticksExisted == pigTicks,
                        pig == null ? null : pig.ticksExisted - pigTicks);
                check("client tick: frozen: the client's clock holds", mc.world.getTotalWorldTime() == clientTime, mc.world.getTotalWorldTime() - clientTime);
                check("client tick: frozen: the player still ticks on the client", mc.player.ticksExisted > playerTicks, mc.player.ticksExisted - playerTicks);
                EntityPlayerMP serverPlayer = serverPlayer(mc);
                check("client tick: frozen: the player still ticks on the server", serverPlayer != null && serverPlayer.ticksExisted > serverPlayerTicks,
                        serverPlayer == null ? null : serverPlayer.ticksExisted - serverPlayerTicks);
                if (pig != null) {
                    pigTicks = pig.ticksExisted;
                }
                say(mc, "/tick step 5");
                break;
            }
            case 6: {
                Entity pig = clientPig(mc);
                int ran = pig == null ? -1 : pig.ticksExisted - pigTicks;
                check("client tick: a step of 5 runs about 5 client ticks", ran >= 4 && ran <= 6, ran);
                check("client tick: frozen again after the step", !client.runsNormally() && client.frozenTicksToRun() == 0, client.frozenTicksToRun());
                say(mc, "/tick unfreeze");
                break;
            }
            case 7:
                check("client tick: unfrozen on the client", !client.isFrozen() && client.runsNormally(), client.isFrozen());
                check("client tick: the pig draws between ticks again", clientPig(mc) == null || ClientTicks.entityPartialTicks(clientPig(mc), 0.3F) == 0.3F, "");
                say(mc, "/tick rate 10");
                break;
            case 8:
                // Left at 10 per second on purpose: leaving the world must start the client over (checked later).
                check("client tick: at 10 again before leaving", client.tickrate() == 10.0F, client.tickrate());
                if (serverPig != null) {
                    mc.getIntegratedServer().addScheduledTask(() -> serverPig.setDead());
                }
                MinecraftForge.EVENT_BUS.register(new LeavingFrame());
                done = true;
                break;
            default:
                done = true;
                break;
        }
    }

    private static EntityPlayerMP serverPlayer(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        return server == null ? null : server.getPlayerList().getPlayerByUUID(mc.player.getUniqueID());
    }

    /** A screen that counts its input calls and its ticks (it does not pause the game). */
    static final class CountingScreen extends GuiScreen {
        int inputs;
        int ticks;

        @Override
        public void handleInput() throws IOException {
            inputs++;
            super.handleInput();
        }

        @Override
        public void updateScreen() {
            ticks++;
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }

    /** The first frame with no world, after Core's own frame work: the title screen's tick length. */
    public static final class LeavingFrame {
        private boolean seen;

        @SubscribeEvent(priority = EventPriority.LOW)
        public void onFrame(TickEvent.RenderTickEvent event) {
            if (!seen && event.phase == TickEvent.Phase.START && Minecraft.getMinecraft().world == null) {
                seen = true;
                leavingFrameTimer = ClientTicks.timerTickLength();
                MinecraftForge.EVENT_BUS.unregister(this);
            }
        }
    }

    /** On the title screen after leaving the world. */
    static void afterLeaving() {
        Minecraft.getMinecraft().gameSettings.pauseOnLostFocus = pauseOnLostFocus;
        check("client tick: the first frame on the title screen is back at 50 ms (left at 10 per second)",
                leavingFrameTimer == 50.0F, leavingFrameTimer);
        TickRateManager client = Ticks.client();
        check("client tick: leaving the world starts the client over (20, running)", client.tickrate() == 20.0F && client.runsNormally()
                && !client.isFrozen(), client.tickrate());
        check("client tick: the timer back at 50 ms", ClientTicks.timerTickLength() == 50.0F, ClientTicks.timerTickLength());
        check("client tick: no server manager once the integrated server stopped", Ticks.server() == null, "");
    }
}

package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.command.swing.client.ClientSwings;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * 26.3's {@code /swing} on a client: the player swings itself (26.3 sends the swing to the swinging player too), for
 * its own duration (40 ticks, where 1.12.2's swing would end after 6), with the hand asked for; {@code none} holds the
 * arm still. Runs after {@link ClientTickTests}.
 */
@SideOnly(Side.CLIENT)
final class ClientSwingTests {

    static boolean done;
    private static int step;
    private static int wait;

    private ClientSwingTests() {
    }

    private static int polls;

    /**
     * Waits for a state, up to 400 client ticks (a loaded machine delays the swing packet and the server's ticks), then
     * checks it.
     */
    private static boolean waiting(boolean reached) {
        if (reached || ++polls > 400) {
            polls = 0;
            return false;
        }
        return true;
    }

    static void tick(Minecraft mc) {
        if (done || --wait > 0) {
            return;
        }
        String me = mc.player.getName();
        int[] state = ClientSwings.state(mc.player);
        switch (step) {
            case 0:
                mc.player.sendChatMessage("/swing " + me + " mainhand whack 40");
                step++;
                break;
            case 1:
                // Ten ticks into the swing: 1.12.2's own would have ended after 6.
                if (waiting(state != null && state[0] >= 10)) {
                    return;
                }
                check("client swing: the player's own swing arrived, 40 ticks", state != null && state[1] == 40,
                        state == null ? "none" : state[0] + "/" + state[1]);
                // Read during the tick, before ClientSwings sets this tick's progress: 1.12.2's update has just moved
                // the driver's last value into prevSwingProgress (what the next frame draws from).
                check("client swing: still swinging after 6+ ticks (1.12.2's would have ended)", mc.player.isSwingInProgress
                        && mc.player.prevSwingProgress > 0.05F && mc.player.prevSwingProgress < 0.6F, mc.player.prevSwingProgress);
                check("client swing: the main hand", mc.player.swingingHand == EnumHand.MAIN_HAND, mc.player.swingingHand);
                step++;
                break;
            case 2:
                if (waiting(state == null)) {
                    return;
                }
                check("client swing: over after its 40 ticks", state == null && !mc.player.isSwingInProgress, mc.player.swingProgress);
                mc.player.sendChatMessage("/swing " + me + " offhand none 30");
                step++;
                break;
            case 3:
                if (waiting(state != null && state[0] >= 2)) {
                    return;
                }
                check("client swing: none: a swing state with the arm held still", state != null && !mc.player.isSwingInProgress
                        && mc.player.swingProgress == 0.0F, state == null ? "none" : mc.player.swingProgress);
                check("client swing: the off hand", mc.player.swingingHand == EnumHand.OFF_HAND, mc.player.swingingHand);
                done = true;
                break;
            default:
                done = true;
                break;
        }
    }
}

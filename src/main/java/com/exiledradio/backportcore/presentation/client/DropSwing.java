package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The arm swings when you drop an item, as in today's game.
 *
 * <ul>
 *   <li>1.15: the drop key swings the main hand when the held stack is not empty ({@code Minecraft.handleKeybinds}:
 *       {@code if (player.drop(...)) player.swing(MAIN_HAND)}). 1.12.2 only sends the drop packet. The key press is
 *       caught as Forge hands it on ({@link InputEvent}), just before {@code processKeyBinds} drops the item, with the
 *       checks vanilla uses there (no screen open, not a spectator).</li>
 *   <li>1.16: every drop swings it ({@code Player.drop}, on the client): throwing from the inventory with the drop key,
 *       clicking outside the window with an item held, and dropping in creative. 1.12.2's client runs those through
 *       {@code ForgeHooks.onPlayerTossEvent}, so {@link ItemTossEvent} fires for the client's own player; the server's
 *       copy of the event is left alone. 26.3 also has the server tell other players; in 1.12.2, as in 1.16 to 1.21.1,
 *       the client's swing reaches them through its own animation packet.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class DropSwing {

    /** For the client self-test: swings started by a drop. */
    public static volatile int swings;

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (Keyboard.getEventKeyState()) {
            dropKey(Keyboard.getEventKey());
        }
    }

    @SubscribeEvent
    public void onMouse(InputEvent.MouseInputEvent event) {
        int button = Mouse.getEventButton();
        if (button >= 0 && Mouse.getEventButtonState()) {
            dropKey(button - 100);
        }
    }

    private static void dropKey(int keyCode) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.currentScreen != null || player.isSpectator() || !VanillaRevisions.dropKeySwing()
                || !mc.gameSettings.keyBindDrop.isActiveAndMatches(keyCode) || player.getHeldItemMainhand().isEmpty()) {
            return;
        }
        swing(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void onToss(ItemTossEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getPlayer() != mc.player || !event.getPlayer().world.isRemote || !VanillaRevisions.everyDropSwings()) {
            return;
        }
        swing(mc.player);
    }

    private static void swing(EntityPlayerSP player) {
        player.swingArm(EnumHand.MAIN_HAND);
        swings++;
    }
}

package com.exiledradio.backportcore.boat.client;

import com.exiledradio.backportcore.boat.BoatNetwork;
import com.exiledradio.backportcore.boat.EntityWoodChestBoat;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The inventory key while riding a chest boat opens the boat's chest, as in 1.19 ({@code Minecraft.handleKeybinds}: a
 * vehicle with its own inventory screen gets {@code sendOpenInventory} instead of the player's inventory), in survival
 * and creative alike. Taken at the start of the client tick, before 1.12.2's own key handling, and only with no screen
 * open, as vanilla reads the key.
 */
@SideOnly(Side.CLIENT)
public final class ChestBoatKey {

    private ChestBoatKey() {
    }

    /** From the client's pre-init. */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new ChestBoatKey());
    }

    /** @return true if the inventory key should open the chest boat's chest: riding one, no screen open */
    public static boolean opensVehicle(Minecraft mc) {
        return mc.player != null && mc.currentScreen == null && mc.player.getRidingEntity() instanceof EntityWoodChestBoat;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!opensVehicle(mc)) {
            return;
        }
        boolean pressed = false;
        while (mc.gameSettings.keyBindInventory.isPressed()) {
            pressed = true;
        }
        if (pressed) {
            BoatNetwork.openVehicleInventory();
        }
    }
}

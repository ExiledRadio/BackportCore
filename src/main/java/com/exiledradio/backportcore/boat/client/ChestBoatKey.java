package com.exiledradio.backportcore.boat.client;

import com.exiledradio.backportcore.boat.BoatNetwork;
import com.exiledradio.backportcore.boat.EntityWoodChestBoat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The inventory key while riding a chest boat opens the boat's chest, as in 1.19 ({@code Minecraft.handleKeybinds}: a
 * vehicle with its own inventory screen gets {@code sendOpenInventory} instead of the player's inventory), in survival
 * and creative alike.
 *
 * <p>1.12.2 reads the key and opens the player's inventory within the same tick the key is pressed
 * ({@code runTickKeyboard} then {@code processKeyBinds}), so a check at the next tick's start never saw the press. The
 * player's inventory screen is taken as it opens instead: when it comes from the inventory key (held down now) with no
 * screen open and the player rides a chest boat, it is cancelled and the boat's chest is asked for.
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
    public void onGuiOpen(GuiOpenEvent event) {
        if (!(event.getGui() instanceof GuiInventory || event.getGui() instanceof GuiContainerCreative)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!opensVehicle(mc) || !mc.gameSettings.keyBindInventory.isKeyDown()) {
            return;
        }
        event.setCanceled(true);
        BoatNetwork.openVehicleInventory();
    }
}

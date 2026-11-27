package com.exiledradio.backportcore.mechanics;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 26.4 Pre-Release 1's {@code Player.interactOn}: an item still on its cooldown (an ender pearl, chorus fruit, a
 * disabled shield, another mod's item) does nothing to the entity it is used on. 1.12.2 let it interact anyway. Both
 * sides, as the event fires on both; with a 26.4 backport installed. Since Core 1.5.0.
 */
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID)
public final class CooldownInteract {

    private CooldownInteract() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void entity(PlayerInteractEvent.EntityInteract event) {
        if (onCooldown(event)) {
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.PASS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void entityAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (onCooldown(event)) {
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.PASS);
        }
    }

    private static boolean onCooldown(PlayerInteractEvent event) {
        ItemStack stack = event.getItemStack();
        return !stack.isEmpty() && event.getEntityPlayer().getCooldownTracker().hasCooldown(stack.getItem())
                && Presentation.installedNewest().isAtLeast("26.4");
    }
}

package com.exiledradio.backportcore.freeze;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Runs {@link FreezeTicks} on every living entity, and keeps watchers told. */
public final class FreezeEvents {

    @SubscribeEvent
    public void livingUpdate(LivingEvent.LivingUpdateEvent event) {
        FreezeTicks.tick(event.getEntityLiving());
    }

    @SubscribeEvent
    public void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntityPlayer() instanceof EntityPlayerMP) {
            FreezeTicks.startTracking((EntityPlayerMP) event.getEntityPlayer(), event.getTarget());
        }
    }

    @SubscribeEvent
    public void joined(EntityJoinWorldEvent event) {
        if (!event.getWorld().isRemote) {
            FreezeTicks.joined(event.getEntity());
        }
    }
}

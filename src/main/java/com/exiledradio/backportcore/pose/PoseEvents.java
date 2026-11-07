package com.exiledradio.backportcore.pose;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** The engine's two common events. Registered by Backport Core in pre-init. */
public final class PoseEvents {

    /**
     * Vanilla fires this as the last line of {@code EntityPlayer.updateSize}. High priority, so other mods'
     * end-of-tick handlers already see the pose's box.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Poses.tick(event.player);
        }
    }

    /** Someone who starts seeing a player in a pose is told it. */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        Entity target = event.getTarget();
        if (target instanceof EntityPlayer && event.getEntityPlayer() instanceof EntityPlayerMP && Poses.active()) {
            Pose pose = Poses.get((EntityPlayer) target);
            if (pose != Pose.STANDING) {
                PoseNetwork.sendTo((EntityPlayerMP) event.getEntityPlayer(), (EntityPlayer) target, pose);
            }
        }
    }
}

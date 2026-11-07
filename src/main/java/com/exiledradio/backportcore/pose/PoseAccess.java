package com.exiledradio.backportcore.pose;

import net.minecraft.entity.player.EntityPlayer;

import java.util.function.Predicate;

/** The client package's way into the engine. Internal: not for backports. */
public final class PoseAccess {

    private PoseAccess() {
    }

    public static void setOwnPlayer(Predicate<EntityPlayer> own) {
        Poses.ownPlayer = own;
    }

    public static void received(EntityPlayer player, Pose pose) {
        Poses.received(player, pose);
    }
}

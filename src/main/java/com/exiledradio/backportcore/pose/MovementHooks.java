package com.exiledradio.backportcore.pose;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Called from vanilla's movement code by patch P1 (docs/PATCHES.md). Each returns vanilla 1.12.2's own value
 * unless the entity is a player and a backport's {@link PlayerMovement} is registered and active.
 *
 * <p>Public only because patched vanilla code calls it; not for backports to call.
 */
public final class MovementHooks {

    /** Vanilla's pull in water, as {@code EntityLivingBase.travel} subtracts it. */
    public static final double WATER_GRAVITY = 0.02D;

    private MovementHooks() {
    }

    /** Start of {@code EntityLivingBase.travel}. */
    public static void beforeTravel(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer && Poses.moving()) {
            for (PlayerMovement m : Poses.movement()) {
                m.beforeTravel((EntityPlayer) entity);
            }
        }
    }

    /** {@code EntityLivingBase.travel}, the value of {@code getWaterSlowDown()}. */
    public static float waterSlowDown(float slowDown, EntityLivingBase entity) {
        if (entity instanceof EntityPlayer && Poses.moving()) {
            for (PlayerMovement m : Poses.movement()) {
                slowDown = m.waterSlowDown((EntityPlayer) entity, slowDown);
            }
        }
        return slowDown;
    }

    /** {@code EntityLivingBase.travel}, the water branch's {@code 0.02D}. */
    public static double waterGravity(EntityLivingBase entity) {
        double gravity = WATER_GRAVITY;
        if (entity instanceof EntityPlayer && Poses.moving()) {
            for (PlayerMovement m : Poses.movement()) {
                gravity = m.waterGravity((EntityPlayer) entity, gravity);
            }
        }
        return gravity;
    }

    /** {@code EntityPlayerSP.onLivingUpdate}, before {@code super.onLivingUpdate()} (from {@code pose.client.ClientHooks}). */
    public static void beforeClientMove(EntityPlayer player) {
        if (Poses.moving()) {
            for (PlayerMovement m : Poses.movement()) {
                m.beforeClientMove(player);
            }
        }
    }
}

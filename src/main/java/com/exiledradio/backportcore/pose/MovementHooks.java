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

    /** P33: the player whose {@code getEyeHeight} is running on this thread. */
    private static final ThreadLocal<EntityPlayer> EYES_OF = new ThreadLocal<EntityPlayer>();

    /** P33: first instruction of {@code EntityPlayer.getEyeHeight}. */
    public static void eyesFor(EntityPlayer player) {
        EYES_OF.set(player);
    }

    /**
     * P33: each return of {@code EntityPlayer.getEyeHeight}. 1.14's crouching eyes, 1.27, for a player in the short
     * crouch's 1.5 box, whether or not the sneak key is down (1.12.2's method answers by the key and the height).
     */
    public static float eyeHeight(float eyes) {
        EntityPlayer player = EYES_OF.get();
        if (player == null) {
            return eyes;
        }
        EYES_OF.set(null); // holds no player past the call
        return eyeHeight(eyes, player);
    }

    /** P33's rule for one player. */
    public static float eyeHeight(float eyes, EntityPlayer player) {
        if (player.height == Poses.CROUCH_HEIGHT && Poses.get(player) == Pose.SNEAKING && !player.isPlayerSleeping()
                && Poses.shortCrouch()) {
            return Poses.CROUCH_EYES;
        }
        return eyes;
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

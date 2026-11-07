package com.exiledradio.backportcore.pose;

import net.minecraft.entity.player.EntityPlayer;

/**
 * A backport's changes to how players move, handed to {@link Poses#registerMovement}. The library's patches
 * (docs/PATCHES.md, P1 and P2) call these from inside vanilla's own movement code; each default is
 * vanilla 1.12.2's behavior. Several registered movements are chained in registration order, each given
 * the value the one before returned.
 *
 * <p>Only players reach these, and only while {@link Poses#active()} is true.
 */
public interface PlayerMovement {

    /** At the start of {@code EntityLivingBase.travel}, before the player is moved this tick. */
    default void beforeTravel(EntityPlayer player) {
    }

    /**
     * @param slowDown what horizontal motion in water is multiplied by after moving (vanilla 0.8), before
     *                 Depth Strider adjusts it
     * @return the slowdown to use
     */
    default float waterSlowDown(EntityPlayer player, float slowDown) {
        return slowDown;
    }

    /**
     * @param gravity what vertical motion in water loses each tick (vanilla 0.02), after the 0.8 drag
     * @return the pull to use
     */
    default double waterGravity(EntityPlayer player, double gravity) {
        return gravity;
    }

    /**
     * Client only, for the client's own player: in {@code EntityPlayerSP.onLivingUpdate}, after vanilla's
     * sprint, flight and elytra checks and before the player moves. The argument is always the client
     * player; a client-side class casts it.
     */
    default void beforeClientMove(EntityPlayer player) {
    }
}

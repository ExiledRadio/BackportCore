package com.exiledradio.backportcore.pose;

import net.minecraft.entity.player.EntityPlayer;

/**
 * A backport's rules for when a player takes a pose, handed to {@link Poses#register}. The library asks
 * them once a tick for every player it simulates (each server player, and a client's own player) and
 * works the pose out from the answers the way 1.14's {@code PlayerEntity.updatePose} does.
 */
public interface PoseRules {

    /** @return false while the rules are switched off; the player then stands as in vanilla 1.12.2 */
    boolean enabled();

    /**
     * Called first, once a tick, for each simulated player: where a backport updates its own state (1.13's
     * swimming flag, for one). Runs at the end of the player's tick, after vanilla's movement.
     */
    default void tick(EntityPlayer player) {
    }

    /** @return true if the player is swimming (1.13's sprint-swimming); the swimming pose wins over others */
    default boolean isSwimming(EntityPlayer player) {
        return false;
    }

    /** @return true if the player is in a spin attack (1.13's Riptide), drawn and sized as 1.14 does */
    default boolean isSpinAttacking(EntityPlayer player) {
        return false;
    }

    /**
     * @return true if a player who has no room to stand or sneak drops into the swimming pose and crawls,
     * as 1.14 does
     */
    default boolean crawls() {
        return false;
    }
}

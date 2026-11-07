package com.exiledradio.backportcore.pose;

/**
 * A player's pose, as 1.14's {@code Pose}: what shape their hitbox has and how they are drawn.
 *
 * <p>The sizes are 1.14.4's {@code PlayerEntity} sizes except sneaking, which keeps 1.12.2's 1.65 box and
 * 1.54 eyes (the shorter 1.14 crouch is a separate change no backport makes yet). 1.14 has one pose for
 * swimming and crawling: crawling is the swimming pose out of water.
 *
 * <p>The order is the packet encoding: add new poses at the end only.
 */
public enum Pose {
    STANDING(0.6F, 1.8F, 1.62F),
    FALL_FLYING(0.6F, 0.6F, 0.4F),
    SLEEPING(0.2F, 0.2F, 0.2F),
    SWIMMING(0.6F, 0.6F, 0.4F),
    SPIN_ATTACK(0.6F, 0.6F, 0.4F),
    SNEAKING(0.6F, 1.65F, 1.54F);

    public final float width;
    public final float height;
    public final float eyeHeight;

    Pose(float width, float height, float eyeHeight) {
        this.width = width;
        this.height = height;
        this.eyeHeight = eyeHeight;
    }

    /** @return true for the poses the library sizes itself; the others are vanilla 1.12.2's own */
    public boolean resized() {
        return this == SWIMMING || this == SPIN_ATTACK;
    }

    static Pose byId(int id) {
        Pose[] all = values();
        return id >= 0 && id < all.length ? all[id] : STANDING;
    }
}

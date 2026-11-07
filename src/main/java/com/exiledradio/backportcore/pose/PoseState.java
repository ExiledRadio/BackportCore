package com.exiledradio.backportcore.pose;

/** One player's pose on one side. */
final class PoseState {

    Pose pose = Pose.STANDING;
    /** The library has shrunk this player's box and lowered their eyes, and owes them a restore. */
    boolean resized;

    /** 1.14's swim animation, client side: rises by 0.09 a tick in the swimming pose, falls by 0.09 out of it. */
    float swim;
    float prevSwim;

    void tickSwim() {
        this.prevSwim = this.swim;
        this.swim = this.pose == Pose.SWIMMING ? Math.min(1.0F, this.swim + 0.09F) : Math.max(0.0F, this.swim - 0.09F);
    }

    float swim(float partialTicks) {
        return this.prevSwim + (this.swim - this.prevSwim) * partialTicks;
    }
}

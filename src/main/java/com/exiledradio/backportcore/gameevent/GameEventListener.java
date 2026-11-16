package com.exiledradio.backportcore.gameevent;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

/** Something that hears game events within a radius. 1.19's and 1.20's {@code GameEventListener}. */
public interface GameEventListener {

    /**
     * Which version's dispatch a listener follows. The two differ in how they measure the radius and in when
     * a listener hears an event that is not {@linkplain #immediate immediate}:
     * <ul>
     *   <li>{@link #V1_19}: within the radius of the exact positions; heard at the end of the world's tick,
     *       with every listener's messages of that tick handed out closest first (Wild Update Backport's
     *       dispatch since 1.0.0).</li>
     *   <li>{@link #V1_20}: within the radius of the two positions' blocks ({@code BlockPos.distSqr}); heard
     *       at once, an event's listeners closest first (1.20's {@code DeliveryMode.BY_DISTANCE}). A 1.20
     *       vibration listener collects the tick's candidates itself ({@link VibrationSystem}).</li>
     * </ul>
     */
    enum Rules {
        V1_19, V1_20
    }

    PositionSource position();

    int radius();

    /**
     * @param origin where the event happened
     * @return true if this listener took the event
     */
    boolean handle(WorldServer world, GameEvent event, Vec3d origin, GameEventContext context);

    /** The dispatch this listener follows; 1.20's unless it says otherwise. */
    default Rules rules() {
        return Rules.V1_20;
    }

    /**
     * {@code handleEventsImmediately} (1.19) / {@code DeliveryMode.UNSPECIFIED} (1.20): true for listeners that
     * must hear an event as it happens (the sculk catalyst, which needs the dying mob's experience before it
     * drops).
     */
    default boolean immediate() {
        return false;
    }

    /**
     * False while the listener's owner has switched its listening off (a mod's own vibrations setting while
     * another mod keeps game events running). An inactive listener hears nothing.
     */
    default boolean active() {
        return true;
    }
}

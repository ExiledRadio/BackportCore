package com.exiledradio.backportcore.gameevent;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every backport's sculk sensors say here when they activate, so another backport can add what a later version
 * does then: 1.20's amethyst resonance ({@code SculkSensorBlock.tryResonateVibration}) is added by Trails &amp;
 * Tales Backport, and reaches Wild Update Backport's sensors too. Sensors call {@link #activated} where 1.20's
 * {@code activate} resonates: after the new state and the neighbor updates, before the tendrils' clicking.
 */
public final class SensorActivation {

    /** Hears sensors activate. */
    public interface Handler {
        void activated(@Nullable Entity source, World world, BlockPos pos, int frequency);
    }

    private static final List<Handler> HANDLERS = new CopyOnWriteArrayList<Handler>();

    private SensorActivation() {
    }

    public static void addHandler(Handler handler) {
        HANDLERS.add(handler);
    }

    /**
     * @param source    who made the vibration, if anyone
     * @param frequency the vibration's frequency (1-15)
     */
    public static void activated(@Nullable Entity source, World world, BlockPos pos, int frequency) {
        if (world.isRemote || frequency < 1 || frequency > 15) {
            return;
        }
        for (Handler h : HANDLERS) {
            h.activated(source, world, pos, frequency);
        }
    }
}

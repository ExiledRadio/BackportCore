package com.exiledradio.backportcore.presentation.versions;

/**
 * Backport Core's own version-feel pieces (Core 1.4.7): what each update changed beyond its content that is useful
 * whichever backport is installed, moved out of the backport that brought it, so that a backport on its own (Caves &amp;
 * Cliffs alone) still has every earlier version's look and feel (1.13's command suggestions, 1.16's F3 + F4). Each
 * version's pieces are registered with {@code Presentation.core(version)} and apply up to the newest installed
 * version. Music and panoramas stay in the backports. {@code docs/CORE_FEEL.md} lists what moved.
 */
public final class CoreLooks {

    private static boolean registered;

    private CoreLooks() {
    }

    /** From Backport Core's construction event, both sides. */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        Look113.register();
        Look114.register();
        Look115.register();
        Look116.register();
        Look118.register();
        Look119.register();
        Look120.register();
        Look121.register();
    }
}

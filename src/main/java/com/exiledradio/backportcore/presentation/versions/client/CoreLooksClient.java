package com.exiledradio.backportcore.presentation.versions.client;

/** The client side of {@code versions.CoreLooks}: from Backport Core's client pre-init (PresentationClient.init). */
public final class CoreLooksClient {

    private static boolean registered;

    private CoreLooksClient() {
    }

    public static synchronized void init() {
        if (registered) {
            return;
        }
        registered = true;
        Look113Client.init();
        Look114Client.init();
        Look115Client.init();
        Look116Client.init();
        Look118Client.init();
        Look119Client.init();
        Look120Client.init();
        Look121Client.init();
    }
}

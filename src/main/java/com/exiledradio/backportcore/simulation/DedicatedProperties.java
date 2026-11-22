package com.exiledradio.backportcore.simulation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

/**
 * server.properties' {@code simulation-distance}. Its own class: {@code DedicatedServer} exists only on a dedicated
 * server, so nothing else may name it (a client verifying a method that does would fail).
 */
final class DedicatedProperties {

    private DedicatedProperties() {
    }

    /** @return the property, written with 1.18's default when the file lacks it (as 1.12.2 does for its own keys) */
    static int simulationDistance(MinecraftServer server) {
        return ((DedicatedServer) server).getIntProperty(SimulationDistance.PROPERTY, SimulationDistance.DEFAULT_SERVER);
    }
}

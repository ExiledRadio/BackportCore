package com.exiledradio.backportcore.light.client;

import com.exiledradio.backportcore.light.LightUpdates.LightPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * The client side of {@link com.exiledradio.backportcore.light.LightUpdates}: the server's light goes into the
 * sections the client holds (as 1.14's {@code ClientPacketListener.handleLightUpdatePacket}), and they redraw.
 */
public final class LightUpdatesClient {

    private LightUpdatesClient() {
    }

    public static void init() {
        LightPacket.Handler.client = packet -> Minecraft.getMinecraft().addScheduledTask(() -> apply(packet));
    }

    static void apply(LightPacket packet) {
        WorldClient world = Minecraft.getMinecraft().world;
        if (world == null) {
            return;
        }
        Chunk chunk = world.getChunkProvider().getLoadedChunk(packet.x, packet.z);
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        for (int i = 0; i < packet.sectionY.length; i++) {
            int y = packet.sectionY[i] << 4;
            for (ExtendedBlockStorage s : sections) {
                if (s == Chunk.NULL_BLOCK_STORAGE || s.getYLocation() != y) {
                    continue;
                }
                System.arraycopy(packet.block[i], 0, s.getBlockLight().getData(), 0, 2048);
                if (packet.sky[i] != null && s.getSkyLight() != null) {
                    System.arraycopy(packet.sky[i], 0, s.getSkyLight().getData(), 0, 2048);
                }
                int bx = packet.x << 4;
                int bz = packet.z << 4;
                world.markBlockRangeForRenderUpdate(bx - 1, y - 1, bz - 1, bx + 16, y + 16, bz + 16);
                break;
            }
        }
    }
}

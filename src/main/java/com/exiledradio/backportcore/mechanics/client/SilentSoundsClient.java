package com.exiledradio.backportcore.mechanics.client;

import com.exiledradio.backportcore.mechanics.SilentSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.server.SPacketCollectItem;
import net.minecraft.network.play.server.SPacketEntityStatus;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;

/**
 * Patch P77's client hooks: 26.4 Pre-Release 1's {@code ClientPacketListener}. With a 26.4 backport, picking up an
 * item or XP makes no sound when the item or the one picking it up is silent (1.12.2 checked neither), and a silent
 * mob's totem pops without its sound. {@code NetHandlerPlayClient.handleCollectItem} and {@code handleEntityStatus}
 * store their packet at their head; their {@code world.playSound(x, y, z, ...)} calls come here.
 */
public final class SilentSoundsClient {

    private static SPacketCollectItem collect;
    private static SPacketEntityStatus status;

    private SilentSoundsClient() {
    }

    public static void collect(SPacketCollectItem packet) {
        collect = packet;
    }

    public static void status(SPacketEntityStatus packet) {
        status = packet;
    }

    public static void collectSound(World world, double x, double y, double z, SoundEvent sound, SoundCategory category,
            float volume, float pitch, boolean distanceDelay) {
        if (collect != null && SilentSounds.on()) {
            Entity from = world.getEntityByID(collect.getCollectedItemEntityID());
            Entity to = world.getEntityByID(collect.getEntityID());
            if (to == null) {
                to = Minecraft.getMinecraft().player;
            }
            if (from != null && from.isSilent() || to != null && to.isSilent()) {
                return;
            }
        }
        world.playSound(x, y, z, sound, category, volume, pitch, distanceDelay);
    }

    /** Opcode 35, the totem: unheard for a silent mob; every other status sound plays. */
    public static void statusSound(World world, double x, double y, double z, SoundEvent sound, SoundCategory category,
            float volume, float pitch, boolean distanceDelay) {
        if (status != null && status.getOpCode() == 35 && SilentSounds.on()) {
            Entity e = status.getEntity(world);
            if (e != null && e.isSilent()) {
                return;
            }
        }
        world.playSound(x, y, z, sound, category, volume, pitch, distanceDelay);
    }
}

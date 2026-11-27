package com.exiledradio.backportcore.mechanics;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/**
 * No entity gets id 0. 1.12.2's firework rocket stores the entity it boosts as an id, with 0 meaning none
 * ({@code EntityFireworkRocket.isAttachedToEntity} is {@code id > 0}), so an elytra flyer whose id is 0 is never
 * boosted. 1.14 fixed it with an {@code OptionalInt}.
 *
 * <p>Ids come from one counter, {@code Entity.nextEntityID}, that starts at 0 with each game. The first entity made
 * gets 0, and that is a player when no entity is made before the player joins: a server that prepares no spawn area at
 * start (1.21.9's no spawn chunks, Tricky Trials Backport), or one whose spawn area holds no entities. That player's
 * rockets did nothing until they joined again. The counter starts at 1 instead.
 */
public final class FirstEntityId {

    private FirstEntityId() {
    }

    /** As the server is about to start, before its worlds make any entity. */
    public static void skipZero() {
        try {
            Field next = ObfuscationReflectionHelper.findField(Entity.class, "field_70152_a");
            if (next.getInt(null) == 0) {
                next.setInt(null, 1);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            BackportCore.LOGGER.warn("Backport Core: could not keep entity id 0 unused; firework boosts may skip the first player.", e);
        }
    }
}

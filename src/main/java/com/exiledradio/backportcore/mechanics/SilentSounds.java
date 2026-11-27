package com.exiledradio.backportcore.mechanics;

import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Patch P77's hooks (docs/PATCHES.md): 26.4 Pre-Release 1's fixes for silent mobs ({@code Silent:1b}) that still made
 * sounds. With a 26.4 backport installed, a silent zombie breaks a door without the break sound (the particles stay),
 * a silent wither breaks blocks quietly, and chorus fruit eaten by a silent mob teleports it without the sound. The
 * client side (item pickup, the totem) is {@code mechanics.client.SilentSoundsClient}.
 *
 * <p>Each patched method stores its mob at its head ({@code BreakDoorGoal.mob}, the wither, the chorus fruit's eater);
 * the method's own sound calls then come here instead of to the world, with the same arguments.
 */
public final class SilentSounds {

    /** The mob of the {@code EntityAIBreakDoor.updateTask} running now (server thread). */
    private static Entity doorMob;
    /** The wither whose {@code updateAITasks} is running now. */
    private static Entity wither;
    /** The mob finishing chorus fruit now. */
    private static Entity chorusEater;
    private static Boolean on;

    private SilentSounds() {
    }

    /** @return true with a 26.4 backport installed */
    public static boolean on() {
        if (on == null) {
            on = Presentation.installedNewest().isAtLeast("26.4");
        }
        return on;
    }

    static boolean silent(Entity e) {
        return e != null && e.isSilent() && on();
    }

    // --- EntityAIBreakDoor.updateTask ------------------------------------------------------------

    public static void door(Entity mob) {
        doorMob = mob;
    }

    /** {@code world.playEvent(type, pos, data)}: 1021 (the break) unheard, 2001 particles without the sound. */
    public static void doorEvent(World world, int type, BlockPos pos, int data) {
        if (silent(doorMob) && (type == 1021 || type == 2001)) {
            if (type == 2001 && world instanceof WorldServer) {
                // 26.4's LevelEvent 2014: the break particles only.
                IBlockState state = Block.getBlockById(data).getDefaultState();
                ((WorldServer) world).spawnParticle(EnumParticleTypes.BLOCK_CRACK, pos.getX() + 0.5D, pos.getY() + 0.5D,
                        pos.getZ() + 0.5D, 40, 0.25D, 0.25D, 0.25D, 0.0D, Block.getStateId(state));
            }
            return;
        }
        world.playEvent(type, pos, data);
    }

    // --- EntityWither.updateAITasks --------------------------------------------------------------

    public static void wither(Entity boss) {
        wither = boss;
    }

    /** {@code world.playEvent(player, type, pos, data)}: 1022 (blocks broken) unheard. */
    public static void witherEvent(World world, EntityPlayer player, int type, BlockPos pos, int data) {
        if (type == 1022 && silent(wither)) {
            return;
        }
        world.playEvent(player, type, pos, data);
    }

    // --- ItemChorusFruit.onItemUseFinish ---------------------------------------------------------

    public static void chorus(Entity eater) {
        chorusEater = eater;
    }

    /** {@code world.playSound(null, x, y, z, ITEM_CHORUS_FRUIT_TELEPORT, ...)} unheard for a silent eater. */
    public static void chorusSound(World world, EntityPlayer player, double x, double y, double z, SoundEvent sound,
            SoundCategory category, float volume, float pitch) {
        if (silent(chorusEater)) {
            return;
        }
        world.playSound(player, x, y, z, sound, category, volume, pitch);
    }
}

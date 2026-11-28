package com.exiledradio.backportcore.mechanics;

import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pistons and blocks that 1.12.2 handles differently from later versions, for patches P84 and P85 (docs/PATCHES.md).
 *
 * <h2>Movable blocks with a tile entity (P84)</h2>
 * 1.12.2's {@code BlockPistonBase.canPush} refuses every block with a tile entity. A block whose later-version
 * counterpart has none (1.14's composter: Village &amp; Pillage keeps one only as a hopper adapter) registers with
 * {@link #allowTileEntity}, and pistons push and pull it like any block. Its block state moves; the tile entity is
 * removed where it was and made anew where it lands, so only a block that keeps nothing in its tile entity (its
 * contents in the state) should register.
 *
 * <h2>Destroyed blocks (P85)</h2>
 * 1.21.5's {@code PistonBaseBlock.moveBlocks}: a block a piston destroys (a torch, a flower) shows its breaking
 * particles and plays its breaking sound, fire excepted. On while a backport of 1.21.5 or later is installed. The
 * server also posts the {@code block_destroy} vibration (1.19) while game events are on.
 */
public final class PistonRules {

    private static final Set<Block> MOVABLE = Collections.newSetFromMap(new ConcurrentHashMap<Block, Boolean>());

    private PistonRules() {
    }

    /** Lets pistons push and pull this block although it has a tile entity (whose contents do not move with it). */
    public static void allowTileEntity(Block block) {
        MOVABLE.add(block);
    }

    /** @return true if pistons may move this block's states although it has a tile entity */
    public static boolean allowsTileEntity(Block block) {
        return MOVABLE.contains(block);
    }

    /** P84, in place of {@code canPush}'s {@code block.hasTileEntity(state)} (Forge's): true when the piston must refuse. */
    public static boolean tileEntityStopsPiston(Block block, IBlockState state) {
        return block.hasTileEntity(state) && !MOVABLE.contains(block);
    }

    /** P84 on vanilla's bytes ({@code block.hasTileEntity()}, without Forge). */
    public static boolean tileEntityStopsPiston(Block block) {
        return block.hasTileEntity() && !MOVABLE.contains(block);
    }

    /** P85, in place of {@code doMove}'s drop of a destroyed block (Forge's {@code dropBlockAsItemWithChance}). */
    public static void destroyedByPiston(Block block, World world, BlockPos pos, IBlockState state, float chance, int fortune) {
        block.dropBlockAsItemWithChance(world, pos, state, chance, fortune);
        breakEffects(block, world, pos, state);
    }

    /** P85 on vanilla's bytes ({@code dropBlockAsItem}). */
    public static void destroyedByPiston(Block block, World world, BlockPos pos, IBlockState state, int fortune) {
        block.dropBlockAsItem(world, pos, state, fortune);
        breakEffects(block, world, pos, state);
    }

    /**
     * Both sides move the blocks. The server posts 1.19's {@code block_destroy} vibration; 1.21.5 shows the effects on
     * the client only (each client its own), fire excepted.
     */
    private static void breakEffects(Block block, World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            com.exiledradio.backportcore.gameevent.GameEvents.emit(world, com.exiledradio.backportcore.gameevent.GameEvent.BLOCK_DESTROY,
                    pos, com.exiledradio.backportcore.gameevent.GameEventContext.of(state));
        } else if (!(block instanceof BlockFire) && breakEffectsOn()) {
            world.playEvent(2001, pos, Block.getStateId(state));
        }
    }

    /** @return true while a backport of 1.21.5 or later is installed */
    public static boolean breakEffectsOn() {
        return Presentation.installedNewest().isAtLeast("1.21.5");
    }
}

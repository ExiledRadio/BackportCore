package com.exiledradio.backportcore.mechanics;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Block behavior newer than 1.12.2 that only a patch can reach, switched on by the backport whose version made the
 * change. With no switch registered, or every one off, vanilla 1.12.2's behavior is kept.
 */
public final class BlockRules {

    private static final List<BooleanSupplier> TORCHES_ON_PANES = new CopyOnWriteArrayList<BooleanSupplier>();

    private BlockRules() {
    }

    /**
     * 1.14 (1.14.3-pre3): torches and redstone torches stand on glass panes and iron bars (any
     * {@code BlockPane}), as today's panes support a torch at their center. Patch P35. Call in pre-init.
     */
    public static void torchesOnPanes(BooleanSupplier on) {
        TORCHES_ON_PANES.add(on);
    }

    /** @return true while some backport has torches on panes switched on */
    public static boolean torchesOnPanes() {
        for (BooleanSupplier on : TORCHES_ON_PANES) {
            if (on.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    /**
     * P35: the body of the {@code canPlaceTorchOnTop} the patch adds to {@code BlockPane}. Off, it answers as
     * Forge's {@code Block.canPlaceTorchOnTop} does (the method the pane inherited), line for line.
     *
     * <p>Public only because patched vanilla code calls it; not for backports to call.
     */
    public static boolean torchOnPane(Block block, IBlockState state, IBlockAccess world, BlockPos pos) {
        if (torchesOnPanes()) {
            return true;
        }
        if (block == Blocks.END_GATEWAY || block == Blocks.LIT_PUMPKIN) {
            return false;
        }
        if (state.isTopSolid() || block instanceof BlockFence || block == Blocks.GLASS || block == Blocks.COBBLESTONE_WALL
                || block == Blocks.STAINED_GLASS) {
            return true;
        }
        BlockFaceShape shape = state.getBlockFaceShape(world, pos, EnumFacing.UP);
        return (shape == BlockFaceShape.SOLID || shape == BlockFaceShape.CENTER || shape == BlockFaceShape.CENTER_BIG)
                && !exception(block);
    }

    /** {@code Block.isExceptionBlockForAttaching}. */
    private static boolean exception(Block b) {
        return b instanceof BlockShulkerBox || b instanceof BlockLeaves || b instanceof BlockTrapDoor || b == Blocks.BEACON
                || b == Blocks.CAULDRON || b == Blocks.GLASS || b == Blocks.GLOWSTONE || b == Blocks.ICE
                || b == Blocks.SEA_LANTERN || b == Blocks.STAINED_GLASS;
    }
}

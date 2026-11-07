package com.exiledradio.backportcore.waterlog;

import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;
import git.jbredwards.fluidlogged_api.api.world.IFluidStatePrimer;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraftforge.fluids.FluidRegistry;

/**
 * Reading and setting a position's water, for generation, tests and the blocks themselves.
 *
 * <p>"Water" means vanilla's water block, or water Fluidlogged API keeps beside a block (a
 * waterlogged block). Every method here is safe on any block: one that cannot hold water just
 * answers false. Server thread, on loaded positions.
 */
public final class Waterlogging {

    /**
     * Fluidlogged API's extra block flag: setting a block with it removes any water kept there, where
     * Fluidlogged API would otherwise keep it with a block that can hold it.
     */
    public static final int REMOVE_WATER = 32;

    private Waterlogging() {
    }

    /** @return true if the position holds water: a water block, or a block with water kept beside it */
    public static boolean hasWater(IBlockAccess world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getMaterial() == Material.WATER) {
            return true;
        }
        FluidState fluid = FluidloggedUtils.getFluidState(world, pos, state);
        return !fluid.isEmpty() && FluidloggedUtils.isCompatibleFluid(fluid.getFluid(), FluidRegistry.WATER);
    }

    /**
     * @return true if the position's water is a still source, as ocean water and the water of a
     * waterlogged block are
     */
    public static boolean hasWaterSource(IBlockAccess world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        FluidState fluid = FluidloggedUtils.getFluidState(world, pos, state);
        if (!fluid.isEmpty()) {
            return FluidloggedUtils.isCompatibleFluid(fluid.getFluid(), FluidRegistry.WATER) && fluid.isSource();
        }
        return false;
    }

    /** @return true if this state can hold water at the position (asks Fluidlogged API, configs included) */
    public static boolean canHoldWater(IBlockState state, World world, BlockPos pos) {
        return !FluidloggedUtils.isFluid(state)
                && FluidloggedUtils.isStateFluidloggable(state, world, pos, FluidState.of(FluidRegistry.WATER));
    }

    /**
     * Adds a water source to the block at the position, or removes its water.
     *
     * <p>With block flag 1 the new water is live at once, as a bucket's is: it flows into open space
     * beside it. Without it (generation) it stays still until something next to it changes, as
     * generated water does.
     *
     * @param flags block flags, as {@code World.setBlockState} takes them (2: tell clients, 1: update)
     * @return false if the block cannot hold water (adding), is a fluid itself (removing), or the
     * world refused the change (water vaporizes in the Nether)
     */
    public static boolean setWater(World world, BlockPos pos, boolean water, int flags) {
        IBlockState state = world.getBlockState(pos);
        if (FluidloggedUtils.isFluid(state)) {
            return false;
        }
        FluidState current = FluidloggedUtils.getFluidState(world, pos, state);
        if (water) {
            if (hasWaterSource(world, pos)) {
                return true;
            }
            FluidState source = FluidState.of(FluidRegistry.WATER);
            if (!FluidloggedUtils.isStateFluidloggable(state, world, pos, source)) {
                return false;
            }
            if (!FluidloggedUtils.setFluidState(world, pos, state, source, true, flags)) {
                return false;
            }
            if ((flags & 1) != 0 && !world.isRemote) {
                // Still water only starts to flow when told something changed, as Fluidlogged API does
                // when it keeps a water block's water.
                FluidState placed = FluidloggedUtils.getFluidState(world, pos);
                if (!placed.isEmpty()) {
                    placed.getState().neighborChanged(world, pos, state.getBlock(), pos);
                }
            }
            return true;
        }
        if (current.isEmpty()) {
            return true;
        }
        return FluidloggedUtils.setFluidState(world, pos, state, FluidState.EMPTY, false, flags);
    }

    /**
     * Places a block and keeps the water that was there: kelp into the sea, a reef block over
     * seagrass, a sea pickle on the floor. Where the new block cannot hold water, it replaces the water
     * as any block would.
     *
     * @return true if the block was placed
     */
    public static boolean placeKeepingWater(World world, BlockPos pos, IBlockState state, int flags) {
        boolean wet = hasWaterSource(world, pos);
        if (!world.setBlockState(pos, state, flags & ~REMOVE_WATER) && world.getBlockState(pos) != state) {
            return false;
        }
        // Fluidlogged API keeps a water block's water with a block that can hold it; this covers a
        // position whose water was itself kept beside an older block, and flowing water.
        if (wet && !hasWaterSource(world, pos) && canHoldWater(world.getBlockState(pos), world, pos)) {
            setWater(world, pos, true, flags);
        }
        return true;
    }

    /**
     * Places a block with a water source, whatever was there: a structure's waterlogged stairs, kelp
     * planted in a pocket of air.
     *
     * @return true if the block was placed with its water; false if it was not placed, or was placed
     * dry because it cannot hold water here
     */
    public static boolean placeWithWater(World world, BlockPos pos, IBlockState state, int flags) {
        if (!world.setBlockState(pos, state, flags & ~REMOVE_WATER) && world.getBlockState(pos) != state) {
            return false;
        }
        return setWater(world, pos, true, flags);
    }

    /**
     * Places a block with no water, whatever was there.
     *
     * @return true if the block was placed
     */
    public static boolean placeDry(World world, BlockPos pos, IBlockState state, int flags) {
        boolean placed = world.setBlockState(pos, state, flags | REMOVE_WATER);
        // The same state already there is no change to the world, so its water would stay.
        IBlockState now = world.getBlockState(pos);
        if (now == state && !FluidloggedUtils.isFluid(now) && !FluidloggedUtils.getFluidState(world, pos, now).isEmpty()) {
            setWater(world, pos, false, flags);
        }
        return placed || now == state;
    }

    /**
     * For terrain generation that fills a {@link ChunkPrimer} (before decoration): gives the block at
     * these chunk-local coordinates a water source, or takes it away. The block itself is set on the
     * primer as usual. Fluidlogged API moves the water into the chunk with the blocks.
     */
    public static void setWater(ChunkPrimer primer, int x, int y, int z, boolean water) {
        IFluidStatePrimer.of(primer).setFluidState(x, y, z, water ? FluidState.of(FluidRegistry.WATER) : FluidState.EMPTY);
    }

    /**
     * One-time move of a released block's stored {@code waterlogged} property into Fluidlogged API
     * water, for a block that shipped its own waterlogging before this library had any.
     *
     * <p>Keep the property in the block's state, and its bit in the metadata, while older worlds may
     * still hold it. Each chunk is checked once, when it loads, and every state of {@code block} with
     * the property true becomes the same state with it false plus a water source. New placements
     * should never set it. Call in pre-init or init.
     */
    public static void migrateStoredProperty(Block block, IProperty<Boolean> waterlogged) {
        WaterlogMigration.register(block, waterlogged);
    }
}

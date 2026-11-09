package com.exiledradio.backportcore.waterlog.fluidlogged;

import com.exiledradio.backportcore.waterlog.WaterBackend;
import com.exiledradio.backportcore.waterlog.Waterlogging;

import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;
import git.jbredwards.fluidlogged_api.api.world.IFluidStatePrimer;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraftforge.fluids.FluidRegistry;

/**
 * {@link Waterlogging} with Fluidlogged API installed: water is a water block, or water Fluidlogged API keeps
 * beside a block. Only loaded with Fluidlogged API installed.
 */
public final class FluidloggedWater implements WaterBackend {

    @Override
    public boolean hasWater(IBlockAccess world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getMaterial() == Material.WATER) {
            return true;
        }
        FluidState fluid = FluidloggedUtils.getFluidState(world, pos, state);
        return !fluid.isEmpty() && FluidloggedUtils.isCompatibleFluid(fluid.getFluid(), FluidRegistry.WATER);
    }

    @Override
    public boolean hasWaterSource(IBlockAccess world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        FluidState fluid = FluidloggedUtils.getFluidState(world, pos, state);
        if (!fluid.isEmpty()) {
            return FluidloggedUtils.isCompatibleFluid(fluid.getFluid(), FluidRegistry.WATER) && fluid.isSource();
        }
        return false;
    }

    @Override
    public boolean canHoldWater(IBlockState state, World world, BlockPos pos) {
        return !FluidloggedUtils.isFluid(state)
                && FluidloggedUtils.isStateFluidloggable(state, world, pos, FluidState.of(FluidRegistry.WATER));
    }

    @Override
    public boolean setWater(World world, BlockPos pos, boolean water, int flags) {
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

    @Override
    public boolean placeKeepingWater(World world, BlockPos pos, IBlockState state, int flags) {
        boolean wet = hasWaterSource(world, pos);
        if (!world.setBlockState(pos, state, flags & ~Waterlogging.REMOVE_WATER) && world.getBlockState(pos) != state) {
            return false;
        }
        // Fluidlogged API keeps a water block's water with a block that can hold it; this covers a
        // position whose water was itself kept beside an older block, and flowing water.
        if (wet && !hasWaterSource(world, pos) && canHoldWater(world.getBlockState(pos), world, pos)) {
            setWater(world, pos, true, flags);
        }
        return true;
    }

    @Override
    public boolean placeWithWater(World world, BlockPos pos, IBlockState state, int flags) {
        if (!world.setBlockState(pos, state, flags & ~Waterlogging.REMOVE_WATER) && world.getBlockState(pos) != state) {
            return false;
        }
        return setWater(world, pos, true, flags);
    }

    @Override
    public boolean placeDry(World world, BlockPos pos, IBlockState state, int flags) {
        boolean placed = world.setBlockState(pos, state, flags | Waterlogging.REMOVE_WATER);
        // The same state already there is no change to the world, so its water would stay.
        IBlockState now = world.getBlockState(pos);
        if (now == state && !FluidloggedUtils.isFluid(now) && !FluidloggedUtils.getFluidState(world, pos, now).isEmpty()) {
            setWater(world, pos, false, flags);
        }
        return placed || now == state;
    }

    @Override
    public void setWater(ChunkPrimer primer, int x, int y, int z, boolean water) {
        IFluidStatePrimer.of(primer).setFluidState(x, y, z, water ? FluidState.of(FluidRegistry.WATER) : FluidState.EMPTY);
    }
}

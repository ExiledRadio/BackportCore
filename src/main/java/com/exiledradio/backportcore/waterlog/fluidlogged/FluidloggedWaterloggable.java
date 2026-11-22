package com.exiledradio.backportcore.waterlog.fluidlogged;

import com.exiledradio.backportcore.waterlog.Waterloggable;

import git.jbredwards.fluidlogged_api.api.block.IFluidloggable;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import javax.annotation.Nonnull;

/**
 * Fluidlogged API's side of {@link Waterloggable}: its {@link IFluidloggable} questions, answered from the
 * block's three choices. Only loaded with Fluidlogged API installed.
 *
 * <p>No block names this interface. Backport Core's coremod ({@code asm.WaterloggableTransformer}) adds it to
 * every class that lists {@link Waterloggable} among its interfaces, when Fluidlogged API is on the class path,
 * so the block is an {@code IFluidloggable} exactly as if it had said so, and still loads when it is not.
 */
public interface FluidloggedWaterloggable extends IFluidloggable {

    @Override
    default boolean isFluidloggable(@Nonnull IBlockState state, @Nonnull World world, @Nonnull BlockPos pos) {
        return ((Waterloggable) this).canHoldWater(state, world, pos);
    }

    @Override
    default boolean isFluidValid(@Nonnull IBlockState state, @Nonnull World world, @Nonnull BlockPos pos, @Nonnull Fluid fluid) {
        return FluidloggedUtils.isCompatibleFluid(fluid, FluidRegistry.WATER) && ((Waterloggable) this).canHoldWater(state, world, pos);
    }

    /**
     * Applies the change itself, then tells the block, so {@link Waterloggable#onWaterChanged} sees the new water.
     * A drained {@link Waterloggable.Need#REQUIRED} block is removed instead.
     */
    @Nonnull
    @Override
    default EnumActionResult onFluidChange(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState here,
                                           @Nonnull FluidState newFluid, int blockFlags) {
        Waterloggable block = (Waterloggable) this;
        boolean water = !newFluid.isEmpty();
        boolean had = !FluidloggedUtils.getFluidState(world, pos, here).isEmpty();
        FluidloggedUtils.setFluidState_Internal(world, world.getChunk(pos), here, pos, newFluid, blockFlags);
        if (!water && block.waterNeed(here) == Waterloggable.Need.REQUIRED) {
            // The water is already gone, so nothing is left behind: no drop, no source.
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), blockFlags);
            return EnumActionResult.SUCCESS;
        }
        if (had != water) {
            // Flowing water changing level is not news to the block; coming or going is.
            block.onWaterChanged(world, pos, here, water);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    default boolean canFluidFlow(@Nonnull net.minecraft.world.IBlockAccess world, @Nonnull BlockPos pos, @Nonnull IBlockState here,
                                 @Nonnull net.minecraft.util.EnumFacing side) {
        Boolean flows = ((Waterloggable) this).waterFlowsThrough(here, world, pos, side);
        return flows != null ? flows : IFluidloggable.super.canFluidFlow(world, pos, here, side);
    }

    @Override
    default boolean overrideApplyDefaultsSetting() {
        return true;
    }
}

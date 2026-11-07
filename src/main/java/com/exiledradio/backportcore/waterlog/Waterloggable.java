package com.exiledradio.backportcore.waterlog;

import git.jbredwards.fluidlogged_api.api.block.IFluidloggable;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import javax.annotation.Nonnull;

/**
 * A family block that 1.13 or later made waterloggable.
 *
 * <p>Waterlogging itself is Fluidlogged API's: it stores the water beside the block, makes it flow,
 * render, drown and carry boats. This interface extends its {@link IFluidloggable}, so Fluidlogged API
 * treats the block as its own, and answers Fluidlogged API's questions from three simple choices a
 * block makes instead: {@link #waterNeed}, {@link #canHoldWater} and {@link #onWaterChanged}. A block
 * never deals with {@code FluidState} directly, and never overrides the {@code IFluidloggable}
 * methods below.
 *
 * <h2>What Fluidlogged API decides</h2>
 * A server's Fluidlogged API config and other mods' {@code FluidloggableEvent} handlers are asked
 * first and win over these answers; with neither, these apply even when the player has turned
 * Fluidlogged API's {@code allowDefaults} off ({@link #overrideApplyDefaultsSetting}). Water only:
 * lava and other mods' fluids are refused.
 *
 * <h2>Rules for implementers</h2>
 * <ul>
 * <li>No {@code waterlogged} property. The water is not part of the block state; ask
 * {@link Waterlogging#hasWater}. A block released with such a property keeps it until older worlds
 * have been converted: see {@link Waterlogging#migrateStoredProperty}.</li>
 * <li>A {@link Need#REQUIRED} block that is drained is removed, with no drop. Its placement rules
 * (only into water) stay the block's own.</li>
 * <li>{@code shouldFluidRender} is client-only in Fluidlogged API; if a block overrides it, mark the
 * override {@code @SideOnly(Side.CLIENT)}.</li>
 * </ul>
 */
public interface Waterloggable extends IFluidloggable {

    /** How a block lives with water. */
    enum Need {
        /** May hold water or not: lanterns, chains, slabs, stairs, walls, scaffolding, campfires, sea pickles, coral. */
        OPTIONAL,
        /** Only exists in water: kelp, kelp plant, seagrass, tall seagrass, bubble columns. Drained, it is removed. */
        REQUIRED
    }

    /** @return whether this state may be dry; {@link Need#OPTIONAL} unless overridden */
    default Need waterNeed(IBlockState state) {
        return Need.OPTIONAL;
    }

    /** @return whether this state can hold water at all (false for a double slab); true unless overridden */
    default boolean canHoldWater(IBlockState state, IBlockAccess world, BlockPos pos) {
        return true;
    }

    /**
     * Called after this position's water came or went (not when flowing water only changes level): a
     * bucket, flowing water, a command or generation through {@link Waterlogging}. Coral starts its death timer here, sea pickles relight,
     * campfires go out. Server and client both; check {@code world.isRemote} for server-only work.
     *
     * @param hasWater whether the position holds water now
     */
    default void onWaterChanged(World world, BlockPos pos, IBlockState state, boolean hasWater) {
    }

    // ---- Fluidlogged API's questions, answered from the three above. Blocks do not override these.

    @Override
    default boolean isFluidloggable(@Nonnull IBlockState state, @Nonnull World world, @Nonnull BlockPos pos) {
        return canHoldWater(state, world, pos);
    }

    @Override
    default boolean isFluidValid(@Nonnull IBlockState state, @Nonnull World world, @Nonnull BlockPos pos, @Nonnull Fluid fluid) {
        return FluidloggedUtils.isCompatibleFluid(fluid, FluidRegistry.WATER) && canHoldWater(state, world, pos);
    }

    /**
     * Applies the change itself, then tells the block, so {@link #onWaterChanged} sees the new water.
     * A drained {@link Need#REQUIRED} block is removed instead.
     */
    @Nonnull
    @Override
    default EnumActionResult onFluidChange(@Nonnull World world, @Nonnull BlockPos pos, @Nonnull IBlockState here,
                                           @Nonnull FluidState newFluid, int blockFlags) {
        boolean water = !newFluid.isEmpty();
        boolean had = !FluidloggedUtils.getFluidState(world, pos, here).isEmpty();
        FluidloggedUtils.setFluidState_Internal(world, world.getChunk(pos), here, pos, newFluid, blockFlags);
        if (!water && waterNeed(here) == Need.REQUIRED) {
            // The water is already gone, so nothing is left behind: no drop, no source.
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), blockFlags);
            return EnumActionResult.SUCCESS;
        }
        if (had != water) {
            // Flowing water changing level is not news to the block; coming or going is.
            onWaterChanged(world, pos, here, water);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    default boolean overrideApplyDefaultsSetting() {
        return true;
    }
}

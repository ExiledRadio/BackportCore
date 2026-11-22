package com.exiledradio.backportcore.waterlog;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * A family block that 1.13 or later made waterloggable.
 *
 * <p>Waterlogging itself is Fluidlogged API's, when it is installed: it stores the water beside the block, makes
 * it flow, render, drown and carry boats. Without it, nothing holds water: every block of this interface is as
 * dry as it was before 1.13, and {@link Waterlogging} answers "no water" for it. A block names no Fluidlogged API
 * type, so it loads either way.
 *
 * <p>With Fluidlogged API installed, Backport Core's coremod adds its {@code fluidlogged.FluidloggedWaterloggable}
 * (an {@code IFluidloggable}) to every class that implements this interface, so Fluidlogged API treats the block
 * as its own and is answered from three simple choices the block makes: {@link #waterNeed}, {@link #canHoldWater}
 * and {@link #onWaterChanged}. A block never deals with Fluidlogged API directly and never implements its
 * {@code IFluidloggable} itself.
 *
 * <h2>What Fluidlogged API decides</h2>
 * Backport Core's own {@code FluidloggableEvent} handler ({@code fluidlogged.WaterRules}, at {@code HIGHEST}) is
 * asked first, for every block of this interface: it refuses every fluid but water, and flowing water unless the
 * block opts in ({@link #acceptsFlowingWater}), before Fluidlogged API's built-in list of stairs, slabs and the
 * like could let them in. A server's Fluidlogged API config and other mods' handlers come next; with neither,
 * these answers apply even when the player has turned Fluidlogged API's {@code allowDefaults} off. A dry block
 * between two water sources takes a source, as 1.13 and later ({@link SourceForming}).
 *
 * <h2>Rules for implementers</h2>
 * <ul>
 * <li>No {@code waterlogged} property. The water is not part of the block state; ask
 * {@link Waterlogging#hasWater}. A block released with such a property keeps it until older worlds
 * have been converted: see {@link Waterlogging#migrateStoredProperty}.</li>
 * <li>A {@link Need#REQUIRED} block that is drained is removed, with no drop. Its placement rules
 * (only into water) stay the block's own.</li>
 * <li>Behavior that only makes sense with water kept in the block (a campfire that a bucket waterlogs instead of
 * putting out) checks {@link Waterlogging#isAvailable()} and keeps its pre-1.13 behavior when it is false.</li>
 * </ul>
 */
public interface Waterloggable {

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
     * @return whether flowing (non-source) water may run into this state; false unless overridden, as in 1.13 and
     * later, where a waterloggable block only takes a water source ({@code IWaterLoggable.canPlaceLiquid}: {@code
     * fluid == Fluids.WATER}). Backport Core's rules ask this before Fluidlogged API's own list.
     */
    default boolean acceptsFlowingWater(IBlockState state) {
        return false;
    }

    /**
     * @return whether water may flow across {@code side} of this state, into or out of it; null (the default) leaves it
     * to Fluidlogged API's own answer, by the face's shape. A block with a full collision box answers false, as 26.3's
     * {@code FlowingFluid.canPassThroughWall} stops flow through full faces: waterlogged leaves keep their water.
     * Asked only with Fluidlogged API.
     */
    @javax.annotation.Nullable
    default Boolean waterFlowsThrough(IBlockState state, IBlockAccess world, BlockPos pos, net.minecraft.util.EnumFacing side) {
        return null;
    }

    /**
     * Called after this position's water came or went (not when flowing water only changes level): a
     * bucket, flowing water, a command or generation through {@link Waterlogging}. Coral starts its death timer
     * here, sea pickles relight, campfires go out. Server and client both; check {@code world.isRemote} for
     * server-only work. Never called without Fluidlogged API.
     *
     * @param hasWater whether the position holds water now
     */
    default void onWaterChanged(World world, BlockPos pos, IBlockState state, boolean hasWater) {
    }
}

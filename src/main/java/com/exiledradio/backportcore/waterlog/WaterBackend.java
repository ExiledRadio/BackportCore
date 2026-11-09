package com.exiledradio.backportcore.waterlog;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;

/**
 * What {@link Waterlogging} does, with Fluidlogged API ({@code fluidlogged.FluidloggedWater}) or without it
 * ({@link #NONE}). Internal to Backport Core: backports call {@link Waterlogging}.
 */
public interface WaterBackend {

    boolean hasWater(IBlockAccess world, BlockPos pos);

    boolean hasWaterSource(IBlockAccess world, BlockPos pos);

    boolean canHoldWater(IBlockState state, World world, BlockPos pos);

    boolean setWater(World world, BlockPos pos, boolean water, int flags);

    boolean placeKeepingWater(World world, BlockPos pos, IBlockState state, int flags);

    boolean placeWithWater(World world, BlockPos pos, IBlockState state, int flags);

    boolean placeDry(World world, BlockPos pos, IBlockState state, int flags);

    void setWater(ChunkPrimer primer, int x, int y, int z, boolean water);

    /**
     * Without Fluidlogged API: water is only ever a water block (vanilla's, or a block of water material such as
     * kelp), and no other block holds any. Every block is placed as {@code World.setBlockState} places it.
     */
    WaterBackend NONE = new WaterBackend() {

        @Override
        public boolean hasWater(IBlockAccess world, BlockPos pos) {
            return world.getBlockState(pos).getMaterial() == Material.WATER;
        }

        @Override
        public boolean hasWaterSource(IBlockAccess world, BlockPos pos) {
            IBlockState state = world.getBlockState(pos);
            if (state.getMaterial() != Material.WATER) {
                return false;
            }
            // Vanilla's water, and water-material blocks that carry its LEVEL (kelp, seagrass) at 0.
            return !state.getPropertyKeys().contains(BlockLiquid.LEVEL) || state.getValue(BlockLiquid.LEVEL) == 0;
        }

        @Override
        public boolean canHoldWater(IBlockState state, World world, BlockPos pos) {
            return false;
        }

        @Override
        public boolean setWater(World world, BlockPos pos, boolean water, int flags) {
            // Nothing can be given water; a block that is not water has none to lose.
            return !water && world.getBlockState(pos).getMaterial() != Material.WATER;
        }

        @Override
        public boolean placeKeepingWater(World world, BlockPos pos, IBlockState state, int flags) {
            return world.setBlockState(pos, state, flags & ~Waterlogging.REMOVE_WATER) || world.getBlockState(pos) == state;
        }

        @Override
        public boolean placeWithWater(World world, BlockPos pos, IBlockState state, int flags) {
            world.setBlockState(pos, state, flags & ~Waterlogging.REMOVE_WATER);
            return false;
        }

        @Override
        public boolean placeDry(World world, BlockPos pos, IBlockState state, int flags) {
            return world.setBlockState(pos, state, flags & ~Waterlogging.REMOVE_WATER) || world.getBlockState(pos) == state;
        }

        @Override
        public void setWater(ChunkPrimer primer, int x, int y, int z, boolean water) {
        }
    };
}

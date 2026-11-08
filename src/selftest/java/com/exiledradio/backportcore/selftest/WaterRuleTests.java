package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.waterlog.SourceForming;
import com.exiledradio.backportcore.waterlog.WaterRules;
import com.exiledradio.backportcore.waterlog.Waterlogging;

import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/** CORE_REQUESTS (Nether section 7): Core's waterlogging rules before Fluidlogged API's list, and source forming. */
final class WaterRuleTests {

    private static BlockPos base;

    private WaterRuleTests() {
    }

    private static boolean takes(World world, BlockPos pos, IBlockState state, FluidState fluid) {
        return FluidloggedUtils.isStateFluidloggable(state, world, pos, fluid);
    }

    /** A 3 by 3 glass floor and walls one high, open on top, around a row of five along x. */
    private static void basin(World world, BlockPos start) {
        for (int dx = -1; dx <= 5; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlockState(start.add(dx, -1, dz), Blocks.GLASS.getDefaultState(), 2);
                boolean inside = dz == 0 && dx >= 0 && dx <= 4;
                world.setBlockState(start.add(dx, 0, dz), inside ? Blocks.AIR.getDefaultState() : Blocks.GLASS.getDefaultState(), 2);
            }
        }
    }

    static void schedule() {
        then(1, "water rules", world -> {
            BlockPos spawn = world.getSpawnPoint();
            base = new BlockPos(spawn.getX(), 210, spawn.getZ() + 40);
            BlockPos p = base;
            world.setBlockState(p.down(), Blocks.GLASS.getDefaultState(), 2);
            FluidState source = FluidState.of(FluidRegistry.WATER);
            FluidState lava = FluidState.of(FluidRegistry.LAVA);
            FluidState flowing = FluidState.of(Blocks.FLOWING_WATER.getDefaultState().withProperty(BlockLiquid.LEVEL, 3));
            check("rules: the flowing test state is not a source", !flowing.isEmpty() && !flowing.isSource(), flowing.getLevel());

            IBlockState wet = TestBlocks.waterStairs.getDefaultState();
            world.setBlockState(p, wet, 2);
            check("rules: a Waterloggable stair takes a water source", takes(world, p, wet, source));
            check("rules: a Waterloggable stair refuses lava (Fluidlogged API's list would allow it)", !takes(world, p, wet, lava));
            check("rules: a Waterloggable stair refuses flowing water", !takes(world, p, wet, flowing));
            check("rules: decide() leaves a water source to Fluidlogged API", WaterRules.decide(wet, source) == null);

            IBlockState post = TestBlocks.optional.getDefaultState();
            check("rules: a Waterloggable post refuses lava too", WaterRules.decide(post, lava) == Boolean.FALSE);

            IBlockState dry = TestBlocks.dryStairs.getDefaultState();
            world.setBlockState(p, dry, 2);
            check("rules: the self-test mod counts as family (it requires Backport Core)", WaterRules.isFamily(TestBlocks.dryStairs));
            check("rules: a family stair that is not Waterloggable refuses water", !takes(world, p, dry, source));
            check("rules: ... and lava", !takes(world, p, dry, lava));
            check("rules: the family slab (not Waterloggable) refuses water", WaterRules.decide(TestBlocks.slab.getDefaultState(), source) == Boolean.FALSE);

            IBlockState oak = Blocks.OAK_STAIRS.getDefaultState();
            world.setBlockState(p, oak, 2);
            check("rules: vanilla's stair is left to Fluidlogged API (water allowed)", WaterRules.decide(oak, source) == null && takes(world, p, oak, source));
            check("rules: vanilla is not family", !WaterRules.isFamily(Blocks.OAK_STAIRS));
            world.setBlockToAir(p);
        });

        // Source forming: stair between two sources over glass; one with only one source; one over air.
        then(1, "source forming setup", world -> {
            BlockPos a = base.add(0, 0, 6);
            basin(world, a);
            world.setBlockState(a.add(2, 0, 0), TestBlocks.waterStairs.getDefaultState(), 3);
            world.setBlockState(a.add(1, 0, 0), Blocks.WATER.getDefaultState(), 3);
            world.setBlockState(a.add(3, 0, 0), Blocks.WATER.getDefaultState(), 3);
            // Only one source beside it; the far end is glass so nothing flows.
            BlockPos b = base.add(0, 0, 12);
            basin(world, b);
            world.setBlockState(b.add(1, 0, 0), Blocks.GLASS.getDefaultState(), 2);
            world.setBlockState(b.add(3, 0, 0), Blocks.GLASS.getDefaultState(), 2);
            world.setBlockState(b.add(4, 0, 0), Blocks.GLASS.getDefaultState(), 2);
            world.setBlockState(b.add(0, 0, 0), Blocks.GLASS.getDefaultState(), 2);
            world.setBlockState(b.add(2, 0, 0), TestBlocks.waterStairs.getDefaultState(), 3);
            world.setBlockState(b.add(2, 0, 1), Blocks.WATER.getDefaultState(), 3);
            check("forming: the rule is met between two sources", SourceForming.formsSource(world, a.add(2, 0, 0)));
            check("forming: the rule is not met beside one source", !SourceForming.formsSource(world, b.add(2, 0, 0)));
        });
        then(20, "source forming check", world -> {
            BlockPos a = base.add(0, 0, 6);
            check("forming: a waterloggable stair between two sources takes a source (1.13+)",
                    Waterlogging.hasWaterSource(world, a.add(2, 0, 0)) && world.getBlockState(a.add(2, 0, 0)).getBlock() == TestBlocks.waterStairs,
                    world.getBlockState(a.add(2, 0, 0)));
            BlockPos b = base.add(0, 0, 12);
            check("forming: beside one source it stays dry", !Waterlogging.hasWater(world, b.add(2, 0, 0)), world.getBlockState(b.add(2, 0, 0)));
        });
    }
}

package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.gameevent.GameEvent;
import com.exiledradio.backportcore.gameevent.GameEvents;
import com.exiledradio.backportcore.mechanics.PistonRules;

import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityPiston;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Pistons (mechanics/PistonRules): a registered tile-entity block is pushed and pulled with its state (P84), an
 * unregistered one is refused as 1.12.2 does, and a block a piston destroys posts block_destroy (P85). Runs after the
 * game event tests, which turn game events on.
 */
final class PistonTests {

    private static final BlockPos AT = new BlockPos(4600, 120, 4600);

    private PistonTests() {
    }

    private static IBlockState piston(boolean extended) {
        return Blocks.STICKY_PISTON.getDefaultState().withProperty(BlockPistonBase.FACING, EnumFacing.EAST)
                .withProperty(BlockPistonBase.EXTENDED, extended);
    }

    /** Ends every piston animation at and around {@code from}, as the moving tile entities would. */
    private static void finish(WorldServer world, BlockPos from) {
        for (int i = -1; i <= 3; i++) {
            TileEntity te = world.getTileEntity(from.east(i));
            if (te instanceof TileEntityPiston) {
                ((TileEntityPiston) te).clearPistonTileEntity();
            }
        }
    }

    private static void clear(WorldServer world) {
        for (int x = -2; x <= 5; x++) {
            world.setBlockState(AT.add(x, -1, 0), Blocks.STONE.getDefaultState(), 2);
            world.setBlockState(AT.add(x, 0, 0), Blocks.AIR.getDefaultState(), 2);
        }
    }

    static void schedule() {
        then(1, "pistons: a registered tile-entity block moves (P84)", world -> {
            check("P84 and P85 applied", "applied".equals(Asm.state("P84")) && "applied".equals(Asm.state("P85")),
                    Asm.state("P84") + " " + Asm.state("P85"));
            clear(world);
            IBlockState bin = TestBlocks.bin.getDefaultState().withProperty(TestBlocks.Bin.LEVEL, 2);
            check("canPush: a registered block with a tile entity can move", BlockPistonBase.canPush(bin, world, AT.east(), EnumFacing.EAST,
                    false, EnumFacing.EAST) && PistonRules.allowsTileEntity(TestBlocks.bin));
            check("canPush: an unregistered one cannot (1.12.2's rule)", !BlockPistonBase.canPush(TestBlocks.lockedBin.getDefaultState(), world,
                    AT.east(), EnumFacing.EAST, false, EnumFacing.EAST));
            check("canPush: a chest still cannot", !BlockPistonBase.canPush(Blocks.CHEST.getDefaultState(), world, AT.east(), EnumFacing.EAST,
                    false, EnumFacing.EAST));

            world.setBlockState(AT, piston(false), 2);
            world.setBlockState(AT.east(), bin, 2);
            world.setBlockState(AT.west(), Blocks.REDSTONE_BLOCK.getDefaultState(), 2);
            Blocks.STICKY_PISTON.eventReceived(piston(false), world, AT, 0, EnumFacing.EAST.getIndex());
            finish(world, AT);
            IBlockState pushed = world.getBlockState(AT.east(2));
            check("pushed: the block and its level moved one block", pushed.getBlock() == TestBlocks.bin && pushed.getValue(TestBlocks.Bin.LEVEL) == 2,
                    pushed);
            check("pushed: a new tile entity where it landed", world.getTileEntity(AT.east(2)) instanceof TestBlocks.BinTile,
                    world.getTileEntity(AT.east(2)));

            world.setBlockState(AT.west(), Blocks.AIR.getDefaultState(), 2);
            Blocks.STICKY_PISTON.eventReceived(world.getBlockState(AT), world, AT, 1, EnumFacing.EAST.getIndex());
            finish(world, AT);
            IBlockState pulled = world.getBlockState(AT.east());
            check("pulled back by the sticky piston, with its level", pulled.getBlock() == TestBlocks.bin && pulled.getValue(TestBlocks.Bin.LEVEL) == 2
                    && world.getTileEntity(AT.east()) instanceof TestBlocks.BinTile, pulled);

            clear(world);
            world.setBlockState(AT, piston(false), 2);
            world.setBlockState(AT.east(), TestBlocks.lockedBin.getDefaultState(), 2);
            world.setBlockState(AT.west(), Blocks.REDSTONE_BLOCK.getDefaultState(), 2);
            Blocks.STICKY_PISTON.eventReceived(piston(false), world, AT, 0, EnumFacing.EAST.getIndex());
            finish(world, AT);
            check("an unregistered tile-entity block stops the piston", world.getBlockState(AT.east()).getBlock() == TestBlocks.lockedBin
                    && !world.getBlockState(AT).getValue(BlockPistonBase.EXTENDED), world.getBlockState(AT));
        });

        then(1, "pistons: a destroyed block posts block_destroy (P85)", world -> {
            clear(world);
            GameEventSourceTests.Rec rec = new GameEventSourceTests.Rec(AT);
            GameEvents.add(world, rec);
            world.setBlockState(AT, piston(false), 2);
            world.setBlockState(AT.east(), Blocks.TALLGRASS.getStateFromMeta(1), 2);
            world.setBlockState(AT.west(), Blocks.REDSTONE_BLOCK.getDefaultState(), 2);
            rec.clear();
            Blocks.STICKY_PISTON.eventReceived(piston(false), world, AT, 0, EnumFacing.EAST.getIndex());
            finish(world, AT);
            check("the piston destroyed the grass", world.getBlockState(AT.east()).getBlock() != Blocks.TALLGRASS, world.getBlockState(AT.east()));
            check("block_destroy for the grass a piston broke", rec.state(GameEvent.BLOCK_DESTROY) != null
                    && rec.state(GameEvent.BLOCK_DESTROY).getBlock() == Blocks.TALLGRASS, rec.heard);
            check("1.21.5's breaking effects follow the installed version (client side)",
                    PistonRules.breakEffectsOn() == com.exiledradio.backportcore.presentation.Presentation.installedNewest().isAtLeast("1.21.5"));
            GameEvents.remove(world, rec);
            clear(world);
        });
    }
}

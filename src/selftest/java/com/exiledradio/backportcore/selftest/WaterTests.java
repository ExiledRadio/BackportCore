package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.waterlog.MigrationAccess;
import com.exiledradio.backportcore.waterlog.Waterlogging;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/** BACKPORT_CORE_WATERLOGGING.md section 4, tests 1-11, plus the helpers. */
final class WaterTests {

    private static BlockPos base;
    private static EntityZombie swimmer;

    private WaterTests() {
    }

    private static BlockPos at(int slot) {
        return base.add(slot * 5, 0, 0);
    }

    /** Glass under and around the position, open at the top (and to the east when asked). */
    private static void cell(World world, BlockPos pos, int height, boolean openEast) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlockState(pos.add(dx, -1, dz), Blocks.GLASS.getDefaultState(), 2);
                for (int y = 0; y < height; y++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    boolean east = dx == 1 && dz == 0;
                    world.setBlockState(pos.add(dx, y, dz), east && openEast ? Blocks.AIR.getDefaultState()
                            : Blocks.GLASS.getDefaultState(), 2);
                }
            }
        }
        if (openEast) {
            for (int dx = 1; dx <= 3; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.setBlockState(pos.add(dx, -1, dz), Blocks.GLASS.getDefaultState(), 2);
                }
            }
        }
    }

    private static boolean changed(String block, BlockPos pos, boolean water) {
        return TestBlocks.CHANGES.contains(block + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "=" + water);
    }

    static void schedule() {
        then(1, "water setup", world -> {
            BlockPos spawn = world.getSpawnPoint();
            base = new BlockPos(spawn.getX(), 200, spawn.getZ());
            TestBlocks.CHANGES.clear();
            check("boot: dedicated server started with Fluidlogged API and no client class",
                    net.minecraftforge.fml.common.Loader.isModLoaded("fluidlogged_api"));
        });

        // 1: placed into a water source, it keeps the water; onWaterChanged(fill)
        then(1, "keeps water", world -> {
            BlockPos a = at(0);
            cell(world, a, 1, false);
            world.setBlockState(a, Blocks.WATER.getDefaultState(), 2);
            check("water block is a water source", Waterlogging.hasWaterSource(world, a));
            world.setBlockState(a, TestBlocks.optional.getDefaultState(), 3);
            check("1 placed into a source keeps it", world.getBlockState(a).getBlock() == TestBlocks.optional
                    && Waterlogging.hasWater(world, a) && Waterlogging.hasWaterSource(world, a), world.getBlockState(a));
            check("6 onWaterChanged on fill", changed("optional", a, true), TestBlocks.CHANGES);
        });

        // 2: a bucket drains it and fills it
        then(1, "bucket", world -> {
            BlockPos a = at(0);
            FakePlayer player = FakePlayerFactory.getMinecraft(world);
            player.setLocationAndAngles(a.getX() + 0.5D, a.getY() + 1.0D, a.getZ() + 0.5D, 0.0F, 90.0F);
            player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            ActionResult<ItemStack> drained = Items.BUCKET.onItemRightClick(world, player, EnumHand.MAIN_HAND);
            check("2 bucket drains it", drained.getResult().getItem() == Items.WATER_BUCKET && !Waterlogging.hasWater(world, a)
                    && world.getBlockState(a).getBlock() == TestBlocks.optional, drained.getType() + " " + drained.getResult());
            check("6 onWaterChanged on drain", changed("optional", a, false), TestBlocks.CHANGES);
            player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            ActionResult<ItemStack> filled = Items.WATER_BUCKET.onItemRightClick(world, player, EnumHand.MAIN_HAND);
            check("2 bucket fills it", Waterlogging.hasWaterSource(world, a) && world.getBlockState(a).getBlock() == TestBlocks.optional,
                    filled.getType() + " " + filled.getResult() + " " + world.getBlockState(a));
            check("2 no water spilled above it", world.getBlockState(a.up()).getMaterial() != Material.WATER, world.getBlockState(a.up()));
        });

        // 3: its water flows out into open air beside it, and back in
        then(1, "flow setup", world -> {
            // Live water (flag 1) flows out at once.
            BlockPos b = at(1);
            cell(world, b, 1, true);
            Waterlogging.placeWithWater(world, b, TestBlocks.optional.getDefaultState(), 3);
            check("placeWithWater gives water", Waterlogging.hasWaterSource(world, b));
            // Generated water (flag 2) waits until the wall beside it goes.
            BlockPos b2 = at(10);
            cell(world, b2, 1, true);
            world.setBlockState(b2.east(), Blocks.GLASS.getDefaultState(), 2);
            Waterlogging.placeWithWater(world, b2, TestBlocks.optional.getDefaultState(), 2);
            // Water flowing in from a source beside it.
            BlockPos c = at(2);
            cell(world, c, 1, true);
            Waterlogging.placeDry(world, c, TestBlocks.optional.getDefaultState(), 3);
            check("placeDry gives none", !Waterlogging.hasWater(world, c));
            // A closed basin: the source beside it can only flow into it.
            world.setBlockState(c.east(2), Blocks.GLASS.getDefaultState(), 2);
            world.setBlockState(c.east(), Blocks.FLOWING_WATER.getDefaultState(), 3);
        });
        then(10, "flow open the wall", world -> {
            BlockPos b2 = at(10);
            check("3 generated water stays still behind its wall", world.getBlockState(b2.east()).getBlock() == Blocks.GLASS
                    && Waterlogging.hasWaterSource(world, b2));
            world.setBlockToAir(b2.east());
        });
        then(40, "flow check", world -> {
            BlockPos b = at(1);
            check("3 water flows out of it", world.getBlockState(b.east()).getMaterial() == Material.WATER, world.getBlockState(b.east()));
            BlockPos b2 = at(10);
            check("3 water flows out of it once the wall beside it is broken",
                    world.getBlockState(b2.east()).getMaterial() == Material.WATER, world.getBlockState(b2.east()));
            check("3 it keeps its source while flowing out", Waterlogging.hasWaterSource(world, b2));
            BlockPos c = at(2);
            check("3 water flows back into it", Waterlogging.hasWater(world, c), world.getBlockState(c));
            check("6 onWaterChanged from flow", changed("optional", c, true), TestBlocks.CHANGES);
        });

        // 4: swimming and drowning inside it
        then(1, "swim setup", world -> {
            BlockPos d = at(3);
            cell(world, d, 3, false);
            Waterlogging.placeWithWater(world, d, TestBlocks.optional.getDefaultState(), 3);
            Waterlogging.placeWithWater(world, d.up(), TestBlocks.optional.getDefaultState(), 3);
            Waterlogging.placeWithWater(world, d.up(2), TestBlocks.optional.getDefaultState(), 3);
            swimmer = new EntityZombie(world);
            swimmer.setNoAI(true);
            swimmer.enablePersistence();
            swimmer.setLocationAndAngles(d.getX() + 0.5D, d.getY(), d.getZ() + 0.5D, 0.0F, 0.0F);
            world.spawnEntity(swimmer);
        });
        then(60, "swim check", world -> {
            check("4 a mob inside it is in water", swimmer.isInWater(), swimmer.getPositionVector());
            check("4 its eyes are in water", swimmer.isInsideOfMaterial(Material.WATER));
            check("4 it is losing air (drowning)", swimmer.getAir() < 300, swimmer.getAir());
            swimmer.setDead();
        });

        // 5: a REQUIRED block breaks when drained, nothing left behind
        then(1, "required", world -> {
            BlockPos e = at(4);
            cell(world, e, 1, false);
            Waterlogging.placeWithWater(world, e, TestBlocks.required.getDefaultState(), 3);
            check("5 required block placed with water", world.getBlockState(e).getBlock() == TestBlocks.required
                    && Waterlogging.hasWaterSource(world, e));
            boolean drained = Waterlogging.setWater(world, e, false, 3);
            check("5 drained, it is removed", drained && world.getBlockState(e).getBlock() == Blocks.AIR
                    && !Waterlogging.hasWater(world, e), world.getBlockState(e));
            check("5 nothing dropped", world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(e).grow(3)).isEmpty());
        });

        // 7: canHoldWater false refuses water
        then(1, "dry", world -> {
            BlockPos f = at(5);
            cell(world, f, 1, false);
            world.setBlockState(f, Blocks.WATER.getDefaultState(), 2);
            world.setBlockState(f, TestBlocks.dry.getDefaultState(), 3);
            check("7 placed into water, it does not keep it", !Waterlogging.hasWater(world, f), world.getBlockState(f));
            check("7 setWater refuses", !Waterlogging.setWater(world, f, true, 3) && !Waterlogging.hasWater(world, f));
            check("7 canHoldWater answers false", !Waterlogging.canHoldWater(world.getBlockState(f), world, f));
        });

        // 8: placeKeepingWater in a pool
        then(1, "keeping", world -> {
            BlockPos g = at(6);
            cell(world, g, 1, false);
            world.setBlockState(g, Blocks.WATER.getDefaultState(), 2);
            boolean placed = Waterlogging.placeKeepingWater(world, g, TestBlocks.optional.getDefaultState(), 2);
            check("8 placeKeepingWater keeps the pool's water", placed && world.getBlockState(g).getBlock() == TestBlocks.optional
                    && Waterlogging.hasWaterSource(world, g));
            BlockPos g2 = g.up(5);
            boolean placedDry = Waterlogging.placeKeepingWater(world, g2, TestBlocks.optional.getDefaultState(), 2);
            check("8 placeKeepingWater in air stays dry", placedDry && !Waterlogging.hasWater(world, g2));
            world.setBlockToAir(g2);
            boolean stone = Waterlogging.placeKeepingWater(world, g, Blocks.STONE.getDefaultState(), 2);
            check("8 a block that cannot hold water replaces it", stone && !Waterlogging.hasWater(world, g), world.getBlockState(g));
        });

        // 9: migrateStoredProperty on a state saved with waterlogged=true
        then(1, "migration", world -> {
            BlockPos h = at(7);
            cell(world, h, 1, false);
            world.setBlockState(h, TestBlocks.legacy.getDefaultState().withProperty(TestBlocks.Legacy.WATERLOGGED, Boolean.TRUE), 2);
            check("9 legacy state stored waterlogged=true", world.getBlockState(h).getValue(TestBlocks.Legacy.WATERLOGGED));
            Chunk chunk = world.getChunk(h);
            int converted = MigrationAccess.convert(world, chunk);
            IBlockState now = world.getBlockState(h);
            check("9 converted to Fluidlogged API water", converted >= 1 && !now.getValue(TestBlocks.Legacy.WATERLOGGED)
                    && Waterlogging.hasWaterSource(world, h), converted + " " + now);

            NBTTagCompound saved = new NBTTagCompound();
            saved.setTag("Level", new NBTTagCompound());
            MigrationAccess.save(chunk, saved);
            NBTTagList list = saved.getCompoundTag("Level").getTagList(MigrationAccess.tag(), 8);
            check("9 a saved chunk records what it was checked for", list.tagCount() == 1
                    && "backportcore_selftest:legacy".equals(list.getStringTagAt(0)), list);
            MigrationAccess.load(chunk, saved);
            check("9 a chunk loaded with the record is not checked again", !MigrationAccess.waiting(world, chunk));
            NBTTagCompound old = new NBTTagCompound();
            old.setTag("Level", new NBTTagCompound());
            MigrationAccess.load(chunk, old);
            check("9 a chunk loaded without it is queued", MigrationAccess.waiting(world, chunk));
            world.setBlockState(h.up(2), TestBlocks.legacy.getDefaultState().withProperty(TestBlocks.Legacy.WATERLOGGED, Boolean.TRUE), 2);
        });
        then(3, "migration tick", world -> {
            BlockPos h = at(7);
            Chunk chunk = world.getChunk(h);
            check("9 the queued chunk is converted on the world tick", !MigrationAccess.waiting(world, chunk)
                    && !world.getBlockState(h.up(2)).getValue(TestBlocks.Legacy.WATERLOGGED)
                    && Waterlogging.hasWaterSource(world, h.up(2)), world.getBlockState(h.up(2)));
        });

        // 10: a vanilla slab still fluidlogs by Fluidlogged API's own defaults
        then(1, "slab", world -> {
            BlockPos i = at(8);
            cell(world, i, 1, false);
            world.setBlockState(i, Blocks.WATER.getDefaultState(), 2);
            world.setBlockState(i, Blocks.STONE_SLAB.getDefaultState().withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.BOTTOM), 3);
            check("10 a vanilla slab keeps water", Waterlogging.hasWaterSource(world, i), world.getBlockState(i));
            BlockPos j = i.up(3);
            world.setBlockState(j, Blocks.WATER.getDefaultState(), 2);
            world.setBlockState(j, Blocks.OAK_STAIRS.getDefaultState(), 3);
            // Not a test: whether Core should add vanilla stairs is the user's call (section 3).
            com.exiledradio.backportcore.BackportCore.LOGGER.info("SELFTEST INFO vanilla oak stairs keep water by Fluidlogged API's defaults: {}",
                    Waterlogging.hasWater(world, j));
            world.setBlockToAir(j);
        });

        // Helpers against plain water
        then(1, "helpers", world -> {
            BlockPos k = at(9);
            cell(world, k, 1, false);
            world.setBlockState(k, Blocks.FLOWING_WATER.getDefaultState().withProperty(net.minecraft.block.BlockLiquid.LEVEL, 3), 2);
            check("flowing water is water but not a source", Waterlogging.hasWater(world, k) && !Waterlogging.hasWaterSource(world, k));
            check("setWater on a fluid block is refused", !Waterlogging.setWater(world, k, false, 2));
            world.setBlockToAir(k);
            check("air has no water", !Waterlogging.hasWater(world, k));
            check("setWater(false) where there is no water is a no-op", Waterlogging.setWater(world, k.up(), false, 2));
            for (EnumFacing side : EnumFacing.HORIZONTALS) {
                world.setBlockToAir(k.offset(side));
            }
        });
    }
}

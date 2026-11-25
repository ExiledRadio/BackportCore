package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.structure.ModernBlockScan;
import com.exiledradio.backportcore.structure.ModernPalette;
import com.exiledradio.backportcore.supply.SuppliedBlocks;
import com.exiledradio.backportcore.supply.SuppliedShapes;
import com.exiledradio.backportcore.supply.SuppliedTable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.io.File;
import java.io.PrintWriter;
import java.util.Random;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * Core 1.5.0's supplied blocks (supply/): the table, registration from {@code supplied_blocks.txt}, structures'
 * palette step, numbers and drops, and the two-way rename. With an owner installed ({@code -PwithMods} Caves &amp;
 * Cliffs) its rows are left to it and resolve to its blocks. The world round trip (a world saved with Core's copies,
 * the owner added, then removed) runs on a real server: docs/PORTING_STRUCTURES.md, "Supplied blocks".
 */
final class SuppliedTests {

    private SuppliedTests() {
    }

    /**
     * Before the registry events, for {@code -PselftestOnly=supplied}: every row in the list, as a game that once needed
     * them all would have it. The full suite keeps what its own structure files need.
     */
    static void preInit() {
        if (!System.getProperty("backportcore.selftest.only", "").contains("supplied")) {
            return;
        }
        File f = SuppliedBlocks.listFile();
        f.getParentFile().mkdirs();
        try (PrintWriter w = new PrintWriter(f, "UTF-8")) {
            for (String name : SuppliedTable.rows().keySet()) {
                w.println(name);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void schedule() {
        SelfTestRunner.then(1, "supplied: blocks", SuppliedTests::blocks);
    }

    private static void blocks(WorldServer world) throws Exception {
        check("supplied: the table covers today's decorative blocks", SuppliedTable.rows().size() >= 400, SuppliedTable.rows().size());
        int ours = 0;
        int owners = 0;
        for (SuppliedTable.Row row : SuppliedTable.rows().values()) {
            boolean ownerHas = row.owner != null && Loader.isModLoaded(row.owner)
                    && ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(row.owner, row.ownerName));
            Block mine = SuppliedBlocks.block(row.name);
            ModernPalette.Resolved r = ModernPalette.resolveUpToDate("minecraft:" + row.name, java.util.Collections.<String, String>emptyMap());
            if (ownerHas) {
                owners++;
                check("supplied: " + row.name + " is left to " + row.owner, mine == null && r.state != null
                        && row.owner.equals(r.state.getBlock().getRegistryName().getNamespace()), r.via);
            } else if (!ModernBlockScan.uses().containsKey("minecraft:" + row.name) && !listed(row.name)) {
                check("supplied: " + row.name + " is not registered when nothing uses it", mine == null);
            } else {
                ours++;
                check("supplied: " + row.name + " is Core's", mine != null && r.state != null && r.state.getBlock() == mine
                        && "supplied".equals(r.via), r.via);
                check("supplied: " + row.name + " has an item", "slab".equals(row.shape) || SuppliedBlocks.itemOf(mine) != net.minecraft.init.Items.AIR);
            }
        }
        check("supplied: some rows are Core's or an owner's", ours + owners > 0, ours + "/" + owners);
        shapes(world);
        // The report lists names the structure files use; the suite's own files may not use deepslate.
        String deepslateOutcome = ModernBlockScan.report().get("minecraft:deepslate");
        check("supplied: the report counts them", deepslateOutcome == null || "supplied".equals(deepslateOutcome)
                || SuppliedBlocks.block("deepslate") == null, deepslateOutcome);

        Block deepslate = SuppliedBlocks.block("deepslate");
        if (deepslate != null) {
            IBlockState x = ModernPalette.resolve("minecraft:deepslate[axis=x]").state;
            check("supplied: deepslate axis=x", x != null && x.getBlock() == deepslate && deepslate.getMetaFromState(x) == 4,
                    x == null ? null : deepslate.getMetaFromState(x));
            check("supplied: deepslate's numbers are today's", deepslate.getBlockHardness(deepslate.getDefaultState(), world, BlockPos.ORIGIN) == 3.0F
                    && Math.abs(deepslate.getExplosionResistance(null) - 6.0F) < 0.01F && "pickaxe".equals(deepslate.getHarvestTool(deepslate.getDefaultState())),
                    deepslate.getExplosionResistance(null));
            Block cobbled = SuppliedBlocks.block("cobbled_deepslate");
            check("supplied: deepslate drops cobbled deepslate", deepslate.getItemDropped(deepslate.getDefaultState(), new Random(1), 0)
                    == Item.getItemFromBlock(cobbled));
            check("supplied: Core's deepslate becomes Caves & Cliffs'", new ResourceLocation("cavesandcliffsbackport", "deepslate")
                    .equals(targetOf(new ResourceLocation("backportcore", "deepslate"))));
            check("supplied: Caves & Cliffs' deepslate becomes Core's", new ResourceLocation("backportcore", "deepslate")
                    .equals(targetOf(new ResourceLocation("cavesandcliffsbackport", "deepslate"))));
        }
        Block stairs = SuppliedBlocks.block("deepslate_tile_stairs");
        if (stairs != null) {
            IBlockState s = ModernPalette.resolve("minecraft:deepslate_tile_stairs[facing=east,half=top,shape=straight,waterlogged=false]").state;
            check("supplied: stairs keep facing and half", s != null && s.getBlock() == stairs && s.getValue(BlockStairs.FACING) == EnumFacing.EAST
                    && s.getValue(BlockStairs.HALF) == BlockStairs.EnumHalf.TOP, s);
        }
        Block slab = SuppliedBlocks.block("polished_tuff_slab");
        if (slab != null) {
            IBlockState top = ModernPalette.resolve("minecraft:polished_tuff_slab[type=top]").state;
            IBlockState dbl = ModernPalette.resolve("minecraft:polished_tuff_slab[type=double]").state;
            check("supplied: a top slab", top != null && top.getBlock() == slab && top.getValue(BlockSlab.HALF) == BlockSlab.EnumBlockHalf.TOP
                    && slab.getMetaFromState(top) == 8, top);
            check("supplied: a double slab", dbl != null && dbl.getBlock() == SuppliedBlocks.block("polished_tuff_double_slab"), dbl);
            check("supplied: a double drops two halves", dbl != null && dbl.getBlock().quantityDropped(new Random(1)) == 2
                    && dbl.getBlock().getItemDropped(dbl, new Random(1), 0) == Item.getItemFromBlock(slab));
            check("supplied: the double slab's name follows its owner", new ResourceLocation("trickytrialsbackport", "polished_tuff_double_slab")
                    .equals(targetOf(new ResourceLocation("backportcore", "polished_tuff_double_slab"))));
        }
        Block chain = SuppliedBlocks.block("iron_chain");
        if (chain != null) {
            check("supplied: iron_chain becomes Nether Update's chain", new ResourceLocation("netherupdatebackport", "chain")
                    .equals(targetOf(new ResourceLocation("backportcore", "iron_chain"))));
            check("supplied: a chain is thin", chain instanceof SuppliedShapes.Chain && !chain.getDefaultState().isOpaqueCube());
        }
        check("supplied: other mods' names are left alone", targetOf(new ResourceLocation("somemod", "deepslate")) == null);
        // Placed and read back: Core's copies are real blocks in a world.
        if (deepslate != null) {
            BlockPos at = world.getSpawnPoint().up(40);
            world.setBlockState(at, ModernPalette.resolve("minecraft:deepslate[axis=z]").state, 2);
            check("supplied: placed in the world", world.getBlockState(at).getBlock() == deepslate, world.getBlockState(at));
            world.setBlockToAir(at);
        }
    }

    /** Slice 3's shapes, where Core has them in this game (with -PselftestOnly=supplied, every row). */
    private static void shapes(WorldServer world) {
        Block button = SuppliedBlocks.block("polished_blackstone_button");
        if (button != null) {
            IBlockState floor = ModernPalette.resolve("minecraft:polished_blackstone_button[face=floor,facing=east,powered=false]").state;
            IBlockState wall = ModernPalette.resolve("minecraft:polished_blackstone_button[face=wall,facing=east,powered=false]").state;
            check("supplied: a floor button faces up", floor != null && floor.getBlock() == button && "up".equals(String.valueOf(floor.getProperties().get(net.minecraft.block.BlockDirectional.FACING))), floor);
            check("supplied: a wall button keeps its facing", wall != null && "east".equals(String.valueOf(wall.getProperties().get(net.minecraft.block.BlockDirectional.FACING))), wall);
        }
        Block lantern = SuppliedBlocks.block("soul_lantern");
        if (lantern != null) {
            IBlockState hung = ModernPalette.resolve("minecraft:soul_lantern[hanging=true]").state;
            check("supplied: a hanging lantern is meta 1", hung != null && hung.getBlock() == lantern && lantern.getMetaFromState(hung) == 1, hung);
            check("supplied: a soul lantern gives light", lantern.getDefaultState().getLightValue() == 10, lantern.getDefaultState().getLightValue());
        }
        Block chain = SuppliedBlocks.block("copper_chain");
        if (chain != null) {
            IBlockState z = ModernPalette.resolve("minecraft:copper_chain[axis=z]").state;
            check("supplied: copper chains keep Tricky Trials' layout (z is 2)", z != null && chain.getMetaFromState(z) == 2, z);
        }
        Block leaves = SuppliedBlocks.block("cherry_leaves");
        if (leaves != null) {
            check("supplied: leaves are stored as not decaying", leaves.getMetaFromState(leaves.getDefaultState()) == 4);
        }
        Block door = SuppliedBlocks.block("crimson_door");
        if (door != null) {
            IBlockState upper = ModernPalette.resolve("minecraft:crimson_door[facing=south,half=upper,hinge=right,open=true]").state;
            check("supplied: a door's upper half", upper != null && upper.getBlock() == door
                    && upper.getValue(net.minecraft.block.BlockDoor.HALF) == net.minecraft.block.BlockDoor.EnumDoorHalf.UPPER, upper);
            check("supplied: a door has its door item", SuppliedBlocks.itemOf(door) instanceof net.minecraft.item.ItemDoor);
            check("supplied: the top half drops nothing", door.getItemDropped(upper, new Random(1), 0) == net.minecraft.init.Items.AIR);
        }
        Block wall = SuppliedBlocks.block("polished_blackstone_wall");
        if (wall != null) {
            IBlockState tall = ModernPalette.resolve("minecraft:polished_blackstone_wall[north=tall,up=true,east=none,south=low,west=none]").state;
            check("supplied: a wall keeps its sides", tall != null && tall.getBlock() == wall && tall.getValue(com.exiledradio.backportcore.block.ModernWall.NORTH) == com.exiledradio.backportcore.block.ModernWall.Side.TALL, tall);
        }
        Block flower = SuppliedBlocks.block("cornflower");
        if (flower != null) {
            check("supplied: a flower has no collision", flower.getDefaultState().getCollisionBoundingBox(world, BlockPos.ORIGIN) == null);
            check("supplied: a flower's outline is today's", Math.abs(flower.getDefaultState().getBoundingBox(world, BlockPos.ORIGIN).maxY - 10 / 16.0) < 1e-6);
        }
        Block deepslate = SuppliedBlocks.block("deepslate_bricks");
        if (deepslate != null) {
            net.minecraft.block.SoundType sound = deepslate.getSoundType();
            check("supplied: deepslate bricks sound like today's", sound.getBreakSound().getRegistryName().equals(new ResourceLocation("backportcore", "block.deepslate_bricks.break"))
                    && ForgeRegistries.SOUND_EVENTS.containsKey(sound.getBreakSound().getRegistryName()), sound.getBreakSound().getRegistryName());
        }
        Block planks = SuppliedBlocks.block("pale_oak_planks");
        if (planks != null) {
            check("supplied: a type 1.12.2 has stays 1.12.2's", planks.getSoundType() == net.minecraft.block.SoundType.WOOD);
        }
    }

    private static boolean listed(String name) throws Exception {
        File f = SuppliedBlocks.listFile();
        return f.isFile() && java.nio.file.Files.readAllLines(f.toPath()).contains(name);
    }

    private static ResourceLocation targetOf(ResourceLocation missing) throws Exception {
        java.lang.reflect.Method m = SuppliedBlocks.class.getDeclaredMethod("target", ResourceLocation.class);
        m.setAccessible(true);
        return (ResourceLocation) m.invoke(null, missing);
    }
}

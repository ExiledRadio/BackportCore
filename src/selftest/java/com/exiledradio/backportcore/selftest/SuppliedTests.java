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
        check("supplied: the table has the first 30", SuppliedTable.rows().size() == 30, SuppliedTable.rows().size());
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
                check("supplied: " + row.name + " has an item", "slab".equals(row.shape) || Item.getItemFromBlock(mine) != net.minecraft.init.Items.AIR);
            }
        }
        check("supplied: some rows are Core's or an owner's", ours + owners > 0, ours + "/" + owners);
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

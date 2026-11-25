package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.structure.ModernPalette;
import com.exiledradio.backportcore.structure.ModernTemplate;
import com.exiledradio.backportcore.structure.ScatteredPlacement;
import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.structure.Structures;
import com.exiledradio.backportcore.structure.TemplateBlock;
import com.exiledradio.backportcore.structure.TemplatePlacement;
import com.exiledradio.backportcore.structure.TemplateProcessors;
import com.exiledradio.backportcore.terrain.TerrainHeights;
import com.exiledradio.backportcore.util.BlockStates;
import com.exiledradio.backportcore.waterlog.Waterlogging;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.tileentity.TileEntityBed;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityFlowerPot;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.Mirror;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/** The template loader, palette, placement, locator and the terrain floor. */
final class StructureTests {

    private static final String[] SHIPWRECKS = {"rightsideup_backhalf", "rightsideup_backhalf_degraded", "rightsideup_fronthalf",
            "rightsideup_fronthalf_degraded", "rightsideup_full", "rightsideup_full_degraded", "sideways_backhalf",
            "sideways_backhalf_degraded", "sideways_fronthalf", "sideways_fronthalf_degraded", "sideways_full",
            "sideways_full_degraded", "upsidedown_backhalf", "upsidedown_backhalf_degraded", "upsidedown_fronthalf",
            "upsidedown_fronthalf_degraded", "upsidedown_full", "upsidedown_full_degraded", "with_mast", "with_mast_degraded"};
    private static final String[] RUINS = {"big_brick_1", "big_brick_2", "big_brick_3", "big_brick_8", "big_cracked_1",
            "big_cracked_2", "big_cracked_3", "big_cracked_8", "big_mossy_1", "big_mossy_2", "big_mossy_3", "big_mossy_8",
            "big_warm_4", "big_warm_5", "big_warm_6", "big_warm_7", "brick_1", "brick_2", "brick_3", "brick_4", "brick_5",
            "brick_6", "brick_7", "brick_8", "cracked_1", "cracked_2", "cracked_3", "cracked_4", "cracked_5", "cracked_6",
            "cracked_7", "cracked_8", "mossy_1", "mossy_2", "mossy_3", "mossy_4", "mossy_5", "mossy_6", "mossy_7", "mossy_8",
            "warm_1", "warm_2", "warm_3", "warm_4", "warm_5", "warm_6", "warm_7", "warm_8"};

    private static BlockPos site;

    private StructureTests() {
    }

    static void init() {
        ModernPalette.map("backportcore_selftest:mapped", props -> Blocks.GOLD_BLOCK.getDefaultState());
        ModernPalette.fallback("minecraft:polished_tuff_wall", "minecraft:stone_bricks");
        ModernPalette.lootTable("minecraft:chests/shipwreck_supply", new ResourceLocation(SelfTestMod.MOD_ID, "chests/test"));
        MapGenStructureIO.registerStructure(TestStructure.Start.class, "BCST_Test");
        MapGenStructureIO.registerStructureComponent(TestStructure.Piece.class, "BCST_TestPiece");
        ScatteredStructure.register(TestStructure.KIND, world -> world.provider.getDimension() == 0 ? new TestStructure() : null);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(SelfTestMod.MOD_ID, path);
    }

    private static Map<String, String> props(String... kv) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out.put(kv[i], kv[i + 1]);
        }
        return out;
    }

    private static boolean is(ModernPalette.Resolved r, Block block, String... kv) {
        if (r.state == null || r.state.getBlock() != block) {
            return false;
        }
        Map<String, String> have = BlockStates.props(r.state);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (!kv[i + 1].equals(have.get(kv[i]))) {
                return false;
            }
        }
        return true;
    }

    private static String show(ModernPalette.Resolved r) {
        return r.state + (r.tile == null ? "" : " " + r.tile) + (r.water ? " wet" : "");
    }

    private static void checkState(String name, String modern, int dv, Block block, String... kv) {
        Map<String, String> p = new LinkedHashMap<String, String>();
        String n = modern;
        int open = modern.indexOf('[');
        if (open > 0) {
            n = modern.substring(0, open);
            for (String pair : modern.substring(open + 1, modern.length() - 1).split(",")) {
                String[] two = pair.split("=");
                p.put(two[0], two[1]);
            }
        }
        ModernPalette.Resolved r = ModernPalette.resolve(n, p, dv);
        check("palette " + name, is(r, block, kv), modern + " -> " + show(r));
    }

    static void schedule() {
        then(1, "palette", world -> {
            checkState("plain block", "minecraft:stone_bricks", 5023, Blocks.STONEBRICK, "variant", "stonebrick");
            checkState("variant", "minecraft:chiseled_stone_bricks", 5023, Blocks.STONEBRICK, "variant", "chiseled_stonebrick");
            checkState("renamed variant", "minecraft:cut_sandstone", 5023, Blocks.SANDSTONE, "type", "smooth_sandstone");
            checkState("stairs with a shape", "minecraft:oak_stairs[facing=west,half=top,shape=outer_right,waterlogged=true]", 5023,
                    Blocks.OAK_STAIRS, "facing", "west", "half", "top", "shape", "outer_right");
            checkState("wood stairs", "minecraft:spruce_stairs[facing=north,half=bottom,shape=straight]", 1976,
                    Blocks.SPRUCE_STAIRS, "facing", "north", "half", "bottom");
            checkState("today's smooth stone slab", "minecraft:smooth_stone_slab[type=top]", 5023, Blocks.STONE_SLAB,
                    "variant", "stone", "half", "top");
            checkState("1.13's stone slab is smooth stone", "minecraft:stone_slab[type=bottom]", 1631, Blocks.STONE_SLAB,
                    "variant", "stone", "half", "bottom");
            checkState("double slab", "minecraft:oak_slab[type=double]", 5023, Blocks.DOUBLE_WOODEN_SLAB, "variant", "oak");
            checkState("floor lever", "minecraft:lever[face=floor,facing=east,powered=false]", 5023, Blocks.LEVER, "facing", "up_x");
            checkState("wall lever", "minecraft:lever[face=wall,facing=east,powered=false]", 5023, Blocks.LEVER, "facing", "east");
            checkState("door", "minecraft:spruce_door[facing=east,half=upper,hinge=left,open=false,powered=false]", 5023,
                    Blocks.SPRUCE_DOOR, "half", "upper", "hinge", "left");
            checkState("log axis", "minecraft:dark_oak_log[axis=x]", 5023, Blocks.LOG2, "variant", "dark_oak", "axis", "x");
            checkState("bark", "minecraft:oak_wood[axis=y]", 5023, Blocks.LOG, "variant", "oak", "axis", "none");
            checkState("dirt path", "minecraft:dirt_path", 5023, Blocks.GRASS_PATH);
            checkState("1.13 grass path", "minecraft:grass_path", 1631, Blocks.GRASS_PATH);
            checkState("short grass", "minecraft:short_grass", 5023, Blocks.TALLGRASS, "type", "tall_grass");
            checkState("1.13 grass", "minecraft:grass", 1631, Blocks.TALLGRASS, "type", "tall_grass");
            checkState("water cauldron", "minecraft:water_cauldron[level=2]", 5023, Blocks.CAULDRON, "level", "2");
            checkState("wall of today", "minecraft:cobblestone_wall[east=low,north=tall,south=none,up=true,west=none,waterlogged=false]",
                    5023, Blocks.COBBLESTONE_WALL, "variant", "cobblestone");
            checkState("spawner", "minecraft:spawner", 5023, Blocks.MOB_SPAWNER);
            checkState("nether portal", "minecraft:nether_portal[axis=z]", 5023, Blocks.PORTAL, "axis", "z");
            checkState("melon", "minecraft:melon", 5023, Blocks.MELON_BLOCK);
            checkState("terracotta", "minecraft:light_blue_terracotta", 5023, Blocks.STAINED_HARDENED_CLAY, "color", "light_blue");
            checkState("glazed terracotta", "minecraft:purple_glazed_terracotta[facing=south]", 5023,
                    Blocks.PURPLE_GLAZED_TERRACOTTA, "facing", "south");
            checkState("chest", "minecraft:chest[facing=west,type=single,waterlogged=false]", 5023, Blocks.CHEST, "facing", "west");
            checkState("fallback trapdoor", "minecraft:spruce_trapdoor[facing=north,half=top,open=true]", 5023, Blocks.TRAPDOOR,
                    "half", "top", "open", "true");
            checkState("fallback stripped log", "minecraft:stripped_birch_log[axis=z]", 5023, Blocks.LOG, "variant", "birch", "axis", "z");
            checkState("registered fallback", "minecraft:polished_tuff_wall", 5023, Blocks.STONEBRICK);
            checkState("registered mapping", "backportcore_selftest:mapped", 5023, Blocks.GOLD_BLOCK);

            ModernPalette.Resolved bed = ModernPalette.resolve("minecraft:lime_bed", props("facing", "east", "part", "head"), 5023);
            check("palette bed color in the tile", is(bed, Blocks.BED, "facing", "east", "part", "head")
                    && bed.tile != null && bed.tile.getInteger("color") == 5, show(bed));
            ModernPalette.Resolved banner = ModernPalette.resolve("minecraft:magenta_wall_banner", props("facing", "south"), 5023);
            check("palette wall banner", is(banner, Blocks.WALL_BANNER, "facing", "south") && banner.tile != null
                    && banner.tile.getInteger("Base") == 13, show(banner));
            ModernPalette.Resolved pot = ModernPalette.resolve("minecraft:potted_azure_bluet", props(), 5023);
            check("palette potted flower", is(pot, Blocks.FLOWER_POT) && pot.tile != null
                    && "minecraft:red_flower".equals(pot.tile.getString("Item")) && pot.tile.getInteger("Data") == 3, show(pot));
            ModernPalette.Resolved skull = ModernPalette.resolve("minecraft:wither_skeleton_skull", props("rotation", "4"), 5023);
            check("palette skull", is(skull, Blocks.SKULL, "facing", "up") && skull.tile != null
                    && skull.tile.getByte("SkullType") == 1 && skull.tile.getByte("Rot") == 4, show(skull));
            ModernPalette.Resolved wallHead = ModernPalette.resolve("minecraft:dragon_wall_head", props("facing", "west"), 5023);
            check("palette wall head", is(wallHead, Blocks.SKULL, "facing", "west") && wallHead.tile.getByte("SkullType") == 5, show(wallHead));
            ModernPalette.Resolved kelp = ModernPalette.resolve("minecraft:kelp", props("age", "3"), 5023);
            check("palette kelp without its mod is water, and wet", is(kelp, Blocks.WATER) && kelp.water, show(kelp));
            ModernPalette.Resolved wet = ModernPalette.resolve("minecraft:oak_slab", props("type", "bottom", "waterlogged", "true"), 5023);
            check("palette waterlogged=true is wet", wet.water && is(wet, Blocks.WOODEN_SLAB, "half", "bottom"), show(wet));
            ModernPalette.Resolved missing = ModernPalette.resolve("minecraft:gilded_blackstone", props(), 5023);
            check("palette a block nothing has is null", missing.state == null, show(missing));
            check("palette loot table", "backportcore_selftest:chests/test".equals(ModernPalette.lootTable("minecraft:chests/shipwreck_supply"))
                    && "minecraft:chests/simple_dungeon".equals(ModernPalette.lootTable("minecraft:chests/simple_dungeon")));
            ModernPalette.Resolved text = ModernPalette.resolve("minecraft:oak_stairs[facing=south,half=top]");
            check("palette state text", is(text, Blocks.OAK_STAIRS, "facing", "south", "half", "top", "shape", "straight"), show(text));
        });

        then(1, "ocean templates", world -> {
            for (String version : new String[] {"v26", "v1144"}) {
                int files = 0;
                int blocks = 0;
                List<String> missing = new ArrayList<String>();
                for (String folder : new String[] {"shipwreck", "underwater_ruin"}) {
                    for (String name : folder.equals("shipwreck") ? SHIPWRECKS : RUINS) {
                        ModernTemplate t = ModernTemplate.load(id(version + "/" + folder + "/" + name));
                        if (t.isEmpty()) {
                            missing.add(name + " (empty)");
                            continue;
                        }
                        files++;
                        Set<String> unresolved = t.unresolvedBlocks();
                        if (!unresolved.isEmpty()) {
                            missing.add(name + " " + unresolved);
                        }
                        blocks += t.size().getX() * t.size().getY() * t.size().getZ();
                    }
                }
                check("templates " + version + ": every ocean structure file loads and every block resolves",
                        files == SHIPWRECKS.length + RUINS.length && missing.isEmpty(), files + " files, " + missing);
                BackportCore.LOGGER.info("SELFTEST INFO {} ocean templates: {} files, {} block volume", version, files, blocks);
            }
            ModernTemplate a = ModernTemplate.load(id("v26/shipwreck/rightsideup_full"));
            ModernTemplate b = ModernTemplate.load(id("v1144/shipwreck/rightsideup_full"));
            check("templates multi-palette file", a.palettes() == 8 && b.palettes() == a.palettes(), a.palettes() + " / " + b.palettes());
            check("templates data version read", a.dataVersion() == 5023 && b.dataVersion() == 1976, a.dataVersion() + " / " + b.dataVersion());
            check("templates data markers", a.dataMarkers().size() == 3, markerNames(a));
            int same = 0;
            int compared = 0;
            for (int p = 0; p < a.palettes(); p++) {
                for (int x = 0; x < a.size().getX(); x++) {
                    for (int y = 0; y < a.size().getY(); y++) {
                        for (int z = 0; z < a.size().getZ(); z++) {
                            TemplateBlock ba = a.blockAt(new BlockPos(x, y, z), p);
                            TemplateBlock bb = b.blockAt(new BlockPos(x, y, z), p);
                            if (ba == null && bb == null) {
                                continue;
                            }
                            compared++;
                            if (ba != null && bb != null && ba.state() == bb.state()) {
                                same++;
                            }
                        }
                    }
                }
            }
            check("templates 1.14 and 26.3 shipwreck give the same 1.12.2 blocks", compared > 0 && same == compared, same + "/" + compared);

            ModernTemplate bastion = ModernTemplate.load(id("v26/bastion/entrance"));
            check("templates jigsaws read", bastion.jigsaws().size() > 5 && !bastion.jigsaws().get(0).pool.isEmpty()
                    && !bastion.jigsaws().get(0).finalState.isEmpty(), bastion.jigsaws().size() + " first pool " + bastion.jigsaws().get(0).pool
                    + " front " + bastion.jigsaws().get(0).front + " top " + bastion.jigsaws().get(0).top);
            check("templates unresolved blocks are reported", bastion.unresolvedBlocks().contains("minecraft:cracked_polished_blackstone_bricks"),
                    bastion.unresolvedBlocks());
            ModernTemplate portal = ModernTemplate.load(id("v26/ruined_portal/portal_1"));
            BackportCore.LOGGER.info("SELFTEST INFO ruined portal_1 needs: {}", portal.unresolvedBlocks());
            check("templates a missing file is empty, not a crash", ModernTemplate.load(id("nope/nothing")).isEmpty());
        });

        then(1, "place shipwreck", world -> {
            BlockPos spawn = world.getSpawnPoint();
            site = new BlockPos(spawn.getX() + 64, 150, spawn.getZ());
            ModernTemplate t = ModernTemplate.load(id("v26/shipwreck/rightsideup_full"));
            TemplatePlacement p = TemplatePlacement.of(Rotation.CLOCKWISE_90, Mirror.NONE).pivot(new BlockPos(4, 0, 15)).palette(2);
            StructureBoundingBox box = t.boundingBox(site, p);
            check("place bounding box turned", box.getXSize() == t.size().getZ() && box.getZSize() == t.size().getX()
                    && box.getYSize() == t.size().getY(), box);
            // Fill the box with sea, as a sunken wreck has around it.
            for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(box.minX, box.minY, box.minZ), new BlockPos(box.maxX, box.maxY, box.maxZ))) {
                world.setBlockState(pos, Blocks.WATER.getDefaultState(), 2);
            }
            int placed = t.place(world, site, p);
            check("place placed blocks", placed > 400, placed);
            int checked = 0;
            int matched = 0;
            int wetHolders = 0;
            int holders = 0;
            for (int x = 0; x < t.size().getX(); x++) {
                for (int y = 0; y < t.size().getY(); y++) {
                    for (int z = 0; z < t.size().getZ(); z++) {
                        BlockPos local = new BlockPos(x, y, z);
                        TemplateBlock tb = t.blockAt(local, 2);
                        if (tb == null || tb.state() == null || "minecraft:air".equals(tb.name())) {
                            continue;
                        }
                        BlockPos pos = ModernTemplate.worldPos(site, local, p);
                        IBlockState expected = tb.state().withMirror(Mirror.NONE).withRotation(Rotation.CLOCKWISE_90);
                        IBlockState actual = world.getBlockState(pos);
                        checked++;
                        if (actual.getBlock() == expected.getBlock()
                                && actual.getBlock().getMetaFromState(actual) == expected.getBlock().getMetaFromState(expected)) {
                            matched++;
                        }
                        if (Waterlogging.canHoldWater(actual, world, pos)) {
                            holders++;
                            if (Waterlogging.hasWater(world, pos)) {
                                wetHolders++;
                            }
                        }
                    }
                }
            }
            check("place every block where today's transform puts it, turned", checked > 400 && matched == checked, matched + "/" + checked);
            check("place keeps the sea in blocks that can hold it", holders > 0 && wetHolders == holders, wetHolders + "/" + holders);
            List<String> chests = new ArrayList<String>();
            int chestCount = 0;
            for (ModernTemplate.DataMarker m : t.dataMarkers()) {
                TileEntity te = world.getTileEntity(ModernTemplate.worldPos(site, m.pos, p).down());
                chests.add(m.metadata + "=" + (te == null ? "none" : te.getClass().getSimpleName()));
                if (te instanceof TileEntityChest) {
                    chestCount++;
                }
            }
            check("place chests under the markers", chestCount == t.dataMarkers().size(), chests);
            check("place no structure block placed", countBlock(world, box, Blocks.STRUCTURE_BLOCK) == 0);
            check("place no item dropped", world.getEntitiesWithinAABB(EntityItem.class, aabb(box).grow(2)).isEmpty());
        });

        then(1, "clip and integrity", world -> {
            ModernTemplate t = ModernTemplate.load(id("v26/underwater_ruin/big_brick_1"));
            BlockPos origin = site.add(0, 0, 48);
            ChunkPos chunk = new ChunkPos(origin.add(3, 0, 3));
            StructureBoundingBox clip = new StructureBoundingBox(chunk.getXStart(), 0, chunk.getZStart(), chunk.getXEnd(), 255, chunk.getZEnd());
            TemplatePlacement p = new TemplatePlacement().clip(clip);
            t.place(world, origin, p);
            StructureBoundingBox box = t.boundingBox(origin, new TemplatePlacement());
            int inside = 0;
            int outside = 0;
            for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(box.minX, box.minY, box.minZ), new BlockPos(box.maxX, box.maxY, box.maxZ))) {
                if (world.getBlockState(pos).getMaterial() != Material.AIR) {
                    if (clip.isVecInside(pos)) {
                        inside++;
                    } else {
                        outside++;
                    }
                }
            }
            check("clip places only inside the clip", inside > 0 && outside == 0, inside + " in, " + outside + " out");

            // Integrity: the same structure placed in two halves, chunk-style, and whole comes out the same.
            BlockPos o1 = site.add(0, 0, 80);
            TemplatePlacement whole = new TemplatePlacement().integrity(0.6F).seed(99L);
            int split = o1.getX() + 8;
            t.place(world, o1, whole.copy().clip(new StructureBoundingBox(o1.getX() - 1, 0, o1.getZ() - 1, split, 255, o1.getZ() + 40)));
            t.place(world, o1, whole.copy().clip(new StructureBoundingBox(split + 1, 0, o1.getZ() - 1, o1.getX() + 40, 255, o1.getZ() + 40)));
            Map<BlockPos, IBlockState> halves = new HashMap<BlockPos, IBlockState>();
            int kept = 0;
            int solid = 0;
            for (int x = 0; x < t.size().getX(); x++) {
                for (int y = 0; y < t.size().getY(); y++) {
                    for (int z = 0; z < t.size().getZ(); z++) {
                        BlockPos local = new BlockPos(x, y, z);
                        IBlockState s1 = world.getBlockState(o1.add(local));
                        halves.put(local, s1);
                        if (s1.getMaterial() != Material.AIR) {
                            kept++;
                        }
                        TemplateBlock tb = t.blockAt(local, 0);
                        if (tb != null && tb.state() != null && tb.state().getMaterial() != Material.AIR) {
                            solid++;
                        }
                        world.setBlockState(o1.add(local), Blocks.AIR.getDefaultState(), 2);
                    }
                }
            }
            int placedWhole = t.place(world, o1, whole.copy());
            int differ = 0;
            for (Map.Entry<BlockPos, IBlockState> e : halves.entrySet()) {
                if (world.getBlockState(o1.add(e.getKey())) != e.getValue()) {
                    differ++;
                }
            }
            check("integrity drops some blocks, keeps most", kept > solid / 3 && kept < solid, kept + " of " + solid);
            check("integrity is the same placed in halves or whole", placedWhole > 0 && differ == 0, differ + " differ");
        });

        then(1, "synthetic template", world -> {
            BlockPos origin = site.add(40, 0, 0);
            ModernTemplate t = ModernTemplate.read(id("synthetic"), synthetic());
            int placed = t.place(world, origin, new TemplatePlacement());
            check("synthetic placed", placed >= 6, placed);
            TileEntity bed = world.getTileEntity(origin);
            check("synthetic bed color", bed instanceof TileEntityBed && ((TileEntityBed) bed).getColor().getMetadata() == 5,
                    bed == null ? "none" : ((TileEntityBed) bed).getColor());
            TileEntity pot = world.getTileEntity(origin.add(2, 0, 0));
            check("synthetic flower pot", pot instanceof TileEntityFlowerPot && ((TileEntityFlowerPot) pot).getFlowerPotItem() != null
                    && "minecraft:red_flower".equals(((TileEntityFlowerPot) pot).getFlowerPotItem().getRegistryName().toString())
                    && ((TileEntityFlowerPot) pot).getFlowerPotData() == 0, pot);
            TileEntity banner = world.getTileEntity(origin.add(3, 0, 0));
            // getColorList is client-only: read the saved color (dye damage; white is 15).
            int base = banner == null ? -1 : banner.writeToNBT(new NBTTagCompound()).getInteger("Base");
            check("synthetic banner color", banner instanceof TileEntityBanner && base == 15, base);
            TileEntity chest = world.getTileEntity(origin.add(4, 0, 0));
            check("synthetic chest loot table renamed", chest instanceof TileEntityChest && ((TileEntityChest) chest).getLootTable() != null
                    && "backportcore_selftest:chests/test".equals(((TileEntityChest) chest).getLootTable().toString()),
                    chest == null ? "none" : ((TileEntityChest) chest).getLootTable());
            TileEntity skull = world.getTileEntity(origin.add(5, 0, 0));
            check("synthetic skull", skull instanceof TileEntitySkull && ((TileEntitySkull) skull).getSkullType() == 2, skull);
            check("synthetic jigsaw became its final state", world.getBlockState(origin.add(6, 0, 0)).getBlock() == Blocks.STONEBRICK,
                    world.getBlockState(origin.add(6, 0, 0)));
            check("synthetic jigsaw read", t.jigsaws().size() == 1 && "minecraft:test_pool".equals(t.jigsaws().get(0).pool)
                    && t.jigsaws().get(0).front == net.minecraft.util.EnumFacing.UP, t.jigsaws());
            List<EntityArmorStand> stands = world.getEntitiesWithinAABB(EntityArmorStand.class, new AxisAlignedBB(origin).grow(4));
            check("synthetic entity spawned where the file puts it", stands.size() == 1
                    && Math.abs(stands.get(0).posX - (origin.getX() + 1.5D)) < 0.01 && Math.abs(stands.get(0).posZ - (origin.getZ() + 2.5D)) < 0.01,
                    stands.isEmpty() ? "none" : stands.get(0).getPositionVector());
            check("synthetic waterlogged=true gets water", Waterlogging.hasWaterSource(world, origin.add(7, 0, 0))
                    && world.getBlockState(origin.add(7, 0, 0)).getBlock() == Blocks.WOODEN_SLAB, world.getBlockState(origin.add(7, 0, 0)));

            // Rotated: the entity turns with it.
            BlockPos origin2 = site.add(40, 0, 20);
            t.place(world, origin2, TemplatePlacement.of(Rotation.CLOCKWISE_180, Mirror.NONE));
            List<EntityArmorStand> turned = world.getEntitiesWithinAABB(EntityArmorStand.class, new AxisAlignedBB(origin2).grow(6));
            check("synthetic entity turned", turned.size() == 1 && Math.abs(turned.get(0).posX - (origin2.getX() - 0.5D)) < 0.01
                    && Math.abs(turned.get(0).posZ - (origin2.getZ() - 1.5D)) < 0.01, turned.isEmpty() ? "none" : turned.get(0).getPositionVector());

            // A wall torch whose wall is in the chunk next door, placed one chunk at a time.
            ChunkPos c = new ChunkPos(site.add(0, 0, 200));
            BlockPos torchOrigin = new BlockPos(c.getXStart() - 1, 150, c.getZStart() + 4);
            ModernTemplate torch = ModernTemplate.read(id("torch"), torchTemplate());
            StructureBoundingBox torchChunk = new StructureBoundingBox(c.getXStart(), 0, c.getZStart(), c.getXEnd(), 255, c.getZEnd());
            torch.place(world, torchOrigin, new TemplatePlacement().clip(torchChunk));
            check("torch placed before its wall stays", world.getBlockState(torchOrigin.east()).getBlock() == Blocks.TORCH
                    && world.getBlockState(torchOrigin).getMaterial() == Material.AIR, world.getBlockState(torchOrigin.east()));
            check("torch no item dropped", world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(torchOrigin).grow(4)).isEmpty());
            StructureBoundingBox wallChunk = new StructureBoundingBox(c.getXStart() - 16, 0, c.getZStart(), c.getXStart() - 1, 255, c.getZEnd());
            torch.place(world, torchOrigin, new TemplatePlacement().clip(wallChunk));
            check("torch then the wall comes in", world.getBlockState(torchOrigin).getBlock() == Blocks.STONEBRICK
                    && world.getBlockState(torchOrigin.east()).getBlock() == Blocks.TORCH);

            // Processors: rule replacement and ignore.
            BlockPos origin3 = site.add(40, 20, 40);
            ModernTemplate ruin = ModernTemplate.load(id("v26/underwater_ruin/brick_1"));
            ruin.place(world, origin3, new TemplatePlacement().ignoreAir(true)
                    .processor(TemplateProcessors.replace("minecraft:stone_bricks", 1.0F, "minecraft:mossy_stone_bricks"))
                    .processor(TemplateProcessors.ignore("minecraft:gravel")));
            StructureBoundingBox rb = ruin.boundingBox(origin3, new TemplatePlacement());
            int plain = 0;
            int mossy = 0;
            int gravel = 0;
            for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(rb.minX, rb.minY, rb.minZ), new BlockPos(rb.maxX, rb.maxY, rb.maxZ))) {
                IBlockState s = world.getBlockState(pos);
                if (s.getBlock() == Blocks.STONEBRICK) {
                    String v = BlockStates.props(s).get("variant");
                    if ("stonebrick".equals(v)) {
                        plain++;
                    } else if ("mossy_stonebrick".equals(v)) {
                        mossy++;
                    }
                }
                if (s.getBlock() == Blocks.GRAVEL) {
                    gravel++;
                }
            }
            check("processors replace and ignore", plain == 0 && mossy > 0 && gravel == 0, plain + " plain, " + mossy + " mossy, " + gravel + " gravel");
        });

        then(1, "piece", world -> {
            Random random = new Random(5L);
            TestStructure.Piece piece = new TestStructure.Piece(site.add(80, 0, 0), Rotation.COUNTERCLOCKWISE_90, random);
            NBTTagCompound tag = piece.createStructureBaseNBT();
            StructureComponent back = MapGenStructureIO.getStructureComponent(tag, world);
            check("piece saves and loads", back instanceof TestStructure.Piece
                    && ((TestStructure.Piece) back).origin().equals(piece.origin())
                    && back.getBoundingBox().toString().equals(piece.getBoundingBox().toString()), back == null ? "null" : back.getBoundingBox());
            StructureBoundingBox chunk = piece.getBoundingBox();
            back.addComponentParts(world, random, chunk);
            check("piece builds", countBlock(world, chunk, Blocks.STONEBRICK) > 0, countBlock(world, chunk, Blocks.STONEBRICK));
        });

        then(1, "scattered placement", world -> {
            ScatteredPlacement p = new ScatteredPlacement(24, 4, 165745295);
            ChunkPos c = p.candidate(world.getSeed(), 100, -37);
            boolean inRegion = Math.floorDiv(c.x, 24) == Math.floorDiv(100, 24) && Math.floorDiv(c.z, 24) == Math.floorDiv(-37, 24)
                    && Math.floorMod(c.x, 24) < 20 && Math.floorMod(c.z, 24) < 20;
            check("scattered candidate inside its region, clear of the next", inRegion && p.isCandidate(world.getSeed(), c.x, c.z)
                    && !p.isCandidate(world.getSeed(), c.x + 1, c.z), c);
            ChunkPos near = p.nearest(world.getSeed(), new BlockPos(0, 64, 0), 64, x -> true);
            check("scattered nearest is a candidate within the radius", near != null && p.isCandidate(world.getSeed(), near.x, near.z)
                    && near.x * near.x + near.z * near.z <= 64 * 64, near);
            ChunkPos none = p.nearest(world.getSeed(), new BlockPos(0, 64, 0), 64, x -> false);
            check("scattered nearest with nothing accepted is null", none == null);
        });

        then(1, "scattered structure", world -> {
            BlockPos spawn = world.getSpawnPoint();
            BlockPos found = Structures.nearest(world, TestStructure.KIND, spawn, 32);
            check("locator finds the registered kind", found != null && Structures.has(TestStructure.KIND), found);
            check("locator none for an unknown kind", Structures.nearest(world, "nothing_here", spawn, 32) == null);
            check("locator nearestOf", Structures.nearestOf(world, spawn, 32, "nothing_here", TestStructure.KIND) != null);
            // Generate around the start the locator found, then look for the ruin.
            if (found != null) {
                int cx = found.getX() >> 4;
                int cz = found.getZ() >> 4;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        world.getChunk(cx + dx, cz + dz);
                    }
                }
                StructureStart start = null;
                for (StructureStart s : TestStructure.LAID_OUT) {
                    if (s.getChunkPosX() == cx && s.getChunkPosZ() == cz) {
                        start = s;
                    }
                }
                check("scattered laid out from the populate step", start != null, TestStructure.LAID_OUT.size() + " laid out");
                if (start != null) {
                    StructureBoundingBox box = start.getBoundingBox();
                    int bricks = countBlock(world, box, Blocks.STONEBRICK);
                    check("scattered built in the world", bricks > 0, bricks + " stone bricks in " + box);
                    check("scattered isInside", ScatteredStructure.isInside(world, TestStructure.KIND,
                            new BlockPos((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2)));
                    TerrainHeights heights = TerrainHeights.forWorld(world);
                    int floor = heights == null ? -1 : heights.floor(box.minX, box.minZ);
                    BackportCore.LOGGER.info("SELFTEST INFO test ruin at {} on floor {}", box, floor);
                }
            }
        });

        then(1, "terrain floor", world -> {
            BlockPos spawn = world.getSpawnPoint();
            List<Biome> oceans = Arrays.asList(Biomes.OCEAN, Biomes.DEEP_OCEAN);
            BlockPos ocean = world.getBiomeProvider().findBiomePosition(spawn.getX(), spawn.getZ(), 3000, oceans, new Random(1L));
            check("terrain an ocean exists near spawn", ocean != null, ocean);
            if (ocean == null) {
                return;
            }
            TerrainHeights heights = TerrainHeights.forWorld(world);
            check("terrain heights for the vanilla generator", heights != null);
            if (heights == null) {
                return;
            }
            // The finder can return a coastal 1:4 cell, so take the chunk with the most predicted water within
            // two chunks of it (predictions generate nothing). A coastal pick failed the "> 200 wet" check below.
            int cx = ocean.getX() >> 4;
            int cz = ocean.getZ() >> 4;
            int bestWet = -1;
            int bestX = cx;
            int bestZ = cz;
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    int count = 0;
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            if (heights.isWater(((cx + ox) << 4) + x, ((cz + oz) << 4) + z)) {
                                count++;
                            }
                        }
                    }
                    if (count > bestWet) {
                        bestWet = count;
                        bestX = cx + ox;
                        bestZ = cz + oz;
                    }
                }
            }
            cx = bestX;
            cz = bestZ;
            int[][] predicted = new int[256][3];
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int bx = (cx << 4) + x;
                    int bz = (cz << 4) + z;
                    predicted[z << 4 | x] = new int[] {heights.height(bx, bz), heights.floor(bx, bz), heights.isWater(bx, bz) ? 1 : 0};
                }
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.getChunk(cx + dx, cz + dz);
                }
            }
            Chunk chunk = world.getChunk(cx, cz);
            int heightOk = 0;
            int floorOk = 0;
            int wet = 0;
            int floorBelow = 0;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int y = 255;
                    while (y > 0 && chunk.getBlockState(x, y, z).getMaterial() == Material.AIR) {
                        y--;
                    }
                    int realHeight = y + 1;
                    while (y > 0 && (chunk.getBlockState(x, y, z).getMaterial().isLiquid()
                            || chunk.getBlockState(x, y, z).getMaterial() == Material.AIR
                            || chunk.getBlockState(x, y, z).getMaterial() == Material.ICE)) {
                        y--;
                    }
                    int realFloor = y + 1;
                    int[] p = predicted[z << 4 | x];
                    if (p[0] == realHeight) {
                        heightOk++;
                    }
                    if (p[1] == realFloor) {
                        floorOk++;
                    }
                    if (p[2] == 1) {
                        wet++;
                        if (p[1] < p[0]) {
                            floorBelow++;
                        }
                    }
                }
            }
            check("terrain ocean columns are wet, the floor below the surface", wet > 200 && floorBelow == wet, wet + " wet, " + floorBelow);
            check("terrain predicted floor matches the generated sea bed", floorOk >= 230, floorOk + "/256 (height " + heightOk + "/256)");
        });
    }

    private static String markerNames(ModernTemplate t) {
        List<String> out = new ArrayList<String>();
        for (ModernTemplate.DataMarker m : t.dataMarkers()) {
            out.add(m.metadata + "@" + m.pos);
        }
        return out.toString();
    }

    private static AxisAlignedBB aabb(StructureBoundingBox box) {
        return new AxisAlignedBB(box.minX, box.minY, box.minZ, box.maxX + 1, box.maxY + 1, box.maxZ + 1);
    }

    private static int countBlock(World world, StructureBoundingBox box, Block block) {
        int n = 0;
        for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(box.minX, box.minY, box.minZ), new BlockPos(box.maxX, box.maxY, box.maxZ))) {
            if (world.getBlockState(pos).getBlock() == block) {
                n++;
            }
        }
        return n;
    }

    // ---- hand-made structure files, written the way 26.1 writes them

    private static NBTTagCompound entry(String id, String... kv) {
        NBTTagCompound e = new NBTTagCompound();
        e.setString("id", id);
        if (kv.length > 0) {
            NBTTagCompound p = new NBTTagCompound();
            for (int i = 0; i + 1 < kv.length; i += 2) {
                p.setString(kv[i], kv[i + 1]);
            }
            e.setTag("properties", p);
        }
        return e;
    }

    private static NBTTagList ints(int... values) {
        NBTTagList list = new NBTTagList();
        for (int v : values) {
            list.appendTag(new NBTTagInt(v));
        }
        return list;
    }

    private static NBTTagCompound block(int x, int y, int z, int state, NBTTagCompound nbt) {
        NBTTagCompound b = new NBTTagCompound();
        b.setTag("pos", ints(x, y, z));
        b.setInteger("state", state);
        if (nbt != null) {
            b.setTag("nbt", nbt);
        }
        return b;
    }

    private static NBTTagCompound synthetic() {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("DataVersion", 5023);
        root.setTag("size", ints(8, 1, 3));
        NBTTagList palette = new NBTTagList();
        palette.appendTag(entry("minecraft:lime_bed", "facing", "west", "part", "head", "occupied", "false"));   // 0
        palette.appendTag(entry("minecraft:lime_bed", "facing", "west", "part", "foot", "occupied", "false"));   // 1
        palette.appendTag(entry("minecraft:potted_poppy"));                                                       // 2
        palette.appendTag(entry("minecraft:white_banner", "rotation", "0"));                                      // 3
        palette.appendTag(entry("minecraft:chest", "facing", "north", "type", "single", "waterlogged", "false")); // 4
        palette.appendTag(entry("minecraft:zombie_head", "rotation", "8"));                                       // 5
        palette.appendTag(entry("minecraft:jigsaw", "orientation", "up_north"));                                  // 6
        palette.appendTag(entry("minecraft:oak_slab", "type", "bottom", "waterlogged", "true"));                  // 7
        root.setTag("palette", palette);
        NBTTagList blocks = new NBTTagList();
        NBTTagCompound bedTile = new NBTTagCompound();
        bedTile.setString("id", "minecraft:bed");
        blocks.appendTag(block(0, 0, 0, 0, bedTile));
        blocks.appendTag(block(1, 0, 0, 1, bedTile.copy()));
        blocks.appendTag(block(2, 0, 0, 2, null));
        NBTTagCompound bannerTile = new NBTTagCompound();
        bannerTile.setString("id", "minecraft:banner");
        blocks.appendTag(block(3, 0, 0, 3, bannerTile));
        NBTTagCompound chestTile = new NBTTagCompound();
        chestTile.setString("id", "minecraft:chest");
        chestTile.setString("LootTable", "minecraft:chests/shipwreck_supply");
        blocks.appendTag(block(4, 0, 0, 4, chestTile));
        NBTTagCompound skullTile = new NBTTagCompound();
        skullTile.setString("id", "minecraft:skull");
        blocks.appendTag(block(5, 0, 0, 5, skullTile));
        NBTTagCompound jigsaw = new NBTTagCompound();
        jigsaw.setString("id", "minecraft:jigsaw");
        jigsaw.setString("name", "minecraft:test");
        jigsaw.setString("target", "minecraft:empty");
        jigsaw.setString("pool", "minecraft:test_pool");
        jigsaw.setString("joint", "rollable");
        jigsaw.setString("final_state", "minecraft:stone_bricks");
        blocks.appendTag(block(6, 0, 0, 6, jigsaw));
        blocks.appendTag(block(7, 0, 0, 7, null));
        root.setTag("blocks", blocks);
        NBTTagList entities = new NBTTagList();
        NBTTagCompound e = new NBTTagCompound();
        NBTTagList pos = new NBTTagList();
        pos.appendTag(new NBTTagDouble(1.5D));
        pos.appendTag(new NBTTagDouble(0.0D));
        pos.appendTag(new NBTTagDouble(2.5D));
        e.setTag("pos", pos);
        e.setTag("blockPos", ints(1, 0, 2));
        NBTTagCompound stand = new NBTTagCompound();
        stand.setString("id", "minecraft:armor_stand");
        stand.setByte("NoGravity", (byte) 1);
        e.setTag("nbt", stand);
        entities.appendTag(e);
        root.setTag("entities", entities);
        return root;
    }

    private static NBTTagCompound torchTemplate() {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("DataVersion", 5023);
        root.setTag("size", ints(2, 1, 1));
        NBTTagList palette = new NBTTagList();
        palette.appendTag(entry("minecraft:stone_bricks"));
        palette.appendTag(entry("minecraft:wall_torch", "facing", "east"));
        root.setTag("palette", palette);
        NBTTagList blocks = new NBTTagList();
        blocks.appendTag(block(0, 0, 0, 0, null));
        blocks.appendTag(block(1, 0, 0, 1, null));
        root.setTag("blocks", blocks);
        root.setTag("entities", new NBTTagList());
        return root;
    }
}

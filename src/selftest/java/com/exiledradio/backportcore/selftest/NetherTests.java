package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.structure.Jigsaw;
import com.exiledradio.backportcore.structure.JigsawElement;
import com.exiledradio.backportcore.structure.JigsawPiece;
import com.exiledradio.backportcore.structure.JigsawPools;
import com.exiledradio.backportcore.structure.ModernPalette;
import com.exiledradio.backportcore.structure.ModernProcessors;
import com.exiledradio.backportcore.structure.ModernTemplate;
import com.exiledradio.backportcore.structure.NetherStructureSlots;
import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.structure.StructureRandoms;
import com.exiledradio.backportcore.structure.TemplatePlacement;
import com.exiledradio.backportcore.structure.VanillaPlacements;
import com.exiledradio.backportcore.terrain.NetherHeights;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Mirror;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkGeneratorHell;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Core 1.1.0's nether support, against Mojang's real 1.16.5 bastion and ruined portal files and
 * against what 1.16.5's own code decides (the oracle files, written by running 1.16.5's classes).
 */
final class NetherTests {

    static final String[] POOLS = {"bastion/blocks/gold", "bastion/bridge/bridge_pieces", "bastion/bridge/connectors",
            "bastion/bridge/legs", "bastion/bridge/rampart_plates", "bastion/bridge/ramparts", "bastion/bridge/starting_pieces",
            "bastion/bridge/walls", "bastion/hoglin_stable/connectors", "bastion/hoglin_stable/large_stables/inner",
            "bastion/hoglin_stable/large_stables/outer", "bastion/hoglin_stable/mirrored_starting_pieces", "bastion/hoglin_stable/posts",
            "bastion/hoglin_stable/rampart_plates", "bastion/hoglin_stable/ramparts", "bastion/hoglin_stable/small_stables/inner",
            "bastion/hoglin_stable/small_stables/outer", "bastion/hoglin_stable/stairs", "bastion/hoglin_stable/starting_pieces",
            "bastion/hoglin_stable/wall_bases", "bastion/hoglin_stable/walls", "bastion/mobs/hoglin", "bastion/mobs/piglin",
            "bastion/mobs/piglin_melee", "bastion/starts", "bastion/treasure/bases", "bastion/treasure/bases/centers",
            "bastion/treasure/brains", "bastion/treasure/connectors", "bastion/treasure/corners/bottom", "bastion/treasure/corners/edges",
            "bastion/treasure/corners/middle", "bastion/treasure/corners/top", "bastion/treasure/entrances",
            "bastion/treasure/extensions/houses", "bastion/treasure/extensions/large_pool", "bastion/treasure/extensions/small_pool",
            "bastion/treasure/ramparts", "bastion/treasure/roofs", "bastion/treasure/stairs", "bastion/treasure/walls",
            "bastion/treasure/walls/bottom", "bastion/treasure/walls/mid", "bastion/treasure/walls/outer", "bastion/treasure/walls/top",
            "bastion/units/center_pieces", "bastion/units/edge_wall_units", "bastion/units/edges", "bastion/units/fillers/stage_0",
            "bastion/units/large_ramparts", "bastion/units/pathways", "bastion/units/rampart_plates", "bastion/units/ramparts",
            "bastion/units/stages/rot/stage_1", "bastion/units/stages/stage_0", "bastion/units/stages/stage_1",
            "bastion/units/stages/stage_2", "bastion/units/stages/stage_3", "bastion/units/wall_units", "bastion/units/walls/wall_bases"};

    static final String[] PORTALS = {"portal_1", "portal_2", "portal_3", "portal_4", "portal_5", "portal_6", "portal_7", "portal_8",
            "portal_9", "portal_10", "giant_portal_1", "giant_portal_2", "giant_portal_3"};

    /** What 1.16-only blocks are placed as in this test: something 1.12.2 has, so placing can be checked. */
    static final IBlockState STAND_IN = Blocks.CONCRETE.getStateFromMeta(15);

    private static int cascades;
    /** Chunks the floor test watches, and their blocks' kinds as generated, before population. */
    private static final Set<Long> WATCH = new HashSet<Long>();
    private static final Map<Long, byte[]> GENERATED = new HashMap<Long, byte[]>();
    private static Field populating;
    private static ChunkPos bastionChunk;

    private NetherTests() {
    }

    static void init() {
        JigsawPools.source(SelfTestMod.MOD_ID);
        // Its test_slab and test_double_slab serve a file's minecraft:test_slab (the family's slab convention).
        ModernPalette.source(SelfTestMod.MOD_ID);
        NetherStructureSlots.install(NetherStructureSlots.VANILLA_1_16_5);
        ModernPalette.entity("minecraft:piglin", new ResourceLocation("minecraft", "zombie_pigman"));
        ModernPalette.entity("minecraft:piglin_brute", new ResourceLocation("minecraft", "zombie_pigman"));
        MapGenStructureIO.registerStructure(TestBastion.Start.class, "BCST_Bastion");
        ScatteredStructure.register(TestBastion.KIND, world -> world.provider.getDimension() == -1 ? new TestBastion() : null);
        MinecraftForge.EVENT_BUS.register(new NetherTests.Cascades());
        try {
            populating = Chunk.class.getDeclaredField("populating");
            populating.setAccessible(true);
        } catch (Exception e) {
            BackportCore.LOGGER.error("SELFTEST cannot watch for cascading generation", e);
        }
    }

    public static final class Cascades {
        @SubscribeEvent
        public void onLoad(ChunkEvent.Load event) {
            if (event.getWorld().isRemote) {
                return;
            }
            long key = ChunkPos.asLong(event.getChunk().x, event.getChunk().z);
            if (event.getWorld().provider.getDimension() == -1 && WATCH.contains(key) && !GENERATED.containsKey(key)) {
                // ChunkProviderServer fires Load right after generating a chunk and before populating it.
                byte[] kinds = new byte[256 * 128];
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 128; y++) {
                            kinds[(z << 4 | x) * 128 + y] = kind(event.getChunk().getBlockState(x, y, z));
                        }
                    }
                }
                GENERATED.put(key, kinds);
            }
            if (populating == null) {
                return;
            }
            try {
                Object during = populating.get(null);
                if (during != null) {
                    cascades++;
                    BackportCore.LOGGER.error("SELFTEST cascade: chunk {} loaded while populating {} in dimension {}",
                            event.getChunk().getPos(), during, event.getWorld().provider.getDimension());
                }
            } catch (IllegalAccessException ignored) {
                // checked at init
            }
        }
    }

    /** The nether, loaded (a dedicated server with nobody in it unloads it) and kept loaded. */
    private static WorldServer nether() {
        DimensionManager.keepDimensionLoaded(-1, true);
        return net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(-1);
    }

    private static List<String[]> oracle(String name) throws Exception {
        List<String[]> out = new ArrayList<String[]>();
        InputStream in = NetherTests.class.getResourceAsStream("/assets/backportcore_selftest/oracle/" + name);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("#") && !line.trim().isEmpty()) {
                    out.add(line.trim().split(" "));
                }
            }
        }
        return out;
    }

    static void schedule() {
        then(1, "1.16.5 slots", world -> {
            int bastions = 0;
            int fortresses = 0;
            int wrong = 0;
            int portals = 0;
            int fossils = 0;
            int netherPortals = 0;
            int placementWrong = 0;
            String first = "";
            for (String[] l : oracle("slots_1165.txt")) {
                long seed = Long.parseLong(l[0]);
                int x = Integer.parseInt(l[2]);
                int z = Integer.parseInt(l[3]);
                switch (l[1]) {
                    case "bastion":
                    case "fortress": {
                        NetherStructureSlots.Slot want = l[1].equals("bastion") ? NetherStructureSlots.Slot.BASTION : NetherStructureSlots.Slot.FORTRESS;
                        if (want == NetherStructureSlots.Slot.BASTION) {
                            bastions++;
                        } else {
                            fortresses++;
                        }
                        NetherStructureSlots.Slot got = NetherStructureSlots.roll(seed, x, z);
                        if (got != want || NetherStructureSlots.roll(seed, x + 1, z) != NetherStructureSlots.Slot.NONE
                                || !VanillaPlacements.V1_16_5.BASTION_REMNANT.isCandidate(seed, x, z)) {
                            if (wrong++ == 0) {
                                first = String.join(" ", l) + " got " + got;
                            }
                        }
                        break;
                    }
                    case "portal":
                        portals++;
                        if (!VanillaPlacements.V1_16_5.RUINED_PORTAL.candidate(seed, x, z).equals(new ChunkPos(x, z))) {
                            placementWrong++;
                        }
                        break;
                    case "portal_nether":
                        portals++;
                        netherPortals++;
                        if (!VanillaPlacements.V1_16_5.RUINED_PORTAL_NETHER.candidate(seed, x, z).equals(new ChunkPos(x, z))) {
                            placementWrong++;
                        }
                        break;
                    case "fossil":
                        fossils++;
                        if (!VanillaPlacements.V1_16_5.NETHER_FOSSIL.candidate(seed, x, z).equals(new ChunkPos(x, z))) {
                            placementWrong++;
                        }
                        break;
                    default:
                        wrong++;
                }
            }
            check("seed parity: bastion or fortress in exactly 1.16.5's chunks", wrong == 0 && bastions > 1000 && fortresses > 1000,
                    bastions + " bastion and " + fortresses + " fortress slots over 4 seeds, " + wrong + " differ " + first);
            check("seed parity: ruined portal and nether fossil candidates are 1.16.5's", placementWrong == 0 && portals > 0 && netherPortals > 0 && fossils > 0,
                    portals + " portals (" + netherPortals + " with the nether's settings), " + fossils + " fossils, " + placementWrong + " differ");
            int randomWrong = 0;
            List<String[]> starts = oracle("start_random_1165.txt");
            for (String[] l : starts) {
                Random r = StructureRandoms.start(Long.parseLong(l[0]), Integer.parseInt(l[1]), Integer.parseInt(l[2]));
                for (int i = 3; i < 7; i++) {
                    if (r.nextInt(1000) != Integer.parseInt(l[i])) {
                        randomWrong++;
                    }
                }
            }
            check("seed parity: the start random is 1.16.5's setLargeFeatureSeed", randomWrong == 0 && starts.size() == 16,
                    starts.size() + " starts, " + randomWrong + " draws differ");
            WorldServer nether = nether();
            int deltas = 0;
            for (int x = -200; x < 200 && nether != null; x++) {
                if (NetherStructureSlots.roll(nether.getSeed(), x, 0) != NetherStructureSlots.startAt(nether, x, 0)) {
                    deltas++;
                }
            }
            check("slots: the biome check passes 1.12.2's one nether biome through", deltas == 0, deltas);
        });

        then(1, "bastion pools", world -> {
            int pools = 0;
            int elements = 0;
            Set<ResourceLocation> templates = new LinkedHashSet<ResourceLocation>();
            List<String> problems = new ArrayList<String>();
            for (String path : POOLS) {
                JigsawPools.Pool pool = JigsawPools.pool(new ResourceLocation("minecraft", path));
                if (pool == null) {
                    problems.add(path + " missing");
                    continue;
                }
                pools++;
                if (JigsawPools.pool(pool.fallback()) == null) {
                    problems.add(path + " fallback " + pool.fallback());
                }
                for (JigsawElement e : new LinkedHashSet<JigsawElement>(pool.elements())) {
                    elements++;
                    for (JigsawElement part : e.parts()) {
                        ResourceLocation t = part.template();
                        if (t == null) {
                            continue;
                        }
                        templates.add(t);
                        ModernTemplate template = ModernTemplate.load(JigsawPools.templateId(t));
                        if (template.isEmpty()) {
                            problems.add(t + " empty");
                        }
                        for (ModernTemplate.Jigsaw j : template.jigsaws()) {
                            if (JigsawPools.pool(new ResourceLocation(j.pool)) == null) {
                                problems.add(t + " points at missing pool " + j.pool);
                            }
                        }
                    }
                }
            }
            int lists = 0;
            for (String name : new String[] {"bastion_generic_degradation", "bottom_rampart", "bridge", "entrance_replacement", "high_rampart",
                    "high_wall", "housing", "rampart_degradation", "roof", "side_wall_degradation", "stable_degradation", "treasure_rooms"}) {
                if (JigsawPools.processorList(new ResourceLocation("minecraft", name)).isEmpty()) {
                    problems.add("processor list " + name + " empty");
                } else {
                    lists++;
                }
            }
            check("pools: every 1.16.5 bastion pool loads, with its fallback, templates and the pools its jigsaws name",
                    pools == POOLS.length && problems.isEmpty(), pools + " pools, " + elements + " elements, " + templates.size()
                            + " templates, " + lists + " processor lists; " + problems);
            check("pools: a missing pool is null, not a crash", JigsawPools.pool(new ResourceLocation("minecraft", "nope/none")) == null);
        });

        then(1, "bastion layouts", world -> {
            WorldServer nether = nether();
            int loaded = nether.getChunkProvider().getLoadedChunkCount();
            Map<String, List<String[]>> layouts = new LinkedHashMap<String, List<String[]>>();
            for (String[] l : oracle("bastion_layouts_1165.txt")) {
                layouts.computeIfAbsent(l[0] + " " + l[1] + " " + l[2], k -> new ArrayList<String[]>()).add(l);
            }
            int same = 0;
            int pieces = 0;
            String firstDiff = "";
            Set<String> used = new HashSet<String>();
            for (Map.Entry<String, List<String[]>> e : layouts.entrySet()) {
                String[] k = e.getKey().split(" ");
                long seed = Long.parseLong(k[0]);
                int cx = Integer.parseInt(k[1]);
                int cz = Integer.parseInt(k[2]);
                List<JigsawPiece> got = Jigsaw.assemble(nether, TestBastion.START, 6, new BlockPos(cx << 4, 33, cz << 4),
                        StructureRandoms.start(seed, cx, cz));
                List<String[]> want = e.getValue();
                pieces += want.size();
                boolean ok = got.size() == want.size();
                for (int i = 0; ok && i < want.size(); i++) {
                    String mine = line(got.get(i));
                    String theirs = String.join(" ", java.util.Arrays.copyOfRange(want.get(i), 4, want.get(i).length));
                    if (!mine.equals(theirs)) {
                        ok = false;
                        firstDiff = e.getKey() + " piece " + i + ": ours " + mine + " / 1.16.5 " + theirs;
                    }
                }
                if (!ok && firstDiff.isEmpty()) {
                    firstDiff = e.getKey() + ": " + got.size() + " pieces, 1.16.5 " + want.size();
                }
                if (ok) {
                    same++;
                }
                for (JigsawPiece p : got) {
                    if (p.element().template() != null) {
                        used.add(p.element().template().getPath());
                    }
                }
            }
            check("seed parity: bastion layouts piece for piece as 1.16.5's JigsawPlacement", same == layouts.size() && same > 0,
                    same + "/" + layouts.size() + " layouts, " + pieces + " pieces, " + used.size() + " templates used. " + firstDiff);
            check("assembly loads and generates no chunk", nether.getChunkProvider().getLoadedChunkCount() == loaded,
                    loaded + " -> " + nether.getChunkProvider().getLoadedChunkCount());

            // Many more layouts: all four starts, every piece inside today's 80-block cube.
            int outside = 0;
            int total = 0;
            int starts = 0;
            Set<String> startKinds = new HashSet<String>();
            for (int i = 0; i < 200; i++) {
                List<JigsawPiece> got = Jigsaw.assemble(nether, TestBastion.START, 6, new BlockPos(i * 16, 33, -i * 16),
                        StructureRandoms.start(nether.getSeed() + i, i, -i));
                starts++;
                total += got.size();
                startKinds.add(String.valueOf(got.get(0).element().template()));
                StructureBoundingBox b = got.get(0).getBoundingBox();
                int mx = (b.maxX + b.minX) / 2;
                int mz = (b.maxZ + b.minZ) / 2;
                for (JigsawPiece p : got) {
                    StructureBoundingBox pb = p.getBoundingBox();
                    if (p != got.get(0) && (pb.minX < mx - 80 || pb.maxX > mx + 80 || pb.minZ < mz - 80 || pb.maxZ > mz + 80
                            || pb.minY < 33 - 80 || pb.maxY > 33 + 80)) {
                        outside++;
                    }
                }
            }
            check("assembly: 200 bastions from all four starts stay in the 80-block cube", outside == 0 && startKinds.size() == 4,
                    starts + " bastions, " + total + " pieces, starts " + startKinds + ", " + outside + " outside");
        });

        then(1, "stream X's Core requests", world -> {
            // A block named without properties is its default state: block_age's bare stone slab is a bottom half.
            IBlockState bare = ModernPalette.resolve("minecraft:stone_slab").state;
            IBlockState smooth = ModernPalette.resolve("minecraft:smooth_stone_slab").state;
            IBlockState brick = ModernPalette.resolve("minecraft:stone_brick_slab").state;
            IBlockState top = ModernPalette.resolve("minecraft:stone_brick_slab[type=top]").state;
            IBlockState dbl = ModernPalette.resolve("minecraft:stone_brick_slab[type=double]").state;
            check("palette: a slab named without properties is a bottom half (its default state)",
                    bare != null && bare.getBlock() == Blocks.STONE_SLAB && bare.getValue(net.minecraft.block.BlockSlab.HALF) == net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM
                            && bare.getValue(net.minecraft.block.BlockStoneSlab.VARIANT) == net.minecraft.block.BlockStoneSlab.EnumType.STONE
                            && bare.equals(smooth)
                            && brick != null && brick.getBlock() == Blocks.STONE_SLAB && brick.getValue(net.minecraft.block.BlockSlab.HALF) == net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM
                            && brick.getValue(net.minecraft.block.BlockStoneSlab.VARIANT) == net.minecraft.block.BlockStoneSlab.EnumType.SMOOTHBRICK
                            && top != null && top.getBlock() == Blocks.STONE_SLAB && top.getValue(net.minecraft.block.BlockSlab.HALF) == net.minecraft.block.BlockSlab.EnumBlockHalf.TOP
                            && dbl != null && dbl.getBlock() == Blocks.DOUBLE_STONE_SLAB,
                    bare + ", " + smooth + ", " + brick + ", " + top + ", " + dbl);
            IBlockState stairs = ModernPalette.resolve("minecraft:oak_stairs").state;
            check("palette: bare stairs are 1.16's default (north, bottom)", stairs != null && stairs.getBlock() == Blocks.OAK_STAIRS
                    && stairs.getValue(net.minecraft.block.BlockStairs.FACING) == net.minecraft.util.EnumFacing.NORTH
                    && stairs.getValue(net.minecraft.block.BlockStairs.HALF) == net.minecraft.block.BlockStairs.EnumHalf.BOTTOM, stairs);
            // A source mod's 1.12.2-style slab: type picks the half or the _double_slab block.
            IBlockState sb = ModernPalette.resolve("minecraft:test_slab[type=bottom,waterlogged=false]").state;
            IBlockState st = ModernPalette.resolve("minecraft:test_slab[type=top,waterlogged=false]").state;
            IBlockState sd = ModernPalette.resolve("minecraft:test_slab[type=double,waterlogged=false]").state;
            IBlockState sn = ModernPalette.resolve("minecraft:test_slab").state;
            check("palette: a source mod's slab follows type (bottom, top, its _double_slab)",
                    sb != null && sb.getBlock() == TestBlocks.slab && sb.getValue(net.minecraft.block.BlockSlab.HALF) == net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM
                            && st != null && st.getBlock() == TestBlocks.slab && st.getValue(net.minecraft.block.BlockSlab.HALF) == net.minecraft.block.BlockSlab.EnumBlockHalf.TOP
                            && sd != null && sd.getBlock() == TestBlocks.doubleSlab
                            && sn != null && sn.getBlock() == TestBlocks.slab && sn.getValue(net.minecraft.block.BlockSlab.HALF) == net.minecraft.block.BlockSlab.EnumBlockHalf.BOTTOM,
                    sb + ", " + st + ", " + sd + ", " + sn);
            // A template of nothing but a jigsaw places the jigsaw's final state (bastion/blocks/gold: a gold block).
            ModernTemplate gold = ModernTemplate.load(JigsawPools.templateId(new ResourceLocation("minecraft", "bastion/blocks/gold")));
            BlockPos site = world.getSpawnPoint().add(3000, 0, 3200);
            site = new BlockPos(site.getX(), 120, site.getZ());
            world.getChunk(site);
            world.setBlockToAir(site);
            int placed = gold.place(world, site, new TemplatePlacement());
            BlockPos at = gold.jigsaws().isEmpty() ? site : site.add(gold.jigsaws().get(0).pos);
            check("placing: a template of nothing but a jigsaw places its final state", placed == 1 && gold.jigsaws().size() == 1
                    && world.getBlockState(at).getBlock() == Blocks.GOLD_BLOCK, placed + " placed, " + world.getBlockState(at));
            check("seed parity: the nether's ruined portals are (25, 10), the overworld's (40, 15)",
                    VanillaPlacements.V1_16_5.RUINED_PORTAL_NETHER.spacing() == 25 && VanillaPlacements.V1_16_5.RUINED_PORTAL_NETHER.separation() == 10
                            && VanillaPlacements.V1_16_5.RUINED_PORTAL.spacing() == 40);
        });

        then(1, "blocks resolved", world -> {
            Set<ResourceLocation> templates = allTemplates();
            Set<String> before = new TreeSet<String>();
            for (ResourceLocation t : templates) {
                before.addAll(ModernTemplate.load(JigsawPools.templateId(t)).unresolvedBlocks());
            }
            Set<String> old = flatteningNames();
            List<String> shouldResolve = new ArrayList<String>();
            for (String name : before) {
                if (old.contains(name)) {
                    shouldResolve.add(name);
                }
            }
            check("blocks: every block of 1.16.5's bastions and ruined portals that 1.12.2 has resolves",
                    shouldResolve.isEmpty() && old.size() > 400, templates.size() + " templates; 1.12.2 blocks unresolved: " + shouldResolve);
            BackportCore.LOGGER.info("SELFTEST INFO 1.16-only blocks the bastions and ruined portals need ({}): {}", before.size(), before);
            for (String name : before) {
                // A jigsaw's final state is reported as written; and 26.x renamed chain to iron_chain, the name
                // a file's chain is read as (names are today's).
                String plain = name.indexOf('[') > 0 ? name.substring(0, name.indexOf('[')) : name;
                ModernPalette.map(plain, props -> STAND_IN);
                if (plain.equals("minecraft:chain")) {
                    ModernPalette.map("minecraft:iron_chain", props -> STAND_IN);
                }
            }
            ModernTemplate.clearCache();
            Set<String> after = new TreeSet<String>();
            for (ResourceLocation t : templates) {
                after.addAll(ModernTemplate.load(JigsawPools.templateId(t)).unresolvedBlocks());
            }
            check("blocks: with the 1.16 blocks provided, every block resolves", after.isEmpty() && !before.isEmpty(),
                    before.size() + " provided, still unresolved " + after);
        });

        then(1, "every piece places", world -> {
            BlockPos site = world.getSpawnPoint().add(3000, 0, 3000);
            site = new BlockPos(site.getX(), 120, site.getZ());
            for (int x = -1; x <= 4; x++) {
                for (int z = -1; z <= 4; z++) {
                    world.getChunk((site.getX() >> 4) + x, (site.getZ() >> 4) + z);
                }
            }
            int templates = 0;
            int blocks = 0;
            List<String> wrong = new ArrayList<String>();
            for (ResourceLocation t : allTemplates()) {
                ModernTemplate template = ModernTemplate.load(JigsawPools.templateId(t));
                template.place(world, site, new TemplatePlacement().finalizeEntities(true));
                templates++;
                final BlockPos origin = site;
                int[] bad = new int[1];
                int[] n = new int[1];
                template.forEachBlock(0, (local, block) -> {
                    if (block.state() == null) {
                        return;
                    }
                    n[0]++;
                    IBlockState actual = world.getBlockState(origin.add(local));
                    if (actual.getBlock() != block.state().getBlock()) {
                        if (bad[0]++ == 0 && wrong.size() < 5) {
                            wrong.add(t + " " + local + " " + block.name() + " -> " + actual);
                        }
                    }
                });
                blocks += n[0];
                if (bad[0] > 0) {
                    wrong.add(t + ": " + bad[0] + " wrong");
                }
            }
            check("placing: every 1.16.5 bastion and ruined portal template places every block", wrong.isEmpty() && templates > 150,
                    templates + " templates, " + blocks + " blocks; " + wrong);
            List<EntityPigZombie> mobs = world.getEntitiesWithinAABB(EntityPigZombie.class, new AxisAlignedBB(site).grow(64));
            boolean armed = !mobs.isEmpty() && mobs.get(0).getItemStackFromSlot(EntityEquipmentSlot.MAINHAND).getItem() == Items.GOLDEN_SWORD;
            check("placing: a piglin of a mob piece spawns as the mapped entity, given its first-spawn gear", armed,
                    mobs.size() + " mapped mobs");
            check("placing: no item dropped", world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(site).grow(64)).isEmpty());
        });

        then(1, "ruined portal processors", world -> {
            ModernTemplate portal = ModernTemplate.load(JigsawPools.templateId(new ResourceLocation("minecraft", "ruined_portal/portal_1")));
            Map<String, Integer> seen = new HashMap<String, Integer>();
            BlockPos site = world.getSpawnPoint().add(3000, 0, 3100);
            site = new BlockPos(site.getX(), 120, site.getZ());
            portal.place(world, site, TemplatePlacement.of(Rotation.NONE, Mirror.NONE)
                    .processor(ModernProcessors.blockAge(0.5F)).processor(ModernProcessors.blackstoneReplace())
                    .processor(ModernProcessors.lavaSubmerged())
                    .processor((w, p, b, r) -> {
                        seen.merge(b.name(), 1, Integer::sum);
                        return b;
                    }));
            boolean stone = seen.keySet().stream().anyMatch(n -> n.startsWith("minecraft:stone") || n.startsWith("minecraft:mossy_")
                    || n.startsWith("minecraft:cobblestone") || n.startsWith("minecraft:cracked_stone"));
            boolean black = seen.keySet().stream().anyMatch(n -> n.contains("blackstone"));
            check("processors: blackstone_replace turns a portal's stone to blackstone", black && !stone, seen.keySet());
        });

        then(1, "nether floor", world -> {
            WorldServer nether = nether();
            NetherHeights heights = NetherHeights.forWorld(nether);
            check("floor: the nether's terrain can be predicted", heights != null && heights.predicts());
            if (heights == null) {
                return;
            }
            int loaded = nether.getChunkProvider().getLoadedChunkCount();
            int[][] chunks = {{300, -300}, {-500, 120}, {77, 900}, {-1200, -1300}, {640, 640}, {-60, 2000}};
            for (int[] c : chunks) {
                WATCH.add(ChunkPos.asLong(c[0], c[1]));
            }
            Map<Long, int[][]> predicted = new HashMap<Long, int[][]>();
            for (int[] c : chunks) {
                int[][] cols = new int[256][];
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int bx = (c[0] << 4) + x;
                        int bz = (c[1] << 4) + z;
                        int[] all = heights.floors(bx, bz, 0, 127);
                        int lowest = heights.floor(bx, bz, 32, 120);
                        int[] col = new int[all.length + 1];
                        col[0] = lowest;
                        System.arraycopy(all, 0, col, 1, all.length);
                        cols[z << 4 | x] = col;
                    }
                }
                predicted.put(ChunkPos.asLong(c[0], c[1]), cols);
            }
            check("floor: predicting loads no chunk", nether.getChunkProvider().getLoadedChunkCount() == loaded,
                    loaded + " -> " + nether.getChunkProvider().getLoadedChunkCount());
            int compared = 0;
            int matched = 0;
            int skipped = 0;
            int lowestOk = 0;
            String firstDiff = "";
            for (int[] c : chunks) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        nether.getChunk(c[0] + dx, c[1] + dz);
                    }
                }
                Chunk chunk = nether.getChunk(c[0], c[1]);
                int[][] cols = predicted.get(ChunkPos.asLong(c[0], c[1]));
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (decorated(chunk, x, z)) {
                            skipped++;
                            continue;
                        }
                        compared++;
                        List<Integer> real = new ArrayList<Integer>();
                        int realLowest = NetherHeights.NONE;
                        for (int y = 1; y <= 127; y++) {
                            if (open(chunk.getBlockState(x, y, z)) && solid(chunk.getBlockState(x, y - 1, z))) {
                                real.add(y);
                                if (realLowest == NetherHeights.NONE && y >= 32 && y <= 120) {
                                    realLowest = y;
                                }
                            }
                        }
                        int[] col = cols[z << 4 | x];
                        List<Integer> mine = new ArrayList<Integer>();
                        for (int i = 1; i < col.length; i++) {
                            mine.add(col[i]);
                        }
                        if (mine.equals(real)) {
                            matched++;
                        } else if (firstDiff.isEmpty()) {
                            firstDiff = "chunk " + c[0] + "," + c[1] + " column " + x + "," + z + ": predicted " + mine + ", generated " + real;
                        }
                        if (col[0] == realLowest) {
                            lowestOk++;
                        }
                    }
                }
            }
            // Exact: every block's kind, against the chunk as the generator made it (ChunkEvent.Load, before population).
            int blocks = 0;
            int blocksOk = 0;
            String firstBlock = "";
            for (int[] c : chunks) {
                byte[] made = GENERATED.get(ChunkPos.asLong(c[0], c[1]));
                if (made == null) {
                    firstBlock = "chunk " + c[0] + "," + c[1] + " not seen generated";
                    continue;
                }
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 128; y++) {
                            int bx = (c[0] << 4) + x;
                            int bz = (c[1] << 4) + z;
                            byte want = made[(z << 4 | x) * 128 + y];
                            byte got = heights.isAir(bx, y, bz) ? 0 : heights.isLiquid(bx, y, bz) ? 1 : heights.isSolid(bx, y, bz) ? (byte) 2 : 3;
                            blocks++;
                            if (want == got) {
                                blocksOk++;
                            } else if (firstBlock.isEmpty()) {
                                firstBlock = "at " + bx + "," + y + "," + bz + ": predicted " + got + ", generated " + want;
                            }
                        }
                    }
                }
            }
            check("floor: the predicted nether columns are the generated chunks, block for block", blocks == 6 * 256 * 128 && blocksOk == blocks,
                    blocksOk + "/" + blocks + " blocks. " + firstBlock);
            // Population then changes a few columns the filter cannot see (gravel falls when fire or lava lands
            // beside it); the lowest floor still holds in nearly all of them.
            check("floor: after population the lowest floor from 32 to 120 still matches (99%)", compared > 600 && lowestOk * 100 >= compared * 99,
                    "lowest " + lowestOk + "/" + compared + ", every floor " + matched + "/" + compared + " (" + skipped
                    + " decorated columns left out). " + firstDiff);
            NetherHeights base = NetherHeights.baseForWorld(nether);
            int f = base == null ? NetherHeights.UNKNOWN : base.floor(5000, 5000, 0, 127);
            check("floor: 1.16's base column (bare noise) reads too", base != null && base.predicts() && f != NetherHeights.UNKNOWN, f);
            NetherHeights over = NetherHeights.forWorld(world);
            int seaBed = over == null ? NetherHeights.UNKNOWN : over.floor(world.getSpawnPoint().getX() + 4000, world.getSpawnPoint().getZ());
            check("floor: the ocean-floor variant answers in the overworld", over != null && seaBed > 0 && seaBed < 256, seaBed);
        });

        then(1, "bastion in the world", world -> {
            WorldServer nether = nether();
            cascades = 0;
            ChunkPos found = null;
            for (int r = 0; r < 6 && found == null; r++) {
                for (int rx = -r; rx <= r && found == null; rx++) {
                    for (int rz = -r; rz <= r && found == null; rz++) {
                        ChunkPos c = VanillaPlacements.V1_16_5.BASTION_REMNANT.candidate(nether.getSeed(), rx * 27, rz * 27);
                        if (NetherStructureSlots.startAt(nether, c.x, c.z) == NetherStructureSlots.Slot.BASTION) {
                            found = c;
                        }
                    }
                }
            }
            check("world: a bastion slot near the middle", found != null, found);
            if (found == null) {
                return;
            }
            bastionChunk = found;
            List<JigsawPiece> planned = Jigsaw.assemble(nether, TestBastion.START, 6, new BlockPos(found.x << 4, 33, found.z << 4),
                    StructureRandoms.start(nether.getSeed(), found.x, found.z));
            StructureBoundingBox box = new StructureBoundingBox(planned.get(0).getBoundingBox());
            for (JigsawPiece p : planned) {
                box.expandTo(p.getBoundingBox());
            }
            int minCX = (box.minX >> 4) - 2;
            int maxCX = (box.maxX >> 4) + 2;
            int minCZ = (box.minZ >> 4) - 2;
            int maxCZ = (box.maxZ >> 4) + 2;
            for (int x = minCX; x <= maxCX; x++) {
                for (int z = minCZ; z <= maxCZ; z++) {
                    nether.getChunk(x, z);
                }
            }
            StructureStart start = null;
            for (StructureStart s : TestBastion.LAID_OUT) {
                if (s.getChunkPosX() == found.x && s.getChunkPosZ() == found.z) {
                    start = s;
                }
            }
            check("world: the bastion is laid out from the populate step, as planned", start != null
                    && start.getComponents().size() == planned.size(), (start == null ? "none" : start.getComponents().size()) + " pieces, planned "
                    + planned.size() + ", box " + box);
            int standIns = 0;
            int chests = 0;
            for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(box.minX, box.minY, box.minZ), new BlockPos(box.maxX, box.maxY, box.maxZ))) {
                IBlockState s = nether.getBlockState(pos);
                if (s == STAND_IN) {
                    standIns++;
                } else if (s.getBlock() == Blocks.CHEST) {
                    chests++;
                }
            }
            check("world: the bastion is built", standIns > 5000 && chests > 0, standIns + " 1.16 blocks, " + chests + " chests in " + box);
            check("world: no cascading generation", cascades == 0, cascades);
            check("world: no item dropped", nether.getEntitiesWithinAABB(EntityItem.class,
                    new AxisAlignedBB(box.minX, box.minY, box.minZ, box.maxX + 1, box.maxY + 1, box.maxZ + 1)).isEmpty());
            check("world: no fortress starts in a bastion slot", NetherStructureSlots.savedFortress(nether, found.x, found.z) == null
                    && !NetherStructureSlots.fortressStartsAt(nether, found.x, found.z));
            if (start == null) {
                return;
            }

            // Saving and loading: the start through MapGenStructureIO, every piece the same.
            NBTTagCompound saved = start.writeStructureComponentsToNBT(found.x, found.z);
            StructureStart back = MapGenStructureIO.getStructureStart(saved, nether);
            int same = 0;
            for (int i = 0; back != null && i < back.getComponents().size() && i < start.getComponents().size(); i++) {
                JigsawPiece a = (JigsawPiece) start.getComponents().get(i);
                StructureComponent bc = back.getComponents().get(i);
                if (bc instanceof JigsawPiece && a.getBoundingBox().toString().equals(bc.getBoundingBox().toString())
                        && a.position().equals(((JigsawPiece) bc).position()) && a.rotation() == ((JigsawPiece) bc).rotation()
                        && a.pivot().equals(((JigsawPiece) bc).pivot()) && a.element().toString().equals(((JigsawPiece) bc).element().toString())) {
                    same++;
                }
            }
            check("saving: every piece saves and loads the same", back != null && same == start.getComponents().size(),
                    same + "/" + start.getComponents().size());
            // The loaded pieces, placed again in order, put back exactly what generation did (processors included).
            Map<BlockPos, IBlockState> before = new HashMap<BlockPos, IBlockState>();
            for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(box.minX, box.minY, box.minZ), new BlockPos(box.maxX, box.maxY, box.maxZ))) {
                before.put(pos.toImmutable(), nether.getBlockState(pos));
            }
            for (int i = 0; back != null && i < back.getComponents().size(); i++) {
                back.getComponents().get(i).addComponentParts(nether, new Random(1L), new StructureBoundingBox(box));
            }
            int differ = 0;
            int checked = 0;
            int decorated = 0;
            String firstDiffer = "";
            for (Map.Entry<BlockPos, IBlockState> e : before.entrySet()) {
                IBlockState now = nether.getBlockState(e.getKey());
                if (populationBlock(e.getValue()) || populationBlock(now)) {
                    decorated++;
                    continue;
                }
                checked++;
                if (now != e.getValue()) {
                    if (differ++ == 0) {
                        firstDiffer = e.getKey() + " " + e.getValue() + " -> " + now;
                    }
                }
            }
            check("saving: the loaded pieces place exactly the blocks generation did", checked > 50000 && differ == 0,
                    differ + " of " + checked + " differ (" + decorated + " fire, glowstone, lava or mushrooms left out). " + firstDiffer);
        });

        then(1, "fortress slots", world -> {
            WorldServer nether = nether();
            Object gen = null;
            try {
                Field f = ChunkGeneratorHell.class.getDeclaredField("genNetherBridge");
                f.setAccessible(true);
                gen = f.get(nether.getChunkProvider().chunkGenerator);
            } catch (Exception e) {
                BackportCore.LOGGER.error("SELFTEST", e);
            }
            check("fortress: the nether's fortress generator follows the shared slots", gen != null
                    && gen.getClass().getName().endsWith("SharedFortress") && NetherStructureSlots.fortressRuleActive(), gen);
            ChunkPos fortress = null;
            for (int r = 0; r < 6 && fortress == null; r++) {
                for (int rx = -r; rx <= r && fortress == null; rx++) {
                    for (int rz = -r; rz <= r && fortress == null; rz++) {
                        ChunkPos c = VanillaPlacements.V1_16_5.NETHER_BRIDGE.candidate(nether.getSeed(), rx * 27, rz * 27);
                        if (NetherStructureSlots.startAt(nether, c.x, c.z) == NetherStructureSlots.Slot.FORTRESS) {
                            fortress = c;
                        }
                    }
                }
            }
            check("fortress: a fortress slot near the middle", fortress != null, fortress);
            if (fortress == null) {
                return;
            }
            cascades = 0;
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    nether.getChunk(fortress.x + x, fortress.z + z);
                }
            }
            StructureStart saved = NetherStructureSlots.savedFortress(nether, fortress.x, fortress.z);
            int bricks = 0;
            if (saved != null) {
                StructureBoundingBox b = saved.getBoundingBox();
                int minX = Math.max(b.minX, (fortress.x - 2) << 4);
                int maxX = Math.min(b.maxX, ((fortress.x + 2) << 4) + 15);
                int minZ = Math.max(b.minZ, (fortress.z - 2) << 4);
                int maxZ = Math.min(b.maxZ, ((fortress.z + 2) << 4) + 15);
                for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(minX, b.minY, minZ), new BlockPos(maxX, b.maxY, maxZ))) {
                    if (nether.getBlockState(pos).getBlock() == Blocks.NETHER_BRICK) {
                        bricks++;
                    }
                }
            }
            check("fortress: one starts in its 1.16.5 slot and is built", saved != null && bricks > 50
                    && NetherStructureSlots.fortressStartsAt(nether, fortress.x, fortress.z), fortress + ", " + bricks + " nether bricks");
            check("fortress: no cascading generation", cascades == 0, cascades);
            BlockPos located = nether.findNearestStructure("Fortress", new BlockPos(fortress.x << 4, 64, fortress.z << 4), false);
            check("fortress: /locate finds a fortress slot", located != null
                    && NetherStructureSlots.roll(nether.getSeed(), located.getX() >> 4, located.getZ() >> 4) == NetherStructureSlots.Slot.FORTRESS,
                    located);
            // 1.12.2's own grid starts nothing any more where 1.16.5 has no fortress.
            int oldGrid = 0;
            int startsThere = 0;
            for (int x = -64; x < 64; x++) {
                for (int z = -64; z < 64; z++) {
                    if (vanilla112(nether.getSeed(), x, z) && NetherStructureSlots.roll(nether.getSeed(), x, z) != NetherStructureSlots.Slot.FORTRESS) {
                        oldGrid++;
                        if (NetherStructureSlots.fortressStartsAt(nether, x, z)) {
                            startsThere++;
                        }
                    }
                }
            }
            check("fortress: 1.12.2's own fortress grid no longer starts fortresses", oldGrid > 0 && startsThere == 0,
                    oldGrid + " old slots, " + startsThere + " still start");
        });
    }

    /** Blocks the nether's population puts down after the structure: they may sit where the structure was. */
    private static boolean populationBlock(IBlockState s) {
        Block b = s.getBlock();
        return b == Blocks.FIRE || b == Blocks.GLOWSTONE || b == Blocks.LAVA || b == Blocks.FLOWING_LAVA || b == Blocks.BROWN_MUSHROOM
                || b == Blocks.RED_MUSHROOM;
    }

    /** 1.12.2's MapGenNetherBridge.canSpawnStructureAtCoords. */
    private static boolean vanilla112(long seed, int chunkX, int chunkZ) {
        int i = chunkX >> 4;
        int j = chunkZ >> 4;
        Random rand = new Random((long) (i ^ j << 4) ^ seed);
        rand.nextInt();
        if (rand.nextInt(3) != 0) {
            return false;
        }
        if (chunkX != (i << 4) + 4 + rand.nextInt(8)) {
            return false;
        }
        return chunkZ == (j << 4) + 4 + rand.nextInt(8);
    }

    private static String line(JigsawPiece p) {
        StructureBoundingBox b = p.getBoundingBox();
        return p.element().template() + " " + p.rotation().name() + " " + p.position().getX() + " " + p.position().getY() + " "
                + p.position().getZ() + " " + b.minX + " " + b.minY + " " + b.minZ + " " + b.maxX + " " + b.maxY + " " + b.maxZ + " "
                + p.groundLevelDelta();
    }

    private static Set<ResourceLocation> allTemplates() {
        Set<ResourceLocation> out = new LinkedHashSet<ResourceLocation>();
        for (String path : POOLS) {
            JigsawPools.Pool pool = JigsawPools.pool(new ResourceLocation("minecraft", path));
            if (pool == null) {
                continue;
            }
            for (JigsawElement e : pool.elements()) {
                for (JigsawElement part : e.parts()) {
                    if (part.template() != null) {
                        out.add(part.template());
                    }
                }
            }
        }
        for (String p : PORTALS) {
            out.add(new ResourceLocation("minecraft", "ruined_portal/" + p));
        }
        return out;
    }

    private static Set<String> flatteningNames() throws Exception {
        Set<String> out = new HashSet<String>();
        InputStream in = BackportCore.class.getResourceAsStream("/assets/backportcore/structure/flattening.txt");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }
                String first = line.split("\t")[0];
                int open = first.indexOf('[');
                out.add(open > 0 ? first.substring(0, open) : first);
            }
        }
        return out;
    }

    /** 0 air, 1 liquid, 2 solid, 3 other (fire, plants): NetherHeights' kinds. */
    private static byte kind(IBlockState s) {
        Material m = s.getMaterial();
        return m == Material.AIR ? 0 : m.isLiquid() ? 1 : m.blocksMovement() ? (byte) 2 : 3;
    }

    private static boolean open(IBlockState s) {
        Material m = s.getMaterial();
        return m == Material.AIR || (!m.isLiquid() && !m.blocksMovement());
    }

    private static boolean solid(IBlockState s) {
        return !s.getMaterial().isLiquid() && s.getMaterial().blocksMovement();
    }

    /** A column population changed: glowstone, lava falls and hidden lava, fortresses, anything not of the bare terrain. */
    private static boolean decorated(Chunk chunk, int x, int z) {
        for (int y = 0; y < 128; y++) {
            IBlockState s = chunk.getBlockState(x, y, z);
            Block b = s.getBlock();
            if (b == Blocks.AIR || b == Blocks.NETHERRACK || b == Blocks.SOUL_SAND || b == Blocks.GRAVEL || b == Blocks.BEDROCK
                    || b == Blocks.QUARTZ_ORE || b == Blocks.MAGMA || b == Blocks.FIRE || b == Blocks.BROWN_MUSHROOM || b == Blocks.RED_MUSHROOM) {
                continue;
            }
            if ((b == Blocks.LAVA || b == Blocks.FLOWING_LAVA) && y <= 31 && b == Blocks.LAVA) {
                continue;
            }
            return true;
        }
        return false;
    }
}

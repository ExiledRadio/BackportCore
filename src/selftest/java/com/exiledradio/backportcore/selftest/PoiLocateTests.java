package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.locate.LocateChecks;
import com.exiledradio.backportcore.locate.PoiLocate;

import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/** Core's {@code /locate poi}: 26.3's types and tags, 1.12.2's blocks, today's 256-block search and messages. */
final class PoiLocateTests {

    /** A test type over a block no world generates. */
    static final String MARKER = "backportcore_selftest:marker";
    static final String MARKERS = "#backportcore_selftest:markers";

    /** A chunk far from spawn, unloaded by the test so its point of interest is read from its save. */
    private static int farX;
    private static int farZ;
    private static BlockPos farPos;

    private PoiLocateTests() {
    }

    static void init() {
        PoiLocate.register(MARKER, Blocks.BARRIER);
        PoiLocate.tag(MARKERS, MARKER, "minecraft:cleric");
    }

    private static MinecraftServer server() {
        return FMLCommonHandler.instance().getMinecraftServerInstance();
    }

    private static String expected(String name, BlockPos from, BlockPos at) {
        double dx = at.getX() - from.getX();
        double dz = at.getZ() - from.getZ();
        int distance = MathHelper.floor(MathHelper.sqrt((float) (dx * dx + dz * dz)));
        return "The nearest " + name + " is at [" + at.getX() + ", ~, " + at.getZ() + "] (" + distance + " blocks away)";
    }

    /** A block made for the test, never registered: only its name matters to the types. */
    private static IBlockState named(String name) {
        net.minecraft.block.Block block = new net.minecraft.block.Block(net.minecraft.block.material.Material.ROCK);
        block.setRegistryName(new net.minecraft.util.ResourceLocation(name));
        return block.getDefaultState();
    }

    private static List<String> locate(WorldServer world, BlockPos from, String what) {
        return LocateChecks.run(server(), world, from, "locate poi " + what);
    }

    private static void unloadFar(WorldServer world) {
        for (Chunk chunk : new ArrayList<Chunk>(world.getChunkProvider().getLoadedChunks())) {
            if (Math.abs(chunk.x - farX) <= 18 && Math.abs(chunk.z - farZ) <= 18) {
                world.getChunkProvider().queueUnload(chunk);
            }
        }
    }

    static void schedule() {
        then(1, "locate poi types", world -> {
            List<String> ids = PoiLocate.ids();
            List<String> today = new ArrayList<String>();
            for (String id : PoiLocate.TODAY) {
                today.add("minecraft:" + id);
            }
            check("26.3's 21 types are known", PoiLocate.TODAY.size() == 21 && ids.containsAll(today), ids);
            check("26.3's tags are known", PoiLocate.tags().containsAll(Arrays.asList("#minecraft:acquirable_job_site",
                    "#minecraft:bee_home", "#minecraft:village")), PoiLocate.tags());
            check("#minecraft:village is the 13 job sites, home and meeting", PoiLocate.members("#minecraft:village").size() == 15
                    && PoiLocate.members("#minecraft:village").contains("minecraft:meeting")
                    && PoiLocate.members("#minecraft:village").contains("minecraft:weaponsmith"),
                    PoiLocate.members("#minecraft:village"));
            IBlockState head = Blocks.BED.getDefaultState().withProperty(BlockBed.PART, BlockBed.EnumPartType.HEAD);
            IBlockState foot = Blocks.BED.getDefaultState().withProperty(BlockBed.PART, BlockBed.EnumPartType.FOOT);
            check("a bed's head is home, its foot is nothing", "minecraft:home".equals(PoiLocate.typeOf(head))
                    && PoiLocate.typeOf(foot) == null, PoiLocate.typeOf(head) + " / " + PoiLocate.typeOf(foot));
            check("1.12.2's cauldron, every level, is the leatherworker's",
                    "minecraft:leatherworker".equals(PoiLocate.typeOf(Blocks.CAULDRON.getDefaultState()))
                    && "minecraft:leatherworker".equals(PoiLocate.typeOf(Blocks.CAULDRON.getDefaultState().withProperty(BlockCauldron.LEVEL, 3))));
            check("the brewing stand is the cleric's", "minecraft:cleric".equals(PoiLocate.typeOf(Blocks.BREWING_STAND.getDefaultState())));
            check("1.12.2's portal is minecraft:nether_portal", "minecraft:nether_portal".equals(PoiLocate.typeOf(Blocks.PORTAL.getDefaultState())));
            check("stone is no point of interest", PoiLocate.typeOf(Blocks.STONE.getDefaultState()) == null);
            check("the family's blocks by today's names", "minecraft:meeting".equals(PoiLocate.typeOf(named("villagerbackport:bell")))
                    && "minecraft:armorer".equals(PoiLocate.typeOf(named("villagerbackport:blast_furnace")))
                    && "minecraft:toolsmith".equals(PoiLocate.typeOf(named("netherupdatebackport:smithing_table")))
                    && "minecraft:bee_nest".equals(PoiLocate.typeOf(named("buzzybeesbackport:bee_nest")))
                    && "minecraft:lodestone".equals(PoiLocate.typeOf(named("netherupdatebackport:lodestone")))
                    && "minecraft:lightning_rod".equals(PoiLocate.typeOf(named("cavesandcliffsbackport:lightning_rod")))
                    && "minecraft:lightning_rod".equals(PoiLocate.typeOf(named("trickytrialsbackport:waxed_oxidized_lightning_rod")))
                    && "minecraft:leatherworker".equals(PoiLocate.typeOf(named("cavesandcliffsbackport:powder_snow_cauldron"))));
            check("another mod's block of the same name is not", PoiLocate.typeOf(named("othermod:bell")) == null
                    && PoiLocate.typeOf(named("villagerbackport:portal")) == null);
            check("a registered type is known and matches", PoiLocate.known(MARKER)
                    && MARKER.equals(PoiLocate.typeOf(Blocks.BARRIER.getDefaultState())));
            check("ids are lowercased with minecraft: assumed", PoiLocate.known("Home") && PoiLocate.known("#Village")
                    && !PoiLocate.known("minecraft:nothing"));
        });

        then(1, "locate poi search and messages", world -> {
            BlockPos spawn = world.getSpawnPoint();
            BlockPos from = new BlockPos(spawn.getX(), 220, spawn.getZ());
            LocateChecks.generateAround(world, from.getX() >> 4, from.getZ() >> 4, 17);
            BlockPos stand = from.add(2, 0, 1);
            BlockPos marker = from.add(-7, 3, 4);
            BlockPos farther = from.add(40, 0, 0);
            world.setBlockState(stand, Blocks.BREWING_STAND.getDefaultState());
            world.setBlockState(marker, Blocks.BARRIER.getDefaultState());
            world.setBlockState(farther, Blocks.BARRIER.getDefaultState());
            BlockPos head = from.add(0, 0, -3);
            world.setBlockState(head, Blocks.BED.getDefaultState().withProperty(BlockBed.FACING, EnumFacing.NORTH)
                    .withProperty(BlockBed.PART, BlockBed.EnumPartType.HEAD));
            world.setBlockState(head.south(), Blocks.BED.getDefaultState().withProperty(BlockBed.FACING, EnumFacing.NORTH)
                    .withProperty(BlockBed.PART, BlockBed.EnumPartType.FOOT));
            try {
                PoiLocate.Found found = PoiLocate.nearest(world, from, "minecraft:cleric");
                check("the brewing stand placed is found", found != null && found.pos.equals(stand), found);
                found = PoiLocate.nearest(world, from, MARKER);
                check("the nearer marker of two", found != null && found.pos.equals(marker), found);
                found = PoiLocate.nearest(world, from, "minecraft:home");
                check("a bed is found at its head", found != null && found.pos.equals(head), found);

                List<String> said = locate(world, from, "minecraft:cleric");
                check("today's success message", said.equals(Arrays.asList(expected("minecraft:cleric", from, stand))), said);
                said = locate(world, from, "cleric");
                check("a bare path is minecraft:'s", said.equals(Arrays.asList(expected("minecraft:cleric", from, stand))), said);
                said = locate(world, from, "#minecraft:acquirable_job_site");
                check("a tag names the type it found", said.equals(Arrays.asList(
                        expected("#minecraft:acquirable_job_site (minecraft:cleric)", from, stand))), said);
                said = locate(world, from, MARKERS);
                check("a tag's nearest member wins", said.equals(Arrays.asList(
                        expected(MARKERS + " (minecraft:cleric)", from, stand))), said);
                said = locate(world, from, "#village");
                check("a nested tag reaches home", said.equals(Arrays.asList(
                        expected("#minecraft:village (minecraft:cleric)", from, stand))), said);
                said = locate(world, from, "minecraft:lodestone");
                check("today's not-found message", said.equals(Arrays.asList(
                        "Could not find a point of interest of type \"minecraft:lodestone\" within a reasonable distance")), said);
                said = locate(world, from, "minecraft:nothing");
                check("an unknown type is today's not-found element", said.equals(Arrays.asList(
                        "Can't find element 'minecraft:nothing' of type 'minecraft:point_of_interest_type'")), said);
                said = locate(world, from, "#minecraft:nothing");
                check("an unknown tag is today's not-found tag", said.equals(Arrays.asList(
                        "Can't find tag 'minecraft:nothing' of type 'minecraft:point_of_interest_type'")), said);

                // The radius: 256 on each axis and 256 in three dimensions, from the block the source stands in.
                world.setBlockToAir(stand);
                world.setBlockToAir(marker);
                world.setBlockToAir(farther);
                BlockPos edge = from.add(256, 0, 0);
                world.setBlockState(edge, Blocks.BARRIER.getDefaultState());
                found = PoiLocate.nearest(world, from, MARKER);
                check("exactly 256 blocks away is found", found != null && found.pos.equals(edge), found);
                found = PoiLocate.nearest(world, from.down(), MARKER);
                check("256 across and one down is beyond 256", found == null, found);
                world.setBlockToAir(edge);
                BlockPos corner = from.add(200, 0, 200);
                world.setBlockState(corner, Blocks.BARRIER.getDefaultState());
                found = PoiLocate.nearest(world, from, MARKER);
                check("inside the square but 282 away is not found", found == null, found);
                world.setBlockToAir(corner);
                BlockPos high = from.add(10, -200, 0);
                world.setBlockState(high, Blocks.BARRIER.getDefaultState());
                found = PoiLocate.nearest(world, from, MARKER);
                check("height counts in the distance (200 down is found)", found != null && found.pos.equals(high), found);
                said = locate(world, from, MARKER);
                check("the distance shown is horizontal", said.equals(Arrays.asList(expected(MARKER, from, high))), said);
                world.setBlockToAir(high);
                long start = System.nanoTime();
                found = PoiLocate.nearest(world, from, "#minecraft:village");
                long ms = (System.nanoTime() - start) / 1000000L;
                check("a #minecraft:village search over 35 x 35 loaded chunks (" + ms + " ms, " + found + ")", ms < 5000, ms);
            } finally {
                world.setBlockToAir(stand);
                world.setBlockToAir(marker);
                world.setBlockToAir(farther);
                world.setBlockToAir(head.south());
                world.setBlockToAir(head);
            }
        });

        then(1, "locate poi saved chunks", world -> {
            BlockPos spawn = world.getSpawnPoint();
            farX = (spawn.getX() >> 4) + 250;
            farZ = (spawn.getZ() >> 4) + 250;
            // The whole search square around it is generated, then unloaded: every chunk is read from its save.
            LocateChecks.generateAround(world, farX, farZ, 18);
            farPos = new BlockPos((farX << 4) + 5, 230, (farZ << 4) + 9);
            world.setBlockState(farPos, Blocks.BARRIER.getDefaultState());
            unloadFar(world);
        });
        // A neighbor's lighting or ticking asks for a chunk and so calls off its unload; asked again until all are gone.
        for (int i = 0; i < 10; i++) {
            then(10, "locate poi saved chunks: unloading", PoiLocateTests::unloadFar);
        }

        then(10, "locate poi saved chunks: unloaded", world -> {
            int still = 0;
            for (Chunk chunk : world.getChunkProvider().getLoadedChunks()) {
                if (Math.abs(chunk.x - farX) <= 18 && Math.abs(chunk.z - farZ) <= 18) {
                    still++;
                }
            }
            check("the far square unloaded", still == 0, still);
            long start = System.nanoTime();
            PoiLocate.Found none = PoiLocate.nearest(world, farPos.add(-3, 0, 0), "minecraft:lodestone");
            long ms = (System.nanoTime() - start) / 1000000L;
            check("a search reading 35 x 35 saved chunks (" + ms + " ms)", none == null && ms < 20000, ms);
            start = System.nanoTime();
            none = PoiLocate.nearest(world, farPos.add(-3, 0, 0), "minecraft:lodestone");
            ms = (System.nanoTime() - start) / 1000000L;
            check("again, from what was read (" + ms + " ms)", none == null && ms < 1000, ms);
            boolean unloaded = world.getChunkProvider().loadedChunks.get(net.minecraft.util.math.ChunkPos.asLong(farX, farZ)) == null;
            check("the far chunk unloaded", unloaded);
            BlockPos from = farPos.add(-100, -10, 30);
            PoiLocate.Found found = PoiLocate.nearest(world, from, MARKER);
            check("a point of interest in an unloaded chunk is read from its save", found != null && found.pos.equals(farPos), found);
            List<String> said = locate(world, from, MARKER);
            check("and /locate says so", said.equals(Arrays.asList(expected(MARKER, from, farPos))), said);
            check("reading it loaded nothing", world.getChunkProvider().loadedChunks.get(
                    net.minecraft.util.math.ChunkPos.asLong(farX, farZ)) == null);
            // Loading the chunk forgets what was read; a change after it is seen.
            world.getChunk(farX, farZ);
            world.setBlockToAir(farPos);
            found = PoiLocate.nearest(world, from, MARKER);
            check("once loaded, the chunk itself is read (the marker gone)", found == null, found);
        });

        then(1, "locate poi completion", world -> {
            MinecraftServer server = server();
            BlockPos spawn = world.getSpawnPoint();
            check("completion offers poi", LocateChecks.complete(server, world, spawn, "locate ").contains("poi"),
                    LocateChecks.complete(server, world, spawn, "locate "));
            check("an empty word completes every type", LocateChecks.complete(server, world, spawn, "locate poi ").equals(PoiLocate.ids()),
                    LocateChecks.complete(server, world, spawn, "locate poi "));
            check("# completes tags only", LocateChecks.complete(server, world, spawn, "locate poi #").equals(PoiLocate.tags()),
                    LocateChecks.complete(server, world, spawn, "locate poi #"));
            check("completion of lightning", LocateChecks.complete(server, world, spawn, "locate poi lightning")
                    .equals(Arrays.asList("minecraft:lightning_rod")), LocateChecks.complete(server, world, spawn, "locate poi lightning"));
            check("completion after a '_' (nest -> bee_nest)", LocateChecks.complete(server, world, spawn, "locate poi nest")
                    .equals(Arrays.asList("minecraft:bee_nest")), LocateChecks.complete(server, world, spawn, "locate poi nest"));
            check("#v completes #minecraft:village", LocateChecks.complete(server, world, spawn, "locate poi #v")
                    .equals(Arrays.asList("#minecraft:village")), LocateChecks.complete(server, world, spawn, "locate poi #v"));
        });
    }
}

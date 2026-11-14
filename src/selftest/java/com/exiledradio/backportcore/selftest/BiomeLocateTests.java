package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.locate.BiomeLocate;
import com.exiledradio.backportcore.locate.LocateChecks;

import net.minecraft.init.Biomes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/** Core's {@code /locate biome}: today's ids for 1.12.2's biomes, 26.3's search order, the command and its completion. */
final class BiomeLocateTests {

    static final String WET = "#backportcore_selftest:wet";

    private BiomeLocateTests() {
    }

    static void init() {
        BiomeLocate.tag(WET, "minecraft:river", "minecraft:swamp");
        BiomeLocate.tag("#backportcore_selftest:empty", "nothing:here");
    }

    private static MinecraftServer server() {
        return FMLCommonHandler.instance().getMinecraftServerInstance();
    }

    /** A biome made for the test, never registered: only its name matters to the ids. */
    private static Biome named(String name) {
        Biome biome = new Biome(new Biome.BiomeProperties("Test " + name)) {
        };
        biome.setRegistryName(new ResourceLocation(name));
        return biome;
    }

    static void schedule() {
        then(1, "locate biome ids", world -> {
            check("1.12.2's names are today's", BiomeLocate.id(Biomes.EXTREME_HILLS).equals("minecraft:windswept_hills")
                    && BiomeLocate.id(Biomes.SWAMPLAND).equals("minecraft:swamp")
                    && BiomeLocate.id(Biomes.ROOFED_FOREST).equals("minecraft:dark_forest")
                    && BiomeLocate.id(Biomes.MESA_ROCK).equals("minecraft:wooded_badlands"),
                    BiomeLocate.id(Biomes.EXTREME_HILLS));
            check("1.18's merges: hills are their base biome", BiomeLocate.id(Biomes.DESERT_HILLS).equals("minecraft:desert")
                    && BiomeLocate.id(Biomes.MUTATED_TAIGA).equals("minecraft:taiga")
                    && BiomeLocate.id(Biomes.EXTREME_HILLS_EDGE).equals("minecraft:windswept_hills")
                    && BiomeLocate.id(Biomes.MUTATED_BIRCH_FOREST_HILLS).equals("minecraft:old_growth_birch_forest"),
                    BiomeLocate.id(Biomes.DESERT_HILLS));
            int unmapped = 0;
            for (Biome b : net.minecraftforge.fml.common.registry.ForgeRegistries.BIOMES) {
                if ("minecraft".equals(b.getRegistryName().getNamespace())
                        && !BiomeLocate.TODAY.contains(BiomeLocate.id(b).substring("minecraft:".length()))) {
                    unmapped++;
                }
            }
            check("every 1.12.2 biome has one of today's ids", unmapped == 0, unmapped);
            check("a mod's biome under today's name is today's id", BiomeLocate.id(named("backportcore:cherry_grove"))
                    .equals("minecraft:cherry_grove"));
            check("a mod's own biome keeps its name", BiomeLocate.id(named("backportcore:glade")).equals("backportcore:glade"));
            List<String> ids = BiomeLocate.ids();
            check("ids list today's names, each once", ids.contains("minecraft:windswept_hills") && !ids.contains("minecraft:extreme_hills")
                    && ids.size() == new java.util.HashSet<String>(ids).size(), ids);
            check("a tag with a member is listed, one without is not", BiomeLocate.tags().contains(WET)
                    && !BiomeLocate.tags().contains("#backportcore_selftest:empty"), BiomeLocate.tags());
        });

        then(1, "locate biome search order", world -> {
            // BlockPos.spiralAround(ZERO, 3, EAST, SOUTH) and its count at 200, from 26.3's own class.
            String expected = "0,0 1,0 1,1 0,1 -1,1 -1,0 -1,-1 0,-1 1,-1 2,-1 2,0 2,1 2,2 1,2 0,2 -1,2 -2,2 -2,1 -2,0 -2,-1 "
                    + "-2,-2 -1,-2 0,-2 1,-2 2,-2 3,-2 3,-1 3,0 3,1 3,2 3,3 2,3 1,3 0,3 -1,3 -2,3 -3,3 -3,2 -3,1 -3,0 -3,-1 "
                    + "-3,-2 -3,-3 -2,-3 -1,-3 0,-3 1,-3 2,-3 3,-3";
            StringBuilder got = new StringBuilder();
            BiomeLocate.Spiral spiral = new BiomeLocate.Spiral(3);
            while (spiral.next()) {
                got.append(got.length() == 0 ? "" : " ").append(spiral.x).append(',').append(spiral.z);
            }
            check("the spiral is 26.3's", got.toString().equals(expected), got);
            int n = 0;
            spiral = new BiomeLocate.Spiral(200);
            while (spiral.next()) {
                n++;
            }
            check("6400 blocks at 32 are 160801 columns", n == 160801, n);
            check("heights out from the origin, 26.3's Mth.outFromOrigin",
                    Arrays.equals(BiomeLocate.outFromOrigin(70, -63, 320, 64), new int[] {70, 134, 6, 198, -58, 262})
                    && Arrays.equals(BiomeLocate.outFromOrigin(64, 1, 256, 64), new int[] {64, 128, 192, 256})
                    && Arrays.equals(BiomeLocate.outFromOrigin(300, 1, 256, 64), new int[] {256, 192, 128, 64})
                    && Arrays.equals(BiomeLocate.outFromOrigin(5, 1, 256, 64), new int[] {5, 69, 133, 197}),
                    Arrays.toString(BiomeLocate.outFromOrigin(70, -63, 320, 64)));
        });

        then(1, "locate biome command", world -> {
            MinecraftServer server = server();
            BlockPos spawn = world.getSpawnPoint();
            BlockPos from = new BlockPos(spawn.getX(), 70, spawn.getZ());
            BiomeLocate.Found found = BiomeLocate.nearest(world, from, "minecraft:ocean");
            check("an ocean is found", found != null);
            if (found != null) {
                Biome[] at = world.getBiomeProvider().getBiomesForGeneration(null, found.pos.getX() >> 2, found.pos.getZ() >> 2, 1, 1);
                check("the ocean found is ocean in the 1:4 map", BiomeLocate.id(at[0]).equals("minecraft:ocean"), at[0].getRegistryName());
                check("at the origin's height (1.12.2's biomes have none)", found.pos.getY() == 70, found.pos);
                check("on the 32-block lattice around the origin", Math.floorMod(found.pos.getX() - from.getX(), 32) == 0
                        && Math.floorMod(found.pos.getZ() - from.getZ(), 32) == 0, found.pos);
                // Nothing on an earlier column of the spiral.
                BiomeLocate.Spiral spiral = new BiomeLocate.Spiral(200);
                boolean earlier = false;
                while (spiral.next()) {
                    int x = from.getX() + spiral.x * 32;
                    int z = from.getZ() + spiral.z * 32;
                    if (x == found.pos.getX() && z == found.pos.getZ()) {
                        break;
                    }
                    Biome[] b = world.getBiomeProvider().getBiomesForGeneration(null, x >> 2, z >> 2, 1, 1);
                    earlier |= BiomeLocate.id(b[0]).equals("minecraft:ocean");
                }
                check("no ocean on an earlier column", !earlier);
                List<String> said = LocateChecks.run(server, world, from, "locate biome ocean");
                double dx = found.pos.getX() - from.getX();
                double dz = found.pos.getZ() - from.getZ();
                int distance = (int) Math.floor(Math.sqrt((float) (dx * dx + dz * dz)));
                String expected = "The nearest minecraft:ocean is at [" + found.pos.getX() + ", 70, " + found.pos.getZ() + "] ("
                        + distance + " blocks away)";
                check("today's success message, with the height", said.size() == 1 && said.get(0).equals(expected), said + " vs " + expected);
            }
            List<String> said = LocateChecks.run(server, world, from, "locate biome " + WET);
            check("a tag names the member it found", said.size() == 1 && (said.get(0).startsWith("The nearest " + WET + " (minecraft:river) is at [")
                    || said.get(0).startsWith("The nearest " + WET + " (minecraft:swamp) is at [")), said);
            said = LocateChecks.run(server, world, from, "locate biome nothing:here");
            check("an unknown id is today's not-found element", said.equals(Arrays.asList(
                    "Can't find element 'nothing:here' of type 'minecraft:worldgen/biome'")), said);
            said = LocateChecks.run(server, world, from, "locate biome #nothing:here");
            check("an unknown tag is today's not-found tag", said.equals(Arrays.asList(
                    "Can't find tag 'nothing:here' of type 'minecraft:worldgen/biome'")), said);
            said = LocateChecks.run(server, world, from, "locate biome #backportcore_selftest:empty");
            check("a tag of nothing registered is not found at once", said.equals(Arrays.asList(
                    "Could not find a biome of type \"#backportcore_selftest:empty\" within a reasonable distance")), said);
            long start = System.nanoTime();
            said = LocateChecks.run(server, world, from, "locate biome minecraft:the_end");
            long ms = (System.nanoTime() - start) / 1000000L;
            check("a biome the overworld never has: the whole spiral, then today's not-found (" + ms + " ms)", said.equals(Arrays.asList(
                    "Could not find a biome of type \"minecraft:the_end\" within a reasonable distance")), said);
            check("the whole spiral takes under 20 s", ms < 20000, ms);
        });

        then(1, "locate biome completion", world -> {
            MinecraftServer server = server();
            BlockPos spawn = world.getSpawnPoint();
            check("completion offers biome", LocateChecks.complete(server, world, spawn, "locate ").contains("biome"),
                    LocateChecks.complete(server, world, spawn, "locate "));
            check("an empty word completes every id", LocateChecks.complete(server, world, spawn, "locate biome ").equals(BiomeLocate.ids()),
                    LocateChecks.complete(server, world, spawn, "locate biome "));
            List<String> wind = new ArrayList<String>();
            for (String id : BiomeLocate.ids()) {
                if (id.startsWith("minecraft:windswept")) {
                    wind.add(id);
                }
            }
            check("completion of windswept", LocateChecks.complete(server, world, spawn, "locate biome windswept").equals(wind),
                    LocateChecks.complete(server, world, spawn, "locate biome windswept"));
            check("completion after a '_' (spikes -> ice_spikes)", LocateChecks.complete(server, world, spawn, "locate biome spikes")
                    .equals(Arrays.asList("minecraft:ice_spikes")), LocateChecks.complete(server, world, spawn, "locate biome spikes"));
            check("# completes tags only", LocateChecks.complete(server, world, spawn, "locate biome #").equals(BiomeLocate.tags()),
                    LocateChecks.complete(server, world, spawn, "locate biome #"));
            check("structure completion is unchanged", LocateChecks.complete(server, world, spawn, "locate structure mon")
                    .equals(Arrays.asList("minecraft:monument")), LocateChecks.complete(server, world, spawn, "locate structure mon"));
        });
    }
}

package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.locate.LocateChecks;
import com.exiledradio.backportcore.locate.StructureLocate;
import com.exiledradio.backportcore.structure.NetherStructureSlots;
import com.exiledradio.backportcore.structure.ScatteredPlacement;
import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.worldgen.ModernTemples;
import com.exiledradio.backportcore.worldgen.StructurePlacements;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.Arrays;
import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/** Core's /locate: the registry, today's search order, the vanilla structures, the command and its completion. */
final class LocateTests {

    static final String TEST = "backportcore_selftest:test";
    static final String GRID = "backportcore_selftest:grid";
    static final String BOTH = "#backportcore_selftest:both";

    private LocateTests() {
    }

    static void init() {
        StructureLocate.scattered("backportcore_selftest:tests", TestStructure.KIND, null, TEST);
        final ScatteredPlacement grid = new ScatteredPlacement(10, 3, 7654321);
        StructureLocate.placement("backportcore_selftest:grids", world -> world.provider.getDimension() != 0 ? null
                : new StructureLocate.Grid(grid, (x, z) -> Math.floorMod(x + z, 3) == 0 ? GRID : null), GRID);
        StructureLocate.tag(BOTH, TEST, GRID);
    }

    private static MinecraftServer server() {
        return FMLCommonHandler.instance().getMinecraftServerInstance();
    }

    static void schedule() {
        then(1, "locate registry", world -> {
            List<String> ids = StructureLocate.ids();
            check("locate lists 1.12.2's structures by today's ids", ids.containsAll(Arrays.asList("minecraft:stronghold",
                    "minecraft:monument", "minecraft:mansion", "minecraft:end_city", "minecraft:mineshaft", "minecraft:fortress")), ids);
            check("locate lists the four temples once a 1.13+ version is installed",
                    !StructurePlacements.isAtLeast("1.13") || ids.containsAll(Arrays.asList(ModernTemples.ids())), ids);
            check("locate lists registered ids and tags", ids.contains(TEST) && ids.contains(GRID)
                    && StructureLocate.tags().contains(BOTH), StructureLocate.tags());
            check("an empty village tag is searchable but not listed", StructureLocate.known("#minecraft:village")
                    && !StructureLocate.tags().contains("#minecraft:village"));
            check("ids normalize to today's form", StructureLocate.normalize("Shipwreck").equals("minecraft:shipwreck")
                    && StructureLocate.normalize("#Village").equals("#minecraft:village"));
        });

        then(1, "locate a scattered structure", world -> {
            BlockPos spawn = world.getSpawnPoint();
            StructureLocate.Found found = StructureLocate.nearest(world, spawn, TEST);
            check("locate finds the test structure", found != null && TEST.equals(found.id), found);
            if (found == null) {
                return;
            }
            check("locate's result is today's ring order", LocateChecks.checkRingOrder(world, spawn, TEST, found) == null,
                    LocateChecks.checkRingOrder(world, spawn, TEST, found));
            check("locate reports the chunk's corner", (found.pos.getX() & 15) == 0 && (found.pos.getZ() & 15) == 0, found);
            int cx = found.pos.getX() >> 4;
            int cz = found.pos.getZ() >> 4;
            LocateChecks.generateAround(world, cx, cz, 2);
            StructureStart start = LocateChecks.savedStart(ScatteredStructure.of(world, TestStructure.KIND), cx, cz);
            check("the generator laid out a start where locate said", start != null && start.isSizeableStructure(), found);
        });

        then(1, "locate a tag", world -> {
            BlockPos spawn = world.getSpawnPoint().add(37, 0, -53);
            StructureLocate.Found found = StructureLocate.nearest(world, spawn, BOTH);
            check("a tag finds one of its members", found != null && (TEST.equals(found.id) || GRID.equals(found.id)), found);
            if (found != null) {
                String why = LocateChecks.checkRingOrder(world, spawn, BOTH, found);
                check("a tag's result is today's ring order over both grids", why == null, why);
            }
            BlockPos far = new BlockPos(100000, 64, 100000);
            StructureLocate.Found none = StructureLocate.nearest(world, far, TEST);
            check("nothing within 100 rings is not found", none == null, none);
        });

        then(1, "locate temples", world -> {
            if (!StructurePlacements.isAtLeast("1.13")) {
                check("temples: 1.12.2 alone (skipped)", true);
                return;
            }
            ModernTemples temples = ModernTemples.of(world);
            check("the world's temples are Core's four grids", temples != null);
            if (temples == null) {
                return;
            }
            String[] ids = ModernTemples.ids();
            String[] pieces = {"DesertPyramid", "Igloo", "JunglePyramid", "SwampHut"};
            BlockPos from = world.getSpawnPoint();
            for (int i = 0; i < ids.length; i++) {
                StructureLocate.Found found = StructureLocate.nearest(world, from, ids[i]);
                check("locate finds " + ids[i], found != null && ids[i].equals(found.id), found);
                if (found == null) {
                    continue;
                }
                String why = LocateChecks.checkRingOrder(world, from, ids[i], found);
                check(ids[i] + " is today's ring order", why == null, why);
                int cx = found.pos.getX() >> 4;
                int cz = found.pos.getZ() >> 4;
                LocateChecks.generateAround(world, cx, cz, 0);
                StructureStart start = LocateChecks.savedStart(temples, cx, cz);
                String piece = start == null || start.getComponents().isEmpty() ? null
                        : start.getComponents().get(0).getClass().getSimpleName();
                check("the generator laid out a " + pieces[i] + " where locate said", pieces[i].equals(piece), found + " " + piece);
            }
        });

        then(1, "locate fortress and stronghold", world -> {
            WorldServer nether = server().getWorld(-1);
            nether.getChunk(0, 0);
            BlockPos from = new BlockPos(0, 64, 0);
            StructureLocate.Found found = StructureLocate.nearest(nether, from, "minecraft:fortress");
            check("locate finds a fortress in the nether", found != null, found);
            if (found != null && NetherStructureSlots.fortressRuleActive()) {
                String why = LocateChecks.checkRingOrder(nether, from, "minecraft:fortress", found);
                check("the fortress is today's ring order on the shared grid", why == null, why);
                int cx = found.pos.getX() >> 4;
                int cz = found.pos.getZ() >> 4;
                LocateChecks.generateAround(nether, cx, cz, 0);
                check("the generator laid out a fortress where locate said",
                        NetherStructureSlots.savedFortress(nether, cx, cz) != null, found);
            }
            check("no fortress in the overworld", StructureLocate.nearest(world, from, "minecraft:fortress") == null);
            StructureLocate.Found stronghold = StructureLocate.nearest(world, from, "minecraft:stronghold");
            BlockPos vanilla = world.findNearestStructure("Stronghold", from, false);
            check("minecraft:stronghold is 1.12.2's stronghold search", stronghold != null && stronghold.pos.equals(vanilla),
                    stronghold + " vs " + vanilla);
        });

        then(1, "locate command", world -> {
            MinecraftServer server = server();
            BlockPos spawn = world.getSpawnPoint();
            StructureLocate.Found found = StructureLocate.nearest(world, spawn, TEST);
            List<String> said = LocateChecks.run(server, world, spawn, "locate structure " + TEST);
            if (found != null) {
                int dx = found.pos.getX() - spawn.getX();
                int dz = found.pos.getZ() - spawn.getZ();
                int distance = (int) Math.floor(Math.sqrt((float) (dx * dx + dz * dz)));
                String expected = "The nearest " + TEST + " is at [" + found.pos.getX() + ", ~, " + found.pos.getZ() + "] ("
                        + distance + " blocks away)";
                check("today's success message", said.size() == 1 && said.get(0).equals(expected), said + " vs " + expected);
            }
            said = LocateChecks.run(server, world, spawn, "locate structure " + BOTH);
            check("a tag names the member it found", said.size() == 1 && said.get(0).startsWith("The nearest " + BOTH + " (backportcore_selftest:"), said);
            said = LocateChecks.run(server, world, spawn, "locate structure nothing:here");
            check("an unknown id is today's invalid message", said.size() == 1
                    && said.get(0).equals("There is no structure with type \"nothing:here\""), said);
            said = LocateChecks.run(server, world, new BlockPos(100000, 64, 100000), "locate structure " + TEST);
            check("nothing nearby is today's not-found message", said.size() == 1
                    && said.get(0).equals("Could not find a structure of type \"" + TEST + "\" nearby"), said);
            said = LocateChecks.run(server, world, spawn, "locate stronghold");
            check("1.12.2's names still work, any case", said.size() == 1 && said.get(0).startsWith("The nearest Stronghold is at ["), said);
            said = LocateChecks.run(server, world, spawn, "locate " + TEST);
            check("1.13's bare id still works", said.size() == 1 && said.get(0).startsWith("The nearest " + TEST + " is at ["), said);
            said = LocateChecks.run(server, world, spawn, "locate Village");
            check("Village still searches", said.size() == 1 && (said.get(0).startsWith("The nearest Village is at [")
                    || said.get(0).startsWith("Could not find")), said);
        });

        then(1, "locate tab completion", world -> {
            MinecraftServer server = server();
            BlockPos spawn = world.getSpawnPoint();
            List<String> first = LocateChecks.complete(server, world, spawn, "locate ");
            check("completion offers structure and 1.12.2's names", first.contains("structure") && first.contains("Stronghold")
                    && first.contains("Temple"), first);
            check("Village is listed while no backport registers village types", first.contains("Village"), first);
            List<String> ids = LocateChecks.complete(server, world, spawn, "locate structure ");
            check("an empty word completes every id and no tag (1.12.2's client writes the first it sorts)",
                    ids.equals(StructureLocate.ids()), ids);
            // 1.12.2's client writes the common prefix of what comes back: a namespace match beside the path matches
            // turned "m" into "minecraft:" and lost the word.
            check("completion of m keeps to paths starting with m", LocateChecks.complete(server, world, spawn, "locate structure m")
                    .equals(Arrays.asList("minecraft:mansion", "minecraft:mineshaft", "minecraft:monument")),
                    LocateChecks.complete(server, world, spawn, "locate structure m"));
            check("completion of mine is the mineshaft, not the minecraft namespace",
                    LocateChecks.complete(server, world, spawn, "locate structure mine").equals(Arrays.asList("minecraft:mineshaft")),
                    LocateChecks.complete(server, world, spawn, "locate structure mine"));
            check("completion of minecraft:m", LocateChecks.complete(server, world, spawn, "locate structure minecraft:m")
                    .equals(Arrays.asList("minecraft:mansion", "minecraft:mineshaft", "minecraft:monument")),
                    LocateChecks.complete(server, world, spawn, "locate structure minecraft:m"));
            check("completion of mon", LocateChecks.complete(server, world, spawn, "locate structure mon")
                    .equals(Arrays.asList("minecraft:monument")), LocateChecks.complete(server, world, spawn, "locate structure mon"));
            check("completion after a '_' when it is the only match (hut -> swamp_hut)", LocateChecks.complete(server, world, spawn,
                    "locate structure hut").equals(Arrays.asList("minecraft:swamp_hut")),
                    LocateChecks.complete(server, world, spawn, "locate structure hut"));
            check("no completion for several '_' matches that would leave only minecraft: (pyramid)",
                    LocateChecks.complete(server, world, spawn, "locate structure pyramid").isEmpty(),
                    LocateChecks.complete(server, world, spawn, "locate structure pyramid"));
            check("a namespace completes when no path matches", LocateChecks.complete(server, world, spawn, "locate structure backportcore")
                    .equals(Arrays.asList(GRID, TEST)), LocateChecks.complete(server, world, spawn, "locate structure backportcore"));
            check("# completes tags only", LocateChecks.complete(server, world, spawn, "locate structure #").equals(Arrays.asList(BOTH)),
                    LocateChecks.complete(server, world, spawn, "locate structure #"));
            check("a mistaken # on an id is today's invalid message", LocateChecks.run(server, world, spawn,
                    "locate structure #minecraft:stronghold").equals(Arrays.asList("There is no structure with type \"#minecraft:stronghold\"")),
                    LocateChecks.run(server, world, spawn, "locate structure #minecraft:stronghold"));
            for (String id : new String[]{"monument", "minecraft:monument", "minecraft:stronghold", "minecraft:mineshaft"}) {
                List<String> said = LocateChecks.run(server, world, spawn, "locate structure " + id);
                check("typed " + id + " locates", said.size() == 1 && said.get(0).startsWith("The nearest " + StructureLocate.normalize(id)
                        + " is at ["), said);
            }
            List<String> byPath = LocateChecks.complete(server, world, spawn, "locate structure strong");
            check("completion matches a path without its namespace", byPath.equals(Arrays.asList("minecraft:stronghold")), byPath);
            List<String> tags = LocateChecks.complete(server, world, spawn, "locate structure #backportcore_selftest:b");
            check("completion of a tag", tags.equals(Arrays.asList(BOTH)), tags);
        });
    }
}

package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.locate.LocateChecks;
import com.exiledradio.backportcore.locate.StructureLocate;
import com.exiledradio.backportcore.structure.DataPacks;
import com.exiledradio.backportcore.structure.DataPlacement;
import com.exiledradio.backportcore.structure.DataStructures;
import com.exiledradio.backportcore.structure.HeightProviders;
import com.exiledradio.backportcore.structure.JigsawPools;
import com.exiledradio.backportcore.structure.ModernTemplate;
import com.exiledradio.backportcore.structure.ScatteredPlacement;
import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.structure.Structures;
import com.exiledradio.backportcore.structure.TerrainAdaptation;

import com.google.gson.JsonParser;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.event.ForgeEventFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Data-driven structures (structure/DataStructures): the self-test's datapack in {@code data/bcst_data}
 * (tools/datastructs/make_selftest_pack.py), two drop-in packs copied into {@code config/backportcore/structures}
 * before Core reads them, and values worked out by hand from 26.3's code (tools/datastructs/oracle.py).
 */
final class DataStructureTests {

    /** The test datapack's namespace (not the self-test mod's, whose /locate completions LocateTests counts). */
    private static final String NS = "bcst_data";
    private static final String RES = "/assets/" + SelfTestMod.MOD_ID + "/dropin/";
    private static final String TOWER = NS + ":tiny_tower";
    private static final String MIX_LAND = NS + ":tiny_mix_land";
    private static final String MIX_SEA = NS + ":tiny_mix_sea";
    private static final String[] DROP_IN_FILES = {"pack.mcmeta", "data/%s/structure/tiny/base.nbt", "data/%s/structure/tiny/middle.nbt",
            "data/%s/structures/tiny/top.nbt", "data/%s/worldgen/template_pool/tiny/base.json",
            "data/%s/worldgen/template_pool/tiny/middle.json", "data/%s/worldgen/template_pool/tiny/top.json",
            "data/%s/worldgen/structure/tower.json", "data/%s/worldgen/structure_set/towers.json"};

    private static String[] blacklistBefore;

    private DataStructureTests() {
    }

    /** Before Core reads its data (load complete): the drop-in packs, and a blacklist entry. */
    static void preInit() {
        File folder = DataPacks.dropInFolder();
        try {
            // A folder pack with data/ at its root.
            File dir = new File(folder, "selftest_folder");
            for (String f : DROP_IN_FILES) {
                String rel = String.format(f, "bcst_dropin");
                File out = new File(dir, rel);
                out.getParentFile().mkdirs();
                try (InputStream in = DataStructureTests.class.getResourceAsStream(RES + "dropin_folder/" + rel)) {
                    Files.copy(in, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            // A zip with the pack inside one top folder.
            folder.mkdirs();
            try (OutputStream os = new FileOutputStream(new File(folder, "selftest_zip.zip")); ZipOutputStream zip = new ZipOutputStream(os)) {
                for (String f : DROP_IN_FILES) {
                    String rel = String.format(f, "bcst_dropzip");
                    zip.putNextEntry(new ZipEntry("selftest_zip/" + rel));
                    try (InputStream in = DataStructureTests.class.getResourceAsStream(RES + "dropin_zip/" + rel)) {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            zip.write(buf, 0, n);
                        }
                    }
                    zip.closeEntry();
                }
            }
        } catch (Exception e) {
            SelfTestRunner.fail("data structures: drop-in packs written", e);
        }
        blacklistBefore = CoreConfig.structure.blacklist;
        CoreConfig.structure.blacklist = new String[] {NS + ":blocked", "nothing_here:*"};
    }

    private static DataStructures.SetDef set(String id) {
        return DataStructures.sets().get(new ResourceLocation(id));
    }

    /** 26.3's RandomSpreadStructurePlacement.getPotentialStructureChunk, written out. */
    private static ChunkPos byHand(long seed, int chunkX, int chunkZ, int spacing, int separation, int salt) {
        int rx = Math.floorDiv(chunkX, spacing);
        int rz = Math.floorDiv(chunkZ, spacing);
        Random r = new Random(rx * 341873128712L + rz * 132897987541L + seed + salt);
        int x = r.nextInt(spacing - separation);
        int z = r.nextInt(spacing - separation);
        return new ChunkPos(rx * spacing + x, rz * spacing + z);
    }

    private static int count(WorldServer world, StructureBoundingBox box, Block block) {
        int n = 0;
        for (BlockPos p : BlockPos.getAllInBox(new BlockPos(box.minX, box.minY, box.minZ), new BlockPos(box.maxX, box.maxY, box.maxZ))) {
            if (world.getBlockState(p).getBlock() == block) {
                n++;
            }
        }
        return n;
    }

    static void schedule() {
        then(1, "data structures: loading", world -> {
            Map<ResourceLocation, DataStructures.SetDef> sets = DataStructures.sets();
            check("data structures: the jar's sets load", sets.containsKey(new ResourceLocation(NS, "tiny_towers"))
                    && sets.containsKey(new ResourceLocation(NS, "tiny_mix")), sets.keySet());
            check("data structures: a folder drop-in pack is read", sets.containsKey(new ResourceLocation("bcst_dropin", "towers"))
                    && DataPacks.dropInNames().contains("selftest_folder"), DataPacks.dropInNames());
            check("data structures: a zip drop-in pack is read (pack in a top folder)",
                    sets.containsKey(new ResourceLocation("bcst_dropzip", "towers")) && DataPacks.dropInNames().contains("selftest_zip.zip"),
                    sets.keySet());
            check("data structures: drop-in pools and templates are found",
                    JigsawPools.pool(new ResourceLocation("bcst_dropin", "tiny/base")) != null
                            && !ModernTemplate.load(JigsawPools.templateId(new ResourceLocation("bcst_dropzip", "tiny/top"))).isEmpty()
                            && !ModernTemplate.load(JigsawPools.templateId(new ResourceLocation("bcst_dropin", "tiny/middle"))).isEmpty());
            check("data structures: data/ pools, structure/ and structures/ templates",
                    JigsawPools.pool(new ResourceLocation(NS, "tiny/middle")) != null
                            && !ModernTemplate.load(new ResourceLocation(NS, "tiny/base")).isEmpty()
                            && !ModernTemplate.load(new ResourceLocation(NS, "tiny/top")).isEmpty());
            check("data structures: the blacklist leaves a set out", !sets.containsKey(new ResourceLocation(NS, "blocked"))
                    && !Structures.has(NS + ":blocked") && DataStructures.blacklisted(NS + ":blocked")
                    && DataStructures.blacklisted("nothing_here:anything") && !DataStructures.blacklisted(TOWER),
                    DataStructures.skipped());
            String skipped = DataStructures.skipped().toString();
            check("data structures: concentric_rings and hardcoded types are skipped",
                    skipped.contains(NS + ":rings: placement minecraft:concentric_rings") && skipped.contains(NS + ":hardcoded: structure type minecraft:igloo")
                            && !sets.containsKey(new ResourceLocation(NS, "rings")), skipped);
            DataStructures.SetDef towers = set(NS + ":tiny_towers");
            DataStructures.SetDef mix = set(NS + ":tiny_mix");
            check("data structures: kinds", towers.kind.equals(TOWER) && mix.kind.equals(NS + ":tiny_mix")
                    && ScatteredStructure.kinds().contains(TOWER) && Structures.has(TOWER) && Structures.has(MIX_SEA),
                    towers.kind + " " + mix.kind);
            check("data structures: /locate knows the ids", StructureLocate.ids().containsAll(Arrays.asList(TOWER, MIX_LAND, MIX_SEA)));
            DataStructures.JigsawDef tower = towers.structures.get(0);
            check("data structures: a nested biome tag resolves", tower.biomes.contains("minecraft:plains")
                    && tower.biomes.contains("minecraft:savanna") && tower.biomes.contains("minecraft:old_growth_pine_taiga")
                    && !tower.biomes.contains("minecraft:ocean"), tower.biomes);
            DataStructures.JigsawDef sea = mix.structures.get(1);
            check("data structures: 26.3's biome tags ship with Core", sea.biomes.contains("minecraft:deep_ocean")
                    && sea.biomes.contains("minecraft:warm_ocean") && !sea.biomes.contains("minecraft:river"), sea.biomes);
            check("data structures: settings read", tower.size == 3 && tower.maxHorizontal == 32 && tower.maxVertical == 32
                    && "WORLD_SURFACE_WG".equals(tower.heightmap) && tower.adaptation == TerrainAdaptation.Kind.BEARD_THIN
                    && tower.applyWaterlogging && sea.maxHorizontal == 40 && sea.maxVertical == 20 && sea.paddingBottom == 1
                    && sea.paddingTop == 0 && !sea.applyWaterlogging && tower.dimensions.equals(java.util.Collections.singleton(0)),
                    tower.dimensions);
            check("data structures: set placement read", mix.placement.frequency() == 0.75F
                    && mix.placement.reduction() == DataPlacement.FrequencyReduction.LEGACY_TYPE_3 && mix.placement.offsetX() == 3
                    && mix.placement.offsetZ() == -2 && mix.weights.equals(Arrays.asList(3, 1)) && towers.placement.grid().spacing() == 10);
            boolean adapted = false;
            for (TerrainAdaptation.Entry e : TerrainAdaptation.registered()) {
                adapted |= e.kind.equals(TOWER) && e.adaptation == TerrainAdaptation.Kind.BEARD_THIN;
            }
            check("data structures: terrain adaptation registered", adapted);
            CoreConfig.structure.blacklist = blacklistBefore == null ? new String[0] : blacklistBefore;
            check("data structures: vanilla sets for exclusion zones", DataStructures.placementOf(new ResourceLocation("minecraft:villages")) != null
                    && DataStructures.placementOf(new ResourceLocation("minecraft:villages")).grid().spacing() == 34);
        });

        then(1, "data structures: 26.3's numbers", world -> {
            long seed = 12345L;
            ScatteredPlacement linear = new ScatteredPlacement(10, 3, 20261123);
            check("data structures: random_spread candidates by hand", linear.candidate(seed, 0, 0).equals(new ChunkPos(0, 0))
                    && linear.candidate(seed, -10, 25).equals(new ChunkPos(-10, 22)) && linear.candidate(seed, 55, -21).equals(new ChunkPos(51, -28)),
                    linear.candidate(seed, -10, 25) + " " + linear.candidate(seed, 55, -21));
            ScatteredPlacement tri = new ScatteredPlacement(18, 5, 4242, true);
            check("data structures: triangular candidates by hand", tri.candidate(seed, 3, 3).equals(new ChunkPos(1, 6))
                    && tri.candidate(seed, 40, -10).equals(new ChunkPos(41, -12)), tri.candidate(seed, 40, -10));
            // 26.3's reducers at chunk 7, -3 (salt 4242): DEFAULT draws 0.7171104, LEGACY_TYPE_2 0.69703954, LEGACY_TYPE_3 0.7542976...
            DataPlacement.FrequencyReduction d = DataPlacement.FrequencyReduction.DEFAULT;
            check("data structures: frequency DEFAULT", d.shouldGenerate(seed, 4242, 7, -3, 0.72F) && !d.shouldGenerate(seed, 4242, 7, -3, 0.71F));
            DataPlacement.FrequencyReduction l2 = DataPlacement.FrequencyReduction.LEGACY_TYPE_2;
            check("data structures: frequency legacy_type_2", l2.shouldGenerate(seed, 4242, 7, -3, 0.70F) && !l2.shouldGenerate(seed, 4242, 7, -3, 0.69F));
            DataPlacement.FrequencyReduction l3 = DataPlacement.FrequencyReduction.LEGACY_TYPE_3;
            check("data structures: frequency legacy_type_3", l3.shouldGenerate(seed, 4242, 7, -3, 0.76F) && !l3.shouldGenerate(seed, 4242, 7, -3, 0.75F));
            // legacy_type_1 at p 0.2 draws nextInt(5): 3 at chunk 7, -3 and 2 at 130, 64; it generates only on 0.
            DataPlacement.FrequencyReduction l1 = DataPlacement.FrequencyReduction.LEGACY_TYPE_1;
            check("data structures: frequency legacy_type_1", !l1.shouldGenerate(seed, 0, 7, -3, 0.2F) && !l1.shouldGenerate(seed, 0, 130, 64, 0.2F)
                    && l1.shouldGenerate(seed, 0, 7, -3, 1.0F));
            HeightProviders.Context ctx = new HeightProviders.Context(0, 256, 63);
            Random r = new Random(99L);
            int[] got = new int[7];
            got[0] = HeightProviders.read(json("{\"type\":\"minecraft:uniform\",\"min_inclusive\":{\"absolute\":10},\"max_inclusive\":{\"absolute\":20}}")).sample(r, ctx);
            got[1] = HeightProviders.read(json("{\"type\":\"minecraft:biased_to_bottom\",\"min_inclusive\":{\"absolute\":5},\"max_inclusive\":{\"absolute\":40}}")).sample(r, ctx);
            got[2] = HeightProviders.read(json("{\"type\":\"minecraft:very_biased_to_bottom\",\"min_inclusive\":{\"above_bottom\":0},\"max_inclusive\":{\"absolute\":30},\"inner\":2}")).sample(r, ctx);
            got[3] = HeightProviders.read(json("{\"type\":\"minecraft:trapezoid\",\"min_inclusive\":{\"absolute\":0},\"max_inclusive\":{\"absolute\":40},\"plateau\":10}")).sample(r, ctx);
            HeightProviders.Height weighted = HeightProviders.read(json("{\"type\":\"minecraft:weighted_list\",\"distribution\":["
                    + "{\"data\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":{\"absolute\":0},\"max_inclusive\":{\"absolute\":5}},\"weight\":1},"
                    + "{\"data\":{\"absolute\":100},\"weight\":3}]}"));
            for (int i = 4; i < 7; i++) {
                got[i] = weighted.sample(r, ctx);
            }
            check("data structures: height providers draw as 26.3", Arrays.equals(got, new int[] {13, 16, 2, 29, 100, 4, 100}), Arrays.toString(got));
            check("data structures: anchors", HeightProviders.read(json("{\"below_top\":8}")).sample(r, ctx) == 247
                    && HeightProviders.read(json("{\"type\":\"minecraft:constant\",\"value\":{\"above_bottom\":5}}")).sample(r, ctx) == 5
                    && HeightProviders.read(json("{\"relative_to_sea_level\":-3}")).sample(r, ctx) == 60
                    && HeightProviders.read(json("{\"absolute\":-40}")).sample(r, ctx) == 16
                    && HeightProviders.read(json("{\"absolute\":-40}")).sample(r, new HeightProviders.Context(-64, 384, 63)) == -40);
        });

        then(1, "data structures: locate and build", world -> {
            BlockPos spawn = world.getSpawnPoint();
            StructureLocate.Found found = StructureLocate.nearest(world, spawn, TOWER);
            check("data structures: /locate finds the structure", found != null && TOWER.equals(found.id) && found.chunk != null, found);
            List<String> said = LocateChecks.run(world.getMinecraftServer(), world, spawn, "locate structure " + TOWER);
            check("data structures: the command says where", said.size() == 1 && said.get(0).startsWith("The nearest " + TOWER + " is at ["), said);
            if (found == null || found.chunk == null) {
                return;
            }
            int cx = found.chunk.x;
            int cz = found.chunk.z;
            DataStructures.SetDef towers = set(NS + ":tiny_towers");
            check("data structures: the start is on 26.3's grid (by hand)", byHand(world.getSeed(), cx, cz, 10, 3, 20261123).equals(found.chunk)
                    && found.pos.equals(new BlockPos(cx << 4, 0, cz << 4)), found.chunk);
            ScatteredStructure s = ScatteredStructure.of(world, TOWER);
            check("data structures: the world has its structure", s instanceof DataStructures.Generator);
            if (!(s instanceof DataStructures.Generator)) {
                return;
            }
            DataStructures.Generator g = (DataStructures.Generator) s;
            DataStructures.Start start = g.startAt(world, cx, cz);
            List<StructureComponent> pieces = start == null ? new ArrayList<StructureComponent>() : start.pieces();
            check("data structures: assembly makes base, middle and top", pieces.size() == 3 && TOWER.equals(start.structure()), pieces);
            if (pieces.size() != 3) {
                return;
            }
            StructureBoundingBox b0 = pieces.get(0).getBoundingBox();
            StructureBoundingBox b1 = pieces.get(1).getBoundingBox();
            StructureBoundingBox b2 = pieces.get(2).getBoundingBox();
            check("data structures: pieces stack on their jigsaws", b1.minY == b0.minY + 2 && b2.minY == b0.minY + 5
                    && b1.minX == b0.minX + 1 && b1.minZ == b0.minZ + 1 && b2.minX == b1.minX, b0 + " " + b1 + " " + b2);
            check("data structures: the start piece has its junction", ((DataStructures.Piece) pieces.get(0)).junctions().length == 3
                    && ((DataStructures.Piece) pieces.get(0)).rigid(), Arrays.toString(((DataStructures.Piece) pieces.get(0)).junctions()));
            // Saved with the world and back.
            NBTTagCompound saved = start.writeStructureComponentsToNBT(cx, cz);
            StructureStart back = MapGenStructureIO.getStructureStart(saved, world);
            check("data structures: a start saves and loads", back instanceof DataStructures.Start && "BCData".equals(saved.getString("id"))
                    && TOWER.equals(((DataStructures.Start) back).structure()) && back.getComponents().size() == 3
                    && back.getComponents().get(2).getBoundingBox().toString().equals(b2.toString())
                    && Arrays.equals(((DataStructures.Piece) back.getComponents().get(0)).junctions(), ((DataStructures.Piece) pieces.get(0)).junctions()),
                    saved.getString("id"));
            // Built from the populate step.
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    world.getChunk(cx + dx, cz + dz);
                }
            }
            StructureBoundingBox all = start.getBoundingBox();
            int lapis = count(world, all, Blocks.LAPIS_BLOCK);
            int gold = count(world, all, Blocks.GOLD_BLOCK);
            int emerald = count(world, all, Blocks.EMERALD_BLOCK);
            check("data structures: a placed chunk has the templates' blocks", lapis == 10 && gold == 3 && emerald == 1,
                    lapis + " lapis, " + gold + " gold, " + emerald + " emerald in " + all);
            check("data structures: the start is the one laid out in the world", g.laidOut(world).size() > 0
                    && ScatteredStructure.isInside(world, TOWER, new BlockPos(b0.minX + 1, b0.minY, b0.minZ + 1)));
            // spawn_overrides: monsters in the whole box are witches only; creatures in a piece none.
            BlockPos inside = new BlockPos(b0.minX + 1, b0.minY + 1, b0.minZ + 1);
            List<Biome.SpawnListEntry> monsters = ForgeEventFactory.getPotentialSpawns(world, EnumCreatureType.MONSTER, inside,
                    new ArrayList<Biome.SpawnListEntry>(world.getBiome(inside).getSpawnableList(EnumCreatureType.MONSTER)));
            List<Biome.SpawnListEntry> creatures = ForgeEventFactory.getPotentialSpawns(world, EnumCreatureType.CREATURE, inside,
                    new ArrayList<Biome.SpawnListEntry>(world.getBiome(inside).getSpawnableList(EnumCreatureType.CREATURE)));
            BlockPos outside = inside.add(64, 0, 64);
            List<Biome.SpawnListEntry> away = ForgeEventFactory.getPotentialSpawns(world, EnumCreatureType.MONSTER, outside,
                    new ArrayList<Biome.SpawnListEntry>(world.getBiome(outside).getSpawnableList(EnumCreatureType.MONSTER)));
            check("data structures: spawn_overrides", monsters != null && monsters.size() == 1 && monsters.get(0).entityClass == EntityWitch.class
                    && creatures != null && creatures.isEmpty() && away != null && away.size() > 1, monsters + " / " + away);
        });

        then(1, "data structures: weighted set", world -> {
            DataStructures.SetDef mix = set(NS + ":tiny_mix");
            ScatteredPlacement grid = mix.placement.grid();
            long seed = world.getSeed();
            int starts = 0;
            int bad = 0;
            String detail = "";
            int sx = world.getSpawnPoint().getX() >> 4;
            int sz = world.getSpawnPoint().getZ() >> 4;
            for (int rx = -3; rx <= 3; rx++) {
                for (int rz = -3; rz <= 3; rz++) {
                    ChunkPos c = grid.candidate(seed, sx + rx * 18, sz + rz * 18);
                    DataStructures.Choice choice = DataStructures.choose(mix, world, c.x, c.z);
                    if (!mix.placement.passesFrequency(seed, c.x, c.z)) {
                        if (choice != null) {
                            bad++;
                            detail += " frequency " + c;
                        }
                        continue;
                    }
                    // 26.3's first draw: setLargeFeatureSeed's nextInt(4), land below 3.
                    Random pick = com.exiledradio.backportcore.structure.StructureRandoms.start(seed, c.x, c.z);
                    DataStructures.JigsawDef first = mix.structures.get(pick.nextInt(4) < 3 ? 0 : 1);
                    DataStructures.JigsawDef other = mix.structures.get(first == mix.structures.get(0) ? 1 : 0);
                    DataStructures.Choice firstStart = DataStructures.startOf(first, world, c.x, c.z);
                    DataStructures.Choice otherStart = DataStructures.startOf(other, world, c.x, c.z);
                    DataStructures.JigsawDef expected = firstStart != null ? first : otherStart != null ? other : null;
                    if ((choice == null ? null : choice.def) != expected) {
                        bad++;
                        detail += " " + c + " " + (choice == null ? null : choice.def) + " != " + expected;
                    }
                    if (choice != null) {
                        starts++;
                    }
                }
            }
            check("data structures: a weighted set picks as 26.3", bad == 0 && starts > 0, starts + " starts" + detail);
            StructureLocate.Found land = StructureLocate.nearest(world, world.getSpawnPoint(), MIX_LAND);
            check("data structures: locate_offset", land != null && land.chunk != null
                    && land.pos.equals(new BlockPos((land.chunk.x << 4) + 3, 0, (land.chunk.z << 4) - 2)), land);
        });
    }

    private static com.google.gson.JsonElement json(String s) {
        return new JsonParser().parse(s);
    }
}

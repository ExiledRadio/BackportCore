package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.gamerules.GameRuleTable;
import com.exiledradio.backportcore.gamerules.GameRulesNetwork;
import com.exiledradio.backportcore.gamerules.NewWorldRules;
import com.exiledradio.backportcore.worldupgrade.WorldOptimizer;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.RegionFile;
import net.minecraftforge.common.util.CompoundDataFixer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import org.apache.commons.io.FileUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/** The Game Rules screen's table and server side, Create World's rules, and Optimize World's worker (Core 1.4.4). */
final class GameRulesTests {

    private static WorldOptimizer optimizer;
    private static File synthetic;
    private static File copy;

    private GameRulesTests() {
    }

    static void schedule() {
        then(1, "game rules: the table", world -> table());
        then(1, "game rules: the server's answer and its checks", GameRulesTests::server);
        then(1, "game rules: a new world's chosen rules", world -> newWorld());
        then(1, "optimize: a synthetic world", world -> startSynthetic());
        then(40, "optimize: the synthetic world, upgraded", world -> checkSynthetic(false));
        then(1, "optimize: again, erasing cached data", world -> {
            optimizer = new WorldOptimizer(synthetic, FMLCommonHandler.instance().getDataFixer(), true).start();
        });
        then(40, "optimize: the synthetic world, its cache erased", world -> checkSynthetic(true));
        then(1, "optimize: this world's height maps worked out again", GameRulesTests::realWorld);
    }

    private static void table() {
        GameRules vanilla = new GameRules();
        int known = 0;
        StringBuilder unknown = new StringBuilder();
        for (String id : vanilla.getRules()) {
            if (GameRuleTable.known(id) != null) {
                known++;
            } else {
                unknown.append(id).append(' ');
            }
        }
        check("game rules: the table knows every 1.12.2 rule but gameLoopFunction", "gameLoopFunction ".equals(unknown.toString()) && known == 23,
                known + " known; unknown: " + unknown);
        check("game rules: gameLoopFunction has no row (a function name)", GameRuleTable.numeric(vanilla, "gameLoopFunction") == null);
        check("game rules: randomTickSpeed is a number, keepInventory a boolean",
                Boolean.TRUE.equals(GameRuleTable.numeric(vanilla, "randomTickSpeed")) && Boolean.FALSE.equals(GameRuleTable.numeric(vanilla, "keepInventory")));
        boolean defaults = true;
        StringBuilder wrong = new StringBuilder();
        for (String id : vanilla.getRules()) {
            GameRuleTable.Rule r = GameRuleTable.known(id);
            if (r != null && !r.defaultValue.equals(vanilla.getString(id))) {
                defaults = false;
                wrong.append(id).append(' ');
            }
        }
        check("game rules: the table's defaults are 1.12.2's", defaults, wrong);
        GameRuleTable.Rule elytra = GameRuleTable.known("disableElytraMovementCheck");
        check("game rules: disableElytraMovementCheck shows as 26.3's elytra_movement_check, turned around",
                elytra.inverted && "gamerule.minecraft.elytra_movement_check".equals(elytra.nameKey) && "true".equals(elytra.shown("false"))
                        && "true".equals(elytra.stored("false")) && elytra.category == GameRuleTable.Category.PLAYER);
        GameRuleTable.Rule raids = GameRuleTable.known("disableRaids");
        check("game rules: Village & Pillage's disableRaids shows as 26.3's raids (Mobs)", raids.inverted && raids.category == GameRuleTable.Category.MOBS
                && Arrays.asList(raids.owners).contains("villagerbackport"));
        check("game rules: 26.3's categories and order", Arrays.toString(GameRuleTable.Category.values())
                .equals("[CHAT, DROPS, MISC, MOBS, PLAYER, SPAWNING, UPDATES]") && "gamerule.category.updates".equals(GameRuleTable.Category.UPDATES.langKey()));
        GameRuleTable.Rule tick = GameRuleTable.known("randomTickSpeed");
        GameRuleTable.Rule snow = GameRuleTable.known("snowAccumulationHeight");
        check("game rules: 26.3's bounds (random_tick_speed from 0, max_snow_accumulation_height 0 to 8)",
                tick.valid("0") && !tick.valid("-1") && !tick.valid("x") && snow.valid("8") && !snow.valid("9"));
        int family = 0;
        for (GameRuleTable.Rule r : GameRuleTable.all()) {
            if (r.owners != null) {
                family++;
            }
        }
        check("game rules: the family's 33 rules, each with its owner", family == 33, family);
        GameRuleTable.Rule other = GameRuleTable.unknown("someModRule", false, "true");
        check("game rules: another mod's rule goes under Miscellaneous by its own id", other.category == GameRuleTable.Category.MISC
                && "gamerule.someModRule".equals(other.nameKey) && !other.inverted);
    }

    private static void server(WorldServer world) {
        MinecraftServer server = world.getMinecraftServer();
        GameRulesNetwork.Values values = GameRulesNetwork.values(server);
        int keep = values.ids.indexOf("keepInventory");
        int tick = values.ids.indexOf("randomTickSpeed");
        check("game rules: the answer has keepInventory (boolean) and randomTickSpeed (number), not gameLoopFunction",
                values.allowed && keep >= 0 && !values.numeric.get(keep) && tick >= 0 && values.numeric.get(tick) && !values.ids.contains("gameLoopFunction"),
                values.ids);
        GameRules rules = world.getGameRules();
        String keepBefore = rules.getString("keepInventory");
        String tickBefore = rules.getString("randomTickSpeed");
        Map<String, String> changed = new LinkedHashMap<String, String>();
        changed.put("keepInventory", "true");
        changed.put("randomTickSpeed", "-1");
        changed.put("doFireTick", "maybe");
        changed.put("noSuchRule", "true");
        changed.put("mobGriefing", rules.getString("mobGriefing"));
        int n = GameRulesNetwork.apply(server, changed);
        check("game rules: applying sets a valid change only (no bad number, no bad boolean, no new rule, no unchanged value)",
                n == 1 && "true".equals(rules.getString("keepInventory")) && tickBefore.equals(rules.getString("randomTickSpeed"))
                        && "true".equals(rules.getString("doFireTick")) && !rules.hasRule("noSuchRule"), n);
        rules.setOrCreateGameRule("keepInventory", keepBefore);
        rules.setOrCreateGameRule("anyTypedRule", "12");
        changed.clear();
        changed.put("anyTypedRule", "13");
        changed.put("reducedDebugInfo", "true");
        n = GameRulesNetwork.apply(server, changed);
        check("game rules: a rule /gamerule made (no type) is a number by its value; reducedDebugInfo tells the players",
                n == 2 && rules.getInt("anyTypedRule") == 13, n);
        rules.setOrCreateGameRule("reducedDebugInfo", "false");
    }

    private static void newWorld() {
        GameRules rules = new GameRules();
        Map<String, String> chosen = new LinkedHashMap<String, String>();
        chosen.put("keepInventory", "true");
        chosen.put("randomTickSpeed", "9");
        chosen.put("doInsomnia", "false");
        chosen.put("maxEntityCramming", "-3");
        int n = NewWorldRules.apply(rules, chosen);
        check("game rules: a new world takes the chosen rules it has, valid ones only", n == 2 && rules.getBoolean("keepInventory")
                && rules.getInt("randomTickSpeed") == 9 && !rules.hasRule("doInsomnia") && rules.getInt("maxEntityCramming") == 24, n);
    }

    // ---- Optimize World ---------------------------------------------------------------------------------------------

    private static void startSynthetic() throws Exception {
        synthetic = new File("selftest-optimize");
        FileUtils.deleteDirectory(synthetic);
        File region = new File(synthetic, "region");
        region.mkdirs();
        new File(synthetic, "DIM-1/region").mkdirs();
        CompoundDataFixer fixer = FMLCommonHandler.instance().getDataFixer();
        RegionFile file = new RegionFile(new File(region, "r.0.0.mca"));
        write(file, chunk(0, 0, 1000, fixer));
        write(file, chunk(1, 0, WorldOptimizer.GAME_VERSION, fixer));
        file.close();
        List<WorldOptimizer.Dimension> dims = WorldOptimizer.findDimensions(synthetic);
        check("optimize: the dimensions are the overworld and the Nether", dims.size() == 2 && dims.get(0).id == 0 && dims.get(1).id == -1, dims.size());
        List<ChunkPos> chunks = WorldOptimizer.chunks(synthetic);
        check("optimize: the region file's two chunks are found", chunks.size() == 2, chunks);
        check("optimize: a 1.12.2 chunk saved by this game is up to date; DataVersion 1000 is not",
                !WorldOptimizer.outdated(chunk(1, 0, WorldOptimizer.GAME_VERSION, fixer), fixer) && WorldOptimizer.outdated(chunk(0, 0, 1000, fixer), fixer));
        optimizer = new WorldOptimizer(synthetic, fixer, false).start();
    }

    private static void checkSynthetic(boolean erased) throws Exception {
        optimizer.awaitFinished(30000L);
        check("optimize: finished" + (erased ? " (erasing)" : ""), optimizer.isFinished() && optimizer.stage() == WorldOptimizer.Stage.FINISHED, optimizer.stage());
        RegionFile file = new RegionFile(new File(synthetic, "region/r.0.0.mca"));
        NBTTagCompound old = read(file, 0, 0);
        NBTTagCompound current = read(file, 1, 0);
        file.close();
        if (!erased) {
            check("optimize: the old chunk upgraded, the current one skipped (1.13's counts)",
                    optimizer.total() == 2 && optimizer.converted() == 1 && optimizer.skipped() == 1,
                    optimizer.converted() + "/" + optimizer.skipped() + "/" + optimizer.total());
            check("optimize: the upgraded chunk is saved at 1343 with Forge's mod versions", old.getInteger("DataVersion") == 1343
                    && old.hasKey("ForgeDataVersion") && current.getCompoundTag("Level").getBoolean("LightPopulated"));
            return;
        }
        check("optimize: Erase Cached Data writes every chunk", optimizer.converted() == 2 && optimizer.skipped() == 0, optimizer.converted());
        NBTTagCompound level = current.getCompoundTag("Level");
        int[] map = level.getIntArray("HeightMap");
        check("optimize: the light is dropped (LightPopulated false) and the height map worked out again (stone to 16, leaves at 50, glass lets light through)",
                !level.getBoolean("LightPopulated") && map.length == 256 && map[0] == 16 && map[4 << 4 | 4] == 51 && map[3 << 4 | 2] == 16,
                map.length == 256 ? map[0] + "/" + map[4 << 4 | 4] + "/" + map[3 << 4 | 2] : "length " + map.length);
        FileUtils.deleteDirectory(synthetic);
    }

    /** A chunk: stone in the bottom section, a leaves block at (4, 50, 4), glass at (2, 53, 3), a wrong old height map. */
    private static NBTTagCompound chunk(int x, int z, int version, CompoundDataFixer fixer) {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound level = new NBTTagCompound();
        level.setInteger("xPos", x);
        level.setInteger("zPos", z);
        level.setBoolean("LightPopulated", true);
        level.setBoolean("TerrainPopulated", true);
        level.setIntArray("HeightMap", new int[256]);
        NBTTagList sections = new NBTTagList();
        sections.appendTag(section(0, null));
        sections.appendTag(section(3, new int[][] {{4, 50 - 48, 4, 18}, {2, 53 - 48, 3, 20}}));
        level.setTag("Sections", sections);
        root.setTag("Level", level);
        if (version >= 0) {
            root.setInteger("DataVersion", version);
        }
        if (version >= WorldOptimizer.GAME_VERSION) {
            fixer.writeVersionData(root);
        }
        return root;
    }

    private static NBTTagCompound section(int y, int[][] blocks) {
        NBTTagCompound s = new NBTTagCompound();
        s.setByte("Y", (byte) y);
        byte[] ids = new byte[4096];
        if (blocks == null) {
            Arrays.fill(ids, (byte) 1);
        } else {
            for (int[] b : blocks) {
                ids[b[1] << 8 | b[2] << 4 | b[0]] = (byte) b[3];
            }
        }
        s.setByteArray("Blocks", ids);
        s.setByteArray("Data", new NibbleArray().getData());
        s.setByteArray("BlockLight", new NibbleArray().getData());
        s.setByteArray("SkyLight", new NibbleArray().getData());
        return s;
    }

    private static void write(RegionFile file, NBTTagCompound tag) throws Exception {
        NBTTagCompound level = tag.getCompoundTag("Level");
        try (DataOutputStream out = file.getChunkDataOutputStream(level.getInteger("xPos") & 31, level.getInteger("zPos") & 31)) {
            CompressedStreamTools.write(tag, out);
        }
    }

    private static NBTTagCompound read(RegionFile file, int x, int z) throws Exception {
        try (DataInputStream in = file.getChunkDataInputStream(x, z)) {
            return CompressedStreamTools.read(in);
        }
    }

    /** This world's own chunks: the height map worked out from the blocks is the one the game saved. */
    private static void realWorld(WorldServer world) throws Exception {
        world.saveAllChunks(true, null);
        world.getChunkProvider().flushToDisk();
        net.minecraft.world.chunk.storage.RegionFileCache.clearRegionFileReferences();
        File dir = world.getSaveHandler().getWorldDirectory();
        copy = new File("selftest-optimize-copy");
        FileUtils.deleteDirectory(copy);
        FileUtils.copyDirectory(new File(dir, "region"), new File(copy, "region"));
        Map<Integer, net.minecraft.block.Block> ids = WorldOptimizer.worldBlockIds(dir);
        int same = 0;
        int all = 0;
        String first = "";
        for (ChunkPos pos : WorldOptimizer.chunks(copy)) {
            RegionFile file = new RegionFile(new File(copy, "region/r." + (pos.x >> 5) + "." + (pos.z >> 5) + ".mca"));
            NBTTagCompound level = read(file, pos.x & 31, pos.z & 31).getCompoundTag("Level");
            file.close();
            if (!level.getBoolean("TerrainPopulated")) {
                continue;
            }
            all++;
            int[] saved = level.getIntArray("HeightMap");
            int[] worked = WorldOptimizer.heightMap(level, ids);
            if (Arrays.equals(saved, worked)) {
                same++;
            } else {
                for (int i = 0; i < 256; i++) {
                    if (saved[i] != worked[i]) {
                        int bx = pos.x * 16 + (i & 15);
                        int bz = pos.z * 16 + (i >> 4);
                        String column = bx + "," + bz + " saved " + saved[i] + " worked " + worked[i] + " blocks at those heights "
                                + world.getBlockState(new net.minecraft.util.math.BlockPos(bx, saved[i] - 1, bz)) + " / "
                                + world.getBlockState(new net.minecraft.util.math.BlockPos(bx, worked[i] - 1, bz));
                        BackportCore.LOGGER.info("SELFTEST height map differs: {}", column);
                        if (first.isEmpty()) {
                            first = pos + " " + column;
                        }
                        break;
                    }
                }
            }
        }
        check("optimize: level.dat's block ids are read", ids != null && ids.size() > 200, ids == null ? "none" : ids.size());
        // After the other tests have built in the world a few columns differ (logged): the self-test mod's own blocks,
        // and a structure's fence the saved map still counts as stopping light. 1.12.2's own generateHeightMap agrees
        // with the worked-out map there; a fresh world (-PselftestOnly=gamerules) matches in every chunk.
        check("optimize: the height map worked out from a saved chunk's blocks is the game's own (populated chunks, 99%)", all > 50 && same * 100 >= all * 99,
                same + " of " + all + (first.isEmpty() ? "" : ", first different " + first));
        FileUtils.deleteDirectory(copy);
    }
}

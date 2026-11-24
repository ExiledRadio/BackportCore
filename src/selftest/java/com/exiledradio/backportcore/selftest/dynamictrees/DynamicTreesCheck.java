package com.exiledradio.backportcore.selftest.dynamictrees;

import com.exiledradio.backportcore.compat.DynamicTrees;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Dynamic Trees against the backports in a real game: a mod for a real server (the jar
 * {@code gradlew dynamicTreesCheckJar reobfDynamicTreesCheckJar -Pselftest} builds), beside Core, the backports and
 * Dynamic Trees, which needs Forge 14.23.5.2859 or later and so never loads in the 2847 dev environment.
 *
 * <p>With a plan file {@value #TRIGGER} in the server's folder (or {@code -Dbackportcore.dtcheck=<file>}) it runs each
 * line, writes {@value #RESULT} and stops the server:
 * <ul>
 *   <li>{@code api}: Core's {@link DynamicTrees} answers with Dynamic Trees installed (PASS/FAIL lines).</li>
 *   <li>{@code site <name> biome <id> <radius>}, {@code site <name> structure <name> <x> <z> <radius>},
 *       {@code site <name> at <x> <z> <radius>}: generates the chunks around the first spot of that biome (a 64-block
 *       spiral from 0, 0), the nearest structure or the spot, and counts the standing static trees (a log trunk on
 *       soil under static leaves) and Dynamic Trees' trees (its branch on its rooty soil) per biome. A static tree of
 *       vanilla wood within 6 blocks of a dynamic one, outside a structure, FAILs the site: both kinds of the same
 *       tree side by side. Fallen trees, stumps, posts and bushes (Dynamic Trees' jungle undergrowth) are only
 *       counted.</li>
 *   <li>{@code dump <x> <y> <z>}: the blocks around a spot.</li>
 * </ul>
 */
@Mod(modid = "backportcore_dtcheck", name = "Backport Core Dynamic Trees check", version = "1",
        dependencies = "required-after:backportcore;required-after:dynamictrees", acceptableRemoteVersions = "*")
public final class DynamicTreesCheck {

    static final String TRIGGER = "dynamictrees-check.txt";
    static final String RESULT = "dynamictrees-check-result.txt";
    private WorldServer world;
    private int pass;
    private int fail;

    private PrintWriter out;

    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) throws Exception {
        String plan = System.getProperty("backportcore.dtcheck", TRIGGER);
        if (!new File(plan).isFile()) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        WorldServer world = DimensionManager.getWorld(0);
        this.world = world;
        out = new PrintWriter(Files.newBufferedWriter(new File(RESULT).toPath(), StandardCharsets.UTF_8));
        out.println("dynamictrees loaded: " + Loader.isModLoaded("dynamictrees") + ", Core linked: " + DynamicTrees.loaded());
        try {
            for (String line : Files.readAllLines(new File(plan).toPath(), StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] p = line.split("\\s+");
                if (p[0].equals("api")) {
                    api(world);
                } else if (p[0].equals("dump")) {
                    int x = Integer.parseInt(p[1]);
                    int y = Integer.parseInt(p[2]);
                    int z = Integer.parseInt(p[3]);
                    out.println("DUMP " + x + "," + y + "," + z + " biome " + world.getBiome(new BlockPos(x, y, z)).getRegistryName());
                    for (int dy = -2; dy <= 8; dy++) {
                        StringBuilder sb = new StringBuilder("  y" + (y + dy) + ":");
                        for (int dz = -3; dz <= 3; dz++) {
                            sb.append(" |");
                            for (int dx = -3; dx <= 3; dx++) {
                                Block b = world.getBlockState(new BlockPos(x + dx, y + dy, z + dz)).getBlock();
                                String n = b.getRegistryName() == null ? "?" : b.getRegistryName().getPath();
                                sb.append(' ').append(n.length() > 10 ? n.substring(0, 10) : n);
                            }
                        }
                        out.println(sb);
                    }
                    out.flush();
                } else if (p[0].equals("site")) {
                    String name = p[1];
                    int r = Integer.parseInt(p[p.length - 1]);
                    BlockPos center;
                    if (p[2].equals("structure")) {
                        center = world.findNearestStructure(p[3], new BlockPos(Integer.parseInt(p[4]), 64, Integer.parseInt(p[5])), false);
                        if (center == null) {
                            out.println("SITE " + name + " structure " + p[3] + " NOT FOUND");
                            out.flush();
                            continue;
                        }
                    } else if (p[2].equals("biome")) {
                        center = find(world, p[3]);
                        if (center == null) {
                            out.println("SITE " + name + " biome " + p[3] + " NOT FOUND");
                            out.flush();
                            continue;
                        }
                    } else {
                        center = new BlockPos(Integer.parseInt(p[3]), 0, Integer.parseInt(p[4]));
                    }
                    site(world, name, center, r);
                }
            }
        } catch (Throwable t) {
            fail++;
            t.printStackTrace(out);
            t.printStackTrace();
        } finally {
            out.println("DONE pass=" + pass + " fail=" + fail);
            out.close();
            server.initiateShutdown();
        }
    }

    private boolean dtCancels(Biome biome) {
        return DynamicTrees.replacesTrees(world, biome);
    }

    private void check(String name, boolean ok, Object detail) {
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        out.println((ok ? "PASS " : "FAIL ") + name + " (" + detail + ")");
        out.flush();
    }

    /** Core's answers with Dynamic Trees installed. */
    private void api(WorldServer world) {
        check("api: Core linked to Dynamic Trees", DynamicTrees.loaded(), "");
        check("api: replaces the forest's trees", DynamicTrees.replacesTrees(world, Biomes.FOREST), "");
        check("api: no answer for a missing world", !DynamicTrees.replacesTrees(null, Biomes.FOREST), "");
        BlockPos at = new BlockPos(28000, 150, 28000);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz);
            }
        }
        for (BlockPos q : BlockPos.getAllInBox(at.add(-7, -2, -7), at.add(7, 32, 7))) {
            world.setBlockState(q, q.getY() < at.getY() ? Blocks.DIRT.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
        }
        world.setBlockState(at.down(), Blocks.GRASS.getDefaultState(), 2);
        boolean replaced = DynamicTrees.replacesTrees(world, at);
        boolean grown = DynamicTrees.grow(world, at, new Random(7), "minecraft:oak_checked");
        check("api: grows a structure's oak where it replaces the biome's trees", grown == replaced,
                world.getBiome(at).getRegistryName() + " replaced " + replaced + " grown " + grown);
        if (grown) {
            Block trunk = world.getBlockState(at).getBlock();
            Block root = world.getBlockState(at.down()).getBlock();
            check("api: dynamic trunk", isDt(trunk), name(trunk));
            check("api: rooty soil", isDt(root) && name(root).contains("rooty"), name(root));
        }
        check("api: no dynamic cherry", !DynamicTrees.grow(world, at.east(6), new Random(7), "minecraft:cherry_checked"), "");
    }

    private BlockPos find(WorldServer world, String id) {
        Biome target = ForgeRegistries.BIOMES.getValue(new ResourceLocation(id));
        if (target == null) {
            return null;
        }
        int step = 64;
        for (int ring = 0; ring < 400; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int s = 0; s < 4; s++) {
                    int x;
                    int z;
                    switch (s) {
                        case 0: x = i; z = -ring; break;
                        case 1: x = i; z = ring; break;
                        case 2: x = -ring; z = i; break;
                        default: x = ring; z = i; break;
                    }
                    if ((s >= 2) && (i == -ring || i == ring)) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x * step, 0, z * step);
                    if (world.getBiomeProvider().getBiome(pos) == target) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isDt(Block block) {
        ResourceLocation n = block.getRegistryName();
        return n != null && n.getNamespace().startsWith("dynamictrees");
    }

    private static String name(Block block) {
        ResourceLocation n = block.getRegistryName();
        return n == null ? block.getClass().getName() : n.toString();
    }

    private static boolean isStaticLog(WorldServer world, BlockPos pos, IBlockState state) {
        Block b = state.getBlock();
        if (isDt(b)) {
            return false;
        }
        String path = b.getRegistryName() == null ? "" : b.getRegistryName().getPath();
        if (path.contains("stripped") || path.contains("wood") && !path.contains("log")) {
            return false;
        }
        return b.isWood(world, pos) || path.endsWith("_log") || path.equals("log") || path.equals("log2")
                || path.endsWith("_stem") && !path.contains("mushroom");
    }

    private static boolean isSoil(IBlockState state) {
        Material m = state.getMaterial();
        String path = state.getBlock().getRegistryName() == null ? "" : state.getBlock().getRegistryName().getPath();
        return m == Material.GRASS || m == Material.GROUND || path.contains("mud") || path.contains("roots")
                || path.contains("moss") || path.contains("podzol") || m == Material.SAND;
    }

    private static boolean staticLeaves(IBlockState s) {
        return s.getMaterial() == Material.LEAVES && !isDt(s.getBlock());
    }

    /** tree (standing trunk 3+ high or 2 under leaves), struct-tree (that, with built blocks around), bush, fallen, stump. */
    private static String kind(WorldServer world, BlockPos pos) {
        IBlockState s = world.getBlockState(pos);
        for (net.minecraft.block.properties.IProperty<?> p : s.getPropertyKeys()) {
            if (p.getName().equals("axis")) {
                String v = s.getValue(p).toString().toLowerCase();
                if (v.equals("x") || v.equals("z")) {
                    return "fallen";
                }
            }
        }
        int h = 0;
        BlockPos up = pos;
        while (h < 40 && isStaticLog(world, up, world.getBlockState(up))) {
            h++;
            up = up.up();
        }
        boolean leaves = false;
        for (int dy = 0; dy <= 2 && !leaves; dy++) {
            for (EnumFacing f : EnumFacing.values()) {
                if (staticLeaves(world.getBlockState(up.up(dy).offset(f))) || staticLeaves(world.getBlockState(up.up(dy)))) {
                    leaves = true;
                    break;
                }
            }
        }
        if (h == 1) {
            return leaves ? "bush" : "stump";
        }
        if (!leaves) {
            return h < 3 ? "stump" : "post";
        }
        return built(world, pos) >= 4 ? "struct-tree" : "tree";
    }

    private static int built(WorldServer world, BlockPos pos) {
        int built = 0;
        for (BlockPos q : BlockPos.getAllInBox(pos.add(-5, -2, -5), pos.add(5, 4, 5))) {
            Block b = world.getBlockState(q).getBlock();
            String n = b.getRegistryName() == null ? "" : b.getRegistryName().getPath();
            if (n.contains("planks") || n.contains("cobblestone") || n.contains("stonebrick") || n.contains("glass")
                    || n.contains("fence") || n.contains("torch") || n.contains("bed") || n.contains("wool")
                    || n.contains("hay") || n.contains("crafting") || n.contains("door") || n.contains("path")
                    || n.contains("stairs") || n.contains("slab")) {
                built++;
            }
        }
        return built;
    }

    private void site(WorldServer world, String name, BlockPos center, int r) {
        long t0 = System.currentTimeMillis();
        int ccx = center.getX() >> 4;
        int ccz = center.getZ() >> 4;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                world.getChunkProvider().provideChunk(ccx + dx, ccz + dz);
            }
        }
        // Population needs the +x/+z neighbours; touch once more so every inner chunk is populated.
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                Chunk c = world.getChunk(ccx + dx, ccz + dz);
                if (!c.isTerrainPopulated()) {
                    c.populate(world.getChunkProvider(), world.getChunkProvider().chunkGenerator);
                }
            }
        }
        Map<String, int[]> perBiome = new TreeMap<>();   // [static, dynamic, staticNearDynamic]
        Map<String, Integer> staticLogs = new TreeMap<>();
        Map<String, Integer> dynBranches = new TreeMap<>();
        List<BlockPos> statics = new ArrayList<>();
        List<String> staticBiome = new ArrayList<>();
        List<String> staticLog = new ArrayList<>();
        List<BlockPos> dynamics = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Map<String, Integer> kinds = new TreeMap<>();
        int dynInStructure = 0;
        List<String> structSamples = new ArrayList<>();
        int nests = 0;
        int nestsOnDt = 0;
        Block nest = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("buzzybeesbackport", "bee_nest"));
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                Chunk c = world.getChunk(ccx + dx, ccz + dz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int wx = c.x * 16 + x;
                        int wz = c.z * 16 + z;
                        int top = Math.min(255, c.getHeightValue(x, z) + 1);
                        IBlockState below = c.getBlockState(x, 40, z);
                        for (int y = 41; y <= top; y++) {
                            IBlockState s = c.getBlockState(x, y, z);
                            Block b = s.getBlock();
                            if (nest != null && b == nest) {
                                nests++;
                                for (EnumFacing f : EnumFacing.HORIZONTALS) {
                                    if (isDt(world.getBlockState(new BlockPos(wx, y, wz).offset(f)).getBlock())) {
                                        nestsOnDt++;
                                        break;
                                    }
                                }
                            }
                            boolean base = false;
                            boolean dyn = false;
                            String kind = "";
                            if (isDt(below.getBlock()) && name(below.getBlock()).contains("rooty") && isDt(b)) {
                                base = true;
                                dyn = true;
                            } else if (isStaticLog(world, m.setPos(wx, y, wz), s) && isSoil(below)) {
                                // Caves & Cliffs' azalea tree over a lush cave, on rooted dirt: Dynamic Trees has none.
                                kind = name(below.getBlock()).endsWith("rooted_dirt") ? "azalea" : kind(world, new BlockPos(wx, y, wz));
                                kinds.merge(kind, 1, Integer::sum);
                                base = kind.equals("tree") || kind.equals("struct-tree");
                                if (kind.equals("struct-tree") && structSamples.size() < 8) {
                                    structSamples.add(name(b) + " @" + wx + "," + y + "," + wz);
                                }
                            }
                            if (base) {
                                long key = ((long) wx << 32) ^ (wz & 0xffffffffL);
                                boolean dup = false;
                                for (int ox = -1; ox <= 0 && !dup; ox++) {
                                    for (int oz = -1; oz <= 0 && !dup; oz++) {
                                        if ((ox != 0 || oz != 0) && seen.contains(((long) (wx + ox) << 32) ^ ((wz + oz) & 0xffffffffL))) {
                                            dup = true;
                                        }
                                    }
                                }
                                seen.add(key);
                                if (!dup) {
                                    BlockPos pos = new BlockPos(wx, y, wz);
                                    Biome biome = world.getBiome(pos);
                                    String bn = String.valueOf(biome.getRegistryName()) + (dtCancels(biome) ? " [DT]" : "")
                                            + (kind.equals("struct-tree") ? " (structure)" : "");
                                    int[] a = perBiome.computeIfAbsent(bn, k -> new int[3]);
                                    if (dyn) {
                                        a[1]++;
                                        if (built(world, pos) >= 4) {
                                            dynInStructure++;
                                        }
                                        dynamics.add(pos);
                                        dynBranches.merge(name(b), 1, Integer::sum);
                                    } else {
                                        a[0]++;
                                        statics.add(pos);
                                        staticBiome.add(bn);
                                        staticLog.add(name(b));
                                        staticLogs.merge(name(b), 1, Integer::sum);
                                    }
                                }
                            }
                            below = s;
                        }
                    }
                }
            }
        }
        int near = 0;
        int nearSameWood = 0;
        List<String> samples = new ArrayList<>();
        for (int i = 0; i < statics.size(); i++) {
            BlockPos s = statics.get(i);
            for (BlockPos d : dynamics) {
                int ddx = s.getX() - d.getX();
                int ddz = s.getZ() - d.getZ();
                if (ddx * ddx + ddz * ddz <= 36) {
                    near++;
                    perBiome.get(staticBiome.get(i))[2]++;
                    String log = staticLog.get(i);
                    if (log.startsWith("minecraft:") && !staticBiome.get(i).endsWith("(structure)")) {
                        nearSameWood++;
                    }
                    if (samples.size() < 12) {
                        samples.add(log + " @" + s.getX() + "," + s.getY() + "," + s.getZ() + " in " + staticBiome.get(i)
                                + "; dynamic " + name(world.getBlockState(d).getBlock()) + " @" + d.getX() + "," + d.getY() + "," + d.getZ());
                    }
                    break;
                }
            }
        }
        out.println("SITE " + name + " center " + center.getX() + "," + center.getZ() + " r=" + r + " chunks ("
                + (System.currentTimeMillis() - t0) / 1000 + "s)");
        out.println("  static trees " + statics.size() + ", dynamic trees " + dynamics.size()
                + ", static within 6 of a dynamic " + near + " (vanilla wood, outside structures " + nearSameWood + ")"
                + ", dynamic among built blocks " + dynInStructure
                + (nest != null ? ", bee nests " + nests + " (on DT " + nestsOnDt + ")" : ""));
        for (Map.Entry<String, int[]> e : perBiome.entrySet()) {
            out.println("  biome " + e.getKey() + ": static " + e.getValue()[0] + ", dynamic " + e.getValue()[1]
                    + ", static-near-dynamic " + e.getValue()[2]);
        }
        out.println("  static log bases by kind " + kinds);
        for (String s : structSamples) {
            out.println("    struct-tree: " + s);
        }
        out.println("  static logs " + staticLogs);
        out.println("  dynamic " + dynBranches);
        for (String s : samples) {
            out.println("    near: " + s);
        }
        check("site " + name + ": no static vanilla tree beside a dynamic one", nearSameWood == 0, nearSameWood);
        out.flush();
        world.getChunkProvider().queueUnloadAll();
        for (int i = 0; i < 200; i++) {
            world.getChunkProvider().tick();
        }
    }
}

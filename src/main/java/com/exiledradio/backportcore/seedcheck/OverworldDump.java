package com.exiledradio.backportcore.seedcheck;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.structure.ScatteredStructure;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.structure.ComponentScatteredFeaturePieces;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.MapGenStronghold;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.MapGenVillage;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureOceanMonument;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraft.world.gen.structure.WoodlandMansion;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Writes what a world's overworld generation decides, in the format of Core's vanilla oracles
 * ({@code tools/vanilla-oracle/Oracle1132}, {@code Oracle1144}), so {@code compare_overworld.py} can hold every
 * backport's seed parity against vanilla's own code.
 *
 * <p>Run with {@code -Dbackportcore.seeddump=<folder>}: once the server has started, the overworld's seed is
 * written to {@code <folder>/seed_<seed>.txt}; add {@code -Dbackportcore.seeddump.stop=true} to stop the
 * server afterwards, and {@code -Dbackportcore.seeddump.center=<x>,<z>} (blocks, multiples of 64) to move the
 * biome maps' window off 0, 0. A dev tool: nothing runs without the property.
 *
 * <p>What it writes, all asked of the generators the world really uses (so every mod's changes count):
 * <ul>
 *   <li>{@code biomeid} lines: each registered biome's numeric id and registry name;</li>
 *   <li>{@code biome4}: the 1:4 biome map (the layer structures and strongholds read) over blocks
 *       -1024..1023; {@code block4}: the block-scale biome at every 4th block of the same area;</li>
 *   <li>{@code start <kind> <chunkX> <chunkZ>}: every structure start over blocks -4096..4095, asked of the
 *       chunk generator's own structure generators, Core's {@link ScatteredStructure}s and Village &amp;
 *       Pillage's outposts; temples are named by the piece their start builds;</li>
 *   <li>{@code stronghold}: all strongholds, in placement order;</li>
 *   <li>{@code spawnbiome}: the biome search at the start of spawn placement; {@code spawn}: the world's
 *       actual spawn;</li>
 *   <li>{@code slime}: the slime chunks of the biome map's area, by vanilla 1.12.2's own
 *       {@code Chunk.getRandomWithSeed}.</li>
 * </ul>
 */
public final class OverworldDump {

    public static final String PROPERTY = "backportcore.seeddump";
    static final int R = 1024;
    static final int SR = 4096;

    private OverworldDump() {
    }

    /** Server started: dump if asked. */
    public static void serverStarted(MinecraftServer server) {
        String dir = System.getProperty(PROPERTY);
        if (dir == null || dir.isEmpty()) {
            return;
        }
        WorldServer world = server.getWorld(0);
        File out = new File(dir, "seed_" + world.getSeed() + ".txt");
        out.getParentFile().mkdirs();
        long start = System.currentTimeMillis();
        try (PrintWriter w = new PrintWriter(out, "UTF-8")) {
            dump(world, w);
        } catch (Exception e) {
            BackportCore.LOGGER.error("OverworldDump failed", e);
        }
        BackportCore.LOGGER.info("OverworldDump wrote {} in {} ms", out, System.currentTimeMillis() - start);
        if (Boolean.getBoolean(PROPERTY + ".stop")) {
            server.initiateShutdown();
        }
    }

    static void dump(WorldServer world, PrintWriter w) throws Exception {
        long seed = world.getSeed();
        BiomeProvider provider = world.getBiomeProvider();
        w.println("version ours");
        w.println("seed " + seed);
        for (Biome b : Biome.REGISTRY) {
            w.println("biomeid " + Biome.getIdForBiome(b) + " " + b.getRegistryName());
        }

        int[] center = center();
        int bx = center[0] - R, bz = center[1] - R;
        int c0 = bx >> 2, d0 = bz >> 2, n = (2 * R) >> 2;
        w.println("biome4 " + c0 + " " + d0 + " " + n + " " + n);
        Biome[] cells = provider.getBiomesForGeneration(null, c0, d0, n, n);
        for (int z = 0; z < n; z++) {
            StringBuilder s = new StringBuilder();
            for (int x = 0; x < n; x++) {
                s.append(x == 0 ? "" : " ").append(Biome.getIdForBiome(cells[x + z * n]));
            }
            w.println(s);
        }
        // Block scale, in 64-block tiles (the biome provider hands out whole areas).
        int[][] block = new int[n][n];
        int tile = 64;
        for (int tz = bz; tz < bz + 2 * R; tz += tile) {
            for (int tx = bx; tx < bx + 2 * R; tx += tile) {
                Biome[] area = provider.getBiomes(null, tx, tz, tile, tile, false);
                for (int z = 0; z < tile; z += 4) {
                    for (int x = 0; x < tile; x += 4) {
                        block[(tz + z - bz) >> 2][(tx + x - bx) >> 2] = Biome.getIdForBiome(area[x + z * tile]);
                    }
                }
            }
        }
        w.println("block4 " + bx + " " + bz + " " + n + " " + n);
        for (int z = 0; z < n; z++) {
            StringBuilder s = new StringBuilder();
            for (int x = 0; x < n; x++) {
                s.append(x == 0 ? "" : " ").append(block[z][x]);
            }
            w.println(s);
        }

        Method canSpawn = ReflectionHelper.findMethod(MapGenStructure.class, "canSpawnStructureAtCoords", "func_75047_a", int.class, int.class);
        Method getStart = ReflectionHelper.findMethod(MapGenStructure.class, "getStructureStart", "func_75049_b", int.class, int.class);
        Map<String, MapGenStructure> generators = generators(world);
        MapGenStronghold stronghold = null;
        int k0 = -SR >> 4, k1 = SR >> 4;
        for (Map.Entry<String, MapGenStructure> e : generators.entrySet()) {
            MapGenStructure gen = e.getValue();
            if (gen instanceof MapGenStronghold) {
                stronghold = (MapGenStronghold) gen;
                continue;
            }
            for (int cx = k0; cx < k1; cx++) {
                for (int cz = k0; cz < k1; cz++) {
                    if (!(Boolean) canSpawn.invoke(gen, cx, cz)) {
                        continue;
                    }
                    String kind = e.getKey();
                    if (gen instanceof MapGenScatteredFeature) {
                        kind = templeKind((StructureStart) getStart.invoke(gen, cx, cz));
                    } else if (gen instanceof ScatteredStructure) {
                        String variant = ((ScatteredStructure) gen).variantAt(world, cx, cz);
                        if (variant != null) {
                            kind = kind + "_" + variant;
                        }
                    }
                    w.println("start " + kind + " " + cx + " " + cz);
                }
            }
        }

        if (stronghold != null) {
            canSpawn.invoke(stronghold, 0, 0);
            for (Field f : MapGenStronghold.class.getDeclaredFields()) {
                if (f.getType() == ChunkPos[].class && !Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    for (ChunkPos p : (ChunkPos[]) f.get(stronghold)) {
                        w.println("stronghold " + p.x + " " + p.z);
                    }
                }
            }
        }

        BlockPos search = provider.findBiomePosition(0, 0, 256, provider.getBiomesToSpawnIn(), new Random(seed));
        w.println(search == null ? "spawnbiome none" : "spawnbiome " + search.getX() + " " + search.getZ());
        BlockPos spawn = world.getSpawnPoint();
        w.println("spawn " + spawn.getX() + " " + spawn.getZ());

        k0 = -R >> 4;
        k1 = R >> 4;
        for (int cx = k0; cx < k1; cx++) {
            for (int cz = k0; cz < k1; cz++) {
                // EntitySlime.getCanSpawnHere's test, on an empty chunk object (only its position and world count).
                if (new Chunk(world, cx, cz).getRandomWithSeed(987234911L).nextInt(10) == 0) {
                    w.println("slime " + cx + " " + cz);
                }
            }
        }
    }

    /** @return the biome window's center, from {@code backportcore.seeddump.center}, else 0, 0 */
    static int[] center() {
        String c = System.getProperty(PROPERTY + ".center", "0,0");
        String[] p = c.split(",");
        return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())};
    }

    /** The structure generators the overworld really uses, by the name the oracles give their starts. */
    static Map<String, MapGenStructure> generators(WorldServer world) throws Exception {
        Map<String, MapGenStructure> out = new LinkedHashMap<String, MapGenStructure>();
        IChunkGenerator generator = world.getChunkProvider().chunkGenerator;
        for (Class<?> c = generator.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!MapGenStructure.class.isAssignableFrom(f.getType()) || Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                f.setAccessible(true);
                MapGenStructure gen = (MapGenStructure) f.get(generator);
                String name = gen instanceof MapGenVillage ? "village"
                        : gen instanceof MapGenScatteredFeature ? "temple"
                        : gen instanceof StructureOceanMonument ? "ocean_monument"
                        : gen instanceof WoodlandMansion ? "mansion"
                        : gen instanceof MapGenStronghold ? "stronghold" : null;
                if (name != null && !out.containsKey(name)) {
                    prepare(gen, world);
                    out.put(name, gen);
                }
            }
        }
        for (String kind : ScatteredStructure.kinds()) {
            ScatteredStructure s = ScatteredStructure.of(world, kind);
            if (s != null) {
                out.put(kind, s);
            }
        }
        MapGenStructure outposts = villageAndPillageOutposts(world);
        if (outposts != null && !out.containsKey("pillager_outpost")) {
            out.put("pillager_outpost", outposts);
        }
        return out;
    }

    /** A generator that has not generated anything yet has no world; give it this one. */
    private static void prepare(MapGenStructure gen, World world) throws Exception {
        Field f = ReflectionHelper.findField(net.minecraft.world.gen.MapGenBase.class, "world", "field_75039_c");
        if (f.get(gen) == null) {
            f.set(gen, world);
        }
    }

    /** Village & Pillage keeps its outposts outside the chunk generator (no Core registry in its Core version). */
    private static MapGenStructure villageAndPillageOutposts(World world) {
        try {
            Class<?> c = Class.forName("com.exiledradio.villagerbackport.village.modern.MapGenOutpost");
            Object o = c.getMethod("of", World.class).invoke(null, world);
            if (o instanceof MapGenStructure) {
                prepare((MapGenStructure) o, world);
                return (MapGenStructure) o;
            }
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            BackportCore.LOGGER.warn("OverworldDump: could not reach Village & Pillage's outposts", e);
        }
        return null;
    }

    /** 1.12.2's one temple generator: the kind is the piece its start builds. */
    static String templeKind(StructureStart start) {
        List<StructureComponent> pieces = start == null ? new ArrayList<StructureComponent>() : start.getComponents();
        for (StructureComponent p : pieces) {
            if (p instanceof ComponentScatteredFeaturePieces.DesertPyramid) {
                return "desert_pyramid";
            }
            if (p instanceof ComponentScatteredFeaturePieces.JunglePyramid) {
                return "jungle_pyramid";
            }
            if (p instanceof ComponentScatteredFeaturePieces.SwampHut) {
                return "swamp_hut";
            }
            if (p instanceof ComponentScatteredFeaturePieces.Igloo) {
                return "igloo";
            }
        }
        return "temple_empty";
    }
}

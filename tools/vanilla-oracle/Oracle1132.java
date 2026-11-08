import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.registry.IRegistry;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.provider.OverworldBiomeProvider;
import net.minecraft.world.biome.provider.OverworldBiomeProviderSettings;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.OverworldGenSettings;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.structure.OceanRuinConfig;
import net.minecraft.world.gen.feature.structure.OceanRuinStructure;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.storage.WorldInfo;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Vanilla 1.13.2's overworld answers, from Mojang's own classes, in the same format as Oracle1144.
 *
 * 1.13.2 has no official mappings. Its server jar is remapped with MCPConfig 1.13.2-20190213.203750's
 * joined.tsrg (obfuscated to SRG names, class names included), with the SRG names turned into MCP names by
 * the mcp_snapshot-20190312-1.13.2 CSVs (tsrg_mcp.py), then FART:
 *   python -I tsrg_mcp.py joined.tsrg csvdir joined-mcp.tsrg
 *   java -jar fart.jar --input server.jar --output server-mapped.jar --names joined-mcp.tsrg --ann-fix --ids-fix --src-fix --strip-sigs
 *   javac -cp server-mapped.jar -d classes Oracle1132.java
 *   java -cp "server-mapped.jar;classes" Oracle1132 out/1.13 [seeds...]
 * The names only help to write this file: what runs is Mojang's bytecode, unchanged.
 */
public final class Oracle1132 {
    static final long[] SEEDS = {42L, 12345L, -7346534567L, 8675309L, 171L, 271L};
    static final int R = 1024;
    /** Structure starts over blocks -SR..SR-1: a wider area, since structures are sparse. */
    static final int SR = 4096;

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        long[] seeds = SEEDS;
        if (args.length > 1) {
            seeds = new long[args.length - 1];
            for (int i = 1; i < args.length; i++) {
                seeds[i - 1] = Long.parseLong(args[i]);
            }
        }
        Bootstrap.register();
        writeBiomeTable(new File(out, "biomes.txt"));
        for (long seed : seeds) {
            try (PrintWriter w = new PrintWriter(new File(out, "seed_" + seed + ".txt"), "UTF-8")) {
                dump(seed, w);
            }
            System.out.println("seed " + seed + " done");
        }
    }

    static Map<String, Structure<?>> kinds() {
        Map<String, Structure<?>> m = new LinkedHashMap<>();
        m.put("village", Feature.VILLAGE);
        m.put("desert_pyramid", Feature.DESERT_PYRAMID);
        m.put("jungle_pyramid", Feature.JUNGLE_PYRAMID);
        m.put("swamp_hut", Feature.SWAMP_HUT);
        m.put("igloo", Feature.IGLOO);
        m.put("ocean_monument", Feature.OCEAN_MONUMENT);
        m.put("mansion", Feature.WOODLAND_MANSION);
        m.put("shipwreck", Feature.SHIPWRECK);
        m.put("ocean_ruin", Feature.OCEAN_RUIN);
        m.put("buried_treasure", Feature.BURIED_TREASURE);
        return m;
    }

    static void writeBiomeTable(File f) throws Exception {
        try (PrintWriter w = new PrintWriter(f, "UTF-8")) {
            for (Object o : IRegistry.BIOME) {
                Biome b = (Biome) o;
                StringBuilder s = new StringBuilder();
                for (Map.Entry<String, Structure<?>> e : kinds().entrySet()) {
                    if (b.hasStructure(e.getValue())) {
                        s.append(' ').append(e.getKey());
                    }
                }
                if (b.hasStructure(Feature.STRONGHOLD)) {
                    s.append(" stronghold");
                }
                w.println(IRegistry.BIOME.getId(b) + " " + IRegistry.BIOME.getKey(b) + s);
            }
        }
    }

    static Method hasStartAt(Structure<?> s) throws Exception {
        for (Class<?> c = s.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod("hasStartAt", IChunkGenerator.class, Random.class, int.class, int.class);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException("hasStartAt in " + s.getClass());
    }

    static void dump(long seed, PrintWriter w) throws Exception {
        WorldInfo info = new WorldInfo(new WorldSettings(seed, GameType.SURVIVAL, true, false, WorldType.DEFAULT), "oracle");
        OverworldGenSettings settings = new OverworldGenSettings();
        OverworldBiomeProvider source = new OverworldBiomeProvider(new OverworldBiomeProviderSettings().setWorldInfo(info).setGeneratorSettings(settings));
        IChunkGenerator<?> gen = (IChunkGenerator<?>) Proxy.newProxyInstance(Oracle1132.class.getClassLoader(), new Class<?>[]{IChunkGenerator.class},
                (proxy, method, a) -> {
                    switch (method.getName()) {
                        case "getSeed":
                            return seed;
                        case "getBiomeProvider":
                            return source;
                        case "getSettings":
                            return settings;
                        // AbstractChunkGenerator's own answers.
                        case "hasStructure":
                            return ((Biome) a[0]).hasStructure((Structure<?>) a[1]);
                        case "getStructureConfig":
                            return ((Biome) a[0]).getStructureConfig((Structure<?>) a[1]);
                        case "getStructureReferenceToStartMap":
                            return new Long2ObjectOpenHashMap<>();
                        default:
                            throw new UnsupportedOperationException(method.toString());
                    }
                });

        w.println("version 1.13.2");
        w.println("seed " + seed);
        // -Doracle.center=<x>,<z> (blocks, multiples of 64) moves the biome window off 0, 0, as OverworldDump's.
        String[] center = System.getProperty("oracle.center", "0,0").split(",");
        int bx = Integer.parseInt(center[0].trim()) - R, bz = Integer.parseInt(center[1].trim()) - R;
        int c0 = bx >> 2, d0 = bz >> 2, n = (2 * R) >> 2;
        Biome[] cells = source.getBiomes(c0, d0, n, n);
        w.println("biome4 " + c0 + " " + d0 + " " + n + " " + n);
        for (int z = 0; z < n; z++) {
            StringBuilder s = new StringBuilder();
            for (int x = 0; x < n; x++) {
                s.append(x == 0 ? "" : " ").append(IRegistry.BIOME.getId(cells[x + z * n]));
            }
            w.println(s);
        }
        w.println("block4 " + bx + " " + bz + " " + n + " " + n);
        for (int z = 0; z < n; z++) {
            StringBuilder s = new StringBuilder();
            for (int x = 0; x < n; x++) {
                s.append(x == 0 ? "" : " ").append(IRegistry.BIOME.getId(source.getBiome(new BlockPos(bx + 4 * x, 0, bz + 4 * z), null)));
            }
            w.println(s);
        }

        int k0 = -SR >> 4, k1 = SR >> 4;
        for (Map.Entry<String, Structure<?>> e : kinds().entrySet()) {
            Structure<?> f = e.getValue();
            Method m = hasStartAt(f);
            for (int cx = k0; cx < k1; cx++) {
                for (int cz = k0; cz < k1; cz++) {
                    if ((Boolean) m.invoke(f, gen, new SharedSeedRandom(), cx, cz)) {
                        String kind = e.getKey();
                        if (f == Feature.OCEAN_RUIN) {
                            Biome b = source.getBiome(new BlockPos((cx << 4) + 9, 0, (cz << 4) + 9), null);
                            OceanRuinConfig c = (OceanRuinConfig) b.getStructureConfig(Feature.OCEAN_RUIN);
                            kind = c.field_204031_a == OceanRuinStructure.Type.WARM ? "ocean_ruin_warm" : "ocean_ruin_cold";
                        }
                        w.println("start " + kind + " " + cx + " " + cz);
                    }
                }
            }
        }

        Structure<?> sh = Feature.STRONGHOLD;
        hasStartAt(sh).invoke(sh, gen, new SharedSeedRandom(), 0, 0);
        for (Field fd : sh.getClass().getDeclaredFields()) {
            if (fd.getType() == ChunkPos[].class) {
                fd.setAccessible(true);
                for (ChunkPos p : (ChunkPos[]) fd.get(sh)) {
                    w.println("stronghold " + p.x + " " + p.z);
                }
            }
        }

        BlockPos spawn = source.findBiomePosition(0, 0, 256, source.getBiomesToSpawnIn(), new Random(seed));
        w.println(spawn == null ? "spawnbiome none" : "spawnbiome " + spawn.getX() + " " + spawn.getZ());

        k0 = -R >> 4;
        k1 = R >> 4;
        for (int cx = k0; cx < k1; cx++) {
            for (int cz = k0; cz < k1; cz++) {
                if (SharedSeedRandom.seedSlimeChunk(cx, cz, seed, 987234911L).nextInt(10) == 0) {
                    w.println("slime " + cx + " " + cz);
                }
            }
        }
    }

    private Oracle1132() {
    }
}

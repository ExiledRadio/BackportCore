import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.LevelType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.OverworldBiomeSource;
import net.minecraft.world.level.biome.OverworldBiomeSourceSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.OverworldGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OceanRuinConfiguration;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.OceanRuinFeature;
import net.minecraft.world.level.storage.LevelData;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Vanilla 1.14.4's overworld answers, from Mojang's own classes, for tools/vanilla-oracle/compare_overworld.py.
 *
 * Server jar remapped to Mojang's names with FART (server.txt from piston-meta, --reverse):
 *   java -jar fart.jar --input server.jar --output server-mapped.jar --names server.txt --reverse --ann-fix --ids-fix --src-fix --strip-sigs
 *   javac -cp server-mapped.jar -d classes Oracle1144.java
 *   java -cp "server-mapped.jar;classes" Oracle1144 out/1.14 [seeds...]
 *
 * Writes one file per seed in the format OverworldDump writes on our side (see that class): the 1:4 biome map,
 * the block-scale biome at every 4th block, every structure start chunk by kind, the 128 strongholds, the
 * spawn biome search and the slime chunks. Biome maps and slime chunks cover blocks -1024..1023 on both axes,
 * structure starts blocks -4096..4095.
 */
public final class Oracle1144 {
    static final long[] SEEDS = {42L, 12345L, -7346534567L, 8675309L, 171L, 271L};
    /** The biome maps cover blocks -R..R-1 on both axes. */
    static final int R = 1024;
    /** Structure starts over blocks -SR..SR-1: a wider area, since structures are sparse. */
    static final int SR = 4096;

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        long[] seeds = SEEDS;
        if (args.length > 1 && !args[1].equals("scan")) {
            seeds = new long[args.length - 1];
            for (int i = 1; i < args.length; i++) {
                seeds[i - 1] = Long.parseLong(args[i]);
            }
        }
        Bootstrap.bootStrap();
        if (args.length == 3 && args[1].equals("scan")) {
            // Seeds 1..N whose biome map area holds bamboo jungle, to pick test seeds with.
            for (long seed = 1; seed <= Long.parseLong(args[2]); seed++) {
                LevelData data = new LevelData(new LevelSettings(seed, GameType.SURVIVAL, true, false, LevelType.NORMAL), "scan");
                OverworldBiomeSource source = new OverworldBiomeSource(new OverworldBiomeSourceSettings().setLevelData(data)
                        .setGeneratorSettings(new OverworldGeneratorSettings()));
                int bamboo = 0;
                for (int x = -R >> 2; x < R >> 2; x += 2) {
                    for (int z = -R >> 2; z < R >> 2; z += 2) {
                        int id = Registry.BIOME.getId(source.getNoiseBiome(x, z));
                        bamboo += id == 168 || id == 169 ? 1 : 0;
                    }
                }
                if (bamboo > 0) {
                    System.out.println("scan " + seed + " " + bamboo);
                }
            }
            return;
        }
        writeBiomeTable(new File(out, "biomes.txt"));
        for (long seed : seeds) {
            try (PrintWriter w = new PrintWriter(new File(out, "seed_" + seed + ".txt"), "UTF-8")) {
                dump(seed, w);
            }
            System.out.println("seed " + seed + " done");
        }
    }

    static Map<String, StructureFeature<?>> kinds() {
        Map<String, StructureFeature<?>> m = new LinkedHashMap<>();
        m.put("village", Feature.VILLAGE);
        m.put("pillager_outpost", Feature.PILLAGER_OUTPOST);
        m.put("desert_pyramid", Feature.DESERT_PYRAMID);
        m.put("jungle_pyramid", Feature.JUNGLE_TEMPLE);
        m.put("swamp_hut", Feature.SWAMP_HUT);
        m.put("igloo", Feature.IGLOO);
        m.put("ocean_monument", Feature.OCEAN_MONUMENT);
        m.put("mansion", Feature.WOODLAND_MANSION);
        m.put("shipwreck", Feature.SHIPWRECK);
        m.put("ocean_ruin", Feature.OCEAN_RUIN);
        m.put("buried_treasure", Feature.BURIED_TREASURE);
        return m;
    }

    /** Every biome's id, name, and the structures that may start in it. */
    static void writeBiomeTable(File f) throws Exception {
        try (PrintWriter w = new PrintWriter(f, "UTF-8")) {
            for (Biome b : Registry.BIOME) {
                StringBuilder s = new StringBuilder();
                for (Map.Entry<String, StructureFeature<?>> e : kinds().entrySet()) {
                    if (b.isValidStart(e.getValue())) {
                        s.append(' ').append(e.getKey());
                    }
                }
                if (b.isValidStart(Feature.STRONGHOLD)) {
                    s.append(" stronghold");
                }
                w.println(Registry.BIOME.getId(b) + " " + Registry.BIOME.getKey(b) + s);
            }
        }
    }

    static void dump(long seed, PrintWriter w) throws Exception {
        LevelData data = new LevelData(new LevelSettings(seed, GameType.SURVIVAL, true, false, LevelType.NORMAL), "oracle");
        OverworldGeneratorSettings settings = new OverworldGeneratorSettings();
        OverworldBiomeSource source = new OverworldBiomeSource(new OverworldBiomeSourceSettings().setLevelData(data).setGeneratorSettings(settings));
        ChunkGenerator<OverworldGeneratorSettings> gen = generator(seed, source, settings);

        w.println("version 1.14.4");
        w.println("seed " + seed);
        // -Doracle.center=<x>,<z> (blocks, multiples of 64) moves the biome window off 0, 0, as OverworldDump's.
        String[] center = System.getProperty("oracle.center", "0,0").split(",");
        int bx = Integer.parseInt(center[0].trim()) - R, bz = Integer.parseInt(center[1].trim()) - R;
        int c0 = bx >> 2, d0 = bz >> 2, n = (2 * R) >> 2;
        w.println("biome4 " + c0 + " " + d0 + " " + n + " " + n);
        for (int z = 0; z < n; z++) {
            StringBuilder s = new StringBuilder();
            for (int x = 0; x < n; x++) {
                s.append(x == 0 ? "" : " ").append(Registry.BIOME.getId(source.getNoiseBiome(c0 + x, d0 + z)));
            }
            w.println(s);
        }
        w.println("block4 " + bx + " " + bz + " " + n + " " + n);
        for (int z = 0; z < n; z++) {
            StringBuilder s = new StringBuilder();
            for (int x = 0; x < n; x++) {
                s.append(x == 0 ? "" : " ").append(Registry.BIOME.getId(source.getBiome(bx + 4 * x, bz + 4 * z)));
            }
            w.println(s);
        }

        int k0 = -SR >> 4, k1 = SR >> 4;
        for (Map.Entry<String, StructureFeature<?>> e : kinds().entrySet()) {
            StructureFeature<?> f = e.getValue();
            for (int cx = k0; cx < k1; cx++) {
                for (int cz = k0; cz < k1; cz++) {
                    if (f.isFeatureChunk(gen, new WorldgenRandom(), cx, cz)) {
                        String kind = e.getKey();
                        if (f == Feature.OCEAN_RUIN) {
                            Biome b = source.getBiome(new BlockPos((cx << 4) + 9, 0, (cz << 4) + 9));
                            OceanRuinConfiguration c = gen.getStructureConfiguration(b, Feature.OCEAN_RUIN);
                            kind = c.biomeTemp == OceanRuinFeature.Type.WARM ? "ocean_ruin_warm" : "ocean_ruin_cold";
                        }
                        w.println("start " + kind + " " + cx + " " + cz);
                    }
                }
            }
        }

        StructureFeature<?> sh = Feature.STRONGHOLD;
        sh.isFeatureChunk(gen, new WorldgenRandom(), 0, 0);
        for (Field fd : sh.getClass().getDeclaredFields()) {
            if (fd.getType() == ChunkPos[].class) {
                fd.setAccessible(true);
                for (ChunkPos p : (ChunkPos[]) fd.get(sh)) {
                    w.println("stronghold " + p.x + " " + p.z);
                }
            }
        }

        // The first step of ServerLevel.setInitialSpawn: the biome search around 0, 0.
        BlockPos spawn = source.findBiome(0, 0, 256, source.getPlayerSpawnBiomes(), new Random(seed));
        w.println(spawn == null ? "spawnbiome none" : "spawnbiome " + spawn.getX() + " " + spawn.getZ());

        k0 = -R >> 4;
        k1 = R >> 4;
        for (int cx = k0; cx < k1; cx++) {
            for (int cz = k0; cz < k1; cz++) {
                if (WorldgenRandom.seedSlimeChunk(cx, cz, seed, 987234911L).nextInt(10) == 0) {
                    w.println("slime " + cx + " " + cz);
                }
            }
        }
    }

    /** A chunk generator that answers only what structure placement asks: seed, biome source, settings. */
    static ChunkGenerator<OverworldGeneratorSettings> generator(long seed, BiomeSource source, OverworldGeneratorSettings settings) {
        LevelAccessor level = (LevelAccessor) Proxy.newProxyInstance(Oracle1144.class.getClassLoader(), new Class<?>[]{LevelAccessor.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getSeed")) {
                        return seed;
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
        return new ChunkGenerator<OverworldGeneratorSettings>(level, source, settings) {
            @Override
            public void buildSurfaceAndBedrock(ChunkAccess chunk) {
            }

            @Override
            public int getSpawnHeight() {
                return 64;
            }

            @Override
            public void fillFromNoise(LevelAccessor l, ChunkAccess chunk) {
            }

            @Override
            public int getBaseHeight(int x, int z, Heightmap.Types type) {
                return 64;
            }
        };
    }

    private Oracle1144() {
    }
}

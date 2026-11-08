import net.minecraft.core.BlockPos;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FuzzyOffsetConstantColumnBiomeZoomer;
import net.minecraft.world.level.biome.OverworldBiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OceanRuinConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RuinedPortalConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.StructureFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.OceanRuinFeature;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Vanilla 1.16.5's overworld answers, from Mojang's own classes, in Oracle1144's format, for compare_overworld.py.
 *
 * Server jar remapped to Mojang's names with FART (server.txt, --reverse):
 *   javac -cp server-mapped.jar -d classes Oracle1165OW.java
 *   java -cp "server-mapped.jar;classes" Oracle1165OW out [seeds...]      (-Doracle.largeBiomes=true for large biomes)
 *
 * What 1.16.5 does, and this asks, through the real classes:
 * - biome4: OverworldBiomeSource.getNoiseBiome (the 1:4 layer map); block4: the server's BiomeManager (hashed seed,
 *   the overworld's FuzzyOffsetConstantColumnBiomeZoomer) at every 4th block;
 * - starts: ChunkGenerator.createStructures: the noise biome at (4x + 2, 0, 4z + 2) lists the configured structures
 *   tried; each one's StructureFeature.generate asks getPotentialFeatureChunk (the overworld's StructureSettings)
 *   and isFeatureChunk. Ocean ruins are named warm/cold and ruined portals by their configured type;
 * - strongholds: ChunkGenerator.generateStrongholds (through hasStronghold), the 128 positions in order;
 * - spawnbiome: MinecraftServer.setInitialSpawn's biome search (playerSpawnFriendly biomes, Random(seed));
 * - slime chunks; and biomes.txt, every biome's structures, stronghold and spawn flags.
 */
public final class Oracle1165OW {
    static final long[] SEEDS = {42L, 12345L, -7346534567L, 8675309L, 171L, 271L};
    static final int R = 1024;
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
        Bootstrap.bootStrap();
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
        m.put("village", StructureFeature.VILLAGE);
        m.put("pillager_outpost", StructureFeature.PILLAGER_OUTPOST);
        m.put("desert_pyramid", StructureFeature.DESERT_PYRAMID);
        m.put("jungle_pyramid", StructureFeature.JUNGLE_TEMPLE);
        m.put("swamp_hut", StructureFeature.SWAMP_HUT);
        m.put("igloo", StructureFeature.IGLOO);
        m.put("ocean_monument", StructureFeature.OCEAN_MONUMENT);
        m.put("mansion", StructureFeature.WOODLAND_MANSION);
        m.put("shipwreck", StructureFeature.SHIPWRECK);
        m.put("ocean_ruin", StructureFeature.OCEAN_RUIN);
        m.put("buried_treasure", StructureFeature.BURIED_TREASURE);
        m.put("ruined_portal", StructureFeature.RUINED_PORTAL);
        return m;
    }

    static void writeBiomeTable(File f) throws Exception {
        OverworldBiomeSource source = new OverworldBiomeSource(0L, false, false, BuiltinRegistries.BIOME);
        try (PrintWriter w = new PrintWriter(f, "UTF-8")) {
            for (Biome b : BuiltinRegistries.BIOME) {
                StringBuilder s = new StringBuilder();
                for (Supplier<ConfiguredStructureFeature<?, ?>> c : b.getGenerationSettings().structures()) {
                    ConfiguredStructureFeature<?, ?> cs = c.get();
                    s.append(' ').append(name(cs));
                }
                if (b.getMobSettings().playerSpawnFriendly()) {
                    s.append(" SPAWN");
                }
                if (!source.possibleBiomes().contains(b)) {
                    s.append(" NOT_POSSIBLE");
                }
                w.println(BuiltinRegistries.BIOME.getId(b) + " " + BuiltinRegistries.BIOME.getKey(b) + s);
            }
        }
    }

    static String name(ConfiguredStructureFeature<?, ?> cs) {
        for (Map.Entry<String, StructureFeature<?>> e : kinds().entrySet()) {
            if (e.getValue() == cs.feature) {
                if (cs.config instanceof OceanRuinConfiguration) {
                    return ((OceanRuinConfiguration) cs.config).biomeTemp == OceanRuinFeature.Type.WARM ? "ocean_ruin_warm" : "ocean_ruin_cold";
                }
                if (cs.config instanceof RuinedPortalConfiguration) {
                    return "ruined_portal_" + ((RuinedPortalConfiguration) cs.config).portalType.name().toLowerCase(Locale.ROOT);
                }
                return e.getKey();
            }
        }
        return cs.feature == StructureFeature.STRONGHOLD ? "stronghold" : cs.feature.getFeatureName().toLowerCase(Locale.ROOT);
    }

    static void dump(long seed, PrintWriter w) throws Exception {
        boolean large = Boolean.getBoolean("oracle.largeBiomes");
        OverworldBiomeSource source = new OverworldBiomeSource(seed, false, large, BuiltinRegistries.BIOME);
        NoiseBasedChunkGenerator gen = new NoiseBasedChunkGenerator(source, seed,
                () -> BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD));
        BiomeManager biomes = new BiomeManager(source, BiomeManager.obfuscateSeed(seed), FuzzyOffsetConstantColumnBiomeZoomer.INSTANCE);

        w.println("version 1.16.5");
        w.println("seed " + seed);
        // -Doracle.center=<x>,<z> (blocks, multiples of 64) moves the biome window off 0, 0, as OverworldDump's.
        String[] center = System.getProperty("oracle.center", "0,0").split(",");
        int bx = Integer.parseInt(center[0].trim()) - R, bz = Integer.parseInt(center[1].trim()) - R;
        int c0 = bx >> 2, d0 = bz >> 2, n = (2 * R) >> 2;
        w.println("biome4 " + c0 + " " + d0 + " " + n + " " + n);
        for (int z = 0; z < n; z++) {
            StringBuilder s = new StringBuilder();
            for (int x = 0; x < n; x++) {
                s.append(x == 0 ? "" : " ").append(BuiltinRegistries.BIOME.getId(source.getNoiseBiome(c0 + x, 0, d0 + z)));
            }
            w.println(s);
        }
        w.println("block4 " + bx + " " + bz + " " + n + " " + n);
        for (int z = 0; z < n; z++) {
            StringBuilder s = new StringBuilder();
            for (int x = 0; x < n; x++) {
                s.append(x == 0 ? "" : " ").append(BuiltinRegistries.BIOME.getId(biomes.getBiome(new BlockPos(bx + 4 * x, 0, bz + 4 * z))));
            }
            w.println(s);
        }

        Map<StructureFeature<?>, Method> checks = new LinkedHashMap<>();
        for (StructureFeature<?> f : kinds().values()) {
            checks.put(f, featureCheck(f));
        }
        int k0 = -SR >> 4, k1 = SR >> 4;
        for (int cx = k0; cx < k1; cx++) {
            for (int cz = k0; cz < k1; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                Biome biome = source.getNoiseBiome((cx << 2) + 2, 0, (cz << 2) + 2);
                for (Supplier<ConfiguredStructureFeature<?, ?>> sup : biome.getGenerationSettings().structures()) {
                    ConfiguredStructureFeature<?, ?> cs = sup.get();
                    Method check = checks.get(cs.feature);
                    if (check == null) {
                        continue;
                    }
                    StructureFeatureConfiguration settings = gen.getSettings().getConfig(cs.feature);
                    if (settings == null) {
                        continue;
                    }
                    WorldgenRandom random = new WorldgenRandom();
                    ChunkPos candidate = cs.feature.getPotentialFeatureChunk(settings, seed, random, cx, cz);
                    if (candidate.x == cx && candidate.z == cz
                            && (Boolean) check.invoke(cs.feature, gen, source, seed, random, cx, cz, biome, candidate, cs.config)) {
                        w.println("start " + name(cs) + " " + cx + " " + cz);
                    }
                }
            }
        }

        if (Boolean.getBoolean("oracle.strongholdTrace")) {
            strongholdTrace(seed, source, w);
        }
        gen.hasStronghold(new ChunkPos(0, 0));
        Field f = net.minecraft.world.level.chunk.ChunkGenerator.class.getDeclaredField("strongholdPositions");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ChunkPos> strongholds = (List<ChunkPos>) f.get(gen);
        for (ChunkPos p : strongholds) {
            w.println("stronghold " + p.x + " " + p.z);
        }

        BlockPos spawn = source.findBiomeHorizontal(0, gen.getSeaLevel(), 0, 256, b -> b.getMobSettings().playerSpawnFriendly(), new Random(seed));
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

    /**
     * ChunkGenerator.generateStrongholds' loop, written out, with each search's centre and number of matching cells
     * ("strongholdtrace i centreX centreZ count foundX foundZ"), to find where two worlds' searches part.
     */
    static void strongholdTrace(long seed, OverworldBiomeSource source, PrintWriter w) {
        java.util.List<Biome> list = new java.util.ArrayList<>();
        for (Biome b : source.possibleBiomes()) {
            if (b.getGenerationSettings().isValidStart(StructureFeature.STRONGHOLD)) {
                list.add(b);
            }
        }
        int k1 = 32, l1 = 128, i = 3;
        Random random = new Random();
        random.setSeed(seed);
        double d0 = random.nextDouble() * Math.PI * 2.0D;
        int j = 0, k = 0;
        for (int l = 0; l < l1; ++l) {
            double d1 = (double) (4 * k1 + k1 * k * 6) + (random.nextDouble() - 0.5D) * (double) k1 * 2.5D;
            int i1 = (int) Math.round(Math.cos(d0) * d1);
            int j1 = (int) Math.round(Math.sin(d0) * d1);
            int x = (i1 << 4) + 8, z = (j1 << 4) + 8;
            int ci = x >> 2, cj = z >> 2, r = 112 >> 2, count = 0;
            BlockPos found = null;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (list.contains(source.getNoiseBiome(ci + dx, 0, cj + dz))) {
                        if (found == null || random.nextInt(count + 1) == 0) {
                            found = new BlockPos(ci + dx << 2, 0, cj + dz << 2);
                        }
                        ++count;
                    }
                }
            }
            if (found != null) {
                i1 = found.getX() >> 4;
                j1 = found.getZ() >> 4;
            }
            w.println("strongholdtrace " + l + " " + x + " " + z + " " + count + " " + i1 + " " + j1);
            d0 += (Math.PI * 2D) / (double) i;
            ++j;
            if (j == i) {
                ++k;
                j = 0;
                i = i + 2 * i / (k + 1);
                i = Math.min(i, l1 - l);
                d0 += random.nextDouble() * Math.PI * 2.0D;
            }
        }
    }

    private static Method featureCheck(StructureFeature<?> feature) {
        for (Class<?> c = feature.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("isFeatureChunk") && m.getParameterCount() == 9 && !m.isBridge()) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new IllegalStateException("no isFeatureChunk on " + feature);
    }

    private Oracle1165OW() {
    }
}

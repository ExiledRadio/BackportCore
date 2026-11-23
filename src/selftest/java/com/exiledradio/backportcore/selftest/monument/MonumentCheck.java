package com.exiledradio.backportcore.selftest.monument;

import com.exiledradio.backportcore.terrain.RtgTerrain;

import net.minecraft.init.Biomes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.StructureOceanMonument;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Where ocean monuments go in a world, against the terrain there: a mod for a real server (the jar
 * {@code gradlew monumentCheckJar -Pselftest} builds, reobfuscated), not part of Core's self-tests.
 *
 * <p>With a file {@value #TRIGGER} in the server's folder ({@code radius=<grid cells>}, {@code generate=<count>}), it
 * walks the monument grid (32/5, salt 10387313) around 0, 0 and asks the world's own monument generator about every
 * candidate ({@code canSpawnStructureAtCoords}, which generation, {@code /locate} and explorer maps all use). For
 * each it writes the biome rule's answer and the terrain under the monument from Realistic Terrain Generation's
 * landscape: the highest ground within 16 and 29 blocks of the chunk's block (9, 9). Then it checks that
 * {@code /locate} (the generator's {@code getNearestStructurePos}) only ever answers with an accepted candidate,
 * generates the chunks of the first {@code generate} accepted and biome-only candidates, and checks that a monument
 * is there exactly when the generator accepted it. Writes {@value #RESULT} and stops the server.
 */
@Mod(modid = MonumentCheck.MOD_ID, name = "Backport Core monument check", version = "1",
        dependencies = "required-after:backportcore", acceptableRemoteVersions = "*")
public final class MonumentCheck {

    static final String MOD_ID = "backportcore_monumentcheck";
    public static final String TRIGGER = "backportcore-monuments.txt";
    public static final String RESULT = "backportcore-monuments-result.txt";

    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) {
        if (FMLCommonHandler.instance().getSide().isServer() && new File(TRIGGER).isFile()) {
            MinecraftForge.EVENT_BUS.register(new Runner());
        }
    }

    public static final class Runner {
        private int ticks;
        private boolean done;

        @SubscribeEvent
        public void tick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || this.done || ++this.ticks < 20) {
                return;
            }
            this.done = true;
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            List<String> out = new ArrayList<String>();
            try {
                run(server.getWorld(0), out);
            } catch (Throwable t) {
                out.add("ERROR " + t);
                for (StackTraceElement e : t.getStackTrace()) {
                    out.add("    at " + e);
                }
            }
            try {
                Files.write(new File(RESULT).toPath(), out, StandardCharsets.UTF_8);
            } catch (Exception e) {
                e.printStackTrace();
            }
            server.initiateShutdown();
        }
    }

    static void run(WorldServer world, List<String> out) throws Exception {
        int radius = 6;
        int generate = 4;
        for (String line : Files.readAllLines(new File(TRIGGER).toPath(), StandardCharsets.UTF_8)) {
            String[] kv = line.trim().split("=");
            if (kv.length == 2 && kv[0].equals("radius")) {
                radius = Integer.parseInt(kv[1].trim());
            } else if (kv.length == 2 && kv[0].equals("generate")) {
                generate = Integer.parseInt(kv[1].trim());
            } else if (kv.length == 2 && kv[0].equals("chamberRadius")) {
                chamberRadius = Integer.parseInt(kv[1].trim());
            } else if (kv.length == 2 && kv[0].equals("chamberGenerate")) {
                chamberGenerate = Integer.parseInt(kv[1].trim());
            }
        }
        IChunkGenerator generator = world.getChunkProvider().chunkGenerator;
        StructureOceanMonument monument = null;
        for (Class<?> c = generator.getClass(); c != null && monument == null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (StructureOceanMonument.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    monument = (StructureOceanMonument) f.get(generator);
                    break;
                }
            }
        }
        out.add("seed " + world.getSeed() + " generator " + generator.getClass().getName() + " monument "
                + (monument == null ? "none" : monument.getClass().getName()));
        if (monument == null) {
            return;
        }
        // Sets the generator's world, as a search does.
        world.findNearestStructure("Monument", BlockPos.ORIGIN, false);
        Method canSpawn = ReflectionHelper.findMethod(MapGenStructure.class, "canSpawnStructureAtCoords",
                "func_75047_a", int.class, int.class);
        RtgTerrain rtg = RtgTerrain.handles(generator) ? RtgTerrain.of(world, generator) : null;

        List<Biome> deep = new ArrayList<Biome>();
        List<Biome> water = new ArrayList<Biome>();
        for (Biome b : ForgeRegistries.BIOMES) {
            boolean ocean = BiomeDictionary.hasType(b, BiomeDictionary.Type.OCEAN);
            if (ocean || BiomeDictionary.hasType(b, BiomeDictionary.Type.RIVER)) {
                water.add(b);
            }
            if (ocean && (b == Biomes.DEEP_OCEAN || b.getRegistryName().getPath().startsWith("deep_"))) {
                deep.add(b);
            }
        }

        Set<Long> accepted = new HashSet<Long>();
        List<ChunkPos> acceptedList = new ArrayList<ChunkPos>();
        List<ChunkPos> biomeOnly = new ArrayList<ChunkPos>();
        int candidates = 0, biomeOk = 0, acceptedDry = 0, acceptedShallow = 0;
        out.add("candidate chunk, biome rule, generator, center biome, highest ground within 16 / 29 blocks, dry columns within 29");
        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                Random random = world.setRandomSeed(cx, cz, 10387313);
                int x = cx * 32 + (random.nextInt(27) + random.nextInt(27)) / 2;
                int z = cz * 32 + (random.nextInt(27) + random.nextInt(27)) / 2;
                candidates++;
                int bx = x * 16 + 9, bz = z * 16 + 9;
                boolean biome = world.getBiomeProvider().areBiomesViable(bx, bz, 16, deep)
                        && world.getBiomeProvider().areBiomesViable(bx, bz, 29, water);
                boolean ok = (Boolean) canSpawn.invoke(monument, x, z);
                if (!biome && !ok) {
                    continue;
                }
                if (biome) {
                    biomeOk++;
                }
                int top16 = Integer.MIN_VALUE, top29 = Integer.MIN_VALUE, dry = 0;
                if (rtg != null) {
                    for (int dx = -29; dx <= 29; dx += 2) {
                        for (int dz = -29; dz <= 29; dz += 2) {
                            int ground = rtg.floor(bx + dx, bz + dz) - 1;
                            top29 = Math.max(top29, ground);
                            if (Math.abs(dx) <= 16 && Math.abs(dz) <= 16) {
                                top16 = Math.max(top16, ground);
                            }
                            if (ground >= world.getSeaLevel() - 1) {
                                dry++;
                            }
                        }
                    }
                }
                if (ok) {
                    accepted.add(ChunkPos.asLong(x, z));
                    acceptedList.add(new ChunkPos(x, z));
                    if (dry > 0) {
                        acceptedDry++;
                    } else if (top16 > 47) {
                        acceptedShallow++;
                    }
                } else {
                    biomeOnly.add(new ChunkPos(x, z));
                }
                out.add(String.format("  %d,%d (block %d,%d) biome=%s generator=%s %s ground16=%d ground29=%d dry=%d",
                        x, z, bx, bz, biome, ok, world.getBiomeProvider().getBiome(new BlockPos(bx, 64, bz)).getRegistryName(),
                        top16, top29, dry));
            }
        }
        out.add(String.format("SUMMARY candidates=%d biomeRule=%d accepted=%d acceptedWithLand=%d acceptedShallow=%d",
                candidates, biomeOk, accepted.size(), acceptedDry, acceptedShallow));

        // /locate from a spread of points: every answer must be a candidate the generator accepts.
        int locates = 0, locateBad = 0;
        for (int ox = -radius * 512 + 256; ox <= radius * 512; ox += 1024) {
            for (int oz = -radius * 512 + 256; oz <= radius * 512; oz += 1024) {
                BlockPos found = world.findNearestStructure("Monument", new BlockPos(ox, 64, oz), false);
                if (found == null) {
                    continue;
                }
                locates++;
                long key = ChunkPos.asLong(found.getX() >> 4, found.getZ() >> 4);
                if (!accepted.contains(key) && !(Boolean) canSpawn.invoke(monument, found.getX() >> 4, found.getZ() >> 4)) {
                    locateBad++;
                    out.add("  LOCATE MISMATCH from " + ox + "," + oz + " -> " + found);
                }
            }
        }
        out.add(String.format("LOCATE answers=%d notAccepted=%d", locates, locateBad));

        // Generation: monuments exactly where the generator said yes.
        int genChecked = 0, genBad = 0;
        List<ChunkPos> toGenerate = new ArrayList<ChunkPos>();
        for (int i = 0; i < Math.min(generate, acceptedList.size()); i++) {
            toGenerate.add(acceptedList.get(i));
        }
        for (int i = 0; i < Math.min(generate, biomeOnly.size()); i++) {
            toGenerate.add(biomeOnly.get(i));
        }
        for (ChunkPos c : toGenerate) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    world.getChunkProvider().provideChunk(c.x + dx, c.z + dz);
                }
            }
            BlockPos center = new BlockPos(c.x * 16 + 8, 50, c.z * 16 + 8);
            boolean present = generator.isInsideStructure(world, "Monument", center);
            boolean expected = accepted.contains(ChunkPos.asLong(c.x, c.z));
            genChecked++;
            // Water at sea level - 1 on the ring just outside the monument's box and its stepped rim.
            int ringDry = 0, ring = 0;
            for (int d = -36; d <= 36; d += 4) {
                int[][] pts = {{d, -36}, {d, 36}, {-36, d}, {36, d}};
                for (int[] p : pts) {
                    ring++;
                    BlockPos at = new BlockPos(center.getX() + p[0], world.getSeaLevel() - 1, center.getZ() + p[1]);
                    if (!world.getBlockState(at).getMaterial().isLiquid()) {
                        ringDry++;
                    }
                }
            }
            if (present != expected) {
                genBad++;
            }
            out.add(String.format("  GENERATED %d,%d generator=%s monumentPresent=%s ringNotWater=%d/%d%s", c.x, c.z,
                    expected, present, ringDry, ring, present != expected ? " MISMATCH" : ""));
        }
        out.add(String.format("GENERATION checked=%d mismatches=%d", genChecked, genBad));
        ChamberCheck.run(world, chamberRadius, chamberGenerate, out);
    }

    static int chamberRadius = 6;
    static int chamberGenerate = 4;
}

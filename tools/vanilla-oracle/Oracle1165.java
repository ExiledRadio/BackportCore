import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.data.worldgen.BastionPieces;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.SimpleReloadableResourceManager;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.StructureSettings;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.feature.configurations.JigsawConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.StructureFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.structures.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureManager;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Run against 1.16.5's server jar remapped to Mojang's names (FART with server.txt, --reverse):
 *   javac -cp server-mapped.jar -d classes DumpPools.java Oracle1165.java
 *   java -cp "server-mapped.jar;classes" DumpPools out/worldgen          (pools and processor lists, Mojang's codecs)
 *   java -cp "server-mapped.jar;classes" Oracle1165 out/oracle saves-dir (what NetherTests compares with)
 *   python -I pack_pools.py out/worldgen <client data/minecraft/structures> src/selftest/resources/assets/backportcore_selftest
 *
 * Writes what vanilla 1.16.5's own code decides, for Backport Core's self-tests to compare with:
 * the shared bastion/fortress slots, ruined portal (overworld and nether settings) and nether fossil candidates, the start random,
 * and whole bastion layouts from JigsawPlacement.addPieces.
 */
public final class Oracle1165 {
    static final long[] SEEDS = {0L, 12345L, -7346534567L, 8675309L};

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        Bootstrap.bootStrap();

        try (PrintWriter w = new PrintWriter(new File(out, "slots_1165.txt"), "UTF-8")) {
            Method bastion = featureCheck(StructureFeature.BASTION_REMNANT);
            Method fortress = featureCheck(StructureFeature.NETHER_BRIDGE);
            StructureFeatureConfiguration bc = StructureSettings.DEFAULTS.get(StructureFeature.BASTION_REMNANT);
            StructureFeatureConfiguration fc = StructureSettings.DEFAULTS.get(StructureFeature.NETHER_BRIDGE);
            StructureFeatureConfiguration rp = StructureSettings.DEFAULTS.get(StructureFeature.RUINED_PORTAL);
            StructureFeatureConfiguration nf = StructureSettings.DEFAULTS.get(StructureFeature.NETHER_FOSSIL);
            // The nether's own structure settings (DimensionSettings.nether): ruined portals at (25, 10).
            StructureFeatureConfiguration rpn = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.get(NoiseGeneratorSettings.NETHER)
                    .structureSettings().getConfig(StructureFeature.RUINED_PORTAL);
            w.println("# nether ruined portal settings: " + rpn.spacing() + " " + rpn.separation() + " " + rpn.salt());
            w.println("# seed kind chunkX chunkZ   (kind: bastion, fortress, none = candidate chunk with neither; portal, portal_nether, fossil = candidates)");
            for (long seed : SEEDS) {
                for (int rx = -20; rx < 20; rx++) {
                    for (int rz = -20; rz < 20; rz++) {
                        int cx = rx * bc.spacing();
                        int cz = rz * bc.spacing();
                        WorldgenRandom r1 = new WorldgenRandom();
                        ChunkPos c = StructureFeature.BASTION_REMNANT.getPotentialFeatureChunk(bc, seed, r1, cx, cz);
                        boolean b = (Boolean) bastion.invoke(StructureFeature.BASTION_REMNANT, null, null, seed, r1, c.x, c.z, null, c, null);
                        WorldgenRandom r2 = new WorldgenRandom();
                        ChunkPos c2 = StructureFeature.NETHER_BRIDGE.getPotentialFeatureChunk(fc, seed, r2, cx, cz);
                        boolean f = (Boolean) fortress.invoke(StructureFeature.NETHER_BRIDGE, null, null, seed, r2, c2.x, c2.z, null, c2, null);
                        if (!c.equals(c2)) {
                            throw new IllegalStateException("grids differ");
                        }
                        w.println(seed + " " + (b ? "bastion" : f ? "fortress" : "none") + " " + c.x + " " + c.z);
                    }
                }
                for (int rx = -10; rx < 10; rx++) {
                    for (int rz = -10; rz < 10; rz++) {
                        ChunkPos c = StructureFeature.RUINED_PORTAL.getPotentialFeatureChunk(rp, seed, new WorldgenRandom(), rx * rp.spacing(), rz * rp.spacing());
                        w.println(seed + " portal " + c.x + " " + c.z);
                        ChunkPos d = StructureFeature.NETHER_FOSSIL.getPotentialFeatureChunk(nf, seed, new WorldgenRandom(), rx * nf.spacing(), rz * nf.spacing());
                        w.println(seed + " fossil " + d.x + " " + d.z);
                        ChunkPos e = StructureFeature.RUINED_PORTAL.getPotentialFeatureChunk(rpn, seed, new WorldgenRandom(), rx * rpn.spacing(), rz * rpn.spacing());
                        w.println(seed + " portal_nether " + e.x + " " + e.z);
                    }
                }
            }
        }

        try (PrintWriter w = new PrintWriter(new File(out, "start_random_1165.txt"), "UTF-8")) {
            w.println("# seed chunkX chunkZ nextInt(1000) x4");
            for (long seed : SEEDS) {
                for (int[] c : new int[][] {{0, 0}, {7, -3}, {-120, 55}, {3000, -2999}}) {
                    WorldgenRandom r = new WorldgenRandom();
                    r.setLargeFeatureSeed(seed, c[0], c[1]);
                    w.println(seed + " " + c[0] + " " + c[1] + " " + r.nextInt(1000) + " " + r.nextInt(1000) + " " + r.nextInt(1000) + " " + r.nextInt(1000));
                }
            }
        }

        SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(PackType.SERVER_DATA);
        resources.add(new VanillaPackResources("minecraft"));
        LevelStorageSource.LevelStorageAccess storage = LevelStorageSource.createDefault(Paths.get(args[1])).createAccess("oracle");
        StructureManager manager = new StructureManager(resources, storage, DataFixers.getDataFixer());
        RegistryAccess.RegistryHolder access = RegistryAccess.builtin();
        Pools.bootstrap();
        try (PrintWriter w = new PrintWriter(new File(out, "bastion_layouts_1165.txt"), "UTF-8")) {
            w.println("# seed chunkX chunkZ index template rotation posX posY posZ minX minY minZ maxX maxY maxZ groundLevelDelta");
            for (long seed : SEEDS) {
                for (int[] c : new int[][] {{0, 0}, {5, -3}, {-40, 17}, {100, 100}, {-7, -250}, {31, -64}}) {
                    WorldgenRandom random = new WorldgenRandom();
                    random.setLargeFeatureSeed(seed, c[0], c[1]);
                    List<PoolElementStructurePiece> pieces = new ArrayList<>();
                    JigsawPlacement.addPieces(access, new JigsawConfiguration(() -> BastionPieces.START, 6), PoolElementStructurePiece::new, null,
                            manager, new BlockPos(c[0] * 16, 33, c[1] * 16), pieces, random, false, false);
                    for (int i = 0; i < pieces.size(); i++) {
                        PoolElementStructurePiece p = pieces.get(i);
                        String e = p.getElement().toString();
                        String loc = e.substring(e.indexOf("[Left[") + 6, e.indexOf("]]"));
                        BoundingBox b = p.getBoundingBox();
                        w.println(seed + " " + c[0] + " " + c[1] + " " + i + " " + loc + " " + p.getRotation().name() + " " + p.getPosition().getX()
                                + " " + p.getPosition().getY() + " " + p.getPosition().getZ() + " " + b.x0 + " " + b.y0 + " " + b.z0 + " " + b.x1 + " "
                                + b.y1 + " " + b.z1 + " " + p.getGroundLevelDelta());
                    }
                }
            }
        }
        storage.close();
        System.out.println("oracle written");
    }

    private static Method featureCheck(StructureFeature<?> feature) {
        for (Class<?> c = feature.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("isFeatureChunk") && m.getParameterCount() == 9) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new IllegalStateException("no isFeatureChunk on " + feature);
    }
}

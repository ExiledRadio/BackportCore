import net.minecraft.init.Bootstrap;
import net.minecraft.world.WorldType;
import net.minecraft.world.gen.layer.GenLayer;
import net.minecraft.world.gen.layer.IntCache;

import java.io.File;
import java.io.PrintWriter;

/**
 * Vanilla 1.12.2's biome maps without Forge, from Mojang's server jar remapped to MCP stable_39 names
 * (FART with ForgeGradle's notch-mcp.srg), in Oracle1144's format (biome4 and block4 only). It tells a
 * difference vanilla made between versions from one Forge makes:
 *   javac -cp server-mapped.jar -d classes Oracle1122.java
 *   java -cp "server-mapped.jar;classes" Oracle1122 out/1.12 [seeds...]
 */
public final class Oracle1122 {
    static final long[] SEEDS = {42L, 12345L, -7346534567L, 8675309L, 171L, 271L};
    static final int R = 1024;

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
        for (long seed : seeds) {
            GenLayer[] layers = GenLayer.initializeAllBiomeGenerators(seed, WorldType.DEFAULT, null);
            layers[0].initWorldGenSeed(seed);
            layers[1].initWorldGenSeed(seed);
            try (PrintWriter w = new PrintWriter(new File(out, "seed_" + seed + ".txt"), "UTF-8")) {
                w.println("version 1.12.2");
                w.println("seed " + seed);
                int c0 = -R >> 2, n = (2 * R) >> 2;
                IntCache.resetIntCache();
                int[] cells = layers[0].getInts(c0, c0, n, n).clone();
                w.println("biome4 " + c0 + " " + c0 + " " + n + " " + n);
                for (int z = 0; z < n; z++) {
                    StringBuilder s = new StringBuilder();
                    for (int x = 0; x < n; x++) {
                        s.append(x == 0 ? "" : " ").append(cells[x + z * n]);
                    }
                    w.println(s);
                }
                int[][] block = new int[n][n];
                for (int tz = -R; tz < R; tz += 64) {
                    for (int tx = -R; tx < R; tx += 64) {
                        IntCache.resetIntCache();
                        int[] area = layers[1].getInts(tx, tz, 64, 64);
                        for (int z = 0; z < 64; z += 4) {
                            for (int x = 0; x < 64; x += 4) {
                                block[(tz + z + R) >> 2][(tx + x + R) >> 2] = area[x + z * 64];
                            }
                        }
                    }
                }
                w.println("block4 " + (-R) + " " + (-R) + " " + n + " " + n);
                for (int z = 0; z < n; z++) {
                    StringBuilder s = new StringBuilder();
                    for (int x = 0; x < n; x++) {
                        s.append(x == 0 ? "" : " ").append(block[z][x]);
                    }
                    w.println(s);
                }
            }
            System.out.println("seed " + seed + " done");
        }
    }

    private Oracle1122() {
    }
}

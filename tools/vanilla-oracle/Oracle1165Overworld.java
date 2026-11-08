import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.OverworldBiomeSource;

import java.io.File;
import java.io.PrintWriter;

/**
 * Vanilla 1.16.5's overworld 1:4 biome map (the one structures and strongholds read from 1.16 on), in
 * Oracle1144's format (biome4 only), to show the 1.15/1.16 overworld layers equal 1.14.4's:
 *   javac -cp server-mapped.jar -d classes Oracle1165Overworld.java
 *   java -cp "server-mapped.jar;classes" Oracle1165Overworld out/1.16 [seeds...]
 * Ids are the builtin biome registry's, the same numbers as 1.13/1.14. 1.15 also replaced the block-scale
 * Voronoi zoom with a 3D one, so there is no block4 to compare.
 */
public final class Oracle1165Overworld {
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
        Bootstrap.bootStrap();
        for (long seed : seeds) {
            OverworldBiomeSource source = new OverworldBiomeSource(seed, false, false, BuiltinRegistries.BIOME);
            try (PrintWriter w = new PrintWriter(new File(out, "seed_" + seed + ".txt"), "UTF-8")) {
                w.println("version 1.16.5");
                w.println("seed " + seed);
                int c0 = -R >> 2, n = (2 * R) >> 2;
                w.println("biome4 " + c0 + " " + c0 + " " + n + " " + n);
                for (int z = 0; z < n; z++) {
                    StringBuilder s = new StringBuilder();
                    for (int x = 0; x < n; x++) {
                        s.append(x == 0 ? "" : " ").append(BuiltinRegistries.BIOME.getId(source.getNoiseBiome(c0 + x, 0, c0 + z)));
                    }
                    w.println(s);
                }
            }
            System.out.println("seed " + seed + " done");
        }
    }

    private Oracle1165Overworld() {
    }
}

package com.exiledradio.backportcore.worldgen;

import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.layer.GenLayer;
import net.minecraft.world.gen.layer.IntCache;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;

/**
 * What Core's worldgen patches call (P8 and P9 in {@code docs/PATCHES.md}). Each hook does exactly what
 * vanilla 1.12.2 does until the newest installed backport is 1.13 or later
 * ({@link StructurePlacements#isAtLeast}).
 */
public final class WorldgenHooks {

    private static final Field GEN_BIOMES = ReflectionHelper.findField(BiomeProvider.class, "genBiomes", "field_76944_d");

    /**
     * Whether the biome layers follow 1.16's neighbor rules (P10, P11). Asked for every cell, so it is not
     * resolved per call: {@link ModernWorldgen} sets it when a world's biome map is made, after every mod's
     * pre-init.
     */
    static volatile boolean layers116;

    private WorldgenHooks() {
    }

    /**
     * Patch P10: the whole body of {@code GenLayer.biomesEqualOrMesaPlateau}, the "same kind of biome" test of
     * the hills, edge and shore layers.
     *
     * <p>1.12.2 (and 1.15's {@code Layers.isSame}, by biome category): the same biome class, except that a
     * badlands plateau is only like a badlands plateau, and asked from the plateau's side only. 1.16.5's
     * {@code Layers.isSame} looks both biomes up in one fixed category table in which the two badlands plateaus
     * have a category of their own, so a badlands (or eroded badlands) cell is no longer "like" a plateau
     * next to it, which keeps more plateau where the layers test from the badlands' side.
     */
    public static boolean similarBiomes(int a, int b) {
        if (a == b) {
            return true;
        }
        Biome x = Biome.getBiome(a);
        Biome y = Biome.getBiome(b);
        if (x == null || y == null) {
            return false;
        }
        boolean px = x == Biomes.MESA_ROCK || x == Biomes.MESA_CLEAR_ROCK;
        boolean py = y == Biomes.MESA_ROCK || y == Biomes.MESA_CLEAR_ROCK;
        if (layers116) {
            return px || py ? px && py : x.getBiomeClass() == y.getBiomeClass();
        }
        // 1.12.2's own.
        if (!px) {
            return x == y || x.getBiomeClass() == y.getBiomeClass();
        }
        return py;
    }

    /**
     * Patch P11: the whole body of {@code GenLayerBiomeEdge.canBiomesBeNeighbors}, which turns mountains next to
     * a biome of another temperature into mountain edge. 1.16.5's {@code BiomeEdgeLayer.checkEdge} keeps every
     * mountain cell as it is (biomes lost their temperature categories in 1.16), so no mountain edge forms there.
     */
    public static boolean canBeNeighbors(int a, int b) {
        if (layers116 || similarBiomes(a, b)) {
            return true;
        }
        // 1.12.2's own.
        Biome x = Biome.getBiome(a);
        Biome y = Biome.getBiome(b);
        if (x != null && y != null) {
            Biome.TempCategory tx = x.getTempCategory();
            Biome.TempCategory ty = y.getTempCategory();
            return tx == ty || tx == Biome.TempCategory.MEDIUM || ty == Biome.TempCategory.MEDIUM;
        }
        return false;
    }

    /**
     * Patch P8: the whole body of {@code BiomeProvider.findBiomePosition}, the biome search that places all
     * 128 strongholds and starts the spawn point.
     *
     * <p>Both versions keep one matching 1:4 cell of the square, replacing the kept one with chance
     * {@code 1 / (n + 1)}. 1.12.2 counts {@code n} only when it replaces; 1.13 ({@code
     * OverworldBiomeProvider.findBiomePosition}, the same in 1.14's {@code findBiome}, 1.15's and 1.16's
     * {@code findBiomeHorizontal} for these squares) counts every matching cell, so the two draw different
     * random numbers and every stronghold after the first difference moves.
     */
    @Nullable
    public static BlockPos findBiomePosition(BiomeProvider provider, int x, int z, int range, List<Biome> biomes, Random random) {
        if (!StructurePlacements.isAtLeast("1.13")) {
            return findBiomePosition112(provider, x, z, range, biomes, random);
        }
        int x0 = x - range >> 2;
        int z0 = z - range >> 2;
        int w = (x + range >> 2) - x0 + 1;
        int h = (z + range >> 2) - z0 + 1;
        Biome[] cells = provider.getBiomesForGeneration(null, x0, z0, w, h);
        BlockPos found = null;
        int count = 0;
        for (int i = 0; i < w * h; i++) {
            if (biomes.contains(cells[i])) {
                if (found == null || random.nextInt(count + 1) == 0) {
                    found = new BlockPos(x0 + i % w << 2, 0, z0 + i / w << 2);
                }
                count++;
            }
        }
        return found;
    }

    /** 1.12.2's own {@code findBiomePosition}, line for line. */
    @Nullable
    static BlockPos findBiomePosition112(BiomeProvider provider, int x, int z, int range, List<Biome> biomes, Random random) {
        IntCache.resetIntCache();
        int i = x - range >> 2;
        int j = z - range >> 2;
        int k = x + range >> 2;
        int l = z + range >> 2;
        int i1 = k - i + 1;
        int j1 = l - j + 1;
        GenLayer genBiomes;
        try {
            genBiomes = (GenLayer) GEN_BIOMES.get(provider);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        int[] aint = genBiomes.getInts(i, j, i1, j1);
        BlockPos blockpos = null;
        int k1 = 0;
        for (int l1 = 0; l1 < i1 * j1; ++l1) {
            int i2 = i + l1 % i1 << 2;
            int j2 = j + l1 / i1 << 2;
            Biome biome = Biome.getBiome(aint[l1]);
            if (biomes.contains(biome) && (blockpos == null || random.nextInt(k1 + 1) == 0)) {
                blockpos = new BlockPos(i2, 0, j2);
                ++k1;
            }
        }
        return blockpos;
    }

    /**
     * Patch P9: {@code MapGenVillage.canSpawnStructureAtCoords} asks
     * {@code areBiomesViable(chunkX * 16 + 8, chunkZ * 16 + 8, 0, VILLAGE_SPAWN_BIOMES)}, the 1:4 cell at the
     * chunk's (8, 8). From 1.13 on a village reads the biome at the newest version's point
     * ({@link StructurePlacements#biomeAt}: the block biome at (9, 9) to 1.15, the 1:4 cell at (4x + 2, 4z + 2)
     * from 1.16). Any other radius, and 1.12.2, go to {@code areBiomesViable} unchanged.
     */
    public static boolean villageBiomes(BiomeProvider provider, int x, int z, int radius, List<Biome> allowed) {
        if (radius != 0 || !StructurePlacements.isAtLeast("1.13")) {
            return provider.areBiomesViable(x, z, radius, allowed);
        }
        Biome biome = StructurePlacements.biomeAt(provider, x >> 4, z >> 4);
        return biome != null && allowed.contains(biome);
    }
}

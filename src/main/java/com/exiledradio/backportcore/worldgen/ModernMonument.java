package com.exiledradio.backportcore.worldgen;

import com.exiledradio.backportcore.structure.ScatteredPlacement;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.StructureOceanMonument;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla's ocean monument, placed by the rule of 1.13 and later ({@code OceanMonumentFeature.isFeatureChunk},
 * the same in 1.16.5's {@code OceanMonumentStructure}): the 1:4 cells within 16 blocks of the chunk's block
 * (9, 9) must all be monument biomes, and those within 29 blocks all oceans or rivers. 1.12.2 measures both
 * squares from (8, 8); for the 29-block square that starts one cell earlier.
 *
 * <p>The center biomes are 1.12.2's ({@link StructureOceanMonument#SPAWN_BIOMES}, the deep ocean) and any a
 * backport registers ({@code structureBiome("ocean_monument", ...)}); the surroundings are every ocean and
 * river by the biome dictionary, which for vanilla's biomes is 1.12.2's own list. Only used where the
 * overworld still has vanilla's generator: a backport that brings its own monument rule (1.13's deep oceans by
 * temperature) keeps it. The grid (32/5, salt 10387313, triangular) is the newest registered one, else
 * vanilla's.
 */
public final class ModernMonument extends StructureOceanMonument {

    private final ScatteredPlacement grid;
    private final List<Biome> center = new ArrayList<Biome>(StructureOceanMonument.SPAWN_BIOMES);
    private final List<Biome> water = new ArrayList<Biome>();

    public ModernMonument() {
        this.grid = StructurePlacements.placement(StructurePlacements.OCEAN_MONUMENT, new ScatteredPlacement(32, 5, 10387313, true));
        for (Biome biome : ForgeRegistries.BIOMES) {
            if (StructurePlacements.isExtraBiome(StructurePlacements.OCEAN_MONUMENT, biome) && !this.center.contains(biome)) {
                this.center.add(biome);
            }
            if (BiomeDictionary.hasType(biome, BiomeDictionary.Type.OCEAN) || BiomeDictionary.hasType(biome, BiomeDictionary.Type.RIVER)) {
                this.water.add(biome);
            }
        }
    }

    @Override
    protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
        if (!this.grid.isCandidate(this.world.getSeed(), chunkX, chunkZ)) {
            return false;
        }
        return this.world.getBiomeProvider().areBiomesViable(chunkX * 16 + 9, chunkZ * 16 + 9, 16, this.center)
                && this.world.getBiomeProvider().areBiomesViable(chunkX * 16 + 9, chunkZ * 16 + 9, 29, this.water);
    }
}

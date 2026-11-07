package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.structure.ScatteredPlacement;
import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.structure.TemplatePiece;
import com.exiledradio.backportcore.structure.TemplatePlacement;
import com.exiledradio.backportcore.terrain.TerrainHeights;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.util.Mirror;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Self-test only: a scattered structure of one ruin, near spawn, on land. */
public final class TestStructure extends ScatteredStructure {

    static final String KIND = "backportcore_selftest";
    static final ResourceLocation RUIN = new ResourceLocation(SelfTestMod.MOD_ID, "v26/underwater_ruin/brick_3");

    /** Starts laid out in this session, for the test to find. */
    static final List<StructureStart> LAID_OUT = new ArrayList<StructureStart>();

    public TestStructure() {
        super("BCST_Test", new ScatteredPlacement(8, 2, 1234567));
    }

    @Override
    protected boolean canStartAt(World world, int chunkX, int chunkZ) {
        BlockPos spawn = world.getSpawnPoint();
        if (Math.abs(chunkX - (spawn.getX() >> 4)) > 24 || Math.abs(chunkZ - (spawn.getZ() >> 4)) > 24) {
            return false;
        }
        Biome biome = world.getBiomeProvider().getBiome(new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8));
        return biome != Biomes.OCEAN && biome != Biomes.DEEP_OCEAN && biome != Biomes.FROZEN_OCEAN;
    }

    @Override
    protected StructureStart getStructureStart(int chunkX, int chunkZ) {
        Start start = new Start(this.world, this.rand, chunkX, chunkZ);
        LAID_OUT.add(start);
        return start;
    }

    public static final class Start extends StructureStart {
        public Start() {
        }

        Start(World world, Random random, int chunkX, int chunkZ) {
            super(chunkX, chunkZ);
            int x = (chunkX << 4) + 4;
            int z = (chunkZ << 4) + 4;
            TerrainHeights heights = TerrainHeights.forWorld(world);
            int y = heights == null ? 64 : heights.floor(x, z);
            Piece piece = new Piece(new BlockPos(x, y, z), Rotation.values()[random.nextInt(4)], random);
            this.components.add(piece);
            updateBoundingBox();
        }
    }

    public static final class Piece extends TemplatePiece {
        public Piece() {
        }

        Piece(BlockPos origin, Rotation rotation, Random random) {
            super(RUIN, origin, rotation, Mirror.NONE, new BlockPos(4, 0, 4), random);
        }

        @Override
        protected TemplatePlacement placement() {
            return super.placement().ignoreAir(true);
        }

        @Override
        protected void handleDataMarker(String metadata, BlockPos pos, World world, Random random, StructureBoundingBox chunk) {
            if ("chest".equals(metadata)) {
                IBlockState chest = Blocks.CHEST.getDefaultState();
                world.setBlockState(pos, chest, 2);
            }
        }
    }
}

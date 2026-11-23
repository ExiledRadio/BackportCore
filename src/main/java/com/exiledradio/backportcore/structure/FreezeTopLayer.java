package com.exiledradio.backportcore.structure;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

/**
 * Snow and ice over structures, as today's {@code freeze_top_layer} lays them: the last feature of a chunk's
 * decoration since 1.13, after the structures, so a snow layer covers a structure's top exactly as it covers the
 * terrain around it.
 *
 * <p>1.12.2 snows the chunk at the end of its own populate step, and the family's structures are built after that
 * (Core's {@link ScatteredStructure}s and Village &amp; Pillage's outposts from Forge's world generators; everything in
 * Caves &amp; Cliffs' 1.18 terrain, which snows while it generates the chunk). Whoever builds a structure after the snow
 * calls {@link #afterBuilding} once its pieces are in; a chunk generator that snows before its structures calls
 * {@link #area}. {@link #afterBuilding} first takes the leaves off the trees the structure cut ({@link CutTrees}).
 * Only each column's top block is touched (the block under the precipitation height turns to ice, a
 * snow layer goes on top), never an interior; grass, podzol and mycelium look snowy by themselves in 1.12.
 *
 * <p>The rules are today's ({@code Biome.shouldFreeze}, {@code Biome.shouldSnow}): ice on still water, a snow layer in
 * air over a top that can hold one, where it is cold enough. Their block light check always passes while a chunk is
 * decorated (light comes later), so lit structures are covered too, and torches melt what is too close by random
 * tick. Cold enough is 1.12's temperature through the world provider ({@code canSnowAt(pos, false)}), so seasons
 * mods still decide; a chunk generator with today's temperatures implements {@link Climate} instead.
 */
public final class FreezeTopLayer {

    /** A world generator's own temperatures, implemented by its {@link IChunkGenerator}. */
    public interface Climate {
        /**
         * Today's {@code Biome.coldEnoughToSnow} at the block, by the generator's biomes. Asked on the server thread
         * while a chunk around the block is populated.
         */
        boolean coldEnoughToSnow(World world, BlockPos pos);
    }

    private static volatile boolean enabled = true;

    private FreezeTopLayer() {
    }

    /** For tests: off, nothing is frozen (1.12's look, bare structures in the snow). */
    public static void setEnabled(boolean on) {
        enabled = on;
    }

    /** @return the area a chunk's populate step builds in: from 8 blocks into it, 16 by 16 */
    public static StructureBoundingBox populationArea(int chunkX, int chunkZ) {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        return new StructureBoundingBox(x, z, x + 15, z + 15);
    }

    /**
     * Freezes the top of every column of a chunk's population area, for a chunk generator that snows before it
     * builds its structures.
     *
     * @return how many blocks it set
     */
    public static int area(World world, int chunkX, int chunkZ) {
        if (!enabled) {
            return 0;
        }
        StructureBoundingBox area = populationArea(chunkX, chunkZ);
        Climate climate = climate(world);
        int set = 0;
        for (int x = area.minX; x <= area.maxX; ++x) {
            for (int z = area.minZ; z <= area.maxZ; ++z) {
                set += column(world, climate, x, z);
            }
        }
        return set;
    }

    /**
     * Freezes the columns of a chunk's population area that the structure's pieces cover, after the structure built
     * its part there ({@code generateStructure(world, random, chunk)}).
     *
     * @return how many blocks it set
     */
    public static int afterBuilding(World world, MapGenStructure structure, int chunkX, int chunkZ) {
        return afterBuilding(world, starts(structure).values(), chunkX, chunkZ);
    }

    /**
     * {@link #afterBuilding(World, MapGenStructure, int, int)} over starts the caller keeps itself. The trees the
     * structure cut lose their leaves first ({@link CutTrees}), so no snow is left lying on a leaf that goes.
     */
    public static int afterBuilding(World world, Collection<StructureStart> starts, int chunkX, int chunkZ) {
        CutTrees.afterBuilding(world, starts, chunkX, chunkZ);
        if (!enabled) {
            return 0;
        }
        boolean[] mask = new boolean[256];
        if (!cover(mask, starts, chunkX, chunkZ)) {
            return 0;
        }
        return freeze(world, mask, chunkX, chunkZ);
    }

    /**
     * Marks the population area's columns the starts' pieces cover in {@code mask} (x | z &lt;&lt; 4, from the area's
     * corner).
     *
     * @return true if any was marked
     */
    static boolean cover(boolean[] mask, Collection<StructureStart> starts, int chunkX, int chunkZ) {
        StructureBoundingBox area = populationArea(chunkX, chunkZ);
        boolean any = false;
        for (StructureStart start : starts) {
            if (start == null || !start.isSizeableStructure() || start.getBoundingBox() == null
                    || !start.getBoundingBox().intersectsWith(area)) {
                continue;
            }
            for (StructureComponent piece : start.getComponents()) {
                StructureBoundingBox box = piece.getBoundingBox();
                if (box == null || !box.intersectsWith(area)) {
                    continue;
                }
                for (int x = Math.max(box.minX, area.minX); x <= Math.min(box.maxX, area.maxX); ++x) {
                    for (int z = Math.max(box.minZ, area.minZ); z <= Math.min(box.maxZ, area.maxZ); ++z) {
                        mask[(x - area.minX) | (z - area.minZ) << 4] = true;
                        any = true;
                    }
                }
            }
        }
        return any;
    }

    /** Freezes the marked columns of the chunk's population area. @return how many blocks it set */
    static int freeze(World world, boolean[] mask, int chunkX, int chunkZ) {
        if (!enabled) {
            return 0;
        }
        StructureBoundingBox area = populationArea(chunkX, chunkZ);
        Climate climate = climate(world);
        int set = 0;
        for (int i = 0; i < 256; ++i) {
            if (mask[i]) {
                set += column(world, climate, area.minX + (i & 15), area.minZ + (i >> 4));
            }
        }
        return set;
    }

    /** One column, as 1.12's populate snows it (ice first, then the snow layer on top of it). */
    private static int column(World world, @Nullable Climate climate, int x, int z) {
        BlockPos top = world.getPrecipitationHeight(new BlockPos(x, 0, z));
        BlockPos below = top.down();
        int set = 0;
        if (freezes(world, climate, below)) {
            world.setBlockState(below, Blocks.ICE.getDefaultState(), 2);
            set++;
        }
        if (snows(world, climate, top)) {
            world.setBlockState(top, Blocks.SNOW_LAYER.getDefaultState(), 2);
            set++;
        }
        return set;
    }

    /**
     * Today's Biome.shouldFreeze(pos, false): a still water source, cold enough. Its block light check always passes
     * while a chunk is decorated (light comes after the features), so a lit structure's top freezes too.
     */
    private static boolean freezes(World world, @Nullable Climate climate, BlockPos pos) {
        if (world.isOutsideBuildHeight(pos)) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        if ((state.getBlock() != Blocks.WATER && state.getBlock() != Blocks.FLOWING_WATER)
                || state.getMaterial() != Material.WATER || state.getValue(BlockLiquid.LEVEL) != 0) {
            return false;
        }
        return cold(world, climate, pos);
    }

    /**
     * Today's Biome.shouldSnow: air, cold enough, and a snow layer could stand there. Its light check always passes too:
     * snow lies next to a structure's torches, and melts where they light it above 11, as today.
     */
    private static boolean snows(World world, @Nullable Climate climate, BlockPos pos) {
        if (world.isOutsideBuildHeight(pos)) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        if (!state.getBlock().isAir(state, world, pos) || !Blocks.SNOW_LAYER.canPlaceBlockAt(world, pos)) {
            return false;
        }
        return cold(world, climate, pos);
    }

    /**
     * Cold enough to snow: the generator's {@link Climate}, else 1.12's temperature check through the world provider
     * ({@code canSnowAt} without its light and block checks), which seasons mods change.
     */
    private static boolean cold(World world, @Nullable Climate climate, BlockPos pos) {
        return climate != null ? climate.coldEnoughToSnow(world, pos) : world.canSnowAt(pos, false);
    }

    /** @return the world's chunk generator's {@link Climate}, or null for 1.12's rules */
    @Nullable
    public static Climate climate(World world) {
        if (!(world instanceof WorldServer)) {
            return null;
        }
        ChunkProviderServer provider = ((WorldServer) world).getChunkProvider();
        IChunkGenerator generator = provider == null ? null : provider.chunkGenerator;
        return generator instanceof Climate ? (Climate) generator : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, StructureStart> starts(MapGenStructure structure) {
        return (Map<Long, StructureStart>) ObfuscationReflectionHelper.getPrivateValue(MapGenStructure.class, structure,
                "structureMap", "field_75053_d");
    }
}

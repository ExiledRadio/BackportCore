package com.exiledradio.backportcore.compat;

import com.exiledradio.backportcore.BackportCore;
import com.ferreusveritas.dynamictrees.ModConfigs;
import com.ferreusveritas.dynamictrees.api.TreeRegistry;
import com.ferreusveritas.dynamictrees.api.WorldGenRegistry;
import com.ferreusveritas.dynamictrees.trees.Species;
import com.ferreusveritas.dynamictrees.util.SafeChunkBounds;
import com.ferreusveritas.dynamictrees.worldgen.BiomeDataBase;
import com.ferreusveritas.dynamictrees.worldgen.BiomeDataBasePopulatorJson;
import com.ferreusveritas.dynamictrees.worldgen.TreeGenerator;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The only class that links against Dynamic Trees (0.9.x for 1.12.2); loaded by {@link DynamicTrees} only when it is
 * installed.
 */
final class DynamicTreesLink {

    private static List<ResourceLocation> biomeData;

    private DynamicTreesLink() {
    }

    static void init(List<ResourceLocation> data) {
        biomeData = data;
        DynamicTreesLink handler = new DynamicTreesLink();
        MinecraftForge.EVENT_BUS.register(handler);
        MinecraftForge.TERRAIN_GEN_BUS.register(handler);
    }

    /** The world's biome database, or null where Dynamic Trees leaves the dimension alone. */
    @Nullable
    private static BiomeDataBase database(World world) {
        if (!ModConfigs.worldGen) {
            return null;
        }
        int dim = world.provider.getDimension();
        BiomeDataBase db = TreeGenerator.getTreeGenerator().getBiomeDataBase(dim);
        if (db == null || db == TreeGenerator.DIMENSIONBLACKLISTED || ModConfigs.dimensionBlacklist.contains(dim)) {
            return null;
        }
        return db;
    }

    /** Whether Dynamic Trees generates trees in this world's dimension at all. */
    static boolean active(World world) {
        return database(world) != null;
    }

    static boolean vanillaCactus() {
        return ModConfigs.vanillaCactusWorldGen;
    }

    static boolean replacesTrees(World world, Biome biome) {
        BiomeDataBase db = database(world);
        return db != null && db.getEntry(biome).shouldCancelVanillaTreeGen();
    }

    /**
     * After Dynamic Trees' own handler: the TREE, BIG_SHROOM and CACTUS decorations follow the biome that decorates
     * the chunk (corner + 16, as {@code ChunkGeneratorOverworld.populate} picks it), not the corner's. A DENY is lifted
     * only where Dynamic Trees' rule for the corner's biome explains it.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void decorate(DecorateBiomeEvent.Decorate event) {
        DecorateBiomeEvent.Decorate.EventType type = event.getType();
        if (type != DecorateBiomeEvent.Decorate.EventType.TREE && type != DecorateBiomeEvent.Decorate.EventType.BIG_SHROOM
                && (type != DecorateBiomeEvent.Decorate.EventType.CACTUS || ModConfigs.vanillaCactusWorldGen)) {
            return;
        }
        World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }
        BiomeDataBase db = database(world);
        if (db == null) {
            return;
        }
        ChunkPos chunk = event.getChunkPos();
        Biome corner = world.getBiome(chunk.getBlock(0, 0, 0));
        Biome decorating = world.getBiome(chunk.getBlock(16, 0, 16));
        if (corner == decorating) {
            return;
        }
        event.setResult(DynamicTrees.decide(db.getEntry(corner).shouldCancelVanillaTreeGen(),
                db.getEntry(decorating).shouldCancelVanillaTreeGen(), event.getResult()));
    }

    /** The backports' worldgen JSONs, before the pack's {@code worldgen.json} (Dynamic Trees registers that last). */
    @SubscribeEvent
    public void populators(WorldGenRegistry.BiomeDataBasePopulatorRegistryEvent event) {
        List<ResourceLocation> data;
        synchronized (biomeData) {
            data = new ArrayList<ResourceLocation>(biomeData);
        }
        for (ResourceLocation json : data) {
            try {
                event.register(new BiomeDataBasePopulatorJson(json));
            } catch (RuntimeException e) {
                BackportCore.LOGGER.warn("Dynamic Trees could not read {}.", json, e);
            }
        }
    }

    static boolean grow(World world, BlockPos pos, Random random, String name, int radius) {
        Species species = TreeRegistry.findSpecies(name.indexOf(':') >= 0 ? new ResourceLocation(name)
                : new ResourceLocation(DynamicTrees.MOD_ID, name));
        if (species == null || !species.isValid()) {
            return false;
        }
        BlockPos root = pos.down();
        IBlockState soil = world.getBlockState(root);
        if (!species.isAcceptableSoilForWorldgen(world, root, soil)) {
            return false;
        }
        return species.generate(world, root, world.getBiome(pos), random, radius, new SafeChunkBounds(world, new ChunkPos(pos)));
    }
}

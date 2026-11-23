package com.exiledradio.backportcore.terrain;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The family's biomes in Realistic Terrain Generation worlds (RTG 6.1 and its Unofficial fork, both modid
 * {@code rtg}).
 *
 * <h2>Why</h2>
 * RTG builds each column from a "realistic biome" registered for the column's biome, and a biome nobody registered
 * becomes its patch biome (plains by default): terrain, surface and the chunk's saved biome alike. A backport's
 * biome reaches RTG's biome map (RTG fires {@code WorldTypeEvent.InitBiomeGens}) and then comes out as plains.
 *
 * <h2>Use</h2>
 * A backport names, for each surface biome, the vanilla biome whose RTG terrain fits it ({@link Template}), as soon
 * as the biome is registered: {@code RtgBiomes.register(biome, "FOREST")}. Core hands the list to RTG in its
 * post-init (before RTG locks it at load complete); a later call goes straight through. Without RTG nothing
 * happens. Backports that must load with an older Core reach this by name, so the string form takes the template.
 *
 * <p>The realistic biome is RTG's own vanilla terrain for the template, with the biome's top and filler blocks
 * painted by RTG's generic surface and no RTG decorations: RTG then runs the biome's own {@code decorate}, so its
 * trees and features are the backport's. Ocean templates use RTG's ocean surface and decorations, as RTG's oceans do.
 *
 * <h2>Where the biome is read</h2>
 * RTG repairs its biome map per chunk (coastal ocean becomes beach, and so on) and the chunk keeps the repaired
 * biome, while the biome provider still answers with the unrepaired map. {@link #biomeAt} reads the repaired biome
 * from RTG's cached landscape without making the chunk; {@code StructurePlacements.biomeAt(World, ...)} uses it.
 */
public final class RtgBiomes {

    public static final String MODID = "rtg";
    private static final String GENERATOR = "rtg.world.gen.ChunkGeneratorRTG";

    /** The vanilla biome whose RTG terrain a biome takes. */
    public enum Template {
        OCEAN, DEEP_OCEAN, PLAINS, FOREST, FOREST_HILLS, BIRCH_FOREST, JUNGLE, JUNGLE_HILLS, SWAMP, ROOFED_FOREST
    }

    private static final Map<Biome, Template> REQUESTED = new LinkedHashMap<Biome, Template>();
    private static boolean handedOver;
    private static boolean failed;

    private static boolean readFailed;
    private static Method landscape;
    private static Field biomes;
    private static Method baseBiome;

    private RtgBiomes() {
    }

    /** Asks for this biome to have RTG terrain of this kind in RTG worlds. */
    public static synchronized void register(@Nullable Biome biome, Template template) {
        if (biome == null || template == null || REQUESTED.containsKey(biome)) {
            return;
        }
        REQUESTED.put(biome, template);
        if (handedOver) {
            Map<Biome, Template> one = new LinkedHashMap<Biome, Template>();
            one.put(biome, template);
            handOver(one);
        }
    }

    /** {@link #register(Biome, Template)} by the template's name, for callers that reach this by reflection. */
    public static void register(@Nullable Biome biome, String template) {
        try {
            register(biome, Template.valueOf(template.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            BackportCore.LOGGER.warn("Unknown RTG terrain template {} for {}; it stays RTG's patch biome.", template,
                    biome == null ? null : biome.getRegistryName());
        }
    }

    /** @return true if a backport asked for this biome (so it is RTG's to build, not the patch biome's) */
    public static synchronized boolean isRequested(Biome biome) {
        return REQUESTED.containsKey(biome);
    }

    /** Core's post-init: every backport has registered its biomes. */
    public static synchronized void postInit() {
        handedOver = true;
        if (!REQUESTED.isEmpty()) {
            handOver(REQUESTED);
        }
    }

    private static void handOver(Map<Biome, Template> biomes) {
        if (failed || !Loader.isModLoaded(MODID)) {
            return;
        }
        try {
            int count = com.exiledradio.backportcore.terrain.rtg.RtgRealisticBiomes.register(biomes);
            BackportCore.LOGGER.info("Gave Realistic Terrain Generation terrain for {} backport biomes.", count);
        } catch (Exception | LinkageError e) {
            failed = true;
            BackportCore.LOGGER.warn("Could not register the backports' biomes with Realistic Terrain Generation; in "
                    + "its worlds they generate as its patch biome.", e);
        }
    }

    @Nullable
    private static IChunkGenerator generator(World world) {
        if (!(world instanceof WorldServer)) {
            return null;
        }
        ChunkProviderServer provider = ((WorldServer) world).getChunkProvider();
        IChunkGenerator generator = provider == null ? null : provider.chunkGenerator;
        return generator != null && GENERATOR.equals(generator.getClass().getName()) ? generator : null;
    }

    /** @return true if this world's chunks come from RTG */
    public static boolean handles(World world) {
        return generator(world) != null;
    }

    /**
     * @return the biome RTG gives this column once its chunk is made (after its coast repair), without making the
     * chunk; null outside RTG worlds or if its landscape cannot be read
     */
    @Nullable
    public static Biome biomeAt(World world, int x, int z) {
        IChunkGenerator generator = readFailed ? null : generator(world);
        if (generator == null) {
            return null;
        }
        try {
            if (landscape == null) {
                Method method = generator.getClass().getMethod("getLandscape", BiomeProvider.class, ChunkPos.class);
                biomes = method.getReturnType().getField("biome");
                baseBiome = Class.forName("rtg.api.world.biome.IRealisticBiome").getMethod("baseBiome");
                landscape = method;
            }
            Object chunk = landscape.invoke(generator, world.getBiomeProvider(), new ChunkPos(x >> 4, z >> 4));
            // Indexed like the landscape's heights, x * 16 + z: an array in RTG 6.1, a list in the Unofficial fork.
            Object all = biomes.get(chunk);
            int index = (x & 15) * 16 + (z & 15);
            Object realistic = all instanceof List ? ((List<?>) all).get(index) : Array.get(all, index);
            return realistic == null ? null : (Biome) baseBiome.invoke(realistic);
        } catch (Exception | LinkageError e) {
            readFailed = true;
            BackportCore.LOGGER.warn("Could not read Realistic Terrain Generation's biomes; structures in its worlds "
                    + "read the unrepaired biome map.", e);
            return null;
        }
    }
}

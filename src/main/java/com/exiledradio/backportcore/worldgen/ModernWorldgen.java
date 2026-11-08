package com.exiledradio.backportcore.worldgen;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.layer.GenLayer;
import net.minecraft.world.gen.layer.GenLayerHills;
import net.minecraft.world.gen.layer.GenLayerVoronoiZoom;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.MapGenStronghold;
import net.minecraft.world.gen.structure.StructureOceanMonument;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.event.terraingen.WorldTypeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * The overworld generation rules every version from 1.13 on shares, which no single backport owns, applied by
 * the newest installed version ({@link StructurePlacements#newest()}). With only Core installed none applies
 * and worlds generate exactly as 1.12.2's.
 *
 * <ul>
 *   <li><b>Hills layer seeding</b> (1.13+): 1.12.2's {@code GenLayerHills.initWorldGenSeed} seeds only its
 *       biome parent, so the two zoom layers of its river noise run with world seed 0. That noise decides
 *       which areas become rare variants (sunflower plains, tall birch forest, gravelly mountains, ice spikes
 *       and the like) and some hills; 1.13's layer contexts all get the world seed. Found by running
 *       vanilla 1.12.2 and 1.13.2 side by side ({@code tools/vanilla-oracle}).</li>
 *   <li><b>1.15's block-scale biome</b> (1.15+): the Voronoi layer gives way to {@link FuzzyZoomLayer}.</li>
 *   <li><b>Temples</b> (1.13+): {@link ModernTemples}, four grids.</li>
 *   <li><b>Monuments</b> (1.13+): {@link ModernMonument}, the squares from block (9, 9).</li>
 *   <li><b>Stronghold biomes</b> (1.13+): 1.12.2 allows every biome above sea level; each version's
 *       registered rulings ({@code strongholdBiome}) change that list (1.13 adds the mushroom field shore,
 *       1.16 leaves out the biomes its overworld biome source cannot make, the bamboo jungles).</li>
 * </ul>
 * The strongholds' and spawn point's biome search is patch P8 ({@link WorldgenHooks#findBiomePosition}), the
 * villages' biome point patch P9.
 */
public final class ModernWorldgen {

    private static final Field PARENT = ReflectionHelper.findField(GenLayer.class, "parent", "field_75909_a");

    private ModernWorldgen() {
    }

    public static void init() {
        MinecraftForge.TERRAIN_GEN_BUS.register(ModernWorldgen.class);
    }

    /** After the backports' own layer changes (Village &amp; Pillage at HIGH, Update Aquatic's ocean mixing). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onInitBiomeGens(WorldTypeEvent.InitBiomeGens event) {
        GenLayer[] layers = event.getNewBiomeGens();
        if (layers == null || !StructurePlacements.isAtLeast("1.13")) {
            return;
        }
        int seeded = seedHillsRiverNoise(layers, event.getSeed());
        BackportCore.LOGGER.debug("Seeded {} hills layer river noise(s) ({})", seeded, event.getWorldType().getName());
        if (StructurePlacements.isAtLeast("1.15") && layers.length > 1 && layers[1] != null
                && layers[1].getClass() == GenLayerVoronoiZoom.class) {
            try {
                FuzzyZoomLayer zoom = new FuzzyZoomLayer((GenLayer) PARENT.get(layers[1]));
                zoom.initWorldGenSeed(event.getSeed());
                layers[1] = zoom;
                event.setNewBiomeGens(layers);
            } catch (IllegalAccessException e) {
                BackportCore.LOGGER.warn("Could not reach the Voronoi layer's map; the block-scale biome stays 1.12.2's", e);
            }
        }
    }

    /**
     * Seeds the river noise of every vanilla hills layer in the map with the world seed. It walks the whole
     * map, so it finds them under other mods' wrapping layers too.
     *
     * @return how many it seeded
     */
    static int seedHillsRiverNoise(GenLayer[] layers, long seed) {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        Deque<GenLayer> todo = new ArrayDeque<GenLayer>();
        for (GenLayer layer : layers) {
            if (layer != null) {
                todo.add(layer);
            }
        }
        int seeded = 0;
        try {
            while (!todo.isEmpty()) {
                GenLayer layer = todo.poll();
                if (!seen.add(layer)) {
                    continue;
                }
                for (Class<?> c = layer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    for (Field f : c.getDeclaredFields()) {
                        if (!GenLayer.class.isAssignableFrom(f.getType()) || Modifier.isStatic(f.getModifiers())) {
                            continue;
                        }
                        f.setAccessible(true);
                        GenLayer child = (GenLayer) f.get(layer);
                        if (child == null) {
                            continue;
                        }
                        // GenLayerHills' second parent (its river noise) is the field that is not 'parent'.
                        if (layer.getClass() == GenLayerHills.class && c == GenLayerHills.class) {
                            child.initWorldGenSeed(seed);
                            seeded++;
                        }
                        todo.add(child);
                    }
                }
            }
        } catch (IllegalAccessException e) {
            BackportCore.LOGGER.warn("Could not seed the hills layer's river noise; rare biome variants stay 1.12.2's", e);
        }
        return seeded;
    }

    /** After every backport's own generators: only vanilla's own are replaced. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInitMapGen(InitMapGenEvent event) {
        if (!StructurePlacements.isAtLeast("1.13")) {
            return;
        }
        switch (event.getType()) {
            case SCATTERED_FEATURE:
                if (event.getNewGen().getClass() == MapGenScatteredFeature.class) {
                    event.setNewGen(new ModernTemples((MapGenScatteredFeature) event.getNewGen()));
                }
                break;
            case OCEAN_MONUMENT:
                if (event.getNewGen().getClass() == StructureOceanMonument.class) {
                    event.setNewGen(new ModernMonument());
                }
                break;
            case STRONGHOLD:
                if (event.getNewGen() instanceof MapGenStronghold) {
                    strongholdBiomes((MapGenStronghold) event.getNewGen());
                }
                break;
            default:
                break;
        }
    }

    /** Applies the registered rulings to a stronghold generator's biome list (positions are placed later). */
    static void strongholdBiomes(MapGenStronghold stronghold) {
        for (Iterator<Biome> it = stronghold.allowedBiomes.iterator(); it.hasNext(); ) {
            if (Boolean.FALSE.equals(StructurePlacements.strongholdBiome(it.next()))) {
                it.remove();
            }
        }
        for (Biome biome : Biome.REGISTRY) {
            if (biome != null && Boolean.TRUE.equals(StructurePlacements.strongholdBiome(biome)) && !stronghold.allowedBiomes.contains(biome)) {
                stronghold.allowedBiomes.add(biome);
            }
        }
    }

}

package com.exiledradio.backportcore.terrain.rtg;

import com.exiledradio.backportcore.terrain.RtgBiomes.Template;

import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import rtg.api.RTGAPI;
import rtg.api.world.biome.IRealisticBiome;
import rtg.api.world.biome.RealisticBiomeBase;
import rtg.api.world.deco.collection.DecoCollectionOcean;
import rtg.api.world.surface.SurfaceBase;
import rtg.api.world.surface.SurfaceGeneric;
import rtg.api.world.terrain.TerrainBase;
import rtg.world.biome.realistic.vanilla.RealisticBiomeVanillaBirchForest;
import rtg.world.biome.realistic.vanilla.RealisticBiomeVanillaDeepOcean;
import rtg.world.biome.realistic.vanilla.RealisticBiomeVanillaForest;
import rtg.world.biome.realistic.vanilla.RealisticBiomeVanillaForestHills;
import rtg.world.biome.realistic.vanilla.RealisticBiomeVanillaJungle;
import rtg.world.biome.realistic.vanilla.RealisticBiomeVanillaJungleHills;
import rtg.world.biome.realistic.vanilla.RealisticBiomeVanillaOcean;
import rtg.world.biome.realistic.vanilla.RealisticBiomeVanillaPlains;
import rtg.world.biome.realistic.vanilla.RealisticBiomeVanillaRoofedForest;
import rtg.world.biome.realistic.vanilla.RealisticBiomeVanillaSwampland;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The only Core class that links against RTG, loaded from {@code RtgBiomes} with RTG installed. Compiled against the
 * Unofficial fork 1.1.0; every RTG type and member used here is the same in RTG 6.1 except the registration call and
 * the {@code ModCompat.Mods} enum's package, both found by reflection.
 */
public final class RtgRealisticBiomes {

    /** Read by {@link Realistic} while RTG's constructor calls its init methods (subclass fields are not set yet). */
    private static final Map<Biome, Template> TEMPLATES = new IdentityHashMap<Biome, Template>();

    private RtgRealisticBiomes() {
    }

    public static int register(Map<Biome, Template> requested) throws ReflectiveOperationException {
        Set<String> mods = new HashSet<String>();
        List<IRealisticBiome> made = new ArrayList<IRealisticBiome>();
        for (Map.Entry<Biome, Template> entry : requested.entrySet()) {
            ResourceLocation name = entry.getKey().getRegistryName();
            if (name == null) {
                continue;
            }
            if (mods.add(name.getNamespace())) {
                knownMod(name.getNamespace());
            }
            TEMPLATES.put(entry.getKey(), entry.getValue());
            made.add(new Realistic(entry.getKey()));
        }
        IRealisticBiome[] all = made.toArray(new IRealisticBiome[0]);
        try {
            // The Unofficial fork.
            RTGAPI.class.getMethod("addRTGBiomes", IRealisticBiome[].class).invoke(null, (Object) all);
        } catch (NoSuchMethodException e) {
            // RTG 6.1: RTGAPI.RTG_BIOMES is a BiomeMap with addBiomes.
            Object map = RTGAPI.class.getField("RTG_BIOMES").get(null);
            map.getClass().getMethod("addBiomes", IRealisticBiome[].class).invoke(map, (Object) all);
        }
        return all.length;
    }

    /**
     * A realistic biome keeps its settings in {@code config/RTG/biomes/<mod>/<biome>.cfg}, the mod looked up by the
     * biome's namespace in RTG's {@code ModCompat.Mods} enum (by constant name), and its constructor throws for a mod
     * the enum does not list. So the mod is added as a constant named after its id, with Forge's EnumHelper; RTG never
     * switches over the enum. {@code rtg.compat.ModCompat$Mods} in the Unofficial fork, {@code rtg.util} in 6.1.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void knownMod(String modid) throws ClassNotFoundException {
        Class mods;
        try {
            mods = Class.forName("rtg.compat.ModCompat$Mods");
        } catch (ClassNotFoundException e) {
            mods = Class.forName("rtg.util.ModCompat$Mods");
        }
        for (Object constant : mods.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(modid)) {
                return;
            }
        }
        ModContainer container = Loader.instance().getIndexedModList().get(modid);
        String folder = (container == null ? modid : container.getName()).replace(" ", "").replace("&", "And");
        EnumHelper.addEnum(mods, modid, new Class<?>[] {String.class}, folder);
    }

    /** One backport biome on its template's RTG terrain. */
    static final class Realistic extends RealisticBiomeBase {

        Realistic(Biome biome) {
            super(biome);
        }

        private Template template() {
            Template template = TEMPLATES.get(baseBiome());
            return template == null ? Template.PLAINS : template;
        }

        private boolean ocean() {
            return template() == Template.OCEAN || template() == Template.DEEP_OCEAN;
        }

        @Override
        public void initConfig() {
            if (ocean()) {
                // As RTG's vanilla oceans.
                getConfig().SURFACE_WATER_LAKE_MULT.set(0.0F);
                getConfig().ALLOW_RIVERS.set(false);
                getConfig().ALLOW_SCENIC_LAKES.set(false);
                getConfig().addProperty(getConfig().ALLOW_SPONGE).set(true);
            }
            getConfig().addProperty(getConfig().SURFACE_MIX_BLOCK).set("");
        }

        @Override
        public TerrainBase initTerrain() {
            switch (template()) {
                case OCEAN:
                    return new RealisticBiomeVanillaOcean.TerrainVanillaOcean();
                case DEEP_OCEAN:
                    return new RealisticBiomeVanillaDeepOcean.TerrainVanillaDeepOcean();
                case FOREST:
                    return new RealisticBiomeVanillaForest.TerrainVanillaForest();
                case FOREST_HILLS:
                    return new RealisticBiomeVanillaForestHills.TerrainVanillaForestHills();
                case BIRCH_FOREST:
                    return new RealisticBiomeVanillaBirchForest.TerrainVanillaBirchForest();
                case JUNGLE:
                    return new RealisticBiomeVanillaJungle.TerrainVanillaJungle();
                case JUNGLE_HILLS:
                    // RTG's own jungle hills numbers.
                    return new RealisticBiomeVanillaJungleHills.TerrainVanillaJungleHills(72.0F, 40.0F);
                case SWAMP:
                    return new RealisticBiomeVanillaSwampland.TerrainVanillaSwampland();
                case ROOFED_FOREST:
                    return new RealisticBiomeVanillaRoofedForest.TerrainVanillaRoofedForest();
                default:
                    return new RealisticBiomeVanillaPlains.TerrainVanillaPlains();
            }
        }

        @Override
        public SurfaceBase initSurface() {
            Biome biome = baseBiome();
            switch (template()) {
                case OCEAN:
                    return new RealisticBiomeVanillaOcean.SurfaceVanillaOcean(getConfig(), biome.topBlock,
                            biome.fillerBlock, Blocks.GRAVEL.getDefaultState(), 20.0F, 0.2F);
                case DEEP_OCEAN:
                    return new RealisticBiomeVanillaDeepOcean.SurfaceVanillaDeepOcean(getConfig(), biome.topBlock,
                            biome.fillerBlock, Blocks.CLAY.getDefaultState(), 20.0F, 0.1F);
                case SWAMP:
                    return new RealisticBiomeVanillaSwampland.SurfaceVanillaSwampland(getConfig(), biome.topBlock,
                            biome.fillerBlock);
                default:
                    return new SurfaceGeneric(getConfig(), biome.topBlock, biome.fillerBlock);
            }
        }

        @Override
        public void initDecos() {
            if (ocean()) {
                addDecoCollection(new DecoCollectionOcean(getConfig()));
            }
            // Land: none of RTG's; RTG runs the biome's own decorate afterwards.
        }
    }
}

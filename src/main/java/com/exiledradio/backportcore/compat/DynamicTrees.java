package com.exiledradio.backportcore.compat;

import com.exiledradio.backportcore.BackportCore;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Dynamic Trees (modid {@code dynamictrees}) support for every backport. Dynamic Trees grows its own trees in the
 * biomes its biome database says to and cancels 1.12.2's there; nothing here runs without it.
 *
 * <ul>
 *   <li>1.12.2's tree decoration steps aside by the biome that decorates the chunk: Dynamic Trees asks the biome at
 *       the chunk's corner, 1.12.2 decorates with the biome 16 blocks in, so next to a biome it leaves alone (a
 *       backport's cherry grove, pale garden, mangrove swamp ...) the neighbor's static oaks still grew beside its
 *       dynamic ones, and the backport's trees were cancelled where its biome decorated a chunk whose corner was
 *       Dynamic Trees'. Core re-answers the TREE, BIG_SHROOM and CACTUS events by the decorating biome
 *       ({@link DynamicTreesLink#decorate}).</li>
 *   <li>A backport that places trees itself, outside those events, asks {@link #replacesTrees}.</li>
 *   <li>Structures' trees (villages, camps) are Dynamic Trees' where it grows that tree ({@link #grow}).</li>
 *   <li>A backport's biome can get Dynamic Trees' trees with its own worldgen JSON ({@link #biomeData}).</li>
 * </ul>
 */
public final class DynamicTrees {

    public static final String MOD_ID = "dynamictrees";

    private static final List<ResourceLocation> BIOME_DATA = new ArrayList<ResourceLocation>();
    private static boolean linked;

    private DynamicTrees() {
    }

    /** Whether Dynamic Trees is installed and Core's link to it works. */
    public static boolean loaded() {
        return linked;
    }

    /** Core pre-init. */
    public static void preInit() {
        if (!Loader.isModLoaded(MOD_ID)) {
            return;
        }
        try {
            DynamicTreesLink.init(BIOME_DATA);
            linked = true;
            BackportCore.LOGGER.info("Dynamic Trees is installed: the backports' trees step aside where it grows its own.");
        } catch (LinkageError | RuntimeException e) {
            BackportCore.LOGGER.warn("Could not link to Dynamic Trees; static and dynamic trees may both generate.", e);
        }
    }

    /**
     * Adds a backport's Dynamic Trees worldgen JSON (Dynamic Trees' own format: {@code select} and {@code apply}
     * entries), read when Dynamic Trees fills its biome database, before the pack's
     * {@code config/dynamictrees/worldgen.json}, which still decides last. Pre-init; does nothing without Dynamic
     * Trees.
     *
     * @param json a resource, {@code new ResourceLocation(modid, "worldgen/dynamictrees.json")} for
     *             {@code assets/<modid>/worldgen/dynamictrees.json}
     */
    public static void biomeData(ResourceLocation json) {
        synchronized (BIOME_DATA) {
            BIOME_DATA.add(json);
        }
    }

    /**
     * @return whether Dynamic Trees grows its own trees in place of 1.12.2's in this biome of this world: its biome
     * database cancels 1.12.2's trees there ({@code cancelvanilla}) and the dimension is not blacklisted. False
     * without Dynamic Trees, on a client world, or when the link fails. A backport placing trees of its own outside
     * the TREE decoration event leaves them out where this is true.
     */
    public static boolean replacesTrees(World world, Biome biome) {
        if (!linked || world == null || world.isRemote || biome == null) {
            return false;
        }
        try {
            return DynamicTreesLink.replacesTrees(world, biome);
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /** {@link #replacesTrees(World, Biome)} for the biome at a block. */
    public static boolean replacesTrees(World world, BlockPos pos) {
        return linked && world != null && !world.isRemote && replacesTrees(world, world.getBiome(pos));
    }

    /**
     * @return whether Dynamic Trees grows its own cacti in place of 1.12.2's at a block: it replaces the trees there
     * and its {@code vanillaCactusWorldGen} is off
     */
    public static boolean replacesCactus(World world, BlockPos pos) {
        return linked && world != null && replacesCactus(world, world.getBiome(pos));
    }

    /** {@link #replacesCactus(World, BlockPos)} for a biome. */
    public static boolean replacesCactus(World world, Biome biome) {
        if (!replacesTrees(world, biome)) {
            return false;
        }
        try {
            return !DynamicTreesLink.vanillaCactus();
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    /**
     * Grows Dynamic Trees' version of one of today's vanilla trees where a structure places it (a village's or a
     * camp's tree), when Dynamic Trees replaces trees in the biome there and has that tree. The caller places its own
     * tree when this returns false.
     *
     * @param pos     where the sapling would stand; the tree roots in the block below
     * @param feature today's configured feature, with or without namespace and {@code _checked}/{@code _bees_*}:
     *                oak, fancy_oak, birch, super_birch, spruce, pine, mega_spruce, mega_pine, jungle_tree,
     *                mega_jungle_tree, acacia, dark_oak (1.12.2's {@code normal_tree}, {@code savanna_tree},
     *                {@code spruce_tree}, {@code pine_tree} too)
     * @return true if a dynamic tree was grown
     */
    public static boolean grow(World world, BlockPos pos, Random random, String feature) {
        if (!linked || world == null || world.isRemote) {
            return false;
        }
        String species = species(feature);
        if (species == null || !replacesTrees(world, pos)) {
            return false;
        }
        try {
            return DynamicTreesLink.grow(world, pos, random, species, radius(feature, random));
        } catch (LinkageError | RuntimeException e) {
            BackportCore.LOGGER.warn("Dynamic Trees could not grow {} at {}; the static tree is placed.", species, pos, e);
            return false;
        }
    }

    /**
     * The TREE, BIG_SHROOM or CACTUS decoration's result by the biome that decorates the chunk: DENY where Dynamic
     * Trees replaces that biome's trees; a DENY lifted where it does not but replaces the corner biome's (its own
     * handler's answer); otherwise unchanged.
     */
    public static Event.Result decide(boolean cornerReplaced, boolean decoratingReplaced, Event.Result current) {
        if (decoratingReplaced) {
            return Event.Result.DENY;
        }
        return current == Event.Result.DENY && cornerReplaced ? Event.Result.DEFAULT : current;
    }

    /** Dynamic Trees' species for one of today's vanilla tree features, or null where it has none. */
    public static String species(String feature) {
        String f = feature.toLowerCase(java.util.Locale.ROOT);
        int colon = f.indexOf(':');
        if (colon >= 0) {
            f = f.substring(colon + 1);
        }
        f = f.replace("_checked", "").replaceAll("_bees(_\\d+)?$", "").replace("_on_snow", "");
        switch (f) {
            case "oak":
            case "fancy_oak":
            case "normal_tree":
                return "oak";
            case "birch":
                return "birch";
            case "super_birch":
                return "tallbirch";
            case "spruce":
            case "pine":
            case "spruce_tree":
            case "pine_tree":
                return "spruce";
            case "mega_spruce":
            case "mega_pine":
                return "megaspruce";
            case "jungle_tree":
            case "jungle":
                return "jungle";
            case "mega_jungle_tree":
                return "megajungle";
            case "acacia":
            case "savanna_tree":
                return "acacia";
            case "dark_oak":
                return "darkoak";
            default:
                return null;
        }
    }

    /** Dynamic Trees sizes a tree by the radius of the circle it grows in (2 to 8): fancy and mega trees larger. */
    private static int radius(String feature, Random random) {
        String f = feature.toLowerCase(java.util.Locale.ROOT);
        if (f.contains("fancy") || f.contains("mega") || f.contains("dark_oak")) {
            return 6 + random.nextInt(3);
        }
        return 4 + random.nextInt(3);
    }
}

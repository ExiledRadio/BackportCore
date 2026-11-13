package com.exiledradio.backportcore.duplicate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The blocks the mod {@code quark} adds that the backports also add, under its own names: 1.14's stone
 * walls, stairs and slabs, smooth stone and smooth sandstone slabs, 1.13's wood trapdoors, buttons and
 * pressure plates and prismarine stairs and slabs, and 1.17's candles. Its half of each pair is hidden, as
 * for any other mod with the same blocks ({@link DuplicateBlocks}): the backports' blocks are the vanilla
 * ones, so they stay (closest to vanilla). Its blocks keep existing, so anything already built carries on,
 * and the blocks only it adds are untouched.
 *
 * <p>Each pair names the backport block's path and the other mod's. A pair is only a pair when both blocks
 * exist, so a backport that is missing, or has the block switched off, hides nothing. Every pair is offered
 * for each family mod id: whichever one registers the block (the owner, or the copy when the owner is
 * absent) is the one found.
 */
public final class QuarkPairs {

    static final String QUARK = "quark";

    /** The backports that can register these blocks. */
    private static final List<String> FAMILY = Arrays.asList("villagerbackport", "updateaquaticbackport",
            "cavesandcliffsbackport");

    private QuarkPairs() {
    }

    /** From Backport Core's pre-init. */
    public static void preInit() {
        for (String family : FAMILY) {
            DuplicateBlocks.register(family, QUARK, () -> DuplicateBlocks.Keep.OURS, QuarkPairs::pairs);
        }
        // Its nether basalt: a second block called Basalt, generated through the whole nether (some 75 times
        // as much as the 1.16 basalt of the nether's own biomes). With 1.16's nether there, only 1.16's basalt
        // generates; the other mod's block, its recipes and everything made from it stay.
        DuplicateBlocks.registerGenerator(NETHER_UPDATE, QUARK, () -> has(NETHER_UPDATE, "basalt")
                ? DuplicateBlocks.Keep.OURS : DuplicateBlocks.Keep.BOTH, "vazkii.quark.world.world.BasaltGenerator");
    }

    private static final String NETHER_UPDATE = "netherupdatebackport";

    private static boolean has(String modId, String path) {
        net.minecraft.util.ResourceLocation name = new net.minecraft.util.ResourceLocation(modId, path);
        return net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.containsKey(name);
    }

    static List<String[]> pairs() {
        List<String[]> pairs = new ArrayList<String[]>();
        // 1.14's walls.
        pair(pairs, "granite_wall", "stone_granite_wall");
        pair(pairs, "diorite_wall", "stone_diorite_wall");
        pair(pairs, "andesite_wall", "stone_andesite_wall");
        pair(pairs, "sandstone_wall", "sandstone_wall");
        pair(pairs, "red_sandstone_wall", "red_sandstone_wall");
        pair(pairs, "stone_brick_wall", "stonebrick_wall");
        pair(pairs, "mossy_stone_brick_wall", "stonebrick_mossy_wall");
        pair(pairs, "brick_wall", "brick_wall");
        pair(pairs, "prismarine_wall", "prismarine_rough_wall");
        pair(pairs, "end_stone_brick_wall", "end_bricks_wall");
        // 1.14's stairs and slabs (each slab with its double), and 1.13's prismarine ones.
        pair(pairs, "stone_stairs", "stone_stairs");
        stairsAndSlab(pairs, "granite", "stone_granite");
        stairsAndSlab(pairs, "diorite", "stone_diorite");
        stairsAndSlab(pairs, "andesite", "stone_andesite");
        stairsAndSlab(pairs, "end_stone_brick", "end_bricks");
        stairsAndSlab(pairs, "red_nether_brick", "red_nether_brick");
        stairsAndSlab(pairs, "mossy_cobblestone", "cobblestone_mossy");
        stairsAndSlab(pairs, "mossy_stone_brick", "stonebrick_mossy");
        stairsAndSlab(pairs, "prismarine", "prismarine");
        stairsAndSlab(pairs, "prismarine_brick", "prismarine_bricks");
        stairsAndSlab(pairs, "dark_prismarine", "prismarine_dark");
        slab(pairs, "smooth_sandstone", "sandstone_smooth");
        slab(pairs, "smooth_red_sandstone", "red_sandstone_smooth");
        // 1.14's smooth stone (1.12.2's double stone slab, as a block of its own).
        pair(pairs, "smooth_stone", "polished_stone");
        // 1.13's trapdoors, buttons and pressure plates in the five woods that are not oak.
        for (String wood : new String[] {"spruce", "birch", "jungle", "acacia", "dark_oak"}) {
            pair(pairs, wood + "_trapdoor", wood + "_trapdoor");
            pair(pairs, wood + "_button", wood + "_button");
            pair(pairs, wood + "_pressure_plate", wood + "_pressure_plate");
        }
        // 1.17's candles. The other mod's are one block with a color per metadata; hiding it hides all sixteen.
        pair(pairs, "white_candle", "candle");
        return pairs;
    }

    private static void pair(List<String[]> pairs, String ours, String theirs) {
        pairs.add(new String[] {ours, theirs});
    }

    private static void stairsAndSlab(List<String[]> pairs, String ours, String theirs) {
        pair(pairs, ours + "_stairs", theirs + "_stairs");
        slab(pairs, ours, theirs);
    }

    private static void slab(List<String[]> pairs, String ours, String theirs) {
        pair(pairs, ours + "_slab", theirs + "_slab");
        pair(pairs, ours + "_double_slab", theirs + "_slab_double");
    }
}

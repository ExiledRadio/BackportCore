package com.exiledradio.backportcore.ownership;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Which backport owns each block more than one of them needs (the family plan's Rule 1).
 *
 * <p>Some blocks belong to one update but are needed by another: 1.14's villages are built from 1.13's
 * stripped logs and blue ice, so Village &amp; Pillage ships them. The mod that ships a block first
 * owns it, unless it fits another backport better (sea pickles are Update Aquatic's, though V&amp;P
 * shipped them first), and every other backport asks the same question the same way:
 * <ul>
 * <li><b>The owner is installed</b> ({@link #ownerProvides}): register nothing, and use the owner's
 * block ({@link #block}).</li>
 * <li><b>It isn't</b>: register a copy built from the same code, with the same states and metadata,
 * under your own mod id ({@link #shouldRegister} answers true).</li>
 * </ul>
 * When the owner is added to a world later, it remaps those copies onto its own blocks as the world
 * loads; because the states match, that is a rename, not a conversion.
 *
 * <h2>Timing</h2>
 * Ask from your block registry event or later. An owner whose blocks can be switched off says so with
 * {@link #provides} from its constructor or pre-init, before any registry event; an owner that never
 * says is taken to provide every block it owns whenever it is installed.
 *
 * <p>Names are block paths without a mod id, the same under every mod: {@code stripped_oak_log}.
 */
public final class BlockOwnership {

    /** Block path to owner mod id. */
    private static final Map<String, String> OWNERS = new LinkedHashMap<String, String>();

    /** Owner mod id to whether it is registering its blocks in this game. */
    private static final Map<String, BooleanSupplier> PROVIDES = new HashMap<String, BooleanSupplier>();

    /** Block path to whether its owner registers that one block (an owner whose parts switch off one by one). */
    private static final Map<String, BooleanSupplier> PROVIDES_NAME = new HashMap<String, BooleanSupplier>();

    static {
        // Village & Pillage Backport (1.14) - 1.13's blocks its villages are built from.
        String vp = "villagerbackport";
        for (String wood : new String[] {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"}) {
            own(vp, "stripped_" + wood + "_log", "stripped_" + wood + "_wood");
            if (!"oak".equals(wood)) {
                // 1.12.2 already has oak's.
                own(vp, wood + "_trapdoor", wood + "_button", wood + "_pressure_plate");
            }
        }
        own(vp, "blue_ice");
        // The smithing table (1.14), which Nether Update (netherite) and Trails & Tales (templates) copy.
        own(vp, "smithing_table");

        // Update Aquatic Backport (1.13). When two backports need the same block, it belongs to the one it
        // fits best: sea pickles are an ocean block, moved here from Village & Pillage on 2026-11-08. V&P's
        // villages and wandering trader use Update Aquatic's when it is installed.
        own("updateaquaticbackport", "sea_pickle");

        // Caves & Cliffs Backport (1.17): tuff and the copper set, which Tricky Trials (1.21) copies.
        String cc = "cavesandcliffsbackport";
        own(cc, "tuff", "copper_ore", "raw_copper_block");
        for (String stage : new String[] {"", "exposed_", "weathered_", "oxidized_"}) {
            String block = stage.isEmpty() ? "copper_block" : stage + "copper";
            for (String waxed : new String[] {"", "waxed_"}) {
                own(cc, waxed + block, waxed + stage + "cut_copper", waxed + stage + "cut_copper_stairs",
                        waxed + stage + "cut_copper_slab", waxed + stage + "cut_copper_double_slab");
            }
        }
        // Amethyst and its geodes' layers, which Trails & Tales (1.20) copies for the calibrated sculk sensor and
        // the amethyst trim.
        own(cc, "amethyst_block", "budding_amethyst", "small_amethyst_bud", "medium_amethyst_bud",
                "large_amethyst_bud", "amethyst_cluster", "calcite", "smooth_basalt");
    }

    private BlockOwnership() {
    }

    private static void own(String modId, String... paths) {
        for (String path : paths) {
            OWNERS.put(path, modId);
        }
    }

    /**
     * Adds blocks to the table. The first mod to claim a block keeps it, so call this only for blocks
     * your mod ships first; a claim on a block someone else owns is ignored.
     *
     * @return true if every block was yours to claim
     */
    public static synchronized boolean declare(String ownerModId, String... paths) {
        boolean all = true;
        for (String path : paths) {
            String owner = OWNERS.get(path);
            if (owner == null || ownerModId.startsWith(owner + ":")) {
                // An owner may hand its own block to a pseudo-owner of its own ("modid:switched_off", never a
                // loaded mod) to say it does not register it in this game.
                OWNERS.put(path, ownerModId);
            } else if (!owner.equals(ownerModId)) {
                all = false;
            }
        }
        return all;
    }

    /**
     * For an owner whose blocks can be switched off: whether it registers them in this game. Read
     * when another mod asks, so a config value works.
     */
    public static synchronized void provides(String ownerModId, BooleanSupplier registering) {
        PROVIDES.put(ownerModId, registering);
    }

    /**
     * For an owner whose blocks switch off one part at a time: whether it registers these blocks in this game.
     * Read when another mod asks; wins over {@link #provides} for these names.
     */
    public static synchronized void provides(String ownerModId, BooleanSupplier registering, String... paths) {
        for (String path : paths) {
            PROVIDES_NAME.put(path, registering);
        }
    }

    /** @return the mod that owns this block, or null if no backport claims it */
    @Nullable
    public static synchronized String owner(String path) {
        return OWNERS.get(path);
    }

    /** @return true if the block's owner is installed and registering its blocks */
    public static synchronized boolean ownerProvides(String path) {
        String owner = OWNERS.get(path);
        if (owner == null || !Loader.isModLoaded(owner)) {
            return false;
        }
        BooleanSupplier one = PROVIDES_NAME.get(path);
        if (one != null) {
            return one.getAsBoolean();
        }
        BooleanSupplier registering = PROVIDES.get(owner);
        return registering == null || registering.getAsBoolean();
    }

    /**
     * @return true if {@code modId} should register its own copy of this block: it owns the block, or
     * the owner isn't there to
     */
    public static boolean shouldRegister(String modId, String path) {
        return modId.equals(owner(path)) || !ownerProvides(path);
    }

    /**
     * @return the owner's block, once registered, if the owner provides it; otherwise null. Ask from
     * {@code RegistryEvent.Register<Item>} or later to be sure every mod's blocks are in.
     */
    @Nullable
    public static Block block(String path) {
        if (!ownerProvides(path)) {
            return null;
        }
        ResourceLocation name = new ResourceLocation(owner(path), path);
        Block block = ForgeRegistries.BLOCKS.containsKey(name) ? ForgeRegistries.BLOCKS.getValue(name) : null;
        return block == null || block == Blocks.AIR ? null : block;
    }

    /**
     * @return the block to use under this name: the owner's if it provides one, otherwise
     * {@code fallbackModId}'s copy, or null if neither is registered
     */
    @Nullable
    public static Block resolve(String path, String fallbackModId) {
        Block owned = block(path);
        if (owned != null) {
            return owned;
        }
        ResourceLocation name = new ResourceLocation(fallbackModId, path);
        Block block = ForgeRegistries.BLOCKS.containsKey(name) ? ForgeRegistries.BLOCKS.getValue(name) : null;
        return block == null || block == Blocks.AIR ? null : block;
    }

    /** @return every claimed block and its owner, for listings and tests */
    public static synchronized Map<String, String> table() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, String>(OWNERS));
    }
}

package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.duplicate.DuplicateBlocks;
import com.exiledradio.backportcore.ownership.BlockOwnership;
import com.exiledradio.backportcore.util.BlockStates;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Block states of today's Minecraft, as structure files write them, to 1.12.2 states.
 *
 * <h2>Where a block comes from</h2>
 * For {@code minecraft:<path>} with its properties, the first of these that has it:
 * <ol>
 * <li>a mapping a mod registered here ({@link #map});</li>
 * <li>the backport that owns the block, through {@link BlockOwnership} (Rule 1);</li>
 * <li>a family mod's block of the same path, from the mods listed with {@link #source}
 * ({@code villagerbackport} is listed already);</li>
 * <li>1.12.2's own block: Minecraft's Flattening table read backwards, plus the blocks 1.13 split out
 * of a tile entity (bed and banner colors, skull types, flower pot contents, note block notes);</li>
 * <li>a decorative copy Core supplies itself ({@code supply/SuppliedBlocks}, since 1.5.0);</li>
 * <li>a stand-in named with {@link #fallback} (a few are built in: wood trapdoors, buttons and
 * pressure plates fall back to oak's, stripped logs to logs, blue ice to packed ice, 1.14's plain
 * stone slab to the smooth one, kelp and seagrass to plain water).</li>
 * </ol>
 * Anything else is left out of the structure, and named once in the log. Properties carry across by
 * name; a hidden duplicate ({@link DuplicateBlocks}) becomes its visible twin.
 *
 * <h2>Names</h2>
 * Today's names ({@code dirt_path}, {@code short_grass}, {@code oak_sign}, {@code smooth_stone_slab},
 * {@code iron_chain}): a structure file saved before a rename is brought up to date from its
 * {@code DataVersion} first. A mapping registered for {@code minecraft:stone_slab} therefore means
 * 1.14's plain stone slab. Names without a namespace are {@code minecraft:}.
 *
 * <p>Register in pre-init or init. Resolving happens when a structure is first placed, after every
 * block is registered.
 */
public final class ModernPalette {

    /** A palette entry read into 1.12.2 terms. */
    public static final class Resolved {
        /** The state to place, or null when nothing provides the block. */
        @Nullable
        public final IBlockState state;
        /** Tile entity data 1.13 moved into the block name (a bed's color), to merge into the block's own. */
        @Nullable
        public final NBTTagCompound tile;
        /** True when the block stands in water: {@code waterlogged=true}, or a block that is always in water. */
        public final boolean water;
        /**
         * Which step gave the block: {@code mapping}, {@code owner}, {@code family}, {@code 1.12.2}, {@code supplied},
         * {@code stand-in <name>}; null when nothing did. For reports and tests. Since 1.5.0.
         */
        @Nullable
        public final String via;

        Resolved(@Nullable IBlockState state, @Nullable NBTTagCompound tile, boolean water) {
            this(state, tile, water, null);
        }

        Resolved(@Nullable IBlockState state, @Nullable NBTTagCompound tile, boolean water, @Nullable String via) {
            this.state = state;
            this.tile = tile;
            this.water = water;
            this.via = state == null ? null : via;
        }

        Resolved via(String step) {
            return new Resolved(this.state, this.tile, this.water, step);
        }
    }

    /** Blocks that only exist in water, so a structure's copy always has its water. */
    private static final Set<String> ALWAYS_WET = new HashSet<String>(Arrays.asList(
            "minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass", "minecraft:tall_seagrass",
            "minecraft:bubble_column"));

    private static final Map<String, Function<Map<String, String>, IBlockState>> MAPPINGS =
            new HashMap<String, Function<Map<String, String>, IBlockState>>();
    private static final List<String> SOURCES = new ArrayList<String>(Collections.singletonList("villagerbackport"));
    private static final Map<String, String> FALLBACKS = new HashMap<String, String>();
    private static final Map<String, String> LOOT_TABLES = new HashMap<String, String>();
    private static final Map<String, String> ENTITIES = new HashMap<String, String>();
    private static final Set<String> REPORTED = Collections.synchronizedSet(new HashSet<String>());

    static {
        for (String wood : new String[] {"spruce", "birch", "jungle", "acacia", "dark_oak"}) {
            FALLBACKS.put("minecraft:" + wood + "_trapdoor", "minecraft:oak_trapdoor");
            FALLBACKS.put("minecraft:" + wood + "_button", "minecraft:oak_button");
            FALLBACKS.put("minecraft:" + wood + "_pressure_plate", "minecraft:oak_pressure_plate");
        }
        for (String wood : new String[] {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"}) {
            FALLBACKS.put("minecraft:stripped_" + wood + "_log", "minecraft:" + wood + "_log");
            FALLBACKS.put("minecraft:stripped_" + wood + "_wood", "minecraft:" + wood + "_wood");
        }
        FALLBACKS.put("minecraft:blue_ice", "minecraft:packed_ice");
        // 1.14's plain stone slab, where no backport has one: 1.12.2's stone slab is the smooth one.
        FALLBACKS.put("minecraft:stone_slab", "minecraft:smooth_stone_slab");
        FALLBACKS.put("minecraft:cave_air", "minecraft:air");
        FALLBACKS.put("minecraft:void_air", "minecraft:air");
        for (String wet : ALWAYS_WET) {
            FALLBACKS.put(wet, "minecraft:water");
        }
    }

    private ModernPalette() {
    }

    /**
     * Builds this block with your own code. The function gets the file's properties (today's names,
     * {@code waterlogged} included) and returns the state, or null to leave the position alone.
     * Wins over every other source. The last mapping registered for a name is used.
     */
    public static synchronized void map(String modernName, Function<Map<String, String>, IBlockState> to) {
        MAPPINGS.put(full(modernName), to);
    }

    /** Builds this block as {@code block}, carrying properties across by name. */
    public static void map(String modernName, Supplier<Block> block) {
        map(modernName, props -> {
            Block b = block.get();
            return b == null ? null : withProps(b.getDefaultState(), props);
        });
    }

    /**
     * Lists a family mod whose blocks are named as today's Minecraft names them: its
     * {@code <modid>:lantern} then serves {@code minecraft:lantern} when no owner provides one. Order
     * of listing is order of asking.
     */
    public static synchronized void source(String modId) {
        if (!SOURCES.contains(modId)) {
            SOURCES.add(modId);
        }
    }

    /**
     * Names a stand-in, used only when nothing provides {@code modernName}: say {@code minecraft:tuff}
     * for {@code minecraft:tuff_bricks}. The stand-in is resolved the same way, properties included.
     */
    public static synchronized void fallback(String modernName, String insteadModernName) {
        FALLBACKS.put(full(modernName), full(insteadModernName));
    }

    /**
     * Gives a loot table a 1.12.2 name: chests a structure file names {@code from} get {@code to}.
     * Loot tables of today's names that 1.12.2 also has (dungeons and the like) need nothing.
     */
    public static synchronized void lootTable(String from, ResourceLocation to) {
        LOOT_TABLES.put(full(from), to.toString());
    }

    /**
     * Gives an entity of a structure file a 1.12.2 id: {@code minecraft:piglin} in a bastion's mob piece
     * spawns as {@code to}. Since 1.1.0. Entities 1.12.2 has under the same id need nothing; others
     * with no id here are left out, as 1.12.2 leaves out any entity it does not know.
     */
    public static synchronized void entity(String from, ResourceLocation to) {
        ENTITIES.put(full(from), to.toString());
    }

    /** @return the entity id to spawn for an id a structure file gives. Since 1.1.0. */
    public static synchronized String entity(String fromFile) {
        String mapped = ENTITIES.get(full(fromFile));
        return mapped == null ? fromFile : mapped;
    }

    /** @return the loot table to use for a name a structure file gives */
    public static synchronized String lootTable(String fromFile) {
        String mapped = LOOT_TABLES.get(full(fromFile));
        return mapped == null ? fromFile : mapped;
    }

    /**
     * @param name a block name as the file has it
     * @param props its properties as the file has them
     * @param dataVersion the file's {@code DataVersion}; 0 for 1.13.2's names
     */
    public static Resolved resolve(String name, Map<String, String> props, int dataVersion) {
        Map<String, String> p = new LinkedHashMap<String, String>(props);
        String modern = Flattening.upgrade(full(name), p, dataVersion);
        boolean water = "true".equals(p.get("waterlogged")) || ALWAYS_WET.contains(modern);
        Resolved found = resolve(modern, p, water, 0);
        if (found.state == null && REPORTED.add(modern)) {
            BackportCore.LOGGER.info("No block for {} in 1.12.2 or any installed backport: structures leave it out.", modern);
        }
        return found;
    }

    /**
     * Reads a block state as today's commands and jigsaw blocks write it,
     * {@code minecraft:oak_stairs[facing=east,half=top]}.
     */
    public static Resolved resolve(String stateText) {
        Map<String, String> props = new LinkedHashMap<String, String>();
        String name = Flattening.parse(stateText.trim(), props);
        return resolve(name, props, Integer.MAX_VALUE);
    }

    /** A name already brought up to date ({@code minecraft:iron_chain}), default properties, nothing logged. Since 1.5.0. */
    public static Resolved resolveUpToDate(String modern, Map<String, String> props) {
        return resolve(modern, new LinkedHashMap<String, String>(props), false, 0);
    }

    private static Resolved resolve(String modern, Map<String, String> props, boolean water, int depth) {
        Function<Map<String, String>, IBlockState> mapping;
        String fallback;
        List<String> sources;
        synchronized (ModernPalette.class) {
            mapping = MAPPINGS.get(modern);
            fallback = FALLBACKS.get(modern);
            sources = new ArrayList<String>(SOURCES);
        }
        if (mapping != null) {
            return new Resolved(mapping.apply(Collections.unmodifiableMap(props)), null, water, "mapping");
        }

        String path = new ResourceLocation(modern).getPath();
        boolean vanilla = modern.startsWith("minecraft:");
        if (vanilla) {
            Block owned = BlockOwnership.block(path);
            if (owned != null) {
                return new Resolved(family(owned, props), null, water, "owner");
            }
            for (String source : sources) {
                Block block = registered(source + ":" + path);
                if (block != null) {
                    return new Resolved(family(block, props), null, water, "family");
                }
            }
            Resolved legacy = LegacyTiles.resolve(path, props, water);
            if (legacy != null) {
                return legacy.via("1.12.2");
            }
            IBlockState flattened = flattened(modern, props);
            if (flattened != null) {
                // Not carried across by name: a 1.13 name can mean something else in 1.12.2 (a floor
                // lever's facing). The closest 1.12.2 combination already has what carries over.
                return new Resolved(DuplicateBlocks.preferred(flattened, props), null, water, "1.12.2");
            }
            // A decorative copy Core supplies (supply/SuppliedBlocks), when nothing above has the block.
            Block supplied = com.exiledradio.backportcore.supply.SuppliedBlocks.block(path);
            if (supplied != null) {
                return new Resolved(family(supplied, props), null, water, "supplied");
            }
        } else {
            Block block = registered(modern);
            if (block != null) {
                return new Resolved(finish(block.getDefaultState(), props), null, water, "mod");
            }
        }
        if (fallback != null && depth < 8) {
            Resolved standIn = resolve(fallback, props, water, depth + 1);
            return standIn.via != null && standIn.via.startsWith("stand-in") ? standIn : standIn.via("stand-in " + fallback);
        }
        return new Resolved(null, null, water);
    }

    @Nullable
    private static Block registered(String name) {
        ResourceLocation id = new ResourceLocation(name);
        if (!ForgeRegistries.BLOCKS.containsKey(id)) {
            return null;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(id);
        return block == Blocks.AIR && !"minecraft:air".equals(name) ? null : block;
    }

    /**
     * 1.12.2's state for a block 1.12.2 had: the id:meta whose 1.13 form matches the most of the
     * file's properties, then the combination of that id:meta closest to them (stairs' shapes, fence
     * connections).
     */
    @Nullable
    private static IBlockState flattened(String modern, Map<String, String> fileProps) {
        List<Flattening.Entry> entries = Flattening.entries(modern);
        if (entries.isEmpty()) {
            return null;
        }
        // A property the file leaves out has the block's default value, as in the game: a bare
        // minecraft:stone_slab is a bottom half, not the first of its id:metas (the double slab).
        Map<String, String> props = new LinkedHashMap<String, String>(Flattening.defaults(modern));
        props.putAll(fileProps);
        // Matches minus mismatches: an id:meta's 1.13 state also carries properties 1.12.2 worked out
        // from its neighbors (a stair's shape is always "straight" there), which a file may differ on.
        Flattening.Entry best = entries.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (Flattening.Entry entry : entries) {
            int score = 0;
            for (Map.Entry<String, String> want : entry.modern.entrySet()) {
                String have = props.get(want.getKey());
                if (have != null) {
                    score += similarity(want.getKey(), want.getValue(), have);
                }
            }
            if (score > bestScore) {
                best = entry;
                bestScore = score;
            }
        }
        Block block = registered(best.legacyName);
        if (block == null) {
            return null;
        }
        Map<String, String> legacy = best.legacy.get(0);
        int legacyScore = -1;
        for (Map<String, String> candidate : best.legacy) {
            int score = 0;
            for (Map.Entry<String, String> e : candidate.entrySet()) {
                String have = props.get(e.getKey());
                if (have != null) {
                    score += sameValue(e.getKey(), e.getValue(), have) ? 2 : 0;
                } else if (e.getValue().equals(best.modern.get(e.getKey()))) {
                    // Not in the file: the id's own 1.13 value (a stair's "straight").
                    score++;
                }
            }
            if (score > legacyScore) {
                legacy = candidate;
                legacyScore = score;
            }
        }
        IBlockState state = block.getDefaultState();
        for (Map.Entry<String, String> e : legacy.entrySet()) {
            state = BlockStates.withOrSame(state, e.getKey(), e.getValue());
        }
        return state;
    }

    /**
     * 2 for the same value, 1 for the opposite facing on the same axis (1.12.2 has only two floor lever
     * orientations where today's game has four), -1 otherwise.
     */
    private static int similarity(String key, String a, String b) {
        if (sameValue(key, a, b)) {
            return 2;
        }
        net.minecraft.util.EnumFacing fa = net.minecraft.util.EnumFacing.byName(a);
        net.minecraft.util.EnumFacing fb = net.minecraft.util.EnumFacing.byName(b);
        return fa != null && fb != null && fa.getAxis() == fb.getAxis() ? 1 : -1;
    }

    /** Walls' sides became none/low/tall in 1.16 [schema 2503]; 1.13 wrote false/true. */
    private static boolean sameValue(String key, String a, String b) {
        return a.equals(b) || wallSide(key, a).equals(wallSide(key, b));
    }

    private static String wallSide(String key, String value) {
        if ("north".equals(key) || "south".equals(key) || "east".equals(key) || "west".equals(key)) {
            if ("none".equals(value)) {
                return "false";
            }
            if ("low".equals(value) || "tall".equals(value)) {
                return "true";
            }
        }
        return value;
    }

    /**
     * A family mod's block for a file's state. Slabs follow the family's 1.12.2 convention: {@code <name>_slab} is
     * the half, with {@code half=bottom|top}, and {@code <name>_double_slab} (same mod) is the double; today's
     * {@code type} picks between them. Every other block carries properties across by name.
     */
    private static IBlockState family(Block block, Map<String, String> props) {
        String type = props.get("type");
        IBlockState state = block.getDefaultState();
        if (type == null || !state.getPropertyKeys().contains(BlockSlab.HALF)) {
            return finish(state, props);
        }
        Map<String, String> rest = new LinkedHashMap<String, String>(props);
        rest.remove("type");
        if ("double".equals(type)) {
            ResourceLocation id = block.getRegistryName();
            if (id != null && id.getPath().endsWith("_slab")) {
                String path = id.getPath();
                Block full = registered(id.getNamespace() + ":" + path.substring(0, path.length() - "_slab".length()) + "_double_slab");
                if (full != null) {
                    return finish(full.getDefaultState(), rest);
                }
            }
            // No double block in this mod: the half is the closest it has.
            rest.put("half", "bottom");
        } else {
            rest.put("half", "top".equals(type) ? "top" : "bottom");
        }
        return finish(state, rest);
    }

    /** Carries properties across by name (wall sides as 1.12.2 writes them), then prefers a visible twin. */
    private static IBlockState finish(IBlockState state, Map<String, String> props) {
        return DuplicateBlocks.preferred(withProps(state, props), props);
    }

    static IBlockState withProps(IBlockState state, Map<String, String> props) {
        IBlockState out = state;
        for (Map.Entry<String, String> e : props.entrySet()) {
            if ("waterlogged".equals(e.getKey())) {
                continue;
            }
            IBlockState next = BlockStates.with(out, e.getKey(), e.getValue());
            if (next == null) {
                next = BlockStates.with(out, e.getKey(), wallSide(e.getKey(), e.getValue()));
            }
            if (next != null) {
                out = next;
            }
        }
        return out;
    }

    static String full(String name) {
        return name.indexOf(':') < 0 ? "minecraft:" + name : name;
    }

    /** Blocks 1.13 split out of a tile entity: the color, type or contents moves back into the tile. */
    static final class LegacyTiles {

        private static final String[] SKULLS = {"skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head",
                "creeper_head", "dragon_head"};
        private static final String[] WALL_SKULLS = {"skeleton_wall_skull", "wither_skeleton_wall_skull", "zombie_wall_head",
                "player_wall_head", "creeper_wall_head", "dragon_wall_head"};

        private static final Map<String, String[]> POTTED = new HashMap<String, String[]>();

        static {
            String[] woods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
            for (int i = 0; i < woods.length; i++) {
                POTTED.put(woods[i] + "_sapling", new String[] {"minecraft:sapling", String.valueOf(i)});
            }
            POTTED.put("fern", new String[] {"minecraft:tallgrass", "2"});
            POTTED.put("dandelion", new String[] {"minecraft:yellow_flower", "0"});
            String[] flowers = {"poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip",
                    "pink_tulip", "oxeye_daisy"};
            for (int i = 0; i < flowers.length; i++) {
                POTTED.put(flowers[i], new String[] {"minecraft:red_flower", String.valueOf(i)});
            }
            POTTED.put("red_mushroom", new String[] {"minecraft:red_mushroom", "0"});
            POTTED.put("brown_mushroom", new String[] {"minecraft:brown_mushroom", "0"});
            POTTED.put("dead_bush", new String[] {"minecraft:deadbush", "0"});
            POTTED.put("cactus", new String[] {"minecraft:cactus", "0"});
        }

        private LegacyTiles() {
        }

        @Nullable
        static Resolved resolve(String path, Map<String, String> props, boolean water) {
            for (EnumDyeColor color : EnumDyeColor.values()) {
                String prefix = color == EnumDyeColor.SILVER ? "light_gray" : color.getName();
                if (path.equals(prefix + "_bed")) {
                    NBTTagCompound tile = new NBTTagCompound();
                    tile.setInteger("color", color.getMetadata());
                    return new Resolved(withProps(Blocks.BED.getDefaultState(), props), tile, water);
                }
                if (path.equals(prefix + "_banner") || path.equals(prefix + "_wall_banner")) {
                    NBTTagCompound tile = new NBTTagCompound();
                    tile.setInteger("Base", color.getDyeDamage());
                    Block block = path.endsWith("_wall_banner") ? Blocks.WALL_BANNER : Blocks.STANDING_BANNER;
                    return new Resolved(withProps(block.getDefaultState(), props), tile, water);
                }
            }
            for (int type = 0; type < SKULLS.length; type++) {
                boolean wall = path.equals(WALL_SKULLS[type]);
                if (wall || path.equals(SKULLS[type])) {
                    NBTTagCompound tile = new NBTTagCompound();
                    tile.setByte("SkullType", (byte) type);
                    IBlockState state = Blocks.SKULL.getDefaultState();
                    if (wall) {
                        state = BlockStates.withOrSame(state, "facing", props.containsKey("facing") ? props.get("facing") : "north");
                    } else {
                        state = BlockStates.withOrSame(state, "facing", "up");
                        tile.setByte("Rot", (byte) parse(props.get("rotation")));
                    }
                    return new Resolved(state, tile, water);
                }
            }
            if ("flower_pot".equals(path)) {
                return new Resolved(Blocks.FLOWER_POT.getDefaultState(), new NBTTagCompound(), water);
            }
            if (path.startsWith("potted_")) {
                String[] plant = POTTED.get(path.substring("potted_".length()));
                if (plant == null) {
                    return null;
                }
                NBTTagCompound tile = new NBTTagCompound();
                tile.setString("Item", plant[0]);
                tile.setInteger("Data", Integer.parseInt(plant[1]));
                return new Resolved(Blocks.FLOWER_POT.getDefaultState(), tile, water);
            }
            if ("note_block".equals(path)) {
                NBTTagCompound tile = new NBTTagCompound();
                tile.setByte("note", (byte) parse(props.get("note")));
                return new Resolved(Blocks.NOTEBLOCK.getDefaultState(), tile, water);
            }
            if ("attached_pumpkin_stem".equals(path) || "attached_melon_stem".equals(path)) {
                Block stem = path.contains("pumpkin") ? Blocks.PUMPKIN_STEM : Blocks.MELON_STEM;
                IBlockState state = BlockStates.withOrSame(stem.getDefaultState(), "age", "7");
                return new Resolved(withProps(state, props), null, water);
            }
            if ("pumpkin".equals(path)) {
                // 1.13's uncarved pumpkin has no face; 1.12.2's pumpkin always shows one.
                return new Resolved(Blocks.PUMPKIN.getDefaultState(), null, water);
            }
            return null;
        }

        private static int parse(@Nullable String value) {
            try {
                return value == null ? 0 : Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}

package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Today's structure processors as Mojang's data files write them ({@code worldgen/processor_list}),
 * read into {@link TemplateProcessor}s, plus the ones ruined portals build in code.
 *
 * <p>Each works as 1.16.5's does, random for random: a processor draws from its own random seeded
 * by the block's world position ({@code new Random(Mth.getSeed(pos))}), so the same block always
 * comes out the same, whichever chunk places it. Types read: {@code rule} (with
 * {@code always_true}, {@code block_match}, {@code blockstate_match}, {@code tag_match},
 * {@code random_block_match}, {@code random_blockstate_match} tests and {@code always_true},
 * {@code linear_pos}, {@code axis_aligned_linear_pos} position tests), {@code block_ignore},
 * {@code block_rot}, {@code gravity}, {@code block_age}, {@code blackstone_replace},
 * {@code lava_submerged_block}, {@code jigsaw_replacement} and {@code nop} (both of which
 * {@link ModernTemplate} does already). An unknown type is logged once and skipped.
 */
public final class ModernProcessors {

    private static final Map<String, Set<String>> TAGS = new HashMap<String, Set<String>>();
    private static final Set<String> REPORTED = Collections.synchronizedSet(new HashSet<String>());

    static {
        List<String> doors = new ArrayList<String>();
        for (String wood : new String[] {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "crimson", "warped"}) {
            doors.add("minecraft:" + wood + "_door");
        }
        doors.add("minecraft:iron_door");
        TAGS.put("minecraft:doors", new HashSet<String>(doors));
    }

    private ModernProcessors() {
    }

    /** Adds block names to a tag that {@code tag_match} tests read (1.16's {@code minecraft:doors} is built in). */
    public static synchronized void tag(String tag, String... modernNames) {
        Set<String> names = TAGS.computeIfAbsent(ModernPalette.full(tag), k -> new HashSet<String>());
        for (String name : modernNames) {
            names.add(ModernPalette.full(name));
        }
    }

    // ---- the processor list

    /** A processor list read from a file: its processors, built per piece (position tests need the structure's pivot). */
    public static final class ProcessorList {
        final List<Spec> specs;
        @Nullable
        final Gravity gravity;

        ProcessorList(List<Spec> specs, @Nullable Gravity gravity) {
            this.specs = specs;
            this.gravity = gravity;
        }

        /**
         * @param pivot today's placement pivot: the middle of the structure's first piece at its floor
         *              ({@code StructureStart.placeInChunk})
         */
        public List<TemplateProcessor> build(BlockPos pivot) {
            List<TemplateProcessor> out = new ArrayList<TemplateProcessor>(this.specs.size());
            for (Spec spec : this.specs) {
                out.add(spec.build(pivot));
            }
            return out;
        }

        /** @return the list's gravity processor ({@code terrain_matching}'s), or null */
        @Nullable
        public Gravity gravity() {
            return this.gravity;
        }

        public boolean isEmpty() {
            return this.specs.isEmpty() && this.gravity == null;
        }
    }

    /** Today's gravity processor: blocks follow the ground. */
    public static final class Gravity {
        public final String heightmap;
        public final int offset;

        Gravity(String heightmap, int offset) {
            this.heightmap = heightmap;
            this.offset = offset;
        }
    }

    interface Spec {
        TemplateProcessor build(BlockPos pivot);
    }

    static final ProcessorList EMPTY = new ProcessorList(Collections.<Spec>emptyList(), null);

    /**
     * Reads a processor list as a pool element's {@code processors} field has it: a list's id, an
     * object with a {@code processors} array, or the array itself.
     */
    public static ProcessorList parse(@Nullable JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return EMPTY;
        }
        if (json.isJsonPrimitive()) {
            return JigsawPools.processorList(new ResourceLocation(json.getAsString()));
        }
        JsonArray array = json.isJsonArray() ? json.getAsJsonArray()
                : json.getAsJsonObject().has("processors") ? json.getAsJsonObject().getAsJsonArray("processors") : new JsonArray();
        List<Spec> specs = new ArrayList<Spec>();
        Gravity gravity = null;
        for (JsonElement e : array) {
            JsonObject p = e.getAsJsonObject();
            String type = ModernPalette.full(string(p, "processor_type", "minecraft:nop"));
            switch (type) {
                case "minecraft:rule":
                    specs.add(rule(p.getAsJsonArray("rules")));
                    break;
                case "minecraft:block_ignore": {
                    Set<String> names = new HashSet<String>();
                    for (JsonElement b : p.getAsJsonArray("blocks")) {
                        names.add(ModernPalette.full(b.getAsJsonObject().get("Name").getAsString()));
                    }
                    specs.add(pivot -> (world, pos, block, random) -> names.contains(block.name()) ? null : block);
                    break;
                }
                case "minecraft:block_rot": {
                    float integrity = p.has("integrity") ? p.get("integrity").getAsFloat() : 1.0F;
                    specs.add(pivot -> blockRot(integrity));
                    break;
                }
                case "minecraft:gravity":
                    gravity = new Gravity(string(p, "heightmap", "WORLD_SURFACE_WG"), p.has("offset") ? p.get("offset").getAsInt() : 0);
                    break;
                case "minecraft:block_age": {
                    float mossiness = p.get("mossiness").getAsFloat();
                    specs.add(pivot -> blockAge(mossiness));
                    break;
                }
                case "minecraft:blackstone_replace":
                    specs.add(pivot -> blackstoneReplace());
                    break;
                case "minecraft:lava_submerged_block":
                    specs.add(pivot -> lavaSubmerged());
                    break;
                case "minecraft:jigsaw_replacement":
                case "minecraft:nop":
                    break;
                default:
                    if (REPORTED.add(type)) {
                        BackportCore.LOGGER.warn("Structure processor {} is not supported; it is skipped.", type);
                    }
            }
        }
        return new ProcessorList(specs, gravity);
    }

    // ---- rule

    private interface StateTest {
        boolean test(TemplateBlock block, Random random);
    }

    private interface WorldTest {
        boolean test(IBlockState state, Random random);
    }

    private interface PosTest {
        boolean test(BlockPos global, BlockPos pivot, Random random);
    }

    private static final class Rule {
        final StateTest input;
        final WorldTest location;
        final PosTest position;
        final String output;
        @Nullable
        final NBTTagCompound nbt;

        Rule(StateTest input, WorldTest location, PosTest position, String output, @Nullable NBTTagCompound nbt) {
            this.input = input;
            this.location = location;
            this.position = position;
            this.output = output;
            this.nbt = nbt;
        }
    }

    private static Spec rule(JsonArray rules) {
        List<Rule> list = new ArrayList<Rule>();
        for (JsonElement e : rules) {
            JsonObject r = e.getAsJsonObject();
            NBTTagCompound nbt = null;
            if (r.has("output_nbt")) {
                try {
                    JsonElement n = r.get("output_nbt");
                    nbt = JsonToNBT.getTagFromJson(n.isJsonPrimitive() ? n.getAsString() : n.toString());
                } catch (Exception ex) {
                    BackportCore.LOGGER.warn("A rule processor's output_nbt could not be read: {}", r.get("output_nbt"), ex);
                }
            }
            list.add(new Rule(stateTest(r.getAsJsonObject("input_predicate")), worldTest(r.getAsJsonObject("location_predicate")),
                    posTest(r.has("position_predicate") ? r.getAsJsonObject("position_predicate") : null),
                    stateText(r.getAsJsonObject("output_state")), nbt));
        }
        return pivot -> (world, pos, block, random) -> {
            // Today's RuleProcessor: one random per block, from its position; the world block where it goes.
            Random r = new Random(ModernTemplate.positionSeed(pos));
            IBlockState here = world.getBlockState(pos);
            for (Rule rule : list) {
                if (rule.input.test(block, r) && rule.location.test(here, r) && rule.position.test(pos, pivot, r)) {
                    TemplateBlock out = block.replacedBy(rule.output);
                    return rule.nbt == null ? out : out.withTile(rule.nbt);
                }
            }
            return block;
        };
    }

    private static StateTest stateTest(JsonObject p) {
        String type = ModernPalette.full(string(p, "predicate_type", "minecraft:always_true"));
        switch (type) {
            case "minecraft:block_match": {
                String name = ModernPalette.full(p.get("block").getAsString());
                return (b, r) -> b.name().equals(name);
            }
            case "minecraft:random_block_match": {
                String name = ModernPalette.full(p.get("block").getAsString());
                float chance = p.get("probability").getAsFloat();
                return (b, r) -> b.name().equals(name) && r.nextFloat() < chance;
            }
            case "minecraft:blockstate_match": {
                JsonObject s = p.getAsJsonObject("block_state");
                String name = ModernPalette.full(s.get("Name").getAsString());
                Map<String, String> props = props(s);
                return (b, r) -> b.name().equals(name) && sameProps(b.props(), props);
            }
            case "minecraft:random_blockstate_match": {
                JsonObject s = p.getAsJsonObject("block_state");
                String name = ModernPalette.full(s.get("Name").getAsString());
                Map<String, String> props = props(s);
                float chance = p.get("probability").getAsFloat();
                return (b, r) -> b.name().equals(name) && sameProps(b.props(), props) && r.nextFloat() < chance;
            }
            case "minecraft:tag_match": {
                String tag = ModernPalette.full(p.get("tag").getAsString());
                return (b, r) -> inTag(tag, b.name());
            }
            case "minecraft:always_true":
                return (b, r) -> true;
            default:
                unknown(type);
                return (b, r) -> false;
        }
    }

    /** Tests on the block already in the world, a 1.12.2 state: compared with the test's block as Core reads it. */
    private static WorldTest worldTest(JsonObject p) {
        String type = ModernPalette.full(string(p, "predicate_type", "minecraft:always_true"));
        switch (type) {
            case "minecraft:always_true":
                return (s, r) -> true;
            case "minecraft:block_match":
            case "minecraft:random_block_match": {
                String name = ModernPalette.full(p.get("block").getAsString());
                float chance = p.has("probability") ? p.get("probability").getAsFloat() : 2.0F;
                return (s, r) -> sameBlock(s, name) && (chance > 1.0F || r.nextFloat() < chance);
            }
            case "minecraft:blockstate_match":
            case "minecraft:random_blockstate_match": {
                String text = stateText(p.getAsJsonObject("block_state"));
                float chance = p.has("probability") ? p.get("probability").getAsFloat() : 2.0F;
                return (s, r) -> {
                    IBlockState want = ModernPalette.resolve(text).state;
                    return want != null && s == want && (chance > 1.0F || r.nextFloat() < chance);
                };
            }
            default:
                unknown(type);
                return (s, r) -> false;
        }
    }

    private static boolean sameBlock(IBlockState state, String modernName) {
        switch (modernName) {
            case "minecraft:air":
            case "minecraft:cave_air":
            case "minecraft:void_air":
                return state.getMaterial() == Material.AIR;
            case "minecraft:water":
                return state.getMaterial() == Material.WATER && state.getBlock() instanceof net.minecraft.block.BlockLiquid;
            case "minecraft:lava":
                return state.getMaterial() == Material.LAVA && state.getBlock() instanceof net.minecraft.block.BlockLiquid;
            default:
                IBlockState want = ModernPalette.resolve(modernName).state;
                return want != null && want.getBlock() == state.getBlock();
        }
    }

    private static PosTest posTest(@Nullable JsonObject p) {
        if (p == null) {
            return (g, pv, r) -> true;
        }
        String type = ModernPalette.full(string(p, "predicate_type", "minecraft:always_true"));
        switch (type) {
            case "minecraft:always_true":
                return (g, pv, r) -> true;
            case "minecraft:linear_pos": {
                float minChance = f(p, "min_chance", 0.0F);
                float maxChance = f(p, "max_chance", 0.0F);
                int minDist = p.has("min_dist") ? p.get("min_dist").getAsInt() : 0;
                int maxDist = p.has("max_dist") ? p.get("max_dist").getAsInt() : 0;
                return (g, pv, r) -> {
                    int d = Math.abs(g.getX() - pv.getX()) + Math.abs(g.getY() - pv.getY()) + Math.abs(g.getZ() - pv.getZ());
                    return r.nextFloat() <= clampedLerp(minChance, maxChance, inverseLerp(d, minDist, maxDist));
                };
            }
            case "minecraft:axis_aligned_linear_pos": {
                float minChance = f(p, "min_chance", 0.0F);
                float maxChance = f(p, "max_chance", 0.0F);
                int minDist = p.has("min_dist") ? p.get("min_dist").getAsInt() : 0;
                int maxDist = p.has("max_dist") ? p.get("max_dist").getAsInt() : 0;
                String axis = string(p, "axis", "y");
                return (g, pv, r) -> {
                    int d = "x".equals(axis) ? Math.abs(g.getX() - pv.getX())
                            : "z".equals(axis) ? Math.abs(g.getZ() - pv.getZ()) : Math.abs(g.getY() - pv.getY());
                    return r.nextFloat() <= clampedLerp(minChance, maxChance, inverseLerp(d, minDist, maxDist));
                };
            }
            default:
                unknown(type);
                return (g, pv, r) -> false;
        }
    }

    private static double inverseLerp(double value, double min, double max) {
        return (value - min) / (max - min);
    }

    private static double clampedLerp(double a, double b, double t) {
        return t < 0.0D ? a : t > 1.0D ? b : a + t * (b - a);
    }

    // ---- processors built in code (ruined portals)

    /** Today's {@code block_rot}: each block kept with this chance, from its position's random. */
    public static TemplateProcessor blockRot(float integrity) {
        return (world, pos, block, random) -> {
            Random r = new Random(ModernTemplate.positionSeed(pos));
            return integrity < 1.0F && !(r.nextFloat() <= integrity) ? null : block;
        };
    }

    private static final String[] HORIZONTAL = {"north", "east", "south", "west"};
    private static final String[] HALVES = {"top", "bottom"};

    /** Today's {@code block_age} (ruined portals' mossiness): cracked and mossy stone, crying obsidian. */
    public static TemplateProcessor blockAge(float mossiness) {
        return (world, pos, block, random) -> {
            Random r = new Random(ModernTemplate.positionSeed(pos));
            String name = block.name();
            String out = null;
            if (name.equals("minecraft:stone_bricks") || name.equals("minecraft:stone") || name.equals("minecraft:chiseled_stone_bricks")) {
                if (r.nextFloat() < 0.5F) {
                    String[] plain = {"minecraft:cracked_stone_bricks", randomStairs(r, "minecraft:stone_brick_stairs")};
                    String[] mossy = {"minecraft:mossy_stone_bricks", randomStairs(r, "minecraft:mossy_stone_brick_stairs")};
                    out = pick(r, mossiness, plain, mossy);
                }
            } else if (name.endsWith("_stairs")) {
                String facing = block.props().get("facing");
                String half = block.props().get("half");
                if (r.nextFloat() < 0.5F) {
                    String[] plain = {"minecraft:stone_slab", "minecraft:stone_brick_slab"};
                    String[] mossy = {"minecraft:mossy_stone_brick_stairs[facing=" + (facing == null ? "north" : facing) + ",half="
                            + (half == null ? "bottom" : half) + "]", "minecraft:mossy_stone_brick_slab"};
                    out = pick(r, mossiness, plain, mossy);
                }
            } else if (name.endsWith("_slab")) {
                out = r.nextFloat() < mossiness ? "minecraft:mossy_stone_brick_slab" : null;
            } else if (name.endsWith("_wall")) {
                out = r.nextFloat() < mossiness ? "minecraft:mossy_stone_brick_wall" : null;
            } else if (name.equals("minecraft:obsidian")) {
                out = r.nextFloat() < 0.15F ? "minecraft:crying_obsidian" : null;
            }
            return out == null ? block : block.replacedBy(out);
        };
    }

    private static String randomStairs(Random r, String name) {
        String facing = HORIZONTAL[r.nextInt(4)];
        String half = HALVES[r.nextInt(2)];
        return name + "[facing=" + facing + ",half=" + half + "]";
    }

    private static String pick(Random r, float mossiness, String[] plain, String[] mossy) {
        String[] from = r.nextFloat() < mossiness ? mossy : plain;
        return from[r.nextInt(from.length)];
    }

    private static final Map<String, String> BLACKSTONE = new HashMap<String, String>();

    static {
        String[][] pairs = {
                {"cobblestone", "blackstone"}, {"mossy_cobblestone", "blackstone"}, {"stone", "polished_blackstone"},
                {"stone_bricks", "polished_blackstone_bricks"}, {"mossy_stone_bricks", "polished_blackstone_bricks"},
                {"cobblestone_stairs", "blackstone_stairs"}, {"mossy_cobblestone_stairs", "blackstone_stairs"},
                {"stone_stairs", "polished_blackstone_stairs"}, {"stone_brick_stairs", "polished_blackstone_brick_stairs"},
                {"mossy_stone_brick_stairs", "polished_blackstone_brick_stairs"}, {"cobblestone_slab", "blackstone_slab"},
                {"mossy_cobblestone_slab", "blackstone_slab"}, {"smooth_stone_slab", "polished_blackstone_slab"},
                {"stone_slab", "polished_blackstone_slab"}, {"stone_brick_slab", "polished_blackstone_brick_slab"},
                {"mossy_stone_brick_slab", "polished_blackstone_brick_slab"}, {"stone_brick_wall", "polished_blackstone_brick_wall"},
                {"mossy_stone_brick_wall", "polished_blackstone_brick_wall"}, {"cobblestone_wall", "blackstone_wall"},
                {"mossy_cobblestone_wall", "blackstone_wall"}, {"chiseled_stone_bricks", "chiseled_polished_blackstone"},
                {"cracked_stone_bricks", "cracked_polished_blackstone_bricks"}, {"iron_bars", "chain"}};
        for (String[] pair : pairs) {
            BLACKSTONE.put("minecraft:" + pair[0], "minecraft:" + pair[1]);
        }
    }

    /** Today's {@code blackstone_replace} (nether ruined portals): stone to blackstone, iron bars to chains. */
    public static TemplateProcessor blackstoneReplace() {
        return (world, pos, block, random) -> {
            String to = BLACKSTONE.get(block.name());
            if (to == null) {
                return block;
            }
            List<String> keep = new ArrayList<String>();
            for (String key : Arrays.asList("facing", "half", "type")) {
                String v = block.props().get(key);
                if (v != null) {
                    keep.add(key + "=" + v);
                }
            }
            return block.replacedBy(keep.isEmpty() ? to : to + "[" + String.join(",", keep) + "]");
        };
    }

    /** Today's {@code lava_submerged_block}: a block that is not a full cube, placed into lava, is lava. */
    public static TemplateProcessor lavaSubmerged() {
        return (world, pos, block, random) -> {
            IBlockState here = world.getBlockState(pos);
            boolean lava = here.getMaterial() == Material.LAVA && here.getBlock() instanceof net.minecraft.block.BlockLiquid;
            IBlockState state = block.state();
            boolean full = state != null && state.isFullCube();
            return lava && !full ? block.replacedBy("minecraft:lava") : block;
        };
    }

    // ---- helpers

    private static boolean inTag(String tag, String name) {
        synchronized (ModernProcessors.class) {
            Set<String> names = TAGS.get(tag);
            return names != null && names.contains(name);
        }
    }

    private static boolean sameProps(Map<String, String> have, Map<String, String> want) {
        for (Map.Entry<String, String> e : want.entrySet()) {
            if (!e.getValue().equals(have.get(e.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> props(JsonObject state) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        if (state.has("Properties")) {
            for (Map.Entry<String, JsonElement> e : state.getAsJsonObject("Properties").entrySet()) {
                out.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return out;
    }

    /** @return {@code minecraft:name[key=value,...]} for a state as the files write it */
    static String stateText(JsonObject state) {
        String name = ModernPalette.full(state.get("Name").getAsString());
        Map<String, String> props = props(state);
        if (props.isEmpty()) {
            return name;
        }
        StringBuilder out = new StringBuilder(name).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : props.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(e.getKey()).append('=').append(e.getValue());
        }
        return out.append(']').toString();
    }

    private static String string(JsonObject o, String key, String fallback) {
        return o.has(key) ? o.get(key).getAsString() : fallback;
    }

    private static float f(JsonObject o, String key, float fallback) {
        return o.has(key) ? o.get(key).getAsFloat() : fallback;
    }

    private static void unknown(String type) {
        if (REPORTED.add(type)) {
            BackportCore.LOGGER.warn("Structure rule test {} is not supported; it never matches.", type);
        }
    }
}

package com.exiledradio.backportcore.migrate;

import com.exiledradio.backportcore.BackportCore;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Which backport's block or item stands in for each of Future MC's, by name alone.
 *
 * <p>Each backport adds itself with {@link #contributor}, from pre-init, and describes its own pairs
 * there; nothing here knows any of them. Nothing here needs Future MC to be installed: {@code
 * /futuremc convert} uses it with Future MC loaded, and {@link FutureMcRemoved} uses it for worlds
 * Future MC has been taken out of, where the only thing left of a Future MC block is its old name and
 * numbers.
 *
 * <h2>Blocks</h2>
 * A Future MC block becomes a contributor's block of the same name ({@code futuremc:stripped_oak_log}
 * becomes {@code villagerbackport:stripped_oak_log}), or the one a contributor names with
 * {@link Contributor#rename}. Renames are looked at first, then same-name blocks, contributors in the
 * order they registered. A Future MC block nobody has a counterpart for is left alone.
 *
 * <h2>Metadata</h2>
 * A world without Future MC still holds each of its blocks as a number and a metadata value. What
 * that metadata meant is in the recorded tables each contributor names with
 * {@link Contributor#recordedStates}: for each block, the state Future MC 0.2.21 decodes each metadata
 * value to, written out by {@code /futuremc dump} on a server with Future MC installed.
 */
public final class FutureMcTable {

    public static final String FUTURE_MC = "futuremc";

    private static final List<Contributor> CONTRIBUTORS = new ArrayList<Contributor>();

    private static Map<String, Map<Integer, Map<String, String>>> states;

    private FutureMcTable() {
    }

    /**
     * Adds a mod whose blocks and items stand in for Future MC's.
     *
     * @param modId          the mod; its blocks and items of the same name as Future MC's are counterparts
     * @param convertRemoved whether to convert a world Future MC has been taken out of, read each time a
     *                       world loads (usually a config value). {@code /futuremc convert} doesn't ask.
     * @return the contributor, to describe its renames and the rest
     */
    public static synchronized Contributor contributor(String modId, BooleanSupplier convertRemoved) {
        Contributor contributor = new Contributor(modId, convertRemoved);
        CONTRIBUTORS.add(contributor);
        return contributor;
    }

    /** One mod's part of the table. */
    public static final class Contributor {
        final String modId;
        final BooleanSupplier convertRemoved;
        final Map<String, String> renames = new HashMap<String, String>();
        final Map<String, String> propertyRenames = new HashMap<String, String>();
        final Set<String> countedFlags = new HashSet<String>();
        final Set<String> ignored = new HashSet<String>();
        final List<ItemRule> itemRules = new ArrayList<ItemRule>();
        final List<ContentHandler> contentHandlers = new ArrayList<ContentHandler>();
        final List<ItemFixer> itemFixers = new ArrayList<ItemFixer>();

        Contributor(String modId, BooleanSupplier convertRemoved) {
            this.modId = modId;
            this.convertRemoved = convertRemoved;
        }

        public String modId() {
            return this.modId;
        }

        /**
         * A Future MC block whose counterpart has another name.
         *
         * @param futureMcPath the Future MC block's name, without {@code futuremc:}
         * @param target       {@code "modid:block"}, or {@code "modid:block|property=value,property=value"}
         *                     for properties to force on it
         */
        public Contributor rename(String futureMcPath, String target) {
            this.renames.put(futureMcPath, target);
            return this;
        }

        /**
         * A Future MC property that has another name on the counterpart.
         *
         * @param flagToCount Future MC's {@code true}/{@code false} becomes {@code 1}/{@code 0}, for a
         *                    property the counterpart counts where Future MC flags
         */
        public Contributor renameProperty(String futureMcPath, String property, String to, boolean flagToCount) {
            String key = futureMcPath + " " + property;
            this.propertyRenames.put(key, to);
            if (flagToCount) {
                this.countedFlags.add(key);
            }
            return this;
        }

        /** A Future MC property with nothing to go to on the counterpart, on purpose. */
        public Contributor ignoreProperty(String futureMcPath, String property) {
            this.ignored.add(futureMcPath + " " + property);
            return this;
        }

        /** Future MC items that aren't simply the counterpart's item of the same name. */
        public Contributor items(ItemRule rule) {
            this.itemRules.add(rule);
            return this;
        }

        /**
         * A recorded state table, as {@code /futuremc dump} writes it, on the classpath (for example
         * {@code /assets/mymod/migration/futuremc_states.json}). Where two tables have the same block,
         * the first one added wins.
         */
        public Contributor recordedStates(String resource) {
            synchronized (FutureMcTable.class) {
                RESOURCES.add(resource);
                states = null;
            }
            return this;
        }

        /** Puts converted contents into one of this mod's blocks that isn't an ordinary inventory. */
        public Contributor contents(ContentHandler handler) {
            this.contentHandlers.add(handler);
            return this;
        }

        /** Rewrites a converted item's tag where Future MC kept something differently. */
        public Contributor itemFixer(ItemFixer fixer) {
            this.itemFixers.add(fixer);
            return this;
        }
    }

    /** Future MC items that aren't simply a block's or an item of the same name. */
    public interface ItemRule {
        /**
         * @param futureMcPath the Future MC item's name, without {@code futuremc:}
         * @return the item and damage it becomes, or null if this rule has nothing to say
         */
        @Nullable
        ItemTarget item(String futureMcPath, int damage);
    }

    /** Puts one converted item into a block that holds things its own way: a lectern's book, a campfire's food. */
    public interface ContentHandler {
        /**
         * @param ours the counterpart's tile entity, possibly not in a world yet
         * @return what didn't go in (empty if all of it did), or null if this handler doesn't take
         * this tile entity
         */
        @Nullable
        ItemStack put(TileEntity ours, ItemStack stack);
    }

    /** Fixes up a converted item stack's tag. */
    public interface ItemFixer {
        /**
         * @param target the item the stack now is
         * @param stack  the whole saved stack: {@code id}, {@code Damage}, {@code tag}
         */
        void fix(Item target, NBTTagCompound stack);
    }

    private static final List<String> RESOURCES = new ArrayList<String>();

    // ---------------------------------------------------------------------------------------------
    // Lookups
    // ---------------------------------------------------------------------------------------------

    /** A Future MC block's counterpart: the block, and any properties it must have. */
    public static final class Target {
        public final String path;
        public final Block block;
        public final Map<String, String> fixed;
        final Contributor contributor;

        Target(String path, Block block, Map<String, String> fixed, Contributor contributor) {
            this.path = path;
            this.block = block;
            this.fixed = fixed;
            this.contributor = contributor;
        }
    }

    /** @return the counterpart for a Future MC block name, or null if no contributor has one */
    @Nullable
    public static Target block(ResourceLocation name) {
        return block(name, false);
    }

    /**
     * @param removedOnly only contributors that convert worlds Future MC was taken out of
     */
    @Nullable
    static synchronized Target block(ResourceLocation name, boolean removedOnly) {
        if (!FUTURE_MC.equals(name.getNamespace())) {
            return null;
        }
        String path = name.getPath();
        for (Contributor contributor : CONTRIBUTORS) {
            String spec = contributor.renames.get(path);
            if (spec != null && (!removedOnly || contributor.convertRemoved.getAsBoolean())) {
                Target target = target(path, spec, contributor);
                if (target != null) {
                    return target;
                }
            }
        }
        for (Contributor contributor : CONTRIBUTORS) {
            if (!removedOnly || contributor.convertRemoved.getAsBoolean()) {
                Target target = target(path, contributor.modId + ":" + path, contributor);
                if (target != null) {
                    return target;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Target target(String path, String spec, Contributor contributor) {
        Map<String, String> fixed = Collections.emptyMap();
        int bar = spec.indexOf('|');
        if (bar >= 0) {
            fixed = new LinkedHashMap<String, String>();
            for (String pair : spec.substring(bar + 1).split(",")) {
                String[] kv = pair.split("=");
                fixed.put(kv[0], kv[1]);
            }
            spec = spec.substring(0, bar);
        }
        ResourceLocation target = new ResourceLocation(spec);
        if (!ForgeRegistries.BLOCKS.containsKey(target)) {
            return null;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(target);
        return block == null || block == Blocks.AIR ? null : new Target(path, block, fixed, contributor);
    }

    /**
     * @return Future MC's property as the counterpart's {name, value}, or null if it is deliberately
     * left out
     */
    @Nullable
    public static String[] property(Target target, String name, String value) {
        String key = target.path + " " + name;
        Contributor contributor = target.contributor;
        if (contributor.ignored.contains(key)) {
            return null;
        }
        String renamed = contributor.propertyRenames.get(key);
        if (renamed == null) {
            return new String[] {name, value};
        }
        if (contributor.countedFlags.contains(key)) {
            value = "true".equals(value) ? "1" : "0";
        }
        return new String[] {renamed, value};
    }

    /** @return the item for a Future MC item name and damage that isn't a block's, or null */
    @Nullable
    public static ItemTarget item(ResourceLocation name, int damage) {
        return item(name, damage, false);
    }

    @Nullable
    static synchronized ItemTarget item(ResourceLocation name, int damage, boolean removedOnly) {
        if (!FUTURE_MC.equals(name.getNamespace())) {
            return null;
        }
        String path = name.getPath();
        for (Contributor contributor : CONTRIBUTORS) {
            if (removedOnly && !contributor.convertRemoved.getAsBoolean()) {
                continue;
            }
            for (ItemRule rule : contributor.itemRules) {
                ItemTarget target = rule.item(path, damage);
                if (target != null) {
                    return target;
                }
            }
        }
        for (Contributor contributor : CONTRIBUTORS) {
            if (removedOnly && !contributor.convertRemoved.getAsBoolean()) {
                continue;
            }
            Item same = itemNamed(contributor.modId + ":" + path);
            if (same != null) {
                return new ItemTarget(same, damage);
            }
        }
        return null;
    }

    /** An item and the damage it should carry. */
    public static final class ItemTarget {
        public final Item item;
        public final int damage;

        public ItemTarget(Item item, int damage) {
            this.item = item;
            this.damage = damage;
        }
    }

    /** @return the registered item of that name, or null */
    @Nullable
    public static Item itemNamed(String name) {
        ResourceLocation key = new ResourceLocation(name);
        if (!ForgeRegistries.ITEMS.containsKey(key)) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(key);
        return item == null || item == Items.AIR ? null : item;
    }

    static synchronized List<Contributor> contributors() {
        return new ArrayList<Contributor>(CONTRIBUTORS);
    }

    /** @return true if any contributor converts worlds Future MC was taken out of */
    static synchronized boolean anyConvertsRemoved() {
        for (Contributor contributor : CONTRIBUTORS) {
            if (contributor.convertRemoved.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Recorded states
    // ---------------------------------------------------------------------------------------------

    /**
     * @return the properties Future MC decodes this block and metadata to, from the recorded tables,
     * or null if no table has it
     */
    @Nullable
    public static Map<String, String> recordedState(String path, int meta) {
        Map<Integer, Map<String, String>> block = recorded().get(path);
        if (block == null) {
            return null;
        }
        Map<String, String> state = block.get(meta);
        // A metadata value Future MC never writes decodes to its default, as Minecraft's own does.
        return state != null ? state : block.get(0);
    }

    /** @return true if a recorded table has this Future MC block */
    public static boolean recorded(String path) {
        return recorded().containsKey(path);
    }

    private static synchronized Map<String, Map<Integer, Map<String, String>>> recorded() {
        if (states != null) {
            return states;
        }
        Map<String, Map<Integer, Map<String, String>>> out = new HashMap<String, Map<Integer, Map<String, String>>>();
        for (String resource : RESOURCES) {
            InputStream in = FutureMcTable.class.getResourceAsStream(resource);
            if (in == null) {
                BackportCore.LOGGER.warn("{} is missing; its Future MC blocks can only be converted while Future MC is installed.",
                        resource);
                continue;
            }
            try {
                JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> block : root.entrySet()) {
                    if (out.containsKey(block.getKey())) {
                        continue;
                    }
                    Map<Integer, Map<String, String>> metas = new HashMap<Integer, Map<String, String>>();
                    for (Map.Entry<String, JsonElement> meta : block.getValue().getAsJsonObject().entrySet()) {
                        Map<String, String> props = new LinkedHashMap<String, String>();
                        for (Map.Entry<String, JsonElement> prop : meta.getValue().getAsJsonObject().entrySet()) {
                            props.put(prop.getKey(), prop.getValue().getAsString());
                        }
                        metas.put(Integer.parseInt(meta.getKey()), props);
                    }
                    out.put(block.getKey(), metas);
                }
            } catch (RuntimeException e) {
                BackportCore.LOGGER.error("Couldn't read {}.", resource, e);
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // nothing to do
                }
            }
        }
        states = out;
        return states;
    }
}

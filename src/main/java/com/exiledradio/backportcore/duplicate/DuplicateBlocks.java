package com.exiledradio.backportcore.duplicate;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.util.BlockStates;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryModifiable;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Blocks two mods both add, with one of each pair hidden.
 *
 * <h2>The problem</h2>
 * Two mods that back-port the same block - a barrel, a wall, a sign - under the same name and with
 * 1.14's own recipe put two of everything in the item list, and two recipes fighting over each
 * crafting grid, only one of which can win.
 *
 * <h2>What this does about it</h2>
 * One block of each pair is hidden. It keeps existing, so anything already placed in a world carries on
 * working and still drops itself. It loses its crafting and smelting recipes, its place in the creative
 * menu, and (through each mod's own JEI plugin, reading {@link #hiddenItems()}) its entry in JEI. Code
 * that builds with blocks - villages, trades - asks {@link #preferred} and gets the visible one.
 *
 * <h2>Registering</h2>
 * Each mod registers its own pairs with {@link #register}, from pre-init. The pairs are only looked up
 * once every mod's blocks are in; a pair whose other half doesn't exist (the other mod is missing, or
 * has that block switched off) is simply not a pair. Items without a block - a berry, a dye - go
 * through {@link #registerItems}.
 */
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID)
public final class DuplicateBlocks {

    /** Which half of each pair stays visible. */
    public enum Keep {
        /** The registering mod's block; the other mod's is hidden. */
        OURS,
        /** The other mod's block; the registering mod's is hidden. */
        THEIRS,
        /** Hide nothing. */
        BOTH
    }

    private static final List<Source> SOURCES = new ArrayList<Source>();
    private static final List<Supplier<? extends Collection<Item>>> ITEMS = new ArrayList<Supplier<? extends Collection<Item>>>();

    /** Hidden block to the visible one standing in for it. Built once the registries are full. */
    private static Map<Block, Block> hidden;

    private DuplicateBlocks() {
    }

    /**
     * Registers one mod's pairs with another mod.
     *
     * @param ourModId   the registering mod
     * @param theirModId the other mod; nothing happens unless it is installed
     * @param keep       which half to keep, read when the pairs are first looked up (once loading is
     *                   done), so a config value works
     * @param pairs      {our block name, their block name}, without mod ids; asked once, at the same time
     */
    public static synchronized void register(String ourModId, String theirModId, Supplier<Keep> keep,
                                             Supplier<? extends Collection<String[]>> pairs) {
        SOURCES.add(new Source(ourModId, theirModId, keep, pairs));
        hidden = null;
    }

    /** Registers items to hide that have no block of their own, already decided by the caller. */
    public static synchronized void registerItems(Supplier<? extends Collection<Item>> items) {
        ITEMS.add(items);
    }

    /** @return true if this block is the hidden half of a duplicated pair. */
    public static boolean isHidden(Block block) {
        return hidden().containsKey(block);
    }

    /** @return the block to build with in place of this one: its visible twin, or itself. */
    public static Block preferred(Block block) {
        Block twin = hidden().get(block);
        return twin == null ? block : twin;
    }

    /**
     * @return this state as its visible twin, or the state itself if it is not hidden
     *
     * <p>Each property carries across by name; both halves of a pair use 1.14's property names.
     * {@code extra} supplies any the hidden block lacks: the 1.14 properties a structure file was
     * written with, such as the grindstone's {@code face}.
     */
    public static IBlockState preferred(IBlockState state, @Nullable Map<String, String> extra) {
        Block twin = hidden().get(state.getBlock());
        if (twin == null) {
            return state;
        }
        IBlockState out = twin.getDefaultState();
        Set<String> set = new HashSet<String>();
        for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
            String name = entry.getKey().getName();
            out = BlockStates.withOrSame(out, name, BlockStates.valueName(entry.getKey(), entry.getValue()));
            set.add(name);
        }
        if (extra != null) {
            for (Map.Entry<String, String> entry : extra.entrySet()) {
                if (!set.contains(entry.getKey())) {
                    out = BlockStates.withOrSame(out, entry.getKey(), entry.getValue());
                }
            }
        }
        return out;
    }

    /** @return the item form of every hidden block, and every item registered to hide */
    public static Set<Item> hiddenItems() {
        Set<Item> items = new LinkedHashSet<Item>();

        for (Block block : hidden().keySet()) {
            Item item = Item.getItemFromBlock(block);
            if (item == null || item == Items.AIR) {
                item = pickedItem(block);
            }
            // Only ever the hidden block's own mod's item. One mod's sign is picked as vanilla's sign
            // item, and hiding that would take the oak sign's recipe out of the game.
            if (item != null && item != Items.AIR && sameMod(item, block)) {
                items.add(item);
            }
        }
        for (Supplier<? extends Collection<Item>> extra : ITEMS) {
            items.addAll(extra.get());
        }

        return items;
    }

    private static boolean sameMod(Item item, Block block) {
        return item.getRegistryName() != null && block.getRegistryName() != null
                && item.getRegistryName().getNamespace().equals(block.getRegistryName().getNamespace());
    }

    /**
     * @return the item a block is picked as, for blocks held as a separate item - a sign is placed
     * by a sign item, and has no item form of its own to look up
     */
    @Nullable
    @SuppressWarnings("deprecation")
    private static Item pickedItem(Block block) {
        try {
            // Signs answer this without looking at the world, which is not there to pass.
            ItemStack picked = block.getItem(null, null, block.getDefaultState());
            return picked.isEmpty() ? null : picked.getItem();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static synchronized Map<Block, Block> hidden() {
        if (hidden != null) {
            return hidden;
        }

        Map<Block, Block> pairs = new HashMap<Block, Block>();
        for (Source source : SOURCES) {
            Keep keep = source.keep.get();
            if (keep == Keep.BOTH || !Loader.isModLoaded(source.theirModId)) {
                continue;
            }
            int found = 0;
            for (String[] pair : source.pairs.get()) {
                Block ours = block(source.ourModId, pair[0]);
                Block theirs = block(source.theirModId, pair[1]);
                // Only a pair if both halves exist; the other mod may switch each of its blocks off.
                if (ours == null || theirs == null) {
                    continue;
                }
                if (keep == Keep.THEIRS) {
                    pairs.put(ours, theirs);
                } else {
                    pairs.put(theirs, ours);
                }
                found++;
            }
            source.found = found;
        }

        hidden = pairs;
        return hidden;
    }

    @Nullable
    private static Block block(String modId, String path) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(modId, path));
        return block == null || block == Blocks.AIR ? null : block;
    }

    /**
     * Takes the hidden blocks out of the creative menu. Called from init, by which point every mod's
     * blocks are registered and none of them has been listed anywhere yet.
     */
    public static void hideFromCreative() {
        for (Block block : hidden().keySet()) {
            try {
                block.setCreativeTab(null);
            } catch (RuntimeException refused) {
                clearField(Block.class, block, block.getRegistryName(), "displayOnCreativeTab", "field_149772_a");
            }
        }
        // Signs are listed by their item, not their block.
        for (Item item : hiddenItems()) {
            hideItem(item);
        }

        for (Source source : SOURCES) {
            if (source.found > 0) {
                Keep keep = source.keep.get();
                BackportCore.LOGGER.info("{} is installed too; hid {} blocks {} also adds (keeping {}'s).",
                        name(source.theirModId), source.found, name(source.ourModId),
                        name(keep == Keep.THEIRS ? source.theirModId : source.ourModId));
            }
        }
    }

    /**
     * Takes one item out of the creative menu. Some mods' items refuse {@code setCreativeTab(null)} (a Kotlin
     * override with a non-null parameter throws {@code IllegalArgumentException}); for those the tab field is
     * cleared directly, so one item can never stop the game loading.
     *
     * @return true when the item's tab is now null
     */
    public static boolean hideItem(Item item) {
        try {
            item.setCreativeTab(null);
            return true;
        } catch (RuntimeException refused) {
            return clearField(Item.class, item, item.getRegistryName(), "tabToDisplayOn", "field_77701_a");
        }
    }

    private static boolean clearField(Class<?> owner, Object target, @Nullable ResourceLocation name, String mcp, String srg) {
        try {
            ReflectionHelper.findField(owner, mcp, srg).set(target, null);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            BackportCore.LOGGER.warn("Could not take {} out of the creative menu.", name, e);
            return false;
        }
    }

    // ---- World generators ----

    /**
     * Registers another mod's world generator that goes with blocks one of {@link #register}'s pairs hides: once
     * that mod's half is the hidden one ({@code keep} is {@link Keep#OURS} and the mod is installed), every
     * {@link IWorldGenerator} of exactly this class is taken out of {@link GameRegistry}'s list in post-init, so
     * the hidden block stops generating (the registering mod's own generator places the visible one).
     *
     * @param theirClassName the generator's class name, as {@code getClass().getName()} gives it
     */
    public static synchronized void registerGenerator(String ourModId, String theirModId, Supplier<Keep> keep, String theirClassName) {
        GENERATORS.add(new Generator(ourModId, theirModId, keep, theirClassName));
    }

    private static final List<Generator> GENERATORS = new ArrayList<Generator>();

    /** Post-init: takes out every registered generator whose mod's duplicates are hidden. Safe to call twice. */
    public static synchronized void removeGenerators() {
        for (Generator g : GENERATORS) {
            if (g.keep.get() != Keep.OURS || !Loader.isModLoaded(g.theirModId)) {
                continue;
            }
            int removed = removeGenerators(g.className);
            if (removed > 0) {
                BackportCore.LOGGER.info("{}'s duplicates are hidden; removed {} of its world generators ({}), {} generates instead.",
                        name(g.theirModId), removed, g.className, name(g.ourModId));
            }
        }
    }

    /** @return how many world generators of exactly this class were taken out of Forge's list */
    @SuppressWarnings("unchecked")
    public static int removeGenerators(String className) {
        int removed = 0;
        try {
            Field setField = GameRegistry.class.getDeclaredField("worldGenerators");
            Field indexField = GameRegistry.class.getDeclaredField("worldGeneratorIndex");
            Field sortedField = GameRegistry.class.getDeclaredField("sortedGeneratorList");
            setField.setAccessible(true);
            indexField.setAccessible(true);
            sortedField.setAccessible(true);
            Collection<IWorldGenerator> set = (Collection<IWorldGenerator>) setField.get(null);
            Map<IWorldGenerator, Integer> index = (Map<IWorldGenerator, Integer>) indexField.get(null);
            for (Iterator<IWorldGenerator> it = set.iterator(); it.hasNext(); ) {
                IWorldGenerator generator = it.next();
                if (generator.getClass().getName().equals(className)) {
                    it.remove();
                    index.remove(generator);
                    removed++;
                }
            }
            if (removed > 0) {
                // Forge rebuilds the sorted list from the set the next time a chunk is decorated.
                sortedField.set(null, null);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            BackportCore.LOGGER.warn("Could not take out another mod's world generator {}.", className, e);
        }
        return removed;
    }

    private static final class Generator {
        final String ourModId;
        final String theirModId;
        final Supplier<Keep> keep;
        final String className;

        Generator(String ourModId, String theirModId, Supplier<Keep> keep, String className) {
            this.ourModId = ourModId;
            this.theirModId = theirModId;
            this.keep = keep;
            this.className = className;
        }
    }

    private static String name(String modId) {
        ModContainer mod = Loader.instance().getIndexedModList().get(modId);
        return mod == null ? modId : mod.getName();
    }

    /**
     * Removes the crafting recipes that make a hidden block.
     *
     * <p>This is what settles the conflict rather than just the look of it: both mods use 1.14's
     * recipes, so each pair is two recipes with identical ingredients, and the grid can only ever
     * produce one of them. Left alone, which one is down to registration order.
     *
     * <p>Lowest priority so every mod registering recipes in this event has done so. Recipes loaded
     * from JSON are already in by the time the event fires at all.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void removeRecipes(RegistryEvent.Register<IRecipe> event) {
        removeRecipes(event.getRegistry());
    }

    /**
     * Again later, for recipes a mod added after the registry event, and the smelting recipes.
     * Safe to call more than once; this library calls it from post-init.
     */
    public static void removeLateRecipes() {
        removeRecipes(ForgeRegistries.RECIPES);
        removeSmelting();
    }

    /**
     * Removes furnace recipes that make a hidden block. Both mods smelt stone into smooth stone and so
     * on, and a furnace keeps one result per input - whichever its map happens to find first.
     */
    private static void removeSmelting() {
        Set<Item> items = hiddenItems();
        if (items.isEmpty()) {
            return;
        }
        int removed = 0;
        Iterator<Map.Entry<ItemStack, ItemStack>> it = FurnaceRecipes.instance().getSmeltingList().entrySet().iterator();
        while (it.hasNext()) {
            if (items.contains(it.next().getValue().getItem())) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            BackportCore.LOGGER.info("Removed {} smelting recipes for hidden duplicates.", removed);
        }
    }

    private static void removeRecipes(IForgeRegistry<IRecipe> registry) {
        Set<Item> items = hiddenItems();

        if (items.isEmpty() || !(registry instanceof IForgeRegistryModifiable)) {
            return;
        }

        List<ResourceLocation> doomed = new ArrayList<ResourceLocation>();

        for (IRecipe recipe : registry) {
            ItemStack output = recipe.getRecipeOutput();

            if (!output.isEmpty() && items.contains(output.getItem())) {
                doomed.add(recipe.getRegistryName());
            }
        }

        for (ResourceLocation name : doomed) {
            ((IForgeRegistryModifiable<IRecipe>) registry).remove(name);
        }

        if (!doomed.isEmpty()) {
            BackportCore.LOGGER.info("Removed {} recipes for hidden duplicates: {}", doomed.size(), doomed);
        }
    }

    private static final class Source {
        final String ourModId;
        final String theirModId;
        final Supplier<Keep> keep;
        final Supplier<? extends Collection<String[]>> pairs;
        int found;

        Source(String ourModId, String theirModId, Supplier<Keep> keep, Supplier<? extends Collection<String[]>> pairs) {
            this.ourModId = ourModId;
            this.theirModId = theirModId;
            this.keep = keep;
            this.pairs = pairs;
        }
    }
}

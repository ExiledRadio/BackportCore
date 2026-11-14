package com.exiledradio.backportcore.recipe.book;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * One cooking block's recipe book (1.13's furnace book, 1.14's smoker and blast furnace books): which container it
 * belongs to, where its input, fuel and output slots are, where its recipes come from, its tabs and its filter's
 * text. Plain data, both sides; register it with {@link SmeltingBooks#register} from pre-init or init.
 *
 * <p>{@link #furnace()} is vanilla's furnace as 1.13 has it. Another block's book (a smoker) is built the same way
 * with its own container, recipe map and tabs; its screen draws the book with
 * {@code recipe.book.client.SmeltingBookGui}, as {@code GuiFurnaceBook} does.
 */
public final class SmeltingBook {

    /** Vanilla furnace's book id. */
    public static final ResourceLocation FURNACE = new ResourceLocation("minecraft", "furnace");

    /** One tab after the search tab: its icons (1 or 2, as 1.13 draws them) and which results it lists. */
    public static final class Tab {
        public final List<ItemStack> icons;
        final Predicate<ItemStack> results;

        public Tab(Predicate<ItemStack> results, ItemStack... icons) {
            if (icons.length < 1 || icons.length > 2) {
                throw new IllegalArgumentException("a tab has one or two icons");
            }
            this.icons = Collections.unmodifiableList(Arrays.asList(icons));
            this.results = results;
        }
    }

    public final ResourceLocation id;
    final Predicate<Container> container;
    public final int inputSlot;
    public final int fuelSlot;
    public final int outputSlot;
    final Supplier<Map<ItemStack, ItemStack>> recipes;
    final Predicate<ItemStack> fuel;
    /** The filter button's text when on ({@code gui.recipebook.toggleRecipes.smeltable}). */
    public final String filterKey;
    public final List<Tab> tabs;
    /** The toast's small icon (the furnace). */
    public final ItemStack toastIcon;

    private List<ItemStack> fuels;

    /**
     * @param container which open containers the book is for (a class test, so a mod's subclass is not mistaken for it)
     * @param recipes input to result, as {@code FurnaceRecipes.getSmeltingList()}; read when the book is first used
     * @param fuel what may go in the fuel slot (the ghost fuel cycles through every such item)
     */
    public SmeltingBook(ResourceLocation id, Predicate<Container> container, int inputSlot, int fuelSlot, int outputSlot,
            Supplier<Map<ItemStack, ItemStack>> recipes, Predicate<ItemStack> fuel, String filterKey, ItemStack toastIcon,
            Tab... tabs) {
        this.id = id;
        this.container = container;
        this.inputSlot = inputSlot;
        this.fuelSlot = fuelSlot;
        this.outputSlot = outputSlot;
        this.recipes = recipes;
        this.fuel = fuel;
        this.filterKey = filterKey;
        this.toastIcon = toastIcon;
        this.tabs = Collections.unmodifiableList(Arrays.asList(tabs));
    }

    /**
     * 1.13's furnace book: every recipe of {@code FurnaceRecipes} (vanilla's and every mod's), tabs Food (a porkchop:
     * edible results), Blocks (stone: block items) and Miscellaneous (lava bucket and emerald: the rest).
     */
    public static SmeltingBook furnace() {
        return new SmeltingBook(FURNACE, c -> c.getClass() == ContainerFurnace.class, 0, 1, 2,
                () -> FurnaceRecipes.instance().getSmeltingList(), TileEntityFurnace::isItemFuel,
                "gui.recipebook.toggleRecipes.smeltable", new ItemStack(Blocks.FURNACE),
                new Tab(s -> s.getItem() instanceof ItemFood, new ItemStack(Items.PORKCHOP)),
                new Tab(s -> s.getItem() instanceof ItemBlock, new ItemStack(Blocks.STONE)),
                new Tab(s -> true, new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.EMERALD)));
    }

    public boolean isFor(Container c) {
        return c != null && this.container.test(c);
    }

    /** @return the tab a result belongs to: the first that takes it, or the last */
    public int tabOf(ItemStack result) {
        for (int i = 0; i < this.tabs.size(); i++) {
            if (this.tabs.get(i).results.test(result)) {
                return i;
            }
        }
        return this.tabs.size() - 1;
    }

    /** @return every fuel item, in registry order (the ghost fuel slot cycles through them, as 1.13's does) */
    public synchronized List<ItemStack> fuels() {
        if (this.fuels == null) {
            List<ItemStack> out = new ArrayList<ItemStack>();
            for (Item item : ForgeRegistries.ITEMS) {
                NonNullList<ItemStack> subs = NonNullList.create();
                try {
                    item.getSubItems(CreativeTabs.SEARCH, subs);
                } catch (RuntimeException e) {
                    continue;
                }
                // One per item, as 1.13 cycles through items.
                for (ItemStack s : subs) {
                    if (!s.isEmpty() && this.fuel.test(s)) {
                        out.add(s);
                        break;
                    }
                }
            }
            this.fuels = Collections.unmodifiableList(out);
        }
        return this.fuels;
    }
}

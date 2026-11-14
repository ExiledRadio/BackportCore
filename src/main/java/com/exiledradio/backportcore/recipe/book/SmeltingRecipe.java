package com.exiledradio.backportcore.recipe.book;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One smelting recipe as 1.13 has it: one result, made from any of its inputs (1.13's {@code iron_nugget_from_smelting}
 * takes every iron tool; 1.12.2 lists each tool on its own). Its id names the result, so both sides find the same
 * recipe in their own {@code FurnaceRecipes}.
 */
public final class SmeltingRecipe {

    public final String id;
    public final ItemStack output;
    public final List<ItemStack> inputs;
    public final Ingredient input;
    /** The tab it shows on (an index of the book's tabs). */
    public final int tab;
    /** The same recipe as 1.12.2's crafting recipes are, for {@code RecipeItemHelper} and the ghost; never registered. */
    public final IRecipe asCrafting;

    SmeltingRecipe(String id, ItemStack output, List<ItemStack> inputs, int tab) {
        this.id = id;
        this.output = output;
        this.inputs = Collections.unmodifiableList(inputs);
        this.input = Ingredient.fromStacks(inputs.toArray(new ItemStack[0]));
        this.tab = tab;
        this.asCrafting = new ShapelessRecipes("", output, NonNullList.from(Ingredient.EMPTY, this.input));
    }

    /** @return {@code modid:item}, then {@code @meta}, {@code *count} and {@code #nbt} where they are not the plain ones */
    public static String idOf(ItemStack result) {
        ResourceLocation name = result.getItem().getRegistryName();
        StringBuilder b = new StringBuilder(name == null ? "unknown" : name.toString());
        if (result.getMetadata() != 0) {
            b.append('@').append(result.getMetadata());
        }
        if (result.getCount() != 1) {
            b.append('*').append(result.getCount());
        }
        if (result.hasTagCompound()) {
            b.append('#').append(Integer.toHexString(result.getTagCompound().toString().hashCode()));
        }
        return b.toString();
    }

    /**
     * @return a book's recipes from its recipe map: one per result, in item registry order (1.12.2's map has no order of
     * its own)
     */
    static List<SmeltingRecipe> build(SmeltingBook book, Map<ItemStack, ItemStack> map) {
        Map<String, ItemStack> outputs = new LinkedHashMap<String, ItemStack>();
        Map<String, List<ItemStack>> inputs = new LinkedHashMap<String, List<ItemStack>>();
        for (Map.Entry<ItemStack, ItemStack> e : map.entrySet()) {
            ItemStack in = e.getKey();
            ItemStack out = e.getValue();
            if (in == null || out == null || in.isEmpty() || out.isEmpty()) {
                continue;
            }
            String id = idOf(out);
            if (!outputs.containsKey(id)) {
                outputs.put(id, out.copy());
                inputs.put(id, new ArrayList<ItemStack>());
            }
            inputs.get(id).add(in.copy());
        }
        List<SmeltingRecipe> out = new ArrayList<SmeltingRecipe>();
        for (Map.Entry<String, ItemStack> e : outputs.entrySet()) {
            List<ItemStack> in = inputs.get(e.getKey());
            in.sort(ORDER);
            out.add(new SmeltingRecipe(e.getKey(), e.getValue(), in, book.tabOf(e.getValue())));
        }
        out.sort((a, b) -> ORDER.compare(a.output, b.output));
        return out;
    }

    static final Comparator<ItemStack> ORDER = (a, b) -> {
        int c = Integer.compare(Item.getIdFromItem(a.getItem()), Item.getIdFromItem(b.getItem()));
        if (c != 0) {
            return c;
        }
        c = Integer.compare(a.getMetadata(), b.getMetadata());
        return c != 0 ? c : Integer.compare(a.getCount(), b.getCount());
    };

    @Override
    public String toString() {
        return this.id;
    }
}

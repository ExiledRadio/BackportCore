package com.exiledradio.backportcore.compat;

import com.exiledradio.backportcore.duplicate.DuplicateBlocks;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.ingredients.IIngredientBlacklist;
import mezz.jei.api.ingredients.VanillaTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Set;

/**
 * Takes the hidden half of every duplicated pair ({@link DuplicateBlocks}) out of JEI's item list, every
 * subtype of it: JEI lists an item with no creative tab anyway. Only JEI loads this class.
 */
@JEIPlugin
public final class HiddenDuplicatesJei implements IModPlugin {

    @Override
    public void register(IModRegistry registry) {
        Set<Item> hidden = DuplicateBlocks.hiddenItems();
        if (hidden.isEmpty()) {
            return;
        }
        IIngredientBlacklist blacklist = registry.getJeiHelpers().getIngredientBlacklist();
        for (ItemStack stack : registry.getIngredientRegistry().getAllIngredients(VanillaTypes.ITEM)) {
            if (hidden.contains(stack.getItem())) {
                blacklist.addIngredientToBlacklist(stack);
            }
        }
    }
}

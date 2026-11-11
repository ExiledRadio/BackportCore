package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.recipe.RecipeUnlocks;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import net.minecraftforge.registries.IForgeRegistry;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Recipe unlocking (recipe/RecipeUnlocks): the test mod requires Core, so its recipes are the family's. It has a
 * smithing table (vanilla: an iron ingot), sticks from bamboo (vanilla has two stick recipes; bamboo's applies),
 * a boat (vanilla: entering water, so any ingredient) and an item vanilla does not have (any ingredient).
 */
final class RecipeTests {

    static Item smithingTable;
    static Item bamboo;
    static Item oakBoat;
    static Item oddity;

    private RecipeTests() {
    }

    static void items(IForgeRegistry<Item> r) {
        smithingTable = item(r, "smithing_table");
        bamboo = item(r, "bamboo");
        oakBoat = item(r, "oak_boat");
        oddity = item(r, "selftest_oddity");
    }

    private static Item item(IForgeRegistry<Item> r, String name) {
        Item item = new Item().setCreativeTab(net.minecraft.creativetab.CreativeTabs.MISC).setRegistryName(new ResourceLocation(SelfTestMod.MOD_ID, name))
                .setTranslationKey(SelfTestMod.MOD_ID + "." + name);
        r.register(item);
        return item;
    }

    static void recipes(IForgeRegistry<IRecipe> r) {
        recipe(r, "smithing_table", new ItemStack(smithingTable), Items.IRON_INGOT, "plankWood");
        recipe(r, "stick_from_bamboo", new ItemStack(Items.STICK), bamboo, bamboo);
        recipe(r, "oak_boat", new ItemStack(oakBoat), "plankWood", Items.BOWL);
        recipe(r, "oddity", new ItemStack(oddity), Items.FEATHER, Blocks.SPONGE);
    }

    private static void recipe(IForgeRegistry<IRecipe> r, String name, ItemStack out, Object... in) {
        ResourceLocation id = new ResourceLocation(SelfTestMod.MOD_ID, name);
        r.register(new ShapelessOreRecipe(id, out, in).setRegistryName(id));
    }

    private static IRecipe get(String name) {
        return CraftingManager.getRecipe(new ResourceLocation(SelfTestMod.MOD_ID, name));
    }

    private static boolean has(EntityPlayerMP player, String name) {
        return player.getRecipeBook().isUnlocked(get(name));
    }

    private static void hold(EntityPlayerMP player, ItemStack stack) {
        player.inventory.addItemStackToInventory(stack);
        RecipeUnlocks.check(player);
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation(SelfTestMod.MOD_ID, name);
    }

    static void schedule() {
        then(1, "recipe unlocking", world -> {
            RecipeUnlocks.serverStarting();
            check("recipes: the test mod's four recipes are covered", RecipeUnlocks.count() >= 4, RecipeUnlocks.count());
            check("recipes: smithing table unlocks on vanilla's item", "[iron_ingot]".equals(String.valueOf(RecipeUnlocks.triggersOf(id("smithing_table")))),
                    RecipeUnlocks.triggersOf(id("smithing_table")));
            check("recipes: sticks from bamboo take the bamboo rule, not the planks one",
                    "[bamboo]".equals(String.valueOf(RecipeUnlocks.triggersOf(id("stick_from_bamboo")))), RecipeUnlocks.triggersOf(id("stick_from_bamboo")));
            check("recipes: a boat (vanilla: entering water) unlocks on any ingredient",
                    "[any ingredient]".equals(String.valueOf(RecipeUnlocks.triggersOf(id("oak_boat")))), RecipeUnlocks.triggersOf(id("oak_boat")));
            check("recipes: an item vanilla lacks unlocks on any ingredient",
                    "[any ingredient]".equals(String.valueOf(RecipeUnlocks.triggersOf(id("oddity")))), RecipeUnlocks.triggersOf(id("oddity")));
            check("recipes: vanilla's own recipes are left alone", RecipeUnlocks.triggersOf(new ResourceLocation("minecraft", "furnace")) == null);

            EntityPlayerMP player = PoseTests.player(world, "recipes");
            RecipeUnlocks.check(player);
            check("recipes: an empty inventory unlocks nothing",
                    !has(player, "smithing_table") && !has(player, "stick_from_bamboo") && !has(player, "oak_boat") && !has(player, "oddity"));

            hold(player, new ItemStack(Blocks.PLANKS, 1, 2));
            check("recipes: planks unlock the boat", has(player, "oak_boat"));
            check("recipes: planks do not unlock the smithing table or bamboo's sticks",
                    !has(player, "smithing_table") && !has(player, "stick_from_bamboo"));

            CoreConfig.recipes.unlockRecipes = false;
            hold(player, new ItemStack(Items.IRON_INGOT));
            check("recipes: switched off, an iron ingot unlocks nothing", !has(player, "smithing_table"));
            CoreConfig.recipes.unlockRecipes = true;
            hold(player, new ItemStack(Items.GOLD_INGOT));
            check("recipes: switched on again, the iron ingot unlocks the smithing table", has(player, "smithing_table"));

            hold(player, new ItemStack(bamboo));
            check("recipes: bamboo unlocks sticks from bamboo", has(player, "stick_from_bamboo"));
            hold(player, new ItemStack(Items.FEATHER));
            check("recipes: a feather unlocks the oddity", has(player, "oddity"));
        });
    }
}

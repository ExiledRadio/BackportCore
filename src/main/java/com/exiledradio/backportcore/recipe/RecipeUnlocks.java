package com.exiledradio.backportcore.recipe;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.creative.VanillaOrder;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Vanilla's recipe unlocking for the backports' recipes: holding an iron ingot gives the smithing table's
 * recipe, a stick or coal the campfire's, with 1.12.2's own "New Recipes Unlocked!" toast, and the recipe then
 * shows in the recipe book.
 *
 * <p>Vanilla does this with one advancement per recipe. 1.12.2 has the system, but the backports' recipes had
 * no such advancements, so none of their items ever reached the recipe book. Core does it for every crafting
 * recipe of every mod that requires it, from one table of what unlocks each recipe in current Java
 * ({@code assets/backportcore/recipes/unlocks.txt}, built by {@code tools/recipes/build_unlocks.py}):
 *
 * <ol>
 *   <li>a recipe is matched to vanilla's recipes of the same result (a backport names its items as today's
 *       game does), and takes their trigger items that are ingredients here (sticks from bamboo unlock with
 *       bamboo, not with planks);</li>
 *   <li>the recipe unlocks once the player holds any one of those items, as vanilla's advancement asks;</li>
 *   <li>a recipe vanilla has no rule for (boats unlock on entering water), does not have at all, or makes from
 *       other things than 1.12.2 can, unlocks with any one of its ingredients.</li>
 * </ol>
 *
 * <p>Items are compared by today's id: {@code minecraft:coal} at metadata 1 is charcoal
 * ({@link VanillaOrder#modernId}), a backport's {@code modid:bamboo} is bamboo. A tag also takes any item
 * under the ore name 1.12.2 mods use for it, so another mod's planks count as planks.
 *
 * <p>Server side. The inventory is looked at once a second, and only when it has changed.
 */
public final class RecipeUnlocks {

    private static final String TABLE = "/assets/backportcore/recipes/unlocks.txt";
    private static final int INTERVAL = 20;

    /** Today's item tags and the ore names 1.12.2 mods register the same things under. */
    private static final Map<String, String> ORE_NAMES = new HashMap<String, String>();

    static {
        ORE_NAMES.put("planks", "plankWood");
        ORE_NAMES.put("wooden_slabs", "slabWood");
        ORE_NAMES.put("wooden_fences", "fenceWood");
        ORE_NAMES.put("logs", "logWood");
        ORE_NAMES.put("eggs", "egg");
        ORE_NAMES.put("stone_crafting_materials", "cobblestone");
        ORE_NAMES.put("stone_tool_materials", "cobblestone");
        ORE_NAMES.put("iron_tool_materials", "ingotIron");
        ORE_NAMES.put("gold_tool_materials", "ingotGold");
        ORE_NAMES.put("diamond_tool_materials", "gemDiamond");
        ORE_NAMES.put("copper_tool_materials", "ingotCopper");
    }

    /** One vanilla recipe's rule: its triggers, each an item path or {@code #tag}. */
    private static final class Rule {
        final String[] triggers;

        Rule(String[] triggers) {
            this.triggers = triggers;
        }
    }

    /** One of the family's recipes and what unlocks it. */
    private static final class Unlock {
        final IRecipe recipe;
        /** Today's item paths, {@code #tag}s, or with {@code ingredients} set, nothing. */
        final Set<String> triggers;
        /** The fallback: any one of these. */
        @Nullable
        final List<Ingredient> ingredients;

        Unlock(IRecipe recipe, Set<String> triggers, @Nullable List<Ingredient> ingredients) {
            this.recipe = recipe;
            this.triggers = triggers;
            this.ingredients = ingredients;
        }
    }

    private static Map<String, List<Rule>> rulesByResult;
    private static Map<String, Set<String>> tags;
    private static final Map<String, Boolean> FAMILY = new HashMap<String, Boolean>();
    private static final Set<String> EXTRA_MODS = new HashSet<String>();

    @Nullable
    private static List<Unlock> unlocks;
    private static final Map<EntityPlayerMP, Integer> SEEN = new WeakHashMap<EntityPlayerMP, Integer>();

    private RecipeUnlocks() {
    }

    /**
     * Adds a mod whose recipes unlock this way. Mods that require Backport Core are included already; this is
     * for one that only uses it when present. Call before the server starts.
     */
    public static synchronized void addMod(String modId) {
        EXTRA_MODS.add(modId);
        unlocks = null;
    }

    /** The recipe list is built again for each server start: recipes can differ between worlds' mods and configs. */
    public static synchronized void serverStarting() {
        unlocks = null;
        SEEN.clear();
    }

    /** @return how many of the family's recipes have an unlock rule (the self-test's number) */
    public static synchronized int count() {
        return unlocks().size();
    }

    /**
     * @return what unlocks a recipe: today's item paths and {@code #tag}s, or {@code "any ingredient"}; null for a
     * recipe this class leaves alone
     */
    @Nullable
    public static synchronized Set<String> triggersOf(ResourceLocation recipe) {
        for (Unlock u : unlocks()) {
            if (recipe.equals(u.recipe.getRegistryName())) {
                return u.ingredients != null ? Collections.singleton("any ingredient") : u.triggers;
            }
        }
        return null;
    }

    public static final class Events {

        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)
                    || event.player.ticksExisted % INTERVAL != 0) {
                return;
            }
            check((EntityPlayerMP) event.player);
        }

        @SubscribeEvent
        public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            synchronized (RecipeUnlocks.class) {
                SEEN.remove(event.player);
            }
        }
    }

    /** Gives the player every recipe their inventory now unlocks. */
    public static synchronized void check(EntityPlayerMP player) {
        if (!CoreConfig.recipes.unlockRecipes) {
            return;
        }
        InventoryPlayer inventory = player.inventory;
        // Which items are held, not how many: 1.12.2's own change counter is client-only.
        int held1 = 1;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                held1 = 31 * held1 + System.identityHashCode(stack.getItem()) + 131 * stack.getMetadata();
            }
        }
        Integer seen = SEEN.get(player);
        if (seen != null && seen == held1) {
            return;
        }
        SEEN.put(player, held1);

        List<Unlock> all = unlocks();
        if (all.isEmpty()) {
            return;
        }
        List<ItemStack> held = new ArrayList<ItemStack>();
        Set<String> ids = new HashSet<String>();
        Set<String> ores = new HashSet<String>();
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            held.add(stack);
            String id = modernPath(stack);
            if (id != null) {
                ids.add(id);
            }
            for (int ore : OreDictionary.getOreIDs(stack)) {
                ores.add(OreDictionary.getOreName(ore));
            }
        }
        if (held.isEmpty()) {
            return;
        }
        List<IRecipe> give = new ArrayList<IRecipe>();
        for (Unlock u : all) {
            if (player.getRecipeBook().isUnlocked(u.recipe)) {
                continue;
            }
            if (u.ingredients != null ? holdsAny(u.ingredients, held) : holdsAny(u.triggers, ids, ores)) {
                give.add(u.recipe);
            }
        }
        if (!give.isEmpty()) {
            player.unlockRecipes(give);
        }
    }

    private static boolean holdsAny(List<Ingredient> ingredients, List<ItemStack> held) {
        for (Ingredient ingredient : ingredients) {
            for (ItemStack stack : held) {
                if (ingredient.apply(stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean holdsAny(Collection<String> triggers, Set<String> ids, Set<String> ores) {
        for (String trigger : triggers) {
            if (trigger.charAt(0) != '#') {
                if (ids.contains(trigger)) {
                    return true;
                }
                continue;
            }
            String tag = trigger.substring(1);
            String ore = ORE_NAMES.get(tag);
            if (ore != null && ores.contains(ore)) {
                return true;
            }
            Set<String> members = tags.get(tag);
            if (members != null && !Collections.disjoint(members, ids)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return today's id of a stack without its namespace: vanilla's by Core's table, a family mod's by its own
     * name; null for any other mod's item
     */
    @Nullable
    static String modernPath(ItemStack stack) {
        ResourceLocation name = stack.getItem().getRegistryName();
        if (name == null) {
            return null;
        }
        if ("minecraft".equals(name.getNamespace())) {
            String id = VanillaOrder.modernId(stack);
            return id == null ? null : id.substring(id.indexOf(':') + 1);
        }
        return isFamily(name.getNamespace()) ? name.getPath() : null;
    }

    private static List<Unlock> unlocks() {
        if (unlocks != null) {
            return unlocks;
        }
        loadTable();
        List<Unlock> out = new ArrayList<Unlock>();
        int byRule = 0;
        for (IRecipe recipe : ForgeRegistries.RECIPES) {
            ResourceLocation name = recipe.getRegistryName();
            if (name == null || !isFamily(name.getNamespace()) || recipe.isDynamic()) {
                continue;
            }
            ItemStack result = recipe.getRecipeOutput();
            if (result.isEmpty()) {
                BackportCore.LOGGER.debug("Recipe unlocking: {} has no fixed result, left alone.", name);
                continue;
            }
            List<Ingredient> ingredients = new ArrayList<Ingredient>();
            for (Ingredient ingredient : recipe.getIngredients()) {
                // Kept even with no stacks to show: an ingredient of an item in no creative tab lists none, and still matches.
                if (ingredient != Ingredient.EMPTY) {
                    ingredients.add(ingredient);
                }
            }
            if (ingredients.isEmpty()) {
                BackportCore.LOGGER.debug("Recipe unlocking: {} has no ingredients, left alone.", name);
                continue;
            }
            Set<String> triggers = triggersFor(result, ingredients);
            if (triggers.isEmpty()) {
                out.add(new Unlock(recipe, triggers, ingredients));
            } else {
                out.add(new Unlock(recipe, triggers, null));
                byRule++;
            }
        }
        BackportCore.LOGGER.info("Recipe unlocking: {} recipes, {} by vanilla's rule and {} by any ingredient.", out.size(),
                byRule, out.size() - byRule);
        unlocks = out;
        return out;
    }

    /**
     * Vanilla's triggers for a recipe with this result, keeping those that mean something for this recipe: an
     * ingredient of it, or the result itself (vanilla gives the lodestone's recipe to whoever holds a lodestone).
     * 1.12.2's form of a recipe can differ from today's (the grindstone here takes 1.12.2's stone slab, today's
     * smooth stone slab), and a trigger for an item this game does not have would never fire.
     */
    private static Set<String> triggersFor(ItemStack result, List<Ingredient> ingredients) {
        String path = modernPath(result);
        List<Rule> rules = path == null ? null : rulesByResult.get(path);
        if (rules == null) {
            return Collections.emptySet();
        }
        Set<String> kept = new java.util.LinkedHashSet<String>();
        boolean self = false;
        for (Rule rule : rules) {
            for (String trigger : rule.triggers) {
                if (trigger.equals(path)) {
                    self = true;
                } else if (isIngredient(trigger, ingredients)) {
                    kept.add(trigger);
                }
            }
        }
        // The result alone would mean the recipe shows only to someone who already has the thing.
        if (self && !kept.isEmpty()) {
            kept.add(path);
        }
        return kept;
    }

    private static boolean isIngredient(String trigger, List<Ingredient> ingredients) {
        Set<String> none = Collections.emptySet();
        for (Ingredient ingredient : ingredients) {
            for (ItemStack stack : ingredient.getMatchingStacks()) {
                String id = modernPath(stack);
                Set<String> ids = id == null ? none : Collections.singleton(id);
                Set<String> ores = new HashSet<String>();
                for (int ore : OreDictionary.getOreIDs(stack)) {
                    ores.add(OreDictionary.getOreName(ore));
                }
                if (holdsAny(Collections.singleton(trigger), ids, ores)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isFamily(String modId) {
        Boolean known = FAMILY.get(modId);
        if (known == null) {
            known = EXTRA_MODS.contains(modId) || requiresCore(modId);
            FAMILY.put(modId, known);
        }
        return known;
    }

    private static boolean requiresCore(String modId) {
        ModContainer mod = Loader.instance().getIndexedModList().get(modId);
        if (mod == null) {
            return false;
        }
        for (ArtifactVersion v : mod.getRequirements()) {
            if (BackportCore.MOD_ID.equals(v.getLabel())) {
                return true;
            }
        }
        for (ArtifactVersion v : mod.getDependencies()) {
            if (BackportCore.MOD_ID.equals(v.getLabel())) {
                return true;
            }
        }
        return false;
    }

    private static void loadTable() {
        if (rulesByResult != null) {
            return;
        }
        Map<String, List<Rule>> rules = new HashMap<String, List<Rule>>();
        Map<String, Set<String>> tagMap = new HashMap<String, Set<String>>();
        InputStream in = RecipeUnlocks.class.getResourceAsStream(TABLE);
        if (in == null) {
            BackportCore.LOGGER.warn("Recipe unlocking: {} is missing; recipes unlock by any ingredient.", TABLE);
        } else {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(" ");
                    if (parts.length == 3 && "t".equals(parts[0])) {
                        tagMap.put(parts[1], new HashSet<String>(Arrays.asList(parts[2].split(","))));
                    } else if (parts.length == 4 && "r".equals(parts[0]) && !"-".equals(parts[3])) {
                        List<Rule> list = rules.get(parts[2]);
                        if (list == null) {
                            list = new ArrayList<Rule>();
                            rules.put(parts[2], list);
                        }
                        list.add(new Rule(parts[3].split(",")));
                    }
                }
            } catch (IOException e) {
                BackportCore.LOGGER.warn("Recipe unlocking: could not read {}: {}", TABLE, e.toString());
            }
        }
        tags = tagMap;
        rulesByResult = rules;
    }
}

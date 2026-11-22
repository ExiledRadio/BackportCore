package com.exiledradio.backportcore.creative.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.creative.CreativePlacement;
import com.exiledradio.backportcore.creative.CreativePlacement.Placement;
import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1.19.3's creative tabs (22w42a): from 1.19.3 on ({@code Presentation.isAtLeast(LookArea.INVENTORY, "1.19.3")}) the
 * creative menu's first page shows 1.19.3's tabs in 1.19.3's places instead of 1.12.2's nine: Building Blocks, Colored
 * Blocks, Natural Blocks, Functional Blocks and Redstone Blocks along the top with Saved Hotbars and Search at its right
 * end; Tools &amp; Utilities, Combat, Food &amp; Drinks, Ingredients and Spawn Eggs along the bottom with Operator
 * Utilities (1.19.3's Operator Items Tab option, for players allowed commands) and the Survival Inventory at its right
 * end. Tabs are 1.19.3's 26 pixels wide, 27 apart (patch P47 draws them; the tab sprites are the backport's own
 * {@code tabs.png}).
 *
 * <p>Each tab lists what 1.19.3's lists ({@code creative/tabs_1_19_3.txt}, from 1.19.3's {@code CreativeModeTabs};
 * items added later are in their 26.3 tab, by 26.3's order), and from 26.3 on what 26.3's lists
 * ({@code creative/order_26_3.txt}). The stacks are 1.12.2's own: every stack the nine 1.12.2 tabs list (with the
 * backports' placed items), each in every 1.19.3 tab that lists the item it is today, in that tab's order. A stack no
 * table lists (another mod's item in a vanilla tab) goes after the rest, in the tab 1.12.2's tab leads to (Decorations:
 * Functional Blocks; Transportation and Tools: Tools &amp; Utilities; Miscellaneous: Ingredients, or Spawn Eggs for a
 * spawn egg; Brewing and Foodstuffs: Food &amp; Drinks). Enchanted books are 1.19.3's: every enchantment's book at its
 * top level, in Ingredients. Operator Utilities lists the operator items 1.12.2 has (command blocks, the structure
 * block and void, the barrier).
 *
 * <p>Other mods' tabs are untouched and page after vanilla's as in 1.12.2 Forge (ten to a page from page 2). The 1.12.2
 * tabs stay what every item's {@code getCreativeTab()} is, so {@code getSubItems}, the search tab and JEI see nothing
 * new. The eleven tabs are added to {@code CreativeTabs.CREATIVE_TAB_ARRAY} at load complete, after every mod's own, and
 * are on no page while the look is older (P47 leaves them out of the page count).
 */
@SideOnly(Side.CLIENT)
public final class ModernTabs {

    /** The version whose tabs these are. */
    public static final String VERSION = "1.19.3";
    /** The patch that draws them. */
    static final String PATCH = "P47";
    /** Vanilla's tab sprites: 1.12.2's are 28 pixels wide, 1.19.3's 26 (the backport supplies them). */
    public static final String TABS_TEXTURE = "textures/gui/container/creative_inventory/tabs.png";

    private static final String FILE = "/assets/backportcore/creative/tabs_1_19_3.txt";

    /** 1.19.3's category tabs: its id, lang key, row and column, and icon. */
    public enum Def {
        BUILDING_BLOCKS("building_blocks", "buildingBlocks", true, 0, false, "minecraft:bricks"),
        COLORED_BLOCKS("colored_blocks", "coloredBlocks", true, 1, false, "minecraft:cyan_wool"),
        NATURAL_BLOCKS("natural_blocks", "natural", true, 2, false, "minecraft:grass_block"),
        FUNCTIONAL_BLOCKS("functional_blocks", "functional", true, 3, false, "minecraft:oak_sign"),
        REDSTONE_BLOCKS("redstone_blocks", "redstone", true, 4, false, "minecraft:redstone"),
        TOOLS_AND_UTILITIES("tools_and_utilities", "tools", false, 0, false, "minecraft:diamond_pickaxe"),
        COMBAT("combat", "combat", false, 1, false, "minecraft:netherite_sword"),
        FOOD_AND_DRINKS("food_and_drinks", "foodAndDrink", false, 2, false, "minecraft:golden_apple"),
        INGREDIENTS("ingredients", "ingredients", false, 3, false, "minecraft:iron_ingot"),
        SPAWN_EGGS("spawn_eggs", "spawnEggs", false, 4, false, "minecraft:pig_spawn_egg"),
        OP_BLOCKS("op_blocks", "op", false, 5, true, "minecraft:command_block");

        public final String id;
        public final String langKey;
        public final boolean top;
        public final int column;
        public final boolean alignedRight;
        final String icon;

        Def(String id, String key, boolean top, int column, boolean alignedRight, String icon) {
            this.id = id;
            this.langKey = "backportcore.itemGroup." + key;
            this.top = top;
            this.column = column;
            this.alignedRight = alignedRight;
            this.icon = icon;
        }
    }

    private static final Map<Def, ModernTab> TABS = new EnumMap<Def, ModernTab>(Def.class);
    private static Map<String, Map<String, Integer>> table;
    private static Map<String, List<String>> opItems;
    private static Map<Def, List<ItemStack>> contents = Collections.emptyMap();
    private static Map<Item, Def> firstTab = new IdentityHashMap<Item, Def>();
    private static final Set<String> WARNED = new HashSet<String>();

    private ModernTabs() {
    }

    // ---- State ----

    /**
     * From Core's load complete on a client, after every mod has made its own tabs: adds the eleven tabs, once P47 is
     * in place (the creative screen is loaded for it). Without the patch no tab is added and 1.12.2's tabs stay.
     */
    public static synchronized void install() {
        if (!TABS.isEmpty()) {
            return;
        }
        try {
            Class.forName("net.minecraft.client.gui.inventory.GuiContainerCreative");
        } catch (Throwable t) {
            BackportCore.LOGGER.warn("Creative tabs: could not load the creative screen", t);
        }
        String state = Asm.state(PATCH);
        if (!"applied".equals(state)) {
            BackportCore.LOGGER.warn("Creative tabs: patch {} is not in place ({}); 1.19.3's tabs are off", PATCH, state);
            return;
        }
        for (Def def : Def.values()) {
            TABS.put(def, new ModernTab(def));
        }
        BackportCore.LOGGER.info("Creative tabs: 1.19.3's tabs added at indexes {} to {}", TABS.get(Def.BUILDING_BLOCKS).getIndex(),
                TABS.get(Def.OP_BLOCKS).getIndex());
    }

    /** @return true once the tabs exist (P47 is in place) */
    public static boolean installed() {
        return !TABS.isEmpty();
    }

    /** @return true while the creative menu shows 1.19.3's tabs: the inventory part looks like 1.19.3 or later */
    public static boolean active() {
        return installed() && Presentation.isAtLeast(LookArea.INVENTORY, VERSION);
    }

    /** @return the tab for {@code def}, or null before {@link #install} */
    @Nullable
    public static ModernTab tab(Def def) {
        return TABS.get(def);
    }

    /** @return how many tabs this adds to the array (none until installed) */
    static int count() {
        return TABS.size();
    }

    /**
     * @return true when a tab shows on the first page: Operator Utilities only with Operator Items Tab on and a player
     * allowed commands (1.19.3: {@code hasPermissions}, operator level 2)
     */
    static boolean shown(Def def) {
        if (def != Def.OP_BLOCKS) {
            return true;
        }
        if (!Presentation.OPERATOR_ITEMS_TAB.isShown() || !Presentation.OPERATOR_ITEMS_TAB.get()) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        return mc.player != null && mc.player.canUseCommand(2, "");
    }

    /** @return true when the tab sprites are a backport's (1.19.3's, 26 wide) rather than 1.12.2's (28 wide) */
    static boolean modernSprites() {
        return Presentation.asset(TABS_TEXTURE) != null;
    }

    // ---- Tables ----

    /** @return {@code modernId}'s place in {@code def}'s list (26.3's from 26.3 on), or -1 when it does not list it */
    static int rank(Def def, String modernId) {
        if (def != Def.OP_BLOCKS && ModernOrder.active()) {
            return ModernOrder.tabRank(def.id, modernId);
        }
        load();
        Map<String, Integer> order = table.get(def.id);
        Integer r = order == null ? null : order.get(ModernOrder.alias(modernId));
        return r == null ? -1 : r;
    }

    private static synchronized void load() {
        if (table != null) {
            return;
        }
        Map<String, Map<String, Integer>> out = new HashMap<String, Map<String, Integer>>();
        Map<String, List<String>> lists = new LinkedHashMap<String, List<String>>();
        try (InputStream in = ModernTabs.class.getResourceAsStream(FILE)) {
            if (in == null) {
                BackportCore.LOGGER.error("Creative tabs: {} is missing from the jar", FILE);
            } else {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                List<String> current = null;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] f = line.split(" ");
                    if ("tab".equals(f[0]) && f.length == 2) {
                        current = new ArrayList<String>();
                        lists.put(f[1], current);
                    } else if (current != null) {
                        current.add(f[0]);
                    }
                }
            }
        } catch (Exception e) {
            BackportCore.LOGGER.error("Creative tabs: could not read {}", FILE, e);
        }
        for (Map.Entry<String, List<String>> e : lists.entrySet()) {
            Map<String, Integer> order = new HashMap<String, Integer>();
            for (String id : e.getValue()) {
                if (!order.containsKey(id)) {
                    order.put(id, order.size());
                }
            }
            out.put(e.getKey(), order);
        }
        opItems = lists;
        table = out;
    }

    // ---- Contents ----

    /** @return the stacks {@code def}'s tab lists now (each call lists the 1.12.2 tabs again, as vanilla does) */
    static List<ItemStack> list(Def def) {
        try {
            contents = build();
        } catch (RuntimeException e) {
            if (WARNED.add("build")) {
                BackportCore.LOGGER.error("Creative tabs: could not list 1.19.3's tabs", e);
            }
        }
        List<ItemStack> out = contents.get(def);
        return out == null ? Collections.<ItemStack>emptyList() : out;
    }

    /**
     * @return the first 1.19.3 tab that lists {@code item} (the search tab's tooltip names it, as 1.19.3 names the
     * tabs an item is in), or null when none does
     */
    @Nullable
    static Def firstTab(Item item) {
        if (contents.isEmpty()) {
            list(Def.BUILDING_BLOCKS);
        }
        return firstTab.get(item);
    }

    private static Map<Def, List<ItemStack>> build() {
        CreativeTabsClient.rebuild();
        // Every stack 1.12.2's nine tabs list, once, with the tab it came from.
        List<ItemStack> stacks = new ArrayList<ItemStack>();
        List<CreativeTabs> from = new ArrayList<CreativeTabs>();
        Set<String> seen = new HashSet<String>();
        for (CreativeTabs tab : CreativeTabsClient.VANILLA_TABS) {
            CreativeTabs inArray = CreativeTabs.CREATIVE_TAB_ARRAY[tab.getIndex()];
            CreativeTabs lister = inArray instanceof VanillaTab ? inArray : tab;
            NonNullList<ItemStack> listed = NonNullList.create();
            lister.displayAllRelevantItems(listed);
            for (ItemStack s : listed) {
                if (s.isEmpty() || s.getItem() == Items.ENCHANTED_BOOK) {
                    continue;
                }
                if (seen.add(identity(s))) {
                    stacks.add(s);
                    from.add(tab);
                }
            }
        }
        // 1.19.3's books: every enchantment at its top level (generateEnchantmentBookTypesOnlyMaxLevel), in Ingredients.
        List<ItemStack> books = new ArrayList<ItemStack>();
        for (Enchantment e : Enchantment.REGISTRY) {
            if (e.type != null) {
                books.add(ItemEnchantedBook.getEnchantedItemStack(new EnchantmentData(e, e.getMaxLevel())));
            }
        }

        Map<Def, List<ItemStack>> lists = new EnumMap<Def, List<ItemStack>>(Def.class);
        Map<Def, List<Double>> keys = new EnumMap<Def, List<Double>>(Def.class);
        for (Def def : Def.values()) {
            lists.put(def, new ArrayList<ItemStack>());
            keys.put(def, new ArrayList<Double>());
        }
        Map<Item, Def> first = new IdentityHashMap<Item, Def>();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack s = stacks.get(i);
            boolean listed = false;
            for (Def def : Def.values()) {
                if (def == Def.OP_BLOCKS) {
                    continue;
                }
                double key = CreativeTabsClient.modernKey(id -> rank(def, id), s);
                if (!Double.isNaN(key)) {
                    lists.get(def).add(s);
                    keys.get(def).add(key);
                    listed = true;
                    if (!first.containsKey(s.getItem())) {
                        first.put(s.getItem(), def);
                    }
                }
            }
            if (!listed) {
                Def home = fallback(s, from.get(i));
                lists.get(home).add(s);
                keys.get(home).add(Double.POSITIVE_INFINITY);
                if (!first.containsKey(s.getItem())) {
                    first.put(s.getItem(), home);
                }
            }
        }
        int bookRank = rank(Def.INGREDIENTS, "minecraft:enchanted_book");
        for (int i = 0; i < books.size(); i++) {
            lists.get(Def.INGREDIENTS).add(books.get(i));
            keys.get(Def.INGREDIENTS).add(bookRank < 0 ? Double.POSITIVE_INFINITY : bookRank + i * 1e-6);
        }
        if (!books.isEmpty()) {
            first.put(Items.ENCHANTED_BOOK, Def.INGREDIENTS);
        }

        Map<Def, List<ItemStack>> out = new EnumMap<Def, List<ItemStack>>(Def.class);
        for (Def def : Def.values()) {
            final List<Double> k = keys.get(def);
            List<ItemStack> l = lists.get(def);
            Integer[] order = new Integer[l.size()];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            // Stable: the variants of one item, and stacks no table lists, keep the order they were listed in.
            java.util.Arrays.sort(order, Comparator.comparingDouble(i -> k.get(i)));
            List<ItemStack> sorted = new ArrayList<ItemStack>(order.length);
            for (int i : order) {
                sorted.add(l.get(i));
            }
            out.put(def, sorted);
        }
        out.put(Def.OP_BLOCKS, operatorItems());
        firstTab = first;
        return out;
    }

    /** @return 1.19.3's Operator Utilities as 1.12.2 has them: no 1.12.2 tab lists these items */
    private static List<ItemStack> operatorItems() {
        load();
        List<ItemStack> out = new ArrayList<ItemStack>();
        List<String> ids = opItems.get(Def.OP_BLOCKS.id);
        if (ids == null) {
            return out;
        }
        for (String id : ids) {
            Item item = Item.getByNameOrId(id);
            if (item != null && item != Items.AIR) {
                out.add(new ItemStack(item));
            }
        }
        return out;
    }

    /** @return where a stack no table lists goes: by the 1.12.2 tab that listed it */
    static Def fallback(ItemStack stack, CreativeTabs from) {
        if (stack.getItem() instanceof ItemMonsterPlacer) {
            return Def.SPAWN_EGGS;
        }
        if (from == CreativeTabs.BUILDING_BLOCKS) {
            return Def.BUILDING_BLOCKS;
        }
        if (from == CreativeTabs.DECORATIONS) {
            return Def.FUNCTIONAL_BLOCKS;
        }
        if (from == CreativeTabs.REDSTONE) {
            return Def.REDSTONE_BLOCKS;
        }
        if (from == CreativeTabs.TRANSPORTATION || from == CreativeTabs.TOOLS) {
            return Def.TOOLS_AND_UTILITIES;
        }
        if (from == CreativeTabs.COMBAT) {
            return Def.COMBAT;
        }
        if (from == CreativeTabs.FOOD || from == CreativeTabs.BREWING) {
            return Def.FOOD_AND_DRINKS;
        }
        return Def.INGREDIENTS;
    }

    private static String identity(ItemStack s) {
        return s.getItem().getRegistryName() + "@" + s.getMetadata() + (s.hasTagCompound() ? s.getTagCompound().toString() : "");
    }

    // ---- Icons ----

    /** @return 1.19.3's icon for {@code def}: the backport item that is it, or 1.12.2's stack of it */
    static ItemStack icon(Def def) {
        for (Placement p : CreativePlacement.all()) {
            if (def.icon.equals(p.modernId) && p.eggEntity == null) {
                Item item = p.item();
                if (item != null) {
                    return new ItemStack(item, 1, p.meta == CreativePlacement.ANY ? 0 : p.meta);
                }
            }
        }
        switch (def) {
            case BUILDING_BLOCKS:
                return new ItemStack(Blocks.BRICK_BLOCK);
            case COLORED_BLOCKS:
                return new ItemStack(Blocks.WOOL, 1, 9);
            case NATURAL_BLOCKS:
                return new ItemStack(Blocks.GRASS);
            case FUNCTIONAL_BLOCKS:
                return new ItemStack(Items.SIGN);
            case REDSTONE_BLOCKS:
                return new ItemStack(Items.REDSTONE);
            case TOOLS_AND_UTILITIES:
                return new ItemStack(Items.DIAMOND_PICKAXE);
            case COMBAT:
                // 1.19.3's is the netherite sword; without a backport that has one, 1.12.2's best.
                return new ItemStack(Items.DIAMOND_SWORD);
            case FOOD_AND_DRINKS:
                return new ItemStack(Items.GOLDEN_APPLE);
            case INGREDIENTS:
                return new ItemStack(Items.IRON_INGOT);
            case SPAWN_EGGS: {
                ItemStack egg = new ItemStack(Items.SPAWN_EGG);
                ItemMonsterPlacer.applyEntityIdToItemStack(egg, new ResourceLocation("minecraft", "pig"));
                return egg;
            }
            default:
                return new ItemStack(Blocks.COMMAND_BLOCK);
        }
    }
}

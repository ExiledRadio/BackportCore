package com.exiledradio.backportcore.creative.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.creative.CreativePlacement;
import com.exiledradio.backportcore.creative.CreativePlacement.Placement;
import com.exiledradio.backportcore.creative.VanillaOrder;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The client side of {@link CreativePlacement}: replaces each vanilla tab that has a placement with a
 * {@link VanillaTab}, and moves the placed stacks when a tab is listed.
 *
 * <p>Each placed stack goes right after the nearest stack before it in vanilla's order ({@link VanillaOrder}) that
 * the tab shows: a vanilla item, or a backport item placed before it. A stack with nothing before it in the tab
 * goes before the nearest one after it. Other mods' items are never moved and are never anchors.
 */
@SideOnly(Side.CLIENT)
public final class CreativeTabsClient {

    /**
     * Dev check: {@code -Dbackportcore.creativeDump=<file>} writes every vanilla tab's list at load complete, to the
     * file and the log: each placed stack marked, with the stack before it.
     */
    private static final String DUMP = "backportcore.creativeDump";
    /** With {@code -Dbackportcore.creativeDump.exit=true} the client then closes. */
    private static final String DUMP_EXIT = "backportcore.creativeDump.exit";

    private static final double NONE = Double.NaN;

    /** 1.12.2's nine item tabs, in the tables' order. */
    static final CreativeTabs[] VANILLA_TABS = {CreativeTabs.BUILDING_BLOCKS, CreativeTabs.DECORATIONS,
            CreativeTabs.REDSTONE, CreativeTabs.TRANSPORTATION, CreativeTabs.MISC, CreativeTabs.FOOD, CreativeTabs.TOOLS,
            CreativeTabs.COMBAT, CreativeTabs.BREWING};

    private static int builtAt = -1;
    private static Map<Item, List<Placement>> byItem = new IdentityHashMap<Item, List<Placement>>();
    private static Map<ResourceLocation, Placement> byEgg = new HashMap<ResourceLocation, Placement>();
    private static Set<String> eggMods = new HashSet<String>();
    /** The mods that place items: the backports. */
    private static Set<String> backportMods = new HashSet<String>();
    private static Map<Placement, Double> keys = new IdentityHashMap<Placement, Double>();
    private static final Map<Item, Map<Integer, Double>> VANILLA = new IdentityHashMap<Item, Map<Integer, Double>>();
    private static final Map<ResourceLocation, Double> VANILLA_EGGS = new HashMap<ResourceLocation, Double>();
    private static final Set<String> WARNED = new HashSet<String>();

    private CreativeTabsClient() {
    }

    /**
     * From Core's init on a client: every block and item is registered, and no tab has been listed. With a backport of
     * 26.3 or later installed every vanilla tab is replaced, as each may be read in 26.3's order ({@link ModernOrder}).
     */
    public static void install() {
        int n = 0;
        List<CreativeTabs> tabs = placedTabs();
        if (ModernOrder.installed()) {
            for (CreativeTabs tab : VANILLA_TABS) {
                if (!tabs.contains(tab)) {
                    tabs.add(tab);
                }
            }
        }
        for (CreativeTabs tab : tabs) {
            CreativeTabs inArray = CreativeTabs.CREATIVE_TAB_ARRAY[tab.getIndex()];
            if (inArray instanceof VanillaTab) {
                continue;
            }
            if (inArray != tab) {
                // Another mod has replaced this slot already; leave its tab alone.
                BackportCore.LOGGER.warn("Creative tabs: the {} tab's slot holds {}; the backports' items in it keep "
                        + "their own order", tab.getTabLabel(), inArray.getClass().getName());
                continue;
            }
            new VanillaTab(tab);
            n++;
        }
        BackportCore.LOGGER.info("Creative tabs: {} placements in {} vanilla tabs", CreativePlacement.all().size(), n);
    }

    private static List<CreativeTabs> placedTabs() {
        List<CreativeTabs> out = new ArrayList<CreativeTabs>();
        for (Placement p : CreativePlacement.all()) {
            CreativeTabs tab = p.tab;
            if (tab == null) {
                Item item = p.item();
                tab = item != null ? item.getCreativeTab() : null;
            }
            if (tab != null && !(tab instanceof VanillaTab) && !out.contains(tab)) {
                out.add(tab);
            }
        }
        if (!CreativePlacement.spawnEggMods().isEmpty() && !out.contains(CreativeTabs.MISC)) {
            out.add(CreativeTabs.MISC);
        }
        return out;
    }

    /**
     * Moves the placed stacks among {@code items} from {@code start} on (a tab's list) to their places; from 26.3 on,
     * then lists the whole tab in 26.3's order ({@link ModernOrder}).
     */
    static void arrange(CreativeTabs tab, NonNullList<ItemStack> items, int start) {
        arrange(items, start);
        if (ModernOrder.active()) {
            sortModern(tab, items, start);
        }
    }

    /** Moves the placed stacks among {@code items} from {@code start} on (a tab's list) to their places. */
    static void arrange(NonNullList<ItemStack> items, int start) {
        try {
            rebuild();
            List<ItemStack> base = new ArrayList<ItemStack>();
            List<Double> baseKeys = new ArrayList<Double>();
            List<String> baseIds = new ArrayList<String>();
            List<ItemStack> pendingStacks = new ArrayList<ItemStack>();
            List<Double> pendingKeys = new ArrayList<Double>();
            List<Placement> pendingPlacements = new ArrayList<Placement>();
            for (int i = start; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                Placement p = placement(stack);
                double key = p == null ? NONE : key(p);
                if (p != null && !Double.isNaN(key)) {
                    // Stacks of one placement (an item's variants) keep their order: a tiny step per stack.
                    pendingStacks.add(stack);
                    pendingKeys.add(key + pendingStacks.size() * 1e-7);
                    pendingPlacements.add(p);
                } else {
                    base.add(stack);
                    baseKeys.add(p == null ? vanillaKey(stack) : NONE);
                    baseIds.add(p == null ? VanillaOrder.modernId(stack) : null);
                }
            }
            if (pendingStacks.isEmpty()) {
                return;
            }
            Integer[] order = new Integer[pendingStacks.size()];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            final List<Double> pk = pendingKeys;
            java.util.Arrays.sort(order, Comparator.comparingDouble(i -> pk.get(i)));
            for (int idx : order) {
                double key = pendingKeys.get(idx);
                Placement p = pendingPlacements.get(idx);
                String id = p.modernId != null ? p.modernId : p.anchor;
                int at = slot(baseKeys, baseIds, key, id, p.modernId != null && VanillaOrder.placedBefore(p.modernId));
                base.add(at, pendingStacks.get(idx));
                baseKeys.add(at, key);
                baseIds.add(at, id);
            }
            for (int i = 0; i < base.size(); i++) {
                items.set(start + i, base.get(i));
            }
        } catch (RuntimeException e) {
            // A listing is never lost over the order: the original's stays as it was listed.
            if (WARNED.add("arrange")) {
                BackportCore.LOGGER.error("Creative tabs: could not order a tab", e);
            }
        }
    }

    /**
     * Lists {@code items} from {@code start} on in 26.3's order for {@code tab} (one of 1.12.2's nine): each stack by
     * the 26.3 item it is (a backport's by its placement, an item placed after another just after that one), the
     * stacks of one item in the order they were listed, and the stacks 26.3 does not list after the rest, as they were.
     */
    public static void sortModern(CreativeTabs tab, NonNullList<ItemStack> items, int start) {
        try {
            rebuild();
            final List<ItemStack> stacks = new ArrayList<ItemStack>(items.subList(start, items.size()));
            final double[] keys = new double[stacks.size()];
            Integer[] order = new Integer[stacks.size()];
            for (int i = 0; i < keys.length; i++) {
                double k = modernKey(tab, stacks.get(i));
                keys[i] = Double.isNaN(k) ? Double.POSITIVE_INFINITY : k;
                order[i] = i;
            }
            // Stable: equal keys (the variants of one item, the stacks 26.3 does not list) keep their order.
            java.util.Arrays.sort(order, Comparator.comparingDouble(i -> keys[i]));
            for (int i = 0; i < order.length; i++) {
                items.set(start + i, stacks.get(order[i]));
            }
        } catch (RuntimeException e) {
            if (WARNED.add("modern")) {
                BackportCore.LOGGER.error("Creative tabs: could not list a tab in 26.3's order", e);
            }
        }
    }

    /** One table's places for today's item ids: an id's place, or -1 when the table does not list it. */
    interface Ranker {
        int rank(String modernId);
    }

    /** @return a stack's place in 26.3's order for {@code tab}, or NONE when 26.3 does not list it */
    static double modernKey(CreativeTabs tab, ItemStack stack) {
        return modernKey(id -> ModernOrder.rank(tab, id), stack);
    }

    /**
     * @return a stack's place in a table ({@link ModernTabs}' tabs use this too): by the item it is today (a vanilla
     * stack through {@code legacy_items.txt}, a backport's by its placement or name, one placed after another just
     * after that one), or NONE when the table does not list it
     */
    static double modernKey(Ranker table, ItemStack stack) {
        Placement p = placement(stack);
        if (p == null) {
            String id = VanillaOrder.modernId(stack);
            if (id != null) {
                return modernRank(table, id);
            }
            // A backport's item it did not place (a mod that places others is a backport) is found by its name, as
            // an item placed after another is; another mod's item has no place.
            ResourceLocation mod = stack.getItem().getRegistryName();
            if (mod == null || !backportMods.contains(mod.getNamespace())) {
                return NONE;
            }
        }
        if (p == null || p.modernId == null) {
            // An item placed after another because Core's older table lacks it may be a 26.3 item by its name: its
            // own (a straw bed), or, for an item with a metadata per dye color, that color's (Game Drops' cushion; its
            // wool stairs, one item per few colors named after the first, pink_wool_stairs@7 being the gray ones).
            String id = namedId(stack);
            double named = id == null ? NONE : modernRank(table, id);
            ResourceLocation own = stack.getItem().getRegistryName();
            if (Double.isNaN(named) && own != null) {
                named = modernRank(table, "minecraft:" + own.getPath());
            }
            if (!Double.isNaN(named) || p == null) {
                return named;
            }
        }
        return modernKey(table, p, 0);
    }

    private static final String[] DYES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    /**
     * @return the 26.3 id a backport stack's name gives: {@code minecraft:<path>}, or for an item with subtypes the
     * dye color of its metadata in place of the color its name starts with (or before the name)
     */
    @Nullable
    static String namedId(ItemStack stack) {
        ResourceLocation name = stack.getItem().getRegistryName();
        if (name == null) {
            return null;
        }
        String path = name.getPath();
        int meta = stack.getMetadata();
        if (!stack.getHasSubtypes() || meta < 0 || meta >= DYES.length) {
            return "minecraft:" + path;
        }
        String rest = path;
        for (String dye : DYES) {
            if (path.startsWith(dye + "_") && path.length() - dye.length() - 1 < rest.length()) {
                rest = path.substring(dye.length() + 1);
            }
        }
        return "minecraft:" + DYES[meta] + "_" + rest;
    }

    private static double modernKey(Ranker table, Placement p, int depth) {
        if (p.modernId != null) {
            return modernRank(table, p.modernId);
        }
        if (p.anchor == null || depth > 16) {
            return NONE;
        }
        // An item vanilla does not have, after its anchor: a vanilla item, or another backport item.
        double anchor = modernRank(table, p.anchor);
        if (Double.isNaN(anchor)) {
            Item item = Item.getByNameOrId(p.anchor);
            List<Placement> list = item == null ? null : byItem.get(item);
            if (list != null && !list.isEmpty()) {
                anchor = modernKey(table, list.get(0), depth + 1);
            }
        }
        return Double.isNaN(anchor) ? NONE : anchor + 0.001 * (depth + 1);
    }

    private static double modernRank(Ranker table, String modernId) {
        int r = table.rank(modernId);
        return r < 0 ? NONE : r;
    }

    /**
     * @return where a stack at {@code key} goes: after the nearest stack before it in vanilla's order, or before
     * the nearest one after it. A tab lists 1.12.2's items in 1.12.2's order, so the two need not stand together;
     * the one of the same kind wins (the longer shared name ending: a shulker box goes with the white shulker box,
     * not the glass pane before it in vanilla), then the nearer in vanilla's order, then the one before.
     */
    private static int slot(List<Double> keys, List<String> ids, double key, @Nullable String id, boolean before) {
        int after = preceding(keys, key);
        int ahead = following(keys, key);
        if (after < 0 && ahead < 0) {
            return keys.size();
        }
        if (after < 0) {
            return ahead;
        }
        if (ahead < 0) {
            return after;
        }
        if (before) {
            return ahead;
        }
        int sharedAfter = sharedEnding(id, ids.get(after - 1));
        int sharedAhead = sharedEnding(id, ids.get(ahead));
        if (sharedAhead != sharedAfter) {
            return sharedAhead > sharedAfter ? ahead : after;
        }
        double gapAfter = key - keys.get(after - 1);
        double gapAhead = keys.get(ahead) - key;
        return gapAhead + 1e-4 < gapAfter ? ahead : after;
    }

    /** @return how many {@code _}-separated words two ids end with in common ({@code stone_slab}, {@code dye}) */
    static int sharedEnding(@Nullable String a, @Nullable String b) {
        if (a == null || b == null) {
            return 0;
        }
        String[] x = a.substring(a.indexOf(':') + 1).split("_");
        String[] y = b.substring(b.indexOf(':') + 1).split("_");
        int n = 0;
        while (n < x.length && n < y.length && x[x.length - 1 - n].equals(y[y.length - 1 - n])) {
            n++;
        }
        return n;
    }

    /** @return the index just after the greatest key below {@code key} (the last such), or -1 */
    private static int preceding(List<Double> keys, double key) {
        int at = -1;
        double best = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < keys.size(); j++) {
            double k = keys.get(j);
            if (!Double.isNaN(k) && k < key && k >= best) {
                best = k;
                at = j + 1;
            }
        }
        return at;
    }

    /** @return the index of the least key above {@code key} (the first such), or -1 */
    private static int following(List<Double> keys, double key) {
        int at = -1;
        double best = Double.POSITIVE_INFINITY;
        for (int j = 0; j < keys.size(); j++) {
            double k = keys.get(j);
            if (!Double.isNaN(k) && k > key && k < best) {
                best = k;
                at = j;
            }
        }
        return at;
    }

    static void rebuild() {
        int now = CreativePlacement.changes();
        if (now == builtAt) {
            return;
        }
        Map<Item, List<Placement>> items = new IdentityHashMap<Item, List<Placement>>();
        Map<ResourceLocation, Placement> eggs = new HashMap<ResourceLocation, Placement>();
        for (Placement p : CreativePlacement.all()) {
            if (p.eggEntity != null) {
                eggs.put(p.eggEntity, p);
                continue;
            }
            Item item = p.item();
            if (item != null) {
                List<Placement> list = items.get(item);
                if (list == null) {
                    list = new ArrayList<Placement>();
                    items.put(item, list);
                }
                list.add(p);
            }
        }
        Set<String> mods = new HashSet<String>(CreativePlacement.spawnEggMods());
        for (Item item : items.keySet()) {
            if (item.getRegistryName() != null) {
                mods.add(item.getRegistryName().getNamespace());
            }
        }
        mods.remove("minecraft");
        byItem = items;
        byEgg = eggs;
        backportMods = mods;
        eggMods = new HashSet<String>(CreativePlacement.spawnEggMods());
        keys = new IdentityHashMap<Placement, Double>();
        builtAt = now;
    }

    @Nullable
    static Placement placement(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.SPAWN_EGG) {
            ResourceLocation entity = ItemMonsterPlacer.getNamedIdFrom(stack);
            if (entity == null) {
                return null;
            }
            Placement p = byEgg.get(entity);
            if (p == null && eggMods.contains(entity.getNamespace())) {
                p = CreativePlacement.eggByName(entity);
                byEgg.put(entity, p);
            }
            return p;
        }
        List<Placement> list = byItem.get(item);
        if (list == null) {
            return null;
        }
        Placement any = null;
        for (Placement p : list) {
            if (p.meta == stack.getMetadata()) {
                return p;
            }
            if (p.meta == CreativePlacement.ANY && any == null) {
                any = p;
            }
        }
        return any;
    }

    /** @return a placement's spot in vanilla's order: its item's, or just after its anchor's */
    private static double key(Placement p) {
        Double cached = keys.get(p);
        if (cached != null) {
            return cached;
        }
        keys.put(p, NONE); // an anchor chain that loops ends here
        double key = NONE;
        if (p.modernId != null) {
            key = VanillaOrder.key(p.modernId);
        } else if (p.anchor != null) {
            double anchor = anchorKey(p.anchor);
            key = Double.isNaN(anchor) ? NONE : anchor + 0.0005;
        }
        if (Double.isNaN(key) && WARNED.add(String.valueOf(p.modernId) + p.anchor)) {
            BackportCore.LOGGER.warn("Creative tabs: no place for {} in vanilla's order; it stays where it is listed",
                    p.modernId != null ? p.modernId : "an item after " + p.anchor);
        }
        keys.put(p, key);
        return key;
    }

    private static double anchorKey(String anchor) {
        double vanilla = VanillaOrder.key(anchor);
        if (!Double.isNaN(vanilla)) {
            return vanilla;
        }
        // Another backport item, by registry name.
        Item item = Item.getByNameOrId(anchor);
        if (item != null) {
            List<Placement> list = byItem.get(item);
            if (list != null && !list.isEmpty()) {
                return key(list.get(0));
            }
        }
        return NONE;
    }

    /** @return a vanilla stack's spot in vanilla's order, or NONE for another mod's item or one vanilla lost */
    private static double vanillaKey(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.SPAWN_EGG) {
            ResourceLocation entity = ItemMonsterPlacer.getNamedIdFrom(stack);
            if (entity == null) {
                return NONE;
            }
            Double cached = VANILLA_EGGS.get(entity);
            if (cached == null) {
                cached = lookUp(stack);
                VANILLA_EGGS.put(entity, cached);
            }
            return cached;
        }
        Map<Integer, Double> metas = VANILLA.get(item);
        if (metas == null) {
            metas = new HashMap<Integer, Double>();
            VANILLA.put(item, metas);
        }
        Double cached = metas.get(stack.getMetadata());
        if (cached == null) {
            cached = lookUp(stack);
            metas.put(stack.getMetadata(), cached);
        }
        return cached;
    }

    private static double lookUp(ItemStack stack) {
        String id = VanillaOrder.modernId(stack);
        if (id == null) {
            return NONE;
        }
        return VanillaOrder.key(id);
    }

    /**
     * From Core's load-complete on a client: the dev check, when asked for, runs at the first title screen, once every
     * mod (JEI's ingredient list too) has finished loading.
     */
    public static void loadComplete() {
        ModernTabs.install();
        String file = System.getProperty(DUMP);
        if (file != null && !file.isEmpty()) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new DumpAtTitle());
        }
    }

    /** Public for the event bus. */
    public static final class DumpAtTitle {
        private boolean done;

        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public void onGui(net.minecraftforge.client.event.GuiOpenEvent event) {
            if (!this.done && event.getGui() instanceof net.minecraft.client.gui.GuiMainMenu) {
                this.done = true;
                writeDump();
            }
        }
    }

    static void writeDump() {
        String file = System.getProperty(DUMP);
        File out = new File(file);
        try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8.name())) {
            java.io.StringWriter text = new java.io.StringWriter();
            dump(new PrintWriter(text));
            w.print(text);
            for (String line : text.toString().split("\r?\n")) {
                BackportCore.LOGGER.info("Creative tabs | {}", line);
            }
            BackportCore.LOGGER.info("Creative tabs: wrote every vanilla tab's list to {}", out.getAbsolutePath());
        } catch (Exception e) {
            BackportCore.LOGGER.error("Creative tabs: could not write {}", out.getAbsolutePath(), e);
        }
        if (Boolean.getBoolean(DUMP_EXIT)) {
            FMLCommonHandler.instance().exitJava(0, false);
        }
    }

    private static void dump(PrintWriter w) {
        rebuild();
        Set<String> unmapped = new LinkedHashSet<String>();
        Set<String> notModern = new LinkedHashSet<String>();
        boolean modern = ModernOrder.active();
        Set<Placement> shown = new HashSet<Placement>();
        int placedStacks = 0;
        for (CreativeTabs tab : CreativeTabs.CREATIVE_TAB_ARRAY) {
            if (tab == null || tab == CreativeTabs.SEARCH || tab == CreativeTabs.HOTBAR || tab == CreativeTabs.INVENTORY) {
                continue;
            }
            NonNullList<ItemStack> items = NonNullList.create();
            tab.displayAllRelevantItems(items);
            boolean ours = tab instanceof VanillaTab;
            w.println("== " + tab.getTabLabel() + " (index " + tab.getIndex()
                    + (ours ? (modern ? ", in " + ModernOrder.VERSION + "'s order" : ", ordered by Core") : "")
                    + ", " + items.size() + " stacks)");
            String previous = "(start)";
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                Placement p = placement(stack);
                String name = describe(stack);
                String modernName = p != null ? (p.modernId != null ? p.modernId : "after " + p.anchor) : VanillaOrder.modernId(stack);
                if (p == null && modernName != null && VanillaOrder.position(modernName) < 0) {
                    unmapped.add(name + " -> " + modernName);
                }
                if (modern && ours && Double.isNaN(modernKey(((VanillaTab) tab).original(), stack))) {
                    notModern.add(tab.getTabLabel() + ": " + name + (modernName == null ? "" : " -> " + modernName));
                }
                if (p != null) {
                    shown.add(p);
                    placedStacks++;
                    w.println(String.format("  %4d * %-48s %-44s after %s", i, name, modernName, previous));
                } else {
                    w.println(String.format("  %4d   %-48s %s", i, name, modernName == null ? "" : modernName));
                }
                previous = name;
            }
        }
        w.println();
        w.println("== Placed stacks shown: " + placedStacks + " (placements " + CreativePlacement.all().size() + ")");
        w.println("== Placements with no stack in any tab (hidden, switched off, or not registered):");
        for (Placement p : CreativePlacement.all()) {
            if (!shown.contains(p)) {
                Item item = p.item();
                w.println("  " + (p.eggEntity != null ? "egg " + p.eggEntity : item == null ? "(no item)" : item.getRegistryName())
                        + " @" + p.meta + " " + (p.modernId != null ? p.modernId : "after " + p.anchor));
            }
        }
        w.println("== Vanilla stacks with no place in vanilla's order (fix legacy_items.txt):");
        for (String s : unmapped) {
            w.println("  " + s);
        }
        if (modern) {
            w.println("== Stacks " + ModernOrder.VERSION + "'s order does not list (after the rest of their tab):");
            for (String s : notModern) {
                w.println("  " + s);
            }
        }
        searchAndJei(w);
    }

    /**
     * The other ways a stack is found: the search tab (and every mod that asks an item for its tabs) lists an item
     * through {@code getSubItems} with vanilla's own tab objects; JEI builds its list the same way.
     */
    private static void searchAndJei(PrintWriter w) {
        int items = 0;
        int inSearch = 0;
        int ownTab = 0;
        Set<Item> placed = new LinkedHashSet<Item>();
        for (Placement p : CreativePlacement.all()) {
            Item item = p.item();
            if (item != null && item.getCreativeTab() != null) {
                placed.add(item);
            }
        }
        List<String> missing = new ArrayList<String>();
        for (Item item : placed) {
            items++;
            NonNullList<ItemStack> search = NonNullList.create();
            item.getSubItems(CreativeTabs.SEARCH, search);
            NonNullList<ItemStack> own = NonNullList.create();
            item.getSubItems(item.getCreativeTab(), own);
            if (!search.isEmpty()) {
                inSearch++;
            } else {
                missing.add(item.getRegistryName() + " (search)");
            }
            if (!own.isEmpty() && !(item.getCreativeTab() instanceof VanillaTab)) {
                ownTab++;
            } else {
                missing.add(item.getRegistryName() + " (its tab)");
            }
        }
        w.println("== Search tab: " + inSearch + " of " + items + " placed items list themselves for it; " + ownTab
                + " for their own (vanilla) tab object");
        for (String m : missing) {
            w.println("  missing: " + m);
        }
        if (net.minecraftforge.fml.common.Loader.isModLoaded("jei")) {
            try {
                Object registry = Class.forName("mezz.jei.Internal").getMethod("getIngredientRegistry").invoke(null);
                java.util.Collection<?> all = (java.util.Collection<?>) registry.getClass()
                        .getMethod("getAllIngredients", Class.class).invoke(registry, ItemStack.class);
                int found = 0;
                Set<Item> seen = new HashSet<Item>();
                for (Object o : all) {
                    Item it = ((ItemStack) o).getItem();
                    if (placed.contains(it) && seen.add(it)) {
                        found++;
                    }
                }
                w.println("== JEI: " + found + " of " + items + " placed items in its ingredient list (" + all.size()
                        + " stacks in all)");
                for (Item it : placed) {
                    if (!seen.contains(it)) {
                        w.println("  not in JEI: " + it.getRegistryName());
                    }
                }
            } catch (Exception e) {
                w.println("== JEI: could not read its ingredient list: " + e);
            }
        } else {
            w.println("== JEI: not installed");
        }
    }

    private static String describe(ItemStack stack) {
        Item item = stack.getItem();
        String name = String.valueOf(item.getRegistryName());
        if (item == Items.SPAWN_EGG) {
            return name + "{" + ItemMonsterPlacer.getNamedIdFrom(stack) + "}";
        }
        return name + "@" + stack.getMetadata();
    }
}

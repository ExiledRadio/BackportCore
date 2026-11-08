package com.exiledradio.backportcore.creative;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Vanilla's creative order, as data: Minecraft 1.19.2's tabs in their order (the last version with 1.12's tab set,
 * which lists every tab in item registry order), with each later item where Mojang's later registry puts it, and
 * the table that turns a 1.12.2 stack into today's item id. Both files are built by
 * {@code tools/creative/build_tables.py}. Common code: a backport asks it for an item's tab on either side.
 */
public final class VanillaOrder {

    private static final String ORDER = "/assets/backportcore/creative/vanilla_order.txt";
    private static final String LEGACY = "/assets/backportcore/creative/legacy_items.txt";

    private static Map<String, Integer> positions;
    private static Map<String, Double> keys;
    private static Map<String, String> tabs;
    private static Set<String> before;
    private static Map<String, String> legacy;
    private static Map<String, String> eggRenames;

    private VanillaOrder() {
    }

    /** @return the item's place in vanilla's creative order, or -1 if vanilla has no such item */
    public static int position(String modernId) {
        load();
        Integer p = positions.get(modernId);
        return p == null ? -1 : p;
    }

    /**
     * @return the item's spot in vanilla's order counted in 1.19.2's items (an item added later sits a fraction after
     * the 1.19.2 item before it), so a distance between two items is the number of 1.19.2 items between them; NaN if
     * vanilla has no such item
     */
    public static double key(String modernId) {
        load();
        Double k = keys.get(modernId);
        return k == null ? Double.NaN : k;
    }

    /** @return true if the item is found by what follows it (the first of a run), not by what comes before */
    public static boolean placedBefore(String modernId) {
        load();
        return before.contains(modernId);
    }

    /**
     * @return the item's tab in 1.19.2 (for a newer item, the tab Core gives it), or null if it has none or vanilla
     * has no such item
     */
    @Nullable
    public static CreativeTabs tab(String modernId) {
        load();
        String name = tabs.get(modernId);
        return name == null ? null : byName(name);
    }

    /** @return a vanilla tab's name in the tables ({@code BUILDING_BLOCKS}...), on either side */
    public static String nameOf(CreativeTabs tab) {
        for (String name : new String[]{"BUILDING_BLOCKS", "DECORATIONS", "REDSTONE", "TRANSPORTATION", "MISC", "FOOD",
                "TOOLS", "COMBAT", "BREWING"}) {
            if (byName(name) == tab) {
                return name;
            }
        }
        return "another mod's tab";
    }

    @Nullable
    static CreativeTabs byName(String name) {
        switch (name) {
            case "BUILDING_BLOCKS": return CreativeTabs.BUILDING_BLOCKS;
            case "DECORATIONS": return CreativeTabs.DECORATIONS;
            case "REDSTONE": return CreativeTabs.REDSTONE;
            case "TRANSPORTATION": return CreativeTabs.TRANSPORTATION;
            case "MISC": return CreativeTabs.MISC;
            case "FOOD": return CreativeTabs.FOOD;
            case "TOOLS": return CreativeTabs.TOOLS;
            case "COMBAT": return CreativeTabs.COMBAT;
            case "BREWING": return CreativeTabs.BREWING;
            default: return null;
        }
    }

    /**
     * @return today's id of a vanilla 1.12.2 stack ({@code minecraft:stone} at metadata 1 is {@code minecraft:granite};
     * a spawn egg is its mob's egg), or null for another mod's item
     */
    @Nullable
    public static String modernId(ItemStack stack) {
        Item item = stack.getItem();
        ResourceLocation name = item.getRegistryName();
        if (name == null || !"minecraft".equals(name.getNamespace())) {
            return null;
        }
        load();
        if (item == Items.SPAWN_EGG) {
            ResourceLocation entity = ItemMonsterPlacer.getNamedIdFrom(stack);
            if (entity == null || !"minecraft".equals(entity.getNamespace())) {
                return null;
            }
            String today = eggRenames.get(entity.toString());
            return (today != null ? today : entity.toString()) + "_spawn_egg";
        }
        String key = name.toString();
        String exact = legacy.get(key + "." + stack.getMetadata());
        if (exact != null) {
            return exact;
        }
        String plain = legacy.get(key);
        return plain != null ? plain : key;
    }

    private static synchronized void load() {
        if (positions != null) {
            return;
        }
        Map<String, Integer> pos = new HashMap<String, Integer>();
        Map<String, String> tab = new HashMap<String, String>();
        Set<String> first = new HashSet<String>();
        Map<String, String> old = new HashMap<String, String>();
        Map<String, String> eggs = new HashMap<String, String>();
        Map<String, Double> key = new HashMap<String, Double>();
        int n = 0;
        int last1192 = -1;
        int added = 0;
        for (String line : lines(ORDER)) {
            String[] f = line.split(" ");
            if (!pos.containsKey(f[0])) {
                pos.put(f[0], n++);
                boolean isNew = false;
                for (int i = 2; i < f.length; i++) {
                    isNew |= "new".equals(f[i]);
                }
                if (isNew) {
                    key.put(f[0], last1192 + ++added * 0.001);
                } else {
                    key.put(f[0], (double) ++last1192);
                    added = 0;
                }
            }
            if (f.length > 1 && !"-".equals(f[1])) {
                tab.put(f[0], f[1]);
            }
            for (int i = 2; i < f.length; i++) {
                if ("before".equals(f[i])) {
                    first.add(f[0]);
                }
            }
        }
        for (String line : lines(LEGACY)) {
            String[] f = line.split(" ");
            if (f.length == 3 && "egg".equals(f[0])) {
                eggs.put(f[1], f[2]);
            } else if (f.length == 2) {
                old.put(f[0], f[1]);
            }
        }
        keys = key;
        tabs = tab;
        before = first;
        legacy = old;
        eggRenames = eggs;
        positions = pos;
    }

    private static Iterable<String> lines(String path) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        try (InputStream in = VanillaOrder.class.getResourceAsStream(path)) {
            if (in == null) {
                BackportCore.LOGGER.error("Creative tabs: {} is missing from the jar", path);
                return out;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    out.add(line);
                }
            }
        } catch (Exception e) {
            BackportCore.LOGGER.error("Creative tabs: could not read {}", path, e);
        }
        return out;
    }
}

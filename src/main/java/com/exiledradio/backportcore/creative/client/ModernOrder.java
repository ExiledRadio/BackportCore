package com.exiledradio.backportcore.creative.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.creative.VanillaOrder;
import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Today's creative order on 1.12.2's tabs: from 26.3 (Game Drops) on, every vanilla tab lists its items in the order
 * 26.3's creative menu shows them, and from 26.4 on in 26.4's (Deep Freeze's ice and calcite sets, icicles, the
 * frostbite egg). Those versions have other tabs, so each 1.12.2 tab reads the tabs its items come from: its home tabs
 * first (Redstone reads Redstone Blocks, Miscellaneous reads Ingredients), then the others in the creative menu's order
 * (Building Blocks, Colored Blocks, Natural Blocks, Functional Blocks, Redstone Blocks, Tools &amp; Utilities, Combat,
 * Food &amp; Drinks, Ingredients, Spawn Eggs). An item listed twice (a log in Building Blocks and Natural Blocks) takes
 * the first of those places. Stacks the order does not list (another mod's, or one it dropped) follow, in the order
 * the tab had.
 *
 * <p>Before 26.3 every tab keeps 1.12.2's order with the backports' items by their neighbors
 * ({@link CreativeTabsClient}). Only the tabs' own listing changes: the search tab, JEI and {@code getSubItems} are
 * untouched. The tables are {@code creative/order_26_3.txt} and {@code creative/order_26_4.txt}, built by
 * {@code tools/creative/build_order_263.py} from each version's own {@code CreativeModeTabs}; the inventory part's
 * version picks one.
 */
@SideOnly(Side.CLIENT)
public final class ModernOrder {

    /** The first version with a table: the order is on from here. */
    public static final String VERSION = "26.3";

    /** The versions with a table, oldest first. */
    private static final String[] VERSIONS = {"26.3", "26.4"};

    private static final Map<String, Table> TABLES = new HashMap<String, Table>();

    private ModernOrder() {
    }

    /** @return true while the tabs read in a modern order: the inventory part looks like 26.3 or later */
    public static boolean active() {
        return Presentation.isAtLeast(LookArea.INVENTORY, VERSION);
    }

    /** @return true when the order can be turned on at all (a backport of 26.3 or later is installed) */
    public static boolean installed() {
        return Presentation.installedNewest().isAtLeast(VERSION);
    }

    /**
     * @return the version whose order the tabs read: the newest with a table the inventory part has reached (26.4 once
     * it looks like 26.4), 26.3 otherwise
     */
    public static String version() {
        for (int i = VERSIONS.length - 1; i > 0; i--) {
            if (Presentation.isAtLeast(LookArea.INVENTORY, VERSIONS[i])) {
                return VERSIONS[i];
            }
        }
        return VERSIONS[0];
    }

    /**
     * @return the place of today's item {@code modernId} in {@code tab} (one of 1.12.2's nine), or -1 when the
     * current order does not list it
     */
    public static int rank(CreativeTabs tab, String modernId) {
        return rank(version(), tab, modernId);
    }

    /** {@link #rank(CreativeTabs, String)} in the order of {@code version} (26.3 or 26.4). */
    public static int rank(String version, CreativeTabs tab, String modernId) {
        Table t = table(version);
        Map<String, Integer> order = t.ranks.get(VanillaOrder.nameOf(tab));
        if (order == null) {
            return -1;
        }
        Integer r = order.get(t.alias(modernId));
        return r == null ? -1 : r;
    }

    /**
     * @return the place of today's item {@code modernId} in the current order's own tab {@code tab26}
     * ({@code building_blocks}, ...: that tab's list alone), or -1 when that tab does not list it. {@link ModernTabs}
     * reads those tabs so.
     */
    public static int tabRank(String tab26, String modernId) {
        Table t = table(version());
        Map<String, Integer> order = t.ownTabs.get(tab26);
        if (order == null) {
            return -1;
        }
        Integer r = order.get(t.alias(modernId));
        return r == null ? -1 : r;
    }

    /** @return the id the current order knows {@code modernId} by (an older id it renamed becomes today's) */
    public static String alias(String modernId) {
        return table(version()).alias(modernId);
    }

    private static synchronized Table table(String version) {
        Table t = TABLES.get(version);
        if (t == null) {
            t = new Table(version);
            TABLES.put(version, t);
        }
        return t;
    }

    /** One version's order file, read. */
    private static final class Table {

        final Map<String, Map<String, Integer>> ranks;
        final Map<String, Map<String, Integer>> ownTabs;
        final Map<String, String> aliases;

        Table(String version) {
            String file = "/assets/backportcore/creative/order_" + version.replace('.', '_') + ".txt";
            Map<String, List<String>> homes = new LinkedHashMap<String, List<String>>();
            Map<String, String> renamed = new HashMap<String, String>();
            Map<String, List<String>> tabs = new LinkedHashMap<String, List<String>>();
            List<String> current = null;
            try (InputStream in = ModernOrder.class.getResourceAsStream(file)) {
                if (in == null) {
                    BackportCore.LOGGER.error("Creative tabs: {} is missing from the jar", file);
                } else {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        String[] f = line.split(" ");
                        if ("home".equals(f[0])) {
                            homes.put(f[1], new ArrayList<String>(Arrays.asList(f).subList(2, f.length)));
                        } else if ("alias".equals(f[0]) && f.length == 3) {
                            renamed.put(f[1], f[2]);
                        } else if ("tab".equals(f[0]) && f.length == 2) {
                            current = new ArrayList<String>();
                            tabs.put(f[1], current);
                        } else if (current != null) {
                            current.add(f[0]);
                        }
                    }
                }
            } catch (Exception e) {
                BackportCore.LOGGER.error("Creative tabs: could not read {}", file, e);
            }
            Map<String, Map<String, Integer>> out = new HashMap<String, Map<String, Integer>>();
            for (Map.Entry<String, List<String>> e : homes.entrySet()) {
                List<String> read = new ArrayList<String>(e.getValue());
                for (String tab : tabs.keySet()) {
                    if (!read.contains(tab)) {
                        read.add(tab);
                    }
                }
                Map<String, Integer> order = new HashMap<String, Integer>();
                for (String tab : read) {
                    List<String> items = tabs.get(tab);
                    if (items == null) {
                        BackportCore.LOGGER.warn("Creative tabs: {} has no {} tab", version, tab);
                        continue;
                    }
                    for (String id : items) {
                        if (!order.containsKey(id)) {
                            order.put(id, order.size());
                        }
                    }
                }
                out.put(e.getKey(), order);
            }
            Map<String, Map<String, Integer>> own = new HashMap<String, Map<String, Integer>>();
            for (Map.Entry<String, List<String>> e : tabs.entrySet()) {
                Map<String, Integer> order = new HashMap<String, Integer>();
                for (String id : e.getValue()) {
                    if (!order.containsKey(id)) {
                        order.put(id, order.size());
                    }
                }
                own.put(e.getKey(), order);
            }
            ownTabs = own;
            aliases = renamed;
            ranks = out;
        }

        String alias(String modernId) {
            String a = aliases.get(modernId);
            return a != null ? a : modernId;
        }
    }
}

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
 * 26.3's creative order on 1.12.2's tabs: from 26.3 (Game Drops) on, every vanilla tab lists its items in the order
 * 26.3's creative menu shows them. 26.3 has other tabs, so each 1.12.2 tab reads the 26.3 tabs its items come from:
 * its home tabs first (Redstone reads Redstone Blocks, Miscellaneous reads Ingredients), then the others in the
 * creative menu's order (Building Blocks, Colored Blocks, Natural Blocks, Functional Blocks, Redstone Blocks, Tools &amp;
 * Utilities, Combat, Food &amp; Drinks, Ingredients, Spawn Eggs). An item 26.3 lists twice (a log in Building Blocks and
 * Natural Blocks) takes the first of those places. Stacks 26.3 does not list (another mod's, or one 26.3 dropped)
 * follow, in the order the tab had.
 *
 * <p>Before 26.3 every tab keeps 1.12.2's order with the backports' items by their neighbors
 * ({@link CreativeTabsClient}). Only the tabs' own listing changes: the search tab, JEI and {@code getSubItems} are
 * untouched. The table is {@code creative/order_26_3.txt}, built by {@code tools/creative/build_order_263.py} from
 * 26.3's own {@code CreativeModeTabs}.
 */
@SideOnly(Side.CLIENT)
public final class ModernOrder {

    /** The version whose order this is. */
    public static final String VERSION = "26.3";

    private static final String FILE = "/assets/backportcore/creative/order_26_3.txt";

    private static Map<String, Map<String, Integer>> ranks;
    private static Map<String, Map<String, Integer>> ownTabs;
    private static Map<String, String> aliases;

    private ModernOrder() {
    }

    /** @return true while the tabs read in 26.3's order: the inventory part looks like 26.3 or later */
    public static boolean active() {
        return Presentation.isAtLeast(LookArea.INVENTORY, VERSION);
    }

    /** @return true when the order can be turned on at all (a backport of 26.3 or later is installed) */
    public static boolean installed() {
        return Presentation.installedNewest().isAtLeast(VERSION);
    }

    /**
     * @return the place of today's item {@code modernId} in {@code tab} (one of 1.12.2's nine), or -1 when 26.3 does
     * not list it
     */
    public static int rank(CreativeTabs tab, String modernId) {
        load();
        Map<String, Integer> order = ranks.get(VanillaOrder.nameOf(tab));
        if (order == null) {
            return -1;
        }
        String id = aliases.containsKey(modernId) ? aliases.get(modernId) : modernId;
        Integer r = order.get(id);
        return r == null ? -1 : r;
    }

    /**
     * @return the place of today's item {@code modernId} in 26.3's own tab {@code tab26} ({@code building_blocks},
     * ...: that tab's list alone), or -1 when that tab does not list it. {@link ModernTabs} reads 26.3's tabs so.
     */
    public static int tabRank(String tab26, String modernId) {
        load();
        Map<String, Integer> order = ownTabs.get(tab26);
        if (order == null) {
            return -1;
        }
        Integer r = order.get(alias(modernId));
        return r == null ? -1 : r;
    }

    /** @return the id 26.3 knows {@code modernId} by (an older id 26.3 renamed becomes 26.3's) */
    public static String alias(String modernId) {
        load();
        String a = aliases.get(modernId);
        return a != null ? a : modernId;
    }

    private static synchronized void load() {
        if (ranks != null) {
            return;
        }
        Map<String, List<String>> homes = new LinkedHashMap<String, List<String>>();
        Map<String, String> renamed = new HashMap<String, String>();
        Map<String, List<String>> tabs = new LinkedHashMap<String, List<String>>();
        List<String> current = null;
        try (InputStream in = ModernOrder.class.getResourceAsStream(FILE)) {
            if (in == null) {
                BackportCore.LOGGER.error("Creative tabs: {} is missing from the jar", FILE);
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
            BackportCore.LOGGER.error("Creative tabs: could not read {}", FILE, e);
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
                    BackportCore.LOGGER.warn("Creative tabs: 26.3 has no {} tab", tab);
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
}

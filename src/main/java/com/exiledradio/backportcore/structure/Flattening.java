package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft's own Flattening table, read backwards: from a block state of today's game to the
 * 1.12.2 state it was made from. Built by {@code tools/flattening/extract.py} from the data fixer
 * every Minecraft since 1.13 carries.
 *
 * <p>Names are today's ({@link #upgrade}): a 1.13 file's {@code grass_path} and a 1.21 file's
 * {@code dirt_path} meet as {@code dirt_path} here. Only blocks 1.12.2 had are in it; the rest come
 * from {@link ModernPalette}'s other sources.
 */
final class Flattening {

    /** One 1.12.2 id:meta: the 1.13 state it became, and every 1.12.2 combination it could show. */
    static final class Entry {
        final Map<String, String> modern;
        final String legacyName;
        final List<Map<String, String>> legacy;

        Entry(Map<String, String> modern, String legacyName, List<Map<String, String>> legacy) {
            this.modern = modern;
            this.legacyName = legacyName;
            this.legacy = legacy;
        }
    }

    private static Map<String, List<Entry>> table;

    private Flattening() {
    }

    /** @return every 1.12.2 id:meta whose 1.13 form is this block, in Mojang's order; empty if none */
    static List<Entry> entries(String modernName) {
        List<Entry> found = table().get(modernName);
        return found == null ? Collections.<Entry>emptyList() : found;
    }

    /** @return true if 1.12.2 had this block under some name */
    static boolean has(String modernName) {
        return table().containsKey(modernName);
    }

    /**
     * Brings an older file's name up to today's. Renames since 1.13.2 in Minecraft's data fixers
     * (schema versions in brackets): smooth stone slab and signs [1802], cauldrons holding water
     * [2679], dirt paths [2680], short grass [3692], iron chains [4541].
     *
     * @param props the state's properties; a cauldron's level is read from it
     */
    static String upgrade(String name, Map<String, String> props, int dataVersion) {
        if (dataVersion < 1802) {
            if ("minecraft:stone_slab".equals(name)) {
                name = "minecraft:smooth_stone_slab";
            } else if ("minecraft:sign".equals(name)) {
                name = "minecraft:oak_sign";
            } else if ("minecraft:wall_sign".equals(name)) {
                name = "minecraft:oak_wall_sign";
            }
        }
        if (dataVersion < 2679 && "minecraft:cauldron".equals(name)) {
            String level = props.get("level");
            if (level != null && !"0".equals(level)) {
                name = "minecraft:water_cauldron";
            }
        }
        if (dataVersion < 2680 && "minecraft:grass_path".equals(name)) {
            name = "minecraft:dirt_path";
        }
        if (dataVersion < 3692 && "minecraft:grass".equals(name)) {
            name = "minecraft:short_grass";
        }
        if (dataVersion < 4541 && "minecraft:chain".equals(name)) {
            name = "minecraft:iron_chain";
        }
        return name;
    }

    private static synchronized Map<String, List<Entry>> table() {
        if (table == null) {
            table = load();
        }
        return table;
    }

    private static Map<String, List<Entry>> load() {
        Map<String, List<Entry>> out = new HashMap<String, List<Entry>>();
        String path = "/assets/" + BackportCore.MOD_ID + "/structure/flattening.txt";
        InputStream in = Flattening.class.getResourceAsStream(path);
        if (in == null) {
            BackportCore.LOGGER.error("The Flattening table {} is missing from the jar; modern structures will be built "
                    + "only from blocks other mods name.", path);
            return out;
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] cells = line.split("\t");
                Map<String, String> modernProps = new LinkedHashMap<String, String>();
                String modernName = parse(cells[0], modernProps);
                // The table is 1.13.2's; give it today's names, as templates get.
                modernName = upgrade(modernName, modernProps, 0);
                String legacyName = null;
                List<Map<String, String>> legacy = new ArrayList<Map<String, String>>();
                for (int i = 1; i < cells.length; i++) {
                    Map<String, String> props = new LinkedHashMap<String, String>();
                    String name = parse(cells[i], props);
                    if (legacyName == null) {
                        legacyName = name;
                    }
                    if (name.equals(legacyName)) {
                        legacy.add(props);
                    }
                }
                if (legacyName == null || modernName.startsWith("%")) {
                    // Mojang's placeholder for states whose 1.13 block came from a tile entity (skulls).
                    continue;
                }
                List<Entry> list = out.get(modernName);
                if (list == null) {
                    list = new ArrayList<Entry>(1);
                    out.put(modernName, list);
                }
                list.add(new Entry(modernProps, legacyName, legacy));
            }
            reader.close();
        } catch (Exception e) {
            BackportCore.LOGGER.error("Could not read the Flattening table {}.", path, e);
        }
        return out;
    }

    /** Parses {@code name[key=value,...]}, filling {@code props}; returns the name. */
    static String parse(String text, Map<String, String> props) {
        int open = text.indexOf('[');
        if (open < 0) {
            return text;
        }
        String inner = text.substring(open + 1, text.length() - 1);
        if (!inner.isEmpty()) {
            for (String pair : inner.split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    props.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                }
            }
        }
        return text.substring(0, open);
    }
}

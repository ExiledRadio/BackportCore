package com.exiledradio.backportcore.supply;

import com.exiledradio.backportcore.BackportCore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The supplied-block table, {@code assets/backportcore/supplied/blocks.txt}: one row per modern block Core can supply,
 * written by {@code tools/supplied-blocks/generate.py} from today's jar. Numbers are vanilla's.
 *
 * @since 1.5.0
 */
public final class SuppliedTable {

    /** One block of the table. */
    public static final class Row {
        /** Today's name, without {@code minecraft:}; also Core's registry path. */
        public final String name;
        /** cube, grate, pillar, chain, stairs or slab. */
        public final String shape;
        public final String material;
        public final float hardness;
        public final float resistance;
        /** 1.12.2's closest sound type. */
        public final String sound;
        /** Today's sound type, for when Core carries today's sounds. */
        public final String modernSound;
        public final int light;
        /** 1.12.2's {@code MapColor.COLORS} index. */
        public final int mapColor;
        /** pickaxe, axe, shovel, hoe, or null. */
        public final String tool;
        public final int level;
        /** Drops nothing without the right tool. */
        public final boolean needsTool;
        /** "self", "none", or another row's name (deepslate drops cobbled deepslate). */
        public final String drop;
        /** The backport that owns the block, or null. */
        public final String owner;
        /** The block's name in its owner ({@code iron_chain} is Nether Update's {@code chain}). */
        public final String ownerName;
        /** solid, cutout or translucent. */
        public final String layer;
        /** The owner's metadata layout where it is not vanilla's: {@code axis_ordinal} (Tricky Trials' chains). */
        public final String layout;
        /** Today's block color: {@code grass}, {@code mangrove}, or null for none. */
        public final String tint;
        /** Outline in sixteenths (x0 y0 z0 x1 y1 z1) for plants, carpets and lanterns; null for a full block. */
        public final float[] box;

        Row(String[] f) {
            this.name = f[0];
            this.shape = f[1];
            this.material = f[2];
            this.hardness = Float.parseFloat(f[3]);
            this.resistance = Float.parseFloat(f[4]);
            this.sound = f[5];
            this.modernSound = f[6];
            this.light = Integer.parseInt(f[7]);
            this.mapColor = Integer.parseInt(f[8]);
            this.tool = "-".equals(f[9]) ? null : f[9];
            this.level = Integer.parseInt(f[10]);
            this.needsTool = "yes".equals(f[11]);
            this.drop = f[12];
            this.owner = "-".equals(f[13]) ? null : f[13];
            this.ownerName = f[14];
            this.layer = f[15];
            this.layout = f.length > 16 ? f[16] : "vanilla";
            this.tint = f.length > 17 && !"-".equals(f[17]) ? f[17] : null;
            float[] b = null;
            if (f.length > 18 && !"-".equals(f[18])) {
                String[] n = f[18].split(",");
                b = new float[6];
                for (int i = 0; i < 6; i++) {
                    b[i] = Float.parseFloat(n[i]);
                }
            }
            this.box = b;
        }

        /** @return the double slab's path for a slab row ({@code polished_tuff_slab} to {@code polished_tuff_double_slab}) */
        public String doubleSlab() {
            return this.name.substring(0, this.name.length() - "_slab".length()) + "_double_slab";
        }

        /** @return the owner's double slab path */
        public String ownerDoubleSlab() {
            return this.ownerName.substring(0, this.ownerName.length() - "_slab".length()) + "_double_slab";
        }
    }

    private static Map<String, Row> rows;

    private SuppliedTable() {
    }

    /** @return every row, by today's name (no namespace), in the table's order */
    public static synchronized Map<String, Row> rows() {
        if (rows == null) {
            Map<String, Row> out = new LinkedHashMap<String, Row>();
            try (InputStream in = SuppliedTable.class.getResourceAsStream("/assets/backportcore/supplied/blocks.txt")) {
                if (in != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        String[] f = line.split("\t");
                        if (f.length >= 16) {
                            Row row = new Row(f);
                            out.put(row.name, row);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                BackportCore.LOGGER.error("Could not read the supplied-block table.", e);
            }
            rows = Collections.unmodifiableMap(out);
        }
        return rows;
    }
}

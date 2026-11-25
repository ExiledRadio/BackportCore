package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Every block name the structure files Core can read would place: each {@code .nbt} template's palettes (and its
 * jigsaws' final states), and each processor's output state in {@code data/<ns>/worldgen/}. Names are brought up to
 * date from the file's {@code DataVersion} ({@link Flattening#upgrade}) first.
 *
 * <p>Read once at startup, before the block registry event, so Core knows which modern blocks it has to supply
 * ({@code supply/SuppliedBlocks}). The result is cached in {@code config/backportcore/cache/modern_block_scan.txt}
 * against the size and date of every jar, folder and drop-in pack, so an unchanged game reads no structure files.
 *
 * <p>{@link #report} writes {@code config/backportcore/modern_blocks_report.txt} once every mod's blocks are in: for
 * each name, how many blocks the files place and what it becomes in this game (a backport's block, 1.12.2's, a block
 * Core supplies, a stand-in, or nothing).
 *
 * @since 1.5.0
 */
public final class ModernBlockScan {

    /** What the files hold of one block name. */
    public static final class Use {
        /** Positions the templates fill with it (each palette counted), plus one per processor output. */
        public int blocks;
        /** Files naming it. */
        public int files;
        /** The first file seen naming it. */
        public String example = "";
    }

    private static final int CACHE_VERSION = 1;

    private static Map<String, Use> uses = Collections.emptyMap();
    private static int filesRead;

    private ModernBlockScan() {
    }

    /** @return every name the files use (today's names), most used first. Empty before {@link #scan}. */
    public static synchronized Map<String, Use> uses() {
        return uses;
    }

    /** Reads every structure file, or the cache if nothing changed. */
    public static synchronized void scan() {
        long start = System.currentTimeMillis();
        StringBuilder stamp = new StringBuilder();
        List<DataPacks.Found> found = DataPacks.everyFile(stamp);
        String key = CACHE_VERSION + "|" + stamp.toString().hashCode() + "|" + stamp.length();
        File cache = new File(Loader.instance().getConfigDir(), "backportcore/cache/modern_block_scan.txt");
        Map<String, Use> cached = readCache(cache, key);
        if (cached != null) {
            uses = cached;
            BackportCore.LOGGER.info("Modern block scan: {} names, from the cache.", uses.size());
            return;
        }
        Map<String, Use> out = new HashMap<String, Use>();
        filesRead = 0;
        for (DataPacks.Found f : found) {
            try (InputStream in = f.open()) {
                if (in == null) {
                    continue;
                }
                Map<String, Integer> names = new HashMap<String, Integer>();
                if (f.path.endsWith(".nbt")) {
                    template(CompressedStreamTools.readCompressed(in), names);
                } else {
                    json(new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)), names);
                }
                filesRead++;
                for (Map.Entry<String, Integer> e : names.entrySet()) {
                    Use use = out.get(e.getKey());
                    if (use == null) {
                        use = new Use();
                        use.example = f.root + "!" + f.path;
                        out.put(e.getKey(), use);
                    }
                    use.blocks += e.getValue();
                    use.files++;
                }
            } catch (IOException | RuntimeException e) {
                BackportCore.LOGGER.debug("Modern block scan could not read {} in {}: {}", f.path, f.root, e.toString());
            }
        }
        uses = sorted(out);
        writeCache(cache, key);
        BackportCore.LOGGER.info("Modern block scan: {} names in {} files, {} ms.", uses.size(), filesRead,
                System.currentTimeMillis() - start);
    }

    // ---- reading

    private static void template(NBTTagCompound tag, Map<String, Integer> names) {
        int dataVersion = tag.getInteger("DataVersion");
        List<String[]> palettes = new ArrayList<String[]>();
        if (tag.hasKey("palettes", Constants.NBT.TAG_LIST)) {
            NBTTagList list = tag.getTagList("palettes", Constants.NBT.TAG_LIST);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTBase p = list.get(i);
                if (p instanceof NBTTagList) {
                    palettes.add(palette((NBTTagList) p, dataVersion));
                }
            }
        } else {
            palettes.add(palette(tag.getTagList("palette", Constants.NBT.TAG_COMPOUND), dataVersion));
        }
        NBTTagList blocks = tag.getTagList("blocks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < blocks.tagCount(); i++) {
            NBTTagCompound b = blocks.getCompoundTagAt(i);
            int state = b.getInteger("state");
            for (String[] palette : palettes) {
                if (state >= 0 && state < palette.length) {
                    add(names, palette[state], 1);
                }
            }
            if (b.hasKey("nbt", Constants.NBT.TAG_COMPOUND)) {
                String finalState = b.getCompoundTag("nbt").getString("final_state");
                if (!finalState.isEmpty()) {
                    Map<String, String> props = new LinkedHashMap<String, String>();
                    String name = Flattening.parse(finalState.trim(), props);
                    add(names, Flattening.upgrade(ModernPalette.full(name), props, dataVersion), 1);
                }
            }
        }
    }

    private static String[] palette(NBTTagList list, int dataVersion) {
        String[] out = new String[list.tagCount()];
        for (int i = 0; i < out.length; i++) {
            NBTTagCompound s = list.getCompoundTagAt(i);
            Map<String, String> props = new LinkedHashMap<String, String>();
            // Some tools write lower-case keys (ModernTemplate reads both).
            NBTTagCompound p = s.hasKey("Properties") ? s.getCompoundTag("Properties") : s.getCompoundTag("properties");
            for (String k : p.getKeySet()) {
                props.put(k, p.getString(k));
            }
            String name = s.hasKey("Name") ? s.getString("Name") : s.getString("id");
            out[i] = Flattening.upgrade(ModernPalette.full(name), props, dataVersion);
        }
        return out;
    }

    /** Every {@code output_state} in a processor list, or a pool's inline processors. */
    private static void json(JsonElement e, Map<String, Integer> names) {
        if (e.isJsonArray()) {
            for (JsonElement c : e.getAsJsonArray()) {
                json(c, names);
            }
        } else if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            for (Map.Entry<String, JsonElement> c : o.entrySet()) {
                if ("output_state".equals(c.getKey()) && c.getValue().isJsonObject() && c.getValue().getAsJsonObject().has("Name")) {
                    String name = ModernPalette.full(c.getValue().getAsJsonObject().get("Name").getAsString());
                    add(names, Flattening.upgrade(name, new HashMap<String, String>(), Integer.MAX_VALUE), 1);
                } else {
                    json(c.getValue(), names);
                }
            }
        }
    }

    private static void add(Map<String, Integer> names, String name, int n) {
        if (ignored(name)) {
            return;
        }
        Integer had = names.get(name);
        names.put(name, had == null ? n : had + n);
    }

    /** Air, and the blocks a template never places. */
    static boolean ignored(String name) {
        switch (name) {
            case "minecraft:air":
            case "minecraft:cave_air":
            case "minecraft:void_air":
            case "minecraft:structure_void":
            case "minecraft:structure_block":
            case "minecraft:jigsaw":
            case "minecraft:":
            case "":
                return true;
            default:
                return false;
        }
    }

    private static Map<String, Use> sorted(Map<String, Use> in) {
        List<Map.Entry<String, Use>> list = new ArrayList<Map.Entry<String, Use>>(in.entrySet());
        list.sort((a, b) -> a.getValue().blocks != b.getValue().blocks ? Integer.compare(b.getValue().blocks, a.getValue().blocks)
                : a.getKey().compareTo(b.getKey()));
        Map<String, Use> out = new LinkedHashMap<String, Use>();
        for (Map.Entry<String, Use> e : list) {
            out.put(e.getKey(), e.getValue());
        }
        return Collections.unmodifiableMap(out);
    }

    // ---- cache

    private static Map<String, Use> readCache(File cache, String key) {
        if (!cache.isFile()) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(cache.toPath(), StandardCharsets.UTF_8);
            if (lines.isEmpty() || !key.equals(lines.get(0))) {
                return null;
            }
            Map<String, Use> out = new LinkedHashMap<String, Use>();
            for (String line : lines.subList(1, lines.size())) {
                String[] f = line.split("\t", 4);
                if (f.length == 4) {
                    Use use = new Use();
                    use.blocks = Integer.parseInt(f[1]);
                    use.files = Integer.parseInt(f[2]);
                    use.example = f[3];
                    out.put(f[0], use);
                }
            }
            return Collections.unmodifiableMap(out);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void writeCache(File cache, String key) {
        try {
            cache.getParentFile().mkdirs();
            try (PrintWriter w = new PrintWriter(cache, "UTF-8")) {
                w.println(key);
                for (Map.Entry<String, Use> e : uses.entrySet()) {
                    w.println(e.getKey() + "\t" + e.getValue().blocks + "\t" + e.getValue().files + "\t" + e.getValue().example);
                }
            }
        } catch (IOException e) {
            BackportCore.LOGGER.debug("Could not write {}: {}", cache, e.toString());
        }
    }

    // ---- report

    /**
     * Writes {@code config/backportcore/modern_blocks_report.txt}: every name, what this game makes of it. Call once
     * every mod's blocks are registered.
     *
     * @return the outcome per name ({@code ModernPalette.Resolved#via}, or {@code missing})
     */
    public static synchronized Map<String, String> report() {
        Map<String, String> outcome = new LinkedHashMap<String, String>();
        Map<String, int[]> totals = new TreeMap<String, int[]>();
        for (Map.Entry<String, Use> e : uses.entrySet()) {
            ModernPalette.Resolved r = ModernPalette.resolveUpToDate(e.getKey(), Collections.<String, String>emptyMap());
            String via = r.via == null ? "missing" : r.via;
            outcome.put(e.getKey(), via);
            String group = via.startsWith("stand-in") ? "stand-in" : via;
            int[] t = totals.computeIfAbsent(group, k -> new int[2]);
            t[0]++;
            t[1] += e.getValue().blocks;
        }
        File out = new File(Loader.instance().getConfigDir(), "backportcore/modern_blocks_report.txt");
        try {
            out.getParentFile().mkdirs();
            try (PrintWriter w = new PrintWriter(out, "UTF-8")) {
                w.println("# Block names in the structure files Core reads (" + filesRead + " files read this start, 0 = from the cache).");
                w.println("# Columns: name, blocks placed, files, what this game uses for it, first file naming it.");
                for (Map.Entry<String, int[]> t : totals.entrySet()) {
                    w.println("# " + t.getKey() + ": " + t.getValue()[0] + " names, " + t.getValue()[1] + " blocks");
                }
                for (String want : new String[] {"missing", "stand-in"}) {
                    w.println();
                    w.println("## " + want);
                    for (Map.Entry<String, Use> e : uses.entrySet()) {
                        String via = outcome.get(e.getKey());
                        if (via.startsWith(want)) {
                            w.println(e.getKey() + "\t" + e.getValue().blocks + "\t" + e.getValue().files + "\t" + via + "\t" + e.getValue().example);
                        }
                    }
                }
                w.println();
                w.println("## provided");
                for (Map.Entry<String, Use> e : uses.entrySet()) {
                    String via = outcome.get(e.getKey());
                    if (!"missing".equals(via) && !via.startsWith("stand-in")) {
                        w.println(e.getKey() + "\t" + e.getValue().blocks + "\t" + e.getValue().files + "\t" + via);
                    }
                }
            }
        } catch (IOException e) {
            BackportCore.LOGGER.warn("Could not write {}: {}", out, e.toString());
        }
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<String, int[]> t : totals.entrySet()) {
            summary.append(summary.length() == 0 ? "" : ", ").append(t.getKey()).append(' ').append(t.getValue()[0]);
        }
        BackportCore.LOGGER.info("Modern blocks in structure files: {} ({}).", summary, out);
        return outcome;
    }
}

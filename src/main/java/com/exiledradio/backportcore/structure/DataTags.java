package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.locate.BiomeLocate;
import com.exiledradio.backportcore.locate.StructureLocate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Today's tags ({@code data/<ns>/tags/<registry>/<path>.json}) as {@link DataStructures} reads them: every copy of a
 * tag on the class path and in the structure packs, merged in that order ({@code "replace": true} starts over), with
 * {@code #tag} entries and {@code {"id": ..., "required": false}} entries. Core ships 26.3's biome tags
 * ({@code #minecraft:is_ocean}, {@code #minecraft:has_structure/...}); a biome tag also has the members backports gave
 * {@link BiomeLocate#tag}.
 *
 * @since 1.4.5
 */
public final class DataTags {

    private static final Map<String, Set<String>> CACHE = new HashMap<String, Set<String>>();

    private DataTags() {
    }

    /** Drops every tag read, so the next ask reads the files again. */
    public static synchronized void clear() {
        CACHE.clear();
    }

    /**
     * @param registry today's tag folder, {@code worldgen/biome} or {@code worldgen/structure}
     * @param tag      {@code #minecraft:is_ocean} or {@code minecraft:is_ocean}
     * @return the tag's ids, today's form, nested tags resolved; empty for an unknown tag
     */
    public static synchronized Set<String> members(String registry, String tag) {
        return Collections.unmodifiableSet(resolve(registry, StructureLocate.normalize(strip(tag)), new HashSet<String>()));
    }

    /** @return true if some file (or, for biomes, a backport) has this tag */
    public static synchronized boolean exists(String registry, String tag) {
        String id = StructureLocate.normalize(strip(tag));
        if ("worldgen/biome".equals(registry) && !BiomeLocate.members(id).isEmpty()) {
            return true;
        }
        return !DataPacks.jsonAll(path(registry, id)).isEmpty();
    }

    /**
     * A structure's {@code biomes}: one id, a {@code #tag}, or a list of ids, each in today's form.
     */
    public static Set<String> biomes(JsonElement json) {
        Set<String> out = new LinkedHashSet<String>();
        if (json == null) {
            return out;
        }
        if (json.isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray()) {
                out.addAll(biomes(e));
            }
            return out;
        }
        String s = json.getAsString();
        if (s.startsWith("#")) {
            Set<String> members = members("worldgen/biome", s);
            if (members.isEmpty() && !exists("worldgen/biome", s)) {
                BackportCore.LOGGER.warn("Biome tag {} is in no file; what names it has no biomes.", s);
            }
            out.addAll(members);
        } else {
            out.add(StructureLocate.normalize(s));
        }
        return out;
    }

    private static String strip(String tag) {
        return tag.startsWith("#") ? tag.substring(1) : tag;
    }

    private static String path(String registry, String id) {
        int colon = id.indexOf(':');
        return "data/" + id.substring(0, colon) + "/tags/" + registry + "/" + id.substring(colon + 1) + ".json";
    }

    private static Set<String> resolve(String registry, String id, Set<String> visiting) {
        String key = registry + " " + id;
        Set<String> known = CACHE.get(key);
        if (known != null) {
            return known;
        }
        if (!visiting.add(key)) {
            BackportCore.LOGGER.error("Tag #{} ({}) includes itself.", id, registry);
            return Collections.emptySet();
        }
        List<String> entries = new ArrayList<String>();
        for (JsonElement file : DataPacks.jsonAll(path(registry, id))) {
            if (!file.isJsonObject()) {
                continue;
            }
            JsonObject o = file.getAsJsonObject();
            if (o.has("replace") && o.get("replace").getAsBoolean()) {
                entries.clear();
            }
            if (o.has("values")) {
                for (JsonElement v : o.getAsJsonArray("values")) {
                    entries.add(v.isJsonObject() ? v.getAsJsonObject().get("id").getAsString() : v.getAsString());
                }
            }
        }
        Set<String> out = new LinkedHashSet<String>();
        for (String entry : entries) {
            if (entry.startsWith("#")) {
                out.addAll(resolve(registry, StructureLocate.normalize(entry.substring(1)), visiting));
            } else {
                out.add(StructureLocate.normalize(entry));
            }
        }
        if ("worldgen/biome".equals(registry)) {
            out.addAll(BiomeLocate.members(id));
        }
        visiting.remove(key);
        CACHE.put(key, out);
        return out;
    }
}

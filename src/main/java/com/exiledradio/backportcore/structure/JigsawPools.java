package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Template pools and processor lists, read from Mojang's own data files: today's
 * {@code StructureTemplatePool} registry.
 *
 * <h2>Files</h2>
 * <ul>
 * <li>{@code assets/<namespace>/worldgen/template_pool/<path>.json}: a pool ({@code name},
 * {@code fallback}, weighted {@code elements}).</li>
 * <li>{@code assets/<namespace>/worldgen/processor_list/<path>.json}: a processor list an element
 * names by id.</li>
 * <li>{@code assets/<namespace>/structures/<path>.nbt} (or {@code structure/}): the templates, read
 * by {@link ModernTemplate}.</li>
 * </ul>
 * Mojang's files name everything {@code minecraft:...} (pools, the templates in them, the pools their
 * jigsaw blocks point at). A mod ships them unchanged under its own namespace and lists it with
 * {@link #source}: a name that is not found under its own namespace is looked for in each source in
 * turn, so {@code minecraft:bastion/starts} is read from {@code assets/yourmod/worldgen/template_pool/bastion/starts.json}.
 * {@code minecraft:empty} is built in.
 */
public final class JigsawPools {

    /** Today's {@code Pools.EMPTY}. */
    public static final ResourceLocation EMPTY = new ResourceLocation("minecraft", "empty");

    private static final List<String> SOURCES = new ArrayList<String>();
    private static final Map<ResourceLocation, Pool> POOLS = new HashMap<ResourceLocation, Pool>();
    private static final Set<ResourceLocation> MISSING = new HashSet<ResourceLocation>();
    private static final Map<ResourceLocation, ModernProcessors.ProcessorList> LISTS = new HashMap<ResourceLocation, ModernProcessors.ProcessorList>();
    private static final Map<ResourceLocation, ResourceLocation> TEMPLATES = new HashMap<ResourceLocation, ResourceLocation>();
    private static final Map<String, FeaturePlacer> FEATURES = new HashMap<String, FeaturePlacer>();
    private static final Set<String> REPORTED = Collections.synchronizedSet(new HashSet<String>());

    /** Places a {@code feature_pool_element}'s feature: 1.12.2 has none of today's configured features. */
    @FunctionalInterface
    public interface FeaturePlacer {
        /**
         * @param feature the element's {@code feature}: a configured feature's id, or the feature
         *                written out ({@code type} and {@code config})
         */
        void place(World world, BlockPos pos, Random random, JsonElement feature);
    }

    private JigsawPools() {
    }

    /** Lists a mod whose assets hold Mojang's files under their {@code minecraft:} names. Pre-init or init. */
    public static synchronized void source(String modId) {
        if (!SOURCES.contains(modId)) {
            SOURCES.add(modId);
            POOLS.clear();
            MISSING.clear();
            LISTS.clear();
            TEMPLATES.clear();
        }
    }

    /**
     * Registers the placer for a feature element: by the configured feature's id
     * ({@code minecraft:oak}) where the file names one, or by the feature's {@code type}
     * ({@code minecraft:tree}) where the file writes it out. An element nobody places is left empty.
     */
    public static synchronized void feature(String idOrType, FeaturePlacer placer) {
        FEATURES.put(ModernPalette.full(idOrType), placer);
    }

    @Nullable
    static synchronized FeaturePlacer featurePlacer(JsonElement feature) {
        String key = feature.isJsonPrimitive() ? feature.getAsString()
                : feature.isJsonObject() && feature.getAsJsonObject().has("type") ? feature.getAsJsonObject().get("type").getAsString() : "";
        FeaturePlacer placer = FEATURES.get(ModernPalette.full(key));
        if (placer == null && REPORTED.add(key)) {
            BackportCore.LOGGER.warn("Nothing places jigsaw feature {}; those elements are left empty.", key);
        }
        return placer;
    }

    // ---- pools

    /** A template pool: today's {@code StructureTemplatePool}. */
    public static final class Pool {
        final ResourceLocation name;
        final ResourceLocation fallback;
        /** Every element repeated by its weight, in file order, as today's {@code templates}. */
        final List<JigsawElement> templates;
        private int maxSize = Integer.MIN_VALUE;

        Pool(ResourceLocation name, ResourceLocation fallback, List<JigsawElement> templates) {
            this.name = name;
            this.fallback = fallback;
            this.templates = templates;
        }

        public ResourceLocation name() {
            return this.name;
        }

        public ResourceLocation fallback() {
            return this.fallback;
        }

        /** @return how many entries, each element counted by its weight */
        public int size() {
            return this.templates.size();
        }

        /** @return every element, each once per weight, in the file's order */
        public List<JigsawElement> elements() {
            return Collections.unmodifiableList(this.templates);
        }

        /** Today's {@code getRandomTemplate}. */
        public JigsawElement random(Random random) {
            return this.templates.get(random.nextInt(this.templates.size()));
        }

        /** Today's {@code getShuffledTemplates}: fastutil's {@code ObjectArrays.shuffle}, draw for draw. */
        public List<JigsawElement> shuffled(Random random) {
            JigsawElement[] a = this.templates.toArray(new JigsawElement[0]);
            for (int i = a.length; i-- != 0; ) {
                int p = random.nextInt(i + 1);
                JigsawElement t = a[i];
                a[i] = a[p];
                a[p] = t;
            }
            List<JigsawElement> out = new ArrayList<JigsawElement>(a.length);
            Collections.addAll(out, a);
            return out;
        }

        /** Today's {@code getMaxSize}: the tallest element. */
        public int maxSize() {
            if (this.maxSize == Integer.MIN_VALUE) {
                int max = 0;
                for (JigsawElement e : this.templates) {
                    max = Math.max(max, e.ySpan());
                }
                this.maxSize = max;
            }
            return this.maxSize;
        }
    }

    /** @return the pool, or null if no file has it */
    @Nullable
    public static Pool pool(ResourceLocation id) {
        synchronized (JigsawPools.class) {
            Pool known = POOLS.get(id);
            if (known != null || MISSING.contains(id)) {
                return known;
            }
        }
        Pool pool;
        if (EMPTY.equals(id)) {
            pool = new Pool(EMPTY, EMPTY, Collections.<JigsawElement>emptyList());
        } else {
            JsonObject json = read("worldgen/template_pool", id, ".json");
            pool = json == null ? null : parsePool(id, json);
        }
        synchronized (JigsawPools.class) {
            if (pool == null) {
                MISSING.add(id);
            } else {
                POOLS.put(id, pool);
            }
        }
        return pool;
    }

    private static Pool parsePool(ResourceLocation id, JsonObject json) {
        ResourceLocation fallback = json.has("fallback") ? new ResourceLocation(json.get("fallback").getAsString()) : EMPTY;
        List<JigsawElement> templates = new ArrayList<JigsawElement>();
        JsonArray elements = json.has("elements") ? json.getAsJsonArray("elements") : new JsonArray();
        for (JsonElement e : elements) {
            JsonObject entry = e.getAsJsonObject();
            int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
            JigsawElement element;
            try {
                element = element(entry.getAsJsonObject("element"));
            } catch (RuntimeException ex) {
                BackportCore.LOGGER.error("Pool {} has an element that could not be read: {}", id, entry, ex);
                continue;
            }
            for (int i = 0; i < weight; i++) {
                templates.add(element);
            }
        }
        return new Pool(id, fallback, templates);
    }

    /** Reads a pool element as a pool file (or a saved piece) writes it. */
    public static JigsawElement element(JsonObject json) {
        String type = ModernPalette.full(json.has("element_type") ? json.get("element_type").getAsString() : "minecraft:single_pool_element");
        String projection = json.has("projection") ? json.get("projection").getAsString() : "rigid";
        switch (type) {
            case "minecraft:single_pool_element":
            case "minecraft:legacy_single_pool_element":
                return new JigsawElement.Single(json, projection, new ResourceLocation(json.get("location").getAsString()),
                        ModernProcessors.parse(json.get("processors")), type.equals("minecraft:legacy_single_pool_element"));
            case "minecraft:list_pool_element": {
                List<JigsawElement> list = new ArrayList<JigsawElement>();
                for (JsonElement e : json.getAsJsonArray("elements")) {
                    JsonObject o = e.getAsJsonObject();
                    // Today's ListPoolElement gives its projection to every element.
                    o.addProperty("projection", projection);
                    list.add(element(o));
                }
                if (list.isEmpty()) {
                    throw new IllegalArgumentException("Elements are empty");
                }
                return new JigsawElement.ListElement(json, projection, list);
            }
            case "minecraft:feature_pool_element":
                return new JigsawElement.Feature(json, projection, json.get("feature"));
            case "minecraft:empty_pool_element":
                return new JigsawElement.Empty(json);
            default:
                throw new IllegalArgumentException("Unknown pool element type " + type);
        }
    }

    /** Reads an element saved with a piece: {@link JigsawElement#json()} as text. */
    static JigsawElement element(String text) {
        return element(new JsonParser().parse(text).getAsJsonObject());
    }

    // ---- processor lists

    /** @return the processor list of this id; empty (and logged) if no file has it */
    public static ModernProcessors.ProcessorList processorList(ResourceLocation id) {
        synchronized (JigsawPools.class) {
            ModernProcessors.ProcessorList known = LISTS.get(id);
            if (known != null) {
                return known;
            }
        }
        ModernProcessors.ProcessorList list;
        if ("minecraft".equals(id.getNamespace()) && "empty".equals(id.getPath())) {
            list = ModernProcessors.EMPTY;
        } else {
            JsonObject json = read("worldgen/processor_list", id, ".json");
            if (json == null) {
                BackportCore.LOGGER.error("Processor list {} is missing; its elements place without processors.", id);
                list = ModernProcessors.EMPTY;
            } else {
                list = ModernProcessors.parse(json);
            }
        }
        synchronized (JigsawPools.class) {
            LISTS.put(id, list);
        }
        return list;
    }

    // ---- templates

    /**
     * @return the id {@link ModernTemplate#load} finds a pool's template under: its own, or the first
     * source mod that has a file of that path
     */
    public static ResourceLocation templateId(ResourceLocation location) {
        synchronized (JigsawPools.class) {
            ResourceLocation known = TEMPLATES.get(location);
            if (known != null) {
                return known;
            }
        }
        ResourceLocation found = location;
        if (!exists(location.getNamespace(), "structures", location.getPath(), ".nbt")
                && !exists(location.getNamespace(), "structure", location.getPath(), ".nbt")) {
            List<String> sources;
            synchronized (JigsawPools.class) {
                sources = new ArrayList<String>(SOURCES);
            }
            for (String source : sources) {
                if (exists(source, "structures", location.getPath(), ".nbt") || exists(source, "structure", location.getPath(), ".nbt")) {
                    found = new ResourceLocation(source, location.getPath());
                    break;
                }
            }
        }
        synchronized (JigsawPools.class) {
            TEMPLATES.put(location, found);
        }
        return found;
    }

    /** Drops every pool and list read, so a test or a resource reload reads files again. */
    public static synchronized void clearCache() {
        POOLS.clear();
        MISSING.clear();
        LISTS.clear();
        TEMPLATES.clear();
    }

    // ---- files

    private static boolean exists(String namespace, String folder, String path, String ext) {
        InputStream in = JigsawPools.class.getResourceAsStream("/assets/" + namespace + "/" + folder + "/" + path + ext);
        if (in == null) {
            return false;
        }
        try {
            in.close();
        } catch (Exception ignored) {
            // nothing to do
        }
        return true;
    }

    @Nullable
    private static JsonObject read(String folder, ResourceLocation id, String ext) {
        List<String> namespaces = new ArrayList<String>();
        namespaces.add(id.getNamespace());
        synchronized (JigsawPools.class) {
            namespaces.addAll(SOURCES);
        }
        for (String namespace : namespaces) {
            String path = "/assets/" + namespace + "/" + folder + "/" + id.getPath() + ext;
            InputStream in = JigsawPools.class.getResourceAsStream(path);
            if (in == null) {
                continue;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return new JsonParser().parse(reader).getAsJsonObject();
            } catch (Exception e) {
                BackportCore.LOGGER.error("Could not read {}.", path, e);
                return null;
            }
        }
        return null;
    }
}

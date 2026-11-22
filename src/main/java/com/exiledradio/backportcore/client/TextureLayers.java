package com.exiledradio.backportcore.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.GameVersion;

/**
 * The layers of the modern texture pack, one per release line that changed vanilla's textures, and which of them
 * apply at a version. Nothing here touches the client, so the server self-tests check it too; {@link LayeredPack}
 * serves the files.
 *
 * <p>The 1.14 layer ({@code backportcore_packs/modern}, 1.14's Texture Update) always applies: it predates the
 * presentation module, and Village & Pillage, which brings it, registers no version. Every later layer ({@code
 * backportcore_packs/modern-<version>}, listed in {@code backportcore_packs/layers.txt}) applies when the newest
 * installed backport ({@code Presentation.newest()}) is that version or later. A file comes from the newest layer
 * that applies and has it. Both are written by {@code tools/textures/build.py}, except the 1.21.5 layer
 * ({@code tools/textures/eggs.py}): the spawn eggs' own textures, under their 26.3 names, for {@link SpawnEggModels}.
 */
public final class TextureLayers {

    /** One layer: its files ({@code assets/<domain>/...}) and the version from which it applies (null: always). */
    public static final class Layer {

        @Nullable
        public final GameVersion since;
        public final String folder;
        final Set<String> files;

        Layer(@Nullable GameVersion since, String folder, Set<String> files) {
            this.since = since;
            this.folder = folder;
            this.files = files;
        }

        public boolean has(String file) {
            return files.contains(file);
        }

        public Set<String> files() {
            return Collections.unmodifiableSet(files);
        }

        /** @return the class-path name of one of its files */
        public String resource(String file) {
            return "/" + BackportCore.MOD_ID + "_packs/" + folder + "/" + file;
        }

        @Override
        public String toString() {
            return since == null ? "1.14" : since.toString();
        }
    }

    private static List<Layer> all;

    private TextureLayers() {
    }

    /** @return every layer, oldest first: the 1.14 layer, then the later ones by version */
    public static synchronized List<Layer> all() {
        if (all == null) {
            List<Layer> out = new ArrayList<>();
            out.add(new Layer(null, "modern", index("modern")));
            for (String line : lines("/" + BackportCore.MOD_ID + "_packs/layers.txt")) {
                out.add(new Layer(GameVersion.parse(line), "modern-" + line, index("modern-" + line)));
            }
            out.sort((a, b) -> a.since == null ? -1 : b.since == null ? 1 : a.since.compareTo(b.since));
            all = Collections.unmodifiableList(out);
        }
        return all;
    }

    /** @return the layers that apply when {@code newest} is the newest installed version, oldest first */
    public static List<Layer> active(GameVersion newest) {
        List<Layer> out = new ArrayList<>();
        for (Layer l : all()) {
            if (l.since == null || newest.isAtLeast(l.since)) {
                out.add(l);
            }
        }
        return out;
    }

    /** @return the layer {@code file} comes from at {@code newest}, or null when no layer that applies has it */
    @Nullable
    public static Layer layerFor(String file, GameVersion newest) {
        return serving(file, active(newest));
    }

    /**
     * @return the layer among {@code active} (oldest first) that serves {@code file}. A {@code .mcmeta} comes only
     * from the layer that serves its image: a lower layer's animation must not apply to a newer still image.
     */
    @Nullable
    static Layer serving(String file, List<Layer> active) {
        if (file.endsWith(".png.mcmeta")) {
            Layer image = serving(file.substring(0, file.length() - ".mcmeta".length()), active);
            return image != null && image.has(file) ? image : null;
        }
        for (int i = active.size() - 1; i >= 0; i--) {
            if (active.get(i).has(file)) {
                return active.get(i);
            }
        }
        return null;
    }

    /** @return the resource domains any layer covers */
    public static Set<String> domains() {
        Set<String> out = new LinkedHashSet<>();
        for (Layer l : all()) {
            for (String f : l.files) {
                int slash = f.indexOf('/', 7);
                if (slash > 7) {
                    out.add(f.substring(7, slash));
                }
            }
        }
        return out;
    }

    private static Set<String> index(String folder) {
        Set<String> files = new HashSet<>();
        for (String line : lines("/" + BackportCore.MOD_ID + "_packs/" + folder + "/index.txt")) {
            if (line.startsWith("assets/")) {
                files.add(line);
            }
        }
        return files;
    }

    private static List<String> lines(String resource) {
        List<String> out = new ArrayList<>();
        try (InputStream in = TextureLayers.class.getResourceAsStream(resource)) {
            if (in == null) {
                BackportCore.LOGGER.error("Texture layers: {} is missing; those textures stay 1.12.2's.", resource);
                return out;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    out.add(line);
                }
            }
        } catch (IOException e) {
            BackportCore.LOGGER.error("Texture layers: could not read {}", resource, e);
        }
        return out;
    }
}

package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Today's datapack files ({@code data/<namespace>/...}) as 1.12.2 can have them: in any mod jar on the class path, and
 * in the drop-in folder {@code config/backportcore/structures/}, where each folder, {@code .zip} or {@code .jar} is
 * laid out like a datapack ({@code data/} at its root, or inside one top folder). 1.12.2 itself reads nothing under
 * {@code data/}, so these files are only what Core reads: {@link JigsawPools}' pools, processor lists and templates,
 * and {@link DataStructures}' structures, structure sets and tags.
 *
 * <p>A drop-in file takes the place of a jar's file of the same path.
 *
 * @since 1.4.5
 */
public final class DataPacks {

    /** The drop-in folder, under the config folder. */
    public static final String DROP_IN = "backportcore/structures";

    private static final List<Root> DROP_INS = new ArrayList<Root>();
    /** Each mod jar's {@code data/} files, read once per {@link #scanDropIns}. */
    private static final Map<File, List<String>> JAR_NAMES = new java.util.HashMap<File, List<String>>();
    @Nullable
    private static List<File> classPathRoots;
    private static boolean dropInsScanned;

    private DataPacks() {
    }

    /** A pack: a folder or an archive, its {@code data/} folder under {@code base}. */
    private abstract static class Root {
        final String name;
        final String base;

        Root(String name, String base) {
            this.name = name;
            this.base = base;
        }

        @Nullable
        abstract InputStream open(String path) throws IOException;

        /** Every file path (from {@code data/}) under the prefix, sorted. */
        abstract List<String> list(String prefix) throws IOException;

        @Override
        public String toString() {
            return this.name;
        }
    }

    private static final class FolderRoot extends Root {
        final File dir;

        FolderRoot(File dir, String base) {
            super(dir.getName(), base);
            this.dir = dir;
        }

        @Nullable
        @Override
        InputStream open(String path) throws IOException {
            File f = new File(this.dir, this.base + path);
            return f.isFile() ? new FileInputStream(f) : null;
        }

        @Override
        List<String> list(String prefix) {
            List<String> out = new ArrayList<String>();
            File start = new File(this.dir, this.base + prefix);
            walk(start, prefix.endsWith("/") ? prefix : prefix + "/", out);
            Collections.sort(out);
            return out;
        }

        private static void walk(File dir, String path, List<String> out) {
            File[] children = dir.listFiles();
            if (children == null) {
                return;
            }
            for (File c : children) {
                if (c.isDirectory()) {
                    walk(c, path + c.getName() + "/", out);
                } else {
                    out.add(path + c.getName());
                }
            }
        }
    }

    private static final class ArchiveRoot extends Root {
        final Map<String, byte[]> files = new LinkedHashMap<String, byte[]>();

        ArchiveRoot(File file) throws IOException {
            super(file.getName(), "");
            String base = null;
            try (ZipFile zip = new ZipFile(file)) {
                List<String> names = new ArrayList<String>();
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    if (!e.isDirectory()) {
                        names.add(e.getName().replace('\\', '/'));
                    }
                }
                for (String n : names) {
                    if (n.startsWith("data/")) {
                        base = "";
                        break;
                    }
                    int slash = n.indexOf('/');
                    if (slash > 0 && n.startsWith("data/", slash + 1)) {
                        // One top folder holding the pack, as an archive of the pack's folder has it.
                        base = n.substring(0, slash + 1);
                    }
                }
                if (base == null) {
                    base = "";
                }
                for (String n : names) {
                    if (n.startsWith(base + "data/")) {
                        try (InputStream in = zip.getInputStream(zip.getEntry(n))) {
                            this.files.put(n.substring(base.length()), readAll(in));
                        }
                    }
                }
            }
        }

        @Nullable
        @Override
        InputStream open(String path) {
            byte[] b = this.files.get(path);
            return b == null ? null : new ByteArrayInputStream(b);
        }

        @Override
        List<String> list(String prefix) {
            String p = prefix.endsWith("/") ? prefix : prefix + "/";
            List<String> out = new ArrayList<String>();
            for (String n : this.files.keySet()) {
                if (n.startsWith(p)) {
                    out.add(n);
                }
            }
            Collections.sort(out);
            return out;
        }
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    // ---- the drop-in folder

    /** @return the drop-in folder ({@code config/backportcore/structures}) */
    public static File dropInFolder() {
        return new File(Loader.instance().getConfigDir(), DROP_IN);
    }

    /** Reads the drop-in folder again (at startup, and for tests). Creates it, empty, if it is missing. */
    public static synchronized void scanDropIns() {
        DROP_INS.clear();
        synchronized (JAR_NAMES) {
            JAR_NAMES.clear();
        }
        dropInsScanned = true;
        File folder = dropInFolder();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            return;
        }
        File[] packs = folder.listFiles();
        if (packs == null) {
            return;
        }
        Arrays.sort(packs);
        for (File pack : packs) {
            String n = pack.getName().toLowerCase(Locale.ROOT);
            try {
                if (pack.isDirectory()) {
                    String base = "";
                    if (!new File(pack, "data").isDirectory()) {
                        File[] inner = pack.listFiles(f -> f.isDirectory() && new File(f, "data").isDirectory());
                        if (inner == null || inner.length != 1) {
                            BackportCore.LOGGER.warn("Structure pack {} has no data folder; it is left out.", pack);
                            continue;
                        }
                        base = inner[0].getName() + "/";
                    }
                    DROP_INS.add(new FolderRoot(pack, base));
                } else if (n.endsWith(".zip") || n.endsWith(".jar")) {
                    DROP_INS.add(new ArchiveRoot(pack));
                }
            } catch (IOException | RuntimeException e) {
                BackportCore.LOGGER.error("Could not read structure pack {}.", pack, e);
            }
        }
        if (!DROP_INS.isEmpty()) {
            BackportCore.LOGGER.info("Structure packs in {}: {}", folder, DROP_INS);
        }
    }

    private static synchronized List<Root> dropIns() {
        if (!dropInsScanned) {
            scanDropIns();
        }
        return new ArrayList<Root>(DROP_INS);
    }

    /** @return the names of the drop-in packs read */
    public static List<String> dropInNames() {
        List<String> out = new ArrayList<String>();
        for (Root r : dropIns()) {
            out.add(r.name);
        }
        return out;
    }

    // ---- reading one file

    /**
     * @param path from the pack's root, {@code data/<namespace>/...}
     * @return the file: a drop-in pack's, else the class path's; null if none has it
     */
    @Nullable
    public static InputStream open(String path) {
        for (Root r : dropIns()) {
            try {
                InputStream in = r.open(path);
                if (in != null) {
                    return in;
                }
            } catch (IOException e) {
                BackportCore.LOGGER.error("Could not read {} from structure pack {}.", path, r, e);
            }
        }
        return DataPacks.class.getResourceAsStream("/" + path);
    }

    /** @return true if {@link #open} finds the file */
    public static boolean exists(String path) {
        InputStream in = open(path);
        if (in == null) {
            return false;
        }
        try {
            in.close();
        } catch (IOException ignored) {
            // nothing to do
        }
        return true;
    }

    /** @return the file as JSON, or null (missing, or logged as unreadable) */
    @Nullable
    public static JsonElement json(String path) {
        InputStream in = open(path);
        return in == null ? null : parse(in, path);
    }

    @Nullable
    private static JsonElement parse(InputStream in, String what) {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader);
        } catch (Exception e) {
            BackportCore.LOGGER.error("Could not read {}.", what, e);
            return null;
        }
    }

    /**
     * Every copy of a file, class path first (in its order), then the drop-in packs: what a tag merges.
     */
    public static List<JsonElement> jsonAll(String path) {
        List<JsonElement> out = new ArrayList<JsonElement>();
        try {
            Enumeration<URL> urls = DataPacks.class.getClassLoader().getResources(path);
            Set<String> seen = new LinkedHashSet<String>();
            while (urls.hasMoreElements()) {
                URL u = urls.nextElement();
                if (seen.add(u.toString())) {
                    JsonElement e = parse(u.openStream(), u.toString());
                    if (e != null) {
                        out.add(e);
                    }
                }
            }
        } catch (IOException e) {
            BackportCore.LOGGER.error("Could not list {} on the class path.", path, e);
        }
        for (Root r : dropIns()) {
            try {
                InputStream in = r.open(path);
                if (in != null) {
                    JsonElement e = parse(in, r + ": " + path);
                    if (e != null) {
                        out.add(e);
                    }
                }
            } catch (IOException e) {
                BackportCore.LOGGER.error("Could not read {} from structure pack {}.", path, r, e);
            }
        }
        return out;
    }

    // ---- listing

    /**
     * Every file of a datapack folder in every namespace: {@code list("worldgen/structure", ".json")} finds
     * {@code data/<ns>/worldgen/structure/<path>.json} in every mod jar and class path folder, then in the drop-in packs
     * (which replace a jar's file of the same id).
     *
     * @return id ({@code <ns>:<path>}, without the extension) to the file's path from the pack root, in the order found
     */
    public static Map<ResourceLocation, String> list(String folder, String ext) {
        Map<ResourceLocation, String> out = new LinkedHashMap<ResourceLocation, String>();
        for (File root : classPathRoots()) {
            try {
                if (root.isDirectory()) {
                    collect(new FolderRoot(root, ""), folder, ext, out);
                } else if (root.isFile()) {
                    collectJar(root, folder, ext, out);
                }
            } catch (IOException | RuntimeException e) {
                BackportCore.LOGGER.debug("Could not look through {} for {}: {}", root, folder, e.toString());
            }
        }
        for (Root r : dropIns()) {
            try {
                collect(r, folder, ext, out);
            } catch (IOException | RuntimeException e) {
                BackportCore.LOGGER.error("Could not look through structure pack {}.", r, e);
            }
        }
        return out;
    }

    private static void collect(Root root, String folder, String ext, Map<ResourceLocation, String> out) throws IOException {
        if (root instanceof FolderRoot) {
            File data = new File(((FolderRoot) root).dir, root.base + "data");
            File[] namespaces = data.listFiles(File::isDirectory);
            if (namespaces == null) {
                return;
            }
            Arrays.sort(namespaces);
            for (File ns : namespaces) {
                String prefix = "data/" + ns.getName() + "/" + folder;
                for (String path : root.list(prefix)) {
                    add(path, folder, ext, out);
                }
            }
        } else {
            for (String path : root.list("data")) {
                add(path, folder, ext, out);
            }
        }
    }

    private static void collectJar(File jar, String folder, String ext, Map<ResourceLocation, String> out) throws IOException {
        List<String> names;
        synchronized (JAR_NAMES) {
            names = JAR_NAMES.get(jar);
        }
        if (names == null) {
            names = new ArrayList<String>();
            try (ZipFile zip = new ZipFile(jar)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    String n = e.getName();
                    if (!e.isDirectory() && n.startsWith("data/")) {
                        names.add(n);
                    }
                }
            }
            Collections.sort(names);
            synchronized (JAR_NAMES) {
                JAR_NAMES.put(jar, names);
            }
        }
        for (String n : names) {
            add(n, folder, ext, out);
        }
    }

    /** Adds {@code data/<ns>/<folder>/<rest><ext>} as {@code <ns>:<rest>}; any other path is skipped. */
    private static void add(String path, String folder, String ext, Map<ResourceLocation, String> out) {
        if (!path.startsWith("data/") || !path.endsWith(ext)) {
            return;
        }
        int slash = path.indexOf('/', 5);
        if (slash < 0) {
            return;
        }
        String ns = path.substring(5, slash);
        String prefix = "data/" + ns + "/" + folder + "/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length() + ext.length()) {
            return;
        }
        String rest = path.substring(prefix.length(), path.length() - ext.length());
        try {
            ResourceLocation id = new ResourceLocation(ns, rest);
            // A later root replaces an earlier one's file of the same path; keep the order of first sight.
            out.put(id, path);
        } catch (RuntimeException e) {
            BackportCore.LOGGER.debug("Skipping data file with an unusable name: {}", path);
        }
    }

    /**
     * Mod jars and folders, and the class path's folders (a development workspace's resources): where a mod's
     * {@code data/} can be. Other jars on the class path (libraries) are not looked through.
     */
    private static synchronized List<File> classPathRoots() {
        if (classPathRoots == null) {
            Set<File> roots = new LinkedHashSet<File>();
            for (ModContainer mod : Loader.instance().getModList()) {
                File source = mod.getSource();
                if (source != null && !source.getName().endsWith(".class")) {
                    roots.add(source.getAbsoluteFile());
                }
            }
            if (Launch.classLoader != null) {
                for (URL u : Launch.classLoader.getSources()) {
                    if (!"file".equals(u.getProtocol())) {
                        continue;
                    }
                    try {
                        File f = new File(u.toURI()).getAbsoluteFile();
                        if (f.isDirectory()) {
                            roots.add(f);
                        }
                    } catch (Exception ignored) {
                        // not a plain file URL
                    }
                }
            }
            classPathRoots = new ArrayList<File>(roots);
        }
        return classPathRoots;
    }
}

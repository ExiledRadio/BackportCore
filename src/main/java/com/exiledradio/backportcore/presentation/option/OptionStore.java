package com.exiledradio.backportcore.presentation.option;

import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The saved values of every {@link PresentationOption}: {@code config/backportcore/options.txt}, one
 * {@code id:value} line each, like vanilla's options.txt. Lines for options no installed mod registers are
 * kept, so taking a backport out and putting it back keeps its settings.
 */
public final class OptionStore {

    static final Logger LOG = LogManager.getLogger("Backport Core");

    private static final Map<String, String> VALUES = new TreeMap<String, String>();
    @Nullable
    private static File file;
    private static boolean loaded;

    private OptionStore() {
    }

    /** @return the file, under the instance's config folder */
    public static synchronized File file() {
        if (file == null) {
            file = new File(new File(Loader.instance().getConfigDir(), "backportcore"), "options.txt");
        }
        return file;
    }

    /** For the self-tests: use another file (and forget what was read). */
    public static synchronized void useFile(File other) {
        file = other;
        loaded = false;
        VALUES.clear();
    }

    /** @return the saved value of any id, or null (Core's own small records use this too) */
    @Nullable
    public static synchronized String get(String id) {
        load();
        return VALUES.get(id);
    }

    /** Saves a value under any id (Core's own small records use this too). */
    public static synchronized void put(String id, String value) {
        load();
        if (value.equals(VALUES.get(id))) {
            return;
        }
        VALUES.put(id, value);
        writeFile();
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File f = file();
        if (!f.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                // The id has its own colon ("modid:name"), so the value starts at the second one.
                int first = line.indexOf(':');
                int colon = first < 0 ? -1 : line.indexOf(':', first + 1);
                if (colon > 0 && !line.startsWith("#")) {
                    VALUES.put(line.substring(0, colon), line.substring(colon + 1));
                }
            }
        } catch (IOException e) {
            LOG.warn("Could not read {}: {}", f, e.toString());
        }
    }

    private static void writeFile() {
        File f = file();
        List<String> lines = new ArrayList<String>();
        for (Map.Entry<String, String> e : VALUES.entrySet()) {
            lines.add(e.getKey() + ":" + e.getValue());
        }
        try {
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Could not write {}: {}", f, e.toString());
        }
    }
}

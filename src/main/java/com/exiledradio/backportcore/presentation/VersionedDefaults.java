package com.exiledradio.backportcore.presentation;

import com.exiledradio.backportcore.presentation.option.PresentationOption;

import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Defaults a version changed (Auto-Jump off in 1.19.4, Brightness 50 in 1.18), applied once per instance.
 *
 * <p>A changed default never reaches an installed instance by itself: the old value is already saved. So
 * Core writes each new default once, records it in {@code config/backportcore/applied_defaults.txt}, and never
 * writes it again; from then on the value is the player's. Entries apply oldest version first, so a later
 * version's default for the same setting wins.
 */
public final class VersionedDefaults {

    /** What kind of setting an entry changes. */
    public enum Kind {
        /** A vanilla setting in options.txt (client only). */
        GAME_OPTION,
        /** A backport option ({@link PresentationOption}). */
        OPTION,
        /** Anything else: a backport's own code. */
        ACTION
    }

    /** One changed default. */
    public static final class Entry {
        public final GameVersion version;
        public final Kind kind;
        public final String key;
        @Nullable
        public final String value;
        @Nullable
        final PresentationOption option;
        @Nullable
        final Runnable action;

        private Entry(GameVersion version, Kind kind, String key, @Nullable String value, @Nullable PresentationOption option, @Nullable Runnable action) {
            this.version = version;
            this.kind = kind;
            this.key = key;
            this.value = value;
            this.option = option;
            this.action = action;
        }

        static Entry gameOption(GameVersion v, String key, String value) {
            return new Entry(v, Kind.GAME_OPTION, key, value, null, null);
        }

        static Entry option(GameVersion v, PresentationOption option, String value) {
            return new Entry(v, Kind.OPTION, option.id(), value, option, null);
        }

        static Entry action(GameVersion v, String key, Runnable action) {
            return new Entry(v, Kind.ACTION, key, null, null, action);
        }

        /** @return the id recorded once this entry is applied */
        public String flag() {
            return this.version + "/" + this.kind.name().toLowerCase(java.util.Locale.ROOT) + "/" + this.key;
        }
    }

    /** Writes vanilla settings into options.txt and reloads them; the client supplies it. */
    public interface GameOptionWriter {
        /** @return true when the values were written */
        boolean write(Map<String, String> values);
    }

    @Nullable
    private static File file;

    private VersionedDefaults() {
    }

    /** @return the record of applied defaults, under the instance's config folder */
    public static synchronized File file() {
        if (file == null) {
            file = new File(new File(Loader.instance().getConfigDir(), "backportcore"), "applied_defaults.txt");
        }
        return file;
    }

    /** For the self-tests: keep the record in another file. */
    public static synchronized void useFile(File other) {
        file = other;
    }

    /** @return the entries of every installed version not yet applied in this instance, oldest version first */
    public static List<Entry> pending(List<Contribution> installed) {
        Set<String> done = readFlags();
        List<Entry> out = new ArrayList<Entry>();
        for (Contribution c : installed) {
            for (Entry e : c.defaults) {
                if (!done.contains(e.flag())) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    /**
     * Applies every pending entry once.
     *
     * @param writer writes vanilla settings; null on a dedicated server, which leaves those entries pending
     * @return how many entries were applied
     */
    public static synchronized int apply(List<Contribution> installed, @Nullable GameOptionWriter writer) {
        List<Entry> pending = pending(installed);
        if (pending.isEmpty()) {
            return 0;
        }
        Set<String> done = readFlags();
        int applied = 0;
        Map<String, String> game = new LinkedHashMap<String, String>();
        List<Entry> gameEntries = new ArrayList<Entry>();
        for (Entry e : pending) {
            try {
                switch (e.kind) {
                    case GAME_OPTION:
                        if (writer != null) {
                            game.put(e.key, e.value);
                            gameEntries.add(e);
                        }
                        continue;
                    case OPTION:
                        e.option.setSaved(e.value);
                        break;
                    default:
                        e.action.run();
                        break;
                }
                done.add(e.flag());
                applied++;
                Presentation.LOG.info("Presentation: applied {}'s default {}", e.version, e.key);
            } catch (RuntimeException ex) {
                Presentation.LOG.warn("Presentation: {}'s default {} failed and will be tried again next launch: {}", e.version, e.key, ex.toString());
            }
        }
        if (!game.isEmpty() && writer.write(game)) {
            for (Entry e : gameEntries) {
                done.add(e.flag());
                applied++;
                Presentation.LOG.info("Presentation: applied {}'s default {}:{}", e.version, e.key, e.value);
            }
        }
        writeFlags(done);
        return applied;
    }

    private static Set<String> readFlags() {
        Set<String> out = new LinkedHashSet<String>();
        File f = file();
        if (f.isFile()) {
            try {
                for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                    if (!line.trim().isEmpty() && !line.startsWith("#")) {
                        out.add(line.trim());
                    }
                }
            } catch (IOException e) {
                Presentation.LOG.warn("Could not read {}: {}", f, e.toString());
            }
        }
        return out;
    }

    private static void writeFlags(Set<String> flags) {
        File f = file();
        List<String> lines = new ArrayList<String>();
        lines.add("# Defaults Backport Core has applied once in this instance (version/kind/setting). Delete a line to apply it again.");
        lines.addAll(flags);
        try {
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Presentation.LOG.warn("Could not write {}: {}", f, e.toString());
        }
    }
}

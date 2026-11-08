package com.exiledradio.backportcore.presentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Steps that run in version order (and in the order they were added within a version), each seeing what the
 * steps before it did: how the fog pipeline ({@code presentation.client.FogPipeline}) applies 1.13's
 * underwater fog before 1.16's darker nether before 1.18's cloud and fog changes.
 *
 * <p>A step whose version is newer than the newest installed version is skipped (it cannot normally happen,
 * since a backport only adds steps for its own version).
 */
public final class OrderedSteps<T> {

    private static final class Slot<T> {
        final GameVersion version;
        final int order;
        final String name;
        final T step;

        Slot(GameVersion version, int order, String name, T step) {
            this.version = version;
            this.order = order;
            this.name = name;
            this.step = step;
        }
    }

    private final List<Slot<T>> slots = new ArrayList<Slot<T>>();
    private volatile List<T> cache = Collections.emptyList();
    private int added;

    /** Adds a step of {@code version}; {@code name} identifies it in the log. */
    public synchronized void add(String version, String name, T step) {
        this.slots.add(new Slot<T>(GameVersion.parse(version), this.added++, name, step));
        this.slots.sort((a, b) -> {
            int c = a.version.compareTo(b.version);
            return c != 0 ? c : Integer.compare(a.order, b.order);
        });
        rebuild();
    }

    /** @return the steps to run, in order, at the newest installed version */
    public List<T> steps() {
        return this.cache;
    }

    /** @return the step names in order, at the given version (for the log and the self-tests) */
    public synchronized List<String> names(GameVersion newest) {
        List<String> out = new ArrayList<String>();
        for (Slot<T> s : this.slots) {
            if (newest.isAtLeast(s.version)) {
                out.add(s.version + " " + s.name);
            }
        }
        return out;
    }

    /** Recomputes the steps for the current newest version (call after more versions register). */
    public synchronized void rebuild() {
        GameVersion newest = Presentation.newest();
        List<T> out = new ArrayList<T>();
        for (Slot<T> s : this.slots) {
            if (newest.isAtLeast(s.version)) {
                out.add(s.step);
            }
        }
        this.cache = Collections.unmodifiableList(out);
    }
}

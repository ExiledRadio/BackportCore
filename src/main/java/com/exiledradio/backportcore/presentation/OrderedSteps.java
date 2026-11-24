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
    private volatile GameVersion builtFor;
    private int added;
    @javax.annotation.Nullable
    private final LookArea area;

    /** Steps that run at the version the whole look resolves at ({@link Presentation#newest()}). */
    public OrderedSteps() {
        this(null);
    }

    /** Steps of one part of the look: they run at {@link Presentation#newest(LookArea)}, none while it is off. */
    public OrderedSteps(@javax.annotation.Nullable LookArea area) {
        this.area = area;
        if (area != null) {
            synchronized (ALL) {
                ALL.add(new java.lang.ref.WeakReference<OrderedSteps<?>>(this));
            }
        }
    }

    private static final List<java.lang.ref.WeakReference<OrderedSteps<?>>> ALL = new ArrayList<java.lang.ref.WeakReference<OrderedSteps<?>>>();

    /** @return the versions of the steps of {@code area} at or before {@code top} (for the Look &amp; Feel page) */
    static List<GameVersion> versions(LookArea area, GameVersion top) {
        List<GameVersion> out = new ArrayList<GameVersion>();
        synchronized (ALL) {
            for (java.lang.ref.WeakReference<OrderedSteps<?>> ref : ALL) {
                OrderedSteps<?> s = ref.get();
                if (s == null || s.area != area) {
                    continue;
                }
                synchronized (s) {
                    for (Slot<?> slot : s.slots) {
                        if (top.isAtLeast(slot.version)) {
                            out.add(slot.version);
                        }
                    }
                }
            }
        }
        return out;
    }

    private GameVersion version() {
        return this.area == null ? Presentation.newest() : Presentation.newest(this.area);
    }

    /** Adds a step of {@code version}; {@code name} identifies it in the log. */
    public synchronized void add(String version, String name, T step) {
        GameVersion v = GameVersion.parse(version);
        for (Slot<T> s : this.slots) {
            if (s.version.equals(v) && s.name.equals(name)) {
                // Backport Core owns this step now (Core 1.4.7) and an older backport jar still adds its copy: the first
                // one, Core's, stays.
                Presentation.LOG.info("Presentation: step \"{} {}\" is already there; the copy is left out", version, name);
                return;
            }
        }
        this.slots.add(new Slot<T>(v, this.added++, name, step));
        this.slots.sort((a, b) -> {
            int c = a.version.compareTo(b.version);
            return c != 0 ? c : Integer.compare(a.order, b.order);
        });
        rebuild();
    }

    /** @return the steps to run, in order, at the newest installed version */
    public List<T> steps() {
        if (!version().equals(this.builtFor)) {
            rebuild();
        }
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
        GameVersion newest = version();
        List<T> out = new ArrayList<T>();
        for (Slot<T> s : this.slots) {
            if (newest.isAtLeast(s.version)) {
                out.add(s.step);
            }
        }
        this.cache = Collections.unmodifiableList(out);
        this.builtFor = newest;
    }
}

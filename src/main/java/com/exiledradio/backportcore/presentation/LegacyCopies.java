package com.exiledradio.backportcore.presentation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Older backport jars that still run their own copy of a piece Backport Core now owns (Core 1.4.7 moved the
 * version-feel pieces that are not tied to one backport's content into Core, {@link Presentation#core(String)}).
 *
 * <p>Options, F3 keys, F3 text, fog steps and the single-slot pieces dedupe by themselves (an older jar's option
 * follows Core's, a step of the same version and name is left out). What cannot is an older jar's own event handler or
 * class patch: Core asks here whether the class that holds that copy is still on the class path, and leaves its own
 * copy of that piece off while it is, so the piece runs once, as it did before. The backports' releases after Core
 * 1.4.7 no longer have those classes.
 */
public final class LegacyCopies {

    private static final Map<String, Boolean> SEEN = new ConcurrentHashMap<String, Boolean>();

    private LegacyCopies() {
    }

    /**
     * @param className a class of the older backport's copy, by its binary name
     *                  ({@code "com.exiledradio.netherupdatebackport.lookfeel.client.PreviousGameMode"})
     * @return true while that class is on the class path (looked up as a resource, never loaded)
     */
    public static boolean present(String className) {
        Boolean known = SEEN.get(className);
        if (known == null) {
            String path = className.replace('.', '/') + ".class";
            ClassLoader loader = LegacyCopies.class.getClassLoader();
            known = loader != null && loader.getResource(path) != null;
            SEEN.put(className, known);
            if (known) {
                Presentation.LOG.info("Presentation: an older backport jar still has {}; Core leaves its own copy of that piece off",
                        className);
            }
        }
        return known;
    }

    /** @return true when none of these classes is on the class path: Core's own copy of the piece runs */
    public static boolean coreRuns(String... olderClasses) {
        for (String c : olderClasses) {
            if (present(c)) {
                return false;
            }
        }
        return true;
    }

    /** For the self-tests: forget what was looked up. */
    public static void forget() {
        SEEN.clear();
    }
}

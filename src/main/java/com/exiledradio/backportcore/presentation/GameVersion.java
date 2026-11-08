package com.exiledradio.backportcore.presentation;

import java.util.Arrays;

/**
 * A Minecraft Java Edition release number ("1.14", "1.16.2", "1.20.5"), compared part by part, with missing
 * parts as zero: "1.14" equals "1.14.0" and comes before "1.14.1".
 *
 * <p>Snapshot and pre-release names are not versions here; a backport names the release it brings.
 */
public final class GameVersion implements Comparable<GameVersion> {

    /** The game every backport runs on, and what the presentation shows when no backport is installed. */
    public static final GameVersion BASE = parse("1.12.2");

    private final int[] parts;
    private final String text;

    private GameVersion(int[] parts, String text) {
        this.parts = parts;
        this.text = text;
    }

    /**
     * @param text a release number: two to four dot-separated non-negative numbers
     * @throws IllegalArgumentException for anything else ("1.14-pre1", "1", "")
     */
    public static GameVersion parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("null version");
        }
        String[] split = text.trim().split("\\.");
        if (split.length < 2 || split.length > 4) {
            throw new IllegalArgumentException("not a release number: \"" + text + "\"");
        }
        int[] parts = new int[split.length];
        for (int i = 0; i < split.length; i++) {
            try {
                parts[i] = Integer.parseInt(split[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("not a release number: \"" + text + "\"");
            }
            if (parts[i] < 0) {
                throw new IllegalArgumentException("not a release number: \"" + text + "\"");
            }
        }
        // "1.14.0" is written "1.14", as Mojang names it.
        int length = parts.length;
        while (length > 2 && parts[length - 1] == 0) {
            length--;
        }
        int[] trimmed = Arrays.copyOf(parts, length);
        StringBuilder canonical = new StringBuilder();
        for (int i = 0; i < trimmed.length; i++) {
            if (i > 0) {
                canonical.append('.');
            }
            canonical.append(trimmed[i]);
        }
        return new GameVersion(trimmed, canonical.toString());
    }

    /** @return true when this is the same release as {@code other} or a later one */
    public boolean isAtLeast(GameVersion other) {
        return compareTo(other) >= 0;
    }

    /** @see #isAtLeast(GameVersion) */
    public boolean isAtLeast(String other) {
        return isAtLeast(parse(other));
    }

    @Override
    public int compareTo(GameVersion o) {
        int n = Math.max(this.parts.length, o.parts.length);
        for (int i = 0; i < n; i++) {
            int a = i < this.parts.length ? this.parts[i] : 0;
            int b = i < o.parts.length ? o.parts[i] : 0;
            if (a != b) {
                return a < b ? -1 : 1;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GameVersion && compareTo((GameVersion) o) == 0;
    }

    @Override
    public int hashCode() {
        return this.text.hashCode();
    }

    /** @return the release number as Mojang writes it ("1.14", "1.16.2") */
    @Override
    public String toString() {
        return this.text;
    }
}

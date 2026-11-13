package com.exiledradio.backportcore.presentation.option;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * A button that steps through a fixed list of values on each click, drawn as vanilla's are: "Label: Value".
 * Each value has a saved name and a lang key.
 */
public final class CycleOption extends PresentationOption {

    private final String[] names;
    private final String[] valueKeys;
    private final int defaultIndex;
    private int index;
    @Nullable
    private Consumer<String> listener;

    /**
     * @param names     the values as saved, in click order
     * @param valueKeys the lang key of each value
     */
    public CycleOption(String id, String labelKey, OptionScreen screen, String[] names, String[] valueKeys, String defaultName) {
        super(id, labelKey, screen);
        if (names.length == 0 || names.length != valueKeys.length) {
            throw new IllegalArgumentException(id + ": names and value keys must pair up");
        }
        this.names = names.clone();
        this.valueKeys = valueKeys.clone();
        this.defaultIndex = indexOf(defaultName);
        if (this.defaultIndex < 0) {
            throw new IllegalArgumentException(id + ": default " + defaultName + " is not a value");
        }
        this.index = this.defaultIndex;
    }

    @Nullable
    private String[] valueTooltips;

    /**
     * One tooltip per value, in the order of the values (1.21.2's "Reduce FPS when" explains each); a null entry
     * falls back to {@link #tooltip}. Since Core 1.2.0.
     */
    public CycleOption valueTooltips(String... keys) {
        if (keys.length != this.names.length) {
            throw new IllegalArgumentException(id() + ": one tooltip per value");
        }
        this.valueTooltips = keys.clone();
        return this;
    }

    @Override
    @Nullable
    public String tooltipKey() {
        if (this.valueTooltips != null) {
            ensureLoaded();
            String key = this.valueTooltips[this.index];
            if (key != null) {
                return key;
            }
        }
        return super.tooltipKey();
    }

    /** @return the current value's saved name */
    public String get() {
        ensureLoaded();
        return this.names[this.index];
    }

    /** @return the current value's lang key */
    public String valueKey() {
        ensureLoaded();
        return this.valueKeys[this.index];
    }

    public void set(String name) {
        int i = indexOf(name);
        if (i < 0) {
            throw new IllegalArgumentException(id() + ": " + name + " is not a value");
        }
        ensureLoaded();
        boolean changed = i != this.index;
        this.index = i;
        save();
        if (changed && this.listener != null) {
            this.listener.accept(name);
        }
    }

    /** Steps to the next value (or the previous one, for {@code backwards}), wrapping around. */
    public void cycle(boolean backwards) {
        ensureLoaded();
        int n = this.names.length;
        set(this.names[(this.index + (backwards ? n - 1 : 1)) % n]);
    }

    /** Called with the new value's name whenever it changes. */
    public CycleOption onChange(Consumer<String> listener) {
        this.listener = listener;
        return this;
    }

    private int indexOf(String name) {
        for (int i = 0; i < this.names.length; i++) {
            if (this.names[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String write() {
        return this.names[this.index];
    }

    @Override
    public void read(String saved) {
        int i = indexOf(saved);
        if (i < 0) {
            throw new IllegalArgumentException(saved);
        }
        this.index = i;
    }

    @Override
    public void reset() {
        set(this.names[this.defaultIndex]);
    }
}

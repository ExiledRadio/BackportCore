package com.exiledradio.backportcore.presentation.option;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/** An on/off option, drawn as vanilla's toggle buttons are: "Label: ON". */
public final class ToggleOption extends PresentationOption {

    private final boolean defaultValue;
    private boolean value;
    @Nullable
    private Consumer<Boolean> listener;

    public ToggleOption(String id, String labelKey, OptionScreen screen, boolean defaultValue) {
        super(id, labelKey, screen);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public boolean get() {
        ensureLoaded();
        return this.value;
    }

    public void set(boolean value) {
        ensureLoaded();
        boolean changed = this.value != value;
        this.value = value;
        save();
        if (changed && this.listener != null) {
            this.listener.accept(value);
        }
    }

    public boolean defaultValue() {
        return this.defaultValue;
    }

    /** Called with the new value whenever it changes. */
    public ToggleOption onChange(Consumer<Boolean> listener) {
        this.listener = listener;
        return this;
    }

    @Override
    public String write() {
        return Boolean.toString(this.value);
    }

    @Override
    public void read(String saved) {
        if (!"true".equals(saved) && !"false".equals(saved)) {
            throw new IllegalArgumentException(saved);
        }
        this.value = Boolean.parseBoolean(saved);
    }

    @Override
    public void reset() {
        set(this.defaultValue);
    }
}

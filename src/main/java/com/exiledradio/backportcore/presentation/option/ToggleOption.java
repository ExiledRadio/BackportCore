package com.exiledradio.backportcore.presentation.option;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** An on/off option, drawn as vanilla's toggle buttons are: "Label: ON". */
public final class ToggleOption extends PresentationOption {

    private final boolean defaultValue;
    private boolean value;
    @Nullable
    private Consumer<Boolean> listener;
    @Nullable
    private BooleanSupplier followedDefault;
    /** True once the player set it (or a saved value was read): a followed default no longer applies. */
    private boolean chosen;
    @Nullable
    private String onTooltip;
    @Nullable
    private String offTooltip;

    public ToggleOption(String id, String labelKey, OptionScreen screen, boolean defaultValue) {
        super(id, labelKey, screen);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public boolean get() {
        if (canonical() != null) {
            return ((ToggleOption) canonical()).get();
        }
        ensureLoaded();
        return this.chosen || this.followedDefault == null ? this.value : this.followedDefault.getAsBoolean();
    }

    public void set(boolean value) {
        if (canonical() != null) {
            ((ToggleOption) canonical()).set(value);
            return;
        }
        ensureLoaded();
        boolean changed = get() != value;
        this.value = value;
        this.chosen = true;
        save();
        if (changed && this.listener != null) {
            this.listener.accept(value);
        }
        if (changed) {
            changedMirrors();
        }
    }

    @Override
    protected void mirrorChanged() {
        if (this.listener != null) {
            this.listener.accept(get());
        }
    }

    public boolean defaultValue() {
        return this.followedDefault != null ? this.followedDefault.getAsBoolean() : this.defaultValue;
    }

    /**
     * A default that follows the installed versions until the player sets the option (26.3 made Exclusive Fullscreen
     * off by default, where 26.1 had it on): nothing is saved until then. Since Core 1.4.4.
     */
    public ToggleOption defaultFollows(BooleanSupplier value) {
        this.followedDefault = value;
        return this;
    }

    /** @return true when the player has set it (or a saved value was read), so a followed default no longer applies */
    public boolean chosen() {
        if (canonical() != null) {
            return ((ToggleOption) canonical()).chosen();
        }
        ensureLoaded();
        return this.chosen;
    }

    /** A tooltip for each value, as 26.1's Exclusive Fullscreen ({@code .on.tooltip}, {@code .off.tooltip}). Since Core 1.4.4. */
    public ToggleOption valueTooltips(String on, String off) {
        this.onTooltip = on;
        this.offTooltip = off;
        return this;
    }

    @Nullable
    @Override
    public String tooltipKey() {
        if (this.onTooltip != null) {
            return get() ? this.onTooltip : this.offTooltip;
        }
        return super.tooltipKey();
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
        this.chosen = true;
    }

    @Override
    public void reset() {
        set(defaultValue());
    }
}

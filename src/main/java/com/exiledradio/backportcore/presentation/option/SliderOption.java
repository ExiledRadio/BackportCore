package com.exiledradio.backportcore.presentation.option;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A slider, drawn as vanilla's are: "Label: 100%". The value runs from {@code min} to {@code max} in steps of
 * {@code step} (0 for none).
 *
 * <p>By default the value shows as a percentage of 1 ({@code 0.5} is "50%"), as vanilla's 0-to-1 sliders do.
 * {@link #decimals(int)} shows the number itself instead, and {@link #valueKey(String)} puts it into a lang
 * string ({@code "%s chunks"}).
 */
public final class SliderOption extends PresentationOption {

    private final double min;
    private final double max;
    private final double step;
    private final double defaultValue;
    private double value;
    private int decimals = -1;
    @Nullable
    private String valueKey;
    @Nullable
    private String zeroKey;
    @Nullable
    private String wholeKey;
    @Nullable
    private String wholeZeroKey;
    @Nullable
    private Consumer<Double> listener;

    public SliderOption(String id, String labelKey, OptionScreen screen, double min, double max, double step, double defaultValue) {
        super(id, labelKey, screen);
        if (!(min < max) || step < 0 || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException(id + ": bad range " + min + ".." + max + " step " + step + " default " + defaultValue);
        }
        this.min = min;
        this.max = max;
        this.step = step;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public double get() {
        if (canonical() != null) {
            return ((SliderOption) canonical()).get();
        }
        ensureLoaded();
        return this.value;
    }

    /** Sets the value, clamped to the range and snapped to the step. */
    public void set(double value) {
        if (canonical() != null) {
            ((SliderOption) canonical()).set(value);
            return;
        }
        ensureLoaded();
        double v = snap(value);
        boolean changed = v != this.value;
        this.value = v;
        save();
        if (changed && this.listener != null) {
            this.listener.accept(v);
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

    /** @return the value clamped to the range and snapped to the step */
    public double snap(double v) {
        if (this.step > 0) {
            v = this.min + Math.round((v - this.min) / this.step) * this.step;
        }
        return Math.max(this.min, Math.min(this.max, v));
    }

    public double min() {
        return this.min;
    }

    public double max() {
        return this.max;
    }

    public double defaultValue() {
        return this.defaultValue;
    }

    /** @return the slider position of the current value, 0 to 1 */
    public double fraction() {
        return (get() - this.min) / (this.max - this.min);
    }

    /** Sets the value from a slider position, 0 to 1. */
    public void setFraction(double fraction) {
        set(this.min + Math.max(0, Math.min(1, fraction)) * (this.max - this.min));
    }

    /** Shows the number itself with this many decimals, instead of a percentage. */
    public SliderOption decimals(int decimals) {
        this.decimals = decimals;
        return this;
    }

    /** Puts the shown number into this lang string ({@code %s}). */
    public SliderOption valueKey(String key) {
        this.valueKey = key;
        return this;
    }

    @Nullable
    public String valueKey() {
        return this.valueKey;
    }

    /**
     * At zero the value shows as this lang string instead of the number, as 1.16's "Distortion Effects: OFF"
     * ({@code zeroKey("options.off")}).
     */
    public SliderOption zeroKey(String key) {
        this.zeroKey = key;
        return this;
    }

    @Nullable
    public String zeroKey() {
        return this.zeroKey;
    }

    /**
     * The whole button text is one lang string with the shown number as {@code %s}, and at zero another one, as
     * 1.16's Chat Delay ({@code "options.chat.delay"} "Chat Delay: %s seconds", {@code "options.chat.delay_none"}
     * "Chat Delay: None"). The label key is then only the option's name for the log.
     */
    public SliderOption wholeLabel(String key, @Nullable String atZero) {
        this.wholeKey = key;
        this.wholeZeroKey = atZero;
        return this;
    }

    @Nullable
    public String wholeKey() {
        return this.wholeKey;
    }

    @Nullable
    public String wholeZeroKey() {
        return this.wholeZeroKey;
    }

    /** @return true when the value is at zero (or the range's bottom when that is above zero, never) */
    public boolean isZero() {
        return get() == 0.0;
    }

    /** @return the value as shown after the label, before any {@link #valueKey}: "50%" or "2.5" */
    public String valueText() {
        double v = get();
        if (this.text != null) {
            return this.text.apply(v);
        }
        if (this.decimals < 0) {
            return Math.round(v * 100) + "%";
        }
        return String.format(Locale.ROOT, "%." + this.decimals + "f", v);
    }

    @Nullable
    private java.util.function.DoubleFunction<String> text;

    /**
     * Shows the value as this function writes it (before any {@link #valueKey}): 1.21.11's Anisotropic Filtering shows
     * 2 to the value ("4x"). Since Core 1.3.0.
     */
    public SliderOption valueText(java.util.function.DoubleFunction<String> text) {
        this.text = text;
        return this;
    }

    /** Called with the new value whenever it changes. */
    public SliderOption onChange(Consumer<Double> listener) {
        this.listener = listener;
        return this;
    }

    @Override
    public String write() {
        return Double.toString(this.value);
    }

    @Override
    public void read(String saved) {
        this.value = snap(Double.parseDouble(saved));
    }

    @Override
    public void reset() {
        set(this.defaultValue);
    }
}

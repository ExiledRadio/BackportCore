package com.exiledradio.backportcore.presentation.option;

/**
 * An option whose value is a line of text, with no button of its own: the screen that shows it draws its own widget
 * (1.13's Fullscreen Resolution, whose values are the monitor's modes). Saved as written. Since Core 1.4.5.
 */
public final class TextOption extends PresentationOption {

    private final String defaultValue;
    private String value;

    public TextOption(String id, String labelKey, OptionScreen screen, String defaultValue) {
        super(id, labelKey, screen);
        if (defaultValue.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(id + ": one line only");
        }
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String get() {
        ensureLoaded();
        return this.value;
    }

    public void set(String value) {
        ensureLoaded();
        this.value = value.replace('\n', ' ').replace('\r', ' ');
        save();
    }

    @Override
    public String write() {
        return this.value;
    }

    @Override
    public void read(String saved) {
        this.value = saved;
    }

    @Override
    public void reset() {
        set(this.defaultValue);
    }
}

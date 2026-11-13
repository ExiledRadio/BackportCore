package com.exiledradio.backportcore.presentation.option;

import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.Presentation;

import javax.annotation.Nullable;

/**
 * One option a backport adds to vanilla's options screens: its id, its label, the screen it goes on, the
 * version that added it and (optionally) the version that removed it.
 *
 * <p>Make one with {@link ToggleOption}, {@link SliderOption} or {@link CycleOption}, then register it with
 * {@link com.exiledradio.backportcore.presentation.Contribution#option(PresentationOption)}, which also makes
 * that contribution's version its {@link #since(String) since} version unless one was set. Read its value with
 * the kind's {@code get()} wherever the option takes effect; Core saves it in
 * {@code config/backportcore/options.txt}, one {@code id:value} line each, as vanilla's options.txt does.
 *
 * <p>It shows only while the newest installed version is at or after {@code since} (and before
 * {@code until}). Where it shows is {@link OptionScreen#resolve}: a screen that does not exist at the newest
 * version sends it to that screen's fallback.
 */
public abstract class PresentationOption {

    private final String id;
    private final String labelKey;
    private final OptionScreen screen;
    @Nullable
    private GameVersion since;
    @Nullable
    private GameVersion until;
    @Nullable
    private String after;
    @Nullable
    private String tooltipKey;
    private final java.util.List<OptionScreen> alsoOn = new java.util.ArrayList<OptionScreen>();
    private boolean loaded;

    /**
     * @param id       unique and permanent: {@code "modid:name"}; it is the key in the options file
     * @param labelKey the lang key of its label, as vanilla's options use ({@code "options.entityDistanceScaling"})
     * @param screen   the screen vanilla has it on at the version that added it
     */
    protected PresentationOption(String id, String labelKey, OptionScreen screen) {
        if (id == null || id.indexOf(':') <= 0 || id.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("option id must be \"modid:name\": " + id);
        }
        this.id = id;
        this.labelKey = labelKey;
        this.screen = screen;
    }

    public final String id() {
        return this.id;
    }

    public final String labelKey() {
        return this.labelKey;
    }

    /** @return the screen it was placed on (before {@link OptionScreen#resolve}) */
    public final OptionScreen screen() {
        return this.screen;
    }

    @Nullable
    public final GameVersion since() {
        return this.since;
    }

    @Nullable
    public final GameVersion until() {
        return this.until;
    }

    /** @return the id it is placed after on its screen, or null to go after the screen's own options */
    @Nullable
    public final String after() {
        return this.after;
    }

    /** The version that added this option. Defaults to the version of the contribution it is registered with. */
    public PresentationOption since(String version) {
        this.since = GameVersion.parse(version);
        return this;
    }

    /** The version that removed it again, if one did: it is hidden from that version on. */
    public PresentationOption until(String version) {
        this.until = GameVersion.parse(version);
        return this;
    }

    /**
     * Places it right after another entry of the same screen, as vanilla's layout at the newest version has it:
     * another option's id, or one of vanilla's own entries by its {@code GameSettings.Options} name
     * ({@code "RENDER_CLOUDS"}, {@code "NARRATOR"}). Honored on the list screens (Video Settings and Core's
     * own screens); on the others it goes after the screen's own buttons.
     */
    public PresentationOption after(String entry) {
        this.after = entry;
        return this;
    }

    /**
     * Its tooltip (1.16-pre1: every option of Video Settings and the screens like it shows one while hovered): a
     * lang key, as vanilla's {@code "options.screenEffectScale.tooltip"}. Lines break at 200 pixels, as vanilla's.
     */
    public PresentationOption tooltip(String key) {
        this.tooltipKey = key;
        return this;
    }

    /** @return its tooltip's lang key now (a cycle option's may follow its value), or null */
    @Nullable
    public String tooltipKey() {
        return this.tooltipKey;
    }

    /**
     * Also shows it on another screen, as vanilla does for some (1.16.2's Distortion Effects and FOV Effects are
     * on Video Settings and on Accessibility Settings). Both buttons change the one value. A screen that does not
     * exist at the newest version resolves to its fallback, as {@link #screen()} does; the option never shows
     * twice on one screen.
     */
    public PresentationOption alsoOn(OptionScreen... screens) {
        for (OptionScreen s : screens) {
            if (s != this.screen && !this.alsoOn.contains(s)) {
                this.alsoOn.add(s);
            }
        }
        return this;
    }

    /** @return the other screens it shows on (before {@link OptionScreen#resolve}) */
    public final java.util.List<OptionScreen> alsoOn() {
        return java.util.Collections.unmodifiableList(this.alsoOn);
    }

    /** @return true when, with {@code newest} the newest installed version, it shows on {@code target} */
    public final boolean isOn(OptionScreen target, GameVersion newest) {
        if (this.screen.resolve(newest) == target) {
            return true;
        }
        for (OptionScreen s : this.alsoOn) {
            if (s.resolve(newest) == target) {
                return true;
            }
        }
        return false;
    }

    /** @return true while the newest installed version shows it */
    public final boolean isShown() {
        return isShownAt(Presentation.newest());
    }

    /** @return true when version {@code v} shows it */
    public final boolean isShownAt(GameVersion v) {
        return (this.since == null || v.isAtLeast(this.since)) && (this.until == null || !v.isAtLeast(this.until));
    }

    /** Reads the saved value once, the first time it is needed. */
    protected final void ensureLoaded() {
        if (!this.loaded) {
            this.loaded = true;
            String saved = OptionStore.get(this.id);
            if (saved != null) {
                try {
                    read(saved);
                } catch (RuntimeException e) {
                    OptionStore.LOG.warn("Option {}: ignoring saved value \"{}\"", this.id, saved);
                }
            }
        }
    }

    /** Saves the current value. */
    protected final void save() {
        OptionStore.put(this.id, write());
    }

    /** Sets the value from its saved form and saves it (no change listener runs). Used for versioned defaults. */
    public final void setSaved(String value) {
        ensureLoaded();
        read(value);
        save();
    }

    /** @return the current value as saved in the options file */
    public abstract String write();

    /** Sets the value from its saved form; may throw on a malformed value. */
    public abstract void read(String saved);

    /** Puts the default value back and saves it. */
    public abstract void reset();
}

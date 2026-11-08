package com.exiledradio.backportcore.presentation.option;

import com.exiledradio.backportcore.presentation.GameVersion;

import javax.annotation.Nullable;

/**
 * The options screens an option can be placed on, by vanilla's name for them.
 *
 * <p>The first six are 1.12.2's own screens, which Core adds to through Forge's GUI events. The rest came
 * later; Core builds {@link #ACCESSIBILITY} and {@link #MOUSE} itself once a 1.14 or later backport is
 * installed. The 1.18 and 1.20.5 screens are not built yet: an option placed on one shows on its
 * {@link #fallback()} until they are.
 */
public enum OptionScreen {
    /** The main Options screen ({@code GuiOptions}). */
    OPTIONS("1.12.2", null, true),
    /** Video Settings ({@code GuiVideoSettings}), a scrolling list. */
    VIDEO("1.12.2", null, true),
    /** Controls ({@code GuiControls}): the row above the key binds. */
    CONTROLS("1.12.2", null, true),
    /** Chat Settings ({@code ScreenChatOptions}). */
    CHAT("1.12.2", null, true),
    /** Music and Sounds ({@code GuiScreenOptionsSounds}). */
    SOUNDS("1.12.2", null, true),
    /** Skin Customization ({@code GuiCustomizeSkin}). */
    SKIN("1.12.2", null, true),
    /** Accessibility Settings, 1.14 (reached from Options). */
    ACCESSIBILITY("1.14", CHAT, true),
    /** Mouse Settings, 1.14 (reached from Controls; Sensitivity, Invert Mouse and Touchscreen move there). */
    MOUSE("1.14", CONTROLS, true),
    /** Key Binds, 1.18. Not built yet. */
    KEY_BINDS("1.18", CONTROLS, false),
    /** Online Options, 1.18. Not built yet. */
    ONLINE("1.18", OPTIONS, false),
    /** Font Settings, 1.20.5. Not built yet. */
    FONT("1.20.5", OPTIONS, false);

    private final GameVersion since;
    @Nullable
    private final OptionScreen fallback;
    private final boolean built;

    OptionScreen(String since, @Nullable OptionScreen fallback, boolean built) {
        this.since = GameVersion.parse(since);
        this.fallback = fallback;
        this.built = built;
    }

    /** @return the version that introduced this screen */
    public GameVersion since() {
        return this.since;
    }

    /** @return where its options go when this screen does not exist, or null for 1.12.2's screens */
    @Nullable
    public OptionScreen fallback() {
        return this.fallback;
    }

    /** @return the screen an option placed here actually shows on, with {@code newest} the newest installed version */
    public OptionScreen resolve(GameVersion newest) {
        OptionScreen s = this;
        while ((!s.built || !newest.isAtLeast(s.since)) && s.fallback != null) {
            s = s.fallback;
        }
        return s;
    }
}

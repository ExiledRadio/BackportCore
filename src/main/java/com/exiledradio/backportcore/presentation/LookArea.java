package com.exiledradio.backportcore.presentation;

import com.exiledradio.backportcore.CoreConfig;

/**
 * The parts of the game's look and feel that can be turned off one by one, back to 1.12.2's, from Core's Look &amp; Feel
 * page ({@code CoreConfig.lookAndFeel}). Each resolves at its own version ({@link Presentation#newest(LookArea)}): the
 * newest installed version, or 1.12.2 when its switch (or the master switch) is off.
 *
 * <p>A backport asks about one with {@link Presentation#isAtLeast(LookArea, String)}, for a piece it draws itself.
 * Since Core 1.4.0.
 */
public enum LookArea {

    /** The world-loading screens: 1.13's percentage, 1.14's chunk grid, 1.16's "Reading world data...", 1.20.5's and 1.21.9's. */
    LOADING("loadingScreens", "1.13", "1.14", "1.16", "1.20.5", "1.21", "1.21.9"),
    /** The startup screen drawn when resources load (1.14's Mojang bar, 1.16's Mojang Studios). */
    STARTUP("startupScreen", "1.14", "1.16", "1.17"),
    /** The title screen: panorama, its overlay and motion, the logo, the fade-in and the Accessibility button. */
    TITLE("titleScreen", "1.13", "1.14", "1.19.4", "1.20", "1.20.5"),
    /** The splash text list. */
    SPLASHES("splashes"),
    /** Menu backgrounds, buttons, the pause and death screens, the world list and the multiplayer warning. */
    MENUS("menus", "1.13", "1.14", "1.15", "1.15.2", "1.16", "1.20.5", "1.21.2", "1.21.9"),
    /** The newer options screens (Accessibility, Mouse, Controls, Key Binds) and the options on them. */
    OPTIONS("optionsScreens", "1.14", "1.16", "1.16.4", "1.18", "1.19.3", "1.19.4", "1.20.2", "1.20.5", "1.21.9"),
    /** Video Settings: its layout, its removed and added options. */
    VIDEO("videoSettings", "1.13", "1.14", "1.21.6", "1.21.11", "26.3"),
    /** Chat: command suggestions, the chat options and look. */
    CHAT("chat", "1.13", "1.14", "1.16"),
    /** F3: the debug keys, the debug screen's lines, Debug Options and the charts. */
    DEBUG("debug", "1.20.2", "1.21.9", "1.21.11", "26.3"),
    /** Toasts: the music toast and the newer toasts' textures. */
    TOASTS("toasts", "1.21.6"),
    /**
     * Inventory and creative tabs: 1.19.3's creative tabs (creative.client.ModernTabs), the creative order (26.3's,
     * creative.client.ModernOrder), slot highlights and the container textures.
     */
    INVENTORY("inventory", "1.19.3", "1.21.2", "26.3"),
    /** Sounds and music: the menu music, the in-game music, the music options. */
    MUSIC("music", "1.14", "1.21.6"),
    /** Sky, fog and clouds: each version's fog, sky color, clouds and sunrise steps. */
    SKY("sky"),
    /** Vanilla's text as each version words it. */
    WORDING("wording", "1.20.2"),
    /** Vanilla's textures as each version has them (Core's existing {@code textures.modernTextures}). */
    TEXTURES("modernTextures", "1.14");

    private final String field;
    private final GameVersion[] coreVersions;

    LookArea(String field, String... coreVersions) {
        this.field = field;
        this.coreVersions = new GameVersion[coreVersions.length];
        for (int i = 0; i < coreVersions.length; i++) {
            this.coreVersions[i] = GameVersion.parse(coreVersions[i]);
        }
    }

    /** @return the name of its switch in Core's config ({@code lookAndFeel.<field>}, or {@code textures.modernTextures}) */
    public String field() {
        return this.field;
    }

    /** @return the versions at which Core itself changes this part (the backports' contributions add theirs) */
    public GameVersion[] coreVersions() {
        return this.coreVersions.clone();
    }

    /** @return its own switch, read from Core's config (the master switch aside) */
    public boolean switchOn() {
        CoreConfig.LookAndFeelSettings s = CoreConfig.lookAndFeel;
        switch (this) {
            case LOADING:
                return s.loadingScreens;
            case STARTUP:
                return s.startupScreen;
            case TITLE:
                return s.titleScreen;
            case SPLASHES:
                return s.splashes;
            case MENUS:
                return s.menus;
            case OPTIONS:
                return s.optionsScreens;
            case VIDEO:
                return s.videoSettings;
            case CHAT:
                return s.chat;
            case DEBUG:
                return s.debug;
            case TOASTS:
                return s.toasts;
            case INVENTORY:
                return s.inventory;
            case MUSIC:
                return s.music;
            case SKY:
                return s.sky;
            case WORDING:
                return s.wording;
            case TEXTURES:
                return CoreConfig.textures.modernTextures;
            default:
                return true;
        }
    }

    /**
     * @return the area a replaced vanilla asset belongs to, by its path under {@code assets/minecraft/}: the title
     *         screen's textures, the startup logos, toasts, containers, other GUI textures (menus), everything else
     *         (blocks, items, mobs) textures
     */
    public static LookArea ofAsset(String path) {
        String p = path.toLowerCase(java.util.Locale.ROOT);
        if (p.startsWith("textures/gui/title/mojang")) {
            return STARTUP;
        }
        if (p.startsWith("textures/gui/title/")) {
            return TITLE;
        }
        if (p.contains("toast") || p.contains("music_notes")) {
            return TOASTS;
        }
        if (p.contains("/container/") || p.contains("recipe_book") || p.contains("recipe_button") || p.contains("slot_highlight")) {
            return INVENTORY;
        }
        if (p.startsWith("textures/gui/")) {
            return MENUS;
        }
        return TEXTURES;
    }
}

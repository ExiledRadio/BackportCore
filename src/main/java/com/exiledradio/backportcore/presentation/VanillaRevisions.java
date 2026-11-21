package com.exiledradio.backportcore.presentation;

/**
 * What Core knows of vanilla's own screens at each version, as it knows the loading screen's revisions
 * ({@link LoadingRevision}): the newest installed version picks each, so a change one version made stays made
 * when only a later backport is installed (vanilla never brought 3D Anaglyph back). The assets and the text these
 * screens show are the backports' ({@link Contribution#replaceAsset}, {@link Contribution#vanillaText}); Core
 * never ships a vanilla asset.
 */
public final class VanillaRevisions {

    private VanillaRevisions() {
    }

    /** The startup (Mojang) screen. */
    public enum Startup {
        /** 1.12.2 and 1.13: no screen of Core's (Forge's loading screen runs, then the title screen). */
        NONE,
        /** 19w06a: white, the 256 by 256 {@code mojang.png} and a red bar, fading into the menu. */
        BAR_1_14,
        /** 20w21a: red ({@code 0xEF323D}), the Mojang Studios logo in two halves, a white bar. */
        STUDIOS_1_16;

        public static Startup forVersion(GameVersion v) {
            return v.isAtLeast("1.16") ? STUDIOS_1_16 : v.isAtLeast("1.14") ? BAR_1_14 : NONE;
        }

        /** @return the vanilla texture this revision draws */
        public String logoPath() {
            return this == STUDIOS_1_16 ? "textures/gui/title/mojangstudios.png" : "textures/gui/title/mojang.png";
        }
    }

    /** @return the startup screen at the newest installed version */
    public static Startup startup() {
        return Startup.forVersion(Presentation.newest(LookArea.STARTUP));
    }

    /** 1.13: the title screen fades in on the first start (its panorama at once, the rest over the next second). */
    public static boolean titleFadeIn() {
        return Presentation.isAtLeast(LookArea.TITLE, "1.13");
    }

    /** 1.13 (17w43a): 3D Anaglyph is gone from Video Settings, and off. */
    public static boolean anaglyphRemoved() {
        return Presentation.isAtLeast(LookArea.VIDEO, "1.13");
    }

    /** 1.13: GUI Scale counts Auto, 1, 2, 3 ... up to what the window fits (4 at 1080p). */
    public static boolean numberedGuiScale() {
        return Presentation.isAtLeast(LookArea.VIDEO, "1.13");
    }

    /** 1.14 (18w44a): Use VBOs is gone from Video Settings, and always on. */
    public static boolean vboRemoved() {
        return Presentation.isAtLeast(LookArea.VIDEO, "1.14");
    }

    /** 1.14 (18w43a): the pause menu's Give Feedback and Report Bugs row. */
    public static boolean pauseFeedback() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.14");
    }

    /**
     * 26.2: the pause menu's Report Bugs and Send Feedback are 20 by 20 icon buttons in one row under Advancements and
     * Statistics (sprites {@code pause_menu/bug} and {@code pause_menu/social_interactions}, supplied by a backport).
     */
    public static boolean pauseIconRow() {
        return Presentation.isAtLeast(LookArea.MENUS, "26.2");
    }

    /** 26.3: an options list's section headers are bold, underlined and centered ({@code OptionsList.addHeader}). */
    public static boolean centeredOptionHeaders() {
        return Presentation.isAtLeast(LookArea.MENUS, "26.3");
    }

    /**
     * 26.2's pause grid top: {@code FrameLayout.alignInRectangle} at a quarter of the free height, the grid being rows
     * of 24 (20 and a 4 padding) with the first 70 (Back to Game's 50 padding).
     */
    public static int pauseGridTop(int height, int rows) {
        int gridHeight = 70 + 24 * (rows - 1);
        return (int) (0.25F * (height - gridHeight));
    }

    /** 1.15 (19w35a): a hardcore death screen has one "Title screen" button instead of Delete World and Leave Server. */
    public static boolean hardcoreTitleButton() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.15");
    }

    /** 1.15: button text is white, hovered or not (was 0xE0E0E0, and yellow 0xFFFFA0 hovered). */
    public static boolean whiteButtonText() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.15");
    }

    // ---- Since Core 1.3.0: 1.18 to 1.21.11's options screens, F3 and the title logo ----

    /**
     * 1.18 (21w37a): Controls is a screen of options (Mouse Settings... and Key Binds..., then Toggle Crouch, Toggle
     * Sprint and Auto-Jump); 1.12.2's key list is the Key Binds screen behind it.
     */
    public static boolean keyBindsScreen(GameVersion v) {
        return v.isAtLeast("1.18");
    }

    /**
     * 1.21.9 (25w31a): Auto-Jump, Toggle Crouch and Toggle Sprint are on Controls only, and Accessibility Settings has
     * a "Controls..." button beside Narrator.
     */
    public static boolean controlsLeaveAccessibility(GameVersion v) {
        return v.isAtLeast("1.21.9");
    }

    /**
     * 1.21.9: the F3 rework. Each part of the debug screen is off, shown with F3, or always shown (the Debug Options
     * screen on F3 + F6), and F3's combinations work in menus.
     */
    public static boolean debugOptions(GameVersion v) {
        return v.isAtLeast("1.21.9");
    }

    /** 1.21.11: the F3 combinations are key binds of their own, in the Key Binds screen's "Debug" category. */
    public static boolean debugKeyBinds(GameVersion v) {
        return v.isAtLeast("1.21.11");
    }

    /**
     * 1.21.11 (25w41a): Video Settings in three sections (Display, Quality &amp; Performance, Preferences) with the
     * Preset slider; Graphics is gone (its leaves are See-Through Leaves) and View Bobbing is on Accessibility.
     */
    public static boolean videoSections(GameVersion v) {
        return v.isAtLeast("1.21.11");
    }

    /**
     * Where vanilla's own option of this name (an option id's path, as {@code toggle_crouch}) shows at {@code v}, when
     * a version moved it: Toggle Crouch and Toggle Sprint are on Accessibility and Controls from 1.18 and on Controls
     * only from 1.21.9. Null when the option stays where it was registered.
     */
    @javax.annotation.Nullable
    public static java.util.List<com.exiledradio.backportcore.presentation.option.OptionScreen> vanillaScreens(String name, GameVersion v) {
        if ("toggle_crouch".equals(name) || "toggle_sprint".equals(name)) {
            if (controlsLeaveAccessibility(v)) {
                return java.util.Collections.singletonList(com.exiledradio.backportcore.presentation.option.OptionScreen.CONTROLS);
            }
            if (keyBindsScreen(v)) {
                return java.util.Arrays.asList(com.exiledradio.backportcore.presentation.option.OptionScreen.ACCESSIBILITY,
                        com.exiledradio.backportcore.presentation.option.OptionScreen.CONTROLS);
            }
        }
        return null;
    }

    /**
     * 1.20.5 (24w09a, {@code PanoramaRenderer}): the panorama's pitch holds at 10 degrees and it only turns (1.13's nod
     * between 20 and 30 degrees is gone), by one spin shared by every screen and never restarted.
     */
    public static boolean panoramaSpinOnly(GameVersion v) {
        return v.isAtLeast("1.20.5");
    }

    /**
     * 1.20.5 (24w09a, {@code Screen.renderBackground}): menus over the blurred panorama (outside a world) or the blurred
     * world, under {@code menu_background.png} or {@code inworld_menu_background.png}; lists over their own darker
     * background between two separators, with no dirt above or below. Container, book and sign screens keep the
     * transparent gradient ({@code renderTransparentBackground}).
     */
    public static boolean menuBackgrounds(GameVersion v) {
        return v.isAtLeast("1.20.5");
    }

    /** 1.20.2 (23w31a): the Narrator Hotkey option (Accessibility); off, Ctrl + B no longer toggles the narrator. */
    public static boolean narratorHotkeyOption(GameVersion v) {
        return v.isAtLeast("1.20.2");
    }

    /** The title screen's logo layout. */
    public enum TitleLogo {
        /** 1.12.2 to 1.19: {@code minecraft.png} in two 155 by 44 pieces, "Java Edition" (98 by 14) below. */
        CLASSIC,
        /**
         * 1.20 (23w17a, {@code LogoRenderer}): {@code minecraft.png} as one 256 by 44 piece of a 256 by 64 texture at
         * width / 2 - 128, {@code edition.png} (128 by 14 of 128 by 16) at width / 2 - 64, 37 below the logo's top,
         * Minceraft from its own {@code minceraft.png}, and the splash at width / 2 + 123, 69.
         */
        LOGO_1_20;

        public static TitleLogo forVersion(GameVersion v) {
            return v.isAtLeast("1.20") ? LOGO_1_20 : CLASSIC;
        }
    }

    /** @return the color a button's label is drawn in, given 1.12.2's ({@code GuiButton.drawButton}) */
    public static int buttonTextColor(int vanilla) {
        if ((vanilla == 0xE0E0E0 || vanilla == 0xFFFFA0) && whiteButtonText()) {
            return 0xFFFFFF;
        }
        return vanilla;
    }
}

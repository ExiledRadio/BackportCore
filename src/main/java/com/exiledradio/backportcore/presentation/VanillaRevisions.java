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
     * 26.2: the pause menu's Report Bugs, Give Feedback, Friends and Player Reporting are 20 by 20 icon buttons in one
     * row under Advancements and Statistics (sprites {@code pause_menu/bug}, {@code pause_menu/social_interactions},
     * {@code friends/friends} and {@code pause_menu/player_reporting}, supplied by a backport); 26.3's row is the same.
     */
    public static boolean pauseIconRow() {
        return Presentation.isAtLeast(LookArea.MENUS, "26.2");
    }

    /**
     * 26.2: the title screen's Friends, Language and Accessibility are one centered row of 20 by 20 icon buttons
     * between Realms and Options/Quit (sprites {@code friends/friends}, {@code icon/language} and
     * {@code icon/accessibility}, supplied by a backport); 26.1 still had Language left of Options and Accessibility
     * right of Quit. Forge's half-width Mods button keeps its place beside Realms.
     */
    public static boolean titleIconRow() {
        return Presentation.isAtLeast(LookArea.TITLE, "26.2");
    }

    /** Set by the mod that brings 26.1's babies (Game Drops Backport's {@code babies.babyModels}); on by default. */
    private static volatile java.util.function.BooleanSupplier babyModelSwitch = () -> true;

    /**
     * 26.1: babies have their own models and {@code *_baby} textures (26.3's {@code Baby*Model} classes), not the
     * adult's model shrunk. A backport draws its own mobs' babies so while this is true: 26.1 or later installed
     * (Game Drops Backport registers it), Core's textures switch on, and the 26.1 mod's own switch
     * ({@link #setBabyModelSwitch}) on. Read it each frame; it can change in game. Since Core 1.4.4.
     */
    public static boolean babyModels() {
        return Presentation.isAtLeast(LookArea.TEXTURES, "26.1") && babyModelSwitch.getAsBoolean();
    }

    /** For the mod that brings 26.1's babies: its own switch for {@link #babyModels()}, read each time. */
    public static void setBabyModelSwitch(java.util.function.BooleanSupplier on) {
        babyModelSwitch = on == null ? () -> true : on;
    }

    /** 26.3: an options list's section headers are bold, underlined and centered ({@code OptionsList.addHeader}). */
    public static boolean centeredOptionHeaders() {
        return Presentation.isAtLeast(LookArea.MENUS, "26.3");
    }

    /** 26.1: Video Settings' Exclusive Fullscreen; off, Fullscreen is a borderless window. */
    public static boolean exclusiveFullscreenOption() {
        return Presentation.isAtLeast(LookArea.VIDEO, "26.1");
    }

    /**
     * 1.21.2's clouds ({@code presentation.client.ModernClouds}): a box per cell with only the faces that can be seen,
     * in place of 1.12.2's textured slabs and their inner walls. Since Core 1.4.5.
     */
    public static boolean modernClouds() {
        return Presentation.isAtLeast(LookArea.SKY, "1.21.2");
    }

    /**
     * 26.1's lightmap ({@code presentation.LightmapColors}, 26.3's {@code lightmap.fsh}): block light tinted warm yellow
     * that turns white toward full brightness, sky light that turns blue at night, and each dimension's ambient light
     * added under both, in place of 1.12.2's formula. Read each time the lightmap updates. Since Core 1.4.5.
     */
    public static boolean modernLightmap() {
        return Presentation.isAtLeast(LookArea.SKY, "26.1");
    }

    /**
     * 26.3 (its window through SDL3, 26.2's through GLFW): on Windows the title bar takes Windows' dark mode when the
     * app mode is dark. Since Core 1.4.5.
     */
    public static boolean darkTitleBar() {
        return Presentation.isAtLeast(LookArea.VIDEO, "26.3");
    }

    /** 26.3: Exclusive Fullscreen is off by default, so Fullscreen is borderless unless the player turns it on. */
    public static boolean borderlessByDefault() {
        return Presentation.isAtLeast(LookArea.VIDEO, "26.3");
    }

    /**
     * Whether this game can make the borderless window: LWJGL 2's {@code Display}, which reads
     * {@code org.lwjgl.opengl.Window.undecorated} each time it makes the window. Cleanroom's LWJGL 3 (lwjglx) draws the
     * window through GLFW, where that property means nothing; there Core uses lwjglx's own
     * {@code Display.setBorderless} (a window over the monitor, no mode change) when it has one, and otherwise steps
     * aside and Fullscreen stays the launcher's. False on a dedicated server.
     */
    public static boolean borderlessPossible() {
        ClassLoader l = VanillaRevisions.class.getClassLoader();
        if (lwjglxBorderless() != null) {
            return true;
        }
        return l.getResource("org/lwjgl/opengl/WindowsDisplay.class") != null || l.getResource("org/lwjgl/opengl/LinuxDisplay.class") != null
                || l.getResource("org/lwjgl/opengl/MacOSXDisplay.class") != null
                ? l.getResource("org/lwjgl/glfw/GLFW.class") == null : false;
    }

    /** Cleanroom's lwjglx {@code Display.setBorderless(boolean)}, or null where there is none. Since Core 1.4.5. */
    @javax.annotation.Nullable
    public static java.lang.reflect.Method lwjglxBorderless() {
        if (lwjglxBorderless == null) {
            java.lang.reflect.Method m = null;
            try {
                // Cleanroom serves lwjglx's Display under LWJGL 2's own name (its LWJGLTransformer), and that class holds
                // the game's window; "org.lwjglx.opengl.Display" by name would be a second, empty copy.
                m = Class.forName("org.lwjgl.opengl.Display", false, VanillaRevisions.class.getClassLoader())
                        .getMethod("setBorderless", boolean.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                // LWJGL 2, an lwjglx without it, or a dedicated server.
            }
            lwjglxBorderless = java.util.Optional.ofNullable(m);
        }
        return lwjglxBorderless.orElse(null);
    }

    private static java.util.Optional<java.lang.reflect.Method> lwjglxBorderless;

    /**
     * Whether 1.12.2's own world renderer draws the chunks: not where OptiFine, Celeritas, Vintagium or Nothirium replace
     * it (each has its own chunk builder and draws its own translucent layer). Chunk Builder and Improved Transparency
     * change that renderer, so they show only here. False on a dedicated server. Since Core 1.4.5.
     */
    public static boolean vanillaRenderer() {
        if (!net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide().isClient()) {
            return false;
        }
        if (vanillaRenderer == null) {
            net.minecraftforge.fml.common.Loader loader = net.minecraftforge.fml.common.Loader.instance();
            vanillaRenderer = !net.minecraftforge.fml.client.FMLClientHandler.instance().hasOptifine() && !loader.isModLoaded("celeritas")
                    && !loader.isModLoaded("vintagium") && !loader.isModLoaded("nothirium");
        }
        return vanillaRenderer;
    }

    private static Boolean vanillaRenderer;

    /** 26.3: Fullscreen Resolution is called Exclusive Fullscreen Mode, with a tooltip. */
    public static boolean exclusiveFullscreenMode() {
        return Presentation.isAtLeast(LookArea.VIDEO, "26.3");
    }

    /** 26.3: the debug overlay's own GUI Scale (Debug Options), Auto by default: half the largest scale the window fits. */
    public static boolean debugGuiScale() {
        return Presentation.isAtLeast(LookArea.DEBUG, "26.3");
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

    // ---- Since Core 1.4.5: 26.3's menus (docs/SCREEN_AUDIT.md sections 1 to 4) ----

    /**
     * 1.20.5 ({@code HeaderAndFooterLayout} on the options screens; 1.20.1's Options and sub-screens still placed their
     * buttons from {@code height / 6} with the title at 15 or 20, 1.20.6's use the layout): a 33-high header with the
     * title centered in it, a 33-high footer with Done, and the screen's list or grid between. Edit World became one
     * centered column in the same release. See {@code client.HeaderFooterLayout}.
     */
    public static boolean headerAndFooter(GameVersion v) {
        return v.isAtLeast("1.20.5");
    }

    /** 1.18 (21w37a): Options' "Online..." in Realms Notifications' place, opening Online Options. */
    public static boolean onlineOptions(GameVersion v) {
        return v.isAtLeast("1.18");
    }

    /**
     * 1.19.3 (not in 1.19's lang, in 1.19.4's): Options' "Telemetry Data...". Until 1.20.4 it opened the Telemetry Data
     * Collection screen, Mojang's data collection, which 1.12.2 does not have; Core shows it greyed out.
     */
    public static boolean telemetryButton(GameVersion v) {
        return v.isAtLeast("1.19.3");
    }

    /**
     * 1.20.5 (not in 1.20.4's lang, in 1.20.6's): with telemetry off, Telemetry Data is greyed out with the tooltip
     * "Telemetry is disabled.", which is what a game without Mojang's telemetry shows.
     */
    public static boolean telemetryDisabledTooltip(GameVersion v) {
        return v.isAtLeast("1.20.5");
    }

    /**
     * 1.19.4 (23w03a): the Credits &amp; Attribution screen (Credits, Attribution, Licenses), from Options and from the
     * title screen's copyright text, which became a {@code PlainTextButton} (underlined while hovered).
     */
    public static boolean creditsAndAttribution(GameVersion v) {
        return v.isAtLeast("1.19.4");
    }

    /** The multiplayer warning's layout. */
    public enum WarningLayout {
        /** 1.15.2 to 1.20.4: the title at 25, 30, the message from y 70, 18 a line, the check box and buttons below. */
        CLASSIC,
        /**
         * 1.20.5 to 1.21.8: one centered column, 8 apart ({@code FrameLayout.centerInRectangle}): the title, the message
         * centered in {@code width - 100} ({@code FocusableTextWidget}, 1.21.8's last), the check box, the buttons.
         */
        COLUMN_1_20_5,
        /**
         * 1.21.9 (26.3's): the same column with the message in a {@code FittingMultiLineTextWidget}: the
         * {@code widget/text_field} frame, {@code width - 100} wide, shrunk to the text, 4 inside, single-spaced,
         * scrolling only when taller than {@code height - 100}.
         */
        BOXED_1_21_9;

        public static WarningLayout forVersion(GameVersion v) {
            return v.isAtLeast("1.21.9") ? BOXED_1_21_9 : v.isAtLeast("1.20.5") ? COLUMN_1_20_5 : CLASSIC;
        }
    }

    /**
     * 26.2: Touchscreen is gone from Mouse Settings ({@code Options.touchscreen} left the code; 26.1 still has it, and
     * the lang key stays behind in 26.3's file).
     */
    public static boolean touchscreenRemoved(GameVersion v) {
        return v.isAtLeast("26.2");
    }

    /**
     * 1.19.4 (23w03a, {@code AccessibilityOnboardingScreen}): the very first start opens a welcome screen offering the
     * Narrator and the accessibility and language settings, before the title screen.
     */
    public static boolean accessibilityOnboarding(GameVersion v) {
        return v.isAtLeast("1.19.4");
    }

    /**
     * 26.3's order of Accessibility Settings ({@code AccessibilityOptionsScreen.options}) by name, used from 1.21.9 (its
     * Controls... button beside Narrator): vanilla's settings and the options named after them, whichever mod registers
     * them; others follow.
     */
    public static final String[] ACCESSIBILITY_ORDER = {"NARRATOR", "SHOW_SUBTITLES", "high_contrast", "menu_background_blurriness",
            "text_background_opacity", "text_background", "CHAT_OPACITY", "chat_line_spacing", "chat_delay", "notification_display_time",
            "VIEW_BOBBING", "screen_effect_scale", "fov_effect_scale", "darkness_effect_scale", "damage_tilt_strength", "glint_speed",
            "glint_strength", "hide_lightning_flashes", "monochrome_logo", "panorama_speed", "hide_splash_texts", "narrator_hotkey",
            "rotate_with_minecart", "high_contrast_block_outline"};

    /** 26.3's macOS-only rows (Right Click Emulation): {@code InputQuirks.EMULATE_RIGHT_CLICK_WITH_CTRL_KEY}. */
    public static boolean macOs() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
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

    // ---- Since Core 1.4.5: the confirm and message screens, the widgets' 26.3 looks and the credits ----

    /** The layout of a yes/no question ({@code ConfirmScreen}; 1.12.2's {@code GuiYesNo}). */
    public enum ConfirmLayout {
        /** 1.12.2 to 1.16.5: the title at 70, the message from 90, the buttons at {@code height / 6 + 96}. */
        CLASSIC,
        /** 1.17 (21w?): the buttons follow the message, {@code clamp(90 + message + 12, height / 6 + 96, height - 24)}. */
        BUTTONS_BELOW_1_17,
        /**
         * 1.19.1: title and message centered on the screen, the title at {@code clamp((height - message) / 2 - 29, 10, 80)},
         * the message 20 below it, the buttons {@code clamp(message end + 20, height / 6 + 96, height - 24)}.
         */
        CENTERED_1_19_1,
        /**
         * 1.21.6 ({@code LinearLayout.vertical().spacing(8)}, centered on the screen): the title, the message (to
         * {@code width - 50}, 15 rows at most), then the two 150-wide buttons 4 apart, 16 further down.
         */
        COLUMN_1_21_6;

        public static ConfirmLayout forVersion(GameVersion v) {
            return v.isAtLeast("1.21.6") ? COLUMN_1_21_6 : v.isAtLeast("1.19.1") ? CENTERED_1_19_1
                    : v.isAtLeast("1.17") ? BUTTONS_BELOW_1_17 : CLASSIC;
        }
    }

    /** @return the yes/no question layout at the newest installed version */
    public static ConfirmLayout confirmLayout() {
        return ConfirmLayout.forVersion(Presentation.newest(LookArea.MENUS));
    }

    /** One line of text alone on a screen while the game works ({@code GenericMessageScreen}): "Saving world". */
    public enum MessageScreen {
        /** 1.12.2: none. Leaving a world shows the loading screen's dirt with Forge's "Shutting down internal server...". */
        NONE,
        /** 1.13 ({@code GuiDirtMessageScreen}): "Saving world" over the dirt, centered at 70. */
        DIRT_1_13,
        /**
         * 1.20.5 (24w09a, {@code GenericMessageScreen}): the line centered on the screen (its top at
         * {@code height / 2 - 4}) in a black box 12 larger on every side with a grey ({@code 0xA0A0A0}) frame.
         */
        BOX_1_20_5,
        /**
         * 1.21.11 ({@code FocusableTextWidget.builder}): the box itself has its top at {@code height / 2 - 4}, its text
         * 12 inside it, so the line sits 12 lower than 1.20.5's.
         */
        BOX_1_21_11;

        public static MessageScreen forVersion(GameVersion v) {
            return v.isAtLeast("1.21.11") ? BOX_1_21_11 : v.isAtLeast("1.20.5") ? BOX_1_20_5 : v.isAtLeast("1.13") ? DIRT_1_13 : NONE;
        }
    }

    /** @return the message screen at the newest installed version */
    public static MessageScreen messageScreen() {
        return MessageScreen.forVersion(Presentation.newest(LookArea.MENUS));
    }

    /**
     * 1.14 ({@code ProgressScreen}): a working screen's second line ("Working... 0%") shows only once it has a stage and
     * some progress; 1.12.2's {@code GuiScreenWorking} draws " 0%" while it waits.
     */
    public static boolean progressLineWhenStarted() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.14");
    }

    /** The disconnected screen's layout ({@code DisconnectedScreen}). */
    public enum DisconnectedLayout {
        /** 1.12.2 to 1.19.4: the reason in grey above the middle, the message, a 200-wide button below it. */
        CLASSIC,
        /**
         * 1.20 ({@code GridLayout}, each cell padded 10, centered on the screen): the reason in white, the message
         * (to {@code width - 50}), a 150-wide button.
         */
        GRID_1_20,
        /** 1.21 ({@code LinearLayout}): as 1.20, the button 200 wide and padded 2. */
        LINEAR_1_21;

        public static DisconnectedLayout forVersion(GameVersion v) {
            return v.isAtLeast("1.21") ? LINEAR_1_21 : v.isAtLeast("1.20") ? GRID_1_20 : CLASSIC;
        }
    }

    /** @return the disconnected screen's layout at the newest installed version */
    public static DisconnectedLayout disconnectedLayout() {
        return DisconnectedLayout.forVersion(Presentation.newest(LookArea.MENUS));
    }

    /**
     * 1.20.5 (24w09a, {@code OutOfMemoryScreen} on {@code HeaderAndFooterLayout}): the title in a 33-high header, the
     * message in a framed box 300 wide in the middle, Back to Title Screen and Quit Game in the footer.
     */
    public static boolean outOfMemoryLayout() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.20.5");
    }

    /** 1.14 ({@code AbstractSliderButton}): a slider's handle takes the hovered button's look while the mouse is on it. */
    public static boolean sliderHandleHover() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.14");
    }

    /** 1.16 ({@code EditBox.renderButton}): a focused text field's frame is white (1.12.2's stays grey). */
    public static boolean textFieldFocusFrame() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.16");
    }

    /**
     * 1.20.2 (23w31a, {@code Checkbox}): the check box is drawn from the {@code widget/checkbox} sprites (supplied by a
     * backport), 17 square ({@code font height + 8}), its label 4 to the right, white-framed once clicked (focused).
     */
    public static boolean checkboxSprites() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.20.2");
    }

    /** 1.17 ({@code WinScreen}): the credits are read from {@code texts/credits.json} (sections, disciplines, titles). */
    public static boolean jsonCredits() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.17");
    }

    // ---- Since Core 1.4.5: inventories, the recipe book, F3 + 2, the debug screen's newer parts ----

    /**
     * 1.18 (21w37a, {@code EffectRenderingInventoryScreen}): the active effects in a column right of the inventory
     * ({@code leftPos + imageWidth + 2}), 120 wide with names when 120 fit, 32 wide (icon only, the name and time
     * in a tooltip) when only 32 fit, none when less; the inventory stays centered (1.12.2 moved it right and drew the
     * effects left of it). 1.17.1 still had 1.12.2's.
     */
    public static boolean effectsBesideInventory() {
        return Presentation.isAtLeast(LookArea.INVENTORY, "1.18");
    }

    /**
     * 1.21.11 ({@code EffectsInInventory}): each effect's background is as wide as its name or time (a nine-slice
     * sprite, edges 4), at most the room left less 7, names cut to fit with the tooltip then, and ambient effects on
     * their own background. 1.21.10 still had 1.18's two widths.
     */
    public static boolean effectsFitText() {
        return Presentation.isAtLeast(LookArea.INVENTORY, "1.21.11");
    }

    /** 1.16 ({@code RecipeBookComponent.SEARCH_HINT}): the recipe book's empty, unfocused search box shows "Search..." in gray italics. */
    public static boolean recipeSearchHint() {
        return Presentation.isAtLeast(LookArea.INVENTORY, "1.16");
    }

    /** 1.20.5: the recipe book's search box one pixel higher ({@code yo + 13}, 1.12.2's {@code j + 14}). */
    public static boolean recipeSearchBoxRaised() {
        return Presentation.isAtLeast(LookArea.INVENTORY, "1.20.5");
    }

    /**
     * 1.21.2 ({@code RecipeBookComponent.magnifierIconPlacement}, with the recipe book rework; in 1.21.4's code, not in
     * 1.20.6's): a click on the magnifier left of the recipe book's search box focuses the box.
     */
    public static boolean recipeMagnifier() {
        return Presentation.isAtLeast(LookArea.INVENTORY, "1.21.2");
    }

    /**
     * 1.20.2 (23w31a, {@code FpsDebugChart}, {@code TpsDebugChart}): F3 + 2 shows the frame time chart bottom left and,
     * on this game's own server, the tick time chart bottom right (1.12.2's lagometer is gone), and the profiler pie
     * (F3 + 1) is drawn at the GUI scale above them ({@code ProfilerPieChart}, radius 105).
     */
    public static boolean debugFrameCharts() {
        return Presentation.isAtLeast(LookArea.DEBUG, "1.20.2");
    }

    /**
     * 26.2 ({@code SystemToast.recalculateWidth}): a system toast splits its title as well as its message at 200 pixels
     * and is {@code max(160, widest line) + 30} wide, again whenever its text changes; 26.1 and before had one line
     * each, {@code max(160, 30 + widest)} wide from the first text.
     */
    public static boolean systemToastRewrap() {
        return Presentation.isAtLeast(LookArea.TOASTS, "26.2");
    }

    /** @return the color a button's label is drawn in, given 1.12.2's ({@code GuiButton.drawButton}) */
    public static int buttonTextColor(int vanilla) {
        if ((vanilla == 0xE0E0E0 || vanilla == 0xFFFFA0) && whiteButtonText()) {
            return 0xFFFFFF;
        }
        return vanilla;
    }

    // ---- Since Core 1.4.5: the tabbed Create New World screen ----

    /**
     * 1.19.4 (23w05a, {@code CreateWorldScreen}'s {@code GameTab}, {@code WorldTab} and {@code MoreTab}): Create New
     * World is one screen of three tabs (Game: name, Game Mode, Difficulty, Allow Cheats; World: World Type, Customize,
     * seed, Generate Structures, Bonus Chest; More: Game Rules), with Create New World and Cancel below; 1.12.2's main
     * page and More World Options page are gone. Difficulty was on the Game tab from the first snapshot. Until 1.20.5
     * the screen is light dirt under a black tab bar (36 high footer); from 1.20.5 ({@link #menuBackgrounds}) the
     * blurred panorama, a clear tab header, 33 high footer. 26.2's {@code MenuTabBar} draws as 26.1's tabs did.
     */
    public static boolean createWorldTabs() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.19.4");
    }

    /** 1.20.5 (24w04a): "Allow Cheats" is "Allow Commands" ({@code selectWorld.allowCommands.new}). */
    public static boolean allowCommandsWording() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.20.5");
    }

    /**
     * 1.21.11 (25w42a, {@code TabButton.renderWidget}): the selected tab's label is drawn over its menu background;
     * before, the background was drawn over the label.
     */
    public static boolean tabLabelOverBackground() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.21.11");
    }
}

package com.exiledradio.backportcore.presentation;

import com.exiledradio.backportcore.presentation.option.CycleOption;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.PresentationOption;
import com.exiledradio.backportcore.presentation.option.SliderOption;
import com.exiledradio.backportcore.presentation.option.ToggleOption;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The look of the game follows the newest installed backport, as vanilla's would at that version: the
 * registry each backport adds its version's non-content changes to.
 *
 * <p>A backport calls {@link #version(String)} once with the release it brings, from pre-init or init, and adds
 * its pieces to the {@link Contribution} that returns. Core owns every single-slot thing (the panorama, the
 * world-loading screen, the options screens, the splash list, the menu music, the F3 keys and the fog), so
 * installed backports never fight over them, and builds each from what all installed versions contribute.
 * {@code docs/PRESENTATION.md} says what to call.
 *
 * <p>With no backport installed, the newest version is {@link GameVersion#BASE} (1.12.2) and the game looks
 * exactly as 1.12.2 does.
 */
public final class Presentation {

    static final Logger LOG = LogManager.getLogger("Backport Core");

    private static final TreeMap<GameVersion, Contribution> VERSIONS = new TreeMap<GameVersion, Contribution>();
    private static final Map<String, PresentationOption> OPTIONS = new LinkedHashMap<String, PresentationOption>();

    /** 1.19.3's Panorama Scroll Speed (Accessibility Settings), 0 to 100%. Core's, since Core owns the panorama. */
    public static final SliderOption PANORAMA_SPEED = new SliderOption("backportcore:panorama_speed",
            "options.accessibility.panorama_speed", OptionScreen.ACCESSIBILITY, 0.0, 1.0, 0.01, 1.0);
    /** 1.20.3's Hide Splash Texts (Accessibility Settings). Core's, since Core owns the splash list. */
    public static final ToggleOption HIDE_SPLASH_TEXTS = new ToggleOption("backportcore:hide_splash_texts",
            "options.hideSplashTexts", OptionScreen.ACCESSIBILITY, false);
    /**
     * 1.20.2's Narrator Hotkey (Accessibility Settings, last): off, Ctrl + B no longer toggles the narrator (patch P32).
     * Core's, since only a patch reaches that key.
     */
    public static final ToggleOption NARRATOR_HOTKEY = new ToggleOption("backportcore:narrator_hotkey",
            "options.accessibility.narrator_hotkey", OptionScreen.ACCESSIBILITY, true);

    /** 1.14's Text Background Opacity (Accessibility, and Chat Settings): the chat's background apart from its text. */
    public static final SliderOption TEXT_BACKGROUND_OPACITY = new SliderOption("backportcore:text_background_opacity",
            "options.accessibility.text_background_opacity", OptionScreen.ACCESSIBILITY, 0.0, 1.0, 0.0, 0.5);
    /** 1.14's Text Background (Accessibility): Chat Only or Everywhere, where that opacity applies. */
    public static final CycleOption TEXT_BACKGROUND = new CycleOption("backportcore:text_background",
            "options.accessibility.text_background", OptionScreen.ACCESSIBILITY, new String[] {"chat", "everywhere"},
            new String[] {"options.accessibility.text_background.chat", "options.accessibility.text_background.everywhere"}, "chat");
    /** 1.16's Line Spacing (Chat Settings and Accessibility), 0 to 100%. */
    public static final SliderOption CHAT_LINE_SPACING = new SliderOption("backportcore:chat_line_spacing",
            "options.chat.line_spacing", OptionScreen.CHAT, 0.0, 1.0, 0.0, 0.0);
    /** 1.16's Chat Delay (Chat Settings and Accessibility), 0 to 6 seconds in tenths. */
    public static final SliderOption CHAT_DELAY = new SliderOption("backportcore:chat_delay",
            "options.chat.delay_none", OptionScreen.CHAT, 0.0, 6.0, 0.1, 0.0);
    /** 1.17's Monochrome Logo (Accessibility): the startup screen in black instead of red. */
    public static final ToggleOption MONOCHROME_LOGO = new ToggleOption("backportcore:monochrome_logo",
            "options.darkMojangStudiosBackgroundColor", OptionScreen.ACCESSIBILITY, false);

    /** 1.21.6's Music Frequency (Music and Sounds): how long the in-game music waits between tracks. */
    public static final CycleOption MUSIC_FREQUENCY = new CycleOption("backportcore:music_frequency", "options.music_frequency",
            OptionScreen.SOUNDS, new String[] {"default", "frequent", "constant"}, new String[] {"options.music_frequency.default",
            "options.music_frequency.frequent", "options.music_frequency.constant"}, "default");
    /** 1.21.6's Music Toast (Music and Sounds; 1.21.11's three values): the playing track's name. */
    public static final CycleOption MUSIC_TOAST = new CycleOption("backportcore:music_toast", "options.musicToast",
            OptionScreen.SOUNDS, new String[] {"never", "pause_menu", "pause_menu_and_toast"}, new String[] {"options.musicToast.never",
            "options.musicToast.pauseMenu", "options.musicToast.pauseMenuAndToast"}, "never");
    /** 1.21.6's UI volume (Music and Sounds): button clicks and other interface sounds, 0 to 100%. */
    public static final SliderOption UI_VOLUME = new SliderOption("backportcore:ui_volume", "soundCategory.ui", OptionScreen.SOUNDS,
            0.0, 1.0, 0.0, 1.0);

    /** 1.21.9's Attack/Destroy (Controls): Hold or Toggle. */
    public static final CycleOption TOGGLE_ATTACK = new CycleOption("backportcore:toggle_attack", "key.attack", OptionScreen.CONTROLS,
            new String[] {"hold", "toggle"}, new String[] {"options.key.hold", "options.key.toggle"}, "hold");
    /** 1.21.9's Use Item/Place Block (Controls): Hold or Toggle. */
    public static final CycleOption TOGGLE_USE = new CycleOption("backportcore:toggle_use", "key.use", OptionScreen.CONTROLS,
            new String[] {"hold", "toggle"}, new String[] {"options.key.hold", "options.key.toggle"}, "hold");

    // ---- 1.21.6 to 1.21.11's Video Settings (Core owns the screen, the clouds and the rendering they change) ----

    /** 1.21.11's Preset (Quality &amp; Performance): Fast, Fancy and Fabulous! set the section's settings; Custom once changed. */
    public static final CycleOption GRAPHICS_PRESET = new CycleOption("backportcore:graphics_preset", "options.graphics.preset",
            OptionScreen.VIDEO, new String[] {"fast", "fancy", "fabulous", "custom"}, new String[] {"options.graphics.fast",
            "options.graphics.fancy", "options.graphics.fabulous", "options.graphics.custom"}, "fancy");
    /** 1.21.6's Cloud Distance, 2 to 128 chunks. */
    public static final SliderOption CLOUD_RANGE = new SliderOption("backportcore:cloud_range", "options.renderCloudsDistance",
            OptionScreen.VIDEO, 2.0, 128.0, 1.0, 128.0);
    /** 1.21.11's See-Through Leaves: 1.12.2's Fancy graphics, which is what draws leaves see-through. */
    public static final ToggleOption CUTOUT_LEAVES = new ToggleOption("backportcore:cutout_leaves", "options.cutoutLeaves",
            OptionScreen.VIDEO, true);
    /** 1.21.11's Weather Effect Radius, 3 to 10 blocks (1.12.2: 10 with Fancy graphics, 5 with Fast). */
    public static final SliderOption WEATHER_RADIUS = new SliderOption("backportcore:weather_radius", "options.weatherRadius",
            OptionScreen.VIDEO, 3.0, 10.0, 1.0, 10.0);
    /** 1.21.11's Texture Filtering: None or Anisotropic (RGSS is a shader of 1.21.11's terrain, which 1.12.2 cannot run). */
    public static final CycleOption TEXTURE_FILTERING = new CycleOption("backportcore:texture_filtering", "options.textureFiltering",
            OptionScreen.VIDEO, new String[] {"none", "anisotropic"}, new String[] {"options.textureFiltering.none",
            "options.textureFiltering.anisotropic"}, "none");
    /** 1.21.11's Anisotropic Filtering, 2x, 4x or 8x (saved as the power of two, as vanilla's {@code maxAnisotropyBit}). */
    public static final SliderOption MAX_ANISOTROPY = new SliderOption("backportcore:max_anisotropy", "options.maxAnisotropy",
            OptionScreen.VIDEO, 1.0, 3.0, 1.0, 2.0);
    /** 1.21.11's Show Vignette (Preferences; 1.12.2 draws it only with Fancy graphics). */
    public static final ToggleOption VIGNETTE = new ToggleOption("backportcore:vignette", "options.vignette", OptionScreen.VIDEO, true);
    /** 1.21.11's Chunk Fade Time, 0 to 2 seconds in twentieths: newly drawn chunks fade in from the fog color. */
    public static final SliderOption CHUNK_FADE = new SliderOption("backportcore:chunk_fade", "options.chunkFade", OptionScreen.VIDEO,
            0.0, 2.0, 0.05, 0.75);

    /**
     * 26.1's Exclusive Fullscreen (Display, after Fullscreen): on, Fullscreen changes the monitor's mode as 1.12.2's does;
     * off, it is a borderless window the size of the monitor. Off by default from 26.3, on in 26.1 and 26.2: the default
     * follows the newest version until the player sets it. Shown only where Core can make the borderless window (LWJGL
     * 2's display; not Cleanroom's LWJGL 3). Since Core 1.4.4.
     */
    public static final ToggleOption EXCLUSIVE_FULLSCREEN = new ToggleOption("backportcore:exclusive_fullscreen",
            "options.exclusiveFullscreen", OptionScreen.VIDEO, true);

    /**
     * 1.13's Fullscreen Resolution, 26.3's Exclusive Fullscreen Mode (Display, a 310-wide slider at the top): Current
     * (the monitor's own mode) or one of the monitor's modes, used when Fullscreen changes the monitor's mode. Saved as
     * vanilla's {@code fullscreenResolution} ("1920x1080@60:24"; empty for Current). It has no button of its own in
     * the option list: Video Settings draws it ({@code FullscreenModes}). Only where 1.12.2's LWJGL 2 draws the
     * window, as Exclusive Fullscreen. Since Core 1.4.5.
     */
    public static final com.exiledradio.backportcore.presentation.option.TextOption FULLSCREEN_RESOLUTION =
            new com.exiledradio.backportcore.presentation.option.TextOption("backportcore:fullscreen_resolution", "options.fullscreen.resolution",
                    OptionScreen.VIDEO, "");

    /**
     * 1.18's Simulation Distance (Quality &amp; Performance), 5 to 32 chunks: entities, block entities, block ticks and
     * random ticks run only this far from a player (patch P46, {@code SimulationDistance}). The game's own world (and a
     * LAN world it opens) takes it from here, as 1.18's integrated server does; a dedicated server takes
     * {@code simulation-distance} from server.properties. Since Core 1.4.5.
     */
    public static final SliderOption SIMULATION_DISTANCE = new SliderOption("backportcore:simulation_distance", "options.simulationDistance",
            OptionScreen.VIDEO, 5.0, 32.0, 1.0, 12.0);
    /**
     * 1.18.2's Chunk Builder (Quality &amp; Performance): which changed chunks are rebuilt at once on the render thread
     * (patch P17). The same setting as Core's config {@code rendering.blockUpdates}: changing one changes the other.
     * Not shown where another renderer replaces 1.12.2's (OptiFine, Celeritas, Vintagium, Nothirium). Since Core 1.4.5.
     */
    public static final CycleOption PRIORITIZE_CHUNK_UPDATES = new CycleOption("backportcore:prioritize_chunk_updates",
            "options.prioritizeChunkUpdates", OptionScreen.VIDEO, new String[] {"none", "player_affected", "nearby"},
            new String[] {"options.prioritizeChunkUpdates.none", "options.prioritizeChunkUpdates.byPlayer", "options.prioritizeChunkUpdates.nearby"},
            "player_affected");
    /**
     * 1.21.11's Improved Transparency (Quality &amp; Performance; Fabulous! graphics before it): rain and snow in front of
     * water and translucent blocks are drawn over them instead of under them ({@code ImprovedTransparency};
     * {@code docs/PRESENTATION.md} says what 1.12.2's pipeline allows). Not shown where another renderer replaces
     * 1.12.2's. Since Core 1.4.5.
     */
    public static final ToggleOption IMPROVED_TRANSPARENCY = new ToggleOption("backportcore:improved_transparency",
            "options.improvedTransparency", OptionScreen.VIDEO, false);

    static {
        // The parts of Core's own options that are not their screen's (LookArea): the rest follow their screen.
        PANORAMA_SPEED.area(LookArea.TITLE);
        HIDE_SPLASH_TEXTS.area(LookArea.SPLASHES);
        TEXT_BACKGROUND_OPACITY.area(LookArea.CHAT);
        TEXT_BACKGROUND.area(LookArea.CHAT);
        MONOCHROME_LOGO.area(LookArea.STARTUP);
        MUSIC_TOAST.area(LookArea.TOASTS);
    }

    static {
        GRAPHICS_PRESET.since("1.21.11");
        GRAPHICS_PRESET.tooltip("options.graphics.preset.tooltip");
        CLOUD_RANGE.since("1.21.6");
        CLOUD_RANGE.after("RENDER_CLOUDS");
        CLOUD_RANGE.decimals(0).valueKey("options.chunks");
        CUTOUT_LEAVES.since("1.21.11");
        CUTOUT_LEAVES.tooltip("options.cutoutLeaves.tooltip");
        WEATHER_RADIUS.since("1.21.11");
        WEATHER_RADIUS.tooltip("options.weatherRadius.tooltip");
        WEATHER_RADIUS.decimals(0).valueKey("options.blocks");
        TEXTURE_FILTERING.since("1.21.11");
        TEXTURE_FILTERING.valueTooltips("options.textureFiltering.none.tooltip", "options.textureFiltering.anisotropic.tooltip");
        MAX_ANISOTROPY.since("1.21.11");
        MAX_ANISOTROPY.tooltip("options.maxAnisotropy.tooltip");
        MAX_ANISOTROPY.valueKey("options.multiplier").valueText(v -> Integer.toString(1 << (int) Math.round(v)));
        VIGNETTE.since("1.21.11");
        VIGNETTE.tooltip("options.vignette.tooltip");
        CHUNK_FADE.since("1.21.11");
        CHUNK_FADE.tooltip("options.chunkFade.tooltip");
        CHUNK_FADE.wholeLabel("options.chunkFade.seconds", "options.chunkFade.none").decimals(2);
        EXCLUSIVE_FULLSCREEN.since("26.1");
        EXCLUSIVE_FULLSCREEN.after("USE_FULLSCREEN");
        EXCLUSIVE_FULLSCREEN.valueTooltips("options.exclusiveFullscreen.on.tooltip", "options.exclusiveFullscreen.off.tooltip")
                .defaultFollows(() -> !VanillaRevisions.borderlessByDefault());
        EXCLUSIVE_FULLSCREEN.availableWhen(VanillaRevisions::borderlessPossible);
        for (PresentationOption o : new PresentationOption[] {GRAPHICS_PRESET, CLOUD_RANGE, CUTOUT_LEAVES, WEATHER_RADIUS, TEXTURE_FILTERING,
                MAX_ANISOTROPY, VIGNETTE, CHUNK_FADE, EXCLUSIVE_FULLSCREEN}) {
            registerOption(o);
        }
        // 1.4.5: the Video Settings 26.3 has that 1.12.2 lacked entirely.
        FULLSCREEN_RESOLUTION.since("1.13");
        FULLSCREEN_RESOLUTION.availableWhen(VanillaRevisions::borderlessPossible);
        SIMULATION_DISTANCE.since("1.18");
        SIMULATION_DISTANCE.after("RENDER_DISTANCE");
        SIMULATION_DISTANCE.decimals(0).valueKey("options.chunks");
        PRIORITIZE_CHUNK_UPDATES.since("1.18.2");
        PRIORITIZE_CHUNK_UPDATES.after("RENDER_DISTANCE");
        PRIORITIZE_CHUNK_UPDATES.valueTooltips("options.prioritizeChunkUpdates.none.tooltip", "options.prioritizeChunkUpdates.byPlayer.tooltip",
                "options.prioritizeChunkUpdates.nearby.tooltip");
        PRIORITIZE_CHUNK_UPDATES.availableWhen(VanillaRevisions::vanillaRenderer);
        IMPROVED_TRANSPARENCY.since("1.21.11");
        IMPROVED_TRANSPARENCY.tooltip("options.improvedTransparency.tooltip");
        IMPROVED_TRANSPARENCY.availableWhen(VanillaRevisions::vanillaRenderer);
        for (PresentationOption o : new PresentationOption[] {SIMULATION_DISTANCE, PRIORITIZE_CHUNK_UPDATES, IMPROVED_TRANSPARENCY}) {
            registerOption(o);
        }
    }

    static {
        TOGGLE_ATTACK.since("1.21.9");
        TOGGLE_USE.since("1.21.9");
        registerOption(TOGGLE_ATTACK);
        registerOption(TOGGLE_USE);
        MUSIC_FREQUENCY.since("1.21.6");
        MUSIC_FREQUENCY.tooltip("options.music_frequency.tooltip");
        MUSIC_TOAST.since("1.21.6");
        MUSIC_TOAST.valueTooltips("options.musicToast.never.tooltip", "options.musicToast.pauseMenu.tooltip",
                "options.musicToast.pauseMenuAndToast.tooltip");
        UI_VOLUME.since("1.21.6");
        UI_VOLUME.decimals(-1);
        UI_VOLUME.zeroKey("options.off");
        registerOption(UI_VOLUME);
        registerOption(MUSIC_FREQUENCY);
        registerOption(MUSIC_TOAST);
        PANORAMA_SPEED.since("1.19.3");
        HIDE_SPLASH_TEXTS.since("1.20.3");
        registerOption(PANORAMA_SPEED);
        registerOption(HIDE_SPLASH_TEXTS);
        NARRATOR_HOTKEY.since("1.20.2");
        NARRATOR_HOTKEY.after(HIDE_SPLASH_TEXTS.id());
        // Vanilla's Util.getOSType test (that method is client-only in 1.12.2).
        NARRATOR_HOTKEY.tooltip(System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")
                ? "options.accessibility.narrator_hotkey.mac.tooltip" : "options.accessibility.narrator_hotkey.tooltip");
        registerOption(NARRATOR_HOTKEY);
        // 1.16.5's AccessibilityOptionsScreen order: narrator, subtitles, text background opacity, text background,
        // chat opacity, line spacing, chat delay, Auto-Jump, ...
        TEXT_BACKGROUND_OPACITY.since("1.14");
        TEXT_BACKGROUND_OPACITY.after("SHOW_SUBTITLES");
        TEXT_BACKGROUND_OPACITY.alsoOn(OptionScreen.CHAT);
        TEXT_BACKGROUND.since("1.14");
        TEXT_BACKGROUND.after(TEXT_BACKGROUND_OPACITY.id());
        CHAT_LINE_SPACING.decimals(-1);
        CHAT_LINE_SPACING.since("1.16");
        CHAT_LINE_SPACING.after("CHAT_OPACITY");
        CHAT_LINE_SPACING.alsoOn(OptionScreen.ACCESSIBILITY);
        CHAT_DELAY.wholeLabel("options.chat.delay", "options.chat.delay_none").decimals(1);
        CHAT_DELAY.since("1.16");
        CHAT_DELAY.after(CHAT_LINE_SPACING.id());
        CHAT_DELAY.alsoOn(OptionScreen.ACCESSIBILITY);
        MONOCHROME_LOGO.since("1.17");
        MONOCHROME_LOGO.tooltip("options.darkMojangStudiosBackgroundColor.tooltip");
        for (PresentationOption o : new PresentationOption[] {TEXT_BACKGROUND_OPACITY, TEXT_BACKGROUND, CHAT_LINE_SPACING, CHAT_DELAY, MONOCHROME_LOGO}) {
            registerOption(o);
        }
    }

    // ---- Single slots: newest installed version that has one ----

    /** @return the source of vanilla asset {@code path} (a path under {@code assets/minecraft/}), or null for vanilla's */
    @Nullable
    public static ResourceLocation asset(String path) {
        ResourceLocation found = null;
        for (Contribution c : installed(LookArea.ofAsset(path))) {
            ResourceLocation r = c.assets.get(path);
            if (r != null) {
                found = r;
            }
        }
        return found;
    }

    /** @return every vanilla asset path an installed version replaces, with its newest source */
    public static Map<String, ResourceLocation> assets() {
        Map<String, ResourceLocation> out = new LinkedHashMap<String, ResourceLocation>();
        for (Contribution c : installed()) {
            for (Map.Entry<String, ResourceLocation> e : c.assets.entrySet()) {
                if (newest(LookArea.ofAsset(e.getKey())).isAtLeast(c.version())) {
                    out.put(e.getKey(), e.getValue());
                }
            }
        }
        return out;
    }

    /** @return every installed version's vanilla-text folders, oldest version first (later ones win) */
    public static List<ResourceLocation> vanillaTexts() {
        List<ResourceLocation> out = new ArrayList<ResourceLocation>();
        for (Contribution c : installed(LookArea.WORDING)) {
            out.addAll(c.texts);
        }
        return out;
    }

    /** @return the title screen's Accessibility icon from 1.14 on, or null when none is to show */
    @Nullable
    public static ResourceLocation titleAccessibilityIcon() {
        if (!isAtLeast(LookArea.TITLE, "1.14")) {
            return null;
        }
        ResourceLocation found = null;
        for (Contribution c : installed(LookArea.TITLE)) {
            if (c.accessibilityIcon != null) {
                found = c.accessibilityIcon;
            }
        }
        return found;
    }

    /** @return the multiplayer warning's {header, message, check} keys, or null when no installed version has it */
    @Nullable
    public static String[] multiplayerWarning() {
        String[] found = null;
        for (Contribution c : installed(LookArea.MENUS)) {
            if (c.multiplayerWarning != null) {
                found = c.multiplayerWarning;
            }
        }
        return found == null ? null : found.clone();
    }

    /**
     * @return the newest installed version's "Command Suggestions" option, or null while no installed version has
     * command suggestions (1.12.2's Tab completion stays)
     */
    @Nullable
    public static com.exiledradio.backportcore.presentation.option.ToggleOption commandSuggestions() {
        com.exiledradio.backportcore.presentation.option.ToggleOption found = null;
        for (Contribution c : installed(LookArea.CHAT)) {
            if (c.commandSuggestions != null) {
                found = c.commandSuggestions;
            }
        }
        return found;
    }

    /** @return the newest installed version's suggestion list look; 1.13's when none names one */
    public static SuggestionLook suggestionLook() {
        SuggestionLook found = SuggestionLook.V1_13;
        for (Contribution c : installed(LookArea.CHAT)) {
            if (c.suggestionLook != null) {
                found = c.suggestionLook;
            }
        }
        return found;
    }

    /** @return the newest installed version's creative order file, or null */
    @Nullable
    public static ResourceLocation creativeOrder() {
        ResourceLocation found = null;
        for (Contribution c : installed(LookArea.INVENTORY)) {
            if (c.creativeOrder != null) {
                found = c.creativeOrder;
            }
        }
        return found;
    }

    private Presentation() {
    }

    /**
     * Declares that {@code version} is installed and returns what it contributes, for the caller to fill in.
     * Calling it again for the same version returns the same contribution (two mods may share a version;
     * the later one's single-slot pieces replace the earlier one's).
     *
     * @param version the vanilla release the backport brings ("1.14", "1.16"); later point releases are written
     *                out when a piece belongs to one ("1.19.3")
     */
    public static synchronized Contribution version(String version) {
        GameVersion v = GameVersion.parse(version);
        if (!v.isAtLeast("1.13")) {
            throw new IllegalArgumentException("a backport brings 1.13 or later, not " + version);
        }
        Contribution c = VERSIONS.get(v);
        if (c == null) {
            c = new Contribution(v);
            VERSIONS.put(v, c);
        }
        ModContainer mod = Loader.instance().activeModContainer();
        c.addOwner(mod == null ? "unknown" : mod.getModId());
        LOG.info("Presentation: {} registered by {}", v, mod == null ? "unknown" : mod.getModId());
        return c;
    }

    /**
     * For tests and tools: a contribution that is not registered (nothing installed changes), to feed the
     * resolvers that take a list of contributions ({@link Splashes#pool}, {@link MenuMusic#pool}, ...).
     */
    public static Contribution detached(String version) {
        return new Contribution(GameVersion.parse(version));
    }

    /** @return the newest installed version, or 1.12.2 when no backport registered one, whatever the look settings */
    public static synchronized GameVersion installedNewest() {
        return VERSIONS.isEmpty() ? GameVersion.BASE : VERSIONS.lastKey();
    }

    /**
     * @return the version the game looks like: the newest installed version, stopped at Core's "Newest look" setting,
     *         or 1.12.2 while "Use the newer look and feel" is off (a dedicated server ignores both). Asked for a look;
     *         content asks {@link #installedNewest()}.
     */
    public static GameVersion newest() {
        return look(null);
    }

    /** @return the version {@code area} looks like: {@link #newest()}, or 1.12.2 while that part's switch is off */
    public static GameVersion newest(LookArea area) {
        return look(area);
    }

    /** @return true when the game looks like {@code version} or later */
    public static boolean isAtLeast(String version) {
        return newest().isAtLeast(version);
    }

    /** @return true when {@code area} looks like {@code version} or later (for a backport's own piece of that part) */
    public static boolean isAtLeast(LookArea area, String version) {
        return newest(area).isAtLeast(version);
    }

    /** @return every installed version's contribution, oldest first, whatever the look settings */
    public static synchronized List<Contribution> installed() {
        return Collections.unmodifiableList(new ArrayList<Contribution>(VERSIONS.values()));
    }

    /** @return the contributions {@code area} uses now, oldest first: those at or before {@link #newest(LookArea)} */
    public static List<Contribution> installed(LookArea area) {
        GameVersion v = newest(area);
        List<Contribution> out = new ArrayList<Contribution>();
        for (Contribution c : installed()) {
            if (v.isAtLeast(c.version())) {
                out.add(c);
            }
        }
        return out;
    }

    private static GameVersion look(@Nullable LookArea area) {
        GameVersion installed = installedNewest();
        if (net.minecraftforge.fml.common.FMLCommonHandler.instance().getSide().isServer()) {
            return installed;
        }
        com.exiledradio.backportcore.CoreConfig.LookAndFeelSettings s = com.exiledradio.backportcore.CoreConfig.lookAndFeel;
        return resolveLook(installed, s.newerLook && (area == null || area.switchOn()), s.newestLook);
    }

    /**
     * @param installed the newest installed version
     * @param on        the master switch and the part's own, both on
     * @param cap       Core's "Newest look": LATEST, or a version
     * @return the version a part looks like
     */
    public static GameVersion resolveLook(GameVersion installed, boolean on, @Nullable String cap) {
        if (!on) {
            return GameVersion.BASE;
        }
        GameVersion c = lookCap(cap);
        return c != null && installed.compareTo(c) > 0 ? c : installed;
    }

    /** @return the version Core's "Newest look" stops at, or null for LATEST (or a value that is not a version) */
    @Nullable
    public static GameVersion lookCap(@Nullable String cap) {
        if (cap == null || cap.trim().isEmpty() || "LATEST".equalsIgnoreCase(cap.trim())) {
            return null;
        }
        try {
            return GameVersion.parse(cap.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return the contribution of exactly this version, or null when it is not installed */
    @Nullable
    public static synchronized Contribution contribution(String version) {
        return VERSIONS.get(GameVersion.parse(version));
    }

    // ---- Resolution ----

    /** @return the world-loading screen to show: vanilla's at the newest version, or a newer one a version declared */
    public static LoadingRevision loadingRevision() {
        return loadingRevision(newest(LookArea.LOADING), installed(LookArea.LOADING));
    }

    /** @see #loadingRevision() */
    public static LoadingRevision loadingRevision(GameVersion newest, List<Contribution> installed) {
        LoadingRevision r = LoadingRevision.forVersion(newest);
        for (Contribution c : installed) {
            if (c.loadingRevision != null && newest.isAtLeast(c.version()) && c.loadingRevision.ordinal() > r.ordinal()) {
                r = c.loadingRevision;
            }
        }
        return r;
    }

    /** @return every installed version with a panorama, oldest first, as (version, six faces) */
    public static List<Panorama> panoramas() {
        List<Panorama> out = new ArrayList<Panorama>();
        for (Contribution c : installed(LookArea.TITLE)) {
            if (c.panorama != null) {
                out.add(new Panorama(c.version(), c.panorama.clone()));
            }
        }
        return out;
    }

    /** @return the panorama overlay of the newest installed version that has one, or null */
    @Nullable
    public static ResourceLocation panoramaOverlay() {
        ResourceLocation found = null;
        for (Contribution c : installed(LookArea.TITLE)) {
            if (c.panoramaOverlay != null) {
                found = c.panoramaOverlay;
            }
        }
        return found;
    }

    // ---- Options ----

    static synchronized void registerOption(PresentationOption option) {
        PresentationOption old = OPTIONS.put(option.id(), option);
        if (old != null && old != option) {
            LOG.warn("Presentation: option {} registered twice; the later one is used", option.id());
        }
    }

    /** @return every registered option, in registration order */
    public static synchronized List<PresentationOption> options() {
        return new ArrayList<PresentationOption>(OPTIONS.values());
    }

    /** @return the registered option with this id, or null */
    @Nullable
    public static synchronized PresentationOption option(String id) {
        return OPTIONS.get(id);
    }

    /**
     * @return the options that show on {@code screen} at the newest installed version, oldest {@code since} first,
     *         then in registration order (placement after another entry is the screen's business)
     */
    public static List<PresentationOption> optionsOn(OptionScreen screen) {
        GameVersion screens = newest(LookArea.OPTIONS);
        List<PresentationOption> out = new ArrayList<PresentationOption>();
        for (PresentationOption o : options()) {
            if (o.isShown() && o.isOn(screen, screens)) {
                out.add(o);
            }
        }
        // A stable sort keeps registration order within a version.
        Collections.sort(out, (a, b) -> {
            GameVersion x = a.since() == null ? GameVersion.BASE : a.since();
            GameVersion y = b.since() == null ? GameVersion.BASE : b.since();
            return x.compareTo(y);
        });
        return out;
    }

    /** One installed version's panorama. */
    public static final class Panorama {
        public final GameVersion version;
        public final ResourceLocation[] faces;

        public Panorama(GameVersion version, ResourceLocation[] faces) {
            this.version = version;
            this.faces = faces;
        }
    }
}

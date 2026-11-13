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

    static {
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
        for (Contribution c : installed()) {
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
            out.putAll(c.assets);
        }
        return out;
    }

    /** @return every installed version's vanilla-text folders, oldest version first (later ones win) */
    public static List<ResourceLocation> vanillaTexts() {
        List<ResourceLocation> out = new ArrayList<ResourceLocation>();
        for (Contribution c : installed()) {
            out.addAll(c.texts);
        }
        return out;
    }

    /** @return the title screen's Accessibility icon from 1.14 on, or null when none is to show */
    @Nullable
    public static ResourceLocation titleAccessibilityIcon() {
        if (!isAtLeast("1.14")) {
            return null;
        }
        ResourceLocation found = null;
        for (Contribution c : installed()) {
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
        for (Contribution c : installed()) {
            if (c.multiplayerWarning != null) {
                found = c.multiplayerWarning;
            }
        }
        return found == null ? null : found.clone();
    }

    /** @return the newest installed version's creative order file, or null */
    @Nullable
    public static ResourceLocation creativeOrder() {
        ResourceLocation found = null;
        for (Contribution c : installed()) {
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

    /** @return the newest installed version, or 1.12.2 when no backport registered one */
    public static synchronized GameVersion newest() {
        return VERSIONS.isEmpty() ? GameVersion.BASE : VERSIONS.lastKey();
    }

    /** @return true when the newest installed version is {@code version} or later */
    public static boolean isAtLeast(String version) {
        return newest().isAtLeast(version);
    }

    /** @return every installed version's contribution, oldest first */
    public static synchronized List<Contribution> installed() {
        return Collections.unmodifiableList(new ArrayList<Contribution>(VERSIONS.values()));
    }

    /** @return the contribution of exactly this version, or null when it is not installed */
    @Nullable
    public static synchronized Contribution contribution(String version) {
        return VERSIONS.get(GameVersion.parse(version));
    }

    // ---- Resolution ----

    /** @return the world-loading screen to show: vanilla's at the newest version, or a newer one a version declared */
    public static LoadingRevision loadingRevision() {
        return loadingRevision(newest(), installed());
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
        for (Contribution c : installed()) {
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
        for (Contribution c : installed()) {
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
        GameVersion newest = newest();
        List<PresentationOption> out = new ArrayList<PresentationOption>();
        for (PresentationOption o : options()) {
            if (o.isShownAt(newest) && o.isOn(screen, newest)) {
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

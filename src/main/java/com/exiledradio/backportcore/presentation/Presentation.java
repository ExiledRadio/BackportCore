package com.exiledradio.backportcore.presentation;

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

    static {
        PANORAMA_SPEED.since("1.19.3");
        HIDE_SPLASH_TEXTS.since("1.20.3");
        registerOption(PANORAMA_SPEED);
        registerOption(HIDE_SPLASH_TEXTS);
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
            if (o.isShownAt(newest) && o.screen().resolve(newest) == screen) {
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

        Panorama(GameVersion version, ResourceLocation[] faces) {
            this.version = version;
            this.faces = faces;
        }
    }
}

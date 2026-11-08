package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.LoadingRevision;
import com.exiledradio.backportcore.presentation.LoadingStatus;
import com.exiledradio.backportcore.presentation.SpawnProgress;
import com.exiledradio.backportcore.presentation.PanoramaChoice;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VersionedDefaults;
import com.exiledradio.backportcore.presentation.option.CycleOption;
import com.exiledradio.backportcore.presentation.option.OptionStore;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.PresentationOption;
import com.exiledradio.backportcore.presentation.option.SliderOption;
import com.exiledradio.backportcore.presentation.option.ToggleOption;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * The presentation module (docs/PRESENTATION.md) with a small dummy registration: "1.14" brings only
 * splashes, "1.16" a panorama (six tiny test faces), an option per kind, a default, a menu track and an F3 key.
 * The client parts are checked by {@code ClientPresentationTests} in a dev client; this checks everything a
 * dedicated server can.
 */
final class PresentationTests {

    static final String DOMAIN = SelfTestMod.MOD_ID;

    static ToggleOption toggle;
    static SliderOption slider;
    static CycleOption cycle;
    static ToggleOption future;
    static ToggleOption retired;
    static ToggleOption accessibility;

    private PresentationTests() {
    }

    /** From the self-test mod's constructor, as a backport registers from its own. */
    static void register() {
        // Fresh option and defaults files every run, so a run never sees the last one's values.
        File dir = new File("selftest-presentation");
        for (String name : new String[] {"options.txt", "applied_defaults.txt"}) {
            new File(dir, name).delete();
        }
        OptionStore.useFile(new File(dir, "options.txt"));
        VersionedDefaults.useFile(new File(dir, "applied_defaults.txt"));
        Presentation.version("1.14")
                .addSplashes("Rainbow turtle?", "{PLAYER} IS YOU", "Now Java 16!")
                .removeSplashes("Made by Notch!");
        toggle = new ToggleOption(DOMAIN + ":toggle", "options.selftest.toggle", OptionScreen.VIDEO, true);
        slider = new SliderOption(DOMAIN + ":slider", "options.selftest.slider", OptionScreen.VIDEO, 0.5, 5.0, 0.25, 1.0);
        cycle = new CycleOption(DOMAIN + ":cycle", "options.selftest.cycle", OptionScreen.CHAT,
                new String[] {"a", "b", "c"}, new String[] {"options.selftest.a", "options.selftest.b", "options.selftest.c"}, "a");
        future = new ToggleOption(DOMAIN + ":future", "options.selftest.future", OptionScreen.VIDEO, false);
        future.since("1.20");
        retired = new ToggleOption(DOMAIN + ":retired", "options.selftest.retired", OptionScreen.VIDEO, false);
        retired.since("1.14").until("1.15");
        accessibility = new ToggleOption(DOMAIN + ":accessibility", "options.selftest.accessibility", OptionScreen.ACCESSIBILITY, false);
        accessibility.after("SHOW_SUBTITLES");
        Presentation.version("1.16")
                .panorama(DOMAIN + ":textures/gui/title/background/panorama_")
                .option(toggle).option(slider).option(cycle).option(future).option(retired).option(accessibility);
    }

    static void schedule() {
        then(1, "presentation: versions", world -> {
            check("1.14 equals 1.14.0", GameVersion.parse("1.14").equals(GameVersion.parse("1.14.0")));
            check("1.14 < 1.14.1 < 1.15 < 1.20.5 < 1.21", GameVersion.parse("1.14").compareTo(GameVersion.parse("1.14.1")) < 0
                    && GameVersion.parse("1.14.1").compareTo(GameVersion.parse("1.15")) < 0
                    && GameVersion.parse("1.15").compareTo(GameVersion.parse("1.20.5")) < 0
                    && GameVersion.parse("1.20.5").compareTo(GameVersion.parse("1.21")) < 0);
            check("1.10 is after 1.9 (numbers, not text)", GameVersion.parse("1.10").isAtLeast("1.9"));
            check("canonical text", "1.16".equals(GameVersion.parse("1.16.0").toString()), GameVersion.parse("1.16.0"));
            check("a snapshot name is refused", refused("1.14-pre1") && refused("20w14a") && refused("1"));
            check("1.12.2 cannot be registered", refusedVersion("1.12.2"));
        });
        then(1, "presentation: registry", world -> {
            check("newest installed is 1.16", Presentation.newest().equals(GameVersion.parse("1.16")), Presentation.newest());
            check("isAtLeast 1.15 and not 1.17", Presentation.isAtLeast("1.15") && !Presentation.isAtLeast("1.17"));
            List<Contribution> all = Presentation.installed();
            check("two versions installed, oldest first", all.size() == 2 && all.get(0).version().toString().equals("1.14"), all);
            check("the same version returns the same contribution", Presentation.version("1.16") == Presentation.contribution("1.16"));
            check("owner is the registering mod", all.get(1).owners().contains(SelfTestMod.MOD_ID), all.get(1).owners());
            check("one panorama, 1.16's", Presentation.panoramas().size() == 1
                    && Presentation.panoramas().get(0).faces[5].equals(new ResourceLocation(DOMAIN, "textures/gui/title/background/panorama_5.png")),
                    Presentation.panoramas().isEmpty() ? "none" : Arrays.toString(Presentation.panoramas().get(0).faces));
            check("a five-face panorama is refused", refusedPanorama());
        });
        then(1, "presentation: option screens", world -> {
            GameVersion v1122 = GameVersion.BASE;
            GameVersion v114 = GameVersion.parse("1.14");
            GameVersion v118 = GameVersion.parse("1.18");
            check("Accessibility falls back to Chat before 1.14", OptionScreen.ACCESSIBILITY.resolve(v1122) == OptionScreen.CHAT);
            check("Accessibility exists from 1.14", OptionScreen.ACCESSIBILITY.resolve(v114) == OptionScreen.ACCESSIBILITY);
            check("Mouse Settings falls back to Controls before 1.14", OptionScreen.MOUSE.resolve(v1122) == OptionScreen.CONTROLS);
            check("Key Binds (not built yet) falls back to Controls", OptionScreen.KEY_BINDS.resolve(v118) == OptionScreen.CONTROLS);
            check("Font Settings (not built yet) falls back to Options", OptionScreen.FONT.resolve(GameVersion.parse("1.21")) == OptionScreen.OPTIONS);
            check("an option takes its contribution's version", toggle.since().equals(GameVersion.parse("1.16")), toggle.since());
            check("its own since is kept", future.since().equals(GameVersion.parse("1.20")), future.since());
            check("a 1.20 option is hidden at 1.16", !future.isShown() && future.isShownAt(GameVersion.parse("1.20")));
            check("an option removed in 1.15 is hidden at 1.16, shown at 1.14", !retired.isShown() && retired.isShownAt(v114));
            List<PresentationOption> video = Presentation.optionsOn(OptionScreen.VIDEO);
            check("Video shows the toggle and the slider only", video.size() == 2 && video.contains(toggle) && video.contains(slider), ids(video));
            check("Chat shows the cycle", Presentation.optionsOn(OptionScreen.CHAT).contains(cycle));
            check("Accessibility (1.14+) shows its option", Presentation.optionsOn(OptionScreen.ACCESSIBILITY).contains(accessibility),
                    ids(Presentation.optionsOn(OptionScreen.ACCESSIBILITY)));
            check("Core's 1.19.3 and 1.20.3 options are hidden at 1.16", !Presentation.PANORAMA_SPEED.isShown() && !Presentation.HIDE_SPLASH_TEXTS.isShown());
            check("an id without a mod id is refused", refusedOption("toggle"));
        });
        schedulePanorama();
        scheduleLoading();
        then(1, "presentation: loading revisions", world -> {
            check("1.12.2 is the classic screen", LoadingRevision.forVersion(GameVersion.BASE) == LoadingRevision.CLASSIC);
            check("1.13.2 is 1.13's text", LoadingRevision.forVersion(GameVersion.parse("1.13.2")) == LoadingRevision.TEXT_1_13);
            check("1.14 and 1.15 are the grid", LoadingRevision.forVersion(GameVersion.parse("1.14")) == LoadingRevision.GRID_1_14
                    && LoadingRevision.forVersion(GameVersion.parse("1.15.2")) == LoadingRevision.GRID_1_14);
            check("1.16 to 1.20.4 are 1.16's", LoadingRevision.forVersion(GameVersion.parse("1.16")) == LoadingRevision.DATA_1_16
                    && LoadingRevision.forVersion(GameVersion.parse("1.20.4")) == LoadingRevision.DATA_1_16);
            check("1.21.9 is the spawn view; Core draws 1.16's until a drawer is registered",
                    LoadingRevision.forVersion(GameVersion.parse("1.21.9")) == LoadingRevision.SPAWN_VIEW_1_21_9
                            && LoadingRevision.SPAWN_VIEW_1_21_9.drawableFallback() == LoadingRevision.DATA_1_16);
            check("the installed 1.16 picks 1.16's", Presentation.loadingRevision() == LoadingRevision.DATA_1_16, Presentation.loadingRevision());
            Contribution declared = Presentation.detached("1.15").loadingScreen(LoadingRevision.DATA_1_16);
            check("a declared newer revision wins over the table", Presentation.loadingRevision(GameVersion.parse("1.15"),
                    Collections.singletonList(declared)) == LoadingRevision.DATA_1_16);
            Contribution older = Presentation.detached("1.16").loadingScreen(LoadingRevision.GRID_1_14);
            check("a declared older revision does not", Presentation.loadingRevision(GameVersion.parse("1.16"),
                    Collections.singletonList(older)) == LoadingRevision.DATA_1_16);
        });
    }

    /** Piece 2: which panorama shows, from the presentation.panorama setting. */
    static void schedulePanorama() {
        then(1, "presentation: panorama choice", world -> {
            ResourceLocation[] a = faces("a");
            ResourceLocation[] b = faces("b");
            List<Presentation.Panorama> two = Arrays.asList(new Presentation.Panorama(GameVersion.parse("1.14"), a),
                    new Presentation.Panorama(GameVersion.parse("1.16"), b));
            Random random = new Random(1);
            check("the default setting is LATEST", "LATEST".equals(CoreConfig.presentation.panorama), CoreConfig.presentation.panorama);
            check("LATEST shows the newest", PanoramaChoice.choose("LATEST", two, null, random).faces == b);
            check("a fixed installed version shows its own", PanoramaChoice.choose("1.14", two, null, random).faces == a);
            PanoramaChoice base = PanoramaChoice.choose("1.12.2", two, null, random);
            check("1.12.2 shows 1.12.2's own faces", base.faces == null && base.version.equals(GameVersion.BASE), base);
            check("a version not installed shows the latest", PanoramaChoice.choose("1.15", two, null, random).faces == b);
            check("a bad setting shows the latest", PanoramaChoice.choose("newest please", two, null, random).faces == b);
            check("lower case works", PanoramaChoice.choose("latest", two, null, random).faces == b);
            check("with nothing installed, 1.12.2's", PanoramaChoice.choose("LATEST", Collections.<Presentation.Panorama>emptyList(), null, random).faces == null);
            boolean neverLast = true;
            boolean sawBase = false;
            boolean sawA = false;
            for (int i = 0; i < 60; i++) {
                PanoramaChoice c = PanoramaChoice.choose("ROTATE", two, "1.16", random);
                neverLast &= c.faces != b;
                sawBase |= c.faces == null;
                sawA |= c.faces == a;
            }
            check("ROTATE never repeats last launch's and reaches every other one", neverLast && sawBase && sawA);
            check("ROTATE with only 1.12.2 shows 1.12.2's", PanoramaChoice.choose("ROTATE", Collections.<Presentation.Panorama>emptyList(), "1.12.2", random).faces == null);
        });
    }

    private static ResourceLocation[] faces(String name) {
        ResourceLocation[] f = new ResourceLocation[6];
        for (int i = 0; i < 6; i++) {
            f[i] = new ResourceLocation(DOMAIN, name + i);
        }
        return f;
    }

    /** Piece 3: the spawn area the world-loading grid drew while this server started. */
    static void scheduleLoading() {
        then(1, "presentation: world-loading grid", world -> {
            check("grid colors are 1.14's", LoadingStatus.EMPTY.color() == 0x545454 && LoadingStatus.FEATURES.color() == 0x21C600
                    && LoadingStatus.FULL.color() == 0xFFFFFF && LoadingStatus.argb(null) == 0xFF000000);
            check("recording stopped once the server started", !SpawnProgress.isActive());
            SpawnProgress.Snapshot s = SpawnProgress.last();
            if (!check("a picture was kept from the server start", s != null)) {
                return;
            }
            BlockPos spawn = world.getSpawnPoint();
            check("its center is the spawn chunk", s.center != null && s.center.x == spawn.getX() >> 4 && s.center.z == spawn.getZ() >> 4, s.center);
            check("43 by 43 cells", s.side == 43 && s.cells.length == 43 * 43, s.side);
            check("the whole spawn area loaded: 100%", s.percent == 100, s.percent);
            check("the spawn chunk is FULL", s.at(0, 0) == LoadingStatus.FULL, s.at(0, 0));
            int full = 0;
            int partial = 0;
            int untracked = 0;
            for (LoadingStatus c : s.cells) {
                if (c == LoadingStatus.FULL) {
                    full++;
                } else if (c == null) {
                    untracked++;
                } else {
                    partial++;
                }
            }
            // 1.12.2 loads exactly the 25 by 25 square; a chunk is populated once its +x, +z and +xz neighbors exist,
            // and FULL once all eight neighbors are populated, so the FULL core is 22 by 22 on vanilla terrain.
            check("1.12.2's 25 by 25 spawn square is tracked, its core FULL", full + partial >= 625 && full >= 21 * 21,
                    "full=" + full + " partial=" + partial + " untracked=" + untracked);
            check("a ring of generated, not yet ready chunks around it", partial > 0, partial);
            check("the far corners were never loaded (the black border)", s.at(21, 21) == null && s.at(-21, -21) == null);
            check("every spawn-square cell has a status", allTracked(s));
        });
    }

    private static boolean allTracked(SpawnProgress.Snapshot s) {
        for (int dx = -SpawnProgress.SPAWN_RADIUS; dx <= SpawnProgress.SPAWN_RADIUS; dx++) {
            for (int dz = -SpawnProgress.SPAWN_RADIUS; dz <= SpawnProgress.SPAWN_RADIUS; dz++) {
                if (s.at(dx, dz) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    static String ids(List<PresentationOption> options) {
        StringBuilder b = new StringBuilder();
        for (PresentationOption o : options) {
            b.append(o.id()).append(' ');
        }
        return b.toString().trim();
    }

    private static boolean refused(String version) {
        try {
            GameVersion.parse(version);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean refusedVersion(String version) {
        try {
            Presentation.version(version);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean refusedPanorama() {
        try {
            Presentation.detached("1.17").panorama(new ResourceLocation("a"), new ResourceLocation("b"), new ResourceLocation("c"),
                    new ResourceLocation("d"), new ResourceLocation("e"));
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean refusedOption(String id) {
        try {
            new ToggleOption(id, "x", OptionScreen.VIDEO, false);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }
}

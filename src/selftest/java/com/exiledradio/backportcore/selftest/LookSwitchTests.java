package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.LookVersions;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.settings.LookFeelPage;
import com.exiledradio.backportcore.settings.SettingsModel;

import java.util.Arrays;
import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Core 1.4.0's Look &amp; Feel switches on a dedicated server: how a part's version resolves, the assets' parts, the page
 * built from Core's settings (the self-test mod's dummy 1.14 and 1.16 are installed), and the server ignoring the switches.
 */
final class LookSwitchTests {

    private LookSwitchTests() {
    }

    private static GameVersion v(String s) {
        return GameVersion.parse(s);
    }

    static void schedule() {
        then(1, "look switches: resolving", world -> {
            check("look: 26.2's pause grid at a quarter of the free height", com.exiledradio.backportcore.presentation.VanillaRevisions.pauseGridTop(240, 5) == 18
                    && com.exiledradio.backportcore.presentation.VanillaRevisions.pauseGridTop(480, 6) == 72);
            check("look: on, Latest: the newest installed", Presentation.resolveLook(v("1.21.11"), true, "LATEST").equals(v("1.21.11")));
            check("look: off: 1.12.2", Presentation.resolveLook(v("1.21.11"), false, "LATEST").equals(GameVersion.BASE));
            check("look: Newest Look stops at its version", Presentation.resolveLook(v("1.21.11"), true, "1.16").equals(v("1.16")));
            check("look: never past what is installed", Presentation.resolveLook(v("1.14"), true, "1.16").equals(v("1.14")));
            check("look: a value that is not a version reads as Latest", Presentation.resolveLook(v("1.20"), true, "nonsense").equals(v("1.20"))
                    && Presentation.lookCap("latest") == null && Presentation.lookCap(" ") == null);

            check("look: assets by path", LookArea.ofAsset("textures/gui/title/minecraft.png") == LookArea.TITLE
                    && LookArea.ofAsset("textures/gui/title/mojangstudios.png") == LookArea.STARTUP
                    && LookArea.ofAsset("textures/gui/sprites/toast/now_playing.png") == LookArea.TOASTS
                    && LookArea.ofAsset("textures/gui/sprites/icon/music_notes.png") == LookArea.TOASTS
                    && LookArea.ofAsset("textures/gui/sprites/container/slot_highlight_back.png") == LookArea.INVENTORY
                    && LookArea.ofAsset("textures/gui/recipe_book.png") == LookArea.INVENTORY
                    && LookArea.ofAsset("textures/gui/widgets.png") == LookArea.MENUS
                    && LookArea.ofAsset("textures/entity/ghast/ghast.png") == LookArea.TEXTURES);
            check("look: an option belongs to its screen's part unless it names one", Presentation.CLOUD_RANGE.area() == LookArea.VIDEO
                    && Presentation.CHAT_DELAY.area() == LookArea.CHAT && Presentation.NARRATOR_HOTKEY.area() == LookArea.OPTIONS
                    && Presentation.UI_VOLUME.area() == LookArea.MUSIC && Presentation.MUSIC_TOAST.area() == LookArea.TOASTS
                    && Presentation.TEXT_BACKGROUND.area() == LookArea.CHAT);

            // A dedicated server ignores the switches: it has no look, and its players choose their own.
            boolean master = CoreConfig.lookAndFeel.newerLook;
            boolean loading = CoreConfig.lookAndFeel.loadingScreens;
            String cap = CoreConfig.lookAndFeel.newestLook;
            try {
                CoreConfig.lookAndFeel.newerLook = false;
                CoreConfig.lookAndFeel.loadingScreens = false;
                CoreConfig.lookAndFeel.newestLook = "1.14";
                check("look: a dedicated server ignores the switches", Presentation.newest().equals(Presentation.installedNewest())
                        && Presentation.newest(LookArea.LOADING).equals(Presentation.installedNewest()), Presentation.newest());
            } finally {
                CoreConfig.lookAndFeel.newerLook = master;
                CoreConfig.lookAndFeel.loadingScreens = loading;
                CoreConfig.lookAndFeel.newestLook = cap;
            }

            List<GameVersion> loadingVersions = LookVersions.of(LookArea.LOADING);
            check("look: the loading screens change at 1.13, 1.14 and 1.16 with 1.16 installed",
                    loadingVersions.containsAll(Arrays.asList(v("1.13"), v("1.14"), v("1.16"))) && !loadingVersions.contains(v("1.20.5")),
                    loadingVersions);
            check("look: shown as a range", "1.13–1.16".equals(LookVersions.range(loadingVersions)), LookVersions.range(loadingVersions));
            check("look: the title screen's versions include the installed panoramas' (1.14's)", LookVersions.of(LookArea.TITLE).contains(v("1.14")),
                    LookVersions.of(LookArea.TITLE));
        });
        then(1, "look switches: the page", world -> {
            SettingsModel.Page page = LookFeelPage.page();
            check("page: built", page != null && !page.groups.isEmpty());
            SettingsModel.Group head = page.groups.get(0);
            check("page: the master switch heads it", head.toggle != null && LookFeelPage.isMaster(head.toggle)
                    && head.toggle.type == SettingsModel.Type.BOOL, head.toggle == null ? "none" : head.toggle.id);
            SettingsModel.Entry newest = null;
            for (SettingsModel.Entry e : head.entries) {
                if ("newestLook".equals(e.name)) {
                    newest = e;
                }
            }
            check("page: Newest Look under it, a choice of Latest and each installed version", newest != null
                    && newest.type == SettingsModel.Type.ENUM && Arrays.asList(newest.validValues()).containsAll(Arrays.asList("LATEST", "1.14", "1.16")),
                    newest == null ? "none" : Arrays.toString(newest.validValues()));
            String[] sections = new String[page.groups.size() - 1];
            for (int i = 1; i < page.groups.size(); i++) {
                sections[i - 1] = page.groups.get(i).path;
            }
            check("page: Core's sections in order", Arrays.asList(sections).equals(Arrays.asList("lookandfeel.loading", "lookandfeel.title",
                    "lookandfeel.menus", "lookandfeel.video", "lookandfeel.chat", "lookandfeel.debug", "lookandfeel.toasts", "lookandfeel.inventory",
                    "lookandfeel.music", "lookandfeel.world")), Arrays.toString(sections));
            int core = 0;
            boolean allClient = true;
            boolean textures = false;
            boolean panorama = false;
            for (SettingsModel.Entry e : page.entries) {
                allClient &= e.side == SettingsModel.Side.CLIENT;
                if (LookFeelPage.area(e) != null) {
                    core++;
                }
                textures |= "modernTextures".equals(e.name);
                panorama |= "panorama".equals(e.name);
            }
            check("page: one switch per part (the textures and panorama too)", core == LookArea.values().length + 1 && textures && panorama, core);
            check("page: every row is the player's own (Client)", allClient);
            check("page: the same settings as Core's page", page.entries.contains(com.exiledradio.backportcore.settings.BackportSettings.entry(head.toggle.id)));
            SettingsModel.Entry loading = page.groups.get(1).entries.get(0);
            check("page: World Loading Screens first, with its versions", "loadingScreens".equals(loading.name)
                    && "1.13–1.16".equals(LookFeelPage.versions(loading)), loading.name + " " + LookFeelPage.versions(loading));
        });
    }
}

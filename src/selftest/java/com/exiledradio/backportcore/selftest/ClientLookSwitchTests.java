package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.LoadingRevision;
import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.client.LookFeelClient;
import com.exiledradio.backportcore.presentation.client.PanoramaRenderer;
import com.exiledradio.backportcore.presentation.client.WorldLoadingScreen;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.settings.BackportSettings;
import com.exiledradio.backportcore.settings.LookFeelPage;
import com.exiledradio.backportcore.settings.SettingsModel;
import com.exiledradio.backportcore.settings.client.SettingsScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * Core 1.4.0's Look &amp; Feel page in the dev client, after the presentation tests (the dummy 1.14 and 1.16 installed): the
 * page opened in code, the master switch turned off and on again through the page's own Done, and a world created with
 * World Loading Screens off, which then draws 1.12.2's screen. No clicks or keys reach the window; screenshots go to
 * {@code screenshots/look-*.png}.
 */
@SideOnly(Side.CLIENT)
final class ClientLookSwitchTests {

    private static final String WORLD = "bc-lookswitch-selftest";

    static boolean done;
    private static int stage;
    private static int ticks;
    private static int framesBefore;
    private static LoadingRevision revisionBefore;

    private ClientLookSwitchTests() {
    }

    private static void shot(Minecraft mc, String name) {
        ScreenShotHelper.saveScreenshot(mc.gameDir, "look-" + name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        BackportCore.LOGGER.info("LOOK SHOT {} ({})", name, mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
    }

    /** From the presentation tests' tick, back at the title screen; sets {@link #done} when finished. */
    static void tick(Minecraft mc) {
        ticks++;
        try {
            switch (stage) {
                case 0:
                    mc.displayGuiScreen(new SettingsScreen(new GuiMainMenu(), BackportCore.MOD_ID));
                    stage = 1;
                    ticks = 0;
                    break;
                case 1:
                    if (ticks >= 10) {
                        opened(mc);
                        shot(mc, "page");
                        masterOff(mc);
                        mc.displayGuiScreen(new GuiMainMenu());
                        stage = 2;
                        ticks = 0;
                    }
                    break;
                case 2:
                    // The resources load again (the vanilla text and textures change); then the title screen settles.
                    if (ticks >= 60) {
                        shot(mc, "off-title");
                        mc.displayGuiScreen(new SettingsScreen(new GuiMainMenu(), BackportCore.MOD_ID));
                        stage = 3;
                        ticks = 0;
                    }
                    break;
                case 3:
                    if (ticks >= 10) {
                        shot(mc, "page-off");
                        masterOn(mc);
                        loadingOff(mc);
                        mc.displayGuiScreen(new GuiMainMenu());
                        stage = 4;
                        ticks = 0;
                    }
                    break;
                case 4:
                    if (ticks >= 60) {
                        shot(mc, "on-title");
                        loadWorld(mc);
                        stage = 5;
                        ticks = 0;
                    }
                    break;
                case 5:
                    if (mc.world != null && mc.player != null && ticks >= 20) {
                        check("client look: World Loading Screens off: Core drew no frame, 1.12.2's screen showed",
                                WorldLoadingScreen.frames == framesBefore && WorldLoadingScreen.lastRevision == revisionBefore,
                                WorldLoadingScreen.frames - framesBefore + " frames, last " + WorldLoadingScreen.lastRevision);
                        shot(mc, "in-world");
                        mc.loadWorld(null);
                        mc.displayGuiScreen(new GuiMainMenu());
                        stage = 6;
                        ticks = 0;
                    }
                    break;
                case 6:
                    set(mc, "loadingScreens", "true");
                    check("client look: World Loading Screens back on: 1.16's again", Presentation.loadingRevision() == LoadingRevision.DATA_1_16,
                            Presentation.loadingRevision());
                    done = true;
                    break;
                default:
                    done = true;
                    break;
            }
        } catch (Throwable t) {
            SelfTestRunner.fail("client look stage " + stage + " threw", t.toString());
            BackportCore.LOGGER.error("CLIENT SELFTEST look stage {} threw", stage, t);
            CoreConfig.lookAndFeel.newerLook = true;
            CoreConfig.lookAndFeel.loadingScreens = true;
            done = true;
        }
    }

    private static SettingsModel.Entry entry(String name) {
        for (SettingsModel.Entry e : LookFeelPage.page().entries) {
            if (e.name.equals(name) && BackportCore.MOD_ID.equals(e.group.page.modid)) {
                return e;
            }
        }
        throw new IllegalStateException("no look setting " + name);
    }

    /** As the player would: stage the value on the open page and press Done. */
    @SuppressWarnings("unchecked")
    private static void press(Minecraft mc, String name, String value) throws ReflectiveOperationException {
        SettingsScreen screen = (SettingsScreen) mc.currentScreen;
        Field staged = SettingsScreen.class.getDeclaredField("staged");
        staged.setAccessible(true);
        ((Map<SettingsModel.Entry, String[]>) staged.get(screen)).put(entry(name), new String[] {value});
        Method finish = SettingsScreen.class.getDeclaredMethod("finish");
        finish.setAccessible(true);
        finish.invoke(screen);
    }

    /** Without the screen, as the cleanup does. */
    private static void set(Minecraft mc, String name, String value) {
        Map<SettingsModel.Entry, String[]> c = new LinkedHashMap<SettingsModel.Entry, String[]>();
        c.put(entry(name), new String[] {value});
        BackportSettings.apply(c, mc.world != null);
    }

    private static void opened(Minecraft mc) throws ReflectiveOperationException {
        check("client look: the settings screen opened on the Family page", mc.currentScreen instanceof SettingsScreen);
        Field selected = SettingsScreen.class.getDeclaredField("selected");
        selected.setAccessible(true);
        Field rows = SettingsScreen.class.getDeclaredField("rows");
        rows.setAccessible(true);
        List<?> r = (List<?>) rows.get(mc.currentScreen);
        int groups = 0;
        for (Object row : r) {
            groups += row.getClass().getSimpleName().equals("GroupRow") ? 1 : 0;
        }
        check("client look: the Family page holds Core's settings and every Look & Feel section (1.4.5)",
                BackportCore.MOD_ID.equals(selected.get(mc.currentScreen)) && groups == LookFeelPage.familyPage().groups.size()
                        && LookFeelPage.familyPage().groups.containsAll(LookFeelPage.page().groups),
                selected.get(mc.currentScreen) + " " + groups + " group rows for " + LookFeelPage.familyPage().groups.size() + " groups");
        check("client look: every Core switch shows its versions", LookFeelPage.versions(entry("loadingScreens")) != null
                && LookFeelPage.versions(entry("titleScreen")) != null, LookFeelPage.versions(entry("loadingScreens")));
        check("client look: on, the game looks like 1.16", Presentation.newest().equals(GameVersion.parse("1.16"))
                && Presentation.loadingRevision() == LoadingRevision.DATA_1_16 && PanoramaRenderer.modern());
    }

    private static void masterOff(Minecraft mc) throws ReflectiveOperationException {
        int reloads = LookFeelClient.reloads;
        boolean commandSuggestions = Presentation.commandSuggestions() != null;
        press(mc, "newerLook", "false");
        check("client look: Done saved the master switch off", !CoreConfig.lookAndFeel.newerLook);
        check("client look: off, everything resolves at 1.12.2", Presentation.newest().equals(GameVersion.BASE)
                && Presentation.newest(LookArea.TITLE).equals(GameVersion.BASE) && Presentation.newest(LookArea.MUSIC).equals(GameVersion.BASE)
                && Presentation.installed(LookArea.SPLASHES).isEmpty(), Presentation.newest());
        check("client look: off, 1.12.2's loading screen, title screen, buttons and Video Settings",
                Presentation.loadingRevision() == LoadingRevision.CLASSIC && !PanoramaRenderer.modern() && !VanillaRevisions.whiteButtonText()
                        && !VanillaRevisions.titleFadeIn() && !VanillaRevisions.anaglyphRemoved()
                        && VanillaRevisions.startup() == VanillaRevisions.Startup.NONE);
        check("client look: off, no newer options on the screens", Presentation.optionsOn(OptionScreen.VIDEO).isEmpty()
                && Presentation.optionsOn(OptionScreen.CHAT).isEmpty() && Presentation.optionsOn(OptionScreen.OPTIONS).isEmpty(),
                Presentation.optionsOn(OptionScreen.CHAT).size());
        check("client look: off, no F3 text, fog or music steps", com.exiledradio.backportcore.presentation.client.DebugText.steps().steps().isEmpty()
                && com.exiledradio.backportcore.presentation.client.FogPipeline.steps().steps().isEmpty()
                && com.exiledradio.backportcore.presentation.client.GameMusic.steps().steps().isEmpty());
        check("client look: off, no command suggestions (were " + commandSuggestions + ")", Presentation.commandSuggestions() == null);
        check("client look: off, the resources load again once", LookFeelClient.reloads == reloads + 1, LookFeelClient.reloads - reloads);
        check("client look: each switch keeps its own choice", CoreConfig.lookAndFeel.loadingScreens && CoreConfig.lookAndFeel.titleScreen
                && CoreConfig.lookAndFeel.debug && CoreConfig.textures.modernTextures);
    }

    private static void masterOn(Minecraft mc) throws ReflectiveOperationException {
        press(mc, "newerLook", "true");
        check("client look: back on, 1.16's look again", CoreConfig.lookAndFeel.newerLook && Presentation.newest().equals(GameVersion.parse("1.16"))
                && Presentation.loadingRevision() == LoadingRevision.DATA_1_16 && PanoramaRenderer.modern() && VanillaRevisions.whiteButtonText()
                && !Presentation.optionsOn(OptionScreen.CHAT).isEmpty() && !com.exiledradio.backportcore.presentation.client.DebugText.steps().steps().isEmpty());
    }

    private static void loadingOff(Minecraft mc) {
        set(mc, "loadingScreens", "false");
        check("client look: World Loading Screens off: 1.12.2's loading screen, the rest still 1.16's",
                Presentation.loadingRevision() == LoadingRevision.CLASSIC && Presentation.newest(LookArea.TITLE).equals(GameVersion.parse("1.16"))
                        && PanoramaRenderer.modern(), Presentation.loadingRevision());
    }

    private static void loadWorld(Minecraft mc) throws Exception {
        FileUtils.deleteDirectory(new File(mc.gameDir, "saves/" + WORLD));
        check("client look: Core's loading screen is still in place, to draw again when switched on", mc.loadingScreen instanceof WorldLoadingScreen,
                mc.loadingScreen == null ? "none" : mc.loadingScreen.getClass().getName());
        framesBefore = WorldLoadingScreen.frames;
        revisionBefore = WorldLoadingScreen.lastRevision;
        mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(20261117L, GameType.CREATIVE, true, false, WorldType.DEFAULT));
    }
}

package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.LoadingRevision;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.client.ClientDefaults;
import com.exiledradio.backportcore.presentation.client.MenuMusicPlayer;
import com.exiledradio.backportcore.presentation.client.OptionWidgets;
import com.exiledradio.backportcore.presentation.client.OptionsInjector;
import com.exiledradio.backportcore.presentation.client.OptionsListScreen;
import com.exiledradio.backportcore.presentation.client.PanoramaRenderer;
import com.exiledradio.backportcore.presentation.client.PresentationHooks;
import com.exiledradio.backportcore.presentation.client.TitleSplashes;
import com.exiledradio.backportcore.presentation.client.WorldLoadingScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiOptionsRowList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.ScreenChatOptions;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * The client half of the presentation tests, for {@code gradlew runClient -Pselftest}: at the title screen it
 * checks the patches, the panorama, the splash, the defaults and the music; opens the options screens in
 * code (no clicks or keys reach the window) and checks what Core put on them; calls the F3 hook; then creates
 * a world, checks the loading screen drew 1.16's revision, leaves, logs
 * {@code CLIENT SELFTEST DONE pass=N fail=M} and closes the game.
 */
@SideOnly(Side.CLIENT)
final class ClientPresentationTests {

    private static final String WORLD = "bc-presentation-selftest";

    private int menuTicks;
    private int stage;
    private int worldTicks;

    private ClientPresentationTests() {
    }

    static void register() {
        MinecraftForge.EVENT_BUS.register(new ClientPresentationTests());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        try {
            switch (this.stage) {
                case 0:
                    if (mc.currentScreen instanceof GuiMainMenu && ++this.menuTicks >= 400) {
                        this.stage = 1;
                        titleScreen(mc);
                        optionScreens(mc);
                        debugKeys(mc);
                        this.stage = 2;
                    }
                    break;
                case 2:
                    this.stage = 3;
                    loadWorld(mc);
                    break;
                case 3:
                    if (mc.world != null && mc.player != null && ++this.worldTicks >= 40) {
                        inWorld(mc);
                        this.stage = 4;
                        mc.world.sendQuittingDisconnectingPacket();
                        mc.loadWorld(null);
                        mc.displayGuiScreen(new GuiMainMenu());
                    }
                    break;
                case 4:
                    this.stage = 5;
                    BackportCore.LOGGER.info("CLIENT SELFTEST DONE pass={} fail={}", SelfTestRunner.pass, SelfTestRunner.fail);
                    mc.shutdown();
                    break;
                default:
                    break;
            }
        } catch (Throwable t) {
            SelfTestRunner.fail("client stage " + this.stage + " threw", t.toString());
            BackportCore.LOGGER.error("CLIENT SELFTEST stage {} threw", this.stage, t);
            this.stage = 4;
        }
    }

    private static void titleScreen(Minecraft mc) {
        check("client: P6 applied to the title screen", "applied".equals(Asm.state("P6")), Asm.state("P6"));
        check("client: P7 applied to the keyboard tick", "applied".equals(Asm.state("P7")), Asm.state("P7"));
        check("client: newest installed is the dummy 1.16", Presentation.newest().equals(GameVersion.parse("1.16")), Presentation.newest());
        check("client: the title screen is drawn the modern way", PanoramaRenderer.modern());
        check("client: the dummy version's faces are chosen", PanoramaRenderer.choice().version.equals(GameVersion.parse("1.16"))
                && PanoramaRenderer.faces()[0].getNamespace().equals(SelfTestMod.MOD_ID), PanoramaRenderer.choice());
        check("client: the panorama hook drew frames", PanoramaRenderer.frames > 20, PanoramaRenderer.frames);
        List<String> pool = TitleSplashes.lastPool;
        check("client: the splash pool has 1.14's additions and not its removal", pool != null && pool.contains("Rainbow turtle?")
                && !pool.contains("Made by Notch!") && !pool.contains("Now Java 16!"), pool == null ? "none" : pool.size() + " splashes");
        String splash = splash((GuiMainMenu) mc.currentScreen);
        check("client: the shown splash comes from the pool", pool != null && splash != null
                && (pool.contains(splash) || splash.endsWith(" IS YOU") || splash.equals("Merry X-mas!") || splash.equals("Happy new year!")
                || splash.equals("OOoooOOOoooo! Spooky!")), splash);
        check("client: the dummy vanilla default was applied once", ClientDefaults.applied >= 1 && !mc.gameSettings.chatLinksPrompt,
                "applied=" + ClientDefaults.applied + " chatLinksPrompt=" + mc.gameSettings.chatLinksPrompt);
        check("client: the loading screen is Core's", mc.loadingScreen instanceof WorldLoadingScreen, mc.loadingScreen.getClass().getName());
        // The ticker starts the menu music within its first 20 to 600 ticks; 400 have passed.
        check("client: the menu music came from the pool", MenuMusicPlayer.lastPick != null, MenuMusicPlayer.lastPick);
    }

    private static void optionScreens(Minecraft mc) throws Exception {
        GuiScreen menu = mc.currentScreen;
        GuiOptions options = new GuiOptions(menu, mc.gameSettings);
        mc.displayGuiScreen(options);
        List<GuiButton> buttons = buttons(options);
        GuiButton access = null;
        for (GuiButton b : buttons) {
            if (b instanceof OptionWidgets.Action) {
                access = b;
            }
        }
        check("client: Options has Accessibility Settings in place of Snooper Settings", access != null && find(buttons, 104) == null
                && access.x == options.width / 2 + 5, access == null ? "none" : access.displayString);

        OptionsListScreen accessibility = OptionsListScreen.accessibility(options);
        mc.displayGuiScreen(accessibility);
        List<GuiButton> a = buttons(accessibility);
        check("client: Accessibility holds narrator, subtitles, the backport option, chat opacity, Auto-Jump in that order",
                a.size() == 6 && a.get(0).id == GameSettings.Options.NARRATOR.getOrdinal()
                        && a.get(1).id == GameSettings.Options.SHOW_SUBTITLES.getOrdinal()
                        && a.get(2) instanceof OptionWidgets.OptionButton
                        && a.get(3).id == GameSettings.Options.CHAT_OPACITY.getOrdinal()
                        && a.get(4).id == GameSettings.Options.AUTO_JUMP.getOrdinal() && a.get(5).id == 200, describe(a));
        check("client: two columns 24 apart", a.size() >= 3 && a.get(1).x - a.get(0).x == 160 && a.get(2).y - a.get(0).y == 24);
        GuiButton option = a.size() > 2 ? a.get(2) : null;
        boolean before = PresentationTests.accessibility.get();
        if (option != null) {
            option.mousePressed(mc, option.x + 2, option.y + 2);
        }
        check("client: pressing the option's button flips it and relabels it", PresentationTests.accessibility.get() != before
                && option.displayString.endsWith(PresentationTests.accessibility.get() ? "ON" : "OFF"), option == null ? "none" : option.displayString);

        GuiVideoSettings video = new GuiVideoSettings(options, mc.gameSettings);
        mc.displayGuiScreen(video);
        Field listField = ReflectionHelper.findField(GuiVideoSettings.class, "optionsRowList", "field_146501_h");
        Field rowsField = ReflectionHelper.findField(GuiOptionsRowList.class, "options", "field_148184_k");
        List<?> rows = (List<?>) rowsField.get(listField.get(video));
        check("client: Video Settings gained a row for its two options", rows.size() == 9, rows.size());

        ScreenChatOptions chat = new ScreenChatOptions(options, mc.gameSettings);
        mc.displayGuiScreen(chat);
        List<GuiButton> c = buttons(chat);
        GuiButton cycle = null;
        for (GuiButton b : c) {
            if (b instanceof OptionWidgets.OptionButton) {
                cycle = b;
            }
        }
        GuiButton done = find(c, 200);
        check("client: Chat Settings has the cycle in the right half of its last row, Done below it", cycle != null && done != null
                && cycle.x == chat.width / 2 + 5 && done.y >= cycle.y + 24, cycle == null ? "none" : cycle.x + "," + cycle.y + " done " + done.y);

        GuiControls controls = new GuiControls(options, mc.gameSettings);
        mc.displayGuiScreen(controls);
        List<GuiButton> k = buttons(controls);
        boolean mouseButton = false;
        for (GuiButton b : k) {
            mouseButton |= b instanceof OptionWidgets.Action && b.x == controls.width / 2 - 155 && b.y == 18;
        }
        GuiButton autoJump = find(k, GameSettings.Options.AUTO_JUMP.getOrdinal());
        check("client: Controls has Mouse Settings and Auto-Jump in its first row, no Sensitivity", mouseButton
                && autoJump != null && autoJump.y == 18 && find(k, GameSettings.Options.SENSITIVITY.getOrdinal()) == null, describe(k));
        check("client: the injector saw all four screens", OptionsInjector.INJECTED.containsAll(
                java.util.Arrays.asList("GuiOptions", "GuiVideoSettings", "ScreenChatOptions", "GuiControls")), OptionsInjector.INJECTED);
        mc.displayGuiScreen(menu);
    }

    private static void debugKeys(Minecraft mc) {
        int runs = PresentationTests.copyRuns;
        check("client: F3+C runs the dummy version's action", PresentationHooks.processKeyF3(mc, 46) && PresentationTests.copyRuns == runs + 1);
        check("client: F3+F, removed, does nothing", !PresentationHooks.processKeyF3(mc, 33));
        check("client: F3+B is still vanilla's (hitboxes on)", PresentationHooks.processKeyF3(mc, 48) && mc.getRenderManager().isDebugBoundingBox());
        PresentationHooks.processKeyF3(mc, 48);
        check("client: and off again", !mc.getRenderManager().isDebugBoundingBox());
        check("client: an unknown key is left alone", !PresentationHooks.processKeyF3(mc, 23));
    }

    private void loadWorld(Minecraft mc) throws Exception {
        FileUtils.deleteDirectory(new File(mc.gameDir, "saves/" + WORLD));
        check("client: the loading revision is 1.16's", Presentation.loadingRevision() == LoadingRevision.DATA_1_16);
        mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(20261107L, GameType.CREATIVE, true, false, WorldType.DEFAULT));
        check("client: the world-loading screen drew frames", WorldLoadingScreen.frames > 0, WorldLoadingScreen.frames);
        check("client: it drew 1.16's revision", WorldLoadingScreen.lastRevision == LoadingRevision.DATA_1_16, WorldLoadingScreen.lastRevision);
        check("client: it drew the chunk grid", WorldLoadingScreen.gridFrames > 0,
                "grid=" + WorldLoadingScreen.gridFrames + " data=" + WorldLoadingScreen.dataFrames);
    }

    private static void inWorld(Minecraft mc) {
        check("client: F3+Q lists the key map in a world", PresentationHooks.processKeyF3(mc, 16));
    }

    private static String splash(GuiMainMenu menu) {
        try {
            return (String) ReflectionHelper.findField(GuiMainMenu.class, "splashText", "field_73975_c").get(menu);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> buttons(GuiScreen screen) throws Exception {
        return (List<GuiButton>) ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n").get(screen);
    }

    private static GuiButton find(List<GuiButton> buttons, int id) {
        for (GuiButton b : buttons) {
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }

    private static String describe(List<GuiButton> buttons) {
        StringBuilder s = new StringBuilder();
        for (GuiButton b : buttons) {
            s.append(b.id).append('@').append(b.x).append(',').append(b.y).append(' ');
        }
        return s.toString().trim();
    }
}

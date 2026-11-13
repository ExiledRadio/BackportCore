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

    static int debugEdits;
    static int musicAsks;
    static int skyAsks;
    static int cloudAsks;

    static void register() {
        MinecraftForge.EVENT_BUS.register(new ClientPresentationTests());
        com.exiledradio.backportcore.presentation.client.DebugText.add("1.16", "selftest line", lines -> {
            lines.left.add("selftest debug line");
            debugEdits++;
        });
        com.exiledradio.backportcore.presentation.client.DebugText.add("1.14", "selftest older", lines -> lines.left.add("selftest older line"));
        com.exiledradio.backportcore.presentation.client.GameMusic.add("1.16", "selftest music", s -> musicAsks++);
        com.exiledradio.backportcore.presentation.client.FogPipeline.add("1.16", "selftest sky and clouds",
                new com.exiledradio.backportcore.presentation.client.FogPipeline.Step() {
                    @Override
                    public void sky(com.exiledradio.backportcore.presentation.client.FogPipeline.Sky sky) {
                        skyAsks++;
                    }

                    @Override
                    public void clouds(com.exiledradio.backportcore.presentation.client.FogPipeline.Clouds clouds) {
                        cloudAsks++;
                        clouds.height += 64.0F;
                    }
                });
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
                        finishTitle(mc);
                        optionScreens(mc);
                        debugKeys(mc);
                        multiplayerWarning(mc);
                        this.stage = 2;
                    }
                    break;
                case 2:
                    this.stage = 3;
                    loadWorld(mc);
                    break;
                case 3:
                    if (mc.world != null && mc.player != null && this.worldTicks == 5) {
                        // Things that need a few frames: debug text, chat, sky and clouds.
                        mc.gameSettings.showDebugInfo = true;
                        mc.ingameGUI.getChatGUI().printChatMessage(new net.minecraft.util.text.TextComponentString("selftest chat line"));
                        Presentation.CHAT_DELAY.set(1.0);
                        net.minecraftforge.client.event.ClientChatReceivedEvent first = new net.minecraftforge.client.event.ClientChatReceivedEvent(
                                net.minecraft.util.text.ChatType.CHAT, new net.minecraft.util.text.TextComponentString("delayed 1"));
                        net.minecraftforge.client.event.ClientChatReceivedEvent second = new net.minecraftforge.client.event.ClientChatReceivedEvent(
                                net.minecraft.util.text.ChatType.CHAT, new net.minecraft.util.text.TextComponentString("delayed 2"));
                        MinecraftForge.EVENT_BUS.post(first);
                        MinecraftForge.EVENT_BUS.post(second);
                        check("client: Chat Delay passes the first line and holds the second", !first.isCanceled() && second.isCanceled()
                                && com.exiledradio.backportcore.presentation.client.ChatRenderer.queued() == 1,
                                first.isCanceled() + "/" + second.isCanceled() + " queued " + com.exiledradio.backportcore.presentation.client.ChatRenderer.queued());
                    }
                    if (mc.world != null && mc.player != null && this.worldTicks >= 10 && ClientLocateTests.instance != null) {
                        ClientLocateTests.instance.tick(mc);
                    }
                    if (mc.world != null && mc.player != null && this.worldTicks > 5) {
                        ClientCameraTests.tick(mc);
                    }
                    if (mc.world != null && mc.player != null && ++this.worldTicks >= 60 && ClientLocateTests.done() && ClientCameraTests.done) {
                        mc.gameSettings.showDebugInfo = false;
                        inWorld(mc);
                        finishWorld(mc);
                        this.stage = 4;
                        // loadWorld(null) stops the integrated server itself; a quitting packet first can stop it before
                        // loadWorld's shutdown task is queued, and that task then never runs (a hang).
                        mc.loadWorld(null);
                        mc.displayGuiScreen(new GuiMainMenu());
                    }
                    break;
                case 4:
                    this.stage = 5;
                    backups(mc);
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
        // 1.16: narrator, subtitles, the backport option (after subtitles), text background opacity, text background,
        // chat opacity, line spacing, chat delay, Auto-Jump, then the option that is also on Video, then Done.
        check("client: Accessibility in 1.16.5's order with the backport options placed by their anchors",
                a.size() == 11 && a.get(0).id == GameSettings.Options.NARRATOR.getOrdinal()
                        && a.get(1).id == GameSettings.Options.SHOW_SUBTITLES.getOrdinal()
                        && isOption(a.get(2), PresentationTests.accessibility)
                        && isOption(a.get(3), Presentation.TEXT_BACKGROUND_OPACITY) && isOption(a.get(4), Presentation.TEXT_BACKGROUND)
                        && a.get(5).id == GameSettings.Options.CHAT_OPACITY.getOrdinal()
                        && isOption(a.get(6), Presentation.CHAT_LINE_SPACING) && isOption(a.get(7), Presentation.CHAT_DELAY)
                        && a.get(8).id == GameSettings.Options.AUTO_JUMP.getOrdinal()
                        && isOption(a.get(9), PresentationTests.tooltipped) && a.get(10).id == 200, describe(a));
        check("client: no Accessibility Guide before 1.16.4 (Done is centered)", a.size() > 0
                && a.get(a.size() - 1).x == accessibility.width / 2 - 100, describe(a));
        GuiButton delay = a.size() > 7 ? a.get(7) : null;
        check("client: Chat Delay reads \"Chat Delay: None\" at zero", delay != null && "Chat Delay: None".equals(delay.displayString),
                delay == null ? "none" : delay.displayString);
        GuiButton tip = a.size() > 9 ? a.get(9) : null;
        PresentationTests.tooltipped.set(0.0);
        if (tip instanceof OptionWidgets.OptionButton) {
            ((OptionWidgets.OptionButton) tip).refresh();
        }
        check("client: a slider with a zero key reads OFF at zero", tip != null && tip.displayString.endsWith(": OFF"), tip == null ? "none" : tip.displayString);
        PresentationTests.tooltipped.set(1.0);
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
        // 16 vanilla entries, less Anaglyph (1.13) and VBOs (1.14), plus the dummy's two and the also-on-Video one.
        check("client: Video Settings: 17 entries in 9 rows", rows.size() == 9, rows.size());
        boolean anaglyph = false;
        boolean vbo = false;
        boolean numbered = false;
        Field rowA = ReflectionHelper.findField(GuiOptionsRowList.Row.class, "buttonA", "field_148323_b");
        Field rowB = ReflectionHelper.findField(GuiOptionsRowList.Row.class, "buttonB", "field_148324_c");
        for (Object row : rows) {
            for (Field f : new Field[] {rowA, rowB}) {
                GuiButton b = (GuiButton) f.get(row);
                if (b == null) {
                    continue;
                }
                anaglyph |= b.id == GameSettings.Options.ANAGLYPH.getOrdinal() && !(b instanceof OptionWidgets.OptionButton);
                vbo |= b.id == GameSettings.Options.USE_VBO.getOrdinal() && !(b instanceof OptionWidgets.OptionButton);
                numbered |= b.getClass().getSimpleName().equals("GuiScaleButton");
            }
        }
        check("client: Video Settings has no 3D Anaglyph and no Use VBOs, and 1.13's numbered GUI Scale", !anaglyph && !vbo && numbered,
                "anaglyph " + anaglyph + " vbo " + vbo + " numbered " + numbered);
        check("client: the removed entries' settings are fixed (anaglyph off, VBOs on)", !mc.gameSettings.anaglyph && mc.gameSettings.useVbo);

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
        check("client: Chat Settings ends with the dummy cycle, Done below it", cycle != null && done != null
                && isOption(cycle, PresentationTests.cycle) && done.y >= cycle.y + 24, cycle == null ? "none" : cycle.x + "," + cycle.y + " done " + done.y);
        int coreOnChat = 0;
        for (GuiButton b : c) {
            if (isOption(b, Presentation.TEXT_BACKGROUND_OPACITY) || isOption(b, Presentation.CHAT_LINE_SPACING) || isOption(b, Presentation.CHAT_DELAY)) {
                coreOnChat++;
            }
        }
        check("client: Chat Settings has Text Background Opacity, Line Spacing and Chat Delay", coreOnChat == 3, coreOnChat);

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

    private static boolean isOption(GuiButton b, com.exiledradio.backportcore.presentation.option.PresentationOption o) {
        return b instanceof OptionWidgets.OptionButton && ((OptionWidgets.OptionButton) b).option() == o;
    }

    private static void finishTitle(Minecraft mc) throws Exception {
        Class.forName("net.minecraft.client.gui.inventory.GuiContainer");
        for (String p : new String[] {"P12", "P13", "P14", "P15", "P16", "P17", "P18", "P22", "P23", "P24", "P25"}) {
            check("client: " + p + " applied", "applied".equals(Asm.state(p)), Asm.state(p));
        }
        com.exiledradio.backportcore.CoreConfig.BlockUpdates mode = com.exiledradio.backportcore.CoreConfig.rendering.blockUpdates;
        try {
            for (com.exiledradio.backportcore.CoreConfig.BlockUpdates m : com.exiledradio.backportcore.CoreConfig.BlockUpdates.values()) {
                com.exiledradio.backportcore.CoreConfig.rendering.blockUpdates = m;
                double d = com.exiledradio.backportcore.render.client.ChunkRebuildHooks.nearbyDistanceSq(768.0D);
                check("client: P17 " + m + ": near-camera distance rule " + (m == com.exiledradio.backportcore.CoreConfig.BlockUpdates.NEARBY ? "kept" : "off"),
                        m == com.exiledradio.backportcore.CoreConfig.BlockUpdates.NEARBY ? d == 768.0D : d <= 0.0D, d);
            }
        } finally {
            com.exiledradio.backportcore.CoreConfig.rendering.blockUpdates = mode;
        }
        check("client: the vanilla asset pack is in", com.exiledradio.backportcore.presentation.client.VanillaAssetPack.isInstalled());
        net.minecraft.client.resources.IResource logo = mc.getResourceManager().getResource(new net.minecraft.util.ResourceLocation("textures/gui/title/minecraft.png"));
        check("client: minecraft:textures/gui/title/minecraft.png comes from Core's pack", "backportcore:vanilla_by_version".equals(logo.getResourcePackName()),
                logo.getResourcePackName());
        net.minecraft.client.resources.IResource widgets = mc.getResourceManager().getResource(new net.minecraft.util.ResourceLocation("textures/gui/widgets.png"));
        check("client: an asset no version replaces is vanilla's", !"backportcore:vanilla_by_version".equals(widgets.getResourcePackName()), widgets.getResourcePackName());
        check("client: vanilla text newest wins (1.16 over 1.14)", "sixteen".equals(net.minecraft.client.resources.I18n.format("selftest.reworded")),
                net.minecraft.client.resources.I18n.format("selftest.reworded"));
        check("client: a vanilla key only 1.14 rewords is 1.14's", "Quit Game (1.14 test)".equals(net.minecraft.client.resources.I18n.format("menu.quit")),
                net.minecraft.client.resources.I18n.format("menu.quit"));
        check("client: a vanilla key 1.16 rewords is 1.16's", "Options... (1.16 test)".equals(net.minecraft.client.resources.I18n.format("menu.options")));
        check("client: and the server-side translation map agrees", "sixteen".equals(net.minecraft.util.text.translation.I18n.translateToLocal("selftest.reworded")));
        check("client: the startup screen drew 1.16's revision at the end of loading",
                com.exiledradio.backportcore.presentation.client.StartupScreen.frames > 0
                        && com.exiledradio.backportcore.presentation.client.StartupScreen.lastDrawn == com.exiledradio.backportcore.presentation.VanillaRevisions.Startup.STUDIOS_1_16,
                com.exiledradio.backportcore.presentation.client.StartupScreen.frames + " " + com.exiledradio.backportcore.presentation.client.StartupScreen.lastDrawn);
        check("client: and is gone after two seconds", !com.exiledradio.backportcore.presentation.client.StartupScreen.showing());
        check("client: the first title screen faded in (1.13)", com.exiledradio.backportcore.presentation.client.TitleScreenExtras.fadeFrames > 0
                && com.exiledradio.backportcore.presentation.client.TitleScreenExtras.fadeDone(), com.exiledradio.backportcore.presentation.client.TitleScreenExtras.fadeFrames);
        List<GuiButton> tb = buttons(mc.currentScreen);
        GuiButton icon = null;
        for (GuiButton b : tb) {
            if (b instanceof com.exiledradio.backportcore.presentation.client.TitleScreenExtras.IconButton) {
                icon = b;
            }
        }
        GuiButton language = find(tb, 5);
        check("client: the title screen has 1.14's Accessibility button, mirroring Language", icon != null && language != null
                && icon.y == language.y && icon.x == mc.currentScreen.width / 2 + 104, icon == null ? "none" : icon.x + "," + icon.y);
        check("client: 1.15's white button text", PresentationHooks.buttonTextColor(14737632) == 0xFFFFFF && PresentationHooks.buttonTextColor(10526880) == 10526880);
        check("client: Core's in-game music ticker is in place (a step is registered)", mc.getMusicTicker() instanceof com.exiledradio.backportcore.presentation.client.GameMusic.CoreTicker,
                mc.getMusicTicker().getClass().getName());
        check("client: the music step is asked every tick, even at the menu", musicAsks > 100, musicAsks);
        check("client: the menu music still comes from the pool through Core's ticker", MenuMusicPlayer.lastPick != null);
        List<String> tip = com.exiledradio.backportcore.presentation.client.OptionTooltips.lines("one\\ntwo");
        check("client: a tooltip keeps its line breaks", tip.size() == 2 && "two".equals(tip.get(1)), tip);
        check("client: the debug text runs oldest version first", com.exiledradio.backportcore.presentation.client.DebugText.steps()
                .names(Presentation.newest()).equals(java.util.Arrays.asList("1.14 selftest older", "1.16 selftest line")),
                com.exiledradio.backportcore.presentation.client.DebugText.steps().names(Presentation.newest()));
    }

    private static void multiplayerWarning(Minecraft mc) throws Exception {
        GuiScreen menu = mc.currentScreen;
        com.exiledradio.backportcore.presentation.option.OptionStore.put(com.exiledradio.backportcore.presentation.client.TitleScreenExtras.SKIP_WARNING, "false");
        mc.displayGuiScreen(new net.minecraft.client.gui.GuiMultiplayer(menu));
        check("client: Multiplayer from the title screen shows the warning first",
                mc.currentScreen instanceof com.exiledradio.backportcore.presentation.client.MultiplayerWarningScreen, mc.currentScreen);
        GuiScreen warning = mc.currentScreen;
        List<GuiButton> wb = buttons(warning);
        check("client: the warning has Proceed, Back and the check box", wb.size() == 3, describe(wb));
        java.lang.reflect.Method action = ReflectionHelper.findMethod(GuiScreen.class, "actionPerformed", "func_146284_a", GuiButton.class);
        action.invoke(warning, find(wb, 2));
        action.invoke(warning, find(wb, 0));
        check("client: Proceed opens the server list", mc.currentScreen instanceof net.minecraft.client.gui.GuiMultiplayer, mc.currentScreen);
        check("client: the ticked box is remembered", "true".equals(com.exiledradio.backportcore.presentation.option.OptionStore.get(
                com.exiledradio.backportcore.presentation.client.TitleScreenExtras.SKIP_WARNING)));
        mc.displayGuiScreen(menu);
        mc.displayGuiScreen(new net.minecraft.client.gui.GuiMultiplayer(menu));
        check("client: after that the server list opens straight away", mc.currentScreen instanceof net.minecraft.client.gui.GuiMultiplayer, mc.currentScreen);
        mc.displayGuiScreen(menu);
        com.exiledradio.backportcore.presentation.option.OptionStore.put(com.exiledradio.backportcore.presentation.client.TitleScreenExtras.SKIP_WARNING, "false");
    }

    private static void finishWorld(Minecraft mc) throws Exception {
        check("client: P13: Core's chat drew (1.14 and later)", com.exiledradio.backportcore.presentation.client.ChatRenderer.frames > 0,
                com.exiledradio.backportcore.presentation.client.ChatRenderer.frames);
        check("client: the held line came out after the delay", com.exiledradio.backportcore.presentation.client.ChatRenderer.queued() == 0,
                com.exiledradio.backportcore.presentation.client.ChatRenderer.queued());
        Presentation.CHAT_DELAY.set(0.0);
        check("client: the debug text steps ran while F3 showed", debugEdits > 0
                && com.exiledradio.backportcore.presentation.client.DebugText.frames > 0, debugEdits);
        check("client: P15/P16: the sky and cloud steps were asked", skyAsks > 0 && cloudAsks > 0, "sky " + skyAsks + " clouds " + cloudAsks);
        check("client: the cloud step moves the cloud layer too", PresentationHooks.cloudLayer(128.0D) == mc.world.provider.getCloudHeight() + 64.0F,
                PresentationHooks.cloudLayer(128.0D));
        check("client: the in-game music choice was made", com.exiledradio.backportcore.presentation.client.GameMusic.lastChoice != null,
                com.exiledradio.backportcore.presentation.client.GameMusic.lastChoice);

        net.minecraft.client.gui.GuiIngameMenu pause = new net.minecraft.client.gui.GuiIngameMenu();
        mc.displayGuiScreen(pause);
        List<GuiButton> pb = buttons(pause);
        int top = pause.height / 4 - 16;
        GuiButton feedback = null;
        GuiButton bugs = null;
        for (GuiButton b : pb) {
            if (b instanceof OptionWidgets.Action && b.y == top + 72) {
                if (b.x == pause.width / 2 - 102) {
                    feedback = b;
                } else if (b.x == pause.width / 2 + 4) {
                    bugs = b;
                }
            }
        }
        GuiButton back = find(pb, 4);
        GuiButton mods = find(pb, 12);
        GuiButton quit = find(pb, 1);
        check("client: pause menu: Mod Options in Give Feedback's place, Report Bugs beside it, 204 wide grid",
                mods != null && mods.y == top + 72 && mods.x == pause.width / 2 - 102 && mods.width == 98
                        && feedback == null && bugs != null && back != null && back.width == 204 && back.x == pause.width / 2 - 102, describe(pb));
        check("client: pause menu: nothing below Save and Quit", quit != null && quit.y == top + 120 && !below(pb, top + 120), describe(pb));
        check("client: Report Bugs opens the configured link (Discord by default)",
                "https://discord.gg/kxQvMDJBTN".equals(com.exiledradio.backportcore.CoreConfig.presentation.reportBugsLink),
                com.exiledradio.backportcore.CoreConfig.presentation.reportBugsLink);
        mc.displayGuiScreen(null);

        java.lang.reflect.Method orderOf = com.exiledradio.backportcore.presentation.client.CreativeOrder.class.getDeclaredMethod("order", net.minecraft.util.ResourceLocation.class);
        orderOf.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Integer> o = (java.util.Map<String, Integer>) orderOf.invoke(null, Presentation.creativeOrder());
        java.lang.reflect.Method rank = com.exiledradio.backportcore.presentation.client.CreativeOrder.class.getDeclaredMethod("rank", java.util.Map.class,
                net.minecraft.item.ItemStack.class);
        rank.setAccessible(true);
        int sword = (Integer) rank.invoke(null, o, new net.minecraft.item.ItemStack(net.minecraft.init.Items.DIAMOND_SWORD));
        int stone = (Integer) rank.invoke(null, o, new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.STONE));
        int granite = (Integer) rank.invoke(null, o, new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.STONE, 1, 1));
        int red = (Integer) rank.invoke(null, o, new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.WOOL, 1, 14));
        check("client: the creative order ranks by name@meta, then name, others last", sword == 0 && stone == 1 && red == 2 && granite == Integer.MAX_VALUE,
                sword + " " + stone + " " + red + " " + granite);
        net.minecraft.client.gui.inventory.GuiContainerCreative creative = new net.minecraft.client.gui.inventory.GuiContainerCreative(mc.player);
        mc.displayGuiScreen(creative);
        java.lang.reflect.Method setTab = ReflectionHelper.findMethod(net.minecraft.client.gui.inventory.GuiContainerCreative.class, "setCurrentCreativeTab",
                "func_147050_b", net.minecraft.creativetab.CreativeTabs.class);
        setTab.invoke(creative, net.minecraft.creativetab.CreativeTabs.COMBAT);
        int sortsBefore = com.exiledradio.backportcore.presentation.client.CreativeOrder.sorts;
        MinecraftForge.EVENT_BUS.post(new net.minecraftforge.client.event.GuiScreenEvent.DrawScreenEvent.Pre(creative, 0, 0, 0.0F));
        net.minecraft.util.NonNullList<net.minecraft.item.ItemStack> items = ((net.minecraft.client.gui.inventory.GuiContainerCreative.ContainerCreative) creative.inventorySlots).itemList;
        check("client: the open creative tab is sorted by the newest order (the diamond sword first in Combat)",
                com.exiledradio.backportcore.presentation.client.CreativeOrder.sorts > sortsBefore && !items.isEmpty()
                        && items.get(0).getItem() == net.minecraft.init.Items.DIAMOND_SWORD, items.isEmpty() ? "empty" : items.get(0));
        mc.displayGuiScreen(null);
    }

    private static void backups(Minecraft mc) {
        try {
            File out = new File(mc.gameDir, "selftest-backups");
            FileUtils.deleteDirectory(out);
            long size = com.exiledradio.backportcore.presentation.client.WorldBackups.backup(new File(mc.gameDir, "saves"), WORLD, out);
            File[] zips = out.listFiles();
            boolean named = zips != null && zips.length == 1 && zips[0].getName().matches("\\d{4}-\\d\\d-\\d\\d_\\d\\d-\\d\\d-\\d\\d_" + WORLD + "\\.zip");
            boolean hasLevel = false;
            if (named) {
                try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(zips[0])) {
                    hasLevel = z.getEntry(WORLD + "/level.dat") != null && z.getEntry(WORLD + "/session.lock") == null;
                }
            }
            check("client: Make Backup zips the world as 1.13 names it, level.dat in, session.lock out", size > 0 && named && hasLevel,
                    zips == null ? "none" : java.util.Arrays.toString(zips));
        } catch (Exception e) {
            SelfTestRunner.fail("client: backup threw", e.toString());
        }
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

    private static boolean below(List<GuiButton> buttons, int y) {
        for (GuiButton b : buttons) {
            if (b.visible && b.y > y) {
                return true;
            }
        }
        return false;
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

package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.creative.client.CreativeScreenHooks;
import com.exiledradio.backportcore.creative.client.ModernTab;
import com.exiledradio.backportcore.creative.client.ModernTabs;
import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.client.CreateWorldTabs;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.client.Credits;
import com.exiledradio.backportcore.presentation.client.MenuBackground;
import com.exiledradio.backportcore.presentation.client.MessageScreens;
import com.exiledradio.backportcore.presentation.client.Widgets;
import com.exiledradio.backportcore.presentation.client.WorldLoadingScreen;
import com.exiledradio.backportcore.presentation.client.PanoramaRenderer;
import com.exiledradio.backportcore.presentation.client.PresentationHooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.io.FileUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Dev only, environment variable {@code BACKPORTCORE_MENUSHOTS=1} with {@code runClient -Pselftest}: 1.20.5's menus
 * (P31), panorama motion and Narrator Hotkey (P32). Registers a dummy 1.20.5 unless a real 1.20.5 backport is loaded
 * ({@code -PwithMods=}Trails &amp; Tales Backport's dev jar), opens screens in code outside and inside a world, saves each
 * to {@code screenshots/menu-*.png}, logs {@code MENU SHOTS DONE pass=N fail=M} and closes the game.
 */
@SideOnly(Side.CLIENT)
final class ClientMenuShots {

    static final String WORLD = "backportcore_menushots";

    static boolean active() {
        return System.getenv("BACKPORTCORE_MENUSHOTS") != null;
    }

    static void register() {
        if (!Loader.isModLoaded("trailsandtalesbackport") && !Loader.isModLoaded("gamedropsbackport")) {
            Presentation.version("1.20.5");
        }
        if (!Loader.isModLoaded("buzzybeesbackport")) {
            // The multiplayer warning is Buzzy Bees' (1.15.2); its keys and 26.3's English stand in for it.
            Presentation.version("1.15.2").multiplayerWarning("multiplayerWarning.header", "multiplayerWarning.message", "multiplayerWarning.check")
                    .vanillaText(new net.minecraft.util.ResourceLocation("backportcore_selftest", "vanilla_text_warning"));
        }
        MinecraftForge.EVENT_BUS.register(new ClientMenuShots());
    }

    private final List<String> names = new ArrayList<String>();
    private final List<Supplier<Boolean>> steps = new ArrayList<Supplier<Boolean>>();
    private int step = -1;
    private int ticks;
    private String shot;
    private int pass;
    private int fail;
    private int menuAt;
    private int worldAt;
    private int listAt;
    private int waited;
    private float yawAt;

    private ClientMenuShots() {
        Minecraft mc = Minecraft.getMinecraft();
        OptionsRowsShots.first(this, mc);
        add("title", () -> {
            check("P31 (screens) applied", "applied".equals(Asm.state("P31")), Asm.state("P31"));
            check("P32 applied", "applied".equals(Asm.state("P32")), Asm.state("P32"));
            check("1.20.5 is the newest", Presentation.isAtLeast("1.20.5") && MenuBackground.active(), Presentation.newest());
            return true;
        });
        add(null, () -> {
            float[] a = PanoramaRenderer.lastAngles();
            check("the title panorama holds its pitch at 10", a[0] == 10.0F, a[0]);
            check("the title screen is not blurred (1.20.5's title has no menu background)", MenuBackground.menuFrames == 0, MenuBackground.menuFrames);
            return true;
        });
        add("options", () -> show(new GuiOptions(new GuiMainMenu(), mc.gameSettings)));
        add(null, () -> {
            check("options: the menu background drawn", MenuBackground.menuFrames > 0, MenuBackground.menuFrames);
            check("options: blurred", !OpenGlHelper.shadersSupported || MenuBackground.blurFrames > 0, MenuBackground.blurFrames);
            this.menuAt = MenuBackground.menuFrames;
            this.yawAt = PanoramaRenderer.lastAngles()[1];
            return true;
        });
        headerAndFooter(mc);
        OptionsRowsShots.steps(this, mc);
        add("video", () -> show(new GuiVideoSettings(new GuiMainMenu(), mc.gameSettings)));
        add("language", () -> show(new GuiLanguage(new GuiMainMenu(), mc.gameSettings, mc.getLanguageManager())));
        add("worlds", () -> show(new GuiWorldSelection(new GuiMainMenu())));
        add(null, () -> {
            check("P31 (lists) applied", "applied".equals(Asm.state("P31-list")), Asm.state("P31-list"));
            check("lists: drawn as 1.20.5's", MenuBackground.listFrames > 0, MenuBackground.listFrames);
            check("lists: the screen behind the world list is drawn (it has no background of its own)",
                    MenuBackground.menuFrames > this.menuAt, MenuBackground.menuFrames + " after " + this.menuAt);
            check("lists: color writes are back on after the list", colorMaskOn());
            check("lists: the scissor is off after the list", !GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
            return true;
        });
        add("packs", () -> show(new GuiScreenResourcePacks(new GuiMainMenu())));
        add(null, () -> {
            com.exiledradio.backportcore.presentation.option.OptionStore.put(
                    com.exiledradio.backportcore.presentation.client.TitleScreenExtras.SKIP_WARNING, "false");
            return show(new GuiMainMenu());
        });
        add("warning", () -> show(new GuiMultiplayer(mc.currentScreen)));
        add(null, () -> {
            GuiScreen s = mc.currentScreen;
            boolean warning = s instanceof com.exiledradio.backportcore.presentation.client.MultiplayerWarningScreen;
            check("the title screen's Multiplayer opens the multiplayer warning first", warning, s);
            if (warning) {
                com.exiledradio.backportcore.presentation.client.MultiplayerWarningScreen w = (com.exiledradio.backportcore.presentation.client.MultiplayerWarningScreen) s;
                boolean boxed = Presentation.isAtLeast(com.exiledradio.backportcore.presentation.LookArea.MENUS, "1.21.9");
                check("the warning in 1.21.9's boxed column (26.3)", !boxed || w.layout() == com.exiledradio.backportcore.presentation.VanillaRevisions.WarningLayout.BOXED_1_21_9, w.layout());
            }
            return true;
        });
        add("multiplayer", () -> show(new GuiMultiplayer(new GuiMainMenu())));
        add(null, () -> {
            check("Play Multiplayer: Back for Cancel (1.20.2)", !Presentation.isAtLeast(com.exiledradio.backportcore.presentation.LookArea.WORDING, "1.20.2")
                    || hasButton(mc.currentScreen, net.minecraft.client.resources.I18n.format("gui.back")), mc.currentScreen);
            return true;
        });
        add(null, () -> show(new net.minecraftforge.fml.client.GuiModList(new GuiMainMenu())));
        add("modlist", () -> selectCore(mc));
        add(null, () -> {
            float now = PanoramaRenderer.lastAngles()[1];
            this.listAt = MenuBackground.listFrames;
            check("the panorama kept turning across five screens (one spin, never restarted)", now < this.yawAt - 5.0F,
                    now + " after " + this.yawAt);
            return true;
        });
        screens263(mc);
        // The world is made on 1.19.4's tabbed Create New World (CreateWorldTabs), by clicks, as a player would.
        createWorld(mc);
        add(null, () -> mc.world != null && mc.player != null && ++this.waited > 4);
        add(null, () -> {
            net.minecraft.world.storage.WorldInfo info = mc.getIntegratedServer().getWorld(0).getWorldInfo();
            check("create world: Creative, as 1.12.2's screen makes it", info.getGameType() == GameType.CREATIVE && !info.isHardcoreModeEnabled(),
                    info.getGameType());
            check("create world: the typed seed", info.getSeed() == 20261116L, info.getSeed());
            check("create world: structures on, Default", info.isMapFeaturesEnabled() && info.getTerrainType() == WorldType.DEFAULT,
                    info.getTerrainType().getName());
            check("create world: commands on (1.12.2 turns them on with Creative)", info.areCommandsAllowed());
            check("create world: the world's name and folder", WORLD.equals(info.getWorldName())
                    && WORLD.equals(mc.getIntegratedServer().getFolderName()), info.getWorldName());
            check("create world: the chosen Difficulty", info.getDifficulty() == this.chosenDifficulty
                    && com.exiledradio.backportcore.gamerules.NewWorldDifficulty.lastApplied == this.chosenDifficulty, info.getDifficulty());
            check("create world: the Difficulty option follows the joined world (1.12.2's handleJoinGame)",
                    mc.gameSettings.difficulty == this.chosenDifficulty, mc.gameSettings.difficulty);
            return true;
        });
        add("pause", () -> {
            this.worldAt = MenuBackground.worldFrames;
            return show(new GuiIngameMenu());
        });
        add(null, () -> {
            check("pause: the blurred world under inworld_menu_background", MenuBackground.worldFrames > this.worldAt, MenuBackground.worldFrames);
            return true;
        });
        add(null, () -> show(new net.minecraftforge.fml.client.GuiModList(new GuiIngameMenu())));
        add("ingame-modlist", () -> selectCore(mc));
        add("ingame-video", () -> show(new GuiVideoSettings(new GuiIngameMenu(), mc.gameSettings)));
        add(null, () -> {
            check("in a world: lists as 1.20.5's", MenuBackground.listFrames > this.listAt, MenuBackground.listFrames);
            return true;
        });
        add(null, () -> {
            mc.gameSettings.pauseOnLostFocus = false; // the dev window is not focused: no pause menu over the charts
            mc.displayGuiScreen(null);
            com.exiledradio.backportcore.presentation.DebugKeys.Key k = com.exiledradio.backportcore.presentation.DebugKeys
                    .map(Presentation.installed()).get(org.lwjgl.input.Keyboard.KEY_3);
            check("F3 + 3 is in the map from 1.20.2, with no F3 + Q line", k != null && k.helpKey == null && k.action != null);
            mc.gameSettings.showDebugInfo = false;
            com.exiledradio.backportcore.presentation.client.NetworkCharts.toggle();
            check("F3 + 3 shows the charts and the debug screen", com.exiledradio.backportcore.presentation.client.NetworkCharts.showing()
                    && mc.gameSettings.showDebugInfo && !mc.gameSettings.showLagometer);
            io.netty.channel.embedded.EmbeddedChannel ch = new io.netty.channel.embedded.EmbeddedChannel(
                    new com.exiledradio.backportcore.presentation.client.NetworkCharts.Counter());
            ch.writeInbound(io.netty.buffer.Unpooled.wrappedBuffer(new byte[1234]));
            ch.finishAndReleaseAll();
            return true;
        });
        add(null, () -> {
            BackportCore.LOGGER.info("MENU network charts: {} pings, {} pongs, {} samples",
                    com.exiledradio.backportcore.presentation.client.NetworkCharts.pings,
                    com.exiledradio.backportcore.presentation.client.NetworkCharts.pongs,
                    com.exiledradio.backportcore.presentation.client.NetworkCharts.PING.size());
            return true;
        });
        // A chart 213 wide shows only the newest 211 of 240 samples (vanilla's skip), so the shot waits for 29.
        add("network-charts", () -> {
            com.exiledradio.backportcore.presentation.DebugCharts.Samples b = com.exiledradio.backportcore.presentation.client.NetworkCharts.BANDWIDTH;
            boolean counted = false;
            for (int i = 0; i < b.size(); i++) {
                counted |= b.get(i) == 1234;
            }
            check("bandwidth: the bytes a connection receives are logged in the tick", counted, b.size());
            check("ping: the integrated server (with Core) answers each tick's request",
                    com.exiledradio.backportcore.presentation.client.NetworkCharts.pongs > 5
                            && com.exiledradio.backportcore.presentation.client.NetworkCharts.PING.size() > 5,
                    com.exiledradio.backportcore.presentation.client.NetworkCharts.pings + " sent, "
                            + com.exiledradio.backportcore.presentation.client.NetworkCharts.pongs + " back");
            check("charts: drawn", com.exiledradio.backportcore.presentation.client.NetworkCharts.frames > 0);
            String line = com.exiledradio.backportcore.presentation.client.NetworkCharts.chartsLine(mc);
            check("the debug line is 1.20.2's", line.equals("Debug charts: [F3+1] Profiler hidden; [F3+2] FPS + TPS hidden; [F3+3] Ping visible"), line);
            return true;
        });
        add(null, () -> {
            mc.gameSettings.showLagometer = true;
            return true;
        });
        add(null, () -> {
            check("F3 + 2's charts turn F3 + 3's off", !com.exiledradio.backportcore.presentation.client.NetworkCharts.showing());
            mc.gameSettings.showLagometer = false;
            mc.gameSettings.showDebugInfo = false;
            return true;
        });
        add("inventory", () -> {
            show(new GuiInventory(mc.player));
            this.worldAt = MenuBackground.worldFrames;
            return true;
        });
        add(null, () -> {
            check("inventory: the transparent gradient kept (no blur)", MenuBackground.worldFrames == this.worldAt,
                    MenuBackground.worldFrames + " after " + this.worldAt);
            check("Narrator Hotkey shows from 1.20.2, on", Presentation.NARRATOR_HOTKEY.isShown() && PresentationHooks.narratorHotkeyOn());
            Presentation.NARRATOR_HOTKEY.set(false);
            check("Narrator Hotkey off: Ctrl + B is not the narrator's", !PresentationHooks.narratorHotkeyOn());
            Presentation.NARRATOR_HOTKEY.set(true);
            mc.displayGuiScreen(null);
            return true;
        });
        creativeTabs(mc);
        saving(mc);
    }

    // ---- Core 1.4.5: the question and message screens, widgets and credits (MessageScreens, Widgets, Credits) ----

    private int frames;

    private void screens263(Minecraft mc) {
        add("confirm", () -> show(new net.minecraft.client.gui.GuiYesNo((r, id) -> mc.displayGuiScreen(new GuiMainMenu()),
                net.minecraft.client.resources.I18n.format("selectWorld.deleteQuestion"),
                "'New World' " + net.minecraft.client.resources.I18n.format("selectWorld.deleteWarning"),
                net.minecraft.client.resources.I18n.format("selectWorld.deleteButton"), net.minecraft.client.resources.I18n.format("gui.cancel"), 0)));
        add(null, () -> {
            VanillaRevisions.ConfirmLayout layout = VanillaRevisions.confirmLayout();
            GuiScreen gui = mc.currentScreen;
            List<String> lines = MessageScreens.lines(gui);
            check("confirm: laid out as " + layout, layout == VanillaRevisions.ConfirmLayout.CLASSIC ? lines == null
                    : lines != null && MessageScreens.confirmFrames > 0, MessageScreens.confirmFrames);
            if (lines != null) {
                String title = net.minecraft.client.resources.I18n.format("selectWorld.deleteQuestion");
                int[] p = MessageScreens.confirmPositions(layout, mc.fontRenderer, gui.width, gui.height, title, lines);
                GuiButton yes = button(gui, 0);
                GuiButton no = button(gui, 1);
                check("confirm: the buttons where " + layout + " puts them", yes != null && no != null && yes.x == p[4] && no.x == p[5]
                        && yes.y == p[6] && no.y == p[6] && yes.width == 150, (yes == null ? "-" : yes.x + "," + yes.y) + " want " + p[4] + "," + p[6]);
                if (layout == VanillaRevisions.ConfirmLayout.COLUMN_1_21_6) {
                    int h = 9 + 8 + lines.size() * 9 + 8 + 36;
                    check("confirm: 26.3's column is centered (its top half the free height down)", p[1] == (int) ((gui.height - h) * 0.5F), p[1]);
                }
            }
            return true;
        });
        add("confirm-empty", () -> show(new net.minecraft.client.gui.GuiYesNo((r, id) -> mc.displayGuiScreen(new GuiMainMenu()),
                net.minecraft.client.resources.I18n.format("deathScreen.quit.confirm"), "",
                net.minecraft.client.resources.I18n.format("deathScreen.titleScreen"), net.minecraft.client.resources.I18n.format("deathScreen.respawn"), 0)));
        add(null, () -> {
            List<String> lines = MessageScreens.lines(mc.currentScreen);
            check("confirm: an empty message takes no line (26.3 splits an empty text into none)", lines == null || lines.isEmpty(), lines);
            return true;
        });
        add("disconnected", () -> show(new net.minecraft.client.gui.GuiDisconnected(new net.minecraft.client.gui.GuiMultiplayer(new GuiMainMenu()),
                "disconnect.lost", new net.minecraft.util.text.TextComponentTranslation("disconnect.timeout"))));
        add(null, () -> {
            VanillaRevisions.DisconnectedLayout layout = VanillaRevisions.disconnectedLayout();
            GuiButton back = button(mc.currentScreen, 0);
            int want = layout == VanillaRevisions.DisconnectedLayout.GRID_1_20 ? 150 : 200;
            check("disconnected: drawn as " + layout, layout == VanillaRevisions.DisconnectedLayout.CLASSIC || MessageScreens.disconnectedFrames > 0,
                    MessageScreens.disconnectedFrames);
            check("disconnected: the button " + want + " wide", back != null && back.width == want, back == null ? null : back.width);
            return true;
        });
        add("out-of-memory", () -> show(new net.minecraft.client.gui.GuiMemoryErrorScreen()));
        add(null, () -> {
            check("out of memory: 1.20.5's layout drawn", !VanillaRevisions.outOfMemoryLayout() || MessageScreens.memoryFrames > 0, MessageScreens.memoryFrames);
            GuiButton quit = button(mc.currentScreen, 1);
            check("out of memory: Quit Game in the 33-high footer", !VanillaRevisions.outOfMemoryLayout()
                    || quit != null && quit.y == mc.currentScreen.height - 27, quit == null ? null : quit.y);
            return true;
        });
        add("working", () -> show(new net.minecraft.client.gui.GuiScreenWorking()));
        add(null, () -> {
            check("working: drawn without the empty progress line", !VanillaRevisions.progressLineWhenStarted() || MessageScreens.workingFrames > 0,
                    MessageScreens.workingFrames);
            return true;
        });
        add("warning-checkbox", () -> {
            try {
                java.lang.reflect.Constructor<?> c = Class.forName("com.exiledradio.backportcore.presentation.client.MultiplayerWarningScreen")
                        .getDeclaredConstructor(GuiScreen.class, String[].class);
                c.setAccessible(true);
                return show((GuiScreen) c.newInstance(new GuiMainMenu(),
                        (Object) new String[] {"multiplayerWarning.header", "multiplayerWarning.message", "multiplayerWarning.check"}));
            } catch (ReflectiveOperationException e) {
                failed("warning screen", e);
                return true;
            }
        });
        add(null, () -> {
            check("check box: 1.20.2's sprites when supplied (" + Widgets.spriteCheckbox() + ")", !Widgets.spriteCheckbox() || Widgets.checkboxFrames > 0,
                    Widgets.checkboxFrames);
            GuiButton box = button(mc.currentScreen, 2);
            check("check box: 17 high with the sprites", !Widgets.spriteCheckbox() || box != null && box.height == Widgets.BOX, box == null ? null : box.height);
            return true;
        });
        add("text-field", () -> show(new net.minecraft.client.gui.GuiScreenAddServer(new GuiMainMenu(),
                new net.minecraft.client.multiplayer.ServerData(net.minecraft.client.resources.I18n.format("selectServer.defaultName"), "", false))));
        add(null, () -> {
            check("P61 applied", "applied".equals(Asm.state("P61")), Asm.state("P61"));
            check("P62 applied", "applied".equals(Asm.state("P62")), Asm.state("P62"));
            net.minecraft.client.gui.GuiTextField field = new net.minecraft.client.gui.GuiTextField(0, mc.fontRenderer, 0, 0, 100, 20);
            field.setFocused(true);
            check("a focused text field's frame is white from 1.16", PresentationHooks.textFieldFrame(field, -6250336)
                    == (VanillaRevisions.textFieldFocusFrame() ? -1 : -6250336));
            field.setFocused(false);
            check("an unfocused text field's frame stays grey", PresentationHooks.textFieldFrame(field, -6250336) == -6250336);
            GuiButton slider = new GuiButton(0, 0, 0, "");
            net.minecraftforge.fml.common.ObfuscationReflectionHelper.setPrivateValue(GuiButton.class, slider, true, "field_146123_n");
            check("a hovered slider's handle is the hovered button's row from 1.14",
                    PresentationHooks.sliderHandleRow(slider, 66) == (VanillaRevisions.sliderHandleHover() ? 86 : 66));
            return true;
        });
        add("credits", () -> {
            net.minecraft.client.gui.GuiWinGame credits = new net.minecraft.client.gui.GuiWinGame(false, () -> { });
            show(credits);
            // Scrolled to the first section (1.12.2's screen starts below the bottom edge).
            net.minecraftforge.fml.common.ObfuscationReflectionHelper.setPrivateValue(net.minecraft.client.gui.GuiWinGame.class, credits, 700.0F, "field_146581_h");
            return true;
        });
        add(null, () -> {
            boolean json = VanillaRevisions.jsonCredits() && Presentation.asset("texts/credits.json") != null;
            List<String> lines = Credits.lastLines;
            check("credits: 26.3's credits.json when a backport supplies it (" + json + ")", !json || lines != null && lines.size() > 100
                    && lines.get(0).endsWith("============") && lines.get(1).contains("Mojang Studios"), lines == null ? null : lines.size());
            mc.displayGuiScreen(new GuiMainMenu());
            return true;
        });
    }

    /** Leaves the world last: "Saving world" is drawn by the loading screen inside loadWorld, so the shot is taken there. */
    private void saving(Minecraft mc) {
        if (Loader.isModLoaded("trailsandtalesbackport")) {
            // Trails & Tales' sign editor (it swaps itself in for GuiEditSign): 26.2's flat oak sign from Game Drops' texture.
            add("sign", () -> {
                net.minecraft.util.math.BlockPos at = mc.player.getPosition().offset(mc.player.getHorizontalFacing(), 2).up();
                mc.getIntegratedServer().getWorld(0).setBlockState(at, Blocks.STANDING_SIGN.getDefaultState());
                mc.world.setBlockState(at, Blocks.STANDING_SIGN.getDefaultState());
                net.minecraft.tileentity.TileEntity te = mc.world.getTileEntity(at);
                if (te instanceof net.minecraft.tileentity.TileEntitySign) {
                    return show(new net.minecraft.client.gui.inventory.GuiEditSign((net.minecraft.tileentity.TileEntitySign) te));
                }
                check("sign: a client sign tile to edit", false, te);
                return true;
            });
            add(null, () -> {
                check("sign: Trails & Tales' editor open", mc.currentScreen != null && mc.currentScreen.getClass().getSimpleName().equals("SignEditScreen"),
                        mc.currentScreen);
                check("sign: vanilla's oak gui/signs texture supplied from 26.2", !Presentation.isAtLeast("26.2")
                        || Presentation.asset("textures/gui/signs/oak.png") != null, Presentation.asset("textures/gui/signs/oak.png"));
                mc.displayGuiScreen(null);
                return true;
            });
        }
        add(null, () -> {
            this.frames = WorldLoadingScreen.savingFrames;
            mc.displayGuiScreen(null);
            mc.world.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            ScreenShotHelper.saveScreenshot(mc.gameDir, "menu-saving.png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            mc.displayGuiScreen(new GuiMainMenu());
            check("leaving the world: Saving world drawn as " + VanillaRevisions.messageScreen(),
                    VanillaRevisions.messageScreen() == VanillaRevisions.MessageScreen.NONE || WorldLoadingScreen.savingFrames > this.frames,
                    WorldLoadingScreen.savingFrames);
            return true;
        });
    }

    @javax.annotation.Nullable
    private static GuiButton button(@javax.annotation.Nullable GuiScreen gui, int id) {
        if (gui == null) {
            return null;
        }
        List<GuiButton> buttons = net.minecraftforge.fml.common.ObfuscationReflectionHelper.getPrivateValue(GuiScreen.class, gui, "field_146292_n");
        for (GuiButton b : buttons) {
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }

    // ---- 1.19.4's Create New World (presentation.client.CreateWorldTabs) ----

    private net.minecraft.client.gui.GuiCreateWorld create;
    private net.minecraft.world.EnumDifficulty optionDifficulty;
    private net.minecraft.world.EnumDifficulty chosenDifficulty;

    private void createWorld(Minecraft mc) {
        add(null, () -> {
            try {
                FileUtils.deleteDirectory(new File(mc.gameDir, "saves/" + WORLD));
            } catch (java.io.IOException e) {
                failed("delete world", e);
            }
            this.optionDifficulty = mc.gameSettings.difficulty;
            this.create = new net.minecraft.client.gui.GuiCreateWorld(new GuiWorldSelection(new GuiMainMenu()));
            return show(this.create);
        });
        add("create-game", () -> {
            net.minecraft.client.gui.GuiCreateWorld g = this.create;
            int w = g.width;
            int h = g.height;
            check("create world: the tabbed screen, Game tab first", CreateWorldTabs.applies(g) && CreateWorldTabs.tab(g) == CreateWorldTabs.GAME
                    && CreateWorldTabs.frames > 0, CreateWorldTabs.frames);
            check("create world: Difficulty starts at the Difficulty option (1.12.2's new worlds take it)",
                    CreateWorldTabs.difficulty(g) == mc.gameSettings.difficulty, CreateWorldTabs.difficulty(g));
            net.minecraft.client.gui.GuiButton mode = byId(g, 2);
            net.minecraft.client.gui.GuiButton cheats = byId(g, 6);
            net.minecraft.client.gui.GuiButton difficulty = CreateWorldTabs.difficultyButton(g);
            check("create world: Game Mode: Survival", "Game Mode: Survival".equals(mode.displayString), mode.displayString);
            check("create world: Difficulty: " + mc.gameSettings.difficulty, difficulty.displayString.equals("Difficulty: "
                    + net.minecraft.client.resources.I18n.format(mc.gameSettings.difficulty.getTranslationKey())), difficulty.displayString);
            check("create world: Allow Commands: OFF (1.20.5's words)", "Allow Commands: OFF".equals(cheats.displayString), cheats.displayString);
            int gx = (w - 210) / 2;
            check("create world: Game tab column 210 wide at the middle, 28 apart", mode.x == gx && mode.width == 210 && difficulty.x == gx
                    && difficulty.y == mode.y + 28 && cheats.y == difficulty.y + 28, mode.x + "," + mode.y + " " + difficulty.y + " " + cheats.y);
            net.minecraft.client.gui.GuiButton createButton = byId(g, 0);
            net.minecraft.client.gui.GuiButton cancel = byId(g, 1);
            boolean modern = MenuBackground.active();
            int fy = modern ? h - 27 : h - 28;
            int fx = (int) (0.5F * (w - (modern ? 308 : 310)));
            check("create world: Create New World and Cancel in the footer", createButton.x == fx && createButton.y == fy && cancel.y == fy
                    && cancel.x == fx + (modern ? 158 : 160), createButton.x + "," + createButton.y + " " + cancel.x);
            List<net.minecraft.client.gui.GuiButton> tabs = CreateWorldTabs.tabButtons(g);
            int tabsWidth = Math.min(400, w) - 28;
            check("create world: three tabs along the top", tabs.size() == 3 && tabs.get(0).y == 0 && tabs.get(1).x == tabs.get(0).x + tabs.get(0).width
                    && tabs.get(0).width % 2 == 0 && tabs.get(0).width >= tabsWidth / 3, tabs.isEmpty() ? "none" : tabs.get(0).x + "+" + tabs.get(0).width);
            check("create world: Game, World, More", tabs.size() == 3 && "Game".equals(tabs.get(0).displayString)
                    && "World".equals(tabs.get(1).displayString) && "More".equals(tabs.get(2).displayString));
            check("create world: only the Game tab's buttons show", mode.visible && !byId(g, 5).visible && !byId(g, 4).visible
                    && !byId(g, 3).visible && (CreateWorldTabs.gameRulesButton(g) == null || !CreateWorldTabs.gameRulesButton(g).visible));
            return true;
        });
        add(null, () -> {
            net.minecraft.client.gui.GuiCreateWorld g = this.create;
            click(g, byId(g, 2));
            return true;
        });
        add("create-game-hardcore", () -> {
            net.minecraft.client.gui.GuiCreateWorld g = this.create;
            net.minecraft.client.gui.GuiButton difficulty = CreateWorldTabs.difficultyButton(g);
            net.minecraft.client.gui.GuiButton cheats = byId(g, 6);
            check("create world: Hardcore", "Game Mode: Hardcore".equals(byId(g, 2).displayString), byId(g, 2).displayString);
            check("create world: Hardcore locks Difficulty at Hard", !difficulty.enabled && "Difficulty: Hard".equals(difficulty.displayString),
                    difficulty.displayString);
            check("create world: Hardcore turns Allow Commands off", !cheats.enabled && "Allow Commands: OFF".equals(cheats.displayString),
                    cheats.displayString);
            return true;
        });
        add(null, () -> {
            net.minecraft.client.gui.GuiCreateWorld g = this.create;
            click(g, byId(g, 2));
            this.chosenDifficulty = this.optionDifficulty == net.minecraft.world.EnumDifficulty.PEACEFUL
                    ? net.minecraft.world.EnumDifficulty.EASY : net.minecraft.world.EnumDifficulty.PEACEFUL;
            for (int i = 0; i < 4 && CreateWorldTabs.difficulty(g) != this.chosenDifficulty; i++) {
                click(g, CreateWorldTabs.difficultyButton(g));
            }
            check("create world: Difficulty cycles", CreateWorldTabs.difficulty(g) == this.chosenDifficulty, CreateWorldTabs.difficulty(g));
            setText(g, "worldNameField", "field_146333_g", WORLD);
            try {
                net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(net.minecraft.client.gui.GuiCreateWorld.class, "calcSaveDirName",
                        "func_146314_g").invoke(g);
            } catch (Exception e) {
                failed("calcSaveDirName", e);
            }
            click(g, CreateWorldTabs.tabButtons(g).get(1));
            return true;
        });
        add("create-world", () -> {
            net.minecraft.client.gui.GuiCreateWorld g = this.create;
            net.minecraft.client.gui.GuiButton type = byId(g, 5);
            net.minecraft.client.gui.GuiButton customize = byId(g, 8);
            net.minecraft.client.gui.GuiButton features = byId(g, 4);
            net.minecraft.client.gui.GuiButton bonus = byId(g, 7);
            check("create world: the World tab", CreateWorldTabs.tab(g) == CreateWorldTabs.WORLD && type.visible && customize.visible
                    && !byId(g, 2).visible, CreateWorldTabs.tab(g));
            check("create world: Creative, and it turned Allow Commands on (1.12.2's rule)", "Game Mode: Creative".equals(byId(g, 2).displayString)
                    && "Allow Commands: ON".equals(byId(g, 6).displayString), byId(g, 2).displayString + " / " + byId(g, 6).displayString);
            check("create world: World Type: Default", "World Type: Default".equals(type.displayString), type.displayString);
            check("create world: Customize beside it, off for Default", customize.x == type.x + 160 && customize.y == type.y && !customize.enabled);
            check("create world: the switches, 44 wide at the right", features.width == 44 && features.x == type.x + 310 - 44
                    && "ON".equals(features.displayString) && bonus.y == features.y + 24 && "OFF".equals(bonus.displayString),
                    features.x + "," + features.y + " " + features.displayString + " " + bonus.displayString);
            return true;
        });
        add(null, () -> {
            net.minecraft.client.gui.GuiCreateWorld g = this.create;
            setText(g, "worldSeedField", "field_146335_h", "20261116");
            click(g, CreateWorldTabs.tabButtons(g).get(2));
            return true;
        });
        add("create-more", () -> {
            net.minecraft.client.gui.GuiCreateWorld g = this.create;
            net.minecraft.client.gui.GuiButton rules = CreateWorldTabs.gameRulesButton(g);
            check("create world: the More tab's Game Rules", CreateWorldTabs.tab(g) == CreateWorldTabs.MORE && rules != null && rules.visible
                    && rules.width == 210 && "Game Rules".equals(rules.displayString) && !byId(g, 5).visible);
            return true;
        });
        add(null, () -> {
            net.minecraft.client.gui.GuiCreateWorld g = this.create;
            click(g, byId(g, 0));
            check("create world: Create New World left the screen", mc.currentScreen != g, mc.currentScreen);
            this.create = null;
            return true;
        });
    }

    private static net.minecraft.client.gui.GuiButton byId(GuiScreen g, int id) {
        for (net.minecraft.client.gui.GuiButton b : CreateWorldTabs.buttons(g)) {
            if (b.id == id) {
                return b;
            }
        }
        throw new IllegalStateException("no button " + id);
    }

    /** A left click in the middle of the button, through the screen's own mouseClicked (and Forge's events). */
    private void click(GuiScreen g, net.minecraft.client.gui.GuiButton b) {
        try {
            net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(GuiScreen.class, "mouseClicked", "func_73864_a", int.class, int.class,
                    int.class).invoke(g, b.x + b.width / 2, b.y + b.height / 2, 0);
        } catch (Exception e) {
            failed("click " + b.displayString, e);
        }
    }

    private void setText(GuiScreen g, String mcp, String srg, String text) {
        try {
            ((net.minecraft.client.gui.GuiTextField) net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
                    net.minecraft.client.gui.GuiCreateWorld.class, mcp, srg).get(g)).setText(text);
        } catch (Exception e) {
            failed("set " + mcp, e);
        }
    }

    // ---- 1.19.3's creative tabs (creative.client.ModernTabs, P47) ----

    private CreativeTabs late;
    private boolean inventoryWas;

    private void creativeTabs(Minecraft mc) {
        add(null, () -> {
            check("P47 applied", "applied".equals(Asm.state("P47")), Asm.state("P47"));
            check("1.19.3's tabs are installed and on (1.20.5 or later)", ModernTabs.installed() && ModernTabs.active(),
                    Presentation.newest(com.exiledradio.backportcore.presentation.LookArea.INVENTORY));
            // A tab another mod makes after Core's: it pages as if Core's were not in the array.
            this.late = new CreativeTabs("backportcore_selftest_late") {
                @Override
                public ItemStack createIcon() {
                    return new ItemStack(Items.APPLE);
                }
            };
            int mods = 0;
            for (CreativeTabs t : CreativeTabs.CREATIVE_TAB_ARRAY) {
                if (t.getIndex() >= 12 && !(t instanceof ModernTab)) {
                    mods++;
                }
            }
            int compact = CreativeScreenHooks.compact(this.late);
            check("a late mod tab keeps the place it would have had (index " + this.late.getIndex() + ")", compact == 12 + mods - 1, compact);
            return true;
        });
        add("creative-building", () -> show(new GuiContainerCreative(mc.player)));
        add(null, () -> {
            GuiContainerCreative gui = creative(mc);
            CreativeTabs open = gui == null ? null : CreativeTabs.CREATIVE_TAB_ARRAY[gui.getSelectedTabIndex()];
            check("the creative screen opens on 1.19.3's Building Blocks", open == ModernTabs.tab(ModernTabs.Def.BUILDING_BLOCKS), open);
            List<ItemStack> items = items(gui);
            check("Building Blocks starts with the oak log (1.19.3's order)",
                    items.size() > 100 && items.get(0).getItem() == Item.getItemFromBlock(Blocks.LOG), describe(items, 4));
            Object[] page = CreativeScreenHooks.tabsOnPage(CreativeTabs.CREATIVE_TAB_ARRAY, 0, 12);
            check("the first page: 10 of 1.19.3's tabs (Operator Utilities off), Saved Hotbars, Search, the inventory", page.length == 13, page.length);
            Object[] page1 = CreativeScreenHooks.tabsOnPage(CreativeTabs.CREATIVE_TAB_ARRAY, 12, Math.min(22, CreativeTabs.CREATIVE_TAB_ARRAY.length));
            check("the late mod tab is on page 2 (with no other mod tabs, its first slot)", Arrays.asList(page1).contains(this.late), Arrays.toString(page1));
            check("getSubItems untouched: stone's tab is still 1.12.2's Building Blocks",
                    Item.getItemFromBlock(Blocks.STONE).getCreativeTab() == CreativeTabs.BUILDING_BLOCKS, "");
            return true;
        });
        tab(mc, "creative-colored", ModernTabs.Def.COLORED_BLOCKS, "minecraft:wool@0");
        tab(mc, "creative-natural", ModernTabs.Def.NATURAL_BLOCKS, "minecraft:grass@0");
        tab(mc, "creative-functional", ModernTabs.Def.FUNCTIONAL_BLOCKS, "minecraft:torch@0");
        tab(mc, "creative-redstone", ModernTabs.Def.REDSTONE_BLOCKS, "minecraft:redstone@0");
        tab(mc, "creative-tools", ModernTabs.Def.TOOLS_AND_UTILITIES, "minecraft:wooden_shovel@0");
        tab(mc, "creative-combat", ModernTabs.Def.COMBAT, "minecraft:wooden_sword@0");
        tab(mc, "creative-food", ModernTabs.Def.FOOD_AND_DRINKS, "minecraft:apple@0");
        tab(mc, "creative-ingredients", ModernTabs.Def.INGREDIENTS, "minecraft:coal@0");
        add(null, () -> {
            List<ItemStack> items = items(creative(mc));
            int books = 0;
            for (ItemStack s : items) {
                books += s.getItem() == Items.ENCHANTED_BOOK ? 1 : 0;
            }
            int enchantments = 0;
            for (net.minecraft.enchantment.Enchantment e : net.minecraft.enchantment.Enchantment.REGISTRY) {
                enchantments += e.type != null ? 1 : 0;
            }
            int lastBook = -1;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getItem() == Items.ENCHANTED_BOOK) {
                    lastBook = i;
                }
            }
            // After the books only stacks no table lists (in this run, none from vanilla).
            StringBuilder after = new StringBuilder();
            for (int i = lastBook + 1; i < items.size(); i++) {
                after.append(' ').append(key(items.get(i)));
            }
            check("Ingredients has every enchantment's book at its top level, last of 1.19.3's items", books == enchantments && lastBook >= 0,
                    books + " of " + enchantments + "; after them:" + after);
            return true;
        });
        tab(mc, "creative-eggs", ModernTabs.Def.SPAWN_EGGS, null);
        add(null, () -> {
            List<ItemStack> items = items(creative(mc));
            boolean eggs = !items.isEmpty();
            for (ItemStack s : items) {
                eggs &= s.getItem() instanceof net.minecraft.item.ItemMonsterPlacer;
            }
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < Math.min(4, items.size()); i++) {
                names.append(' ').append(net.minecraft.item.ItemMonsterPlacer.getNamedIdFrom(items.get(i)));
            }
            // 1.19.3's eggs are by name: the allay (Wild Update) before the bat, else the bat first. 26.3's (Game
            // Drops) start with the farm animals: chicken, cow, pig, sheep.
            String first = com.exiledradio.backportcore.presentation.Presentation.isAtLeast(
                    com.exiledradio.backportcore.presentation.LookArea.INVENTORY, "26.3") ? "chicken"
                    : Loader.isModLoaded("wildupdatebackport") ? "allay" : "bat";
            String icon = com.exiledradio.backportcore.presentation.Presentation.isAtLeast(
                    com.exiledradio.backportcore.presentation.LookArea.INVENTORY, "1.21.5") ? "creeper" : "pig";
            check("Spawn Eggs' tab icon is the " + icon + " spawn egg (the creeper's from 1.21.5)", icon.equals(ModernTabs.spawnEggsIcon()),
                    ModernTabs.spawnEggsIcon());
            check("Spawn Eggs lists spawn eggs only, in the installed version's order (" + first + " first)", eggs
                    && String.valueOf(net.minecraft.item.ItemMonsterPlacer.getNamedIdFrom(items.get(0))).endsWith(":" + first), names);
            // Every stack the nine 1.12.2 tabs list is in some 1.19.3 tab.
            Set<String> listed = new HashSet<String>();
            for (ModernTabs.Def d : ModernTabs.Def.values()) {
                NonNullList<ItemStack> l = NonNullList.create();
                ModernTabs.tab(d).displayAllRelevantItems(l);
                for (ItemStack s : l) {
                    listed.add(key(s));
                }
            }
            List<String> lost = new ArrayList<String>();
            for (CreativeTabs t : new CreativeTabs[] {CreativeTabs.BUILDING_BLOCKS, CreativeTabs.DECORATIONS, CreativeTabs.REDSTONE,
                    CreativeTabs.TRANSPORTATION, CreativeTabs.MISC, CreativeTabs.FOOD, CreativeTabs.TOOLS, CreativeTabs.COMBAT, CreativeTabs.BREWING}) {
                NonNullList<ItemStack> l = NonNullList.create();
                t.displayAllRelevantItems(l);
                for (ItemStack s : l) {
                    if (s.getItem() != Items.ENCHANTED_BOOK && !listed.contains(key(s))) {
                        lost.add(key(s));
                    }
                }
            }
            check("every stack of 1.12.2's nine tabs is in a 1.19.3 tab", lost.isEmpty(), lost);
            return true;
        });
        add("creative-search", () -> {
            selectTab(mc, CreativeTabs.SEARCH);
            List<ItemStack> items = items(creative(mc));
            check("Search lists as before (1.12.2's search tab)", items.size() > 500, items.size());
            check("the search tooltip names 1.19.3's tab: stone is in Building Blocks",
                    CreativeScreenHooks.searchTooltipTab(Item.getItemFromBlock(Blocks.STONE)) == ModernTabs.tab(ModernTabs.Def.BUILDING_BLOCKS), "");
            return true;
        });
        add("creative-inventory", () -> selectTab(mc, CreativeTabs.INVENTORY));
        add("creative-page2", () -> {
            setPage(1);
            return selectTab(mc, this.late);
        });
        add(null, () -> {
            setPage(0);
            Presentation.OPERATOR_ITEMS_TAB.set(true);
            check("Operator Items Tab shows from 1.19.3", Presentation.OPERATOR_ITEMS_TAB.isShown(), "");
            mc.player.setPermissionLevel(4); // as an operator (the test world has no cheats)
            check("the player may use commands", mc.player.canUseCommand(2, ""), "");
            return show(new GuiContainerCreative(mc.player));
        });
        add("creative-op", () -> selectTab(mc, ModernTabs.tab(ModernTabs.Def.OP_BLOCKS)));
        add(null, () -> {
            List<ItemStack> items = items(creative(mc));
            check("Operator Utilities: the command block first, the barrier last (1.12.2 has no jigsaw, debug stick or light)", !items.isEmpty()
                    && items.get(0).getItem() == Item.getItemFromBlock(Blocks.COMMAND_BLOCK)
                    && key(items.get(items.size() - 1)).equals("minecraft:barrier@0"), describe(items, 8));
            check("the first page has Operator Utilities", CreativeScreenHooks.tabsOnPage(CreativeTabs.CREATIVE_TAB_ARRAY, 0, 12).length == 14, "");
            Presentation.OPERATOR_ITEMS_TAB.set(false);
            this.inventoryWas = com.exiledradio.backportcore.CoreConfig.lookAndFeel.inventory;
            com.exiledradio.backportcore.CoreConfig.lookAndFeel.inventory = false;
            check("the inventory part off: 1.19.3's tabs off", !ModernTabs.active(), "");
            mc.refreshResources(); // as the Look & Feel switch does: 1.12.2's tab sprites back
            return show(new GuiContainerCreative(mc.player));
        });
        add("creative-1-12-2", () -> {
            GuiContainerCreative gui = creative(mc);
            CreativeTabs open = gui == null ? null : CreativeTabs.CREATIVE_TAB_ARRAY[gui.getSelectedTabIndex()];
            check("off, the screen opens on 1.12.2's Building Blocks", open != null && open.getIndex() == CreativeTabs.BUILDING_BLOCKS.getIndex(), open);
            Object[] page = CreativeScreenHooks.tabsOnPage(CreativeTabs.CREATIVE_TAB_ARRAY, 0, 12);
            boolean ours = false;
            for (Object o : page) {
                ours |= o instanceof ModernTab;
            }
            check("off, the first page is 1.12.2's twelve", page.length == 12 && !ours, page.length);
            check("off, the added tabs are not counted as pages",
                    CreativeScreenHooks.tabCount(CreativeTabs.CREATIVE_TAB_ARRAY) == CreativeTabs.CREATIVE_TAB_ARRAY.length - 11, "");
            return true;
        });
        add(null, () -> {
            com.exiledradio.backportcore.CoreConfig.lookAndFeel.inventory = this.inventoryWas;
            mc.refreshResources();
            mc.displayGuiScreen(null);
            return true;
        });
        // Edit World of the shots' world, once it is closed.
        add(null, () -> {
            mc.world.sendQuittingDisconnectingPacket();
            mc.loadWorld(null);
            return show(new GuiMainMenu());
        });
        add("edit-world", () -> show(new net.minecraft.client.gui.GuiWorldEdit(new GuiWorldSelection(new GuiMainMenu()), WORLD)));
        add(null, () -> {
            check("Edit World opens as 1.20.5's column", !com.exiledradio.backportcore.presentation.client.EditWorldScreen.active()
                    || mc.currentScreen instanceof com.exiledradio.backportcore.presentation.client.EditWorldScreen, mc.currentScreen);
            return true;
        });
    }

    // ---- 1.20.5's header and footer, 26.3's Options (Core 1.4.5) ----

    private void headerAndFooter(Minecraft mc) {
        add(null, () -> {
            boolean modern = com.exiledradio.backportcore.presentation.client.HeaderFooterLayout.active(com.exiledradio.backportcore.presentation.LookArea.OPTIONS);
            GuiScreen s = mc.currentScreen;
            check("Options: Online... (1.18) in Realms Notifications' place", !Presentation.isAtLeast(com.exiledradio.backportcore.presentation.LookArea.OPTIONS, "1.18")
                    || hasButton(s, net.minecraft.client.resources.I18n.format("options.online")), s);
            net.minecraft.client.gui.GuiButton t = com.exiledradio.backportcore.presentation.client.OptionsInjector.telemetry;
            check("Options: Telemetry Data (1.19.3) greyed out", !Presentation.isAtLeast(com.exiledradio.backportcore.presentation.LookArea.OPTIONS, "1.19.3")
                    || t != null && !t.enabled, t);
            check("Options: Credits & Attribution (1.19.4)", !Presentation.isAtLeast(com.exiledradio.backportcore.presentation.LookArea.OPTIONS, "1.19.4")
                    || hasButton(s, net.minecraft.client.resources.I18n.format("options.credits_and_attribution")), s);
            net.minecraft.client.gui.GuiButton done = find(s, 200);
            check("Options: Done (200 wide) in the 33-high footer", !modern || done != null && done.width == 200 && done.y == s.height - 27, done == null ? null : done.y);
            this.headerFrames = com.exiledradio.backportcore.presentation.client.HeaderFooterLayout.frames;
            return true;
        });
        add("online", () -> show(com.exiledradio.backportcore.presentation.client.OptionsListScreen.online(new GuiOptions(new GuiMainMenu(), mc.gameSettings))));
        add("credits", () -> show(new com.exiledradio.backportcore.presentation.client.CreditsAndAttributionScreen(new GuiOptions(new GuiMainMenu(), mc.gameSettings))));
        add("skin", () -> show(new net.minecraft.client.gui.GuiCustomizeSkin(new GuiOptions(new GuiMainMenu(), mc.gameSettings))));
        add(null, () -> {
            boolean modern = com.exiledradio.backportcore.presentation.client.HeaderFooterLayout.active(com.exiledradio.backportcore.presentation.LookArea.OPTIONS);
            check("Skin Customization: its buttons a list between the separators", !modern
                    || com.exiledradio.backportcore.presentation.client.HeaderFooterLayout.list(mc.currentScreen) != null, mc.currentScreen);
            check("header and footer: titles drawn", !modern || com.exiledradio.backportcore.presentation.client.HeaderFooterLayout.frames > this.headerFrames,
                    com.exiledradio.backportcore.presentation.client.HeaderFooterLayout.frames);
            return true;
        });
        add("sounds", () -> show(new net.minecraft.client.gui.GuiScreenOptionsSounds(new GuiOptions(new GuiMainMenu(), mc.gameSettings), mc.gameSettings)));
        add("chat", () -> show(new net.minecraft.client.gui.ScreenChatOptions(new GuiOptions(new GuiMainMenu(), mc.gameSettings), mc.gameSettings)));
        add("accessibility", () -> show(com.exiledradio.backportcore.presentation.client.OptionsListScreen.accessibility(new GuiOptions(new GuiMainMenu(), mc.gameSettings))));
        add("controls", () -> show(com.exiledradio.backportcore.presentation.client.OptionsListScreen.controls(new GuiOptions(new GuiMainMenu(), mc.gameSettings))));
        add("keybinds", () -> show(new net.minecraft.client.gui.GuiControls(new GuiOptions(new GuiMainMenu(), mc.gameSettings), mc.gameSettings)));
    }

    private int headerFrames;

    private static boolean hasButton(GuiScreen s, String text) {
        try {
            for (net.minecraft.client.gui.GuiButton b : buttons(s)) {
                if (text.equals(b.displayString)) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException e) {
            return false;
        }
        return false;
    }

    @javax.annotation.Nullable
    private static net.minecraft.client.gui.GuiButton find(GuiScreen s, int id) {
        try {
            for (net.minecraft.client.gui.GuiButton b : buttons(s)) {
                if (b.id == id) {
                    return b;
                }
            }
        } catch (ReflectiveOperationException e) {
            return null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<net.minecraft.client.gui.GuiButton> buttons(GuiScreen s) throws ReflectiveOperationException {
        java.lang.reflect.Field f = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n");
        return (List<net.minecraft.client.gui.GuiButton>) f.get(s);
    }

    private void tab(Minecraft mc, String shot, ModernTabs.Def def, String first) {
        add(shot, () -> selectTab(mc, ModernTabs.tab(def)));
        if (first != null) {
            add(null, () -> {
                List<ItemStack> items = items(creative(mc));
                check(def.id + " starts with " + first + " (1.19.3's first item 1.12.2 has)", !items.isEmpty() && key(items.get(0)).equals(first),
                        describe(items, 4));
                return true;
            });
        }
    }

    private boolean selectTab(Minecraft mc, CreativeTabs tab) {
        GuiContainerCreative gui = creative(mc);
        if (gui == null) {
            check("the creative screen is open", false, mc.currentScreen);
            return true;
        }
        try {
            java.lang.reflect.Method set = net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(GuiContainerCreative.class,
                    "setCurrentCreativeTab", "func_147050_b", CreativeTabs.class);
            set.invoke(gui, tab);
        } catch (ReflectiveOperationException e) {
            failed("select " + tab, e);
        }
        return true;
    }

    private void setPage(int page) {
        try {
            net.minecraftforge.fml.relauncher.ReflectionHelper.findField(GuiContainerCreative.class, "tabPage").setInt(null, page);
        } catch (ReflectiveOperationException e) {
            failed("page " + page, e);
        }
    }

    @javax.annotation.Nullable
    private static GuiContainerCreative creative(Minecraft mc) {
        return mc.currentScreen instanceof GuiContainerCreative ? (GuiContainerCreative) mc.currentScreen : null;
    }

    private static List<ItemStack> items(@javax.annotation.Nullable GuiContainerCreative gui) {
        if (gui == null) {
            return new ArrayList<ItemStack>();
        }
        return ((GuiContainerCreative.ContainerCreative) gui.inventorySlots).itemList;
    }

    private static String key(ItemStack s) {
        return s.getItem().getRegistryName() + "@" + s.getMetadata();
    }

    private static String describe(List<ItemStack> items, int n) {
        StringBuilder b = new StringBuilder(items.size() + ":");
        for (int i = 0; i < Math.min(n, items.size()); i++) {
            b.append(' ').append(key(items.get(i)));
        }
        return b.toString();
    }

    private static boolean show(GuiScreen screen) {
        Minecraft.getMinecraft().displayGuiScreen(screen);
        return true;
    }

    private static boolean colorMaskOn() {
        ByteBuffer b = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, b);
        return b.get(0) != 0 && b.get(1) != 0 && b.get(2) != 0;
    }

    private boolean selectCore(Minecraft mc) {
        try {
            java.lang.reflect.Field f = net.minecraftforge.fml.client.GuiModList.class.getDeclaredField("mods");
            f.setAccessible(true);
            List<?> mods = (List<?>) f.get(mc.currentScreen);
            for (int i = 0; i < mods.size(); i++) {
                if (BackportCore.MOD_ID.equals(((net.minecraftforge.fml.common.ModContainer) mods.get(i)).getModId())) {
                    ((net.minecraftforge.fml.client.GuiModList) mc.currentScreen).selectModIndex(i);
                }
            }
        } catch (ReflectiveOperationException e) {
            failed("modlist", e);
        }
        return true;
    }

    void add(String name, Supplier<Boolean> step) {
        this.names.add(name);
        this.steps.add(step);
    }

    void check(String what, boolean ok, Object detail) {
        if (ok) {
            this.pass++;
            BackportCore.LOGGER.info("MENU PASS {} ({})", what, detail);
        } else {
            this.fail++;
            BackportCore.LOGGER.error("MENU FAIL {} ({})", what, detail);
        }
    }

    void check(String what, boolean ok) {
        check(what, ok, "");
    }

    void failed(String what, Throwable t) {
        this.fail++;
        BackportCore.LOGGER.error("MENU FAIL {}", what, t);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (this.step < 0) {
            if ((mc.currentScreen instanceof GuiMainMenu || OptionsRowsShots.onboarding(mc)) && ++this.ticks > 100) {
                this.step = 0;
                this.ticks = 0;
                run();
            }
            return;
        }
        if (this.shot != null || ++this.ticks < 20) {
            return;
        }
        this.ticks = 0;
        this.step++;
        if (this.step >= this.steps.size()) {
            BackportCore.LOGGER.info("MENU SHOTS DONE pass={} fail={}", this.pass, this.fail);
            mc.shutdown();
            return;
        }
        run();
    }

    private void run() {
        boolean done;
        try {
            done = this.steps.get(this.step).get();
        } catch (RuntimeException e) {
            failed("step " + this.step, e);
            done = true;
        }
        if (!done) {
            this.step--; // ask again next time
            return;
        }
        this.shot = this.names.get(this.step);
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.shot == null) {
            return;
        }
        if (++this.ticks < 10) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScreenShotHelper.saveScreenshot(mc.gameDir, "menu-" + this.shot + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        BackportCore.LOGGER.info("MENU SHOT {} ({})", this.shot, mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
        this.shot = null;
        this.ticks = 0;
    }
}

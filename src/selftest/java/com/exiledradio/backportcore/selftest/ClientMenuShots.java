package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.creative.client.CreativeScreenHooks;
import com.exiledradio.backportcore.creative.client.ModernTab;
import com.exiledradio.backportcore.creative.client.ModernTabs;
import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.client.MenuBackground;
import com.exiledradio.backportcore.presentation.client.PanoramaRenderer;
import com.exiledradio.backportcore.presentation.client.PresentationHooks;

import net.minecraft.client.Minecraft;
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
        if (!Loader.isModLoaded("trailsandtalesbackport")) {
            Presentation.version("1.20.5");
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
        add("multiplayer", () -> show(new GuiMultiplayer(new GuiMainMenu())));
        add(null, () -> show(new net.minecraftforge.fml.client.GuiModList(new GuiMainMenu())));
        add("modlist", () -> selectCore(mc));
        add(null, () -> {
            float now = PanoramaRenderer.lastAngles()[1];
            this.listAt = MenuBackground.listFrames;
            check("the panorama kept turning across five screens (one spin, never restarted)", now < this.yawAt - 5.0F,
                    now + " after " + this.yawAt);
            return true;
        });
        add(null, () -> {
            try {
                FileUtils.deleteDirectory(new File(mc.gameDir, "saves/" + WORLD));
            } catch (java.io.IOException e) {
                failed("delete world", e);
            }
            mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(20261116L, GameType.CREATIVE, true, false, WorldType.DEFAULT));
            return true;
        });
        add(null, () -> mc.world != null && mc.player != null && ++this.waited > 4);
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
        screenGaps(mc);
        creativeTabs(mc);
    }

    // ---- Core 1.4.5: the screen audit's items 8 to 11, 21 and 23 ----

    private final List<String> debugLeft = new ArrayList<String>();
    private final List<String> debugRight = new ArrayList<String>();
    private int subtitleAt;

    /** The F3 text as Core leaves it, after the versions' steps and 1.21.9's arrangement. */
    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOW)
    public void onDebugText(net.minecraftforge.client.event.RenderGameOverlayEvent.Text event) {
        this.debugLeft.clear();
        this.debugLeft.addAll(event.getLeft());
        this.debugRight.clear();
        this.debugRight.addAll(event.getRight());
    }

    private static boolean anyStarts(List<String> lines, String prefix) {
        for (String l : lines) {
            if (l != null && net.minecraft.util.text.TextFormatting.getTextWithoutFormattingCodes(l).startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void screenGaps(Minecraft mc) {
        add(null, () -> {
            for (String p : new String[] {"P57", "P58", "P59", "P60"}) {
                check(p + " applied", "applied".equals(Asm.state(p)), Asm.state(p));
            }
            check("26.3 is the newest (inventory, debug, toasts)", com.exiledradio.backportcore.presentation.VanillaRevisions.effectsFitText()
                    && com.exiledradio.backportcore.presentation.VanillaRevisions.debugFrameCharts()
                    && com.exiledradio.backportcore.presentation.VanillaRevisions.systemToastRewrap(), Presentation.newest());
            net.minecraft.server.integrated.IntegratedServer server = mc.getIntegratedServer();
            final java.util.UUID id = mc.player.getUniqueID();
            mc.playerController.setGameType(GameType.SURVIVAL);
            server.addScheduledTask(() -> {
                net.minecraft.entity.player.EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
                p.setGameType(GameType.SURVIVAL);
                p.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.SPEED, 6000, 1));
                p.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.JUMP_BOOST, 6000, 4));
                p.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.NIGHT_VISION, 6000, 0, true, true));
                p.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.FIRE_RESISTANCE, 6000, 0));
            });
            return true;
        });
        add(null, () -> mc.player.getActivePotionEffects().size() == 4 && !mc.player.capabilities.isCreativeMode);
        add("inventory-effects", () -> show(new GuiInventory(mc.player)));
        add(null, () -> {
            GuiInventory gui = (GuiInventory) mc.currentScreen;
            int[] bg = com.exiledradio.backportcore.presentation.client.InventoryEffects.lastBackgrounds;
            check("effects: the inventory stays centered (no 1.12.2 shift)", gui.getGuiLeft() == (gui.width - gui.getXSize()) / 2, gui.getGuiLeft());
            check("effects: four backgrounds right of the inventory, 33 apart", bg.length == 12 && bg[0] == gui.getGuiLeft() + gui.getXSize() + 2
                    && bg[4] == bg[1] + 33, Arrays.toString(bg));
            int room = gui.width - bg[0];
            check("effects: each as wide as its text (1.21.11), at most the room less 7", bg[2] > 32 && bg[2] <= Math.max(32, room - 7)
                    && (bg[2] != bg[5] || bg[5] != bg[8]), Arrays.toString(bg) + " room " + room);
            check("effects: Jump Boost V named with 26.3's levels", com.exiledradio.backportcore.presentation.client.InventoryEffects.name(
                    new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.JUMP_BOOST, 10, 4)).endsWith(" V"), "");
            return true;
        });
        add("creative-effects", () -> {
            mc.playerController.setGameType(GameType.CREATIVE);
            server(mc, p -> p.setGameType(GameType.CREATIVE));
            return show(new GuiContainerCreative(mc.player));
        });
        add(null, () -> {
            GuiContainerCreative gui = (GuiContainerCreative) mc.currentScreen;
            int[] bg = com.exiledradio.backportcore.presentation.client.InventoryEffects.lastBackgrounds;
            int room = gui.width - (gui.getGuiLeft() + gui.getXSize() + 2);
            check("creative: effects beside it, compact (32 wide, icon only) when names do not fit", room >= 120 || bg.length == 12 && bg[2] == 32,
                    Arrays.toString(bg) + " room " + room);
            mc.player.getRecipeBook().setGuiOpen(true);
            mc.playerController.setGameType(GameType.SURVIVAL);
            server(mc, p -> p.setGameType(GameType.SURVIVAL));
            mc.displayGuiScreen(null);
            return true;
        });
        add("recipe-book", () -> show(new GuiInventory(mc.player)));
        add(null, () -> {
            int[] box = com.exiledradio.backportcore.presentation.client.RecipeBookLook.lastBox;
            int j = (mc.currentScreen.height - 166) / 2;
            check("recipe book: \"Search...\" in the empty box (1.16)", com.exiledradio.backportcore.presentation.client.RecipeBookLook.hints > 0,
                    com.exiledradio.backportcore.presentation.client.RecipeBookLook.hints);
            check("recipe book: the box at 1.20.5's place (yo + 13, 81 wide)", box != null && box[1] == j + 13 && box[2] == 81, box == null ? null
                    : Arrays.toString(box) + " j " + j);
            net.minecraft.client.gui.GuiTextField field = com.exiledradio.backportcore.presentation.client.RecipeBookLook.searchBox(
                    ((GuiInventory) mc.currentScreen).func_194310_f());
            boolean clicked = field != null && com.exiledradio.backportcore.presentation.client.RecipeBookLook.magnifierClick(field, field.x - 10, field.y + 3);
            check("recipe book: the magnifier focuses the box (1.21.2)", clicked && field.isFocused(), field == null ? null : field.isFocused());
            mc.player.getRecipeBook().setGuiOpen(false);
            mc.displayGuiScreen(null);
            server(mc, p -> {
                p.clearActivePotions();
                p.setGameType(GameType.CREATIVE);
            });
            mc.playerController.setGameType(GameType.CREATIVE);
            return true;
        });
        // F3 + 2: 1.20.2's FPS and TPS charts.
        add(null, () -> {
            mc.gameSettings.showDebugInfo = true;
            mc.gameSettings.showLagometer = true;
            mc.gameSettings.showDebugProfilerChart = false;
            return com.exiledradio.backportcore.presentation.client.FrameCharts.TICKS.size() > 100;
        });
        add("fps-charts", () -> {
            check("F3 + 2: the charts drawn in place of the lagometer", com.exiledradio.backportcore.presentation.client.FrameCharts.frames > 0,
                    com.exiledradio.backportcore.presentation.client.FrameCharts.frames);
            check("F3 + 2: the integrated server's ticks logged (start to start, with tick()'s share)",
                    com.exiledradio.backportcore.presentation.client.FrameCharts.TICKS.size() > 100
                            && com.exiledradio.backportcore.presentation.client.FrameCharts.TICKS.get(0, 0) >= com.exiledradio.backportcore.presentation.client.FrameCharts.TICKS.get(0, 1),
                    com.exiledradio.backportcore.presentation.client.FrameCharts.TICKS.size());
            check("F3 + 2: 30 fps is the chart's top, 60 its middle", com.exiledradio.backportcore.presentation.DebugCharts.fpsHeight(1.0E9 / 30) == 60
                    && com.exiledradio.backportcore.presentation.DebugCharts.fpsHeight(1.0E9 / 60) == 30, "");
            return true;
        });
        add(null, () -> {
            mc.gameSettings.showDebugProfilerChart = true;
            return true;
        });
        add("!profiler-pie", () -> true);
        add(null, () -> {
            int[] pie = com.exiledradio.backportcore.presentation.client.FrameCharts.lastPie;
            net.minecraft.client.gui.ScaledResolution res = new net.minecraft.client.gui.ScaledResolution(mc);
            int[] d = com.exiledradio.backportcore.presentation.client.DebugScale.size(mc, res);
            check("F3 + 1: 1.20.2's pie at the GUI scale, above the F3 + 2 charts", com.exiledradio.backportcore.presentation.client.FrameCharts.pies > 0
                    && pie != null && pie[3] == d[1] - 69 - 5 + 5 && pie[2] == d[0] - 10 + 5, pie == null ? null : Arrays.toString(pie) + " in " + Arrays.toString(d));
            mc.gameSettings.showDebugProfilerChart = false;
            mc.gameSettings.showLagometer = false;
            // The debug parts: every new entry on, as Always or In F3.
            com.exiledradio.backportcore.presentation.client.DebugEntries.loadProfile(com.exiledradio.backportcore.presentation.client.DebugEntries.DEFAULT);
            for (String e : new String[] {"tps", "sound_mood", "heightmap", "detailed_memory", "looking_at_entity", "looking_at_fluid",
                    "entity_spawn_counts", "light_levels", "biome", "local_difficulty", "chunk_source_stats", "looking_at_block"}) {
                com.exiledradio.backportcore.presentation.client.DebugEntries.setStatus(e, com.exiledradio.backportcore.presentation.client.DebugEntries.Status.IN_OVERLAY);
            }
            mc.player.rotationPitch = 60.0F;
            return true;
        });
        add("debug-parts", () -> {
            List<String> l = this.debugLeft;
            List<String> r = this.debugRight;
            // 1.21.9's parts run in their ids' order: fps before game_version, so the frame rate is top left.
            check("F3: the frame rate first on the left, the version first on the right (1.21.9's priority lines)",
                    !l.isEmpty() && l.get(0).contains(" fps") && !r.isEmpty() && r.get(0).startsWith("Minecraft "), l.isEmpty() ? l : l.get(0) + " | " + (r.isEmpty() ? r : r.get(0)));
            List<String> all = new ArrayList<String>(l);
            all.addAll(r);
            check("F3: tps (1.21.9)", anyStarts(all, "Integrated server @ "), all);
            check("F3: sound_mood (1.21.9)", anyStarts(all, "Sounds: "), "");
            check("F3: heightmap (1.21.11)", anyStarts(all, "CH S: ") && anyStarts(all, "SH S: "), "");
            check("F3: detailed_memory (26.1)", anyStarts(all, "Memory (heap)") && anyStarts(all, "Memory (non-heap)"), "");
            check("F3: looking_at_fluid (1.21.9), the ground below", anyStarts(all, "Targeted Fluid: "), "");
            check("F3: the To edit line last on the left", l.get(l.size() - 1).startsWith("To edit: press"), l.get(l.size() - 1));
            return true;
        });
        add(null, () -> {
            com.exiledradio.backportcore.presentation.client.DebugEntries.loadProfile(com.exiledradio.backportcore.presentation.client.DebugEntries.DEFAULT);
            mc.gameSettings.showDebugInfo = false;
            mc.player.rotationPitch = 0.0F;
            // System toasts: three ids at once, in 26.3's slots.
            com.exiledradio.backportcore.presentation.client.SystemToasts.add(com.exiledradio.backportcore.presentation.client.SystemToasts.Id.WORLD_BACKUP,
                    "Backed up: New World", "Size: 3 MB");
            com.exiledradio.backportcore.presentation.client.SystemToasts.addOrUpdate(com.exiledradio.backportcore.presentation.client.SystemToasts.Id.LOW_DISK_SPACE,
                    "Low disk space!", "Might not be able to save the world.");
            com.exiledradio.backportcore.presentation.client.SystemToasts.addOrUpdate(com.exiledradio.backportcore.presentation.client.SystemToasts.Id.CHUNK_SAVE_FAILURE,
                    net.minecraft.util.text.TextFormatting.RED + "Failed to save chunk at [7, 8]", "See log for more details");
            com.exiledradio.backportcore.presentation.client.SystemToasts.addOrUpdate(com.exiledradio.backportcore.presentation.client.SystemToasts.Id.LOW_DISK_SPACE,
                    "Low disk space!", "Might not be able to save the world. Free up some space on the disk, then try again.");
            // Subtitles: Text Background Everywhere at 0.75.
            mc.gameSettings.showSubtitles = true;
            Presentation.TEXT_BACKGROUND.set("everywhere");
            Presentation.TEXT_BACKGROUND_OPACITY.set(0.75);
            this.subtitleAt = PresentationHooks.subtitleBoxes;
            return true;
        });
        // The subtitle overlay listens for sounds from the first frame drawn with subtitles on.
        add(null, () -> {
            net.minecraft.client.audio.PositionedSoundRecord moo = new net.minecraft.client.audio.PositionedSoundRecord(
                    net.minecraft.init.SoundEvents.ENTITY_COW_AMBIENT, net.minecraft.util.SoundCategory.NEUTRAL, 1.0F, 1.0F,
                    (float) mc.player.posX + 2, (float) mc.player.posY, (float) mc.player.posZ);
            try {
                net.minecraft.client.gui.GuiSubtitleOverlay overlay = (net.minecraft.client.gui.GuiSubtitleOverlay) net.minecraftforge.fml.relauncher.ReflectionHelper
                        .findField(net.minecraft.client.gui.GuiIngame.class, "overlaySubtitle", "field_184049_t").get(mc.ingameGUI);
                // The dev run may lack the sound assets: the accessor is made here, with the cow's subtitle.
                overlay.soundPlay(moo, new net.minecraft.client.audio.SoundEventAccessor(moo.getSoundLocation(), "subtitles.entity.cow.ambient"));
            } catch (IllegalAccessException e) {
                failed("subtitle overlay", e);
            }
            return true;
        });
        add("!system-toasts", () -> {
            int[] slots = com.exiledradio.backportcore.presentation.client.SystemToasts.visibleSlots();
            check("toasts: three at once, each in its own slots down from the top", slots.length == 3 && slots[0] == 0 && slots[1] == 1 && slots[2] > 1,
                    Arrays.toString(slots));
            com.exiledradio.backportcore.presentation.client.SystemToasts.Toast disk = com.exiledradio.backportcore.presentation.client.SystemToasts
                    .get(com.exiledradio.backportcore.presentation.client.SystemToasts.Id.LOW_DISK_SPACE);
            int lines = mc.fontRenderer.listFormattedStringToWidth("Might not be able to save the world. Free up some space on the disk, then try again.", 200).size();
            check("toasts: addOrUpdate rewraps the shown toast (26.2) at 200", disk != null && lines > 1 && disk.height() == 20 + 12 * lines
                    && disk.width() <= 230, disk == null ? null : disk.width() + "x" + disk.height() + ", " + lines + " lines");
            check("subtitles: drawn through P59 with Text Background Everywhere", PresentationHooks.subtitleBoxes > this.subtitleAt
                    && PresentationHooks.subtitleBoxColor(0xCC000000) == 0xBF000000, PresentationHooks.subtitleBoxes);
            return true;
        });
        add(null, () -> {
            Presentation.TEXT_BACKGROUND.set("chat");
            Presentation.TEXT_BACKGROUND_OPACITY.set(0.5);
            check("subtitles: Chat Only keeps 1.12.2's box", PresentationHooks.subtitleBoxColor(0xCC000000) == 0xCC000000);
            mc.gameSettings.showSubtitles = false;
            com.exiledradio.backportcore.presentation.client.SystemToasts.clear();
            return true;
        });
    }

    private static void server(Minecraft mc, java.util.function.Consumer<net.minecraft.entity.player.EntityPlayerMP> action) {
        net.minecraft.server.integrated.IntegratedServer server = mc.getIntegratedServer();
        final java.util.UUID id = mc.player.getUniqueID();
        server.addScheduledTask(() -> action.accept(server.getPlayerList().getPlayerByUUID(id)));
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

    private void backBuffer(Minecraft mc, File file) {
        int w = mc.displayWidth;
        int h = mc.displayHeight;
        java.nio.IntBuffer buf = BufferUtils.createIntBuffer(w * h);
        GL11.glReadBuffer(GL11.GL_BACK);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, w, h, org.lwjgl.opengl.GL12.GL_BGRA, org.lwjgl.opengl.GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buf);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, h - 1 - y, buf.get(y * w + x) & 0xFFFFFF);
            }
        }
        try {
            file.getParentFile().mkdirs();
            javax.imageio.ImageIO.write(img, "png", file);
        } catch (java.io.IOException e) {
            failed("back buffer shot", e);
        }
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

    private void add(String name, Supplier<Boolean> step) {
        this.names.add(name);
        this.steps.add(step);
    }

    private void check(String what, boolean ok, Object detail) {
        if (ok) {
            this.pass++;
            BackportCore.LOGGER.info("MENU PASS {} ({})", what, detail);
        } else {
            this.fail++;
            BackportCore.LOGGER.error("MENU FAIL {} ({})", what, detail);
        }
    }

    private void check(String what, boolean ok) {
        check(what, ok, "");
    }

    private void failed(String what, Throwable t) {
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
            if (mc.currentScreen instanceof GuiMainMenu && ++this.ticks > 100) {
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

    /** Last, so the toasts Core draws at the end of the frame are in the back buffer shots. */
    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.shot == null) {
            return;
        }
        if (++this.ticks < 10) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (this.shot.startsWith("!")) {
            // Toasts and the profiler pie are drawn on the window after the framebuffer: read the back buffer.
            if (this.shot.contains("pie")) {
                PresentationHooks.profilerPie(mc, 0L);
            }
            this.shot = this.shot.substring(1);
            backBuffer(mc, new File(mc.gameDir, "screenshots/menu-" + this.shot + ".png"));
        } else {
            ScreenShotHelper.saveScreenshot(mc.gameDir, "menu-" + this.shot + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        }
        BackportCore.LOGGER.info("MENU SHOT {} ({})", this.shot, mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
        this.shot = null;
        this.ticks = 0;
    }
}

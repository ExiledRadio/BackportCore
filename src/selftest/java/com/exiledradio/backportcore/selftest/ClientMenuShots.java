package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
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
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.OpenGlHelper;
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
import java.util.List;
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

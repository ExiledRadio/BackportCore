package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.client.FullscreenModes;
import com.exiledradio.backportcore.presentation.client.OptionWidgets;
import com.exiledradio.backportcore.render.client.ChunkRebuildHooks;
import com.exiledradio.backportcore.render.client.ImprovedTransparency;
import com.exiledradio.backportcore.simulation.SimulationDistance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.io.FileUtils;
import org.lwjgl.opengl.DisplayMode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Dev only, environment variable {@code BACKPORTCORE_VIDEOSHOTS=1} with {@code runClient -Pselftest}: Core 1.4.5's
 * Video Settings additions at a dummy 26.3. Video Settings (Display with Exclusive Fullscreen Mode, Quality &amp;
 * Performance with Chunk Builder, Simulation Distance and Improved Transparency), the mode list, the Chunk Builder and
 * its config as one setting, the option reaching the game's own server, and a world in the rain over water with
 * Improved Transparency off and on. Saves {@code screenshots/video-*.png}, logs {@code VIDEO SHOTS DONE pass=N fail=M}
 * and closes the game. The monitor's mode is never changed.
 */
@SideOnly(Side.CLIENT)
final class ClientVideoShots {

    static final String WORLD = "backportcore_videoshots";

    static boolean active() {
        return System.getenv("BACKPORTCORE_VIDEOSHOTS") != null;
    }

    static void register() {
        Presentation.version("26.3");
        MinecraftForge.EVENT_BUS.register(new ClientVideoShots());
    }

    private final List<String> names = new ArrayList<String>();
    private final List<Supplier<Boolean>> steps = new ArrayList<Supplier<Boolean>>();
    private int step = -1;
    private int ticks;
    private String shot;
    private int pass;
    private int fail;
    private int waited;
    private int framesAt;
    private String presetAt;

    private ClientVideoShots() {
        Minecraft mc = Minecraft.getMinecraft();
        add(null, () -> {
            check("26.3 is the newest", Presentation.isAtLeast("26.3"), Presentation.newest());
            check("P17 and P42 applied", "applied".equals(Asm.state("P17")) && "applied".equals(Asm.state("P42")),
                    Asm.state("P17") + " / " + Asm.state("P42"));
            check("the dev client draws with 1.12.2's renderer", VanillaRevisions.vanillaRenderer());
            check("shown: Simulation Distance, Chunk Builder, Improved Transparency, Exclusive Fullscreen Mode",
                    Presentation.SIMULATION_DISTANCE.isShown() && Presentation.PRIORITIZE_CHUNK_UPDATES.isShown()
                            && Presentation.IMPROVED_TRANSPARENCY.isShown() && FullscreenModes.shown());
            List<DisplayMode> modes = FullscreenModes.modes();
            check("the monitor lists its modes", !modes.isEmpty(), modes.size());
            String before = Presentation.FULLSCREEN_RESOLUTION.get();
            FullscreenModes.choose(-1);
            boolean current = FullscreenModes.index() == -1 && FullscreenModes.chosen() == null;
            FullscreenModes.choose(modes.size() - 1);
            String saved = Presentation.FULLSCREEN_RESOLUTION.get();
            boolean last = FullscreenModes.index() == modes.size() - 1 && saved.matches("\\d+x\\d+@\\d+:\\d+");
            Presentation.FULLSCREEN_RESOLUTION.set("1x1@1:24");
            boolean gone = FullscreenModes.index() == -1;
            Presentation.FULLSCREEN_RESOLUTION.set(before);
            check("modes: Current, the last mode saved as vanilla's 'WxH@R:B', a mode the monitor lacks is Current",
                    current && last && gone, saved);
            boolean sorted = true;
            for (int i = 1; i < modes.size(); i++) {
                DisplayMode a = modes.get(i - 1);
                DisplayMode b = modes.get(i);
                sorted &= a.getWidth() * a.getHeight() <= b.getWidth() * b.getHeight() || a.getBitsPerPixel() < b.getBitsPerPixel();
            }
            check("modes: lowest first, as GLFW lists them", sorted, modes.get(0) + " .. " + modes.get(modes.size() - 1));
            // The Chunk Builder and rendering.blockUpdates are one setting.
            String chunkAt = Presentation.PRIORITIZE_CHUNK_UPDATES.get();
            Presentation.PRIORITIZE_CHUNK_UPDATES.set("nearby");
            boolean nearby = CoreConfig.rendering.blockUpdates == CoreConfig.BlockUpdates.NEARBY && ChunkRebuildHooks.nearbyDistanceSq(768.0) == 768.0;
            Presentation.PRIORITIZE_CHUNK_UPDATES.set("none");
            boolean none = CoreConfig.rendering.blockUpdates == CoreConfig.BlockUpdates.NONE && !ChunkRebuildHooks.needsImmediateUpdate(true)
                    && ChunkRebuildHooks.nearbyDistanceSq(768.0) < 0.0;
            Presentation.PRIORITIZE_CHUNK_UPDATES.set("player_affected");
            boolean player = CoreConfig.rendering.blockUpdates == CoreConfig.BlockUpdates.PLAYER_AFFECTED && ChunkRebuildHooks.needsImmediateUpdate(true)
                    && ChunkRebuildHooks.nearbyDistanceSq(768.0) < 0.0;
            Presentation.PRIORITIZE_CHUNK_UPDATES.set(chunkAt);
            check("Chunk Builder: Fully Blocking, Threaded, Semi Blocking reach P17 and the config", nearby && none && player,
                    nearby + " " + none + " " + player);
            this.presetAt = Presentation.GRAPHICS_PRESET.get();
            return true;
        });
        add("video-display", () -> show(new GuiVideoSettings(new GuiMainMenu(), mc.gameSettings)));
        add(null, () -> {
            List<String> on = optionsOnScreen(mc);
            check("Video Settings: the mode slider and the three new options are on it", on.contains(Presentation.FULLSCREEN_RESOLUTION.id())
                    && on.contains(Presentation.SIMULATION_DISTANCE.id()) && on.contains(Presentation.PRIORITIZE_CHUNK_UPDATES.id())
                    && on.contains(Presentation.IMPROVED_TRANSPARENCY.id()), on);
            int mode = on.indexOf(Presentation.FULLSCREEN_RESOLUTION.id());
            check("Exclusive Fullscreen Mode comes first, 310 wide, with 26.3's label and tooltip", mode == 0
                    && button(mc, Presentation.FULLSCREEN_RESOLUTION.id()).width == 310
                    && button(mc, Presentation.FULLSCREEN_RESOLUTION.id()).displayString.startsWith("Exclusive Fullscreen Mode: ")
                    && "options.fullscreen.exclusive.mode.tooltip".equals(Presentation.FULLSCREEN_RESOLUTION.tooltipKey()),
                    button(mc, Presentation.FULLSCREEN_RESOLUTION.id()).displayString);
            int rd = on.indexOf(Presentation.PRIORITIZE_CHUNK_UPDATES.id());
            check("Quality: Chunk Builder, then Simulation Distance (26.3's order)", rd >= 0 && on.indexOf(Presentation.SIMULATION_DISTANCE.id()) == rd + 1, on);
            check("Simulation Distance's label", button(mc, Presentation.SIMULATION_DISTANCE.id()).displayString.startsWith("Simulation Distance: "),
                    button(mc, Presentation.SIMULATION_DISTANCE.id()).displayString);
            scroll(mc, 140);
            return true;
        });
        add("video-quality", () -> true);
        add(null, () -> {
            try {
                FileUtils.deleteDirectory(new File(mc.gameDir, "saves/" + WORLD));
            } catch (java.io.IOException e) {
                failed("delete world", e);
            }
            mc.displayGuiScreen(null);
            mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(20261122L, GameType.CREATIVE, true, false, WorldType.FLAT));
            return true;
        });
        add(null, () -> mc.world != null && mc.player != null && ++this.waited > 4);
        add(null, () -> {
            mc.gameSettings.pauseOnLostFocus = false;
            int want = Math.max(2, (int) Math.round(Presentation.SIMULATION_DISTANCE.get()));
            check("Simulation Distance reaches the game's own server", SimulationDistance.distance() == want, SimulationDistance.distance() + " want " + want);
            check("P46 applied", "applied".equals(Asm.state("P46")), Asm.state("P46"));
            WorldServer w = mc.getIntegratedServer().getWorld(0);
            EntityPlayerMP p = mc.getIntegratedServer().getPlayerList().getPlayers().get(0);
            BlockPos base = new BlockPos(p.posX, 120, p.posZ);
            // A basin of water 13 wide, 10 long, in front of the camera; stained glass along its far side.
            for (int x = -6; x <= 6; x++) {
                for (int z = 2; z <= 12; z++) {
                    w.setBlockState(base.add(x, -1, z), Blocks.STONE.getDefaultState(), 2);
                    w.setBlockState(base.add(x, 0, z), x == -6 || x == 6 || z == 12 ? Blocks.STONE.getDefaultState() : Blocks.WATER.getDefaultState(), 2);
                    if (z == 12) {
                        for (int y = 1; y <= 4; y++) {
                            w.setBlockState(base.add(x, y, z), Blocks.STAINED_GLASS.getDefaultState(), 2);
                        }
                    }
                }
            }
            w.getWorldInfo().setRaining(true);
            w.getWorldInfo().setRainTime(200000);
            w.setRainStrength(1.0F);
            w.setWorldTime(6000L);
            p.capabilities.isFlying = true;
            p.sendPlayerAbilities();
            p.connection.setPlayerLocation(base.getX() + 0.5, base.getY() + 5, base.getZ() - 1.5, 0.0F, 40.0F);
            mc.world.setRainStrength(1.0F);
            Presentation.IMPROVED_TRANSPARENCY.set(false);
            return true;
        });
        add(null, () -> ++this.waited > 10);
        add("transparency-off", () -> {
            mc.world.setRainStrength(1.0F);
            this.framesAt = ImprovedTransparency.frames;
            return true;
        });
        add(null, () -> {
            check("off: no second weather pass", ImprovedTransparency.frames == this.framesAt, ImprovedTransparency.frames - this.framesAt);
            Presentation.IMPROVED_TRANSPARENCY.set(true);
            mc.world.setRainStrength(1.0F);
            this.framesAt = ImprovedTransparency.frames;
            return true;
        });
        add("transparency-on", () -> true);
        add(null, () -> {
            check("on: the second weather pass runs each frame it rains", ImprovedTransparency.frames > this.framesAt,
                    ImprovedTransparency.frames - this.framesAt);
            Presentation.IMPROVED_TRANSPARENCY.set(false);
            Presentation.GRAPHICS_PRESET.set(this.presetAt);
            return true;
        });
    }

    /** @return the ids of Core's options with a button on the open screen, in order */
    private static List<String> optionsOnScreen(Minecraft mc) {
        List<String> out = new ArrayList<String>();
        for (GuiButton b : buttons(mc)) {
            if (b instanceof OptionWidgets.OptionButton) {
                out.add(((OptionWidgets.OptionButton) b).option().id());
            }
        }
        return out;
    }

    private static GuiButton button(Minecraft mc, String id) {
        for (GuiButton b : buttons(mc)) {
            if (b instanceof OptionWidgets.OptionButton && ((OptionWidgets.OptionButton) b).option().id().equals(id)) {
                return b;
            }
        }
        return new GuiButton(0, 0, 0, 0, 0, "(missing)");
    }

    private static List<GuiButton> buttons(Minecraft mc) {
        try {
            java.lang.reflect.Method rows = Class.forName("com.exiledradio.backportcore.presentation.client.OptionsInjector")
                    .getDeclaredMethod("rowButtons", net.minecraft.client.gui.GuiScreen.class);
            rows.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<GuiButton> list = (List<GuiButton>) rows.invoke(null, mc.currentScreen);
            return list == null ? new ArrayList<GuiButton>() : list;
        } catch (ReflectiveOperationException e) {
            return new ArrayList<GuiButton>();
        }
    }

    private static void scroll(Minecraft mc, int by) {
        Object list = ReflectionHelper.getPrivateValue(GuiVideoSettings.class, (GuiVideoSettings) mc.currentScreen, "optionsRowList", "field_146501_h");
        if (list instanceof GuiSlot) {
            ((GuiSlot) list).scrollBy(by);
        }
    }

    private static boolean show(net.minecraft.client.gui.GuiScreen screen) {
        Minecraft.getMinecraft().displayGuiScreen(screen);
        return true;
    }

    private void add(String name, Supplier<Boolean> step) {
        this.names.add(name);
        this.steps.add(step);
    }

    private void check(String what, boolean ok, Object detail) {
        if (ok) {
            this.pass++;
            BackportCore.LOGGER.info("VIDEO PASS {} ({})", what, detail);
        } else {
            this.fail++;
            BackportCore.LOGGER.error("VIDEO FAIL {} ({})", what, detail);
        }
    }

    private void check(String what, boolean ok) {
        check(what, ok, "");
    }

    private void failed(String what, Throwable t) {
        this.fail++;
        BackportCore.LOGGER.error("VIDEO FAIL {}", what, t);
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
            BackportCore.LOGGER.info("VIDEO SHOTS DONE pass={} fail={}", this.pass, this.fail);
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
            this.step--;
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
        ScreenShotHelper.saveScreenshot(mc.gameDir, "video-" + this.shot + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        BackportCore.LOGGER.info("VIDEO SHOT {}", this.shot);
        this.shot = null;
        this.ticks = 0;
    }
}

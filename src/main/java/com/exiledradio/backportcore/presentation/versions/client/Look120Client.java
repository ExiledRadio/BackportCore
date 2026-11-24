package com.exiledradio.backportcore.presentation.versions.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.pose.client.PlayerModelHook;
import com.exiledradio.backportcore.pose.client.PoseClient;
import com.exiledradio.backportcore.presentation.LegacyCopies;
import com.exiledradio.backportcore.presentation.LoadingRevision;
import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.client.LoadingScreens;
import com.exiledradio.backportcore.presentation.versions.Look120;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * The client side of {@code versions.Look120}: 1.20's look and feel that Backport Core owns since 1.4.7 (Trails &amp;
 * Tales Backport's until 1.0.4).
 * <ul>
 *   <li>1.20.2's F3 + 1 (profiler chart) and F3 + 2 (FPS and TPS charts), which also keep the charts when F3 is pressed
 *       with Shift or Alt (1.20.2 took those away);</li>
 *   <li>1.20 (23w13a): Ctrl + scroll on Video Settings changes GUI Scale;</li>
 *   <li>1.20's application icon (a grass block, was a crafting table);</li>
 *   <li>1.20.5's loading screen ({@link LoadingScreen1205}), dimension-change screens ({@link DimensionScreen1205}) and
 *       the shield arm following the look.</li>
 * </ul>
 * (1.20.2's Back on the Multiplayer and World Selection screens was already Core's: {@code MenuScreens.backForCancel}.)
 *
 * <p>An older Trails &amp; Tales jar still has its own copy of each event handler here: its F3 keys replace Core's by
 * themselves, and the handlers below stay off while its classes are on the class path ({@link LegacyCopies}).
 */
@SideOnly(Side.CLIENT)
public final class Look120Client {

    /** Trails &amp; Tales' own copies, in its releases before Core 1.4.7. */
    static final String TRAILS_CLIENT = "com.exiledradio.trailsandtalesbackport.lookfeel.client.LookAndFeelClient";
    static final String TRAILS_POSES = "com.exiledradio.trailsandtalesbackport.lookfeel.client.ArmPoses";

    private static final float PI = (float) Math.PI;

    /** Counters for the client self-test. */
    public static volatile int guiScaleScrolls;
    public static volatile boolean iconSet;
    public static volatile int blockPosed;

    /** 1.20.2's chart switches, which F3 alone no longer changes. */
    static boolean profilerChart;
    static boolean fpsCharts;

    private static boolean iconChecked;

    private Look120Client() {
    }

    /** From Backport Core's client pre-init, before any backport's client code. */
    static void init() {
        Presentation.core("1.20.2")
                .debugKey(Keyboard.KEY_1, "debug.profiler_chart.help", Look120Client::toggleProfilerChart)
                .debugKey(Keyboard.KEY_2, "debug.fps_charts.help", Look120Client::toggleFpsCharts);
        LoadingScreens.register(LoadingRevision.PANORAMA_1_20_5, new LoadingScreen1205());
        MinecraftForge.EVENT_BUS.register(new Look120Client());
        MinecraftForge.EVENT_BUS.register(new DimensionScreen1205());
        PoseClient.addModelHook(new BlockingArm());
    }

    /** @return true while no older Trails &amp; Tales jar runs its own copy of these handlers */
    static boolean coreRuns() {
        return LegacyCopies.coreRuns(TRAILS_CLIENT);
    }

    // ---- F3 charts (1.20.2) ----

    /** F3 + 1: the profiler chart. */
    static boolean toggleProfilerChart() {
        profilerChart = !profilerChart;
        applyCharts(Minecraft.getMinecraft());
        return true;
    }

    /** F3 + 2: the FPS chart (and, in singleplayer, the server's tick chart: 1.12.2's lagometer). */
    static boolean toggleFpsCharts() {
        fpsCharts = !fpsCharts;
        applyCharts(Minecraft.getMinecraft());
        return true;
    }

    static void applyCharts(Minecraft mc) {
        mc.gameSettings.showDebugProfilerChart = profilerChart;
        mc.gameSettings.showLagometer = fpsCharts;
    }

    /**
     * F3 released on its own: 1.12.2 sets the profiler chart to Shift and the lagometer to Alt. 1.20.2 shows only the
     * charts F3 + 1 and F3 + 2 chose, so they are put back.
     */
    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (Keyboard.getEventKey() == Keyboard.KEY_F3 && !Keyboard.getEventKeyState() && Presentation.isAtLeast(LookArea.DEBUG, "1.20.2")
                && coreRuns()) {
            applyCharts(Minecraft.getMinecraft());
        }
    }

    // ---- GUI Scale (1.20) ----

    /** 1.20.6 {@code VideoSettingsScreen.mouseScrolled}: Ctrl + scroll steps GUI Scale (Auto counts as one above the most). */
    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiVideoSettings) || !GuiScreen.isCtrlKeyDown() || !Presentation.isAtLeast(LookArea.VIDEO, "1.20")
                || !coreRuns()) {
            return;
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        event.setCanceled(true);
        Minecraft mc = Minecraft.getMinecraft();
        int next = Look120.nextGuiScale(mc.gameSettings.guiScale, Integer.signum(wheel), maxGuiScale(mc));
        if (next != mc.gameSettings.guiScale) {
            mc.gameSettings.guiScale = next;
            mc.gameSettings.saveOptions();
            ScaledResolution resolution = new ScaledResolution(mc);
            event.getGui().setWorldAndResolution(mc, resolution.getScaledWidth(), resolution.getScaledHeight());
            guiScaleScrolls++;
        }
    }

    /** @return the GUI scale Auto picks for this window (1.12.2's {@code ScaledResolution} with no limit) */
    static int maxGuiScale(Minecraft mc) {
        int scale = 1;
        while (scale < 1000 && mc.displayWidth / (scale + 1) >= 320 && mc.displayHeight / (scale + 1) >= 240) {
            scale++;
        }
        if (mc.isUnicode() && scale % 2 != 0 && scale != 1) {
            scale--;
        }
        return scale;
    }

    // ---- Application icon (1.20) ----

    /**
     * 1.20's window icon, 16 and 32 pixels, as 1.12.2's {@code Minecraft.setWindowIcon} sets it (not on macOS), while
     * the menus look like 1.20 or later (Core's Look &amp; Feel page); otherwise 1.12.2's own, from vanilla's jar. The
     * backports declare their versions after Core's pre-init, so this is first asked when a screen opens.
     */
    static void setIcon() {
        if (Util.getOSType() == Util.EnumOS.OSX || !coreRuns()) {
            return;
        }
        boolean ours = Presentation.isAtLeast(LookArea.MENUS, "1.20");
        if (iconChecked && ours == iconSet) {
            return;
        }
        if (!iconChecked && !ours) {
            // 1.12.2's own icon is already set.
            iconChecked = true;
            return;
        }
        iconChecked = true;
        try {
            ByteBuffer small = icon("icon_16x16.png", ours);
            ByteBuffer large = icon("icon_32x32.png", ours);
            if (small != null && large != null) {
                Display.setIcon(new ByteBuffer[] {small, large});
                iconSet = ours;
            }
        } catch (Exception e) {
            BackportCore.LOGGER.warn("Look and feel: the window icon was not set ({})", e.toString());
        }
    }

    @SubscribeEvent
    public void onOpen(GuiOpenEvent event) {
        setIcon();
    }

    /** Core's Look &amp; Feel page changed: the icon follows it. */
    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.PostConfigChangedEvent event) {
        if ("backportcore".equals(event.getModID())) {
            setIcon();
        }
    }

    private static ByteBuffer icon(String name, boolean ours) throws Exception {
        InputStream in = ours ? Look120Client.class.getResourceAsStream("/assets/backportcore/look/1.20/icons/" + name)
                : Minecraft.getMinecraft().getResourcePackRepository().rprDefaultResourcePack.getInputStream(new ResourceLocation("icons/" + name));
        if (in == null) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(in);
            int[] argb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            ByteBuffer buffer = ByteBuffer.allocate(4 * argb.length);
            for (int p : argb) {
                buffer.putInt(p << 8 | p >> 24 & 255);
            }
            buffer.flip();
            return buffer;
        } finally {
            in.close();
        }
    }

    // ---- Shield (1.20.5) ----

    /**
     * 1.20.5's {@code HumanoidModel.poseBlockingArm}, on Backport Core's player model hook (P3, after 1.12.2's own
     * angles): 1.12.2's shield arm plus the head's pitch clamped to -80..25 degrees and its yaw to -30..30 degrees, so
     * the shield follows the look.
     */
    static final class BlockingArm implements PlayerModelHook {
        @Override
        public void apply(ModelBiped model, EntityPlayer player) {
            if (model.rightArmPose != ModelBiped.ArmPose.BLOCK && model.leftArmPose != ModelBiped.ArmPose.BLOCK
                    || !Presentation.isAtLeast("1.20.5") || !LegacyCopies.coreRuns(TRAILS_POSES)) {
                return;
            }
            if (model.rightArmPose == ModelBiped.ArmPose.BLOCK) {
                block(model, model.bipedRightArm);
            }
            if (model.leftArmPose == ModelBiped.ArmPose.BLOCK) {
                block(model, model.bipedLeftArm);
            }
        }

        private static void block(ModelBiped model, ModelRenderer arm) {
            arm.rotateAngleX += MathHelper.clamp(model.bipedHead.rotateAngleX, -PI * 4.0F / 9.0F, 0.43633232F);
            arm.rotateAngleY += MathHelper.clamp(model.bipedHead.rotateAngleY, -PI / 6.0F, PI / 6.0F);
            blockPosed++;
        }
    }
}

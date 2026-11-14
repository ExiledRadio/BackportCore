package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VideoSections;
import com.exiledradio.backportcore.presentation.client.OptionWidgets;
import com.exiledradio.backportcore.presentation.client.OptionsInjector;
import com.exiledradio.backportcore.presentation.client.OptionsListScreen;
import com.exiledradio.backportcore.presentation.client.OptiFineVideoScreen;
import com.exiledradio.backportcore.presentation.option.PresentationOption;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiCustomizeSkin;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenOptionsSounds;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.ScreenChatOptions;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * A client test for a game with OptiFine (a Prism copy; the dev client has none): started by a file
 * {@code selftest-optifine} in the game directory. At the title screen it opens Options, Video Settings (which must
 * open as {@link OptiFineVideoScreen}, laid out), scrolls it, clicks an OptiFine setting, one of Core's options and
 * a submenu button through the list, opens every OptiFine submenu and comes back, checks what was saved, then opens
 * Music and Sounds, Controls, Chat, Accessibility and Skin Customization. A screenshot of each step goes to
 * {@code screenshots/optifine-*.png}; it logs {@code CLIENT SELFTEST DONE pass=N fail=M}, writes
 * {@code selftest-client-report.txt} and closes the game.
 */
@SideOnly(Side.CLIENT)
final class ClientOptiFineTests {

    static boolean active() {
        return new File(Minecraft.getMinecraft().gameDir, "selftest-optifine").exists();
    }

    static void register() {
        MinecraftForge.EVENT_BUS.register(new ClientOptiFineTests());
    }

    private final List<Runnable> steps = new ArrayList<Runnable>();
    private final List<String> names = new ArrayList<String>();
    private final List<String> report = new ArrayList<String>();
    private int ticks;
    private int step = -1;
    private String shot;
    private int pass;
    private int fail;
    private GuiOptions options;
    private OptiFineVideoScreen video;
    private int dynamicFov;
    private int hoverX = -1;
    private int hoverY = -1;
    private double weather;

    private ClientOptiFineTests() {
        Minecraft mc = Minecraft.getMinecraft();
        add("options", () -> {
            this.options = new GuiOptions(new GuiMainMenu(), mc.gameSettings);
            mc.displayGuiScreen(this.options);
            check(OptionsInjector.INJECTED.contains("GuiOptions"), "Options: injected");
            checkNoOverlap("Options");
        });
        add("video-top", this::openVideo);
        add("video-middle", () -> this.video.scrollTo(250));
        add("video-bottom", () -> this.video.scrollTo(10000));
        add("tooltip-optifine", () -> hover(byId(GameSettings.Options.FRAMERATE_LIMIT.getOrdinal())));
        add("tooltip-core", () -> hover(byOption(Presentation.CUTOUT_LEAVES)));
        add(null, () -> check("options.cutoutLeaves.tooltip".equals(com.exiledradio.backportcore.presentation.client.OptionTooltips.lastShown),
                "Core's tooltip shows over See-Through Leaves (" + com.exiledradio.backportcore.presentation.client.OptionTooltips.lastShown + ")"));
        add(null, this::clickThroughList);
        add("video-after-clicks", this::checkClicks);
        for (int i = 0; i < OptiFineVideoScreen.SUBMENUS.length; i++) {
            final int n = i;
            add("submenu-" + i, () -> openSubmenu(n));
            add(null, () -> closeSubmenu(n));
        }
        add(null, this::doneAndSaved);
        add("sounds", () -> openChecked(new GuiScreenOptionsSounds(this.options, mc.gameSettings), "GuiScreenOptionsSounds"));
        add("keybinds", () -> openChecked(new GuiControls(this.options, mc.gameSettings), "GuiControls"));
        add("controls", () -> openChecked(OptionsListScreen.controls(this.options), null));
        add("chat", () -> openChecked(new ScreenChatOptions(this.options, mc.gameSettings), "ScreenChatOptions"));
        add("accessibility", () -> {
            openChecked(OptionsListScreen.accessibility(this.options), null);
            OptionsListScreen a = (OptionsListScreen) mc.currentScreen;
            this.report.add("Accessibility scrolls: " + (a.rowButtons() != null));
        });
        add("accessibility-bottom", () -> scrollAccessibility(mc));
        add("skin", () -> openChecked(new GuiCustomizeSkin(this.options), "GuiCustomizeSkin"));
        add(null, () -> check(OptionsInjector.failures == 0, "no screen failed to take its options (" + OptionsInjector.failures + ")"));
    }

    private void add(String name, Runnable r) {
        this.names.add(name);
        this.steps.add(r);
    }

    private void openVideo() {
        Minecraft mc = Minecraft.getMinecraft();
        check(FMLClientHandler.instance().hasOptifine(), "OptiFine is installed");
        mc.displayGuiScreen(new GuiVideoSettings(this.options, mc.gameSettings));
        check(mc.currentScreen instanceof OptiFineVideoScreen, "Video Settings opens as Core's OptiFine layout ("
                + (mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getName()) + ")");
        if (!(mc.currentScreen instanceof OptiFineVideoScreen)) {
            return;
        }
        this.video = (OptiFineVideoScreen) mc.currentScreen;
        check(this.video.laidOut(), "it is laid out (OptiFine's buttons found)");
        check(this.video.optiFineButtons().size() == 6, "OptiFine's six submenu buttons are in the list");
        check(OptionsInjector.INJECTED.contains("OptiFineVideoScreen"), "the injector saw it");
        List<PresentationOption> on = new ArrayList<PresentationOption>();
        List<Integer> vanilla = new ArrayList<Integer>();
        for (GuiButton b : this.video.rowButtons()) {
            if (b instanceof OptionWidgets.OptionButton) {
                on.add(((OptionWidgets.OptionButton) b).option());
            } else if (b.id < 200) {
                vanilla.add(b.id);
            }
        }
        StringBuilder ids = new StringBuilder();
        for (PresentationOption o : on) {
            ids.append(o.id()).append(' ');
        }
        StringBuilder settings = new StringBuilder();
        for (int id : vanilla) {
            GameSettings.Options o = GameSettings.Options.byOrdinal(id);
            settings.append(o == null ? "?" + id : o.name()).append(' ');
        }
        this.report.add("options in the list: " + ids);
        this.report.add("OptiFine settings in the list: " + settings);
        for (PresentationOption o : new PresentationOption[] {Presentation.GRAPHICS_PRESET, Presentation.CLOUD_RANGE, Presentation.CUTOUT_LEAVES,
                Presentation.WEATHER_RADIUS}) {
            check(on.contains(o), o.id() + " is in the list");
        }
        for (PresentationOption o : Presentation.options()) {
            if (VideoSections.optiFineHas(o.id())) {
                check(!on.contains(o), o.id() + " is left to OptiFine");
            }
        }
        check(settings.indexOf("RENDER_DISTANCE") >= 0 && settings.indexOf("FRAMERATE_LIMIT") >= 0 && settings.indexOf("DYNAMIC_LIGHTS") >= 0,
                "OptiFine's own settings are in the list");
        check(settings.indexOf("GRAPHICS") < 0 && settings.indexOf("VIEW_BOBBING") < 0, "Graphics and View Bobbing are not (1.21.11)");
    }

    /** Puts the mouse on a button in the list's first screenful and keeps it there (tooltips wait for a still mouse). */
    private void hover(GuiButton b) {
        Minecraft mc = Minecraft.getMinecraft();
        if (b == null) {
            failed("no button to hover", null);
            return;
        }
        this.video.scrollTo(0);
        this.video.drawScreen(0, 0, 0);
        this.hoverX = b.x + 40;
        this.hoverY = b.y + 10;
    }

    private GuiButton byId(int id) {
        for (GuiButton b : this.video.rowButtons()) {
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }

    private GuiButton byOption(PresentationOption o) {
        for (GuiButton b : this.video.rowButtons()) {
            if (b instanceof OptionWidgets.OptionButton && ((OptionWidgets.OptionButton) b).option() == o) {
                return b;
            }
        }
        return null;
    }

    /** Clicks at the middle of a button, scrolled into view, as the mouse would: through the screen and its list. */
    private boolean click(GuiButton b, int dx) throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        // Bring it into view: rows are 25 high from 32 + 4; aim for the button's row near the top.
        this.video.scrollTo(0);
        this.video.drawScreen(0, 0, 0);
        int y0 = b.y;
        this.video.scrollTo(Math.max(0, y0 - 60));
        this.video.drawScreen(0, 0, 0);
        int x = b.x + dx;
        int y = b.y + 10;
        Method clicked = ReflectionHelper.findMethod(GuiScreen.class, "mouseClicked", "func_73864_a", int.class, int.class, int.class);
        Method released = ReflectionHelper.findMethod(GuiScreen.class, "mouseReleased", "func_146286_b", int.class, int.class, int.class);
        GuiScreen before = mc.currentScreen;
        clicked.invoke(before, x, y, 0);
        released.invoke(before, x, y, 0);
        return true;
    }

    private void clickThroughList() {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            GameSettings.Options fov = optionNamed("DYNAMIC_FOV");
            GuiButton ofButton = fov == null ? null : byId(fov.getOrdinal());
            check(ofButton != null, "OptiFine's Dynamic FOV is in the list");
            if (ofButton != null) {
                this.dynamicFov = settingValue("ofDynamicFov");
                click(ofButton, 75);
            }
            GuiButton weather = byOption(Presentation.WEATHER_RADIUS);
            this.weather = Presentation.WEATHER_RADIUS.get();
            if (weather != null) {
                // Its left end: 3 blocks.
                click(weather, 2);
            }
            check(mc.currentScreen == this.video, "still on Video Settings after the clicks");
        } catch (Exception e) {
            failed("clicking through the list", e);
        }
    }

    private void checkClicks() {
        check(settingValue("ofDynamicFov") != this.dynamicFov, "clicking Dynamic FOV in the list changed OptiFine's setting");
        check(Presentation.WEATHER_RADIUS.get() == 3.0, "clicking Weather Effect Radius' left end set 3 (" + Presentation.WEATHER_RADIUS.get() + ")");
        check(savedOption(Presentation.WEATHER_RADIUS.id()).startsWith("3"), "Core saved it (" + savedOption(Presentation.WEATHER_RADIUS.id()) + ")");
    }

    private static final String[] SUBMENU_CLASSES = {"GuiShaders", "GuiQualitySettingsOF", "GuiDetailSettingsOF", "GuiPerformanceSettingsOF",
            "GuiAnimationSettingsOF", "GuiOtherSettingsOF"};

    private void openSubmenu(int n) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiButton b = this.video.optiFineButtons().get(n);
        try {
            if (n == 2) {
                // Details through the list, as the mouse would.
                click(b, 75);
            } else {
                this.video.optiFineAction(b);
            }
        } catch (Exception e) {
            failed("submenu " + n, e);
        }
        String name = mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName();
        // Shaders shows a message instead while antialiasing or similar is on.
        check(name.equals(SUBMENU_CLASSES[n]) || n == 0 && name.equals("GuiMessage"), "button " + b.displayString + " opens " + SUBMENU_CLASSES[n] + " (" + name + ")");
        // OptiFine's own screens, untouched: their buttons are listed, not checked.
        this.report.add(name + ": " + labels(buttons(mc.currentScreen)));
    }

    private void closeSubmenu(int n) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen s = mc.currentScreen;
        try {
            GuiButton done = null;
            for (GuiButton b : buttons(s)) {
                if (b.id == 200 || net.minecraft.client.resources.I18n.format("gui.done").equals(b.displayString)
                        || b.id == 0 && "GuiMessage".equals(s.getClass().getSimpleName())) {
                    done = b;
                }
            }
            if (done == null) {
                failed("no Done on " + s.getClass().getSimpleName(), null);
                mc.displayGuiScreen(this.video);
                return;
            }
            Method action = ReflectionHelper.findMethod(GuiScreen.class, "actionPerformed", "func_146284_a", GuiButton.class);
            action.invoke(s, done);
        } catch (Exception e) {
            failed("closing submenu " + n, e);
        }
        check(mc.currentScreen == this.video, "Done on " + SUBMENU_CLASSES[n] + " comes back to Video Settings");
        if (mc.currentScreen != this.video) {
            mc.displayGuiScreen(this.video);
        }
    }

    private void doneAndSaved() {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            Method action = ReflectionHelper.findMethod(GuiScreen.class, "actionPerformed", "func_146284_a", GuiButton.class);
            GuiButton done = null;
            for (GuiButton b : buttons(this.video)) {
                if (b.id == 200) {
                    done = b;
                }
            }
            check(done != null, "Video Settings has Done");
            if (done != null) {
                action.invoke(this.video, done);
            }
        } catch (Exception e) {
            failed("Done", e);
        }
        check(mc.currentScreen == this.options, "Done goes back to Options");
        String of = read(new File(mc.gameDir, "optionsof.txt"));
        check(of.contains("ofDynamicFov:" + (settingValue("ofDynamicFov") == 1)), "optionsof.txt has the new Dynamic FOV");
        // Back as they were.
        try {
            java.lang.reflect.Field f = GameSettings.class.getField("ofDynamicFov");
            f.set(mc.gameSettings, this.dynamicFov != 0);
        } catch (ReflectiveOperationException e) {
            failed("Dynamic FOV back", e);
        }
        mc.gameSettings.saveOptions();
        Presentation.WEATHER_RADIUS.set(this.weather);
    }

    private void scrollAccessibility(Minecraft mc) {
        try {
            Object list = ReflectionHelper.findField(OptionsListScreen.class, "list").get(mc.currentScreen);
            if (list != null) {
                ((com.exiledradio.backportcore.presentation.client.OptionRowList) list).scrollTo(10000);
            }
        } catch (Exception e) {
            failed("scrolling Accessibility", e);
        }
    }

    private static String labels(List<GuiButton> list) {
        StringBuilder labels = new StringBuilder();
        for (GuiButton b : list) {
            labels.append(b.displayString).append(" | ");
        }
        return labels.toString();
    }

    private void openChecked(GuiScreen s, String injected) {
        OptionsInjector.INJECTED.clear();
        Minecraft.getMinecraft().displayGuiScreen(s);
        if (injected != null) {
            check(OptionsInjector.INJECTED.contains(injected), injected + ": injected");
        }
        checkNoOverlap(s.getClass().getSimpleName());
    }

    /** Fails when two visible buttons of the screen overlap. */
    private void checkNoOverlap(String what) {
        GuiScreen s = Minecraft.getMinecraft().currentScreen;
        List<GuiButton> list = buttons(s);
        StringBuilder labels = new StringBuilder();
        for (GuiButton b : list) {
            labels.append(b.displayString).append(" | ");
        }
        this.report.add(what + ": " + labels);
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                GuiButton a = list.get(i);
                GuiButton b = list.get(j);
                if (a.visible && b.visible && a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height) {
                    failed(what + ": " + a.displayString + " overlaps " + b.displayString, null);
                    return;
                }
            }
        }
        this.pass++;
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> buttons(GuiScreen s) {
        try {
            return new ArrayList<GuiButton>((List<GuiButton>) ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n").get(s));
        } catch (Exception e) {
            return new ArrayList<GuiButton>();
        }
    }

    private static GameSettings.Options optionNamed(String name) {
        for (GameSettings.Options o : GameSettings.Options.values()) {
            if (o.name().equals(name)) {
                return o;
            }
        }
        return null;
    }

    /** @return one of OptiFine's GameSettings fields as a number (booleans 0 or 1), or -1 */
    private static int settingValue(String field) {
        try {
            Object v = GameSettings.class.getField(field).get(Minecraft.getMinecraft().gameSettings);
            return v instanceof Boolean ? ((Boolean) v ? 1 : 0) : ((Number) v).intValue();
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    private static String savedOption(String id) {
        // The self-test mod saves to its own file (PresentationTests); OptionStore.file() is wherever it is.
        for (String line : read(com.exiledradio.backportcore.presentation.option.OptionStore.file()).split("\n")) {
            if (line.startsWith(id + ":")) {
                return line.substring(id.length() + 1).trim();
            }
        }
        return "";
    }

    private static String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return "";
        }
    }

    private void check(boolean ok, String what) {
        if (ok) {
            this.pass++;
            BackportCore.LOGGER.info("OPTIFINE PASS {}", what);
        } else {
            this.fail++;
            BackportCore.LOGGER.error("OPTIFINE FAIL {}", what);
        }
        this.report.add((ok ? "PASS " : "FAIL ") + what);
    }

    private void failed(String what, Throwable t) {
        this.fail++;
        BackportCore.LOGGER.error("OPTIFINE FAIL {}", what, t);
        this.report.add("FAIL " + what + (t == null ? "" : " (" + t + ")"));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (this.step < 0) {
            if (mc.currentScreen instanceof GuiMainMenu && ++this.ticks > 60) {
                this.step = 0;
                this.ticks = 0;
                run();
            }
            return;
        }
        if (this.shot != null) {
            return;
        }
        if (++this.ticks < 10) {
            return;
        }
        this.ticks = 0;
        this.step++;
        if (this.step >= this.steps.size()) {
            String summary = "CLIENT SELFTEST DONE pass=" + this.pass + " fail=" + this.fail;
            BackportCore.LOGGER.info(summary);
            this.report.add(summary);
            try {
                Files.write(new File(mc.gameDir, "selftest-client-report.txt").toPath(), this.report, StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                BackportCore.LOGGER.error("could not write the report", e);
            }
            mc.shutdown();
            return;
        }
        run();
    }

    private void run() {
        try {
            this.steps.get(this.step).run();
        } catch (RuntimeException e) {
            failed("step " + this.step + " " + this.names.get(this.step), e);
        }
        this.shot = this.names.get(this.step);
        this.ticks = 0;
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.shot == null) {
            return;
        }
        if (this.shot.startsWith("tooltip") && this.hoverX >= 0) {
            // The screen again over this frame with the mouse held on the button, through Forge's hook (so Core's
            // tooltip handler runs too): tooltips wait for a mouse that stays still.
            net.minecraftforge.client.ForgeHooksClient.drawScreen(Minecraft.getMinecraft().currentScreen, this.hoverX, this.hoverY, event.renderTickTime);
        }
        if (++this.ticks < (this.shot.startsWith("tooltip") ? 150 : 6)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScreenShotHelper.saveScreenshot(mc.gameDir, "optifine-" + this.shot + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        BackportCore.LOGGER.info("OPTIFINE SHOT {} ({})", this.shot, mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
        this.shot = null;
        this.ticks = 0;
    }
}

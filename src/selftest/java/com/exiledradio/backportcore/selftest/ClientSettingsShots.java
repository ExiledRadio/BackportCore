package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.settings.BackportSettings;
import com.exiledradio.backportcore.settings.SettingsModel;
import com.exiledradio.backportcore.settings.client.SettingsScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dev only, environment variable {@code BACKPORTCORE_SETTINGSSHOTS=1} with {@code runClient -Pselftest}: opens the settings screen in
 * code at the title screen, saves a screenshot of each state to {@code screenshots/settings-*.png}, checks that a
 * change reaches the field and the file, logs {@code SETTINGS SHOTS DONE pass=N fail=M} and closes the game.
 */
@SideOnly(Side.CLIENT)
final class ClientSettingsShots {

    static boolean active() {
        return System.getenv("BACKPORTCORE_SETTINGSSHOTS") != null;
    }

    private final List<Runnable> steps = new ArrayList<Runnable>();
    private final List<String> names = new ArrayList<String>();
    private int ticks;
    private int step = -1;
    private String shot;
    private int pass;
    private int fail;

    static void register() {
        MinecraftForge.EVENT_BUS.register(new ClientSettingsShots());
    }

    private ClientSettingsShots() {
        Minecraft mc = Minecraft.getMinecraft();
        add("core-features", () -> mc.displayGuiScreen(new SettingsScreen(new GuiMainMenu(), BackportCore.MOD_ID)));
        add("core-all", () -> view(1));
        add("core-search-chunk", () -> search("chunk"));
        add("not-installed", () -> mc.displayGuiScreen(new SettingsScreen(new GuiMainMenu(), "buzzybeesbackport")));
        if (Loader.isModLoaded("updateaquaticbackport")) {
            add("ua-features", () -> mc.displayGuiScreen(new SettingsScreen(new GuiMainMenu(), "updateaquaticbackport")));
            add("ua-water-open", () -> expand("general.water"));
            add("ua-all", () -> view(1));
        }
        if (Loader.isModLoaded("villagerbackport")) {
            add("vp-features", () -> mc.displayGuiScreen(new SettingsScreen(new GuiMainMenu(), "villagerbackport")));
            add("vp-all", () -> view(1));
            add("vp-all-scrolled", () -> scroll(300));
            add("vp-pricing-staged", this::stagePricing);
            add("vp-changed", () -> view(2));
            add("vp-list-editor", this::openWorkstations);
            add("search-spawn", () -> {
                mc.displayGuiScreen(new SettingsScreen(new GuiMainMenu(), "villagerbackport"));
                search("spawn");
            });
        }
        if (Loader.isModLoaded("trickytrialsbackport")) {
            add("tr-features", () -> mc.displayGuiScreen(new SettingsScreen(new GuiMainMenu(), "trickytrialsbackport")));
        }
        add("mod-list", () -> mc.displayGuiScreen(new GuiModList(new GuiMainMenu())));
        add(null, this::checkApply);
    }

    private void add(String name, Runnable r) {
        this.names.add(name);
        this.steps.add(r);
    }

    private static SettingsScreen screen() {
        GuiScreen s = Minecraft.getMinecraft().currentScreen;
        return s instanceof SettingsScreen ? (SettingsScreen) s : null;
    }

    private static Object get(Object o, String field) throws ReflectiveOperationException {
        Field f = o.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return f.get(o);
    }

    private static void set(Object o, String field, Object v) throws ReflectiveOperationException {
        Field f = o.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(o, v);
    }

    private static void rebuild(Object o) throws ReflectiveOperationException {
        Method m = SettingsScreen.class.getDeclaredMethod("rebuild");
        m.setAccessible(true);
        m.invoke(o);
    }

    private void view(int i) {
        try {
            SettingsScreen s = screen();
            Class<?> viewType = Class.forName(SettingsScreen.class.getName() + "$View");
            set(s, "view", viewType.getEnumConstants()[i]);
            set(s, "scroll", 0);
            rebuild(s);
        } catch (ReflectiveOperationException e) {
            failed("view " + i, e);
        }
    }

    private void search(String q) {
        try {
            SettingsScreen s = screen();
            ((net.minecraft.client.gui.GuiTextField) get(s, "search")).setText(q);
            set(s, "query", q);
            rebuild(s);
        } catch (ReflectiveOperationException e) {
            failed("search", e);
        }
    }

    private void scroll(int y) {
        try {
            set(screen(), "scroll", y);
        } catch (ReflectiveOperationException e) {
            failed("scroll", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void expand(String path) {
        try {
            SettingsScreen s = screen();
            for (SettingsModel.Group g : BackportSettings.page("updateaquaticbackport").groups) {
                if (g.path.equals(path)) {
                    ((java.util.Set<SettingsModel.Group>) get(s, "expanded")).add(g);
                }
            }
            rebuild(s);
        } catch (ReflectiveOperationException e) {
            failed("expand", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void stagePricing() {
        try {
            SettingsScreen s = screen();
            view(1);
            Map<SettingsModel.Entry, String[]> staged = (Map<SettingsModel.Entry, String[]>) get(s, "staged");
            for (SettingsModel.Entry e : BackportSettings.page("villagerbackport").entries) {
                if (e.name.equals("demandPricing")) {
                    staged.put(e, new String[]{"false"});
                }
                if (e.name.equals("maxRestocksPerDay")) {
                    staged.put(e, new String[]{"5"});
                }
            }
            rebuild(s);
            int y = 0;
            for (SettingsModel.Entry e : BackportSettings.page("villagerbackport").entries) {
                if (e.name.equals("demandPricing")) {
                    break;
                }
                y += 22;
            }
            set(s, "scroll", Math.max(0, y - 40));
        } catch (ReflectiveOperationException e) {
            failed("stage", e);
        }
    }

    private void openWorkstations() {
        try {
            SettingsScreen s = screen();
            for (SettingsModel.Entry e : BackportSettings.page("villagerbackport").entries) {
                if (e.name.equals("workstations")) {
                    Class<?> c = Class.forName("com.exiledradio.backportcore.settings.client.ListEditScreen");
                    Constructor<?> k = c.getDeclaredConstructor(SettingsScreen.class, SettingsModel.Entry.class, String[].class, boolean.class);
                    k.setAccessible(true);
                    String[] v = e.get().clone();
                    v = java.util.Arrays.copyOf(v, v.length + 2);
                    v[v.length - 2] = "minecraft:bookshelf=librarian";
                    v[v.length - 1] = "somemod:loom=shepherd";
                    Minecraft.getMinecraft().displayGuiScreen((GuiScreen) k.newInstance(s, e, v, true));
                    return;
                }
            }
            failed("workstations list not found", null);
        } catch (ReflectiveOperationException e) {
            failed("list editor", e);
        }
    }

    /** A change made through the screen's apply path reaches the field, the file and the change handlers. */
    private void checkApply() {
        SettingsModel.Entry e = null;
        for (SettingsModel.Entry x : BackportSettings.page(BackportCore.MOD_ID).entries) {
            if (x.name.equals("blockUpdates")) {
                e = x;
            }
        }
        check(e != null, "Core's chunk builder setting is on the page");
        if (e == null) {
            return;
        }
        check(e.side == SettingsModel.Side.CLIENT, "it is a client setting");
        check(e.type == SettingsModel.Type.ENUM, "it is an enum");
        check(e.check(new String[]{"BOGUS"}) != null, "a value outside the enum is refused");
        CoreConfig.BlockUpdates before = CoreConfig.rendering.blockUpdates;
        Map<SettingsModel.Entry, String[]> change = new LinkedHashMap<SettingsModel.Entry, String[]>();
        change.put(e, new String[]{"NEARBY"});
        BackportSettings.apply(change, false);
        check(CoreConfig.rendering.blockUpdates == CoreConfig.BlockUpdates.NEARBY, "the field took NEARBY");
        try {
            String file = new String(Files.readAllBytes(new File(Loader.instance().getConfigDir(), "backportcore.cfg").toPath()), StandardCharsets.UTF_8);
            check(file.contains("S:blockUpdates=NEARBY"), "the file says NEARBY");
        } catch (java.io.IOException ex) {
            failed("reading the file", ex);
        }
        change.put(e, new String[]{before.name()});
        BackportSettings.apply(change, false);
        check(CoreConfig.rendering.blockUpdates == before, "and back");
        int total = 0;
        for (SettingsModel.Page p : SettingsModel.pages()) {
            total += p.entries.size();
            BackportCore.LOGGER.info("SETTINGS page {}: {} groups, {} settings", p.modid, p.groups.size(), p.entries.size());
        }
        check(total > 0, "pages were built");
        String parent = Loader.instance().getIndexedModList().containsKey("villagerbackport")
                ? Loader.instance().getIndexedModList().get("villagerbackport").getMetadata().parent : BackportCore.MOD_ID;
        check(BackportCore.MOD_ID.equals(parent), "backports fold under Core in the mod list");
    }

    private void check(boolean ok, String what) {
        if (ok) {
            this.pass++;
            BackportCore.LOGGER.info("SETTINGS PASS {}", what);
        } else {
            this.fail++;
            BackportCore.LOGGER.error("SETTINGS FAIL {}", what);
        }
    }

    private void failed(String what, Throwable t) {
        this.fail++;
        BackportCore.LOGGER.error("SETTINGS FAIL {}", what, t);
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
        if (++this.ticks < 15) {
            return;
        }
        this.ticks = 0;
        this.step++;
        if (this.step >= this.steps.size()) {
            BackportCore.LOGGER.info("SETTINGS SHOTS DONE pass={} fail={}", this.pass, this.fail);
            mc.shutdown();
            return;
        }
        run();
    }

    private void run() {
        try {
            this.steps.get(this.step).run();
        } catch (RuntimeException e) {
            failed("step " + this.names.get(this.step), e);
        }
        this.shot = this.names.get(this.step);
        this.ticks = 0;
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.shot == null) {
            return;
        }
        if (++this.ticks < 6) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScreenShotHelper.saveScreenshot(mc.gameDir, "settings-" + this.shot + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        BackportCore.LOGGER.info("SETTINGS SHOT {} ({})", this.shot, mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
        this.shot = null;
        this.ticks = 0;
    }
}

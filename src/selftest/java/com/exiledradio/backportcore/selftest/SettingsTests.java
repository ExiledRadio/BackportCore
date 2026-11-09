package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.settings.BackportSettings;
import com.exiledradio.backportcore.settings.Setting;
import com.exiledradio.backportcore.settings.SettingsModel;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * The settings screen's common half on a dedicated server: a page built from a test {@code @Config}, the server's
 * values, value checks, the apply path (field, file, handler), and a non-operator's changes refused.
 */
final class SettingsTests {

    static int changedEvents;

    private SettingsTests() {
    }

    @Config(modid = SelfTestMod.MOD_ID, name = "backportcore_selftest")
    public static final class TestConfig {

        @Setting.Feature(icon = "minecraft:stone")
        public static final Things things = new Things();

        public static final class Things {
            @Config.RequiresMcRestart
            public boolean enabled = true;

            @Config.RangeInt(min = 0, max = 10)
            public int count = 3;

            @Setting.NewChunks
            public double chance = 0.5D;

            @Setting.Client
            public boolean clientOnly = false;

            @Setting.ListOf(Setting.Kind.BLOCK)
            public String[] blocks = {"minecraft:stone", "minecraft:dirt"};

            public Mode mode = Mode.ONE;
        }

        public enum Mode {
            ONE, TWO
        }
    }

    static void register() {
        if (System.getenv("BACKPORTCORE_SETTINGSSHOTS") != null) {
            return; // the client shots show only real mods in the sidebar
        }
        BackportSettings.register(SelfTestMod.MOD_ID, TestConfig.class);
    }

    private static SettingsModel.Entry entry(String name) {
        SettingsModel.Page page = BackportSettings.page(SelfTestMod.MOD_ID);
        if (page == null) {
            return null;
        }
        for (SettingsModel.Entry e : page.entries) {
            if (e.name.equals(name)) {
                return e;
            }
        }
        return null;
    }

    static void schedule() {
        then(1, "settings: page", world -> {
            SettingsModel.Page page = BackportSettings.page(SelfTestMod.MOD_ID);
            check("settings: the test page was built", page != null);
            check("settings: Core's own page was built", BackportSettings.page(BackportCore.MOD_ID) != null);
            if (page == null) {
                return;
            }
            check("settings: one group", page.groups.size() == 1, page.groups.size());
            SettingsModel.Group g = page.groups.get(0);
            check("settings: enabled is the group's toggle", g.toggle != null && g.toggle.name.equals("enabled"));
            check("settings: the icon is read", "minecraft:stone".equals(g.icon), g.icon);
            check("settings: five settings under it", g.entries.size() == 5, g.entries.size());
            check("settings: declaration order", g.entries.get(0).name.equals("count") && g.entries.get(4).name.equals("mode"));
            SettingsModel.Entry count = entry("count");
            check("settings: an int", count.type == SettingsModel.Type.INT);
            check("settings: its range", "0".equals(count.min()) && "10".equals(count.max()), count.min() + ".." + count.max());
            check("settings: the toggle needs a restart", g.toggle.restart);
            check("settings: new chunks flag", entry("chance").newChunks);
            check("settings: client flag", entry("clientOnly").side == SettingsModel.Side.CLIENT);
            check("settings: unmarked is server", count.side == SettingsModel.Side.SERVER);
            check("settings: list kind", entry("blocks").listKind == Setting.Kind.BLOCK && entry("blocks").type == SettingsModel.Type.LIST);
            check("settings: enum", entry("mode").type == SettingsModel.Type.ENUM && entry("mode").validValues().length == 2);
            check("settings: id", count.id.equals(SelfTestMod.MOD_ID + "/general.things/count"), count.id);
        });
        then(1, "settings: checks", world -> {
            SettingsModel.Entry count = entry("count");
            check("settings: 7 is fine", count.check(new String[]{"7"}) == null);
            check("settings: 11 is out of range", "backportcore.settings.range".equals(count.check(new String[]{"11"})));
            check("settings: words are not a number", "backportcore.settings.notNumber".equals(count.check(new String[]{"lots"})));
            check("settings: no list for a single value", count.check(new String[]{"1", "2"}) != null);
            check("settings: a bool is true or false", entry("enabled").check(new String[]{"yes"}) != null);
            check("settings: an enum takes its values only", entry("mode").check(new String[]{"THREE"}) != null
                    && entry("mode").check(new String[]{"TWO"}) == null);
            check("settings: a list entry has no line break", entry("blocks").check(new String[]{"a\nb"}) != null);
            check("settings: a double", entry("chance").check(new String[]{"0.25"}) == null && entry("chance").check(new String[]{"NaN"}) != null);
        });
        then(1, "settings: apply", world -> {
            SettingsModel.Entry count = entry("count");
            changedEvents = 0;
            Map<SettingsModel.Entry, String[]> c = new LinkedHashMap<SettingsModel.Entry, String[]>();
            c.put(count, new String[]{"7"});
            c.put(entry("blocks"), new String[]{"minecraft:sand"});
            BackportSettings.apply(c, true);
            check("settings: the int field took 7", TestConfig.things.count == 7, TestConfig.things.count);
            check("settings: the list field took the new list", TestConfig.things.blocks.length == 1 && "minecraft:sand".equals(TestConfig.things.blocks[0]));
            check("settings: one config-changed event", changedEvents == 1, changedEvents);
            check("settings: not default any more", !count.isDefault(count.get()));
            try {
                String file = new String(Files.readAllBytes(new File(Loader.instance().getConfigDir(), "backportcore_selftest.cfg").toPath()), StandardCharsets.UTF_8);
                check("settings: the file says 7", file.contains("I:count=7"));
            } catch (java.io.IOException ex) {
                check("settings: file read", false, ex);
            }
            c.clear();
            c.put(count, count.defaults());
            c.put(entry("blocks"), entry("blocks").defaults());
            BackportSettings.apply(c, true);
            check("settings: back to 3", TestConfig.things.count == 3);
        });
        then(1, "settings: the server's side", world -> serverSide(world));
    }

    @SuppressWarnings("unchecked")
    private static void serverSide(WorldServer world) {
        try {
            Class<?> net = Class.forName("com.exiledradio.backportcore.settings.SettingsNetwork");
            Method values = net.getDeclaredMethod("serverValues");
            values.setAccessible(true);
            Map<String, String[]> sv = (Map<String, String[]>) values.invoke(null);
            check("settings: server values hold server settings", sv.containsKey(SelfTestMod.MOD_ID + "/general.things/count"));
            check("settings: and not client ones", !sv.containsKey(SelfTestMod.MOD_ID + "/general.things/clientOnly"));
            check("settings: nor Core's client-only page", !sv.containsKey(BackportCore.MOD_ID + "/general.rendering/blockUpdates"));

            Method apply = net.getDeclaredMethod("applyFromPlayer", net.minecraft.entity.player.EntityPlayerMP.class, Map.class);
            apply.setAccessible(true);
            Map<String, String[]> change = new LinkedHashMap<String, String[]>();
            change.put(SelfTestMod.MOD_ID + "/general.things/count", new String[]{"9"});
            Object result = apply.invoke(null, FakePlayerFactory.getMinecraft(world), change);
            java.lang.reflect.Field status = result.getClass().getDeclaredField("status");
            status.setAccessible(true);
            check("settings: a non-operator's change is refused", ((Byte) status.get(result)) == 1);
            check("settings: and nothing changed", TestConfig.things.count == 3, TestConfig.things.count);
        } catch (ReflectiveOperationException e) {
            check("settings: server side", false, e);
        }
    }
}

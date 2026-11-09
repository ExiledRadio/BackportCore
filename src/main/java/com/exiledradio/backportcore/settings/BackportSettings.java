package com.exiledradio.backportcore.settings;

import com.exiledradio.backportcore.BackportCore;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core's settings screen: one screen for every backport's {@code @Config} settings. Every installed family mod is
 * on it without doing anything; {@link #register} adds another mod, or names a family mod's config classes
 * explicitly. The {@code .cfg} files stay Forge's: the screen sets Forge's properties and syncs them as Forge's own
 * config screen does, so each mod's config-changed handler still runs.
 */
public final class BackportSettings {

    private static final Map<String, Class<?>[]> REGISTERED = new LinkedHashMap<String, Class<?>[]>();
    /** Mods that build their own {@link Configuration}: the file, and the categories in their order. */
    private static final Map<String, Configuration> CONFIGURATIONS = new LinkedHashMap<String, Configuration>();
    private static final Map<String, String[]> CATEGORIES = new LinkedHashMap<String, String[]>();
    private static final Map<String, Map<String, String[]>> HINTS = new LinkedHashMap<String, Map<String, String[]>>();
    private static final Map<String, SettingsModel.Page> PAGES = new LinkedHashMap<String, SettingsModel.Page>();
    private static final Map<String, SettingsModel.Entry> BY_ID = new LinkedHashMap<String, SettingsModel.Entry>();
    private static boolean built;

    private BackportSettings() {
    }

    /**
     * Puts a mod's settings on the screen. Call in pre-init or init. A family mod is listed even without this; the
     * call only matters to name its config classes, or for a mod outside the family.
     *
     * @param configClasses its {@code @Config} classes; none for all of them
     */
    public static synchronized void register(String modid, Class<?>... configClasses) {
        if (built) {
            BackportCore.LOGGER.warn("Settings: {} registered after loading finished; ignored", modid);
            return;
        }
        REGISTERED.put(modid, configClasses);
    }

    /**
     * Puts a mod that builds its own {@link Configuration} (no {@code @Config}) on the screen. Call in pre-init or
     * init, after the Configuration is made. Each category is a feature row; the file stays the mod's own, and
     * Done saves it and posts the config-changed events the mod already listens for.
     *
     * @param categories its top-level categories in the order the screen lists them; none for all of them
     */
    public static synchronized void register(String modid, Configuration config, String... categories) {
        if (built) {
            BackportCore.LOGGER.warn("Settings: {} registered after loading finished; ignored", modid);
            return;
        }
        CONFIGURATIONS.put(modid, config);
        CATEGORIES.put(modid, categories);
    }

    /**
     * What {@link Setting}'s annotations say, for a mod registered with a {@link Configuration}.
     *
     * @param path  a category ({@code items}) or one property ({@code items/KEEP_ARMOR})
     * @param hints for a property {@code client}, {@code newChunks}, {@code vanilla=<value>} or
     *              {@code list=block|item|entity|biome}; for a category {@code icon=<item id>} and
     *              {@code toggle=<boolean property>}
     */
    public static synchronized void hint(String modid, String path, String... hints) {
        Map<String, String[]> m = HINTS.get(modid);
        if (m == null) {
            m = new LinkedHashMap<String, String[]>();
            HINTS.put(modid, m);
        }
        m.put(path, hints);
    }

    /** At load complete, on both sides: builds every page and records the values the game started with. */
    public static synchronized void build() {
        if (built) {
            return;
        }
        built = true;
        Map<String, Class<?>[]> all = new LinkedHashMap<String, Class<?>[]>();
        for (Family.Member m : Family.MEMBERS) {
            if (Loader.isModLoaded(m.modid)) {
                all.put(m.modid, REGISTERED.containsKey(m.modid) ? REGISTERED.get(m.modid) : new Class<?>[0]);
            }
        }
        for (Map.Entry<String, Class<?>[]> e : REGISTERED.entrySet()) {
            if (!all.containsKey(e.getKey()) && Loader.isModLoaded(e.getKey())) {
                all.put(e.getKey(), e.getValue());
            }
        }
        for (String modid : CONFIGURATIONS.keySet()) {
            if (!all.containsKey(modid) && Loader.isModLoaded(modid)) {
                all.put(modid, new Class<?>[0]);
            }
        }
        for (Map.Entry<String, Class<?>[]> e : all.entrySet()) {
            SettingsModel.Page page;
            Configuration cfg = CONFIGURATIONS.get(e.getKey());
            if (cfg != null) {
                Map<String, String[]> hints = HINTS.get(e.getKey());
                page = SettingsModel.build(e.getKey(), cfg, CATEGORIES.get(e.getKey()),
                        hints == null ? new LinkedHashMap<String, String[]>() : hints);
            } else {
                Class<?>[] classes = e.getValue().length > 0 ? e.getValue() : ConfigManager.getModConfigClasses(e.getKey());
                page = SettingsModel.build(e.getKey(), classes);
            }
            PAGES.put(e.getKey(), page);
            for (SettingsModel.Entry entry : page.entries) {
                BY_ID.put(entry.id, entry);
            }
        }
        BackportCore.LOGGER.info("Settings: {} pages, {} settings", PAGES.size(), BY_ID.size());
    }

    static synchronized List<SettingsModel.Page> pages() {
        return new ArrayList<SettingsModel.Page>(PAGES.values());
    }

    public static synchronized SettingsModel.Page page(String modid) {
        return PAGES.get(modid);
    }

    public static synchronized Collection<String> modids() {
        return new ArrayList<String>(PAGES.keySet());
    }

    public static synchronized SettingsModel.Entry entry(String id) {
        return BY_ID.get(id);
    }

    /**
     * Sets settings and saves them, then tells each mod, as Forge's config screen does.
     *
     * @param changes      setting to new value; every value must already pass {@link SettingsModel.Entry#check}
     * @param worldRunning whether a world is open (Forge's {@code isWorldRunning})
     * @return the mods whose settings changed
     */
    public static Set<String> apply(Map<SettingsModel.Entry, String[]> changes, boolean worldRunning) {
        Map<String, Boolean> mods = new LinkedHashMap<String, Boolean>();
        for (Map.Entry<SettingsModel.Entry, String[]> c : changes.entrySet()) {
            SettingsModel.Entry e = c.getKey();
            String[] value = c.getValue();
            if (SettingsModel.same(value, e.get())) {
                continue;
            }
            if (e.type == SettingsModel.Type.LIST) {
                e.property.set(value);
            } else {
                e.property.set(e.type == SettingsModel.Type.INT || e.type == SettingsModel.Type.DOUBLE ? value[0].trim() : value[0]);
            }
            String modid = e.group.page.modid;
            mods.put(modid, Boolean.TRUE.equals(mods.get(modid)) || e.restart);
        }
        for (Map.Entry<String, Boolean> m : mods.entrySet()) {
            String modid = m.getKey();
            try {
                Configuration cfg = CONFIGURATIONS.get(modid);
                if (cfg != null) {
                    if (cfg.hasChanged()) {
                        cfg.save();
                    }
                } else {
                    ConfigManager.sync(modid, Config.Type.INSTANCE);
                }
            } catch (RuntimeException ex) {
                BackportCore.LOGGER.error("Settings: saving {} failed", modid, ex);
            }
            ConfigChangedEvent event = new ConfigChangedEvent.OnConfigChangedEvent(modid, null, worldRunning, m.getValue());
            MinecraftForge.EVENT_BUS.post(event);
            if (event.getResult() != Event.Result.DENY) {
                MinecraftForge.EVENT_BUS.post(new ConfigChangedEvent.PostConfigChangedEvent(modid, null, worldRunning, m.getValue()));
            }
        }
        return new LinkedHashSet<String>(mods.keySet());
    }
}

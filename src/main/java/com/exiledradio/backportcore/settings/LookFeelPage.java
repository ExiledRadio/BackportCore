package com.exiledradio.backportcore.settings;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.LookVersions;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * The Look &amp; Feel page of the settings screen: every switch that only changes how the game looks or feels, from Core
 * and every installed backport, in one place. It holds no settings of its own: each row is the same setting as on its
 * mod's page (the same Forge property), so the two always agree.
 *
 * <p>At the top, Core's "Use the newer look and feel" (off: 1.12.2's look everywhere, each switch keeping its choice) and
 * "Newest look" (the version the look stops at). Then one section per part of the game, each with Core's switch for that
 * part ({@link LookArea}) and the backports' switches that belong to it. A backport's field joins with
 * {@link Setting.Look}; the family's existing ones are listed here by name ({@link #KNOWN}).
 */
public final class LookFeelPage {

    /** Its id on the settings screen's side list. Not a mod id. */
    public static final String ID = "backportcore:lookandfeel";

    /** The page's sections, in order. */
    public static final String[] SECTIONS = {"loading", "title", "menus", "video", "chat", "debug", "toasts", "inventory", "music", "world",
            "backports"};

    /**
     * The settings listed here without {@link Setting.Look}: {@code modid/category/field} (the category's last part, in
     * lower case, as in the {@code .cfg}) to section.
     */
    static final Map<String, String> KNOWN = new HashMap<String, String>();

    /** Core's own switch fields, each a part of the look. */
    private static final Map<String, LookArea> CORE_AREAS = new HashMap<String, LookArea>();

    static {
        core("loadingScreens", "loading", LookArea.LOADING);
        core("startupScreen", "loading", LookArea.STARTUP);
        core("titleScreen", "title", LookArea.TITLE);
        core("splashes", "title", LookArea.SPLASHES);
        core("menus", "menus", LookArea.MENUS);
        core("optionsScreens", "menus", LookArea.OPTIONS);
        core("videoSettings", "video", LookArea.VIDEO);
        core("chat", "chat", LookArea.CHAT);
        core("debug", "debug", LookArea.DEBUG);
        core("toasts", "toasts", LookArea.TOASTS);
        core("inventory", "inventory", LookArea.INVENTORY);
        core("music", "music", LookArea.MUSIC);
        core("sky", "world", LookArea.SKY);
        core("wording", "world", LookArea.WORDING);
        KNOWN.put("backportcore/textures/modernTextures", "world");
        CORE_AREAS.put("textures/modernTextures", LookArea.TEXTURES);
        KNOWN.put("backportcore/presentation/panorama", "title");
        CORE_AREAS.put("presentation/panorama", LookArea.TITLE);
        // The top of the page, outside the sections.
        KNOWN.put("backportcore/lookandfeel/newerLook", "");
        KNOWN.put("backportcore/lookandfeel/newestLook", "");

        // The backports' switches as they are today.
        KNOWN.put("trailsandtalesbackport/lookandfeel/enabled", "backports");
        KNOWN.put("trailsandtalesbackport/lookandfeel/gameMusic", "music");
        KNOWN.put("trailsandtalesbackport/lookandfeel/biomeMusic", "music");
        KNOWN.put("trailsandtalesbackport/presentation1205/chunkToasts", "toasts");
        KNOWN.put("trailsandtalesbackport/presentation1205/lowDiskSpace", "toasts");
        KNOWN.put("trailsandtalesbackport/presentation1205/recoverWorld", "menus");
        KNOWN.put("trailsandtalesbackport/presentation1205/viossa", "world");
        KNOWN.put("trailsandtalesbackport/misc/effectParticles", "world");
        KNOWN.put("cavesandcliffsbackport/music/menuMusic", "music");
        KNOWN.put("cavesandcliffsbackport/music/gameMusic", "music");
        KNOWN.put("cavesandcliffsbackport/music/biomeMusic", "music");
        KNOWN.put("netherupdatebackport/music/biomeMusic", "music");
        KNOWN.put("netherupdatebackport/changes/lavaFog", "world");
        KNOWN.put("updateaquaticbackport/water/underwaterFog", "world");
        KNOWN.put("buzzybeesbackport/tweaks/splashTexts", "title");
    }

    private static void core(String field, String section, LookArea area) {
        KNOWN.put("backportcore/lookandfeel/" + field, section);
        CORE_AREAS.put("lookandfeel/" + field, area);
    }

    @Nullable
    private static SettingsModel.Page page;

    private LookFeelPage() {
    }

    /** @return the last part of a category path ({@code general.lookandfeel} to {@code lookandfeel}) */
    private static String last(String path) {
        int dot = path.lastIndexOf('.');
        return (dot < 0 ? path : path.substring(dot + 1)).toLowerCase(Locale.ENGLISH);
    }

    /** @return the section of a setting listed by name, or null */
    @Nullable
    static String knownSection(String modid, String path, String name) {
        return KNOWN.get(modid + "/" + last(path) + "/" + name);
    }

    /** @return the part of the look a Core switch turns on and off, or null for any other setting */
    @Nullable
    public static LookArea area(SettingsModel.Entry e) {
        if (!BackportCore.MOD_ID.equals(e.group.page.modid)) {
            return null;
        }
        return CORE_AREAS.get(last(e.group.path) + "/" + e.name);
    }

    /** @return true for the master switch, "Use the newer look and feel" */
    public static boolean isMaster(SettingsModel.Entry e) {
        return BackportCore.MOD_ID.equals(e.group.page.modid) && "lookandfeel".equals(last(e.group.path)) && "newerLook".equals(e.name);
    }

    /**
     * @return the version text of a row on the page: the versions that change that part with what is installed
     *         ("1.13–1.21.9"), a backport's own version, or null for none
     */
    @Nullable
    public static String versions(SettingsModel.Entry e) {
        if (e.lookSection == null || e.lookSection.isEmpty()) {
            return null;
        }
        LookArea area = area(e);
        if (area != null) {
            String r = LookVersions.range(LookVersions.of(area));
            return r.isEmpty() ? null : r;
        }
        Family.Member m = Family.get(e.group.page.modid);
        return m != null && !m.version.isEmpty() ? m.version : null;
    }

    /** @return the versions that change a Core switch's part, for its info line ("1.13, 1.14, 1.16"), or empty */
    public static String versionList(SettingsModel.Entry e) {
        LookArea area = area(e);
        if (area == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (GameVersion v : LookVersions.of(area)) {
            if (b.length() > 0) {
                b.append(", ");
            }
            b.append(v);
        }
        return b.toString();
    }

    /**
     * Before the pages are built (both sides): the choices of Core's "Newest look" (LATEST and each installed version)
     * and of the panorama (LATEST, ROTATE, 1.12.2 and each installed version with one), so the screen cycles them.
     */
    static void prepare() {
        Configuration cfg = SettingsModel.configurationOf(BackportCore.MOD_ID, CoreConfig.class);
        if (cfg == null) {
            return;
        }
        TreeSet<GameVersion> installed = new TreeSet<GameVersion>();
        TreeSet<GameVersion> panoramas = new TreeSet<GameVersion>();
        for (Contribution c : Presentation.installed()) {
            installed.add(c.version());
            if (LookVersions.hasPanorama(c)) {
                panoramas.add(c.version());
            }
        }
        choices(cfg, "lookandfeel", "newestLook", new String[] {"LATEST"}, installed);
        choices(cfg, "presentation", "panorama", new String[] {"LATEST", "ROTATE", "1.12.2"}, panoramas);
    }

    private static void choices(Configuration cfg, String category, String name, String[] first, TreeSet<GameVersion> versions) {
        Property p = null;
        for (String cat : cfg.getCategoryNames()) {
            if (last(cat).equals(category)) {
                ConfigCategory c = cfg.getCategory(cat);
                if (c.containsKey(name)) {
                    p = c.get(name);
                }
            }
        }
        if (p == null) {
            return;
        }
        List<String> values = new ArrayList<String>();
        for (String f : first) {
            values.add(f);
        }
        for (GameVersion v : versions) {
            values.add(v.toString());
        }
        String current = p.getString();
        boolean found = false;
        for (String v : values) {
            found |= v.equals(current);
        }
        if (!found) {
            // A saved value that names no installed version (or in other case) stays choosable, as it was saved.
            values.add(current);
        }
        p.setValidValues(values.toArray(new String[0]));
    }

    /** @return the page, built once from the other pages' settings */
    public static synchronized SettingsModel.Page page() {
        if (page == null) {
            page = build();
        }
        return page;
    }

    private static SettingsModel.Page build() {
        SettingsModel.Page p = new SettingsModel.Page(ID);
        Map<String, List<SettingsModel.Entry>> sections = new LinkedHashMap<String, List<SettingsModel.Entry>>();
        for (String s : SECTIONS) {
            sections.put(s, new ArrayList<SettingsModel.Entry>());
        }
        SettingsModel.Entry master = null;
        List<SettingsModel.Entry> top = new ArrayList<SettingsModel.Entry>();
        for (SettingsModel.Page other : BackportSettings.pages()) {
            for (SettingsModel.Entry e : other.entries) {
                if (e.lookSection == null) {
                    continue;
                }
                if (isMaster(e)) {
                    master = e;
                } else if (e.lookSection.isEmpty()) {
                    top.add(e);
                } else {
                    List<SettingsModel.Entry> list = sections.get(e.lookSection);
                    if (list == null) {
                        list = sections.get("backports");
                    }
                    list.add(e);
                }
            }
        }
        SettingsModel.Group head = new SettingsModel.Group(p, "lookandfeel", "backportcore.settings.look.master", "", null, "minecraft:item_frame");
        head.toggle = master;
        head.entries.addAll(top);
        p.groups.add(head);
        for (Map.Entry<String, List<SettingsModel.Entry>> s : sections.entrySet()) {
            if (s.getValue().isEmpty()) {
                continue;
            }
            SettingsModel.Group g = new SettingsModel.Group(p, "lookandfeel." + s.getKey(), "backportcore.settings.look." + s.getKey(), "", null,
                    icon(s.getKey()));
            g.entries.addAll(s.getValue());
            p.groups.add(g);
        }
        for (SettingsModel.Group g : p.groups) {
            p.entries.addAll(g.all());
        }
        return p;
    }

    private static String icon(String section) {
        switch (section) {
            case "loading":
                return "minecraft:clock";
            case "title":
                return "minecraft:painting";
            case "menus":
                return "minecraft:stone_button";
            case "video":
                return "minecraft:ender_eye";
            case "chat":
                return "minecraft:writable_book";
            case "debug":
                return "minecraft:command_block";
            case "toasts":
                return "minecraft:bread";
            case "inventory":
                return "minecraft:chest";
            case "music":
                return "minecraft:jukebox";
            case "world":
                return "minecraft:grass";
            default:
                return "minecraft:book";
        }
    }
}

package com.exiledradio.backportcore.settings;

import com.exiledradio.backportcore.BackportCore;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A mod's settings as the settings screen shows them, read from the {@code Configuration} Forge built from its
 * {@code @Config} classes. Values are kept in Forge's {@link Property}s as strings, so the screen never needs to
 * know a field's Java type, and the {@code .cfg} file stays exactly what Forge writes.
 */
public final class SettingsModel {

    private SettingsModel() {
    }

    public enum Type {
        BOOL, INT, DOUBLE, ENUM, STRING, LIST
    }

    public enum Side {
        CLIENT, SERVER
    }

    /** One mod's page. */
    public static final class Page {
        public final String modid;
        public final List<Group> groups = new ArrayList<Group>();
        public final List<Entry> entries = new ArrayList<Entry>();

        Page(String modid) {
            this.modid = modid;
        }
    }

    /** One {@code @Config} category: a feature row, with its own settings under it. */
    public static final class Group {
        public final Page page;
        /** The category's path in the file, such as {@code general.water}. */
        public final String path;
        /** Its lang key (Forge's, from {@code @Config.LangKey}). */
        public final String langKey;
        public final String comment;
        /** The parent group's lang key for a nested category, else null. */
        public final String parentLangKey;
        public final String icon;
        /**
         * The control on the feature's own row, or null: its on/off switch (or {@code @Feature.toggle}'s), or the one
         * setting of a category that has nothing else. Not in {@link #entries}.
         */
        public Entry toggle;
        public final List<Entry> entries = new ArrayList<Entry>();

        Group(Page page, String path, String langKey, String comment, String parentLangKey, String icon) {
            this.page = page;
            this.path = path;
            this.langKey = langKey;
            this.comment = comment == null ? "" : comment;
            this.parentLangKey = parentLangKey;
            this.icon = icon;
        }

        /** @return the toggle and every setting under it */
        public List<Entry> all() {
            List<Entry> all = new ArrayList<Entry>();
            if (this.toggle != null) {
                all.add(this.toggle);
            }
            all.addAll(this.entries);
            return all;
        }
    }

    /** One setting. */
    public static final class Entry {
        public final Group group;
        public final Property property;
        public final String name;
        /** {@code modid/category.path/name}: how the network and the screen name it. */
        public final String id;
        public final Type type;
        /** For a list: the type of each entry (INT, DOUBLE, BOOL or STRING). */
        public final Type elementType;
        public final Side side;
        public final boolean restart;
        public final boolean worldRestart;
        public final boolean newChunks;
        public final String vanilla;
        public final Setting.Kind listKind;
        /** The value when the game started, to tell which changes still wait for a restart. */
        private final String[] startup;

        /** The lang key the screen reads: Forge's, or for a plain Configuration {@code modid.category.name}. */
        private final String langKey;

        Entry(Group group, Property property, Field field) {
            this(group, property, Hints.of(field), property.getLanguageKey());
        }

        Entry(Group group, Property property, Hints hints, String langKey) {
            this.group = group;
            this.property = property;
            this.langKey = langKey;
            this.name = property.getName();
            this.id = group.page.modid + "/" + group.path + "/" + this.name;
            Type element = elementType(property.getType());
            if (property.isList()) {
                this.type = Type.LIST;
                this.elementType = element;
            } else if (property.getValidValues() != null && property.getValidValues().length > 0) {
                this.type = Type.ENUM;
                this.elementType = Type.STRING;
            } else {
                this.type = element;
                this.elementType = element;
            }
            this.side = hints.client ? Side.CLIENT : Side.SERVER;
            this.restart = property.requiresMcRestart();
            this.worldRestart = property.requiresWorldRestart();
            this.newChunks = hints.newChunks;
            this.vanilla = hints.vanilla;
            this.listKind = hints.listKind;
            this.startup = get();
        }

        private static Type elementType(Property.Type t) {
            switch (t) {
                case BOOLEAN:
                    return Type.BOOL;
                case INTEGER:
                    return Type.INT;
                case DOUBLE:
                    return Type.DOUBLE;
                default:
                    return Type.STRING;
            }
        }

        /** @return the current value; one element unless this is a list */
        public String[] get() {
            return this.property.isList() ? this.property.getStringList().clone() : new String[]{this.property.getString()};
        }

        /** @return the default value; one element unless this is a list */
        public String[] defaults() {
            return this.property.isList() ? this.property.getDefaults().clone() : new String[]{this.property.getDefault()};
        }

        public boolean isDefault(String[] value) {
            return same(value, defaults());
        }

        /** @return whether this setting, set to {@code value}, waits for a restart to take effect */
        public boolean waitsForRestart(String[] value) {
            return this.restart && !same(value, this.startup);
        }

        public String langKey() {
            return this.langKey;
        }

        public String comment() {
            return this.property.getComment() == null ? "" : this.property.getComment();
        }

        public String[] validValues() {
            return this.property.getValidValues();
        }

        public String min() {
            return this.property.getMinValue();
        }

        public String max() {
            return this.property.getMaxValue();
        }

        /** @return null if {@code value} is a value this setting takes, else the reason it is not, as a lang key */
        public String check(String[] value) {
            if (value == null) {
                return "backportcore.settings.invalid";
            }
            if (this.type != Type.LIST) {
                if (value.length != 1) {
                    return "backportcore.settings.invalid";
                }
                if (this.type == Type.ENUM) {
                    return Arrays.asList(validValues()).contains(value[0]) ? null : "backportcore.settings.invalid";
                }
                return checkOne(this.type, value[0]);
            }
            if (this.property.isListLengthFixed() && value.length != defaults().length) {
                return "backportcore.settings.invalid";
            }
            if (this.property.getMaxListLength() >= 0 && value.length > this.property.getMaxListLength()) {
                return "backportcore.settings.invalid";
            }
            for (String s : value) {
                String why = checkOne(this.elementType, s);
                if (why != null) {
                    return why;
                }
            }
            return null;
        }

        private String checkOne(Type t, String s) {
            if (s == null || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
                return "backportcore.settings.invalid";
            }
            switch (t) {
                case BOOL:
                    return "true".equals(s) || "false".equals(s) ? null : "backportcore.settings.invalid";
                case INT:
                    try {
                        int i = Integer.parseInt(s.trim());
                        return inRange(i) ? null : "backportcore.settings.range";
                    } catch (NumberFormatException e) {
                        return "backportcore.settings.notNumber";
                    }
                case DOUBLE:
                    try {
                        double d = Double.parseDouble(s.trim());
                        return !Double.isNaN(d) && inRange(d) ? null : "backportcore.settings.range";
                    } catch (NumberFormatException e) {
                        return "backportcore.settings.notNumber";
                    }
                default:
                    if (this.property.getValidationPattern() != null && !this.property.getValidationPattern().matcher(s).matches()) {
                        return "backportcore.settings.invalid";
                    }
                    return null;
            }
        }

        private boolean inRange(double d) {
            try {
                return d >= Double.parseDouble(min()) && d <= Double.parseDouble(max());
            } catch (NumberFormatException | NullPointerException e) {
                return true;
            }
        }
    }

    /** What a setting's {@link Setting} annotations say, or a plain Configuration's {@link BackportSettings#hint}s. */
    static final class Hints {
        static final Hints NONE = new Hints();

        boolean client;
        boolean newChunks;
        String vanilla;
        Setting.Kind listKind = Setting.Kind.ANY;
        String icon = "";
        String toggle = "";

        static Hints of(Field field) {
            if (field == null) {
                return NONE;
            }
            Hints h = new Hints();
            h.client = field.isAnnotationPresent(Setting.Client.class);
            h.newChunks = field.isAnnotationPresent(Setting.NewChunks.class);
            Setting.Vanilla v = field.getAnnotation(Setting.Vanilla.class);
            h.vanilla = v == null ? null : v.value();
            Setting.ListOf l = field.getAnnotation(Setting.ListOf.class);
            h.listKind = l == null ? Setting.Kind.ANY : l.value();
            return h;
        }

        /**
         * @param words {@code client}, {@code server}, {@code newChunks}, {@code vanilla=2}, {@code list=block},
         *              and for a category {@code icon=minecraft:book} and {@code toggle=propertyName}
         */
        static Hints of(String[] words) {
            Hints h = new Hints();
            for (String w : words) {
                String k = w;
                String v = "";
                int eq = w.indexOf('=');
                if (eq >= 0) {
                    k = w.substring(0, eq);
                    v = w.substring(eq + 1).trim();
                }
                k = k.trim().toLowerCase(Locale.ENGLISH);
                if (k.equals("client")) {
                    h.client = true;
                } else if (k.equals("server")) {
                    h.client = false;
                } else if (k.equals("newchunks")) {
                    h.newChunks = true;
                } else if (k.equals("vanilla")) {
                    h.vanilla = v;
                } else if (k.equals("icon")) {
                    h.icon = v;
                } else if (k.equals("toggle")) {
                    h.toggle = v;
                } else if (k.equals("list")) {
                    try {
                        h.listKind = Setting.Kind.valueOf(v.toUpperCase(Locale.ENGLISH));
                    } catch (IllegalArgumentException e) {
                        BackportCore.LOGGER.warn("Settings: unknown list kind {}", v);
                    }
                } else {
                    BackportCore.LOGGER.warn("Settings: unknown hint {}", w);
                }
            }
            return h;
        }
    }

    public static boolean same(String[] a, String[] b) {
        return Arrays.equals(a, b);
    }

    // ---------------------------------------------------------------------------------------------------------

    private static Method getConfiguration;

    /** @return Forge's Configuration for a {@code @Config} class, or null */
    static Configuration configurationOf(String modid, Class<?> cls) {
        Config c = cls.getAnnotation(Config.class);
        if (c == null) {
            return null;
        }
        try {
            if (getConfiguration == null) {
                getConfiguration = ConfigManager.class.getDeclaredMethod("getConfiguration", String.class, String.class);
                getConfiguration.setAccessible(true);
            }
            return (Configuration) getConfiguration.invoke(null, modid, c.name());
        } catch (ReflectiveOperationException | RuntimeException e) {
            BackportCore.LOGGER.warn("Settings: no Forge config for {} ({})", modid, cls.getName(), e);
            return null;
        }
    }

    /** @return the page of a mod's {@code @Config} classes, in the order the classes declare their fields */
    static Page build(String modid, Class<?>[] classes) {
        Page page = new Page(modid);
        for (Class<?> cls : classes) {
            Configuration cfg = configurationOf(modid, cls);
            if (cfg == null) {
                continue;
            }
            Config c = cls.getAnnotation(Config.class);
            String root = c.category();
            Group general = null;
            for (Field f : cls.getDeclaredFields()) {
                if (!Modifier.isPublic(f.getModifiers()) || !Modifier.isStatic(f.getModifiers()) || f.isAnnotationPresent(Config.Ignore.class)) {
                    continue;
                }
                if (isValue(f.getType())) {
                    if (root.isEmpty() || !cfg.hasCategory(root)) {
                        continue;
                    }
                    if (general == null) {
                        ConfigCategory cat = cfg.getCategory(root);
                        general = new Group(page, root, cat.getLanguagekey(), cat.getComment(), null, "");
                        page.groups.add(general);
                    }
                    addEntry(general, cfg.getCategory(root), f);
                } else {
                    String path = (root.isEmpty() ? "" : root + ".") + name(f);
                    try {
                        category(page, cfg, f, path, f.get(null), null);
                    } catch (IllegalAccessException e) {
                        BackportCore.LOGGER.warn("Settings: cannot read {}.{}", cls.getName(), f.getName(), e);
                    }
                }
            }
        }
        for (Group g : page.groups) {
            // A category of one setting and no switch: that setting is the row's control.
            if (g.toggle == null && g.entries.size() == 1 && g.entries.get(0).type != Type.LIST) {
                g.toggle = g.entries.remove(0);
            }
            page.entries.addAll(g.all());
        }
        return page;
    }

    /**
     * The page of a mod that builds its own {@link Configuration} instead of using {@code @Config}. Each category is
     * a feature row, in {@code categories}' order (every top-level category when none are named), with its
     * subcategories after it. A boolean named {@code enabled} (or the category's {@code toggle} hint) is the row's
     * switch. Properties keep the mod's order. A property or category without a lang key of its own reads
     * {@code modid.category.name} and {@code modid.category}.
     *
     * @param hints {@code category} or {@code category/property} to {@link Hints#of(String[])} words
     */
    static Page build(String modid, Configuration cfg, String[] categories, Map<String, String[]> hints) {
        Page page = new Page(modid);
        List<String> top = new ArrayList<String>();
        if (categories != null && categories.length > 0) {
            top.addAll(Arrays.asList(categories));
        } else {
            for (String name : cfg.getCategoryNames()) {
                if (name.indexOf('.') < 0) {
                    top.add(name);
                }
            }
        }
        for (String name : top) {
            if (cfg.hasCategory(name)) {
                rawCategory(page, cfg.getCategory(name), null, hints);
            }
        }
        for (Group g : page.groups) {
            if (g.toggle == null && g.entries.size() == 1 && g.entries.get(0).type != Type.LIST) {
                g.toggle = g.entries.remove(0);
            }
            page.entries.addAll(g.all());
        }
        return page;
    }

    private static void rawCategory(Page page, ConfigCategory cat, String parentLangKey, Map<String, String[]> hints) {
        String path = cat.getQualifiedName();
        String[] words = hints.get(path);
        Hints h = words == null ? Hints.NONE : Hints.of(words);
        String key = cat.getLanguagekey();
        if (key == null || key.isEmpty() || key.equals(path)) {
            key = page.modid + "." + path;
        }
        Group group = new Group(page, path, key, cat.getComment(), parentLangKey, h.icon);
        page.groups.add(group);
        String toggle = h.toggle.isEmpty() ? "enabled" : h.toggle;
        for (Property p : cat.getOrderedValues()) {
            String[] pw = hints.get(path + "/" + p.getName());
            String pk = p.getLanguageKey();
            if (pk == null || pk.isEmpty() || pk.equals(p.getName())) {
                pk = key + "." + p.getName();
            }
            Entry e = new Entry(group, p, pw == null ? Hints.NONE : Hints.of(pw), pk);
            if (e.type == Type.BOOL && p.getName().equalsIgnoreCase(toggle) && group.toggle == null) {
                group.toggle = e;
            } else {
                group.entries.add(e);
            }
        }
        for (ConfigCategory child : cat.getChildren()) {
            rawCategory(page, child, key, hints);
        }
    }

    private static void category(Page page, Configuration cfg, Field field, String path, Object instance, String parentLangKey) throws IllegalAccessException {
        if (instance == null || !cfg.hasCategory(path)) {
            return;
        }
        ConfigCategory cat = cfg.getCategory(path);
        Setting.Feature feature = field.getAnnotation(Setting.Feature.class);
        Group group = new Group(page, path, cat.getLanguagekey(), cat.getComment(), parentLangKey, feature == null ? "" : feature.icon());
        page.groups.add(group);
        String toggle = feature != null && !feature.toggle().isEmpty() ? feature.toggle() : "enabled";
        List<Field> nested = new ArrayList<Field>();
        for (Field f : instance.getClass().getDeclaredFields()) {
            if (!Modifier.isPublic(f.getModifiers()) || Modifier.isStatic(f.getModifiers()) || f.isAnnotationPresent(Config.Ignore.class)) {
                continue;
            }
            if (isValue(f.getType())) {
                Entry e = addEntry(group, cat, f);
                if (e != null && e.type == Type.BOOL && f.getName().equals(toggle) && group.toggle == null) {
                    group.entries.remove(e);
                    group.toggle = e;
                }
            } else {
                nested.add(f);
            }
        }
        for (Field f : nested) {
            category(page, cfg, f, path + "." + name(f), f.get(instance), group.langKey);
        }
    }

    private static Entry addEntry(Group group, ConfigCategory cat, Field f) {
        if (Map.class.isAssignableFrom(f.getType())) {
            return null;
        }
        Property p = cat.get(f.getName());
        if (p == null) {
            return null;
        }
        Entry e = new Entry(group, p, f);
        group.entries.add(e);
        return e;
    }

    private static String name(Field f) {
        Config.Name n = f.getAnnotation(Config.Name.class);
        return (n != null ? n.value() : f.getName()).toLowerCase(Locale.ENGLISH);
    }

    /** As Forge's FieldWrapper: a value, not a category. */
    private static boolean isValue(Class<?> t) {
        return t.isPrimitive() || t.isArray() || t.isEnum() || t == String.class || Number.class.isAssignableFrom(t)
                || t == Boolean.class || t == Character.class || Map.class.isAssignableFrom(t);
    }

    /** @return the pages of every registered mod, in registration order */
    public static List<Page> pages() {
        return Collections.unmodifiableList(BackportSettings.pages());
    }
}

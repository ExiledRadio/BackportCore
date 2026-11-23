package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.option.OptionStore;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiOverlayDebug;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.21.9's debug screen entries ({@code DebugScreenEntries}, {@code DebugScreenEntryList}): each part of the F3 screen
 * is Off, shown In F3 (In Overlay from 1.21.11), or Always, and the Default and Performance profiles set them all.
 * Only the parts 1.12.2's debug screen has are listed: its lines are sorted into the parts by what they start with,
 * and lines after one (a block's properties, a backport's additions) belong to it. 1.12.2's renderers (hitboxes,
 * chunk borders, the 3D crosshair and four of its debug renderers) are entries too.
 *
 * <p>Saved in Core's options file: {@code backportcore:debug_profile} (default, performance or custom) and, for a
 * custom set, one {@code backportcore:debug/<entry>} line each.
 */
@SideOnly(Side.CLIENT)
public final class DebugEntries {

    /** An entry's status, as vanilla's {@code DebugScreenEntryStatus}. */
    public enum Status {
        ALWAYS_ON("alwaysOn"), IN_OVERLAY("inOverlay"), NEVER("never");

        public final String serialized;

        Status(String serialized) {
            this.serialized = serialized;
        }

        @Nullable
        static Status of(String s) {
            for (Status v : values()) {
                if (v.serialized.equals(s)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** One entry: its id (vanilla's path), whether it is a renderer, whether reduced debug info still shows it. */
    public static final class Entry {
        public final String id;
        public final boolean renderer;
        public final boolean allowedReduced;
        /** The version that added it (1.21.9 for the first set). */
        public final String since;

        Entry(String id, boolean renderer, boolean allowedReduced) {
            this(id, renderer, allowedReduced, "1.21.9");
        }

        Entry(String id, boolean renderer, boolean allowedReduced, String since) {
            this.id = id;
            this.renderer = renderer;
            this.allowedReduced = allowedReduced;
            this.since = since;
        }

        /** @return true when the newest installed version has it (Debug Options lists it) */
        public boolean present() {
            return Presentation.isAtLeast(com.exiledradio.backportcore.presentation.LookArea.DEBUG, this.since);
        }

        /** @return true when the player may see it ({@code DebugScreenEntry.isAllowed}) */
        public boolean allowed(boolean reduced) {
            return this.allowedReduced || !reduced;
        }
    }

    public static final String GAME_VERSION = "game_version";
    public static final String FPS = "fps";
    public static final String MEMORY = "memory";
    public static final String SYSTEM_SPECS = "system_specs";
    public static final String LOOKING_AT_BLOCK = "looking_at_block";
    public static final String CHUNK_RENDER_STATS = "chunk_render_stats";
    public static final String ENTITY_RENDER_STATS = "entity_render_stats";
    public static final String PARTICLE_RENDER_STATS = "particle_render_stats";
    public static final String CHUNK_SOURCE_STATS = "chunk_source_stats";
    public static final String PLAYER_POSITION = "player_position";
    public static final String PLAYER_SECTION_POSITION = "player_section_position";
    public static final String LIGHT_LEVELS = "light_levels";
    public static final String BIOME = "biome";
    public static final String LOCAL_DIFFICULTY = "local_difficulty";
    /** 26.1: "Day #n", split from Local Difficulty's "(Day n)". */
    public static final String DAY_COUNT = "day_count";
    public static final String ENTITY_SPAWN_COUNTS = "entity_spawn_counts";
    /** 26.1: "Speed: n blocks/tick" in the position group. */
    public static final String PLAYER_SPEED = "player_speed";
    public static final String POST_EFFECT = "post_effect";
    public static final String ENTITY_HITBOXES = "entity_hitboxes";
    public static final String CHUNK_BORDERS = "chunk_borders";
    public static final String THREE_DIMENSIONAL_CROSSHAIR = "3d_crosshair";
    public static final String WATER_LEVELS = "visualize_water_levels";
    public static final String HEIGHTMAP = "visualize_heightmap";
    public static final String COLLISION_BOXES = "visualize_collision_boxes";
    public static final String SOLID_FACES = "visualize_solid_faces";
    /** 1.21.9: the server's tick time and the connection's packets ({@link DebugParts}). */
    public static final String TPS = "tps";
    /** 1.21.9: "Targeted Fluid" and its state. */
    public static final String LOOKING_AT_FLUID = "looking_at_fluid";
    /** 1.21.9: "Targeted Entity" and its id. */
    public static final String LOOKING_AT_ENTITY = "looking_at_entity";
    /** 1.21.9: "Sounds: ..." and the cave mood. */
    public static final String SOUND_MOOD = "sound_mood";
    /** 1.21.11: the client's and the server's heightmaps (the text; {@link #HEIGHTMAP} is the renderer). */
    public static final String HEIGHTMAP_TEXT = "heightmap";
    /** 26.1: the heap and non-heap memory. */
    public static final String DETAILED_MEMORY = "detailed_memory";

    /** The entries, in vanilla's order (text by id, then renderers by id). */
    public static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            new Entry(BIOME, false, false),
            new Entry(CHUNK_RENDER_STATS, false, true),
            new Entry(CHUNK_SOURCE_STATS, false, true),
            new Entry(DAY_COUNT, false, false, "26.1"),
            new Entry(DETAILED_MEMORY, false, true, "26.1"),
            new Entry(ENTITY_RENDER_STATS, false, true),
            new Entry(ENTITY_SPAWN_COUNTS, false, false),
            new Entry(FPS, false, true),
            new Entry(GAME_VERSION, false, true),
            new Entry(HEIGHTMAP_TEXT, false, false, "1.21.11"),
            new Entry(LIGHT_LEVELS, false, false),
            new Entry(LOCAL_DIFFICULTY, false, false),
            new Entry(LOOKING_AT_BLOCK, false, false),
            new Entry(LOOKING_AT_ENTITY, false, false),
            new Entry(LOOKING_AT_FLUID, false, false),
            new Entry(MEMORY, false, true),
            new Entry(PARTICLE_RENDER_STATS, false, true),
            new Entry(PLAYER_POSITION, false, false),
            new Entry(PLAYER_SECTION_POSITION, false, true),
            new Entry(PLAYER_SPEED, false, false, "26.1"),
            new Entry(POST_EFFECT, false, false),
            new Entry(SOUND_MOOD, false, false),
            new Entry(SYSTEM_SPECS, false, true),
            new Entry(TPS, false, true),
            new Entry(THREE_DIMENSIONAL_CROSSHAIR, true, false),
            new Entry(CHUNK_BORDERS, true, false),
            new Entry(ENTITY_HITBOXES, true, false),
            new Entry(COLLISION_BOXES, true, false),
            new Entry(HEIGHTMAP, true, false),
            new Entry(SOLID_FACES, true, false),
            new Entry(WATER_LEVELS, true, false)));

    public static final String DEFAULT = "default";
    public static final String PERFORMANCE = "performance";
    public static final String CUSTOM = "custom";
    static final String PROFILE_ID = "backportcore:debug_profile";
    static final String ENTRY_ID = "backportcore:debug/";

    private static final Map<String, Status> STATUSES = new LinkedHashMap<String, Status>();
    private static String profile;

    /** For the client self-test. */
    public static volatile int filtered;

    private static Field[] rendererFlags;

    private DebugEntries() {
    }

    /** @return true when the newest installed version has 1.21.9's debug options */
    public static boolean active() {
        return VanillaRevisions.debugOptions(Presentation.newest(com.exiledradio.backportcore.presentation.LookArea.DEBUG));
    }

    /** @return a profile's statuses ({@code DebugScreenEntries.PROFILES}); entries not named are Off */
    public static Map<String, Status> profile(String name) {
        Map<String, Status> m = new LinkedHashMap<String, Status>();
        if (PERFORMANCE.equals(name)) {
            m.put(FPS, Status.ALWAYS_ON);
            m.put(MEMORY, Status.IN_OVERLAY);
        } else {
            m.put(THREE_DIMENSIONAL_CROSSHAIR, Status.IN_OVERLAY);
            m.put(GAME_VERSION, Status.IN_OVERLAY);
            m.put(FPS, Status.IN_OVERLAY);
            m.put(MEMORY, Status.IN_OVERLAY);
            m.put(SYSTEM_SPECS, Status.IN_OVERLAY);
            m.put(PLAYER_POSITION, Status.IN_OVERLAY);
            m.put(PLAYER_SECTION_POSITION, Status.IN_OVERLAY);
        }
        return m;
    }

    private static synchronized void ensureLoaded() {
        if (profile != null) {
            return;
        }
        String saved = OptionStore.get(PROFILE_ID);
        if (PERFORMANCE.equals(saved) || CUSTOM.equals(saved)) {
            profile = saved;
        } else {
            profile = DEFAULT;
        }
        STATUSES.clear();
        if (CUSTOM.equals(profile)) {
            for (Entry e : ENTRIES) {
                Status s = Status.of(String.valueOf(OptionStore.get(ENTRY_ID + e.id)));
                STATUSES.put(e.id, s == null ? Status.NEVER : s);
            }
        } else {
            STATUSES.putAll(profile(profile));
        }
    }

    /** @return the entry's status (Off when the profile does not name it) */
    public static synchronized Status status(String id) {
        ensureLoaded();
        Status s = STATUSES.get(id);
        return s == null ? Status.NEVER : s;
    }

    /** Sets one entry; the set is then custom. */
    public static synchronized void setStatus(String id, Status status) {
        ensureLoaded();
        if (!CUSTOM.equals(profile)) {
            profile = CUSTOM;
            for (Entry e : ENTRIES) {
                OptionStore.put(ENTRY_ID + e.id, status(e.id).serialized);
            }
        }
        STATUSES.put(id, status);
        OptionStore.put(PROFILE_ID, CUSTOM);
        OptionStore.put(ENTRY_ID + id, status.serialized);
    }

    /** Loads a profile (its button on Debug Options). */
    public static synchronized void loadProfile(String name) {
        profile = name;
        STATUSES.clear();
        STATUSES.putAll(profile(name));
        OptionStore.put(PROFILE_ID, name);
    }

    /** @return true when the set is exactly that profile's ({@code isUsingProfile}) */
    public static synchronized boolean usingProfile(String name) {
        ensureLoaded();
        return name.equals(profile);
    }

    /**
     * F3 + B and F3 + G ({@code toggleStatus}): Off or In F3 with the screen hidden becomes Always; with it shown, In F3
     * becomes Off and Off becomes In F3; Always becomes Off.
     *
     * @return true when the entry is now on
     */
    public static synchronized boolean toggle(String id, boolean overlayVisible) {
        ensureLoaded();
        Status s = STATUSES.get(id);
        if (s == Status.ALWAYS_ON) {
            setStatus(id, Status.NEVER);
            return false;
        }
        if (s == Status.IN_OVERLAY && overlayVisible) {
            setStatus(id, Status.NEVER);
            return false;
        }
        setStatus(id, s == Status.NEVER && overlayVisible ? Status.IN_OVERLAY : Status.ALWAYS_ON);
        return true;
    }

    @Nullable
    public static Entry entry(String id) {
        for (Entry e : ENTRIES) {
            if (e.id.equals(id)) {
                return e;
            }
        }
        return null;
    }

    /** @return true when the entry shows now: Always, or In F3 while F3 shows, and allowed */
    public static boolean enabled(String id, boolean overlayVisible, boolean reduced) {
        Entry e = entry(id);
        Status s = status(id);
        return e != null && e.allowed(reduced) && (s == Status.ALWAYS_ON || overlayVisible && s == Status.IN_OVERLAY);
    }

    // ---- The text ----

    /**
     * @return the entry each line belongs to (null for lines no entry claims: they show with F3, as other mods'),
     *         one per line; {@code ""} lines are separators ("")
     */
    public static List<String> classify(List<String> lines, boolean left, @Nullable String fpsLine, @Nullable String chunkSource,
            @Nullable String lookedAtBlock) {
        List<String> out = new ArrayList<String>();
        String current = null;
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            if (s == null || s.isEmpty()) {
                out.add("");
                current = null;
                continue;
            }
            String id = left ? leftEntry(s, fpsLine, chunkSource) : rightEntry(s, lookedAtBlock);
            if (id != null) {
                current = id;
            }
            out.add(current);
        }
        return out;
    }

    @Nullable
    static String leftEntry(String s, @Nullable String fpsLine, @Nullable String chunkSource) {
        if (s.startsWith("Minecraft ")) {
            return GAME_VERSION;
        }
        if (s.equals(fpsLine) || fpsLine == null && s.contains(" fps ")) {
            return FPS;
        }
        if (s.startsWith("C: ")) {
            return CHUNK_RENDER_STATS;
        }
        if (s.startsWith("E: ")) {
            return ENTITY_RENDER_STATS;
        }
        if (s.startsWith("P: ")) {
            return PARTICLE_RENDER_STATS;
        }
        if (s.equals(chunkSource)) {
            return CHUNK_SOURCE_STATS;
        }
        if (s.startsWith("XYZ:") || s.startsWith("Block:") || s.startsWith("Facing:") || s.startsWith("Waiting for chunk")
                || s.startsWith("Outside of world")) {
            return PLAYER_POSITION;
        }
        if (s.startsWith("Chunk:") || s.startsWith("Chunk-relative:")) {
            return PLAYER_SECTION_POSITION;
        }
        if (s.startsWith("Biome:")) {
            return BIOME;
        }
        if (s.startsWith("Light:") || s.startsWith("Client Light:") || s.startsWith("Server Light:")) {
            return LIGHT_LEVELS;
        }
        if (s.startsWith("Local Difficulty:")) {
            return LOCAL_DIFFICULTY;
        }
        if (s.startsWith("Day #")) {
            return DAY_COUNT;
        }
        if (s.startsWith("SC: ")) {
            return ENTITY_SPAWN_COUNTS;
        }
        if (s.startsWith("Speed: ")) {
            return PLAYER_SPEED;
        }
        if (s.startsWith("Shader:")) {
            return POST_EFFECT;
        }
        if (s.startsWith("Integrated server @ ") || s.startsWith("\"") && s.contains("\" server")) {
            return TPS;
        }
        if (s.startsWith("Sounds: ")) {
            return SOUND_MOOD;
        }
        if (s.startsWith("CH S: ") || s.startsWith("SH S: ")) {
            return HEIGHTMAP_TEXT;
        }
        if (s.startsWith("Looking at")) {
            return LOOKING_AT_BLOCK;
        }
        return null;
    }

    @Nullable
    static String rightEntry(String s, @Nullable String lookedAtBlock) {
        if (s.startsWith("Java:") || s.startsWith("Mem:") || s.startsWith("Allocated:")) {
            return MEMORY;
        }
        if (s.startsWith("CPU:") || s.startsWith("Display:")) {
            return SYSTEM_SPECS;
        }
        if (s.startsWith("Memory (heap)") || s.startsWith("Memory (non-heap)")) {
            return DETAILED_MEMORY;
        }
        if (s.contains("Targeted Fluid")) {
            return LOOKING_AT_FLUID;
        }
        if (s.contains("Targeted Entity")) {
            return LOOKING_AT_ENTITY;
        }
        if (s.equals(lookedAtBlock) || s.startsWith("Targeted ") || s.contains("Targeted Block")) {
            return LOOKING_AT_BLOCK;
        }
        return null;
    }

    /**
     * Keeps the lines whose entry shows now (unclaimed ones only while F3 shows), with no separator at the start, the
     * end or twice in a row. {@code entries} is {@link #classify}'s.
     */
    public static List<String> filter(List<String> lines, List<String> entries, boolean overlayVisible, boolean reduced) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < lines.size(); i++) {
            String id = entries.get(i);
            if ("".equals(id)) {
                if (!out.isEmpty() && !"".equals(out.get(out.size() - 1))) {
                    out.add("");
                }
            } else if (id == null ? overlayVisible : enabled(id, overlayVisible, reduced)) {
                out.add(lines.get(i));
            }
        }
        while (!out.isEmpty() && "".equals(out.get(out.size() - 1))) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    /** How 1.21.9's entries add their lines ({@code DebugScreenDisplayer}): alternating priority lines, plain lines, or a group. */
    static final Map<String, String> KINDS = new LinkedHashMap<String, String>();

    static {
        KINDS.put(GAME_VERSION, "!");
        KINDS.put(FPS, "!");
        for (String id : new String[] {BIOME, CHUNK_RENDER_STATS, CHUNK_SOURCE_STATS, DAY_COUNT, ENTITY_RENDER_STATS, ENTITY_SPAWN_COUNTS,
                LOCAL_DIFFICULTY, PARTICLE_RENDER_STATS, POST_EFFECT, SOUND_MOOD, TPS}) {
            KINDS.put(id, "");
        }
        KINDS.put(DETAILED_MEMORY, "memory");
        KINDS.put(MEMORY, "memory");
        KINDS.put(HEIGHTMAP_TEXT, "heightmaps");
        KINDS.put(LIGHT_LEVELS, "light");
        KINDS.put(LOOKING_AT_BLOCK, "looking_at_block");
        KINDS.put(LOOKING_AT_ENTITY, "looking_at_entity");
        KINDS.put(LOOKING_AT_FLUID, "looking_at_fluid");
        KINDS.put(PLAYER_POSITION, "position");
        KINDS.put(PLAYER_SECTION_POSITION, "position");
        KINDS.put(PLAYER_SPEED, "position");
        KINDS.put(SYSTEM_SPECS, "system");
    }

    /**
     * 1.21.9's arrangement ({@code DebugScreenOverlay.extractRenderState}): the parts that show, in their ids' order,
     * each adding its lines as vanilla's entry does: the version and the frame rate as priority lines (left, then
     * right), the plain lines split half and half (left first), then the groups (memory, heightmaps, light, what is
     * looked at, position, system) split by count, left first, a blank line after each. Lines no part claims (other
     * mods') follow in their own column. {@code entries} are {@link #classify}'s.
     */
    static void arrange(List<String> left, List<String> leftIds, List<String> right, List<String> rightIds, boolean overlayVisible,
            boolean reduced, List<String> outLeft, List<String> outRight) {
        Map<String, List<String>> blocks = new LinkedHashMap<String, List<String>>();
        List<String> otherLeft = new ArrayList<String>();
        List<String> otherRight = new ArrayList<String>();
        collect(left, leftIds, blocks, otherLeft, overlayVisible);
        collect(right, rightIds, blocks, otherRight, overlayVisible);
        List<String> regular = new ArrayList<String>();
        Map<String, List<String>> groups = new LinkedHashMap<String, List<String>>();
        List<String> ids = new ArrayList<String>(blocks.keySet());
        java.util.Collections.sort(ids);
        for (String id : ids) {
            List<String> lines = blocks.get(id);
            if (!enabled(id, overlayVisible, reduced) || lines.isEmpty()) {
                continue;
            }
            String kind = KINDS.get(id);
            if ("!".equals(kind)) {
                for (String line : lines) {
                    (outLeft.size() > outRight.size() ? outRight : outLeft).add(line);
                }
            } else if (kind == null || kind.isEmpty()) {
                regular.addAll(lines);
            } else {
                List<String> g = groups.get(kind);
                if (g == null) {
                    g = new ArrayList<String>();
                    groups.put(kind, g);
                }
                g.addAll(lines);
            }
        }
        if (!outLeft.isEmpty()) {
            outLeft.add("");
        }
        if (!outRight.isEmpty()) {
            outRight.add("");
        }
        if (!regular.isEmpty()) {
            int mid = (regular.size() + 1) / 2;
            outLeft.addAll(regular.subList(0, mid));
            outRight.addAll(regular.subList(mid, regular.size()));
            outLeft.add("");
            if (mid < regular.size()) {
                outRight.add("");
            }
        }
        List<List<String>> all = new ArrayList<List<String>>(groups.values());
        int mid = (all.size() + 1) / 2;
        for (int i = 0; i < all.size(); i++) {
            List<String> target = i < mid ? outLeft : outRight;
            target.addAll(all.get(i));
            target.add("");
        }
        appendOther(outLeft, otherLeft);
        appendOther(outRight, otherRight);
        trim(outLeft);
        trim(outRight);
    }

    /** Each claimed line into its entry's block (separators dropped); unclaimed lines, while F3 shows, kept apart. */
    private static void collect(List<String> lines, List<String> ids, Map<String, List<String>> blocks, List<String> other, boolean overlayVisible) {
        for (int i = 0; i < lines.size(); i++) {
            String id = ids.get(i);
            String line = lines.get(i);
            if ("".equals(id) || line == null) {
                if (!other.isEmpty() && !"".equals(other.get(other.size() - 1))) {
                    other.add("");
                }
                continue;
            }
            if (id == null) {
                if (overlayVisible) {
                    other.add(line);
                }
                continue;
            }
            List<String> b = blocks.get(id);
            if (b == null) {
                b = new ArrayList<String>();
                blocks.put(id, b);
            }
            b.add(line);
        }
    }

    private static void appendOther(List<String> column, List<String> other) {
        trim(other);
        while (!other.isEmpty() && "".equals(other.get(0))) {
            other.remove(0);
        }
        if (other.isEmpty()) {
            return;
        }
        if (!column.isEmpty() && !"".equals(column.get(column.size() - 1))) {
            column.add("");
        }
        column.addAll(other);
    }

    private static void trim(List<String> column) {
        while (!column.isEmpty() && "".equals(column.get(column.size() - 1))) {
            column.remove(column.size() - 1);
        }
    }

    /** 1.12.2's help line at the left's end, which 1.21.9's "To edit" line replaces (its chart line stays). */
    static boolean helpLine(String s) {
        return s != null && s.startsWith("For help: press F3 + Q");
    }

    /**
     * Edits the debug text as 1.21.9 shows it: from Forge's lists while F3 shows ({@code computed} false), or from
     * lines Core computed itself to show the Always parts while it is hidden.
     */
    static void apply(List<String> left, List<String> right, boolean overlayVisible) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean reduced = mc.isReducedDebug();
        left.removeIf(DebugEntries::helpLine);
        String fps = mc.debug;
        String source = mc.world == null ? null : mc.world.getProviderName();
        String block = null;
        RayTraceResult hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK && hit.getBlockPos() != null && mc.world != null) {
            block = String.valueOf(Block.REGISTRY.getNameForObject(mc.world.getBlockState(hit.getBlockPos()).getBlock()));
        }
        String charts = null;
        for (String s : left) {
            if (s != null && (s.startsWith("Debug: Pie [shift]") || s.startsWith("Debug charts: "))) {
                charts = s;
            }
        }
        left.remove(charts);
        List<String> leftIds = classify(left, true, fps, source, null);
        List<String> rightIds = classify(right, false, null, null, block);
        List<String> l = new ArrayList<String>();
        List<String> r = new ArrayList<String>();
        arrange(left, leftIds, right, rightIds, overlayVisible, reduced, l, r);
        if (overlayVisible) {
            if (!l.isEmpty()) {
                l.add("");
            }
            if (charts != null) {
                l.add(charts);
            }
            l.add("To edit: press [" + DebugBindings.comboName(DebugBindings.OPTIONS) + "]");
        }
        left.clear();
        left.addAll(l);
        right.clear();
        right.addAll(r);
        filtered++;
    }

    /** 1.12.2's own debug lines, for the Always parts while F3 is hidden. */
    static final class Lines extends GuiOverlayDebug {
        Lines(Minecraft mc) {
            super(mc);
        }

        List<String> left() {
            return call();
        }

        List<String> right() {
            return getDebugInfoRight();
        }

        @Override
        protected void renderDebugInfoLeft() {
        }

        @Override
        protected void renderDebugInfoRight(ScaledResolution scaledRes) {
        }
    }

    // ---- The renderers ----

    /** Each client tick from 1.21.9: 1.12.2's renderer switches follow the entries. */
    static void syncRenderers(Minecraft mc) {
        if (mc.world == null || mc.getRenderManager() == null) {
            return;
        }
        boolean f3 = mc.gameSettings.showDebugInfo;
        boolean reduced = mc.isReducedDebug();
        mc.getRenderManager().setDebugBoundingBox(enabled(ENTITY_HITBOXES, f3, reduced));
        if (rendererFlags != null && rendererFlags.length == 0) {
            return;
        }
        try {
            if (rendererFlags == null) {
                Class<?> c = net.minecraft.client.renderer.debug.DebugRenderer.class;
                rendererFlags = new Field[] {
                        ReflectionHelper.findField(c, "chunkBorderEnabled", "field_190079_e"),
                        ReflectionHelper.findField(c, "waterEnabled", "field_190081_g"),
                        ReflectionHelper.findField(c, "heightMapEnabled", "field_190082_h"),
                        ReflectionHelper.findField(c, "collisionBoxEnabled", "field_191326_j"),
                        ReflectionHelper.findField(c, "solidFaceEnabled", "field_193853_n")};
            }
            String[] ids = {CHUNK_BORDERS, WATER_LEVELS, HEIGHTMAP, COLLISION_BOXES, SOLID_FACES};
            for (int i = 0; i < ids.length; i++) {
                rendererFlags[i].setBoolean(mc.debugRenderer, enabled(ids[i], f3, reduced));
            }
        } catch (RuntimeException | IllegalAccessException e) {
            BackportCore.LOGGER.warn("Presentation: debug renderers could not be set: {}", e.toString());
            rendererFlags = new Field[0];
        }
    }
}

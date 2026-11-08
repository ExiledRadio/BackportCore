package com.exiledradio.backportcore.locate;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.structure.ScatteredPlacement;
import com.exiledradio.backportcore.structure.ScatteredStructure;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The family's {@code /locate structure}: every backport registers the structures it builds, under today's ids
 * ({@code minecraft:shipwreck}, {@code minecraft:village_plains}), and Core's {@code /locate} finds them the way
 * today's game does ({@code ChunkGenerator.findNearestMapStructure}).
 *
 * <h2>Registering</h2>
 * Most structures sit on a grid ({@link ScatteredPlacement}): one candidate chunk per region, and a rule that says
 * which structure (if any) starts there. Register the grid once per structure set, with every id it can start, in
 * init:
 * <pre>
 * StructureLocate.placement("minecraft:shipwrecks", world -&gt; ..., "minecraft:shipwreck", "minecraft:shipwreck_beached");
 * StructureLocate.tag("minecraft:shipwreck", "minecraft:shipwreck", "minecraft:shipwreck_beached");
 * </pre>
 * A {@link ScatteredStructure} kind has a shortcut, {@link #scattered}. Structures that are not on a grid
 * (strongholds, 1.12.2's own searches) register a {@link Finder} instead. An id can have both: the finder answers
 * in worlds where the placement has nothing (the world's generator is another mod's, say).
 *
 * <h2>Searching (today's rules)</h2>
 * Finders are asked first. Then the grids ring by ring outwards from the region holding the sender's chunk, up to
 * 100 rings: in each ring every grid is walked x outer, z inner, and its first candidate chunk that starts one of
 * the wanted structures counts. The first ring where any grid found one ends the search, with the nearest of what
 * was found (finders included). The position is the start chunk's lowest corner plus the grid's locate offset, as
 * today's {@code StructurePlacement.getLocatePos}; nothing is loaded or generated.
 */
public final class StructureLocate {

    /** Today's {@code /locate} search radius, in regions. */
    public static final int RINGS = 100;

    /** One structure set's grid in one world. */
    public interface Placement {
        ScatteredPlacement grid();

        /**
         * Asked only of the grid's candidate chunks. Must not load or generate chunks.
         *
         * @return the id of the structure that starts in this chunk, or null for none
         */
        @Nullable
        String startAt(int chunkX, int chunkZ);

        /** Today's {@code getLocatePos}: the chunk's lowest corner plus the set's {@code locate_offset}. */
        default BlockPos locatePos(int chunkX, int chunkZ) {
            return new BlockPos(chunkX << 4, 0, chunkZ << 4);
        }
    }

    /** Gives a world's {@link Placement}. */
    @FunctionalInterface
    public interface PlacementFactory {
        /**
         * @return the set's placement in this world, or null where the world has none of it
         * @throws Unavailable with the reason, where a player would otherwise wonder why nothing is found
         */
        @Nullable
        Placement in(WorldServer world) throws Unavailable;
    }

    /** Finds a structure that is not on a grid. */
    @FunctionalInterface
    public interface Finder {
        /** @return a position of the nearest one, or null for none */
        @Nullable
        BlockPos nearest(WorldServer world, BlockPos from) throws Unavailable;
    }

    /** What decides a {@link ScatteredStructure} start's id. */
    @FunctionalInterface
    public interface StartName {
        /** @return the id of what starts in this chunk (which does start one of the kind), or null */
        @Nullable
        String idAt(World world, ScatteredStructure structure, int chunkX, int chunkZ);
    }

    /** Why a world has none of a structure, said to the player instead of "could not find". */
    public static final class Unavailable extends Exception {
        private static final long serialVersionUID = 1L;
        private final ITextComponent reason;

        public Unavailable(ITextComponent reason) {
            super(reason.getUnformattedText());
            this.reason = reason;
        }

        public Unavailable(String key, Object... args) {
            this(new TextComponentTranslation(key, args));
        }

        public ITextComponent reason() {
            return this.reason;
        }
    }

    /** A grid-placed structure set's placement made from parts. */
    public static final class Grid implements Placement {
        private final ScatteredPlacement grid;
        private final StartRule rule;
        private final int offsetX;
        private final int offsetY;
        private final int offsetZ;

        /** Which id starts in a candidate chunk. */
        @FunctionalInterface
        public interface StartRule {
            @Nullable
            String startAt(int chunkX, int chunkZ);
        }

        public Grid(ScatteredPlacement grid, StartRule rule) {
            this(grid, rule, 0, 0, 0);
        }

        /** @param offsetX today's {@code locate_offset} (buried treasure's is 9, 0, 9) */
        public Grid(ScatteredPlacement grid, StartRule rule, int offsetX, int offsetY, int offsetZ) {
            this.grid = grid;
            this.rule = rule;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }

        @Override
        public ScatteredPlacement grid() {
            return this.grid;
        }

        @Nullable
        @Override
        public String startAt(int chunkX, int chunkZ) {
            return this.rule.startAt(chunkX, chunkZ);
        }

        @Override
        public BlockPos locatePos(int chunkX, int chunkZ) {
            return new BlockPos((chunkX << 4) + this.offsetX, this.offsetY, (chunkZ << 4) + this.offsetZ);
        }
    }

    /** A located structure: where, and which id (a tag search says which of its members it found). */
    public static final class Found {
        public final BlockPos pos;
        public final String id;

        Found(BlockPos pos, String id) {
            this.pos = pos;
            this.id = id;
        }

        @Override
        public String toString() {
            return this.id + " at " + this.pos.getX() + ", " + this.pos.getZ();
        }
    }

    private static final class SetEntry {
        final String name;
        final PlacementFactory factory;
        final Set<String> ids;

        SetEntry(String name, PlacementFactory factory, Set<String> ids) {
            this.name = name;
            this.factory = factory;
            this.ids = ids;
        }
    }

    private static final Map<String, SetEntry> SETS = new LinkedHashMap<String, SetEntry>();
    /** id -> the set that places it. */
    private static final Map<String, SetEntry> SET_OF = new LinkedHashMap<String, SetEntry>();
    /** id or "#tag" -> its finder. */
    private static final Map<String, Finder> FINDERS = new LinkedHashMap<String, Finder>();
    private static final Map<String, Set<String>> TAGS = new LinkedHashMap<String, Set<String>>();

    private StructureLocate() {
    }

    // ---- registering

    /**
     * Registers a structure set's grid. Call in init. A set registered again replaces the earlier one.
     *
     * @param set the set's name (today's {@code worldgen/structure_set}, {@code minecraft:shipwrecks}); only
     *            used to tell sets apart
     * @param ids every structure id the set can start
     */
    public static synchronized void placement(String set, PlacementFactory factory, String... ids) {
        Set<String> names = new LinkedHashSet<String>();
        for (String id : ids) {
            names.add(normalize(id));
        }
        SetEntry old = SETS.remove(set);
        if (old != null) {
            for (String id : old.ids) {
                SET_OF.remove(id);
            }
        }
        SetEntry entry = new SetEntry(set, factory, names);
        SETS.put(set, entry);
        for (String id : names) {
            SET_OF.put(id, entry);
        }
    }

    /**
     * Registers a {@link ScatteredStructure} kind's grid: a chunk starts {@code ids[0]} (or the id {@code name}
     * gives) where the kind's own {@code canStartAt} says it starts one. A start laid out and found empty is
     * skipped, as today's game skips an invalid start. The world's instance comes from
     * {@link ScatteredStructure#of}, so a world without the kind has none.
     *
     * @param name gives the id when the kind starts several (warm and cold ocean ruins); null for {@code ids[0]}
     */
    public static void scattered(String set, String kind, @Nullable StartName name, String... ids) {
        scattered(set, kind, name, 0, 0, 0, ids);
    }

    /** {@link #scattered} with today's {@code locate_offset} for the set. */
    public static void scattered(String set, final String kind, @Nullable final StartName name,
                                 final int offsetX, final int offsetY, final int offsetZ, String... ids) {
        final String only = normalize(ids[0]);
        placement(set, world -> {
            final ScatteredStructure structure = ScatteredStructure.of(world, kind);
            if (structure == null) {
                return null;
            }
            return new Grid(structure.placement(), (x, z) -> {
                if (!structure.locatableAt(world, x, z)) {
                    return null;
                }
                if (name == null) {
                    return only;
                }
                String id = name.idAt(world, structure, x, z);
                return id == null ? null : normalize(id);
            }, offsetX, offsetY, offsetZ);
        }, ids);
    }

    /**
     * Registers a finder for an id, or for a tag ({@code "#minecraft:village"}). An id's finder answers in worlds
     * where its grid gives no placement; a tag's finder answers in worlds where none of its members can be
     * searched (1.12.2's own villages, say).
     */
    public static synchronized void finder(String idOrTag, Finder finder) {
        FINDERS.put(normalize(idOrTag), finder);
    }

    /** Adds members to a tag ({@code "minecraft:village"} or {@code "#minecraft:village"}); a tag can be built up by several mods. */
    public static synchronized void tag(String tag, String... members) {
        String name = normalize(tag.startsWith("#") ? tag.substring(1) : tag);
        Set<String> set = TAGS.get(name);
        if (set == null) {
            set = new LinkedHashSet<String>();
            TAGS.put(name, set);
        }
        for (String member : members) {
            set.add(normalize(member));
        }
    }

    // ---- asking

    /** @return today's id form: lower case, {@code minecraft:} where no namespace is given; a leading '#' is kept */
    public static String normalize(String id) {
        boolean tag = id.startsWith("#");
        String bare = (tag ? id.substring(1) : id).toLowerCase(Locale.ROOT);
        String full = bare.indexOf(':') < 0 ? "minecraft:" + bare : bare;
        return tag ? "#" + full : full;
    }

    /** @return every registered structure id, sorted */
    public static synchronized List<String> ids() {
        Set<String> all = new TreeSet<String>(SET_OF.keySet());
        for (String key : FINDERS.keySet()) {
            if (!key.startsWith("#")) {
                all.add(key);
            }
        }
        return new ArrayList<String>(all);
    }

    /** @return every tag with at least one member, as {@code #namespace:path}, sorted */
    public static synchronized List<String> tags() {
        Set<String> all = new TreeSet<String>();
        for (Map.Entry<String, Set<String>> e : TAGS.entrySet()) {
            if (!e.getValue().isEmpty()) {
                all.add("#" + e.getKey());
            }
        }
        return new ArrayList<String>(all);
    }

    /** @return a tag's members (without '#'), empty for an unknown tag */
    public static synchronized Set<String> members(String tag) {
        Set<String> set = TAGS.get(normalize(tag.startsWith("#") ? tag.substring(1) : tag));
        return set == null ? Collections.<String>emptySet() : new LinkedHashSet<String>(set);
    }

    /** @return true if this id or tag names anything that can be searched for */
    public static synchronized boolean known(String idOrTag) {
        String key = normalize(idOrTag);
        if (key.startsWith("#")) {
            return !members(key).isEmpty() || FINDERS.containsKey(key);
        }
        return SET_OF.containsKey(key) || FINDERS.containsKey(key);
    }

    /**
     * The nearest of an id or a tag's members, by today's search.
     *
     * @return what was found, or null for none
     * @throws Unavailable when nothing was found and a placement or finder said why
     */
    @Nullable
    public static Found nearest(WorldServer world, BlockPos from, String idOrTag) throws Unavailable {
        String key = normalize(idOrTag);
        Collection<String> wanted = key.startsWith("#") ? members(key) : Collections.singleton(key);

        Map<SetEntry, Set<String>> bySet = new LinkedHashMap<SetEntry, Set<String>>();
        List<String> byFinder = new ArrayList<String>();
        synchronized (StructureLocate.class) {
            for (String id : wanted) {
                SetEntry set = SET_OF.get(id);
                if (set != null) {
                    Set<String> ids = bySet.get(set);
                    if (ids == null) {
                        ids = new LinkedHashSet<String>();
                        bySet.put(set, ids);
                    }
                    ids.add(id);
                } else if (FINDERS.containsKey(id)) {
                    byFinder.add(id);
                }
            }
        }

        Unavailable why = null;
        List<Placement> placements = new ArrayList<Placement>();
        List<Set<String>> placementIds = new ArrayList<Set<String>>();
        for (Map.Entry<SetEntry, Set<String>> e : bySet.entrySet()) {
            Placement p = null;
            try {
                p = e.getKey().factory.in(world);
            } catch (Unavailable u) {
                why = why == null ? u : why;
            } catch (RuntimeException ex) {
                BackportCore.LOGGER.error("Locate: the {} placement failed in {}.", e.getKey().name, world.provider.getDimensionType().getName(), ex);
            }
            if (p != null) {
                placements.add(p);
                placementIds.add(e.getValue());
            } else {
                // The world's generator has no grid for these: an id's own finder answers, if it has one.
                for (String id : e.getValue()) {
                    if (finder(id) != null) {
                        byFinder.add(id);
                    }
                }
            }
        }

        Found best = null;
        double bestDistance = Double.MAX_VALUE;
        for (String id : byFinder) {
            BlockPos pos;
            try {
                pos = ask(finder(id), world, from);
            } catch (Unavailable u) {
                why = why == null ? u : why;
                continue;
            }
            if (pos != null) {
                double d = from.distanceSq(pos);
                if (d < bestDistance) {
                    best = new Found(pos, id);
                    bestDistance = d;
                }
            }
        }

        if (!placements.isEmpty()) {
            Found ring = searchGrids(world.getSeed(), from, placements, placementIds, best, bestDistance);
            if (ring != null) {
                return ring;
            }
        }
        if (best != null) {
            return best;
        }

        // A tag whose members this world cannot search: the tag's own finder (1.12.2's search).
        if (key.startsWith("#") && placements.isEmpty() && byFinder.isEmpty()) {
            Finder tagFinder = finder(key);
            if (tagFinder != null) {
                try {
                    BlockPos pos = ask(tagFinder, world, from);
                    if (pos != null) {
                        return new Found(pos, key);
                    }
                } catch (Unavailable u) {
                    why = why == null ? u : why;
                }
            }
        }
        if (why != null) {
            throw why;
        }
        return null;
    }

    @Nullable
    private static synchronized Finder finder(String key) {
        return FINDERS.get(key);
    }

    @Nullable
    private static BlockPos ask(@Nullable Finder finder, WorldServer world, BlockPos from) throws Unavailable {
        if (finder == null) {
            return null;
        }
        try {
            return finder.nearest(world, from);
        } catch (RuntimeException e) {
            BackportCore.LOGGER.error("Locate: a structure finder failed.", e);
            return null;
        }
    }

    /** Today's ring walk over the random-spread sets ({@code findNearestMapStructure}). */
    @Nullable
    static Found searchGrids(long seed, BlockPos from, List<Placement> placements, List<Set<String>> ids,
                             @Nullable Found best, double bestDistance) {
        int originX = from.getX() >> 4;
        int originZ = from.getZ() >> 4;
        for (int radius = 0; radius <= RINGS; radius++) {
            boolean foundSomething = false;
            for (int i = 0; i < placements.size(); i++) {
                Found found = firstInRing(seed, originX, originZ, radius, placements.get(i), ids.get(i));
                if (found != null) {
                    foundSomething = true;
                    double d = from.distanceSq(found.pos);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = found;
                    }
                }
            }
            if (foundSomething) {
                return best;
            }
        }
        return best;
    }

    /** Today's {@code getNearestGeneratedStructure} for one ring of one grid: the first start, x outer, z inner. */
    @Nullable
    private static Found firstInRing(long seed, int originX, int originZ, int radius, Placement placement, Set<String> ids) {
        ScatteredPlacement grid = placement.grid();
        int spacing = grid.spacing();
        for (int x = -radius; x <= radius; x++) {
            boolean xEdge = x == -radius || x == radius;
            for (int z = -radius; z <= radius; z++) {
                boolean zEdge = z == -radius || z == radius;
                if (!xEdge && !zEdge) {
                    continue;
                }
                ChunkPos target = grid.candidate(seed, originX + spacing * x, originZ + spacing * z);
                String id;
                try {
                    id = placement.startAt(target.x, target.z);
                } catch (RuntimeException e) {
                    BackportCore.LOGGER.error("Locate: a start check failed at chunk {}, {}.", target.x, target.z, e);
                    id = null;
                }
                if (id != null && ids.contains(id)) {
                    return new Found(placement.locatePos(target.x, target.z), id);
                }
            }
        }
        return null;
    }

    /** @return true if the string reads as a resource location (letters, digits, {@code _-./:}) */
    static boolean isIdLike(String s) {
        String body = s.startsWith("#") ? s.substring(1) : s;
        if (body.isEmpty()) {
            return false;
        }
        try {
            new ResourceLocation(body);
        } catch (RuntimeException e) {
            return false;
        }
        return body.matches("[a-zA-Z0-9_.\\-]+(:[a-zA-Z0-9_./\\-]+)?");
    }

    /** @return the grid sets placing any of these ids, by set name */
    static synchronized Map<String, PlacementFactory> factoriesFor(Collection<String> ids) {
        Map<String, PlacementFactory> out = new LinkedHashMap<String, PlacementFactory>();
        for (String id : ids) {
            SetEntry set = SET_OF.get(id);
            if (set != null) {
                out.put(set.name, set.factory);
            }
        }
        return out;
    }

    /** @return every grid set name, for the self-tests and the log */
    public static synchronized List<String> sets() {
        return new ArrayList<String>(SETS.keySet());
    }
}

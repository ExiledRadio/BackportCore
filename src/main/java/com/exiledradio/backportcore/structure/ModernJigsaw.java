package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.IntBinaryOperator;

/**
 * Jigsaw assembly as 1.20 to 26.x do it ({@code JigsawPlacement.addPieces}'s {@code Placer}), beside {@link Jigsaw},
 * which is 1.16.5's (bastions, villages) and stays as it is. From the start piece, each jigsaw of each piece (shuffled,
 * then by {@code selection_priority}) tries the elements of its pool and then its fallback's (shuffled), each in
 * shuffled rotations, joining the first that has a matching jigsaw and fits in the free space; the pieces it opens are
 * worked through by {@code placement_priority}, first in, first out within one. Every shuffle is {@code Util.shuffle}
 * (n - 1 draws, as {@link Collections#shuffle}); 1.16.5's pool shuffle is fastutil's, one draw more. So with
 * {@link StructureRandoms#start} as the random the same seed gives the version's own layout, and the pieces record
 * their jigsaw junctions as today's game does, for {@link TerrainAdaptation}'s beard.
 *
 * <p>The versions' rules, each a switch:
 * <ul>
 * <li>{@link #priorities}: 1.20.3+ reads the jigsaws' {@code selection_priority} and {@code placement_priority}.</li>
 * <li>{@link #aliases}: 1.20.5+'s {@code pool_aliases} ({@link PoolAliases}).</li>
 * <li>{@link #namelessFeatures}: in 26.x a {@code feature_pool_element}'s jigsaw has no name, so it joins any
 *     jigsaw ({@code JigsawBlock.canAttach}: {@code target.name() == null}); before, it is {@code minecraft:bottom}.</li>
 * <li>{@link #expansionHack}: {@code use_expansion_hack}.</li>
 * <li>{@link #surface}: the ground for {@code terrain_matching} pieces; without one, the joint's own height.</li>
 * </ul>
 *
 * <pre>
 * // the stub (start piece) is the structure's own: start height, projection, padding, biome checks
 * Random random = StructureRandoms.start(seed, chunkX, chunkZ);
 * MyPiece start = ...;  // made from random as the version draws it
 * List&lt;MyPiece&gt; pieces = ModernJigsaw.of(MyPiece::new).maxDepth(7).priorities(true)
 *         .aliases(aliases.resolve(seed, origin)).assemble(start, bounds, random);
 * </pre>
 *
 * Pools are read by {@link JigsawPools}. Nothing is placed and no chunk is loaded here.
 *
 * @param <P> the structure's piece: a {@link JigsawPiece} that keeps its junctions
 * @since 1.4.4
 */
public final class ModernJigsaw<P extends JigsawPiece & ModernJigsaw.Joined> {

    /** A piece that records today's jigsaw junctions (for the terrain's beard). */
    public interface Joined {
        /** Today's {@code JigsawJunction}: the joint's x and z, the ground's y there. */
        void addJunction(int x, int groundY, int z);
    }

    /** Makes the piece objects. */
    @FunctionalInterface
    public interface PieceFactory<P> {
        P create(JigsawElement element, BlockPos position, int groundLevelDelta, Rotation rotation, StructureBoundingBox box);
    }

    private final PieceFactory<P> factory;
    private int maxDepth = 7;
    private boolean expansionHack;
    private boolean priorities;
    private boolean namelessFeatures;
    private Map<String, String> aliases = Collections.emptyMap();
    @Nullable
    private IntBinaryOperator surface;

    private ModernJigsaw(PieceFactory<P> factory) {
        this.factory = factory;
    }

    public static <P extends JigsawPiece & Joined> ModernJigsaw<P> of(PieceFactory<P> factory) {
        return new ModernJigsaw<P>(factory);
    }

    /** The structure's {@code size}: how many joins from the start piece. */
    public ModernJigsaw<P> maxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
        return this;
    }

    /** {@code use_expansion_hack}: short pieces leave room above for what joins them. */
    public ModernJigsaw<P> expansionHack(boolean expansionHack) {
        this.expansionHack = expansionHack;
        return this;
    }

    /** 1.20.3+: the jigsaws' {@code selection_priority} and {@code placement_priority}. Off, every jigsaw is 0. */
    public ModernJigsaw<P> priorities(boolean priorities) {
        this.priorities = priorities;
        return this;
    }

    /** 26.x: a feature element's jigsaw has no name and joins any jigsaw. */
    public ModernJigsaw<P> namelessFeatures(boolean nameless) {
        this.namelessFeatures = nameless;
        return this;
    }

    /** The structure's resolved {@code pool_aliases}, full pool name to full pool name ({@link PoolAliases#resolve}). */
    public ModernJigsaw<P> aliases(Map<String, String> aliases) {
        this.aliases = aliases;
        return this;
    }

    /**
     * {@code getFirstFreeHeight(x, z, WORLD_SURFACE_WG)} for {@code terrain_matching} pieces, asked only if one needs
     * it; null (the default) takes the joint's own height.
     */
    public ModernJigsaw<P> surface(@Nullable IntBinaryOperator surface) {
        this.surface = surface;
        return this;
    }

    /** @return {@code pool} as a full name, through the aliases */
    public String pool(String pool) {
        String full = full(pool);
        String to = this.aliases.get(full);
        return to == null ? full : to;
    }

    /**
     * Lays out the children of a start piece already made from {@code random}, and gives every piece today's pivot
     * (the start piece's middle at its floor, {@code StructureStart.placeInChunk}).
     *
     * @param bounds the box the pieces stay inside: {@code max_distance_from_center} out from the start, cut to the
     *               world's heights (and padding) as the version does; its blocks are the AABB's, max inclusive
     * @param random the layout's random, drawn on from where the stub left it
     * @return the pieces, start piece first
     */
    public List<P> assemble(P start, StructureBoundingBox bounds, Random random) {
        List<P> pieces = new ArrayList<P>();
        pieces.add(start);
        if (this.maxDepth > 0) {
            Space cube = new Space(new StructureBoundingBox(bounds));
            cube.taken.add(new StructureBoundingBox(start.getBoundingBox()));
            Placing<P> placing = new Placing<P>();
            placing.add(new State<P>(start, cube, 0), 0);
            State<P> next;
            while ((next = placing.next()) != null) {
                tryPlacingChildren(next.piece, next.free, next.depth, pieces, placing, random);
            }
        }
        StructureBoundingBox b = start.getBoundingBox();
        BlockPos pivot = new BlockPos(b.minX + (b.maxX - b.minX + 1) / 2, b.minY, b.minZ + (b.maxZ - b.minZ + 1) / 2);
        for (P piece : pieces) {
            piece.setPivot(pivot);
        }
        return pieces;
    }

    // ---- free space: today's VoxelShapes, for whole-block boxes

    private static final class Space {
        @Nullable
        final StructureBoundingBox outside;
        final List<StructureBoundingBox> taken = new ArrayList<StructureBoundingBox>();

        Space(@Nullable StructureBoundingBox outside) {
            this.outside = outside;
        }

        /** "The box, shrunk by a quarter block, is all inside the free shape." */
        boolean fits(StructureBoundingBox box) {
            if (this.outside != null && !(this.outside.minX <= box.minX && box.maxX <= this.outside.maxX
                    && this.outside.minY <= box.minY && box.maxY <= this.outside.maxY
                    && this.outside.minZ <= box.minZ && box.maxZ <= this.outside.maxZ)) {
                return false;
            }
            for (StructureBoundingBox t : this.taken) {
                if (t.intersectsWith(box)) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class State<P> {
        final P piece;
        final Space free;
        final int depth;

        State(P piece, Space free, int depth) {
            this.piece = piece;
            this.free = free;
            this.depth = depth;
        }
    }

    /** {@code SequencedPriorityIterator}: the highest priority first, first in, first out within one. */
    private static final class Placing<P> {
        private final TreeMap<Integer, ArrayDeque<State<P>>> queues = new TreeMap<Integer, ArrayDeque<State<P>>>();

        void add(State<P> s, int priority) {
            ArrayDeque<State<P>> q = this.queues.get(priority);
            if (q == null) {
                q = new ArrayDeque<State<P>>();
                this.queues.put(priority, q);
            }
            q.addLast(s);
        }

        @Nullable
        State<P> next() {
            while (!this.queues.isEmpty()) {
                Map.Entry<Integer, ArrayDeque<State<P>>> top = this.queues.lastEntry();
                State<P> s = top.getValue().pollFirst();
                if (top.getValue().isEmpty()) {
                    this.queues.remove(top.getKey());
                }
                if (s != null) {
                    return s;
                }
            }
            return null;
        }
    }

    private static final Comparator<JigsawElement.Connector> HIGHEST_SELECTION_PRIORITY_FIRST =
            Comparator.comparingInt((JigsawElement.Connector c) -> c.selectionPriority).reversed();

    /** {@code StructurePoolElement.getShuffledJigsawBlocks}: shuffled, then stably sorted by selection priority. */
    private List<JigsawElement.Connector> shuffledJoints(JigsawElement element, BlockPos pos, Rotation rotation, Random random) {
        List<JigsawElement.Connector> out = shuffled(element.connectors(pos, rotation), random);
        if (this.priorities) {
            out.sort(HIGHEST_SELECTION_PRIORITY_FIRST);
        }
        return out;
    }

    /** {@code JigsawBlock.canAttach}; with nameless features, a feature's jigsaw joins any. */
    private boolean canAttach(JigsawElement.Connector source, JigsawElement.Connector target, boolean targetNameless) {
        boolean rollable = "rollable".equals(source.joint);
        return source.front == target.front.getOpposite() && (rollable || source.top == target.top)
                && (targetNameless || source.target.equals(target.name));
    }

    private void tryPlacingChildren(P parent, Space outerFree, int depth, List<P> pieces, Placing<P> placing, Random random) {
        JigsawElement element = parent.element();
        BlockPos position = parent.position();
        Rotation rotation = parent.rotation();
        boolean parentRigid = element.rigid();
        Space inner = null;
        StructureBoundingBox parentBox = parent.getBoundingBox();
        int parentMinY = parentBox.minY;

        connectorLoop:
        for (JigsawElement.Connector connector : shuffledJoints(element, position, rotation, random)) {
            EnumFacing front = connector.front;
            BlockPos at = connector.pos;
            BlockPos target = at.offset(front);
            int connectorY = at.getY() - parentMinY;
            int surfaceY = Integer.MIN_VALUE;
            ResourceLocation poolId = new ResourceLocation(pool(connector.pool));
            JigsawPools.Pool pool = JigsawPools.pool(poolId);
            if (pool == null || (pool.size() == 0 && !poolId.equals(JigsawPools.EMPTY))) {
                BackportCore.LOGGER.warn("Empty or non-existent pool: {}", poolId);
                continue;
            }
            ResourceLocation fallbackId = pool.fallback();
            JigsawPools.Pool fallback = JigsawPools.pool(fallbackId);
            if (fallback == null || (fallback.size() == 0 && !fallbackId.equals(JigsawPools.EMPTY))) {
                BackportCore.LOGGER.warn("Empty or non-existent fallback pool: {}", fallbackId);
                continue;
            }
            Space free;
            if (parentBox.isVecInside(target)) {
                if (inner == null) {
                    inner = new Space(new StructureBoundingBox(parentBox));
                }
                free = inner;
            } else {
                free = outerFree;
            }
            List<JigsawElement> candidates = new ArrayList<JigsawElement>();
            if (depth != this.maxDepth) {
                candidates.addAll(shuffled(pool.elements(), random));
            }
            candidates.addAll(shuffled(fallback.elements(), random));
            int placementPriority = this.priorities ? connector.placementPriority : 0;

            for (JigsawElement candidate : candidates) {
                if (candidate.isEmpty()) {
                    break;
                }
                boolean nameless = this.namelessFeatures && candidate instanceof JigsawElement.Feature;
                for (Rotation r : shuffled(Arrays.asList(Rotation.values()), random)) {
                    List<JigsawElement.Connector> theirs = shuffledJoints(candidate, BlockPos.ORIGIN, r, random);
                    int expandTo = 0;
                    if (this.expansionHack) {
                        StructureBoundingBox hackBox = candidate.box(BlockPos.ORIGIN, r);
                        if (hackBox.getYSize() <= 16) {
                            for (JigsawElement.Connector j : theirs) {
                                if (!hackBox.isVecInside(j.pos.offset(j.front))) {
                                    continue;
                                }
                                JigsawPools.Pool child = JigsawPools.pool(new ResourceLocation(pool(j.pool)));
                                int size = child == null ? 0 : child.maxSize();
                                JigsawPools.Pool childFallback = child == null ? null : JigsawPools.pool(child.fallback());
                                int fallbackSize = childFallback == null ? 0 : childFallback.maxSize();
                                expandTo = Math.max(expandTo, Math.max(size, fallbackSize));
                            }
                        }
                    }
                    for (JigsawElement.Connector their : theirs) {
                        if (!canAttach(connector, their, nameless)) {
                            continue;
                        }
                        BlockPos theirPos = their.pos;
                        BlockPos offset = target.subtract(theirPos);
                        StructureBoundingBox childBox = candidate.box(offset, r);
                        boolean childRigid = candidate.rigid();
                        int theirY = theirPos.getY();
                        int delta = connectorY - theirY + front.getYOffset();
                        int targetMinY;
                        if (parentRigid && childRigid) {
                            targetMinY = parentMinY + delta;
                        } else {
                            if (surfaceY == Integer.MIN_VALUE) {
                                surfaceY = ground(at);
                            }
                            targetMinY = surfaceY - theirY;
                        }
                        int dy = targetMinY - childBox.minY;
                        StructureBoundingBox moved = new StructureBoundingBox(childBox);
                        moved.offset(0, dy, 0);
                        BlockPos childPos = offset.add(0, dy, 0);
                        if (expandTo > 0) {
                            int newSize = Math.max(expandTo + 1, moved.maxY - moved.minY);
                            moved.maxY = Math.max(moved.maxY, moved.minY + newSize);
                        }
                        if (free.fits(moved)) {
                            free.taken.add(new StructureBoundingBox(moved));
                            int parentGround = parent.groundLevelDelta();
                            int childGround = childRigid ? parentGround - delta : candidate.groundLevelDelta();
                            P child = this.factory.create(candidate, childPos, childGround, r, moved);
                            int sourceGroundY;
                            if (parentRigid) {
                                sourceGroundY = parentMinY + connectorY;
                            } else if (childRigid) {
                                sourceGroundY = targetMinY + theirY;
                            } else {
                                if (surfaceY == Integer.MIN_VALUE) {
                                    surfaceY = ground(at);
                                }
                                sourceGroundY = surfaceY + delta / 2;
                            }
                            parent.addJunction(target.getX(), sourceGroundY - connectorY + parentGround, target.getZ());
                            child.addJunction(at.getX(), sourceGroundY - theirY + childGround, at.getZ());
                            pieces.add(child);
                            if (depth + 1 <= this.maxDepth) {
                                placing.add(new State<P>(child, free, depth + 1), placementPriority);
                            }
                            continue connectorLoop;
                        }
                    }
                }
            }
        }
    }

    private int ground(BlockPos at) {
        return this.surface == null ? at.getY() : this.surface.applyAsInt(at.getX(), at.getZ());
    }

    /** {@code Util.shuffle} on a copy: n - 1 draws, as {@link Collections#shuffle}. */
    public static <T> List<T> shuffled(List<T> in, Random random) {
        List<T> out = new ArrayList<T>(in);
        Collections.shuffle(out, random);
        return out;
    }

    static String full(String name) {
        return name.indexOf(':') < 0 ? "minecraft:" + name : name;
    }

    // ---- pool aliases

    /**
     * A structure's {@code pool_aliases} (1.20.5+), read from its JSON: {@code direct}, {@code random} and
     * {@code random_group} bindings, in file order.
     */
    public static final class PoolAliases {
        public static final PoolAliases NONE = new PoolAliases(Collections.<Binding>emptyList());

        private final List<Binding> bindings;

        private PoolAliases(List<Binding> bindings) {
            this.bindings = bindings;
        }

        /** @param array the structure's {@code pool_aliases}; null for none */
        public static PoolAliases read(@Nullable JsonArray array) {
            if (array == null || array.size() == 0) {
                return NONE;
            }
            List<Binding> out = new ArrayList<Binding>();
            for (JsonElement e : array) {
                out.add(Binding.read(e.getAsJsonObject()));
            }
            return new PoolAliases(Collections.unmodifiableList(out));
        }

        public boolean isEmpty() {
            return this.bindings.isEmpty();
        }

        /** @return how many top-level bindings the file gives */
        public int size() {
            return this.bindings.size();
        }

        /**
         * {@code PoolAliasLookup.create}: each binding resolved from {@code RandomSource.create(seed).forkPositional()
         * .at(origin)}, in order.
         *
         * @param origin the start chunk's corner at the start height
         * @return full pool name to full pool name
         */
        public Map<String, String> resolve(long seed, BlockPos origin) {
            Map<String, String> out = new HashMap<String, String>();
            if (this.bindings.isEmpty()) {
                return out;
            }
            long positional = new Random(seed).nextLong();
            Random random = new Random(positionSeed(origin.getX(), origin.getY(), origin.getZ()) ^ positional);
            for (Binding b : this.bindings) {
                b.resolve(random, out);
            }
            return out;
        }

        /** {@code Mth.getSeed(x, y, z)}. */
        static long positionSeed(int x, int y, int z) {
            long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
            l = l * l * 42317861L + l * 11L;
            return l >> 16;
        }
    }

    /** A pool alias binding: {@code direct}, {@code random} or {@code random_group}. */
    static final class Binding {
        final String type;
        /** direct and random: the alias. */
        @Nullable
        final String alias;
        /** direct: the one target; random: the weighted targets. */
        final List<String> targets;
        /** random and random_group: the weights, in order. */
        final List<Integer> weights;
        /** random_group: each group's bindings. */
        final List<List<Binding>> groups;

        private Binding(String type, @Nullable String alias, List<String> targets, List<Integer> weights, List<List<Binding>> groups) {
            this.type = type;
            this.alias = alias;
            this.targets = targets;
            this.weights = weights;
            this.groups = groups;
        }

        static Binding read(JsonObject o) {
            String type = full(o.get("type").getAsString());
            List<String> targets = new ArrayList<String>();
            List<Integer> weights = new ArrayList<Integer>();
            List<List<Binding>> groups = new ArrayList<List<Binding>>();
            String alias = o.has("alias") ? o.get("alias").getAsString() : null;
            switch (type) {
                case "minecraft:direct":
                    targets.add(o.get("target").getAsString());
                    break;
                case "minecraft:random":
                    for (JsonElement e : o.getAsJsonArray("targets")) {
                        targets.add(e.getAsJsonObject().get("data").getAsString());
                        weights.add(e.getAsJsonObject().get("weight").getAsInt());
                    }
                    break;
                case "minecraft:random_group":
                    for (JsonElement e : o.getAsJsonArray("groups")) {
                        List<Binding> group = new ArrayList<Binding>();
                        for (JsonElement b : e.getAsJsonObject().getAsJsonArray("data")) {
                            group.add(read(b.getAsJsonObject()));
                        }
                        groups.add(group);
                        weights.add(e.getAsJsonObject().get("weight").getAsInt());
                    }
                    break;
                default:
                    throw new IllegalStateException("Unknown pool alias type " + type);
            }
            return new Binding(type, alias, targets, weights, groups);
        }

        /** {@code PoolAliasBinding.forEachResolved}. */
        void resolve(Random random, Map<String, String> out) {
            switch (this.type) {
                case "minecraft:direct":
                    put(out, this.alias, this.targets.get(0));
                    break;
                case "minecraft:random":
                    put(out, this.alias, this.targets.get(pick(this.weights, random)));
                    break;
                default:
                    for (Binding b : this.groups.get(pick(this.weights, random))) {
                        b.resolve(random, out);
                    }
            }
        }

        /** {@code ImmutableMap.Builder.put} then {@code build}: a key given twice is an error in vanilla too. */
        private static void put(Map<String, String> out, String alias, String target) {
            if (out.put(full(alias), full(target)) != null) {
                throw new IllegalStateException("Pool alias " + alias + " is bound twice");
            }
        }

        /** {@code WeightedList.getRandom}: {@code nextInt(totalWeight)}, then the entry that range falls in. */
        private static int pick(List<Integer> weights, Random random) {
            int total = 0;
            for (int w : weights) {
                total += w;
            }
            int r = random.nextInt(total);
            for (int i = 0; i < weights.size(); i++) {
                r -= weights.get(i);
                if (r < 0) {
                    return i;
                }
            }
            return weights.size() - 1;
        }
    }
}

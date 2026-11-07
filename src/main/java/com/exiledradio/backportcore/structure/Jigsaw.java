package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.terrain.NetherHeights;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.function.IntBinaryOperator;

/**
 * Jigsaw assembly as 1.16.5 does it ({@code JigsawPlacement.addPieces}): a start piece from the
 * start pool, then, breadth first to the maximum depth, each jigsaw block of each piece tries the
 * elements of its pool (and then its fallback pool) in shuffled order, each in shuffled rotations,
 * joining the first that has a matching jigsaw and fits in the free space. Pieces stay inside a
 * cube 80 blocks out from the start piece's middle, and inside their parent where they join on
 * from within it. The random is drawn from exactly as vanilla draws it, so with
 * {@link StructureRandoms#start} as the random the same seed gives vanilla's layout.
 *
 * <pre>
 * // a bastion (BastionFeature: start y 33, depth 6, no expansion hack, no projection)
 * Random random = StructureRandoms.start(world.getSeed(), chunkX, chunkZ);
 * List&lt;JigsawPiece&gt; pieces = Jigsaw.assemble(world, new ResourceLocation("minecraft", "bastion/starts"), 6,
 *         new BlockPos(chunkX * 16, 33, chunkZ * 16), random);
 * </pre>
 *
 * Pools are read by {@link JigsawPools}. Nothing is placed and no chunk is loaded here; the pieces
 * place themselves chunk by chunk ({@link JigsawPiece}).
 */
public final class Jigsaw {

    /** Makes the piece objects: {@link JigsawPiece}'s constructor, or a subclass's. */
    @FunctionalInterface
    public interface PieceFactory {
        JigsawPiece create(JigsawElement element, BlockPos position, int groundLevelDelta, Rotation rotation, StructureBoundingBox box);
    }

    private final ResourceLocation startPool;
    private int maxDepth = 7;
    private boolean expansionHack;
    private boolean projectStartToHeightmap;
    @Nullable
    private IntBinaryOperator surface;
    private PieceFactory factory = JigsawPiece::new;

    private Jigsaw(ResourceLocation startPool) {
        this.startPool = startPool;
    }

    /**
     * Today's bastion settings: no expansion hack, the start at {@code origin}'s height.
     *
     * @param maxDepth how many joins from the start piece (bastions 6, villages 6, outposts 7)
     */
    public static List<JigsawPiece> assemble(World world, ResourceLocation startPool, int maxDepth, BlockPos origin, Random random) {
        return of(startPool).maxDepth(maxDepth).assemble(world, origin, random);
    }

    public static Jigsaw of(ResourceLocation startPool) {
        return new Jigsaw(startPool);
    }

    public Jigsaw maxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
        return this;
    }

    /** Today's {@code doExpansionHack} (villages): short pieces leave room above for what joins them. */
    public Jigsaw expansionHack(boolean expansionHack) {
        this.expansionHack = expansionHack;
        return this;
    }

    /** Today's {@code projectStartToHeightmap} (villages, outposts): the start goes on the ground. */
    public Jigsaw projectStartToHeightmap(boolean project) {
        this.projectStartToHeightmap = project;
        return this;
    }

    /**
     * The ground, as 1.16's {@code getFirstFreeHeight(x, z, WORLD_SURFACE_WG)}, for
     * {@code terrain_matching} pieces and a projected start. By default {@link NetherHeights#height}
     * (which is {@code TerrainHeights} in the overworld), asked only if a piece needs it.
     */
    public Jigsaw surface(@Nullable IntBinaryOperator surface) {
        this.surface = surface;
        return this;
    }

    public Jigsaw pieces(PieceFactory factory) {
        this.factory = factory;
        return this;
    }

    // ---- free space: today's VoxelShapes, for whole-block boxes

    /** A box with boxes taken out of it; null outside means unbounded. */
    private static final class Space {
        @Nullable
        final StructureBoundingBox outside;
        final List<StructureBoundingBox> taken = new ArrayList<StructureBoundingBox>();

        Space(@Nullable StructureBoundingBox outside) {
            this.outside = outside;
        }

        /** Today's "the box, shrunk by a quarter block, is all inside the free shape". */
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

    private static final class State {
        final JigsawPiece piece;
        final Space free;
        final int depth;

        State(JigsawPiece piece, Space free, int depth) {
            this.piece = piece;
            this.free = free;
            this.depth = depth;
        }
    }

    /**
     * Lays the structure out.
     *
     * @param origin the start piece's corner before it is turned; with no projection the start piece
     *               ends up with its floor one below {@code origin}'s y, as today's
     * @param random the layout's random; {@link StructureRandoms#start} for vanilla's layouts
     * @return the pieces, start piece first; empty if the start pool is missing or empty
     */
    public List<JigsawPiece> assemble(World world, BlockPos origin, Random random) {
        List<JigsawPiece> pieces = new ArrayList<JigsawPiece>();
        JigsawPools.Pool start = JigsawPools.pool(this.startPool);
        if (start == null || start.size() == 0) {
            BackportCore.LOGGER.warn("Jigsaw start pool {} is missing or empty.", this.startPool);
            return pieces;
        }
        Surface ground = new Surface(world, this.surface);
        Rotation rotation = Rotation.values()[random.nextInt(4)];
        JigsawElement element = start.random(random);
        JigsawPiece first = this.factory.create(element, origin, element.groundLevelDelta(), rotation, element.box(origin, rotation));
        StructureBoundingBox box = first.getBoundingBox();
        int midX = (box.maxX + box.minX) / 2;
        int midZ = (box.maxZ + box.minZ) / 2;
        int y = this.projectStartToHeightmap ? origin.getY() + ground.at(midX, midZ) : origin.getY();
        int floor = box.minY + first.groundLevelDelta();
        first.offset(0, y - floor, 0);
        pieces.add(first);
        if (this.maxDepth > 0) {
            Space cube = new Space(new StructureBoundingBox(midX - 80, y - 80, midZ - 80, midX + 80, y + 80, midZ + 80));
            cube.taken.add(new StructureBoundingBox(first.getBoundingBox()));
            Deque<State> placing = new ArrayDeque<State>();
            placing.addLast(new State(first, cube, 0));
            while (!placing.isEmpty()) {
                State s = placing.removeFirst();
                tryPlacingChildren(s.piece, s.free, s.depth, pieces, placing, random, ground);
            }
        }
        // Today's StructureStart.placeInChunk gives every piece the first piece's middle, at its floor.
        StructureBoundingBox b = first.getBoundingBox();
        BlockPos pivot = new BlockPos(b.minX + (b.maxX - b.minX + 1) / 2, b.minY, b.minZ + (b.maxZ - b.minZ + 1) / 2);
        for (JigsawPiece piece : pieces) {
            piece.setPivot(pivot);
        }
        return pieces;
    }

    private void tryPlacingChildren(JigsawPiece parent, Space outerFree, int depth, List<JigsawPiece> pieces, Deque<State> placing,
                                    Random random, Surface ground) {
        JigsawElement element = parent.element();
        BlockPos position = parent.position();
        Rotation rotation = parent.rotation();
        boolean parentRigid = element.rigid();
        Space[] inner = new Space[1];
        StructureBoundingBox parentBox = parent.getBoundingBox();
        int parentMinY = parentBox.minY;

        List<JigsawElement.Connector> connectors = new ArrayList<JigsawElement.Connector>(element.connectors(position, rotation));
        Collections.shuffle(connectors, random);
        connectorLoop:
        for (JigsawElement.Connector connector : connectors) {
            EnumFacing front = connector.front;
            BlockPos at = connector.pos;
            BlockPos joint = at.offset(front);
            int connectorY = at.getY() - parentMinY;
            int surfaceY = -1;
            ResourceLocation poolId = new ResourceLocation(connector.pool);
            JigsawPools.Pool pool = JigsawPools.pool(poolId);
            if (pool == null || (pool.size() == 0 && !poolId.equals(JigsawPools.EMPTY))) {
                BackportCore.LOGGER.warn("Empty or none existent pool: {}", poolId);
                continue;
            }
            ResourceLocation fallbackId = pool.fallback();
            JigsawPools.Pool fallback = JigsawPools.pool(fallbackId);
            if (fallback == null || (fallback.size() == 0 && !fallbackId.equals(JigsawPools.EMPTY))) {
                BackportCore.LOGGER.warn("Empty or none existent fallback pool: {}", fallbackId);
                continue;
            }
            boolean inside = parentBox.isVecInside(joint);
            Space free;
            if (inside) {
                if (inner[0] == null) {
                    inner[0] = new Space(new StructureBoundingBox(parentBox));
                }
                free = inner[0];
            } else {
                free = outerFree;
            }
            List<JigsawElement> candidates = new ArrayList<JigsawElement>();
            if (depth != this.maxDepth) {
                candidates.addAll(pool.shuffled(random));
            }
            candidates.addAll(fallback.shuffled(random));

            for (JigsawElement candidate : candidates) {
                if (candidate.isEmpty()) {
                    break;
                }
                List<Rotation> rotations = new ArrayList<Rotation>(Arrays.asList(Rotation.values()));
                Collections.shuffle(rotations, random);
                for (Rotation r : rotations) {
                    List<JigsawElement.Connector> theirs = new ArrayList<JigsawElement.Connector>(candidate.connectors(BlockPos.ORIGIN, r));
                    Collections.shuffle(theirs, random);
                    StructureBoundingBox localBox = candidate.box(BlockPos.ORIGIN, r);
                    int expansion = 0;
                    if (this.expansionHack && localBox.getYSize() <= 16) {
                        for (JigsawElement.Connector c : theirs) {
                            if (!localBox.isVecInside(c.pos.offset(c.front))) {
                                continue;
                            }
                            JigsawPools.Pool p = JigsawPools.pool(new ResourceLocation(c.pool));
                            JigsawPools.Pool f = p == null ? null : JigsawPools.pool(p.fallback());
                            int a = p == null ? 0 : p.maxSize();
                            int b = f == null ? 0 : f.maxSize();
                            expansion = Math.max(expansion, Math.max(a, b));
                        }
                    }
                    for (JigsawElement.Connector their : theirs) {
                        if (!connector.canAttach(their)) {
                            continue;
                        }
                        BlockPos theirPos = their.pos;
                        BlockPos offset = new BlockPos(joint.getX() - theirPos.getX(), joint.getY() - theirPos.getY(), joint.getZ() - theirPos.getZ());
                        StructureBoundingBox childBox = candidate.box(offset, r);
                        int childMinY = childBox.minY;
                        boolean childRigid = candidate.rigid();
                        int theirY = theirPos.getY();
                        int delta = connectorY - theirY + front.getYOffset();
                        int targetMinY;
                        if (parentRigid && childRigid) {
                            targetMinY = parentMinY + delta;
                        } else {
                            if (surfaceY == -1) {
                                surfaceY = ground.at(at.getX(), at.getZ());
                            }
                            targetMinY = surfaceY - theirY;
                        }
                        int dy = targetMinY - childMinY;
                        StructureBoundingBox moved = new StructureBoundingBox(childBox);
                        moved.offset(0, dy, 0);
                        BlockPos childPos = offset.add(0, dy, 0);
                        if (expansion > 0) {
                            int span = Math.max(expansion + 1, moved.maxY - moved.minY);
                            moved.maxY = moved.minY + span;
                        }
                        if (free.fits(moved)) {
                            free.taken.add(new StructureBoundingBox(moved));
                            int parentGround = parent.groundLevelDelta();
                            int childGround = childRigid ? parentGround - delta : candidate.groundLevelDelta();
                            JigsawPiece child = this.factory.create(candidate, childPos, childGround, r, moved);
                            pieces.add(child);
                            if (depth + 1 <= this.maxDepth) {
                                placing.addLast(new State(child, free, depth + 1));
                            }
                            continue connectorLoop;
                        }
                    }
                }
            }
        }
    }

    /** The ground, asked only when a piece needs it. */
    private static final class Surface {
        final World world;
        @Nullable
        IntBinaryOperator given;
        @Nullable
        NetherHeights heights;

        Surface(World world, @Nullable IntBinaryOperator given) {
            this.world = world;
            this.given = given;
        }

        int at(int x, int z) {
            if (this.given != null) {
                return this.given.applyAsInt(x, z);
            }
            if (this.heights == null) {
                this.heights = NetherHeights.forWorld(this.world);
            }
            int y = this.heights == null ? NetherHeights.UNKNOWN : this.heights.height(x, z);
            return y == NetherHeights.UNKNOWN ? this.world.getSeaLevel() : y;
        }
    }
}

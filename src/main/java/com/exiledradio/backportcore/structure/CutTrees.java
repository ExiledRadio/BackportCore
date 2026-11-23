package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.terrain.WorldHeights;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;

/**
 * The leaves of trees a structure cut, gone with their logs. Since Core 1.4.5.
 *
 * <p>A structure built where trees stand clears their logs inside its footprint (its template's air, as today's
 * {@code SinglePoolElement} places it: only structure blocks are ignored), and the leaves outside stay. Today a leaf
 * knows how far it is from a log ({@code LeavesBlock.DISTANCE}, through leaves, up to 7); placing the structure updates
 * the shape of the blocks at its edge, the cut-off leaves count up to 7, and every non-persistent leaf at 7 decays
 * ({@code LeavesBlock.decaying}). 1.12.2's generated leaves never check: they are placed with {@code check_decay} false
 * and stay in the air for good. So whoever builds a structure calls {@link #afterBuilding} once its pieces are in, and
 * every decayable leaf near them that today's rule would decay (no log within 6 steps through leaves) is removed, with
 * the snow layer on top of it. Any mod's trees: a log is whatever sustains leaves ({@code canSustainLeaves}, as 1.12's
 * own decay asks) or is a {@code BlockLog} or {@code isWood}; a leaf is whatever {@code isLeaves}, and it is decayable
 * when its {@code decayable} property is true (or a {@code persistent} property false).
 *
 * <p>It reads only chunks already loaded and never loads or generates one; a leaf whose distance it cannot see out to
 * 6 (an unloaded chunk in the way) is left alone. No random is drawn.
 */
public final class CutTrees {

    /** Today's {@code LeavesBlock.DECAY_DISTANCE} is 7: a leaf more than 6 steps from a log decays. */
    private static final int REACH = 6;

    private static volatile boolean enabled = true;

    private static final byte OTHER = 0;
    private static final byte LOG = 1;
    private static final byte LEAF = 2;
    private static final byte DECAYABLE_LEAF = 3;

    /** Per state: is it a log, a leaf (and a decayable one), or neither. */
    private static final Map<IBlockState, Byte> KINDS = new ConcurrentHashMap<IBlockState, Byte>();

    private CutTrees() {
    }

    /** For tests: off, cut trees keep their leaves (1.12's look). */
    public static void setEnabled(boolean on) {
        enabled = on;
    }

    /**
     * Removes the cut-off leaves around the starts' pieces in a chunk's population area, after the structure built its
     * part there ({@code generateStructure(world, random, chunk)}).
     *
     * @return how many leaves it removed
     */
    public static int afterBuilding(World world, Collection<StructureStart> starts, int chunkX, int chunkZ) {
        if (!enabled || world.isRemote) {
            return 0;
        }
        StructureBoundingBox area = FreezeTopLayer.populationArea(chunkX, chunkZ);
        StructureBoundingBox built = null;
        for (StructureStart start : starts) {
            if (start == null || !start.isSizeableStructure() || start.getBoundingBox() == null
                    || !start.getBoundingBox().intersectsWith(area)) {
                continue;
            }
            for (StructureComponent piece : start.getComponents()) {
                StructureBoundingBox box = piece.getBoundingBox();
                if (box == null || !box.intersectsWith(area)) {
                    continue;
                }
                StructureBoundingBox part = new StructureBoundingBox(Math.max(box.minX, area.minX), box.minY,
                        Math.max(box.minZ, area.minZ), Math.min(box.maxX, area.maxX), box.maxY, Math.min(box.maxZ, area.maxZ));
                if (built == null) {
                    built = part;
                } else {
                    built.expandTo(part);
                }
            }
        }
        return built == null ? 0 : box(world, built);
    }

    /**
     * Removes every decayable leaf within 6 blocks of the box that has no log within 6 steps through leaves.
     *
     * @return how many leaves it removed
     */
    public static int box(World world, StructureBoundingBox built) {
        if (!enabled || world.isRemote) {
            return 0;
        }
        int bottom = WorldHeights.minY(world);
        int top = WorldHeights.maxYExclusive(world) - 1;
        // The leaves that may have lost their tree, and around them the blocks their distance is read from.
        int cMinY = Math.max(bottom, built.minY - REACH);
        int cMaxY = Math.min(top, built.maxY + REACH);
        if (cMinY > cMaxY) {
            return 0;
        }
        int pad = REACH + 1;
        int x0 = built.minX - REACH - pad;
        int y0 = Math.max(bottom, cMinY - pad);
        int z0 = built.minZ - REACH - pad;
        int x1 = built.maxX + REACH + pad;
        int y1 = Math.min(top, cMaxY + pad);
        int z1 = built.maxZ + REACH + pad;
        int sx = x1 - x0 + 1;
        int sy = y1 - y0 + 1;
        int sz = z1 - z0 + 1;

        // Distance from a log through leaves, as today's leaves count it; unknown (an unloaded chunk, or the edge of
        // the box, past which a log may stand) counts as a log, so nothing is removed on a guess.
        byte[] dist = new byte[sx * sy * sz];
        byte[] kind = new byte[sx * sy * sz];
        ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int cx = x0 >> 4; cx <= x1 >> 4; ++cx) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; ++cz) {
                Chunk chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
                int ax = Math.max(x0, cx << 4);
                int bx = Math.min(x1, (cx << 4) + 15);
                int az = Math.max(z0, cz << 4);
                int bz = Math.min(z1, (cz << 4) + 15);
                for (int x = ax; x <= bx; ++x) {
                    for (int z = az; z <= bz; ++z) {
                        for (int y = y0; y <= y1; ++y) {
                            int i = (x - x0) + sx * ((z - z0) + sz * (y - y0));
                            if (chunk == null) {
                                kind[i] = LOG;
                                queue.add(i);
                                continue;
                            }
                            at.setPos(x, y, z);
                            IBlockState state = chunk.getBlockState(at);
                            byte k = kind(world, at, state);
                            kind[i] = k;
                            boolean edge = x == x0 || x == x1 || z == z0 || z == z1 || y == y0 || y == y1;
                            if (k == LOG || (edge && k != OTHER)) {
                                queue.add(i);
                            } else if (k != OTHER) {
                                dist[i] = REACH + 1;
                            }
                        }
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int d = dist[i];
            if (d >= REACH) {
                continue;
            }
            int x = i % sx;
            int z = (i / sx) % sz;
            int y = i / (sx * sz);
            visit(queue, kind, dist, x > 0 ? i - 1 : -1, d);
            visit(queue, kind, dist, x < sx - 1 ? i + 1 : -1, d);
            visit(queue, kind, dist, z > 0 ? i - sx : -1, d);
            visit(queue, kind, dist, z < sz - 1 ? i + sx : -1, d);
            visit(queue, kind, dist, y > 0 ? i - sx * sz : -1, d);
            visit(queue, kind, dist, y < sy - 1 ? i + sx * sz : -1, d);
        }

        List<BlockPos> cut = new ArrayList<BlockPos>();
        for (int y = cMinY; y <= cMaxY; ++y) {
            for (int z = built.minZ - REACH; z <= built.maxZ + REACH; ++z) {
                for (int x = built.minX - REACH; x <= built.maxX + REACH; ++x) {
                    int i = (x - x0) + sx * ((z - z0) + sz * (y - y0));
                    if (kind[i] == DECAYABLE_LEAF && dist[i] > REACH) {
                        cut.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        for (BlockPos pos : cut) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
            BlockPos above = pos.up();
            if (world.isBlockLoaded(above, false) && world.getBlockState(above).getBlock() == Blocks.SNOW_LAYER) {
                world.setBlockState(above, Blocks.AIR.getDefaultState(), 2);
            }
        }
        return cut.size();
    }

    private static void visit(ArrayDeque<Integer> queue, byte[] kind, byte[] dist, int j, int d) {
        if (j < 0 || (kind[j] != LEAF && kind[j] != DECAYABLE_LEAF) || dist[j] <= d + 1) {
            return;
        }
        dist[j] = (byte) (d + 1);
        queue.add(j);
    }

    private static byte kind(World world, BlockPos pos, IBlockState state) {
        Byte known = KINDS.get(state);
        if (known != null) {
            return known;
        }
        Block block = state.getBlock();
        byte k = OTHER;
        if (block == Blocks.AIR) {
            k = OTHER;
        } else if (block instanceof BlockLog || block.canSustainLeaves(state, world, pos) || block.isWood(world, pos)) {
            k = LOG;
        } else if (block.isLeaves(state, world, pos)) {
            Boolean decayable = bool(state, "decayable");
            Boolean persistent = bool(state, "persistent");
            k = Boolean.TRUE.equals(decayable) || (decayable == null && Boolean.FALSE.equals(persistent)) ? DECAYABLE_LEAF : LEAF;
        }
        KINDS.put(state, k);
        return k;
    }

    @Nullable
    private static Boolean bool(IBlockState state, String name) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (name.equals(property.getName()) && property.getValueClass() == Boolean.class) {
                return (Boolean) state.getValue(property);
            }
        }
        return null;
    }
}

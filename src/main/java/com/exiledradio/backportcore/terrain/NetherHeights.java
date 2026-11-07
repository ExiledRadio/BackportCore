package com.exiledradio.backportcore.terrain;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.ChunkGeneratorHell;
import net.minecraft.world.gen.ChunkGeneratorOverworld;
import net.minecraft.world.gen.IChunkGenerator;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Whole terrain columns, for structures laid out before their chunks exist: the nether's several
 * floors, and the overworld's sea bed. {@link TerrainHeights} answers one surface per column; a
 * nether column has a lava sea, cave floors and a ceiling, so this keeps every block's kind.
 *
 * <h2>Two kinds of column</h2>
 * <ul>
 * <li>{@link #forWorld}: the terrain as the chunk will be generated, before decoration. In the nether
 * that is a copy of the world's own {@code ChunkGeneratorHell} building the chunk (noise, soul sand and
 * gravel, caves), so it matches the generated chunk block for block until population adds glowstone,
 * fire, lava falls and fortresses.</li>
 * <li>{@link #baseForWorld}: 1.16's {@code getBaseColumn}, the bare noise (netherrack, the lava sea and
 * air; in the overworld stone and water), which is what 1.16's ruined portals measure their corners
 * against. Use it where vanilla 1.16 does, for parity.</li>
 * </ul>
 *
 * <p>Both need a generator this can copy: vanilla's nether generator (or a subclass with its
 * constructor), or vanilla's overworld generator. Anywhere else, columns are read from chunks that are
 * already loaded, and a column of a chunk that is not answers {@link #UNKNOWN} ({@link #predicts} is
 * false): such a structure is best laid out at a fixed height, as 1.16's bastions are (y 33).
 *
 * <p>Call {@code forWorld} once per structure start on the server thread, as with
 * {@link TerrainHeights}: it drops the previous layout's columns (and, in the overworld, starts a fresh
 * {@code TerrainHeights} layout too).
 */
public final class NetherHeights {

    /** A column with no floor in the range. */
    public static final int NONE = -1;
    /** A column this cannot know: no prediction, and its chunk is not loaded. */
    public static final int UNKNOWN = Integer.MIN_VALUE;

    private static final byte AIR = 0;
    private static final byte LIQUID = 1;
    private static final byte SOLID = 2;
    /** Neither: fire, mushrooms, plants. Not solid, not liquid, not air. */
    private static final byte OTHER = 3;

    private static final Map<World, NetherHeights> GENERATED = new WeakHashMap<World, NetherHeights>();
    private static final Map<World, NetherHeights> BASE = new WeakHashMap<World, NetherHeights>();

    private final World world;
    private final boolean base;
    @Nullable
    private final IChunkGenerator copy;
    private final int height;
    private final Map<Long, byte[]> chunks = new HashMap<Long, byte[]>();
    /** The overworld's surface planner for this layout, where it has one. */
    @Nullable
    private TerrainHeights terrain;

    private NetherHeights(World world, boolean base, @Nullable IChunkGenerator copy) {
        this.world = world;
        this.base = base;
        this.copy = copy;
        this.height = world.getHeight();
    }

    /** @return the columns as generated (caves included), or null on a client world */
    @Nullable
    public static synchronized NetherHeights forWorld(World world) {
        return of(world, false);
    }

    /** @return 1.16's base columns (bare noise), or null on a client world */
    @Nullable
    public static synchronized NetherHeights baseForWorld(World world) {
        return of(world, true);
    }

    @Nullable
    private static NetherHeights of(World world, boolean base) {
        if (!(world instanceof WorldServer)) {
            return null;
        }
        Map<World, NetherHeights> known = base ? BASE : GENERATED;
        NetherHeights heights = known.get(world);
        if (heights == null) {
            heights = new NetherHeights(world, base, copy(world));
            known.put(world, heights);
        }
        heights.chunks.clear();
        heights.terrain = world.provider.getDimension() == 0 ? TerrainHeights.forWorld(world) : null;
        return heights;
    }

    @Nullable
    private static IChunkGenerator copy(World world) {
        IChunkGenerator live = ((WorldServer) world).getChunkProvider().chunkGenerator;
        if (live == null) {
            return null;
        }
        try {
            if (live instanceof ChunkGeneratorHell) {
                Constructor<? extends IChunkGenerator> ctor = live.getClass().getConstructor(World.class, boolean.class, long.class);
                return ctor.newInstance(world, false, world.getSeed());
            }
            if (live.getClass() == ChunkGeneratorOverworld.class) {
                return new ChunkGeneratorOverworld(world, world.getSeed(), false, world.getWorldInfo().getGeneratorOptions());
            }
        } catch (Exception e) {
            BackportCore.LOGGER.warn("Could not copy the world generator {} to read terrain columns ahead of generation; "
                    + "columns are read from loaded chunks only.", live.getClass().getName(), e);
            return null;
        }
        BackportCore.LOGGER.debug("World generator {} cannot be copied; terrain columns are read from loaded chunks only.",
                live.getClass().getName());
        return null;
    }

    /** @return true if columns of chunks that do not exist yet are known */
    public boolean predicts() {
        return this.copy != null;
    }

    /** @return true if this column can be read now */
    public boolean known(int x, int z) {
        return column(x >> 4, z >> 4) != null;
    }

    /**
     * The lowest floor in a range: the lowest {@code y} from {@code minY} to {@code maxY} whose block
     * is open (not solid, not liquid) and stands on a solid one. Lava is no floor and no air.
     *
     * @return that y (where something standing there has its feet), {@link #NONE}, or {@link #UNKNOWN}
     */
    public int floor(int x, int z, int minY, int maxY) {
        byte[] c = column(x >> 4, z >> 4);
        if (c == null) {
            return UNKNOWN;
        }
        int base = index(x, z);
        for (int y = Math.max(1, minY); y <= maxY && y < this.height; y++) {
            if (open(c[base + y]) && c[base + y - 1] == SOLID) {
                return y;
            }
        }
        return NONE;
    }

    /** @return every floor from {@code minY} to {@code maxY}, lowest first (see {@link #floor(int, int, int, int)}), or null if unknown */
    @Nullable
    public int[] floors(int x, int z, int minY, int maxY) {
        byte[] c = column(x >> 4, z >> 4);
        if (c == null) {
            return null;
        }
        int base = index(x, z);
        int[] out = new int[16];
        int n = 0;
        for (int y = Math.max(1, minY); y <= maxY && y < this.height; y++) {
            if (open(c[base + y]) && c[base + y - 1] == SOLID) {
                if (n == out.length) {
                    out = java.util.Arrays.copyOf(out, n * 2);
                }
                out[n++] = y;
            }
        }
        return java.util.Arrays.copyOf(out, n);
    }

    /**
     * The ocean-floor variant, 1.16's {@code OCEAN_FLOOR_WG}: one above the highest solid block, water
     * and lava ignored. In the overworld, where {@link TerrainHeights} can plan the world it answers
     * (so every terrain it reads works); else from these columns.
     *
     * @return that y, or {@link #UNKNOWN}
     */
    public int floor(int x, int z) {
        if (this.terrain != null) {
            return this.terrain.floor(x, z);
        }
        byte[] c = column(x >> 4, z >> 4);
        if (c == null) {
            return UNKNOWN;
        }
        int base = index(x, z);
        for (int y = this.height - 1; y >= 0; y--) {
            if (c[base + y] == SOLID) {
                return y + 1;
            }
        }
        return 0;
    }

    /**
     * 1.16's {@code WORLD_SURFACE_WG}: one above the highest block that is not air (liquids
     * included). In the nether that is the bedrock ceiling. In the overworld, {@link TerrainHeights}
     * where it can plan the world.
     */
    public int height(int x, int z) {
        if (this.terrain != null) {
            return this.terrain.height(x, z);
        }
        byte[] c = column(x >> 4, z >> 4);
        if (c == null) {
            return UNKNOWN;
        }
        int base = index(x, z);
        for (int y = this.height - 1; y >= 0; y--) {
            if (c[base + y] != AIR) {
                return y + 1;
            }
        }
        return 0;
    }

    /** @return true if the block blocks movement (false where unknown) */
    public boolean isSolid(int x, int y, int z) {
        return kind(x, y, z) == SOLID;
    }

    /** @return true for water or lava (false where unknown) */
    public boolean isLiquid(int x, int y, int z) {
        return kind(x, y, z) == LIQUID;
    }

    /** @return true for air (false where unknown); fire and plants are not air */
    public boolean isAir(int x, int y, int z) {
        return kind(x, y, z) == AIR;
    }

    private byte kind(int x, int y, int z) {
        if (y < 0 || y >= this.height) {
            return AIR;
        }
        byte[] c = column(x >> 4, z >> 4);
        return c == null ? -1 : c[index(x, z) + y];
    }

    private int index(int x, int z) {
        return ((z & 15) << 4 | (x & 15)) * this.height;
    }

    private static boolean open(byte kind) {
        return kind == AIR || kind == OTHER;
    }

    private static byte kindOf(IBlockState state) {
        Material m = state.getMaterial();
        if (m == Material.AIR) {
            return AIR;
        }
        if (m.isLiquid()) {
            return LIQUID;
        }
        return m.blocksMovement() ? SOLID : OTHER;
    }

    @Nullable
    private byte[] column(int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
        byte[] known = this.chunks.get(key);
        if (known != null) {
            return known;
        }
        byte[] out = null;
        Chunk loaded = this.copy == null ? this.world.getChunkProvider().getLoadedChunk(chunkX, chunkZ) : null;
        if (this.copy != null) {
            out = sample(chunkX, chunkZ);
        } else if (loaded != null) {
            out = new byte[256 * this.height];
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int base = (z << 4 | x) * this.height;
                    for (int y = 0; y < this.height; y++) {
                        out[base + y] = kindOf(loaded.getBlockState(x, y, z));
                    }
                }
            }
        }
        if (out != null) {
            this.chunks.put(key, out);
        }
        return out;
    }

    private byte[] sample(int chunkX, int chunkZ) {
        byte[] out = new byte[256 * this.height];
        if (this.base) {
            ChunkPrimer primer = new ChunkPrimer();
            if (this.copy instanceof ChunkGeneratorHell) {
                ((ChunkGeneratorHell) this.copy).prepareHeights(chunkX, chunkZ, primer);
            } else {
                ((ChunkGeneratorOverworld) this.copy).setBlocksInChunk(chunkX, chunkZ, primer);
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int base = (z << 4 | x) * this.height;
                    for (int y = 0; y < this.height && y < 256; y++) {
                        out[base + y] = kindOf(primer.getBlockState(x, y, z));
                    }
                }
            }
            return out;
        }
        // The copy builds the chunk exactly as the world's generator will (it never touches the world:
        // structures are off and the chunk is never added), and is then dropped.
        Chunk chunk = this.copy.generateChunk(chunkX, chunkZ);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int base = (z << 4 | x) * this.height;
                for (int y = 0; y < this.height; y++) {
                    out[base + y] = kindOf(chunk.getBlockState(x, y, z));
                }
            }
        }
        return out;
    }
}

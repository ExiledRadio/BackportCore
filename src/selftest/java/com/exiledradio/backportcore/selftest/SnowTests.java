package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.structure.FreezeTopLayer;
import com.exiledradio.backportcore.structure.ScatteredPlacement;
import com.exiledradio.backportcore.structure.ScatteredStructure;
import com.exiledradio.backportcore.terrain.TerrainHeights;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.MapGenStructureIO;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraft.world.gen.structure.template.TemplateManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Snow over structures (today's freeze_top_layer after the structures): a test structure built by Core's driver in a
 * cold biome, once with the pass off (1.12's look) and once on. The structure is an outpost's pad: a 9 x 9 grass pad
 * with air over it, a 3 x 3 stone pillar four high in the middle and a pumpkin in one corner.
 */
final class SnowTests {

    static final String KIND = "backportcore_selftest_snow";
    /** Chunks the test structure starts in; set before the chunks are generated. */
    static final Set<Long> TARGETS = new HashSet<Long>();

    private static final int PAD = 9;
    private static final int PILLAR = 4;
    private static ChunkPos off;
    private static ChunkPos on;

    private SnowTests() {
    }

    static void init() {
        MapGenStructureIO.registerStructure(Start.class, "BCST_Snow");
        MapGenStructureIO.registerStructureComponent(Pad.class, "BCST_SnowPad");
        ScatteredStructure.register(KIND, world -> world.provider.getDimension() == 0 ? new SnowStructure() : null);
    }

    static void schedule() {
        then(20, "snow: find two cold sites", SnowTests::find);
        then(5, "snow: build without the pass", world -> build(world, off, false));
        then(5, "snow: build with the pass", world -> build(world, on, true));
        then(5, "snow: count", SnowTests::count);
    }

    private static void find(WorldServer world) {
        List<ChunkPos> found = new ArrayList<ChunkPos>();
        BlockPos spawn = world.getSpawnPoint();
        int sx = spawn.getX() >> 4;
        int sz = spawn.getZ() >> 4;
        // Rings outward from 40 chunks, every fourth chunk: a site whose 7 x 7 chunks are all cold and not generated.
        for (int r = 40; r < 400 && found.size() < 2; r += 4) {
            for (int dx = -r; dx <= r && found.size() < 2; dx += 4) {
                for (int dz = -r; dz <= r && found.size() < 2; dz += 4) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    ChunkPos c = new ChunkPos(sx + dx, sz + dz);
                    if (cold(world, c) && (found.isEmpty() || Math.abs(found.get(0).x - c.x) + Math.abs(found.get(0).z - c.z) > 16)) {
                        found.add(c);
                    }
                }
            }
        }
        if (!check("snow: two cold sites found", found.size() == 2, found)) {
            return;
        }
        off = found.get(0);
        on = found.get(1);
    }

    private static boolean cold(WorldServer world, ChunkPos c) {
        TerrainHeights heights = TerrainHeights.forWorld(world);
        for (int dx = -3; dx <= 3; ++dx) {
            for (int dz = -3; dz <= 3; ++dz) {
                if (world.getChunkProvider().isChunkGeneratedAt(c.x + dx, c.z + dz)) {
                    return false;
                }
            }
        }
        for (int dx = -24; dx <= 40; dx += 4) {
            for (int dz = -24; dz <= 40; dz += 4) {
                int x = (c.x << 4) + dx;
                int z = (c.z << 4) + dz;
                Biome biome = world.getBiomeProvider().getBiome(new BlockPos(x, 0, z));
                if (biome.getDefaultTemperature() >= 0.15F || !biome.isSnowyBiome()) {
                    return false;
                }
            }
        }
        int x = (c.x << 4) + 8;
        int z = (c.z << 4) + 8;
        return heights == null || !heights.isWater(x, z);
    }

    private static void build(WorldServer world, ChunkPos c, boolean pass) {
        if (c == null) {
            return;
        }
        TARGETS.add(ChunkPos.asLong(c.x, c.z));
        FreezeTopLayer.setEnabled(pass);
        try {
            for (int dx = -2; dx <= 3; ++dx) {
                for (int dz = -2; dz <= 3; ++dz) {
                    world.getChunk(c.x + dx, c.z + dz);
                }
            }
        } finally {
            FreezeTopLayer.setEnabled(true);
        }
    }

    private static void count(WorldServer world) {
        if (off == null || on == null) {
            return;
        }
        int[] a = survey(world, off, "without");
        int[] b = survey(world, on, "with");
        BackportCore.LOGGER.info("SNOW before/after: without the pass {} of {} piece columns snowed ({} could be, {} cut later), with it {} of {} ({} could be, {} cut later)",
                a[0], a[2], a[1], a[5], b[0], b[2], b[1], b[5]);
        check("snow: piece built at both sites", a[2] == PAD * PAD && b[2] == PAD * PAD, a[2] + " / " + b[2]);
        check("snow: without the pass the piece is bare (1.12's look)", a[1] > (PAD * PAD - a[5]) / 2 && a[0] < a[1],
                a[0] + " snowed, " + a[1] + " open, " + a[5] + " cut later");
        // Columns a later chunk's feature cut after the snow (see survey) are left out; they stay a minority.
        check("snow: with the pass every column that can snow did", b[1] == 0 && b[0] > PAD * PAD / 2,
                b[0] + " snowed, " + b[1] + " open, " + b[5] + " cut later");
        check("snow: interiors untouched", b[3] == 0 && a[3] == 0, a[3] + " / " + b[3]);
        check("snow: snow on the pumpkin and the pillar", b[4] == b[6], b[4] + " of " + b[6]);
    }

    /**
     * Only columns whose top is still the pad's own (its grass, the pillar's top stone or the pumpkin, at the height
     * the piece built them) are counted: 1.12.2 populates the pad's four population areas one after another, and a
     * lake or tree of a later one reaches past its own area into one already snowed, cutting it after the snow as it
     * cuts the terrain around (Village &amp; Pillage's outpost survey counts the same as {@code l}). Those columns are
     * {@code l} on the map and counted apart.
     *
     * @return {snowed columns, columns where snow could still be placed, piece columns found, interior blocks changed,
     * tops snowed of the pumpkin and the pillar, columns cut later, pumpkin and pillar tops still the pad's}
     */
    private static int[] survey(WorldServer world, ChunkPos c, String name) {
        Pad pad = null;
        StructureStart start = null;
        for (StructureStart s : LAID_OUT) {
            if (s.getChunkPosX() == c.x && s.getChunkPosZ() == c.z) {
                start = s;
            }
        }
        if (start != null && !start.getComponents().isEmpty()) {
            pad = (Pad) start.getComponents().get(0);
        }
        if (pad == null) {
            check("snow: " + name + " site laid out", false, c);
            return new int[] {0, 0, 0, 0, 0, 0, 0};
        }
        StructureBoundingBox box = pad.getBoundingBox();
        int snowed = 0;
        int open = 0;
        int columns = 0;
        int interior = 0;
        int tops = 0;
        int cut = 0;
        int ownTops = 0;
        StringBuilder map = new StringBuilder();
        for (int z = box.minZ; z <= box.maxZ; ++z) {
            map.append('\n');
            for (int x = box.minX; x <= box.maxX; ++x) {
                BlockPos top = world.getPrecipitationHeight(new BlockPos(x, 0, z));
                columns++;
                boolean pumpkin = x == box.minX && z == box.minZ;
                boolean pillarTop = x == box.minX + PAD / 2 && z == box.minZ + PAD / 2;
                Block own = pad.pillar(x, z) ? Blocks.STONE : pumpkin ? Blocks.PUMPKIN : Blocks.GRASS;
                int ownY = box.minY + (pad.pillar(x, z) ? PILLAR - 1 : pumpkin ? 1 : 0);
                if (world.getBlockState(top.down()).getBlock() != own || top.getY() - 1 != ownY) {
                    cut++;
                    map.append('l');
                    BackportCore.LOGGER.info("SNOW column {} {} cut after the snow: {} under {}", x, z, world.getBlockState(top.down()),
                            world.getBlockState(top));
                    continue;
                }
                if (pumpkin || pillarTop) {
                    ownTops++;
                }
                boolean snow = world.getBlockState(top).getBlock() == Blocks.SNOW_LAYER;
                if (snow) {
                    snowed++;
                } else if (world.canSnowAt(top, true)) {
                    open++;
                }
                Block under = world.getBlockState(top.down()).getBlock();
                map.append(snow ? (under == Blocks.GRASS ? '*' : under == Blocks.PUMPKIN ? 'P' : '#') : under == Blocks.GRASS ? 'g'
                        : under == Blocks.STONE ? 's' : under == Blocks.PUMPKIN ? 'p' : '?');
                // Inside: the pad's grass, and the pillar's stone under its top, never snow or ice (a tree the decoration
                // of a later chunk grows on the pad turns grass to dirt; that is not the pass).
                BlockPos floor = new BlockPos(x, box.minY, z);
                interior += frozen(world, floor);
                if (pad.pillar(x, z)) {
                    for (int y = 1; y < PILLAR; ++y) {
                        interior += frozen(world, floor.up(y));
                    }
                    if (x == box.minX + PAD / 2 && z == box.minZ + PAD / 2 && snow) {
                        tops++;
                    }
                }
                if (x == box.minX && z == box.minZ && snow && under == Blocks.PUMPKIN) {
                    tops++;
                }
            }
        }
        BackportCore.LOGGER.info("SNOW top view {} the pass at {} (* snow on grass, # on stone, P on the pumpkin; lower case bare; l cut by a later chunk):{}",
                name, box, map);
        return new int[] {snowed, open, columns, interior, tops, cut, ownTops};
    }

    private static int frozen(World world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        if (block == Blocks.SNOW_LAYER || block == Blocks.ICE) {
            BackportCore.LOGGER.info("SNOW interior block at {} is {}", pos, block.getRegistryName());
            return 1;
        }
        return 0;
    }

    static final List<StructureStart> LAID_OUT = new ArrayList<StructureStart>();

    public static final class SnowStructure extends ScatteredStructure {
        public SnowStructure() {
            super("BCST_Snow", new ScatteredPlacement(2, 1, 7654321));
        }

        @Override
        protected boolean canStartAt(World world, int chunkX, int chunkZ) {
            return TARGETS.contains(ChunkPos.asLong(chunkX, chunkZ));
        }

        @Override
        protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
            // Any chunk the test names, whatever the grid.
            return canStartAt(this.world, chunkX, chunkZ);
        }

        @Override
        protected StructureStart getStructureStart(int chunkX, int chunkZ) {
            Start start = new Start(this.world, chunkX, chunkZ);
            LAID_OUT.add(start);
            return start;
        }
    }

    public static final class Start extends StructureStart {
        public Start() {
        }

        Start(World world, int chunkX, int chunkZ) {
            super(chunkX, chunkZ);
            int x = (chunkX << 4) + 4;
            int z = (chunkZ << 4) + 4;
            TerrainHeights heights = TerrainHeights.forWorld(world);
            int y = heights == null ? 64 : heights.height(x + PAD / 2, z + PAD / 2) - 1;
            this.components.add(new Pad(new BlockPos(x, y, z)));
            updateBoundingBox();
        }
    }

    /** The pad, its pillar and its pumpkin, at the box's floor. */
    public static final class Pad extends StructureComponent {
        public Pad() {
        }

        Pad(BlockPos corner) {
            super(0);
            setCoordBaseMode(EnumFacing.NORTH);
            this.boundingBox = new StructureBoundingBox(corner.getX(), corner.getY(), corner.getZ(), corner.getX() + PAD - 1,
                    corner.getY() + PILLAR + 1, corner.getZ() + PAD - 1);
        }

        boolean pillar(int x, int z) {
            int i = x - this.boundingBox.minX - PAD / 2;
            int k = z - this.boundingBox.minZ - PAD / 2;
            return Math.abs(i) <= 1 && Math.abs(k) <= 1;
        }

        @Override
        protected void writeStructureToNBT(NBTTagCompound tag) {
        }

        @Override
        protected void readStructureFromNBT(NBTTagCompound tag, TemplateManager templates) {
        }

        @Override
        public boolean addComponentParts(World world, Random random, StructureBoundingBox chunk) {
            StructureBoundingBox b = this.boundingBox;
            IBlockState grass = Blocks.GRASS.getDefaultState();
            IBlockState air = Blocks.AIR.getDefaultState();
            for (int x = b.minX; x <= b.maxX; ++x) {
                for (int z = b.minZ; z <= b.maxZ; ++z) {
                    for (int y = b.minY; y <= b.maxY; ++y) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!chunk.isVecInside(pos)) {
                            continue;
                        }
                        IBlockState state = y == b.minY ? grass : pillar(x, z) && y - b.minY < PILLAR ? Blocks.STONE.getDefaultState() : air;
                        if (y == b.minY + 1 && x == b.minX && z == b.minZ) {
                            state = Blocks.PUMPKIN.getDefaultState();
                        }
                        world.setBlockState(pos, state, 2);
                    }
                }
            }
            return true;
        }
    }
}

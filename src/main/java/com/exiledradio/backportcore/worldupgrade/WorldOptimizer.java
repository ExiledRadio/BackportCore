package com.exiledradio.backportcore.worldupgrade;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.RegionFile;
import net.minecraftforge.common.util.CompoundDataFixer;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 1.13's {@code WorldOptimizer} (Edit World's Optimize World) for a 1.12.2 world: every chunk of every dimension's
 * region files is read, brought up to date by the game's data fixer (Forge's, with each mod's own fixes) and written
 * back when it was older than the game (its {@code DataVersion} below 1343, or a mod's {@code ForgeDataVersion} below
 * that mod's); others count as skipped. With Erase Cached Data (1.14) every chunk is written, its cached light dropped
 * ({@code LightPopulated} false, so the game lights it again) and its height map worked out again from its blocks (1.14
 * drops {@code Heightmaps} and {@code isLightOn}; 1.12.2 needs a height map in every chunk). Runs on its own thread, a
 * chunk of each dimension in turn, while the world is closed. Since Core 1.4.4.
 */
public final class WorldOptimizer {

    private static final Pattern REGION = Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");
    private static final Pattern DIM = Pattern.compile("^DIM(-?[0-9]+)$");
    /** 1.12.2's {@code DataVersion}. */
    public static final int GAME_VERSION = 1343;

    /** One dimension's folder and chunks. */
    public static final class Dimension {
        public final int id;
        public final File folder;
        final List<ChunkPos> chunks = new ArrayList<ChunkPos>();
        volatile float progress;

        Dimension(int id, File folder) {
            this.id = id;
            this.folder = folder;
        }

        public float progress() {
            return this.progress;
        }
    }

    public enum Stage {
        COUNTING("optimizeWorld.stage.counting"), UPGRADING("optimizeWorld.stage.upgrading"), FINISHED("optimizeWorld.stage.finished"),
        FAILED("optimizeWorld.stage.failed");

        public final String langKey;

        Stage(String langKey) {
            this.langKey = langKey;
        }
    }

    private final File world;
    private final CompoundDataFixer fixer;
    private final boolean eraseCache;
    private final List<Dimension> dimensions = new java.util.concurrent.CopyOnWriteArrayList<Dimension>();
    private final Thread thread;
    private volatile boolean running = true;
    private volatile boolean finished;
    private volatile Stage stage = Stage.COUNTING;
    private volatile int total;
    private volatile int converted;
    private volatile int skipped;
    private volatile float progress;
    /** The world's block ids (level.dat's FML registry), for the height map. */
    @Nullable
    private Map<Integer, Block> blockIds;

    public WorldOptimizer(File world, CompoundDataFixer fixer, boolean eraseCache) {
        this.world = world;
        this.fixer = fixer;
        this.eraseCache = eraseCache;
        this.thread = new Thread(this::work, "Chunk Upgrader");
        this.thread.setDaemon(true);
        this.thread.setUncaughtExceptionHandler((t, e) -> {
            BackportCore.LOGGER.error("Error upgrading world", e);
            this.stage = Stage.FAILED;
            this.finished = true;
        });
    }

    /** Starts the work (1.13 starts it in the constructor; here the caller does, once the screen is up). */
    public WorldOptimizer start() {
        this.thread.start();
        return this;
    }

    public void cancel() {
        this.running = false;
        try {
            this.thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Waits up to {@code millis} for the work to end. @return whether it has */
    public boolean awaitFinished(long millis) {
        try {
            this.thread.join(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return this.finished;
    }

    public boolean isFinished() {
        return this.finished;
    }

    public Stage stage() {
        return this.stage;
    }

    public int total() {
        return this.total;
    }

    public int converted() {
        return this.converted;
    }

    public int skipped() {
        return this.skipped;
    }

    public float progress() {
        return this.progress;
    }

    /** The dimensions in 1.13's order: the overworld, the Nether, the End, then the others by id. */
    public List<Dimension> dimensions() {
        return this.dimensions;
    }

    /** @return each dimension of {@code world}: its id and the folder that holds its {@code region} folder */
    public static List<Dimension> findDimensions(File world) {
        List<Dimension> out = new ArrayList<Dimension>();
        out.add(new Dimension(0, world));
        List<Dimension> others = new ArrayList<Dimension>();
        File[] files = world.listFiles();
        if (files != null) {
            for (File f : files) {
                Matcher m = DIM.matcher(f.getName());
                if (f.isDirectory() && m.matches() && new File(f, "region").isDirectory()) {
                    others.add(new Dimension(Integer.parseInt(m.group(1)), f));
                }
            }
        }
        others.sort((a, b) -> Integer.compare(order(a.id), order(b.id)) != 0 ? Integer.compare(order(a.id), order(b.id)) : Integer.compare(a.id, b.id));
        out.addAll(others);
        return out;
    }

    private static int order(int id) {
        return id == -1 ? 0 : id == 1 ? 1 : 2;
    }

    /** 1.13's {@code getAllChunkPos}: every chunk a region file of {@code folder/region} holds. */
    public static List<ChunkPos> chunks(File folder) {
        List<ChunkPos> out = new ArrayList<ChunkPos>();
        File[] regions = new File(folder, "region").listFiles((dir, name) -> name.endsWith(".mca"));
        if (regions == null) {
            return out;
        }
        Arrays.sort(regions);
        for (File file : regions) {
            Matcher m = REGION.matcher(file.getName());
            if (!m.matches()) {
                continue;
            }
            int rx = Integer.parseInt(m.group(1)) << 5;
            int rz = Integer.parseInt(m.group(2)) << 5;
            RegionFile region = new RegionFile(file);
            try {
                for (int x = 0; x < 32; x++) {
                    for (int z = 0; z < 32; z++) {
                        if (region.isChunkSaved(x, z)) {
                            out.add(new ChunkPos(rx + x, rz + z));
                        }
                    }
                }
            } finally {
                close(region);
            }
        }
        return out;
    }

    private void work() {
        this.total = 0;
        for (Dimension d : findDimensions(this.world)) {
            d.chunks.addAll(chunks(d.folder));
            this.total += d.chunks.size();
            this.dimensions.add(d);
        }
        if (this.total == 0) {
            this.finished = true;
            return;
        }
        if (this.eraseCache) {
            this.blockIds = worldBlockIds(this.world);
        }
        long start = System.currentTimeMillis();
        float total = this.total;
        Map<Dimension, ListIterator<ChunkPos>> iterators = new LinkedHashMap<Dimension, ListIterator<ChunkPos>>();
        Map<Dimension, Map<String, RegionFile>> open = new HashMap<Dimension, Map<String, RegionFile>>();
        for (Dimension d : this.dimensions) {
            iterators.put(d, d.chunks.listIterator());
            open.put(d, new HashMap<String, RegionFile>());
        }
        this.stage = Stage.UPGRADING;
        try {
            while (this.running) {
                boolean any = false;
                float sum = 0.0F;
                for (Dimension d : this.dimensions) {
                    ListIterator<ChunkPos> it = iterators.get(d);
                    if (it.hasNext()) {
                        ChunkPos pos = it.next();
                        boolean wrote = false;
                        try {
                            wrote = upgrade(region(open.get(d), d.folder, pos), pos);
                        } catch (IOException | RuntimeException e) {
                            BackportCore.LOGGER.error("Error upgrading chunk {}", pos, e);
                        }
                        if (wrote) {
                            this.converted++;
                        } else {
                            this.skipped++;
                        }
                        any = true;
                    }
                    float p = it.nextIndex() / total;
                    d.progress = p;
                    sum += p;
                }
                this.progress = sum;
                if (!any) {
                    this.running = false;
                }
            }
            this.stage = Stage.FINISHED;
        } finally {
            for (Map<String, RegionFile> regions : open.values()) {
                for (RegionFile r : regions.values()) {
                    close(r);
                }
            }
            // The game's own cache may hold the files written here.
            net.minecraft.world.chunk.storage.RegionFileCache.clearRegionFileReferences();
        }
        BackportCore.LOGGER.info("World optimizaton finished after {} ms", System.currentTimeMillis() - start);
        this.finished = true;
    }

    private static RegionFile region(Map<String, RegionFile> open, File folder, ChunkPos pos) {
        String name = "r." + (pos.x >> 5) + "." + (pos.z >> 5) + ".mca";
        RegionFile r = open.get(name);
        if (r == null) {
            r = new RegionFile(new File(new File(folder, "region"), name));
            open.put(name, r);
        }
        return r;
    }

    /** One chunk. @return whether it was written */
    boolean upgrade(RegionFile region, ChunkPos pos) throws IOException {
        NBTTagCompound tag;
        try (DataInputStream in = region.getChunkDataInputStream(pos.x & 31, pos.z & 31)) {
            if (in == null) {
                return false;
            }
            tag = CompressedStreamTools.read(in);
        }
        boolean outdated = outdated(tag, this.fixer);
        NBTTagCompound fixed = this.fixer.process(FixTypes.CHUNK, tag);
        NBTTagCompound level = fixed.getCompoundTag("Level");
        if (level.getInteger("xPos") != pos.x || level.getInteger("zPos") != pos.z) {
            BackportCore.LOGGER.warn("Chunk {} has invalid position [{}, {}]", pos, level.getInteger("xPos"), level.getInteger("zPos"));
        }
        boolean write = outdated;
        if (this.eraseCache) {
            write = true;
            level.setBoolean("LightPopulated", false);
            level.setIntArray("HeightMap", heightMap(level, this.blockIds));
        }
        if (!write) {
            return false;
        }
        fixed.setInteger("DataVersion", GAME_VERSION);
        this.fixer.writeVersionData(fixed);
        try (DataOutputStream out = region.getChunkDataOutputStream(pos.x & 31, pos.z & 31)) {
            CompressedStreamTools.write(fixed, out);
        }
        return true;
    }

    /** @return whether the chunk was saved by an older game, or before one of its mods' data fixes */
    public static boolean outdated(NBTTagCompound tag, CompoundDataFixer fixer) {
        int version = tag.hasKey("DataVersion", Constants.NBT.TAG_ANY_NUMERIC) ? tag.getInteger("DataVersion") : -1;
        if (version < GAME_VERSION) {
            return true;
        }
        NBTTagCompound current = new NBTTagCompound();
        fixer.writeVersionData(current);
        NBTTagCompound now = current.getCompoundTag("ForgeDataVersion");
        NBTTagCompound saved = tag.getCompoundTag("ForgeDataVersion");
        for (String mod : now.getKeySet()) {
            if (!saved.hasKey(mod, Constants.NBT.TAG_ANY_NUMERIC) || saved.getInteger(mod) < now.getInteger(mod)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 1.12.2's {@code Chunk.generateHeightMap} from a chunk's saved sections: for each column, one above the highest
     * block that stops light (0 when none does).
     */
    public static int[] heightMap(NBTTagCompound level, @Nullable Map<Integer, Block> ids) {
        int[] map = new int[256];
        NBTTagList sections = level.getTagList("Sections", Constants.NBT.TAG_COMPOUND);
        IBlockState[][] states = new IBlockState[16][];
        for (int i = 0; i < sections.tagCount(); i++) {
            NBTTagCompound s = sections.getCompoundTagAt(i);
            int y = s.getByte("Y");
            if (y < 0 || y >= 16 || !s.hasKey("Blocks", Constants.NBT.TAG_BYTE_ARRAY)) {
                continue;
            }
            byte[] blocks = s.getByteArray("Blocks");
            NibbleArray data = new NibbleArray(s.getByteArray("Data"));
            NibbleArray add = s.hasKey("Add", Constants.NBT.TAG_BYTE_ARRAY) ? new NibbleArray(s.getByteArray("Add")) : null;
            IBlockState[] out = new IBlockState[4096];
            for (int j = 0; j < 4096 && j < blocks.length; j++) {
                int x = j & 15;
                int by = j >> 8 & 15;
                int z = j >> 4 & 15;
                int id = (add == null ? 0 : add.get(x, by, z)) << 8 | (blocks[j] & 255);
                if (id == 0) {
                    continue;
                }
                Block block = ids != null ? ids.get(id) : Block.getBlockById(id);
                if (block != null) {
                    out[j] = stateOf(block, data.get(x, by, z));
                }
            }
            states[y] = out;
        }
        int topSection = -1;
        for (int y = 15; y >= 0; y--) {
            if (states[y] != null) {
                topSection = y;
                break;
            }
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int k = (topSection + 1) * 16; k > 0; k--) {
                    IBlockState[] s = states[(k - 1) >> 4];
                    IBlockState state = s == null ? null : s[((k - 1) & 15) << 8 | z << 4 | x];
                    if (state != null && opacity(state) != 0) {
                        map[z << 4 | x] = k;
                        break;
                    }
                }
            }
        }
        return map;
    }

    @SuppressWarnings("deprecation")
    private static IBlockState stateOf(Block block, int meta) {
        try {
            return block.getStateFromMeta(meta);
        } catch (RuntimeException e) {
            return block.getDefaultState();
        }
    }

    @SuppressWarnings("deprecation")
    private static int opacity(IBlockState state) {
        try {
            return state.getLightOpacity();
        } catch (RuntimeException e) {
            return 255;
        }
    }

    /**
     * The world's numeric block ids from its {@code level.dat} (FML's {@code Registries/minecraft:blocks/ids}), named
     * by this game's blocks; null when the file has none (this game's ids are used).
     */
    @Nullable
    public static Map<Integer, Block> worldBlockIds(File world) {
        File file = new File(world, "level.dat");
        if (!file.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            NBTTagCompound root = CompressedStreamTools.readCompressed(in);
            NBTTagList ids = root.getCompoundTag("FML").getCompoundTag("Registries").getCompoundTag("minecraft:blocks")
                    .getTagList("ids", Constants.NBT.TAG_COMPOUND);
            if (ids.tagCount() == 0) {
                return null;
            }
            Map<Integer, Block> out = new HashMap<Integer, Block>();
            for (int i = 0; i < ids.tagCount(); i++) {
                NBTTagCompound e = ids.getCompoundTagAt(i);
                Block block = Block.REGISTRY.containsKey(new ResourceLocation(e.getString("K"))) ? Block.REGISTRY.getObject(new ResourceLocation(e.getString("K"))) : null;
                if (block != null) {
                    out.put(e.getInteger("V"), block);
                }
            }
            return out;
        } catch (IOException | RuntimeException e) {
            BackportCore.LOGGER.warn("Optimize World: cannot read {}'s block ids; using this game's", world.getName(), e);
            return null;
        }
    }

    private static void close(RegionFile region) {
        try {
            region.close();
        } catch (IOException e) {
            BackportCore.LOGGER.error("Error upgrading chunk", e);
        }
    }
}

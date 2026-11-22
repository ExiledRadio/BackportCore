package com.exiledradio.backportcore.locate;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.settings.Family;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.BlockStatePaletteRegistry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IBlockStatePalette;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Today's {@code /locate poi}: points of interest by 26.3's type ids and tags, and today's search
 * ({@code PoiManager.findClosestWithType(type, origin, 256, ANY)}).
 *
 * <h2>Types</h2>
 * A point of interest is a block state, as today's ({@code PoiTypes}: each type is a set of states). 1.12.2 keeps no
 * record of them, so a type here is a predicate over block states. Every one of 26.3's types is known (suggested,
 * searchable) whether or not its block exists in this game; Core matches each to its blocks by registry name:
 * <ul>
 *   <li>{@code minecraft:home}: the head half of any bed ({@link BlockBed}), as today's every bed's head;</li>
 *   <li>{@code minecraft:nether_portal}: 1.12.2's {@code minecraft:portal};</li>
 *   <li>every other type: a block under today's vanilla name ({@code blast_furnace}, {@code lodestone},
 *       {@code exposed_lightning_rod}, ...), all its states, in {@code minecraft:} or a family backport's namespace
 *       ({@link Family#MEMBERS}): 1.12.2's brewing stand and cauldron, Village &amp; Pillage's workstations and bell,
 *       Buzzy Bees' nests and hives, Nether Update's lodestone, Caves &amp; Cliffs' lightning rod and cauldrons.</li>
 * </ul>
 * Other mods' blocks are no point of interest unless a mod says so with {@link #register}. Tags ({@link #tag}) are
 * 26.3's three, and whatever a backport adds.
 *
 * <h2>Search</h2>
 * Today's {@code getInRange}: the chunks within 17 of the origin's ({@code floorDiv(256, 16) + 1}), the records in
 * them within 256 blocks on x and z and 256 in three dimensions, the nearest by {@code distSqr} wins. Today's index
 * covers every saved chunk, loaded or not; here a loaded chunk's sections are read (only those whose palette holds a
 * wanted state), and an unloaded one's from its save (the region file, or the copy still waiting to be written). Saved
 * chunks are read once and kept until the chunk loads, the only way their blocks can change.
 * Ties go to the first found: chunks z then x, sections bottom up, blocks y, z, x.
 */
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID)
public final class PoiLocate {

    /** 26.3's {@code LocateCommand.POI_SEARCH_RADIUS}. */
    public static final int RADIUS = 256;

    /** 26.3's point of interest types ({@code PoiTypes}). */
    public static final List<String> TODAY = Collections.unmodifiableList(Arrays.asList(
            "armorer", "bee_nest", "beehive", "butcher", "cartographer", "cleric", "farmer", "fisherman", "fletcher",
            "home", "leatherworker", "librarian", "lightning_rod", "lodestone", "mason", "meeting", "nether_portal",
            "shepherd", "test_instance", "toolsmith", "weaponsmith"));

    /** What the search found: where, and which type. */
    public static final class Found {
        public final BlockPos pos;
        public final String id;

        public Found(BlockPos pos, String id) {
            this.pos = pos;
            this.id = id;
        }

        @Override
        public String toString() {
            return this.id + " at " + this.pos;
        }
    }

    /** One point of interest of a saved chunk. */
    private static final class Record {
        final BlockPos pos;
        final String id;

        Record(BlockPos pos, String id) {
            this.pos = pos;
            this.id = id;
        }
    }

    private static final Set<String> IDS = new LinkedHashSet<String>();
    /** Type id to its matchers, in registration order. */
    private static final Map<String, List<Predicate<IBlockState>>> MATCHERS = new LinkedHashMap<String, List<Predicate<IBlockState>>>();
    /** Block path (no namespace) to the type it names, for {@code minecraft:} and family blocks. */
    private static final Map<String, String> BY_NAME = new HashMap<String, String>();
    private static final Map<String, Set<String>> TAGS = new LinkedHashMap<String, Set<String>>();
    private static final Set<String> NAMESPACES = new HashSet<String>();

    /** State to its type, or {@link #NONE}; cleared when a registration changes. */
    private static final Map<IBlockState, String> TYPE_OF = new IdentityHashMap<IBlockState, String>();
    private static final String NONE = "";

    /** Dimension to saved (unloaded) chunk to its points of interest. */
    private static final Map<Integer, Map<Long, List<Record>>> SAVED = new HashMap<Integer, Map<Long, List<Record>>>();

    private static Field paletteField;
    private static Field pendingField;
    private static boolean reflected;

    static {
        for (String id : TODAY) {
            IDS.add("minecraft:" + id);
        }
        names("armorer", "blast_furnace");
        names("butcher", "smoker");
        names("cartographer", "cartography_table");
        names("cleric", "brewing_stand");
        names("farmer", "composter");
        names("fisherman", "barrel");
        names("fletcher", "fletching_table");
        names("leatherworker", "cauldron", "water_cauldron", "lava_cauldron", "powder_snow_cauldron");
        names("librarian", "lectern");
        names("mason", "stonecutter");
        names("shepherd", "loom");
        names("toolsmith", "smithing_table");
        names("weaponsmith", "grindstone");
        names("meeting", "bell");
        names("beehive", "beehive");
        names("bee_nest", "bee_nest");
        names("nether_portal", "nether_portal", "portal");
        names("lodestone", "lodestone");
        names("test_instance", "test_instance_block");
        names("lightning_rod", "lightning_rod", "exposed_lightning_rod", "weathered_lightning_rod", "oxidized_lightning_rod",
                "waxed_lightning_rod", "waxed_exposed_lightning_rod", "waxed_weathered_lightning_rod",
                "waxed_oxidized_lightning_rod");
        // Today's BEDS: every bed's head half.
        register("minecraft:home", state -> state.getBlock() instanceof BlockBed
                && state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD);
        // 26.3's data/minecraft/tags/point_of_interest_type.
        tag("#minecraft:acquirable_job_site", "minecraft:armorer", "minecraft:butcher", "minecraft:cartographer",
                "minecraft:cleric", "minecraft:farmer", "minecraft:fisherman", "minecraft:fletcher", "minecraft:leatherworker",
                "minecraft:librarian", "minecraft:mason", "minecraft:shepherd", "minecraft:toolsmith", "minecraft:weaponsmith");
        tag("#minecraft:bee_home", "minecraft:beehive", "minecraft:bee_nest");
        tag("#minecraft:village", "#minecraft:acquirable_job_site", "minecraft:home", "minecraft:meeting");
        NAMESPACES.add("minecraft");
        for (Family.Member member : Family.MEMBERS) {
            NAMESPACES.add(member.modid);
        }
    }

    private PoiLocate() {
    }

    private static void names(String type, String... paths) {
        for (String path : paths) {
            BY_NAME.put(path, "minecraft:" + type);
        }
    }

    // ---- registering

    /**
     * Adds block states to a type ({@code minecraft:} if no namespace); a new id becomes a new type. A state two
     * types claim belongs to the first registered (today's game refuses such a state outright).
     */
    public static synchronized void register(String id, Predicate<IBlockState> matches) {
        String key = StructureLocate.normalize(id);
        IDS.add(key);
        List<Predicate<IBlockState>> list = MATCHERS.get(key);
        if (list == null) {
            list = new ArrayList<Predicate<IBlockState>>();
            MATCHERS.put(key, list);
        }
        list.add(matches);
        TYPE_OF.clear();
    }

    /** Every state of these blocks is a point of interest of this type. */
    public static void register(String id, Block... blocks) {
        final Set<Block> set = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
        set.addAll(Arrays.asList(blocks));
        register(id, state -> set.contains(state.getBlock()));
    }

    /** A tag of types and tags ({@code #minecraft:village}); adds to the tag if it exists. */
    public static synchronized void tag(String tag, String... members) {
        String key = StructureLocate.normalize(tag.startsWith("#") ? tag : "#" + tag);
        Set<String> set = TAGS.get(key);
        if (set == null) {
            set = new LinkedHashSet<String>();
            TAGS.put(key, set);
        }
        for (String member : members) {
            set.add(StructureLocate.normalize(member));
        }
    }

    // ---- asking

    /** @return every type id, sorted */
    public static synchronized List<String> ids() {
        return new ArrayList<String>(new TreeSet<String>(IDS));
    }

    /** @return every tag ({@code #} included), sorted */
    public static synchronized List<String> tags() {
        return new ArrayList<String>(new TreeSet<String>(TAGS.keySet()));
    }

    /** @return whether an id or {@code #tag} names a type or tag */
    public static synchronized boolean known(String key) {
        String k = StructureLocate.normalize(key);
        return k.startsWith("#") ? TAGS.containsKey(k) : IDS.contains(k);
    }

    /** @return the types an id or tag stands for (a tag's members, nested tags followed) */
    public static synchronized Set<String> members(String key) {
        Set<String> out = new LinkedHashSet<String>();
        collect(StructureLocate.normalize(key), out, new HashSet<String>());
        return out;
    }

    private static void collect(String key, Set<String> out, Set<String> seen) {
        if (!key.startsWith("#")) {
            out.add(key);
            return;
        }
        if (!seen.add(key) || !TAGS.containsKey(key)) {
            return;
        }
        for (String member : TAGS.get(key)) {
            collect(member, out, seen);
        }
    }

    /** @return the type a block state is a point of interest of, or null */
    @Nullable
    public static synchronized String typeOf(IBlockState state) {
        String type = TYPE_OF.get(state);
        if (type == null) {
            type = resolve(state);
            TYPE_OF.put(state, type);
        }
        return type.isEmpty() ? null : type;
    }

    private static String resolve(IBlockState state) {
        for (Map.Entry<String, List<Predicate<IBlockState>>> e : MATCHERS.entrySet()) {
            for (Predicate<IBlockState> p : e.getValue()) {
                try {
                    if (p.test(state)) {
                        return e.getKey();
                    }
                } catch (RuntimeException ignored) {
                    // A predicate asking for a property this state lacks.
                }
            }
        }
        ResourceLocation name = state.getBlock().getRegistryName();
        if (name != null && NAMESPACES.contains(name.getNamespace())) {
            String type = BY_NAME.get(name.getPath());
            if (type != null && ("portal".equals(name.getPath()) ? "minecraft".equals(name.getNamespace()) : true)) {
                return type;
            }
        }
        return NONE;
    }

    // ---- searching

    /**
     * Today's {@code findClosestWithType(type, origin, 256, ANY)}.
     *
     * @return the nearest point of interest of the id or any of the tag's types, or null
     */
    @Nullable
    public static Found nearest(WorldServer world, BlockPos origin, String key) {
        Set<String> wanted = members(key);
        if (wanted.isEmpty()) {
            return null;
        }
        reflect();
        int chunkRadius = Math.floorDiv(RADIUS, 16) + 1;
        int ox = origin.getX() >> 4;
        int oz = origin.getZ() >> 4;
        Search search = new Search(origin, wanted);
        // ChunkPos.rangeClosed: x inner, z outer.
        for (int cz = oz - chunkRadius; cz <= oz + chunkRadius; cz++) {
            for (int cx = ox - chunkRadius; cx <= ox + chunkRadius; cx++) {
                // Not getLoadedChunk, which would call off every pending unload in range.
                Chunk chunk = world.getChunkProvider().loadedChunks.get(ChunkPos.asLong(cx, cz));
                if (chunk != null) {
                    scanLoaded(chunk, search);
                } else {
                    for (Record r : saved(world, cx, cz)) {
                        search.offer(r.pos, r.id);
                    }
                }
            }
        }
        return search.best == null ? null : new Found(search.best, search.bestId);
    }

    /** The running minimum, today's {@code getInRange} filter and {@code min(distSqr)}. */
    private static final class Search {
        final BlockPos origin;
        final Set<String> wanted;
        BlockPos best;
        String bestId;
        double bestDistance = Double.MAX_VALUE;

        Search(BlockPos origin, Set<String> wanted) {
            this.origin = origin;
            this.wanted = wanted;
        }

        void offer(BlockPos pos, String id) {
            if (!this.wanted.contains(id) || Math.abs(pos.getX() - this.origin.getX()) > RADIUS
                    || Math.abs(pos.getZ() - this.origin.getZ()) > RADIUS) {
                return;
            }
            double d = pos.distanceSq(this.origin);
            if (d <= (double) (RADIUS * RADIUS) && d < this.bestDistance) {
                this.best = pos;
                this.bestId = id;
                this.bestDistance = d;
            }
        }
    }

    private static void scanLoaded(Chunk chunk, Search search) {
        int baseX = chunk.x << 4;
        int baseZ = chunk.z << 4;
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray().clone();
        Arrays.sort(sections, (a, b) -> Integer.compare(a == null ? Integer.MIN_VALUE : a.getYLocation(),
                b == null ? Integer.MIN_VALUE : b.getYLocation()));
        for (ExtendedBlockStorage section : sections) {
            if (section == Chunk.NULL_BLOCK_STORAGE || section.isEmpty() || !mayHold(section.getData(), search.wanted)) {
                continue;
            }
            int baseY = section.getYLocation();
            BlockStateContainer data = section.getData();
            IBlockState last = null;
            String lastType = null;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        IBlockState state = data.get(x, y, z);
                        if (state != last) {
                            last = state;
                            lastType = typeOf(state);
                        }
                        if (lastType != null) {
                            search.offer(new BlockPos(baseX + x, baseY + y, baseZ + z), lastType);
                        }
                    }
                }
            }
        }
    }

    /** False when the section's palette holds no wanted state (a palette may hold states no longer placed). */
    private static boolean mayHold(BlockStateContainer data, Set<String> wanted) {
        if (paletteField == null) {
            return true;
        }
        IBlockStatePalette palette;
        try {
            palette = (IBlockStatePalette) paletteField.get(data);
        } catch (IllegalAccessException e) {
            return true;
        }
        if (palette == null || palette instanceof BlockStatePaletteRegistry) {
            return true;
        }
        // Linear and hash-map palettes number their states from 0 with no gaps.
        for (int id = 0; id < 4096; id++) {
            IBlockState state;
            try {
                state = palette.getBlockState(id);
            } catch (RuntimeException e) {
                return true;
            }
            if (state == null) {
                return false;
            }
            String type = typeOf(state);
            if (type != null && wanted.contains(type)) {
                return true;
            }
        }
        return true;
    }

    /** A saved chunk's points of interest, read once until the chunk loads. */
    private static List<Record> saved(WorldServer world, int cx, int cz) {
        int dim = world.provider.getDimension();
        long key = ChunkPos.asLong(cx, cz);
        Map<Long, List<Record>> byChunk = SAVED.get(dim);
        if (byChunk == null) {
            byChunk = new HashMap<Long, List<Record>>();
            SAVED.put(dim, byChunk);
        }
        List<Record> records = byChunk.get(key);
        if (records == null) {
            records = read(world, cx, cz);
            byChunk.put(key, records);
        }
        return records;
    }

    @SuppressWarnings("unchecked")
    private static List<Record> read(WorldServer world, int cx, int cz) {
        IChunkLoader loader = world.getChunkProvider().chunkLoader;
        if (!(loader instanceof AnvilChunkLoader)) {
            return Collections.emptyList();
        }
        AnvilChunkLoader anvil = (AnvilChunkLoader) loader;
        NBTTagCompound tag = null;
        try {
            if (pendingField != null) {
                tag = ((Map<ChunkPos, NBTTagCompound>) pendingField.get(anvil)).get(new ChunkPos(cx, cz));
            }
            if (tag == null) {
                DataInputStream in = RegionFileCache.getChunkInputStream(anvil.chunkSaveLocation, cx, cz);
                if (in == null) {
                    return Collections.emptyList();
                }
                try {
                    tag = CompressedStreamTools.read(in);
                } finally {
                    in.close();
                }
            }
        } catch (Exception e) {
            BackportCore.LOGGER.warn("Couldn't read chunk {}, {} for /locate poi: {}", cx, cz, e.toString());
            return Collections.emptyList();
        }
        if (!tag.hasKey("Level", Constants.NBT.TAG_COMPOUND)) {
            return Collections.emptyList();
        }
        NBTTagList sections = tag.getCompoundTag("Level").getTagList("Sections", Constants.NBT.TAG_COMPOUND);
        List<NBTTagCompound> sorted = new ArrayList<NBTTagCompound>();
        for (int i = 0; i < sections.tagCount(); i++) {
            sorted.add(sections.getCompoundTagAt(i));
        }
        sorted.sort((a, b) -> Integer.compare(a.getByte("Y"), b.getByte("Y")));
        List<Record> out = new ArrayList<Record>();
        String[] typeById = new String[65536];
        int baseX = cx << 4;
        int baseZ = cz << 4;
        for (NBTTagCompound section : sorted) {
            byte[] blocks = section.getByteArray("Blocks");
            byte[] data = section.getByteArray("Data");
            byte[] add = section.hasKey("Add", Constants.NBT.TAG_BYTE_ARRAY) ? section.getByteArray("Add") : null;
            if (blocks.length != 4096 || data.length != 2048 || (add != null && add.length != 2048)) {
                // Not 1.12.2's own section format (an id-extending mod's): nothing to read.
                continue;
            }
            int baseY = section.getByte("Y") << 4;
            for (int i = 0; i < 4096; i++) {
                int meta = nibble(data, i);
                int extra = add == null ? 0 : nibble(add, i);
                int stateId = extra << 12 | (blocks[i] & 255) << 4 | meta;
                String type = typeById[stateId];
                if (type == null) {
                    IBlockState state = Block.BLOCK_STATE_IDS.getByValue(stateId);
                    String t = state == null ? null : typeOf(state);
                    type = t == null ? NONE : t;
                    typeById[stateId] = type;
                }
                if (!type.isEmpty()) {
                    // 1.12.2's index: y << 8 | z << 4 | x.
                    out.add(new Record(new BlockPos(baseX + (i & 15), baseY + (i >> 8), baseZ + (i >> 4 & 15)), type));
                }
            }
        }
        return out.isEmpty() ? Collections.<Record>emptyList() : out;
    }

    private static int nibble(byte[] array, int index) {
        int b = array[index >> 1];
        return (index & 1) == 0 ? b & 15 : b >> 4 & 15;
    }

    private static synchronized void reflect() {
        if (reflected) {
            return;
        }
        reflected = true;
        paletteField = instanceField(BlockStateContainer.class, IBlockStatePalette.class);
        pendingField = instanceField(AnvilChunkLoader.class, Map.class);
    }

    @Nullable
    private static Field instanceField(Class<?> owner, Class<?> type) {
        for (Field f : owner.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && f.getType() == type) {
                try {
                    f.setAccessible(true);
                    return f;
                } catch (RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    // ---- keeping saved chunks honest

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        World world = event.getWorld();
        if (world.isRemote) {
            return;
        }
        Map<Long, List<Record>> byChunk = SAVED.get(world.provider.getDimension());
        if (byChunk != null) {
            Chunk chunk = event.getChunk();
            byChunk.remove(ChunkPos.asLong(chunk.x, chunk.z));
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) {
            SAVED.remove(event.getWorld().provider.getDimension());
        }
    }

    /** Forgets every saved chunk read (the self-tests, after writing a chunk's save by hand). */
    public static void forgetSaved() {
        SAVED.clear();
    }
}

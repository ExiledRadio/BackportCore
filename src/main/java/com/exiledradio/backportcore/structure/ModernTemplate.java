package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.util.BlockStates;
import com.exiledradio.backportcore.waterlog.Waterloggable;
import com.exiledradio.backportcore.waterlog.Waterlogging;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A structure file of 1.13 or any later Minecraft, read into 1.12.2 blocks as it is placed.
 *
 * <h2>Files</h2>
 * Mojang's own {@code .nbt} files, unchanged: drop {@code data/minecraft/structure/shipwreck/*.nbt}
 * from a client jar into {@code assets/<yourmod>/structures/shipwreck/} and
 * {@link #load load("yourmod:shipwreck/rightsideup_full")}. Every format since 1.13 reads: one
 * palette or several ({@code palettes}, as shipwrecks have one per wood), {@code Name}/{@code Properties}
 * or 26.1's {@code id}/{@code properties}, and today's renamed blocks (see {@link ModernPalette}).
 * Files saved by 1.12.2 itself (DataVersion below 1451) read too.
 *
 * <h2>Blocks</h2>
 * Each palette entry becomes a 1.12.2 state through {@link ModernPalette}, once, the first time the
 * template is placed. Three kinds of block never go into the world as themselves:
 * <ul>
 * <li>structure blocks in data mode are {@link #dataMarkers() markers}: the structure's own code
 * decides what goes there (a chest, a drowned). Other structure blocks and structure voids are left
 * out.</li>
 * <li>jigsaw blocks are {@link #jigsaws() connectors}, placed as their final state.</li>
 * </ul>
 *
 * <h2>Placing</h2>
 * {@link #place} follows today's {@code StructureTemplate.placeInWorld}: mirror, then rotation around
 * the pivot, integrity, processors, tile entities, entities. It is written for world generation: give
 * it the chunk being populated as the {@link TemplatePlacement#clip clip} and it places that chunk's
 * part only, never loading a neighbor. Blocks that pop off without their support (a torch whose wall
 * is in the next chunk) are written in anyway, and no item drops while it places.
 */
public final class ModernTemplate {

    private static final Map<ResourceLocation, ModernTemplate> CACHE = new HashMap<ResourceLocation, ModernTemplate>();

    /** 1.13's first data version (17w47a): the Flattening. Files below it use 1.12.2's names. */
    private static final int FLATTENING = 1451;

    private final ResourceLocation id;
    private final int dataVersion;
    private final BlockPos size;
    /** Palettes: for each, the name and properties of every entry. */
    private final List<String[]> names = new ArrayList<String[]>();
    private final List<List<Map<String, String>>> props = new ArrayList<List<Map<String, String>>>();
    private final List<Entry> blocks = new ArrayList<Entry>();
    private final List<EntityEntry> entities = new ArrayList<EntityEntry>();
    private final List<DataMarker> markers = new ArrayList<DataMarker>();
    private final List<Jigsaw> jigsaws = new ArrayList<Jigsaw>();
    /** Per palette, its entries resolved; filled on first placement. */
    @Nullable
    private ModernPalette.Resolved[][] resolved;

    private static final class Entry {
        final BlockPos pos;
        final int state;
        @Nullable
        final NBTTagCompound nbt;

        Entry(BlockPos pos, int state, @Nullable NBTTagCompound nbt) {
            this.pos = pos;
            this.state = state;
            this.nbt = nbt;
        }
    }

    private static final class EntityEntry {
        final Vec3d pos;
        final BlockPos blockPos;
        final NBTTagCompound nbt;

        EntityEntry(Vec3d pos, BlockPos blockPos, NBTTagCompound nbt) {
            this.pos = pos;
            this.blockPos = blockPos;
            this.nbt = nbt;
        }
    }

    /** A structure block in data mode: a spot the structure's code fills ("chest", "drowned"). */
    public static final class DataMarker {
        /** Position in the template, before rotation. */
        public final BlockPos pos;
        public final String metadata;

        DataMarker(BlockPos pos, String metadata) {
            this.pos = pos;
            this.metadata = metadata;
        }
    }

    /** A jigsaw block: where another piece may join on. Read for jigsaw assembly. */
    public static final class Jigsaw {
        /** Position in the template, before rotation. */
        public final BlockPos pos;
        /** The face that points at the next piece, before rotation. */
        public final EnumFacing front;
        /** Which way is up for the joined piece (1.16+; up for side-facing 1.14 jigsaws), before rotation. */
        public final EnumFacing top;
        public final String name;
        public final String target;
        public final String pool;
        /** "rollable" or "aligned" (1.16+); for older files, "aligned" facing sideways, else "rollable", as today's game reads them. */
        public final String joint;
        /** The block the jigsaw becomes, as today's game writes a state. */
        public final String finalState;

        Jigsaw(BlockPos pos, EnumFacing front, EnumFacing top, String name, String target, String pool, String joint,
               String finalState) {
            this.pos = pos;
            this.front = front;
            this.top = top;
            this.name = name;
            this.target = target;
            this.pool = pool;
            this.joint = joint;
            this.finalState = finalState;
        }
    }

    private ModernTemplate(ResourceLocation id, NBTTagCompound tag) {
        this.id = id;
        this.dataVersion = tag.getInteger("DataVersion");
        NBTTagList sizeTag = tag.getTagList("size", Constants.NBT.TAG_INT);
        this.size = sizeTag.tagCount() == 3
                ? new BlockPos(sizeTag.getIntAt(0), sizeTag.getIntAt(1), sizeTag.getIntAt(2)) : BlockPos.ORIGIN;

        if (tag.hasKey("palettes", Constants.NBT.TAG_LIST)) {
            NBTTagList palettes = tag.getTagList("palettes", Constants.NBT.TAG_LIST);
            for (int i = 0; i < palettes.tagCount(); i++) {
                NBTBase palette = palettes.get(i);
                if (palette instanceof NBTTagList) {
                    readPalette((NBTTagList) palette);
                }
            }
        } else {
            readPalette(tag.getTagList("palette", Constants.NBT.TAG_COMPOUND));
        }

        NBTTagList blockList = tag.getTagList("blocks", Constants.NBT.TAG_COMPOUND);
        String[] first = this.names.isEmpty() ? new String[0] : this.names.get(0);
        for (int i = 0; i < blockList.tagCount(); i++) {
            NBTTagCompound b = blockList.getCompoundTagAt(i);
            NBTTagList p = b.getTagList("pos", Constants.NBT.TAG_INT);
            BlockPos pos = new BlockPos(p.getIntAt(0), p.getIntAt(1), p.getIntAt(2));
            int state = b.getInteger("state");
            NBTTagCompound nbt = b.hasKey("nbt", Constants.NBT.TAG_COMPOUND) ? b.getCompoundTag("nbt") : null;
            String name = state >= 0 && state < first.length ? first[state] : "minecraft:air";
            if ("minecraft:structure_void".equals(name)) {
                continue;
            }
            if ("minecraft:structure_block".equals(name)) {
                if (nbt != null && "DATA".equalsIgnoreCase(nbt.getString("mode"))) {
                    this.markers.add(new DataMarker(pos, nbt.getString("metadata")));
                }
                continue;
            }
            if ("minecraft:jigsaw".equals(name)) {
                this.jigsaws.add(jigsaw(pos, this.props.get(0).get(state), nbt));
                continue;
            }
            this.blocks.add(new Entry(pos, state, nbt));
        }

        NBTTagList entityList = tag.getTagList("entities", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < entityList.tagCount(); i++) {
            NBTTagCompound e = entityList.getCompoundTagAt(i);
            NBTTagList p = e.getTagList("pos", Constants.NBT.TAG_DOUBLE);
            NBTTagList bp = e.getTagList("blockPos", Constants.NBT.TAG_INT);
            if (p.tagCount() != 3 || !e.hasKey("nbt", Constants.NBT.TAG_COMPOUND)) {
                continue;
            }
            Vec3d pos = new Vec3d(p.getDoubleAt(0), p.getDoubleAt(1), p.getDoubleAt(2));
            BlockPos blockPos = bp.tagCount() == 3 ? new BlockPos(bp.getIntAt(0), bp.getIntAt(1), bp.getIntAt(2)) : new BlockPos(pos);
            this.entities.add(new EntityEntry(pos, blockPos, e.getCompoundTag("nbt")));
        }
    }

    private void readPalette(NBTTagList list) {
        String[] palNames = new String[list.tagCount()];
        List<Map<String, String>> palProps = new ArrayList<Map<String, String>>(list.tagCount());
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            String name = entry.hasKey("Name") ? entry.getString("Name") : entry.getString("id");
            NBTTagCompound p = entry.hasKey("Properties") ? entry.getCompoundTag("Properties") : entry.getCompoundTag("properties");
            Map<String, String> map = new LinkedHashMap<String, String>();
            for (String key : p.getKeySet()) {
                map.put(key, p.getString(key));
            }
            palNames[i] = ModernPalette.full(name);
            palProps.add(map);
        }
        this.names.add(palNames);
        this.props.add(palProps);
    }

    private static Jigsaw jigsaw(BlockPos pos, Map<String, String> props, @Nullable NBTTagCompound nbt) {
        EnumFacing front = EnumFacing.NORTH;
        EnumFacing top = EnumFacing.UP;
        String orientation = props.get("orientation");
        if (orientation != null && orientation.indexOf('_') > 0) {
            EnumFacing f = EnumFacing.byName(orientation.substring(0, orientation.indexOf('_')));
            EnumFacing t = EnumFacing.byName(orientation.substring(orientation.indexOf('_') + 1));
            front = f == null ? front : f;
            top = t == null ? top : t;
        } else if (props.containsKey("facing")) {
            EnumFacing f = EnumFacing.byName(props.get("facing"));
            front = f == null ? front : f;
            top = front.getAxis() == EnumFacing.Axis.Y ? EnumFacing.NORTH : EnumFacing.UP;
        }
        NBTTagCompound n = nbt == null ? new NBTTagCompound() : nbt;
        String name = n.hasKey("name") ? n.getString("name") : n.getString("attachement_type");
        // Today's JigsawBlock.canAttach: a missing or unknown joint is aligned for a side-facing jigsaw.
        String joint = n.getString("joint");
        if (!"rollable".equals(joint) && !"aligned".equals(joint)) {
            joint = front.getAxis().isHorizontal() ? "aligned" : "rollable";
        }
        String finalState = n.hasKey("final_state") ? n.getString("final_state") : "minecraft:air";
        return new Jigsaw(pos, front, top, name, n.getString("target"), n.getString("pool"), joint, finalState);
    }

    // ---- loading

    /**
     * @param id {@code yourmod:shipwreck/rightsideup_full}, read from
     *           {@code assets/yourmod/structures/shipwreck/rightsideup_full.nbt} (or today's folder name,
     *           {@code structure/})
     * @return the template; an empty one, logged, if the file is missing or unreadable
     */
    public static ModernTemplate load(ResourceLocation id) {
        synchronized (CACHE) {
            ModernTemplate known = CACHE.get(id);
            if (known != null) {
                return known;
            }
        }
        ModernTemplate template = null;
        for (String folder : new String[] {"structures", "structure"}) {
            String path = "/assets/" + id.getNamespace() + "/" + folder + "/" + id.getPath() + ".nbt";
            InputStream in = ModernTemplate.class.getResourceAsStream(path);
            if (in == null) {
                continue;
            }
            try {
                template = read(id, CompressedStreamTools.readCompressed(in));
            } catch (Exception e) {
                BackportCore.LOGGER.error("Could not read structure file {}.", path, e);
            } finally {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // nothing to do
                }
            }
            break;
        }
        if (template == null) {
            BackportCore.LOGGER.error("Structure {} is missing; it will be left out.", id);
            template = new ModernTemplate(id, new NBTTagCompound());
        }
        synchronized (CACHE) {
            CACHE.put(id, template);
        }
        return template;
    }

    /** @return a template read from structure NBT you already have (not cached) */
    public static ModernTemplate read(ResourceLocation id, NBTTagCompound tag) {
        return new ModernTemplate(id, tag);
    }

    // ---- what the file holds

    public ResourceLocation id() {
        return this.id;
    }

    /** @return true for a missing file, or one with no blocks and no entities */
    public boolean isEmpty() {
        return this.blocks.isEmpty() && this.entities.isEmpty() && this.markers.isEmpty() && this.jigsaws.isEmpty();
    }

    /** @return the size before rotation */
    public BlockPos size() {
        return this.size;
    }

    public int dataVersion() {
        return this.dataVersion;
    }

    /** @return how many palettes the file has (1 for most; shipwrecks have several) */
    public int palettes() {
        return Math.max(1, this.names.size());
    }

    public List<DataMarker> dataMarkers() {
        return Collections.unmodifiableList(this.markers);
    }

    public List<Jigsaw> jigsaws() {
        return Collections.unmodifiableList(this.jigsaws);
    }

    /** @return every block name of the file that nothing provides here, for tests and logs */
    public Set<String> unresolvedBlocks() {
        Set<String> out = new LinkedHashSet<String>();
        ModernPalette.Resolved[][] all = resolved();
        for (int p = 0; p < all.length; p++) {
            for (int i = 0; i < all[p].length; i++) {
                String name = this.names.get(p)[i];
                if (all[p][i].state == null && !isAir(name) && !"minecraft:jigsaw".equals(name)
                        && !"minecraft:structure_block".equals(name) && !"minecraft:structure_void".equals(name)) {
                    out.add(name);
                }
            }
        }
        for (Jigsaw jigsaw : this.jigsaws) {
            if (ModernPalette.resolve(jigsaw.finalState).state == null) {
                out.add(jigsaw.finalState);
            }
        }
        return out;
    }

    /**
     * @return the block a palette entry of the given template position becomes, before any
     * processor, or null for a position the file does not fill
     */
    @Nullable
    public TemplateBlock blockAt(BlockPos local, int palette) {
        for (Entry entry : this.blocks) {
            if (entry.pos.equals(local)) {
                return block(entry, Math.min(Math.max(palette, 0), palettes() - 1));
            }
        }
        return null;
    }

    /**
     * Every block of the file in one palette, before any processor: its position in the template and
     * the block. Jigsaw blocks, structure blocks and structure voids are not among them. Since 1.2.0.
     */
    public void forEachBlock(int palette, java.util.function.BiConsumer<BlockPos, TemplateBlock> action) {
        int p = Math.min(Math.max(palette, 0), palettes() - 1);
        for (Entry entry : this.blocks) {
            action.accept(entry.pos, block(entry, p));
        }
    }

    // ---- geometry: today's StructureTemplate.transform

    /** @return where a template position ends up, relative to the origin */
    public static BlockPos transform(BlockPos local, Mirror mirror, Rotation rotation, BlockPos pivot) {
        int x = local.getX();
        int y = local.getY();
        int z = local.getZ();
        boolean mirrored = true;
        switch (mirror) {
            case LEFT_RIGHT:
                z = -z;
                break;
            case FRONT_BACK:
                x = -x;
                break;
            default:
                mirrored = false;
        }
        int px = pivot.getX();
        int pz = pivot.getZ();
        switch (rotation) {
            case COUNTERCLOCKWISE_90:
                return new BlockPos(px - pz + z, y, px + pz - x);
            case CLOCKWISE_90:
                return new BlockPos(px + pz - z, y, pz - px + x);
            case CLOCKWISE_180:
                return new BlockPos(px + px - x, y, pz + pz - z);
            default:
                return mirrored ? new BlockPos(x, y, z) : local;
        }
    }

    /** @return where an entity's template position ends up, relative to the origin */
    public static Vec3d transform(Vec3d local, Mirror mirror, Rotation rotation, BlockPos pivot) {
        double x = local.x;
        double y = local.y;
        double z = local.z;
        boolean mirrored = true;
        switch (mirror) {
            case LEFT_RIGHT:
                z = 1.0D - z;
                break;
            case FRONT_BACK:
                x = 1.0D - x;
                break;
            default:
                mirrored = false;
        }
        int px = pivot.getX();
        int pz = pivot.getZ();
        switch (rotation) {
            case COUNTERCLOCKWISE_90:
                return new Vec3d(px - pz + z, y, px + pz + 1 - x);
            case CLOCKWISE_90:
                return new Vec3d(px + pz + 1 - z, y, pz - px + x);
            case CLOCKWISE_180:
                return new Vec3d(px + px + 1 - x, y, pz + pz + 1 - z);
            default:
                return mirrored ? new Vec3d(x, y, z) : local;
        }
    }

    /** @return a template position in the world */
    public static BlockPos worldPos(BlockPos origin, BlockPos local, TemplatePlacement placement) {
        return transform(local, placement.mirror, placement.rotation, placement.pivot).add(origin);
    }

    /** @return the box this template fills in the world */
    public StructureBoundingBox boundingBox(BlockPos origin, TemplatePlacement placement) {
        if (this.size.getX() <= 0 || this.size.getY() <= 0 || this.size.getZ() <= 0) {
            return new StructureBoundingBox(origin, origin);
        }
        BlockPos a = worldPos(origin, BlockPos.ORIGIN, placement);
        BlockPos b = worldPos(origin, this.size.add(-1, -1, -1), placement);
        return new StructureBoundingBox(a, b);
    }

    // ---- placing

    /**
     * Places the template, or the part of it inside the placement's clip.
     *
     * @param origin where the template's corner (0, 0, 0) goes, before rotation around the pivot
     * @return how many blocks were placed
     */
    public int place(World world, BlockPos origin, TemplatePlacement placement) {
        // Today's StructureTemplate.placeInWorld counts jigsaw blocks among the palette's blocks, so a template
        // of nothing but jigsaws (bastion/blocks/gold, for one) still places their final states.
        if (this.blocks.isEmpty() && this.entities.isEmpty() && this.jigsaws.isEmpty()) {
            return 0;
        }
        int palette = placement.palette >= 0 ? Math.min(placement.palette, palettes() - 1)
                : palettes() == 1 ? 0 : new Random(positionSeed(origin) ^ placement.seed).nextInt(palettes());
        StructureBoundingBox clip = placement.clip;
        int placed = 0;

        boolean restoring = world.restoringBlockSnapshots;
        // No block placed here drops an item: a torch placed before its wall would otherwise pop off as one.
        world.restoringBlockSnapshots = true;
        try {
            for (Entry entry : this.blocks) {
                BlockPos pos = worldPos(origin, entry.pos, placement);
                // Gravity moves a block up or down only: the column decides first, so no other chunk is read.
                if (clip != null && (pos.getX() < clip.minX || pos.getX() > clip.maxX || pos.getZ() < clip.minZ || pos.getZ() > clip.maxZ)) {
                    continue;
                }
                BlockPos at = onSurface(pos, entry.pos, placement);
                if (clip != null && !clip.isVecInside(at)) {
                    continue;
                }
                if (place(world, pos, at, block(entry, palette), placement)) {
                    placed++;
                }
            }
            for (Jigsaw jigsaw : this.jigsaws) {
                BlockPos pos = worldPos(origin, jigsaw.pos, placement);
                // Gravity moves a block up or down only: the column decides first, so no other chunk is read.
                if (clip != null && (pos.getX() < clip.minX || pos.getX() > clip.maxX || pos.getZ() < clip.minZ || pos.getZ() > clip.maxZ)) {
                    continue;
                }
                BlockPos at = onSurface(pos, jigsaw.pos, placement);
                if (clip != null && !clip.isVecInside(at)) {
                    continue;
                }
                Map<String, String> p = new LinkedHashMap<String, String>();
                String n = ModernPalette.full(Flattening.parse(jigsaw.finalState.trim(), p));
                ModernPalette.Resolved r = ModernPalette.resolve(n, p, Integer.MAX_VALUE);
                if (place(world, pos, at, new TemplateBlock(n, p, r.state, r.tile, r.water), placement)) {
                    placed++;
                }
            }
        } finally {
            world.restoringBlockSnapshots = restoring;
        }

        if (placement.entities) {
            for (EntityEntry entry : this.entities) {
                BlockPos blockPos = worldPos(origin, entry.blockPos, placement);
                if (clip != null && !clip.isVecInside(blockPos)) {
                    continue;
                }
                Vec3d pos = transform(entry.pos, placement.mirror, placement.rotation, placement.pivot)
                        .add(origin.getX(), origin.getY(), origin.getZ());
                spawn(world, entry.nbt, pos, blockPos, placement);
            }
        }
        return placed;
    }

    private TemplateBlock block(Entry entry, int palette) {
        ModernPalette.Resolved[][] all = resolved();
        int p = Math.min(palette, all.length - 1);
        String name = entry.state < this.names.get(p).length ? this.names.get(p)[entry.state] : "minecraft:air";
        Map<String, String> props = entry.state < this.props.get(p).size()
                ? this.props.get(p).get(entry.state) : Collections.<String, String>emptyMap();
        ModernPalette.Resolved r = entry.state < all[p].length ? all[p][entry.state] : new ModernPalette.Resolved(null, null, false);
        NBTTagCompound tile = merge(entry.nbt, r.tile);
        return new TemplateBlock(name, new LinkedHashMap<String, String>(props), r.state, tile, r.water);
    }

    /** Today's gravity processor, where the placement follows the ground: the block's height above it. */
    private static BlockPos onSurface(BlockPos pos, BlockPos local, TemplatePlacement placement) {
        if (placement.surface == null) {
            return pos;
        }
        return new BlockPos(pos.getX(), placement.surface.applyAsInt(pos.getX(), pos.getZ()) + placement.surfaceOffset + local.getY(), pos.getZ());
    }

    /**
     * @param from where the block lands before any gravity: what the processors see, as today's do
     * @param pos where it goes
     */
    private boolean place(World world, BlockPos from, BlockPos pos, TemplateBlock block, TemplatePlacement placement) {
        if (placement.ignoreAir && "minecraft:air".equals(block.name())) {
            return false;
        }
        Random random = new Random(positionSeed(from) ^ placement.seed);
        if (placement.integrity < 1.0F && random.nextFloat() > placement.integrity) {
            return false;
        }
        TemplateBlock b = block;
        for (TemplateProcessor processor : placement.processors) {
            b = processor.process(world, from, b, random);
            if (b == null) {
                return false;
            }
        }
        IBlockState base = b.state();
        if (base == null) {
            return false;
        }
        IBlockState state = base.withMirror(placement.mirror).withRotation(placement.rotation);
        NBTTagCompound tile = b.tile();

        TileEntity old = world.getTileEntity(pos);
        if (old != null) {
            if (old instanceof IInventory) {
                ((IInventory) old).clear();
            }
            world.setBlockState(pos, Blocks.BARRIER.getDefaultState(), 4 | Waterlogging.REMOVE_WATER);
        }

        Block kind = state.getBlock();
        boolean required = kind instanceof Waterloggable && ((Waterloggable) kind).waterNeed(state) == Waterloggable.Need.REQUIRED;
        boolean wet = b.water() || required || (placement.keepWater && old == null && Waterlogging.hasWaterSource(world, pos));
        boolean done = state.getMaterial().isLiquid() || !wet
                ? Waterlogging.placeDry(world, pos, state, placement.flags)
                : Waterlogging.placeWithWater(world, pos, state, placement.flags) || world.getBlockState(pos) == state;
        IBlockState now = world.getBlockState(pos);
        if (now.getBlock() != kind) {
            // It needed support that is not there yet (the next chunk's wall): written in as it stands.
            writeRaw(world, pos, state, now);
            done = true;
        }
        if (!done) {
            return false;
        }
        if (tile != null) {
            TileEntity te = world.getTileEntity(pos);
            if (te != null) {
                tile.setInteger("x", pos.getX());
                tile.setInteger("y", pos.getY());
                tile.setInteger("z", pos.getZ());
                if (tile.hasKey("LootTable", Constants.NBT.TAG_STRING)) {
                    tile.setString("LootTable", ModernPalette.lootTable(tile.getString("LootTable")));
                }
                fixItems(tile);
                te.readFromNBT(tile);
                te.mirror(placement.mirror);
                te.rotate(placement.rotation);
                te.markDirty();
            }
        }
        return true;
    }

    private static void writeRaw(World world, BlockPos pos, IBlockState state, IBlockState was) {
        Chunk chunk = world.getChunk(pos);
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        int section = pos.getY() >> 4;
        if (section < 0 || section >= storage.length) {
            return;
        }
        if (storage[section] == Chunk.NULL_BLOCK_STORAGE) {
            storage[section] = new ExtendedBlockStorage(section << 4, world.provider.hasSkyLight());
        }
        storage[section].set(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state);
        chunk.markDirty();
        world.checkLight(pos);
        world.notifyBlockUpdate(pos, was, state, 2);
    }

    private void spawn(World world, NBTTagCompound original, Vec3d pos, BlockPos blockPos, TemplatePlacement placement) {
        NBTTagCompound nbt = original.copy();
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagDouble(pos.x));
        list.appendTag(new NBTTagDouble(pos.y));
        list.appendTag(new NBTTagDouble(pos.z));
        nbt.setTag("Pos", list);
        nbt.removeTag("UUIDMost");
        nbt.removeTag("UUIDLeast");
        nbt.removeTag("UUID");
        if (nbt.hasKey("TileX")) {
            nbt.setInteger("TileX", blockPos.getX());
            nbt.setInteger("TileY", blockPos.getY());
            nbt.setInteger("TileZ", blockPos.getZ());
        }
        fixItems(nbt);
        if (nbt.hasKey("id", Constants.NBT.TAG_STRING)) {
            nbt.setString("id", ModernPalette.entity(nbt.getString("id")));
        }
        Entity entity;
        try {
            entity = EntityList.createEntityFromNBT(nbt, world);
        } catch (RuntimeException e) {
            BackportCore.LOGGER.warn("Structure {} has an entity 1.12.2 could not read: {}", this.id, nbt.getString("id"), e);
            return;
        }
        if (entity == null) {
            return;
        }
        float yaw = entity.getMirroredYaw(placement.mirror);
        yaw = yaw + (entity.rotationYaw - entity.getRotatedYaw(placement.rotation));
        entity.setLocationAndAngles(pos.x, pos.y, pos.z, yaw, entity.rotationPitch);
        if (placement.finalizeEntities && entity instanceof EntityLiving) {
            ((EntityLiving) entity).onInitialSpawn(world.getDifficultyForLocation(new BlockPos(entity)), null);
            // onInitialSpawn may turn the mob; the structure's facing wins, as today's game keeps it.
            entity.setLocationAndAngles(pos.x, pos.y, pos.z, yaw, entity.rotationPitch);
        }
        world.spawnEntity(entity);
    }

    /**
     * Item stacks in tile and entity data: 1.20.5 renamed {@code Count} to {@code count}. Stacks of
     * items 1.12.2 does not have come out empty, as any unknown item does.
     */
    private static void fixItems(NBTTagCompound tag) {
        if (tag.hasKey("Items", Constants.NBT.TAG_LIST)) {
            NBTTagList items = tag.getTagList("Items", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < items.tagCount(); i++) {
                fixStack(items.getCompoundTagAt(i));
            }
        }
        if (tag.hasKey("Item", Constants.NBT.TAG_COMPOUND)) {
            fixStack(tag.getCompoundTag("Item"));
        }
    }

    private static void fixStack(NBTTagCompound stack) {
        if (!stack.hasKey("Count") && stack.hasKey("count")) {
            stack.setByte("Count", (byte) stack.getInteger("count"));
        }
        if (!stack.hasKey("Slot") && stack.hasKey("slot")) {
            stack.setByte("Slot", (byte) stack.getInteger("slot"));
        }
    }

    @Nullable
    private static NBTTagCompound merge(@Nullable NBTTagCompound file, @Nullable NBTTagCompound extra) {
        if (file == null && extra == null) {
            return null;
        }
        NBTTagCompound out = file == null ? new NBTTagCompound() : file.copy();
        if (extra != null) {
            out.merge(extra);
        }
        return out;
    }

    private ModernPalette.Resolved[][] resolved() {
        ModernPalette.Resolved[][] known = this.resolved;
        if (known != null) {
            return known;
        }
        int count = Math.max(1, this.names.size());
        ModernPalette.Resolved[][] out = new ModernPalette.Resolved[count][];
        for (int p = 0; p < count; p++) {
            String[] palNames = p < this.names.size() ? this.names.get(p) : new String[0];
            out[p] = new ModernPalette.Resolved[palNames.length];
            for (int i = 0; i < palNames.length; i++) {
                Map<String, String> palProps = this.props.get(p).get(i);
                out[p][i] = this.dataVersion < FLATTENING && this.dataVersion > 0
                        ? legacy(palNames[i], palProps) : ModernPalette.resolve(palNames[i], palProps, this.dataVersion);
            }
        }
        this.resolved = out;
        return out;
    }

    /** A file 1.12.2 (or earlier) saved: its names are this game's own. */
    private static ModernPalette.Resolved legacy(String name, Map<String, String> props) {
        Block block = Block.getBlockFromName(name);
        if (block == null) {
            return new ModernPalette.Resolved(null, null, false);
        }
        IBlockState state = block.getDefaultState();
        for (Map.Entry<String, String> e : props.entrySet()) {
            state = BlockStates.withOrSame(state, e.getKey(), e.getValue());
        }
        return new ModernPalette.Resolved(state, null, false);
    }

    private static boolean isAir(String name) {
        return "minecraft:air".equals(name) || "minecraft:cave_air".equals(name) || "minecraft:void_air".equals(name);
    }

    /**
     * Today's {@code Mth.getSeed}: a block position's own random seed. Inlined: 1.12.2's
     * {@code MathHelper.getPositionRandom} is client-only.
     */
    static long positionSeed(BlockPos pos) {
        long l = (long) (pos.getX() * 3129871) ^ (long) pos.getZ() * 116129781L ^ (long) pos.getY();
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }

    /** Drops every loaded template, so a resource reload or a test reads files again. */
    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }
}

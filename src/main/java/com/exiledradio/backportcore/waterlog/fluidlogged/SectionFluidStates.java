package com.exiledradio.backportcore.waterlog.fluidlogged;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.terrain.WorldHeights;

import git.jbredwards.fluidlogged_api.api.capability.IFluidStateCapability;
import git.jbredwards.fluidlogged_api.api.capability.IFluidStateContainer;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.mod.FluidloggedAPI;
import git.jbredwards.fluidlogged_api.mod.asm.iface.IHardcodedCapability;
import git.jbredwards.fluidlogged_api.mod.common.capability.FluidStateCapabilityVanilla;
import git.jbredwards.fluidlogged_api.mod.common.message.SMessageSyncFluidStates;
import it.unimi.dsi.fastutil.chars.AbstractChar2ObjectMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluidlogged API's water outside y 0-255: a chunk's kept fluid states in a Caves &amp; Cliffs Backport extended
 * world (y -64 to 319), one store per 16-block section.
 *
 * <p>Fluidlogged API 3.3.3 keeps a chunk's fluid states in one {@code FluidStateCapabilityVanilla}, which packs a
 * position into a char with y in the high eight bits, so it has no room for y below 0 or above 255. Every reader and
 * writer asks the chunk's store for {@code getContainer(y)} first, the way Fluidlogged API's own Cubic Chunks
 * support gives each cube its own store. This store answers with the section's own container: a {@code
 * FluidStateCapabilityVanilla} whose y is the 4 bits within the section, at the section's signed base y (the same
 * packing as Fluidlogged API's Cubic Chunks cubes). Placing, reading, flowing, lighting and the per-position sync all
 * go through {@code getContainer}, so they work at every height.
 *
 * <p>Patch P20 ({@code asm.ExtendedFluidStatesTransformer}) calls {@link #install} at the end of {@code Chunk}'s
 * constructor, once the chunk knows its world. Fluidlogged API makes its store earlier in that constructor, before
 * the world is set, so it cannot tell an extended world from any other; P20 replaces that store, still empty, only in
 * an extended world. Every other world keeps Fluidlogged API's store.
 *
 * <p><b>Saving.</b> {@link #serializeNBT} writes Fluidlogged API's own format (version 2, a {@code data} list), with
 * each position as a whole block position (a long), which Fluidlogged API's store reads too: y 0-255 go in {@code
 * data}, so a world opened without extended height still has that water; the other heights go in {@value
 * #EXTENDED}, which Fluidlogged API leaves alone. Chunks saved by Fluidlogged API's store (char positions) load
 * here unchanged.
 *
 * <p><b>Sync.</b> When a player starts watching a chunk, Fluidlogged API sends one packet with its store's {@code
 * getContainer(0)}: here the section at y 0-15. {@link #watch} sends each other section that holds anything in
 * Fluidlogged API's own packet ({@code SMessageSyncFluidStates}, which already carries a section's y for Cubic
 * Chunks), so the client's store, also this one, takes them section by section.
 */
public final class SectionFluidStates implements IFluidStateCapability {

    /** The NBT list of positions outside y 0-255, beside Fluidlogged API's own {@code data}. */
    public static final String EXTENDED = "backportcore:extended";

    private static boolean warned;

    private final int chunkX;
    private final int chunkZ;
    /** Signed section y of {@code sections[0]}. */
    private final int minSection;
    /** Every section of the world's height, made up front so readers on other threads never see one being added. */
    private final Section[] sections;

    /** One section's store: Fluidlogged API's, with y as the 4 bits within the section. */
    public static final class Section extends FluidStateCapabilityVanilla {

        private final int baseY;

        Section(int chunkX, int chunkZ, int sectionY) {
            super(chunkX, chunkZ);
            this.baseY = sectionY << 4;
        }

        /** @return the lowest block y of this section */
        public int baseY() {
            return this.baseY;
        }

        @Override
        public int serializeY(int y) {
            return (y & 15) << 8;
        }

        @Override
        public int deserializeY(char serializedPos) {
            return this.baseY | serializedPos >> 8 & 15;
        }
    }

    /**
     * @param minY the world's lowest block y
     * @param maxY one above the world's highest block y
     */
    public SectionFluidStates(int chunkX, int chunkZ, int minY, int maxY) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minSection = minY >> 4;
        int count = Math.max(1, ((maxY + 15) >> 4) - this.minSection);
        this.sections = new Section[count];
        for (int i = 0; i < count; i++) {
            this.sections[i] = new Section(chunkX, chunkZ, this.minSection + i);
        }
    }

    /**
     * Patch P20, at the end of every {@code Chunk(World, int, int)}: gives a chunk of a Caves &amp; Cliffs Backport
     * extended world this store in place of Fluidlogged API's (made a moment before, still empty). Any other chunk,
     * and a chunk whose store is not Fluidlogged API's plain one (Cubic Chunks), is left as it is.
     */
    public static void install(Chunk chunk) {
        World world = chunk.getWorld();
        if (world == null || !WorldHeights.isCavesAndCliffsExtended(world)) {
            return;
        }
        IHardcodedCapability holder = (IHardcodedCapability) (Object) chunk;
        IFluidStateCapability current = holder.getFluidStateCapability();
        if (current == null || current.getClass() != FluidStateCapabilityVanilla.class) {
            return;
        }
        if (!((FluidStateCapabilityVanilla) current).getSerializedPositions().isEmpty()) {
            // Nothing writes before the constructor ends; if something did, keep its water.
            if (!warned) {
                warned = true;
                BackportCore.LOGGER.warn("A chunk had Fluidlogged API water before it was made; its water stays in "
                        + "Fluidlogged API's store (y 0-255 only).");
            }
            return;
        }
        holder.setFluidStateCapability(new SectionFluidStates(chunk.x, chunk.z, WorldHeights.minY(world),
                WorldHeights.maxYExclusive(world)));
    }

    /** Called once from {@code WaterRules.register}: the chunk-watch sync of the other sections. */
    static void register() {
        MinecraftForge.EVENT_BUS.register(new SectionFluidStates.Sync());
    }

    /** @return the section store holding block y, or an empty throwaway store outside the world's height */
    @Override
    public IFluidStateContainer getContainer(int y) {
        int i = (y >> 4) - this.minSection;
        if (i >= 0 && i < this.sections.length) {
            return this.sections[i];
        }
        return new Section(this.chunkX, this.chunkZ, y >> 4);
    }

    /** @return every section, bottom up */
    public Section[] sections() {
        return this.sections.clone();
    }

    /** @return how many positions hold a fluid state, in every section */
    public int size() {
        int n = 0;
        for (Section s : this.sections) {
            n += s.getSerializedPositions().size();
        }
        return n;
    }

    @Override
    public NBTBase serializeNBT() {
        NBTTagList inside = new NBTTagList();
        NBTTagList outside = new NBTTagList();
        for (Section section : this.sections) {
            final NBTTagList into = section.baseY() >= 0 && section.baseY() < 256 ? inside : outside;
            section.forEach((pos, fluidState) -> {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setLong("pos", section.deserializePos(pos).toLong());
                tag.setString("id", String.valueOf(fluidState.getBlock().getRegistryName()));
                if (fluidState.getMetadata() != 0) {
                    tag.setInteger("meta", fluidState.getMetadata());
                }
                into.appendTag(tag);
            });
        }
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("data", inside);
        if (!outside.isEmpty()) {
            nbt.setTag(EXTENDED, outside);
        }
        nbt.setInteger("version", 2);
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTBase nbtIn) {
        if (nbtIn instanceof NBTTagCompound) {
            NBTTagCompound nbt = (NBTTagCompound) nbtIn;
            int version = nbt.getInteger("version");
            if (version != 1 && version != 2) {
                BackportCore.LOGGER.warn("Chunk [{}, {}]: unknown Fluidlogged API data version {}; its water is not read.",
                        this.chunkX, this.chunkZ, version);
                return;
            }
            read(nbt.getTagList("data", Constants.NBT.TAG_COMPOUND));
            read(nbt.getTagList(EXTENDED, Constants.NBT.TAG_COMPOUND));
        } else if (nbtIn instanceof NBTTagList) {
            // Fluidlogged API's oldest format: a list of {id, pos (long)}.
            for (NBTBase base : (NBTTagList) nbtIn) {
                if (base instanceof NBTTagCompound) {
                    NBTTagCompound tag = (NBTTagCompound) base;
                    if (tag.hasKey("id", Constants.NBT.TAG_STRING) && tag.hasKey("pos", Constants.NBT.TAG_LONG)) {
                        Block block = Block.getBlockFromName(tag.getString("id"));
                        if (block != null) {
                            set(BlockPos.fromLong(tag.getLong("pos")), FluidState.of(block));
                        }
                    }
                }
            }
        }
    }

    private void read(NBTTagList list) {
        for (NBTBase base : list) {
            NBTTagCompound tag = (NBTTagCompound) base;
            if (!tag.hasKey("id", Constants.NBT.TAG_STRING) || !tag.hasKey("pos", Constants.NBT.TAG_ANY_NUMERIC)) {
                continue;
            }
            Block block = Block.getBlockFromName(tag.getString("id"));
            if (block == null) {
                continue;
            }
            NBTPrimitive posTag = (NBTPrimitive) tag.getTag("pos");
            BlockPos pos;
            if (posTag instanceof NBTTagLong) {
                pos = BlockPos.fromLong(posTag.getLong());
            } else {
                // Fluidlogged API's own store: x in bits 0-3, z in 4-7, y (0-255) in 8-15.
                char c = (char) posTag.getInt();
                pos = new BlockPos(this.chunkX << 4 | c & 15, c >> 8, this.chunkZ << 4 | c >> 4 & 15);
            }
            @SuppressWarnings("deprecation")
            FluidState state = FluidState.of(block.getStateFromMeta(tag.getInteger("meta")));
            set(pos, state);
        }
    }

    private void set(BlockPos pos, FluidState state) {
        getContainer(pos.getY()).setFluidState(pos, state);
    }

    /**
     * @return Fluidlogged API's chunk sync packet for every section but the one at y 0-15 (which Fluidlogged API
     * sends itself) that holds anything
     */
    public List<SMessageSyncFluidStates> syncMessages() {
        List<SMessageSyncFluidStates> out = new ArrayList<SMessageSyncFluidStates>();
        for (Section section : this.sections) {
            if (section.baseY() == 0 || section.getSerializedPositions().isEmpty()) {
                continue;
            }
            out.add(message(section));
        }
        return out;
    }

    /** Fluidlogged API's packet for one section; its constructor reads only section 0, so the fields are set here. */
    @SuppressWarnings("unchecked")
    static SMessageSyncFluidStates message(Section section) {
        SMessageSyncFluidStates message = new SMessageSyncFluidStates();
        final List<Char2ObjectMap.Entry<FluidState>> data = new ArrayList<Char2ObjectMap.Entry<FluidState>>();
        section.forEach((pos, fluidState) -> data.add(new AbstractChar2ObjectMap.BasicEntry<FluidState>(pos, fluidState)));
        message.data = data.toArray(new Char2ObjectMap.Entry[0]);
        message.chunkX = section.deserializeX((char) 0) >> 4;
        message.chunkZ = section.deserializeZ((char) 0) >> 4;
        message.indexY = section.baseY();
        message.layersLength = section.layers.length;
        message.lowestYPos = section.lowestYPos;
        message.isValid = true;
        return message;
    }

    /** Sends the sections Fluidlogged API's own chunk sync leaves out. */
    public static final class Sync {

        @SubscribeEvent
        public void watch(ChunkWatchEvent.Watch event) {
            Chunk chunk = event.getChunkInstance();
            if (chunk == null) {
                return;
            }
            IFluidStateCapability cap = ((IHardcodedCapability) (Object) chunk).getFluidStateCapability();
            if (!(cap instanceof SectionFluidStates)) {
                return;
            }
            EntityPlayerMP player = event.getPlayer();
            for (SMessageSyncFluidStates message : ((SectionFluidStates) cap).syncMessages()) {
                FluidloggedAPI.WRAPPER.sendTo(message, player);
            }
        }
    }
}

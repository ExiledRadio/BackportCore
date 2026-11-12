package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.selftest.deepwater.DeepWater;
import com.exiledradio.backportcore.waterlog.Waterlogging;
import com.exiledradio.backportcore.waterlog.fluidlogged.SectionFluidStates;

import git.jbredwards.fluidlogged_api.api.capability.IFluidStateCapability;
import git.jbredwards.fluidlogged_api.api.capability.IFluidStateContainer;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.mod.asm.iface.IHardcodedCapability;
import git.jbredwards.fluidlogged_api.mod.common.capability.FluidStateCapabilityVanilla;
import git.jbredwards.fluidlogged_api.mod.common.capability.util.FluidStateLayer;
import git.jbredwards.fluidlogged_api.mod.common.message.SMessageSyncFluidStates;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fluids.FluidRegistry;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Patch P20 and {@code waterlog.fluidlogged.SectionFluidStates}: Fluidlogged API water outside y 0-255.
 *
 * <p>The store's own checks (positions, saving, Fluidlogged API reading its save, the sync packets) run in every
 * world. The rest needs a Caves &amp; Cliffs Backport extended overworld ({@code gradlew runServer -Pselftest
 * -PwithMods=<its dev jar> -PselftestOnly=deepwater}, level-type cavesandcliffs): waterlogged blocks at y -60 and
 * y 300, and, over two runs, that they survive a save and reload ({@value #MARKER} carries them from one run to the
 * next).
 */
final class DeepWaterTests {

    static final String MARKER = "selftest-deepwater.txt";

    private static final FluidState WATER = FluidState.of(FluidRegistry.WATER);

    private DeepWaterTests() {
    }

    static void schedule() {
        then(1, "deep water: the store", world -> store());
        then(1, "deep water: in the world", world -> world(world));
    }

    private static void store() {
        // A chunk at negative coordinates, so every packing is checked with the sign bits set.
        int cx = -2;
        int cz = -3;
        SectionFluidStates store = new SectionFluidStates(cx, cz, -64, 320);
        check("P20 store: 24 sections for y -64..319", store.sections().length == 24, store.sections().length);
        BlockPos low = new BlockPos(cx * 16 + 3, -60, cz * 16 + 5);
        BlockPos mid = new BlockPos(cx * 16 + 15, 10, cz * 16);
        BlockPos high = new BlockPos(cx * 16, 300, cz * 16 + 15);
        BlockPos top = new BlockPos(cx * 16 + 7, 319, cz * 16 + 7);
        for (BlockPos p : new BlockPos[] {low, mid, high, top}) {
            store.getContainer(p.getY()).setFluidState(p, WATER);
        }
        for (BlockPos p : new BlockPos[] {low, mid, high, top}) {
            IFluidStateContainer c = store.getContainer(p.getY());
            check("P20 store: water kept at y " + p.getY(), c.getFluidState(p, FluidState.EMPTY) == WATER);
            char key = c.serializePos(p);
            check("P20 store: y " + p.getY() + " packs and unpacks", p.equals(c.deserializePos(key)), c.deserializePos(key));
        }
        check("P20 store: y -59 (same section) is dry", store.getContainer(-59).getFluidState(low.up(), FluidState.EMPTY).isEmpty());
        check("P20 store: y 44 (vanilla packing's alias of y 300) is dry",
                store.getContainer(44).getFluidState(new BlockPos(high.getX(), 44, high.getZ()), FluidState.EMPTY).isEmpty());
        check("P20 store: outside the height, an empty throwaway store", store.getContainer(-200).getSerializedPositions().isEmpty());
        check("P20 store: 4 positions", store.size() == 4, store.size());

        // Saving: Fluidlogged API's format, y 0-255 in its own list.
        NBTTagCompound nbt = (NBTTagCompound) store.serializeNBT();
        check("P20 save: y 0-255 in Fluidlogged API's data list", nbt.getTagList("data", 10).tagCount() == 1, nbt);
        check("P20 save: the other heights beside it", nbt.getTagList(SectionFluidStates.EXTENDED, 10).tagCount() == 3, nbt);
        SectionFluidStates read = new SectionFluidStates(cx, cz, -64, 320);
        read.deserializeNBT(nbt.copy());
        boolean same = read.size() == 4;
        for (BlockPos p : new BlockPos[] {low, mid, high, top}) {
            same &= read.getContainer(p.getY()).getFluidState(p, FluidState.EMPTY) == WATER;
        }
        check("P20 save: read back, every position", same, read.size());
        FluidStateCapabilityVanilla plain = new FluidStateCapabilityVanilla(cx, cz);
        plain.deserializeNBT(nbt.copy());
        check("P20 save: Fluidlogged API's own store reads y 0-255 of it (a world opened without extended height)",
                plain.getSerializedPositions().size() == 1 && plain.getFluidState(mid, FluidState.EMPTY) == WATER,
                plain.getSerializedPositions().size());

        // A chunk Fluidlogged API saved before the world had extended height (char positions).
        FluidStateCapabilityVanilla old = new FluidStateCapabilityVanilla(cx, cz);
        BlockPos oldPos = new BlockPos(cx * 16 + 9, 200, cz * 16 + 4);
        old.setFluidState(oldPos, WATER);
        old.setFluidState(mid, WATER);
        NBTBase oldNbt = old.serializeNBT();
        SectionFluidStates upgraded = new SectionFluidStates(cx, cz, -64, 320);
        upgraded.deserializeNBT(oldNbt);
        check("P20 save: Fluidlogged API's own save loads into sections",
                upgraded.size() == 2 && upgraded.getContainer(200).getFluidState(oldPos, FluidState.EMPTY) == WATER
                        && upgraded.getContainer(10).getFluidState(mid, FluidState.EMPTY) == WATER, upgraded.size());

        // Sync: each section but y 0-15 in Fluidlogged API's own packet, written, read and applied as its client does.
        List<SMessageSyncFluidStates> messages = store.syncMessages();
        check("P20 sync: one packet per other non-empty section (y -64, 288, 304)", messages.size() == 3, messages.size());
        SectionFluidStates client = new SectionFluidStates(cx, cz, -64, 320);
        client.getContainer(mid.getY()).setFluidState(mid, WATER); // Fluidlogged API's own packet's part
        boolean coords = true;
        for (SMessageSyncFluidStates sent : messages) {
            ByteBuf buf = Unpooled.buffer();
            sent.toBytes(buf);
            SMessageSyncFluidStates got = new SMessageSyncFluidStates();
            got.fromBytes(buf);
            coords &= got.chunkX == cx && got.chunkZ == cz && got.isValid;
            IFluidStateContainer container = client.getContainer(got.indexY);
            container.clearFluidStates();
            if (container instanceof FluidStateCapabilityVanilla) {
                ((FluidStateCapabilityVanilla) container).layers = new FluidStateLayer[got.layersLength];
                ((FluidStateCapabilityVanilla) container).lowestYPos = (char) got.lowestYPos;
            }
            for (Char2ObjectMap.Entry<FluidState> e : got.data) {
                container.setFluidState(e.getCharKey(), e.getValue());
            }
        }
        check("P20 sync: packets carry the chunk", coords);
        boolean synced = client.size() == 4;
        for (BlockPos p : new BlockPos[] {low, mid, high, top}) {
            synced &= client.getContainer(p.getY()).getFluidState(p, FluidState.EMPTY) == WATER;
        }
        check("P20 sync: the client's store has every position", synced, client.size());
    }

    private static void world(WorldServer world) throws Exception {
        String state = Asm.state("P20");
        if (!DeepWater.extended(world)) {
            check("P20 outside an extended world: Fluidlogged API's own store",
                    store(world.getChunk(world.getSpawnPoint())) instanceof FluidStateCapabilityVanilla,
                    store(world.getChunk(world.getSpawnPoint())));
            note("deep water world checks skipped: not a Caves & Cliffs extended world (P20 " + state + ")");
            return;
        }
        check("P20 applied in an extended world", "applied".equals(state), state);
        check("P20: an overworld chunk has the section store", store(world.getChunk(world.getSpawnPoint())) instanceof SectionFluidStates,
                store(world.getChunk(world.getSpawnPoint())));
        WorldServer nether = world.getMinecraftServer().getWorld(-1);
        if (nether != null) {
            Object cap = store(nether.getChunk(0, 0));
            check("P20: a Nether chunk keeps Fluidlogged API's store", cap instanceof FluidStateCapabilityVanilla
                    && !(cap instanceof SectionFluidStates), cap);
        }
        Block block = DeepWater.block();
        File marker = new File(MARKER);
        if (marker.isFile()) {
            List<String> problems = DeepWater.verify(world, block);
            check("deep water after a save and reload: y -60 and y 300 still waterlogged", problems.isEmpty(), problems);
            Files.delete(marker.toPath());
        }
        List<String> placed = DeepWater.place(world, block);
        check("deep water: waterlogged " + block.getRegistryName() + " at y -60 and y 300", placed.isEmpty(), placed);

        // The helpers at both heights, beside the placed blocks.
        for (int y : DeepWater.HEIGHTS) {
            BlockPos pos = DeepWater.positions(world).get(y < 0 ? 0 : 1);
            check("deep water y " + y + ": setWater false drains", Waterlogging.setWater(world, pos, false, 3)
                    && !Waterlogging.hasWater(world, pos));
            check("deep water y " + y + ": setWater true fills", Waterlogging.setWater(world, pos, true, 3)
                    && Waterlogging.hasWaterSource(world, pos));
            BlockPos glass = pos.up();
            check("deep water y " + y + ": glass above stays dry", !Waterlogging.hasWater(world, glass)
                    && world.getBlockState(glass).getBlock() == Blocks.GLASS);
        }

        // The chunk's own save and load (Forge's capability NBT), into a fresh chunk of the same world.
        for (BlockPos pos : DeepWater.positions(world)) {
            Chunk chunk = world.getChunk(pos);
            NBTTagCompound caps = chunk.getCapabilities() == null ? null : chunk.getCapabilities().serializeNBT();
            Chunk fresh = new Chunk(world, chunk.x, chunk.z);
            boolean ok = caps != null && fresh.getCapabilities() != null;
            if (ok) {
                fresh.getCapabilities().deserializeNBT(caps);
                IFluidStateCapability cap = store(fresh);
                ok = cap instanceof SectionFluidStates
                        && cap.getContainer(pos.getY()).getFluidState(pos, FluidState.EMPTY).getFluid() == FluidRegistry.WATER;
            }
            check("deep water y " + pos.getY() + ": the chunk's capability NBT reloads into a new chunk", ok, caps);
            SectionFluidStates sections = (SectionFluidStates) store(chunk);
            boolean sent = false;
            for (SMessageSyncFluidStates m : sections.syncMessages()) {
                sent |= m.indexY == (pos.getY() & ~15);
            }
            check("deep water y " + pos.getY() + ": its section is in the chunk-watch sync", sent);
        }
        Files.write(marker.toPath(), "placed".getBytes(StandardCharsets.UTF_8));
    }

    private static IFluidStateCapability store(Chunk chunk) {
        return ((IHardcodedCapability) (Object) chunk).getFluidStateCapability();
    }

    private static void note(String message) {
        com.exiledradio.backportcore.BackportCore.LOGGER.info("SELFTEST NOTE {}", message);
    }
}

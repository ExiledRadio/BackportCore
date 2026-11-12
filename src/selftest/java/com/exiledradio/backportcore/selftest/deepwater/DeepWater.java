package com.exiledradio.backportcore.selftest.deepwater;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.terrain.WorldHeights;
import com.exiledradio.backportcore.waterlog.Waterloggable;
import com.exiledradio.backportcore.waterlog.Waterlogging;
import com.exiledradio.backportcore.waterlog.fluidlogged.SectionFluidStates;

import git.jbredwards.fluidlogged_api.mod.asm.iface.IHardcodedCapability;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Waterlogged blocks at y -60 and y 300 of a Caves &amp; Cliffs Backport extended world, across a save and reload.
 *
 * <p>{@link #place} builds a glass cell at each height (beside the spawn, in a spawn chunk) and puts a waterlogged
 * {@link Waterloggable} block in it; {@link #verify} checks the block and its water are there. Core's own self-test
 * ({@code selftest.DeepWaterTests}) uses both. On its own, as the jar {@code gradlew deepWaterJar -Pselftest} builds,
 * it is a mod for another mod's dev server: with a file {@value #TRIGGER} in the server's folder saying {@code place}
 * it places the blocks, rewrites the file to {@code check} and stops the server (which saves the world); started
 * again, it checks them, writes {@value #RESULT} and stops the server. Without the file it does nothing.
 */
@Mod(modid = DeepWater.MOD_ID, name = "Backport Core deep water check", version = "1",
        dependencies = "required-after:backportcore", acceptableRemoteVersions = "*")
public final class DeepWater {

    static final String MOD_ID = "backportcore_deepwater";
    public static final String TRIGGER = "backportcore-deepwater.txt";
    public static final String RESULT = "backportcore-deepwater-result.txt";
    public static final int[] HEIGHTS = {-60, 300};

    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) {
        if (FMLCommonHandler.instance().getSide().isServer() && new File(TRIGGER).isFile()) {
            MinecraftForge.EVENT_BUS.register(new Standalone());
        }
    }

    /** @return the positions this check uses in the world: beside the spawn, at {@link #HEIGHTS} */
    public static List<BlockPos> positions(World world) {
        BlockPos spawn = world.getSpawnPoint();
        List<BlockPos> out = new ArrayList<BlockPos>();
        for (int y : HEIGHTS) {
            out.add(new BlockPos(spawn.getX() + 3, y, spawn.getZ() + 3));
        }
        return out;
    }

    /**
     * @return the block the check places: Core's self-test block when it is there, else a {@link Waterloggable}
     * block whose default state can hold water, stairs and slabs first (they need no support)
     */
    public static Block block() {
        Block test = Block.REGISTRY.getObject(new ResourceLocation("backportcore_selftest", "optional"));
        if (test != Blocks.AIR) {
            return test;
        }
        Block any = null;
        Block stable = null;
        for (Block block : Block.REGISTRY) {
            if (!(block instanceof Waterloggable)
                    || ((Waterloggable) block).waterNeed(block.getDefaultState()) != Waterloggable.Need.OPTIONAL) {
                continue;
            }
            String path = block.getRegistryName().getPath();
            if (stable == null && (path.endsWith("_stairs") || path.endsWith("_slab"))) {
                stable = block;
            }
            if (any == null) {
                any = block;
            }
        }
        return stable != null ? stable : any;
    }

    /** Builds the glass cells and places the waterlogged blocks. @return what went wrong, empty when nothing did */
    public static List<String> place(World world, Block block) {
        List<String> problems = new ArrayList<String>();
        if (block == null) {
            problems.add("no Waterloggable block is registered");
            return problems;
        }
        for (BlockPos pos : positions(world)) {
            for (BlockPos p : BlockPos.getAllInBoxMutable(pos.add(-1, -1, -1), pos.add(1, 1, 1))) {
                world.setBlockState(p.toImmutable(), Blocks.GLASS.getDefaultState(), 2);
            }
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
            IBlockState state = block.getDefaultState();
            if (!Waterlogging.placeWithWater(world, pos, state, 3)) {
                problems.add("placeWithWater at y " + pos.getY() + " returned false");
            }
        }
        problems.addAll(verify(world, block));
        return problems;
    }

    /** @return what is missing at the check's positions, empty when every block holds its water */
    public static List<String> verify(World world, Block block) {
        List<String> problems = new ArrayList<String>();
        for (BlockPos pos : positions(world)) {
            Chunk chunk = world.getChunk(pos);
            Object cap = ((IHardcodedCapability) (Object) chunk).getFluidStateCapability();
            if (!(cap instanceof SectionFluidStates)) {
                problems.add("chunk at y " + pos.getY() + " has " + (cap == null ? "no store" : cap.getClass().getName()));
            }
            IBlockState here = world.getBlockState(pos);
            if (here.getBlock() != block) {
                problems.add("y " + pos.getY() + " holds " + here + ", not " + block.getRegistryName());
            }
            if (!Waterlogging.hasWater(world, pos)) {
                problems.add("y " + pos.getY() + " has no water");
            } else if (!Waterlogging.hasWaterSource(world, pos)) {
                problems.add("y " + pos.getY() + " has water but no source");
            }
        }
        return problems;
    }

    /** @return true in a Caves &amp; Cliffs Backport extended world */
    public static boolean extended(World world) {
        return WorldHeights.isCavesAndCliffsExtended(world);
    }

    /** The trigger-file run on another mod's dev server. */
    static final class Standalone {

        private int ticks;
        private boolean done;

        @SubscribeEvent
        public void tick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || this.done || ++this.ticks < 20) {
                return;
            }
            this.done = true;
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            WorldServer world = server.getWorld(0);
            File trigger = new File(TRIGGER);
            String step;
            try {
                step = new String(Files.readAllBytes(trigger.toPath()), StandardCharsets.UTF_8).trim();
                Block block = block();
                List<String> problems = new ArrayList<String>();
                if (!extended(world)) {
                    problems.add("the overworld is not a Caves & Cliffs extended world");
                }
                String report;
                if ("place".equals(step)) {
                    problems.addAll(place(world, block));
                    Files.write(trigger.toPath(), "check".getBytes(StandardCharsets.UTF_8));
                    report = "PLACED " + block.getRegistryName() + " at " + positions(world) + " problems=" + problems;
                } else {
                    problems.addAll(verify(world, block));
                    Files.delete(trigger.toPath());
                    report = (problems.isEmpty() ? "PASS" : "FAIL") + " after reload: " + block.getRegistryName()
                            + " at " + positions(world) + " problems=" + problems;
                }
                BackportCore.LOGGER.info("DEEPWATER {}", report);
                Files.write(new File(RESULT).toPath(), Arrays.asList(report), StandardCharsets.UTF_8);
            } catch (Exception e) {
                BackportCore.LOGGER.error("DEEPWATER check threw", e);
            }
            server.initiateShutdown();
        }
    }
}

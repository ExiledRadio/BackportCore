package com.exiledradio.backportcore.waterlog;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;

/**
 * Reading and setting a position's water, for generation, tests and the blocks themselves.
 *
 * <p>"Water" means vanilla's water block (or a block of water material), or water Fluidlogged API keeps beside
 * a block (a waterlogged block). Fluidlogged API is optional: without it no block holds water, and every method
 * here answers as 1.12.2 would ({@link #isAvailable()} is false, {@link #hasWater} is true for water blocks only,
 * {@link #setWater} never adds any). Every method is safe on any block: one that cannot hold water just answers
 * false. Server thread, on loaded positions.
 */
public final class Waterlogging {

    /** Fluidlogged API's mod id. */
    public static final String FLUIDLOGGED_API = "fluidlogged_api";

    /**
     * Fluidlogged API's extra block flag: setting a block with it removes any water kept there, where
     * Fluidlogged API would otherwise keep it with a block that can hold it. Without Fluidlogged API it is
     * never passed on.
     */
    public static final int REMOVE_WATER = 32;

    private static volatile WaterBackend backend;

    private Waterlogging() {
    }

    /**
     * @return true when Fluidlogged API is installed, so blocks can hold water; false when every {@link
     * Waterloggable} block is as dry as before 1.13
     */
    public static boolean isAvailable() {
        return backend() != WaterBackend.NONE;
    }

    private static WaterBackend backend() {
        WaterBackend b = backend;
        if (b == null) {
            synchronized (Waterlogging.class) {
                b = backend;
                if (b == null) {
                    b = Loader.isModLoaded(FLUIDLOGGED_API) ? load("fluidlogged.FluidloggedWater") : WaterBackend.NONE;
                    backend = b;
                }
            }
        }
        return b;
    }

    /** Fluidlogged API's side is reached by name only, so this class loads without it. */
    private static WaterBackend load(String name) {
        try {
            return (WaterBackend) Class.forName(Waterlogging.class.getPackage().getName() + "." + name).newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Backport Core could not reach Fluidlogged API", e);
        }
    }

    /** @return true if the position holds water: a water block, or a block with water kept beside it */
    public static boolean hasWater(IBlockAccess world, BlockPos pos) {
        return backend().hasWater(world, pos);
    }

    /**
     * @return true if the position's water is a still source, as ocean water and the water of a
     * waterlogged block are
     */
    public static boolean hasWaterSource(IBlockAccess world, BlockPos pos) {
        return backend().hasWaterSource(world, pos);
    }

    /**
     * @return true if this state can hold water at the position (asks Fluidlogged API, configs included);
     * always false without Fluidlogged API
     */
    public static boolean canHoldWater(IBlockState state, World world, BlockPos pos) {
        return backend().canHoldWater(state, world, pos);
    }

    /**
     * Adds a water source to the block at the position, or removes its water.
     *
     * <p>With block flag 1 the new water is live at once, as a bucket's is: it flows into open space
     * beside it. Without it (generation) it stays still until something next to it changes, as
     * generated water does.
     *
     * @param flags block flags, as {@code World.setBlockState} takes them (2: tell clients, 1: update)
     * @return false if the block cannot hold water (adding; always, without Fluidlogged API), is a fluid itself
     * (removing), or the world refused the change (water vaporizes in the Nether)
     */
    public static boolean setWater(World world, BlockPos pos, boolean water, int flags) {
        return backend().setWater(world, pos, water, flags);
    }

    /**
     * Places a block and keeps the water that was there: kelp into the sea, a reef block over
     * seagrass, a sea pickle on the floor. Where the new block cannot hold water (or without Fluidlogged API), it
     * replaces the water as any block would.
     *
     * @return true if the block was placed
     */
    public static boolean placeKeepingWater(World world, BlockPos pos, IBlockState state, int flags) {
        return backend().placeKeepingWater(world, pos, state, flags);
    }

    /**
     * Places a block with a water source, whatever was there: a structure's waterlogged stairs, kelp
     * planted in a pocket of air.
     *
     * @return true if the block was placed with its water; false if it was not placed, or was placed
     * dry because it cannot hold water here (always dry without Fluidlogged API)
     */
    public static boolean placeWithWater(World world, BlockPos pos, IBlockState state, int flags) {
        return backend().placeWithWater(world, pos, state, flags);
    }

    /**
     * Places a block with no water, whatever was there.
     *
     * @return true if the block was placed
     */
    public static boolean placeDry(World world, BlockPos pos, IBlockState state, int flags) {
        return backend().placeDry(world, pos, state, flags);
    }

    /**
     * For terrain generation that fills a {@link ChunkPrimer} (before decoration): gives the block at
     * these chunk-local coordinates a water source, or takes it away. The block itself is set on the
     * primer as usual. Fluidlogged API moves the water into the chunk with the blocks; without it, nothing.
     */
    public static void setWater(ChunkPrimer primer, int x, int y, int z, boolean water) {
        backend().setWater(primer, x, y, z, water);
    }

    /**
     * Backport Core's own pre-init calls this; nothing else should. With Fluidlogged API: the family's water
     * rules, source forming's checks and the stored-property conversions. Without it: older worlds' Fluidlogged
     * API water is carried through every save untouched ({@link KeptWater}), for when it is installed again.
     */
    public static void init() {
        if (isAvailable()) {
            try {
                Class.forName(Waterlogging.class.getPackage().getName() + ".fluidlogged.WaterRules")
                        .getMethod("register").invoke(null);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Backport Core could not reach Fluidlogged API", e);
            }
            WaterlogMigration.listen();
        } else {
            BackportCore.LOGGER.info("Fluidlogged API is not installed: the backports' blocks will not hold water.");
            MinecraftForge.EVENT_BUS.register(new KeptWater());
        }
    }

    /**
     * One-time move of a released block's stored {@code waterlogged} property into Fluidlogged API
     * water, for a block that shipped its own waterlogging before this library had any.
     *
     * <p>Keep the property in the block's state, and its bit in the metadata, while older worlds may
     * still hold it. Each chunk is checked once, when it loads, and every state of {@code block} with
     * the property true becomes the same state with it false plus a water source. New placements
     * should never set it. Call in pre-init or init. Without Fluidlogged API nothing is converted (and
     * nothing is marked checked), so the property waits for it.
     */
    public static void migrateStoredProperty(Block block, IProperty<Boolean> waterlogged) {
        WaterlogMigration.register(block, waterlogged);
    }
}

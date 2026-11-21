package com.exiledradio.backportcore.worldgen;

import com.exiledradio.backportcore.waterlog.Waterlogging;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Patch P37's hooks (asm/GroundSpawnTransformer): 1.13's on-ground spawn spot holds no fluid.
 *
 * <p>1.12.2 asks only that the two blocks a mob stands in are not liquid material. A block that holds water (kelp,
 * seagrass, any block Fluidlogged API waterlogs) is not, so mobs that spawn on the ground (at chunk generation every
 * animal, Lycanites' included) were placed on the sea floor. Current Java's {@code NaturalSpawner.isValidEmptySpawnBlock}
 * also asks for an empty fluid state.
 */
public final class GroundSpawnHooks {

    private GroundSpawnHooks() {
    }

    /** The feet block: vanilla's answer, and no water held there. */
    public static boolean feet(boolean valid, World world, BlockPos pos) {
        return valid && !Waterlogging.hasWater(world, pos);
    }

    /** The head block (the one above the feet): vanilla's answer, and no water held there. */
    public static boolean head(boolean valid, World world, BlockPos pos) {
        return valid && !Waterlogging.hasWater(world, pos.up());
    }
}

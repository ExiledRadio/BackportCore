package com.exiledradio.backportcore.structure;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Changes or drops a template's block as it is placed: today's structure processors. Runs after the
 * integrity roll, once per block, only for blocks inside the placement's clip.
 */
@FunctionalInterface
public interface TemplateProcessor {

    /**
     * @param pos where the block goes in the world
     * @param block the block as it stands so far: today's name and properties, and the 1.12.2 state
     * @param random this block's own random (the same for the same position and seed every time)
     * @return the block to place, {@code block} itself to keep it, or null to leave the world as it is
     */
    @Nullable
    TemplateBlock process(World world, BlockPos pos, TemplateBlock block, Random random);
}

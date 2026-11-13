package com.exiledradio.backportcore.copper;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * One oxidation table for every backport's copper (1.17's set, 1.21's bulbs, doors, grates and chiseled copper,
 * 1.21.9's chests, bars, chains, lanterns and lightning rods, whichever mod registers them): what a block oxidizes
 * into, what scraping returns it to, its waxed and unwaxed forms, and current Java's aging rule, which counts every
 * registered copper block nearby.
 *
 * <pre>{@code
 * Oxidation.chain(copperBulb, exposedBulb, weatheredBulb, oxidizedBulb);   // unaffected first
 * Oxidation.waxed(copperBulb, waxedCopperBulb);                            // once per stage
 * // in the block's randomTick:
 * Oxidation.randomTick(world, pos, state, rand);
 * // an axe, honeycomb or lightning:
 * Block to = Oxidation.previous(block);  Oxidation.change(world, pos, state, to);
 * }</pre>
 * Register from the block registry event (or init). Every mod's handlers asking this table treat every mod's
 * copper alike: an axe scrapes Caves &amp; Cliffs' copper and Tricky Trials' the same way, and copper waits for the
 * least oxidized copper near it whichever mod it comes from.
 *
 * <h2>Aging (current Java, {@code ChangeOverTimeBlock})</h2>
 * On a random tick an unwaxed block that is not fully oxidized tries to age with chance 64/1125. It looks at every
 * other unwaxed copper block within a Manhattan distance of 4: if any is less oxidized it stops; otherwise, with
 * {@code a} as many at its own stage and {@code b} more oxidized, it ages with chance
 * {@code ((b + 1) / (a + b + 1))^2}, times 0.75 while unaffected.
 *
 * <p>States carry across by property (stairs keep facing, half and shape; bulbs stay lit); a door's two halves
 * change together.
 */
public final class Oxidation {

    /** 64/1125: the chance a random tick tries to age a block at all. */
    public static final float TRY_CHANCE = 0.05688889F;

    private static final Map<Block, Block> NEXT = new HashMap<Block, Block>();
    private static final Map<Block, Block> PREVIOUS = new HashMap<Block, Block>();
    private static final Map<Block, Block> WAXED = new HashMap<Block, Block>();
    private static final Map<Block, Block> UNWAXED = new HashMap<Block, Block>();
    /** Oxidation stage of every unwaxed copper block, 0-3. */
    private static final Map<Block, Integer> STAGE = new HashMap<Block, Integer>();

    private Oxidation() {
    }

    /** Declares one shape's stages, unaffected first. Declaring a chain again changes nothing. */
    public static synchronized void chain(Block... stages) {
        for (int i = 0; i < stages.length; i++) {
            STAGE.put(stages[i], i);
            if (i + 1 < stages.length) {
                NEXT.put(stages[i], stages[i + 1]);
                PREVIOUS.put(stages[i + 1], stages[i]);
            }
        }
    }

    /** Declares a block and its waxed form. */
    public static synchronized void waxed(Block plain, Block waxed) {
        WAXED.put(plain, waxed);
        UNWAXED.put(waxed, plain);
    }

    /** @return the next stage of an unwaxed block, or null at the last stage or for anything else */
    @Nullable
    public static synchronized Block next(Block b) {
        return NEXT.get(b);
    }

    /** @return the stage before (an axe's scrape), or null for an unaffected block or anything else */
    @Nullable
    public static synchronized Block previous(Block b) {
        return PREVIOUS.get(b);
    }

    @Nullable
    public static synchronized Block waxedOf(Block b) {
        return WAXED.get(b);
    }

    @Nullable
    public static synchronized Block unwaxed(Block b) {
        return UNWAXED.get(b);
    }

    /** @return the unaffected form of an unwaxed copper block (itself if unaffected) */
    public static synchronized Block first(Block b) {
        Block p;
        while ((p = PREVIOUS.get(b)) != null) {
            b = p;
        }
        return b;
    }

    /** @return 0-3 for unwaxed copper blocks, -1 for anything else (waxed copper included) */
    public static synchronized int stage(Block b) {
        Integer s = STAGE.get(b);
        return s == null ? -1 : s;
    }

    /** @return true for every registered copper block, waxed or not */
    public static synchronized boolean isCopper(Block b) {
        return STAGE.containsKey(b) || UNWAXED.containsKey(b);
    }

    /** The same state on another block of the chain: every property both blocks have is copied. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static IBlockState copy(IBlockState from, Block to) {
        IBlockState out = to.getDefaultState();
        for (IProperty p : from.getPropertyKeys()) {
            if (out.getPropertyKeys().contains(p)) {
                out = out.withProperty(p, from.getValue(p));
            }
        }
        return out;
    }

    /** Changes a copper block to another block of its chain, keeping its state; for a door, both halves. */
    public static void change(World world, BlockPos pos, IBlockState state, Block to) {
        if (state.getBlock() instanceof BlockDoor) {
            boolean lower = state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER;
            BlockPos other = lower ? pos.up() : pos.down();
            IBlockState otherState = world.getBlockState(other);
            // The upper half first, so the lower half never sees a mismatched partner and breaks.
            if (otherState.getBlock() == state.getBlock()) {
                world.setBlockState(lower ? other : pos, copy(lower ? otherState : state, to), 2);
                world.setBlockState(lower ? pos : other, copy(lower ? state : otherState, to), 3);
                return;
            }
        }
        world.setBlockState(pos, copy(state, to), 3);
    }

    /** A random tick on a copper block: ages it by the rule above (a door by its lower half). */
    public static void randomTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (state.getBlock() instanceof BlockDoor && state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER) {
            return;
        }
        Block next = next(state.getBlock());
        if (next == null || rand.nextFloat() >= TRY_CHANCE) {
            return;
        }
        if (rand.nextFloat() < ageChance(world, pos, stage(state.getBlock()))) {
            change(world, pos, state, next);
        }
    }

    /** @return the chance a block of stage {@code age} ages, given the copper around it; 0 if a less oxidized one is near */
    public static float ageChance(World world, BlockPos pos, int age) {
        int same = 0;
        int older = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -4; dx <= 4; dx++) {
            int rx = 4 - Math.abs(dx);
            for (int dy = -rx; dy <= rx; dy++) {
                int rz = rx - Math.abs(dy);
                for (int dz = -rz; dz <= rz; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    p.setPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!world.isBlockLoaded(p)) {
                        continue;
                    }
                    int other = stage(world.getBlockState(p).getBlock());
                    if (other < 0) {
                        continue;
                    }
                    if (other < age) {
                        return 0;
                    }
                    if (other > age) {
                        older++;
                    } else {
                        same++;
                    }
                }
            }
        }
        float f = (older + 1) / (float) (older + same + 1);
        return f * f * (age == 0 ? 0.75F : 1.0F);
    }
}

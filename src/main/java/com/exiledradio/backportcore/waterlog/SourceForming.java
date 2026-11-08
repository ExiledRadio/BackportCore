package com.exiledradio.backportcore.waterlog;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 1.13 and later form a water source in a dry waterloggable block between two sources, as they do in open
 * space (1.16.5's {@code FlowingFluid.getNewLiquid}: at least two horizontal neighbors are water sources whose
 * shared face has a hole, and the block below is solid or a water source). Fluidlogged API asks a waterloggable
 * block about the flowing water first, which {@link WaterRules} refuses as vanilla does, so the source would never
 * form without this.
 *
 * <p>When a block tells its neighbors it changed, every dry {@link Waterloggable} block at that position or beside
 * it is checked again five ticks later (water's tick rate, as vanilla's spread is scheduled); one that meets the
 * rule gets a water source through {@link Waterlogging#setWater}, so Fluidlogged API's config still has its say.
 * Server worlds only; water that vaporizes (the Nether) never forms.
 */
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID)
public final class SourceForming {

    /** Water's tick rate (1.16.5's {@code WaterFluid.getTickDelay}). */
    static final int DELAY = 5;

    private static final Map<World, Map<BlockPos, Long>> PENDING = new WeakHashMap<World, Map<BlockPos, Long>>();

    private SourceForming() {
    }

    @SubscribeEvent
    public static void neighborNotify(BlockEvent.NeighborNotifyEvent event) {
        World world = event.getWorld();
        if (world.isRemote) {
            return;
        }
        BlockPos pos = event.getPos();
        consider(world, pos);
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            consider(world, pos.offset(side));
        }
        consider(world, pos.up());
    }

    private static void consider(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) {
            return;
        }
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof Waterloggable) || Waterlogging.hasWater(world, pos)) {
            return;
        }
        synchronized (PENDING) {
            Map<BlockPos, Long> pending = PENDING.get(world);
            if (pending == null) {
                pending = new LinkedHashMap<BlockPos, Long>();
                PENDING.put(world, pending);
            }
            if (!pending.containsKey(pos)) {
                pending.put(pos.toImmutable(), world.getTotalWorldTime() + DELAY);
            }
        }
    }

    @SubscribeEvent
    public static void worldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) {
            return;
        }
        World world = event.world;
        List<BlockPos> due = new ArrayList<BlockPos>();
        synchronized (PENDING) {
            Map<BlockPos, Long> pending = PENDING.get(world);
            if (pending == null || pending.isEmpty()) {
                return;
            }
            long now = world.getTotalWorldTime();
            for (Iterator<Map.Entry<BlockPos, Long>> it = pending.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<BlockPos, Long> e = it.next();
                if (e.getValue() <= now) {
                    due.add(e.getKey());
                    it.remove();
                }
            }
        }
        for (BlockPos pos : due) {
            if (world.isBlockLoaded(pos) && formsSource(world, pos)) {
                Waterlogging.setWater(world, pos, true, 3);
            }
        }
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload event) {
        synchronized (PENDING) {
            PENDING.remove(event.getWorld());
        }
    }

    /**
     * @return true when the dry waterloggable block at {@code pos} would take a source by 1.16.5's rule: two or
     * more horizontal water sources reach it through faces neither block closes, and below is solid or a source
     */
    public static boolean formsSource(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof Waterloggable) || world.provider.doesWaterVaporize()
                || Waterlogging.hasWater(world, pos) || !Waterlogging.canHoldWater(state, world, pos)) {
            return false;
        }
        int sources = 0;
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            BlockPos next = pos.offset(side);
            if (!world.isBlockLoaded(next) || !Waterlogging.hasWaterSource(world, next)) {
                continue;
            }
            IBlockState there = world.getBlockState(next);
            if (state.getBlockFaceShape(world, pos, side) == BlockFaceShape.SOLID
                    || there.getBlockFaceShape(world, next, side.getOpposite()) == BlockFaceShape.SOLID) {
                continue;
            }
            sources++;
        }
        if (sources < 2) {
            return false;
        }
        BlockPos below = pos.down();
        return world.getBlockState(below).getMaterial().isSolid() || Waterlogging.hasWaterSource(world, below);
    }
}

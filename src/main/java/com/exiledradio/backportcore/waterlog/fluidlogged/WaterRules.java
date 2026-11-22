package com.exiledradio.backportcore.waterlog.fluidlogged;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.waterlog.Waterloggable;

import git.jbredwards.fluidlogged_api.api.event.FluidloggableEvent;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBasePressurePlate;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockSign;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockWall;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.13 and later's waterlogging rules for the family's blocks, over Fluidlogged API's own.
 *
 * <p>Fluidlogged API also waterlogs by its built-in list ({@code whitelist.jsonc}): every block extending a
 * vanilla stair, slab, wall, fence, trapdoor, sign, fence gate, door, button or pressure plate, with any fluid and
 * with flowing fluid too. Its list answers before a block's own {@link Waterloggable} is asked, so a family stair
 * would take lava. This handler, at {@code HIGHEST}, puts vanilla's rules back:
 * <ul>
 *   <li>A {@link Waterloggable} block takes water only, and only a <i>source</i> (1.16.5's {@code
 *       IWaterLoggable.canPlaceLiquid}: {@code fluid == Fluids.WATER}, never {@code FLOWING_WATER}), unless it
 *       opts in to flowing water ({@link Waterloggable#acceptsFlowingWater}). Flowing water does not run into a
 *       stair; a block placed into flowing water replaces it; a bucket or a source still waterlogs.</li>
 *   <li>A family block that is not {@link Waterloggable} holds no fluid at all: every block the family's versions
 *       made waterloggable implements {@link Waterloggable}, so the others (a fence gate, a door, a button, a torch,
 *       a plant) are dry in vanilla too, whatever Fluidlogged API's list says about their vanilla superclass.
 *       "Family" means a mod that requires Backport Core.</li>
 * </ul>
 * Every other block is left to Fluidlogged API and its config. The block's own {@link
 * Waterloggable#canHoldWater} (a double slab) is asked afterwards by Fluidlogged API as usual.
 *
 * <p>Only loaded with Fluidlogged API installed: {@code Waterlogging.init} registers it by name.
 */
public final class WaterRules {

    /** The vanilla shapes Fluidlogged API's built-in list waterlogs, as the family requests name them. */
    private static final Class<?>[] SHAPES = {
            BlockStairs.class, BlockSlab.class, BlockWall.class, BlockFence.class, BlockTrapDoor.class, BlockSign.class,
            BlockFenceGate.class, BlockDoor.class, BlockButton.class, BlockBasePressurePlate.class,
    };

    private static final Map<String, Boolean> FAMILY = new ConcurrentHashMap<String, Boolean>();

    private WaterRules() {
    }

    /** Called once from {@code Waterlogging.init}, with Fluidlogged API installed. */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(WaterRules.class);
        // Water outside y 0-255 in Caves & Cliffs Backport's extended worlds (patch P20): sync its sections.
        SectionFluidStates.register();
    }

    /** Set when Fluidlogged API is about to apply its configs; the next server tick clears its flow overrides. */
    private static volatile boolean flowOverridesPending;

    /**
     * Fluidlogged API's built-in list gives every {@code BlockLeaves} (and hopper, spawner) a "fluid always flows out"
     * override, asked before the block's own {@code canFluidFlow}. {@code Apply} comes just before it applies its
     * configs (which first resets every override), so the overrides are cleared on the next tick, for the states of
     * every {@link Waterloggable} block that answers {@link Waterloggable#waterFlowsThrough} itself.
     */
    @SubscribeEvent
    public static void configsApplied(git.jbredwards.fluidlogged_api.api.event.FluidloggedAPIConfigsEvent.Apply event) {
        flowOverridesPending = true;
    }

    @SubscribeEvent
    public static void serverTick(net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.fml.common.gameevent.TickEvent.Phase.START && flowOverridesPending) {
            flowOverridesPending = false;
            clearFlowOverrides();
        }
    }

    /** Hands flow back to {@link Waterloggable#waterFlowsThrough} for each block that overrides it. */
    public static void clearFlowOverrides() {
        int states = 0;
        for (Block block : net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS) {
            if (!(block instanceof Waterloggable)) {
                continue;
            }
            try {
                if (block.getClass().getMethod("waterFlowsThrough", IBlockState.class, net.minecraft.world.IBlockAccess.class,
                        net.minecraft.util.math.BlockPos.class, net.minecraft.util.EnumFacing.class).getDeclaringClass() == Waterloggable.class) {
                    continue;
                }
            } catch (NoSuchMethodException e) {
                continue;
            }
            for (IBlockState state : block.getBlockState().getValidStates()) {
                git.jbredwards.fluidlogged_api.mod.asm.iface.ICanFluidFlowHandler.Accessor.setOverride(state, null);
                states++;
            }
        }
        BackportCore.LOGGER.debug("Waterlogging: {} states answer water flow themselves", states);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void fluidloggable(FluidloggableEvent event) {
        if (decide(event.state, event.fluidState) == Boolean.FALSE) {
            event.setCanceled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * @return {@code false} when the rules refuse this fluid in this state, or null when they leave it to
     * Fluidlogged API (they never force a block to take a fluid)
     */
    @Nullable
    public static Boolean decide(IBlockState state, FluidState fluid) {
        Block block = state.getBlock();
        if (block instanceof Waterloggable) {
            if (fluid.isEmpty()) {
                return null;
            }
            if (!FluidloggedUtils.isCompatibleFluid(fluid.getFluid(), FluidRegistry.WATER)) {
                return Boolean.FALSE;
            }
            if (!fluid.isSource() && !((Waterloggable) block).acceptsFlowingWater(state)) {
                return Boolean.FALSE;
            }
            return null;
        }
        return isFamily(block) ? Boolean.FALSE : null;
    }

    /** @return true for a block extending one of the vanilla shapes Fluidlogged API's list waterlogs */
    public static boolean isShape(Block block) {
        for (Class<?> shape : SHAPES) {
            if (shape.isInstance(block)) {
                return true;
            }
        }
        return false;
    }

    /** @return true for a block registered by Backport Core or by a mod that requires it */
    public static boolean isFamily(Block block) {
        ResourceLocation name = block.getRegistryName();
        if (name == null) {
            return false;
        }
        String mod = name.getNamespace();
        Boolean known = FAMILY.get(mod);
        if (known == null) {
            known = requiresCore(mod);
            FAMILY.put(mod, known);
        }
        return known;
    }

    private static boolean requiresCore(String modId) {
        if (BackportCore.MOD_ID.equals(modId)) {
            return true;
        }
        ModContainer mod = Loader.instance().getIndexedModList().get(modId);
        if (mod == null) {
            return false;
        }
        for (ArtifactVersion v : mod.getRequirements()) {
            if (BackportCore.MOD_ID.equals(v.getLabel())) {
                return true;
            }
        }
        for (ArtifactVersion v : mod.getDependencies()) {
            if (BackportCore.MOD_ID.equals(v.getLabel())) {
                return true;
            }
        }
        return false;
    }
}

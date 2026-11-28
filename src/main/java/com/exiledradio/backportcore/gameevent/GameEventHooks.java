package com.exiledradio.backportcore.gameevent;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The game event sources 1.12.2 fires no event for, called from the coremod's patches P80-P83
 * (docs/PATCHES.md). Each hook does nothing but emit, and only on a server while {@link GameEvents#active()};
 * the patched methods keep 1.12.2's behavior.
 */
public final class GameEventHooks {

    private GameEventHooks() {
    }

    /**
     * P80, the head of {@code Entity.updateFallState}: 1.19's {@code Entity.checkFallDamage} posts
     * {@code hit_ground} for every entity that lands after falling ({@code fallDistance > 0}), dropped items included,
     * with the block landed on as the context (so wool and carpets dampen it). Living entities are left to
     * {@code LivingFallEvent} ({@link GameEventEmitters#fall}); boats override the method, as in vanilla.
     */
    public static void landed(Entity entity, boolean onGround, IBlockState state) {
        if (onGround && entity.fallDistance > 0.0F && !(entity instanceof EntityLivingBase) && entity.world != null
                && !entity.world.isRemote && GameEvents.active()) {
            GameEvents.emit(entity.world, GameEvent.HIT_GROUND, new Vec3d(entity.posX, entity.posY, entity.posZ),
                    GameEventContext.of(entity, state));
        }
    }

    /**
     * P81, {@code BlockCauldron.fillWithRain}'s {@code setBlockState}: 1.19's {@code LayeredCauldronBlock} posts
     * {@code block_change} when rain raises the water.
     */
    public static boolean rainFill(World world, BlockPos pos, IBlockState state, int flags) {
        boolean set = world.setBlockState(pos, state, flags);
        if (set && !world.isRemote && GameEvents.active()) {
            GameEvents.emit(world, GameEvent.BLOCK_CHANGE, pos, GameEventContext.of(state));
        }
        return set;
    }

    /**
     * P82, the head of {@code World.destroyBlock}: 1.19's {@code Level.destroyBlock} posts {@code block_destroy}
     * (sheep eating tall grass, villagers harvesting, a wither, a trampled last turtle egg, cactus next to a block).
     * 1.12.2 returns at once for air, and so does this.
     */
    public static void blockDestroyed(World world, BlockPos pos) {
        if (world.isRemote || !GameEvents.active()) {
            return;
        }
        IBlockState state = world.getBlockState(pos);
        if (!state.getBlock().isAir(state, world, pos)) {
            GameEvents.emit(world, GameEvent.BLOCK_DESTROY, pos, GameEventContext.of(state));
        }
    }

    /**
     * P83, the head of {@code EntityLivingBase.resetActiveHand}: 1.19's {@code LivingEntity.stopUsingItem} posts
     * {@code item_interact_finish} whenever an entity that was using an item stops (finishing food, releasing a bow,
     * crossbow or trident, lowering a shield, a skeleton shooting).
     */
    public static void stoppedUsing(EntityLivingBase entity) {
        if (!entity.world.isRemote && entity.isHandActive() && GameEvents.active()) {
            GameEvents.emit(entity.world, GameEvent.ITEM_INTERACT_FINISH, new Vec3d(entity.posX, entity.posY, entity.posZ),
                    GameEventContext.of(entity));
        }
    }
}

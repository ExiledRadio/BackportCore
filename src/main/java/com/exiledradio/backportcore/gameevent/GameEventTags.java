package com.exiledradio.backportcore.gameevent;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 1.19.2's and 1.20.6's game event and block tags, as fixed sets (1.12.2 has no tags).
 *
 * <p>The version-neutral accessors ({@link #vibrations()}, {@link #wardenCanListen()}) give 1.20's sets when a
 * 1.20 backport has game events on ({@link GameEvents#isAtLeast}), else 1.19.2's. 1.20's sets also hold the
 * 1.19.2 events 1.20 renamed (piston extend and contract, entity roar and shake), with the same frequencies
 * as what they became, so an older mod's emitters still reach a 1.20 listener.
 */
public final class GameEventTags {

    private GameEventTags() {
    }

    private static Set<GameEvent> set(GameEvent... events) {
        return Collections.unmodifiableSet(new HashSet<GameEvent>(Arrays.asList(events)));
    }

    /** 1.19.2's {@code #vibrations}. */
    public static final Set<GameEvent> VIBRATIONS_1_19 = set(GameEvent.BLOCK_ATTACH, GameEvent.BLOCK_CHANGE, GameEvent.BLOCK_CLOSE,
            GameEvent.BLOCK_DESTROY, GameEvent.BLOCK_DETACH, GameEvent.BLOCK_OPEN, GameEvent.BLOCK_PLACE, GameEvent.BLOCK_ACTIVATE,
            GameEvent.BLOCK_DEACTIVATE, GameEvent.CONTAINER_CLOSE, GameEvent.CONTAINER_OPEN, GameEvent.DISPENSE_FAIL, GameEvent.DRINK,
            GameEvent.EAT, GameEvent.ELYTRA_GLIDE, GameEvent.ENTITY_DAMAGE, GameEvent.ENTITY_DIE, GameEvent.ENTITY_INTERACT,
            GameEvent.ENTITY_PLACE, GameEvent.ENTITY_ROAR, GameEvent.ENTITY_SHAKE, GameEvent.EQUIP, GameEvent.EXPLODE,
            GameEvent.FLUID_PICKUP, GameEvent.FLUID_PLACE, GameEvent.HIT_GROUND, GameEvent.INSTRUMENT_PLAY,
            GameEvent.ITEM_INTERACT_FINISH, GameEvent.LIGHTNING_STRIKE, GameEvent.NOTE_BLOCK_PLAY, GameEvent.PISTON_CONTRACT,
            GameEvent.PISTON_EXTEND, GameEvent.PRIME_FUSE, GameEvent.PROJECTILE_LAND, GameEvent.PROJECTILE_SHOOT, GameEvent.SHEAR,
            GameEvent.SPLASH, GameEvent.STEP, GameEvent.SWIM, GameEvent.TELEPORT, GameEvent.FLAP);

    /** 1.20.6's {@code #vibrations} (with the renamed 1.19.2 events). */
    public static final Set<GameEvent> VIBRATIONS_1_20;

    /** 1.19.2's {@code #warden_can_listen}: the vibrations but flap, the shriek, and the tendrils' clicking. */
    public static final Set<GameEvent> WARDEN_CAN_LISTEN_1_19;

    /** 1.20.6's {@code #warden_can_listen}. */
    public static final Set<GameEvent> WARDEN_CAN_LISTEN_1_20;

    static {
        Set<GameEvent> v = new HashSet<GameEvent>(Arrays.asList(GameEvent.BLOCK_ATTACH, GameEvent.BLOCK_CHANGE, GameEvent.BLOCK_CLOSE,
                GameEvent.BLOCK_DESTROY, GameEvent.BLOCK_DETACH, GameEvent.BLOCK_OPEN, GameEvent.BLOCK_PLACE, GameEvent.BLOCK_ACTIVATE,
                GameEvent.BLOCK_DEACTIVATE, GameEvent.CONTAINER_CLOSE, GameEvent.CONTAINER_OPEN, GameEvent.DRINK, GameEvent.EAT,
                GameEvent.ELYTRA_GLIDE, GameEvent.ENTITY_DAMAGE, GameEvent.ENTITY_DIE, GameEvent.ENTITY_DISMOUNT,
                GameEvent.ENTITY_INTERACT, GameEvent.ENTITY_MOUNT, GameEvent.ENTITY_PLACE, GameEvent.ENTITY_ACTION, GameEvent.EQUIP,
                GameEvent.EXPLODE, GameEvent.FLUID_PICKUP, GameEvent.FLUID_PLACE, GameEvent.HIT_GROUND, GameEvent.INSTRUMENT_PLAY,
                GameEvent.ITEM_INTERACT_FINISH, GameEvent.LIGHTNING_STRIKE, GameEvent.NOTE_BLOCK_PLAY, GameEvent.PRIME_FUSE,
                GameEvent.PROJECTILE_LAND, GameEvent.PROJECTILE_SHOOT, GameEvent.SHEAR, GameEvent.SPLASH, GameEvent.STEP,
                GameEvent.SWIM, GameEvent.TELEPORT, GameEvent.UNEQUIP));
        for (int f = 1; f <= 15; f++) {
            v.add(GameEvent.resonance(f));
        }
        // 1.19.2's names for what 1.20 posts as block_activate, block_deactivate and entity_action.
        v.add(GameEvent.PISTON_EXTEND);
        v.add(GameEvent.PISTON_CONTRACT);
        v.add(GameEvent.ENTITY_ROAR);
        v.add(GameEvent.ENTITY_SHAKE);
        Set<GameEvent> warden20 = new HashSet<GameEvent>(v);
        v.add(GameEvent.FLAP);
        VIBRATIONS_1_20 = Collections.unmodifiableSet(v);
        warden20.add(GameEvent.SHRIEK);
        warden20.add(GameEvent.SCULK_SENSOR_TENDRILS_CLICKING);
        WARDEN_CAN_LISTEN_1_20 = Collections.unmodifiableSet(warden20);

        Set<GameEvent> warden = new HashSet<GameEvent>(VIBRATIONS_1_19);
        warden.remove(GameEvent.FLAP);
        warden.add(GameEvent.SHRIEK);
        warden.add(GameEvent.SCULK_SENSOR_TENDRILS_CLICKING);
        WARDEN_CAN_LISTEN_1_19 = Collections.unmodifiableSet(warden);
    }

    /** {@code #shrieker_can_listen} (1.19.2 and 1.20.6). */
    public static final Set<GameEvent> SHRIEKER_CAN_LISTEN = set(GameEvent.SCULK_SENSOR_TENDRILS_CLICKING);

    /** {@code #allay_can_listen} (1.19.2 and 1.20.6). */
    public static final Set<GameEvent> ALLAY_CAN_LISTEN = set(GameEvent.NOTE_BLOCK_PLAY);

    /** {@code #ignore_vibrations_sneaking} (1.19.2 and 1.20.6). */
    public static final Set<GameEvent> IGNORE_VIBRATIONS_SNEAKING = set(GameEvent.HIT_GROUND, GameEvent.PROJECTILE_SHOOT,
            GameEvent.STEP, GameEvent.SWIM, GameEvent.ITEM_INTERACT_START, GameEvent.ITEM_INTERACT_FINISH);

    /** {@code #vibrations} of the newest rules on. */
    public static Set<GameEvent> vibrations() {
        return GameEvents.isAtLeast("1.20") ? VIBRATIONS_1_20 : VIBRATIONS_1_19;
    }

    /** {@code #warden_can_listen} of the newest rules on. */
    public static Set<GameEvent> wardenCanListen() {
        return GameEvents.isAtLeast("1.20") ? WARDEN_CAN_LISTEN_1_20 : WARDEN_CAN_LISTEN_1_19;
    }

    /** {@code #occludes_vibration_signals}: wool. */
    public static boolean occludes(IBlockState state) {
        return state.getBlock() == Blocks.WOOL;
    }

    private static final ResourceLocation WARDEN = new ResourceLocation("wildupdatebackport", "warden");

    /** {@code #dampens_vibrations}: wool and wool carpets. */
    public static boolean dampens(IBlockState state) {
        Block b = state.getBlock();
        return b == Blocks.WOOL || b == Blocks.CARPET;
    }

    /** {@code Entity.dampensVibrations}: a dropped wool or carpet item, and the warden. */
    public static boolean dampens(Entity entity) {
        if (WARDEN.equals(EntityList.getKey(entity))) {
            return true;
        }
        if (entity instanceof EntityItem) {
            Block b = Block.getBlockFromItem(((EntityItem) entity).getItem().getItem());
            return b == Blocks.WOOL || b == Blocks.CARPET;
        }
        return false;
    }

    private static final Set<Block> RESONATORS = new CopyOnWriteArraySet<Block>();

    /** Adds a block to {@code #vibration_resonators} (1.20: the amethyst block, whichever mod registers it). */
    public static void addResonator(Block block) {
        if (block != null) {
            RESONATORS.add(block);
        }
    }

    /** {@code #vibration_resonators}. */
    public static boolean resonates(IBlockState state) {
        return RESONATORS.contains(state.getBlock());
    }
}

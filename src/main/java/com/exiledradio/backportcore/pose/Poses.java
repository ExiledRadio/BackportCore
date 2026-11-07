package com.exiledradio.backportcore.pose;

import com.exiledradio.backportcore.BackportCore;
import com.google.common.collect.MapMaker;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * The pose engine: 1.14's player poses on 1.12.2 - the swimming pose and its 0.6 x 0.6 box and eyes at 0.4,
 * crawling, and the Riptide spin - worked out the same way on the server and on the player's own client,
 * sent to everyone else who can see the player, and drawn (see {@code pose.client}).
 *
 * <p>Nothing happens until a backport hands over its {@link PoseRules} with {@link #register}; with none
 * enabled, players are exactly vanilla 1.12.2's. With a mod installed that resizes players or adds
 * swimming of its own, the engine stands down quietly ({@link #standsDown()}).
 *
 * <h2>When</h2>
 * At the end of each player's tick ({@code PlayerTickEvent} END, which vanilla fires as the last line of
 * {@code EntityPlayer.updateSize}): the rules tick, then the pose is chosen as 1.14's
 * {@code PlayerEntity.updatePose} chooses it, then the box and eyes are set. The box is then the one the
 * next tick's movement and the server's movement check use. 1.12.2's own {@code updateSize} keeps a box it
 * cannot grow, so a player in a one-block gap stays small until there is room.
 */
public final class Poses {

    /** Mods that resize players or add swimming of their own: with one installed the engine stands down. */
    private static final String[] RESIZERS = {"aquaacrobatics", "morph", "metamorph", "chiseled_me"};
    /** Mods that draw players their own way: with one installed only the drawing stands down. */
    private static final String[] REDRAWERS = {"mobends"};

    /** Eye height base in the swimming pose: 1.12.2 returns 0.4 for a 0.6-tall player, and this less 0.08 sneaking. */
    static final float SWIM_EYE_BASE = 0.48F;

    private static final List<PoseRules> RULES = new CopyOnWriteArrayList<PoseRules>();
    private static final List<PlayerMovement> MOVEMENT = new CopyOnWriteArrayList<PlayerMovement>();
    /** Keyed by the player object itself (weak, by identity): a world's client and server copies of a player share an entity id. */
    private static final Map<EntityPlayer, PoseState> STATES = new MapMaker().weakKeys().makeMap();

    /** Client side: whether a player is the client's own (set by {@code pose.client.PoseClient}). */
    static Predicate<EntityPlayer> ownPlayer = p -> false;

    private static Boolean standsDown;
    private static Boolean drawStandsDown;
    private static Method setSize;

    private Poses() {
    }

    // ------------------------------------------------------------------------------------------------
    // For backports
    // ------------------------------------------------------------------------------------------------

    /** Hands a backport's pose rules to the engine. Call once, in pre-init. */
    public static void register(PoseRules rules) {
        RULES.add(rules);
    }

    /** Hands a backport's movement changes to the library's patches. Call once, in pre-init. */
    public static void registerMovement(PlayerMovement movement) {
        MOVEMENT.add(movement);
    }

    /** @return true while some backport's rules are on and no mod the engine stands down for is installed */
    public static boolean active() {
        if (standsDown()) {
            return false;
        }
        for (PoseRules rules : RULES) {
            if (rules.enabled()) {
                return true;
            }
        }
        return false;
    }

    /** @return true if a mod is installed that resizes players or adds swimming of its own */
    public static boolean standsDown() {
        if (standsDown == null) {
            standsDown = anyLoaded(RESIZERS);
            if (standsDown) {
                BackportCore.LOGGER.info("Player poses: another installed mod resizes players; Backport Core leaves them alone.");
            }
        }
        return standsDown;
    }

    /** @return true while poses are active and no installed mod draws players its own way */
    public static boolean drawn() {
        if (drawStandsDown == null) {
            drawStandsDown = anyLoaded(REDRAWERS);
        }
        return !drawStandsDown && active();
    }

    /** @return the player's pose on this side (STANDING for anyone the engine has not seen) */
    public static Pose get(EntityPlayer player) {
        PoseState state = STATES.get(player);
        return state == null ? Pose.STANDING : state.pose;
    }

    /** @return true in the swimming pose out of water: 1.14's crawling */
    public static boolean isCrawling(EntityPlayer player) {
        return get(player) == Pose.SWIMMING && !player.isInWater();
    }

    /**
     * @return the player's swim animation at this partial tick, 0 to 1 (client side; 1.14's
     * {@code getSwimAnimation})
     */
    public static float swimAmount(EntityPlayer player, float partialTicks) {
        PoseState state = STATES.get(player);
        return state == null ? 0.0F : state.swim(partialTicks);
    }

    /** @return the box the player would have in this pose where they stand now */
    public static AxisAlignedBB box(EntityPlayer player, Pose pose) {
        double half = pose.width / 2.0D;
        return new AxisAlignedBB(player.posX - half, player.posY, player.posZ - half,
                player.posX + half, player.posY + pose.height, player.posZ + half);
    }

    /** @return true if nothing solid (blocks, boats, the world border) is in the player's box for this pose: 1.14's {@code isPoseClear} */
    public static boolean fits(EntityPlayer player, Pose pose) {
        return player.world.getCollisionBoxes(player, box(player, pose)).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------
    // The engine
    // ------------------------------------------------------------------------------------------------

    static List<PlayerMovement> movement() {
        return MOVEMENT;
    }

    static boolean moving() {
        return !MOVEMENT.isEmpty() && active();
    }

    /** One player's end of tick, on either side. */
    static void tick(EntityPlayer player) {
        PoseState state = STATES.get(player);
        if (!active()) {
            if (state != null && (state.pose != Pose.STANDING || state.resized)) {
                Pose before = state.pose;
                state.pose = Pose.STANDING;
                apply(player, state, Pose.STANDING);
                state.swim = state.prevSwim = 0.0F;
                sync(player, before, Pose.STANDING);
            }
            return;
        }
        if (state == null) {
            state = new PoseState();
            STATES.put(player, state);
        }
        if (player.world.isRemote) {
            // 1.14 moves the animation at the start of the tick, from the pose the last tick chose.
            state.tickSwim();
        }
        if (!player.world.isRemote || ownPlayer.test(player)) {
            for (PoseRules rules : RULES) {
                if (rules.enabled()) {
                    rules.tick(player);
                }
            }
            Pose before = state.pose;
            Pose pose = decide(player, before);
            state.pose = pose;
            apply(player, state, pose);
            sync(player, before, pose);
        } else {
            // Someone else on a client: the pose is the server's.
            apply(player, state, state.pose);
        }
    }

    /** 1.14.4's {@code PlayerEntity.updatePose}. */
    static Pose decide(EntityPlayer player, Pose previous) {
        if (!fits(player, Pose.SWIMMING)) {
            return previous;
        }
        Pose pose;
        if (player.isElytraFlying()) {
            pose = Pose.FALL_FLYING;
        } else if (player.isPlayerSleeping()) {
            pose = Pose.SLEEPING;
        } else if (anyRule(player, 0)) {
            pose = Pose.SWIMMING;
        } else if (anyRule(player, 1)) {
            pose = Pose.SPIN_ATTACK;
        } else if (player.isSneaking() && !player.capabilities.isFlying) {
            pose = Pose.SNEAKING;
        } else {
            pose = Pose.STANDING;
        }
        if (!player.isSpectator() && !player.isRiding() && !fits(player, pose)) {
            if (fits(player, Pose.SNEAKING)) {
                pose = Pose.SNEAKING;
            } else if (anyRule(player, 2)) {
                pose = Pose.SWIMMING;
            }
        }
        return pose;
    }

    private static boolean anyRule(EntityPlayer player, int which) {
        for (PoseRules rules : RULES) {
            if (rules.enabled() && (which == 0 ? rules.isSwimming(player) : which == 1 ? rules.isSpinAttacking(player) : rules.crawls())) {
                return true;
            }
        }
        return false;
    }

    /** Sets the box and eyes for a pose; vanilla's own poses are left to vanilla, once a shrunk box can grow back. */
    static void apply(EntityPlayer player, PoseState state, Pose pose) {
        if (pose.resized()) {
            if (player.width != pose.width || player.height != pose.height) {
                setSize(player, pose.width, pose.height);
            }
            player.eyeHeight = SWIM_EYE_BASE;
            state.resized = true;
            return;
        }
        if (!state.resized) {
            return;
        }
        if (pose == Pose.FALL_FLYING || pose == Pose.SLEEPING) {
            // Vanilla's updateSize has already given these their own boxes.
            player.eyeHeight = player.getDefaultEyeHeight();
            state.resized = false;
            return;
        }
        // Standing or sneaking: grow back only into room, as vanilla's updateSize does.
        Pose target = pose == Pose.SNEAKING || player.isSneaking() ? Pose.SNEAKING : Pose.STANDING;
        if (player.width == target.width && player.height == target.height) {
            player.eyeHeight = player.getDefaultEyeHeight();
            state.resized = false;
        } else if (fits(player, target)) {
            setSize(player, target.width, target.height);
            player.eyeHeight = player.getDefaultEyeHeight();
            state.resized = false;
        }
    }

    private static void sync(EntityPlayer player, Pose before, Pose now) {
        if (before != now && !player.world.isRemote) {
            PoseNetwork.sendToTrackers(player, now);
        }
    }

    /** Client side: a pose the server sent for someone else. */
    static void received(EntityPlayer player, Pose pose) {
        PoseState state = STATES.get(player);
        if (state == null) {
            state = new PoseState();
            STATES.put(player, state);
        }
        state.pose = pose;
    }

    static void setSize(Entity entity, float width, float height) {
        try {
            if (setSize == null) {
                setSize = ObfuscationReflectionHelper.findMethod(Entity.class, "func_70105_a", void.class, float.class, float.class);
            }
            setSize.invoke(entity, width, height);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Backport Core: cannot resize " + entity, e);
        }
    }

    private static boolean anyLoaded(String[] ids) {
        for (String id : ids) {
            if (Loader.isModLoaded(id)) {
                return true;
            }
        }
        return false;
    }
}

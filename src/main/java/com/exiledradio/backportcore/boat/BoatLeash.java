package com.exiledradio.backportcore.boat;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.block.BlockFence;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.EntityLeashKnot;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Leashed boats, 1.21.6's {@code Leashable} for every {@link EntityBoat} (vanilla's and the family's): a lead ties a
 * boat to a player, a fence knot or another boat, and the boat follows on an elastic rope.
 *
 * <h2>Rules (1.21.6)</h2>
 * <ul>
 *   <li>Using a lead on a boat not held by a player ties it to the player (one lead used, none in Creative); using a
 *       boat you hold unties it (the lead dropped, none in Creative); shears cut every lead on the boat (dropping
 *       them) and wear by one; sneak-using a boat ties the boats you hold to it. A player holding boats ties them to
 *       a fence by using the fence or its knot.</li>
 *   <li>Each server tick ({@code Leashable.tickLeash}): the lead breaks (the lead dropped, a snap) past 12 blocks
 *       between the two boxes' centers, or when the holder is gone; past the elastic distance (6, less both widths)
 *       the rope pulls as a damped spring at one point on each (four with a {@link #quadHolder quad holder}, a
 *       happy ghast): force {@code (0.8, 0.2, 0.8)} of the stretch, beyond the slack, a third of it while the boat
 *       already moves that way, plus 0.11 of the holder's movement relative to the boat; the rope's torque turns the
 *       boat (10 a unit, damped by 0.91 in the air, 0.8 in water, the block's slipperiness times 0.91 on ground).</li>
 *   <li>Saved with the boat (Forge's entity data, {@code backportcore:leash}: the holder's UUID or the knot's
 *       position); a holder not found within 100 ticks of loading drops the lead.</li>
 * </ul>
 * Clients learn each boat's holder on channel {@code backportcore_boat} (message 1) and draw the rope
 * ({@code client.BoatLeashRender}). The sounds are 1.12.2's knot and shear sounds until a backport sets 1.21's
 * ({@link #setSounds}). Only while a backport of 1.21 or later is installed, or behind the switches backports
 * {@linkplain #require require} ({@link #on()}). Leashes already tied keep working when it is off; only new ones wait.
 */
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID)
public final class BoatLeash {

    static final String KEY = "backportcore:leash";

    /** {@code Leashable.leashSnapDistance}. */
    public static final double SNAP_DISTANCE = 12.0D;
    /** {@code Leashable.leashElasticDistance}. */
    public static final double ELASTIC_DISTANCE = 6.0D;
    /** How far a player's leashed boats are looked for when they tie them to something (1.12.2's lead: 7). */
    public static final double REACH = 7.0D;

    private static final Vec3d ELASTICITY = new Vec3d(0.8D, 0.2D, 0.8D);
    private static final Vec3d[] ONE_ON_ENTITY = {new Vec3d(0.0D, 0.5D, 0.5D)};
    private static final Vec3d[] ONE_ON_HOLDER = {new Vec3d(0.0D, 0.5D, 0.0D)};
    private static final Vec3d[] QUAD = {new Vec3d(-0.5D, 0.5D, 0.5D), new Vec3d(-0.5D, 0.5D, -0.5D),
            new Vec3d(0.5D, 0.5D, -0.5D), new Vec3d(0.5D, 0.5D, 0.5D)};

    /** A boat's lead: the holder, once found, and the rope's spin. */
    static final class Data {
        @Nullable
        Entity holder;
        @Nullable
        UUID holderId;
        @Nullable
        BlockPos knot;
        double angularMomentum;
    }

    private static final Map<Entity, Data> LEASHED = Collections.synchronizedMap(new WeakHashMap<Entity, Data>());

    /** Client: each boat's holder by entity id, from the server (read by {@code client.BoatLeashRender}). */
    public static final Map<Integer, Integer> CLIENT_HOLDERS = new ConcurrentHashMap<Integer, Integer>();

    private static volatile Predicate<Entity> quadHolder = e -> false;
    private static SoundEvent tied = SoundEvents.ENTITY_LEASHKNOT_PLACE;
    private static SoundEvent untied = SoundEvents.ENTITY_LEASHKNOT_BREAK;
    private static SoundEvent snapped = SoundEvents.ENTITY_LEASHKNOT_BREAK;
    private static SoundEvent snip = SoundEvents.ENTITY_SHEEP_SHEAR;

    private BoatLeash() {
    }

    // --- API --------------------------------------------------------------------------------------

    private static final List<java.util.function.BooleanSupplier> SWITCHES = new java.util.concurrent.CopyOnWriteArrayList<java.util.function.BooleanSupplier>();

    /**
     * Puts leashed boats behind a backport's own setting (Tricky Trials': its lead stream). Once any mod has, boats can
     * be leashed while one of the switches is on.
     */
    public static void require(java.util.function.BooleanSupplier enabled) {
        SWITCHES.add(enabled);
    }

    /** @return true while boats can be leashed: a required switch is on, or with none, a backport of 1.21 or later is installed */
    public static boolean on() {
        if (SWITCHES.isEmpty()) {
            return Presentation.installedNewest().isAtLeast("1.21");
        }
        for (java.util.function.BooleanSupplier s : SWITCHES) {
            if (s.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 1.21.6's {@code supportQuadLeashAsHolder}: holders that carry a boat by four ropes at its corners (Tricky Trials'
     * happy ghast). The force is then averaged over the four.
     */
    public static void quadHolder(Predicate<Entity> holders) {
        quadHolder = holders;
    }

    /** 1.21's lead sounds ({@code item.lead.tied}, {@code untied}, {@code break}, {@code item.shears.snip}), from the backport that has them. */
    public static void setSounds(SoundEvent leadTied, SoundEvent leadUntied, SoundEvent leadBreak, SoundEvent shearsSnip) {
        tied = leadTied;
        untied = leadUntied;
        snapped = leadBreak;
        snip = shearsSnip;
    }

    /** @return what holds this boat, or null (server; the client asks {@link #CLIENT_HOLDERS}) */
    @Nullable
    public static Entity holder(Entity boat) {
        Data d = LEASHED.get(boat);
        return d == null ? null : d.holder;
    }

    /** @return true if the boat has a lead, its holder found or not yet */
    public static boolean isLeashed(Entity boat) {
        return LEASHED.containsKey(boat);
    }

    /** Ties a boat to a holder (a player, a fence knot, another boat or a mob), replacing its lead. Server. */
    public static void leash(EntityBoat boat, Entity holder) {
        if (boat.world.isRemote || holder == boat) {
            return;
        }
        Data d = LEASHED.get(boat);
        if (d == null) {
            d = new Data();
            LEASHED.put(boat, d);
        }
        d.holder = holder;
        d.holderId = holder instanceof EntityLeashKnot ? null : holder.getUniqueID();
        d.knot = holder instanceof EntityLeashKnot ? ((EntityLeashKnot) holder).getHangingPosition() : null;
        save(boat, d);
        sync(boat, holder.getEntityId());
    }

    /** {@code dropLeash}: removes the lead, leaving the lead item at the boat. */
    public static void dropLeash(Entity boat) {
        unleash(boat, true);
    }

    /** {@code removeLeash}: removes the lead without an item. */
    public static void removeLeash(Entity boat) {
        unleash(boat, false);
    }

    private static void unleash(Entity boat, boolean drop) {
        if (boat.world.isRemote || LEASHED.remove(boat) == null) {
            return;
        }
        boat.getEntityData().removeTag(KEY);
        if (drop) {
            boat.entityDropItem(new ItemStack(Items.LEAD), 0.0F);
        }
        sync(boat, -1);
    }

    /** @return the leashed boats this entity holds within {@link #REACH} */
    public static List<EntityBoat> heldBy(Entity holder) {
        List<EntityBoat> out = new ArrayList<EntityBoat>();
        AxisAlignedBB box = new AxisAlignedBB(holder.posX - REACH, holder.posY - REACH, holder.posZ - REACH,
                holder.posX + REACH, holder.posY + REACH, holder.posZ + REACH);
        for (EntityBoat b : holder.world.getEntitiesWithinAABB(EntityBoat.class, box)) {
            if (holder(b) == holder) {
                out.add(b);
            }
        }
        return out;
    }

    // --- saving ----------------------------------------------------------------------------------

    private static void save(Entity boat, Data d) {
        NBTTagCompound tag = new NBTTagCompound();
        if (d.knot != null) {
            tag.setInteger("X", d.knot.getX());
            tag.setInteger("Y", d.knot.getY());
            tag.setInteger("Z", d.knot.getZ());
        } else if (d.holderId != null) {
            tag.setUniqueId("UUID", d.holderId);
        }
        boat.getEntityData().setTag(KEY, tag);
    }

    @SubscribeEvent
    public static void join(EntityJoinWorldEvent event) {
        Entity e = event.getEntity();
        if (event.getWorld().isRemote || !(e instanceof EntityBoat) || LEASHED.containsKey(e) || !e.getEntityData().hasKey(KEY, 10)) {
            return;
        }
        NBTTagCompound tag = e.getEntityData().getCompoundTag(KEY);
        Data d = new Data();
        if (tag.hasUniqueId("UUID")) {
            d.holderId = tag.getUniqueId("UUID");
        } else if (tag.hasKey("X")) {
            d.knot = new BlockPos(tag.getInteger("X"), tag.getInteger("Y"), tag.getInteger("Z"));
        } else {
            return;
        }
        LEASHED.put(e, d);
    }

    // --- syncing ---------------------------------------------------------------------------------

    private static void sync(Entity boat, int holderId) {
        BoatNetwork.leashChanged(boat, holderId);
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        Entity target = event.getTarget();
        Data d = LEASHED.get(target);
        if (d != null && d.holder != null && event.getEntityPlayer() instanceof EntityPlayerMP) {
            BoatNetwork.leashTo((EntityPlayerMP) event.getEntityPlayer(), target, d.holder.getEntityId());
        }
    }

    // --- interacting -----------------------------------------------------------------------------

    /** 1.21.6's {@code Entity.interact} for a leashable boat. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!(target instanceof EntityBoat) || target.isDead || !on()) {
            if (target instanceof EntityLeashKnot && !event.getWorld().isRemote && on()) {
                knotUsed(event, (EntityLeashKnot) target);
            }
            return;
        }
        EntityBoat boat = (EntityBoat) target;
        EntityPlayer player = event.getEntityPlayer();
        ItemStack held = event.getItemStack();
        boolean server = !event.getWorld().isRemote;
        if (player.isSneaking() && server) {
            boolean any = false;
            for (EntityBoat b : heldBy(player)) {
                if (b != boat) {
                    leash(b, boat);
                    any = true;
                }
            }
            if (any) {
                sound(boat, tied);
                handled(event);
                return;
            }
        }
        if (held.getItem() == Items.SHEARS) {
            if (server && shearOff(boat, player)) {
                held.damageItem(1, player);
                handled(event);
            }
            return;
        }
        boolean mine = server ? holder(boat) == player : Integer.valueOf(player.getEntityId()).equals(CLIENT_HOLDERS.get(boat.getEntityId()));
        if (mine) {
            if (server) {
                unleash(boat, !player.capabilities.isCreativeMode);
                sound(boat, untied);
            }
            handled(event);
            return;
        }
        if (held.getItem() == Items.LEAD && !(server ? holder(boat) instanceof EntityPlayer : isHeldByPlayer(boat))) {
            if (server) {
                if (isLeashed(boat)) {
                    dropLeash(boat);
                }
                leash(boat, player);
                sound(boat, tied);
                if (!player.capabilities.isCreativeMode) {
                    held.shrink(1);
                }
            }
            handled(event);
        }
    }

    private static boolean isHeldByPlayer(Entity boat) {
        Integer id = CLIENT_HOLDERS.get(boat.getEntityId());
        return id != null && boat.world.getEntityByID(id) instanceof EntityPlayer;
    }

    private static void handled(PlayerInteractEvent.EntityInteract event) {
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
    }

    /** 1.21.6's {@code shearOffAllLeashConnections}: the boat's own lead and the boats it holds, each lead dropped. */
    private static boolean shearOff(EntityBoat boat, EntityPlayer player) {
        boolean any = false;
        if (isLeashed(boat)) {
            dropLeash(boat);
            any = true;
        }
        for (EntityBoat b : heldBy(boat)) {
            dropLeash(b);
            any = true;
        }
        if (any) {
            sound(boat, snip);
        }
        return any;
    }

    /** A player holding boats uses a fence: they are tied to its knot, as 1.12.2's {@code ItemLead.attachToFence} ties mobs. */
    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || event.isCanceled() || !on() || !(world.getBlockState(event.getPos()).getBlock() instanceof BlockFence)) {
            return;
        }
        List<EntityBoat> boats = heldBy(event.getEntityPlayer());
        if (boats.isEmpty()) {
            return;
        }
        EntityLeashKnot knot = knotAt(world, event.getPos());
        for (EntityBoat b : boats) {
            leash(b, knot);
        }
        knot.playPlaceSound();
    }

    /** Using a knot while holding boats ties them to it (and the player's mobs, which vanilla would only do with a lead in hand). */
    private static void knotUsed(PlayerInteractEvent.EntityInteract event, EntityLeashKnot knot) {
        EntityPlayer player = event.getEntityPlayer();
        List<EntityBoat> boats = heldBy(player);
        if (boats.isEmpty()) {
            return;
        }
        for (EntityBoat b : boats) {
            leash(b, knot);
        }
        AxisAlignedBB box = new AxisAlignedBB(knot.posX - REACH, knot.posY - REACH, knot.posZ - REACH,
                knot.posX + REACH, knot.posY + REACH, knot.posZ + REACH);
        for (EntityLiving mob : knot.world.getEntitiesWithinAABB(EntityLiving.class, box)) {
            if (mob.getLeashed() && mob.getLeashHolder() == player) {
                mob.setLeashHolder(knot, true);
            }
        }
        knot.playPlaceSound();
        handled(event);
    }

    private static EntityLeashKnot knotAt(World world, BlockPos pos) {
        EntityLeashKnot knot = EntityLeashKnot.getKnotForPosition(world, pos);
        return knot != null ? knot : EntityLeashKnot.createKnot(world, pos);
    }

    private static void sound(Entity at, SoundEvent sound) {
        at.world.playSound(null, at.posX, at.posY, at.posZ, sound, SoundCategory.NEUTRAL, 1.0F, 1.0F);
    }

    // --- ticking ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void worldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.world instanceof WorldServer) || LEASHED.isEmpty()) {
            return;
        }
        List<Map.Entry<Entity, Data>> boats;
        synchronized (LEASHED) {
            boats = new ArrayList<Map.Entry<Entity, Data>>(LEASHED.entrySet());
        }
        for (Map.Entry<Entity, Data> en : boats) {
            if (en.getKey().world == event.world) {
                tick((WorldServer) event.world, en.getKey(), en.getValue());
            }
        }
    }

    /** One leash tick for a boat, what the world's tick does before the boat moves (for tests and tools). */
    public static void step(WorldServer world, Entity boat) {
        Data d = LEASHED.get(boat);
        if (d != null) {
            tick(world, boat, d);
        }
    }

    /** 1.21.6's {@code Leashable.tickLeash}, before the boat's own update moves it. */
    static void tick(WorldServer world, Entity boat, Data d) {
        if (d.holder == null && !restore(world, boat, d)) {
            return;
        }
        boolean drops = world.getGameRules().getBoolean("doEntityDrops");
        if (boat.isDead) {
            // Broken or killed: the lead drops where it was.
            LEASHED.remove(boat);
            if (drops) {
                boat.entityDropItem(new ItemStack(Items.LEAD), 0.0F);
            }
            return;
        }
        if (world.getEntityByID(boat.getEntityId()) != boat) {
            LEASHED.remove(boat); // unloaded with its chunk: the lead stays in its saved data
            return;
        }
        if (!d.holder.isEntityAlive()) {
            unleash(boat, drops);
            return;
        }
        Entity holder = d.holder;
        if (holder.world != boat.world) {
            return;
        }
        double distance = center(boat).distanceTo(center(holder));
        if (distance > SNAP_DISTANCE) {
            world.playSound(null, holder.posX, holder.posY, holder.posZ, snapped, SoundCategory.NEUTRAL, 1.0F, 1.0F);
            unleash(boat, true);
            return;
        }
        if (distance > ELASTIC_DISTANCE - holder.width - boat.width) {
            elastic(boat, holder, d);
        }
        boat.rotationYaw = (float) (boat.rotationYaw - d.angularMomentum);
        d.angularMomentum *= angularFriction(boat);
    }

    /** {@code restoreLeashFromSave}: the knot (made again if it is gone) or the holder by UUID; given up after 100 ticks. */
    private static boolean restore(WorldServer world, Entity boat, Data d) {
        Entity found = null;
        if (d.knot != null) {
            found = world.isBlockLoaded(d.knot) ? knotAt(world, d.knot) : null;
        } else if (d.holderId != null) {
            found = world.getEntityFromUuid(d.holderId);
        }
        if (found != null) {
            d.holder = found;
            sync(boat, found.getEntityId());
            return true;
        }
        if (boat.ticksExisted > 100) {
            unleash(boat, true);
        }
        return false;
    }

    private static Vec3d center(Entity e) {
        AxisAlignedBB box = e.getEntityBoundingBox();
        return new Vec3d((box.minX + box.maxX) * 0.5D, (box.minY + box.maxY) * 0.5D, (box.minZ + box.maxZ) * 0.5D);
    }

    /** 1.21.6's {@code checkElasticInteractions}. */
    static boolean elastic(Entity boat, Entity holder, Data d) {
        boolean quad = quadHolder.test(holder);
        Vec3d[] onBoat = quad ? QUAD : ONE_ON_ENTITY;
        Vec3d[] onHolder = quad ? QUAD : ONE_ON_HOLDER;
        Vec3d boatMove = movement(boat);
        double fx = 0.0D;
        double fy = 0.0D;
        double fz = 0.0D;
        double torque = 0.0D;
        int n = 0;
        for (int i = 0; i < onBoat.length; i++) {
            Vec3d arm = point(onBoat[i], boat);
            Vec3d at = new Vec3d(boat.posX, boat.posY, boat.posZ).add(arm);
            Vec3d to = new Vec3d(holder.posX, holder.posY, holder.posZ).add(point(onHolder[i], holder));
            double stretch = at.distanceTo(to);
            if (stretch < ELASTIC_DISTANCE) {
                continue;
            }
            Vec3d force = to.subtract(at).normalize().scale(stretch - ELASTIC_DISTANCE);
            double t = arm.z * force.x - arm.x * force.z;
            if (boatMove.dotProduct(force) >= 0.0D) {
                force = force.scale(0.3F);
            }
            fx += force.x;
            fy += force.y;
            fz += force.z;
            torque += t;
            n++;
        }
        if (n == 0) {
            return false;
        }
        double scale = quad ? 0.25D : 1.0D;
        d.angularMomentum += 10.0D * torque * scale;
        Vec3d relative = movement(holder).subtract(boatMove);
        boat.motionX += fx * scale * ELASTICITY.x + relative.x * 0.11D;
        boat.motionY += fy * scale * ELASTICITY.y + relative.y * 0.11D;
        boat.motionZ += fz * scale * ELASTICITY.z + relative.z * 0.11D;
        boat.velocityChanged = true;
        return true;
    }

    /** An attachment point, scaled by the entity's box and turned with its yaw ({@code Vec3.yRot(-yaw)}). */
    private static Vec3d point(Vec3d p, Entity e) {
        double x = p.x * e.width;
        double y = p.y * e.height;
        double z = p.z * e.width;
        float yaw = e.rotationYaw * 0.017453292F;
        double c = MathHelper.cos(yaw);
        double s = MathHelper.sin(yaw);
        return new Vec3d(x * c - z * s, y, z * c + x * s);
    }

    /** {@code getKnownMovement}: a player's from where it stood last tick (the server's motion is not theirs), else the motion. */
    private static Vec3d movement(Entity e) {
        if (e instanceof EntityPlayer) {
            return new Vec3d(e.posX - e.lastTickPosX, e.posY - e.lastTickPosY, e.posZ - e.lastTickPosZ);
        }
        return new Vec3d(e.motionX, e.motionY, e.motionZ);
    }

    /** {@code Leashable.angularFriction}. */
    private static double angularFriction(Entity e) {
        if (e.onGround) {
            BlockPos below = new BlockPos(e.posX, e.getEntityBoundingBox().minY - 0.5D, e.posZ);
            return e.world.getBlockState(below).getBlock().getSlipperiness(e.world.getBlockState(below), e.world, below, e) * 0.91F;
        }
        return e.isInWater() || e.isInLava() ? 0.8F : 0.91F;
    }

}

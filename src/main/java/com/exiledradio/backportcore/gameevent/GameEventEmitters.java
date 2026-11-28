package com.exiledradio.backportcore.gameevent;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.entity.EntityAreaEffectCloud;
import net.minecraft.entity.EntityLeashKnot;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityEnderEye;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityFireworkRocket;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityEvoker;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.monster.EntityVex;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityRabbit;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityEvokerFangs;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockJukebox;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntityParrot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemArmorStand;
import net.minecraft.item.ItemBoat;
import net.minecraft.item.ItemElytra;
import net.minecraft.item.ItemEndCrystal;
import net.minecraft.item.ItemHangingEntity;
import net.minecraft.item.ItemMinecart;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.IShearable;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.NoteBlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Emits vanilla's game events from what 1.12.2 tells mods, for every backport (Wild Update Backport's emitters
 * since 1.0.0, moved here). Nothing runs unless {@link GameEvents#active()}. 1.12.2 has no game events, so each one comes from the
 * nearest hook:
 *
 * <table>
 *   <tr><th>Game event</th><th>From</th></tr>
 *   <tr><td>step, swim, flap</td><td>each living entity's update, when its walked distance passes the next whole block
 *       (vanilla's own step-sound rule): in water a swim, flying mobs a flap; the stepped-on block is the context, so
 *       wool and carpets damp steps</td></tr>
 *   <tr><td>splash</td><td>a living entity entering water</td></tr>
 *   <tr><td>elytra_glide</td><td>every 10 ticks of elytra flight</td></tr>
 *   <tr><td>hit_ground</td><td>{@code LivingFallEvent}; any other entity landing (a dropped item, falling sand): P80.
 *       The block landed on is the context, so wool and carpets dampen it</td></tr>
 *   <tr><td>block_place / block_destroy</td><td>Forge's place and break events (player-made); a snow golem's snow and a
 *       villager's planted crop (the new block notifying its neighbors, the mob beside it); {@code World.destroyBlock}
 *       (P82: sheep on tall grass, villagers harvesting, the wither, a trampled last turtle egg)</td></tr>
 *   <tr><td>block_open/close, block_activate/deactivate, block_change, jukebox_play/stop, entity_place</td>
 *       <td>a player's right-click: the block is compared with itself at the end of the tick (doors, trapdoors and
 *       gates open or close, buttons and levers activate, anything else changing is a block change, and so is one
 *       block becoming another with no place or break event: stripping, tilling, paths, a daylight detector); a held
 *       boat, minecart, armor stand, painting, crystal or spawn egg that was used up placed an entity, and so did any
 *       other used item (not a block) beside which an entity appeared that tick (a cushion); bone meal is silent in
 *       1.19 and 1.20.2's item_interact_finish</td></tr>
 *   <tr><td>block_change (more)</td><td>a cauldron filling with rain (P81); an item put in, turned or knocked out of an
 *       item frame</td></tr>
 *   <tr><td>container_open/close</td><td>Forge's container events, at the clicked block or the player</td></tr>
 *   <tr><td>eat, drink, item_interact_start/finish</td><td>Forge's use-item events; item_interact_finish whenever an
 *       entity stops using an item (P83: a bow or crossbow released, a shield lowered)</td></tr>
 *   <tr><td>entity_place (more)</td><td>a spawner's mob ({@code LivingSpawnEvent.SpecialSpawn}); a chicken laying an
 *       egg</td></tr>
 *   <tr><td>teleport (more), projectile_shoot (more)</td><td>chorus fruit, at where the eater stood; an eye of ender
 *       thrown</td></tr>
 *   <tr><td>entity_die, entity_damage (more)</td><td>a player or projectile hitting an entity that has no living
 *       events: paintings, item frames, armor stands and cushions die; boats, minecarts and an armor stand's first
 *       punch are damaged</td></tr>
 *   <tr><td>projectile_shoot / projectile_land</td><td>a new arrow or thrown thing with an owner joining the world;
 *       {@code ProjectileImpactEvent}</td></tr>
 *   <tr><td>entity_damage / entity_die</td><td>{@code LivingDamageEvent} (context: the attacker) / {@code LivingDeathEvent}</td></tr>
 *   <tr><td>explode, note_block_play, teleport, fluid_pickup/place, shear, entity_interact,
 *       equip, prime_fuse, lightning_strike</td><td>the matching Forge events; a new primed TNT, a creeper lighting;
 *       new lightning bolts in the world's weather list</td></tr>
 *   <tr><td>piston_extend / piston_contract</td><td>neighbor notifications around piston bases</td></tr>
 *   <tr><td>entity_action (1.20)</td><td>a wolf starting to shake itself dry</td></tr>
 *   <tr><td>entity_mount / entity_dismount (1.20)</td><td>{@code EntityMountEvent}, at the vehicle, the rider as the
 *       source</td></tr>
 *   <tr><td>equip / unequip (1.20's rules)</td><td>{@code LivingEquipmentChangeEvent}: a player's armor slots, any slot
 *       of a mob; equip when the new item is something worn, else unequip</td></tr>
 *   <tr><td>1.20.2's new sources</td><td>a witch finishing her potion (drink), a sheep eating grass (eat), a rabbit
 *       nibbling a carrot (block_change), a totem saving a player and bone meal (item_interact_finish), evoker fangs
 *       and vexes (entity_place), a water potion putting out fire (block_destroy); vehicles breaking (entity_die)</td></tr>
 * </table>
 * The 1.20 rows run only when {@link GameEvents#isAtLeast GameEvents.isAtLeast("1.20")}; until then equip is 1.19.2's
 * (a worn item going into an armor slot).
 *
 * <p>Not emitted here: blocks changed by redstone or other non-player causes, dispense_fail, entity_shake, entity_roar
 * for vanilla mobs, block attach/detach. Other backports' blocks and mobs (turtle eggs, foxes, pandas, camels, waxing,
 * berries) post their own through {@link GameEvents#emit}.
 */
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID)
public final class GameEventEmitters {

    /** Per living entity: the whole blocks walked so far, and whether it was in water, as of last tick. */
    private static final Map<Entity, int[]> WALKED = new WeakHashMap<Entity, int[]>();

    /** Right-clicked blocks this tick, compared at the tick's end. */
    private static final List<Click> CLICKS = new ArrayList<Click>();

    /** Per player: the last block they right-clicked, for container events. */
    private static final Map<EntityPlayer, BlockPos> LAST_CLICK = new WeakHashMap<EntityPlayer, BlockPos>();

    /** Pistons that moved this tick. */
    private static final Set<BlockPos> PISTONS = new HashSet<BlockPos>();

    /** Creepers that have started to swell. */
    private static final Set<Entity> SWELLING = Collections.newSetFromMap(new WeakHashMap<Entity, Boolean>());

    /** Wolves shaking themselves dry (1.20's entity_action). */
    private static final Set<Entity> SHAKING = Collections.newSetFromMap(new WeakHashMap<Entity, Boolean>());

    /** {@code EntityWolf.isShaking}, private. */
    private static java.lang.reflect.Field wolfShaking;

    /** Lightning bolts already emitted. */
    private static final Set<Entity> BOLTS = Collections.newSetFromMap(new WeakHashMap<Entity, Boolean>());

    /** Per world: blocks a Forge place or break event covered this tick (a right-clicked block swapped otherwise is a block change). */
    private static final Map<World, Set<BlockPos>> HANDLED = new WeakHashMap<World, Set<BlockPos>>();

    /** Entities that joined a world this tick and may have been placed by a used item (a cushion, a modded boat). */
    private static final List<Entity> JOINED = new ArrayList<Entity>();

    /** Entities a player or projectile hit this tick that no death event covers (paintings, frames, vehicles, armor stands). */
    private static final List<Hit> HITS = new ArrayList<Hit>();

    /** Mobs a spawner is spawning ({@code LivingSpawnEvent.SpecialSpawn}), until they join the world. */
    private static final Set<Entity> FROM_SPAWNERS = Collections.newSetFromMap(new WeakHashMap<Entity, Boolean>());

    /** Witches drinking a potion (1.20.2's drink as they finish). */
    private static final Set<Entity> DRINKING = Collections.newSetFromMap(new WeakHashMap<Entity, Boolean>());

    /** Per rabbit: its carrot timer as of last tick (0 until it eats). */
    private static final Map<Entity, int[]> CARROTS = new WeakHashMap<Entity, int[]>();

    /** Per entity eating chorus fruit: where it stood the tick it finishes (the teleport's origin). */
    private static final Map<Entity, Vec3d> CHORUS = new WeakHashMap<Entity, Vec3d>();

    /** Players a lethal hit landed on while holding a totem, with the totems in their hands before it. */
    private static final Map<EntityPlayerMP, Integer> TOTEMS = new WeakHashMap<EntityPlayerMP, Integer>();

    /** Fire next to where a water potion broke, checked at the tick's end (1.20.2's extinguishing). */
    private static final List<Object[]> DOUSED = new ArrayList<Object[]>();

    /** {@code EntitySheep.sheepTimer} and {@code EntityRabbit.carrotTicks}, private. */
    private static java.lang.reflect.Field sheepTimer;
    private static java.lang.reflect.Field carrotTicks;

    /** Items a backport adds that place an entity when used (its boats): their use is {@code entity_place}. */
    private static final List<Predicate<ItemStack>> ENTITY_PLACERS = new CopyOnWriteArrayList<Predicate<ItemStack>>();

    private GameEventEmitters() {
    }

    /** Adds a backport's entity-placing items (boats and the like, the ones vanilla's classes don't cover). */
    public static void addEntityPlacer(Predicate<ItemStack> items) {
        ENTITY_PLACERS.add(items);
    }

    private static final class Click {
        final World world;
        final EntityPlayer player;
        final BlockPos pos;
        final IBlockState before;
        final ItemStack held;
        /** The held stack as clicked: a stack used up by the click reads as air afterwards. */
        final ItemStack heldCopy;
        final int heldCount;

        Click(World world, EntityPlayer player, BlockPos pos, IBlockState before, ItemStack held) {
            this.world = world;
            this.player = player;
            this.pos = pos;
            this.before = before;
            this.held = held;
            this.heldCopy = held.copy();
            this.heldCount = held.getCount();
        }

        boolean used() {
            return this.held.getCount() < this.heldCount;
        }
    }

    private static final class Hit {
        final Entity target;
        final Entity attacker;
        final boolean framed;

        Hit(Entity target, Entity attacker) {
            this.target = target;
            this.attacker = attacker;
            this.framed = target instanceof EntityItemFrame && !((EntityItemFrame) target).getDisplayedItem().isEmpty();
        }
    }

    private static void emit(World world, GameEvent event, Vec3d pos, Entity source, IBlockState state) {
        GameEvents.emit(world, event, pos, GameEventContext.of(source, state));
    }

    private static Vec3d at(Entity e) {
        return new Vec3d(e.posX, e.posY, e.posZ);
    }

    // --- movement ----------------------------------------------------------------------------

    @SubscribeEvent
    public static void livingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        World world = e.world;
        if (world.isRemote || !GameEvents.active()) {
            return;
        }
        int walked = (int) e.distanceWalkedOnStepModified;
        boolean inWater = e.isInWater();
        int[] last = WALKED.get(e);
        if (last == null) {
            last = new int[] {walked, inWater ? 1 : 0};
            WALKED.put(e, last);
        }
        if (inWater && last[1] == 0 && e.ticksExisted > 1) {
            emit(world, GameEvent.SPLASH, at(e), e, null);
        }
        last[1] = inWater ? 1 : 0;
        if (walked > last[0]) {
            last[0] = walked;
            if (!e.isRiding()) {
                if (e instanceof EntityFlying || e instanceof EntityBat || (e instanceof EntityParrot && !e.onGround)) {
                    emit(world, GameEvent.FLAP, at(e), e, null);
                } else if (inWater) {
                    emit(world, GameEvent.SWIM, at(e), e, null);
                } else if (e.onGround) {
                    BlockPos below = new BlockPos(e.posX, e.posY - 0.2D, e.posZ);
                    IBlockState ground = world.getBlockState(below);
                    if (ground.getMaterial() != Material.AIR) {
                        emit(world, GameEvent.STEP, at(e), e, ground);
                    }
                }
            }
        } else if (walked < last[0]) {
            last[0] = walked;
        }
        if (e.isElytraFlying() && ticksElytraFlying(e) % 10 == 0) {
            emit(world, GameEvent.ELYTRA_GLIDE, at(e), e, null);
        }
        if (e instanceof net.minecraft.entity.passive.EntityWolf && GameEvents.isAtLeast("1.20")) {
            boolean shaking = wolfShaking(e);
            if (shaking && SHAKING.add(e)) {
                // 1.20's Wolf.tick: entity_action as the shake starts (shakeAnim 0).
                emit(world, GameEvent.ENTITY_ACTION, at(e), e, null);
            } else if (!shaking) {
                SHAKING.remove(e);
            }
        }
        if (e instanceof EntityCreeper) {
            boolean swelling = ((EntityCreeper) e).getCreeperState() > 0;
            if (swelling && SWELLING.add(e)) {
                emit(world, GameEvent.PRIME_FUSE, at(e), e, null);
            } else if (!swelling) {
                SWELLING.remove(e);
            }
        }
        if (e instanceof EntityChicken) {
            chicken((EntityChicken) e);
        }
        if (GameEvents.isAtLeast("1.20")) {
            if (e instanceof EntityWitch) {
                witch((EntityWitch) e);
            } else if (e instanceof EntitySheep) {
                sheep((EntitySheep) e);
            } else if (e instanceof EntityRabbit) {
                rabbit((EntityRabbit) e);
            }
        }
    }

    /**
     * 1.19's {@code Chicken.aiStep}: laying an egg is {@code entity_place}. 1.12.2 lays it later this tick when
     * {@code --timeUntilNextEgg <= 0}, under the same conditions.
     */
    private static void chicken(EntityChicken c) {
        if (!c.isChild() && !c.isChickenJockey() && c.timeUntilNextEgg <= 1) {
            emit(c.world, GameEvent.ENTITY_PLACE, at(c), c, null);
        }
    }

    /** 1.20.2's {@code Witch.aiStep}: drink when a witch finishes drinking her potion (1.12.2 clears the flag then). */
    private static void witch(EntityWitch w) {
        if (w.isDrinkingPotion()) {
            DRINKING.add(w);
        } else if (DRINKING.remove(w)) {
            emit(w.world, GameEvent.DRINK, at(w), w, null);
        }
    }

    /**
     * 1.20.2's {@code EatBlockGoal}: eat as a sheep eats grass. 1.12.2's goal eats when its timer reaches 4, and
     * the sheep copies the timer at the end of its AI update, so it reads 4 for exactly the tick after.
     */
    private static void sheep(EntitySheep sheep) {
        try {
            if (sheepTimer == null) {
                sheepTimer = net.minecraftforge.fml.common.ObfuscationReflectionHelper.findField(EntitySheep.class, "field_70899_e");
            }
            if (sheepTimer.getInt(sheep) == 4) {
                emit(sheep.world, GameEvent.EAT, at(sheep), sheep, null);
            }
        } catch (RuntimeException | IllegalAccessException ex) {
            // left silent
        }
    }

    /**
     * 1.20.2's {@code Rabbit.RaidGardenGoal}: a rabbit nibbling a carrot that keeps a stage is a block change at the
     * carrot (eating the last stage leaves air, and 1.12.2's {@code destroyBlock} of that air does nothing, as in
     * vanilla). 1.12.2 sets the rabbit's carrot timer from 0 to 40 as it eats.
     */
    private static void rabbit(EntityRabbit rabbit) {
        int ticks;
        try {
            if (carrotTicks == null) {
                carrotTicks = net.minecraftforge.fml.common.ObfuscationReflectionHelper.findField(EntityRabbit.class, "field_175541_bs");
            }
            ticks = carrotTicks.getInt(rabbit);
        } catch (RuntimeException | IllegalAccessException ex) {
            return;
        }
        int[] last = CARROTS.get(rabbit);
        if (last == null) {
            CARROTS.put(rabbit, new int[] {ticks});
            return;
        }
        boolean ate = last[0] == 0 && ticks > 0;
        last[0] = ticks;
        if (!ate) {
            return;
        }
        BlockPos feet = new BlockPos(rabbit);
        for (BlockPos p : BlockPos.getAllInBox(feet.add(-1, -1, -1), feet.add(1, 1, 1))) {
            IBlockState state = rabbit.world.getBlockState(p);
            if (state.getBlock() instanceof net.minecraft.block.BlockCarrot && rabbit.getDistanceSqToCenter(p) <= 1.0D) {
                GameEvents.emit(rabbit.world, GameEvent.BLOCK_CHANGE, p.toImmutable(), GameEventContext.of(rabbit, state));
                return;
            }
        }
    }

    private static boolean wolfShaking(Entity wolf) {
        try {
            if (wolfShaking == null) {
                wolfShaking = net.minecraftforge.fml.common.ObfuscationReflectionHelper.findField(
                        net.minecraft.entity.passive.EntityWolf.class, "field_70928_h");
            }
            return wolfShaking.getBoolean(wolf);
        } catch (RuntimeException | IllegalAccessException e) {
            return false;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fall(LivingFallEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (!event.isCanceled() && event.getDistance() > 0.0F && !e.world.isRemote) {
            // 1.19 skips the event on wool; 1.20 passes the block landed on, which wool and carpets dampen.
            IBlockState ground = e.world.getBlockState(new BlockPos(e.posX, e.posY - 0.2D, e.posZ));
            emit(e.world, GameEvent.HIT_GROUND, at(e), e, ground.getMaterial() == Material.AIR ? null : ground);
        }
    }

    // --- blocks ------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void place(BlockEvent.PlaceEvent event) {
        if (!event.isCanceled() && !changedInPlace(event)) {
            handled(event.getWorld(), event.getPos());
            if (event instanceof BlockEvent.MultiPlaceEvent) {
                for (net.minecraftforge.common.util.BlockSnapshot snap : ((BlockEvent.MultiPlaceEvent) event).getReplacedBlockSnapshots()) {
                    handled(event.getWorld(), snap.getPos());
                }
            }
            GameEvents.emit(event.getWorld(), GameEvent.BLOCK_PLACE, event.getPos(), GameEventContext.of(event.getPlayer(), event.getPlacedBlock()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (!event.isCanceled()) {
            handled(event.getWorld(), event.getPos());
            GameEvents.emit(event.getWorld(), GameEvent.BLOCK_DESTROY, event.getPos(), GameEventContext.of(event.getPlayer(), event.getState()));
        }
    }

    /**
     * Forge fires a place event for every block an item's {@code onItemUse} sets (it captures them), so a hoe, a
     * shovel, bone meal or a disc going into a jukebox "places" the block it changed. Vanilla's items post a block
     * change or nothing there, so such an event is left to the right-click comparison: an item that is not a block,
     * whose first block replaced a solid one in place.
     */
    static boolean changedInPlace(BlockEvent.PlaceEvent event) {
        EntityPlayer p = event.getPlayer();
        if (p == null || event.getHand() == null || p.getHeldItem(event.getHand()).getItem() instanceof ItemBlock) {
            return false;
        }
        Material replaced = event.getBlockSnapshot().getReplacedBlock().getMaterial();
        return replaced != Material.AIR && !replaced.isLiquid() && !replaced.isReplaceable();
    }

    private static void handled(World world, BlockPos pos) {
        if (world.isRemote) {
            return;
        }
        Set<BlockPos> set = HANDLED.get(world);
        if (set == null) {
            set = new HashSet<BlockPos>();
            HANDLED.put(world, set);
        }
        set.add(pos.toImmutable());
    }

    private static boolean wasHandled(World world, BlockPos pos) {
        Set<BlockPos> set = HANDLED.get(world);
        return set != null && set.contains(pos);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        if (world.isRemote || event.isCanceled() || !GameEvents.active()) {
            return;
        }
        BlockPos pos = event.getPos();
        CLICKS.add(new Click(world, event.getEntityPlayer(), pos, world.getBlockState(pos), event.getItemStack()));
        LAST_CLICK.put(event.getEntityPlayer(), pos);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getWorld().isRemote && !event.isCanceled() && GameEvents.active() && placesEntity(event.getItemStack())) {
            EntityPlayer p = event.getEntityPlayer();
            CLICKS.add(new Click(event.getWorld(), p, new BlockPos(p), event.getWorld().getBlockState(new BlockPos(p)), event.getItemStack()));
        }
    }

    private static boolean placesEntity(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof ItemBoat || stack.getItem() instanceof ItemMinecart
                || stack.getItem() instanceof ItemArmorStand || stack.getItem() instanceof ItemHangingEntity
                || stack.getItem() instanceof ItemEndCrystal || stack.getItem() instanceof ItemMonsterPlacer
                || placedByBackport(stack));
    }

    private static boolean placedByBackport(ItemStack stack) {
        for (Predicate<ItemStack> p : ENTITY_PLACERS) {
            if (p.test(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Settles what this tick's clicks, hits, lethal hits and water potions did, as the tick ends. */
    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        PISTONS.clear();
        List<Entity> joined = new ArrayList<Entity>(JOINED);
        JOINED.clear();
        if (!CLICKS.isEmpty()) {
            clicks(joined);
        }
        if (!HITS.isEmpty()) {
            hits();
        }
        if (!TOTEMS.isEmpty()) {
            totems();
        }
        if (!DOUSED.isEmpty()) {
            doused();
        }
        HANDLED.clear();
    }

    /** Compares the tick's right-clicked blocks with themselves. */
    private static void clicks(List<Entity> joined) {
        List<Click> clicks = new ArrayList<Click>(CLICKS);
        CLICKS.clear();
        Set<BlockPos> done = new HashSet<BlockPos>();
        for (Click c : clicks) {
            IBlockState now = c.world.getBlockState(c.pos);
            boolean used = c.used() || c.player.capabilities.isCreativeMode;
            if (placesEntity(c.heldCopy) && used) {
                emit(c.world, GameEvent.ENTITY_PLACE, new Vec3d(c.pos).add(0.5D, 1.0D, 0.5D), c.player, null);
                continue;
            }
            if (c.heldCopy.getItem() == Items.ENDER_EYE) {
                continue; // an eye into an end portal frame is not heard (1.19's EnderEyeItem posts nothing)
            }
            if (isBoneMeal(c.heldCopy)) {
                // 1.20.2's BoneMealItem.useOn: item_interact_finish from the player; the plant itself grows silently
                // (1.19 posts nothing).
                if ((c.used() || c.player.capabilities.isCreativeMode && now != c.before) && GameEvents.isAtLeast("1.20")) {
                    emit(c.world, GameEvent.ITEM_INTERACT_FINISH, at(c.player), c.player, null);
                }
                continue;
            }
            if (used && !(c.heldCopy.getItem() instanceof ItemBlock)) {
                Entity placed = placedNear(joined, c);
                if (placed != null) {
                    // An item placing an entity its class does not tell (a cushion, another mod's boat).
                    joined.remove(placed);
                    emit(c.world, GameEvent.ENTITY_PLACE, at(placed), c.player, null);
                    continue;
                }
            }
            if (now == c.before || !done.add(c.pos)) {
                continue;
            }
            if (now.getBlock() != c.before.getBlock()) {
                // Placed or broken: Forge's own events covered it. Otherwise one block became another in place,
                // which 1.19 posts as a block change: stripping a log, tilling, making a path, a daylight detector.
                if (swapped(c, now)) {
                    GameEvents.emit(c.world, GameEvent.BLOCK_CHANGE, c.pos, GameEventContext.of(c.player, now));
                }
                continue;
            }
            GameEvent event2 = changeEvent(c.before, now);
            if (event2 != null) {
                GameEvents.emit(c.world, event2, c.pos, GameEventContext.of(c.player, now));
            }
        }
    }

    /** @return true if a right-click turned one block into another in place, with no place or break event */
    private static boolean swapped(Click c, IBlockState now) {
        Material before = c.before.getMaterial();
        Material after = now.getMaterial();
        return before != Material.AIR && after != Material.AIR && !before.isLiquid() && !after.isLiquid()
                && !wasHandled(c.world, c.pos);
    }

    static boolean isBoneMeal(ItemStack stack) {
        return stack.getItem() == Items.DYE && net.minecraft.item.EnumDyeColor.byDyeDamage(stack.getMetadata())
                == net.minecraft.item.EnumDyeColor.WHITE;
    }

    /** @return an entity that joined this tick next to the clicked face, or null */
    private static Entity placedNear(List<Entity> joined, Click c) {
        Vec3d center = new Vec3d(c.pos).add(0.5D, 0.5D, 0.5D);
        for (Entity e : joined) {
            if (e.world == c.world && !e.isDead && e.getDistanceSq(center.x, center.y, center.z) <= 4.0D) {
                return e;
            }
        }
        return null;
    }

    /** Entities that a used item may have placed: none of the kinds that come from anything else. */
    private static boolean placeable(Entity e) {
        return !(e instanceof EntityLivingBase) && !(e instanceof EntityItem) && !(e instanceof EntityXPOrb)
                && !(e instanceof IProjectile) && !(e instanceof net.minecraft.entity.effect.EntityWeatherEffect)
                && !(e instanceof EntityFallingBlock) && !(e instanceof EntityTNTPrimed) && !(e instanceof EntityFireworkRocket)
                && !(e instanceof EntityEnderEye) && !(e instanceof EntityFishHook) && !(e instanceof EntityAreaEffectCloud)
                && !(e instanceof EntityLeashKnot) && !(e instanceof EntityEvokerFangs);
    }

    /**
     * What hitting an entity that no damage or death event covers did: 1.19's {@code Entity.kill} posts entity_die
     * (paintings, item frames, armor stands, cushions); an item frame losing its item is a block change; boats,
     * minecarts and an armor stand's first punch are entity_damage. Vehicles post entity_die only from 1.20.3's
     * {@code VehicleEntity}.
     */
    private static void hits() {
        List<Hit> hits = new ArrayList<Hit>(HITS);
        HITS.clear();
        Set<Entity> done = Collections.newSetFromMap(new java.util.IdentityHashMap<Entity, Boolean>());
        for (Hit h : hits) {
            Entity t = h.target;
            if (!done.add(t)) {
                continue;
            }
            boolean vehicle = t instanceof EntityBoat || t instanceof EntityMinecart;
            if (vehicle) {
                emit(t.world, GameEvent.ENTITY_DAMAGE, at(t), h.attacker, null);
                if (t.isDead && GameEvents.isAtLeast("1.20")) {
                    emit(t.world, GameEvent.ENTITY_DIE, at(t), t, null);
                }
            } else if (t.isDead) {
                emit(t.world, GameEvent.ENTITY_DIE, at(t), t, null);
            } else if (h.framed && ((EntityItemFrame) t).getDisplayedItem().isEmpty()) {
                emit(t.world, GameEvent.BLOCK_CHANGE, at(t), h.attacker, null);
            } else if (t instanceof EntityArmorStand) {
                emit(t.world, GameEvent.ENTITY_DAMAGE, at(t), h.attacker, null);
            }
        }
    }

    /** Entities a hit is followed for: those without {@code LivingDamageEvent} and {@code LivingDeathEvent}. */
    private static boolean followsHits(Entity e) {
        return !(e instanceof EntityLivingBase) || e instanceof EntityArmorStand;
    }

    private static void hit(Entity target, Entity attacker) {
        if (target != null && !target.isDead && !target.world.isRemote && followsHits(target) && GameEvents.active()) {
            HITS.add(new Hit(target, attacker));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void attack(AttackEntityEvent event) {
        if (!event.isCanceled()) {
            hit(event.getTarget(), event.getEntityPlayer());
        }
    }

    /** 1.20.2's {@code LivingEntity.checkTotemDeathProtection}: a player saved by a totem posts item_interact_finish. */
    private static void totems() {
        Map<EntityPlayerMP, Integer> pending = new java.util.HashMap<EntityPlayerMP, Integer>(TOTEMS);
        TOTEMS.clear();
        for (Map.Entry<EntityPlayerMP, Integer> en : pending.entrySet()) {
            EntityPlayerMP p = en.getKey();
            if (!p.isDead && p.getHealth() > 0.0F && totemsHeld(p) < en.getValue()) {
                emit(p.world, GameEvent.ITEM_INTERACT_FINISH, at(p), p, null);
            }
        }
    }

    /** Totems in both hands: 1.12.2's {@code checkTotemDeathProtection} uses one up when it saves the player. */
    private static int totemsHeld(EntityLivingBase e) {
        int n = 0;
        for (ItemStack s : new ItemStack[] {e.getHeldItemMainhand(), e.getHeldItemOffhand()}) {
            if (s.getItem() == Items.TOTEM_OF_UNDYING) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** 1.20.2's {@code ThrownPotion.dowseFire}: fire a water potion put out is a block destroyed, the potion its source. */
    private static void doused() {
        List<Object[]> doused = new ArrayList<Object[]>(DOUSED);
        DOUSED.clear();
        for (Object[] d : doused) {
            World world = (World) d[0];
            BlockPos pos = (BlockPos) d[1];
            if (world.getBlockState(pos).getBlock() != net.minecraft.init.Blocks.FIRE) {
                GameEvents.emit(world, GameEvent.BLOCK_DESTROY, pos, GameEventContext.of((Entity) d[2],
                        net.minecraft.init.Blocks.FIRE.getDefaultState()));
            }
        }
    }

    /** @return what a player's right-click that changed a block's state is, as 1.19 names it */
    static GameEvent changeEvent(IBlockState before, IBlockState now) {
        Block block = now.getBlock();
        if (block instanceof BlockDoor || block instanceof BlockTrapDoor || block instanceof BlockFenceGate) {
            Boolean open = bool(now, "open");
            if (open != null && !open.equals(bool(before, "open"))) {
                return open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE;
            }
        }
        if (block instanceof BlockButton || block instanceof BlockLever) {
            Boolean powered = bool(now, "powered");
            if (powered != null && !powered.equals(bool(before, "powered"))) {
                return powered ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE;
            }
        }
        if (block instanceof BlockJukebox) {
            return now.getValue(BlockJukebox.HAS_RECORD) ? GameEvent.JUKEBOX_PLAY : GameEvent.JUKEBOX_STOP_PLAY;
        }
        return GameEvent.BLOCK_CHANGE;
    }

    private static Boolean bool(IBlockState state, String name) {
        for (IProperty<?> p : state.getPropertyKeys()) {
            if (p instanceof PropertyBool && p.getName().equals(name)) {
                return state.getValue((PropertyBool) p);
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void containerOpen(PlayerContainerEvent.Open event) {
        EntityPlayer p = event.getEntityPlayer();
        if (!p.world.isRemote) {
            containerEvent(p, GameEvent.CONTAINER_OPEN);
        }
    }

    @SubscribeEvent
    public static void containerClose(PlayerContainerEvent.Close event) {
        EntityPlayer p = event.getEntityPlayer();
        if (!p.world.isRemote && event.getContainer() != p.inventoryContainer) {
            containerEvent(p, GameEvent.CONTAINER_CLOSE);
        }
    }

    private static void containerEvent(EntityPlayer p, GameEvent event) {
        BlockPos clicked = LAST_CLICK.get(p);
        TileEntity tile = clicked != null && clicked.distanceSq(p.getPosition()) < 100 ? p.world.getTileEntity(clicked) : null;
        if (tile != null) {
            GameEvents.emit(p.world, event, clicked, GameEventContext.of(p, p.world.getBlockState(clicked)));
        } else {
            emit(p.world, event, at(p), p, null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void note(NoteBlockEvent.Play event) {
        if (!event.isCanceled()) {
            GameEvents.emit(event.getWorld(), GameEvent.NOTE_BLOCK_PLAY, event.getPos(), GameEventContext.NONE);
        }
    }

    /**
     * Pistons (Forge 14.23.5.2847 has no piston event): a piston base whose own state change notifies its
     * neighbors with {@code extended=true} has extended; a block next to a retracted piston that faces it
     * changing (the head going) means it contracted. Each piston once per tick.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void neighborNotify(BlockEvent.NeighborNotifyEvent event) {
        World world = event.getWorld();
        if (world.isRemote || event.isCanceled()) {
            return;
        }
        BlockPos pos = event.getPos();
        IBlockState state = event.getState();
        if (GameEvents.active() && !wasHandled(world, pos)) {
            mobPlaced(world, pos, state);
        }
        if (state.getBlock() instanceof BlockPistonBase) {
            if (state.getValue(BlockPistonBase.EXTENDED) && PISTONS.add(pos.toImmutable())) {
                GameEvents.emit(world, GameEvent.PISTON_EXTEND, pos, GameEventContext.NONE);
            }
            return;
        }
        for (EnumFacing f : EnumFacing.values()) {
            BlockPos base = pos.offset(f);
            IBlockState s = world.getBlockState(base);
            if (s.getBlock() instanceof BlockPistonBase && s.getValue(BlockDirectional.FACING) == f.getOpposite()
                    && !s.getValue(BlockPistonBase.EXTENDED) && PISTONS.add(base.toImmutable())) {
                GameEvents.emit(world, GameEvent.PISTON_CONTRACT, base, GameEventContext.NONE);
            }
        }
    }

    /**
     * Blocks mobs place, seen as the block notifies its neighbors (no player placed it this tick): 1.19's
     * {@code SnowGolem.aiStep} and {@code HarvestFarmland} post block_place with the mob as the source, for a snow
     * golem's trail and a villager's planted crop.
     */
    private static void mobPlaced(World world, BlockPos pos, IBlockState state) {
        Block block = state.getBlock();
        AxisAlignedBB near = new AxisAlignedBB(pos).grow(1.0D);
        if (block == Blocks.SNOW_LAYER) {
            for (EntitySnowman golem : world.getEntitiesWithinAABB(EntitySnowman.class, near)) {
                if (trailsAt(golem, pos)) {
                    GameEvents.emit(world, GameEvent.BLOCK_PLACE, pos, GameEventContext.of(golem, state));
                    return;
                }
            }
        } else if (block instanceof BlockCrops && state == block.getDefaultState()) {
            List<EntityVillager> villagers = world.getEntitiesWithinAABB(EntityVillager.class, near);
            if (!villagers.isEmpty()) {
                GameEvents.emit(world, GameEvent.BLOCK_PLACE, pos, GameEventContext.of(villagers.get(0), state));
            }
        }
    }

    /** @return true if this is one of the four blocks 1.12.2's {@code EntitySnowman.onLivingUpdate} covers in snow */
    private static boolean trailsAt(EntitySnowman golem, BlockPos pos) {
        for (int l = 0; l < 4; ++l) {
            int x = MathHelper.floor(golem.posX + (double) ((float) (l % 2 * 2 - 1) * 0.25F));
            int y = MathHelper.floor(golem.posY);
            int z = MathHelper.floor(golem.posZ + (double) ((float) (l / 2 % 2 * 2 - 1) * 0.25F));
            if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void explode(ExplosionEvent.Detonate event) {
        Vec3d p = event.getExplosion().getPosition();
        GameEvents.emit(event.getWorld(), GameEvent.EXPLODE, p, GameEventContext.of(event.getExplosion().getExplosivePlacedBy()));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void bucket(FillBucketEvent event) {
        RayTraceResult hit = event.getTarget();
        if (event.isCanceled() || hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || event.getWorld().isRemote) {
            return;
        }
        boolean empty = event.getEmptyBucket().getItem() == Items.BUCKET;
        BlockPos pos = empty ? hit.getBlockPos() : hit.getBlockPos().offset(hit.sideHit);
        GameEvents.emit(event.getWorld(), empty ? GameEvent.FLUID_PICKUP : GameEvent.FLUID_PLACE, pos, GameEventContext.of(event.getEntityPlayer()));
    }

    // --- entities ----------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void interactEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || event.getWorld().isRemote) {
            return;
        }
        Entity target = event.getTarget();
        ItemStack held = event.getItemStack();
        if (held.getItem() instanceof ItemShears && target instanceof IShearable
                && ((IShearable) target).isShearable(held, event.getWorld(), new BlockPos(target))) {
            emit(event.getWorld(), GameEvent.SHEAR, at(target), event.getEntityPlayer(), null);
        } else if (target instanceof EntityItemFrame) {
            // 1.19's ItemFrame.interact: putting an item in or turning it is a block change; an empty hand at an
            // empty frame does nothing.
            if (!held.isEmpty() || !((EntityItemFrame) target).getDisplayedItem().isEmpty()) {
                emit(event.getWorld(), GameEvent.BLOCK_CHANGE, at(target), event.getEntityPlayer(), null);
            }
        } else {
            emit(event.getWorld(), GameEvent.ENTITY_INTERACT, at(target), event.getEntityPlayer(), null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (!event.isCanceled() && event.getAmount() > 0.0F && !e.world.isRemote) {
            emit(e.world, GameEvent.ENTITY_DAMAGE, at(e), event.getSource().getTrueSource(), null);
            if (e instanceof EntityPlayerMP && event.getAmount() >= e.getHealth() && totemsHeld(e) > 0 && GameEvents.isAtLeast("1.20")) {
                // Whether the totem saved them is known once the hit is over (totems()).
                TOTEMS.put((EntityPlayerMP) e, totemsHeld(e));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (!event.isCanceled() && !e.world.isRemote) {
            emit(e.world, GameEvent.ENTITY_DIE, at(e), e, null);
        }
    }

    @SubscribeEvent
    public static void useStart(LivingEntityUseItemEvent.Start event) {
        EntityLivingBase e = event.getEntityLiving();
        if (!e.world.isRemote) {
            emit(e.world, GameEvent.ITEM_INTERACT_START, at(e), e, null);
        }
    }

    @SubscribeEvent
    public static void useFinish(LivingEntityUseItemEvent.Finish event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote) {
            return;
        }
        EnumAction action = event.getItem().getItemUseAction();
        if (action == EnumAction.EAT) {
            emit(e.world, GameEvent.EAT, at(e), e, null);
        } else if (action == EnumAction.DRINK) {
            emit(e.world, GameEvent.DRINK, at(e), e, null);
        }
        if (event.getItem().getItem() == Items.CHORUS_FRUIT) {
            // 1.19's ChorusFruitItem: teleport at where the eater stood, when the teleport worked.
            Vec3d from = CHORUS.remove(e);
            if (from != null && from.squareDistanceTo(at(e)) > 1.0E-6D) {
                emit(e.world, GameEvent.TELEPORT, from, e, null);
            }
        }
        if (!"applied".equals(com.exiledradio.backportcore.asm.Asm.state("P83"))) {
            // Without P83 only finished uses are heard (P83 posts it for every stop, as 1.19 does).
            emit(e.world, GameEvent.ITEM_INTERACT_FINISH, at(e), e, null);
        }
    }

    /** Where an entity finishing chorus fruit stands before 1.12.2 teleports it, the same tick. */
    @SubscribeEvent
    public static void useTick(LivingEntityUseItemEvent.Tick event) {
        EntityLivingBase e = event.getEntityLiving();
        if (!e.world.isRemote && event.getDuration() <= 1 && event.getItem().getItem() == Items.CHORUS_FRUIT && GameEvents.active()) {
            CHORUS.put(e, at(e));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void equip(LivingEquipmentChangeEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (e.world.isRemote || e.ticksExisted < 2) {
            return;
        }
        if (GameEvents.isAtLeast("1.20")) {
            equip120(e, event.getSlot(), event.getFrom(), event.getTo());
            return;
        }
        if (event.getSlot().getSlotType() != EntityEquipmentSlot.Type.ARMOR || event.getTo().isEmpty()) {
            return;
        }
        emit(e.world, GameEvent.EQUIP, at(e), e, null);
    }

    /** 1.20.6's {@code LivingEntity.onEquipItem} (players: {@code doesEmitEquipEvent} only for armor). */
    private static void equip120(EntityLivingBase e, EntityEquipmentSlot slot, ItemStack from, ItemStack to) {
        if ((from.isEmpty() && to.isEmpty()) || ItemStack.areItemStacksEqual(from, to)) {
            return;
        }
        if (e instanceof EntityPlayer && (((EntityPlayer) e).isSpectator() || slot.getSlotType() != EntityEquipmentSlot.Type.ARMOR)) {
            return;
        }
        emit(e.world, isWorn(to) ? GameEvent.EQUIP : GameEvent.UNEQUIP, at(e), e, null);
    }

    private static final java.lang.reflect.Field TICKS_ELYTRA_FLYING =
            net.minecraftforge.fml.common.ObfuscationReflectionHelper.findField(EntityLivingBase.class, "field_184629_bo");

    /**
     * {@code ticksElytraFlying}, read from the field: its getter {@code getTicksElytraFlying} is client-only, and calling
     * it on a dedicated server threw NoSuchMethodError and kicked every player who opened an elytra.
     */
    private static int ticksElytraFlying(EntityLivingBase e) {
        try {
            return TICKS_ELYTRA_FLYING.getInt(e);
        } catch (IllegalAccessException ex) {
            return 0;
        }
    }

    /** 1.20's {@code Equipable.get(stack) != null}: armor, elytra, a carved pumpkin, heads, a shield. */
    static boolean isWorn(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        net.minecraft.item.Item item = stack.getItem();
        return item instanceof ItemArmor || item instanceof ItemElytra || item instanceof ItemSkull || item instanceof ItemShield
                || item == net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.PUMPKIN)
                || item.getEquipmentSlot(stack) != null;
    }

    /** 1.20's {@code Entity.addPassenger} / {@code removePassenger}: at the vehicle, the rider as the source. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void mount(EntityMountEvent event) {
        World world = event.getWorldObj();
        if (event.isCanceled() || world == null || world.isRemote || !GameEvents.isAtLeast("1.20")
                || event.getEntityBeingMounted() == null) {
            return;
        }
        emit(world, event.isMounting() ? GameEvent.ENTITY_MOUNT : GameEvent.ENTITY_DISMOUNT, at(event.getEntityBeingMounted()),
                event.getEntityMounting(), null);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void teleport(EnderTeleportEvent event) {
        EntityLivingBase e = event.getEntityLiving();
        if (!event.isCanceled() && !e.world.isRemote) {
            emit(e.world, GameEvent.TELEPORT, at(e), e, null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinWorldEvent event) {
        Entity e = event.getEntity();
        World world = event.getWorld();
        if (event.isCanceled() || world.isRemote || e.ticksExisted > 0) {
            return;
        }
        if (FROM_SPAWNERS.remove(e)) {
            // 1.19's BaseSpawner: entity_place at the mob it spawned.
            emit(world, GameEvent.ENTITY_PLACE, at(e), e, null);
        }
        if (GameEvents.active() && placeable(e)) {
            JOINED.add(e);
        }
        if (e instanceof EntityEnderEye) {
            // 1.19's EnderEyeItem.use: projectile_shoot at the thrower (1.12.2's eye has no owner; it starts at
            // the thrower's middle).
            EntityPlayer thrower = world.getClosestPlayer(e.posX, e.posY, e.posZ, 2.0D, false);
            if (thrower != null) {
                emit(world, GameEvent.PROJECTILE_SHOOT, at(thrower), thrower, null);
            }
        } else if (e instanceof EntityEvokerFangs || e instanceof EntityVex) {
            // 1.20.2's Evoker spells: entity_place for each fang and vex, the evoker as the source.
            Entity caster = e instanceof EntityVex ? ((EntityVex) e).getOwner() : ((EntityEvokerFangs) e).getCaster();
            if (caster instanceof EntityEvoker && GameEvents.isAtLeast("1.20")) {
                emit(world, GameEvent.ENTITY_PLACE, at(e), caster, null);
            }
        }
        if (e instanceof IProjectile) {
            Entity owner = e instanceof EntityArrow ? ((EntityArrow) e).shootingEntity
                    : e instanceof EntityThrowable ? ((EntityThrowable) e).getThrower() : null;
            if (owner != null) {
                emit(world, GameEvent.PROJECTILE_SHOOT, at(e), owner, null);
            }
        } else if (e instanceof EntityTNTPrimed && ((EntityTNTPrimed) e).getFuse() >= 79) {
            emit(world, GameEvent.PRIME_FUSE, at(e), ((EntityTNTPrimed) e).getTntPlacedBy(), null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void impact(ProjectileImpactEvent event) {
        Entity e = event.getEntity();
        if (!event.isCanceled() && !e.world.isRemote && event.getRayTraceResult() != null) {
            RayTraceResult result = event.getRayTraceResult();
            Vec3d hit = result.hitVec;
            emit(e.world, GameEvent.PROJECTILE_LAND, hit != null ? hit : at(e), e, null);
            if (result.typeOfHit == RayTraceResult.Type.ENTITY) {
                hit(result.entityHit, owner(e));
            } else if (result.typeOfHit == RayTraceResult.Type.BLOCK && e instanceof EntityPotion && isWater((EntityPotion) e)
                    && GameEvents.isAtLeast("1.20")) {
                // 1.12.2's EntityPotion.onImpact puts out fire at the block it hit, and beside it.
                BlockPos at = result.getBlockPos().offset(result.sideHit);
                for (BlockPos p : new BlockPos[] {at, at.north(), at.south(), at.east(), at.west()}) {
                    if (e.world.getBlockState(p).getBlock() == Blocks.FIRE) {
                        DOUSED.add(new Object[] {e.world, p, e});
                    }
                }
            }
        }
    }

    private static boolean isWater(EntityPotion potion) {
        ItemStack stack = potion.getPotion();
        return net.minecraft.potion.PotionUtils.getPotionFromItem(stack) == net.minecraft.init.PotionTypes.WATER
                && net.minecraft.potion.PotionUtils.getEffectsFromStack(stack).isEmpty();
    }

    private static Entity owner(Entity projectile) {
        return projectile instanceof EntityArrow ? ((EntityArrow) projectile).shootingEntity
                : projectile instanceof EntityThrowable ? ((EntityThrowable) projectile).getThrower() : projectile;
    }

    /** A spawner's mob, before it joins the world (join() posts its entity_place). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void spawnerSpawn(LivingSpawnEvent.SpecialSpawn event) {
        if (event.getSpawner() != null && !event.getWorld().isRemote && GameEvents.active()) {
            FROM_SPAWNERS.add(event.getEntityLiving());
        }
    }

    @SubscribeEvent
    public static void worldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.world instanceof WorldServer) || event.world.weatherEffects.isEmpty()) {
            return;
        }
        for (Entity bolt : event.world.weatherEffects) {
            if (bolt instanceof EntityLightningBolt && BOLTS.add(bolt)) {
                emit(event.world, GameEvent.LIGHTNING_STRIKE, at(bolt), null, null);
            }
        }
    }
}

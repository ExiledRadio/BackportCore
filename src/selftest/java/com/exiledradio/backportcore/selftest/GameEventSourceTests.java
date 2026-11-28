package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.gameevent.GameEvent;
import com.exiledradio.backportcore.gameevent.GameEventContext;
import com.exiledradio.backportcore.gameevent.GameEventListener;
import com.exiledradio.backportcore.gameevent.GameEvents;
import com.exiledradio.backportcore.gameevent.PositionSource;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.item.EntityEnderEye;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.monster.EntityEvoker;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.monster.EntityVex;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntityRabbit;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.projectile.EntityEvokerFangs;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.PotionTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionUtils;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * The vibration sources the xisumavoid audit found missing (Wild Update, Trails &amp; Tales, Game Drops), each driven
 * through the 1.12.2 code that makes it: landing items (P80), block swaps, bone meal, item use stopping (P83), mobs
 * placing, eating and drinking, cauldrons (P81), {@code destroyBlock} (P82), spawners, chorus fruit, eyes of ender,
 * entities placed by items, hits on paintings, frames, vehicles and armor stands, totems, evokers and water potions.
 * Runs after {@link GameEventTests}, which requires "1.19" and "1.20"; the 1.20.2 rows flip its 1.20 switch.
 */
final class GameEventSourceTests {

    private GameEventSourceTests() {
    }

    /** Hears everything at once and records the event, its source, the block state touched and where. */
    static final class Rec implements GameEventListener {
        final BlockPos pos;
        final List<GameEvent> heard = new ArrayList<GameEvent>();
        final List<Entity> sources = new ArrayList<Entity>();
        final List<IBlockState> states = new ArrayList<IBlockState>();
        final List<Vec3d> where = new ArrayList<Vec3d>();

        Rec(BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public PositionSource position() {
            return new PositionSource.Block(this.pos);
        }

        @Override
        public int radius() {
            return 16;
        }

        @Override
        public boolean immediate() {
            return true;
        }

        @Override
        public boolean handle(WorldServer world, GameEvent event, Vec3d origin, GameEventContext context) {
            this.heard.add(event);
            this.sources.add(context.source);
            this.states.add(context.state);
            this.where.add(origin);
            return true;
        }

        void clear() {
            this.heard.clear();
            this.sources.clear();
            this.states.clear();
            this.where.clear();
        }

        int count(GameEvent event) {
            int n = 0;
            for (GameEvent e : this.heard) {
                if (e == event) {
                    n++;
                }
            }
            return n;
        }

        @Nullable
        Entity source(GameEvent event) {
            int i = this.heard.indexOf(event);
            return i < 0 ? null : this.sources.get(i);
        }

        @Nullable
        IBlockState state(GameEvent event) {
            int i = this.heard.indexOf(event);
            return i < 0 ? null : this.states.get(i);
        }

        @Nullable
        Vec3d where(GameEvent event) {
            int i = this.heard.indexOf(event);
            return i < 0 ? null : this.where.get(i);
        }
    }

    private static final BlockPos AT = new BlockPos(4500, 120, 4500);
    private static Rec rec;
    private static FakePlayer player;

    /** A fake player that can be hurt (Forge's never is), for the totem. */
    static final class Hurtable extends FakePlayer {
        Hurtable(WorldServer world) {
            super(world, new GameProfile(UUID.randomUUID(), "[SelfTestTotem]"));
        }

        @Override
        public boolean isEntityInvulnerable(DamageSource source) {
            return false;
        }

        // No connection to tell about the totem's effects.
        @Override
        protected void onNewPotionEffect(net.minecraft.potion.PotionEffect effect) {
        }

        @Override
        protected void onChangedPotionEffect(net.minecraft.potion.PotionEffect effect, boolean reapply) {
        }

        @Override
        protected void onFinishedPotionEffect(net.minecraft.potion.PotionEffect effect) {
        }
    }

    private static void floor(WorldServer world, BlockPos center, int r) {
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                world.setBlockState(center.add(x, -1, z), Blocks.STONE.getDefaultState());
                for (int y = 0; y <= 3; y++) {
                    world.setBlockToAir(center.add(x, y, z));
                }
            }
        }
    }

    private static void rightClick(WorldServer world, ItemStack stack, BlockPos pos, EnumFacing face) {
        player.setHeldItem(EnumHand.MAIN_HAND, stack);
        player.interactionManager.processRightClickBlock(player, world, stack, EnumHand.MAIN_HAND, pos, face, 0.5F, 1.0F, 0.5F);
    }

    private static ItemStack unbreakable(net.minecraft.item.Item item) {
        ItemStack stack = new ItemStack(item);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Unbreakable", true);
        stack.setTagCompound(tag);
        return stack;
    }

    static void schedule() {
        then(1, "vibration sources: setup", world -> {
            GameEventTests.on119 = true;
            GameEventTests.on120 = true;
            floor(world, AT, 6);
            rec = new Rec(AT);
            GameEvents.add(world, rec);
            player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "[SelfTestVibrations]"));
            player.setPosition(AT.getX() - 3.5D, AT.getY(), AT.getZ() + 0.5D);
            check("the vibration source patches are applied (P80-P83)", "applied".equals(Asm.state("P80"))
                    && "applied".equals(Asm.state("P81")) && "applied".equals(Asm.state("P82")) && "applied".equals(Asm.state("P83")),
                    Asm.state("P80") + " " + Asm.state("P81") + " " + Asm.state("P82") + " " + Asm.state("P83"));
        });

        then(1, "vibrations: dropped items and falling sand landing (P80)", world -> {
            rec.clear();
            EntityItem item = new EntityItem(world, AT.getX() + 2.5D, AT.getY() + 3.0D, AT.getZ() + 0.5D, new ItemStack(Items.STICK));
            item.motionX = item.motionZ = 0.0D;
            for (int i = 0; i < 40 && !rec.heard.contains(GameEvent.HIT_GROUND); i++) {
                item.onUpdate();
            }
            check("a dropped item hitting the ground is hit_ground, the item its source, the stone its block",
                    rec.source(GameEvent.HIT_GROUND) == item && rec.state(GameEvent.HIT_GROUND) == Blocks.STONE.getDefaultState(),
                    rec.heard);
            world.setBlockState(AT.add(2, -1, 2), Blocks.WOOL.getDefaultState());
            rec.clear();
            EntityItem onWool = new EntityItem(world, AT.getX() + 2.5D, AT.getY() + 3.0D, AT.getZ() + 2.5D, new ItemStack(Items.STICK));
            onWool.motionX = onWool.motionZ = 0.0D;
            for (int i = 0; i < 40 && !rec.heard.contains(GameEvent.HIT_GROUND); i++) {
                onWool.onUpdate();
            }
            check("landing on wool carries the wool (which dampens it)", rec.state(GameEvent.HIT_GROUND) == Blocks.WOOL.getDefaultState(),
                    rec.states);
            world.setBlockState(AT.add(2, -1, 2), Blocks.STONE.getDefaultState());
            rec.clear();
            EntityFallingBlock sand = new EntityFallingBlock(world, AT.getX() - 1.5D, AT.getY() + 3.0D, AT.getZ() + 1.5D, Blocks.SAND.getDefaultState());
            sand.fallTime = 1;
            for (int i = 0; i < 40 && !rec.heard.contains(GameEvent.HIT_GROUND); i++) {
                sand.onUpdate();
            }
            check("falling sand landing is hit_ground", rec.source(GameEvent.HIT_GROUND) == sand, rec.heard);
            world.setBlockToAir(AT.add(-2, 0, 1));
            GameEventTests.on119 = false;
            GameEventTests.on120 = false;
            rec.clear();
            EntityItem quiet = new EntityItem(world, AT.getX() + 2.5D, AT.getY() + 3.0D, AT.getZ() + 0.5D, new ItemStack(Items.STICK));
            for (int i = 0; i < 40; i++) {
                quiet.onUpdate();
            }
            GameEventTests.on119 = true;
            GameEventTests.on120 = true;
            check("with game events off a landing item posts nothing", rec.heard.isEmpty(), rec.heard);
        });

        // Block swaps: tilling, a path, a daylight detector (compared at the end of the tick).
        then(1, "vibrations: block swaps by right-click (tilling, paths, daylight detector)", world -> {
            world.setBlockState(AT.add(1, -1, 0), Blocks.GRASS.getDefaultState());
            world.setBlockState(AT.add(1, -1, 3), Blocks.GRASS.getDefaultState());
            world.setBlockState(AT.add(-1, 0, -2), Blocks.DAYLIGHT_DETECTOR.getDefaultState());
            rec.clear();
            rightClick(world, unbreakable(Items.STONE_HOE), AT.add(1, -1, 0), EnumFacing.UP);
            rightClick(world, unbreakable(Items.STONE_SHOVEL), AT.add(1, -1, 3), EnumFacing.UP);
            rightClick(world, ItemStack.EMPTY, AT.add(-1, 0, -2), EnumFacing.UP);
        });
        then(2, "vibrations: block swaps heard", world -> {
            check("the hoe made farmland", world.getBlockState(AT.add(1, -1, 0)).getBlock() == Blocks.FARMLAND);
            check("tilling, a path and the daylight detector are three block changes by the player",
                    rec.count(GameEvent.BLOCK_CHANGE) == 3 && rec.source(GameEvent.BLOCK_CHANGE) == player
                            && rec.state(GameEvent.BLOCK_CHANGE).getBlock() == Blocks.FARMLAND, rec.heard + " " + rec.states);
            check("a swap is neither a place nor a break", !rec.heard.contains(GameEvent.BLOCK_PLACE) && !rec.heard.contains(GameEvent.BLOCK_DESTROY),
                    rec.heard);
            rec.clear();
            rightClick(world, new ItemStack(Blocks.COBBLESTONE, 2), AT.add(0, -1, 3), EnumFacing.UP);
        });
        then(2, "vibrations: placing a block is still only block_place", world -> {
            check("placing cobblestone: one block_place, no block_change", rec.count(GameEvent.BLOCK_PLACE) == 1
                    && !rec.heard.contains(GameEvent.BLOCK_CHANGE), rec.heard);
            world.setBlockToAir(AT.add(0, 0, 3));
            world.setBlockState(AT.add(1, -1, 0), Blocks.FARMLAND.getDefaultState());
            world.setBlockState(AT.add(1, 0, 0), Blocks.WHEAT.getDefaultState());
            rec.clear();
            rightClick(world, new ItemStack(Items.DYE, 4, 15), AT.add(1, 0, 0), EnumFacing.UP);
        });
        then(2, "vibrations: bone meal (1.20.2)", world -> {
            check("bone meal grew the wheat", world.getBlockState(AT.add(1, 0, 0)) != Blocks.WHEAT.getDefaultState());
            check("1.20.2: bone meal is item_interact_finish from the player, and the plant is no block change",
                    rec.source(GameEvent.ITEM_INTERACT_FINISH) == player && !rec.heard.contains(GameEvent.BLOCK_CHANGE)
                            && !rec.heard.contains(GameEvent.BLOCK_PLACE), rec.heard);
            GameEventTests.on120 = false;
            world.setBlockState(AT.add(1, 0, 0), Blocks.WHEAT.getDefaultState());
            rec.clear();
            rightClick(world, new ItemStack(Items.DYE, 4, 15), AT.add(1, 0, 0), EnumFacing.UP);
        });
        then(2, "vibrations: bone meal (1.19)", world -> {
            GameEventTests.on120 = true;
            check("1.19: bone meal is silent", rec.heard.isEmpty(), rec.heard);
            world.setBlockToAir(AT.add(1, 0, 0));
        });

        // A mod that posts its own event for a block a click changed (Buzzy Bees bottling a hive: fluid_pickup).
        then(1, "vibrations: a mod's own event for a click (alreadyEmitted)", world -> {
            world.setBlockState(AT.add(-2, 0, -2), Blocks.LEVER.getDefaultState().withProperty(net.minecraft.block.BlockLever.FACING,
                    net.minecraft.block.BlockLever.EnumOrientation.UP_X));
            world.setBlockState(AT.add(-1, 0, -2), Blocks.DAYLIGHT_DETECTOR.getDefaultState());
            rec.clear();
            rightClick(world, ItemStack.EMPTY, AT.add(-2, 0, -2), EnumFacing.UP);
            rightClick(world, ItemStack.EMPTY, AT.add(-1, 0, -2), EnumFacing.UP);
            com.exiledradio.backportcore.gameevent.GameEventEmitters.alreadyEmitted(world, AT.add(-2, 0, -2));
            com.exiledradio.backportcore.gameevent.GameEventEmitters.alreadyEmitted(world, AT.add(-1, 0, -2));
        });
        then(2, "vibrations: alreadyEmitted heard", world -> {
            check("the lever turned and the detector swapped", world.getBlockState(AT.add(-2, 0, -2)).getValue(net.minecraft.block.BlockLever.POWERED)
                    && world.getBlockState(AT.add(-1, 0, -2)).getBlock() == Blocks.DAYLIGHT_DETECTOR_INVERTED);
            check("a state change and a swap marked alreadyEmitted post nothing more", !rec.heard.contains(GameEvent.BLOCK_ACTIVATE)
                    && !rec.heard.contains(GameEvent.BLOCK_CHANGE), rec.heard);
            world.setBlockToAir(AT.add(-2, 0, -2));
            world.setBlockToAir(AT.add(-1, 0, -2));
        });

        then(1, "vibrations: stopping an item's use (P83)", world -> {
            rec.clear();
            player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BOW));
            player.setActiveHand(EnumHand.MAIN_HAND);
            player.stopActiveHand();
            check("releasing a bow is one item_interact_finish", rec.count(GameEvent.ITEM_INTERACT_FINISH) == 1
                    && rec.source(GameEvent.ITEM_INTERACT_FINISH) == player, rec.heard);
            rec.clear();
            player.setHeldItem(EnumHand.OFF_HAND, new ItemStack(Items.SHIELD));
            player.setActiveHand(EnumHand.OFF_HAND);
            player.resetActiveHand();
            check("lowering a shield is item_interact_finish", rec.count(GameEvent.ITEM_INTERACT_FINISH) == 1, rec.heard);
            player.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
            rec.clear();
            player.resetActiveHand();
            check("stopping while using nothing posts nothing", rec.heard.isEmpty(), rec.heard);
            EntityZombie archer = new EntityZombie(world);
            archer.setPosition(AT.getX() + 0.5D, AT.getY(), AT.getZ() - 2.5D);
            archer.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BOW));
            archer.setActiveHand(EnumHand.MAIN_HAND);
            rec.clear();
            archer.resetActiveHand();
            check("a mob stopping its bow (a skeleton shooting) is item_interact_finish, the mob its source",
                    rec.source(GameEvent.ITEM_INTERACT_FINISH) == archer, rec.heard);
        });

        then(1, "vibrations: chickens, witches, sheep and rabbits", world -> {
            EntityChicken chicken = new EntityChicken(world);
            chicken.setPosition(AT.getX() + 0.5D, AT.getY(), AT.getZ() + 1.5D);
            world.spawnEntity(chicken);
            chicken.timeUntilNextEgg = 1;
            rec.clear();
            chicken.onUpdate();
            check("a chicken laying an egg is entity_place", rec.source(GameEvent.ENTITY_PLACE) == chicken, rec.heard);
            rec.clear();
            chicken.timeUntilNextEgg = 100;
            chicken.onUpdate();
            check("no egg, no entity_place", !rec.heard.contains(GameEvent.ENTITY_PLACE), rec.heard);
            chicken.setDead();

            EntityWitch witch = new EntityWitch(world);
            witch.setPosition(AT.getX() - 1.5D, AT.getY(), AT.getZ() + 1.5D);
            world.spawnEntity(witch);
            witch.setDrinkingPotion(true);
            witch.onUpdate();
            rec.clear();
            witch.onUpdate();
            check("1.20.2: a witch finishing her potion is drink", rec.source(GameEvent.DRINK) == witch, rec.heard);
            GameEventTests.on120 = false;
            witch.setDrinkingPotion(true);
            witch.onUpdate();
            rec.clear();
            witch.onUpdate();
            GameEventTests.on120 = true;
            check("1.19: no drink from witches", !rec.heard.contains(GameEvent.DRINK), rec.heard);
            witch.setDead();

            EntitySheep sheep = new EntitySheep(world);
            sheep.setPosition(AT.getX() + 1.5D, AT.getY(), AT.getZ() - 1.5D);
            world.spawnEntity(sheep);
            ObfuscationReflectionHelper.setPrivateValue(EntitySheep.class, sheep, 4, "field_70899_e");
            rec.clear();
            sheep.onUpdate();
            check("1.20.2: a sheep eating grass is eat", rec.source(GameEvent.EAT) == sheep, rec.heard);
            rec.clear();
            sheep.onUpdate();
            check("eat once per meal", !rec.heard.contains(GameEvent.EAT), rec.heard);
            sheep.setDead();

            BlockPos carrot = AT.add(-3, 0, -3);
            world.setBlockState(carrot.down(), Blocks.FARMLAND.getDefaultState());
            world.setBlockState(carrot, Blocks.CARROTS.getDefaultState().withProperty(net.minecraft.block.BlockCarrot.AGE, 6));
            EntityRabbit rabbit = new EntityRabbit(world);
            rabbit.setPosition(carrot.getX() + 0.5D, carrot.getY(), carrot.getZ() + 0.5D);
            world.spawnEntity(rabbit);
            rabbit.onUpdate();
            ObfuscationReflectionHelper.setPrivateValue(EntityRabbit.class, rabbit, 40, "field_175541_bs");
            rec.clear();
            rabbit.onUpdate();
            check("1.20.2: a rabbit nibbling a carrot is a block change at the carrot, the rabbit its source",
                    rec.source(GameEvent.BLOCK_CHANGE) == rabbit && rec.state(GameEvent.BLOCK_CHANGE) != null
                            && rec.state(GameEvent.BLOCK_CHANGE).getBlock() == Blocks.CARROTS, rec.heard);
            rabbit.setDead();
            world.setBlockToAir(carrot);
            world.setBlockState(carrot.down(), Blocks.STONE.getDefaultState());
        });

        then(1, "vibrations: snow golems, villagers, cauldrons and destroyBlock (P81, P82)", world -> {
            EntitySnowman golem = new EntitySnowman(world);
            golem.setNoAI(true);
            golem.setPosition(AT.getX() + 3.5D, AT.getY(), AT.getZ() + 3.5D);
            world.spawnEntity(golem);
            rec.clear();
            world.setBlockState(AT.add(3, 0, 3), Blocks.SNOW_LAYER.getDefaultState());
            check("a snow golem's snow is block_place, the golem its source", rec.source(GameEvent.BLOCK_PLACE) == golem, rec.heard);
            golem.setDead();
            world.setBlockToAir(AT.add(3, 0, 3));

            BlockPos crop = AT.add(-3, 0, 3);
            world.setBlockState(crop.down(), Blocks.FARMLAND.getDefaultState());
            rec.clear();
            world.setBlockState(crop, Blocks.WHEAT.getDefaultState());
            check("a crop appearing with no villager beside it is not heard", !rec.heard.contains(GameEvent.BLOCK_PLACE), rec.heard);
            world.setBlockToAir(crop);
            EntityVillager villager = new EntityVillager(world);
            villager.setNoAI(true);
            villager.setPosition(crop.getX() + 0.5D, crop.getY(), crop.getZ() + 0.5D);
            world.spawnEntity(villager);
            rec.clear();
            world.setBlockState(crop, Blocks.WHEAT.getDefaultState());
            check("a villager planting is block_place, the villager its source", rec.source(GameEvent.BLOCK_PLACE) == villager, rec.heard);
            villager.setDead();

            rec.clear();
            check("destroyBlock on wheat", world.destroyBlock(crop, false));
            check("World.destroyBlock is block_destroy with the block (P82)", rec.count(GameEvent.BLOCK_DESTROY) == 1
                    && rec.state(GameEvent.BLOCK_DESTROY).getBlock() == Blocks.WHEAT, rec.heard);
            rec.clear();
            world.destroyBlock(crop, false);
            check("destroyBlock on air posts nothing", rec.heard.isEmpty(), rec.heard);
            world.setBlockState(crop.down(), Blocks.STONE.getDefaultState());

            BlockPos cauldron = AT.add(-2, 0, -1);
            world.setBlockState(cauldron, Blocks.CAULDRON.getDefaultState());
            rec.clear();
            for (int i = 0; i < 4000 && world.getBlockState(cauldron).getValue(BlockCauldron.LEVEL) == 0; i++) {
                Blocks.CAULDRON.fillWithRain(world, cauldron);
            }
            if (world.getBlockState(cauldron).getValue(BlockCauldron.LEVEL) > 0) {
                check("rain filling a cauldron is a block change (P81)", rec.count(GameEvent.BLOCK_CHANGE) == 1, rec.heard);
            } else {
                check("the biome is too cold for rain here; the hook posts the change itself",
                        com.exiledradio.backportcore.gameevent.GameEventHooks.rainFill(world, cauldron,
                                Blocks.CAULDRON.getDefaultState().withProperty(BlockCauldron.LEVEL, 1), 2)
                                && rec.count(GameEvent.BLOCK_CHANGE) == 1, rec.heard);
            }
            world.setBlockToAir(cauldron);
        });

        then(1, "vibrations: spawners, chorus fruit and eyes of ender", world -> {
            EntityPig pig = new EntityPig(world);
            pig.setPosition(AT.getX() + 2.5D, AT.getY(), AT.getZ() - 2.5D);
            TileEntityMobSpawner spawner = new TileEntityMobSpawner();
            spawner.setWorld(world);
            spawner.setPos(AT.add(2, -1, -2));
            ForgeEventFactory.doSpecialSpawn(pig, world, (float) pig.posX, (float) pig.posY, (float) pig.posZ, spawner.getSpawnerBaseLogic());
            rec.clear();
            world.spawnEntity(pig);
            check("a spawner's mob is entity_place, the mob its source", rec.source(GameEvent.ENTITY_PLACE) == pig, rec.heard);
            pig.setDead();

            ItemStack fruit = new ItemStack(Items.CHORUS_FRUIT);
            Vec3d from = new Vec3d(player.posX, player.posY, player.posZ);
            MinecraftForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Tick(player, fruit, 1));
            player.setPosition(player.posX + 4.0D, player.posY, player.posZ);
            rec.clear();
            MinecraftForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, fruit, 0, ItemStack.EMPTY));
            check("chorus fruit: eat, then teleport where the eater stood", rec.heard.indexOf(GameEvent.EAT) >= 0
                    && rec.heard.indexOf(GameEvent.TELEPORT) > rec.heard.indexOf(GameEvent.EAT)
                    && from.equals(rec.where(GameEvent.TELEPORT)), rec.heard + " " + rec.where);
            player.setPosition(from.x, from.y, from.z);

            world.playerEntities.add(player);
            try {
                rec.clear();
                EntityEnderEye eye = new EntityEnderEye(world, player.posX, player.posY + 0.9D, player.posZ);
                world.spawnEntity(eye);
                eye.setDead();
                check("throwing an eye of ender is projectile_shoot from the thrower", rec.source(GameEvent.PROJECTILE_SHOOT) == player, rec.heard);
            } finally {
                world.playerEntities.remove(player);
            }
        });

        // An item that places an entity Core does not know (a cushion): the used item and the entity beside the click.
        then(1, "vibrations: an item placing an entity", world -> {
            ItemStack stick = new ItemStack(Items.STICK, 2);
            player.setHeldItem(EnumHand.MAIN_HAND, stick);
            ForgeHooks.onRightClickBlock(player, EnumHand.MAIN_HAND, AT.add(2, -1, 1), EnumFacing.UP, new Vec3d(AT.add(2, 0, 1)));
            stick.shrink(1);
            EntityEnderCrystal placed = new EntityEnderCrystal(world, AT.getX() + 2.5D, AT.getY(), AT.getZ() + 1.5D);
            placed.setShowBottom(false);
            world.spawnEntity(placed);
            rec.clear();
        });
        then(2, "vibrations: the placed entity heard", world -> {
            check("a used item and a new entity beside the click is entity_place by the player",
                    rec.source(GameEvent.ENTITY_PLACE) == player, rec.heard);
            for (EntityEnderCrystal c : world.getEntitiesWithinAABB(EntityEnderCrystal.class, new net.minecraft.util.math.AxisAlignedBB(AT).grow(4))) {
                c.setDead();
            }
        });

        then(1, "vibrations: hitting frames, vehicles and armor stands", world -> {
            BlockPos wall = AT.add(4, 1, -4);
            world.setBlockState(wall, Blocks.STONE.getDefaultState());
            EntityItemFrame frame = new EntityItemFrame(world, wall.west(), EnumFacing.WEST);
            world.spawnEntity(frame);
            rec.clear();
            player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.APPLE));
            ForgeHooks.onInteractEntity(player, frame, EnumHand.MAIN_HAND);
            check("putting an item in a frame is a block change", rec.source(GameEvent.BLOCK_CHANGE) == player, rec.heard);
            frame.setDisplayedItem(new ItemStack(Items.APPLE));
            punch(frame);
            EntityBoat boat = new EntityBoat(world, AT.getX() - 3.5D, AT.getY(), AT.getZ() - 3.5D);
            world.spawnEntity(boat);
            punch(boat);
            EntityArmorStand stand = new EntityArmorStand(world, AT.getX() + 3.5D, AT.getY(), AT.getZ() - 0.5D);
            world.spawnEntity(stand);
            punch(stand);
            rec.clear();
            state.frame = frame;
            state.boat = boat;
            state.stand = stand;
        });
        then(2, "vibrations: the hits heard", world -> {
            check("knocking the item out of a frame is a block change", rec.source(GameEvent.BLOCK_CHANGE) == player, rec.heard);
            check("hitting a boat and an armor stand is entity_damage, twice", rec.count(GameEvent.ENTITY_DAMAGE) == 2, rec.heard);
            check("the frame, the boat and the stand still stand", !state.frame.isDead && !state.boat.isDead && !state.stand.isDead);
            punch(state.frame);
            rec.clear();
        });
        then(2, "vibrations: a broken frame", world -> {
            check("breaking an item frame is entity_die, the frame its source", state.frame.isDead
                    && rec.source(GameEvent.ENTITY_DIE) == state.frame, rec.heard);
            state.boat.setDead();
            state.stand.setDead();
            world.setBlockToAir(AT.add(4, 1, -4));
        });

        then(1, "vibrations: a totem saving a player (1.20.2)", world -> {
            Hurtable p = new Hurtable(world);
            p.setPosition(AT.getX() + 0.5D, AT.getY(), AT.getZ() - 3.5D);
            p.setHeldItem(EnumHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            p.setHealth(2.0F);
            ObfuscationReflectionHelper.setPrivateValue(net.minecraft.entity.player.EntityPlayerMP.class, p, 0, "field_147101_bU");
            p.attackEntityFrom(DamageSource.GENERIC, 10.0F);
            state.totem = p;
            rec.clear();
        });
        then(2, "vibrations: the totem heard", world -> {
            check("the totem saved the player", state.totem.getHealth() > 0.0F && state.totem.getHeldItemOffhand().isEmpty(),
                    state.totem.getHealth());
            check("1.20.2: a totem is item_interact_finish from the player", rec.source(GameEvent.ITEM_INTERACT_FINISH) == state.totem,
                    rec.heard);
        });

        then(1, "vibrations: evokers and water potions (1.20.2)", world -> {
            EntityEvoker evoker = new EntityEvoker(world);
            evoker.setNoAI(true);
            evoker.setPosition(AT.getX() - 2.5D, AT.getY(), AT.getZ() + 2.5D);
            world.spawnEntity(evoker);
            rec.clear();
            world.spawnEntity(new EntityEvokerFangs(world, AT.getX() - 1.5D, AT.getY(), AT.getZ() + 2.5D, 0.0F, 0, evoker));
            EntityVex vex = new EntityVex(world);
            vex.setPosition(AT.getX() - 2.5D, AT.getY() + 1.0D, AT.getZ() + 1.5D);
            vex.setOwner(evoker);
            world.spawnEntity(vex);
            check("evoker fangs and a vex are two entity_place by the evoker", rec.count(GameEvent.ENTITY_PLACE) == 2
                    && rec.source(GameEvent.ENTITY_PLACE) == evoker, rec.heard);
            vex.setDead();
            evoker.setDead();

            BlockPos fire = AT.add(1, 0, 2);
            world.setBlockState(fire, Blocks.FIRE.getDefaultState());
            EntityPotion potion = new EntityPotion(world, fire.getX() + 0.5D, fire.getY() + 1.0D, fire.getZ() + 0.5D,
                    PotionUtils.addPotionToItemStack(new ItemStack(Items.SPLASH_POTION), PotionTypes.WATER));
            RayTraceResult hit = new RayTraceResult(new Vec3d(fire.getX() + 0.5D, fire.getY(), fire.getZ() + 0.5D), EnumFacing.UP, fire.down());
            if (!ForgeEventFactory.onProjectileImpact(potion, hit)) {
                try {
                    java.lang.reflect.Method impact = ObfuscationReflectionHelper.findMethod(EntityPotion.class, "func_70184_a", void.class,
                            RayTraceResult.class);
                    impact.invoke(potion, hit);
                } catch (ReflectiveOperationException e) {
                    check("EntityPotion.onImpact reachable", false, e);
                }
            }
            state.fire = fire;
            rec.clear();
        });
        then(2, "vibrations: the doused fire heard", world -> {
            check("the water potion put the fire out", world.getBlockState(state.fire).getBlock() != Blocks.FIRE);
            check("1.20.2: fire put out by a water potion is block_destroy", rec.count(GameEvent.BLOCK_DESTROY) == 1
                    && rec.state(GameEvent.BLOCK_DESTROY).getBlock() == Blocks.FIRE && rec.source(GameEvent.BLOCK_DESTROY) instanceof EntityPotion,
                    rec.heard);
            GameEvents.remove(world, rec);
        });
    }

    /** What the hit steps hand to the next step. */
    private static final class State {
        EntityItemFrame frame;
        EntityBoat boat;
        EntityArmorStand stand;
        Hurtable totem;
        BlockPos fire;
    }

    private static final State state = new State();

    /** A player's left-click: Forge's attack event, then the hit itself. */
    private static void punch(Entity target) {
        if (ForgeHooks.onPlayerAttackTarget(player, target)) {
            target.attackEntityFrom(DamageSource.causePlayerDamage(player), 1.0F);
        }
    }
}

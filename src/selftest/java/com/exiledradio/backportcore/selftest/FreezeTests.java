package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.freeze.FreezeTicks;
import com.exiledradio.backportcore.speleothem.BlockSpeleothem;
import com.exiledradio.backportcore.speleothem.EntityFallingSpeleothem;
import com.exiledradio.backportcore.speleothem.Thickness;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityPolarBear;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.Random;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Core 1.5.0's freeze ticks ({@code freeze/}, 26.4's rules) and speleothems ({@code speleothem/}, 26.4's
 * {@code SpeleothemBlock}), with a test effect (unluck, freezing at 1.5) and a test speleothem.
 */
final class FreezeTests {

    static TestSpeleothem speleothem;

    private FreezeTests() {
    }

    /** A test speleothem growing on clay, 4 long, down only, no tip merging (an icicle's choices). */
    static final class TestSpeleothem extends BlockSpeleothem {
        TestSpeleothem() {
            super(Material.ROCK, MapColor.STONE, Blocks.CLAY.getDefaultState());
            setRegistryName(SelfTestMod.MOD_ID, "test_speleothem");
            setTranslationKey("test_speleothem");
        }

        @Override
        protected SoundEvent landingSound() {
            return null;
        }

        @Override
        protected int maxGrowthLength() {
            return 4;
        }

        @Override
        protected java.util.List<EnumFacing> naturalGrowthDirections() {
            return java.util.Collections.singletonList(EnumFacing.DOWN);
        }

        @Override
        protected boolean shouldMergeTips() {
            return false;
        }
    }

    static void blocks(IForgeRegistry<net.minecraft.block.Block> registry) {
        speleothem = new TestSpeleothem();
        registry.register(speleothem);
        FreezeTicks.registerEffect(MobEffects.UNLUCK, 1.5F);
    }

    private static BlockPos area(WorldServer world) {
        BlockPos s = world.getSpawnPoint();
        return new BlockPos(s.getX() + 300, 200, s.getZ() + 300);
    }

    static void schedule() {
        then(1, "freeze ticks: rules", world -> {
            BlockPos at = area(world);
            world.getChunk(at);
            EntityPig pig = new EntityPig(world);
            pig.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            check("freeze: a pig can freeze", FreezeTicks.canFreeze(pig));
            EntityZombie zombie = new EntityZombie(world);
            com.exiledradio.backportcore.presentation.GameVersion v117 = com.exiledradio.backportcore.presentation.GameVersion.parse("1.17");
            com.exiledradio.backportcore.presentation.GameVersion v263 = com.exiledradio.backportcore.presentation.GameVersion.parse("26.3");
            com.exiledradio.backportcore.presentation.GameVersion v264 = com.exiledradio.backportcore.presentation.GameVersion.parse("26.4");
            net.minecraft.entity.monster.EntityStray stray = new net.minecraft.entity.monster.EntityStray(world);
            net.minecraft.entity.boss.EntityWither wither = new net.minecraft.entity.boss.EntityWither(world);
            check("freeze: 26.4's #immune_to_freezing: the undead (zombie, stray, wither), polar bear, snow golem",
                    FreezeTicks.immune(zombie, v264) && FreezeTicks.immune(stray, v264) && FreezeTicks.immune(wither, v264)
                            && FreezeTicks.immune(new EntityPolarBear(world), v264)
                            && FreezeTicks.immune(new net.minecraft.entity.monster.EntitySnowman(world), v264));
            check("freeze: 1.17-26.3's #freeze_immune_entity_types: stray, polar bear, snow golem, wither; a zombie freezes",
                    !FreezeTicks.immune(zombie, v117) && !FreezeTicks.immune(zombie, v263) && FreezeTicks.immune(stray, v117)
                            && FreezeTicks.immune(wither, v263) && FreezeTicks.immune(new EntityPolarBear(world), v117)
                            && FreezeTicks.immune(new net.minecraft.entity.monster.EntitySnowman(world), v117)
                            && !FreezeTicks.immune(new net.minecraft.entity.monster.EntityHusk(world), v117));
            check("freeze: the installed version picks the list", FreezeTicks.immune(zombie) == FreezeTicks.rules().isAtLeast("26.4")
                    && FreezeTicks.immune(stray), FreezeTicks.rules());
            check("freeze: polar bear immune", FreezeTicks.immune(new EntityPolarBear(world)));
            check("stalagmite: 2 blocks more before 1.21.5, 2.5 from it", BlockSpeleothem.stalagmiteFallOffset(v117) == 2.0F
                    && BlockSpeleothem.stalagmiteFallOffset(com.exiledradio.backportcore.presentation.GameVersion.parse("1.21.4")) == 2.0F
                    && BlockSpeleothem.stalagmiteFallOffset(com.exiledradio.backportcore.presentation.GameVersion.parse("1.21.5")) == 2.5F
                    && BlockSpeleothem.stalagmiteFallOffset(v264) == 2.5F);
            check("freeze: blaze hurts extra", FreezeTicks.hurtsExtra(new EntityBlaze(world)));
            FreezeTicks.freezeFor(pig, 200);
            check("freeze: capped at 140", FreezeTicks.ticks(pig) == 140, FreezeTicks.ticks(pig));
            check("freeze: fully frozen", FreezeTicks.fullyFrozen(pig));
            EntityPolarBear bear = new EntityPolarBear(world);
            FreezeTicks.freezeFor(bear, 10);
            check("freeze: immune mob gains none", FreezeTicks.ticks(bear) == 0, FreezeTicks.ticks(bear));
            pig.setItemStackToSlot(EntityEquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            check("freeze: leather boots stop it", !FreezeTicks.canFreeze(pig));
            pig.setItemStackToSlot(EntityEquipmentSlot.FEET, ItemStack.EMPTY);
            pig.setFire(5);
            check("freeze: on fire can't freeze (26.4)", !FreezeTicks.canFreeze(pig));
            pig.extinguish();
            check("freeze: percent", Math.abs(FreezeTicks.percent(pig) - 1.0F) < 1e-6, FreezeTicks.percent(pig));
        });
        then(1, "freeze ticks: thaw, hold, damage, slow", world -> {
            BlockPos at = area(world);
            world.setBlockState(at.down(), Blocks.STONE.getDefaultState(), 2);
            EntityPig pig = new EntityPig(world);
            pig.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            pig.setNoAI(true);
            world.spawnEntity(pig);
            FreezeTicks.setTicks(pig, 100);
            pig.onUpdate();
            check("freeze: thaws by 2 a tick", FreezeTicks.ticks(pig) == 98, FreezeTicks.ticks(pig));
            double slow = pig.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getModifier(FreezeTicks.SLOW).getAmount();
            check("freeze: slowed by -0.05 x percent", Math.abs(slow - (-0.05F * (98 / 140.0F))) < 1e-6, slow);
            com.exiledradio.backportcore.presentation.GameVersion old = com.exiledradio.backportcore.presentation.GameVersion.parse("26.3");
            com.exiledradio.backportcore.presentation.GameVersion now = com.exiledradio.backportcore.presentation.GameVersion.parse("26.4");
            check("freeze: on stone, slowed at both rules", FreezeTicks.slowedHere(pig, old) && FreezeTicks.slowedHere(pig, now));
            EntityPig flier = new EntityPig(world);
            flier.setNoAI(true);
            flier.setNoGravity(true);
            flier.setPosition(at.getX() + 0.5, at.getY() + 5.0, at.getZ() + 0.5);
            world.spawnEntity(flier);
            check("freeze: over air, slowed only from 26.4 (1.17-26.3's tryAddFrost needs ground)",
                    !FreezeTicks.slowedHere(flier, old) && FreezeTicks.slowedHere(flier, now));
            FreezeTicks.setTicks(flier, 100);
            flier.onUpdate();
            boolean slowedInAir = flier.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getModifier(FreezeTicks.SLOW) != null;
            check("freeze: over air the installed rules decide", slowedInAir == FreezeTicks.rules().isAtLeast("26.4"), slowedInAir);
            flier.setDead();
            FreezeTicks.markInFreezingBlock(pig);
            int before = FreezeTicks.ticks(pig);
            pig.onUpdate();
            check("freeze: held by a freezing block, no thaw", FreezeTicks.ticks(pig) == before, before + " -> " + FreezeTicks.ticks(pig));
            pig.addPotionEffect(new PotionEffect(MobEffects.UNLUCK, 100));
            FreezeTicks.setTicks(pig, 140);
            pig.ticksExisted = 40;
            float health = pig.getHealth();
            pig.hurtResistantTime = 0;
            pig.onUpdate();
            check("freeze: effect holds the counter", FreezeTicks.ticks(pig) == 140, FreezeTicks.ticks(pig));
            check("freeze: effect damage 1.5 every 40 ticks", Math.abs(health - pig.getHealth() - 1.5F) < 1e-4, health + " -> " + pig.getHealth());
            pig.removePotionEffect(MobEffects.UNLUCK);
            pig.setDead();
        });
        then(1, "speleothem: thickness, growth, falling", world -> {
            BlockPos at = area(world).add(10, 0, 0);
            for (int y = 0; y < 8; y++) {
                world.setBlockState(at.down(y), Blocks.AIR.getDefaultState(), 2);
            }
            world.setBlockState(at.up(), Blocks.CLAY.getDefaultState(), 2);
            check("speleothem: valid under a solid face", speleothem.isValidPlacement(world, at, EnumFacing.DOWN));
            IBlockState placed = speleothem.placementState(world, at, EnumFacing.DOWN, true);
            check("speleothem: placed as a stalactite tip", placed.getValue(BlockSpeleothem.VERTICAL_DIRECTION) == EnumFacing.DOWN
                    && placed.getValue(BlockSpeleothem.THICKNESS) == Thickness.TIP, placed);
            world.setBlockState(at, placed, 3);
            Random always = new Random() {
                @Override
                public int nextInt(int bound) {
                    return 0;
                }
            };
            for (int i = 0; i < 6; i++) {
                speleothem.growIfPossible(world.getBlockState(at), world, at, always);
            }
            int length = 0;
            while (world.getBlockState(at.down(length)).getBlock() == speleothem) {
                length++;
            }
            // Vanilla's findTip searches maxGrowthLength steps (i < max), so a column reaches max + 1 (dripstone 8, icicles 5).
            check("speleothem: grows down to max length + 1 (5)", length == 5, length);
            check("speleothem: base at the root", world.getBlockState(at).getValue(BlockSpeleothem.THICKNESS) == Thickness.BASE,
                    world.getBlockState(at));
            check("speleothem: frustum before the tip", world.getBlockState(at.down(3)).getValue(BlockSpeleothem.THICKNESS) == Thickness.FRUSTUM,
                    world.getBlockState(at.down(3)));
            check("speleothem: tip last", world.getBlockState(at.down(4)).getValue(BlockSpeleothem.THICKNESS) == Thickness.TIP,
                    world.getBlockState(at.down(4)));
            check("speleothem: no growth without the block it grows on",
                    !speleothemGrowsOn(world, at, Blocks.STONE.getDefaultState()), "");
            check("speleothem: falling entity registered",
                    new ResourceLocation("backportcore", "falling_speleothem").equals(EntityList.getKey(EntityFallingSpeleothem.class)),
                    EntityList.getKey(EntityFallingSpeleothem.class));
            world.setBlockToAir(at.up());
            world.notifyNeighborsOfStateChange(at.up(), Blocks.AIR, false);
            speleothem.neighborChanged(world.getBlockState(at), world, at, Blocks.AIR, at.up());
            check("speleothem: loss of support schedules the fall", world.isUpdateScheduled(at, speleothem));
            BlockSpeleothem.spawnFallingStalactite(world.getBlockState(at), world, at);
            check("speleothem: the column became entities", world.getBlockState(at).getBlock() != speleothem
                    && world.getBlockState(at.down(4)).getBlock() != speleothem, world.getBlockState(at));
        });
    }

    private static boolean speleothemGrowsOn(WorldServer world, BlockPos at, IBlockState root) {
        BlockPos p = at.add(3, 0, 0);
        world.setBlockState(p.up(), root, 2);
        world.setBlockState(p, speleothem.state(EnumFacing.DOWN, Thickness.TIP), 2);
        world.setBlockToAir(p.down());
        speleothem.growIfPossible(world.getBlockState(p), world, p, new Random(1));
        boolean grew = world.getBlockState(p.down()).getBlock() == speleothem;
        world.setBlockToAir(p.down());
        world.setBlockToAir(p);
        world.setBlockToAir(p.up());
        return grew;
    }
}

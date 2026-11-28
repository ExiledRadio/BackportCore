package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.boat.BoatLeash;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLeashKnot;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;

import java.util.UUID;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Leashed boats (boat/BoatLeash, 1.21.6's Leashable): tying, untying, shears, handing boats to a boat and to a fence,
 * the elastic pull, the snap past 12 blocks, the holder or the boat gone, and the lead kept in the boat's saved data.
 * The test turns leashed boats on behind its own switch ({@link BoatLeash#require}).
 */
final class BoatLeashTests {

    static boolean on;
    private static final BlockPos AT = new BlockPos(4700, 120, 4700);
    private static FakePlayer player;

    private BoatLeashTests() {
    }

    private static EntityBoat boat(WorldServer world, double dx, double dz) {
        EntityBoat b = new EntityBoat(world, AT.getX() + 0.5D + dx, AT.getY(), AT.getZ() + 0.5D + dz);
        world.spawnEntity(b);
        return b;
    }

    private static boolean use(Entity target, ItemStack held) {
        player.setHeldItem(EnumHand.MAIN_HAND, held);
        return ForgeHooks.onInteractEntity(player, target, EnumHand.MAIN_HAND) != null;
    }

    private static int leads(WorldServer world, Entity near) {
        int n = 0;
        for (EntityItem item : world.getEntitiesWithinAABB(EntityItem.class, near.getEntityBoundingBox().grow(3.0D))) {
            if (item.getItem().getItem() == Items.LEAD && !item.isDead) {
                n += item.getItem().getCount();
            }
        }
        return n;
    }

    private static void sweep(WorldServer world) {
        for (EntityItem item : world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(AT).grow(24.0D))) {
            item.setDead();
        }
    }

    private static ItemStack shears() {
        ItemStack s = new ItemStack(Items.SHEARS);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Unbreakable", true);
        s.setTagCompound(tag);
        return s;
    }

    static void schedule() {
        then(1, "boat leash: tying, untying, shears", world -> {
            BoatLeash.require(() -> on);
            on = true;
            for (int x = -16; x <= 16; x++) {
                for (int z = -3; z <= 3; z++) {
                    world.setBlockState(AT.add(x, -1, z), Blocks.STONE.getDefaultState(), 2);
                    world.setBlockState(AT.add(x, 0, z), Blocks.AIR.getDefaultState(), 2);
                }
            }
            player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "[SelfTestLeash]"));
            player.setPosition(AT.getX() + 0.5D, AT.getY(), AT.getZ() - 1.5D);
            EntityBoat boat = boat(world, 2.0D, 0.0D);
            ItemStack leads = new ItemStack(Items.LEAD, 2);
            check("a lead on a boat ties it to the player", use(boat, leads) && BoatLeash.holder(boat) == player, BoatLeash.holder(boat));
            check("one lead used", leads.getCount() == 1, leads.getCount());
            check("the lead is in the boat's saved data", boat.getEntityData().getCompoundTag("backportcore:leash").hasUniqueId("UUID"));
            sweep(world);
            check("using a boat you hold unties it, dropping the lead", use(boat, ItemStack.EMPTY) && !BoatLeash.isLeashed(boat)
                    && leads(world, boat) == 1 && !boat.getEntityData().hasKey("backportcore:leash"), leads(world, boat));
            sweep(world);
            use(boat, new ItemStack(Items.LEAD));
            check("shears cut the lead, dropping it", use(boat, shears()) && !BoatLeash.isLeashed(boat) && leads(world, boat) == 1);
            sweep(world);
            on = false;
            check("switched off, a lead does nothing", !use(boat, new ItemStack(Items.LEAD)) && !BoatLeash.isLeashed(boat));
            on = true;
            boat.setDead();
        });

        then(1, "boat leash: handing boats to a boat and to a fence", world -> {
            EntityBoat a = boat(world, 2.0D, 0.0D);
            EntityBoat b = boat(world, 4.0D, 0.0D);
            EntityBoat carrier = boat(world, -3.0D, 0.0D);
            use(a, new ItemStack(Items.LEAD));
            use(b, new ItemStack(Items.LEAD));
            check("the player holds two boats", BoatLeash.heldBy(player).size() == 2, BoatLeash.heldBy(player));
            player.setSneaking(true);
            use(carrier, ItemStack.EMPTY);
            player.setSneaking(false);
            check("sneak-using a boat ties the held boats to it", BoatLeash.holder(a) == carrier && BoatLeash.holder(b) == carrier,
                    BoatLeash.holder(a));
            check("a boat can hold boats", BoatLeash.heldBy(carrier).size() == 2);
            sweep(world);
            check("shears on the carrier cut the leads it holds", use(carrier, shears()) && !BoatLeash.isLeashed(a) && !BoatLeash.isLeashed(b)
                    && leads(world, carrier) + leads(world, a) >= 2);
            sweep(world);

            BlockPos fence = AT.add(0, 0, 2);
            world.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState(), 2);
            use(a, new ItemStack(Items.LEAD));
            player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            ForgeHooks.onRightClickBlock(player, EnumHand.MAIN_HAND, fence, EnumFacing.UP, new Vec3d(fence));
            Entity knot = BoatLeash.holder(a);
            check("using a fence ties the held boat to its knot", knot instanceof EntityLeashKnot
                    && ((EntityLeashKnot) knot).getHangingPosition().equals(fence), knot);
            check("a knot is saved by its position", a.getEntityData().getCompoundTag("backportcore:leash").getInteger("Y") == fence.getY());
            BoatLeash.removeLeash(a);
            if (knot != null) {
                knot.setDead();
            }
            world.setBlockToAir(fence);
            a.setDead();
            b.setDead();
            carrier.setDead();
            sweep(world);
        });

        then(1, "boat leash: the rope (1.21.6's Leashable.tickLeash)", world -> {
            EntityBoat boat = boat(world, 0.0D, 0.0D);
            EntityPig pig = new EntityPig(world);
            pig.setNoAI(true);
            pig.setPosition(boat.posX + 8.0D, boat.posY, boat.posZ);
            world.spawnEntity(pig);
            BoatLeash.leash(boat, pig);
            boat.motionX = boat.motionY = boat.motionZ = 0.0D;
            BoatLeash.step(world, boat);
            // The rope runs from the boat's bow (half its width ahead) to the pig's middle: mostly along x.
            check("8 blocks off: the elastic rope pulls the boat toward the holder", boat.motionX > 0.0D && boat.motionX > Math.abs(boat.motionZ),
                    boat.motionX + " " + boat.motionZ);
            pig.setPosition(boat.posX + 3.0D, boat.posY, boat.posZ);
            boat.motionX = 0.0D;
            BoatLeash.step(world, boat);
            check("3 blocks off: slack, no pull", boat.motionX == 0.0D, boat.motionX);

            NBTTagCompound saved = new NBTTagCompound();
            boat.writeToNBTAtomically(saved);
            saved = saved.copy(); // the live ForgeData tag is shared, as a save to disk would not be
            BoatLeash.removeLeash(boat);
            boat.setDead();
            Entity copy = EntityList.createEntityFromNBT(saved, world);
            check("a saved boat comes back as an entity", copy instanceof EntityBoat);
            copy.setUniqueId(UUID.randomUUID());
            world.spawnEntity(copy);
            check("loaded, it has its lead, the holder not yet found", BoatLeash.isLeashed(copy) && BoatLeash.holder(copy) == null);
            BoatLeash.step(world, copy);
            check("the next tick finds the holder by its UUID", BoatLeash.holder(copy) == pig, BoatLeash.holder(copy));

            pig.setPosition(copy.posX + 13.0D, copy.posY, copy.posZ);
            sweep(world);
            BoatLeash.step(world, copy);
            check("past 12 blocks the lead snaps and drops", !BoatLeash.isLeashed(copy) && leads(world, copy) == 1, leads(world, copy));
            sweep(world);

            BoatLeash.leash((EntityBoat) copy, pig);
            pig.setDead();
            BoatLeash.step(world, copy);
            check("the holder gone, the lead drops", !BoatLeash.isLeashed(copy) && leads(world, copy) == 1);
            sweep(world);
            EntityPig other = new EntityPig(world);
            other.setNoAI(true);
            other.setPosition(copy.posX + 2.0D, copy.posY, copy.posZ);
            world.spawnEntity(other);
            BoatLeash.leash((EntityBoat) copy, other);
            copy.setDead();
            BoatLeash.step(world, copy);
            check("the boat broken, the lead drops where it was", !BoatLeash.isLeashed(copy) && leads(world, copy) == 1);
            other.setDead();
            sweep(world);
            on = false;
        });
    }
}

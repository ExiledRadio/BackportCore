package com.exiledradio.backportcore.boat;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * A boat, raft or boat with chest of a {@link BoatWood}, as held. The wood's owner registers it under its own name
 * ({@code trailsandtalesbackport:cherry_boat}) and hands it to the wood.
 *
 * <p>Placed as vanilla's {@code ItemBoat} places: where the player looks, within 5 blocks, on a block or on water (a
 * little lower on water), facing the player's way, unless an entity is in the way or the boat would not fit. Stacks
 * to 1; burns 1200 ticks, as vanilla's boats.
 */
public class ItemWoodBoat extends Item {

    public static final int BURN_TIME = 1200;

    private final BoatWood wood;
    private final boolean chest;

    public ItemWoodBoat(BoatWood wood, boolean chest) {
        this.wood = wood;
        this.chest = chest;
        this.setMaxStackSize(1);
    }

    public BoatWood wood() {
        return this.wood;
    }

    public boolean chest() {
        return this.chest;
    }

    @Override
    public int getItemBurnTime(ItemStack stack) {
        return BURN_TIME;
    }

    /** @return a new boat of this item's kind at a position, facing a way (not yet spawned) */
    public EntityWoodBoat create(World world, double x, double y, double z, float yaw) {
        EntityWoodBoat boat = this.chest ? new EntityWoodChestBoat(world, x, y, z, this.wood) : new EntityWoodBoat(world, x, y, z, this.wood);
        boat.rotationYaw = yaw;
        return boat;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        float pitch = player.rotationPitch;
        float yaw = player.rotationYaw;
        Vec3d eye = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        float cy = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float sy = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cp = -MathHelper.cos(-pitch * 0.017453292F);
        float sp = MathHelper.sin(-pitch * 0.017453292F);
        Vec3d end = eye.add(sy * cp * 5.0D, sp * 5.0D, cy * cp * 5.0D);
        RayTraceResult hit = world.rayTraceBlocks(eye, end, true);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return new ActionResult<ItemStack>(EnumActionResult.PASS, stack);
        }
        Vec3d look = player.getLook(1.0F);
        List<Entity> near = world.getEntitiesWithinAABBExcludingEntity(player,
                player.getEntityBoundingBox().expand(look.x * 5.0D, look.y * 5.0D, look.z * 5.0D).grow(1.0D));
        for (Entity e : near) {
            if (e.canBeCollidedWith()) {
                AxisAlignedBB box = e.getEntityBoundingBox().grow(e.getCollisionBorderSize());
                if (box.contains(eye)) {
                    return new ActionResult<ItemStack>(EnumActionResult.PASS, stack);
                }
            }
        }
        Block block = world.getBlockState(hit.getBlockPos()).getBlock();
        boolean water = block == Blocks.WATER || block == Blocks.FLOWING_WATER;
        EntityWoodBoat boat = create(world, hit.hitVec.x, water ? hit.hitVec.y - 0.12D : hit.hitVec.y, hit.hitVec.z, player.rotationYaw);
        if (!world.getCollisionBoxes(boat, boat.getEntityBoundingBox().grow(-0.1D)).isEmpty()) {
            return new ActionResult<ItemStack>(EnumActionResult.FAIL, stack);
        }
        if (!world.isRemote) {
            world.spawnEntity(boat);
        }
        if (!player.capabilities.isCreativeMode) {
            stack.shrink(1);
        }
        player.addStat(StatList.getObjectUseStats(this));
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, stack);
    }
}

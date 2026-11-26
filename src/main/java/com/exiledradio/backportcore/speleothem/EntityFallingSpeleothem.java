package com.exiledradio.backportcore.speleothem;

import io.netty.buffer.ByteBuf;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

/**
 * One falling segment of a {@link BlockSpeleothem} stalactite (26.4's {@code FallingBlockEntity} with
 * {@code setHurtsEntities}, as {@code SpeleothemBlock.spawnFallingStalactite} makes it).
 *
 * <p>Our own entity rather than 1.12's falling block, whose damage is fixed to the anvil's. It falls as a falling
 * block does; where it lands it stays a block only if a stalactite could hang there, otherwise it breaks with the
 * block's {@link BlockSpeleothem#landingSound()} ({@code onBrokenAfterFall}) and drops its item. The tip hurts what
 * it lands on: {@code floor(blocks fallen x perBlock)}, at most {@code max} ({@code falling_stalactite}).
 */
public class EntityFallingSpeleothem extends Entity implements IEntityAdditionalSpawnData {

    private IBlockState state = Blocks.AIR.getDefaultState();
    private int fallTime;
    private float hurtPerBlock;
    private int hurtMax;

    public EntityFallingSpeleothem(World world) {
        super(world);
        this.preventEntitySpawning = true;
        setSize(0.98F, 0.98F);
    }

    public EntityFallingSpeleothem(World world, double x, double y, double z, IBlockState state) {
        this(world);
        this.state = state;
        setPosition(x, y + (1.0F - this.height) / 2.0F, z);
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.prevPosX = x;
        this.prevPosY = y;
        this.prevPosZ = z;
    }

    public IBlockState getState() {
        return this.state;
    }

    public void setHurts(float perBlock, int max) {
        this.hurtPerBlock = perBlock;
        this.hurtMax = max;
    }

    public float hurtPerBlock() {
        return this.hurtPerBlock;
    }

    @Override
    protected void entityInit() {
    }

    @Override
    protected boolean canTriggerWalking() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !this.isDead;
    }

    @Override
    public boolean canBeAttackedWithItem() {
        return false;
    }

    @Override
    public void onUpdate() {
        if (!BlockSpeleothem.isSpeleothem(this.state)) {
            setDead();
            return;
        }
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
        this.fallTime++;
        if (!hasNoGravity()) {
            this.motionY -= 0.04D;
        }
        move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
        if (!this.world.isRemote) {
            BlockPos here = new BlockPos(this);
            if (this.onGround) {
                land(here);
            } else if (this.fallTime > 100 && (here.getY() < 1 || here.getY() > this.world.getHeight()) || this.fallTime > 600) {
                dropAsItem();
                setDead();
            }
        }
        this.motionX *= 0.98D;
        this.motionY *= 0.98D;
        this.motionZ *= 0.98D;
    }

    private void land(BlockPos here) {
        this.motionX *= 0.7D;
        this.motionZ *= 0.7D;
        this.motionY *= -0.5D;
        IBlockState at = this.world.getBlockState(here);
        if (at.getBlock() == Blocks.PISTON_EXTENSION) {
            return;
        }
        setDead();
        BlockSpeleothem block = (BlockSpeleothem) this.state.getBlock();
        if (at.getBlock().isReplaceable(this.world, here) && !at.getMaterial().isLiquid()
                && block.isValidPlacement(this.world, here, EnumFacing.DOWN)) {
            this.world.setBlockState(here, block.placementState(this.world, here, EnumFacing.DOWN, true), 3);
            return;
        }
        SoundEvent sound = block.landingSound();
        if (!isSilent() && sound != null) {
            this.world.playSound(null, here, sound, SoundCategory.BLOCKS, 2.0F, this.rand.nextFloat() * 0.1F + 0.9F);
        }
        dropAsItem();
    }

    private void dropAsItem() {
        if (this.world.getGameRules().getBoolean("doEntityDrops")) {
            entityDropItem(new ItemStack(this.state.getBlock()), 0.0F);
        }
    }

    @Override
    public void fall(float distance, float damageMultiplier) {
        if (this.hurtPerBlock <= 0.0F) {
            return;
        }
        int blocks = MathHelper.ceil(distance - 1.0F);
        if (blocks <= 0) {
            return;
        }
        float damage = Math.min(MathHelper.floor(blocks * this.hurtPerBlock), this.hurtMax);
        for (Entity e : this.world.getEntitiesWithinAABBExcludingEntity(this, getEntityBoundingBox())) {
            e.attackEntityFrom(SpeleothemEvents.FALLING_STALACTITE, damage);
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setString("Block", String.valueOf(this.state.getBlock().getRegistryName()));
        tag.setInteger("Meta", this.state.getBlock().getMetaFromState(this.state));
        tag.setInteger("Time", this.fallTime);
        tag.setFloat("FallHurtAmount", this.hurtPerBlock);
        tag.setInteger("FallHurtMax", this.hurtMax);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void readEntityFromNBT(NBTTagCompound tag) {
        Block block = Block.getBlockFromName(tag.getString("Block"));
        this.state = block != null ? block.getStateFromMeta(tag.getInteger("Meta")) : Blocks.AIR.getDefaultState();
        this.fallTime = tag.getInteger("Time");
        this.hurtPerBlock = tag.getFloat("FallHurtAmount");
        this.hurtMax = tag.getInteger("FallHurtMax");
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        buffer.writeInt(Block.getStateId(this.state));
    }

    @Override
    public void readSpawnData(ByteBuf buffer) {
        this.state = Block.getStateById(buffer.readInt());
    }

    @Override
    public boolean canRenderOnFire() {
        return false;
    }

    @Override
    public boolean ignoreItemEntityData() {
        return true;
    }
}

package com.exiledradio.backportcore.boat;

import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * {@code backportcore:boat}: a boat (or raft) of any {@link BoatWood}, for the woods vanilla's boat cannot be.
 * Everything a boat does - paddling, floating, ice, carrying, breaking - is vanilla's {@link EntityBoat}; this adds
 * the wood and what it changes.
 *
 * <ul>
 *   <li>The wood is synced to clients and saved as {@code Wood}, its id. The saved id is kept as read, so a boat of a
 *       wood whose mod is gone is an oak boat until that mod is back. A bare name (the backports' boats before Core
 *       1.4.4 saved {@code "cherry"}) becomes the registered wood's id.</li>
 *   <li>It drops its wood's item when broken and is picked as it; a crash drops the wood's own planks (vanilla's six
 *       woods also carry vanilla's type, so vanilla's own crash code is right for them).</li>
 *   <li>A raft seats its rider higher (1.20: 0.25 instead of -0.1).</li>
 * </ul>
 */
public class EntityWoodBoat extends EntityBoat {

    private static final DataParameter<String> WOOD = EntityDataManager.createKey(EntityWoodBoat.class, DataSerializers.STRING);

    public EntityWoodBoat(World world) {
        super(world);
    }

    public EntityWoodBoat(World world, double x, double y, double z, BoatWood wood) {
        super(world, x, y, z);
        setWood(wood);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(WOOD, "minecraft:oak");
    }

    public BoatWood getWood() {
        return BoatWood.get(this.dataManager.get(WOOD));
    }

    /** @return the wood's id as saved, even when that wood is not in this game */
    public String getWoodId() {
        return this.dataManager.get(WOOD);
    }

    public void setWood(BoatWood wood) {
        setWoodId(wood.id);
    }

    private void setWoodId(String id) {
        this.dataManager.set(WOOD, id);
        BoatWood wood = BoatWood.get(id);
        this.setBoatType(wood.vanilla != null ? wood.vanilla : Type.OAK);
    }

    @Override
    public Item getItemBoat() {
        Item item = getWood().boatItem();
        return item != null ? item : Items.BOAT;
    }

    @Override
    public double getMountedYOffset() {
        return getWood().raft ? 0.25D : super.getMountedYOffset();
    }

    /** A crash drops vanilla's planks of the vanilla type; a new wood drops its own instead. */
    @Override
    @Nullable
    public EntityItem entityDropItem(ItemStack stack, float offsetY) {
        BoatWood wood = getWood();
        if (wood.vanilla == null && !stack.isEmpty() && Block.getBlockFromItem(stack.getItem()) == Blocks.PLANKS) {
            ItemStack planks = wood.planks();
            if (!planks.isEmpty()) {
                planks = planks.copy();
                planks.setCount(stack.getCount());
                stack = planks;
            }
        }
        return super.entityDropItem(stack, offsetY);
    }

    @Override
    public ItemStack getPickedResult(RayTraceResult target) {
        return new ItemStack(getItemBoat());
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setString("Wood", this.dataManager.get(WOOD));
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        if (compound.hasKey("Wood", 8)) {
            String id = compound.getString("Wood");
            BoatWood wood = BoatWood.find(id);
            setWoodId(wood != null ? wood.id : id);
        }
    }
}

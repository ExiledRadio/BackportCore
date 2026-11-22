package com.exiledradio.backportcore.boat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ITeleporter;

import javax.annotation.Nullable;

/**
 * {@code backportcore:chest_boat}: 1.19's boat with chest (Wild Update's mechanic), of any {@link BoatWood}.
 *
 * <ul>
 *   <li><b>Opening</b>: right-click while sneaking, or when the seat is taken; otherwise right-click rides (1.19's
 *       {@code ChestBoat.interact}). The screen is vanilla's single chest, titled with the boat's item name, usable
 *       within 8 blocks while the boat lives.</li>
 *   <li><b>One seat</b>, the rider 0.15 blocks forward of the middle (1.19's single-passenger offset).</li>
 *   <li><b>Contents</b> drop whenever the boat is removed for good - broken (by a creative player too), crashed,
 *       {@code /kill} - as 1.19's {@code ChestBoat.remove} drops them; moving to another dimension keeps them with
 *       the boat. This is 1.12.2's chest minecart's rule as well.</li>
 *   <li><b>Hoppers</b> fill and empty it, as 1.19's: being an {@link IInventory} entity is how a 1.12.2 hopper finds
 *       minecarts with chests, and it finds this the same way.</li>
 * </ul>
 */
public class EntityWoodChestBoat extends EntityWoodBoat implements IInventory {

    public static final int SIZE = 27;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private boolean dropContentsWhenDead = true;

    public EntityWoodChestBoat(World world) {
        super(world);
    }

    public EntityWoodChestBoat(World world, double x, double y, double z, BoatWood wood) {
        super(world, x, y, z, wood);
    }

    @Override
    public Item getItemBoat() {
        Item item = getWood().chestBoatItem();
        return item != null ? item : super.getItemBoat();
    }

    @Override
    protected boolean canFitPassenger(Entity passenger) {
        return this.getPassengers().isEmpty();
    }

    @Override
    public void updatePassenger(Entity passenger) {
        super.updatePassenger(passenger);
        if (this.isPassenger(passenger)) {
            Vec3d offset = new Vec3d(0.15D, 0.0D, 0.0D).rotateYaw(-this.rotationYaw * 0.017453292F - ((float) Math.PI / 2F));
            passenger.setPosition(passenger.posX + offset.x, passenger.posY, passenger.posZ + offset.z);
        }
    }

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        if (!player.isSneaking() && canFitPassenger(player)) {
            return super.processInitialInteract(player, hand);
        }
        if (!this.world.isRemote) {
            player.displayGUIChest(this);
        }
        return true;
    }

    // --- removal --------------------------------------------------------------------------------

    @Override
    public void setDead() {
        if (!this.world.isRemote && this.dropContentsWhenDead) {
            InventoryHelper.dropInventoryItems(this.world, this, this);
        }
        super.setDead();
    }

    @Override
    @Nullable
    public Entity changeDimension(int dimension, ITeleporter teleporter) {
        this.dropContentsWhenDead = false;
        Entity moved = super.changeDimension(dimension, teleporter);
        if (moved == null && !this.isDead) {
            this.dropContentsWhenDead = true;
        }
        return moved;
    }

    // --- saving ---------------------------------------------------------------------------------

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        ItemStackHelper.saveAllItems(compound, this.items);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(compound, this.items);
    }

    // --- the chest ------------------------------------------------------------------------------

    /** The boat's item name ("Cherry Boat with Chest"), unless it was named; the chest screen's title. */
    @Override
    public ITextComponent getDisplayName() {
        if (hasCustomName()) {
            return super.getDisplayName();
        }
        return new TextComponentTranslation(getItemBoat().getTranslationKey() + ".name");
    }

    @Override
    public int getSizeInventory() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : this.items) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return this.items.get(index);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        return ItemStackHelper.getAndSplit(this.items, index, count);
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        return ItemStackHelper.getAndRemove(this.items, index);
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        this.items.set(index, stack);
        if (!stack.isEmpty() && stack.getCount() > getInventoryStackLimit()) {
            stack.setCount(getInventoryStackLimit());
        }
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return !this.isDead && player.getDistanceSq(this) <= 64.0D;
    }

    @Override
    public void openInventory(EntityPlayer player) {
    }

    @Override
    public void closeInventory(EntityPlayer player) {
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return true;
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {
    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        this.items.clear();
    }
}

package com.exiledradio.backportcore.migrate;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * An item another mod's item loads as, in a world that mod has been removed from, when it can't simply
 * be renamed.
 *
 * <p>Forge can point an old item name at one new item, keeping its damage value. That is wrong when
 * one old item is several new ones (Future MC's one dye item, four colors by damage, against four dye
 * items) or several old items are one new one with the difference in the damage value (Future MC's six
 * bark woods against 1.12.2's logs). The old name points at a stand-in instead. A stand-in looks and is
 * named like what it stands for, and turns into it the moment it is in a player's inventory;
 * {@code /futuremc convert} and the chunk conversion turn the ones in containers straight into the
 * real thing.
 *
 * <p>A backport subclasses this and registers its instances under its own names, which then stay in
 * worlds forever: never rename one. Give each the ore names of what it stands for, so it works in
 * recipes, and keep it out of creative tabs and recipes - nothing should make one except loading such
 * a world.
 */
public abstract class StandInItem extends Item {

    /** "oldmod:oldname" to the stand-in its items load as. */
    private static final Map<String, StandInItem> BY_OLD_NAME = new HashMap<String, StandInItem>();

    private final String oldModId;
    private final String oldName;

    /**
     * @param oldModId the removed mod, such as {@code futuremc}
     * @param oldName  its item's name, without the mod id
     */
    protected StandInItem(String oldModId, String oldName) {
        this.oldModId = oldModId;
        this.oldName = oldName;
        this.setHasSubtypes(true);
        this.setMaxDamage(0);
        synchronized (BY_OLD_NAME) {
            BY_OLD_NAME.put(oldModId + ":" + oldName, this);
        }
    }

    /** @return the stand-in for a removed mod's item, or null if none was made */
    @Nullable
    public static StandInItem forOldName(String oldModId, String oldName) {
        synchronized (BY_OLD_NAME) {
            return BY_OLD_NAME.get(oldModId + ":" + oldName);
        }
    }

    /** @return what {@code count} of the old item with this damage really are, or empty if nothing */
    public abstract ItemStack real(int damage, int count);

    /** @return what this stand-in stack really is */
    public final ItemStack real(ItemStack stack) {
        return real(stack.getMetadata(), stack.getCount());
    }

    public final String oldModId() {
        return this.oldModId;
    }

    public final String oldName() {
        return this.oldName;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        ItemStack real = real(stack);
        return real.isEmpty() ? super.getItemStackDisplayName(stack) : real.getDisplayName();
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isRemote || !(entity instanceof EntityPlayer)) {
            return;
        }
        ItemStack real = real(stack);
        EntityPlayer player = (EntityPlayer) entity;
        if (!real.isEmpty() && player.inventory.getStackInSlot(slot) == stack) {
            player.inventory.setInventorySlotContents(slot, real);
        }
    }
}

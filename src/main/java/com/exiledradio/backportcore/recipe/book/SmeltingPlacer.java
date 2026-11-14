package com.exiledradio.backportcore.recipe.book;

import com.exiledradio.backportcore.BackportCore;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.util.RecipeItemHelper;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side: a click on a smelting recipe, as 1.13.2's {@code ServerRecipePlacer} and {@code ServerRecipePlacerFurnace}
 * do it. The input slot gets one more of the recipe's ingredient (shift: as many as the inventory has, up to a stack);
 * a different recipe's items go back to the inventory first, with what is in the output. Without the ingredients, the
 * input, fuel and output go back to the inventory and the client is told to show the ghost recipe. The fuel slot is
 * never filled.
 *
 * <p>One change from vanilla: 1.13 refuses when the input and fuel would not fit back in the inventory, but not the
 * output, which it then deletes. Here the output counts too, so a full output slot with a full inventory refuses the
 * click instead of losing the items.
 */
public final class SmeltingPlacer {

    /** What a click did. */
    public enum Result {
        /** Items moved into the input slot. */
        PLACED,
        /** Nothing to add: the input slot already holds all the inventory has (or a full stack). */
        NOTHING,
        /** Not enough ingredients: the slots were emptied into the inventory and the ghost recipe shown. */
        GHOST,
        /** The inventory could not take back what the slots hold. */
        REFUSED,
        /** The player has not unlocked the recipe. */
        LOCKED
    }

    private final RecipeItemHelper helper = new RecipeItemHelper();
    private final SmeltingBook book;
    private final Container container;
    private InventoryPlayer inventory;
    private boolean matches;

    public SmeltingPlacer(SmeltingBook book, Container container) {
        this.book = book;
        this.container = container;
    }

    public Result place(EntityPlayerMP player, SmeltingRecipe recipe, boolean placeAll) {
        if (!SmeltingBooks.data(player, this.book).isUnlocked(recipe.id)) {
            return Result.LOCKED;
        }
        this.inventory = player.inventory;
        this.helper.clear();
        this.inventory.fillStackedContents(this.helper, false);
        // A furnace accounts every slot it has (1.13's AbstractFurnaceTileEntity.fillStackedContents).
        for (int slot : new int[] {this.book.inputSlot, this.book.fuelSlot, this.book.outputSlot}) {
            this.helper.accountStack(stack(slot));
        }
        boolean craftable = this.helper.canCraft(recipe.asCrafting, null);
        this.matches = recipe.input.apply(stack(this.book.inputSlot));
        boolean returnsOutput = !craftable || !this.matches;
        if (!canTakeBack(player.isCreative(), returnsOutput)) {
            return Result.REFUSED;
        }
        Result result;
        if (craftable) {
            result = tryPlace(recipe, placeAll);
        } else {
            clear();
            result = Result.GHOST;
        }
        this.inventory.markDirty();
        return result;
    }

    private ItemStack stack(int slot) {
        return this.container.getSlot(slot).getStack();
    }

    private Result tryPlace(SmeltingRecipe recipe, boolean placeAll) {
        int biggest = this.helper.getBiggestCraftableStack(recipe.asCrafting, null);
        if (this.matches) {
            ItemStack in = stack(this.book.inputSlot);
            if (in.isEmpty() || biggest <= in.getCount()) {
                return Result.NOTHING;
            }
        }
        int amount = maxAmount(placeAll, biggest);
        IntList picked = new IntArrayList();
        if (!this.helper.canCraft(recipe.asCrafting, picked, amount)) {
            return Result.NOTHING;
        }
        if (!this.matches) {
            giveToPlayer(this.book.outputSlot);
            giveToPlayer(this.book.inputSlot);
        }
        Slot slot = this.container.getSlot(this.book.inputSlot);
        ItemStack ingredient = RecipeItemHelper.unpack(picked.getInt(0));
        if (ingredient.isEmpty()) {
            return Result.NOTHING;
        }
        int n = Math.min(ingredient.getMaxStackSize(), amount);
        if (this.matches) {
            n -= slot.getStack().getCount();
        }
        int moved = 0;
        for (int i = 0; i < n; i++) {
            if (consume(slot, ingredient)) {
                moved++;
            }
        }
        return moved > 0 ? Result.PLACED : Result.NOTHING;
    }

    /** 1.13's {@code getMaxAmount}: shift takes all; a click on the recipe already there adds one. */
    private int maxAmount(boolean placeAll, int biggest) {
        if (placeAll) {
            return biggest;
        }
        if (!this.matches) {
            return 1;
        }
        int n = 64;
        for (int slot : new int[] {this.book.inputSlot, this.book.fuelSlot}) {
            ItemStack s = stack(slot);
            if (!s.isEmpty() && n > s.getCount()) {
                n = s.getCount();
            }
        }
        return n < 64 ? n + 1 : n;
    }

    private boolean consume(Slot slot, ItemStack ingredient) {
        int i = this.inventory.findSlotMatchingUnusedItem(ingredient);
        if (i == -1) {
            return false;
        }
        ItemStack taken = this.inventory.getStackInSlot(i).copy();
        if (taken.isEmpty()) {
            return false;
        }
        if (taken.getCount() > 1) {
            this.inventory.decrStackSize(i, 1);
        } else {
            this.inventory.removeStackFromSlot(i);
        }
        taken.setCount(1);
        if (slot.getStack().isEmpty()) {
            slot.putStack(taken);
        } else {
            slot.getStack().grow(1);
            slot.onSlotChanged();
        }
        return true;
    }

    /** Input, fuel and output back to the inventory (1.13's {@code clear}). */
    private void clear() {
        giveToPlayer(this.book.outputSlot);
        giveToPlayer(this.book.inputSlot);
        giveToPlayer(this.book.fuelSlot);
    }

    private void giveToPlayer(int slotIndex) {
        Slot slot = this.container.getSlot(slotIndex);
        ItemStack stack = slot.getStack();
        while (!stack.isEmpty() && stack.getCount() > 0) {
            int i = this.inventory.storeItemStack(stack);
            if (i == -1) {
                i = this.inventory.getFirstEmptyStack();
            }
            ItemStack one = stack.copy();
            one.setCount(1);
            if (!this.inventory.add(i, one)) {
                // Never deleted: what does not fit drops at the player's feet.
                BackportCore.LOGGER.warn("Smelting book: no room in the inventory for {}; dropped", one);
                this.inventory.player.dropItem(one, false);
            }
            slot.decrStackSize(1);
            stack = slot.getStack();
        }
    }

    /**
     * 1.13's {@code func_194328_c}: whether the input and fuel fit back in the inventory (and here the output too, when
     * it would go back). A creative player skips the input and fuel test, as in 1.13.
     */
    private boolean canTakeBack(boolean creative, boolean withOutput) {
        List<Integer> slots = new ArrayList<Integer>();
        if (!creative) {
            slots.add(this.book.inputSlot);
            slots.add(this.book.fuelSlot);
        }
        if (withOutput) {
            slots.add(this.book.outputSlot);
        }
        List<ItemStack> toEmpty = new ArrayList<ItemStack>();
        int empty = 0;
        for (ItemStack s : this.inventory.mainInventory) {
            if (s.isEmpty()) {
                empty++;
            }
        }
        for (int slot : slots) {
            ItemStack stack = stack(slot).copy();
            if (stack.isEmpty()) {
                continue;
            }
            int k = this.inventory.storeItemStack(stack);
            if (k == -1 && toEmpty.size() <= empty) {
                for (ItemStack other : toEmpty) {
                    if (other.isItemEqual(stack) && other.getCount() != other.getMaxStackSize()
                            && other.getCount() + stack.getCount() <= other.getMaxStackSize()) {
                        other.grow(stack.getCount());
                        stack.setCount(0);
                        break;
                    }
                }
                if (!stack.isEmpty()) {
                    if (toEmpty.size() >= empty) {
                        return false;
                    }
                    toEmpty.add(stack);
                }
            } else if (k == -1) {
                return false;
            }
        }
        return true;
    }
}

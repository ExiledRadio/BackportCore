package com.exiledradio.backportcore.migrate;

import com.exiledradio.backportcore.util.BlockStates;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turning one Future MC block, item or block's contents into a backport's.
 *
 * <h2>States</h2>
 * Future MC uses 1.14's property names, as the backports do, so a state carries across property by
 * property, by name and value: {@code axis=x} stays {@code axis=x}, {@code facing=east,half=top}
 * stays exactly that. Anything that doesn't carry - a property the counterpart doesn't have, or a
 * value it doesn't know - is reported, never guessed at; {@code /futuremc check} lists every one for
 * every state Future MC can save, so a mismatch is found before anyone's base is touched.
 *
 * <h2>Contents</h2>
 * Whatever a Future MC block holds - a barrel's items, a furnace's, the book on a lectern, the food on
 * a campfire, a sign's text - is read from its saved data, so this works the same with Future MC
 * installed and without it. Each item found is converted too, then put in the same slot of the
 * counterpart, or the first free one. Anything that doesn't fit is dropped on the spot, never lost.
 */
public final class FutureMcConvert {

    private FutureMcConvert() {
    }

    /** A converted state, and the Future MC properties that could not be carried across. */
    public static final class Converted {
        public final IBlockState state;
        public final List<String> dropped;

        Converted(IBlockState state, List<String> dropped) {
            this.state = state;
            this.dropped = dropped;
        }
    }

    /** @return the state's stored properties by name, as the strings a blockstate file uses */
    public static Map<String, String> props(IBlockState state) {
        return BlockStates.props(state);
    }

    /** Future MC's properties onto its counterpart, then the counterpart's fixed ones. */
    public static Converted state(FutureMcTable.Target target, Map<String, String> props) {
        IBlockState out = target.block.getDefaultState();
        List<String> dropped = new ArrayList<String>();
        for (Map.Entry<String, String> entry : props.entrySet()) {
            String[] mapped = FutureMcTable.property(target, entry.getKey(), entry.getValue());
            if (mapped == null || target.fixed.containsKey(mapped[0])) {
                continue;
            }
            IBlockState next = BlockStates.with(out, mapped[0], mapped[1]);
            if (next == null) {
                dropped.add(entry.getKey() + "=" + entry.getValue());
            } else {
                out = next;
            }
        }
        for (Map.Entry<String, String> entry : target.fixed.entrySet()) {
            IBlockState next = BlockStates.with(out, entry.getKey(), entry.getValue());
            if (next != null) {
                out = next;
            }
        }
        return new Converted(out, dropped);
    }

    /**
     * The properties a Future MC block was saved with: from Future MC itself when it's installed,
     * otherwise from the recorded table.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    public static Map<String, String> savedProps(ResourceLocation name, int meta) {
        Block block = ForgeRegistries.BLOCKS.containsKey(name) ? ForgeRegistries.BLOCKS.getValue(name) : null;
        if (block != null && block.getRegistryName() != null && block.getRegistryName().equals(name)) {
            return props(block.getStateFromMeta(meta));
        }
        return FutureMcTable.recordedState(name.getPath(), meta);
    }

    // ------------------------------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------------------------------

    /**
     * Converts a saved item in place, and any items saved inside it (a shulker box's, a backpack's).
     *
     * @return the number of items converted
     */
    public static int item(NBTTagCompound tag) {
        int changed = 0;
        if (tag.hasKey("id", 8)) {
            ResourceLocation id = new ResourceLocation(tag.getString("id"));
            // A stand-in a removed Future MC's item loaded as becomes what it stands for.
            Item standIn = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
            if (standIn instanceof StandInItem) {
                ItemStack real = ((StandInItem) standIn).real(tag.getShort("Damage"), 1);
                if (!real.isEmpty() && real.getItem().getRegistryName() != null) {
                    tag.setString("id", real.getItem().getRegistryName().toString());
                    tag.setShort("Damage", (short) real.getMetadata());
                    changed++;
                }
            }
            if (FutureMcTable.FUTURE_MC.equals(id.getNamespace())) {
                int damage = tag.getShort("Damage");
                Item target = null;
                int targetDamage = damage;

                FutureMcTable.Target block = FutureMcTable.block(id);
                if (block != null) {
                    Item blockItem = Item.getItemFromBlock(block.block);
                    if (blockItem != Items.AIR) {
                        Map<String, String> props = savedProps(id, damage & 15);
                        IBlockState state = props == null ? block.block.getDefaultState() : state(block, props).state;
                        target = blockItem;
                        targetDamage = block.block.damageDropped(state);
                    }
                }
                if (target == null) {
                    FutureMcTable.ItemTarget item = FutureMcTable.item(id, damage);
                    if (item != null) {
                        target = item.item;
                        targetDamage = item.damage;
                    }
                }
                if (target != null && target.getRegistryName() != null) {
                    tag.setString("id", target.getRegistryName().toString());
                    tag.setShort("Damage", (short) targetDamage);
                    for (FutureMcTable.Contributor contributor : FutureMcTable.contributors()) {
                        for (FutureMcTable.ItemFixer fixer : contributor.itemFixers) {
                            fixer.fix(target, tag);
                        }
                    }
                    changed++;
                }
            }
        }
        if (tag.hasKey("tag", 10)) {
            changed += nested(tag.getCompoundTag("tag"));
        }
        return changed;
    }

    /** Items saved anywhere inside a compound, as a container item's contents are. */
    private static int nested(NBTTagCompound tag) {
        int changed = 0;
        for (String key : tag.getKeySet()) {
            NBTBase value = tag.getTag(key);
            if (value instanceof NBTTagCompound) {
                NBTTagCompound compound = (NBTTagCompound) value;
                changed += isItem(compound) ? item(compound) : nested(compound);
            } else if (value instanceof NBTTagList) {
                NBTTagList list = (NBTTagList) value;
                for (int i = 0; i < list.tagCount(); i++) {
                    if (list.get(i) instanceof NBTTagCompound) {
                        NBTTagCompound compound = list.getCompoundTagAt(i);
                        changed += isItem(compound) ? item(compound) : nested(compound);
                    }
                }
            }
        }
        return changed;
    }

    private static boolean isItem(NBTTagCompound tag) {
        return tag.hasKey("id", 8) && tag.hasKey("Count");
    }

    /** @return the stack as a backport's, or the same stack if it isn't Future MC's */
    public static ItemStack item(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        NBTTagCompound tag = stack.writeToNBT(new NBTTagCompound());
        if (item(tag) == 0) {
            return stack;
        }
        ItemStack out = new ItemStack(tag);
        return out.isEmpty() ? stack : out;
    }

    /** @return true if the stack, or anything inside it, is Future MC's and has a counterpart */
    public static boolean needsConverting(ItemStack stack) {
        return !stack.isEmpty() && item(stack.writeToNBT(new NBTTagCompound()).copy()) > 0;
    }

    // ------------------------------------------------------------------------------------------
    // Contents
    // ------------------------------------------------------------------------------------------

    /** An item found in a Future MC block's saved data, and the slot it was in (-1 if none). */
    public static final class Held {
        public final int slot;
        public final ItemStack stack;

        Held(int slot, ItemStack stack) {
            this.slot = slot;
            this.stack = stack;
        }
    }

    /** @return every item a Future MC block's saved data holds, already converted */
    public static List<Held> contents(NBTTagCompound saved) {
        List<Held> out = new ArrayList<Held>();
        collect(saved, out, true);
        return out;
    }

    private static void collect(NBTTagCompound tag, List<Held> out, boolean root) {
        if (!root && isItem(tag)) {
            NBTTagCompound copy = tag.copy();
            item(copy);
            ItemStack stack = new ItemStack(copy);
            if (!stack.isEmpty()) {
                out.add(new Held(tag.hasKey("Slot") ? tag.getByte("Slot") & 255 : -1, stack));
            }
            return;
        }
        for (String key : tag.getKeySet()) {
            NBTBase value = tag.getTag(key);
            if (value instanceof NBTTagCompound) {
                collect((NBTTagCompound) value, out, false);
            } else if (value instanceof NBTTagList) {
                NBTTagList list = (NBTTagList) value;
                for (int i = 0; i < list.tagCount(); i++) {
                    if (list.get(i) instanceof NBTTagCompound) {
                        collect(list.getCompoundTagAt(i), out, false);
                    }
                }
            }
        }
    }

    /**
     * Puts a Future MC block's contents into the block that replaced it. Works on a tile entity that
     * isn't in a world yet, as when a chunk is being loaded.
     *
     * @param ours      the tile entity of the block that replaced it, or null if it has none
     * @param saved     the Future MC block's saved data
     * @param leftovers gets anything that didn't fit, to be dropped on the spot
     * @return the number of items put in place
     */
    public static int fill(@Nullable TileEntity ours, NBTTagCompound saved, List<ItemStack> leftovers) {
        String id = saved.getString("id");
        if (ours instanceof TileEntitySign) {
            NBTTagCompound tag = ours.writeToNBT(new NBTTagCompound());
            for (int i = 1; i <= 4; i++) {
                if (saved.hasKey("Text" + i, 8)) {
                    tag.setString("Text" + i, saved.getString("Text" + i));
                }
            }
            ours.readFromNBT(tag);
            return 0;
        }
        // A composter's level is in its block state. What Future MC keeps besides is its own
        // bookkeeping for the bone meal a full one gives, which a backport's gives by its level.
        if (id.endsWith(":composter")) {
            return 0;
        }

        List<FutureMcTable.ContentHandler> handlers = new ArrayList<FutureMcTable.ContentHandler>();
        for (FutureMcTable.Contributor contributor : FutureMcTable.contributors()) {
            handlers.addAll(contributor.contentHandlers);
        }

        int placed = 0;
        for (Held held : contents(saved)) {
            ItemStack stack = held.stack;
            ItemStack rest = null;
            if (ours != null) {
                for (FutureMcTable.ContentHandler handler : handlers) {
                    rest = handler.put(ours, stack);
                    if (rest != null) {
                        break;
                    }
                }
            }
            if (rest != null) {
                stack = rest;
            } else if (ours instanceof IInventory) {
                stack = insert((IInventory) ours, held.slot, stack);
            }
            if (stack.isEmpty()) {
                placed++;
            } else {
                leftovers.add(stack);
            }
        }
        if (ours != null) {
            ours.markDirty();
        }
        return placed;
    }

    /** Into the slot it came from if that's free, otherwise the first free slot that takes it. */
    private static ItemStack insert(IInventory inventory, int slot, ItemStack stack) {
        if (slot >= 0 && slot < inventory.getSizeInventory() && inventory.getStackInSlot(slot).isEmpty()) {
            inventory.setInventorySlotContents(slot, stack);
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (inventory.getStackInSlot(i).isEmpty() && inventory.isItemValidForSlot(i, stack)) {
                inventory.setInventorySlotContents(i, stack);
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }
}

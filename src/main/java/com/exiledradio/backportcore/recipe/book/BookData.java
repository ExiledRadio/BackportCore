package com.exiledradio.backportcore.recipe.book;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One player's state of one smelting book, as 1.12.2 keeps its crafting book's ({@code isGuiOpen},
 * {@code isFilteringCraftable}, the unlocked recipes): in the player's data, under Forge's persisted tag so it
 * stays through death and the End's return, at {@code PlayerPersisted.backportcore:recipe_books.<book id>}.
 */
public final class BookData {

    static final String ROOT = "backportcore:recipe_books";

    private final NBTTagCompound tag;
    private final Set<String> unlocked = new LinkedHashSet<String>();

    private BookData(NBTTagCompound tag) {
        this.tag = tag;
        NBTTagList list = tag.getTagList("recipes", Constants.NBT.TAG_STRING);
        for (int i = 0; i < list.tagCount(); i++) {
            this.unlocked.add(list.getStringTagAt(i));
        }
    }

    static BookData of(EntityPlayer player, SmeltingBook book) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG, Constants.NBT.TAG_COMPOUND)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey(ROOT, Constants.NBT.TAG_COMPOUND)) {
            persisted.setTag(ROOT, new NBTTagCompound());
        }
        NBTTagCompound books = persisted.getCompoundTag(ROOT);
        String key = book.id.toString();
        if (!books.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
            books.setTag(key, new NBTTagCompound());
        }
        return new BookData(books.getCompoundTag(key));
    }

    public boolean isOpen() {
        return this.tag.getBoolean("open");
    }

    public boolean isFiltering() {
        return this.tag.getBoolean("filter");
    }

    public void setOpen(boolean open) {
        this.tag.setBoolean("open", open);
    }

    public void setFiltering(boolean filter) {
        this.tag.setBoolean("filter", filter);
    }

    public boolean isUnlocked(String recipe) {
        return this.unlocked.contains(recipe);
    }

    public Set<String> unlocked() {
        return java.util.Collections.unmodifiableSet(this.unlocked);
    }

    /** @return the ids that were not unlocked before */
    public List<String> unlock(Collection<String> recipes) {
        List<String> added = new ArrayList<String>();
        for (String r : recipes) {
            if (this.unlocked.add(r)) {
                added.add(r);
            }
        }
        if (!added.isEmpty()) {
            write();
        }
        return added;
    }

    /** @return whether the recipe was unlocked */
    public boolean lock(String recipe) {
        boolean removed = this.unlocked.remove(recipe);
        if (removed) {
            write();
        }
        return removed;
    }

    private void write() {
        NBTTagList list = new NBTTagList();
        for (String r : this.unlocked) {
            list.appendTag(new NBTTagString(r));
        }
        this.tag.setTag("recipes", list);
    }
}

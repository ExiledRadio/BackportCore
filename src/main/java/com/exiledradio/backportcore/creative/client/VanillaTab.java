package com.exiledradio.backportcore.creative.client;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * Stands in for one vanilla tab in {@code CreativeTabs.CREATIVE_TAB_ARRAY}, at the same index (the constructor
 * takes the slot). It looks and behaves as the original - every question is passed to it - and lists the
 * original's items, then moves the backports' to their places ({@link CreativeTabsClient#arrange}).
 *
 * <p>The original stays what {@code CreativeTabs.BUILDING_BLOCKS} and every item's tab are, so
 * {@code Item.isInCreativeTab} and {@code getSubItems} are asked with the original: the listing is the
 * original's own.
 */
@SideOnly(Side.CLIENT)
public final class VanillaTab extends CreativeTabs {

    private final CreativeTabs original;

    VanillaTab(CreativeTabs original) {
        super(original.getIndex(), original.getTabLabel());
        this.original = original;
        setBackgroundImageName(original.getBackgroundImageName());
        if (!original.drawInForegroundOfTab()) {
            setNoTitle();
        }
        if (!original.hasScrollbar()) {
            setNoScrollbar();
        }
    }

    /** @return the vanilla tab this lists */
    public CreativeTabs original() {
        return this.original;
    }

    @Override
    public void displayAllRelevantItems(NonNullList<ItemStack> items) {
        int start = items.size();
        this.original.displayAllRelevantItems(items);
        CreativeTabsClient.arrange(this.original, items, start);
    }

    @Override
    public ItemStack createIcon() {
        return this.original.getIcon();
    }

    @Override
    public ItemStack getIcon() {
        return this.original.getIcon();
    }

    @Override
    public String getTranslationKey() {
        return this.original.getTranslationKey();
    }

    @Override
    public String getBackgroundImageName() {
        return this.original.getBackgroundImageName();
    }

    @Override
    public boolean drawInForegroundOfTab() {
        return this.original.drawInForegroundOfTab();
    }

    @Override
    public boolean hasScrollbar() {
        return this.original.hasScrollbar();
    }

    @Override
    public int getColumn() {
        return this.original.getColumn();
    }

    @Override
    public boolean isOnTopRow() {
        return this.original.isOnTopRow();
    }

    @Override
    public boolean isAlignedRight() {
        return this.original.isAlignedRight();
    }

    @Override
    public EnumEnchantmentType[] getRelevantEnchantmentTypes() {
        return this.original.getRelevantEnchantmentTypes();
    }

    /** A mod adding a type to the tab it found in the array adds it to vanilla's tab. */
    @Override
    public CreativeTabs setRelevantEnchantmentTypes(EnumEnchantmentType... types) {
        if (this.original != null) {
            this.original.setRelevantEnchantmentTypes(types);
        }
        return this;
    }

    @Override
    public boolean hasRelevantEnchantmentType(@Nullable EnumEnchantmentType type) {
        return this.original.hasRelevantEnchantmentType(type);
    }

    @Override
    public int getTabPage() {
        return this.original.getTabPage();
    }

    @Override
    public boolean hasSearchBar() {
        return this.original.hasSearchBar();
    }

    @Override
    public int getSearchbarWidth() {
        return this.original.getSearchbarWidth();
    }

    @Override
    public ResourceLocation getBackgroundImage() {
        return this.original.getBackgroundImage();
    }

    @Override
    public int getLabelColor() {
        return this.original.getLabelColor();
    }
}

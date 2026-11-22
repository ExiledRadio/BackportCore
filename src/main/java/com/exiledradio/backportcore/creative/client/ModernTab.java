package com.exiledradio.backportcore.creative.client;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * One of 1.19.3's tabs ({@link ModernTabs}): appended to {@code CreativeTabs.CREATIVE_TAB_ARRAY}, on the first page
 * at 1.19.3's place while the look is 1.19.3's or later, on no page otherwise. It lists the stacks of 1.12.2's tabs
 * that 1.19.3's tab lists; no item names it as its tab.
 */
@SideOnly(Side.CLIENT)
public final class ModernTab extends CreativeTabs {

    private final ModernTabs.Def def;

    ModernTab(ModernTabs.Def def) {
        super("backportcore." + def.id);
        this.def = def;
    }

    /** @return which of 1.19.3's tabs this is */
    public ModernTabs.Def def() {
        return this.def;
    }

    @Override
    public void displayAllRelevantItems(NonNullList<ItemStack> items) {
        items.addAll(ModernTabs.list(this.def));
    }

    @Override
    public ItemStack createIcon() {
        return ModernTabs.icon(this.def);
    }

    @Override
    public String getTranslationKey() {
        return this.def.langKey;
    }

    @Override
    public int getColumn() {
        return this.def.column;
    }

    @Override
    public boolean isOnTopRow() {
        return this.def.top;
    }

    @Override
    public boolean isAlignedRight() {
        return this.def.alignedRight;
    }

    /** The first page while 1.19.3's tabs show (and this one does), none otherwise. */
    @Override
    public int getTabPage() {
        return ModernTabs.active() && ModernTabs.shown(this.def) ? 0 : -1;
    }
}

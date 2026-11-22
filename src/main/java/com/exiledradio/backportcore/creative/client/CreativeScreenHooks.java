package com.exiledradio.backportcore.creative.client;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Patch P47's hooks in {@code GuiContainerCreative} (docs/PATCHES.md): where the creative screen's tabs are, which a
 * page shows, and how one is drawn. Until 1.19.3's tabs are on ({@link ModernTabs#active}) each runs 1.12.2's own code
 * line for line, with the eleven added tabs on no page; once on, 1.19.3's: the first page shows {@link ModernTabs}'
 * tabs with Saved Hotbars, Search and the Survival Inventory in 1.19.3's places, the other pages other mods' tabs as
 * Forge pages them, and every tab is 26 pixels wide, 27 apart (1.19.3's {@code getTabX}, {@code getTabY},
 * {@code checkTabClicked}, {@code checkTabHovering} and {@code renderTabButton}).
 */
@SideOnly(Side.CLIENT)
public final class CreativeScreenHooks {

    private static Field tabPage;
    private static boolean warned;

    private CreativeScreenHooks() {
    }

    // ---- initGui ----

    /**
     * {@code initGui}'s {@code CREATIVE_TAB_ARRAY.length}, which counts the pages: the added tabs are on no page of
     * their own.
     */
    public static int tabCount(CreativeTabs[] tabs) {
        return tabs.length - ModernTabs.count();
    }

    /**
     * {@code initGui}'s {@code CREATIVE_TAB_ARRAY[selectedTabIndex]}, the tab the screen opens on: a 1.12.2 tab the
     * page no longer shows opens 1.19.3's Building Blocks, and the other way round.
     */
    public static CreativeTabs openTab(CreativeTabs[] tabs, int index) {
        CreativeTabs tab = tabs[index];
        if (!ModernTabs.installed()) {
            return tab;
        }
        boolean active = ModernTabs.active();
        if (active && (hiddenVanilla(tab) || (tab instanceof ModernTab && tab.getTabPage() != 0))) {
            return ModernTabs.tab(ModernTabs.Def.BUILDING_BLOCKS);
        }
        if (!active && tab instanceof ModernTab) {
            return tabs[CreativeTabs.BUILDING_BLOCKS.getIndex()];
        }
        return tab;
    }

    // ---- drawScreen and drawGuiContainerBackgroundLayer ----

    /**
     * Their {@code Arrays.copyOfRange(CREATIVE_TAB_ARRAY, start, end)}, the tabs of the page ({@code start} is 0 on
     * the first page, {@code page * 10 + 2} on the others). 1.19.3's first page is its tabs with Saved Hotbars,
     * Search and the inventory; the others are as before (Search and the inventory are drawn there apart).
     */
    public static Object[] tabsOnPage(Object[] array, int start, int end) {
        Object[] range = Arrays.copyOfRange(array, start, end);
        if (!ModernTabs.installed()) {
            return range;
        }
        List<CreativeTabs> out = new ArrayList<CreativeTabs>();
        if (ModernTabs.active() && start == 0) {
            for (ModernTabs.Def def : ModernTabs.Def.values()) {
                ModernTab tab = ModernTabs.tab(def);
                if (tab != null && ModernTabs.shown(def)) {
                    out.add(tab);
                }
            }
            out.add(CreativeTabs.HOTBAR);
            out.add(CreativeTabs.SEARCH);
            out.add(CreativeTabs.INVENTORY);
        } else {
            // The page's tabs by their page: 1.12.2's index range, but a tab made after the added ones keeps the place
            // it would have had without them.
            int page = start == 0 ? 0 : (start - 2) / 10;
            for (CreativeTabs tab : CreativeTabs.CREATIVE_TAB_ARRAY) {
                if (tab == null || tab instanceof ModernTab) {
                    continue;
                }
                int index = tab.getIndex();
                boolean late = compact(tab) != index;
                if (late ? page > 0 && pageOf(tab) == page : index >= start && index < end) {
                    out.add(tab);
                }
            }
        }
        // The caller casts to CreativeTabs[].
        return out.toArray(new CreativeTabs[0]);
    }

    // ---- isMouseOverTab, renderCreativeInventoryHoveringText, drawTab (whole bodies) ----

    /** {@code isMouseOverTab(tab, mouseX, mouseY)}, the mouse relative to the screen's corner. */
    public static boolean isMouseOverTab(GuiContainerCreative gui, CreativeTabs tab, int mouseX, int mouseY) {
        if (!onPage(tab, page())) {
            return false;
        }
        if (!ModernTabs.active()) {
            // 1.12.2's isMouseOverTab from here on.
            int i = columnOf(tab);
            int j = 28 * i;
            int k = 0;
            if (rightOf(tab)) {
                j = gui.getXSize() - 28 * (6 - i) + 2;
            } else if (i > 0) {
                j += i;
            }
            if (topOf(tab)) {
                k = k - 32;
            } else {
                k = k + gui.getYSize();
            }
            return mouseX >= j && mouseX <= j + 28 && mouseY >= k && mouseY <= k + 32;
        }
        // 1.19.3's checkTabClicked.
        int x = tabX(gui, tab);
        int y = tabY(gui, tab);
        return mouseX >= x && mouseX <= x + 26 && mouseY >= y && mouseY <= y + 32;
    }

    /** {@code renderCreativeInventoryHoveringText(tab, mouseX, mouseY)}: the tab's name over it. */
    public static boolean renderCreativeInventoryHoveringText(GuiContainerCreative gui, CreativeTabs tab, int mouseX, int mouseY) {
        if (tab instanceof ModernTab && !ModernTabs.active()) {
            return false;
        }
        int j;
        int k;
        int w;
        if (!ModernTabs.active()) {
            // 1.12.2's: the region (j + 3, k + 3, 23, 27).
            int i = columnOf(tab);
            j = 28 * i;
            k = 0;
            if (rightOf(tab)) {
                j = gui.getXSize() - 28 * (6 - i) + 2;
            } else if (i > 0) {
                j += i;
            }
            if (topOf(tab)) {
                k = k - 32;
            } else {
                k = k + gui.getYSize();
            }
            w = 23;
        } else {
            // 1.19.3's checkTabHovering: (x + 3, y + 3, 21, 27).
            j = tabX(gui, tab);
            k = tabY(gui, tab);
            w = 21;
        }
        if (inRegion(gui, j + 3, k + 3, w, 27, mouseX, mouseY)) {
            gui.drawHoveringText(I18n.format(tab.getTranslationKey()), mouseX, mouseY);
            return true;
        }
        return false;
    }

    /** {@code drawTab(tab)}: the tab's sprite (selected or not) and its icon. */
    public static void drawTab(GuiContainerCreative gui, CreativeTabs tab) {
        if (tab instanceof ModernTab && !ModernTabs.active()) {
            return;
        }
        boolean active = ModernTabs.active();
        boolean selected = tab.getIndex() == gui.getSelectedTabIndex();
        boolean top = active ? row(tab) : topOf(tab);
        int column = active ? column(tab) : columnOf(tab);
        int width = active ? 26 : 28;
        int u = column * width;
        int v = selected ? 32 : 0;
        int x;
        int y = gui.getGuiTop();
        if (active) {
            x = gui.getGuiLeft() + tabX(gui, tab);
        } else {
            x = gui.getGuiLeft() + 28 * column;
            if (rightOf(tab)) {
                x = gui.getGuiLeft() + gui.getXSize() - 28 * (6 - column);
            } else if (column > 0) {
                x += column;
            }
        }
        if (top) {
            y -= 28;
        } else {
            v += 64;
            y += gui.getYSize() - 4;
        }
        GlStateManager.disableLighting();
        GlStateManager.color(1F, 1F, 1F); // Forge: reset color in case items change it.
        GlStateManager.enableBlend(); // Forge: blend on, else the tabs show a white border.
        if (!active || ModernTabs.modernSprites()) {
            gui.drawTexturedModalRect(x, y, u, v, width, 32);
        } else {
            // 1.12.2's sprites at 1.19.3's width: an edge tab's own sprite (the first column's or the right-hand
            // one's), the rest the middle one's; its left 13 pixels and right 13 pixels.
            int old = column == 0 ? 0 : column == 6 ? 5 : 1;
            gui.drawTexturedModalRect(x, y, old * 28, v, 13, 32);
            gui.drawTexturedModalRect(x + 13, y, old * 28 + 15, v, 13, 32);
        }
        RenderItem items = Minecraft.getMinecraft().getRenderItem();
        items.zLevel = 100.0F;
        x += active ? 5 : 6;
        y += 8 + (top ? 1 : -1);
        GlStateManager.enableLighting();
        GlStateManager.enableRescaleNormal();
        ItemStack icon = tab.getIcon();
        items.renderItemAndEffectIntoGUI(icon, x, y);
        items.renderItemOverlays(Minecraft.getMinecraft().fontRenderer, icon, x, y);
        GlStateManager.disableLighting();
        items.zLevel = 0.0F;
    }

    // ---- renderToolTip ----

    /**
     * The search tab's tooltip names the item's tab ({@code stack.getItem().getCreativeTab()}): with 1.19.3's tabs on,
     * the first 1.19.3 tab that lists it, as 1.19.3's tooltip names the tabs an item is in.
     */
    @Nullable
    public static CreativeTabs searchTooltipTab(Item item) {
        if (ModernTabs.active()) {
            ModernTabs.Def def = ModernTabs.firstTab(item);
            if (def != null) {
                return ModernTabs.tab(def);
            }
        }
        return item.getCreativeTab();
    }

    // ---- Layout ----

    /** @return whether {@code tab} shows on page {@code page} */
    static boolean onPage(CreativeTabs tab, int page) {
        if (tab == null) {
            return false;
        }
        if (!ModernTabs.active()) {
            if (tab instanceof ModernTab) {
                return false;
            }
            // 1.12.2's: Search and the inventory on every page.
            return pageOf(tab) == page || tab == CreativeTabs.SEARCH || tab == CreativeTabs.INVENTORY;
        }
        if (tab == CreativeTabs.SEARCH || tab == CreativeTabs.INVENTORY) {
            return true;
        }
        if (tab == CreativeTabs.HOTBAR) {
            return page == 0;
        }
        if (tab instanceof ModernTab) {
            return page == 0 && tab.getTabPage() == 0;
        }
        if (hiddenVanilla(tab)) {
            return false;
        }
        return pageOf(tab) == page && page > 0;
    }

    // ---- Other mods' tabs ----

    /**
     * @return a tab's index as if the added tabs were not in the array: its own for every tab made before them (all
     * of them when every mod makes its tabs by load complete), less the added ones before it for a tab made later
     */
    public static int compact(CreativeTabs tab) {
        int index = tab.getIndex();
        if (index < 12 || !ModernTabs.installed() || tab instanceof ModernTab) {
            return index;
        }
        int below = 0;
        for (ModernTabs.Def def : ModernTabs.Def.values()) {
            ModernTab m = ModernTabs.tab(def);
            if (m != null && m.getIndex() < index) {
                below++;
            }
        }
        return index - below;
    }

    /**
     * Forge's {@code getTabPage}, at the tab's {@link #compact} index for a tab made after the added ones. Also
     * {@code drawGuiContainerBackgroundLayer}'s test of whether the open tab is on the page shown.
     */
    public static int pageOf(CreativeTabs tab) {
        int c = compact(tab);
        return c == tab.getIndex() ? tab.getTabPage() : (c - 12) / 10 + 1;
    }

    /** Forge's {@code getColumn}, likewise. */
    static int columnOf(CreativeTabs tab) {
        int c = compact(tab);
        return c == tab.getIndex() ? tab.getColumn() : ((c - 12) % 10) % 5;
    }

    /** Forge's {@code isOnTopRow}, likewise. */
    static boolean topOf(CreativeTabs tab) {
        int c = compact(tab);
        return c == tab.getIndex() ? tab.isOnTopRow() : ((c - 12) % 10) < 5;
    }

    /** Forge's {@code isAlignedRight}, likewise. */
    static boolean rightOf(CreativeTabs tab) {
        int c = compact(tab);
        return c == tab.getIndex() ? tab.isAlignedRight() : columnOf(tab) == 5;
    }

    /** @return true for a 1.12.2 item tab (or whatever holds its slot), which 1.19.3's first page replaces */
    static boolean hiddenVanilla(CreativeTabs tab) {
        int i = tab.getIndex();
        return i >= 0 && i < 12 && tab != CreativeTabs.HOTBAR && tab != CreativeTabs.SEARCH && tab != CreativeTabs.INVENTORY;
    }

    /** @return 1.19.3's column: Saved Hotbars 5, Search and the inventory 6 (aligned right); others their own */
    static int column(CreativeTabs tab) {
        if (tab == CreativeTabs.HOTBAR) {
            return 5;
        }
        if (tab == CreativeTabs.SEARCH || tab == CreativeTabs.INVENTORY) {
            return 6;
        }
        return columnOf(tab);
    }

    /** @return true on the top row */
    static boolean row(CreativeTabs tab) {
        if (tab == CreativeTabs.HOTBAR || tab == CreativeTabs.SEARCH) {
            return true;
        }
        if (tab == CreativeTabs.INVENTORY) {
            return false;
        }
        return topOf(tab);
    }

    static boolean alignedRight(CreativeTabs tab) {
        if (tab == CreativeTabs.HOTBAR || tab == CreativeTabs.SEARCH || tab == CreativeTabs.INVENTORY) {
            return true;
        }
        return rightOf(tab);
    }

    /** 1.19.3's {@code getTabX}: 27 apart, the right-aligned ones from the right edge. */
    static int tabX(GuiContainerCreative gui, CreativeTabs tab) {
        int column = column(tab);
        int x = 27 * column;
        if (alignedRight(tab)) {
            x = gui.getXSize() - 27 * (7 - column) + 1;
        }
        return x;
    }

    /** 1.19.3's {@code getTabY}. */
    static int tabY(GuiContainerCreative gui, CreativeTabs tab) {
        return row(tab) ? -32 : gui.getYSize();
    }

    /** GuiContainer's {@code isPointInRegion}. */
    private static boolean inRegion(GuiContainerCreative gui, int x, int y, int w, int h, int mouseX, int mouseY) {
        int px = mouseX - gui.getGuiLeft();
        int py = mouseY - gui.getGuiTop();
        return px >= x - 1 && px < x + w + 1 && py >= y - 1 && py < y + h + 1;
    }

    /** @return the screen's page (Forge's static {@code tabPage}) */
    static int page() {
        try {
            if (tabPage == null) {
                tabPage = ReflectionHelper.findField(GuiContainerCreative.class, "tabPage");
            }
            return tabPage.getInt(null);
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                BackportCore.LOGGER.error("Creative tabs: could not read the creative screen's page", e);
            }
            return 0;
        }
    }
}

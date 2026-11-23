package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The recipe book's search box as the newer versions draw it, for 1.12.2's crafting book (patch P58 on
 * {@code GuiRecipeBook.render}) and Core's furnace books ({@code recipe.book.client.SmeltingBookGui}):
 * <ul>
 *   <li>1.16: "Search..." ({@code gui.recipebook.search_hint}) in gray italics while the box is empty and not focused
 *       ({@link VanillaRevisions#recipeSearchHint}).</li>
 *   <li>1.20.5: the box one pixel higher and one wider ({@code xo + 25, yo + 13}, 81 wide; 1.12.2's {@code j + 14},
 *       80), its text with it ({@link VanillaRevisions#recipeSearchBoxRaised}).</li>
 *   <li>1.21.2: a click on the magnifier left of the box ({@code xo + 8}, 25 wide, the box's height) focuses it
 *       ({@link VanillaRevisions#recipeMagnifier}).</li>
 * </ul>
 * The rest of 26.3's crafting book is 1.12.2's: the same tabs (Search, Equipment, Building Blocks, Miscellaneous,
 * Redstone) at the same places, the filter toggle, the page arrows, the ghost recipe's colors and its 30-tick cycle.
 */
@SideOnly(Side.CLIENT)
public final class RecipeBookLook {

    /** Boxes already moved to 1.20.5's place (each book makes a new one in {@code initVisuals}). */
    private static final Set<GuiTextField> RAISED = Collections.newSetFromMap(new WeakHashMap<GuiTextField, Boolean>());

    /** For the client self-test: hints drawn, and the last box's position (x, y, width). */
    public static volatile int hints;
    public static volatile int[] lastBox;

    private RecipeBookLook() {
    }

    /** Draws a recipe book's search box (in place of its {@code drawTextBox()}), with the hint where it shows. */
    public static void drawSearchBox(GuiTextField box) {
        if (VanillaRevisions.recipeSearchBoxRaised() && RAISED.add(box)) {
            box.y -= 1;
            box.width += 1;
        }
        box.drawTextBox();
        lastBox = new int[] {box.x, box.y, box.width};
        if (VanillaRevisions.recipeSearchHint() && box.getVisible() && !box.isFocused() && box.getText().isEmpty()) {
            Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(
                    TextFormatting.GRAY.toString() + TextFormatting.ITALIC + I18n.format("gui.recipebook.search_hint"), box.x, box.y, 0xFFFFFF);
            hints++;
        }
    }

    /** The magnifier (1.21.2): a left click there focuses the box. @return true when it did */
    public static boolean magnifierClick(GuiTextField box, int mouseX, int mouseY) {
        if (!VanillaRevisions.recipeMagnifier() || box == null || !box.getVisible()) {
            return false;
        }
        int left = box.x - 25 + 8;
        if (mouseX >= left && mouseX < left + 25 && mouseY >= box.y && mouseY < box.y + box.height) {
            box.setFocused(true);
            magnifierClicks++;
            return true;
        }
        return false;
    }

    /** For the client self-test: magnifier clicks. */
    public static volatile int magnifierClicks;

    private static java.lang.reflect.Field searchBar;

    /** @return a 1.12.2 recipe book's search box, or null */
    @javax.annotation.Nullable
    public static GuiTextField searchBox(net.minecraft.client.gui.recipebook.GuiRecipeBook book) {
        try {
            if (searchBar == null) {
                searchBar = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.client.gui.recipebook.GuiRecipeBook.class,
                        "searchBar", "field_193962_q");
            }
            return (GuiTextField) searchBar.get(book);
        } catch (Exception e) {
            return null;
        }
    }

    /** Forge's handler; registered by Core's client init. */
    public static final class Events {
        /** 1.12.2's crafting books (the inventory's and the crafting table's): the magnifier. */
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public void onMouse(net.minecraftforge.client.event.GuiScreenEvent.MouseInputEvent.Pre event) {
            if (!org.lwjgl.input.Mouse.getEventButtonState() || org.lwjgl.input.Mouse.getEventButton() != 0
                    || !(event.getGui() instanceof net.minecraft.client.gui.recipebook.IRecipeShownListener)) {
                return;
            }
            net.minecraft.client.gui.recipebook.GuiRecipeBook book = ((net.minecraft.client.gui.recipebook.IRecipeShownListener) event.getGui()).func_194310_f();
            if (book == null || !book.isVisible()) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            net.minecraft.client.gui.GuiScreen gui = event.getGui();
            int x = org.lwjgl.input.Mouse.getEventX() * gui.width / mc.displayWidth;
            int y = gui.height - org.lwjgl.input.Mouse.getEventY() * gui.height / mc.displayHeight - 1;
            if (magnifierClick(searchBox(book), x, y)) {
                event.setCanceled(true);
            }
        }
    }
}

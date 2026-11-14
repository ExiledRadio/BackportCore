package com.exiledradio.backportcore.recipe.book.client;

import com.exiledradio.backportcore.recipe.book.BookNetwork;
import com.exiledradio.backportcore.recipe.book.SmeltingBook;
import com.exiledradio.backportcore.recipe.book.SmeltingBooks;
import com.exiledradio.backportcore.recipe.book.SmeltingRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiButtonToggle;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.recipebook.GhostRecipe;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.client.util.RecipeItemHelper;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 1.13's furnace recipe book, drawn by 1.12.2's means: 1.13.2's {@code GuiRecipeBook} with the furnace's
 * {@code GuiFurnaceRecipeBook} parts (the "Showing Smeltable" filter at the texture's 152, 182, the ghost fuel that
 * cycles through every fuel, the big ghost output slot), for one {@link SmeltingBook}.
 *
 * <p>A screen uses it as 1.12.2's {@code GuiCrafting} uses its book: {@link #init} from {@code initGui},
 * {@link #updateScreenPosition} for {@code guiLeft}, {@link #render}, {@link #renderGhostRecipe} and
 * {@link #renderTooltip} from {@code drawScreen}, and {@link #mouseClicked}, {@link #keyPressed},
 * {@link #slotClicked}, {@link #hasClickedOutside}, {@link #tick} and {@link #removed} from the matching methods;
 * the screen implements {@link BookScreen}. {@link GuiFurnaceBook} is the vanilla furnace's.
 *
 * <p>1.13's smelting recipes have no groups, so every button is one recipe and the right-click overlay of other
 * recipes never opens. Search matches the results' names (their tooltip lines), and ids when the text has a colon.
 */
@SideOnly(Side.CLIENT)
public class SmeltingBookGui extends Gui {

    public static final ResourceLocation RECIPE_BOOK = new ResourceLocation("textures/gui/recipe_book.png");

    private final SmeltingBook book;
    private int xOffset;
    private int width;
    private int height;
    private final GhostRecipe ghostRecipe = new GhostRecipe();
    private final List<TabButton> tabs = new ArrayList<TabButton>();
    private TabButton currentTab;
    private GuiButtonToggle toggleRecipesBtn;
    private Container container;
    private Minecraft mc;
    private GuiTextField searchBar;
    private String lastSearch = "";
    private final RecipeItemHelper stackedContents = new RecipeItemHelper();
    private int timesInventoryChanged;
    private final Map<String, Boolean> craftable = new HashMap<String, Boolean>();
    private final Map<String, List<String>> searchText = new HashMap<String, List<String>>();

    // The page (1.12.2's RecipeBookPage).
    private final List<RecipeButton> buttons = new ArrayList<RecipeButton>();
    @Nullable
    private RecipeButton hoveredButton;
    private GuiButtonToggle forwardButton;
    private GuiButtonToggle backButton;
    private int totalPages;
    private int currentPage;
    private List<SmeltingRecipe> shown = new ArrayList<SmeltingRecipe>();

    // The ghost fuel (1.13's GuiFurnaceRecipeBook).
    @Nullable
    private Slot fuelSlot;
    @Nullable
    private Iterator<ItemStack> fuelIterator;
    @Nullable
    private ItemStack fuelItem;
    private float fuelTime;

    public SmeltingBookGui(SmeltingBook book) {
        this.book = book;
        this.tabs.add(new TabButton(-1, Collections.singletonList(new ItemStack(Items.COMPASS))));
        for (int i = 0; i < book.tabs.size(); i++) {
            this.tabs.add(new TabButton(i, book.tabs.get(i).icons));
        }
        for (int i = 0; i < 20; i++) {
            this.buttons.add(new RecipeButton());
        }
    }

    public SmeltingBook book() {
        return this.book;
    }

    private ClientBooks.State state() {
        return ClientBooks.state(this.book.id);
    }

    /** 1.12.2's {@code func_194303_a}, from the screen's {@code initGui}. */
    public void init(int width, int height, Minecraft mc, boolean narrow, Container container) {
        this.mc = mc;
        this.width = width;
        this.height = height;
        this.container = container;
        this.timesInventoryChanged = mc.player.inventory.getTimesChanged();
        this.currentTab = this.tabs.get(0);
        for (TabButton t : this.tabs) {
            t.setStateTriggered(t == this.currentTab);
        }
        if (isVisible()) {
            initVisuals(narrow);
        }
        Keyboard.enableRepeatEvents(true);
    }

    public void initVisuals(boolean narrow) {
        this.xOffset = narrow ? 0 : 86;
        int i = (this.width - 147) / 2 - this.xOffset;
        int j = (this.height - 166) / 2;
        fillStackedContents();
        String search = this.searchBar == null ? "" : this.searchBar.getText();
        this.searchBar = new GuiTextField(0, this.mc.fontRenderer, i + 25, j + 14, 80, this.mc.fontRenderer.FONT_HEIGHT + 5);
        this.searchBar.setMaxStringLength(50);
        this.searchBar.setEnableBackgroundDrawing(false);
        this.searchBar.setVisible(true);
        this.searchBar.setTextColor(16777215);
        this.searchBar.setText(search);
        for (int k = 0; k < this.buttons.size(); k++) {
            this.buttons.get(k).setPosition(i + 11 + 25 * (k % 5), j + 31 + 25 * (k / 5));
        }
        this.forwardButton = new GuiButtonToggle(0, i + 93, j + 137, 12, 17, false);
        this.forwardButton.initTextureValues(1, 208, 13, 18, RECIPE_BOOK);
        this.backButton = new GuiButtonToggle(0, i + 38, j + 137, 12, 17, true);
        this.backButton.initTextureValues(1, 208, 13, 18, RECIPE_BOOK);
        this.toggleRecipesBtn = new GuiButtonToggle(0, i + 110, j + 12, 26, 16, state().filter);
        // 1.13's furnace filter (a flame): the texture's 152, 182.
        this.toggleRecipesBtn.initTextureValues(152, 182, 28, 18, RECIPE_BOOK);
        updateCollections(false);
        updateTabs();
    }

    public void removed() {
        Keyboard.enableRepeatEvents(false);
    }

    public int updateScreenPosition(boolean narrow, int screenWidth, int xSize) {
        if (isVisible() && !narrow) {
            return 177 + (screenWidth - xSize - 200) / 2;
        }
        return (screenWidth - xSize) / 2;
    }

    public void toggleVisibility() {
        setVisible(!isVisible());
    }

    public boolean isVisible() {
        return state().open;
    }

    private void setVisible(boolean visible) {
        ClientBooks.changed(this.book, visible, state().filter);
    }

    private boolean isBookSlot(@Nullable Slot slot) {
        if (slot == null || this.container == null) {
            return false;
        }
        int n = slot.slotNumber;
        return n == this.book.inputSlot || n == this.book.fuelSlot || n == this.book.outputSlot;
    }

    public void slotClicked(@Nullable Slot slot) {
        if (isBookSlot(slot)) {
            this.ghostRecipe.clear();
            this.fuelSlot = null;
            if (isVisible()) {
                updateStackedContents();
            }
        }
    }

    private void fillStackedContents() {
        this.stackedContents.clear();
        this.mc.player.inventory.fillStackedContents(this.stackedContents, false);
        for (int slot : new int[] {this.book.inputSlot, this.book.fuelSlot, this.book.outputSlot}) {
            if (slot >= 0 && slot < this.container.inventorySlots.size()) {
                this.stackedContents.accountStack(this.container.getSlot(slot).getStack());
            }
        }
    }

    /** @return the unlocked recipes of a tab (-1: every tab, the search tab) */
    private List<SmeltingRecipe> unlocked(int tab) {
        ClientBooks.State s = state();
        List<SmeltingRecipe> out = new ArrayList<SmeltingRecipe>();
        for (SmeltingRecipe r : SmeltingBooks.recipes(this.book)) {
            if ((tab < 0 || r.tab == tab) && s.unlocked.contains(r.id)) {
                out.add(r);
            }
        }
        return out;
    }

    private boolean isCraftable(SmeltingRecipe r) {
        Boolean c = this.craftable.get(r.id);
        return c != null && c;
    }

    private void updateCollections(boolean resetPage) {
        List<SmeltingRecipe> list = unlocked(this.currentTab.tab);
        this.craftable.clear();
        for (SmeltingRecipe r : list) {
            this.craftable.put(r.id, this.stackedContents.canCraft(r.asCrafting, null));
        }
        String s = this.searchBar == null ? "" : this.searchBar.getText().toLowerCase(Locale.ROOT);
        if (!s.isEmpty()) {
            list.removeIf(r -> !matchesSearch(r, s));
        }
        if (state().filter) {
            list.removeIf(r -> !isCraftable(r));
        }
        updateLists(list, resetPage);
    }

    /** 1.12.2's recipe search tree: a result's tooltip lines contain the text, or with a colon, its id does. */
    private boolean matchesSearch(SmeltingRecipe r, String s) {
        List<String> lines = this.searchText.get(r.id);
        if (lines == null) {
            lines = new ArrayList<String>();
            try {
                for (String line : r.output.getTooltip(this.mc.player, ITooltipFlag.TooltipFlags.NORMAL)) {
                    String plain = TextFormatting.getTextWithoutFormattingCodes(line);
                    if (plain != null) {
                        lines.add(plain.trim().toLowerCase(Locale.ROOT));
                    }
                }
            } catch (RuntimeException e) {
                lines.add(r.output.getDisplayName().toLowerCase(Locale.ROOT));
            }
            this.searchText.put(r.id, lines);
        }
        for (String line : lines) {
            if (line.contains(s)) {
                return true;
            }
        }
        if (s.indexOf(':') >= 0) {
            ResourceLocation id = r.output.getItem().getRegistryName();
            return id != null && id.toString().contains(s);
        }
        return false;
    }

    private void updateTabs() {
        int i = (this.width - 147) / 2 - this.xOffset - 30;
        int j = (this.height - 166) / 2 + 3;
        int l = 0;
        for (TabButton t : this.tabs) {
            if (t.tab < 0) {
                t.visible = true;
                t.setPosition(i, j + 27 * l++);
            } else {
                t.visible = !unlocked(t.tab).isEmpty();
                if (t.visible) {
                    t.setPosition(i, j + 27 * l++);
                }
            }
        }
    }

    public void tick() {
        if (isVisible() && this.timesInventoryChanged != this.mc.player.inventory.getTimesChanged()) {
            updateStackedContents();
            this.timesInventoryChanged = this.mc.player.inventory.getTimesChanged();
        }
    }

    private void updateStackedContents() {
        fillStackedContents();
        updateCollections(false);
    }

    // ------------------------------------------------------------------------------------------------- page

    private void updateLists(List<SmeltingRecipe> list, boolean resetPage) {
        this.shown = list;
        this.totalPages = (int) Math.ceil((double) list.size() / 20.0D);
        if (this.totalPages <= this.currentPage || resetPage) {
            this.currentPage = 0;
        }
        updateButtonsForPage();
    }

    private void updateButtonsForPage() {
        int first = 20 * this.currentPage;
        for (int k = 0; k < this.buttons.size(); k++) {
            RecipeButton b = this.buttons.get(k);
            if (first + k < this.shown.size()) {
                b.recipe = this.shown.get(first + k);
                b.visible = true;
            } else {
                b.recipe = null;
                b.visible = false;
            }
        }
        if (this.forwardButton != null) {
            this.forwardButton.visible = this.totalPages > 1 && this.currentPage < this.totalPages - 1;
            this.backButton.visible = this.totalPages > 1 && this.currentPage > 0;
        }
    }

    // ------------------------------------------------------------------------------------------------- drawing

    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!isVisible()) {
            return;
        }
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 100.0F);
        this.mc.getTextureManager().bindTexture(RECIPE_BOOK);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int i = (this.width - 147) / 2 - this.xOffset;
        int j = (this.height - 166) / 2;
        this.drawTexturedModalRect(i, j, 1, 1, 147, 166);
        this.searchBar.drawTextBox();
        RenderHelper.disableStandardItemLighting();
        for (TabButton t : this.tabs) {
            t.drawButton(this.mc, mouseX, mouseY, partialTicks);
        }
        this.toggleRecipesBtn.drawButton(this.mc, mouseX, mouseY, partialTicks);
        if (this.totalPages > 1) {
            String s = this.currentPage + 1 + "/" + this.totalPages;
            int w = this.mc.fontRenderer.getStringWidth(s);
            this.mc.fontRenderer.drawString(s, i - w / 2 + 73, j + 141, -1);
        }
        RenderHelper.disableStandardItemLighting();
        this.hoveredButton = null;
        for (RecipeButton b : this.buttons) {
            b.drawButton(this.mc, mouseX, mouseY, partialTicks);
            if (b.visible && b.isMouseOver()) {
                this.hoveredButton = b;
            }
        }
        this.backButton.drawButton(this.mc, mouseX, mouseY, partialTicks);
        this.forwardButton.drawButton(this.mc, mouseX, mouseY, partialTicks);
        GlStateManager.popMatrix();
    }

    public void renderTooltip(int guiLeft, int guiTop, int mouseX, int mouseY) {
        if (!isVisible() || this.mc.currentScreen == null) {
            return;
        }
        GuiScreen screen = this.mc.currentScreen;
        if (this.hoveredButton != null && this.hoveredButton.recipe != null) {
            screen.drawHoveringText(screen.getItemToolTip(this.hoveredButton.recipe.output), mouseX, mouseY);
        }
        if (this.toggleRecipesBtn.isMouseOver()) {
            screen.drawHoveringText(I18n.format(this.toggleRecipesBtn.isStateTriggered() ? this.book.filterKey
                    : "gui.recipebook.toggleRecipes.all"), mouseX, mouseY);
        }
        ItemStack ghost = null;
        for (int k = 0; k < this.ghostRecipe.size(); k++) {
            GhostRecipe.GhostIngredient g = this.ghostRecipe.get(k);
            int x = g.getX() + guiLeft;
            int y = g.getY() + guiTop;
            if (mouseX >= x && mouseY >= y && mouseX < x + 16 && mouseY < y + 16) {
                ghost = g.getItem();
            }
        }
        if (ghost != null) {
            screen.drawHoveringText(screen.getItemToolTip(ghost), mouseX, mouseY);
        }
    }

    /** The ghost recipe over the slots, and 1.13's ghost fuel cycling through every fuel. */
    public void renderGhostRecipe(int guiLeft, int guiTop, boolean bigOutput, float partialTicks) {
        this.ghostRecipe.render(this.mc, guiLeft, guiTop, bigOutput, partialTicks);
        Slot fuel = this.fuelSlot;
        if (fuel == null || this.ghostRecipe.getRecipe() == null) {
            return;
        }
        if (!GuiScreen.isCtrlKeyDown()) {
            this.fuelTime += partialTicks;
        }
        ItemStack item = fuelItem();
        if (item == null) {
            return;
        }
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.disableLighting();
        int x = fuel.xPos + guiLeft;
        int y = fuel.yPos + guiTop;
        Gui.drawRect(x, y, x + 16, y + 16, 822018048);
        RenderItem render = this.mc.getRenderItem();
        render.renderItemAndEffectIntoGUI(this.mc.player, item, x, y);
        GlStateManager.depthFunc(516);
        Gui.drawRect(x, y, x + 16, y + 16, 822083583);
        GlStateManager.depthFunc(515);
        GlStateManager.enableLighting();
        RenderHelper.disableStandardItemLighting();
    }

    @Nullable
    private ItemStack fuelItem() {
        if (this.fuelItem == null || this.fuelTime > 30.0F) {
            this.fuelTime = 0.0F;
            if (this.fuelIterator == null || !this.fuelIterator.hasNext()) {
                List<ItemStack> fuels = this.book.fuels();
                if (fuels.isEmpty()) {
                    return null;
                }
                this.fuelIterator = fuels.iterator();
            }
            this.fuelItem = this.fuelIterator.next();
        }
        return this.fuelItem;
    }

    /** The server's answer to a recipe it could not place: what goes where. */
    public void setupGhostRecipe(SmeltingRecipe recipe, List<Slot> slots) {
        this.ghostRecipe.clear();
        this.ghostRecipe.setRecipe(recipe.asCrafting);
        Slot out = slots.get(this.book.outputSlot);
        this.ghostRecipe.addIngredient(Ingredient.fromStacks(recipe.output), out.xPos, out.yPos);
        this.fuelSlot = slots.get(this.book.fuelSlot);
        this.fuelIterator = null;
        this.fuelItem = null;
        if (recipe.input.getMatchingStacks().length > 0) {
            Slot in = slots.get(this.book.inputSlot);
            this.ghostRecipe.addIngredient(recipe.input, in.xPos, in.yPos);
        }
    }

    // ------------------------------------------------------------------------------------------------- input

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!isVisible() || this.mc.player.isSpectator()) {
            return false;
        }
        int left = (this.width - 147) / 2 - this.xOffset;
        int top = (this.height - 166) / 2;
        if (pageClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (mouseX >= left && mouseX < left + 147 && mouseY >= top && mouseY < top + 166) {
            for (RecipeButton b : this.buttons) {
                if (b.mousePressed(this.mc, mouseX, mouseY)) {
                    b.playPressSound(this.mc.getSoundHandler());
                    SmeltingRecipe r = b.recipe;
                    if (button != 0 || r == null) {
                        return true;
                    }
                    if (!isCraftable(r) && this.ghostRecipe.getRecipe() == r.asCrafting) {
                        return false;
                    }
                    this.ghostRecipe.clear();
                    this.fuelSlot = null;
                    BookNetwork.sendToServer(new BookNetwork.Place(this.mc.player.openContainer.windowId, this.book.id, r.id,
                            GuiScreen.isShiftKeyDown()));
                    if (!isOffsetNextToMainGUI()) {
                        setVisible(false);
                    }
                    return true;
                }
            }
        }
        if (button != 0) {
            return false;
        }
        if (this.searchBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (this.toggleRecipesBtn.mousePressed(this.mc, mouseX, mouseY)) {
            boolean filter = !state().filter;
            ClientBooks.changed(this.book, isVisible(), filter);
            this.toggleRecipesBtn.setStateTriggered(filter);
            this.toggleRecipesBtn.playPressSound(this.mc.getSoundHandler());
            updateCollections(false);
            return true;
        }
        for (TabButton t : this.tabs) {
            if (t.mousePressed(this.mc, mouseX, mouseY)) {
                if (this.currentTab != t) {
                    t.playPressSound(this.mc.getSoundHandler());
                    this.currentTab.setStateTriggered(false);
                    this.currentTab = t;
                    this.currentTab.setStateTriggered(true);
                    updateCollections(true);
                }
                return true;
            }
        }
        return false;
    }

    private boolean pageClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (this.forwardButton.mousePressed(this.mc, mouseX, mouseY)) {
            this.forwardButton.playPressSound(this.mc.getSoundHandler());
            this.currentPage++;
            updateButtonsForPage();
            return true;
        }
        if (this.backButton.mousePressed(this.mc, mouseX, mouseY)) {
            this.backButton.playPressSound(this.mc.getSoundHandler());
            this.currentPage--;
            updateButtonsForPage();
            return true;
        }
        return false;
    }

    public boolean hasClickedOutside(int mouseX, int mouseY, int guiLeft, int guiTop, int xSize, int ySize) {
        if (!isVisible()) {
            return true;
        }
        boolean outside = mouseX < guiLeft || mouseY < guiTop || mouseX >= guiLeft + xSize || mouseY >= guiTop + ySize;
        boolean onBook = guiLeft - 147 < mouseX && mouseX < guiLeft && guiTop < mouseY && mouseY < guiTop + ySize;
        return outside && !onBook && !this.currentTab.mousePressed(this.mc, mouseX, mouseY);
    }

    public boolean keyPressed(char typedChar, int keyCode) {
        if (!isVisible() || this.mc.player.isSpectator()) {
            return false;
        }
        if (keyCode == 1 && !isOffsetNextToMainGUI()) {
            setVisible(false);
            return true;
        }
        if (GameSettings.isKeyDown(this.mc.gameSettings.keyBindChat) && !this.searchBar.isFocused()) {
            this.searchBar.setFocused(true);
        } else if (this.searchBar.textboxKeyTyped(typedChar, keyCode)) {
            String s = this.searchBar.getText().toLowerCase(Locale.ROOT);
            if (!s.equals(this.lastSearch)) {
                updateCollections(false);
                this.lastSearch = s;
            }
            return true;
        }
        return false;
    }

    private boolean isOffsetNextToMainGUI() {
        return this.xOffset == 86;
    }

    /** New state or unlocks from the server. */
    public void recipesUpdated() {
        if (this.searchBar == null) {
            return;
        }
        updateTabs();
        if (this.toggleRecipesBtn != null) {
            this.toggleRecipesBtn.setStateTriggered(state().filter);
        }
        if (isVisible()) {
            updateCollections(false);
        }
    }

    // ------------------------------------------------------------------------------------------------- self-test access

    /** @return the recipes on the current page, for the client self-test */
    public List<String> shownForTest() {
        List<String> out = new ArrayList<String>();
        for (SmeltingRecipe r : this.shown) {
            out.add(r.id);
        }
        return out;
    }

    public boolean craftableForTest(String id) {
        Boolean c = this.craftable.get(id);
        return c != null && c;
    }

    public int ghostSizeForTest() {
        return this.ghostRecipe.size() + (this.fuelSlot != null ? 1 : 0);
    }

    /** One recipe's button (1.12.2's {@code GuiButtonRecipe}; never more than one recipe). */
    private final class RecipeButton extends GuiButton {
        @Nullable
        SmeltingRecipe recipe;

        RecipeButton() {
            super(0, 0, 0, 25, 25, "");
        }

        void setPosition(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible || this.recipe == null) {
                return;
            }
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            RenderHelper.enableGUIStandardItemLighting();
            mc.getTextureManager().bindTexture(RECIPE_BOOK);
            GlStateManager.disableLighting();
            int u = isCraftable(this.recipe) ? 29 : 54;
            this.drawTexturedModalRect(this.x, this.y, u, 206, this.width, this.height);
            mc.getRenderItem().renderItemAndEffectIntoGUI(this.recipe.output, this.x + 4, this.y + 4);
            GlStateManager.enableLighting();
            RenderHelper.disableStandardItemLighting();
        }
    }

    /** A tab (1.12.2's {@code GuiButtonRecipeTab} with 1.13's one or two icons). */
    private static final class TabButton extends GuiButtonToggle {
        final int tab;
        final List<ItemStack> icons;

        TabButton(int tab, List<ItemStack> icons) {
            super(0, 0, 0, 35, 27, false);
            this.tab = tab;
            this.icons = icons;
            this.initTextureValues(153, 2, 35, 0, RECIPE_BOOK);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            mc.getTextureManager().bindTexture(this.resourceLocation);
            GlStateManager.disableDepth();
            int u = this.xTexStart;
            int v = this.yTexStart;
            if (this.stateTriggered) {
                u += this.xDiffTex;
            }
            if (this.hovered) {
                v += this.yDiffTex;
            }
            int x = this.x;
            if (this.stateTriggered) {
                x -= 2;
            }
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            this.drawTexturedModalRect(x, this.y, u, v, this.width, this.height);
            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.disableLighting();
            int shift = this.stateTriggered ? -2 : 0;
            RenderItem render = mc.getRenderItem();
            if (this.icons.size() == 1) {
                render.renderItemAndEffectIntoGUI(this.icons.get(0), this.x + 9 + shift, this.y + 5);
            } else if (this.icons.size() == 2) {
                render.renderItemAndEffectIntoGUI(this.icons.get(0), this.x + 3 + shift, this.y + 5);
                render.renderItemAndEffectIntoGUI(this.icons.get(1), this.x + 14 + shift, this.y + 5);
            }
            GlStateManager.enableLighting();
            RenderHelper.disableStandardItemLighting();
        }
    }
}

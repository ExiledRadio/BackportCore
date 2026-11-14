package com.exiledradio.backportcore.recipe.book.client;

import com.exiledradio.backportcore.recipe.book.SmeltingBook;
import com.exiledradio.backportcore.recipe.book.SmeltingBooks;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiButtonImage;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;

/**
 * 1.13's furnace screen: 1.12.2's {@code GuiFurnace} with the green recipe book button (1.13's {@code recipe_button.png},
 * at guiLeft + 20, the middle - 49) and the furnace book beside it, laid out as 1.12.2's crafting table lays out its
 * book. A subclass, so mods that look for {@code GuiFurnace} still find it.
 */
@SideOnly(Side.CLIENT)
public class GuiFurnaceBook extends GuiFurnace implements BookScreen {

    private static final ResourceLocation FURNACE_GUI_TEXTURES = new ResourceLocation("textures/gui/container/furnace.png");
    public static final ResourceLocation RECIPE_BUTTON = new ResourceLocation("textures/gui/recipe_button.png");
    private static final int BOOK_BUTTON = 10;

    private final IInventory furnace;
    private final SmeltingBookGui book;
    private GuiButtonImage recipeButton;
    private boolean widthTooNarrow;

    public GuiFurnaceBook(InventoryPlayer playerInv, IInventory furnaceInv) {
        super(playerInv, furnaceInv);
        this.furnace = furnaceInv;
        SmeltingBook b = SmeltingBooks.get(SmeltingBook.FURNACE);
        this.book = new SmeltingBookGui(b != null ? b : SmeltingBook.furnace());
    }

    @Override
    public SmeltingBookGui smeltingBook() {
        return this.book;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.widthTooNarrow = this.width < 379;
        this.book.init(this.width, this.height, this.mc, this.widthTooNarrow, this.inventorySlots);
        this.guiLeft = this.book.updateScreenPosition(this.widthTooNarrow, this.width, this.xSize);
        this.recipeButton = new GuiButtonImage(BOOK_BUTTON, this.guiLeft + 20, this.height / 2 - 49, 20, 18, 0, 0, 19, RECIPE_BUTTON);
        this.buttonList.add(this.recipeButton);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        this.book.tick();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (this.book.isVisible() && this.widthTooNarrow) {
            this.drawDefaultBackground();
            this.drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);
            this.book.render(mouseX, mouseY, partialTicks);
        } else {
            // GuiFurnace's own drawing (its background, the container, the hovered slot's tooltip), then the book
            // beside it: they never overlap, and the tooltip is drawn again on top below.
            super.drawScreen(mouseX, mouseY, partialTicks);
            this.book.render(mouseX, mouseY, partialTicks);
            this.book.renderGhostRecipe(this.guiLeft, this.guiTop, true, partialTicks);
        }
        this.renderHoveredToolTip(mouseX, mouseY);
        this.book.renderTooltip(this.guiLeft, this.guiTop, mouseX, mouseY);
    }

    /** At guiLeft, which the book moves (1.12.2's furnace draws at the middle of the screen). */
    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(FURNACE_GUI_TEXTURES);
        int i = this.guiLeft;
        int j = this.guiTop;
        this.drawTexturedModalRect(i, j, 0, 0, this.xSize, this.ySize);
        if (TileEntityFurnace.isBurning(this.furnace)) {
            int k = burnLeftScaled(13);
            this.drawTexturedModalRect(i + 56, j + 36 + 12 - k, 176, 12 - k, 14, k + 1);
        }
        int l = cookProgressScaled(24);
        this.drawTexturedModalRect(i + 79, j + 34, 176, 14, l + 1, 16);
    }

    private int cookProgressScaled(int pixels) {
        int i = this.furnace.getField(2);
        int j = this.furnace.getField(3);
        return j != 0 && i != 0 ? i * pixels / j : 0;
    }

    private int burnLeftScaled(int pixels) {
        int i = this.furnace.getField(1);
        if (i == 0) {
            i = 200;
        }
        return this.furnace.getField(0) * pixels / i;
    }

    @Override
    protected boolean isPointInRegion(int rectX, int rectY, int rectWidth, int rectHeight, int pointX, int pointY) {
        return (!this.widthTooNarrow || !this.book.isVisible()) && super.isPointInRegion(rectX, rectY, rectWidth, rectHeight, pointX, pointY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!this.book.mouseClicked(mouseX, mouseY, mouseButton)) {
            if (!this.widthTooNarrow || !this.book.isVisible()) {
                super.mouseClicked(mouseX, mouseY, mouseButton);
            }
        }
    }

    @Override
    protected boolean hasClickedOutside(int mouseX, int mouseY, int guiLeft, int guiTop) {
        boolean outside = mouseX < guiLeft || mouseY < guiTop || mouseX >= guiLeft + this.xSize || mouseY >= guiTop + this.ySize;
        return this.book.hasClickedOutside(mouseX, mouseY, this.guiLeft, this.guiTop, this.xSize, this.ySize) && outside;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BOOK_BUTTON && button == this.recipeButton) {
            this.book.initVisuals(this.widthTooNarrow);
            this.book.toggleVisibility();
            this.guiLeft = this.book.updateScreenPosition(this.widthTooNarrow, this.width, this.xSize);
            this.recipeButton.setPosition(this.guiLeft + 20, this.height / 2 - 49);
        } else {
            super.actionPerformed(button);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (!this.book.keyPressed(typedChar, keyCode)) {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void handleMouseClick(Slot slotIn, int slotId, int mouseButton, ClickType type) {
        super.handleMouseClick(slotIn, slotId, mouseButton, type);
        this.book.slotClicked(slotIn);
    }

    @Override
    public void onGuiClosed() {
        this.book.removed();
        super.onGuiClosed();
    }
}

package com.exiledradio.backportcore.presentation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiOptionsRowList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A scrolling list of option rows as vanilla's {@code GuiOptionsRowList} (400 wide, rows of two 150-wide buttons),
 * over rows given: Core's Video Settings with OptiFine, and its options screens when their buttons do not fit.
 */
@SideOnly(Side.CLIENT)
public final class OptionRowList extends GuiListExtended {

    private final List<GuiOptionsRowList.Row> rows;

    public OptionRowList(Minecraft mc, int width, int height, int top, int bottom, int slotHeight, List<GuiOptionsRowList.Row> rows) {
        super(mc, width, height, top, bottom, slotHeight);
        this.centerListVertically = false;
        this.rows = rows;
    }

    @Override
    public IGuiListEntry getListEntry(int index) {
        return this.rows.get(index);
    }

    @Override
    protected int getSize() {
        return this.rows.size();
    }

    @Override
    public int getListWidth() {
        return 400;
    }

    @Override
    protected int getScrollBarX() {
        return super.getScrollBarX() + 32;
    }

    public float scrolled() {
        return this.amountScrolled;
    }

    public void scrollTo(float amount) {
        this.amountScrolled = amount;
        bindAmountScrolled();
    }

    /**
     * A row of two buttons that, besides what vanilla's row does (1.12.2's option buttons), runs Core's
     * {@link OptionWidgets.Action} buttons, which a list never passes to its screen's {@code actionPerformed}.
     */
    public static final class ButtonRow extends GuiOptionsRowList.Row {
        private final GuiButton[] buttons;

        public ButtonRow(GuiButton a, @Nullable GuiButton b) {
            super(a, b);
            this.buttons = b == null ? new GuiButton[] {a} : new GuiButton[] {a, b};
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
            Minecraft mc = Minecraft.getMinecraft();
            for (GuiButton b : this.buttons) {
                if (b instanceof OptionWidgets.Action && b.mousePressed(mc, mouseX, mouseY)) {
                    b.playPressSound(mc.getSoundHandler());
                    ((OptionWidgets.Action) b).action.run();
                    return true;
                }
            }
            return super.mousePressed(slotIndex, mouseX, mouseY, mouseEvent, relativeX, relativeY);
        }
    }
}

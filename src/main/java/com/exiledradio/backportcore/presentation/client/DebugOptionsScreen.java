package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 1.21.9's Debug Options ({@code DebugOptionsScreen}, F3 + F6): the title and a search box across the top, the
 * warning below them, then each entry of {@link DebugEntries} under its category ("Debug Screen Text", "Debug
 * Renderers") with its Off, In F3 (In Overlay from 1.21.11) and Always buttons, and at the bottom the two profiles and
 * Done. 1.21.11's numbers: header 61 and footer 33 pixels, rows 20 tall and 350 wide, the state buttons 60 by 16, the
 * profile buttons 120 wide and Done 60, 8 apart.
 */
@SideOnly(Side.CLIENT)
public class DebugOptionsScreen extends GuiScreen {

    static final int HEADER = 61;
    static final int FOOTER = 33;
    static final int ROW_WIDTH = 350;
    /** {@code -2142128}: the warning's and the selected Always' red. */
    static final int RED = 0xDF5050;
    /** {@code -171}: the selected In Overlay's yellow. */
    static final int YELLOW = 0xFFFF55;
    /** {@code -4539718}: the other buttons' gray. */
    static final int GRAY = 0xBABABA;

    private static final int DEFAULT_BUTTON = 1;
    private static final int PERFORMANCE_BUTTON = 2;
    private static final int DONE_BUTTON = 200;

    private GuiTextField search;
    private OptionList list;
    private List<String> warning = new ArrayList<String>();
    private String query = "";

    /** For the client self-test: entries listed in the last layout. */
    public int listed;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        int third = ROW_WIDTH / 3;
        String title = I18n.format("debug.options.title");
        int titleWidth = this.fontRenderer.getStringWidth(title);
        int rowWidth = third + 8 + titleWidth + 8 + third;
        int rowX = this.width / 2 - rowWidth / 2;
        this.search = new GuiTextField(0, this.fontRenderer, rowX + third + 8 + titleWidth + 8, 8, third, 20);
        this.search.setMaxStringLength(64);
        this.search.setText(this.query);
        this.search.setFocused(true);
        this.warning = this.fontRenderer.listFormattedStringToWidth(I18n.format("debug.options.warning"), ROW_WIDTH);
        this.list = new OptionList(this.mc, this.width, this.height, HEADER, this.height - FOOTER);
        this.list.refill(this.query);
        int total = 120 + 8 + 120 + 8 + 60;
        int x = this.width / 2 - total / 2;
        int y = this.height - FOOTER + (FOOTER - 20) / 2;
        GuiButton def = new GuiButton(DEFAULT_BUTTON, x, y, 120, 20, I18n.format("debug.options.profile.default"));
        GuiButton perf = new GuiButton(PERFORMANCE_BUTTON, x + 128, y, 120, 20, I18n.format("debug.options.profile.performance"));
        def.enabled = !DebugEntries.usingProfile(DebugEntries.DEFAULT);
        perf.enabled = !DebugEntries.usingProfile(DebugEntries.PERFORMANCE);
        this.buttonList.add(def);
        this.buttonList.add(perf);
        this.buttonList.add(new GuiButton(DONE_BUTTON, x + 256, y, 60, 20, I18n.format("gui.done")));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == DONE_BUTTON) {
            this.mc.displayGuiScreen(null);
        } else if (button.id == DEFAULT_BUTTON || button.id == PERFORMANCE_BUTTON) {
            DebugEntries.loadProfile(button.id == DEFAULT_BUTTON ? DebugEntries.DEFAULT : DebugEntries.PERFORMANCE);
            for (GuiButton b : this.buttonList) {
                if (b.id == DEFAULT_BUTTON || b.id == PERFORMANCE_BUTTON) {
                    b.enabled = b != button;
                }
            }
            this.list.refresh();
        }
    }

    /** A state changed in the list: both profile buttons can be pressed again. */
    void custom() {
        for (GuiButton b : this.buttonList) {
            if (b.id == DEFAULT_BUTTON || b.id == PERFORMANCE_BUTTON) {
                b.enabled = true;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.search.textboxKeyTyped(typedChar, keyCode)) {
            if (!this.search.getText().equals(this.query)) {
                this.query = this.search.getText();
                this.list.refill(this.query);
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        this.search.mouseClicked(mouseX, mouseY, mouseButton);
        if (!this.list.mouseClicked(mouseX, mouseY, mouseButton)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state != 0 || !this.list.mouseReleased(mouseX, mouseY, state)) {
            super.mouseReleased(mouseX, mouseY, state);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        this.list.handleMouseInput();
    }

    @Override
    public void updateScreen() {
        this.search.updateCursorCounter();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (this.mc.world == null) {
            drawDefaultBackground();
        }
        this.list.drawScreen(mouseX, mouseY, partialTicks);
        String title = I18n.format("debug.options.title");
        int third = ROW_WIDTH / 3;
        int titleWidth = this.fontRenderer.getStringWidth(title);
        int rowX = this.width / 2 - (third + 8 + titleWidth + 8 + third) / 2;
        this.fontRenderer.drawStringWithShadow(title, rowX + third + 8, 14, 0xFFFFFF);
        this.search.drawTextBox();
        if (this.search.getText().isEmpty() && !this.search.isFocused()) {
            this.fontRenderer.drawStringWithShadow(TextFormatting.ITALIC + I18n.format("debug.options.search"), this.search.x + 4, this.search.y + 6, 0x808080);
        }
        int y = 36;
        for (String line : this.warning) {
            this.drawCenteredString(this.fontRenderer, line, this.width / 2, y, RED);
            y += this.fontRenderer.FONT_HEIGHT;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** The entries' list. */
    final class OptionList extends GuiListExtended {
        private final List<IGuiListEntry> rows = new ArrayList<IGuiListEntry>();

        OptionList(Minecraft mc, int width, int height, int top, int bottom) {
            super(mc, width, height, top, bottom, 20);
        }

        void refill(String query) {
            this.rows.clear();
            Boolean renderers = null;
            int n = 0;
            for (DebugEntries.Entry e : DebugEntries.ENTRIES) {
                if (!e.id.contains(query)) {
                    continue;
                }
                if (renderers == null || renderers != e.renderer) {
                    renderers = e.renderer;
                    this.rows.add(new CategoryRow(I18n.format(e.renderer ? "debug.options.category.renderer" : "debug.options.category.text")));
                }
                this.rows.add(new OptionRow(e));
                n++;
            }
            DebugOptionsScreen.this.listed = n;
            this.amountScrolled = 0.0F;
        }

        void refresh() {
            for (IGuiListEntry r : this.rows) {
                if (r instanceof OptionRow) {
                    ((OptionRow) r).refresh();
                }
            }
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
            return ROW_WIDTH;
        }

        @Override
        protected int getScrollBarX() {
            return this.width / 2 + ROW_WIDTH / 2 + 10;
        }
    }

    /** A category's title, centered ({@code CategoryEntry}). */
    final class CategoryRow implements GuiListExtended.IGuiListEntry {
        private final String text;

        CategoryRow(String text) {
            this.text = text;
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            drawCenteredString(fontRenderer, this.text, x + listWidth / 2, y + 5, 0xFFFFFF);
        }

        @Override
        public void updatePosition(int slotIndex, int x, int y, float partialTicks) {
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
            return false;
        }

        @Override
        public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {
        }
    }

    /** One entry: its name, then Off, In F3 and Always ({@code OptionEntry}). */
    final class OptionRow implements GuiListExtended.IGuiListEntry {
        private final DebugEntries.Entry entry;
        private final StateButton never;
        private final StateButton overlay;
        private final StateButton always;

        OptionRow(DebugEntries.Entry entry) {
            this.entry = entry;
            this.never = new StateButton(I18n.format("options.off"), 0xFFFFFF, DebugEntries.Status.NEVER);
            boolean f3 = !com.exiledradio.backportcore.presentation.VanillaRevisions.debugKeyBinds(Presentation.newest());
            this.overlay = new StateButton(I18n.format(f3 ? "debug.entry.f3" : "debug.entry.overlay"), YELLOW, DebugEntries.Status.IN_OVERLAY);
            this.always = new StateButton(I18n.format("debug.entry.always"), RED, DebugEntries.Status.ALWAYS_ON);
            refresh();
        }

        void refresh() {
            DebugEntries.Status s = DebugEntries.status(this.entry.id);
            for (StateButton b : new StateButton[] {this.never, this.overlay, this.always}) {
                b.selected = b.status == s;
                b.enabled = !b.selected;
            }
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            boolean allowed = this.entry.allowed(mc.isReducedDebug());
            String name = allowed ? this.entry.id : TextFormatting.ITALIC + this.entry.id;
            fontRenderer.drawStringWithShadow(name, x, y + 5, allowed ? 0xFFFFFF : 0x808080);
            int bx = x + listWidth - 180;
            for (StateButton b : new StateButton[] {this.never, this.overlay, this.always}) {
                b.x = bx;
                b.y = y;
                b.drawButton(mc, mouseX, mouseY, partialTicks);
                bx += 60;
            }
            if (!allowed && mouseX >= x && mouseX < x + listWidth - 180 && mouseY >= y && mouseY < y + slotHeight) {
                drawHoveringText(fontRenderer.listFormattedStringToWidth(I18n.format("debug.options.notAllowed.tooltip"), 200), mouseX, mouseY);
            }
        }

        @Override
        public void updatePosition(int slotIndex, int x, int y, float partialTicks) {
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
            for (StateButton b : new StateButton[] {this.never, this.overlay, this.always}) {
                if (b.mousePressed(mc, mouseX, mouseY)) {
                    b.playPressSound(mc.getSoundHandler());
                    DebugEntries.setStatus(this.entry.id, b.status);
                    refresh();
                    custom();
                    return true;
                }
            }
            return false;
        }

        @Override
        public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {
        }
    }

    /**
     * A 60 by 16 button showing only its value: the selected one (inactive) in its color, the others gray, as
     * 1.21.9's {@code CycleButton.booleanBuilder(...).displayOnlyValue()}. 1.12.2's button texture is 20 tall: its top
     * and bottom 8 rows are drawn.
     */
    static final class StateButton extends GuiButton {
        final DebugEntries.Status status;
        final int color;
        boolean selected;

        StateButton(String text, int color, DebugEntries.Status status) {
            super(OptionWidgets.nextId(), 0, 0, 60, 16, text);
            this.color = color;
            this.status = status;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            mc.getTextureManager().bindTexture(BUTTON_TEXTURES);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            int v = 46 + getHoverState(this.hovered) * 20;
            int half = this.width / 2;
            drawTexturedModalRect(this.x, this.y, 0, v, half, 8);
            drawTexturedModalRect(this.x + half, this.y, 200 - half, v, half, 8);
            drawTexturedModalRect(this.x, this.y + 8, 0, v + 12, half, 8);
            drawTexturedModalRect(this.x + half, this.y + 8, 200 - half, v + 12, half, 8);
            drawCenteredString(mc.fontRenderer, this.displayString, this.x + half, this.y + 4, this.selected ? this.color : GRAY);
        }
    }
}

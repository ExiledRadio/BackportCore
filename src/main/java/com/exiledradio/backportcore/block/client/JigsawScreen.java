package com.exiledradio.backportcore.block.client;

import com.exiledradio.backportcore.block.BlockJigsaw;
import com.exiledradio.backportcore.block.JigsawNetwork;
import com.exiledradio.backportcore.block.TileEntityJigsaw;
import com.exiledradio.backportcore.presentation.client.PresentationHooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 26.3's {@code JigsawBlockEditScreen}, widget for widget and in its places: Target Pool, Name, Target Name and Turns
 * Into fields, Selection Priority and Placement Priority (each with its tooltip), the Joint Type button (only for a
 * jigsaw facing up or down), the Levels slider (0 to 20), Keep Jigsaws (on at first), Generate, Done and Cancel. Done
 * and Generate are off while a name, target or pool is not a valid id. Done sends the fields; Generate sends them and
 * then asks the server to generate. Since Core 1.4.5.
 */
@SideOnly(Side.CLIENT)
public class JigsawScreen extends GuiScreen {

    static final int LABEL = 0xA0A0A0;
    static final int TOOLTIP_WIDTH = 170;

    private final TileEntityJigsaw jigsaw;
    /** The fields' text kept across a resize (1.12.2 builds every widget again). Widgets are public for the self-test. */
    @Nullable
    private String[] kept;

    public GuiTextField poolEdit;
    public GuiTextField nameEdit;
    public GuiTextField targetEdit;
    public GuiTextField finalStateEdit;
    public GuiTextField selectionPriorityEdit;
    public GuiTextField placementPriorityEdit;
    private final List<GuiTextField> fields = new ArrayList<GuiTextField>();

    public int levels;
    public boolean keepJigsaws = true;
    public TileEntityJigsaw.Joint joint;
    double sliderValue;

    public GuiButton jointButton;
    public GuiButton keepButton;
    public GuiButton generateButton;
    public GuiButton doneButton;
    public GuiButton cancelButton;
    public LevelsSlider levelsSlider;

    public JigsawScreen(TileEntityJigsaw jigsaw) {
        this.jigsaw = jigsaw;
        this.joint = jigsaw.getJoint();
    }

    public TileEntityJigsaw jigsaw() {
        return this.jigsaw;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        this.fields.clear();
        int cx = this.width / 2;
        this.poolEdit = field(0, cx - 153, 20, 300, 128, this.jigsaw.getPool());
        this.nameEdit = field(1, cx - 153, 55, 300, 128, this.jigsaw.getName());
        this.targetEdit = field(2, cx - 153, 90, 300, 128, this.jigsaw.getTarget());
        this.finalStateEdit = field(3, cx - 153, 125, 300, 256, this.jigsaw.getFinalState());
        this.selectionPriorityEdit = field(4, cx - 153, 160, 98, 3, Integer.toString(this.jigsaw.getSelectionPriority()));
        this.placementPriorityEdit = field(5, cx - 50, 160, 98, 3, Integer.toString(this.jigsaw.getPlacementPriority()));
        if (this.kept != null) {
            for (int i = 0; i < this.fields.size(); i++) {
                this.fields.get(i).setText(this.kept[i]);
            }
        }
        this.poolEdit.setFocused(true);

        this.jointButton = addButton(new GuiButton(10, cx + 54, 160, 100, 20, jointLabel()));
        boolean vertical = vertical();
        this.jointButton.enabled = vertical;
        this.jointButton.visible = vertical;
        this.levelsSlider = addButton(new LevelsSlider(11, cx - 154, 185));
        this.keepButton = addButton(new GuiButton(12, cx - 50, 185, 100, 20, keepLabel()));
        this.generateButton = addButton(new GuiButton(13, cx + 54, 185, 100, 20, I18n.format("jigsaw_block.generate")));
        this.doneButton = addButton(new GuiButton(14, cx - 4 - 150, 210, 150, 20, I18n.format("gui.done")));
        this.cancelButton = addButton(new GuiButton(15, cx + 4, 210, 150, 20, I18n.format("gui.cancel")));
        updateValidity();
    }

    private GuiTextField field(int id, int x, int y, int width, int max, String text) {
        GuiTextField f = new GuiTextField(id, this.fontRenderer, x, y, width, 20);
        f.setMaxStringLength(max);
        f.setText(text);
        this.fields.add(f);
        return f;
    }

    boolean vertical() {
        return this.jigsaw.front().getAxis().isVertical();
    }

    String jointLabel() {
        return I18n.format("jigsaw_block.joint." + this.joint.getName());
    }

    String keepLabel() {
        return I18n.format("jigsaw_block.keep_jigsaws") + ": " + I18n.format(this.keepJigsaws ? "options.on" : "options.off");
    }

    String levelsLabel() {
        return I18n.format("jigsaw_block.levels", this.levels);
    }

    @Override
    public void onResize(Minecraft mc, int w, int h) {
        this.kept = new String[this.fields.size()];
        for (int i = 0; i < this.fields.size(); i++) {
            this.kept[i] = this.fields.get(i).getText();
        }
        super.onResize(mc, w, h);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        for (GuiTextField f : this.fields) {
            f.updateCursorCounter();
        }
    }

    public void updateValidity() {
        boolean valid = BlockJigsaw.isValidId(this.nameEdit.getText()) && BlockJigsaw.isValidId(this.targetEdit.getText())
                && BlockJigsaw.isValidId(this.poolEdit.getText());
        this.doneButton.enabled = valid;
        this.generateButton.enabled = valid;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) {
            return;
        }
        if (button == this.jointButton) {
            TileEntityJigsaw.Joint[] all = TileEntityJigsaw.Joint.values();
            this.joint = all[(this.joint.ordinal() + 1) % all.length];
            this.jointButton.displayString = jointLabel();
        } else if (button == this.keepButton) {
            this.keepJigsaws = !this.keepJigsaws;
            this.keepButton.displayString = keepLabel();
        } else if (button == this.generateButton) {
            onDone();
            JigsawNetwork.send(new JigsawNetwork.Generate(this.jigsaw.getPos(), this.levels, this.keepJigsaws));
        } else if (button == this.doneButton) {
            onDone();
        } else if (button == this.cancelButton) {
            this.mc.displayGuiScreen(null);
        }
    }

    /** The message Done sends, as 26.3's {@code sendToServer} builds it. */
    public JigsawNetwork.Set message() {
        return new JigsawNetwork.Set(this.jigsaw.getPos(), this.nameEdit.getText(), this.targetEdit.getText(), this.poolEdit.getText(),
                this.finalStateEdit.getText(), this.joint, parseAsInt(this.selectionPriorityEdit.getText()),
                parseAsInt(this.placementPriorityEdit.getText()));
    }

    private void onDone() {
        JigsawNetwork.send(message());
        this.mc.displayGuiScreen(null);
    }

    static int parseAsInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_TAB) {
            int at = 0;
            for (int i = 0; i < this.fields.size(); i++) {
                if (this.fields.get(i).isFocused()) {
                    at = i;
                }
            }
            int next = (at + (isShiftKeyDown() ? this.fields.size() - 1 : 1)) % this.fields.size();
            for (int i = 0; i < this.fields.size(); i++) {
                this.fields.get(i).setFocused(i == next);
            }
            return;
        }
        for (GuiTextField f : this.fields) {
            if (f.isFocused() && f.textboxKeyTyped(typedChar, keyCode)) {
                updateValidity();
                return;
            }
        }
        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && this.doneButton.enabled) {
            onDone();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        for (GuiTextField f : this.fields) {
            f.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        int x = this.width / 2 - 153;
        drawString(this.fontRenderer, I18n.format("jigsaw_block.pool"), x, 10, LABEL);
        this.poolEdit.drawTextBox();
        drawString(this.fontRenderer, I18n.format("jigsaw_block.name"), x, 45, LABEL);
        this.nameEdit.drawTextBox();
        drawString(this.fontRenderer, I18n.format("jigsaw_block.target"), x, 80, LABEL);
        this.targetEdit.drawTextBox();
        drawString(this.fontRenderer, I18n.format("jigsaw_block.final_state"), x, 115, LABEL);
        this.finalStateEdit.drawTextBox();
        drawString(this.fontRenderer, I18n.format("jigsaw_block.selection_priority"), x, 150, LABEL);
        this.placementPriorityEdit.drawTextBox();
        drawString(this.fontRenderer, I18n.format("jigsaw_block.placement_priority"), this.width / 2 - 50, 150, LABEL);
        this.selectionPriorityEdit.drawTextBox();
        if (vertical()) {
            drawString(this.fontRenderer, I18n.format("jigsaw_block.joint_label"), this.width / 2 + 53, 150, LABEL);
        }
        String tooltip = hovered(this.selectionPriorityEdit, mouseX, mouseY) ? "jigsaw_block.selection_priority.tooltip"
                : hovered(this.placementPriorityEdit, mouseX, mouseY) ? "jigsaw_block.placement_priority.tooltip" : null;
        if (tooltip != null) {
            drawHoveringText(tooltipLines(I18n.format(tooltip)), mouseX, mouseY);
        }
    }

    private static boolean hovered(GuiTextField f, int mouseX, int mouseY) {
        return mouseX >= f.x && mouseX < f.x + f.width && mouseY >= f.y && mouseY < f.y + f.height;
    }

    /** Today's {@code Tooltip.splitTooltip}: its own line breaks, each wrapped at 170 pixels. */
    List<String> tooltipLines(String text) {
        List<String> out = new ArrayList<String>();
        for (String part : text.replace("\\n", "\n").split("\n", -1)) {
            if (part.isEmpty()) {
                out.add("");
            } else {
                out.addAll(this.fontRenderer.listFormattedStringToWidth(part, TOOLTIP_WIDTH));
            }
        }
        return out;
    }

    /** Today's {@code AbstractSliderButton} for Levels: 0 to 20, {@code floor(clampedLerp(value, 0, 20))}. */
    public final class LevelsSlider extends GuiButton {
        private boolean dragging;

        LevelsSlider(int id, int x, int y) {
            super(id, x, y, 100, 20, "");
            this.displayString = levelsLabel();
        }

        public void setValue(double value) {
            JigsawScreen.this.sliderValue = Math.max(0.0, Math.min(1.0, value));
            JigsawScreen.this.levels = (int) Math.floor(JigsawScreen.this.sliderValue * 20.0);
            this.displayString = levelsLabel();
        }

        @Override
        protected int getHoverState(boolean mouseOver) {
            return 0;
        }

        @Override
        protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
            if (this.visible) {
                if (this.dragging) {
                    setValue((double) (mouseX - (this.x + 4)) / (double) (this.width - 8));
                }
                mc.getTextureManager().bindTexture(BUTTON_TEXTURES);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                int knob = this.x + (int) (JigsawScreen.this.sliderValue * (double) (this.width - 8));
                drawTexturedModalRect(knob, this.y, 0, PresentationHooks.sliderHandleRow(this, 66), 4, 20);
                drawTexturedModalRect(knob + 4, this.y, 196, PresentationHooks.sliderHandleRow(this, 66), 4, 20);
            }
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                setValue((double) (mouseX - (this.x + 4)) / (double) (this.width - 8));
                this.dragging = true;
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            this.dragging = false;
        }
    }
}

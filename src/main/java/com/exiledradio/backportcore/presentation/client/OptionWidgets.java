package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.option.CycleOption;
import com.exiledradio.backportcore.presentation.option.PresentationOption;
import com.exiledradio.backportcore.presentation.option.SliderOption;
import com.exiledradio.backportcore.presentation.option.ToggleOption;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The buttons Core draws for {@link PresentationOption}s, looking and behaving as 1.12.2's own option buttons
 * and sliders do ({@code GuiOptionButton}, {@code GuiOptionSlider}). Each changes its option itself when
 * pressed, so it works both in a screen's button list and in a scrolling options list (Video Settings),
 * which only calls {@code mousePressed}.
 */
@SideOnly(Side.CLIENT)
public final class OptionWidgets {

    /** Core's button ids start here, well clear of vanilla's (0 to 200) and of the usual mod ranges. */
    public static final int FIRST_ID = 0x5BC000;
    private static int nextId = FIRST_ID;

    private OptionWidgets() {
    }

    static synchronized int nextId() {
        return nextId++;
    }

    /** @return the button for an option, 150 wide as vanilla's option buttons */
    public static GuiButton create(PresentationOption option, int x, int y) {
        if (option instanceof SliderOption) {
            return new Slider((SliderOption) option, x, y);
        }
        if (option instanceof CycleOption) {
            return new Cycle((CycleOption) option, x, y);
        }
        if (option instanceof ToggleOption) {
            return new Toggle((ToggleOption) option, x, y);
        }
        throw new IllegalArgumentException("no button for " + option.getClass().getName());
    }

    /** A button of one of Core's options. */
    public interface OptionButton {
        PresentationOption option();

        void refresh();
    }

    /**
     * @return a slider's text: "Label: value", with 1.16's OFF at zero ({@link SliderOption#zeroKey}), or one whole
     * lang string ({@link SliderOption#wholeLabel}, 1.16's "Chat Delay: 2.0 seconds" and "Chat Delay: None")
     */
    public static String label(SliderOption option) {
        if (option.wholeKey() != null) {
            if (option.isZero() && option.wholeZeroKey() != null) {
                return I18n.format(option.wholeZeroKey());
            }
            return I18n.format(option.wholeKey(), option.valueText());
        }
        String value;
        if (option.isZero() && option.zeroKey() != null) {
            value = I18n.format(option.zeroKey());
        } else {
            value = option.valueKey() != null ? I18n.format(option.valueKey(), option.valueText()) : option.valueText();
        }
        return I18n.format(option.labelKey()) + ": " + value;
    }

    /** A plain button that runs an action when pressed (opening one of Core's screens). */
    public static class Action extends GuiButton {
        final Runnable action;

        public Action(int x, int y, int width, String text, Runnable action) {
            super(nextId(), x, y, width, 20, text);
            this.action = action;
        }
    }

    static final class Toggle extends GuiButton implements OptionButton {
        private final ToggleOption option;

        Toggle(ToggleOption option, int x, int y) {
            super(nextId(), x, y, 150, 20, "");
            this.option = option;
            refresh();
        }

        @Override
        public PresentationOption option() {
            return this.option;
        }

        @Override
        public void refresh() {
            this.displayString = I18n.format(this.option.labelKey()) + ": " + I18n.format(this.option.get() ? "options.on" : "options.off");
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                this.option.set(!this.option.get());
                refresh();
                return true;
            }
            return false;
        }
    }

    static final class Cycle extends GuiButton implements OptionButton {
        private final CycleOption option;

        Cycle(CycleOption option, int x, int y) {
            super(nextId(), x, y, 150, 20, "");
            this.option = option;
            refresh();
        }

        @Override
        public PresentationOption option() {
            return this.option;
        }

        @Override
        public void refresh() {
            this.displayString = I18n.format(this.option.labelKey()) + ": " + I18n.format(this.option.valueKey());
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                this.option.cycle(GuiScreen.isShiftKeyDown());
                refresh();
                return true;
            }
            return false;
        }
    }

    /** As 1.12.2's {@code GuiOptionSlider}. */
    static final class Slider extends GuiButton implements OptionButton {
        private final SliderOption option;
        private boolean dragging;
        /** 1.21.11: where the knob is while dragged; it snaps to the nearest value on release. */
        private double dragFraction;

        Slider(SliderOption option, int x, int y) {
            super(nextId(), x, y, 150, 20, "");
            this.option = option;
            refresh();
        }

        @Override
        public PresentationOption option() {
            return this.option;
        }

        @Override
        public void refresh() {
            this.displayString = label(this.option);
        }

        @Override
        protected int getHoverState(boolean mouseOver) {
            return 0;
        }

        @Override
        protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
            if (this.visible) {
                if (this.dragging) {
                    this.dragFraction = Math.max(0.0, Math.min(1.0, (double) (mouseX - (this.x + 4)) / (double) (this.width - 8)));
                    this.option.setFraction(this.dragFraction);
                    refresh();
                }
                mc.getTextureManager().bindTexture(BUTTON_TEXTURES);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                double at = this.dragging && snapsOnRelease() ? this.dragFraction : this.option.fraction();
                int knob = this.x + (int) (at * (double) (this.width - 8));
                drawTexturedModalRect(knob, this.y, 0, 66, 4, 20);
                drawTexturedModalRect(knob + 4, this.y, 196, 66, 4, 20);
            }
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                this.dragFraction = Math.max(0.0, Math.min(1.0, (double) (mouseX - (this.x + 4)) / (double) (this.width - 8)));
                this.option.setFraction(this.dragFraction);
                refresh();
                this.dragging = true;
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            this.dragging = false;
        }

        /** 1.21.11 (25w41a): the knob follows the mouse and snaps to the nearest value when let go. */
        static boolean snapsOnRelease() {
            return com.exiledradio.backportcore.presentation.Presentation.isAtLeast(com.exiledradio.backportcore.presentation.LookArea.OPTIONS, "1.21.11");
        }
    }
}

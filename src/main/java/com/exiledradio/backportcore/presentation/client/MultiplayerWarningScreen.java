package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.OptionStore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.List;

/**
 * 1.15.2's multiplayer warning ({@code SafetyScreen}, 1.16.5's layout): the header in bold at 25, 30, the message
 * wrapped to the width less 50 from y 70, 18 pixels a line, "Do not show this screen again" and the Proceed and
 * Back buttons below it. The wording is the newest installed version's ({@link Presentation#multiplayerWarning}).
 */
@SideOnly(Side.CLIENT)
public final class MultiplayerWarningScreen extends GuiScreen {

    private static final ResourceLocation CHECKBOX = new ResourceLocation("textures/gui/checkbox.png");

    private final GuiScreen previous;
    private final String[] keys;
    private List<String> lines = Collections.emptyList();
    private Checkbox stopShowing;

    MultiplayerWarningScreen(GuiScreen previous, String[] keys) {
        this.previous = previous;
        this.keys = keys.clone();
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.lines = this.fontRenderer.listFormattedStringToWidth(I18n.format(this.keys[1]), this.width - 50);
        int k = (this.lines.size() + 1) * 9 * 2;
        this.buttonList.add(new GuiButton(0, this.width / 2 - 155, 100 + k, 150, 20, I18n.format("gui.proceed")));
        this.buttonList.add(new GuiButton(1, this.width / 2 - 155 + 160, 100 + k, 150, 20, I18n.format("gui.back")));
        boolean was = this.stopShowing != null && this.stopShowing.selected;
        this.stopShowing = new Checkbox(2, this.width / 2 - 155 + 80, 76 + k, 150, I18n.format(this.keys[2]), was);
        this.buttonList.add(this.stopShowing);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            if (this.stopShowing.selected) {
                OptionStore.put(TitleScreenExtras.SKIP_WARNING, "true");
            }
            TitleScreenExtras.openServerList(this.previous);
        } else if (button.id == 1) {
            this.mc.displayGuiScreen(this.previous);
        } else if (button.id == 2) {
            this.stopShowing.selected = !this.stopShowing.selected;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackground(0);
        drawString(this.fontRenderer, TextFormatting.BOLD + I18n.format(this.keys[0]), 25, 30, 0xFFFFFF);
        int y = 70;
        for (String line : this.lines) {
            drawString(this.fontRenderer, line, 25, y, 0xFFFFFF);
            y += 18;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** 1.16's 20 by 20 {@code Checkbox}, from {@code textures/gui/checkbox.png} when a version ships it. */
    static final class Checkbox extends GuiButton {
        boolean selected;

        Checkbox(int id, int x, int y, int width, String text, boolean selected) {
            super(id, x, y, width, 20, text);
            this.selected = selected;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            if (Presentation.asset("textures/gui/checkbox.png") != null) {
                mc.getTextureManager().bindTexture(CHECKBOX);
                GlStateManager.enableBlend();
                Gui.drawModalRectWithCustomSizedTexture(this.x, this.y, this.hovered ? 20.0F : 0.0F, this.selected ? 20.0F : 0.0F, 20, this.height, 64.0F, 64.0F);
            } else {
                drawRect(this.x, this.y, this.x + 20, this.y + 20, this.hovered ? 0xFFFFFFFF : 0xFFA0A0A0);
                drawRect(this.x + 1, this.y + 1, this.x + 19, this.y + 19, 0xFF000000);
                if (this.selected) {
                    drawCenteredString(mc.fontRenderer, "✔", this.x + 10, this.y + 6, 0xFFFFFF);
                }
            }
            drawString(mc.fontRenderer, this.displayString, this.x + 24, this.y + (this.height - 8) / 2, 14737632);
        }
    }
}

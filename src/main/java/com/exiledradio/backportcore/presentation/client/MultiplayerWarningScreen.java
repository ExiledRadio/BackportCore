package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.option.OptionStore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * The multiplayer warning ({@code SafetyScreen} over {@code WarningScreen}) before the server list, in the newest
 * installed version's layout ({@link VanillaRevisions.WarningLayout}); the wording is that version's
 * ({@link Presentation#multiplayerWarning}).
 *
 * <ul>
 *   <li>1.15.2 to 1.20.4: the header in bold at 25, 30, the message wrapped to {@code width - 50} from y 70, 18 pixels
 *       a line, "Do not show this screen again" and Proceed and Back below it.</li>
 *   <li>1.20.5 to 1.21.8: one column centered in the screen, 8 apart: the bold title, the message centered in
 *       {@code width - 100} (12 around it, 4 inside), the check box, then Proceed and Back (150 each, 8 apart).</li>
 *   <li>1.21.9 (26.3's): the same column, the message in a {@code FittingMultiLineTextWidget}: framed as the
 *       {@code widget/text_field} sprite (a one-pixel 0xA0A0A0 border around black), {@code width - 100} wide, as tall
 *       as the text and 4 inside it, single-spaced, at most {@code height - 100}; taller text scrolls (the mouse wheel,
 *       9 pixels a notch) with a scroll bar on its right edge.</li>
 * </ul>
 * Nothing else is drawn: no box behind the column, and the screen's background is the menu background.
 */
@SideOnly(Side.CLIENT)
public final class MultiplayerWarningScreen extends GuiScreen {

    private static final ResourceLocation CHECKBOX = new ResourceLocation("textures/gui/checkbox.png");
    /** {@code AbstractTextAreaWidget.innerPadding}. */
    static final int INNER = 4;
    /** {@code WarningScreen}'s padding around the message's cell. */
    static final int CELL = 12;

    private final GuiScreen previous;
    private final String[] keys;
    private List<String> lines = Collections.emptyList();
    private Checkbox stopShowing;
    private VanillaRevisions.WarningLayout layout = VanillaRevisions.WarningLayout.CLASSIC;
    /** The message's frame (the column layouts). */
    int boxX;
    int boxY;
    int boxWidth;
    int boxHeight;
    private int titleY;
    private int scrolled;

    MultiplayerWarningScreen(GuiScreen previous, String[] keys) {
        this.previous = previous;
        this.keys = keys.clone();
    }

    /** @return the layout this screen was laid out in (for the client self-test) */
    public VanillaRevisions.WarningLayout layout() {
        return this.layout;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.layout = VanillaRevisions.WarningLayout.forVersion(Presentation.newest(LookArea.MENUS));
        boolean was = this.stopShowing != null && this.stopShowing.selected;
        String check = I18n.format(this.keys[2]);
        if (this.layout == VanillaRevisions.WarningLayout.CLASSIC) {
            this.lines = this.fontRenderer.listFormattedStringToWidth(I18n.format(this.keys[1]), this.width - 50);
            int k = (this.lines.size() + 1) * 9 * 2;
            this.buttonList.add(new GuiButton(0, this.width / 2 - 155, 100 + k, 150, 20, I18n.format("gui.proceed")));
            this.buttonList.add(new GuiButton(1, this.width / 2 - 155 + 160, 100 + k, 150, 20, I18n.format("gui.back")));
            this.stopShowing = new Checkbox(2, this.width / 2 - 155 + 80, 76 + k, 150, check, was);
            this.buttonList.add(this.stopShowing);
            return;
        }
        boolean boxed = this.layout == VanillaRevisions.WarningLayout.BOXED_1_21_9;
        this.boxWidth = this.width - 100;
        this.lines = this.fontRenderer.listFormattedStringToWidth(I18n.format(this.keys[1]), this.boxWidth - (boxed ? 2 * INNER : 0));
        int text = this.lines.size() * 9;
        this.boxHeight = boxed ? Math.min(text + 2 * INNER, this.height - 100) : text + 2 * INNER;
        this.scrolled = MathHelper.clamp(this.scrolled, 0, maxScroll());
        int checkWidth = 24 + this.fontRenderer.getStringWidth(check);
        int total = 9 + 8 + (CELL + this.boxHeight + CELL) + 8 + 20 + 8 + 20;
        int y = (this.height - total) / 2;
        this.titleY = y;
        y += 9 + 8 + CELL;
        this.boxX = this.width / 2 - this.boxWidth / 2;
        this.boxY = y;
        y += this.boxHeight + CELL + 8;
        this.stopShowing = new Checkbox(2, this.width / 2 - checkWidth / 2, y, checkWidth, check, was);
        this.buttonList.add(this.stopShowing);
        y += 20 + 8;
        GuiButton proceed = new GuiButton(0, 0, 0, 150, 20, I18n.format("gui.proceed"));
        GuiButton back = new GuiButton(1, 0, 0, 150, 20, I18n.format("gui.back"));
        HeaderFooterLayout.row(this.width / 2, y, proceed, back);
        this.buttonList.add(proceed);
        this.buttonList.add(back);
    }

    /** @return how far the message can scroll (0 when it fits) */
    int maxScroll() {
        if (this.layout != VanillaRevisions.WarningLayout.BOXED_1_21_9) {
            return 0;
        }
        return Math.max(0, this.lines.size() * 9 + 2 * INNER - this.boxHeight);
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
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && maxScroll() > 0) {
            // Cleanroom's lwjglx reports 1 a notch, LWJGL 2 120: only the sign is used.
            this.scrolled = MathHelper.clamp(this.scrolled - Integer.signum(wheel) * 9, 0, maxScroll());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackground(0);
        String title = TextFormatting.BOLD + I18n.format(this.keys[0]);
        if (this.layout == VanillaRevisions.WarningLayout.CLASSIC) {
            drawString(this.fontRenderer, title, 25, 30, 0xFFFFFF);
            int y = 70;
            for (String line : this.lines) {
                drawString(this.fontRenderer, line, 25, y, 0xFFFFFF);
                y += 18;
            }
        } else {
            drawCenteredString(this.fontRenderer, title, this.width / 2, this.titleY, 0xFFFFFF);
            if (this.layout == VanillaRevisions.WarningLayout.BOXED_1_21_9) {
                drawBoxed();
            } else {
                int y = this.boxY + INNER;
                for (String line : this.lines) {
                    drawCenteredString(this.fontRenderer, line, this.width / 2, y, 0xFFFFFF);
                    y += 9;
                }
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** {@code AbstractTextAreaWidget.extractWidgetRenderState}: the frame, the text clipped inside it, the scroll bar. */
    private void drawBoxed() {
        int x0 = this.boxX;
        int y0 = this.boxY;
        int x1 = x0 + this.boxWidth;
        int y1 = y0 + this.boxHeight;
        drawRect(x0, y0, x1, y1, 0xFFA0A0A0);
        drawRect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF000000);
        boolean scissor = maxScroll() > 0;
        if (scissor) {
            int scale = new net.minecraft.client.gui.ScaledResolution(this.mc).getScaleFactor();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor((x0 + 1) * scale, this.mc.displayHeight - (y1 - 1) * scale, (this.boxWidth - 2) * scale, (this.boxHeight - 2) * scale);
        }
        int y = y0 + INNER - this.scrolled;
        for (String line : this.lines) {
            drawString(this.fontRenderer, line, x0 + INNER, y, 0xFFFFFF);
            y += 9;
        }
        if (scissor) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            // 26.3's scroll bar (scroller_background, scroller), 6 wide on the frame's right edge.
            int content = this.lines.size() * 9 + 2 * INNER;
            int thumb = MathHelper.clamp(this.boxHeight * this.boxHeight / content, 32, this.boxHeight - 8);
            int thumbY = y0 + this.scrolled * (this.boxHeight - thumb) / maxScroll();
            drawRect(x1, y0, x1 + 6, y1, 0xFF000000);
            drawRect(x1, thumbY, x1 + 6, thumbY + thumb, 0xFF808080);
            drawRect(x1, thumbY, x1 + 5, thumbY + thumb - 1, 0xFFC0C0C0);
        }
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
                if (Presentation.isAtLeast(LookArea.MENUS, "1.16")) {
                    // 1.16's 64 by 64 texture: a hovered column beside the plain one.
                    Gui.drawModalRectWithCustomSizedTexture(this.x, this.y, this.hovered ? 20.0F : 0.0F, this.selected ? 20.0F : 0.0F, 20, this.height, 64.0F, 64.0F);
                } else {
                    // 1.15.2's 32 by 64 texture: plain and ticked, no hovered look.
                    Gui.drawModalRectWithCustomSizedTexture(this.x, this.y, 0.0F, this.selected ? 20.0F : 0.0F, 20, this.height, 32.0F, 64.0F);
                }
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

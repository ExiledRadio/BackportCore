package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.option.OptionStore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiOptionButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenOptionsSounds;
import net.minecraft.client.gui.chat.NarratorChatListener;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.List;

/**
 * 1.19.4's accessibility onboarding ({@code AccessibilityOnboardingScreen}, 26.3's layout): on the very first start the
 * game opens a welcome screen instead of the title screen, once. Vanilla keeps {@code onboardAccessibility} in
 * options.txt (true until the screen is left, so a player who updates sees it once too); Core keeps
 * {@code backportcore:onboard_accessibility} in its own options file.
 *
 * <p>26.3's screen: the logo (as the title screen draws it), the text "Welcome to Minecraft! ..." in a black box with a
 * grey outline (a {@code FocusableTextWidget}, 374 wide at most, padding 4, the box's cell padded 8), then two columns of
 * 150 wide buttons padded 4: Narrator and Music &amp; Sounds..., Accessibility Settings... and Language...; Continue in
 * the 33 tall footer. The text and buttons sit centered between a 90 tall header and the footer. The narrator says
 * "Press enter to enable the narrator" once the window is active, and Enter turns the narrator on (its button has the
 * focus in 26.3). Leaving the screen any way ends the onboarding; Continue, Escape and the settings screens' Done lead
 * to the title screen.
 */
@SideOnly(Side.CLIENT)
public final class AccessibilityOnboarding {

    public static final String STORE_KEY = "backportcore:onboard_accessibility";
    private boolean checked;

    /** For the client self-test: times the screen was opened. */
    public static volatile int shown;

    /** @return true while the next title screen should be the onboarding (the first start at 1.19.4's look or later) */
    public static boolean pending() {
        return VanillaRevisions.accessibilityOnboarding(Presentation.newest(LookArea.OPTIONS)) && !"false".equals(OptionStore.get(STORE_KEY));
    }

    /** Vanilla's {@code Options.onboardingAccessibilityFinished()}. */
    static void finished() {
        OptionStore.put(STORE_KEY, "false");
    }

    /** The first title screen of the session becomes the onboarding while it is pending. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onOpen(GuiOpenEvent event) {
        if (this.checked || !(event.getGui() instanceof GuiMainMenu)) {
            return;
        }
        this.checked = true;
        if (pending()) {
            event.setGui(new Screen());
            shown++;
            BackportCore.LOGGER.info("Presentation: first start, showing the accessibility onboarding");
        }
    }

    /** The screen. */
    public static final class Screen extends GuiScreen {

        private static final int TEXT_WIDTH = 374;
        private static final int OUTLINE = 0xFFA0A0A0;
        private static final int CONTINUE = 200;
        private static final int NARRATOR = 1;
        private static final int SOUNDS = 2;
        private static final int ACCESSIBILITY = 3;
        private static final int LANGUAGE = 4;

        private List<String> lines;
        private int textX;
        private int textY;
        private int textW;
        private int textH;
        private int ticks;
        private boolean narrated;
        private boolean ended;

        @Override
        public void initGui() {
            this.buttonList.clear();
            this.lines = this.fontRenderer.listFormattedStringToWidth(
                    I18n.format("accessibility.onboarding.screen.title").replace("\\n", "\n"), TEXT_WIDTH - 8);
            int widest = 0;
            for (String l : this.lines) {
                widest = Math.max(widest, this.fontRenderer.getStringWidth(l));
            }
            this.textW = widest + 8;
            this.textH = this.lines.size() * this.fontRenderer.FONT_HEIGHT + 8;
            // The contents: the text's cell (padding 8) over the grid's (padding 4; its cells 158 by 28).
            int contents = this.textH + 16 + 56 + 8;
            int top = 90 + Math.max(0, (this.height - 33 - 90 - contents) / 2);
            this.textX = this.width / 2 - this.textW / 2;
            this.textY = top + 8;
            int gridY = top + this.textH + 16 + 4;
            int left = this.width / 2 - 154;
            int right = this.width / 2 + 4;
            GuiOptionButton narrator = new GuiOptionButton(NARRATOR, left, gridY + 4, GameSettings.Options.NARRATOR,
                    this.mc.gameSettings.getKeyBinding(GameSettings.Options.NARRATOR));
            narrator.enabled = NarratorChatListener.INSTANCE.isActive();
            this.buttonList.add(narrator);
            this.buttonList.add(new IconButton(SOUNDS, right, gridY + 4, I18n.format("options.sounds"), "icon/music_notes", 16));
            this.buttonList.add(new IconButton(ACCESSIBILITY, left, gridY + 32, I18n.format("accessibility.onboarding.accessibility.button"),
                    "icon/accessibility", 15));
            this.buttonList.add(new IconButton(LANGUAGE, right, gridY + 32, I18n.format("options.language"), "icon/language", 15));
            this.buttonList.add(new GuiButton(CONTINUE, this.width / 2 - 75, this.height - 27, 150, 20, I18n.format("gui.continue")));
        }

        @Override
        public void updateScreen() {
            this.ticks++;
            // 26.3's handleInitialNarrationDelay: once, about two seconds in, while the window has the focus.
            if (!this.narrated && this.ticks >= 40 && org.lwjgl.opengl.Display.isActive()) {
                this.narrated = true;
                say(I18n.format("accessibility.onboarding.screen.narrator"));
            }
        }

        @Override
        protected void actionPerformed(GuiButton button) throws IOException {
            if (!button.enabled) {
                return;
            }
            switch (button.id) {
                case NARRATOR:
                    this.mc.gameSettings.setOptionValue(GameSettings.Options.NARRATOR, 1);
                    button.displayString = this.mc.gameSettings.getKeyBinding(GameSettings.Options.NARRATOR);
                    this.mc.gameSettings.saveOptions();
                    break;
                case SOUNDS:
                    leave(new GuiScreenOptionsSounds(new GuiMainMenu(), this.mc.gameSettings));
                    break;
                case ACCESSIBILITY:
                    leave(OptionsListScreen.accessibility(new GuiMainMenu()));
                    break;
                case LANGUAGE:
                    leave(new GuiLanguage(new GuiMainMenu(), this.mc.gameSettings, this.mc.getLanguageManager()));
                    break;
                case CONTINUE:
                    leave(new GuiMainMenu());
                    break;
                default:
                    break;
            }
        }

        @Override
        protected void keyTyped(char typedChar, int keyCode) throws IOException {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                leave(new GuiMainMenu());
                return;
            }
            if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && !this.buttonList.isEmpty()) {
                // 26.3 focuses the Narrator button first: Enter presses it.
                GuiButton narrator = this.buttonList.get(0);
                if (narrator.enabled) {
                    narrator.playPressSound(this.mc.getSoundHandler());
                    actionPerformed(narrator);
                }
            }
        }

        /** 26.3's {@code close}: the onboarding ends (saved), the narrator stops, the next screen opens. */
        private void leave(GuiScreen next) {
            if (!this.ended) {
                this.ended = true;
                finished();
                clearNarrator();
            }
            this.mc.displayGuiScreen(next);
        }

        @Override
        public void onGuiClosed() {
            if (!this.ended) {
                this.ended = true;
                finished();
            }
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawLogo();
            Gui.drawRect(this.textX + 1, this.textY + 1, this.textX + this.textW - 1, this.textY + this.textH - 1, 0xFF000000);
            drawHorizontalLine(this.textX, this.textX + this.textW - 1, this.textY, OUTLINE);
            drawHorizontalLine(this.textX, this.textX + this.textW - 1, this.textY + this.textH - 1, OUTLINE);
            drawVerticalLine(this.textX, this.textY, this.textY + this.textH - 1, OUTLINE);
            drawVerticalLine(this.textX + this.textW - 1, this.textY, this.textY + this.textH - 1, OUTLINE);
            int y = this.textY + 4;
            for (String l : this.lines) {
                // MultiLineTextWidget centers each line.
                drawCenteredString(this.fontRenderer, l, this.width / 2, y, 0xFFFFFF);
                y += this.fontRenderer.FONT_HEIGHT;
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        /** The logo as the title screen draws it at this look (26.3's {@code LogoRenderer} at y 30). */
        private void drawLogo() {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            this.mc.getTextureManager().bindTexture(new ResourceLocation(TitleLogo.LOGO));
            if (TitleLogo.layout() == VanillaRevisions.TitleLogo.LOGO_1_20) {
                Gui.drawModalRectWithCustomSizedTexture(this.width / 2 - 128, 30, 0.0F, 0.0F, 256, 44, 256.0F, 64.0F);
                this.mc.getTextureManager().bindTexture(new ResourceLocation(TitleLogo.EDITION));
                Gui.drawModalRectWithCustomSizedTexture(this.width / 2 - 64, 30 + 37, 0.0F, 0.0F, 128, 14, 128.0F, 16.0F);
            } else {
                int x = this.width / 2 - 137;
                drawTexturedModalRect(x, 30, 0, 0, 155, 44);
                drawTexturedModalRect(x + 155, 30, 0, 45, 155, 44);
                this.mc.getTextureManager().bindTexture(new ResourceLocation(TitleLogo.EDITION));
                Gui.drawModalRectWithCustomSizedTexture(x + 88, 30 + 37, 0.0F, 0.0F, 98, 14, 128.0F, 16.0F);
            }
            GlStateManager.disableBlend();
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }

    /**
     * 26.3's {@code SpriteIconButton.TextAndIcon}, 150 wide: the text centered between the left edge plus 2 and the icon
     * less 4, the icon (a sprite, square) 2 from the right edge, centered up and down. Without the sprite (no backport
     * has it) the text is centered on the whole button, as a plain button's.
     */
    static final class IconButton extends GuiButton {
        private final ResourceLocation sprite;
        private final int size;
        private Boolean present;

        IconButton(int id, int x, int y, String text, String sprite, int size) {
            super(id, x, y, 150, 20, text);
            this.sprite = new ResourceLocation("textures/gui/sprites/" + sprite + ".png");
            this.size = size;
        }

        private boolean hasSprite(Minecraft mc) {
            if (this.present == null) {
                try {
                    mc.getResourceManager().getResource(this.sprite).close();
                    this.present = true;
                } catch (IOException e) {
                    this.present = false;
                }
            }
            return this.present;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible || !hasSprite(mc)) {
                super.drawButton(mc, mouseX, mouseY, partialTicks);
                return;
            }
            String text = this.displayString;
            this.displayString = "";
            super.drawButton(mc, mouseX, mouseY, partialTicks);
            this.displayString = text;
            int color = !this.enabled ? 0xA0A0A0 : this.hovered ? 0xFFFFA0 : 0xE0E0E0;
            int textLeft = this.x + 2;
            int textRight = this.x + this.width - this.size - 4;
            drawCenteredString(mc.fontRenderer, text, (textLeft + textRight) / 2, this.y + (this.height - 8) / 2,
                    VanillaRevisions.buttonTextColor(color));
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();
            mc.getTextureManager().bindTexture(this.sprite);
            Gui.drawModalRectWithCustomSizedTexture(this.x + this.width - this.size - 2, this.y + this.height / 2 - this.size / 2, 0.0F, 0.0F,
                    this.size, this.size, this.size, this.size);
            GlStateManager.disableBlend();
        }
    }

    private static Object narrator() throws ReflectiveOperationException {
        return ReflectionHelper.findField(NarratorChatListener.class, "narrator", "field_192580_a").get(NarratorChatListener.INSTANCE);
    }

    /** Says a line through the game's narrator (1.12.2's text2speech), whatever the Narrator setting, as 26.3 does here. */
    static void say(String text) {
        if (!NarratorChatListener.INSTANCE.isActive()) {
            return;
        }
        try {
            ((com.mojang.text2speech.Narrator) narrator()).say(text);
        } catch (ReflectiveOperationException | RuntimeException e) {
            BackportCore.LOGGER.debug("Presentation: could not narrate the onboarding", e);
        }
    }

    static void clearNarrator() {
        try {
            ((com.mojang.text2speech.Narrator) narrator()).clear();
        } catch (ReflectiveOperationException | RuntimeException e) {
            BackportCore.LOGGER.debug("Presentation: could not clear the narrator", e);
        }
    }
}

package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.PresentationOption;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionButton;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.chat.NarratorChatListener;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * One of the options screens later versions added, built by Core in 1.14's layout ({@code AccessibilityScreen},
 * {@code MouseSettingsScreen}): the title at y 20, two columns of 150-wide buttons from {@code height / 6}
 * every 24 pixels, Done below at {@code height / 6 + 144} (lower when there are more than six rows). It holds
 * vanilla's own settings that the version moved there, then the backports' options, each after the entry
 * it names ({@link PresentationOption#after}) or at the end.
 */
@SideOnly(Side.CLIENT)
public class OptionsListScreen extends GuiScreen {

    /** 1.14.4's Accessibility Settings, as far as 1.12.2 has the settings: narrator, subtitles, chat opacity, Auto-Jump. */
    public static final GameSettings.Options[] ACCESSIBILITY = {
            GameSettings.Options.NARRATOR, GameSettings.Options.SHOW_SUBTITLES, GameSettings.Options.CHAT_OPACITY, GameSettings.Options.AUTO_JUMP};
    /** 1.14.4's Mouse Settings, as far as 1.12.2 has them: sensitivity, invert, touchscreen. */
    public static final GameSettings.Options[] MOUSE = {
            GameSettings.Options.SENSITIVITY, GameSettings.Options.INVERT_MOUSE, GameSettings.Options.TOUCHSCREEN};

    private final GuiScreen parent;
    private final String titleKey;
    private final OptionScreen screen;
    private final GameSettings.Options[] vanilla;
    private String title = "";

    public OptionsListScreen(GuiScreen parent, String titleKey, OptionScreen screen, GameSettings.Options[] vanilla) {
        this.parent = parent;
        this.titleKey = titleKey;
        this.screen = screen;
        this.vanilla = vanilla.clone();
    }

    /** @return Core's Accessibility Settings screen */
    public static OptionsListScreen accessibility(GuiScreen parent) {
        return new OptionsListScreen(parent, "options.accessibility.title", OptionScreen.ACCESSIBILITY, ACCESSIBILITY);
    }

    /** @return Core's Mouse Settings screen */
    public static OptionsListScreen mouse(GuiScreen parent) {
        return new OptionsListScreen(parent, "options.mouse_settings.title", OptionScreen.MOUSE, MOUSE);
    }

    public OptionScreen screen() {
        return this.screen;
    }

    @Override
    public void initGui() {
        this.title = I18n.format(this.titleKey);
        this.buttonList.clear();
        List<GuiButton> entries = new ArrayList<GuiButton>();
        List<String> keys = new ArrayList<String>();
        for (GameSettings.Options o : this.vanilla) {
            entries.add(vanillaButton(o));
            keys.add(o.name());
        }
        OptionsInjector.insertAfterAnchors(entries, keys, Presentation.optionsOn(this.screen));
        int i = 0;
        int lastY = this.height / 6;
        for (GuiButton b : entries) {
            b.x = this.width / 2 - 155 + i % 2 * 160;
            b.y = this.height / 6 + 24 * (i >> 1);
            lastY = b.y;
            this.buttonList.add(b);
            i++;
        }
        int doneY = Math.max(this.height / 6 + 144, lastY + 24);
        this.buttonList.add(new GuiButton(200, this.width / 2 - 100, doneY, I18n.format("gui.done")));
    }

    private GuiButton vanillaButton(GameSettings.Options o) {
        if (o.isFloat()) {
            return new GuiOptionSlider(o.getOrdinal(), 0, 0, o);
        }
        GuiOptionButton b = new GuiOptionButton(o.getOrdinal(), 0, 0, o, this.mc.gameSettings.getKeyBinding(o));
        if (o == GameSettings.Options.NARRATOR) {
            // As 1.12.2's chat settings: the narrator button only works where the system has a narrator.
            b.enabled = NarratorChatListener.INSTANCE.isActive();
        }
        return b;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (!button.enabled) {
            return;
        }
        if (button.id == 200) {
            this.mc.gameSettings.saveOptions();
            this.mc.displayGuiScreen(this.parent);
        } else if (button instanceof GuiOptionButton) {
            GameSettings.Options o = ((GuiOptionButton) button).getOption();
            this.mc.gameSettings.setOptionValue(o, 1);
            button.displayString = this.mc.gameSettings.getKeyBinding(GameSettings.Options.byOrdinal(button.id));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) {
            this.mc.gameSettings.saveOptions();
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(this.fontRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
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

import javax.annotation.Nullable;
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
    /** The scrolling list, when the buttons do not fit on the screen (null: they do). */
    @Nullable
    private OptionRowList list;
    private final List<GuiButton> rowButtons = new ArrayList<GuiButton>();

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

    /** 1.18's Controls (after Mouse Settings... and Key Binds...): Auto-Jump, with the toggles before it. */
    public static final GameSettings.Options[] CONTROLS = {GameSettings.Options.AUTO_JUMP};

    /**
     * 1.21.11's order of Controls' options ({@code ControlsScreen.options}) by name: vanilla's settings and the
     * options named after them, whichever mod registers them; others follow.
     */
    static final String[] CONTROLS_ORDER = {"toggle_crouch", "toggle_sprint", "toggle_attack", "toggle_use", "AUTO_JUMP",
            "sprint_window", "operator_items_tab"};

    /** @return Core's Controls screen (1.18 and later; before, Options opens 1.12.2's own) */
    public static OptionsListScreen controls(GuiScreen parent) {
        return new OptionsListScreen(parent, "controls.title", OptionScreen.CONTROLS, CONTROLS);
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
        GameVersion newest = Presentation.newest();
        for (GameSettings.Options o : this.vanilla) {
            if (o == GameSettings.Options.AUTO_JUMP && this.screen == OptionScreen.ACCESSIBILITY && VanillaRevisions.controlsLeaveAccessibility(newest)) {
                continue;
            }
            entries.add(vanillaButton(o));
            keys.add(o.name());
        }
        if (this.screen == OptionScreen.ACCESSIBILITY && VanillaRevisions.videoSections(newest)) {
            // 1.21.11: View Bobbing left Video Settings for Accessibility.
            entries.add(vanillaButton(GameSettings.Options.VIEW_BOBBING));
            keys.add(GameSettings.Options.VIEW_BOBBING.name());
        }
        OptionsInjector.insertAfterAnchors(entries, keys, Presentation.optionsOn(this.screen));
        if (this.screen == OptionScreen.CONTROLS) {
            order(entries, keys, CONTROLS_ORDER);
            // 1.18's ControlsScreen: Mouse Settings... and Key Binds... first.
            entries.add(0, new OptionWidgets.Action(0, 0, 150, I18n.format("options.mouse_settings"),
                    () -> this.mc.displayGuiScreen(mouse(this))));
            entries.add(1, new OptionWidgets.Action(0, 0, 150, I18n.format("controls.keybinds"),
                    () -> this.mc.displayGuiScreen(new net.minecraft.client.gui.GuiControls(this, this.mc.gameSettings))));
        } else if (this.screen == OptionScreen.ACCESSIBILITY && VanillaRevisions.controlsLeaveAccessibility(newest)) {
            // 1.21.9: "Controls..." beside Narrator.
            int at = keys.indexOf(GameSettings.Options.NARRATOR.name());
            entries.add(at + 1, new OptionWidgets.Action(0, 0, 150, I18n.format("options.controls"),
                    () -> this.mc.displayGuiScreen(controls(this))));
        }
        boolean footer = this.screen == OptionScreen.ACCESSIBILITY && Presentation.isAtLeast("1.16.4");
        int gridBottom = this.height / 6 + 24 * ((entries.size() + 1) / 2 - 1) + 20;
        this.list = null;
        this.rowButtons.clear();
        if (gridBottom > (footer ? this.height - 31 : this.height - 28)) {
            scrolling(entries, footer);
            return;
        }
        int i = 0;
        int lastY = this.height / 6;
        for (GuiButton b : entries) {
            b.x = this.width / 2 - 155 + i % 2 * 160;
            b.y = this.height / 6 + 24 * (i >> 1);
            lastY = b.y;
            this.buttonList.add(b);
            i++;
        }
        if (footer) {
            addFooter();
            return;
        }
        int doneY = Math.max(this.height / 6 + 144, lastY + 24);
        this.buttonList.add(new GuiButton(200, this.width / 2 - 100, doneY, I18n.format("gui.done")));
    }

    /** 1.16.4's AccessibilityOptionsScreen.createFooter: the guide and Done side by side at the bottom. */
    private void addFooter() {
        this.buttonList.add(new OptionWidgets.Action(this.width / 2 - 155, this.height - 27, 150, I18n.format("options.accessibility.link"),
                () -> MenuScreens.confirmLink(this, GUIDE_URL)));
        this.buttonList.add(new GuiButton(200, this.width / 2 + 5, this.height - 27, 150, 20, I18n.format("gui.done")));
    }

    /**
     * More buttons than the screen holds (the whole family on a small window): they scroll, as every options screen
     * does from 1.16 ({@code OptionsList} from y 32 to 32 above the bottom, rows 25 tall), Done or the footer below.
     */
    private void scrolling(List<GuiButton> entries, boolean footer) {
        List<net.minecraft.client.gui.GuiOptionsRowList.Row> rows = new ArrayList<net.minecraft.client.gui.GuiOptionsRowList.Row>();
        int left = this.width / 2 - 155;
        for (int i = 0; i < entries.size(); i += 2) {
            GuiButton a = entries.get(i);
            GuiButton b = i + 1 < entries.size() ? entries.get(i + 1) : null;
            a.x = left;
            if (b != null) {
                b.x = left + 160;
            }
            rows.add(new OptionRowList.ButtonRow(a, b));
        }
        this.rowButtons.addAll(entries);
        this.list = new OptionRowList(this.mc, this.width, this.height, 32, this.height - 32, 25, rows);
        if (footer) {
            addFooter();
        } else {
            this.buttonList.add(new GuiButton(200, this.width / 2 - 100, this.height - 27, I18n.format("gui.done")));
        }
    }

    /** @return the buttons in the scrolling list, or null when the screen does not scroll */
    @Nullable
    public List<GuiButton> rowButtons() {
        return this.list == null ? null : this.rowButtons;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (this.list != null) {
            this.list.handleMouseInput();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.list != null && this.mc.currentScreen == this) {
            this.list.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (this.list != null) {
            this.list.mouseReleased(mouseX, mouseY, state);
        }
    }

    /**
     * Puts the entries named in {@code order} (a vanilla name, or an option id's path) first, in that order; the rest
     * keep theirs after them. {@code entries} and {@code keys} run in step.
     */
    static void order(List<GuiButton> entries, List<String> keys, String[] order) {
        List<GuiButton> e = new ArrayList<GuiButton>();
        List<String> k = new ArrayList<String>();
        for (String name : order) {
            for (int i = 0; i < keys.size(); i++) {
                String key = keys.get(i);
                if (key.equals(name) || key.indexOf(':') > 0 && key.substring(key.indexOf(':') + 1).equals(name)) {
                    e.add(entries.remove(i));
                    k.add(keys.remove(i));
                    break;
                }
            }
        }
        e.addAll(entries);
        k.addAll(keys);
        entries.clear();
        keys.clear();
        entries.addAll(e);
        keys.addAll(k);
    }

    /** 1.16.4's Accessibility Guide. */
    static final String GUIDE_URL = "https://aka.ms/MinecraftJavaAccessibility";


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
        if (this.list != null) {
            this.list.drawScreen(mouseX, mouseY, partialTicks);
        }
        drawCenteredString(this.fontRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

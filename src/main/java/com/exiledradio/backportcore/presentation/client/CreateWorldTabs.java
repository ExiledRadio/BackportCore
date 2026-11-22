package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.gamerules.NewWorldDifficulty;
import com.exiledradio.backportcore.gamerules.client.GameRulesClient;
import com.exiledradio.backportcore.gamerules.client.GameRulesScreen;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiLabel;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 1.19.4's tabbed Create New World screen (23w05a; {@link VanillaRevisions#createWorldTabs}) over 1.12.2's
 * {@link GuiCreateWorld}, laid out and drawn as 26.3's {@code CreateWorldScreen}: Game | World | More along the top,
 * Create New World and Cancel along the bottom.
 *
 * <p>1.12.2's screen stays the screen. Its own buttons, text boxes and fields hold every setting, its own
 * {@code actionPerformed} changes them and launches the world, and other mods' buttons, events and reflection see
 * the {@code GuiCreateWorld} they expect. Core only moves its controls onto the tabs each frame, draws the screen in
 * place of {@code drawScreen} (whose 1.12.2 labels and pages are gone), and adds what 1.12.2 lacks: the tabs, the
 * Difficulty button (1.12.2 takes a new world's difficulty from the Difficulty option, so that is where it starts;
 * {@link NewWorldDifficulty}) and the More tab's Game Rules button (Core's 26.1 Game Rules screen). The Game tab is
 * 1.12.2's main page (its text box is the name box), the World and More tabs its More World Options page (the seed
 * box). Buttons other mods add are on the More tab, under Game Rules. Data Packs and Experiments have no 1.12.2
 * counterpart and are left out. Since Core 1.4.5.
 */
@SideOnly(Side.CLIENT)
public final class CreateWorldTabs {

    public static final int GAME = 0;
    public static final int WORLD = 1;
    public static final int MORE = 2;

    private static final String[] TAB_KEYS = {"createWorld.tab.game.title", "createWorld.tab.world.title", "createWorld.tab.more.title"};

    /** 26.3's {@code GridLayoutTab}: a tab's content sits a sixth of the way down the free height. */
    private static final float TAB_ALIGN_Y = 0.16666667F;

    private static final ResourceLocation OPTIONS_BACKGROUND = Gui.OPTIONS_BACKGROUND;

    /** Each open Create World screen's tab and Difficulty (kept while Customize or Game Rules is open over it). */
    private static final Map<GuiScreen, State> STATES = Collections.synchronizedMap(new WeakHashMap<GuiScreen, State>());

    /** For the self-test: frames drawn, and the last tooltip shown. */
    public static volatile int frames;
    @Nullable
    public static volatile String lastTooltip;

    private static boolean broken;

    // 1.12.2's GuiCreateWorld, by MCP and SRG name.
    private static Field nameField;
    private static Field seedField;
    private static Field saveDirName;
    private static Field gameMode;
    private static Field hardcore;
    private static Field allowCheats;
    private static Field selectedIndex;
    private static Field moreDisplay;
    private static Field parent;
    private static Field btnGameMode;
    private static Field btnMoreOptions;
    private static Field btnMapFeatures;
    private static Field btnBonusItems;
    private static Field btnMapType;
    private static Field btnAllowCommands;
    private static Field btnCustomizeType;
    private static Field buttonList;
    private static Field labelList;
    private static Method showMoreWorldOptions;

    /** One screen's state. */
    static final class State {
        int tab = GAME;
        EnumDifficulty difficulty;
        final List<TabButton> tabs = new ArrayList<TabButton>();
        DifficultyButton difficultyButton;
        @Nullable
        GuiButton gameRules;
        /** Other mods' buttons Core hid off the More tab, with the visibility they had. */
        final Map<GuiButton, Boolean> hidden = new IdentityHashMap<GuiButton, Boolean>();
        // Where this frame's layout put the labels and boxes.
        int nameLabelX;
        int nameLabelY;
        int nameBoxX;
        int nameBoxY;
        int seedX;
        int seedY;
        int switchY;
    }

    /** @return whether this screen is drawn as the tabbed screen */
    public static boolean applies(@Nullable GuiScreen gui) {
        return gui != null && gui.getClass() == GuiCreateWorld.class && VanillaRevisions.createWorldTabs() && reflect();
    }

    /** For the self-test: the tab this screen shows, or -1. */
    public static int tab(GuiScreen gui) {
        State st = STATES.get(gui);
        return st == null ? -1 : st.tab;
    }

    /** For the self-test: the Difficulty this screen shows, or null. */
    @Nullable
    public static EnumDifficulty difficulty(GuiScreen gui) {
        State st = STATES.get(gui);
        return st == null ? null : st.difficulty;
    }

    /** For the self-test: this screen's Difficulty button, or null. */
    @Nullable
    public static GuiButton difficultyButton(GuiScreen gui) {
        State st = STATES.get(gui);
        return st == null ? null : st.difficultyButton;
    }

    /** For the self-test: this screen's tab buttons. */
    public static List<GuiButton> tabButtons(GuiScreen gui) {
        State st = STATES.get(gui);
        return st == null ? Collections.<GuiButton>emptyList() : new ArrayList<GuiButton>(st.tabs);
    }

    /** For the self-test: the More tab's Game Rules button, or null. */
    @Nullable
    public static GuiButton gameRulesButton(GuiScreen gui) {
        State st = STATES.get(gui);
        return st == null ? null : st.gameRules;
    }

    /** Selects a tab of this screen, as clicking it would. */
    public static void select(GuiScreen gui, int tab) {
        State st = STATES.get(gui);
        if (st != null && applies(gui)) {
            select((GuiCreateWorld) gui, st, tab);
        }
    }

    // ---- Events

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!applies(event.getGui())) {
            return;
        }
        GuiCreateWorld gui = (GuiCreateWorld) event.getGui();
        State st = STATES.get(gui);
        if (st == null) {
            st = new State();
            st.difficulty = Minecraft.getMinecraft().gameSettings.difficulty;
            st.tab = bool(moreDisplay, gui) ? WORLD : GAME;
            STATES.put(gui, st);
        }
        st.hidden.clear();
        st.tabs.clear();
        for (int i = 0; i < TAB_KEYS.length; i++) {
            st.tabs.add(new TabButton(i, st));
        }
        st.difficultyButton = new DifficultyButton(st);
        List<GuiButton> list = event.getButtonList();
        list.addAll(st.tabs);
        list.add(st.difficultyButton);
        st.gameRules = null;
        if (GameRulesScreen.available()) {
            st.gameRules = new OptionWidgets.Action(0, 0, 210, I18n.format("selectWorld.gameRules"), () -> GameRulesClient.open(gui));
            list.add(st.gameRules);
        }
        layout(gui, st);
    }

    /** Core draws the screen; 1.12.2's {@code drawScreen} (its labels and pages) does not run. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        GuiScreen gui = event.getGui();
        State st = STATES.get(gui);
        if (st == null || !applies(gui)) {
            return;
        }
        event.setCanceled(true);
        try {
            layout((GuiCreateWorld) gui, st);
            draw((GuiCreateWorld) gui, st, event.getMouseX(), event.getMouseY(), event.getRenderPartialTicks());
            frames++;
        } catch (Exception e) {
            BackportCore.LOGGER.error("Create World: the tabbed screen failed; 1.12.2's screen is back", e);
            broken = true;
            STATES.remove(gui);
            Minecraft.getMinecraft().displayGuiScreen(gui);
        }
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        GuiScreen gui = event.getGui();
        State st = STATES.get(gui);
        if (st == null || !applies(gui)) {
            return;
        }
        GuiButton b = event.getButton();
        if (b instanceof TabButton) {
            select((GuiCreateWorld) gui, st, ((TabButton) b).index);
        } else if (b == st.difficultyButton) {
            st.difficulty = cycle(st.difficulty, GuiScreen.isShiftKeyDown() ? -1 : 1);
        } else if (b.id == 0 && b == buttons(gui).get(0) && b.enabled) {
            // Create: the chosen Difficulty waits for the new world (1.12.2 would give it the Difficulty option's).
            String folder = (String) get(saveDirName, gui);
            if (folder != null && st.difficulty != Minecraft.getMinecraft().gameSettings.difficulty) {
                NewWorldDifficulty.expect(folder, st.difficulty);
            } else {
                NewWorldDifficulty.clear();
            }
        }
    }

    /** After a click: 1.12.2's own action may have shown or hidden its buttons, or changed what is active. */
    @SubscribeEvent
    public void onActionDone(GuiScreenEvent.ActionPerformedEvent.Post event) {
        GuiScreen gui = event.getGui();
        State st = STATES.get(gui);
        if (st != null && applies(gui)) {
            layout((GuiCreateWorld) gui, st);
        }
    }

    /** 26.3's keys: Escape goes back; Ctrl + Tab, Ctrl + Shift + Tab and Ctrl + 1 to 3 change the tab. */
    @SubscribeEvent
    public void onKey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        GuiScreen gui = event.getGui();
        State st = STATES.get(gui);
        if (st == null || !applies(gui) || !Keyboard.getEventKeyState()) {
            return;
        }
        int key = Keyboard.getEventKey();
        if (key == Keyboard.KEY_ESCAPE) {
            event.setCanceled(true);
            Minecraft.getMinecraft().displayGuiScreen((GuiScreen) get(parent, gui));
        } else if (GuiScreen.isCtrlKeyDown() && key == Keyboard.KEY_TAB) {
            event.setCanceled(true);
            select((GuiCreateWorld) gui, st, Math.floorMod(st.tab + (GuiScreen.isShiftKeyDown() ? -1 : 1), TAB_KEYS.length));
        } else if (GuiScreen.isCtrlKeyDown() && key >= Keyboard.KEY_1 && key < Keyboard.KEY_1 + TAB_KEYS.length) {
            event.setCanceled(true);
            select((GuiCreateWorld) gui, st, key - Keyboard.KEY_1);
        }
    }

    // ---- Tabs and layout

    private static void select(GuiCreateWorld gui, State st, int tab) {
        st.tab = tab;
        // 1.12.2's main page is the Game tab; its More World Options page (the seed box's) the World and More tabs.
        boolean more = tab != GAME;
        if (bool(moreDisplay, gui) != more) {
            try {
                showMoreWorldOptions.invoke(gui, more);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        ((GuiTextField) get(nameField, gui)).setFocused(false);
        ((GuiTextField) get(seedField, gui)).setFocused(false);
        layout(gui, st);
    }

    /** Puts every control where 26.3 has it on this tab, and hides the rest. */
    static void layout(GuiCreateWorld gui, State st) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        int w = gui.width;
        int h = gui.height;
        boolean modern = MenuBackground.active();
        List<GuiButton> list = buttons(gui);
        GuiButton create = list.get(0);
        GuiButton cancel = list.get(1);
        GuiButton mode = button(btnGameMode, gui);
        GuiButton features = button(btnMapFeatures, gui);
        GuiButton bonus = button(btnBonusItems, gui);
        GuiButton type = button(btnMapType, gui);
        GuiButton cheats = button(btnAllowCommands, gui);
        GuiButton customize = button(btnCustomizeType, gui);
        GuiButton moreOptions = button(btnMoreOptions, gui);

        // The footer: 1.20.5's HeaderAndFooterLayout (33 high, 8 apart); before it 36 high, 10 apart.
        int footer = modern ? 33 : 36;
        int gap = modern ? 8 : 10;
        int fx = (int) (0.5F * (w - 300 - gap));
        int fy = h - footer + (int) (0.5F * (footer - 20));
        place(create, fx, fy, 150, true);
        place(cancel, fx + 150 + gap, fy, 150, true);
        place(moreOptions, 0, 0, moreOptions.width, false);

        // The tab bar: up to 400 wide less 28, tabs of an even width.
        int tabsWidth = Math.min(400, w) - 28;
        int tabWidth = even(tabsWidth / TAB_KEYS.length);
        int tabsX = even((w - tabsWidth) / 2);
        for (TabButton t : st.tabs) {
            t.x = tabsX + t.index * tabWidth;
            t.y = 0;
            t.width = tabWidth;
            t.setHeight(24);
            t.visible = true;
        }
        int top = 24;
        int bottom = modern ? h - footer : fy;
        int area = bottom - top;
        boolean debug = worldType(gui) == WorldType.DEBUG_ALL_BLOCK_STATES;
        boolean isHardcore = bool(hardcore, gui);

        // Game: World Name, Game Mode, Difficulty, Allow Commands; one column 210 wide.
        boolean game = st.tab == GAME;
        int gx = (int) (0.5F * (w - 210));
        int gy = top + (int) (TAB_ALIGN_Y * (area - 117));
        int labelled = Math.max(font.getStringWidth(I18n.format("selectWorld.enterName")), 208);
        st.nameLabelX = gx + (int) (0.5F * (210 - labelled));
        st.nameLabelY = gy;
        st.nameBoxX = st.nameLabelX;
        st.nameBoxY = gy + 13;
        box((GuiTextField) get(nameField, gui), game, st.nameBoxX, st.nameBoxY, 208);
        place(mode, gx, gy + 41, 210, game);
        mode.enabled = !debug;
        place(st.difficultyButton, gx, gy + 69, 210, game);
        st.difficultyButton.enabled = !isHardcore;
        place(cheats, gx, gy + 97, 210, game);
        cheats.enabled = !isHardcore && !debug;

        // World: World Type and Customize, the seed, then the switches; two columns of 150, 10 apart.
        boolean world = st.tab == WORLD;
        int wx = (int) (0.5F * (w - 310));
        int wy = top + (int) (TAB_ALIGN_Y * (area - 117));
        place(type, wx, wy, 150, world);
        place(customize, wx + 160, wy, 150, world);
        customize.enabled = !debug && worldType(gui).isCustomizable();
        st.seedX = wx;
        st.seedY = wy + 28;
        box((GuiTextField) get(seedField, gui), world, wx, wy + 41, 308);
        // SwitchGrid: a spacer row, then each switch 4 below the last, 44 wide at the right.
        st.switchY = wy + 69 + 4;
        place(features, wx + 310 - 44, st.switchY, 44, world);
        features.enabled = !debug && worldType(gui) != WorldType.CUSTOMIZED;
        place(bonus, wx + 310 - 44, st.switchY + 24, 44, world);
        bonus.enabled = !isHardcore && !debug;

        // More: Game Rules, then any other mod's buttons.
        boolean more = st.tab == MORE;
        List<GuiButton> others = new ArrayList<GuiButton>();
        for (GuiButton b : list) {
            if (b != create && b != cancel && b != mode && b != features && b != bonus && b != type && b != cheats && b != customize
                    && b != moreOptions && !(b instanceof TabButton) && b != st.difficultyButton && b != st.gameRules) {
                others.add(b);
            }
        }
        int height = st.gameRules != null ? 20 : -8;
        for (GuiButton b : others) {
            height += 8 + b.height;
        }
        int mx = (int) (0.5F * (w - 210));
        int y = top + (int) (TAB_ALIGN_Y * (area - height));
        if (st.gameRules != null) {
            place(st.gameRules, mx, y, 210, more);
            y += 28;
        }
        for (GuiButton b : others) {
            if (more) {
                Boolean was = st.hidden.remove(b);
                if (was != null) {
                    b.visible = was;
                }
                b.x = mx + (int) (0.5F * (210 - b.width));
                b.y = y;
            } else if (b.visible) {
                st.hidden.put(b, Boolean.TRUE);
                b.visible = false;
            }
            y += 8 + b.height;
        }
    }

    private static void place(GuiButton b, int x, int y, int width, boolean visible) {
        b.x = x;
        b.y = y;
        b.width = width;
        b.visible = visible;
    }

    /** A text box 20 high at x, y: 1.12.2's own border (drawn outside it) is Core's, so the box is exactly there. */
    private static void box(GuiTextField field, boolean shown, int x, int y, int width) {
        field.x = shown ? x : -10000;
        field.y = y;
        field.width = width;
        field.height = 20;
        field.setVisible(shown);
        if (!shown) {
            field.setFocused(false);
        }
    }

    /** {@code Mth.roundToward(value, 2)}: up to an even number. */
    private static int even(int value) {
        return (value + 1) / 2 * 2;
    }

    // ---- Drawing

    private static void draw(GuiCreateWorld gui, State st, int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        int w = gui.width;
        int h = gui.height;
        boolean modern = MenuBackground.active();
        int footer = modern ? 33 : 36;

        // The background, the tab bar's strip and separators.
        if (modern) {
            MenuBackground.drawScreen(gui, 24);
            ResourceLocation header = MenuBackground.texture(mc, "tab_header_background");
            if (header != null) {
                blit(mc, header, 0, 0, 0, 0, w, 24, 16, 16);
            }
            TabButton first = st.tabs.get(0);
            TabButton last = st.tabs.get(st.tabs.size() - 1);
            MenuBackground.separator(mc, MenuBackground.HEADER, 0, 22, first.x, true);
            MenuBackground.separator(mc, MenuBackground.HEADER, last.x + last.width, 22, w - last.x - last.width, true);
        } else {
            lightDirt(mc, w, h);
            Gui.drawRect(0, 0, w, 24, 0xFF000000);
            MenuBackground.separator(mc, MenuBackground.HEADER, 0, 22, w, true);
        }

        // The tab's text.
        int white = 0xFFFFFF;
        if (st.tab == GAME) {
            font.drawStringWithShadow(I18n.format("selectWorld.enterName"), st.nameLabelX, st.nameLabelY, white);
        } else if (st.tab == WORLD) {
            font.drawStringWithShadow(I18n.format("selectWorld.enterSeed"), st.seedX, st.seedY, white);
            font.drawStringWithShadow(label("selectWorld.mapFeatures"), st.seedX, st.switchY + 5, white);
            font.drawStringWithShadow(label("selectWorld.bonusItems"), st.seedX, st.switchY + 24 + 5, white);
        }

        // The buttons, with 26.3's words.
        texts(gui, st);
        for (GuiButton b : buttons(gui)) {
            b.drawButton(mc, mouseX, mouseY, partialTicks);
        }
        for (GuiLabel l : labels(gui)) {
            l.drawLabel(mc, mouseX, mouseY);
        }
        drawBox(font, (GuiTextField) get(nameField, gui), null);
        drawBox(font, (GuiTextField) get(seedField, gui), I18n.format("selectWorld.seedInfo"));

        // The footer separator over everything (26.3 draws it last).
        if (modern) {
            MenuBackground.separator(mc, MenuBackground.FOOTER, 0, h - footer - 2, w, false);
        } else {
            MenuBackground.separator(mc, MenuBackground.FOOTER, 0, even(h - footer - 2), w, false);
        }

        String tooltip = tooltip(gui, st, mouseX, mouseY);
        if (tooltip != null) {
            List<String> lines = new ArrayList<String>();
            for (String part : tooltip.split("\n")) {
                lines.addAll(font.listFormattedStringToWidth(part, 170));
            }
            gui.drawHoveringText(lines, mouseX, mouseY);
            lastTooltip = tooltip;
        }
    }

    /** The buttons' words as 26.3 has them ("Game Mode: Survival"); 1.12.2's own come back with its next update. */
    private static void texts(GuiCreateWorld gui, State st) {
        boolean isHardcore = bool(hardcore, gui);
        button(btnGameMode, gui).displayString = label("selectWorld.gameMode") + ": " + I18n.format("selectWorld.gameMode." + get(gameMode, gui));
        st.difficultyButton.displayString = label("options.difficulty") + ": "
                + I18n.format((isHardcore ? EnumDifficulty.HARD : st.difficulty).getTranslationKey());
        button(btnAllowCommands, gui).displayString = label(VanillaRevisions.allowCommandsWording() ? "selectWorld.allowCommands.new" : "selectWorld.allowCommands")
                + ": " + onOff(bool(allowCheats, gui) && !isHardcore);
        button(btnMapType, gui).displayString = label("selectWorld.mapType") + ": " + I18n.format(worldType(gui).getTranslationKey());
        button(btnCustomizeType, gui).displayString = I18n.format("selectWorld.customizeType");
        button(btnMapFeatures, gui).displayString = onOff(structures(gui));
        button(btnBonusItems, gui).displayString = onOff(bonusChest(gui) && !isHardcore);
        for (TabButton t : st.tabs) {
            t.displayString = I18n.format(TAB_KEYS[t.index]);
        }
    }

    /** 26.3's tooltips: the game mode's, the difficulty's, Allow Commands', the structures', the save folder. */
    @Nullable
    private static String tooltip(GuiCreateWorld gui, State st, int mouseX, int mouseY) {
        GuiTextField name = (GuiTextField) get(nameField, gui);
        if (st.tab == GAME && over(mouseX, mouseY, name.x, name.y, name.width, name.height)) {
            return I18n.format("selectWorld.targetFolder", TextFormatting.ITALIC + String.valueOf(get(saveDirName, gui)) + TextFormatting.RESET);
        }
        GuiButton hovered = null;
        for (GuiButton b : buttons(gui)) {
            if (b.visible && over(mouseX, mouseY, b.x, b.y, b.width, b.height)) {
                hovered = b;
            }
        }
        if (hovered == null) {
            return null;
        }
        boolean isHardcore = bool(hardcore, gui);
        if (hovered == button(btnGameMode, gui)) {
            return I18n.format("selectWorld.gameMode." + get(gameMode, gui) + ".info");
        } else if (hovered == st.difficultyButton) {
            return I18n.format((isHardcore ? EnumDifficulty.HARD : st.difficulty).getTranslationKey() + ".info");
        } else if (hovered == button(btnAllowCommands, gui)) {
            return I18n.format("selectWorld.allowCommands.info");
        } else if (hovered == button(btnMapFeatures, gui)) {
            return I18n.format("selectWorld.mapFeatures.info");
        } else if (hovered == button(btnMapType, gui) && worldType(gui).hasInfoNotice()) {
            return I18n.format(worldType(gui).getInfoTranslationKey());
        }
        return null;
    }

    private static boolean over(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    /**
     * 26.3's text box: a one-pixel border (gray, white while focused) around black, the text 4 in and centered, the
     * hint in dark gray while empty and not focused. 1.12.2's box keeps its size for clicks and scrolling; only its
     * drawing is moved inside the border.
     */
    private static void drawBox(FontRenderer font, GuiTextField field, @Nullable String hint) {
        if (!field.getVisible() || field.x < -1000) {
            return;
        }
        int x = field.x;
        int y = field.y;
        int width = field.width;
        int height = field.height;
        Gui.drawRect(x, y, x + width, y + height, field.isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0);
        Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF000000);
        field.setEnableBackgroundDrawing(false);
        field.x = x + 4;
        field.y = y + (height - 8) / 2;
        field.width = width - 8;
        try {
            field.drawTextBox();
        } finally {
            field.setEnableBackgroundDrawing(true);
            field.x = x;
            field.y = y;
            field.width = width;
        }
        if (hint != null && field.getText().isEmpty() && !field.isFocused()) {
            font.drawStringWithShadow(hint, x + 4, y + (height - 8) / 2, 0x555555);
        }
    }

    /** 1.19.4's {@code light_dirt_background.png}, or 1.12.2's dirt at its brightness (0.3 of the dirt's own). */
    private static void lightDirt(Minecraft mc, int w, int h) {
        ResourceLocation light = MenuBackground.texture(mc, "light_dirt_background");
        if (light != null) {
            blit(mc, light, 0, 0, 0, 0, w, h, 32, 32);
        } else {
            dirt(mc, 0, 0, w, h, 76);
        }
    }

    /** 1.12.2's dirt over a rectangle, two pixels to a texel, at this brightness (of 255). */
    static void dirt(Minecraft mc, int x, int y, int width, int height, int brightness) {
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.enableTexture2D();
        mc.getTextureManager().bindTexture(OPTIONS_BACKGROUND);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        double u0 = x / 32.0;
        double v0 = y / 32.0;
        double u1 = (x + width) / 32.0;
        double v1 = (y + height) / 32.0;
        buffer.pos(x, y + height, 0.0).tex(u0, v1).color(brightness, brightness, brightness, 255).endVertex();
        buffer.pos(x + width, y + height, 0.0).tex(u1, v1).color(brightness, brightness, brightness, 255).endVertex();
        buffer.pos(x + width, y, 0.0).tex(u1, v0).color(brightness, brightness, brightness, 255).endVertex();
        buffer.pos(x, y, 0.0).tex(u0, v0).color(brightness, brightness, brightness, 255).endVertex();
        tessellator.draw();
    }

    /** {@code GuiGraphics.blit(texture, x, y, u, v, w, h, texWidth, texHeight)}: tiled, with blending. */
    static void blit(Minecraft mc, ResourceLocation texture, int x, int y, float u, float v, int width, int height, float texWidth, float texHeight) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(texture);
        Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, width, height, texWidth, texHeight);
        GlStateManager.disableBlend();
    }

    // ---- Values

    /** A label without 1.12.2's trailing colon ("World Type:" is "World Type" from 1.19.4). */
    static String label(String key) {
        String s = I18n.format(key).trim();
        while (s.endsWith(":") || s.endsWith("：")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        return s;
    }

    private static String onOff(boolean on) {
        return I18n.format(on ? "options.on" : "options.off");
    }

    static EnumDifficulty cycle(EnumDifficulty d, int by) {
        EnumDifficulty[] all = EnumDifficulty.values();
        return all[Math.floorMod(d.ordinal() + by, all.length)];
    }

    private static WorldType worldType(GuiCreateWorld gui) {
        WorldType t = WorldType.WORLD_TYPES[((Integer) get(selectedIndex, gui)).intValue()];
        return t == null ? WorldType.DEFAULT : t;
    }

    private static boolean structures(GuiCreateWorld gui) {
        return bool(field("generateStructuresEnabled", "field_146341_s"), gui);
    }

    private static boolean bonusChest(GuiCreateWorld gui) {
        return bool(field("bonusChestEnabled", "field_146338_v"), gui);
    }

    // ---- Reflection

    private static synchronized boolean reflect() {
        if (broken) {
            return false;
        }
        if (nameField != null) {
            return true;
        }
        try {
            nameField = field("worldNameField", "field_146333_g");
            seedField = field("worldSeedField", "field_146335_h");
            saveDirName = field("saveDirName", "field_146336_i");
            gameMode = field("gameMode", "field_146342_r");
            hardcore = field("hardCoreMode", "field_146337_w");
            allowCheats = field("allowCheats", "field_146340_t");
            selectedIndex = field("selectedIndex", "field_146331_K");
            moreDisplay = field("inMoreWorldOptionsDisplay", "field_146344_y");
            parent = field("parentScreen", "field_146332_f");
            btnGameMode = field("btnGameMode", "field_146343_z");
            btnMoreOptions = field("btnMoreOptions", "field_146324_A");
            btnMapFeatures = field("btnMapFeatures", "field_146325_B");
            btnBonusItems = field("btnBonusItems", "field_146326_C");
            btnMapType = field("btnMapType", "field_146320_D");
            btnAllowCommands = field("btnAllowCommands", "field_146321_E");
            btnCustomizeType = field("btnCustomizeType", "field_146322_F");
            buttonList = ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n");
            labelList = ReflectionHelper.findField(GuiScreen.class, "labelList", "field_146293_o");
            showMoreWorldOptions = ReflectionHelper.findMethod(GuiCreateWorld.class, "showMoreWorldOptions", "func_146316_a", boolean.class);
            return true;
        } catch (Exception e) {
            BackportCore.LOGGER.error("Create World: 1.12.2's screen is not as expected; it keeps its own look", e);
            broken = true;
            nameField = null;
            return false;
        }
    }

    private static Field field(String mcp, String srg) {
        return ReflectionHelper.findField(GuiCreateWorld.class, mcp, srg);
    }

    @Nullable
    private static Object get(Field f, Object o) {
        try {
            return f.get(o);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean bool(Field f, Object o) {
        return Boolean.TRUE.equals(get(f, o));
    }

    private static GuiButton button(Field f, GuiCreateWorld gui) {
        return (GuiButton) get(f, gui);
    }

    /** The screen's buttons (1.12.2's {@code buttonList}). */
    @SuppressWarnings("unchecked")
    public static List<GuiButton> buttons(GuiScreen gui) {
        return (List<GuiButton>) get(buttonList, gui);
    }

    @SuppressWarnings("unchecked")
    private static List<GuiLabel> labels(GuiScreen gui) {
        return (List<GuiLabel>) get(labelList, gui);
    }

    // ---- Widgets

    /** Difficulty: 26.3's cycle button (Shift goes back), Hard and inactive in Hardcore. */
    static final class DifficultyButton extends GuiButton {
        DifficultyButton(State st) {
            super(OptionWidgets.nextId(), 0, 0, 210, 20, "");
        }
    }

    /**
     * A tab ({@code TabButton}, 26.2's {@code MenuTabBar.MenuTabButton}): 24 high, no click sound. From 1.20.5 the
     * {@code widget/tab*} sprites (nine-sliced, borders 2, 2, 2, 0) with the selected tab's inside the menu background
     * and its label underlined; before, 1.19.4's dirt {@code tab_button.png}.
     */
    static final class TabButton extends GuiButton {
        final int index;
        private final State state;

        TabButton(int index, State state) {
            super(OptionWidgets.nextId(), 0, 0, 100, 24, I18n.format(TAB_KEYS[index]));
            this.index = index;
            this.state = state;
        }

        void setHeight(int height) {
            this.height = height;
        }

        boolean selected() {
            return this.state.tab == this.index;
        }

        @Override
        public void playPressSound(net.minecraft.client.audio.SoundHandler soundHandler) {
            // 26.3's TabButton.playDownSound does nothing.
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }
            this.hovered = over(mouseX, mouseY, this.x, this.y, this.width, this.height);
            boolean selected = selected();
            int color = this.enabled ? 0xFFFFFFFF : 0xFFA0A0A0;
            FontRenderer font = mc.fontRenderer;
            if (MenuBackground.active()) {
                TabSprites.modern(mc, this.x, this.y, this.width, this.height, selected, this.hovered);
                boolean labelLast = VanillaRevisions.tabLabelOverBackground();
                if (!labelLast) {
                    label(font, selected, color);
                }
                if (selected) {
                    MenuBackground.tile(MenuBackground.MENU, MenuBackground.MENU_COLOR, this.x + 2, this.y + 2, this.width - 4, this.height - 2, 0, 0);
                    underline(font, color);
                }
                if (labelLast) {
                    label(font, selected, color);
                }
            } else {
                TabSprites.dirt(mc, this.x, this.y, this.width, this.height, selected, this.hovered);
                label(font, selected, color);
                if (selected) {
                    underline(font, color);
                }
            }
        }

        /** {@code renderScrollingString} between x + 1 and the right less 1, 3 lower when not selected. */
        private void label(FontRenderer font, boolean selected, int color) {
            int top = this.y + (selected ? 0 : 3);
            int bottom = this.y + this.height;
            int textY = (top + bottom - 9) / 2 + 1;
            String text = this.displayString;
            int room = this.width - 2;
            if (font.getStringWidth(text) > room) {
                text = font.trimStringToWidth(text, room);
            }
            int center = (this.x + 1 + this.x + this.width - 1) / 2;
            font.drawStringWithShadow(text, center - font.getStringWidth(text) / 2, textY, color);
        }

        /** One pixel under the label, two above the tab's bottom, as wide as the label (at most the tab less 4). */
        private void underline(FontRenderer font, int color) {
            int width = Math.min(font.getStringWidth(this.displayString), this.width - 4);
            int left = this.x + (this.width - width) / 2;
            int top = this.y + this.height - 2;
            Gui.drawRect(left, top, left + width, top + 1, color);
        }
    }
}

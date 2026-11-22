package com.exiledradio.backportcore.gamerules.client;

import com.exiledradio.backportcore.gamerules.GameRuleTable;
import com.exiledradio.backportcore.gamerules.GameRulesNetwork;
import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameRules;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Game Rules screen, which 1.12.2 never had, as the newest installed version draws it:
 * <ul>
 *   <li>1.16 ({@code EditGameRulesScreen}, 20w20a's Create World button): "Edit Game Rules" at y 20, the rules in a
 *       list from y 43 to 32 above the bottom, under their categories (bold, yellow), Done and Cancel at the bottom.
 *       A row is the rule's name (wrapped at 170, two lines at most) with an ON/OFF button or a 44-wide number box at
 *       its right; its tooltip is the rule's id in yellow, its description and "Default: x" in gray.</li>
 *   <li>1.20.5 ({@code HeaderAndFooterLayout}): a 33-high header and footer around the list.</li>
 *   <li>26.1 ({@code AbstractGameRulesScreen}): the header is 36 high and holds a search box under the title
 *       ("Search...", matching a rule's id, name, category or description); categories are also underlined.</li>
 *   <li>26.3 ({@code InWorldGameRulesScreen}, from World Options): the server's own rules, fetched ("Retrieving game
 *       rules...") and changed with Done; leaving with changes asks first.</li>
 * </ul>
 * Rules are 1.12.2's and the backports' (see {@link GameRuleTable}), under 26.3's names and categories, in 26.3's
 * order. On Create World the list is 1.12.2's rules and those of the installed backports, at their defaults.
 * Since Core 1.4.4.
 */
@SideOnly(Side.CLIENT)
public final class GameRulesScreen extends GuiScreen {

    /** 26.x: {@code AbstractGameRulesScreen.RuleList.ITEM_HEIGHT}. */
    static final int ROW = 24;
    private static final int DONE = 0;
    private static final int CANCEL = 1;

    /** For the client self-test: the screens that sent changes. */
    public static volatile int sent;

    private final GuiScreen parent;
    private final boolean inWorld;
    @Nullable
    private final Consumer<Map<String, String>> onDone;
    /** 1.12.2 names to stored values, as the screen opened. */
    private final Map<String, String> initial = new LinkedHashMap<String, String>();
    /** 1.12.2 names to stored values, as edited. */
    final Map<String, String> values = new LinkedHashMap<String, String>();
    private final List<GameRuleTable.Rule> rules = new ArrayList<GameRuleTable.Rule>();
    private final Set<String> invalid = new HashSet<String>();
    private boolean received;
    private final long opened = System.currentTimeMillis();

    private RuleList list;
    @Nullable
    private GuiTextField search;
    private String filter = "";
    private GuiButton done;
    private int top;
    private int bottom;
    private float keepScroll;

    private GameRulesScreen(GuiScreen parent, boolean inWorld, @Nullable Consumer<Map<String, String>> onDone) {
        this.parent = parent;
        this.inWorld = inWorld;
        this.onDone = onDone;
    }

    /**
     * Create World's screen: 1.12.2's rules and the installed backports', with {@code chosen} (1.12.2 names to
     * values) over their defaults. {@code onDone} gets every rule's value when Done is pressed.
     */
    public static GameRulesScreen forNewWorld(GuiScreen parent, Map<String, String> chosen, Consumer<Map<String, String>> onDone) {
        GameRulesScreen screen = new GameRulesScreen(parent, false, onDone);
        GameRules vanilla = new GameRules();
        Map<String, String> start = new LinkedHashMap<String, String>();
        for (String id : vanilla.getRules()) {
            if (GameRuleTable.numeric(vanilla, id) != null) {
                GameRuleTable.Rule known = GameRuleTable.known(id);
                start.put(id, known != null ? known.defaultValue : vanilla.getString(id));
            }
        }
        for (GameRuleTable.Rule rule : GameRuleTable.all()) {
            if (rule.owners != null && !start.containsKey(rule.id) && anyLoaded(rule.owners)) {
                start.put(rule.id, rule.defaultValue);
            }
        }
        for (Map.Entry<String, String> e : start.entrySet()) {
            GameRuleTable.Rule rule = GameRuleTable.known(e.getKey());
            if (rule == null) {
                rule = GameRuleTable.unknown(e.getKey(), GameRuleTable.numeric(vanilla, e.getKey()), e.getValue());
            }
            screen.rules.add(rule);
            String value = chosen.containsKey(e.getKey()) ? chosen.get(e.getKey()) : e.getValue();
            screen.initial.put(e.getKey(), value);
            screen.values.put(e.getKey(), value);
        }
        screen.received = true;
        return screen;
    }

    /** 26.3's in-world screen (World Options' Game Rules...): the server's rules, changed there on Done. */
    public static GameRulesScreen inWorld(GuiScreen parent) {
        return new GameRulesScreen(parent, true, null);
    }

    /**
     * @return whether this game shows the Game Rules screen (1.16's look or newer) and, in a world, whether the
     *     server can be asked (it runs Core)
     */
    public static boolean available() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.16");
    }

    /** @return whether the player may change the server's game rules (26.3's {@code COMMANDS_GAMEMASTER}: level 2) */
    public static boolean permitted(Minecraft mc) {
        return mc.player != null && mc.player.getPermissionLevel() >= 2;
    }

    private static boolean anyLoaded(String[] mods) {
        for (String mod : mods) {
            if (Loader.isModLoaded(mod)) {
                return true;
            }
        }
        return false;
    }

    static boolean searchLook() {
        return Presentation.isAtLeast(LookArea.MENUS, "26.1");
    }

    static boolean headerFooterLook() {
        return Presentation.isAtLeast(LookArea.MENUS, "1.20.5");
    }

    // ---- layout ---------------------------------------------------------------------------------------------------

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        boolean header = headerFooterLook();
        if (searchLook()) {
            this.top = 36;
            String text = this.search != null ? this.search.getText() : this.filter;
            this.search = new GuiTextField(2, this.fontRenderer, this.width / 2 - 100, 4 + 9 + 4, 200, 15);
            this.search.setMaxStringLength(256);
            this.search.setText(text);
            this.search.setFocused(true);
            this.filter = text;
        } else {
            this.search = null;
            this.filter = "";
            this.top = header ? 33 : 43;
        }
        this.bottom = header ? this.height - 33 : this.height - 32;
        if (header) {
            this.done = new GuiButton(DONE, this.width / 2 - 154, this.height - 27, 150, 20, I18n.format("gui.done"));
            this.buttonList.add(this.done);
            this.buttonList.add(new GuiButton(CANCEL, this.width / 2 + 4, this.height - 27, 150, 20, I18n.format("gui.cancel")));
        } else {
            this.done = new GuiButton(DONE, this.width / 2 - 155, this.height - 29, 150, 20, I18n.format("gui.done"));
            this.buttonList.add(this.done);
            this.buttonList.add(new GuiButton(CANCEL, this.width / 2 + 5, this.height - 29, 150, 20, I18n.format("gui.cancel")));
        }
        if (this.list != null) {
            this.keepScroll = this.list.scrolled();
        }
        this.list = new RuleList(this.mc, this.width, this.height, this.top, this.bottom);
        populate();
        this.list.scrollTo(this.keepScroll);
        if (this.inWorld && !this.received) {
            GameRulesNetwork.ask(answer -> Minecraft.getMinecraft().addScheduledTask(() -> received(answer)));
        }
        updateDone();
    }

    /** 26.3's {@code onGameRuleValuesUpdated}: the server's answer (once). */
    void received(GameRulesNetwork.Values answer) {
        if (this.received || this.mc.currentScreen != this) {
            return;
        }
        GameRulesNetwork.forget();
        if (!answer.allowed) {
            // HasGamemasterPermissionReaction: no permission, back to the screen before.
            this.mc.displayGuiScreen(this.parent);
            return;
        }
        this.received = true;
        for (int i = 0; i < answer.ids.size(); i++) {
            String id = answer.ids.get(i);
            this.rules.add(GameRuleTable.unknown(id, answer.numeric.get(i), answer.values.get(i)));
            this.initial.put(id, answer.values.get(i));
            this.values.put(id, answer.values.get(i));
        }
        populate();
        updateDone();
    }

    /** 26.x's {@code RuleList.populateChildren}: the matching rules under their categories, both in 26.3's order. */
    void populate() {
        this.list.entries.clear();
        this.invalid.clear();
        if (!this.received) {
            return;
        }
        String lower = this.filter.toLowerCase(Locale.ROOT);
        Map<GameRuleTable.Category, List<GameRuleTable.Rule>> byCategory =
                new EnumMap<GameRuleTable.Category, List<GameRuleTable.Rule>>(GameRuleTable.Category.class);
        for (GameRuleTable.Rule rule : this.rules) {
            if (matches(rule, lower)) {
                List<GameRuleTable.Rule> in = byCategory.get(rule.category);
                if (in == null) {
                    byCategory.put(rule.category, in = new ArrayList<GameRuleTable.Rule>());
                }
                in.add(rule);
            }
        }
        for (Map.Entry<GameRuleTable.Category, List<GameRuleTable.Rule>> e : byCategory.entrySet()) {
            this.list.entries.add(new CategoryEntry(e.getKey()));
            List<GameRuleTable.Rule> sorted = new ArrayList<GameRuleTable.Rule>(e.getValue());
            Collections.sort(sorted, Comparator.comparing((GameRuleTable.Rule r) -> GameRuleTable.known(r.id) == null)
                    .thenComparing(r -> r.sortId));
            for (GameRuleTable.Rule rule : sorted) {
                this.list.entries.add(rule.numeric ? new NumberEntry(rule) : new BooleanEntry(rule));
            }
        }
    }

    /** 26.1's {@code matchesFilter}: the id (1.12.2's or 26.3's), the name, the category or the description. */
    static boolean matches(GameRuleTable.Rule rule, String lower) {
        if (lower.isEmpty()) {
            return true;
        }
        return rule.id.toLowerCase(Locale.ROOT).contains(lower) || rule.sortId.contains(lower)
                || name(rule).toLowerCase(Locale.ROOT).contains(lower)
                || I18n.format(rule.category.langKey()).toLowerCase(Locale.ROOT).contains(lower)
                || I18n.hasKey(rule.descriptionKey()) && I18n.format(rule.descriptionKey()).toLowerCase(Locale.ROOT).contains(lower);
    }

    static String name(GameRuleTable.Rule rule) {
        return I18n.hasKey(rule.nameKey) ? I18n.format(rule.nameKey) : rule.id;
    }

    private void updateDone() {
        this.done.enabled = this.invalid.isEmpty() && this.received;
    }

    // ---- actions --------------------------------------------------------------------------------------------------

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }
        if (button.id == DONE) {
            done();
        } else if (button.id == CANCEL) {
            close();
        }
    }

    /** Done: Create World keeps the values; in a world, the changed rules go to the server. */
    void done() {
        if (!this.invalid.isEmpty()) {
            return;
        }
        if (this.inWorld) {
            Map<String, String> changed = changed();
            if (!changed.isEmpty()) {
                GameRulesNetwork.send(changed);
                sent++;
            }
        } else if (this.onDone != null) {
            this.onDone.accept(new LinkedHashMap<String, String>(this.values));
        }
        this.mc.displayGuiScreen(this.parent);
    }

    /** Cancel or Esc: 26.3's {@code onClose}, which asks first in a world when there are changes. */
    void close() {
        if (this.inWorld && !changed().isEmpty()) {
            this.mc.displayGuiScreen(new GuiYesNo((result, id) -> this.mc.displayGuiScreen(result ? this.parent : this),
                    I18n.format("editGamerule.inGame.discardChanges.title"), I18n.format("editGamerule.inGame.discardChanges.message"), 0));
        } else {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    /** @return the rules whose value differs from the one the screen opened with */
    public Map<String, String> changed() {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : this.values.entrySet()) {
            if (!e.getValue().equals(this.initial.get(e.getKey()))) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return;
        }
        if (this.search != null && this.search.isFocused()) {
            if (this.search.textboxKeyTyped(typedChar, keyCode)) {
                setFilter(this.search.getText());
            }
            return;
        }
        for (IEntry entry : this.list.entries) {
            if (entry instanceof NumberEntry && ((NumberEntry) entry).field.isFocused()) {
                ((NumberEntry) entry).typed(typedChar, keyCode);
                return;
            }
        }
    }

    /** 26.1's {@code filterGameRules}: the list again, scrolled to the top. */
    public void setFilter(String text) {
        if (this.search != null && !this.search.getText().equals(text)) {
            this.search.setText(text);
        }
        this.filter = text;
        populate();
        this.list.scrollTo(0.0F);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        for (IEntry entry : this.list.entries) {
            if (entry instanceof NumberEntry) {
                ((NumberEntry) entry).field.setFocused(false);
            }
        }
        if (this.search != null) {
            this.search.mouseClicked(mouseX, mouseY, mouseButton);
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.list.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        this.list.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        this.list.handleMouseInput();
    }

    @Override
    public void updateScreen() {
        if (this.search != null) {
            this.search.updateCursorCounter();
        }
        for (IEntry entry : this.list.entries) {
            if (entry instanceof NumberEntry) {
                ((NumberEntry) entry).field.updateCursorCounter();
            }
        }
        if (this.inWorld && this.mc.world == null) {
            this.mc.displayGuiScreen(this.parent);
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        this.list.drawScreen(mouseX, mouseY, partialTicks);
        String title = I18n.format("editGamerule.title");
        int titleY = searchLook() ? 4 : headerFooterLook() ? (33 - 9) / 2 : 20;
        drawCenteredString(this.fontRenderer, title, this.width / 2, titleY, 0xFFFFFF);
        if (this.search != null) {
            this.search.drawTextBox();
            if (this.search.getText().isEmpty()) {
                // EditBox's hint, in SEARCH_HINT_STYLE.
                this.fontRenderer.drawStringWithShadow(TextFormatting.GRAY.toString() + TextFormatting.ITALIC + I18n.format("gui.game_rule.search"),
                        this.search.x + 4, this.search.y + (15 - 8) / 2, 0xFFFFFF);
            }
        }
        if (!this.received) {
            // LoadingDotsWidget: the text, and the dots under it.
            int mid = (this.top + this.bottom) / 2;
            drawCenteredString(this.fontRenderer, I18n.format("editGamerule.inGame.downloadingGamerules"), this.width / 2, mid - 9 - 1, 0xFFFFFF);
            drawCenteredString(this.fontRenderer, dots(System.currentTimeMillis() - this.opened), this.width / 2, mid + 2, 0x808080);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        List<String> tooltip = tooltipAt(mouseX, mouseY);
        if (tooltip != null) {
            drawHoveringText(tooltip, mouseX, mouseY);
        }
    }

    /** {@code LoadingDotsText}: a frame every 300 ms. */
    static String dots(long millis) {
        String[] frames = {"O o o", "o O o", "o o O", "o O o"};
        return frames[(int) (millis / 300L % frames.length)];
    }

    @Nullable
    List<String> tooltipAt(int mouseX, int mouseY) {
        if (mouseY < this.top || mouseY >= this.bottom) {
            return null;
        }
        int index = this.list.getSlotIndexFromScreenCoords(mouseX, mouseY);
        if (index < 0 || index >= this.list.entries.size()) {
            return null;
        }
        IEntry entry = this.list.entries.get(index);
        return entry instanceof RuleEntry ? ((RuleEntry) entry).tooltip() : null;
    }

    // ---- for the client self-test ---------------------------------------------------------------------------------

    public boolean received() {
        return this.received;
    }

    /** @return each row as the screen shows it: "# Category", "Name = value" */
    public List<String> rows() {
        List<String> out = new ArrayList<String>();
        for (IEntry entry : this.list.entries) {
            out.add(entry.toString());
        }
        return out;
    }

    /** Sets a rule as clicking or typing would (the shown value: "true"/"false" or a number). */
    public boolean set(String id, String shown) {
        for (IEntry entry : this.list.entries) {
            if (entry instanceof RuleEntry && ((RuleEntry) entry).rule.id.equals(id)) {
                if (entry instanceof BooleanEntry) {
                    if (!((BooleanEntry) entry).shown().equals(shown)) {
                        ((BooleanEntry) entry).toggle();
                    }
                } else {
                    ((NumberEntry) entry).field.setText(shown);
                    ((NumberEntry) entry).changed();
                }
                return true;
            }
        }
        return false;
    }

    @Nullable
    public List<String> tooltipOf(String id) {
        for (IEntry entry : this.list.entries) {
            if (entry instanceof RuleEntry && ((RuleEntry) entry).rule.id.equals(id)) {
                return ((RuleEntry) entry).tooltip();
            }
        }
        return null;
    }

    public boolean doneEnabled() {
        return this.done.enabled;
    }

    public void pressDone() {
        actionPerformed(this.done);
    }

    public void pressCancel() {
        close();
    }

    public int top() {
        return this.top;
    }

    public boolean hasSearch() {
        return this.search != null;
    }

    // ---- the list -------------------------------------------------------------------------------------------------

    interface IEntry extends GuiListExtended.IGuiListEntry {
    }

    final class RuleList extends GuiListExtended {
        final List<IEntry> entries = new ArrayList<IEntry>();

        RuleList(Minecraft mc, int width, int height, int top, int bottom) {
            super(mc, width, height, top, bottom, ROW);
            this.centerListVertically = false;
        }

        @Override
        public IGuiListEntry getListEntry(int index) {
            return this.entries.get(index);
        }

        @Override
        protected int getSize() {
            return this.entries.size();
        }

        float scrolled() {
            return this.amountScrolled;
        }

        void scrollTo(float amount) {
            this.amountScrolled = amount;
            bindAmountScrolled();
        }
    }

    /** 26.x's {@code CategoryRuleEntry}: the category's name, centered, bold and yellow (26.1: underlined too). */
    final class CategoryEntry implements IEntry {
        final GameRuleTable.Category category;

        CategoryEntry(GameRuleTable.Category category) {
            this.category = category;
        }

        String label() {
            String style = TextFormatting.YELLOW.toString() + TextFormatting.BOLD + (searchLook() ? TextFormatting.UNDERLINE : "");
            return style + I18n.format(this.category.langKey());
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            drawCenteredString(GameRulesScreen.this.fontRenderer, label(), x + (listWidth - 4) / 2, y + (searchLook() ? 6 : 5), 0xFFFFFF);
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

        @Override
        public String toString() {
            return "# " + I18n.format(this.category.langKey());
        }
    }

    /** 26.x's {@code GameRuleEntry}: the name at the left, the control 45 from the right. */
    abstract class RuleEntry implements IEntry {
        final GameRuleTable.Rule rule;
        final List<String> label;

        RuleEntry(GameRuleTable.Rule rule) {
            this.rule = rule;
            this.label = GameRulesScreen.this.fontRenderer.listFormattedStringToWidth(name(rule), 170);
        }

        String shown() {
            return this.rule.shown(GameRulesScreen.this.values.get(this.rule.id));
        }

        /** The id in yellow, the description (wrapped at 150), "Default: x" in gray. */
        List<String> tooltip() {
            List<String> out = new ArrayList<String>();
            out.add(TextFormatting.YELLOW + this.rule.id);
            if (I18n.hasKey(this.rule.descriptionKey())) {
                out.addAll(GameRulesScreen.this.fontRenderer.listFormattedStringToWidth(I18n.format(this.rule.descriptionKey()), 150));
            }
            if (GameRuleTable.known(this.rule.id) != null) {
                out.add(TextFormatting.GRAY + I18n.format("editGamerule.default", this.rule.shown(this.rule.defaultValue)));
            }
            return out;
        }

        void drawLabel(int x, int y) {
            if (this.label.size() == 1) {
                GameRulesScreen.this.fontRenderer.drawString(this.label.get(0), x, y + 5, 0xFFFFFF);
            } else if (this.label.size() >= 2) {
                GameRulesScreen.this.fontRenderer.drawString(this.label.get(0), x, y, 0xFFFFFF);
                GameRulesScreen.this.fontRenderer.drawString(this.label.get(1), x, y + 10, 0xFFFFFF);
            }
        }

        static final int CONTROL_FROM_RIGHT = 45;

        int controlX(int x, int listWidth) {
            return x + (listWidth - 4) - CONTROL_FROM_RIGHT;
        }

        @Override
        public void updatePosition(int slotIndex, int x, int y, float partialTicks) {
        }

        @Override
        public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {
        }

        @Override
        public String toString() {
            return name(this.rule) + " = " + shown();
        }
    }

    /** 26.x's {@code BooleanRuleEntry}: an ON/OFF button, 44 wide. */
    final class BooleanEntry extends RuleEntry {
        final GuiButton button = new GuiButton(100, 0, 0, 44, 20, "");

        BooleanEntry(GameRuleTable.Rule rule) {
            super(rule);
        }

        void toggle() {
            boolean on = Boolean.parseBoolean(shown());
            GameRulesScreen.this.values.put(this.rule.id, this.rule.stored(String.valueOf(!on)));
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            drawLabel(x, y);
            this.button.x = controlX(x, listWidth);
            this.button.y = y;
            this.button.displayString = I18n.format(Boolean.parseBoolean(shown()) ? "options.on" : "options.off");
            this.button.drawButton(GameRulesScreen.this.mc, mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
            if (this.button.mousePressed(GameRulesScreen.this.mc, mouseX, mouseY)) {
                this.button.playPressSound(GameRulesScreen.this.mc.getSoundHandler());
                toggle();
                return true;
            }
            return false;
        }
    }

    /** 26.x's {@code IntegerRuleEntry}: a 44-wide box, its text red while the value is not a valid number. */
    final class NumberEntry extends RuleEntry {
        final GuiTextField field;

        NumberEntry(GameRuleTable.Rule rule) {
            super(rule);
            this.field = new GuiTextField(101, GameRulesScreen.this.fontRenderer, 0, 0, 44, 20);
            this.field.setMaxStringLength(32);
            this.field.setText(shown());
            changed();
        }

        void typed(char c, int key) {
            if (this.field.textboxKeyTyped(c, key)) {
                changed();
            }
        }

        void changed() {
            String text = this.field.getText();
            if (this.rule.valid(text)) {
                this.field.setTextColor(0xE0E0E0);
                GameRulesScreen.this.invalid.remove(this.rule.id);
                GameRulesScreen.this.values.put(this.rule.id, this.rule.stored(text));
            } else {
                this.field.setTextColor(0xFF0000);
                GameRulesScreen.this.invalid.add(this.rule.id);
            }
            if (GameRulesScreen.this.done != null) {
                updateDone();
            }
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            drawLabel(x, y);
            // GuiTextField's border is outside its box: one pixel in, as 1.12.2's own screens place theirs.
            this.field.x = controlX(x, listWidth) + 1;
            this.field.y = y + 1;
            this.field.width = 42;
            this.field.height = 18;
            this.field.drawTextBox();
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
            return this.field.mouseClicked(mouseX, mouseY, mouseEvent);
        }

        @Override
        public String toString() {
            return name(this.rule) + " = " + this.field.getText();
        }
    }
}

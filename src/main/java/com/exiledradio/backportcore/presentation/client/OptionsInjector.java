package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.VideoSections;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.PresentationOption;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiCustomizeSkin;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiOptionButton;
import net.minecraft.client.gui.GuiOptionSlider;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiOptionsRowList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenOptionsSounds;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.ScreenChatOptions;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts the backports' options on 1.12.2's own options screens, through Forge's GUI events (no patch), and
 * gives those screens the newest installed version's layout as far as that goes without one:
 * <ul>
 * <li>Options: from 1.14, "Accessibility Settings..." takes the place of "Snooper Settings..." (1.14
 * removed the snooper screen). Its options fill the free row above Done.</li>
 * <li>Video Settings: its options join the scrolling list, after the entry each names or at the end. Where
 * OptiFine has rewritten the screen (no list), {@link OptiFineVideoScreen} lays OptiFine's screen out the same way,
 * with OptiFine's submenus as a section of their own, and leaves out the options OptiFine has its own of
 * ({@link VideoSections#OPTIFINE_HAS}).</li>
 * <li>Controls: from 1.14, "Mouse Settings..." and Auto-Jump take the first row (Sensitivity, Invert Mouse
 * and Touchscreen move to Mouse Settings, as in 1.14); its options take the second row.</li>
 * <li>Chat, Music and Sounds, Skin Customization: its options continue the two-column grid after vanilla's,
 * and Done moves down when it has to.</li>
 * </ul>
 * Vanilla's own entries keep 1.12.2's order and names; a version's renames are its backport's lang file.
 */
@SideOnly(Side.CLIENT)
public final class OptionsInjector {

    private static final int SNOOPER_BUTTON = 104;
    private static final int DONE_BUTTON = 200;

    private static final int CONTROLS_BUTTON = 100;

    private static Field videoList;
    private static boolean videoListMissing;
    private static boolean optiFineParentMissing;
    private static Field controlsTitle;
    private static Field controlsList;
    private static Field scrolledField;
    private static Field rowOptions;
    private static Field rowA;
    private static Field rowB;

    /** For the client self-test: the screens injected into this session. */
    public static final List<String> INJECTED = new ArrayList<String>();
    /** For the client self-test: screens Core could not add its options to. */
    public static volatile int failures;

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.getGui();
        List<GuiButton> buttons = event.getButtonList();
        Class<?> c = gui.getClass();
        try {
            if (c == OptiFineVideoScreen.class) {
                if (!((OptiFineVideoScreen) gui).laidOut()) {
                    return;
                }
            } else if (c == GuiOptions.class) {
                options(gui, buttons);
            } else if (c == GuiVideoSettings.class) {
                video(gui, buttons);
            } else if (c == GuiControls.class) {
                controls(gui, buttons);
            } else if (c == ScreenChatOptions.class) {
                appendToGrid(gui, buttons, Presentation.optionsOn(OptionScreen.CHAT));
            } else if (c == GuiScreenOptionsSounds.class) {
                appendToGrid(gui, buttons, Presentation.optionsOn(OptionScreen.SOUNDS));
            } else if (c == GuiCustomizeSkin.class) {
                appendToGrid(gui, buttons, Presentation.optionsOn(OptionScreen.SKIN));
            } else {
                return;
            }
            INJECTED.add(c.getSimpleName());
        } catch (Exception e) {
            failures++;
            BackportCore.LOGGER.error("Presentation: could not add options to {}", c.getName(), e);
        }
    }

    /**
     * OptiFine's Video Settings (a {@code GuiVideoSettings} without a list) opens as {@link OptiFineVideoScreen}: the
     * same screen of OptiFine's, laid out as Core lays out vanilla's.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onOpen(GuiOpenEvent event) {
        GuiScreen gui = event.getGui();
        if (gui == null || gui.getClass() != GuiVideoSettings.class || !OptiFineVideoScreen.optiFineShape()) {
            return;
        }
        if (!VideoLayout.active() && videoOptions(true).isEmpty()) {
            return;
        }
        GuiScreen parent = OptiFineVideoScreen.parentOf((GuiVideoSettings) gui);
        if (parent == null) {
            if (!optiFineParentMissing) {
                optiFineParentMissing = true;
                BackportCore.LOGGER.warn("Presentation: OptiFine's Video Settings has no parent screen field; left as it is");
            }
            return;
        }
        try {
            event.setGui(new OptiFineVideoScreen(parent, Minecraft.getMinecraft().gameSettings));
        } catch (RuntimeException e) {
            BackportCore.LOGGER.warn("Presentation: could not open OptiFine's Video Settings as Core's ({}); left as it is", e.toString());
        }
    }

    /** @return the options on Video Settings, less those OptiFine has its own of when {@code optiFine} */
    static List<PresentationOption> videoOptions(boolean optiFine) {
        List<PresentationOption> out = new ArrayList<PresentationOption>();
        for (PresentationOption o : Presentation.optionsOn(OptionScreen.VIDEO)) {
            if (!optiFine || !VideoSections.optiFineHas(o.id())) {
                out.add(o);
            }
        }
        return out;
    }

    /** Core's action buttons (opening its screens) act here, and vanilla's own handler never sees them. */
    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.getButton() instanceof OptionWidgets.Action) {
            ((OptionWidgets.Action) event.getButton()).action.run();
            event.setCanceled(true);
        }
    }

    private static void options(GuiScreen gui, List<GuiButton> buttons) {
        if (VanillaRevisions.keyBindsScreen(Presentation.newest(com.exiledradio.backportcore.presentation.LookArea.OPTIONS))) {
            // 1.18: "Controls..." opens the Controls screen; 1.12.2's key list is Key Binds behind it.
            GuiButton controls = find(buttons, CONTROLS_BUTTON);
            if (controls != null) {
                buttons.remove(controls);
                buttons.add(new OptionWidgets.Action(controls.x, controls.y, controls.width, controls.displayString,
                        () -> Minecraft.getMinecraft().displayGuiScreen(OptionsListScreen.controls(gui))));
            }
        }
        if (Presentation.isAtLeast(com.exiledradio.backportcore.presentation.LookArea.OPTIONS, "1.14")) {
            GuiButton snooper = find(buttons, SNOOPER_BUTTON);
            if (snooper != null) {
                buttons.remove(snooper);
                buttons.add(new OptionWidgets.Action(snooper.x, snooper.y, snooper.width, I18n.format("options.accessibility.title"),
                        () -> Minecraft.getMinecraft().displayGuiScreen(OptionsListScreen.accessibility(gui))));
            }
        }
        appendToGrid(gui, buttons, Presentation.optionsOn(OptionScreen.OPTIONS));
    }

    private static void controls(GuiScreen gui, List<GuiButton> buttons) throws Exception {
        if (VanillaRevisions.keyBindsScreen(Presentation.newest(com.exiledradio.backportcore.presentation.LookArea.OPTIONS))) {
            keyBinds((GuiControls) gui, buttons);
            return;
        }
        int left = gui.width / 2 - 155;
        int right = gui.width / 2 + 5;
        if (Presentation.isAtLeast(com.exiledradio.backportcore.presentation.LookArea.OPTIONS, "1.14")) {
            for (GameSettings.Options moved : OptionsListScreen.MOUSE) {
                GuiButton b = find(buttons, moved.getOrdinal());
                if (b instanceof GuiOptionButton || b instanceof GuiOptionSlider) {
                    buttons.remove(b);
                }
            }
            GuiButton autoJump = find(buttons, GameSettings.Options.AUTO_JUMP.getOrdinal());
            if (autoJump != null) {
                autoJump.x = right;
                autoJump.y = 18;
            }
            buttons.add(new OptionWidgets.Action(left, 18, 150, I18n.format("options.mouse_settings"),
                    () -> Minecraft.getMinecraft().displayGuiScreen(OptionsListScreen.mouse(gui))));
        }
        // The key list starts at y 63: room for the row at 18 and the one at 42, no more.
        List<PresentationOption> options = Presentation.optionsOn(OptionScreen.CONTROLS);
        int[][] slots = {{left, 18}, {right, 18}, {left, 42}, {right, 42}};
        int placed = 0;
        for (int[] slot : slots) {
            if (placed >= options.size()) {
                break;
            }
            if (occupied(buttons, slot[0], slot[1])) {
                continue;
            }
            buttons.add(OptionWidgets.create(options.get(placed++), slot[0], slot[1]));
        }
        if (placed < options.size()) {
            BackportCore.LOGGER.warn("Presentation: no room on Controls for {} option(s), starting with {}",
                    options.size() - placed, options.get(placed).id());
        }
    }

    /**
     * 1.18's Key Binds (1.12.2's Controls with its top rows gone): the title "Key Binds" and the list from y 20, as
     * 1.18's {@code KeyBindsList}. Mouse Settings, Auto-Jump and the options are on Core's Controls screen.
     */
    private static void keyBinds(GuiControls gui, List<GuiButton> buttons) throws Exception {
        for (java.util.Iterator<GuiButton> it = buttons.iterator(); it.hasNext(); ) {
            GuiButton b = it.next();
            if (b instanceof GuiOptionButton || b instanceof GuiOptionSlider) {
                it.remove();
            }
        }
        if (controlsTitle == null) {
            controlsTitle = ReflectionHelper.findField(GuiControls.class, "screenTitle", "field_146495_a");
            controlsList = ReflectionHelper.findField(GuiControls.class, "keyBindingList", "field_146494_r");
        }
        controlsTitle.set(gui, I18n.format("controls.keybinds.title"));
        Object list = controlsList.get(gui);
        if (list instanceof net.minecraft.client.gui.GuiSlot) {
            ((net.minecraft.client.gui.GuiSlot) list).top = 20;
        }
    }

    /** Lays a screen out again (its rows show new values), keeping where Video Settings' list was scrolled. */
    static void relayout(GuiScreen screen) {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            Float scrolled = null;
            if (screen instanceof OptiFineVideoScreen) {
                // It keeps where its list was scrolled itself.
                screen.setWorldAndResolution(mc, screen.width, screen.height);
                return;
            }
            if (screen instanceof GuiVideoSettings && videoList != null && scrollField() != null) {
                Object list = videoList.get(screen);
                if (list instanceof net.minecraft.client.gui.GuiSlot) {
                    scrolled = (Float) scrollField().get(list);
                }
            }
            screen.setWorldAndResolution(mc, screen.width, screen.height);
            if (scrolled != null) {
                Object list = videoList.get(screen);
                if (list instanceof net.minecraft.client.gui.GuiSlot) {
                    scrollField().set(list, scrolled);
                }
            }
        } catch (Exception e) {
            BackportCore.LOGGER.error("Presentation: could not lay out {} again", screen.getClass().getName(), e);
        }
    }

    @Nullable
    private static Field scrollField() {
        if (scrolledField == null) {
            scrolledField = field(net.minecraft.client.gui.GuiSlot.class, "amountScrolled", "field_148169_q");
        }
        return scrolledField;
    }

    /** @return the field by one of its names, or null (never throws) */
    @Nullable
    static Field field(Class<?> c, String... names) {
        try {
            return ReflectionHelper.findField(c, names);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Finds vanilla's list fields once; false (and one line in the log) when this game's screen has no list. */
    private static boolean videoFields() {
        if (videoList != null) {
            return true;
        }
        if (videoListMissing) {
            return false;
        }
        videoList = field(GuiVideoSettings.class, "optionsRowList", "field_146501_h");
        rowFields();
        if (videoList == null || rowOptions == null || rowA == null || rowB == null) {
            videoList = null;
            videoListMissing = true;
            BackportCore.LOGGER.warn("Presentation: this game's Video Settings has no options list (another mod's screen); its options are not added");
            return false;
        }
        return true;
    }

    private static void rowFields() {
        if (rowA == null) {
            rowOptions = field(GuiOptionsRowList.class, "options", "field_148184_k");
            rowA = field(GuiOptionsRowList.Row.class, "buttonA", "field_148323_b");
            rowB = field(GuiOptionsRowList.Row.class, "buttonB", "field_148324_c");
        }
    }

    /** @return the buttons in these rows (headers have none) */
    static List<GuiButton> buttonsOf(List<GuiOptionsRowList.Row> rows) throws IllegalAccessException {
        rowFields();
        List<GuiButton> out = new ArrayList<GuiButton>();
        if (rowA == null || rowB == null) {
            return out;
        }
        for (GuiOptionsRowList.Row row : rows) {
            for (Field f : new Field[] {rowA, rowB}) {
                GuiButton b = (GuiButton) f.get(row);
                if (b != null) {
                    out.add(b);
                }
            }
        }
        return out;
    }

    /** Each frame of Video Settings from 1.21.11: the preset turns Custom on a change; Anisotropic Filtering's state. */
    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!(event.getGui() instanceof GuiVideoSettings) || !VideoLayout.active()
                || videoList == null && !(event.getGui() instanceof OptiFineVideoScreen)) {
            return;
        }
        try {
            List<GuiButton> all = rowButtons(event.getGui());
            if (all != null) {
                VideoLayout.tick(event.getGui(), all);
            }
        } catch (Exception e) {
            BackportCore.LOGGER.debug("Presentation: Video Settings rows unreadable", e);
        }
    }

    /** @return every button in Video Settings' rows, or null when its list is another mod's */
    @Nullable
    @SuppressWarnings("unchecked")
    static List<GuiButton> rowButtons(GuiScreen gui) throws Exception {
        if (gui instanceof OptiFineVideoScreen) {
            return ((OptiFineVideoScreen) gui).laidOut() ? ((OptiFineVideoScreen) gui).rowButtons() : null;
        }
        if (gui instanceof OptionsListScreen) {
            return ((OptionsListScreen) gui).rowButtons();
        }
        if (!(gui instanceof GuiVideoSettings) || !videoFields()) {
            return null;
        }
        Object list = videoList.get(gui);
        if (!(list instanceof GuiOptionsRowList)) {
            return null;
        }
        return buttonsOf((List<GuiOptionsRowList.Row>) rowOptions.get(list));
    }

    private static void video(GuiScreen gui, List<GuiButton> buttons) throws Exception {
        List<PresentationOption> options = Presentation.optionsOn(OptionScreen.VIDEO);
        boolean edits = com.exiledradio.backportcore.presentation.VanillaRevisions.anaglyphRemoved();
        if (options.isEmpty() && !edits) {
            return;
        }
        if (!videoFields()) {
            return;
        }
        GuiListExtended list = (GuiListExtended) videoList.get(gui);
        if (!(list instanceof GuiOptionsRowList)) {
            // Another mod's video screen: the options go in its button grid instead.
            appendToGrid(gui, buttons, options);
            return;
        }
        @SuppressWarnings("unchecked")
        List<GuiOptionsRowList.Row> rows = (List<GuiOptionsRowList.Row>) rowOptions.get(list);
        List<GuiButton> entries = buttonsOf(rows);
        List<String> keys = new ArrayList<String>();
        for (GuiButton b : entries) {
            GameSettings.Options o = GameSettings.Options.byOrdinal(b.id);
            keys.add(o == null ? "" : o.name());
        }
        List<GuiOptionsRowList.Row> laid = videoRows(gui, entries, keys, false);
        rows.clear();
        rows.addAll(laid);
    }

    /**
     * Video Settings' rows from the screen's own entries ({@code entries} and {@code keys} in step, edited in
     * place): vanilla's entries as the newest version had them, the options after their anchors, then 1.21.11's
     * sections or 1.12.2's two columns. With {@code optiFine}, the options OptiFine has its own of are left out.
     */
    static List<GuiOptionsRowList.Row> videoRows(GuiScreen gui, List<GuiButton> entries, List<String> keys, boolean optiFine) {
        VideoOptions.edit(gui, entries, keys);
        insertAfterAnchors(entries, keys, videoOptions(optiFine));
        List<GuiOptionsRowList.Row> rows = new ArrayList<GuiOptionsRowList.Row>();
        if (VideoLayout.active()) {
            rows.addAll(VideoLayout.rows(gui, entries, keys));
            return rows;
        }
        for (int i = 0; i < entries.size(); i += 2) {
            GuiButton a = entries.get(i);
            GuiButton b = i + 1 < entries.size() ? entries.get(i + 1) : null;
            a.x = gui.width / 2 - 155;
            if (b != null) {
                b.x = gui.width / 2 - 155 + 160;
            }
            rows.add(new GuiOptionsRowList.Row(a, b));
        }
        return rows;
    }

    /**
     * Puts each option's button after the entry it names ({@link PresentationOption#after}: a key of
     * {@code keys}, or an option placed before it), or at the end. {@code entries} and {@code keys} run in step.
     */
    static void insertAfterAnchors(List<GuiButton> entries, List<String> keys, List<PresentationOption> options) {
        for (PresentationOption o : options) {
            GuiButton button = OptionWidgets.create(o, 0, 0);
            int at = o.after() == null ? -1 : keys.lastIndexOf(o.after());
            if (at < 0) {
                entries.add(button);
                keys.add(o.id());
            } else {
                entries.add(at + 1, button);
                keys.add(at + 1, o.id());
            }
        }
    }

    /**
     * Continues the screen's two-column grid of 150-wide buttons: the right half of a last row with only a left
     * button, then new rows 24 apart; Done moves down to stay below them.
     */
    static void appendToGrid(GuiScreen gui, List<GuiButton> buttons, List<PresentationOption> options) {
        if (options.isEmpty()) {
            return;
        }
        int left = gui.width / 2 - 155;
        int right = gui.width / 2 + 5;
        int lastY = Integer.MIN_VALUE;
        for (GuiButton b : buttons) {
            if (b.id != DONE_BUTTON && b.width == 150 && (b.x == left || b.x == right)) {
                lastY = Math.max(lastY, b.y);
            }
        }
        int x;
        int y;
        if (lastY == Integer.MIN_VALUE) {
            x = left;
            y = gui.height / 6;
        } else if (occupied(buttons, left, lastY) && !occupied(buttons, right, lastY)) {
            x = right;
            y = lastY;
        } else {
            x = left;
            y = lastY + 24;
        }
        int gridEnd = lastY == Integer.MIN_VALUE ? y - 1 : lastY;
        int lastPlaced = y;
        List<GuiButton> added = new ArrayList<GuiButton>();
        for (PresentationOption o : options) {
            added.add(OptionWidgets.create(o, x, y));
            lastPlaced = y;
            if (x == left) {
                x = right;
            } else {
                x = left;
                y += 24;
            }
        }
        // Buttons below the grid that are not part of it (Done; OptiFine's "OptiFine Cape..." on Skin Customization)
        // move down together, keeping their order, to stay below the new rows.
        int below = Integer.MAX_VALUE;
        for (GuiButton b : buttons) {
            if (b.y > gridEnd) {
                below = Math.min(below, b.y);
            }
        }
        if (below != Integer.MAX_VALUE && below < lastPlaced + 24) {
            int shift = lastPlaced + 24 - below;
            for (GuiButton b : buttons) {
                if (b.y > gridEnd) {
                    b.y += shift;
                }
            }
        }
        buttons.addAll(added);
    }

    private static boolean occupied(List<GuiButton> buttons, int x, int y) {
        for (GuiButton b : buttons) {
            if (b.visible && b.y == y && x < b.x + b.width && b.x < x + 150) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static GuiButton find(List<GuiButton> buttons, int id) {
        for (GuiButton b : buttons) {
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }
}

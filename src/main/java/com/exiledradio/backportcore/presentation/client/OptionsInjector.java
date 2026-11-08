package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;
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
 * <li>Video Settings: its options join the scrolling list, after the entry each names or at the end.</li>
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

    private static Field videoList;
    private static Field rowOptions;
    private static Field rowA;
    private static Field rowB;

    /** For the client self-test: the screens injected into this session. */
    public static final List<String> INJECTED = new ArrayList<String>();

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.getGui();
        List<GuiButton> buttons = event.getButtonList();
        Class<?> c = gui.getClass();
        try {
            if (c == GuiOptions.class) {
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
            BackportCore.LOGGER.error("Presentation: could not add options to {}", c.getName(), e);
        }
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
        if (Presentation.isAtLeast("1.14")) {
            GuiButton snooper = find(buttons, SNOOPER_BUTTON);
            if (snooper != null) {
                buttons.remove(snooper);
                buttons.add(new OptionWidgets.Action(snooper.x, snooper.y, snooper.width, I18n.format("options.accessibility.title"),
                        () -> Minecraft.getMinecraft().displayGuiScreen(OptionsListScreen.accessibility(gui))));
            }
        }
        appendToGrid(gui, buttons, Presentation.optionsOn(OptionScreen.OPTIONS));
    }

    private static void controls(GuiScreen gui, List<GuiButton> buttons) {
        int left = gui.width / 2 - 155;
        int right = gui.width / 2 + 5;
        if (Presentation.isAtLeast("1.14")) {
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

    private static void video(GuiScreen gui, List<GuiButton> buttons) throws Exception {
        List<PresentationOption> options = Presentation.optionsOn(OptionScreen.VIDEO);
        if (options.isEmpty()) {
            return;
        }
        if (videoList == null) {
            videoList = ReflectionHelper.findField(GuiVideoSettings.class, "optionsRowList", "field_146501_h");
            rowOptions = ReflectionHelper.findField(GuiOptionsRowList.class, "options", "field_148184_k");
            rowA = ReflectionHelper.findField(GuiOptionsRowList.Row.class, "buttonA", "field_148323_b");
            rowB = ReflectionHelper.findField(GuiOptionsRowList.Row.class, "buttonB", "field_148324_c");
        }
        GuiListExtended list = (GuiListExtended) videoList.get(gui);
        if (!(list instanceof GuiOptionsRowList)) {
            // Another mod's video screen: the options go in its button grid instead.
            appendToGrid(gui, buttons, options);
            return;
        }
        @SuppressWarnings("unchecked")
        List<GuiOptionsRowList.Row> rows = (List<GuiOptionsRowList.Row>) rowOptions.get(list);
        List<GuiButton> entries = new ArrayList<GuiButton>();
        List<String> keys = new ArrayList<String>();
        for (GuiOptionsRowList.Row row : rows) {
            for (Field f : new Field[] {rowA, rowB}) {
                GuiButton b = (GuiButton) f.get(row);
                if (b != null) {
                    entries.add(b);
                    GameSettings.Options o = GameSettings.Options.byOrdinal(b.id);
                    keys.add(o == null ? "" : o.name());
                }
            }
        }
        insertAfterAnchors(entries, keys, options);
        rows.clear();
        for (int i = 0; i < entries.size(); i += 2) {
            GuiButton a = entries.get(i);
            GuiButton b = i + 1 < entries.size() ? entries.get(i + 1) : null;
            a.x = gui.width / 2 - 155;
            if (b != null) {
                b.x = gui.width / 2 - 155 + 160;
            }
            rows.add(new GuiOptionsRowList.Row(a, b));
        }
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
        int lastPlaced = y;
        for (PresentationOption o : options) {
            buttons.add(OptionWidgets.create(o, x, y));
            lastPlaced = y;
            if (x == left) {
                x = right;
            } else {
                x = left;
                y += 24;
            }
        }
        GuiButton done = find(buttons, DONE_BUTTON);
        if (done != null && done.y < lastPlaced + 24) {
            done.y = lastPlaced + 24;
        }
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

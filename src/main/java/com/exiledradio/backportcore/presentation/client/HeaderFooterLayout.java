package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionsRowList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 1.20.5's {@code HeaderAndFooterLayout} (26.3's is the same class) for 1.12.2's screens and Core's own. Since Core 1.4.5.
 *
 * <p>26.3's options-like screens are three frames the width of the screen: a header ({@link #HEADER}, 33 high) with the
 * title centered in it ({@code addTitleHeader}: a 9-high line at y 12), a footer ({@link #FOOTER}, 33 high) with Done
 * (200 wide) or a row of buttons 8 apart, centered at {@code height - 27}, and the contents between. A list fills the
 * contents ({@code OptionsSubScreen}: rows 25 high, two 150-wide columns at {@code width / 2 - 155} and {@code + 5},
 * the list background and separators of {@link MenuBackground}); anything else is centered and starts 30 under the
 * header ({@link #contentsTop}, {@code arrangeElements}).
 *
 * <p><b>Using it on a screen</b> (Core's options screens do; Language, Resource Packs and Select World are next):
 * <ol>
 *   <li>Gate it: {@link #active(LookArea)} for the area the screen belongs to. Before 1.20.5 a screen keeps the layout
 *       it had at the newest installed version.</li>
 *   <li>The title: {@link #title(GuiScreen, String, int)}. For a 1.12.2 screen that draws its own title from a field,
 *       {@link #takeTitle(GuiScreen, Class, String...)} empties the field first and hands back its text.</li>
 *   <li>The footer: {@link #footer(GuiScreen, GuiButton...)} puts the buttons in one centered row.</li>
 *   <li>The contents: a 1.12.2 {@code GuiSlot} list fits between the separators with {@link #fitList}; a screen's own
 *       grid of buttons becomes 26.3's scrolling list with {@link #attachRows} (the buttons leave the screen's button list
 *       for the list's rows, and still reach the screen's {@code actionPerformed} and Forge's action events); a grid that
 *       stays a grid takes its top from {@link #contentsTop}.</li>
 * </ol>
 * Everything is done from Forge's {@code InitGuiEvent.Post} (or the screen's own {@code initGui}); the title and the
 * list are drawn, and the list handles the mouse, from this class's {@link Events}, so a screen needs no patch. Only one
 * screen is attached at a time: the one opened last.
 */
@SideOnly(Side.CLIENT)
public final class HeaderFooterLayout {

    /** {@code HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT}. */
    public static final int HEADER = 33;
    public static final int FOOTER = 33;
    /** {@code OptionsList}'s row height. */
    public static final int ROW = 25;
    /** The space between a footer's buttons ({@code LinearLayout.spacing(8)}). */
    public static final int SPACING = 8;

    private static Field buttonListField;
    private static Field selectedButtonField;
    private static Method actionPerformed;

    @Nullable
    private static Attached current;

    /** For the client self-test: frames in which an attached screen's title or list was drawn. */
    public static volatile int frames;

    private HeaderFooterLayout() {
    }

    /** @return true when screens of {@code area} have 1.20.5's header and footer (the newest installed version's look) */
    public static boolean active(LookArea area) {
        return VanillaRevisions.headerAndFooter(Presentation.newest(area));
    }

    /** @return the y of a title centered in a header this high ({@code FrameLayout} centering a 9-high line) */
    public static int titleY(int header) {
        return (header - 9) / 2;
    }

    /** @return the y of a 20-high footer button */
    public static int footerY(GuiScreen screen) {
        return screen.height - FOOTER + (FOOTER - 20) / 2;
    }

    /**
     * {@code HeaderAndFooterLayout.arrangeElements}: the contents' top, 30 under the header, or higher when they would
     * otherwise run into the footer.
     */
    public static int contentsTop(GuiScreen screen, int header, int contentHeight) {
        return Math.min(header + 30, screen.height - FOOTER - contentHeight);
    }

    /** Puts the buttons in one row centered on {@code centerX}, {@link #SPACING} apart, at {@code y}. */
    public static void row(int centerX, int y, GuiButton... buttons) {
        int total = SPACING * (buttons.length - 1);
        for (GuiButton b : buttons) {
            total += b.width;
        }
        int x = centerX - total / 2;
        for (GuiButton b : buttons) {
            b.x = x;
            b.y = y;
            x += b.width + SPACING;
        }
    }

    /** The footer: the buttons in one row, centered, at {@link #footerY}. */
    public static void footer(GuiScreen screen, GuiButton... buttons) {
        row(screen.width / 2, footerY(screen), buttons);
        attached(screen).footer = buttons.clone();
    }

    /** A 1.12.2 list ({@code GuiSlot}) between the header and the footer separators. */
    public static void fitList(GuiSlot list, GuiScreen screen) {
        list.top = HEADER;
        list.bottom = screen.height - FOOTER;
    }

    /** The title, drawn centered in a header {@code header} high (33, or 61 for Options). */
    public static void title(GuiScreen screen, String title, int header) {
        titleAt(screen, title, titleY(header));
    }

    /** The title, drawn centered with its top at {@code y} (a header that holds more than the title, as Options'). */
    public static void titleAt(GuiScreen screen, String title, int y) {
        Attached a = attached(screen);
        a.title = title;
        a.titleY = y;
    }

    /**
     * Empties a 1.12.2 screen's own title field (so it draws nothing where it used to) and returns the text it held.
     * Call it after the screen's {@code initGui}, which sets the field.
     *
     * @return the title, or null when the screen has none of these fields
     */
    @Nullable
    public static String takeTitle(GuiScreen screen, Class<?> owner, String... fieldNames) {
        try {
            Field f = ReflectionHelper.findField(owner, fieldNames);
            Object v = f.get(screen);
            f.set(screen, "");
            return v instanceof String ? (String) v : null;
        } catch (Exception e) {
            BackportCore.LOGGER.warn("Header and footer: {} has no title field {}", owner.getName(), java.util.Arrays.toString(fieldNames));
            return null;
        }
    }

    /**
     * 26.3's {@code OptionsList} out of a screen's own buttons: they leave the screen's button list and become rows of a
     * list between the header and the footer, which scrolls when they do not fit. A row holds two 150-wide buttons, or
     * one wider one centered; the buttons keep the screen's order (top to bottom, left to right) and their own
     * {@code x} only where they are wider than a column. A click on a row's button acts as the screen's own click would:
     * Forge's {@code ActionPerformedEvent}s, then the screen's {@code actionPerformed}.
     */
    public static void attachRows(GuiScreen screen, List<GuiButton> buttonList, List<GuiButton> rows) {
        List<GuiButton> ordered = new ArrayList<GuiButton>(rows);
        Collections.sort(ordered, (a, b) -> a.y != b.y ? Integer.compare(a.y, b.y) : Integer.compare(a.x, b.x));
        buttonList.removeAll(ordered);
        int left = screen.width / 2 - 155;
        List<GuiOptionsRowList.Row> out = new ArrayList<GuiOptionsRowList.Row>();
        for (int i = 0; i < ordered.size(); ) {
            GuiButton a = ordered.get(i);
            GuiButton b = i + 1 < ordered.size() ? ordered.get(i + 1) : null;
            if (a.width > 150) {
                a.x = screen.width / 2 - a.width / 2;
                out.add(new ScreenRow(screen, a, null));
                i++;
            } else if (b != null && b.width <= 150 && b.y == a.y) {
                a.x = left;
                b.x = left + 160;
                out.add(new ScreenRow(screen, a, b));
                i += 2;
            } else {
                a.x = left;
                out.add(new ScreenRow(screen, a, null));
                i++;
            }
        }
        Attached at = attached(screen);
        at.rows = ordered;
        at.list = new OptionRowList(Minecraft.getMinecraft(), screen.width, screen.height, HEADER, screen.height - FOOTER, ROW, out);
    }

    /** @return the buttons in the attached list of {@code screen}, or null when it has none (for tooltips) */
    @Nullable
    public static List<GuiButton> rowButtons(GuiScreen screen) {
        Attached a = current;
        return a != null && a.screen == screen && a.list != null ? a.rows : null;
    }

    /** @return the list attached to {@code screen}, or null */
    @Nullable
    public static OptionRowList list(GuiScreen screen) {
        Attached a = current;
        return a != null && a.screen == screen ? a.list : null;
    }

    /** Lets go of {@code screen}: it is laid out again from the start, or closed. */
    public static void detach(GuiScreen screen) {
        if (current != null && current.screen == screen) {
            current = null;
        }
    }

    private static Attached attached(GuiScreen screen) {
        if (current == null || current.screen != screen) {
            current = new Attached(screen);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    static List<GuiButton> buttonList(GuiScreen screen) throws IllegalAccessException {
        if (buttonListField == null) {
            buttonListField = ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n");
        }
        return (List<GuiButton>) buttonListField.get(screen);
    }

    /** What 1.12.2's {@code GuiScreen.mouseClicked} does for one button, for a button that sits in a list row. */
    static void click(GuiScreen screen, GuiButton button) {
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (button instanceof OptionWidgets.Action) {
                button.playPressSound(mc.getSoundHandler());
                ((OptionWidgets.Action) button).action.run();
                return;
            }
            List<GuiButton> list = buttonList(screen);
            GuiScreenEvent.ActionPerformedEvent.Pre pre = new GuiScreenEvent.ActionPerformedEvent.Pre(screen, button, list);
            if (MinecraftForge.EVENT_BUS.post(pre)) {
                return;
            }
            GuiButton b = pre.getButton();
            if (selectedButtonField == null) {
                selectedButtonField = ReflectionHelper.findField(GuiScreen.class, "selectedButton", "field_146290_a");
                actionPerformed = ReflectionHelper.findMethod(GuiScreen.class, "actionPerformed", "func_146284_a", GuiButton.class);
            }
            selectedButtonField.set(screen, b);
            b.playPressSound(mc.getSoundHandler());
            actionPerformed.invoke(screen, b);
            if (screen == mc.currentScreen) {
                MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.ActionPerformedEvent.Post(screen, pre.getButton(), list));
            }
        } catch (Exception e) {
            BackportCore.LOGGER.error("Header and footer: a list button of {} failed", screen.getClass().getName(), e);
        }
    }

    /** One screen's header, footer and list. */
    private static final class Attached {
        final GuiScreen screen;
        @Nullable
        String title;
        int titleY = titleY(HEADER);
        GuiButton[] footer = new GuiButton[0];
        @Nullable
        OptionRowList list;
        List<GuiButton> rows = Collections.emptyList();

        Attached(GuiScreen screen) {
            this.screen = screen;
        }
    }

    /** A list row of a screen's own buttons, which a click passes to the screen ({@link #click}). */
    static final class ScreenRow extends GuiOptionsRowList.Row {
        private final GuiScreen screen;
        private final GuiButton[] buttons;

        ScreenRow(GuiScreen screen, GuiButton a, @Nullable GuiButton b) {
            super(a, b);
            this.screen = screen;
            this.buttons = b == null ? new GuiButton[] {a} : new GuiButton[] {a, b};
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
            if (mouseEvent != 0) {
                return false;
            }
            Minecraft mc = Minecraft.getMinecraft();
            for (GuiButton b : this.buttons) {
                if (b.mousePressed(mc, mouseX, mouseY)) {
                    click(this.screen, b);
                    return true;
                }
            }
            return false;
        }
    }

    /** Forge event handlers; public for the event bus. */
    public static final class Events {

        @SubscribeEvent
        public void onInit(GuiScreenEvent.InitGuiEvent.Pre event) {
            detach(event.getGui());
        }

        /** Before the tooltips (which are drawn at LOW): the list, the footer again over a 1.12.2 list's dirt, the title. */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
            Attached a = current;
            GuiScreen gui = event.getGui();
            if (a == null || a.screen != gui) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            if (a.list != null) {
                a.list.drawScreen(event.getMouseX(), event.getMouseY(), event.getRenderPartialTicks());
                for (GuiButton b : a.footer) {
                    b.drawButton(mc, event.getMouseX(), event.getMouseY(), event.getRenderPartialTicks());
                }
            }
            if (a.title != null) {
                gui.drawCenteredString(mc.fontRenderer, a.title, gui.width / 2, a.titleY, 0xFFFFFF);
            }
            frames++;
        }

        @SubscribeEvent
        public void onMouse(GuiScreenEvent.MouseInputEvent.Post event) {
            Attached a = current;
            if (a == null || a.screen != event.getGui() || a.list == null) {
                return;
            }
            GuiScreen gui = event.getGui();
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen != gui) {
                return;
            }
            int x = Mouse.getEventX() * gui.width / mc.displayWidth;
            int y = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;
            int button = Mouse.getEventButton();
            if (button != -1) {
                if (Mouse.getEventButtonState()) {
                    a.list.mouseClicked(x, y, button);
                } else {
                    a.list.mouseReleased(x, y, button);
                }
            }
            if (mc.currentScreen == gui && current == a) {
                a.list.handleMouseInput();
            }
        }
    }
}

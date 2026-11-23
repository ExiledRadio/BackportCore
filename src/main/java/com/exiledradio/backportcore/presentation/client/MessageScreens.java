package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiMemoryErrorScreen;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenWorking;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 1.12.2's own question and message screens as the newest installed version lays them out ({@link VanillaRevisions}):
 * the screen object stays 1.12.2's (its callback, ids and button delay are untouched, so every mod's {@code GuiYesNo}
 * works as before); Core moves its buttons on {@code InitGuiEvent.Post} and draws its text in place of its
 * {@code drawScreen}. Only these exact classes, never a subclass, which draws its own.
 * <ul>
 *   <li>{@code GuiYesNo}: {@link VanillaRevisions.ConfirmLayout}, 1.17's, 1.19.1's or 1.21.6's.</li>
 *   <li>{@code GuiScreenWorking}: 1.14's second line only once there is a stage and progress.</li>
 *   <li>{@code GuiDisconnected}: 1.20's and 1.21's centered column, the reason white.</li>
 *   <li>{@code GuiMemoryErrorScreen}: 1.20.5's header, framed message and footer, worded by the backport's
 *       {@code outOfMemory.title} and {@code outOfMemory.message} (1.12.2's English when none has them).</li>
 * </ul>
 * "Saving world" ({@link VanillaRevisions.MessageScreen}) is drawn by the loading screen, {@link WorldLoadingScreen}.
 */
@SideOnly(Side.CLIENT)
public final class MessageScreens {

    static final int LINE = 9;

    /** What Core laid out for a screen at its last init: the message lines. */
    private static final Map<GuiScreen, List<String>> LINES = Collections.synchronizedMap(new WeakHashMap<GuiScreen, List<String>>());

    /** Frames Core drew, by screen, for the client self-test. */
    public static volatile int confirmFrames;
    public static volatile int disconnectedFrames;
    public static volatile int memoryFrames;
    public static volatile int workingFrames;

    // ---- init: the buttons

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.getGui();
        Class<?> c = gui.getClass();
        try {
            if (c == GuiYesNo.class) {
                initConfirm((GuiYesNo) gui, event.getButtonList());
            } else if (c == GuiDisconnected.class) {
                initDisconnected((GuiDisconnected) gui, event.getButtonList());
            } else if (c == GuiMemoryErrorScreen.class && VanillaRevisions.outOfMemoryLayout()) {
                initMemory(gui, event.getButtonList());
            }
        } catch (RuntimeException e) {
            LINES.remove(gui);
            BackportCore.LOGGER.error("Presentation: couldn't lay out {}", c.getSimpleName(), e);
        }
    }

    private static void initConfirm(GuiYesNo gui, List<GuiButton> buttons) {
        VanillaRevisions.ConfirmLayout layout = VanillaRevisions.confirmLayout();
        if (layout == VanillaRevisions.ConfirmLayout.CLASSIC) {
            LINES.remove(gui);
            return;
        }
        String message = ObfuscationReflectionHelper.getPrivateValue(GuiYesNo.class, gui, "field_146354_r");
        List<String> lines = wrap(gui.mc.fontRenderer, message, gui.width - 50);
        if (layout == VanillaRevisions.ConfirmLayout.COLUMN_1_21_6 && lines.size() > 15) {
            lines = new ArrayList<String>(lines.subList(0, 15));
        }
        LINES.put(gui, lines);
        int[] p = confirmPositions(layout, gui.mc.fontRenderer, gui.width, gui.height, title(gui), lines);
        for (GuiButton b : buttons) {
            if (b.id == 0 || b.id == 1) {
                b.x = b.id == 0 ? p[4] : p[5];
                b.y = p[6];
                b.width = 150;
                b.height = 20;
            }
        }
    }

    /**
     * Where a question's pieces go: {@code {titleX, titleY, messageCenterX, messageY, yesX, noX, buttonsY}}. The title
     * x is its left edge for {@code COLUMN_1_21_6} (a {@code StringWidget}), its center otherwise.
     */
    public static int[] confirmPositions(VanillaRevisions.ConfirmLayout layout, FontRenderer font, int width, int height,
                                         String title, List<String> lines) {
        int messageHeight = lines.size() * LINE;
        switch (layout) {
            case COLUMN_1_21_6: {
                int titleWidth = font.getStringWidth(title);
                int messageWidth = 0;
                for (String s : lines) {
                    messageWidth = Math.max(messageWidth, font.getStringWidth(s));
                }
                int rowWidth = 150 + 4 + 150;
                int layoutWidth = Math.max(rowWidth, Math.max(titleWidth, messageWidth));
                int layoutHeight = LINE + 8 + messageHeight + 8 + 16 + 20;
                int left = align(width, layoutWidth);
                int top = align(height, layoutHeight);
                int messageX = left + align(layoutWidth, messageWidth);
                int row = left + align(layoutWidth, rowWidth);
                int buttons = top + LINE + 8 + messageHeight + 8 + 16;
                return new int[] {left + align(layoutWidth, titleWidth), top, messageX + messageWidth / 2, top + LINE + 8, row, row + 154, buttons};
            }
            case CENTERED_1_19_1: {
                int titleTop = MathHelper.clamp((height - messageHeight) / 2 - 20 - LINE, 10, 80);
                int messageTop = titleTop + 20;
                int y = MathHelper.clamp(messageTop + messageHeight + 20, height / 6 + 96, height - 24);
                return new int[] {width / 2, titleTop, width / 2, messageTop, width / 2 - 155, width / 2 + 5, y};
            }
            default: {
                int y = MathHelper.clamp(90 + messageHeight + 12, height / 6 + 96, height - 24);
                return new int[] {width / 2, 70, width / 2, 90, width / 2 - 155, width / 2 + 5, y};
            }
        }
    }

    private static void initDisconnected(GuiDisconnected gui, List<GuiButton> buttons) {
        VanillaRevisions.DisconnectedLayout layout = VanillaRevisions.disconnectedLayout();
        if (layout == VanillaRevisions.DisconnectedLayout.CLASSIC) {
            LINES.remove(gui);
            return;
        }
        List<String> lines = ObfuscationReflectionHelper.getPrivateValue(GuiDisconnected.class, gui, "field_146305_g");
        lines = lines == null ? new ArrayList<String>() : new ArrayList<String>(lines);
        if (lines.size() == 1 && lines.get(0).isEmpty()) {
            lines.clear();
        }
        LINES.put(gui, lines);
        int[] p = disconnectedPositions(layout, gui.width, gui.height, lines.size());
        for (GuiButton b : buttons) {
            if (b.id == 0) {
                b.width = p[3];
                b.x = align(gui.width, p[3]);
                b.y = p[2];
            }
        }
    }

    /** {@code {titleY, messageY, buttonY, buttonWidth}} of the disconnected screen. */
    public static int[] disconnectedPositions(VanillaRevisions.DisconnectedLayout layout, int width, int height, int lines) {
        int messageHeight = lines * LINE;
        boolean linear = layout == VanillaRevisions.DisconnectedLayout.LINEAR_1_21;
        int buttonPadding = linear ? 2 : 10;
        int layoutHeight = (LINE + 20) + (messageHeight + 20) + (20 + 2 * buttonPadding);
        int top = align(height, layoutHeight);
        return new int[] {top + 10, top + LINE + 20 + 10, top + LINE + 20 + messageHeight + 20 + buttonPadding, linear ? 200 : 150};
    }

    private static void initMemory(GuiScreen gui, List<GuiButton> buttons) {
        int row = align(gui.width, 150 + 8 + 150);
        int y = gui.height - 33 + align(33, 20);
        for (GuiButton b : buttons) {
            if (b.id == 0 || b.id == 1) {
                b.x = b.id == 0 ? row : row + 158;
                b.y = y;
                b.width = 150;
            }
        }
        LINES.put(gui, wrap(gui.mc.fontRenderer, memoryMessage(), VanillaRevisions.messageScreen() == VanillaRevisions.MessageScreen.BOX_1_21_11 ? 292 : 300));
    }

    // ---- drawing

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (event.isCanceled()) {
            return;
        }
        GuiScreen gui = event.getGui();
        Class<?> c = gui.getClass();
        try {
            if (c == GuiScreenWorking.class) {
                if (VanillaRevisions.progressLineWhenStarted()) {
                    event.setCanceled(true);
                    drawWorking((GuiScreenWorking) gui, event.getMouseX(), event.getMouseY(), event.getRenderPartialTicks());
                }
                return;
            }
            List<String> lines = LINES.get(gui);
            if (lines == null) {
                return;
            }
            if (c == GuiYesNo.class) {
                event.setCanceled(true);
                drawConfirm((GuiYesNo) gui, lines, event.getMouseX(), event.getMouseY(), event.getRenderPartialTicks());
            } else if (c == GuiDisconnected.class) {
                event.setCanceled(true);
                drawDisconnected((GuiDisconnected) gui, lines, event.getMouseX(), event.getMouseY(), event.getRenderPartialTicks());
            } else if (c == GuiMemoryErrorScreen.class) {
                event.setCanceled(true);
                drawMemory(gui, lines, event.getMouseX(), event.getMouseY(), event.getRenderPartialTicks());
            }
        } catch (RuntimeException e) {
            LINES.remove(gui);
            BackportCore.LOGGER.error("Presentation: couldn't draw {}", c.getSimpleName(), e);
        }
    }

    private static void drawConfirm(GuiYesNo gui, List<String> lines, int mouseX, int mouseY, float partialTicks) {
        VanillaRevisions.ConfirmLayout layout = VanillaRevisions.confirmLayout();
        FontRenderer font = gui.mc.fontRenderer;
        String title = title(gui);
        int[] p = confirmPositions(layout, font, gui.width, gui.height, title, lines);
        gui.drawDefaultBackground();
        if (layout == VanillaRevisions.ConfirmLayout.COLUMN_1_21_6) {
            font.drawStringWithShadow(title, p[0], p[1], 0xFFFFFF);
        } else {
            gui.drawCenteredString(font, title, p[0], p[1], 0xFFFFFF);
        }
        drawCenteredLines(gui, font, lines, p[2], p[3]);
        drawWidgets(gui, mouseX, mouseY, partialTicks);
        confirmFrames++;
    }

    private static void drawDisconnected(GuiDisconnected gui, List<String> lines, int mouseX, int mouseY, float partialTicks) {
        FontRenderer font = gui.mc.fontRenderer;
        int[] p = disconnectedPositions(VanillaRevisions.disconnectedLayout(), gui.width, gui.height, lines.size());
        String reason = ObfuscationReflectionHelper.getPrivateValue(GuiDisconnected.class, gui, "field_146306_a");
        gui.drawDefaultBackground();
        int w = font.getStringWidth(reason);
        font.drawStringWithShadow(reason, align(gui.width, w), p[0], 0xFFFFFF);
        drawCenteredLines(gui, font, lines, messageCenter(font, gui.width, lines), p[1]);
        drawWidgets(gui, mouseX, mouseY, partialTicks);
        disconnectedFrames++;
    }

    private static void drawMemory(GuiScreen gui, List<String> lines, int mouseX, int mouseY, float partialTicks) {
        FontRenderer font = gui.mc.fontRenderer;
        gui.drawDefaultBackground();
        String title = I18n.hasKey("outOfMemory.title") ? I18n.format("outOfMemory.title") : "Out of memory!";
        int tw = font.getStringWidth(title);
        font.drawStringWithShadow(title, align(gui.width, tw), align(33, LINE), 0xFFFFFF);
        int contents = gui.height - 66;
        if (VanillaRevisions.messageScreen() == VanillaRevisions.MessageScreen.BOX_1_21_11) {
            // 26.3: FocusableTextWidget.builder(...).maxWidth(300): 300 wide, the text wrapped to 292, 4 inside.
            int h = lines.size() * LINE + 8;
            int x = align(gui.width, 300);
            int y = 33 + align(contents, h);
            box(x, y, 300, h);
            drawCenteredLines(gui, font, lines, x + 150, y + 4);
        } else {
            // 1.20.5: the widget is the text (wrapped to 300), the box 4 outside it.
            int w = maxWidth(font, lines);
            int h = lines.size() * LINE;
            int x = align(gui.width, w);
            int y = 33 + align(contents, h);
            box(x - 4, y - 4, w + 8, h + 8);
            drawCenteredLines(gui, font, lines, x + w / 2, y);
        }
        drawWidgets(gui, mouseX, mouseY, partialTicks);
        memoryFrames++;
    }

    private static void drawWorking(GuiScreenWorking gui, int mouseX, int mouseY, float partialTicks) {
        boolean done = ObfuscationReflectionHelper.getPrivateValue(GuiScreenWorking.class, gui, "field_146592_h");
        if (done) {
            if (!gui.mc.isConnectedToRealms()) {
                gui.mc.displayGuiScreen(null);
            }
            return;
        }
        String title = ObfuscationReflectionHelper.getPrivateValue(GuiScreenWorking.class, gui, "field_146591_a");
        String stage = ObfuscationReflectionHelper.getPrivateValue(GuiScreenWorking.class, gui, "field_146589_f");
        int progress = ObfuscationReflectionHelper.<Integer, GuiScreenWorking>getPrivateValue(GuiScreenWorking.class, gui, "field_146590_g");
        gui.drawDefaultBackground();
        gui.drawCenteredString(gui.mc.fontRenderer, title, gui.width / 2, 70, 0xFFFFFF);
        if (stage != null && !stage.isEmpty() && progress != 0) {
            gui.drawCenteredString(gui.mc.fontRenderer, stage + " " + progress + "%", gui.width / 2, 90, 0xFFFFFF);
        }
        drawWidgets(gui, mouseX, mouseY, partialTicks);
        workingFrames++;
    }

    // ---- "Saving world" and the box both message screens draw

    /**
     * One {@code GenericMessageScreen} frame's text: {@link VanillaRevisions.MessageScreen#DIRT_1_13} centered at 70
     * (the caller draws the dirt), the boxes centered on the screen. The background is the caller's.
     */
    public static void drawMessage(FontRenderer font, String text, int width, int height, VanillaRevisions.MessageScreen revision) {
        int w = font.getStringWidth(text);
        switch (revision) {
            case BOX_1_21_11: {
                // The widget is the text plus 12 on each side, placed at (width / 2 - its width / 2, height / 2 - 4).
                int bw = w + 24;
                int bh = LINE + 24;
                int x = width / 2 - bw / 2;
                int y = height / 2 - LINE / 2;
                box(x, y, bw, bh);
                font.drawStringWithShadow(text, x + bw / 2 - w / 2, y + 12, 0xFFFFFF);
                break;
            }
            case BOX_1_20_5: {
                // The widget is the text at (width / 2 - its width / 2, height / 2 - 4), the box 12 outside it.
                int x = width / 2 - w / 2;
                int y = height / 2 - LINE / 2;
                box(x - 12, y - 12, w + 24, LINE + 24);
                font.drawStringWithShadow(text, x, y, 0xFFFFFF);
                break;
            }
            default:
                font.drawStringWithShadow(text, width / 2 - w / 2, 70, 0xFFFFFF);
                break;
        }
    }

    /**
     * {@code FocusableTextWidget}'s always-framed box: black from one pixel in on the left, then a 1-pixel
     * {@code 0xA0A0A0} frame ({@code GuiGraphics.renderOutline}) over the whole rectangle.
     */
    static void box(int x, int y, int w, int h) {
        Gui.drawRect(x + 1, y, x + w, y + h, 0xFF000000);
        int c = 0xFFA0A0A0;
        Gui.drawRect(x, y, x + w, y + 1, c);
        Gui.drawRect(x, y + h - 1, x + w, y + h, c);
        Gui.drawRect(x, y + 1, x + 1, y + h - 1, c);
        Gui.drawRect(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    // ---- helpers

    /** The text a {@code MultiLineTextWidget} shows: no line at all for an empty message (1.12.2 wraps "" to one). */
    static List<String> wrap(FontRenderer font, String text, int width) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(font.listFormattedStringToWidth(text.replace("\\n", "\n"), width));
    }

    private static String title(GuiYesNo gui) {
        String t = ObfuscationReflectionHelper.getPrivateValue(GuiYesNo.class, gui, "field_146351_f");
        return t == null ? "" : t;
    }

    /** 26.3's English when no backport words it (1.12.2 draws its English lines without a key). */
    static String memoryMessage() {
        if (I18n.hasKey("outOfMemory.message")) {
            return I18n.format("outOfMemory.message");
        }
        return "Minecraft has run out of memory.\n\nThis could be caused by a bug in the game or by the Java Virtual Machine "
                + "not being allocated enough memory.\n\nTo prevent level corruption, the current game has quit. We've tried to "
                + "free up enough memory to let you go back to the main menu and back to playing, but this may not have "
                + "worked.\n\nPlease restart the game if you see this message again.";
    }

    /** Where a layout child of {@code size} starts in {@code space}: {@code (int) lerp(0.5, 0, space - size)}. */
    static int align(int space, int size) {
        return (int) ((space - size) * 0.5F);
    }

    private static int maxWidth(FontRenderer font, List<String> lines) {
        int w = 0;
        for (String s : lines) {
            w = Math.max(w, font.getStringWidth(s));
        }
        return w;
    }

    private static int messageCenter(FontRenderer font, int width, List<String> lines) {
        int w = maxWidth(font, lines);
        return align(width, w) + w / 2;
    }

    private static void drawCenteredLines(GuiScreen gui, FontRenderer font, List<String> lines, int centerX, int top) {
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            font.drawStringWithShadow(s, centerX - font.getStringWidth(s) / 2, top + i * LINE, 0xFFFFFF);
        }
    }

    /** {@code GuiScreen.drawScreen}'s part: the buttons and labels. */
    private static void drawWidgets(GuiScreen gui, int mouseX, int mouseY, float partialTicks) {
        List<GuiButton> buttons = ObfuscationReflectionHelper.getPrivateValue(GuiScreen.class, gui, "field_146292_n");
        for (GuiButton b : buttons) {
            b.drawButton(gui.mc, mouseX, mouseY, partialTicks);
        }
        List<net.minecraft.client.gui.GuiLabel> labels = ObfuscationReflectionHelper.getPrivateValue(GuiScreen.class, gui, "field_146293_o");
        for (net.minecraft.client.gui.GuiLabel l : labels) {
            l.drawLabel(gui.mc, mouseX, mouseY);
        }
    }

    /** For the client self-test: the lines Core laid out for a screen, or null when it left the screen alone. */
    public static List<String> lines(GuiScreen gui) {
        return LINES.get(gui);
    }

}

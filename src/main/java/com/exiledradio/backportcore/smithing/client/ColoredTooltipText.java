package com.exiledradio.backportcore.smithing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tooltip lines in any color, as 1.20's text styles allow (trim materials are {@code #B4684D} copper,
 * {@code #416E97} lapis...). 1.12.2's font draws only the sixteen chat colors, so a line is added as a placeholder of
 * spaces as wide as the text, led by an invisible marker of color codes, and the text is drawn over it in its own
 * color once the tooltip is drawn ({@code RenderTooltipEvent.PostText}).
 */
@SideOnly(Side.CLIENT)
public final class ColoredTooltipText {

    /** Zero-width color codes no one writes on purpose; the entry's number follows as more of them. */
    private static final String MARK = "§0§1§0§1";
    private static final String END = "§r";

    private static final Map<String, Integer> IDS = new HashMap<String, Integer>();
    private static final List<String> TEXTS = new ArrayList<String>();
    private static final List<Integer> COLORS = new ArrayList<Integer>();

    private static boolean registered;

    private ColoredTooltipText() {
    }

    /** @return a tooltip line that shows as {@code text} drawn in {@code rgb} (0xRRGGBB) */
    public static synchronized String line(String text, int rgb) {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.register(new ColoredTooltipText());
        }
        String key = rgb + "|" + text;
        Integer id = IDS.get(key);
        if (id == null) {
            id = TEXTS.size();
            IDS.put(key, id);
            TEXTS.add(text);
            COLORS.add(rgb);
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int width = font.getStringWidth(text);
        int space = Math.max(1, font.getCharWidth(' '));
        StringBuilder b = new StringBuilder(MARK);
        for (char c : Integer.toHexString(id).toCharArray()) {
            b.append('§').append(c);
        }
        b.append(END);
        for (int w = 0; w < width; w += space) {
            b.append(' ');
        }
        return b.toString();
    }

    @SubscribeEvent
    public void draw(RenderTooltipEvent.PostText event) {
        List<String> lines = event.getLines();
        int y = event.getY();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            // GuiScreen.renderToolTip puts a gray code ahead of every line after the name.
            int mark = line.indexOf(MARK);
            if (mark >= 0) {
                int end = line.indexOf(END, mark + MARK.length());
                if (end > 0) {
                    int id = decode(line.substring(mark + MARK.length(), end));
                    synchronized (ColoredTooltipText.class) {
                        if (id >= 0 && id < TEXTS.size()) {
                            event.getFontRenderer().drawStringWithShadow(TEXTS.get(id), event.getX(), y, COLORS.get(id));
                        }
                    }
                }
            }
            // GuiUtils.drawHoveringText: 2 pixels more under the title line, 10 per line.
            y += i == 0 ? 12 : 10;
        }
    }

    private static int decode(String codes) {
        StringBuilder hex = new StringBuilder();
        for (int i = 1; i < codes.length(); i += 2) {
            hex.append(codes.charAt(i));
        }
        try {
            return Integer.parseInt(hex.toString(), 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.option.PresentationOption;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
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
 * 1.16's option tooltips (1.16-pre1): while the mouse is over one of Core's option buttons that has a tooltip
 * ({@link PresentationOption#tooltip}), its text shows beside the mouse, lines broken at 200 pixels, as 1.16.5's
 * {@code OptionsList} tooltips do. Every screen Core puts options on, Video Settings' list included.
 */
@SideOnly(Side.CLIENT)
public final class OptionTooltips {

    private static Field buttonList;
    /** For the client self-test: the last tooltip drawn. */
    public static volatile String lastShown;

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen gui = event.getGui();
        GuiButton hovered = hovered(gui);
        if (hovered == null) {
            return;
        }
        String key = ((OptionWidgets.OptionButton) hovered).option().tooltipKey();
        if (key == null) {
            return;
        }
        List<String> lines = lines(I18n.format(key));
        gui.drawHoveringText(lines, event.getMouseX(), event.getMouseY());
        lastShown = key;
    }

    /** @return the tooltip's lines: its own line breaks, then wrapped at 200 pixels */
    public static List<String> lines(String text) {
        List<String> out = new ArrayList<String>();
        for (String part : text.replace("\\n", "\n").split("\n")) {
            out.addAll(Minecraft.getMinecraft().fontRenderer.listFormattedStringToWidth(part, 200));
        }
        return out;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static GuiButton hovered(GuiScreen gui) {
        try {
            if (buttonList == null) {
                buttonList = ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n");
            }
            for (GuiButton b : (List<GuiButton>) buttonList.get(gui)) {
                if (b instanceof OptionWidgets.OptionButton && b.visible && b.isMouseOver()) {
                    return b;
                }
            }
            List<GuiButton> rows = OptionsInjector.rowButtons(gui);
            if (rows != null) {
                for (GuiButton b : rows) {
                    if (b instanceof OptionWidgets.OptionButton && b.visible && b.isMouseOver()) {
                        return b;
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}

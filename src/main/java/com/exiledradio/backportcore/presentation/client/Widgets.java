package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Widgets as 26.3's sprites draw them, where Core draws the widget itself. Since Core 1.4.5.
 * <ul>
 *   <li>Check box (1.20.2, {@code Checkbox}): {@code widget/checkbox}, {@code _selected}, {@code _highlighted},
 *       {@code _selected_highlighted} (20 by 20, supplied by a backport) stretched to a 17 square ({@code font height + 8});
 *       highlighted while focused, which on a 1.12.2 screen is "clicked last"; the label white, 4 to the right of the box
 *       and centered on it.</li>
 *   <li>Slider handles and text fields are 1.12.2's own widgets: patches P62 and P61 (1.14's hovered handle, 1.16's
 *       white focus frame). 26.3's {@code slider}, {@code slider_handle} and {@code text_field} sprites are those
 *       textures pixel for pixel (the family's {@code widgets.png} rows 46, 66 and 86; the grey {@code 0xA0A0A0} frame
 *       on black), as are {@code scroller} and {@code scroller_background} 1.12.2's list scroll bar.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class Widgets {

    static final String CHECKBOX = "textures/gui/sprites/widget/checkbox.png";
    static final String CHECKBOX_SELECTED = "textures/gui/sprites/widget/checkbox_selected.png";
    static final String CHECKBOX_HIGHLIGHTED = "textures/gui/sprites/widget/checkbox_highlighted.png";
    static final String CHECKBOX_SELECTED_HIGHLIGHTED = "textures/gui/sprites/widget/checkbox_selected_highlighted.png";

    /** 26.3's {@code Checkbox.getBoxSize}. */
    public static final int BOX = 9 + 8;

    /** For the client self-test. */
    public static volatile int checkboxFrames;

    private Widgets() {
    }

    /** @return true when 1.20.2's check box is drawn: the version, and every sprite supplied */
    public static boolean spriteCheckbox() {
        return VanillaRevisions.checkboxSprites() && Presentation.asset(CHECKBOX) != null && Presentation.asset(CHECKBOX_SELECTED) != null
                && Presentation.asset(CHECKBOX_HIGHLIGHTED) != null && Presentation.asset(CHECKBOX_SELECTED_HIGHLIGHTED) != null;
    }

    /**
     * Draws {@code b} as 1.20.2's check box (and makes it 17 high, as 26.3's is with a one-line label).
     *
     * @return false when the sprites are not there (the caller draws its own)
     */
    static boolean drawCheckbox(GuiButton b, Minecraft mc, int mouseX, int mouseY, boolean selected, boolean focused) {
        if (!spriteCheckbox()) {
            return false;
        }
        b.height = BOX;
        b.width = BOX + 4 + mc.fontRenderer.getStringWidth(b.displayString);
        String sprite = selected ? (focused ? CHECKBOX_SELECTED_HIGHLIGHTED : CHECKBOX_SELECTED) : (focused ? CHECKBOX_HIGHLIGHTED : CHECKBOX);
        mc.getTextureManager().bindTexture(new ResourceLocation(sprite));
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        Gui.drawScaledCustomSizeModalRect(b.x, b.y, 0.0F, 0.0F, 20, 20, BOX, BOX, 20.0F, 20.0F);
        mc.fontRenderer.drawStringWithShadow(b.displayString, b.x + BOX + 4, b.y + BOX / 2 - 9 / 2, 0xFFFFFF);
        checkboxFrames++;
        return true;
    }
}

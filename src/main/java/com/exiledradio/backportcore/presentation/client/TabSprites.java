package com.exiledradio.backportcore.presentation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The tab buttons' textures. A pack's own (a backport's {@code replaceAsset}, or a resource pack) is drawn as vanilla
 * draws it; without one Core draws the same pixels, as {@link MenuBackground} draws its flat textures' colors.
 *
 * <ul>
 *   <li>1.20.2's sprites {@code widget/tab}, {@code tab_highlighted}, {@code tab_selected}, {@code
 *       tab_selected_highlighted}: 130 by 24, nine-sliced with borders 2, 2, 2, 0. Translucent lines over the blurred
 *       panorama: a black (191) outline, a white (51) inner line, white (255) when hovered; an unselected tab starts 4
 *       lower and is black (219) inside, a selected one is open inside and at its bottom.</li>
 *   <li>1.19.4's {@code textures/gui/tab_button.png} (256 by 256, the four states 24 apart: selected, selected
 *       hovered, unselected, unselected hovered): dirt, a selected tab as light as the screen behind it, an unselected
 *       one darker and 4 lower, with a black outline and light top and left edges, white when hovered.</li>
 * </ul>
 * Since Core 1.4.5.
 */
@SideOnly(Side.CLIENT)
final class TabSprites {

    private static final int OUTLINE = 0xBF000000;
    private static final int LINE = 0x33FFFFFF;
    private static final int HOVER = 0xFFFFFFFF;
    private static final int INSIDE = 0xDB000000;

    private TabSprites() {
    }

    /** 1.20.5's tab (the 1.20.2 sprites). */
    static void modern(Minecraft mc, int x, int y, int w, int h, boolean selected, boolean hovered) {
        String name = "sprites/widget/" + (selected ? hovered ? "tab_selected_highlighted" : "tab_selected" : hovered ? "tab_highlighted" : "tab");
        ResourceLocation texture = MenuBackground.texture(mc, name);
        if (texture != null) {
            nineSlice(mc, texture, x, y, w, h, 0, 130, 24, 130, 24);
            return;
        }
        int line = hovered ? HOVER : LINE;
        int r = x + w;
        if (selected) {
            fill(x, y, r, y + 1, OUTLINE);
            fill(x, y + 1, x + 1, y + 22, OUTLINE);
            fill(r - 1, y + 1, r, y + 22, OUTLINE);
            fill(x + 1, y + 1, r - 1, y + 2, line);
            fill(x + 1, y + 2, x + 2, y + 23, line);
            fill(r - 2, y + 2, r - 1, y + 23, line);
            fill(x, y + 22, x + 1, y + 23, LINE);
            fill(r - 1, y + 22, r, y + 23, LINE);
            fill(x, y + 23, x + 2, y + 24, OUTLINE);
            fill(r - 2, y + 23, r, y + 24, OUTLINE);
        } else {
            fill(x, y + 4, r, y + 5, OUTLINE);
            fill(x, y + 5, x + 1, y + 22, OUTLINE);
            fill(r - 1, y + 5, r, y + 22, OUTLINE);
            fill(x + 1, y + 5, r - 1, y + 6, line);
            fill(x + 1, y + 6, x + 2, y + 21, line);
            fill(r - 2, y + 6, r - 1, y + 21, line);
            fill(x + 2, y + 6, r - 2, y + 21, INSIDE);
            if (hovered) {
                fill(x + 1, y + 21, r - 1, y + 22, HOVER);
            } else {
                fill(x + 1, y + 21, x + 2, y + 22, LINE);
                fill(x + 2, y + 21, r - 2, y + 22, INSIDE);
                fill(r - 2, y + 21, r - 1, y + 22, LINE);
            }
            fill(x, y + 22, r, y + 23, LINE);
            fill(x, y + 23, r, y + 24, OUTLINE);
        }
    }

    /** 1.19.4's dirt tab ({@code tab_button.png}). */
    static void dirt(Minecraft mc, int x, int y, int w, int h, boolean selected, boolean hovered) {
        ResourceLocation texture = MenuBackground.texture(mc, "tab_button");
        if (texture != null) {
            int state = selected ? hovered ? 1 : 0 : hovered ? 3 : 2;
            nineSlice(mc, texture, x, y, w, h, state * 24, 130, 24, 256, 256);
            return;
        }
        int r = x + w;
        int top = selected ? y : y + 4;
        int bottom = selected ? y + 24 : y + 22;
        // The dirt inside: as light as the screen (76) when selected, darker (45) behind.
        if (selected) {
            // Open at the bottom: the last two rows are dirt but for the outline's corners.
            CreateWorldTabs.dirt(mc, x + 1, y + 1, w - 2, 21, 76);
            CreateWorldTabs.dirt(mc, x + 2, y + 22, w - 4, 2, 76);
        } else {
            CreateWorldTabs.dirt(mc, x + 1, top + 1, w - 2, bottom - top - 1, 45);
        }
        int light = hovered ? HOVER : selected ? 0xFF514A43 : 0xFF2A2726;
        int dark = hovered ? HOVER : selected ? 0xFF0B0705 : 0xFF030201;
        fill(x, top, r, top + 1, 0xFF000000);
        int sides = selected ? y + 22 : bottom;
        fill(x, top + 1, x + 1, sides, 0xFF000000);
        fill(r - 1, top + 1, r, sides, 0xFF000000);
        fill(x + 1, top + 1, r - 1, top + 2, light);
        fill(x + 1, top + 2, x + 2, sides, light);
        fill(r - 2, top + 2, r - 1, sides, dark);
        if (!selected && hovered) {
            fill(x + 1, bottom - 1, r - 1, bottom, HOVER);
        }
    }

    private static void fill(int x0, int y0, int x1, int y1, int color) {
        if (x1 > x0 && y1 > y0) {
            Gui.drawRect(x0, y0, x1, y1, color);
        }
    }

    /** A nine-slice with 2-pixel left, top and right borders and none at the bottom (the tabs' only slicing). */
    private static void nineSlice(Minecraft mc, ResourceLocation texture, int x, int y, int w, int h, int v, int spriteW, int spriteH,
                                  int texW, int texH) {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        mc.getTextureManager().bindTexture(texture);
        int b = 2;
        int midW = w - 2 * b;
        int midSprite = spriteW - 2 * b;
        int midH = h - b;
        int midSpriteH = spriteH - b;
        // Top row: corners and the stretched edge.
        Gui.drawScaledCustomSizeModalRect(x, y, 0, v, b, b, b, b, texW, texH);
        Gui.drawScaledCustomSizeModalRect(x + b, y, b, v, midSprite, b, midW, b, texW, texH);
        Gui.drawScaledCustomSizeModalRect(x + w - b, y, spriteW - b, v, b, b, b, b, texW, texH);
        // The rest: the side edges and the middle, stretched down.
        Gui.drawScaledCustomSizeModalRect(x, y + b, 0, v + b, b, midSpriteH, b, midH, texW, texH);
        Gui.drawScaledCustomSizeModalRect(x + b, y + b, b, v + b, midSprite, midSpriteH, midW, midH, texW, texH);
        Gui.drawScaledCustomSizeModalRect(x + w - b, y + b, spriteW - b, v + b, b, midSpriteH, b, midH, texW, texH);
        GlStateManager.disableBlend();
    }
}

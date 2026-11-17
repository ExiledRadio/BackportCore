package com.exiledradio.backportcore.map.client;

import com.exiledradio.backportcore.map.MapIcons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.MapDecoration;

/**
 * Draws a {@link MapIcons} decoration exactly where and how 1.12.2's {@code MapItemRenderer.Instance.render} draws
 * one from its sheet, but from the type's own 8 x 8 sprite (the whole texture). Vanilla binds its sheet again
 * after this returns.
 */
public final class MapIconRenderer implements MapIcons.Renderer {

    public static void init() {
        MapIcons.setRenderer(new MapIconRenderer());
    }

    @Override
    public void draw(ResourceLocation sprite, MapDecoration decoration, int index) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(sprite);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F + decoration.getX() / 2.0F + 64.0F, 0.0F + decoration.getY() / 2.0F + 64.0F, -0.02F);
        GlStateManager.rotate((decoration.getRotation() * 360) / 16.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.scale(4.0F, 4.0F, 3.0F);
        GlStateManager.translate(-0.125F, 0.125F, 0.0F);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        double z = index * -0.001F;
        buffer.pos(-1.0D, 1.0D, z).tex(0.0D, 0.0D).endVertex();
        buffer.pos(1.0D, 1.0D, z).tex(1.0D, 0.0D).endVertex();
        buffer.pos(1.0D, -1.0D, z).tex(1.0D, 1.0D).endVertex();
        buffer.pos(-1.0D, -1.0D, z).tex(0.0D, 1.0D).endVertex();
        tessellator.draw();
        GlStateManager.popMatrix();
    }
}

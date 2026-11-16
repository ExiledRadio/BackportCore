package com.exiledradio.backportcore.smithing.client;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.io.IOUtils;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * 1.20's {@code paletted_permutations}: a grayscale texture whose colors are the key palette's is redrawn in a
 * material's palette, color for color, keeping each pixel's alpha. Colors not in the key stay as they are.
 */
@SideOnly(Side.CLIENT)
final class Palettes {

    /** The key: 1.20's {@code trims/color_palettes/trim_palette}. */
    static final ResourceLocation KEY = new ResourceLocation(BackportCore.MOD_ID, "textures/trims/color_palettes/trim_palette.png");

    private Palettes() {
    }

    static ResourceLocation paletteTexture(ResourceLocation palette) {
        return new ResourceLocation(palette.getNamespace(), "textures/trims/color_palettes/" + palette.getPath() + ".png");
    }

    /** @return the source redrawn in the palette, as ARGB pixels, row by row */
    static BufferedImage permute(IResourceManager manager, ResourceLocation source, ResourceLocation palette) throws IOException {
        BufferedImage image = read(manager, source);
        int[] key = colors(read(manager, KEY));
        int[] to = colors(read(manager, paletteTexture(palette)));
        int n = Math.min(key.length, to.length);
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;
                if (alpha != 0) {
                    int rgb = argb & 0xFFFFFF;
                    for (int i = 0; i < n; i++) {
                        if ((key[i] & 0xFFFFFF) == rgb) {
                            argb = alpha << 24 | to[i] & 0xFFFFFF;
                            break;
                        }
                    }
                }
                out.setRGB(x, y, argb);
            }
        }
        return out;
    }

    private static BufferedImage read(IResourceManager manager, ResourceLocation location) throws IOException {
        IResource resource = null;
        try {
            resource = manager.getResource(location);
            return TextureUtil.readBufferedImage(resource.getInputStream());
        } finally {
            IOUtils.closeQuietly(resource);
        }
    }

    /** A palette's colors, left to right along its top row. */
    private static int[] colors(BufferedImage palette) {
        int[] c = new int[palette.getWidth()];
        for (int x = 0; x < c.length; x++) {
            c[x] = palette.getRGB(x, 0);
        }
        return c;
    }
}

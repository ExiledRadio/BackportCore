package com.exiledradio.backportcore.smithing.client;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.function.Function;

/** One item overlay in one palette, built into the item atlas as it is stitched (1.20's {@code trims/items/*}). */
@SideOnly(Side.CLIENT)
final class PalettedSprite extends TextureAtlasSprite {

    private final ResourceLocation source;
    private final ResourceLocation palette;

    PalettedSprite(String name, ResourceLocation source, ResourceLocation palette) {
        super(name);
        this.source = source;
        this.palette = palette;
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    @Override
    public boolean load(IResourceManager manager, ResourceLocation location, Function<ResourceLocation, TextureAtlasSprite> getter) {
        BufferedImage image;
        try {
            image = Palettes.permute(manager, this.source, this.palette);
        } catch (IOException e) {
            BackportCore.LOGGER.warn("Armor trims: cannot build {} in {}: {}", this.source, this.palette, e.toString());
            image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        }
        int w = image.getWidth(), h = image.getHeight();
        setIconWidth(w);
        setIconHeight(h);
        int[][] frame = new int[Minecraft.getMinecraft().gameSettings.mipmapLevels + 1][];
        frame[0] = new int[w * h];
        image.getRGB(0, 0, w, h, frame[0], 0, w);
        clearFramesTextureData();
        java.util.List<int[][]> frames = new java.util.ArrayList<int[][]>();
        frames.add(frame);
        setFramesTextureData(frames);
        // false: stitch it into the atlas (true would leave it out).
        return false;
    }
}

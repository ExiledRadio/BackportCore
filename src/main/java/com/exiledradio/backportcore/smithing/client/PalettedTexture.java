package com.exiledradio.backportcore.smithing.client;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;

/**
 * One worn trim texture (pattern x layer x palette), built from the grayscale pattern when first bound and again at
 * each resource reload (the texture manager reloads every texture it holds).
 */
@SideOnly(Side.CLIENT)
final class PalettedTexture extends AbstractTexture {

    private final ResourceLocation source;
    private final ResourceLocation palette;

    private PalettedTexture(ResourceLocation source, ResourceLocation palette) {
        this.source = source;
        this.palette = palette;
    }

    /** Registers the texture under {@code id} unless it is already there. */
    static void ensure(ResourceLocation id, ResourceLocation source, ResourceLocation palette) {
        TextureManager textures = Minecraft.getMinecraft().getTextureManager();
        ITextureObject existing = textures.getTexture(id);
        if (existing == null) {
            textures.loadTexture(id, new PalettedTexture(source, palette));
        }
    }

    @Override
    public void loadTexture(IResourceManager manager) throws IOException {
        deleteGlTexture();
        try {
            TextureUtil.uploadTextureImageAllocate(getGlTextureId(), Palettes.permute(manager, this.source, this.palette),
                    false, false);
        } catch (IOException e) {
            BackportCore.LOGGER.warn("Armor trims: cannot build {} in {}: {}", this.source, this.palette, e.toString());
            throw e;
        }
    }
}

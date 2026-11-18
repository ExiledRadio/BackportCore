package com.exiledradio.backportcore.client;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The modern texture pack: every {@link TextureLayers texture layer} that applies at the newest installed version,
 * as one resource pack. Which layers apply is decided at each lookup, not when the pack goes in: the pack goes in at
 * construction, and the backports register their versions in pre-init and init, before the texture atlas is
 * stitched and before FML's resource reload at the end of loading.
 */
@SideOnly(Side.CLIENT)
final class LayeredPack implements IResourcePack {

    private final Set<String> domains = TextureLayers.domains();
    @Nullable
    private GameVersion activeFor;
    private List<TextureLayers.Layer> active;

    int size() {
        int n = 0;
        for (TextureLayers.Layer l : active()) {
            n += l.files().size();
        }
        return n;
    }

    /** @return the layers in use now, oldest first */
    synchronized List<TextureLayers.Layer> active() {
        GameVersion newest = Presentation.newest(com.exiledradio.backportcore.presentation.LookArea.TEXTURES);
        if (!newest.equals(activeFor)) {
            active = TextureLayers.active(newest);
            activeFor = newest;
        }
        return active;
    }

    private static String path(ResourceLocation location) {
        return "assets/" + location.getNamespace() + "/" + location.getPath();
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        String file = path(location);
        TextureLayers.Layer layer = TextureLayers.serving(file, active());
        InputStream in = layer == null ? null : LayeredPack.class.getResourceAsStream(layer.resource(file));
        if (in == null) {
            throw new FileNotFoundException(file);
        }
        return in;
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        return TextureLayers.serving(path(location), active()) != null;
    }

    @Override
    public Set<String> getResourceDomains() {
        return domains;
    }

    @Nullable
    @Override
    public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer serializer, String section) {
        return null;
    }

    @Override
    public BufferedImage getPackImage() throws IOException {
        throw new FileNotFoundException("pack.png");
    }

    @Override
    public String getPackName() {
        return BackportCore.MOD_ID + ":modern";
    }
}

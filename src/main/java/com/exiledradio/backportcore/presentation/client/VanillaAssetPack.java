package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Vanilla's own assets and text as the newest installed version had them, newest wins, whatever order the mods
 * load in ({@link com.exiledradio.backportcore.presentation.Contribution#replaceAsset} and {@link
 * com.exiledradio.backportcore.presentation.Contribution#vanillaText}).
 *
 * <p>A resource pack of the {@code minecraft} domain placed right above vanilla's own default pack (and above
 * Core's texture packs), so a player's own resource packs still win, as they would over vanilla's. Assets are read
 * from the backports' jars through the class loader. Language files are composed: {@code minecraft:lang/<code>.lang}
 * is every installed version's file of that language, oldest version first; 1.12.2's {@code Locale} reads the
 * {@code minecraft} domain before every mod's, and a later line wins, so the newest version's wording stands over
 * vanilla's and over older versions'.
 *
 * <p>Nothing is answered until a version registers something, so with no backport installed every lookup misses.
 */
@SideOnly(Side.CLIENT)
public final class VanillaAssetPack implements IResourcePack {

    static final VanillaAssetPack INSTANCE = new VanillaAssetPack();
    private static final Set<String> DOMAINS = Collections.singleton("minecraft");
    private static boolean installed;

    private VanillaAssetPack() {
    }

    /** Puts the pack into the client's default packs, last (above vanilla's). From Core's construction event. */
    @SuppressWarnings("unchecked")
    public static synchronized void install() {
        if (installed) {
            return;
        }
        try {
            List<IResourcePack> packs = (List<IResourcePack>) ReflectionHelper.findField(Minecraft.class,
                    "defaultResourcePacks", "field_110449_ao").get(Minecraft.getMinecraft());
            packs.add(INSTANCE);
            installed = true;
        } catch (Exception e) {
            BackportCore.LOGGER.error("Presentation: could not add the vanilla asset pack; vanilla's assets and text stay 1.12.2's", e);
        }
    }

    public static boolean isInstalled() {
        return installed;
    }

    @Nullable
    private static String langCode(ResourceLocation location) {
        String p = location.getPath();
        if ("minecraft".equals(location.getNamespace()) && p.startsWith("lang/") && p.endsWith(".lang") && p.indexOf('/', 5) < 0) {
            return p.substring(5, p.length() - 5).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    /** @return the composed language file for this code, or null when no installed version rewords it */
    @Nullable
    public static byte[] composedLang(String code) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean any = false;
        for (ResourceLocation folder : Presentation.vanillaTexts()) {
            byte[] file = read(new ResourceLocation(folder.getNamespace(), folder.getPath() + "/" + code + ".lang"));
            if (file != null) {
                out.write(file, 0, file.length);
                out.write('\n');
                any = true;
            }
        }
        return any ? out.toByteArray() : null;
    }

    @Nullable
    private static byte[] read(ResourceLocation source) {
        String path = "assets/" + source.getNamespace() + "/" + source.getPath();
        try (InputStream in = VanillaAssetPack.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
            }
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
    private static ResourceLocation source(ResourceLocation location) {
        if (!"minecraft".equals(location.getNamespace())) {
            return null;
        }
        return Presentation.asset(location.getPath());
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        String code = langCode(location);
        byte[] bytes;
        if (code != null) {
            bytes = composedLang(code);
        } else {
            ResourceLocation src = source(location);
            bytes = src == null ? null : read(src);
        }
        if (bytes == null) {
            throw new FileNotFoundException(location.toString());
        }
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        String code = langCode(location);
        if (code != null) {
            return composedLang(code) != null;
        }
        ResourceLocation src = source(location);
        return src != null && VanillaAssetPack.class.getClassLoader().getResource("assets/" + src.getNamespace() + "/" + src.getPath()) != null;
    }

    @Override
    public Set<String> getResourceDomains() {
        return DOMAINS;
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
        return BackportCore.MOD_ID + ":vanilla_by_version";
    }

    /**
     * Once every mod has loaded: the game's resource load ran before the backports' pre-init registered their
     * vanilla text and assets, so the languages are read again now (only the languages; 1.12.2 reads them once per
     * load), and any texture Core now serves that was already loaded is read again from its new source.
     */
    public static void afterLoading(Minecraft mc) {
        if (!installed) {
            return;
        }
        if (!Presentation.vanillaTexts().isEmpty()) {
            mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
        }
        int textures = 0;
        for (String path : Presentation.assets().keySet()) {
            ResourceLocation rl = new ResourceLocation(path);
            net.minecraft.client.renderer.texture.ITextureObject tex = mc.getTextureManager().getTexture(rl);
            if (tex instanceof net.minecraft.client.renderer.texture.SimpleTexture) {
                try {
                    ((net.minecraft.client.renderer.texture.SimpleTexture) tex).loadTexture(mc.getResourceManager());
                    textures++;
                } catch (IOException e) {
                    BackportCore.LOGGER.warn("Presentation: could not reload {}", rl, e);
                }
            }
        }
        BackportCore.LOGGER.info("Presentation: vanilla text from {} folder(s) and {} asset(s) in place ({} reloaded textures)",
                Presentation.vanillaTexts().size(), Presentation.assets().size(), textures);
    }

    /** For the self-tests: what the pack serves now, path to source. */
    public static Map<String, ResourceLocation> served() {
        return Presentation.assets();
    }
}

package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1.19.4's High Contrast ({@link Presentation#HIGH_CONTRAST}): vanilla's built-in {@code high_contrast} resource pack,
 * from the newest installed version that has its art ({@link com.exiledradio.backportcore.presentation.Contribution#highContrast}).
 *
 * <p>A pack of the {@code minecraft} domain in the client's default packs, right above Core's {@link VanillaAssetPack},
 * answering only while the option is on. It serves every file of the version's pack under its own path (menu
 * backgrounds, separators, the icon sprites Core draws), and 1.12.2's {@code textures/gui/widgets.png} sheet made from
 * the sheet below it with the pack's sprites put in their 1.12.2 places: {@code button_disabled}, {@code button} and
 * {@code button_highlighted} at y 46, 66 and 86 (200 by 20); the language button (a 20 wide button with
 * {@code icon/language} centered, as 26.3's {@code SpriteIconButton}) at 0, 106 and hovered at 0, 126; the lock buttons
 * at 0 and 20 across, 146, 166 and 186 down. Everything else on the sheet (the hotbar) stays the sheet's below.
 *
 * <p>26.3 puts the pack above the player's packs; 1.12.2's default packs are below them, so here a player's own pack
 * still wins, as over every other vanilla asset. Turning it on or off reloads the textures it touches, not every
 * resource (26.3 reloads the packs).
 */
@SideOnly(Side.CLIENT)
public final class HighContrastPack implements IResourcePack {

    static final HighContrastPack INSTANCE = new HighContrastPack();
    static final String WIDGETS = "textures/gui/widgets.png";
    private static final Set<String> DOMAINS = Collections.singleton("minecraft");
    private static boolean installed;
    /** The composed sheet, made once per pack folder and base sheet. */
    @Nullable
    private static byte[] widgets;
    @Nullable
    private static ResourceLocation widgetsFrom;
    /** Every texture path served since the last reload, to load again when the option goes off. */
    private static final Set<String> SERVED = Collections.synchronizedSet(new HashSet<String>());
    private static Field textureMap;

    /** For the client self-test: sheets composed. */
    public static volatile int composed;

    private HighContrastPack() {
    }

    /** Puts the pack into the client's default packs, last (above {@link VanillaAssetPack}). From Core's construction. */
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
            Presentation.HIGH_CONTRAST.onChange(on -> Minecraft.getMinecraft().addScheduledTask(HighContrastPack::reloadTextures));
        } catch (Exception e) {
            BackportCore.LOGGER.error("Presentation: could not add the High Contrast pack", e);
        }
    }

    /** @return true while the pack answers: the option on and a version's art installed */
    public static boolean active() {
        return Presentation.HIGH_CONTRAST.isShown() && Presentation.HIGH_CONTRAST.get() && Presentation.highContrastPack() != null;
    }

    @Nullable
    private static ResourceLocation source(ResourceLocation location) {
        if (!"minecraft".equals(location.getNamespace()) || !active()) {
            return null;
        }
        ResourceLocation folder = Presentation.highContrastPack();
        return new ResourceLocation(folder.getNamespace(), folder.getPath() + "/" + location.getPath());
    }

    private static boolean exists(ResourceLocation src) {
        return HighContrastPack.class.getClassLoader().getResource("assets/" + src.getNamespace() + "/" + src.getPath()) != null;
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        ResourceLocation src = source(location);
        byte[] bytes = null;
        if (src != null) {
            bytes = WIDGETS.equals(location.getPath()) ? widgets() : read(src);
        }
        if (bytes == null) {
            throw new FileNotFoundException(location.toString());
        }
        SERVED.add(location.getPath());
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        ResourceLocation src = source(location);
        if (src == null) {
            return false;
        }
        if (WIDGETS.equals(location.getPath())) {
            ResourceLocation folder = Presentation.highContrastPack();
            return exists(new ResourceLocation(folder.getNamespace(), folder.getPath() + "/textures/gui/sprites/widget/button.png"));
        }
        return exists(src);
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
        return BackportCore.MOD_ID + ":high_contrast";
    }

    // ---- The sheet ----

    @Nullable
    private static synchronized byte[] widgets() {
        ResourceLocation folder = Presentation.highContrastPack();
        if (folder == null) {
            return null;
        }
        if (widgets != null && folder.equals(widgetsFrom)) {
            return widgets;
        }
        try {
            BufferedImage base = baseSheet();
            BufferedImage sheet = compose(base, sprites(folder));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(sheet, "png", out);
            widgets = out.toByteArray();
            widgetsFrom = folder;
            composed++;
            return widgets;
        } catch (IOException | RuntimeException e) {
            BackportCore.LOGGER.warn("High Contrast: could not make widgets.png ({}); the buttons keep their look", e.toString());
            return null;
        }
    }

    /** The sheet below this pack: the newest installed version's, else 1.12.2's own. */
    private static BufferedImage baseSheet() throws IOException {
        ResourceLocation rl = new ResourceLocation(WIDGETS);
        IResourcePack below = VanillaAssetPack.INSTANCE.resourceExists(rl) ? VanillaAssetPack.INSTANCE
                : Minecraft.getMinecraft().getResourcePackRepository().rprDefaultResourcePack;
        try (InputStream in = below.getInputStream(rl)) {
            return ImageIO.read(in);
        }
    }

    /** The pack's sprites by name ({@code widget/button}), those it has. */
    static Map<String, BufferedImage> sprites(ResourceLocation folder) throws IOException {
        Map<String, BufferedImage> out = new java.util.HashMap<String, BufferedImage>();
        for (String name : new String[] {"widget/button", "widget/button_disabled", "widget/button_highlighted", "icon/language",
                "widget/locked_button", "widget/locked_button_highlighted", "widget/locked_button_disabled", "widget/unlocked_button",
                "widget/unlocked_button_highlighted", "widget/unlocked_button_disabled"}) {
            byte[] b = read(new ResourceLocation(folder.getNamespace(), folder.getPath() + "/textures/gui/sprites/" + name + ".png"));
            if (b != null) {
                out.put(name, ImageIO.read(new ByteArrayInputStream(b)));
            }
        }
        return out;
    }

    /**
     * 1.12.2's sheet with the sprites in their places, at the base sheet's scale (a 512 wide sheet takes each sprite
     * doubled, nearest neighbor). Places with no sprite keep the base's pixels.
     */
    static BufferedImage compose(BufferedImage base, Map<String, BufferedImage> sprites) {
        int scale = Math.max(1, base.getWidth() / 256);
        BufferedImage out = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(base, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        place(g, scale, sprites.get("widget/button_disabled"), 0, 46);
        place(g, scale, sprites.get("widget/button"), 0, 66);
        place(g, scale, sprites.get("widget/button_highlighted"), 0, 86);
        place(g, scale, iconButton(sprites.get("widget/button"), sprites.get("icon/language")), 0, 106);
        place(g, scale, iconButton(sprites.get("widget/button_highlighted"), sprites.get("icon/language")), 0, 126);
        String[] states = {"", "_highlighted", "_disabled"};
        for (int i = 0; i < 3; i++) {
            place(g, scale, sprites.get("widget/locked_button" + states[i]), 0, 146 + 20 * i);
            place(g, scale, sprites.get("widget/unlocked_button" + states[i]), 20, 146 + 20 * i);
        }
        g.dispose();
        return out;
    }

    private static void place(Graphics2D g, int scale, @Nullable BufferedImage sprite, int x, int y) {
        if (sprite == null) {
            return;
        }
        // Clear the place first: the base's pixels must not show through the sprite's transparent ones.
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(x * scale, y * scale, sprite.getWidth() * scale, sprite.getHeight() * scale);
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        g.drawImage(sprite, x * scale, y * scale, sprite.getWidth() * scale, sprite.getHeight() * scale, null);
    }

    /**
     * A 20 by 20 button with an icon: the button sprite's left and right 10 columns (as 1.12.2 draws a button from its
     * two halves; the sprite's border is 3) and the icon centered as 26.3's {@code SpriteIconButton} centers it.
     */
    @Nullable
    static BufferedImage iconButton(@Nullable BufferedImage button, @Nullable BufferedImage icon) {
        if (button == null || icon == null) {
            return null;
        }
        BufferedImage out = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(button.getSubimage(0, 0, 10, 20), 0, 0, null);
        g.drawImage(button.getSubimage(button.getWidth() - 10, 0, 10, 20), 10, 0, null);
        g.drawImage(icon, 10 - icon.getWidth() / 2, 10 - icon.getHeight() / 2, null);
        g.dispose();
        return out;
    }

    // ---- Reloading ----

    /**
     * Loads again every loaded texture this pack serves or served: on toggling the option, and once every mod has loaded
     * (the game's first resource load ran before the backports said which pack they have).
     */
    @SuppressWarnings("unchecked")
    static void reloadTextures() {
        Minecraft mc = Minecraft.getMinecraft();
        synchronized (HighContrastPack.class) {
            widgets = null;
        }
        Set<String> paths = new HashSet<String>(SERVED);
        SERVED.clear();
        int count = 0;
        try {
            if (textureMap == null) {
                textureMap = ReflectionHelper.findField(net.minecraft.client.renderer.texture.TextureManager.class, "mapTextureObjects", "field_110585_a");
            }
            Map<ResourceLocation, ITextureObject> map = (Map<ResourceLocation, ITextureObject>) textureMap.get(mc.getTextureManager());
            for (Map.Entry<ResourceLocation, ITextureObject> e : new ArrayList<Map.Entry<ResourceLocation, ITextureObject>>(map.entrySet())) {
                ResourceLocation rl = e.getKey();
                if (!(e.getValue() instanceof SimpleTexture) || !"minecraft".equals(rl.getNamespace())) {
                    continue;
                }
                if (paths.contains(rl.getPath()) || INSTANCE.resourceExists(rl)) {
                    try {
                        ((SimpleTexture) e.getValue()).loadTexture(mc.getResourceManager());
                    } catch (FileNotFoundException gone) {
                        // Only the pack had it (1.20.5's menu backgrounds where no version supplies them): forget the
                        // texture, so whoever draws it finds it missing again, as before the pack.
                        mc.getTextureManager().deleteTexture(rl);
                    }
                    count++;
                }
            }
        } catch (Exception e) {
            BackportCore.LOGGER.warn("High Contrast: could not reload the textures ({}); they change on the next resource reload", e.toString());
        }
        MenuBackground.invalidate();
        BackportCore.LOGGER.info("High Contrast {}: {} texture(s) reloaded", active() ? "on" : "off", count);
    }

    @Nullable
    private static byte[] read(ResourceLocation source) {
        String path = "assets/" + source.getNamespace() + "/" + source.getPath();
        try (InputStream in = HighContrastPack.class.getClassLoader().getResourceAsStream(path)) {
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
}

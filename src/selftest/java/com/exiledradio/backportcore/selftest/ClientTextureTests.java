package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.client.ModernTextures;
import com.exiledradio.backportcore.client.TextureLayers;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.IntBuffer;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * Dev only, environment variable {@code BACKPORTCORE_TEXTURES=<version>} with {@code runClient -Pselftest}: registers
 * that version as an installed backport's. In a dev client Forge's own resource pack is the merged forgeSrc jar,
 * which holds every vanilla asset and sits above Core's packs (Forge's real jar has none), so the test first moves
 * Core's modern pack above it and reloads. Then it reads the stitched block atlas and a mob
 * texture back from the graphics card and compares them with the texture layer each should come from; then turns
 * the modern textures off and checks the ores are 1.12.2's, turns them back on, logs
 * {@code TEXTURE SELFTEST DONE pass=N fail=M} and closes the game. The other client self-tests do not run.
 */
@SideOnly(Side.CLIENT)
final class ClientTextureTests {

    private static final String[] ORES = {"coal", "iron", "gold", "diamond", "emerald", "lapis", "redstone"};
    private static final String CAT = "textures/entity/cat/siamese.png";

    private int ticks;
    private boolean done;

    @Nullable
    static String version() {
        return System.getenv("BACKPORTCORE_TEXTURES");
    }

    static void register() {
        Presentation.version(version());
        MinecraftForge.EVENT_BUS.register(new ClientTextureTests());
    }

    private ClientTextureTests() {
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || this.done || !(mc.currentScreen instanceof GuiMainMenu) || ++this.ticks < 40) {
            return;
        }
        this.done = true;
        try {
            run(mc);
        } catch (Throwable t) {
            SelfTestRunner.fail("client textures threw", t);
            BackportCore.LOGGER.error("client textures", t);
        }
        BackportCore.LOGGER.info("TEXTURE SELFTEST DONE pass={} fail={}", SelfTestRunner.pass, SelfTestRunner.fail);
        mc.shutdown();
    }

    private static void run(Minecraft mc) throws Exception {
        aboveDevForge(mc);
        String newest = Presentation.newest().toString();
        check("client textures: the pack serves the layers for " + newest,
                ModernTextures.activeLayers().toString().equals(TextureLayers.active(Presentation.newest()).toString()),
                ModernTextures.activeLayers());
        for (String ore : ORES) {
            String file = "assets/minecraft/textures/blocks/" + ore + "_ore.png";
            TextureLayers.Layer layer = TextureLayers.layerFor(file, Presentation.newest());
            BufferedImage want = layerImage(layer, file);
            String diff = compare(atlasSprite(mc, "minecraft:blocks/" + ore + "_ore"), want);
            check("client textures: the atlas's " + ore + " ore is layer " + layer + "'s at " + newest, diff == null, diff);
        }
        TextureLayers.Layer catLayer = TextureLayers.layerFor("assets/minecraft/" + CAT, Presentation.newest());
        mc.getTextureManager().bindTexture(new ResourceLocation(CAT));
        String diff = compare(bound(), layerImage(catLayer, "assets/minecraft/" + CAT));
        check("client textures: the siamese cat is layer " + catLayer + "'s at " + newest, diff == null, diff);

        // The switch: off gives 1.12.2's own textures back, on gives the layers again.
        CoreConfig.textures.modernTextures = false;
        MinecraftForge.EVENT_BUS.post(new ConfigChangedEvent.PostConfigChangedEvent(BackportCore.MOD_ID, null, true, false));
        mc.refreshResources();
        for (String ore : ORES) {
            BufferedImage vanilla;
            try (InputStream in = mc.getResourcePackRepository().rprDefaultResourcePack.getInputStream(
                    new ResourceLocation("textures/blocks/" + ore + "_ore.png"))) {
                vanilla = ImageIO.read(in);
            }
            String d = compare(atlasSprite(mc, "minecraft:blocks/" + ore + "_ore"), vanilla);
            check("client textures: with the switch off the " + ore + " ore is 1.12.2's", d == null, d);
        }
        CoreConfig.textures.modernTextures = true;
        MinecraftForge.EVENT_BUS.post(new ConfigChangedEvent.PostConfigChangedEvent(BackportCore.MOD_ID, null, true, false));
        aboveDevForge(mc);
        String file = "assets/minecraft/textures/blocks/coal_ore.png";
        String d = compare(atlasSprite(mc, "minecraft:blocks/coal_ore"), layerImage(TextureLayers.layerFor(file, Presentation.newest()), file));
        check("client textures: switched back on, the coal ore is the layers' again", d == null, d);
    }

    /** Moves Core's modern pack just above the dev client's Forge pack (see the class comment) and reloads. */
    @SuppressWarnings("unchecked")
    private static void aboveDevForge(Minecraft mc) throws Exception {
        java.util.List<net.minecraft.client.resources.IResourcePack> packs = (java.util.List<net.minecraft.client.resources.IResourcePack>)
                net.minecraftforge.fml.relauncher.ReflectionHelper.findField(Minecraft.class, "defaultResourcePacks", "field_110449_ao").get(mc);
        net.minecraft.client.resources.IResourcePack modern = null;
        int forge = -1;
        for (int i = 0; i < packs.size(); i++) {
            String name = packs.get(i).getPackName();
            if ((BackportCore.MOD_ID + ":modern").equals(name)) {
                modern = packs.get(i);
            } else if (name.endsWith(":Minecraft Forge")) {
                forge = i;
            }
        }
        check("client textures: the modern pack and the dev Forge pack are both there", modern != null && forge >= 0, forge);
        if (modern != null && forge >= 0) {
            net.minecraft.client.resources.IResourcePack forgePack = packs.get(forge);
            packs.remove(modern);
            packs.add(packs.indexOf(forgePack) + 1, modern);
        }
        mc.refreshResources();
    }

    private static BufferedImage layerImage(@Nullable TextureLayers.Layer layer, String file) throws Exception {
        if (layer == null) {
            throw new IllegalStateException("no layer has " + file);
        }
        try (InputStream in = ClientTextureTests.class.getResourceAsStream(layer.resource(file))) {
            return ImageIO.read(in);
        }
    }

    /** @return the sprite's first frame as the graphics card has it in the block atlas */
    private static BufferedImage atlasSprite(Minecraft mc, String name) {
        TextureMap map = mc.getTextureMapBlocks();
        TextureAtlasSprite sprite = map.getAtlasSprite(name);
        GlStateManager.bindTexture(map.getGlTextureId());
        BufferedImage atlas = bound();
        return atlas.getSubimage(sprite.getOriginX(), sprite.getOriginY(), sprite.getIconWidth(), sprite.getIconHeight());
    }

    /** @return mip level 0 of the bound 2D texture */
    private static BufferedImage bound() {
        int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        IntBuffer buf = BufferUtils.createIntBuffer(w * h);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buf);
        int[] px = new int[w * h];
        buf.get(px);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    /** @return null when the two match pixel for pixel (fully transparent pixels match whatever their color), else how they differ */
    @Nullable
    private static String compare(BufferedImage got, BufferedImage want) {
        int w = want.getWidth();
        int h = Math.min(want.getHeight(), w == got.getWidth() ? got.getHeight() : want.getHeight());
        if (got.getWidth() != w) {
            return "size " + got.getWidth() + "x" + got.getHeight() + ", want " + w + "x" + want.getHeight();
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = got.getRGB(x, y);
                int b = want.getRGB(x, y);
                if (a != b && ((a >>> 24) != 0 || (b >>> 24) != 0)) {
                    return String.format("pixel %d,%d is %08x, want %08x", x, y, a, b);
                }
            }
        }
        return null;
    }
}

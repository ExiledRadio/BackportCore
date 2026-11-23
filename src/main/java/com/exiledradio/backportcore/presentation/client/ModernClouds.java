package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import javax.imageio.ImageIO;

/**
 * 1.21.2's clouds ({@code CloudRenderer}, as 26.3 has it), once the sky's look is 1.21.2 or later: every 12 by 12
 * texel of {@code clouds.png} is one cell, 4 blocks tall, and only the faces that can be seen are built: the top and
 * bottom (one of them when the camera is above or below the layer), and a side only where the next cell is empty and
 * only on the camera's side of it. 1.12.2 drew a wall at every texel edge, inside a cloud too, and those walls showed
 * through the clouds' undersides as a grid of thin lines, more so the farther the clouds reach (Cloud Distance).
 *
 * <p>26.3 builds the faces ring by ring from the camera out and draws them once, writing depth, so each pixel shows only
 * the nearest face. 1.12.2's fixed pipeline draws each face direction as its own display list (its shade times the
 * cloud color, 26.3's {@code faceColors}), so the order is lost; a depth-only pass first, as 1.12.2's own fancy clouds
 * do, gives the same picture. Within one cell of the camera the faces are also built facing inward (26.3's
 * {@code FLAG_INSIDE_FACE}), so the cloud around you is seen from inside. Fast clouds are 26.3's flat clouds: the
 * bottom faces only, in the top face's color, both sides drawn.
 *
 * <p>The cell grid, drift (0.03 blocks a tick, by {@code RenderGlobal.cloudTickCounter} or world time through P25),
 * the 3.96-block offset, the 0.8 alpha and the cloud height (P15's steps, 0.33 above it) are 1.12.2's and 26.3's
 * alike. The reach is the Cloud Distance (1.21.6), or the render distance before it. Fog is the one set up for the
 * clouds already (P26's cloud distance); 26.3 fades the clouds' alpha instead, which a fixed pipeline cannot do per
 * pixel. 1.12.2's clouds stay for a world with its own cloud renderer, and while a shader pack is on (its clouds
 * program expects 1.12.2's textured clouds).
 */
@SideOnly(Side.CLIENT)
public final class ModernClouds {

    private static final ResourceLocation TEXTURE = new ResourceLocation("textures/environment/clouds.png");
    private static final float CELL = 12.0F;
    private static final float HEIGHT = 4.0F;

    /** 26.3's {@code clouds.vsh} vertex table: four corners per face, faces in {@code Direction} order. */
    private static final int PACKED_X = 0xF03CC3;
    private static final int PACKED_Y = 0x6666F0;
    private static final int PACKED_Z = 0xC3F066;
    /** {@code faceColors}: down, up, north, south, west, east. */
    private static final float[] SHADE = {0.7F, 1.0F, 0.8F, 0.8F, 0.9F, 0.9F};
    private static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;

    private enum Relative { ABOVE, INSIDE, BELOW }

    /** For the client self-test: frames drawn, and quads in the last mesh. */
    public static volatile int frames;
    public static volatile int quads;

    private static boolean failed;
    private static boolean listening;
    private static boolean[] cells; // true where the texel holds a cloud
    private static int width, height;
    private static boolean needsRebuild = true;
    private static int prevCellX = Integer.MIN_VALUE, prevCellZ = Integer.MIN_VALUE, prevRadius = -1;
    private static Relative prevRelative;
    private static boolean prevFancy;
    private static int lists = -1; // 6 display lists, one per face direction
    private static final boolean[] listUsed = new boolean[6];
    private static Field tickCounter;

    private ModernClouds() {
    }

    /** @return true when 1.21.2's clouds should replace 1.12.2's this frame */
    public static boolean active(Minecraft mc) {
        if (failed || mc.world == null || !mc.world.provider.isSurfaceWorld() || mc.world.provider.getCloudRenderer() != null) {
            return false;
        }
        return VanillaRevisions.modernClouds() && !shaderPack();
    }

    /**
     * At the head of {@code RenderGlobal.renderClouds}, after P26's far plane and fog.
     *
     * @return true when this drew the clouds and 1.12.2's must not
     */
    public static boolean render(float partialTicks, double x, double y, double z) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!active(mc)) {
            return false;
        }
        try {
            if (cells == null) {
                load(mc);
                if (cells == null) {
                    return false;
                }
            }
            draw(mc, partialTicks, x, y, z);
            return true;
        } catch (Throwable t) {
            failed = true;
            BackportCore.LOGGER.error("Clouds: 1.21.2's clouds failed, back to 1.12.2's", t);
            return false;
        }
    }

    private static void draw(Minecraft mc, float partialTicks, double x, double y, double z) {
        int clouds = mc.gameSettings.shouldRenderClouds();
        if (clouds == 0) {
            return;
        }
        boolean fancy = clouds == 2;
        int blocks = RenderOptions.cloudBlocks();
        if (blocks < 0) {
            blocks = mc.gameSettings.renderDistanceChunks * 16;
        }
        int radius = MathHelper.ceil(blocks / CELL);

        Entity viewer = mc.getRenderViewEntity();
        float bottom = PresentationHooks.cloudHeight(mc.world.provider.getCloudHeight()) + 0.33F;
        // The mesh is placed relative to the entity's feet (the model view already holds the eye); above, below or
        // inside is decided by the eye.
        float relBottom = (float) (bottom - y);
        float eyeBottom = relBottom - (viewer == null ? 0.0F : viewer.getEyeHeight());
        Relative relative = eyeBottom + HEIGHT < 0.0F ? Relative.ABOVE : eyeBottom > 0.0F ? Relative.BELOW : Relative.INSIDE;

        double ticks = cloudTicks(mc) + partialTicks;
        double cloudX = x + (ticks % (width * 400.0D)) * 0.03F;
        double cloudZ = z + 3.96F;
        double widthBlocks = width * (double) CELL;
        double heightBlocks = height * (double) CELL;
        cloudX -= MathHelper.floor(cloudX / widthBlocks) * widthBlocks;
        cloudZ -= MathHelper.floor(cloudZ / heightBlocks) * heightBlocks;
        int cellX = MathHelper.floor(cloudX / CELL);
        int cellZ = MathHelper.floor(cloudZ / CELL);
        float inCellX = (float) (cloudX - cellX * CELL);
        float inCellZ = (float) (cloudZ - cellZ * CELL);

        if (needsRebuild || cellX != prevCellX || cellZ != prevCellZ || relative != prevRelative || fancy != prevFancy || radius != prevRadius) {
            needsRebuild = false;
            prevCellX = cellX;
            prevCellZ = cellZ;
            prevRelative = relative;
            prevFancy = fancy;
            prevRadius = radius;
            build(relative, cellX, cellZ, fancy, radius);
        }
        if (quads == 0) {
            return;
        }

        Vec3d color = mc.world.getCloudColour(partialTicks);
        float r = (float) color.x, g = (float) color.y, b = (float) color.z;
        if (mc.gameSettings.anaglyph) {
            float ar = (r * 30.0F + g * 59.0F + b * 11.0F) / 100.0F;
            float ag = (r * 30.0F + g * 70.0F) / 100.0F;
            float ab = (r * 30.0F + b * 70.0F) / 100.0F;
            r = ar;
            g = ag;
            b = ab;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(-inCellX, relBottom, -inCellZ);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        if (fancy) {
            GlStateManager.enableCull();
        } else {
            GlStateManager.disableCull();
        }
        // Depth first, then color where the nearest face is: each pixel shows one face, as 26.3's ordered draw does.
        GlStateManager.colorMask(false, false, false, false);
        callLists(r, g, b, fancy);
        GlStateManager.colorMask(true, true, true, true);
        callLists(r, g, b, fancy);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
        frames++;
    }

    private static void callLists(float r, float g, float b, boolean fancy) {
        for (int d = 0; d < 6; d++) {
            if (listUsed[d]) {
                float shade = fancy ? SHADE[d] : SHADE[UP];
                GlStateManager.color(r * shade, g * shade, b * shade, 0.8F);
                GlStateManager.callList(lists + d);
            }
        }
    }

    /** 26.3's {@code buildMesh}: cells in rings from the camera's cell out, within a circle of the reach. */
    private static void build(Relative relative, int centerX, int centerZ, boolean fancy, int radius) {
        if (lists < 0) {
            lists = GLAllocation.generateDisplayLists(6);
        }
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        int count = 0;
        for (int d = 0; d < 6; d++) {
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
            int before = count;
            for (int ring = 0; ring <= 2 * radius; ring++) {
                for (int rx = -ring; rx <= ring; rx++) {
                    int rz = ring - Math.abs(rx);
                    if (rz >= 0 && rz <= radius && rx * rx + rz * rz <= radius * radius) {
                        if (rz != 0) {
                            count += cell(buf, d, relative, centerX, centerZ, rx, -rz, fancy);
                        }
                        count += cell(buf, d, relative, centerX, centerZ, rx, rz, fancy);
                    }
                }
            }
            listUsed[d] = count > before;
            GlStateManager.glNewList(lists + d, GL11.GL_COMPILE);
            tess.draw();
            GlStateManager.glEndList();
        }
        quads = count;
    }

    /** 26.3's {@code tryBuildCell}, {@code buildExtrudedCell} and {@code buildFlatCell}, for one face direction. */
    private static int cell(BufferBuilder buf, int dir, Relative relative, int centerX, int centerZ, int x, int z, boolean fancy) {
        int ix = Math.floorMod(centerX + x, width);
        int iz = Math.floorMod(centerZ + z, height);
        if (!cells[ix + iz * width]) {
            return 0;
        }
        if (!fancy) {
            if (dir != DOWN) {
                return 0;
            }
            face(buf, x, z, DOWN, false);
            return 1;
        }
        int n = 0;
        boolean show;
        switch (dir) {
            case UP:
                show = relative != Relative.BELOW;
                break;
            case DOWN:
                show = relative != Relative.ABOVE;
                break;
            case NORTH:
                show = z > 0 && empty(ix, iz - 1);
                break;
            case SOUTH:
                show = z < 0 && empty(ix, iz + 1);
                break;
            case WEST:
                show = x > 0 && empty(ix - 1, iz);
                break;
            default:
                show = x < 0 && empty(ix + 1, iz);
                break;
        }
        if (show) {
            face(buf, x, z, dir, false);
            n++;
        }
        if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
            face(buf, x, z, dir, true);
            n++;
        }
        return n;
    }

    private static boolean empty(int ix, int iz) {
        return !cells[Math.floorMod(ix, width) + Math.floorMod(iz, height) * width];
    }

    private static void face(BufferBuilder buf, int x, int z, int dir, boolean inside) {
        for (int q = 0; q < 4; q++) {
            int i = dir * 4 + (inside ? 3 - q : q);
            float vx = (PACKED_X >> i) & 1;
            float vy = (PACKED_Y >> i) & 1;
            float vz = (PACKED_Z >> i) & 1;
            buf.pos((vx + x) * CELL, vy * HEIGHT, (vz + z) * CELL).endVertex();
        }
    }

    /** 26.3's {@code prepare(ResourceManager)}: a texel is a cloud where its alpha is 10 or more. */
    private static void load(Minecraft mc) {
        if (!listening && mc.getResourceManager() instanceof IReloadableResourceManager) {
            listening = true;
            ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener((IResourceManager manager) -> {
                cells = null;
                needsRebuild = true;
            });
        }
        try (IResource res = mc.getResourceManager().getResource(TEXTURE); InputStream in = res.getInputStream()) {
            BufferedImage img = ImageIO.read(in);
            int w = img.getWidth(), h = img.getHeight();
            boolean[] c = new boolean[w * h];
            for (int zz = 0; zz < h; zz++) {
                for (int xx = 0; xx < w; xx++) {
                    c[xx + zz * w] = (img.getRGB(xx, zz) >>> 24) >= 10;
                }
            }
            width = w;
            height = h;
            cells = c;
            needsRebuild = true;
        } catch (Exception e) {
            failed = true;
            BackportCore.LOGGER.error("Clouds: could not read {}, keeping 1.12.2's clouds", TEXTURE, e);
        }
    }

    /** P25's tick count: {@code RenderGlobal.cloudTickCounter}, or world time once a step asks for it. */
    private static int cloudTicks(Minecraft mc) {
        int vanilla = 0;
        try {
            if (tickCounter == null) {
                tickCounter = ReflectionHelper.findField(RenderGlobal.class, "cloudTickCounter", "field_72773_u");
            }
            vanilla = tickCounter.getInt(mc.renderGlobal);
        } catch (Exception e) {
            // Keep 0: the clouds stand still rather than vanish.
        }
        return FogPipeline.cloudTicks(vanilla);
    }

    private static Boolean optifineShaders;
    private static Field shaderPackLoaded;

    /** @return true while an OptiFine shader pack is on */
    private static boolean shaderPack() {
        if (optifineShaders == null) {
            try {
                shaderPackLoaded = Class.forName("net.optifine.shaders.Shaders").getField("shaderPackLoaded");
                optifineShaders = Boolean.TRUE;
            } catch (Throwable t) {
                optifineShaders = Boolean.FALSE;
            }
        }
        if (!optifineShaders) {
            return false;
        }
        try {
            return shaderPackLoaded.getBoolean(null);
        } catch (Exception e) {
            return false;
        }
    }
}

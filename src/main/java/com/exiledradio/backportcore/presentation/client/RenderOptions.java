package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.entity.Entity;
import net.minecraft.init.MobEffects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GLContext;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What 1.21.6 to 1.21.11's Video Settings change in the drawing: Cloud Distance (P26), Weather Effect Radius (P27),
 * Chunk Fade (P28), Show Vignette and Anisotropic Filtering (no patch). Each takes effect only once the newest
 * installed version shows its option; until then 1.12.2 draws as it does.
 */
@SideOnly(Side.CLIENT)
public final class RenderOptions {

    /** For the client self-test: frames each hook changed something. */
    public static volatile int cloudFrames;
    public static volatile int fadedChunks;
    public static volatile int vignettes;
    /** For the client self-test: the anisotropy last set on the block atlas (0 until set). */
    public static volatile float anisotropy;

    private static Method renderVignette;
    private static boolean vignetteBroken;
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

    private RenderOptions() {
    }

    /**
     * @return true when OptiFine is installed and has a setting of its own for this option
     *         ({@link com.exiledradio.backportcore.presentation.VideoSections#OPTIFINE_HAS}): the option is not on
     *         Video Settings then, and OptiFine's setting does the drawing
     */
    static boolean leftToOptiFine(com.exiledradio.backportcore.presentation.option.PresentationOption option) {
        return FMLClientHandler.instance().hasOptifine()
                && com.exiledradio.backportcore.presentation.VideoSections.optiFineHas(option.id());
    }

    // ---- P26: Cloud Distance (1.21.6) ----

    /** @return the cloud distance in blocks, or -1 while 1.12.2's clouds stand */
    static int cloudBlocks() {
        return Presentation.CLOUD_RANGE.isShown() ? (int) Math.round(Presentation.CLOUD_RANGE.get()) * 16 : -1;
    }

    /**
     * P26, at the head of {@code RenderGlobal.renderClouds}: the clouds' far plane reaches the cloud distance, and
     * they fade out by it (1.21.6's {@code FogCloudsEnd}, from the camera to the cloud distance) instead of by the
     * render distance. Not under water, in lava, while blind, or for a world with its own cloud renderer.
     */
    static void cloudsBegin() {
        int blocks = cloudBlocks();
        Minecraft mc = Minecraft.getMinecraft();
        if (blocks < 0 || mc.world == null || mc.world.provider.getCloudRenderer() != null) {
            return;
        }
        // The far plane: 1.12.2 draws the clouds with 4 times the render distance.
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        MATRIX.clear();
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, MATRIX);
        float m10 = MATRIX.get(10);
        float m14 = MATRIX.get(14);
        float near = m14 / (m10 - 1.0F);
        float far = m14 / (m10 + 1.0F);
        float want = blocks * 1.5F + 64.0F;
        if (far < want && near > 0.0F) {
            MATRIX.put(10, (want + near) / (near - want));
            MATRIX.put(14, 2.0F * want * near / (near - want));
            MATRIX.rewind();
            GL11.glLoadMatrix(MATRIX);
        }
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        Entity viewer = mc.getRenderViewEntity();
        boolean blind = viewer instanceof net.minecraft.entity.EntityLivingBase
                && ((net.minecraft.entity.EntityLivingBase) viewer).isPotionActive(MobEffects.BLINDNESS);
        if (!blind && GL11.glGetInteger(GL11.GL_FOG_MODE) == GL11.GL_LINEAR) {
            GlStateManager.setFogStart(0.0F);
            GlStateManager.setFogEnd(blocks);
        }
        cloudFrames++;
    }

    /** P26: 1.12.2's fast clouds run from -256 to 256 blocks; from 1.21.6, to the cloud distance (in 32-block steps). */
    static int cloudFast(int vanilla) {
        int blocks = cloudBlocks();
        if (blocks < 0) {
            return vanilla;
        }
        int r = (blocks + 31) / 32 * 32;
        return vanilla < 0 ? -r : r;
    }

    /** P26: 1.12.2's fancy clouds draw cells -3 to 4 (each 96 blocks); from 1.21.6, enough to reach the cloud distance. */
    static int cloudFancy(int vanilla) {
        int blocks = cloudBlocks();
        if (blocks < 0) {
            return vanilla;
        }
        int n = MathHelper.ceil(blocks / 96.0F);
        return vanilla < 0 ? -n : n;
    }

    // ---- P27: Weather Effect Radius (1.21.11) ----

    /** P27: {@code renderRainSnow}'s radius, 5 (Fast) or 10 (Fancy) in 1.12.2; the option's 3 to 10 from 1.21.11. */
    static int weatherRadius(int vanilla) {
        return Presentation.WEATHER_RADIUS.isShown() ? (int) Math.round(Presentation.WEATHER_RADIUS.get()) : vanilla;
    }

    // ---- P28: Chunk Fade (1.21.11) ----

    /** Each render chunk's position and the time it was first drawn there. */
    private static final Map<RenderChunk, long[]> FIRST_DRAWN = new WeakHashMap<RenderChunk, long[]>();
    private static boolean fogCoord;
    private static int fogMode = -1;
    private static float fogStart;
    private static float fogEnd;
    private static float fogDensity;
    private static Boolean supported;

    private static boolean fadeSupported() {
        if (supported == null) {
            supported = GLContext.getCapabilities().OpenGL14 && !FMLClientHandler.instance().hasOptifine();
        }
        return supported;
    }

    /**
     * P28, at the head of {@code ChunkRenderContainer.preRenderChunk}: 1.21.11's terrain shader mixes a section with the
     * fog color by its visibility ({@code ChunkVisibility}, 0 to 1 over the fade time after it is first drawn). 1.12.2's
     * fixed pipeline gives the chunk one fog coordinate for that frame: the fog it would have at its center, times
     * its visibility.
     */
    static void chunkPre(RenderChunk chunk) {
        double seconds = Presentation.CHUNK_FADE.isShown() ? Presentation.CHUNK_FADE.get() : 0.0;
        if (seconds <= 0.0 || !fadeSupported()) {
            endFog();
            return;
        }
        BlockPos pos = chunk.getPosition();
        long now = Minecraft.getSystemTime();
        long[] seen = FIRST_DRAWN.get(chunk);
        if (seen == null || seen[0] != pos.toLong()) {
            seen = new long[] {pos.toLong(), now};
            FIRST_DRAWN.put(chunk, seen);
        }
        double visibility = (now - seen[1]) / (seconds * 1000.0);
        if (visibility >= 1.0 || !GL11.glIsEnabled(GL11.GL_FOG)) {
            endFog();
            return;
        }
        if (fogMode < 0) {
            fogMode = GL11.glGetInteger(GL11.GL_FOG_MODE);
            fogStart = GL11.glGetFloat(GL11.GL_FOG_START);
            fogEnd = GL11.glGetFloat(GL11.GL_FOG_END);
            fogDensity = GL11.glGetFloat(GL11.GL_FOG_DENSITY);
        }
        Minecraft mc = Minecraft.getMinecraft();
        Entity e = mc.getRenderViewEntity();
        if (e == null) {
            endFog();
            return;
        }
        float pt = mc.getRenderPartialTicks();
        double dx = pos.getX() + 8 - (e.lastTickPosX + (e.posX - e.lastTickPosX) * pt);
        double dy = pos.getY() + 8 - (e.lastTickPosY + (e.posY - e.lastTickPosY) * pt + e.getEyeHeight());
        double dz = pos.getZ() + 8 - (e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt);
        float coord = fogCoordinate(fogMode, fogStart, fogEnd, fogDensity, Math.sqrt(dx * dx + dy * dy + dz * dz), Math.max(0.0, visibility));
        GL11.glFogi(GL14.GL_FOG_COORDINATE_SOURCE, GL14.GL_FOG_COORDINATE);
        GL14.glFogCoordf(coord);
        fogCoord = true;
        fadedChunks++;
    }

    /**
     * @return the fog coordinate whose fog factor (1 none, 0 all fog) is {@code visibility} times the factor at
     *         {@code distance}, for 1.12.2's linear, exp and exp2 fog
     */
    public static float fogCoordinate(int mode, float start, float end, float density, double distance, double visibility) {
        double f;
        if (mode == GL11.GL_EXP) {
            f = Math.exp(-density * distance);
        } else if (mode == GL11.GL_EXP2) {
            f = Math.exp(-(density * distance) * (density * distance));
        } else {
            f = end > start ? MathHelper.clamp((end - distance) / (end - start), 0.0, 1.0) : 1.0;
        }
        f *= visibility;
        if (mode == GL11.GL_EXP || mode == GL11.GL_EXP2) {
            if (f <= 1.0E-6 || density <= 0.0F) {
                return 1.0E6F;
            }
            double l = -Math.log(f);
            return (float) (mode == GL11.GL_EXP ? l / density : Math.sqrt(l) / density);
        }
        return (float) (end - f * (end - start));
    }

    /** P28, after {@code RenderGlobal.renderBlockLayer}'s {@code renderChunkLayer}: depth fog again for everything else. */
    static void chunkLayerEnd() {
        endFog();
        fogMode = -1;
    }

    private static void endFog() {
        if (fogCoord) {
            GL11.glFogi(GL14.GL_FOG_COORDINATE_SOURCE, GL14.GL_FRAGMENT_DEPTH);
            fogCoord = false;
        }
    }

    // ---- Anisotropic Filtering (1.21.11) ----

    /** Sets the block atlas' anisotropy: the option's 2x to 8x with Anisotropic filtering, else none. */
    public static void applyAnisotropy() {
        if (!Presentation.TEXTURE_FILTERING.isShown() || leftToOptiFine(Presentation.TEXTURE_FILTERING)) {
            return;
        }
        try {
            if (!GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            float level = 1.0F;
            if ("anisotropic".equals(Presentation.TEXTURE_FILTERING.get())) {
                float max = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
                level = Math.min(1 << (int) Math.round(Presentation.MAX_ANISOTROPY.get()), max);
            }
            GlStateManager.bindTexture(mc.getTextureMapBlocks().getGlTextureId());
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, level);
            anisotropy = level;
        } catch (RuntimeException e) {
            BackportCore.LOGGER.warn("Presentation: anisotropic filtering could not be set: {}", e.toString());
        }
    }

    /** From Core's client init. */
    static void init() {
        Presentation.TEXTURE_FILTERING.onChange(v -> applyAnisotropy());
        Presentation.MAX_ANISOTROPY.onChange(v -> applyAnisotropy());
    }

    /** Forge event handlers; public for the event bus. */
    public static final class Events {

        @SubscribeEvent
        public void onStitched(TextureStitchEvent.Post event) {
            if (event.getMap() == Minecraft.getMinecraft().getTextureMapBlocks()) {
                applyAnisotropy();
            }
        }

        /** 1.21.11's Show Vignette: off takes it away; on draws it with Fast graphics too (1.12.2 drew it only with Fancy). */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onOverlay(RenderGameOverlayEvent.Pre event) {
            if (!Presentation.VIGNETTE.isShown() || leftToOptiFine(Presentation.VIGNETTE)) {
                return;
            }
            if (event.getType() == RenderGameOverlayEvent.ElementType.VIGNETTE && !Presentation.VIGNETTE.get()) {
                event.setCanceled(true);
            } else if (event.getType() == RenderGameOverlayEvent.ElementType.ALL && Presentation.VIGNETTE.get()
                    && !Minecraft.isFancyGraphicsEnabled() && !vignetteBroken) {
                drawVignette(event.getResolution());
            }
        }

        private static void drawVignette(ScaledResolution res) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null) {
                return;
            }
            try {
                if (renderVignette == null) {
                    renderVignette = ReflectionHelper.findMethod(GuiIngame.class, "renderVignette", "func_180480_a", float.class, ScaledResolution.class);
                }
                GlStateManager.enableBlend();
                renderVignette.invoke(mc.ingameGUI, mc.player.getBrightness(), res);
                vignettes++;
            } catch (Exception e) {
                BackportCore.LOGGER.warn("Presentation: vignette could not be drawn: {}", e.toString());
                vignetteBroken = true;
            }
        }
    }
}

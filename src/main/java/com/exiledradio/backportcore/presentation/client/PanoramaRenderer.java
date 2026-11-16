package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.presentation.PanoramaChoice;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.option.OptionStore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.util.glu.Project;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Random;

/**
 * The title screen's panorama as 1.13 and later draw it ({@code RenderSkyboxCube} and {@code RenderSkybox} in
 * 1.13): the six faces on a cube seen from inside through an 85 degree perspective at the window's own shape,
 * four slightly offset passes for smooth edges in place of 1.12.2's blur, a slow full turn ({@code -0.1}
 * degrees a tick) and a gentle nod ({@code sin(t / 1000) * 5 + 25} degrees). The clock restarts for each new
 * title screen, as 1.13's does. 1.19.3's Panorama Scroll Speed scales the turn once a 1.19.3 or later
 * backport shows it.
 *
 * <p>From 1.20.5 ({@link VanillaRevisions#panoramaSpinOnly}) it is 1.20.6's {@code PanoramaRenderer}: the pitch holds at
 * 10 degrees and the view only turns, by one spin shared by every screen ({@code Screen.PANORAMA} is static) that never
 * restarts; each screen advances it by the ticks since its own last frame, half a tick for a screen's first frame or
 * after more than seven ({@code Screen.advancePanoramaTime}).
 */
@SideOnly(Side.CLIENT)
public final class PanoramaRenderer {

    /** 1.12.2's own faces, used when 1.12.2's panorama is chosen in a modern title screen. */
    private static final ResourceLocation[] VANILLA = new ResourceLocation[6];

    static {
        for (int i = 0; i < 6; i++) {
            VANILLA[i] = new ResourceLocation("textures/gui/title/background/panorama_" + i + ".png");
        }
    }

    @Nullable
    private static PanoramaChoice choice;
    private static WeakReference<GuiScreen> screen = new WeakReference<GuiScreen>(null);
    private static float time;
    private static long lastMillis;
    /** 1.20.5's spin, in degrees (0 to 360), shared by every screen. */
    private static float spin;
    /** The angles of the frame drawn last, for {@link #redraw}. */
    private static float lastPitch = 25.0F;
    private static float lastYaw;
    /** For the client self-test: frames drawn. */
    public static volatile int frames;

    private PanoramaRenderer() {
    }

    /** @return true when the title screen is drawn as 1.13 and later draw it: any backport is installed */
    public static boolean modern() {
        return Presentation.isAtLeast("1.13");
    }

    /** @return this launch's panorama, chosen once from Core's {@code presentation.panorama} setting */
    public static synchronized PanoramaChoice choice() {
        if (choice == null) {
            choice = PanoramaChoice.choose(CoreConfig.presentation.panorama, Presentation.panoramas(),
                    OptionStore.get(PanoramaChoice.LAST_ROTATED_KEY), new Random());
            if (PanoramaChoice.ROTATE.equalsIgnoreCase(CoreConfig.presentation.panorama.trim())) {
                OptionStore.put(PanoramaChoice.LAST_ROTATED_KEY, choice.version.toString());
            }
            com.exiledradio.backportcore.BackportCore.LOGGER.info("Presentation: title screen shows {}", choice);
        }
        return choice;
    }

    /** @return the six faces drawn this launch */
    public static ResourceLocation[] faces() {
        PanoramaChoice c = choice();
        return c.faces != null ? c.faces : VANILLA;
    }

    /** Draws the panorama over the whole screen, advancing its clock by the real time since the last frame. */
    public static void render(GuiScreen menu) {
        long now = Minecraft.getSystemTime();
        float speed = Presentation.PANORAMA_SPEED.isShown() ? (float) Presentation.PANORAMA_SPEED.get() : 1.0F;
        if (VanillaRevisions.panoramaSpinOnly(Presentation.newest())) {
            if (screen.get() != menu) {
                // A new screen's last panorama time is 0, so its first frame advances half a tick.
                screen = new WeakReference<GuiScreen>(menu);
                lastMillis = 0L;
            }
            spin = advanceSpin(spin, advance(now, lastMillis), speed);
            lastMillis = now;
            lastPitch = 10.0F;
            lastYaw = -spin;
        } else {
            if (screen.get() != menu) {
                screen = new WeakReference<GuiScreen>(menu);
                time = 0.0F;
                lastMillis = now;
            }
            // 1.13's screens get the ticks elapsed since the last frame; 1.12.2's get only the partial tick.
            float elapsed = MathHelper.clamp((now - lastMillis) / 50.0F, 0.0F, 10.0F);
            lastMillis = now;
            time += elapsed * speed;
            lastPitch = MathHelper.sin(time * 0.001F) * 5.0F + 25.0F;
            lastYaw = -time * 0.1F;
        }
        drawCube(Minecraft.getMinecraft(), faces(), lastPitch, lastYaw, 1.0F);
        frames++;
    }

    /** 1.20.6's {@code Screen.advancePanoramaTime}: the ticks since a screen's last frame, half a tick past seven. */
    public static float advance(long nowMillis, long lastMillis) {
        float ticks = (nowMillis - lastMillis) / 50.0F;
        return ticks > 7.0F ? 0.5F : ticks;
    }

    /** 1.20.6's {@code PanoramaRenderer.render}: 0.1 degrees a tick, scaled by the scroll speed, kept under 360. */
    public static float advanceSpin(float spin, float ticks, float speed) {
        float next = spin + ticks * speed * 0.1F;
        return next > 360.0F ? next - 360.0F : next;
    }

    /** @return the pitch and yaw of the panorama frame drawn last, in degrees (for the self-tests) */
    public static float[] lastAngles() {
        return new float[] {lastPitch, lastYaw};
    }

    /** 1.20.5's Menu Background Blurriness default (5), the blur's radius. */
    public static final float MENU_BLUR = 5.0F;

    @Nullable
    private static net.minecraft.client.shader.Framebuffer blurTarget;
    @Nullable
    private static net.minecraft.client.shader.ShaderGroup blur;
    private static boolean blurFailed;
    /** For the client self-test: frames drawn blurred. */
    public static volatile int blurredFrames;

    /**
     * 1.20.5's menu panorama ({@code Screen.renderPanorama} then {@code renderBlurredBackground}): {@link #render} into a
     * buffer of its own, blurred at {@code radius} by 1.20.6's six box-blur passes on 1.12.2's {@code blur} program
     * ({@code backportcore:shaders/post/menu_blur.json}; 1.3.0 and 1.3.1 ran 1.12.2's two-pass {@code blur.json}), then
     * drawn over the whole screen. Wherever the blur cannot run (no shader support, or an error once), the sharp panorama is drawn.
     * Since Core 1.3.0.
     */
    public static void renderBlurred(@Nullable GuiScreen menu, float radius) {
        Minecraft mc = Minecraft.getMinecraft();
        if (blurFailed || !net.minecraft.client.renderer.OpenGlHelper.shadersSupported || !net.minecraft.client.renderer.OpenGlHelper.isFramebufferEnabled()) {
            render(menu);
            return;
        }
        int previous = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);
        int w = mc.displayWidth;
        int h = mc.displayHeight;
        try {
            if (blurTarget == null || blurTarget.framebufferWidth != w || blurTarget.framebufferHeight != h) {
                if (blurTarget == null) {
                    blurTarget = new net.minecraft.client.shader.Framebuffer(w, h, true);
                } else {
                    blurTarget.createBindFramebuffer(w, h);
                }
                if (blur != null) {
                    blur.deleteShaderGroup();
                }
                blur = new net.minecraft.client.shader.ShaderGroup(mc.getTextureManager(), mc.getResourceManager(), blurTarget,
                        new ResourceLocation("backportcore", "shaders/post/menu_blur.json"));
                blur.createBindFramebuffers(w, h);
            }
            java.util.List<net.minecraft.client.shader.Shader> passes = net.minecraftforge.fml.common.ObfuscationReflectionHelper
                    .getPrivateValue(net.minecraft.client.shader.ShaderGroup.class, blur, "field_148031_d");
            for (net.minecraft.client.shader.Shader pass : passes) {
                net.minecraft.client.shader.ShaderUniform r = pass.getShaderManager().getShaderUniform("Radius");
                if (r != null) {
                    r.set(radius);
                }
            }
            blurTarget.framebufferClear();
            blurTarget.bindFramebuffer(true);
            render(menu);
            GlStateManager.matrixMode(5889);
            GlStateManager.pushMatrix();
            GlStateManager.matrixMode(5888);
            GlStateManager.pushMatrix();
            blur.render(mc.getRenderPartialTicks());
            net.minecraft.client.renderer.OpenGlHelper.glBindFramebuffer(net.minecraft.client.renderer.OpenGlHelper.GL_FRAMEBUFFER, previous);
            GlStateManager.viewport(0, 0, w, h);
            blurTarget.framebufferRenderExt(w, h, true);
            GlStateManager.matrixMode(5889);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5888);
            GlStateManager.popMatrix();
            GlStateManager.enableTexture2D();
            GlStateManager.disableDepth();
            blurredFrames++;
        } catch (Exception e) {
            blurFailed = true;
            com.exiledradio.backportcore.BackportCore.LOGGER.warn("Presentation: the blurred panorama is not available; drawn sharp.", e);
            net.minecraft.client.renderer.OpenGlHelper.glBindFramebuffer(net.minecraft.client.renderer.OpenGlHelper.GL_FRAMEBUFFER, previous);
            GlStateManager.viewport(0, 0, w, h);
            render(menu);
        }
    }

    /**
     * Draws this frame's panorama (and the newest overlay) again over the finished screen, covering it by
     * {@code cover} (0 to 1): the title screen's 1.13 fade-in. The clock does not advance.
     */
    static void redraw(GuiScreen menu, float cover) {
        if (cover <= 0.0F) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // drawCube's four jittered passes each blend a / (pass + 1); find the a whose total cover is the one asked.
        float lo = 0.0F;
        float hi = 1.0F;
        for (int i = 0; i < 20; i++) {
            float mid = (lo + hi) / 2.0F;
            if (coverOf(mid) < cover) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        GlStateManager.disableDepth();
        drawCube(mc, faces(), lastPitch, lastYaw, hi);
        net.minecraft.util.ResourceLocation overlay = Presentation.panoramaOverlay();
        if (overlay != null) {
            mc.getTextureManager().bindTexture(overlay);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, cover);
            Gui.drawScaledCustomSizeModalRect(0, 0, 0.0F, 0.0F, 16, 128, menu.width, menu.height, 16.0F, 128.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
        GlStateManager.enableDepth();
    }

    /** @return how much of what is beneath four passes of alpha a / (pass + 1) cover */
    static float coverOf(float a) {
        float left = 1.0F;
        for (int pass = 0; pass < 4; pass++) {
            left *= 1.0F - a / (pass + 1);
        }
        return 1.0F - left;
    }

    /** 1.13's {@code RenderSkyboxCube.render}, on 1.12.2's GL state manager. */
    static void drawCube(Minecraft mc, ResourceLocation[] faces, float pitch, float yaw, float alpha) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        GlStateManager.matrixMode(5889);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        Project.gluPerspective(85.0F, (float) mc.displayWidth / (float) Math.max(1, mc.displayHeight), 0.05F, 10.0F);
        GlStateManager.matrixMode(5888);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        for (int pass = 0; pass < 4; ++pass) {
            GlStateManager.pushMatrix();
            float dx = ((float) (pass % 2) / 2.0F - 0.5F) / 256.0F;
            float dy = ((float) (pass / 2) / 2.0F - 0.5F) / 256.0F;
            GlStateManager.translate(dx, dy, 0.0F);
            GlStateManager.rotate(pitch, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(yaw, 0.0F, 1.0F, 0.0F);
            for (int face = 0; face < 6; ++face) {
                mc.getTextureManager().bindTexture(faces[face]);
                buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
                int a = Math.round(255.0F * alpha) / (pass + 1);
                switch (face) {
                    case 0:
                        vertex(buffer, -1, -1, 1, 0, 0, a);
                        vertex(buffer, -1, 1, 1, 0, 1, a);
                        vertex(buffer, 1, 1, 1, 1, 1, a);
                        vertex(buffer, 1, -1, 1, 1, 0, a);
                        break;
                    case 1:
                        vertex(buffer, 1, -1, 1, 0, 0, a);
                        vertex(buffer, 1, 1, 1, 0, 1, a);
                        vertex(buffer, 1, 1, -1, 1, 1, a);
                        vertex(buffer, 1, -1, -1, 1, 0, a);
                        break;
                    case 2:
                        vertex(buffer, 1, -1, -1, 0, 0, a);
                        vertex(buffer, 1, 1, -1, 0, 1, a);
                        vertex(buffer, -1, 1, -1, 1, 1, a);
                        vertex(buffer, -1, -1, -1, 1, 0, a);
                        break;
                    case 3:
                        vertex(buffer, -1, -1, -1, 0, 0, a);
                        vertex(buffer, -1, 1, -1, 0, 1, a);
                        vertex(buffer, -1, 1, 1, 1, 1, a);
                        vertex(buffer, -1, -1, 1, 1, 0, a);
                        break;
                    case 4:
                        vertex(buffer, -1, -1, -1, 0, 0, a);
                        vertex(buffer, -1, -1, 1, 0, 1, a);
                        vertex(buffer, 1, -1, 1, 1, 1, a);
                        vertex(buffer, 1, -1, -1, 1, 0, a);
                        break;
                    default:
                        vertex(buffer, -1, 1, 1, 0, 0, a);
                        vertex(buffer, -1, 1, -1, 0, 1, a);
                        vertex(buffer, 1, 1, -1, 1, 1, a);
                        vertex(buffer, 1, 1, 1, 1, 0, a);
                        break;
                }
                tessellator.draw();
            }
            GlStateManager.popMatrix();
            GlStateManager.colorMask(true, true, true, false);
        }
        buffer.setTranslation(0.0D, 0.0D, 0.0D);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.matrixMode(5889);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(5888);
        GlStateManager.popMatrix();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableDepth();
    }

    private static void vertex(BufferBuilder buffer, double x, double y, double z, double u, double v, int alpha) {
        buffer.pos(x, y, z).tex(u, v).color(255, 255, 255, alpha).endVertex();
    }

    /**
     * Draws the newest version's overlay over the whole screen, as 1.13's title screen draws
     * {@code panorama_overlay.png} (a 16 by 128 texture stretched to the screen).
     */
    static void drawOverlay(Minecraft mc, ResourceLocation overlay, int width, int height) {
        mc.getTextureManager().bindTexture(overlay);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Gui.drawScaledCustomSizeModalRect(0, 0, 0.0F, 0.0F, 16, 128, width, height, 16.0F, 128.0F);
    }
}

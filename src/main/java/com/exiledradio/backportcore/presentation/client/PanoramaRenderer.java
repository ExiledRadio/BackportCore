package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.presentation.PanoramaChoice;
import com.exiledradio.backportcore.presentation.Presentation;
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
        if (screen.get() != menu) {
            screen = new WeakReference<GuiScreen>(menu);
            time = 0.0F;
            lastMillis = now;
        }
        // 1.13's screens get the ticks elapsed since the last frame; 1.12.2's get only the partial tick.
        float elapsed = MathHelper.clamp((now - lastMillis) / 50.0F, 0.0F, 10.0F);
        lastMillis = now;
        float speed = Presentation.PANORAMA_SPEED.isShown() ? (float) Presentation.PANORAMA_SPEED.get() : 1.0F;
        time += elapsed * speed;
        drawCube(Minecraft.getMinecraft(), faces(), MathHelper.sin(time * 0.001F) * 5.0F + 25.0F, -time * 0.1F, 1.0F);
        frames++;
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

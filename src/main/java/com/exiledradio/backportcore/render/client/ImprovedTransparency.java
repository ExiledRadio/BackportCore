package com.exiledradio.backportcore.render.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Method;

/**
 * 1.21.11's Improved Transparency ({@link Presentation#IMPROVED_TRANSPARENCY}) as far as 1.12.2's pipeline allows.
 *
 * <p>1.12.2's {@code EntityRenderer.renderWorldPass} draws particles (writing depth), then rain and snow (not writing
 * depth), then the translucent layer (water, ice, stained glass, slime; not writing depth either), then the clouds when
 * the camera is above them. So rain and snow between the camera and a lake or a window are drawn first and the water or
 * glass is blended over them: they look as if behind it. Vanilla's Improved Transparency (Fabulous! before it) draws
 * each of these into a target of its own and blends them by depth ({@code shaders/post/transparency.json}).
 *
 * <p>Here, at the end of the world (Forge's {@code RenderWorldLastEvent}, lowest priority, after every other mod's
 * drawing there), the translucent layer is drawn once more into the depth buffer only, and the rain and snow once more
 * over it: what is in front of a translucent surface is drawn over it, what is behind it stays behind (it fails the
 * depth test), and nothing changes where nothing translucent is. It runs only while rain or snow is falling. Particles
 * and clouds keep 1.12.2's order: particles already write depth (only their soft edges cut into water behind them), and
 * the clouds are drawn after the translucent layer when the camera is above them. See {@code docs/PRESENTATION.md}.
 */
@SideOnly(Side.CLIENT)
public final class ImprovedTransparency {

    /** For the client self-test: frames that drew the second weather pass. */
    public static volatile int frames;

    private static Method renderRainSnow;
    private static boolean broken;

    /** @return true while it draws: the option shown and on, a world with rain or snow falling */
    public static boolean active(Minecraft mc, float partialTicks) {
        return !broken && Presentation.IMPROVED_TRANSPARENCY.isShown() && Presentation.IMPROVED_TRANSPARENCY.get() && mc.world != null
                && mc.getRenderViewEntity() != null && mc.world.getRainStrength(partialTicks) > 0.0F;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRenderLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        float partialTicks = event.getPartialTicks();
        if (!active(mc, partialTicks)) {
            return;
        }
        try {
            draw(mc, partialTicks);
            frames++;
        } catch (Exception e) {
            broken = true;
            BackportCore.LOGGER.error("Improved Transparency: the second weather pass failed; 1.12.2's drawing from here on", e);
        }
    }

    private static void draw(Minecraft mc, float partialTicks) throws Exception {
        Entity viewer = mc.getRenderViewEntity();
        // 1. The translucent layer's depth, nothing else.
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        GlStateManager.colorMask(false, false, false, false);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        RenderHelper.disableStandardItemLighting();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        mc.renderGlobal.renderBlockLayer(BlockRenderLayer.TRANSLUCENT, partialTicks, 2, viewer);
        GlStateManager.colorMask(true, true, true, true);
        // 2. Rain and snow again, as renderWorldPass draws them (fog on, depth not written).
        GlStateManager.enableFog();
        GlStateManager.depthMask(false);
        GlStateManager.enableCull();
        rainSnow().invoke(mc.entityRenderer, partialTicks);
        GlStateManager.depthMask(true);
        GlStateManager.disableFog();
        GlStateManager.disableBlend();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
    }

    private static Method rainSnow() {
        if (renderRainSnow == null) {
            renderRainSnow = ReflectionHelper.findMethod(net.minecraft.client.renderer.EntityRenderer.class, "renderRainSnow", "func_78474_d", float.class);
        }
        return renderRainSnow;
    }
}

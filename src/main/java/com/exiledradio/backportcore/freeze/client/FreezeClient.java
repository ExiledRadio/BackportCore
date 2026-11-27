package com.exiledradio.backportcore.freeze.client;

import com.exiledradio.backportcore.freeze.FreezeTicks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/**
 * Freeze ticks on the client: the counter from {@code FreezeNetwork}, the frost overlay ({@code Gui.renderCameraOverlays}:
 * {@code powder_snow_outline} at the percent frozen) and the shaking of a fully frozen mob
 * ({@code LivingEntityRenderer.setupRotations}: body yaw plus {@code cos(floor(age) x 3.25) x PI x 0.4} degrees).
 */
public final class FreezeClient {

    static final ResourceLocation OUTLINE = new ResourceLocation("backportcore", "textures/misc/powder_snow_outline.png");

    private float shaken;

    private FreezeClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new FreezeClient());
    }

    public static void receive(final int entityId, final int ticks, final int flags) {
        final Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                if (mc.world != null) {
                    Entity entity = mc.world.getEntityByID(entityId);
                    if (entity != null) {
                        FreezeTicks.received(entity, ticks, flags);
                    }
                }
            }
        });
    }

    /** Drawn with the pumpkin blur, before the hotbar, as vanilla's camera overlays are. */
    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Pre event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.HELMET) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || FreezeTicks.ticks(mc.player) <= 0) {
            return;
        }
        draw(event.getResolution(), FreezeTicks.percent(mc.player));
    }

    private static void draw(ScaledResolution res, float alpha) {
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        GlStateManager.disableAlpha();
        mc.getTextureManager().bindTexture(OUTLINE);
        double w = res.getScaledWidth_double(), h = res.getScaledHeight_double();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(0.0D, h, -90.0D).tex(0.0D, 1.0D).endVertex();
        buffer.pos(w, h, -90.0D).tex(1.0D, 1.0D).endVertex();
        buffer.pos(w, 0.0D, -90.0D).tex(1.0D, 0.0D).endVertex();
        buffer.pos(0.0D, 0.0D, -90.0D).tex(0.0D, 0.0D).endVertex();
        tessellator.draw();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void shakeStart(RenderLivingEvent.Pre<EntityLivingBase> event) {
        EntityLivingBase e = event.getEntity();
        this.shaken = 0.0F;
        if (FreezeTicks.fullyFrozen(e)) {
            this.shaken = (float) (Math.cos(MathHelper.floor(e.ticksExisted) * 3.25D) * Math.PI * 0.4F);
            e.renderYawOffset += this.shaken;
            e.prevRenderYawOffset += this.shaken;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void shakeEnd(RenderLivingEvent.Post<EntityLivingBase> event) {
        if (this.shaken != 0.0F) {
            EntityLivingBase e = event.getEntity();
            e.renderYawOffset -= this.shaken;
            e.prevRenderYawOffset -= this.shaken;
            this.shaken = 0.0F;
        }
    }
}

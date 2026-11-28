package com.exiledradio.backportcore.boat.client;

import com.exiledradio.backportcore.boat.BoatLeash;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Map;

/**
 * Draws the lead of each leashed boat ({@link BoatLeash#CLIENT_HOLDERS}), from the front of the boat to the holder,
 * with 1.12.2's lead: 24 segments, two crossed strips, the same browns and sag ({@code RenderLiving.renderLeash}). The
 * holder end is 1.12.2's too: a player's hand, a knot's middle; a boat holds by its own front. Tricky Trials' 1.21.6
 * rope can set {@link #halfWidth}.
 */
@SideOnly(Side.CLIENT)
public final class BoatLeashRender {

    /** Half the rope's width (1.12.2's 0.025). */
    public static volatile double halfWidth = 0.025D;

    private BoatLeashRender() {
    }

    /** From the client's pre-init. */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new BoatLeashRender());
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        BoatLeash.CLIENT_HOLDERS.clear();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || BoatLeash.CLIENT_HOLDERS.isEmpty()) {
            return;
        }
        float partial = event.getPartialTicks();
        for (Map.Entry<Integer, Integer> en : BoatLeash.CLIENT_HOLDERS.entrySet()) {
            Entity boat = mc.world.getEntityByID(en.getKey());
            Entity holder = mc.world.getEntityByID(en.getValue());
            if (boat instanceof EntityBoat && holder != null && !boat.isDead) {
                draw(boatEnd(boat, partial), holderEnd(holder, partial));
            }
        }
    }

    /** The boat's end: 1.21's default leash offset, the eye height and 0.4 of the width ahead. */
    static Vec3d boatEnd(Entity boat, float partial) {
        double yaw = lerp(boat.prevRotationYaw, boat.rotationYaw, partial) * 0.017453292D;
        double ahead = boat.width * 0.4D;
        return new Vec3d(lerp(boat.prevPosX, boat.posX, partial) - Math.sin(yaw) * ahead,
                lerp(boat.prevPosY, boat.posY, partial) + boat.getEyeHeight(),
                lerp(boat.prevPosZ, boat.posZ, partial) + Math.cos(yaw) * ahead);
    }

    /** 1.12.2's holder end ({@code RenderLiving.renderLeash}): a hand, or a knot's middle; another boat's front. */
    static Vec3d holderEnd(Entity holder, float partial) {
        if (holder instanceof EntityBoat) {
            return boatEnd(holder, partial);
        }
        double yaw = lerp(holder.prevRotationYaw, holder.rotationYaw, partial * 0.5F) * 0.017453292D;
        double pitch = lerp(holder.prevRotationPitch, holder.rotationPitch, partial * 0.5F) * 0.017453292D;
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double up = Math.sin(pitch);
        if (holder instanceof EntityHanging) {
            cos = 0.0D;
            sin = 0.0D;
            up = -1.0D;
        }
        double level = Math.cos(pitch);
        return new Vec3d(lerp(holder.prevPosX, holder.posX, partial) - cos * 0.7D - sin * 0.5D * level,
                lerp(holder.prevPosY, holder.posY, partial) + holder.getEyeHeight() * 0.7D - up * 0.5D - 0.25D,
                lerp(holder.prevPosZ, holder.posZ, partial) - sin * 0.7D + cos * 0.5D * level);
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static void draw(Vec3d from, Vec3d to) {
        double x = from.x - TileEntityRendererDispatcher.staticPlayerX;
        double y = from.y - TileEntityRendererDispatcher.staticPlayerY;
        double z = from.z - TileEntityRendererDispatcher.staticPlayerZ;
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double w = halfWidth;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        for (int strip = 0; strip < 2; strip++) {
            buffer.begin(5, DefaultVertexFormats.POSITION_COLOR);
            for (int j = 0; j <= 24; ++j) {
                float r = 0.5F;
                float g = 0.4F;
                float b = 0.3F;
                if (j % 2 == 0) {
                    r *= 0.7F;
                    g *= 0.7F;
                    b *= 0.7F;
                }
                float t = j / 24.0F;
                double px = x + dx * t;
                double py = y + dy * (t * t + t) * 0.5D;
                double pz = z + dz * t;
                if (strip == 0) {
                    buffer.pos(px, py, pz).color(r, g, b, 1.0F).endVertex();
                    buffer.pos(px + w, py + w, pz).color(r, g, b, 1.0F).endVertex();
                } else {
                    buffer.pos(px, py + w, pz).color(r, g, b, 1.0F).endVertex();
                    buffer.pos(px + w, py, pz + w).color(r, g, b, 1.0F).endVertex();
                }
            }
            tessellator.draw();
        }
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
    }
}

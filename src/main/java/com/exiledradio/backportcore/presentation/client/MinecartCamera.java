package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 1.21.3's Rotate With Minecarts ({@link Presentation#ROTATE_WITH_MINECART}): while riding a minecart, the camera turns as
 * far as the cart turned since the last tick ({@code NewMinecartBehavior}: the passenger's yaw plus the cart's yaw
 * change). Only in a world with Minecart Improvements ({@link Presentation#minecartImprovements()}), as in vanilla, so
 * never in a plain 1.12.2 world. 1.12.2's cart flips its yaw by 180 degrees when it reverses; a change over 90 degrees
 * is such a flip, not a turn, and is skipped.
 */
@SideOnly(Side.CLIENT)
public final class MinecartCamera {

    private Entity cart;
    private float cartYaw;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Entity riding = mc.player == null ? null : mc.player.getRidingEntity();
        if (!(riding instanceof EntityMinecart) || !Presentation.ROTATE_WITH_MINECART.isShown() || !Presentation.ROTATE_WITH_MINECART.get()
                || !Presentation.minecartImprovements()) {
            this.cart = null;
            return;
        }
        if (riding != this.cart) {
            this.cart = riding;
            this.cartYaw = riding.rotationYaw;
            return;
        }
        float delta = MathHelper.wrapDegrees(riding.rotationYaw - this.cartYaw);
        this.cartYaw = riding.rotationYaw;
        if (delta != 0.0F && Math.abs(delta) <= 90.0F) {
            mc.player.rotationYaw += delta;
            mc.player.prevRotationYaw += delta;
        }
    }
}

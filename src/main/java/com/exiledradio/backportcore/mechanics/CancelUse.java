package com.exiledradio.backportcore.mechanics;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 26.4 Pre-Release 1's {@code ServerboundPlayerActionPacket.Action.CANCEL_USE_ITEM} on Core's channel
 * {@code backportcore_canceluse}: pausing the game while drawing a bow (or eating, or raising a shield) cancels the
 * use instead of releasing it, so the arrow no longer flies when the game goes on. The client side is
 * {@code mechanics.client.CancelUseClient}. Since Core 1.5.0.
 */
public final class CancelUse {

    public static final String CHANNEL = "backportcore_canceluse";

    private static SimpleNetworkWrapper channel;

    private CancelUse() {
    }

    /** From Core's pre-init, both sides. */
    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(Handler.class, Cancel.class, 0, Side.SERVER);
    }

    /** Client: tells the server the player stopped using their item without releasing it. */
    public static void send() {
        if (channel != null) {
            channel.sendToServer(new Cancel());
        }
    }

    /** The cancel: no payload. */
    public static final class Cancel implements IMessage {
        public Cancel() {
        }

        @Override
        public void fromBytes(ByteBuf buf) {
        }

        @Override
        public void toBytes(ByteBuf buf) {
        }
    }

    /** Server: {@code LivingEntity.cancelUsingItem}, 1.12's {@code resetActiveHand}. */
    public static final class Handler implements IMessageHandler<Cancel, IMessage> {
        @Override
        public IMessage onMessage(Cancel message, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.isHandActive()) {
                    player.resetActiveHand();
                }
            });
            return null;
        }
    }
}

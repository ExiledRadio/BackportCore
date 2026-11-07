package com.exiledradio.backportcore.pose;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Backport Core's network channel, used only by the pose engine: the server tells the players who can see a
 * player when that player's pose changes (1.14 syncs its pose as entity data). A client works out its own
 * player's pose itself, as 1.14's does, so nothing is sent to the player whose pose it is.
 *
 * <p>Nothing is sent while the engine is inactive. A backport that turns poses on requires itself on both
 * sides, so a client that gets these always has the library.
 */
public final class PoseNetwork {

    public static final String CHANNEL = "backportcore";

    private static SimpleNetworkWrapper channel;

    private PoseNetwork() {
    }

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(PosePacket.Handler.class, PosePacket.class, 0, Side.CLIENT);
    }

    static void sendToTrackers(EntityPlayer player, Pose pose) {
        if (channel != null) {
            channel.sendToAllTracking(new PosePacket(player.getEntityId(), pose), player);
        }
    }

    static void sendTo(EntityPlayerMP to, EntityPlayer whose, Pose pose) {
        if (channel != null) {
            channel.sendTo(new PosePacket(whose.getEntityId(), pose), to);
        }
    }

    /** Server to client: a player's pose (entity id, then the pose's ordinal as a byte). */
    public static final class PosePacket implements IMessage {

        int entityId;
        Pose pose = Pose.STANDING;

        public PosePacket() {
        }

        public PosePacket(int entityId, Pose pose) {
            this.entityId = entityId;
            this.pose = pose;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.entityId = buf.readInt();
            this.pose = Pose.byId(buf.readByte());
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(this.entityId);
            buf.writeByte(this.pose.ordinal());
        }

        public int entityId() {
            return this.entityId;
        }

        public Pose pose() {
            return this.pose;
        }

        /** Names no client type; the client work is reached through a call that names none either. */
        public static final class Handler implements IMessageHandler<PosePacket, IMessage> {
            @Override
            public IMessage onMessage(PosePacket message, MessageContext ctx) {
                com.exiledradio.backportcore.pose.client.PoseClient.receive(message.entityId, message.pose.ordinal());
                return null;
            }
        }
    }
}

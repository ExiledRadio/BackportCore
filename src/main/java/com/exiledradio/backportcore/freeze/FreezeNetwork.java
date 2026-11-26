package com.exiledradio.backportcore.freeze;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Freeze ticks' channel: an entity's counter and whether it is freezing, sent when that changes, not every tick
 * (vanilla's {@code DATA_TICKS_FROZEN} entity data, made cheap: the client counts between packets).
 */
public final class FreezeNetwork {

    public static final String CHANNEL = "backportcore_frz";

    private static SimpleNetworkWrapper channel;

    private FreezeNetwork() {
    }

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(Packet.Handler.class, Packet.class, 0, Side.CLIENT);
    }

    static void send(EntityLivingBase e, int ticks, int flags) {
        if (channel == null) {
            return;
        }
        Packet msg = new Packet(e.getEntityId(), ticks, flags);
        channel.sendToAllTracking(msg, e);
        if (e instanceof EntityPlayerMP) {
            sendTo((EntityPlayerMP) e, msg);
        }
    }

    static void sendTo(EntityPlayerMP player, int entityId, int ticks, int flags) {
        sendTo(player, new Packet(entityId, ticks, flags));
    }

    private static void sendTo(EntityPlayerMP player, Packet msg) {
        // Fake players, and players whose connection goes nowhere, have no channel to write to.
        if (channel == null || player instanceof FakePlayer || player.connection == null
                || player.connection.netManager.channel() == null) {
            return;
        }
        channel.sendTo(msg, player);
    }

    /** Server to client: entity id, ticks (0-140), flags. */
    public static final class Packet implements IMessage {

        int entityId;
        int ticks;
        int flags;

        public Packet() {
        }

        Packet(int entityId, int ticks, int flags) {
            this.entityId = entityId;
            this.ticks = ticks;
            this.flags = flags;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.entityId = ByteBufUtils.readVarInt(buf, 5);
            this.ticks = buf.readUnsignedByte();
            this.flags = buf.readUnsignedByte();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeVarInt(buf, this.entityId, 5);
            buf.writeByte(this.ticks);
            buf.writeByte(this.flags);
        }

        /** Names no client type: the work is in the client class, which only a client loads. */
        public static final class Handler implements IMessageHandler<Packet, IMessage> {
            @Override
            public IMessage onMessage(final Packet message, MessageContext ctx) {
                com.exiledradio.backportcore.freeze.client.FreezeClient.receive(message.entityId, message.ticks, message.flags);
                return null;
            }
        }
    }
}

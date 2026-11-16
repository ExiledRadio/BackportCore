package com.exiledradio.backportcore.presentation;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.function.LongConsumer;

/**
 * 1.20.2's ping request and pong ({@code ServerboundPingRequestPacket}, {@code ClientboundPongResponsePacket}) on Core's
 * channel {@code backportcore_ping}: the client sends the time, the server sends it straight back, and the F3 + 3 ping
 * chart logs the difference. Both sides; the client's reply handler is set by its client code. Since Core 1.3.2.
 */
public final class PingNetwork {

    public static final String CHANNEL = "backportcore_ping";

    private static SimpleNetworkWrapper channel;
    /** The client's pong handler (the time sent); null on a server. */
    public static volatile LongConsumer onPong;

    private PingNetwork() {
    }

    /** From Core's pre-init, both sides. */
    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(PingHandler.class, Ping.class, 0, Side.SERVER);
        channel.registerMessage(PongHandler.class, Pong.class, 1, Side.CLIENT);
    }

    /** Client: asks the server for a pong carrying {@code time}. */
    public static void ping(long time) {
        if (channel != null) {
            channel.sendToServer(new Ping(time));
        }
    }

    /** The request: the client's time in milliseconds. */
    public static final class Ping implements IMessage {
        long time;

        public Ping() {
        }

        Ping(long time) {
            this.time = time;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.time = buf.readLong();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeLong(this.time);
        }
    }

    /** The answer: the same time, back. */
    public static final class Pong implements IMessage {
        long time;

        public Pong() {
        }

        Pong(long time) {
            this.time = time;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.time = buf.readLong();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeLong(this.time);
        }
    }

    /** Server: answers at once, on the network thread, as 1.20.2's {@code handlePingRequest}. */
    public static final class PingHandler implements IMessageHandler<Ping, Pong> {
        @Override
        public Pong onMessage(Ping message, MessageContext ctx) {
            return new Pong(message.time);
        }
    }

    /** Client: hands the time to the chart. */
    public static final class PongHandler implements IMessageHandler<Pong, IMessage> {
        @Override
        public IMessage onMessage(Pong message, MessageContext ctx) {
            LongConsumer handler = onPong;
            if (handler != null) {
                handler.accept(message.time);
            }
            return null;
        }
    }
}

package com.exiledradio.backportcore.gameevent;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.function.Consumer;

/**
 * The game events' channel: the vibration particle. Nothing is sent unless a backport has game events on, and
 * such a backport requires itself (and so Core) on both sides.
 */
public final class GameEventNetwork {

    public static final String CHANNEL = "backportcore_ge";

    private static SimpleNetworkWrapper channel;

    private GameEventNetwork() {
    }

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(VibrationPacket.Handler.class, VibrationPacket.class, 0, Side.CLIENT);
    }

    /**
     * Shows a vibration leaving {@code from} for {@code target}, arriving in {@code ticks}, to the players within
     * {@code range} blocks of {@code from} (1.19.2's packet went 64 blocks; 1.20's {@code sendParticles} 32).
     */
    public static void sendVibration(WorldServer world, Vec3d from, PositionSource target, int ticks, double range) {
        if (channel != null) {
            channel.sendToAllAround(new VibrationPacket(from, target, ticks),
                    new NetworkRegistry.TargetPoint(world.provider.getDimension(), from.x, from.y, from.z, range));
        }
    }

    /**
     * Server to client: a vibration leaving {@code from} for a listener, arriving in {@code ticks} ticks; the
     * client draws the vibration particle along the way.
     */
    public static class VibrationPacket implements IMessage {

        public Vec3d from = Vec3d.ZERO;
        public NBTTagCompound target = new NBTTagCompound();
        public int ticks;

        public VibrationPacket() {
        }

        public VibrationPacket(Vec3d from, PositionSource target, int ticks) {
            this.from = from;
            target.write(this.target);
            this.ticks = ticks;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.from = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            this.target = ByteBufUtils.readTag(buf);
            this.ticks = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeDouble(this.from.x);
            buf.writeDouble(this.from.y);
            buf.writeDouble(this.from.z);
            ByteBufUtils.writeTag(buf, this.target);
            buf.writeInt(this.ticks);
        }

        /**
         * Hands the packet to the client's particle code ({@code gameevent.client.VibrationClient} sets
         * {@link #client} in pre-init); this class names no client type, so a dedicated server loads it safely.
         */
        public static class Handler implements IMessageHandler<VibrationPacket, IMessage> {
            public static Consumer<VibrationPacket> client = packet -> {
            };

            @Override
            public IMessage onMessage(VibrationPacket message, MessageContext ctx) {
                client.accept(message);
                return null;
            }
        }
    }
}

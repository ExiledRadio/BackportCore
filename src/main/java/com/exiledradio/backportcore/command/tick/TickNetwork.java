package com.exiledradio.backportcore.command.tick;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;

/**
 * 1.20.3's {@code ClientboundTickingStatePacket} (rate, frozen) and {@code ClientboundTickingStepPacket} (steps), on
 * their own channel. Sent to everyone on each change, and to a player on joining, respawning and changing
 * dimension (26.3's {@code PlayerList.sendLevelInfo}). A client without Backport Core ignores the channel.
 */
public final class TickNetwork {

    public static final String CHANNEL = "backportcore_tick";

    private static SimpleNetworkWrapper channel;

    private TickNetwork() {
    }

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(State.Handler.class, State.class, 0, Side.CLIENT);
        channel.registerMessage(Step.Handler.class, Step.class, 1, Side.CLIENT);
    }

    /** To one player, or to everyone when {@code to} is null. */
    static void sendState(@Nullable EntityPlayerMP to, TickRateManager manager) {
        send(to, new State(manager.tickrate(), manager.isFrozen()));
    }

    static void sendStep(@Nullable EntityPlayerMP to, TickRateManager manager) {
        send(to, new Step(manager.frozenTicksToRun()));
    }

    private static void send(@Nullable EntityPlayerMP to, IMessage message) {
        if (channel == null) {
            return;
        }
        if (to == null) {
            channel.sendToAll(message);
        } else {
            channel.sendTo(message, to);
        }
    }

    /** The client's side of both packets (on the client thread). */
    static void applyState(float rate, boolean frozen) {
        TickRateManager manager = Ticks.client();
        manager.setTickRate(rate);
        manager.setFrozen(frozen);
    }

    static void applyStep(int steps) {
        Ticks.client().setFrozenTicksToRun(steps);
    }

    public static final class State implements IMessage {

        float tickRate = 20.0F;
        boolean frozen;

        public State() {
        }

        State(float tickRate, boolean frozen) {
            this.tickRate = tickRate;
            this.frozen = frozen;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            tickRate = buf.readFloat();
            frozen = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeFloat(tickRate);
            buf.writeBoolean(frozen);
        }

        public static final class Handler implements IMessageHandler<State, IMessage> {
            @Override
            public IMessage onMessage(State message, MessageContext ctx) {
                com.exiledradio.backportcore.command.tick.client.ClientTicks.schedule(() -> applyState(message.tickRate, message.frozen));
                return null;
            }
        }
    }

    public static final class Step implements IMessage {

        int steps;

        public Step() {
        }

        Step(int steps) {
            this.steps = steps;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            steps = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(steps);
        }

        public static final class Handler implements IMessageHandler<Step, IMessage> {
            @Override
            public IMessage onMessage(Step message, MessageContext ctx) {
                com.exiledradio.backportcore.command.tick.client.ClientTicks.schedule(() -> applyStep(message.steps));
                return null;
            }
        }
    }
}

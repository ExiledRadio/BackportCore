package com.exiledradio.backportcore.command.swing;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.util.EnumHand;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The server's side of {@code /swing}: 26.3's {@code LivingEntity.swing(hand, animation, true)} and its
 * {@code SwingState.startIfAble}, and {@code ClientboundSwingAnimationPacket} on its own channel
 * ({@code backportcore_swing}), to every player tracking the entity and to the entity itself when it is a player.
 * 1.12.2's own swing packet has neither the animation nor the duration, and its client swings for a fixed time, so the
 * client's side ({@code client.ClientSwings}) drives the arm's progress itself. Vanilla swings stay 1.12.2's.
 */
public final class Swings {

    public static final String CHANNEL = "backportcore_swing";
    /** {@code SwingAnimation.DEFAULT}: whack, 6 ticks. */
    public static final int DEFAULT_DURATION = 6;

    private static SimpleNetworkWrapper channel;
    /** Swings this command started, by entity: {ticksExisted at the start, modified duration}. */
    private static final Map<EntityLivingBase, int[]> ACTIVE = new WeakHashMap<EntityLivingBase, int[]>();
    /** Set by the client: what a swing packet does there. */
    public static volatile ClientHandler client;

    private Swings() {
    }

    public interface ClientHandler {
        void start(int entityId, EnumHand hand, SwingType type, int duration);
    }

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(Packet.Handler.class, Packet.class, 0, Side.CLIENT);
    }

    /** 26.3's {@code getModifiedSwingDuration}: Haste shortens, Mining Fatigue lengthens (no lower bound, as 26.3). */
    public static int modifiedDuration(EntityLivingBase entity, int duration) {
        if (entity.isPotionActive(MobEffects.HASTE)) {
            return duration - (1 + entity.getActivePotionEffect(MobEffects.HASTE).getAmplifier());
        }
        if (entity.isPotionActive(MobEffects.MINING_FATIGUE)) {
            return duration + (1 + entity.getActivePotionEffect(MobEffects.MINING_FATIGUE).getAmplifier()) * 2;
        }
        return duration;
    }

    /**
     * {@code startIfAble}: refused while the current swing is past its first tick and not yet half done. The current
     * swing is this command's when one is running, else 1.12.2's own (6 ticks, as modified).
     *
     * @return whether the swing started (26.3 counts the entity either way)
     */
    public static boolean swing(EntityLivingBase entity, EnumHand hand, SwingType type, int duration) {
        int now = entity.ticksExisted;
        int[] current = ACTIVE.get(entity);
        if (current != null && now - current[0] < current[1]) {
            int ticks = now - current[0];
            if (ticks > 0 && ticks <= current[1] / 2) {
                return false;
            }
        } else if (entity.isSwingInProgress && entity.swingProgressInt > 0
                && entity.swingProgressInt <= modifiedDuration(entity, DEFAULT_DURATION) / 2) {
            return false;
        }
        int modified = modifiedDuration(entity, duration);
        ACTIVE.put(entity, new int[]{now, modified});
        if (channel != null && entity.world instanceof WorldServer) {
            Packet packet = new Packet(entity.getEntityId(), hand, type, modified);
            for (EntityPlayer player : ((WorldServer) entity.world).getEntityTracker().getTrackingPlayers(entity)) {
                if (player instanceof EntityPlayerMP) {
                    channel.sendTo(packet, (EntityPlayerMP) player);
                }
            }
            if (entity instanceof EntityPlayerMP) {
                channel.sendTo(packet, (EntityPlayerMP) entity);
            }
        }
        return true;
    }

    /** For the self-test: the duration of the swing this command last started on the entity, or -1. */
    public static int activeDuration(EntityLivingBase entity) {
        int[] current = ACTIVE.get(entity);
        return current == null ? -1 : current[1];
    }

    public static final class Packet implements IMessage {

        int entityId;
        EnumHand hand = EnumHand.MAIN_HAND;
        SwingType type = SwingType.WHACK;
        int duration = DEFAULT_DURATION;

        public Packet() {
        }

        Packet(int entityId, EnumHand hand, SwingType type, int duration) {
            this.entityId = entityId;
            this.hand = hand;
            this.type = type;
            this.duration = duration;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            entityId = buf.readInt();
            hand = buf.readBoolean() ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
            type = SwingType.byId(buf.readByte());
            duration = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(entityId);
            buf.writeBoolean(hand == EnumHand.OFF_HAND);
            buf.writeByte(type.id);
            buf.writeInt(duration);
        }

        public static final class Handler implements IMessageHandler<Packet, IMessage> {
            @Override
            public IMessage onMessage(Packet message, MessageContext ctx) {
                ClientHandler handler = client;
                if (handler != null) {
                    handler.start(message.entityId, message.hand, message.type, message.duration);
                }
                return null;
            }
        }
    }
}

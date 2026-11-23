package com.exiledradio.backportcore.light;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 1.14's light update packet ({@code ClientboundLightUpdatePacket}): light the server changes in a chunk its players
 * already have reaches them.
 *
 * <p>1.12.2 sends a chunk's light once, with the chunk; after that only block changes go out, and the client lights
 * around each changed block itself. Light the server changes without a block change there (a neighbor chunk's
 * decoration spilling over, light spreading in from a chunk generated later) never reaches the client: those cells
 * keep their old light until a block update nearby makes the client recompute them, and a re-render (F3+A) does not
 * help. Under water lit 1.13's way (Update Aquatic: one level a block) that shows as whole chunks of sea floor
 * brighter or darker than the ones around them at night.
 *
 * <p>Every light change on a server world already passes {@link IWorldEventListener#notifyLightSet} ({@code
 * World.setLightFor} and {@code Chunk.relightBlock}; the server's own listener ignores it). A listener here collects
 * the changed sections per chunk; at the end of the world's tick, each collected chunk whose players have it gets the
 * sky and block light of those sections, as 1.14 sends them. Chunks not sent yet are skipped: they go out whole, with
 * their light as it is then.
 */
public final class LightUpdates {

    public static final String CHANNEL = "backportcore_light";
    /** A section's bit in the masks: section y (signed, y >> 4) plus this, so y -256 to 767 fit a long. */
    private static final int BIAS = 16;

    private static SimpleNetworkWrapper channel;

    private LightUpdates() {
    }

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(LightPacket.Handler.class, LightPacket.class, 0, Side.CLIENT);
    }

    /** The bit of a section y in a mask; 0 outside the range a long holds. */
    static long bit(int sectionY) {
        int b = sectionY + BIAS;
        return b < 0 || b > 63 ? 0L : 1L << b;
    }

    /** The changed sections of a server world, by chunk. */
    static final class Listener implements IWorldEventListener {
        final WorldServer world;
        final Long2LongOpenHashMap changed = new Long2LongOpenHashMap();

        Listener(WorldServer world) {
            this.world = world;
        }

        @Override
        public void notifyLightSet(BlockPos pos) {
            long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            this.changed.put(key, this.changed.get(key) | bit(pos.getY() >> 4));
        }

        /** Sends the changes of the chunks players have, and forgets every change. */
        void flush() {
            if (this.changed.isEmpty()) {
                return;
            }
            for (Long2LongMap.Entry e : this.changed.long2LongEntrySet()) {
                long key = e.getLongKey();
                int x = (int) key;
                int z = (int) (key >> 32);
                PlayerChunkMapEntry entry = this.world.getPlayerChunkMap().getEntry(x, z);
                if (entry == null || !entry.isSentToPlayers() || entry.getChunk() == null || channel == null) {
                    continue;
                }
                LightPacket packet = LightPacket.of(entry.getChunk(), e.getLongValue());
                if (packet != null) {
                    entry.sendPacket(channel.getPacketFrom(packet));
                }
            }
            this.changed.clear();
        }

        @Override
        public void notifyBlockUpdate(World worldIn, BlockPos pos, net.minecraft.block.state.IBlockState oldState,
                net.minecraft.block.state.IBlockState newState, int flags) {
        }

        @Override
        public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {
        }

        @Override
        public void playSoundToAllNearExcept(@Nullable EntityPlayer player, SoundEvent soundIn, SoundCategory category,
                double x, double y, double z, float volume, float pitch) {
        }

        @Override
        public void playRecord(SoundEvent soundIn, BlockPos pos) {
        }

        @Override
        public void spawnParticle(int particleID, boolean ignoreRange, double xCoord, double yCoord, double zCoord,
                double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        }

        @Override
        public void spawnParticle(int id, boolean ignoreRange, boolean minimiseParticleLevel, double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed, int... parameters) {
        }

        @Override
        public void onEntityAdded(Entity entityIn) {
        }

        @Override
        public void onEntityRemoved(Entity entityIn) {
        }

        @Override
        public void broadcastSound(int soundID, BlockPos pos, int data) {
        }

        @Override
        public void playEvent(EntityPlayer player, int type, BlockPos blockPosIn, int data) {
        }

        @Override
        public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {
        }
    }

    public static final class Events {
        private final List<Listener> listeners = new ArrayList<Listener>();

        @SubscribeEvent
        public void load(WorldEvent.Load event) {
            if (event.getWorld() instanceof WorldServer) {
                Listener l = new Listener((WorldServer) event.getWorld());
                event.getWorld().addEventListener(l);
                this.listeners.add(l);
            }
        }

        @SubscribeEvent
        public void unload(WorldEvent.Unload event) {
            this.listeners.removeIf(l -> l.world == event.getWorld());
        }

        @SubscribeEvent
        public void tick(TickEvent.WorldTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)) {
                return;
            }
            for (Listener l : this.listeners) {
                if (l.world == event.world) {
                    l.flush();
                }
            }
        }
    }

    /**
     * Server to client: the sky and block light of some sections of a chunk (each section's y and its two nibble
     * arrays; a world without sky light sends none).
     */
    public static class LightPacket implements IMessage {
        public int x;
        public int z;
        public int[] sectionY = new int[0];
        public byte[][] sky = new byte[0][];
        public byte[][] block = new byte[0][];

        public LightPacket() {
        }

        /** The sections of {@code mask} the chunk holds, or null if it holds none of them. */
        @Nullable
        static LightPacket of(Chunk chunk, long mask) {
            List<ExtendedBlockStorage> found = new ArrayList<ExtendedBlockStorage>();
            for (ExtendedBlockStorage s : chunk.getBlockStorageArray()) {
                if (s != Chunk.NULL_BLOCK_STORAGE && (mask & bit(s.getYLocation() >> 4)) != 0) {
                    found.add(s);
                }
            }
            if (found.isEmpty()) {
                return null;
            }
            LightPacket p = new LightPacket();
            p.x = chunk.x;
            p.z = chunk.z;
            int n = found.size();
            p.sectionY = new int[n];
            p.sky = new byte[n][];
            p.block = new byte[n][];
            for (int i = 0; i < n; i++) {
                ExtendedBlockStorage s = found.get(i);
                p.sectionY[i] = s.getYLocation() >> 4;
                p.block[i] = s.getBlockLight().getData().clone();
                p.sky[i] = s.getSkyLight() == null ? null : s.getSkyLight().getData().clone();
            }
            return p;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.x = buf.readInt();
            this.z = buf.readInt();
            int n = buf.readUnsignedByte();
            this.sectionY = new int[n];
            this.sky = new byte[n][];
            this.block = new byte[n][];
            for (int i = 0; i < n; i++) {
                this.sectionY[i] = buf.readByte();
                this.block[i] = new byte[2048];
                buf.readBytes(this.block[i]);
                if (buf.readBoolean()) {
                    this.sky[i] = new byte[2048];
                    buf.readBytes(this.sky[i]);
                }
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(this.x);
            buf.writeInt(this.z);
            buf.writeByte(this.sectionY.length);
            for (int i = 0; i < this.sectionY.length; i++) {
                buf.writeByte(this.sectionY[i]);
                buf.writeBytes(this.block[i]);
                buf.writeBoolean(this.sky[i] != null);
                if (this.sky[i] != null) {
                    buf.writeBytes(this.sky[i]);
                }
            }
        }

        /**
         * Hands the packet to the client ({@code light.client.LightUpdatesClient} sets {@link #client} in pre-init);
         * this class names no client type, so a dedicated server loads it safely.
         */
        public static class Handler implements IMessageHandler<LightPacket, IMessage> {
            public static Consumer<LightPacket> client = packet -> {
            };

            @Override
            public IMessage onMessage(LightPacket message, MessageContext ctx) {
                client.accept(message);
                return null;
            }
        }
    }
}

package com.exiledradio.backportcore.worldgen;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Underground biomes in 1.12.2's column biomes: a backport's own regions, by column and height, that the debug screen,
 * grass, foliage and water colors and {@link #biomeAt} read in place of the column's biome. Since Core 1.4.4 (Game Drops
 * Backport's sulfur caves).
 *
 * <p>Since 1.18 a biome is three-dimensional and the client reads it at the block (F3's "Biome:", the 3 by 3 blend of
 * grass and water colors). 1.12.2's is one per column, so a backport that places a biome under ground registers each
 * column's span ({@link #set}, server side, while the column's chunk is loaded, usually from population). Spans are
 * saved with their chunk (its {@code backportcore_underground} array) and sent to every player that sees the chunk,
 * again when they change. A world nobody registers into costs one map lookup per call.
 *
 * <p>Colors: patch P44 makes {@code BiomeColorHelper}'s blend read {@link #colorBiome} (the span's biome where there is
 * one); {@code water.WaterColors.at} reads {@link #at} first.
 */
public final class UndergroundBiomes {

    public static final String CHANNEL = "backportcore_ub";
    private static final String TAG = "backportcore_underground";

    private static SimpleNetworkWrapper channel;

    /** Server: per world, chunk key to its spans (4 ints each: column index z*16+x, from y, to y exclusive, biome id). */
    private static final Map<World, Map<Long, int[]>> SERVER = new WeakHashMap<World, Map<Long, int[]>>();
    /** Server: chunks whose spans changed this tick, to send again. */
    private static final Map<World, Set<Long>> CHANGED = new WeakHashMap<World, Set<Long>>();
    /** Client: the current world's chunks. Read from chunk render threads too. */
    private static final Map<Long, int[]> CLIENT = new ConcurrentHashMap<Long, int[]>();
    private static volatile int clientDimension = Integer.MIN_VALUE;

    /** Set by Core's client code: redraws a chunk whose spans arrived. */
    public static volatile Consumer<long[]> redraw = k -> {
    };

    private UndergroundBiomes() {
    }

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(SpansMessage.Handler.class, SpansMessage.class, 0, Side.CLIENT);
    }

    // ------------------------------------------------------------------------------------------------- the API

    /**
     * Sets a column's underground biome from {@code fromY} to {@code toYExclusive}, over what spans were there (null
     * biome: clears the range). Server side; the column's chunk must be loaded (else nothing happens).
     */
    public static void set(World world, int x, int z, int fromY, int toYExclusive, @Nullable Biome biome) {
        if (world.isRemote || toYExclusive <= fromY) {
            return;
        }
        Chunk chunk = world.getChunkProvider().getLoadedChunk(x >> 4, z >> 4);
        if (chunk == null) {
            return;
        }
        long key = key(x >> 4, z >> 4);
        int column = (z & 15) << 4 | (x & 15);
        synchronized (SERVER) {
            Map<Long, int[]> map = server(world);
            int[] old = map.get(key);
            int[] spans = cut(old == null ? new int[0] : old, column, fromY, toYExclusive);
            if (biome != null) {
                int n = spans.length;
                spans = Arrays.copyOf(spans, n + 4);
                spans[n] = column;
                spans[n + 1] = fromY;
                spans[n + 2] = toYExclusive;
                spans[n + 3] = Biome.getIdForBiome(biome);
            }
            if (spans.length == 0) {
                map.remove(key);
            } else {
                map.put(key, spans);
            }
            Set<Long> changed = CHANGED.get(world);
            if (changed == null) {
                changed = new LinkedHashSet<Long>();
                CHANGED.put(world, changed);
            }
            changed.add(key);
        }
        chunk.markDirty();
    }

    /**
     * Sets a column's spans of one biome from a mask over the world's height ({@code mask[i]} for y = minY + i): every
     * run of true is one span; the rest of the column loses that biome's spans. A null mask clears them.
     */
    public static void setColumn(World world, int x, int z, int minY, @Nullable boolean[] mask, Biome biome) {
        clearBiome(world, x, z, biome);
        if (mask == null) {
            return;
        }
        int i = 0;
        while (i < mask.length) {
            if (!mask[i]) {
                i++;
                continue;
            }
            int j = i;
            while (j < mask.length && mask[j]) {
                j++;
            }
            set(world, x, z, minY + i, minY + j, biome);
            i = j;
        }
    }

    private static void clearBiome(World world, int x, int z, Biome biome) {
        if (world.isRemote) {
            return;
        }
        int id = Biome.getIdForBiome(biome);
        int column = (z & 15) << 4 | (x & 15);
        int[] spans;
        synchronized (SERVER) {
            spans = server(world).get(key(x >> 4, z >> 4));
        }
        if (spans == null) {
            return;
        }
        for (int i = 0; i < spans.length; i += 4) {
            if (spans[i] == column && spans[i + 3] == id) {
                set(world, x, z, spans[i + 1], spans[i + 2], null);
                clearBiome(world, x, z, biome);
                return;
            }
        }
    }

    /** @return the underground biome registered at a block, or null (either side; the client knows the chunks it has) */
    @Nullable
    public static Biome at(World world, BlockPos pos) {
        int[] spans;
        long key = key(pos.getX() >> 4, pos.getZ() >> 4);
        if (world.isRemote) {
            if (CLIENT.isEmpty() || world.provider.getDimension() != clientDimension) {
                return null;
            }
            spans = CLIENT.get(key);
        } else {
            synchronized (SERVER) {
                Map<Long, int[]> map = SERVER.get(world);
                spans = map == null ? null : map.get(key);
            }
        }
        return find(spans, pos);
    }

    /** @return the underground biome at a block, else the column's ({@code world.getBiome}) */
    public static Biome biomeAt(World world, BlockPos pos) {
        Biome b = at(world, pos);
        return b != null ? b : world.getBiome(pos);
    }

    /**
     * Patch P44's hook in {@code BiomeColorHelper.getColorAtPos} (client only; chunk render threads too): the client's
     * underground biome at the block, else what the access says.
     */
    public static Biome colorBiome(IBlockAccess access, BlockPos pos) {
        if (!CLIENT.isEmpty()) {
            Biome b = find(CLIENT.get(key(pos.getX() >> 4, pos.getZ() >> 4)), pos);
            if (b != null) {
                return b;
            }
        }
        return access.getBiome(pos);
    }

    /** @return how many chunks with spans the client holds (tests) */
    public static int clientChunks() {
        return CLIENT.size();
    }

    // ------------------------------------------------------------------------------------------------- spans

    static long key(int cx, int cz) {
        return (long) cx << 32 | (cz & 0xFFFFFFFFL);
    }

    @Nullable
    private static Biome find(@Nullable int[] spans, BlockPos pos) {
        if (spans == null) {
            return null;
        }
        int column = (pos.getZ() & 15) << 4 | (pos.getX() & 15);
        int y = pos.getY();
        for (int i = 0; i < spans.length; i += 4) {
            if (spans[i] == column && y >= spans[i + 1] && y < spans[i + 2]) {
                return Biome.getBiome(spans[i + 3], null);
            }
        }
        return null;
    }

    /** @return the spans with the column's range [from, to) taken out (a span across it splits in two) */
    static int[] cut(int[] spans, int column, int from, int to) {
        List<int[]> out = new ArrayList<int[]>();
        for (int i = 0; i < spans.length; i += 4) {
            int c = spans[i], a = spans[i + 1], b = spans[i + 2], id = spans[i + 3];
            if (c != column || b <= from || a >= to) {
                out.add(new int[] {c, a, b, id});
                continue;
            }
            if (a < from) {
                out.add(new int[] {c, a, from, id});
            }
            if (b > to) {
                out.add(new int[] {c, to, b, id});
            }
        }
        int[] r = new int[out.size() * 4];
        for (int i = 0; i < out.size(); i++) {
            System.arraycopy(out.get(i), 0, r, i * 4, 4);
        }
        return r;
    }

    private static Map<Long, int[]> server(World world) {
        Map<Long, int[]> map = SERVER.get(world);
        if (map == null) {
            map = new HashMap<Long, int[]>();
            SERVER.put(world, map);
        }
        return map;
    }

    @Nullable
    private static int[] spans(World world, int cx, int cz) {
        synchronized (SERVER) {
            Map<Long, int[]> map = SERVER.get(world);
            return map == null ? null : map.get(key(cx, cz));
        }
    }

    // ------------------------------------------------------------------------------------------------- client

    static void receive(int dimension, int cx, int cz, int[] spans) {
        if (dimension != clientDimension) {
            CLIENT.clear();
            clientDimension = dimension;
        }
        if (spans.length == 0) {
            CLIENT.remove(key(cx, cz));
        } else {
            CLIENT.put(key(cx, cz), spans);
        }
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < spans.length; i += 4) {
            lo = Math.min(lo, spans[i + 1]);
            hi = Math.max(hi, spans[i + 2]);
        }
        redraw.accept(new long[] {cx, cz, lo == Integer.MAX_VALUE ? 0 : lo, hi == Integer.MIN_VALUE ? 0 : hi});
    }

    /** The client left its world or unloaded a chunk. */
    public static void clientForget(@Nullable int[] chunk) {
        if (chunk == null) {
            CLIENT.clear();
            clientDimension = Integer.MIN_VALUE;
        } else {
            CLIENT.remove(key(chunk[0], chunk[1]));
        }
    }

    /** Server to client: one chunk's spans (all of them; none clears it). */
    public static final class SpansMessage implements IMessage {
        int dimension, cx, cz;
        int[] spans = new int[0];

        public SpansMessage() {
        }

        SpansMessage(int dimension, int cx, int cz, int[] spans) {
            this.dimension = dimension;
            this.cx = cx;
            this.cz = cz;
            this.spans = spans;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.dimension = buf.readInt();
            this.cx = buf.readInt();
            this.cz = buf.readInt();
            int n = buf.readUnsignedShort();
            this.spans = new int[n * 4];
            for (int i = 0; i < n; i++) {
                this.spans[i * 4] = buf.readUnsignedByte();
                this.spans[i * 4 + 1] = buf.readShort();
                this.spans[i * 4 + 2] = buf.readShort();
                this.spans[i * 4 + 3] = buf.readInt();
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(this.dimension);
            buf.writeInt(this.cx);
            buf.writeInt(this.cz);
            int n = this.spans.length / 4;
            buf.writeShort(n);
            for (int i = 0; i < n; i++) {
                buf.writeByte(this.spans[i * 4]);
                buf.writeShort(this.spans[i * 4 + 1]);
                buf.writeShort(this.spans[i * 4 + 2]);
                buf.writeInt(this.spans[i * 4 + 3]);
            }
        }

        public static final class Handler implements IMessageHandler<SpansMessage, IMessage> {
            @Override
            public IMessage onMessage(SpansMessage message, MessageContext ctx) {
                FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(
                        () -> receive(message.dimension, message.cx, message.cz, message.spans));
                return null;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------- events

    /** Forge's events (both sides); registered by Core. */
    public static final class Events {

        @SubscribeEvent
        public void onLoad(ChunkDataEvent.Load event) {
            World world = event.getWorld();
            if (world.isRemote || !event.getData().hasKey(TAG, 11)) {
                return;
            }
            int[] spans = event.getData().getIntArray(TAG);
            if (spans.length == 0 || spans.length % 4 != 0) {
                return;
            }
            synchronized (SERVER) {
                server(world).put(key(event.getChunk().x, event.getChunk().z), spans);
            }
        }

        @SubscribeEvent
        public void onSave(ChunkDataEvent.Save event) {
            World world = event.getWorld();
            if (world.isRemote) {
                return;
            }
            Chunk chunk = event.getChunk();
            int[] spans = spans(world, chunk.x, chunk.z);
            if (spans != null) {
                event.getData().setIntArray(TAG, spans);
            }
            // 1.12.2 unloads a chunk (onUnload) before it saves it: forget it only now.
            if (!chunk.isLoaded()) {
                synchronized (SERVER) {
                    Map<Long, int[]> map = SERVER.get(world);
                    if (map != null) {
                        map.remove(key(chunk.x, chunk.z));
                    }
                }
            }
        }

        @SubscribeEvent
        public void onUnload(ChunkEvent.Unload event) {
            if (event.getWorld().isRemote) {
                clientForget(new int[] {event.getChunk().x, event.getChunk().z});
            }
        }

        @SubscribeEvent
        public void onWorldUnload(WorldEvent.Unload event) {
            if (event.getWorld().isRemote) {
                clientForget(null);
            } else {
                synchronized (SERVER) {
                    SERVER.remove(event.getWorld());
                    CHANGED.remove(event.getWorld());
                }
            }
        }

        @SubscribeEvent
        public void onWatch(ChunkWatchEvent.Watch event) {
            EntityPlayerMP player = event.getPlayer();
            World world = player.world;
            Chunk chunk = event.getChunkInstance();
            int cx = chunk != null ? chunk.x : event.getChunk().x, cz = chunk != null ? chunk.z : event.getChunk().z;
            int[] spans = spans(world, cx, cz);
            if (spans != null && channel != null) {
                channel.sendTo(new SpansMessage(world.provider.getDimension(), cx, cz, spans), player);
            }
        }

        @SubscribeEvent
        public void onTick(TickEvent.WorldTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer) || channel == null) {
                return;
            }
            List<Long> changed;
            synchronized (SERVER) {
                Set<Long> set = CHANGED.get(event.world);
                if (set == null || set.isEmpty()) {
                    return;
                }
                changed = new ArrayList<Long>(set);
                set.clear();
            }
            WorldServer world = (WorldServer) event.world;
            for (long key : changed) {
                int cx = (int) (key >> 32), cz = (int) key;
                PlayerChunkMapEntry entry = world.getPlayerChunkMap().getEntry(cx, cz);
                if (entry == null || !entry.isSentToPlayers()) {
                    // Sent on watch, once the chunk itself is sent.
                    continue;
                }
                int[] spans = spans(world, cx, cz);
                entry.sendPacket(channel.getPacketFrom(new SpansMessage(world.provider.getDimension(), cx, cz,
                        spans == null ? new int[0] : spans)));
            }
        }
    }
}

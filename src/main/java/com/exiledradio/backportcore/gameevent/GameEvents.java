package com.exiledradio.backportcore.gameevent;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/**
 * The family's game event dispatcher: 1.19's and 1.20's {@code ServerLevel.gameEvent} and their listener lists,
 * shared by every backport, so a sensor of one mod hears what another mod's mob does.
 *
 * <h2>Turning it on</h2>
 * Nothing is emitted, and {@link GameEventEmitters} does no work, until a mod {@linkplain #require requires}
 * game events. Each requirement names the version whose rules it brings ("1.19" for Wild Update Backport,
 * "1.20" for Trails &amp; Tales Backport) and a switch (the mod's own setting); {@link #isAtLeast} answers from
 * the requirements whose switch is on, and {@link GameEventTags#vibrations()} follows it: with a 1.20 backport
 * installed, everyone's sensors hear 1.20's vibrations.
 *
 * <h2>Dispatch</h2>
 * <ul>
 *   <li>Listeners are kept per world, by chunk; an entity listener calls {@link #move} as it moves.</li>
 *   <li>An event reaches every {@linkplain GameEventListener#active active} listener within the listener's own
 *       radius, looked for in the chunks within the event's radius. How the radius is measured and when a
 *       listener hears follow its {@link GameEventListener.Rules}.</li>
 *   <li>{@link GameEventListener#immediate} listeners hear it at once; 1.20 listeners next, closest first; 1.19
 *       listeners' messages wait until the end of the world's tick, then are handed out closest first (events
 *       emitted while handling wait for the next tick's round, as in 1.19).</li>
 * </ul>
 * Server only: emitting on a client does nothing.
 */
public final class GameEvents {

    private static final Map<World, Dispatcher> WORLDS = new WeakHashMap<World, Dispatcher>();
    private static final List<Requirement> REQUIRED = new ArrayList<Requirement>();

    private GameEvents() {
    }

    // --- who wants game events ----------------------------------------------------------------

    private static final class Requirement {
        final int[] version;
        final String text;
        final BooleanSupplier enabled;

        Requirement(String version, BooleanSupplier enabled) {
            this.text = version;
            this.version = parse(version);
            this.enabled = enabled;
        }
    }

    /**
     * Turns game events on for a mod, from its construction or pre-init.
     *
     * @param version the Minecraft version whose game event rules the mod brings ("1.19", "1.20")
     * @param enabled the mod's own switch, read each time (game events stop when every switch is off)
     */
    public static synchronized void require(String version, BooleanSupplier enabled) {
        REQUIRED.add(new Requirement(version, enabled));
    }

    /** @return true if some mod requires game events and its switch is on */
    public static boolean active() {
        for (Requirement r : REQUIRED) {
            if (r.enabled.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    /** @return true if a mod whose switch is on brings this version's rules or a newer one's */
    public static boolean isAtLeast(String version) {
        int[] want = parse(version);
        for (Requirement r : REQUIRED) {
            if (compare(r.version, want) >= 0 && r.enabled.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    /** @return the newest required version whose switch is on, or null */
    public static String newest() {
        Requirement best = null;
        for (Requirement r : REQUIRED) {
            if (r.enabled.getAsBoolean() && (best == null || compare(r.version, best.version) > 0)) {
                best = r;
            }
        }
        return best == null ? null : best.text;
    }

    static int[] parse(String version) {
        String[] parts = version.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    static int compare(int[] a, int[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return 0;
    }

    // --- listeners and events -----------------------------------------------------------------

    private static Dispatcher of(World world) {
        Dispatcher d = WORLDS.get(world);
        if (d == null) {
            d = new Dispatcher();
            WORLDS.put(world, d);
        }
        return d;
    }

    public static void add(World world, GameEventListener listener) {
        if (!world.isRemote) {
            of(world).add(world, listener);
        }
    }

    public static void remove(World world, GameEventListener listener) {
        if (!world.isRemote) {
            of(world).remove(listener);
        }
    }

    /** For entity listeners: call as the entity moves (cheap when it stays in its chunk). */
    public static void move(World world, GameEventListener listener) {
        if (!world.isRemote) {
            of(world).move(world, listener);
        }
    }

    /** At the block's center. */
    public static void emit(World world, GameEvent event, BlockPos pos, GameEventContext context) {
        emit(world, event, new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D), context);
    }

    public static void emit(World world, GameEvent event, Vec3d pos, GameEventContext context) {
        if (world.isRemote || !(world instanceof WorldServer) || !active()) {
            return;
        }
        of(world).emit((WorldServer) world, event, pos, context);
    }

    /** @return how many listeners a world has, for the tests */
    public static int listenerCount(World world) {
        Dispatcher d = WORLDS.get(world);
        return d == null ? 0 : d.where.size();
    }

    /** Hands out the tick's queued 1.19 messages, closest first. Public for the tests, which run it directly. */
    public static void flush(WorldServer world) {
        Dispatcher d = WORLDS.get(world);
        if (d != null) {
            d.flush(world);
        }
    }

    /** Registered on Forge's bus by Backport Core. */
    public static final class Events {
        @SubscribeEvent
        public void tick(TickEvent.WorldTickEvent event) {
            if (event.phase == TickEvent.Phase.END && event.world instanceof WorldServer) {
                flush((WorldServer) event.world);
            }
        }

        @SubscribeEvent
        public void unload(WorldEvent.Unload event) {
            WORLDS.remove(event.getWorld());
        }
    }

    /** One delivery: 1.19's {@code GameEvent.Message} / 1.20's {@code GameEvent.ListenerInfo}, by squared distance. */
    private static final class Message implements Comparable<Message> {
        final GameEvent event;
        final Vec3d origin;
        final GameEventContext context;
        final GameEventListener listener;
        final double distanceSq;

        Message(GameEvent event, Vec3d origin, GameEventContext context, GameEventListener listener, double distanceSq) {
            this.event = event;
            this.origin = origin;
            this.context = context;
            this.listener = listener;
            this.distanceSq = distanceSq;
        }

        @Override
        public int compareTo(Message o) {
            return Double.compare(this.distanceSq, o.distanceSq);
        }
    }

    private static double blockDistanceSq(Vec3d a, Vec3d b) {
        double dx = Math.floor(a.x) - Math.floor(b.x);
        double dy = Math.floor(a.y) - Math.floor(b.y);
        double dz = Math.floor(a.z) - Math.floor(b.z);
        return dx * dx + dy * dy + dz * dz;
    }

    private static final class Dispatcher {
        final Map<Long, List<GameEventListener>> byChunk = new HashMap<Long, List<GameEventListener>>();
        final Map<GameEventListener, Long> where = new IdentityHashMap<GameEventListener, Long>();
        List<Message> queue = new ArrayList<Message>();

        private static long key(Vec3d p) {
            return ChunkPos.asLong(((int) Math.floor(p.x)) >> 4, ((int) Math.floor(p.z)) >> 4);
        }

        void add(World world, GameEventListener l) {
            Vec3d p = l.position().position(world);
            if (p == null || this.where.containsKey(l)) {
                return;
            }
            long k = key(p);
            this.where.put(l, k);
            List<GameEventListener> list = this.byChunk.get(k);
            if (list == null) {
                list = new ArrayList<GameEventListener>(2);
                this.byChunk.put(k, list);
            }
            list.add(l);
        }

        void remove(GameEventListener l) {
            Long k = this.where.remove(l);
            if (k != null) {
                List<GameEventListener> list = this.byChunk.get(k);
                if (list != null) {
                    list.remove(l);
                    if (list.isEmpty()) {
                        this.byChunk.remove(k);
                    }
                }
            }
        }

        void move(World world, GameEventListener l) {
            Vec3d p = l.position().position(world);
            Long k = this.where.get(l);
            if (p == null) {
                remove(l);
            } else if (k == null || k != key(p)) {
                remove(l);
                add(world, l);
            }
        }

        void emit(WorldServer world, GameEvent event, Vec3d origin, GameEventContext context) {
            if (this.byChunk.isEmpty()) {
                return;
            }
            int r = event.radius();
            int x0 = ((int) Math.floor(origin.x) - r) >> 4;
            int x1 = ((int) Math.floor(origin.x) + r) >> 4;
            int z0 = ((int) Math.floor(origin.z) - r) >> 4;
            int z1 = ((int) Math.floor(origin.z) + r) >> 4;
            List<Message> now = null;
            List<Message> byDistance = null;
            for (int cx = x0; cx <= x1; cx++) {
                for (int cz = z0; cz <= z1; cz++) {
                    List<GameEventListener> list = this.byChunk.get(ChunkPos.asLong(cx, cz));
                    if (list == null) {
                        continue;
                    }
                    for (GameEventListener l : new ArrayList<GameEventListener>(list)) {
                        if (!l.active()) {
                            continue;
                        }
                        Vec3d p = l.position().position(world);
                        if (p == null) {
                            continue;
                        }
                        boolean modern = l.rules() == GameEventListener.Rules.V1_20;
                        double rangeSq = modern ? blockDistanceSq(p, origin) : p.squareDistanceTo(origin);
                        if (rangeSq > (double) l.radius() * l.radius()) {
                            continue;
                        }
                        Message m = new Message(event, origin, context, l, p.squareDistanceTo(origin));
                        if (l.immediate()) {
                            if (now == null) {
                                now = new ArrayList<Message>();
                            }
                            now.add(m);
                        } else if (modern) {
                            if (byDistance == null) {
                                byDistance = new ArrayList<Message>();
                            }
                            byDistance.add(m);
                        } else {
                            this.queue.add(m);
                        }
                    }
                }
            }
            if (now != null) {
                deliver(world, now);
            }
            if (byDistance != null) {
                deliver(world, byDistance);
            }
        }

        void flush(WorldServer world) {
            if (this.queue.isEmpty()) {
                return;
            }
            List<Message> round = this.queue;
            this.queue = new ArrayList<Message>();
            deliver(world, round);
        }

        private static void deliver(WorldServer world, List<Message> messages) {
            Collections.sort(messages);
            for (Message m : messages) {
                try {
                    m.listener.handle(world, m.event, m.origin, m.context);
                } catch (RuntimeException e) {
                    BackportCore.LOGGER.error("A game event listener failed on {}", m.event, e);
                }
            }
        }
    }
}

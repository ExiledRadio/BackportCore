package com.exiledradio.backportcore.gameevent;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

/**
 * 1.20.6's vibration system ({@code VibrationSystem} with its {@code Data}, {@code Listener}, {@code Ticker},
 * {@code User}, {@code VibrationInfo} and {@code VibrationSelector}), for 1.20 backports' sensors.
 * {@link VibrationListener} keeps 1.19.2's rules for Wild Update Backport.
 *
 * <p>How 1.20 hears:
 * <ul>
 *   <li>The dispatcher hands an event to the {@link Listener} at once, closest listener first. A listener
 *       already receiving a vibration hears nothing; then the event must be in the user's
 *       {@linkplain User#listenableEvents listenable set} and {@linkplain User#isValidVibration valid}
 *       (spectators, sneaking through {@code #ignore_vibrations_sneaking}, wool), the user must
 *       {@linkplain User#canReceiveVibration accept it}, and the six lines to it must not all pass through wool.</li>
 *   <li>An accepted event is a <i>candidate</i> ({@link Data}'s selector): within one game tick the closest
 *       candidate wins, and at equal distance the higher frequency. On a later tick the owner's
 *       {@link #tick} takes it: the vibration particle leaves, and after {@code floor(distance)} ticks
 *       {@link User#onReceiveVibration} runs (only once the 3x3 chunks around the listener are loaded, for users
 *       that {@linkplain User#requiresAdjacentChunksToBeTicking ask}).</li>
 *   <li>Everything in flight and the selector's candidate are saved ({@link Data#write}), as 1.20's
 *       {@code listener} tag.</li>
 * </ul>
 */
public final class VibrationSystem {

    private VibrationSystem() {
    }

    /** {@code VibrationSystem.getGameEventFrequency}. */
    public static int frequency(GameEvent event) {
        return event.frequency();
    }

    /** {@code getRedstoneStrengthForDistance}: 15 at the listener, down to 1 at the edge of its radius. */
    public static int redstoneStrengthForDistance(float distance, int radius) {
        double step = 15.0D / (double) radius;
        return Math.max(1, 15 - MathHelper.floor(step * (double) distance));
    }

    /** {@code Listener.distanceBetweenInBlocks}. */
    public static float distanceBetweenInBlocks(BlockPos a, BlockPos b) {
        return (float) Math.sqrt(a.distanceSq(b));
    }

    // --- the user -------------------------------------------------------------------------------

    /** What owns a vibration listener: 1.20's {@code VibrationSystem.User}. */
    public interface User {

        int listenerRadius();

        PositionSource positionSource();

        boolean canReceiveVibration(WorldServer world, BlockPos origin, GameEvent event, GameEventContext context);

        void onReceiveVibration(WorldServer world, BlockPos origin, GameEvent event, @Nullable Entity source,
                                @Nullable Entity projectileOwner, float distance);

        default Set<GameEvent> listenableEvents() {
            return GameEventTags.vibrations();
        }

        /** True for sensors: sneaking past them earns <i>Sneak 100</i>. */
        default boolean canTriggerAvoidVibration() {
            return false;
        }

        default boolean requiresAdjacentChunksToBeTicking() {
            return false;
        }

        default int calculateTravelTimeInTicks(float distance) {
            return MathHelper.floor(distance);
        }

        default boolean isValidVibration(GameEvent event, GameEventContext context) {
            if (!listenableEvents().contains(event)) {
                return false;
            }
            Entity source = context.source;
            if (source != null) {
                if (source instanceof EntityPlayer && ((EntityPlayer) source).isSpectator()) {
                    return false;
                }
                // 1.20's isSteppingCarefully: sneaking (1.12.2 has no separate crouching pose here).
                if (source.isSneaking() && GameEventTags.IGNORE_VIBRATIONS_SNEAKING.contains(event)) {
                    if (canTriggerAvoidVibration() && source instanceof EntityPlayerMP) {
                        AvoidVibrationTrigger.INSTANCE.trigger((EntityPlayerMP) source);
                    }
                    return false;
                }
                if (GameEventTags.dampens(source)) {
                    return false;
                }
            }
            return context.state == null || !GameEventTags.dampens(context.state);
        }

        /** The owner should save: something it keeps changed. */
        default void onDataChanged() {
        }

        /** False while the owner's listening is switched off ({@link GameEventListener#active}). */
        default boolean active() {
            return true;
        }
    }

    // --- one vibration ------------------------------------------------------------------------------

    /** 1.20's {@code VibrationInfo}: the event, how far it was, where, and who made it. */
    public static final class Info {
        public final GameEvent event;
        public final float distance;
        public final Vec3d pos;
        @Nullable
        public final UUID sourceId;
        @Nullable
        public final UUID projectileOwnerId;
        @Nullable
        private Entity source;

        public Info(GameEvent event, float distance, Vec3d pos, @Nullable Entity source) {
            this.event = event;
            this.distance = distance;
            this.pos = pos;
            this.source = source;
            this.sourceId = source != null ? source.getUniqueID() : null;
            Entity owner = VibrationListener.projectileOwner(source);
            this.projectileOwnerId = owner != null ? owner.getUniqueID() : null;
        }

        private Info(GameEvent event, float distance, Vec3d pos, @Nullable UUID sourceId, @Nullable UUID ownerId) {
            this.event = event;
            this.distance = distance;
            this.pos = pos;
            this.sourceId = sourceId;
            this.projectileOwnerId = ownerId;
        }

        @Nullable
        public Entity entity(WorldServer world) {
            if (this.source != null && !this.source.isDead) {
                return this.source;
            }
            return this.sourceId != null ? world.getEntityFromUuid(this.sourceId) : null;
        }

        @Nullable
        public Entity projectileOwner(WorldServer world) {
            return this.projectileOwnerId != null ? world.getEntityFromUuid(this.projectileOwnerId) : null;
        }

        NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("game_event", this.event.name().toString());
            tag.setFloat("distance", this.distance);
            NBTTagCompound pos = new NBTTagCompound();
            pos.setDouble("x", this.pos.x);
            pos.setDouble("y", this.pos.y);
            pos.setDouble("z", this.pos.z);
            tag.setTag("pos", pos);
            if (this.sourceId != null) {
                tag.setUniqueId("source", this.sourceId);
            }
            if (this.projectileOwnerId != null) {
                tag.setUniqueId("projectile_owner", this.projectileOwnerId);
            }
            return tag;
        }

        @Nullable
        static Info read(NBTTagCompound tag) {
            GameEvent event = GameEvent.get(new ResourceLocation(tag.getString("game_event")));
            if (event == null) {
                return null;
            }
            NBTTagCompound pos = tag.getCompoundTag("pos");
            return new Info(event, tag.getFloat("distance"), new Vec3d(pos.getDouble("x"), pos.getDouble("y"), pos.getDouble("z")),
                    tag.hasUniqueId("source") ? tag.getUniqueId("source") : null,
                    tag.hasUniqueId("projectile_owner") ? tag.getUniqueId("projectile_owner") : null);
        }
    }

    // --- what a listener keeps ------------------------------------------------------------------------

    /** 1.20's {@code VibrationSystem.Data} with its {@code VibrationSelector}. */
    public static final class Data {
        @Nullable
        Info current;
        int travelTicks;
        boolean reloadParticle;
        @Nullable
        Info candidate;
        long candidateTick = -1L;

        @Nullable
        public Info current() {
            return this.current;
        }

        public int travelTicks() {
            return this.travelTicks;
        }

        /** {@code VibrationSelector.addCandidate}: closer wins within a tick, then the higher frequency. */
        void addCandidate(Info info, long tick) {
            if (this.candidate == null) {
                this.candidate = info;
                this.candidateTick = tick;
                return;
            }
            if (tick != this.candidateTick) {
                return;
            }
            if (info.distance < this.candidate.distance
                    || (info.distance == this.candidate.distance && frequency(info.event) > frequency(this.candidate.event))) {
                this.candidate = info;
                this.candidateTick = tick;
            }
        }

        /** The {@code listener} tag: {@code event}, {@code selector} ({@code event}, {@code tick}), {@code event_delay}. */
        public NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            if (this.current != null) {
                tag.setTag("event", this.current.write());
            }
            NBTTagCompound selector = new NBTTagCompound();
            if (this.candidate != null) {
                selector.setTag("event", this.candidate.write());
            }
            selector.setLong("tick", this.candidate != null ? this.candidateTick : -1L);
            tag.setTag("selector", selector);
            tag.setInteger("event_delay", this.travelTicks);
            return tag;
        }

        public void read(NBTTagCompound tag) {
            this.current = tag.hasKey("event", 10) ? Info.read(tag.getCompoundTag("event")) : null;
            NBTTagCompound selector = tag.getCompoundTag("selector");
            this.candidate = selector.hasKey("event", 10) ? Info.read(selector.getCompoundTag("event")) : null;
            this.candidateTick = this.candidate != null ? selector.getLong("tick") : -1L;
            this.travelTicks = Math.max(0, tag.getInteger("event_delay"));
            // 1.20's codec sets reloadVibrationParticle on everything it reads.
            this.reloadParticle = true;
        }
    }

    // --- the listener ---------------------------------------------------------------------------

    /** 1.20's {@code VibrationSystem.Listener}: registered with {@link GameEvents#add}. */
    public static final class Listener implements GameEventListener {
        private final User user;
        private final Data data;

        public Listener(User user, Data data) {
            this.user = user;
            this.data = data;
        }

        @Override
        public PositionSource position() {
            return this.user.positionSource();
        }

        @Override
        public int radius() {
            return this.user.listenerRadius();
        }

        @Override
        public boolean active() {
            return this.user.active();
        }

        @Override
        public boolean handle(WorldServer world, GameEvent event, Vec3d origin, GameEventContext context) {
            if (this.data.current != null || !this.user.isValidVibration(event, context)) {
                return false;
            }
            Vec3d here = this.user.positionSource().position(world);
            if (here == null || !this.user.canReceiveVibration(world, new BlockPos(origin), event, context)) {
                return false;
            }
            if (VibrationListener.isOccluded(world, origin, here)) {
                return false;
            }
            schedule(world, event, context, origin, here);
            return true;
        }

        /** {@code forceScheduleVibration}: a candidate without the checks (a sensor stepped on). */
        public void forceSchedule(WorldServer world, GameEvent event, GameEventContext context, Vec3d origin) {
            Vec3d here = this.user.positionSource().position(world);
            if (here != null) {
                schedule(world, event, context, origin, here);
            }
        }

        private void schedule(WorldServer world, GameEvent event, GameEventContext context, Vec3d origin, Vec3d here) {
            this.data.addCandidate(new Info(event, (float) origin.distanceTo(here), origin, context.source), world.getTotalWorldTime());
        }
    }

    // --- the ticker ------------------------------------------------------------------------------

    /** 1.20's {@code VibrationSystem.Ticker.tick}: from the owner's own tick, server side. */
    public static void tick(World world, Data data, User user) {
        if (!(world instanceof WorldServer)) {
            return;
        }
        WorldServer server = (WorldServer) world;
        if (data.current == null) {
            selectAndSchedule(server, data, user);
        }
        if (data.current == null) {
            return;
        }
        boolean changed = data.travelTicks > 0;
        reloadParticle(server, data, user);
        data.travelTicks = Math.max(0, data.travelTicks - 1);
        if (data.travelTicks <= 0) {
            changed = receive(server, data, user, data.current);
        }
        if (changed) {
            user.onDataChanged();
        }
    }

    private static void selectAndSchedule(WorldServer world, Data data, User user) {
        if (data.candidate == null || data.candidateTick >= world.getTotalWorldTime()) {
            return;
        }
        Info info = data.candidate;
        data.current = info;
        data.travelTicks = user.calculateTravelTimeInTicks(info.distance);
        GameEventNetwork.sendVibration(world, info.pos, user.positionSource(), data.travelTicks, 32.0D);
        user.onDataChanged();
        data.candidate = null;
        data.candidateTick = -1L;
    }

    private static void reloadParticle(WorldServer world, Data data, User user) {
        if (!data.reloadParticle) {
            return;
        }
        if (data.current == null) {
            data.reloadParticle = false;
            return;
        }
        Vec3d from = data.current.pos;
        Vec3d to = user.positionSource().position(world);
        if (to == null) {
            to = from;
        }
        int left = data.travelTicks;
        int total = user.calculateTravelTimeInTicks(data.current.distance);
        double f = total <= 0 ? 1.0D : 1.0D - (double) left / (double) total;
        Vec3d at = new Vec3d(from.x + (to.x - from.x) * f, from.y + (to.y - from.y) * f, from.z + (to.z - from.z) * f);
        // 1.20 clears the flag only once some player received the particle.
        if (!world.playerEntities.isEmpty() && world.getClosestPlayer(at.x, at.y, at.z, 32.0D, false) != null) {
            GameEventNetwork.sendVibration(world, at, user.positionSource(), left, 32.0D);
            data.reloadParticle = false;
        }
    }

    private static boolean receive(WorldServer world, Data data, User user, Info info) {
        BlockPos origin = new BlockPos(info.pos);
        Vec3d here = user.positionSource().position(world);
        BlockPos listener = here != null ? new BlockPos(here) : origin;
        if (user.requiresAdjacentChunksToBeTicking() && !adjacentChunksLoaded(world, listener)) {
            return false;
        }
        user.onReceiveVibration(world, origin, info.event, info.entity(world), info.projectileOwner(world),
                distanceBetweenInBlocks(origin, listener));
        data.current = null;
        return true;
    }

    /** 1.20's {@code areAdjacentChunksTicking}: on 1.12.2, the 3x3 chunks around are loaded. */
    private static boolean adjacentChunksLoaded(WorldServer world, BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                if (world.getChunkProvider().getLoadedChunk(x, z) == null) {
                    return false;
                }
            }
        }
        return true;
    }
}

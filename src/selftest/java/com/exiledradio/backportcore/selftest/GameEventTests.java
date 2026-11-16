package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.gameevent.GameEvent;
import com.exiledradio.backportcore.gameevent.GameEventContext;
import com.exiledradio.backportcore.gameevent.GameEventListener;
import com.exiledradio.backportcore.gameevent.GameEventTags;
import com.exiledradio.backportcore.gameevent.GameEvents;
import com.exiledradio.backportcore.gameevent.PositionSource;
import com.exiledradio.backportcore.gameevent.SensorActivation;
import com.exiledradio.backportcore.gameevent.VibrationListener;
import com.exiledradio.backportcore.gameevent.VibrationSystem;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Game events (gameevent/): turning them on by version, both versions' dispatch, 1.20's vibration system
 * (selector, travel, saving), the version-following tags, resonators, sensor activation and the 1.20 emitters.
 * The test requires "1.19" and "1.20" itself, each behind a switch it flips.
 */
final class GameEventTests {

    static boolean on119;
    static boolean on120;

    private GameEventTests() {
    }

    /** Hears everything, at once (1.20's rules), and records it. */
    static final class Ears implements GameEventListener {
        final BlockPos pos;
        final List<GameEvent> heard = new ArrayList<GameEvent>();
        final List<Entity> sources = new ArrayList<Entity>();
        final List<Vec3d> where = new ArrayList<Vec3d>();

        Ears(BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public PositionSource position() {
            return new PositionSource.Block(this.pos);
        }

        @Override
        public int radius() {
            return 16;
        }

        @Override
        public boolean immediate() {
            return true;
        }

        @Override
        public boolean handle(WorldServer world, GameEvent event, Vec3d origin, GameEventContext context) {
            this.heard.add(event);
            this.sources.add(context.source);
            this.where.add(origin);
            return true;
        }
    }

    /** A 1.20 vibration user that hears everything and records what arrives. */
    static final class User implements VibrationSystem.User {
        final PositionSource position;
        final int radius;
        final List<GameEvent> received = new ArrayList<GameEvent>();
        final List<Float> distances = new ArrayList<Float>();
        boolean active = true;

        User(BlockPos pos, int radius) {
            this.position = new PositionSource.Block(pos);
            this.radius = radius;
        }

        @Override
        public int listenerRadius() {
            return this.radius;
        }

        @Override
        public PositionSource positionSource() {
            return this.position;
        }

        @Override
        public boolean canReceiveVibration(WorldServer world, BlockPos origin, GameEvent event, GameEventContext context) {
            return true;
        }

        @Override
        public void onReceiveVibration(WorldServer world, BlockPos origin, GameEvent event, @Nullable Entity source,
                                       @Nullable Entity projectileOwner, float distance) {
            this.received.add(event);
            this.distances.add(distance);
        }

        @Override
        public boolean active() {
            return this.active;
        }
    }

    /** A 1.19.2 listener's owner that hears everything. */
    static final class Config119 implements VibrationListener.Config {
        final List<GameEvent> heard = new ArrayList<GameEvent>();

        @Override
        public boolean shouldListen(WorldServer world, VibrationListener l, BlockPos origin, GameEvent event, GameEventContext context) {
            return true;
        }

        @Override
        public void onSignalReceive(WorldServer world, VibrationListener l, BlockPos origin, GameEvent event, @Nullable Entity source,
                                    @Nullable Entity projectileOwner, float distance) {
            this.heard.add(event);
        }
    }

    private static void ticks(WorldServer world, VibrationSystem.Data data, VibrationSystem.User user, int n) {
        for (int i = 0; i < n; i++) {
            VibrationSystem.tick(world, data, user);
        }
    }

    static void schedule() {
        then(1, "game events off until required", world -> {
            BlockPos at = new BlockPos(4000, 120, 4000);
            Ears ears = new Ears(at);
            GameEvents.add(world, ears);
            GameEvents.emit(world, GameEvent.EAT, at.east(2), GameEventContext.NONE);
            check("nothing is emitted while no mod requires game events", ears.heard.isEmpty(), ears.heard);
            GameEvents.require("1.19", () -> on119);
            GameEvents.require("1.20", () -> on120);
            check("a requirement whose switch is off turns nothing on", !GameEvents.active());
            on119 = true;
            check("1.19 on: active, at least 1.19, not 1.20", GameEvents.active() && GameEvents.isAtLeast("1.19")
                    && !GameEvents.isAtLeast("1.20"), GameEvents.newest());
            check("1.19's vibrations without 1.20's", GameEventTags.vibrations() == GameEventTags.VIBRATIONS_1_19
                    && !GameEventTags.vibrations().contains(GameEvent.ENTITY_MOUNT), GameEventTags.vibrations().size());
            on120 = true;
            check("1.20 on: the newest is 1.20", "1.20".equals(GameEvents.newest()) && GameEvents.isAtLeast("1.19.4"), GameEvents.newest());
            check("1.20's vibrations: mount, unequip, resonate_7 and the renamed piston events", GameEventTags.vibrations().contains(GameEvent.ENTITY_MOUNT)
                    && GameEventTags.vibrations().contains(GameEvent.UNEQUIP) && GameEventTags.vibrations().contains(GameEvent.resonance(7))
                    && GameEventTags.vibrations().contains(GameEvent.PISTON_EXTEND) && !GameEventTags.vibrations().contains(GameEvent.DISPENSE_FAIL));
            check("the 1.20 warden hears the shriek but not flaps", GameEventTags.wardenCanListen().contains(GameEvent.SHRIEK)
                    && !GameEventTags.wardenCanListen().contains(GameEvent.FLAP));
            check("frequencies: resonate_n is n, entity_action 4, mount 6, dismount 5, unequip 4",
                    GameEvent.resonance(13).frequency() == 13 && GameEvent.ENTITY_ACTION.frequency() == 4 && GameEvent.ENTITY_MOUNT.frequency() == 6
                            && GameEvent.ENTITY_DISMOUNT.frequency() == 5 && GameEvent.UNEQUIP.frequency() == 4);
            GameEvents.emit(world, GameEvent.EAT, at.east(2), GameEventContext.NONE);
            check("an immediate listener hears at once", ears.heard.size() == 1 && ears.heard.get(0) == GameEvent.EAT, ears.heard);
            GameEvents.remove(world, ears);
        });

        then(1, "1.20's selector", world -> {
            BlockPos at = new BlockPos(4100, 120, 4100);
            User user = new User(at, 8);
            VibrationSystem.Data data = new VibrationSystem.Data();
            VibrationSystem.Listener listener = new VibrationSystem.Listener(user, data);
            GameEvents.add(world, listener);
            GameEvents.emit(world, GameEvent.BLOCK_CHANGE, at.east(6), GameEventContext.NONE);
            GameEvents.emit(world, GameEvent.EAT, at.east(3), GameEventContext.NONE);
            GameEvents.emit(world, GameEvent.STEP, at.west(3), GameEventContext.NONE);
            ticks(world, data, user, 1);
            check("a candidate is not taken in the tick it was heard", data.current() == null);
            long now = world.getTotalWorldTime();
            world.getWorldInfo().setWorldTotalTime(now + 1);
            ticks(world, data, user, 1);
            VibrationSystem.Info info = data.current();
            check("the closest wins, and at equal distance the higher frequency (eat 8 over step 1)",
                    info != null && info.event == GameEvent.EAT, info == null ? null : info.event);
            check("travel time is the distance in blocks, less this tick", data.travelTicks() == 2, data.travelTicks());
            NBTTagCompound saved = data.write();
            VibrationSystem.Data copy = new VibrationSystem.Data();
            copy.read(saved);
            check("a vibration in flight is saved", copy.current() != null && copy.current().event == GameEvent.EAT
                    && copy.travelTicks() == 2, saved);
            ticks(world, data, user, 1);
            check("not arrived after 2 of 3 ticks", user.received.isEmpty(), user.received);
            ticks(world, data, user, 1);
            check("arrives after 3 ticks, with the distance between the two blocks", user.received.size() == 1
                    && user.received.get(0) == GameEvent.EAT && Math.abs(user.distances.get(0) - 3.0F) < 1.0E-4F, user.distances);
            ticks(world, data, user, 5);
            check("the farther ones of that tick are dropped", user.received.size() == 1, user.received);
            world.getWorldInfo().setWorldTotalTime(now + 2);

            // Radius: 1.20 measures between blocks, so (8.4, 0, 0) is 8 blocks off and heard; 1.19 measures exactly.
            Vec3d edge = new Vec3d(at.getX() + 8.9D, at.getY() + 0.5D, at.getZ() + 0.5D);
            GameEvents.emit(world, GameEvent.EAT, edge, GameEventContext.NONE);
            check("1.20: within the radius by blocks", data.current() == null && copyHasCandidate(data), data.write());
            Config119 config = new Config119();
            VibrationListener old = new VibrationListener(new PositionSource.Block(at.up(2)), 8, config);
            GameEvents.add(world, old);
            GameEvents.emit(world, GameEvent.EAT, new Vec3d(at.getX() + 8.9D, at.getY() + 2.5D, at.getZ() + 0.5D), GameEventContext.NONE);
            GameEvents.flush(world);
            check("1.19: 8.4 blocks exactly is beyond 8", !old.isReceiving());
            GameEvents.emit(world, GameEvent.EAT, at.up(2).east(4), GameEventContext.NONE);
            check("1.19: nothing heard before the end of the tick", !old.isReceiving());
            GameEvents.flush(world);
            check("1.19: heard at the end of the tick", old.isReceiving());
            GameEvents.remove(world, old);

            user.active = false;
            VibrationSystem.Data quiet = new VibrationSystem.Data();
            VibrationSystem.Listener deaf = new VibrationSystem.Listener(user, quiet);
            GameEvents.add(world, deaf);
            GameEvents.emit(world, GameEvent.EAT, at.east(1), GameEventContext.NONE);
            check("an inactive listener hears nothing", !copyHasCandidate(quiet));
            GameEvents.remove(world, deaf);
            GameEvents.remove(world, listener);
        });

        then(1, "1.20's validity rules", world -> {
            BlockPos at = new BlockPos(4200, 120, 4200);
            User user = new User(at, 8);
            VibrationSystem.Data data = new VibrationSystem.Data();
            VibrationSystem.Listener listener = new VibrationSystem.Listener(user, data);
            GameEvents.add(world, listener);
            GameEvents.emit(world, GameEvent.BLOCK_PLACE, at.east(2), GameEventContext.of(Blocks.WOOL.getDefaultState()));
            check("placing wool makes no vibration", !copyHasCandidate(data));
            GameEvents.emit(world, GameEvent.ITEM_INTERACT_START, at.east(2), GameEventContext.NONE);
            check("item_interact_start is not a vibration", !copyHasCandidate(data));
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    world.setBlockState(at.add(2, y, z), Blocks.WOOL.getDefaultState());
                }
            }
            GameEvents.emit(world, GameEvent.EAT, at.east(4), GameEventContext.NONE);
            check("a wool wall occludes", !copyHasCandidate(data));
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    world.setBlockToAir(at.add(2, y, z));
                }
            }
            GameEvents.emit(world, GameEvent.resonance(5), at.east(4), GameEventContext.NONE);
            check("resonate_5 is heard", copyHasCandidate(data));
            GameEvents.remove(world, listener);

            GameEventTags.addResonator(Blocks.GLASS);
            check("an added resonator resonates", GameEventTags.resonates(Blocks.GLASS.getDefaultState())
                    && !GameEventTags.resonates(Blocks.STONE.getDefaultState()));
            final int[] got = {0};
            SensorActivation.addHandler((source, w, pos, frequency) -> got[0] = frequency);
            SensorActivation.activated(null, world, at, 9);
            SensorActivation.activated(null, world, at, 0);
            check("sensor activations reach the handlers (frequency 1-15 only)", got[0] == 9, got[0]);
        });

        then(1, "1.20 emitters: mount and dismount", world -> {
            BlockPos at = new BlockPos(4300, 120, 4300);
            world.setBlockState(at.down(), Blocks.STONE.getDefaultState());
            Ears ears = new Ears(at);
            GameEvents.add(world, ears);
            EntityBoat boat = new EntityBoat(world, at.getX() + 3.5D, at.getY(), at.getZ() + 0.5D);
            world.spawnEntity(boat);
            EntityPig pig = new EntityPig(world);
            pig.setPosition(at.getX() + 3.5D, at.getY(), at.getZ() + 0.5D);
            world.spawnEntity(pig);
            ears.heard.clear();
            ears.sources.clear();
            pig.startRiding(boat);
            check("entity_mount at the vehicle, the rider as the source", ears.heard.contains(GameEvent.ENTITY_MOUNT)
                    && ears.sources.get(ears.heard.indexOf(GameEvent.ENTITY_MOUNT)) == pig, ears.heard);
            pig.dismountRidingEntity();
            check("entity_dismount", ears.heard.contains(GameEvent.ENTITY_DISMOUNT), ears.heard);
            on120 = false;
            ears.heard.clear();
            pig.startRiding(boat);
            check("with only 1.19 on, no entity_mount", !ears.heard.contains(GameEvent.ENTITY_MOUNT), ears.heard);
            pig.dismountRidingEntity();
            on120 = true;
            boat.setDead();
            pig.setDead();
            GameEvents.remove(world, ears);
        });
    }

    /** @return true if the selector holds a candidate (seen through the saved tag) */
    private static boolean copyHasCandidate(VibrationSystem.Data data) {
        return data.write().getCompoundTag("selector").hasKey("event", 10);
    }
}

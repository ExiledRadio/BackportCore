package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.command.RotateCommand;
import com.exiledradio.backportcore.command.random.RandomSequences;
import com.exiledradio.backportcore.command.random.Xoroshiro;
import com.exiledradio.backportcore.command.tick.ServerTickRateManager;
import com.exiledradio.backportcore.command.tick.TickHooks;
import com.exiledradio.backportcore.command.tick.Ticks;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Today's commands (command/): {@code /tick} on a real server (freezing entities, block entities and the clock,
 * stepping, the rate the loop runs at, sprinting, query, errors, permission), {@code /random} (26.3's sequences
 * against the 26.3 oracle, ranges, reset, saving), {@code /ride}, {@code /rotate}, {@code /spectate} and
 * {@code /teammsg}, {@code /swing}. Every command runs through the server's command manager, as typed.
 */
final class CommandTests {

    private CommandTests() {
    }

    /** A command source with a permission level that records what it is told (the translation keys). */
    static final class Recorder implements ICommandSender {
        final MinecraftServer server;
        final int level;
        @Nullable final Entity entity;
        final List<String> keys = new ArrayList<String>();
        final List<ITextComponent> lines = new ArrayList<ITextComponent>();

        Recorder(MinecraftServer server, int level, @Nullable Entity entity) {
            this.server = server;
            this.level = level;
            this.entity = entity;
        }

        List<String> run(String command) {
            keys.clear();
            lines.clear();
            server.getCommandManager().executeCommand(this, command);
            return keys;
        }

        String first() {
            return keys.isEmpty() ? "" : keys.get(0);
        }

        @Override
        public String getName() {
            return "SelfTest";
        }

        @Override
        public void sendMessage(ITextComponent component) {
            lines.add(component);
            keys.add(component instanceof TextComponentTranslation ? ((TextComponentTranslation) component).getKey() : component.getUnformattedText());
        }

        @Override
        public boolean canUseCommand(int permLevel, String commandName) {
            return permLevel <= level;
        }

        @Override
        public World getEntityWorld() {
            return entity != null ? entity.world : server.getWorld(0);
        }

        @Nullable
        @Override
        public MinecraftServer getServer() {
            return server;
        }

        @Nullable
        @Override
        public Entity getCommandSenderEntity() {
            return entity;
        }

        @Override
        public BlockPos getPosition() {
            return entity != null ? entity.getPosition() : BlockPos.ORIGIN;
        }

        @Override
        public Vec3d getPositionVector() {
            return entity != null ? entity.getPositionVector() : Vec3d.ZERO;
        }

        @Override
        public boolean sendCommandFeedback() {
            return true;
        }
    }

    private static MinecraftServer server() {
        return FMLCommonHandler.instance().getMinecraftServerInstance();
    }

    private static Object[] args(String key, List<ITextComponent> lines) {
        for (ITextComponent c : lines) {
            if (c instanceof TextComponentTranslation && key.equals(((TextComponentTranslation) c).getKey())) {
                return ((TextComponentTranslation) c).getFormatArgs();
            }
        }
        return null;
    }

    // State carried between the tick steps.
    private static EntityPig pig;
    private static EntityCow zombie;
    private static BlockPos furnace;
    private static int pigTicks;
    private static int burn;
    private static long time;
    private static long wall;

    private static int burnTime(World world) {
        return ((TileEntityFurnace) world.getTileEntity(furnace)).getField(0);
    }

    private static void record(WorldServer world) {
        pigTicks = pig.ticksExisted;
        burn = burnTime(world);
        time = world.getTotalWorldTime();
    }

    static void schedule() {
        scheduleTick();
        scheduleRandom();
        scheduleOthers();
    }

    private static void scheduleTick() {
        // Well after start-up: the loop must not still be catching up on the world's first ticks.
        then(200, "tick: set up", world -> {
            check("tick: P38 applied (server loop)", "applied".equals(Asm.state("P38")), Asm.state("P38"));
            check("tick: P39 applied (freezing)", "applied".equals(Asm.state("P39")), Asm.state("P39"));
            for (String name : new String[] {"tick", "random", "ride", "rotate", "spectate", "teammsg", "tm", "swing"}) {
                check("commands: /" + name + " registered", server().getCommandManager().getCommands().containsKey(name), name);
            }
            check("tick: a server manager exists, at 20 per second and running", Ticks.server() != null
                    && Ticks.server().tickrate() == 20.0F && Ticks.server().runsNormally() && !Ticks.server().isFrozen(), "");
            check("tick: the loop waits vanilla's 50 ms", TickHooks.msPerTick() == 50L, TickHooks.msPerTick());
            world.resetUpdateEntityTick();
            BlockPos spawn = world.getTopSolidOrLiquidBlock(world.getSpawnPoint());
            pig = new EntityPig(world);
            pig.setPosition(spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5);
            world.spawnEntity(pig);
            zombie = new EntityCow(world);
            zombie.setPosition(spawn.getX() + 3.5, spawn.getY() + 1, spawn.getZ() + 0.5);
            zombie.setNoAI(true);
            world.spawnEntity(zombie);
            furnace = spawn.up(4);
            world.setBlockState(furnace, Blocks.FURNACE.getDefaultState(), 2);
            ((TileEntityFurnace) world.getTileEntity(furnace)).setField(0, 1000);
            record(world);
        });
        then(5, "tick: running, then freeze", world -> {
            world.resetUpdateEntityTick();
            check("tick: running: the pig ticks", pig.ticksExisted > pigTicks, pig.ticksExisted - pigTicks);
            check("tick: running: the furnace burns", burnTime(world) < burn, burn - burnTime(world));
            check("tick: running: the clock runs", world.getTotalWorldTime() > time, world.getTotalWorldTime() - time);
            Recorder ops = new Recorder(server(), 4, null);
            check("tick freeze: 'The game is frozen'", ops.run("tick freeze").contains("commands.tick.status.frozen"), ops.keys);
            record(world);
        });
        then(5, "tick: frozen", world -> {
            world.resetUpdateEntityTick();
            check("tick: frozen: the pig holds still", pig.ticksExisted == pigTicks, pig.ticksExisted - pigTicks);
            check("tick: frozen: the furnace holds", burnTime(world) == burn, burn - burnTime(world));
            check("tick: frozen: the clock holds", world.getTotalWorldTime() == time, world.getTotalWorldTime() - time);
            ServerTickRateManager m = Ticks.server();
            // A fake player with no connection: dismounting must not teleport it.
            FakePlayer rider = new FakePlayer(world, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "[SelfTestRider]")) {
                @Override
                public void setPositionAndUpdate(double x, double y, double z) {
                }
            };
            check("tick: frozen: a pig is frozen", m.isEntityFrozen(pig), "");
            check("tick: frozen: a player is not", !m.isEntityFrozen(rider), "");
            rider.startRiding(pig, true);
            check("tick: frozen: a pig carrying a player is not", !m.isEntityFrozen(pig), pig.getPassengers());
            rider.dismountRidingEntity();
            check("tick: frozen: the pig without its rider is frozen again", m.isEntityFrozen(pig), "");
            Recorder ops = new Recorder(server(), 4, null);
            List<String> query = new ArrayList<String>(ops.run("tick query"));
            check("tick query, frozen: status, rate, percentiles", query.size() == 3 && "commands.tick.status.frozen".equals(query.get(0))
                    && "commands.tick.query.rate.running".equals(query.get(1)) && "commands.tick.query.percentiles".equals(query.get(2)), query);
            Object[] rate = args("commands.tick.query.rate.running", ops.lines);
            check("tick query: target 20.0 per second, 50.0 ms", rate != null && "20.0".equals(rate[0]) && "50.0".equals(rate[2]),
                    rate == null ? null : java.util.Arrays.asList(rate));
            Object[] pct = args("commands.tick.query.percentiles", ops.lines);
            check("tick query: a sample of 100 ticks", pct != null && Integer.valueOf(100).equals(pct[3]), pct == null ? null : pct[3]);
            check("tick step 3: 'Stepping 3 tick(s)'", ops.run("tick step 3").contains("commands.tick.step.success")
                    && Integer.valueOf(3).equals(args("commands.tick.step.success", ops.lines)[0]), ops.keys);
            record(world);
        });
        then(6, "tick: stepped", world -> {
            world.resetUpdateEntityTick();
            check("tick step: the pig ran exactly 3 ticks", pig.ticksExisted - pigTicks == 3, pig.ticksExisted - pigTicks);
            check("tick step: the clock ran exactly 3 ticks", world.getTotalWorldTime() - time == 3, world.getTotalWorldTime() - time);
            check("tick step: the furnace burned 3 ticks", burn - burnTime(world) == 3, burn - burnTime(world));
            Recorder ops = new Recorder(server(), 4, null);
            check("tick step stop with no step: 'No tick step in progress'", ops.run("tick step stop").contains("commands.tick.step.stop.fail"), ops.keys);
            check("tick step 2s: 40 ticks", ops.run("tick step 2s").contains("commands.tick.step.success")
                    && Integer.valueOf(40).equals(args("commands.tick.step.success", ops.lines)[0]), ops.keys);
            check("tick step stop: 'Interrupted the current tick step'", ops.run("tick step stop").contains("commands.tick.step.stop.success")
                    && Ticks.server().frozenTicksToRun() == 0, ops.keys);
            check("tick step 0: too low", ops.run("tick step 0").contains("argument.time.tick_count_too_low"), ops.keys);
            check("tick step 1x: invalid unit", ops.run("tick step 1x").contains("argument.time.invalid_unit"), ops.keys);
            check("tick unfreeze: 'The game is running normally'", ops.run("tick unfreeze").contains("commands.tick.status.running"), ops.keys);
            check("tick step while running: must be frozen", ops.run("tick step").contains("commands.tick.step.fail"), ops.keys);
            Recorder gamemaster = new Recorder(server(), 2, null);
            check("tick needs permission level 3", gamemaster.run("tick query").contains("commands.generic.permission"), gamemaster.keys);
            record(world);
        });
        then(3, "tick: running again; rate", world -> {
            world.resetUpdateEntityTick();
            check("tick unfreeze: the pig ticks again", pig.ticksExisted > pigTicks, pig.ticksExisted - pigTicks);
            Recorder ops = new Recorder(server(), 4, null);
            check("tick rate 0.5: below 1", ops.run("tick rate 0.5").contains("argument.float.low"), ops.keys);
            check("tick rate 10001: above 10000", ops.run("tick rate 10001").contains("argument.float.big"), ops.keys);
            check("tick rate abc: not a number", ops.run("tick rate abc").contains("parsing.float.invalid"), ops.keys);
            ops.run("tick rate 8");
            Object[] set = args("commands.tick.rate.success", ops.lines);
            check("tick rate 8: 'Set the target tick rate to 8.0 per second'", set != null && "8.0".equals(set[0]), ops.keys);
            check("tick rate 8: 125 ms a tick", Ticks.server().nanosecondsPerTick() == 125_000_000L, Ticks.server().nanosecondsPerTick());
        });
        // Any backlog the loop still had at 20 per second runs off first (26.3 catches up the same way).
        then(24, "tick: 8 per second, settled", world -> wall = System.nanoTime());
        then(8, "tick: 8 ticks at 8 per second", world -> {
            long ms = (System.nanoTime() - wall) / 1_000_000L;
            // 20 per second would take 400 ms.
            check("tick rate 8: 8 ticks take about a second", ms > 800L && ms < 2500L, ms + " ms");
            check("tick rate 8: the loop waits 125 ms", TickHooks.msPerTick() == 125L, TickHooks.msPerTick());
            new Recorder(server(), 4, null).run("tick rate 100");
            check("tick rate 100: 10 ms a tick", Ticks.server().nanosecondsPerTick() == 10_000_000L, Ticks.server().nanosecondsPerTick());
            wall = System.nanoTime();
        });
        then(100, "tick: 100 ticks at 100 per second", world -> {
            long ms = (System.nanoTime() - wall) / 1_000_000L;
            // 20 per second would take 5000 ms; a dev server's ticks may not all fit in 10 ms.
            check("tick rate 100: 100 ticks faster than 20 per second", ms < 4000L, ms + " ms");
            check("tick rate 100: the loop waits 10 ms", TickHooks.msPerTick() == 10L, TickHooks.msPerTick());
            new Recorder(server(), 4, null).run("tick rate 30");
        });
        then(30, "tick: 30 per second in whole milliseconds", world -> {
            long slot = TickHooks.msPerTick();
            check("tick rate 30: slots of 33 or 34 ms", slot == 33L || slot == 34L, slot);
            Recorder ops = new Recorder(server(), 4, null);
            ops.run("tick rate 20");
            check("tick sprint stop with no sprint: 'No tick sprint in progress'", ops.run("tick sprint stop").contains("commands.tick.sprint.stop.fail"), ops.keys);
            ops.run("tick freeze");
            check("tick sprint 400: 'The game is sprinting'", ops.run("tick sprint 400").contains("commands.tick.status.sprinting"), ops.keys);
            ServerTickRateManager m = Ticks.server();
            check("tick sprint: sprinting, and no longer frozen", m.isSprinting() && !m.isFrozen(), m.isFrozen());
            List<String> query = new ArrayList<String>(ops.run("tick query"));
            check("tick query, sprinting: status, ignored rate, percentiles", query.size() == 3 && "commands.tick.status.sprinting".equals(query.get(0))
                    && "commands.tick.query.rate.sprinting".equals(query.get(1)), query);
            wall = System.nanoTime();
            record(world);
        });
        then(400, "tick: sprinted 400 ticks", world -> {
            long ms = (System.nanoTime() - wall) / 1_000_000L;
            // 20 per second would take 20 000 ms.
            check("tick sprint: 400 ticks in far less than 20 s", ms < 10_000L, ms + " ms");
            check("tick sprint: the world ran them", world.getTotalWorldTime() - time >= 399, world.getTotalWorldTime() - time);
        });
        then(3, "tick: sprint over", world -> {
            ServerTickRateManager m = Ticks.server();
            check("tick sprint: done", !m.isSprinting() && m.remainingSprintTicks() == 0, m.remainingSprintTicks());
            check("tick sprint: frozen again, as before it", m.isFrozen(), "");
            check("tick sprint: the loop back to 50 ms", TickHooks.msPerTick() == 50L, TickHooks.msPerTick());
            Recorder ops = new Recorder(server(), 4, null);
            ops.run("tick sprint 1000");
            check("tick sprint over a sprint: interrupted, then sprinting", ops.run("tick sprint 2000").contains("commands.tick.sprint.stop.success")
                    && ops.keys.contains("commands.tick.status.sprinting"), ops.keys);
            check("tick freeze stops the sprint", ops.run("tick freeze").contains("commands.tick.status.frozen") && !m.isSprinting(), m.isSprinting());
            ops.run("tick unfreeze");
            ops.run("tick sprint 1000");
            check("tick sprint stop: 'Interrupted the current tick sprint'", ops.run("tick sprint stop").contains("commands.tick.sprint.stop.success")
                    && !m.isSprinting(), ops.keys);
            check("tick: not frozen, at 20 again", !m.isFrozen() && m.tickrate() == 20.0F, m.tickrate());
        });
        then(1, "tick: running", world -> check("tick: the next tick runs normally", Ticks.server().runsNormally(), ""));
    }

    private static void scheduleRandom() {
        then(1, "random: 26.3's sequences", world -> {
            int lines = 0;
            int same = 0;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(
                    CommandTests.class.getResourceAsStream("/assets/backportcore_selftest/oracle/random_sequences_263.txt"), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    int colon = line.indexOf(": ");
                    String[] head = line.substring(0, colon).split(" ");
                    String[] values = line.substring(colon + 2).split(" ");
                    long seed = Long.parseLong(head[0]);
                    Xoroshiro r = RandomSequences.create(head[1], seed, Integer.parseInt(head[2]), true, Boolean.parseBoolean(head[3]));
                    boolean ok = true;
                    for (int i = 0; i < 6; i++) {
                        ok &= r.nextInt(100) + 1 == Integer.parseInt(values[i]);
                    }
                    ok &= r.nextInt(2147483646) == Integer.parseInt(values[6]);
                    lines++;
                    if (ok) {
                        same++;
                    } else {
                        check("random: oracle line " + line, false, "");
                    }
                }
            }
            check("random: every 26.3 oracle sequence matches (24 lines)", lines == 24 && same == 24, same + "/" + lines);

            Recorder anyone = new Recorder(server(), 0, null);
            Recorder gm = new Recorder(server(), 2, null);
            anyone.run("random value 1..6");
            Object[] v = args("commands.random.sample.success", anyone.lines);
            check("random value 1..6: anyone, a value from 1 to 6", v != null && (Integer) v[0] >= 1 && (Integer) v[0] <= 6, anyone.keys);
            check("random value 5: range too small", anyone.run("random value 5").contains("commands.random.error.range_too_small"), anyone.keys);
            check("random value ..: empty range", anyone.run("random value ..").contains("argument.range.empty"), anyone.keys);
            check("random value 1.5..3: whole numbers only", anyone.run("random value 1.5..3").contains("argument.range.ints"), anyone.keys);
            check("random value 9..1: swapped", anyone.run("random value 9..1").contains("argument.range.swapped"), anyone.keys);
            check("random value ..-2: from the smallest int", anyone.run("random value ..-2").contains("commands.random.sample.success"), anyone.keys);
            check("random value ..0: one too many", anyone.run("random value ..0").contains("commands.random.error.range_too_large"), anyone.keys);
            check("random value -2147483648..2147483647: too large", anyone.run("random value -2147483648..2147483647")
                    .contains("commands.random.error.range_too_large"), anyone.keys);
            check("random value with a sequence needs level 2", anyone.run("random value 1..6 minecraft:test").contains("commands.generic.permission"), anyone.keys);
            check("random reset needs level 2", anyone.run("random reset *").contains("commands.generic.permission"), anyone.keys);

            long seed = world.getSeed();
            // The defaults are saved with the world (an earlier run may have changed them): back to 26.3's.
            gm.run("random reset * 0 true true");
            int first = (Integer) args("commands.random.sample.success", runAndKeep(gm, "random value 1..100 test"))[0];
            Xoroshiro expected = RandomSequences.create("minecraft:test", seed, 0, true, true);
            check("random value 1..100 test: minecraft:test's first value", first == expected.nextInt(100) + 1, first);
            int second = (Integer) args("commands.random.sample.success", runAndKeep(gm, "random value 1..100 minecraft:test"))[0];
            check("random: the sequence goes on", second == expected.nextInt(100) + 1, second);
            gm.run("random reset minecraft:test");
            check("random reset minecraft:test: 'Reset random sequence minecraft:test'", gm.keys.contains("commands.random.reset.success"), gm.keys);
            int again = (Integer) args("commands.random.sample.success", runAndKeep(gm, "random value 1..100 minecraft:test"))[0];
            check("random reset: starts over", again == first, again + " vs " + first);
            gm.run("random reset * 7 false true");
            Object[] count = args("commands.random.reset.all.success", gm.lines);
            check("random reset * 7 false true: one sequence cleared", count != null && Integer.valueOf(1).equals(count[0]), gm.keys);
            int salted = (Integer) args("commands.random.sample.success", runAndKeep(gm, "random value 1..100 minecraft:test"))[0];
            check("random: new defaults (salt 7, no world seed) give 26.3's 74", salted == 74, salted);

            RandomSequences saved = RandomSequences.get(server().getWorld(0));
            NBTTagCompound nbt = saved.writeToNBT(new NBTTagCompound());
            RandomSequences loaded = new RandomSequences("x");
            loaded.readFromNBT(nbt);
            check("random: a saved sequence continues where it was", loaded.get("minecraft:test", seed).nextInt(1000)
                    == saved.get("minecraft:test", seed).nextInt(1000), nbt);
            check("random roll: told to the players (none here), not to the roller alone", gm.run("random roll 1..20").isEmpty(), gm.keys);
            gm.run("random reset * 0 true true");
        });
    }

    private static List<ITextComponent> runAndKeep(Recorder r, String command) {
        r.run(command);
        return new ArrayList<ITextComponent>(r.lines);
    }

    private static void scheduleOthers() {
        then(1, "ride, rotate, spectate, teammsg", world -> {
            Recorder gm = new Recorder(server(), 2, null);
            String p = pig.getCachedUniqueIdString();
            String z = zombie.getCachedUniqueIdString();
            check("ride mount: 'started riding'", gm.run("ride " + z + " mount " + p).contains("commands.ride.mount.success")
                    && zombie.getRidingEntity() == pig, gm.keys);
            check("ride mount again: already riding", gm.run("ride " + z + " mount " + p).contains("commands.ride.already_riding"), gm.keys);
            check("ride the pig on its own rider: a loop", gm.run("ride " + p + " mount " + z).contains("commands.ride.mount.failure.loop"), gm.keys);
            check("ride on itself: a loop", gm.run("ride " + p + " mount " + p).contains("commands.ride.mount.failure.loop"), gm.keys);
            check("ride dismount: 'stopped riding'", gm.run("ride " + z + " dismount").contains("commands.ride.dismount.success")
                    && zombie.getRidingEntity() == null, gm.keys);
            check("ride dismount again: not riding", gm.run("ride " + z + " dismount").contains("commands.ride.not_riding"), gm.keys);
            Recorder none = new Recorder(server(), 0, null);
            check("ride needs level 2", none.run("ride " + z + " dismount").contains("commands.generic.permission"), none.keys);

            check("rotate <pig> 90 45", gm.run("rotate " + p + " 90 45").contains("commands.rotate.success")
                    && pig.rotationYaw == 90.0F && pig.rotationPitch == 45.0F && pig.getRotationYawHead() == 90.0F, pig.rotationYaw + "/" + pig.rotationPitch);
            check("rotate <pig> ~10 ~: relative to the command's own rotation (none: 0)", gm.run("rotate " + p + " ~10 ~").contains("commands.rotate.success")
                    && pig.rotationYaw == 10.0F && pig.rotationPitch == 0.0F, pig.rotationYaw + "/" + pig.rotationPitch);
            gm.run("rotate " + p + " facing " + pig.posX + " " + pig.posY + " " + (pig.posZ + 5));
            check("rotate facing a spot to the south: yaw 0", Math.abs(pig.rotationYaw) < 0.01F && Math.abs(pig.rotationPitch) < 0.01F, pig.rotationYaw);
            zombie.setPosition(pig.posX + 3, pig.posY, pig.posZ);
            gm.run("rotate " + p + " facing entity " + z);
            check("rotate facing an entity to the east: yaw -90", Math.abs(pig.rotationYaw + 90.0F) < 0.01F, pig.rotationYaw);
            gm.run("rotate " + p + " facing entity " + z + " eyes");
            check("rotate facing its eyes: looking up", pig.rotationPitch < -10.0F, pig.rotationPitch);
            check("rotate facing entity <e> knee: bad anchor", gm.run("rotate " + p + " facing entity " + z + " knee").contains("argument.anchor.invalid"), gm.keys);
            RotateCommand.lookAt(pig, new Vec3d(pig.posX, pig.posY - 5, pig.posZ + 0.0001));
            check("rotate: looking straight down is pitch 90", Math.abs(pig.rotationPitch - 90.0F) < 0.1F, pig.rotationPitch);

            check("spectate from the console: needs a player", !gm.run("spectate").isEmpty()
                    && !gm.keys.contains("commands.spectate.success.stopped"), gm.keys);

            Recorder pigSource = new Recorder(server(), 0, pig);
            check("teammsg off any team: 'You must be on a team'", pigSource.run("teammsg hello").contains("commands.teammsg.failed.noteam"), pigSource.keys);
            Scoreboard board = world.getScoreboard();
            ScorePlayerTeam team = board.getTeam("selftest_team") != null ? board.getTeam("selftest_team") : board.createTeam("selftest_team");
            board.addPlayerToTeam(p, "selftest_team");
            check("teammsg on a team with no player online: no error", pigSource.run("tm hello").isEmpty(), pigSource.keys);
            check("teammsg from the console: needs an entity", gm.run("teammsg hello").contains("permissions.requires.entity"), gm.keys);
            ITextComponent name = com.exiledradio.backportcore.command.TeamMsgCommand.teamName(team);
            check("teammsg: the team name suggests /teammsg", name.getStyle().getClickEvent() != null
                    && "/teammsg ".equals(name.getStyle().getClickEvent().getValue()), name);
            board.removeTeam(team);
            swing(world, gm, none, p);
            pig.setDead();
            zombie.setDead();
            world.setBlockState(furnace, Blocks.AIR.getDefaultState(), 2);
        });
    }

    /** 26.3's {@code /swing}: messages, hands, animations, durations (Haste), errors, startIfAble. */
    private static void swing(WorldServer world, Recorder gm, Recorder none, String p) {
        List<ITextComponent> lines = runAndKeep(gm, "swing " + p);
        check("swing <pig>: 'Made Pig swing an arm'", gm.keys.contains("commands.swing.success.single")
                && args("commands.swing.success.single", lines) != null, gm.keys);
        check("swing: the default duration is 6", com.exiledradio.backportcore.command.swing.Swings.activeDuration(pig) == 6,
                com.exiledradio.backportcore.command.swing.Swings.activeDuration(pig));
        check("swing <pig> offhand stab 20t: 20 ticks", gm.run("swing " + p + " offhand stab 20t").contains("commands.swing.success.single")
                && com.exiledradio.backportcore.command.swing.Swings.activeDuration(pig) == 20, gm.keys);
        check("swing <pig> mainhand whack 1s: 20 ticks", gm.run("swing " + p + " mainhand whack 1s").contains("commands.swing.success.single")
                && com.exiledradio.backportcore.command.swing.Swings.activeDuration(pig) == 20, gm.keys);
        pig.ticksExisted += 3;
        check("swing: refused while the current swing is under half done (startIfAble), still counted",
                !com.exiledradio.backportcore.command.swing.Swings.swing(pig, net.minecraft.util.EnumHand.MAIN_HAND,
                        com.exiledradio.backportcore.command.swing.SwingType.WHACK, 6)
                        && gm.run("swing " + p).contains("commands.swing.success.single"), gm.keys);
        pig.ticksExisted += 20;
        pig.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.init.MobEffects.HASTE, 200, 1));
        gm.run("swing " + p + " mainhand whack 10");
        check("swing with Haste II: 10 - 2 = 8 ticks", com.exiledradio.backportcore.command.swing.Swings.activeDuration(pig) == 8,
                com.exiledradio.backportcore.command.swing.Swings.activeDuration(pig));
        pig.removePotionEffect(net.minecraft.init.MobEffects.HASTE);
        check("swing @e[type=!player]: 'Made %s entities swing their arms'", gm.run("swing @e[type=!player]")
                .contains("commands.swing.success.multiple"), gm.keys);
        check("swing <pig> mainhand twirl: unknown animation", gm.run("swing " + p + " mainhand twirl")
                .contains("argument.swing_animation.invalid"), gm.keys);
        check("swing <pig> mainhand whack 0: too short", gm.run("swing " + p + " mainhand whack 0")
                .contains("argument.time.tick_count_too_low"), gm.keys);
        check("swing <pig> sideways: usage", gm.run("swing " + p + " sideways").contains("commands.generic.usage"), gm.keys);
        check("swing from the console: needs an entity", gm.run("swing").contains("permissions.requires.entity"), gm.keys);
        net.minecraft.entity.item.EntityItem item = new net.minecraft.entity.item.EntityItem(world, pig.posX, pig.posY + 2, pig.posZ,
                new net.minecraft.item.ItemStack(Blocks.DIRT));
        item.setNoGravity(true);
        world.spawnEntity(item);
        check("swing <item>: no living entities", gm.run("swing " + item.getCachedUniqueIdString()).contains("commands.swing.failed.notliving"), gm.keys);
        item.setDead();
        check("swing needs level 2", none.run("swing " + p).contains("commands.generic.permission"), none.keys);
        Recorder pigSource = new Recorder(server(), 2, pig);
        check("swing with no targets: the command's own entity", pigSource.run("swing").contains("commands.swing.success.single"), pigSource.keys);
    }
}

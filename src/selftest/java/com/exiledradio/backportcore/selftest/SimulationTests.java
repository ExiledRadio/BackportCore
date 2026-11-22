package com.exiledradio.backportcore.selftest;


import com.exiledradio.backportcore.simulation.SimulationDistance;

import net.minecraft.block.BlockButton;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.ArrayList;
import java.util.UUID;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * 1.18's Simulation Distance (patch P46, {@code SimulationDistance}) on the test server, which has no players: only
 * the spawn chunks (and chunks a mod keeps loaded) tick while a distance is set. A pig, a burning furnace and a pressed
 * button (a scheduled tick) at the spawn and the same far away; the distance's square edges with a stand-in player;
 * natural spawning; server.properties. The distance is 0 again after the last step.
 */
final class SimulationTests {

    private static final int FAR = 3000;
    private static EntityPig nearPig;
    private static EntityPig farPig;
    private static int nearTicks;
    private static int farTicks;
    private static BlockPos nearFurnace;
    private static BlockPos farFurnace;
    private static BlockPos nearButton;
    private static BlockPos farButton;

    private SimulationTests() {
    }

    static void schedule() {
        then(1, "simulation distance: set up", world -> {
            world.resetUpdateEntityTick();
            BlockPos spawn = world.getTopSolidOrLiquidBlock(world.getSpawnPoint()).up(6);
            BlockPos far = new BlockPos(spawn.getX() + FAR, spawn.getY(), spawn.getZ());
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    world.getChunk((far.getX() >> 4) + dx, (far.getZ() >> 4) + dz);
                }
            }
            check("simulation: the far place is not a spawn chunk", !world.isSpawnChunk(far.getX() >> 4, far.getZ() >> 4), far);
            nearPig = pig(world, spawn);
            farPig = pig(world, far);
            nearFurnace = spawn.up(3);
            farFurnace = far.up(3);
            nearButton = spawn.up(5);
            farButton = far.up(5);
            for (BlockPos p : new BlockPos[] {nearFurnace, farFurnace, nearButton.down(), farButton.down()}) {
                world.setBlockState(p, p.equals(nearFurnace) || p.equals(farFurnace) ? Blocks.FURNACE.getDefaultState()
                        : Blocks.STONE.getDefaultState(), 2);
            }
            ((TileEntityFurnace) world.getTileEntity(nearFurnace)).setField(0, 1000);
            ((TileEntityFurnace) world.getTileEntity(farFurnace)).setField(0, 1000);
            SimulationDistance.set(3);
            press(world, nearButton);
            press(world, farButton);
            record();
        });
        then(30, "simulation distance 3: only the spawn chunks tick", world -> {
            try {
                world.resetUpdateEntityTick();
                check("simulation: the pig at the spawn ticks", nearPig.ticksExisted > nearTicks, nearPig.ticksExisted - nearTicks);
                check("simulation: the far pig holds still", farPig.ticksExisted == farTicks, farPig.ticksExisted - farTicks);
                check("simulation: the furnace at the spawn burns", burn(world, nearFurnace) < 1000, burn(world, nearFurnace));
                check("simulation: the far furnace holds", burn(world, farFurnace) == 1000, burn(world, farFurnace));
                check("simulation: the button at the spawn pops out (its scheduled tick ran)", !pressed(world, nearButton), "");
                check("simulation: the far button stays in (its scheduled tick waits)", pressed(world, farButton), SimulationDistance.deferredTicks);
                check("simulation: counters (entities, block entities, scheduled ticks)", SimulationDistance.skippedEntities > 0
                        && SimulationDistance.skippedTiles > 0 && SimulationDistance.deferredTicks > 0,
                        SimulationDistance.skippedEntities + "/" + SimulationDistance.skippedTiles + "/" + SimulationDistance.deferredTicks);
            } finally {
                SimulationDistance.set(0);
            }
            record();
        });
        then(30, "simulation distance off: everything loaded ticks again", world -> {
            world.resetUpdateEntityTick();
            check("simulation off: the far pig ticks", farPig.ticksExisted > farTicks, farPig.ticksExisted - farTicks);
            check("simulation off: the far furnace burns", burn(world, farFurnace) < 1000, burn(world, farFurnace));
            check("simulation off: the far button pops out", !pressed(world, farButton), "");
            farPig.setDead();
            nearPig.setDead();
        });
        then(1, "simulation distance: the square edges (26.3's chunk levels)", world -> {
            FakePlayer p = new FakePlayer(world, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "[SelfTestSim]"));
            p.chunkCoordX = 1000;
            p.chunkCoordZ = 1000;
            world.playerEntities.add(p);
            SimulationDistance.set(4);
            try {
                check("edges: entities tick 4 chunks away, diagonally too", SimulationDistance.entityTicking(world, chunk(1004, 996))
                        && SimulationDistance.entityTicking(world, chunk(996, 1004)), "");
                check("edges: entities stop 5 chunks away", !SimulationDistance.entityTicking(world, chunk(1005, 1000)), "");
                check("edges: block entities and random ticks reach one chunk further (5)", SimulationDistance.blockTicking(world, 1005, 995)
                        && !SimulationDistance.blockTicking(world, 1006, 1000), "");
                WorldEvent.PotentialSpawns near = spawns(world, chunk(1003, 1003));
                WorldEvent.PotentialSpawns far = spawns(world, chunk(1005, 1000));
                check("spawning: inside the distance mobs may spawn", !near.isCanceled(), "");
                check("spawning: outside it nothing is found to spawn", far.isCanceled(), SimulationDistance.deniedSpawns);
                SimulationDistance.set(0);
                check("off: everything ticks", SimulationDistance.entityTicking(world, chunk(5000, 5000))
                        && SimulationDistance.blockTicking(world, 5000, 5000) && !spawns(world, chunk(5000, 5000)).isCanceled(), "");
            } finally {
                world.playerEntities.remove(p);
                SimulationDistance.set(0);
            }
        });
        then(1, "simulation distance: server.properties", world -> {
            SimulationDistance.serverAboutToStart(FMLCommonHandler.instance().getMinecraftServerInstance());
            int d = SimulationDistance.distance();
            SimulationDistance.set(0);
            java.util.Properties props = new java.util.Properties();
            try (java.io.Reader r = new java.io.InputStreamReader(new java.io.FileInputStream("server.properties"), java.nio.charset.StandardCharsets.UTF_8)) {
                props.load(r);
            }
            boolean modern = com.exiledradio.backportcore.presentation.Presentation.newest().isAtLeast("1.18");
            check("server.properties: simulation-distance is there once a 1.18 or later version is installed, and applies",
                    !modern || props.getProperty(SimulationDistance.PROPERTY) != null && d == Math.max(2, Integer.parseInt(props.getProperty(SimulationDistance.PROPERTY))),
                    modern + " " + props.getProperty(SimulationDistance.PROPERTY) + " -> " + d);
        });
    }

    private static EntityPig pig(WorldServer world, BlockPos at) {
        EntityPig pig = new EntityPig(world);
        pig.setPosition(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        pig.setNoAI(true);
        pig.setNoGravity(true);
        pig.enablePersistence();
        world.spawnEntity(pig);
        return pig;
    }

    private static void press(WorldServer world, BlockPos pos) {
        world.setBlockState(pos, Blocks.STONE_BUTTON.getDefaultState().withProperty(BlockButton.FACING, net.minecraft.util.EnumFacing.UP)
                .withProperty(BlockButton.POWERED, true), 2);
        world.scheduleUpdate(pos, Blocks.STONE_BUTTON, 2);
    }

    private static boolean pressed(WorldServer world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() == Blocks.STONE_BUTTON && world.getBlockState(pos).getValue(BlockButton.POWERED);
    }

    private static int burn(WorldServer world, BlockPos pos) {
        return ((TileEntityFurnace) world.getTileEntity(pos)).getField(0);
    }

    private static void record() {
        nearTicks = nearPig.ticksExisted;
        farTicks = farPig.ticksExisted;
    }

    private static BlockPos chunk(int cx, int cz) {
        return new BlockPos(cx * 16 + 8, 64, cz * 16 + 8);
    }

    private static WorldEvent.PotentialSpawns spawns(WorldServer world, BlockPos pos) {
        WorldEvent.PotentialSpawns e = new WorldEvent.PotentialSpawns(world, EnumCreatureType.MONSTER, pos, new ArrayList<>());
        MinecraftForge.EVENT_BUS.post(e);
        return e;
    }
}

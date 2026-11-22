package com.exiledradio.backportcore.simulation;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import com.google.common.collect.AbstractIterator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

/**
 * 1.18's Simulation Distance on 1.12.2's server: entities, block entities, block ticks, random ticks and natural
 * spawning run only near players, apart from render distance, which still decides what is loaded. Patch P46
 * ({@code asm/SimulationTransformer}) and one event.
 *
 * <p>26.3's {@code DistanceManager}: each player puts a ticket on their chunk at level {@code 31 - distance}
 * ({@code ChunkLevel.byStatus(ENTITY_TICKING) - simulationDistance}), and levels rise by one a chunk in every
 * direction. A chunk at level 31 or lower ticks entities and its scheduled block and fluid ticks
 * ({@code inEntityTickingRange}; {@code LevelTicks} runs only there) and spawns mobs; at 32 or lower, block entities and
 * random ticks ({@code inBlockTickingRange}). So with the square (chessboard) distance {@code d} from a player's chunk:
 * entities and scheduled ticks at {@code d <= distance}, block entities and random ticks at {@code d <= distance + 1}.
 * Entities outside still get their despawn check ({@code ServerLevel.tick} calls {@code checkDespawn} first).
 *
 * <p>What 1.12.2 keeps loaded and ticking for other reasons ticks as before: chunks a mod keeps loaded (Forge's chunk
 * tickets, as 1.18's forced chunks) and the spawn chunks (1.18 keeps them ticking too). Random ticks stay within 1.12.2's
 * 128 blocks of a player ({@code PlayerChunkMap.getChunkIterator}) when the distance reaches further: a larger area
 * would cost more than 1.12.2 ever spent.
 *
 * <p>Where the distance comes from: the game's own world (and a LAN world it opens) from the Video Settings option, as
 * 1.18's {@code IntegratedServer} ({@code max(2, option)}), once the newest version shows it; a dedicated server from
 * {@code simulation-distance} in server.properties (1.18's default 10), once a 1.18 or later backport is installed.
 * Otherwise none: 1.12.2's ticking.
 */
public final class SimulationDistance {

    /** 1.18's server.properties key and default. */
    public static final String PROPERTY = "simulation-distance";
    public static final int DEFAULT_SERVER = 10;

    /** The distance in force, in chunks; 0 for none (1.12.2's ticking). */
    private static volatile int distance;
    /** From the client thread: the option's value while it is shown, else 0. */
    private static volatile int fromClient;
    private static boolean dedicated;

    /** For the self-tests: what was held back. */
    public static volatile int skippedEntities;
    public static volatile int skippedTiles;
    public static volatile int deferredTicks;
    public static volatile int skippedChunks;
    public static volatile int deniedSpawns;

    private static Method despawn;
    private static boolean despawnBroken;

    private SimulationDistance() {
    }

    /** @return the distance in force, in chunks, or 0 for none */
    public static int distance() {
        return distance;
    }

    /** For the self-test: sets the distance directly (0 for none); the next server start sets it again. */
    public static void set(int chunks) {
        distance = Math.max(0, chunks);
    }

    /** From the client tick: the Video Settings option, or 0 while it is not shown. */
    public static void fromClient(int chunks) {
        fromClient = chunks;
        if (!dedicated) {
            MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
            int want = chunks <= 0 ? 0 : Math.max(2, chunks);
            if (server != null && server.isSinglePlayer() && want != distance) {
                BackportCore.LOGGER.info("Changing simulation distance to {}", want);
                distance = want;
            }
        }
    }

    /** From Core's server-about-to-start: where the distance comes from for this server. */
    public static void serverAboutToStart(MinecraftServer server) {
        dedicated = server.isDedicatedServer();
        if (dedicated) {
            int d = Presentation.newest().isAtLeast("1.18") ? Math.max(2, DedicatedProperties.simulationDistance(server)) : 0;
            String test = selfTest();
            if (d > 0 && test != null) {
                // A backport's headless self-test server has no players, so only spawn chunks would tick.
                BackportCore.LOGGER.info("Simulation distance off for the self-test ({} is set)", test);
                d = 0;
            }
            distance = d;
            if (d > 0) {
                BackportCore.LOGGER.info("Simulation distance {} ({} in server.properties)", d, PROPERTY);
            }
        } else {
            distance = fromClient <= 0 ? 0 : Math.max(2, fromClient);
        }
    }

    /** @return the {@code <modid>.selftest} system property a loaded mod's self-test run sets (the family's convention), or null */
    @javax.annotation.Nullable
    private static String selfTest() {
        for (net.minecraftforge.fml.common.ModContainer mod : net.minecraftforge.fml.common.Loader.instance().getActiveModList()) {
            String key = mod.getModId() + ".selftest";
            if (System.getProperty(key) != null) {
                return key;
            }
        }
        return null;
    }

    // ---- The rule --------------------------------------------------------------------------------------------------

    /** @return true when chunk ({@code cx}, {@code cz}) is within {@code reach} chunks (square) of a player, kept loaded by a mod, or a spawn chunk */
    static boolean near(World world, int cx, int cz, int reach) {
        List<EntityPlayer> players = world.playerEntities;
        for (int i = 0, n = players.size(); i < n; i++) {
            EntityPlayer p = players.get(i);
            if (Math.abs(p.chunkCoordX - cx) <= reach && Math.abs(p.chunkCoordZ - cz) <= reach) {
                return true;
            }
        }
        if (world.provider.getDimensionType().shouldLoadSpawn() && world.isSpawnChunk(cx, cz)) {
            return true;
        }
        return !world.getPersistentChunks().isEmpty() && world.getPersistentChunks().containsKey(new ChunkPos(cx, cz));
    }

    /** @return true while the distance applies to this world (a server world, a distance set) */
    private static boolean applies(World world) {
        return distance > 0 && !world.isRemote;
    }

    /** @return true when entities and scheduled ticks at this block run (26.3's {@code inEntityTickingRange}) */
    public static boolean entityTicking(World world, BlockPos pos) {
        return !applies(world) || near(world, pos.getX() >> 4, pos.getZ() >> 4, distance);
    }

    /** @return true when block entities and random ticks in this chunk run (26.3's {@code inBlockTickingRange}) */
    public static boolean blockTicking(World world, int cx, int cz) {
        return !applies(world) || near(world, cx, cz, distance + 1);
    }

    // ---- P46's hooks -----------------------------------------------------------------------------------------------

    /**
     * P46, after {@code World.updateEntityWithOptionalForce}'s area check (reached only for a forced update of an
     * entity that is not a player): an entity outside the distance gets only its despawn check, as 26.3's
     * {@code ServerLevel.tick}. Answering false skips the tick, as an unloaded area does in 1.12.2 (Forge's
     * {@code EntityEvent.CanUpdate} may still let it tick).
     */
    public static boolean entityArea(boolean loaded, World world, Entity entity) {
        if (!loaded || !applies(world)) {
            return loaded;
        }
        int cx = net.minecraft.util.math.MathHelper.floor(entity.posX) >> 4;
        int cz = net.minecraft.util.math.MathHelper.floor(entity.posZ) >> 4;
        if (near(world, cx, cz, distance) || entity.isBeingRidden() && hasPlayerPassenger(entity)) {
            return true;
        }
        skippedEntities++;
        if (entity instanceof EntityLiving && !entity.isDead) {
            checkDespawn((EntityLiving) entity);
        }
        return false;
    }

    private static boolean hasPlayerPassenger(Entity entity) {
        for (Entity e : entity.getRecursivePassengers()) {
            if (e instanceof EntityPlayer) {
                return true;
            }
        }
        return false;
    }

    private static void checkDespawn(EntityLiving entity) {
        if (despawnBroken) {
            return;
        }
        try {
            if (despawn == null) {
                despawn = ReflectionHelper.findMethod(EntityLiving.class, "despawnEntity", "func_70623_bb");
            }
            despawn.invoke(entity);
        } catch (Exception e) {
            despawnBroken = true;
            BackportCore.LOGGER.warn("Simulation distance: no despawn check for entities outside it ({})", e.toString());
        }
    }

    /** P46, in place of {@code World.updateEntities}' {@code isBlockLoaded(pos, false)} before a block entity's tick. */
    public static boolean tileLoaded(World world, BlockPos pos, boolean allowEmpty) {
        if (!world.isBlockLoaded(pos, allowEmpty)) {
            return false;
        }
        if (blockTicking(world, pos.getX() >> 4, pos.getZ() >> 4)) {
            return true;
        }
        skippedTiles++;
        return false;
    }

    /**
     * P46, in place of {@code WorldServer.tickUpdates}' {@code isAreaLoaded(pos, pos)}: a scheduled tick outside the
     * distance waits (1.12.2 schedules it again for the next tick, as for an unloaded area), as 26.3's
     * {@code LevelTicks} keeps it until its chunk ticks.
     */
    public static boolean scheduledTickLoaded(World world, BlockPos from, BlockPos to) {
        if (!world.isAreaLoaded(from, to)) {
            return false;
        }
        if (entityTicking(world, from)) {
            return true;
        }
        deferredTicks++;
        return false;
    }

    /**
     * P46, in place of {@code WorldServer.updateBlocks}' {@code getPersistentChunkIterable(iterator)}: chunks outside
     * the distance get only their lighting work (what the loop does first: {@code enqueueRelightChecks} and
     * {@code onTick}); thunder, ice and snow and random ticks are left to the chunks inside.
     */
    public static Iterator<Chunk> randomTickChunks(World world, Iterator<Chunk> chunks) {
        Iterator<Chunk> all = world.getPersistentChunkIterable(chunks);
        if (!applies(world)) {
            return all;
        }
        return new AbstractIterator<Chunk>() {
            @Override
            protected Chunk computeNext() {
                while (all.hasNext()) {
                    Chunk c = all.next();
                    if (blockTicking(world, c.x, c.z)) {
                        return c;
                    }
                    skippedChunks++;
                    c.enqueueRelightChecks();
                    c.onTick(false);
                }
                return endOfData();
            }
        };
    }

    // ---- Natural spawning ------------------------------------------------------------------------------------------

    /**
     * Natural spawning ({@code WorldEntitySpawner.findChunksForSpawning}, through {@code getSpawnListEntryForTypeAt})
     * finds nothing to spawn outside the distance, as 26.3 spawns only in entity-ticking chunks. Spawners are block
     * entities (held back above) and chunk generation's animals do not ask this, so neither is touched.
     */
    public static final class Events {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onPotentialSpawns(WorldEvent.PotentialSpawns event) {
            World world = event.getWorld();
            if (world instanceof WorldServer && applies(world) && !event.isCanceled() && !entityTicking(world, event.getPos())) {
                deniedSpawns++;
                event.setCanceled(true);
            }
        }
    }
}

package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.structure.MapGenNetherBridge;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * 1.16's sharing of one placement grid between bastions and nether fortresses, exactly as 1.16.5
 * decides it, so a seed puts a bastion or a fortress in the chunks vanilla 1.16.5 does.
 *
 * <h2>1.16.5's rule</h2>
 * Both structures use spacing 27, separation 4 and salt 30084232 ({@link VanillaPlacements.V1_16_5}).
 * Each region has one candidate chunk, found from {@code setLargeFeatureWithSalt}; the same random then
 * draws {@code nextInt(5)}: below 2 is a fortress ({@code NetherFortressFeature.isFeatureChunk}), 2 or
 * more a bastion ({@code BastionFeature.isFeatureChunk}). Then the biome at the chunk's quart
 * {@code ((x << 2) + 2, 0, (z << 2) + 2)}, block {@code (x * 16 + 8, z * 16 + 8)}, must list the
 * structure ({@code ChunkGenerator.createStructures}): every 1.16 nether biome lists fortresses, and
 * every one but the basalt deltas lists bastions. So a bastion roll in the basalt deltas starts nothing.
 *
 * <h2>Turning it on</h2>
 * Nothing changes until a backport calls {@link #install} in pre-init. Then the nether's fortress
 * generator is swapped, through Forge's {@code InitMapGenEvent} (no patch), for vanilla's with this
 * rule in place of 1.12.2's 16-chunk grid, laid out with 1.16's start random. A fortress is decided
 * when its start is laid out, never piece by piece, so it never half-generates beside a bastion, and
 * {@code /locate Fortress}, blaze spawning and saved fortresses all keep working: fortresses already in
 * a world's saved data stay where they are. The bastion side is the backport's own
 * {@link ScatteredStructure}: its {@code canStartAt} asks {@link #startAt} for {@link Slot#BASTION}.
 *
 * <p>If another mod has already replaced the fortress generator with its own, it is left alone (with a
 * log line) and 1.12.2's fortresses stay; {@link #fortressRuleActive} says which happened.
 *
 * <p>{@link #roll} and {@link #startAt} generate and load nothing, so a seed tool can ask them for any
 * chunk and compare with an outside reference.
 */
public final class NetherStructureSlots {

    /** What a chunk of the shared grid starts. */
    public enum Slot {
        NONE,
        FORTRESS,
        BASTION
    }

    /**
     * Whether the biome at a candidate chunk allows a structure. Answer from the biome at block
     * {@code (chunkX * 16 + 8, chunkZ * 16 + 8)}, which is 1.16's quart {@code ((x << 2) + 2, (z << 2) + 2)},
     * without loading chunks.
     */
    @FunctionalInterface
    public interface BiomeRule {
        boolean allows(World world, int chunkX, int chunkZ, Slot slot);
    }

    /**
     * 1.16.5's lists: fortresses in every nether biome, bastions in every one but the basalt deltas
     * (any biome whose registry name is {@code basalt_deltas}).
     */
    public static final BiomeRule VANILLA_1_16_5 = (world, chunkX, chunkZ, slot) -> {
        if (slot != Slot.BASTION) {
            return true;
        }
        Biome biome = world.getBiomeProvider().getBiome(new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8));
        return biome == null || biome.getRegistryName() == null || !"basalt_deltas".equals(biome.getRegistryName().getPath());
    };

    private static final ScatteredPlacement GRID = VanillaPlacements.V1_16_5.NETHER_BRIDGE;
    private static final int FORTRESS_BELOW = 2;
    private static final int ROLL = 5;

    @Nullable
    private static volatile BiomeRule rule;
    private static volatile boolean registered;
    private static volatile boolean fortressRuleActive;
    private static final Map<World, SharedFortress> FORTRESSES = new WeakHashMap<World, SharedFortress>();

    private NetherStructureSlots() {
    }

    /**
     * Turns 1.16's shared slots on for the nether's fortresses. Call in pre-init, before any world loads.
     *
     * @param biomes which biome allows what; {@link #VANILLA_1_16_5} unless the backport's biomes
     *               need another answer
     */
    public static synchronized void install(BiomeRule biomes) {
        rule = biomes;
        if (!registered) {
            registered = true;
            MinecraftForge.TERRAIN_GEN_BUS.register(new NetherStructureSlots.Events());
        }
    }

    /** @return true once a backport has called {@link #install} */
    public static boolean installed() {
        return rule != null;
    }

    /** @return true if the nether's fortress generator follows this rule (false if another mod owns it) */
    public static boolean fortressRuleActive() {
        return fortressRuleActive;
    }

    /**
     * 1.16.5's draw alone, with no biome check: {@link Slot#NONE} for a chunk that is not its region's
     * candidate, else fortress or bastion. Pure arithmetic on the seed.
     */
    public static Slot roll(long worldSeed, int chunkX, int chunkZ) {
        int spacing = GRID.spacing();
        int range = spacing - GRID.separation();
        int regionX = Math.floorDiv(chunkX, spacing);
        int regionZ = Math.floorDiv(chunkZ, spacing);
        Random random = StructureRandoms.region(worldSeed, regionX, regionZ, GRID.salt());
        int x = regionX * spacing + random.nextInt(range);
        int z = regionZ * spacing + random.nextInt(range);
        if (x != chunkX || z != chunkZ) {
            return Slot.NONE;
        }
        return random.nextInt(ROLL) < FORTRESS_BELOW ? Slot.FORTRESS : Slot.BASTION;
    }

    /**
     * What starts in this chunk under 1.16.5's rule: {@link #roll} and the biome check. Loads and
     * generates nothing. Uses the installed {@link BiomeRule}, or {@link #VANILLA_1_16_5}.
     */
    public static Slot startAt(World world, int chunkX, int chunkZ) {
        Slot slot = roll(world.getSeed(), chunkX, chunkZ);
        if (slot == Slot.NONE) {
            return slot;
        }
        BiomeRule biomes = rule == null ? VANILLA_1_16_5 : rule;
        return biomes.allows(world, chunkX, chunkZ, slot) ? slot : Slot.NONE;
    }

    /**
     * @return true if the world's fortress generator starts a fortress in this chunk: one saved in the
     * world already, or one the rule starts there. False before the nether has generated anything.
     */
    public static boolean fortressStartsAt(World world, int chunkX, int chunkZ) {
        SharedFortress fortress;
        synchronized (FORTRESSES) {
            fortress = FORTRESSES.get(world);
        }
        return fortress != null && fortress.startsAt(chunkX, chunkZ);
    }

    /** @return the fortress start saved in the world for this chunk, or null */
    @Nullable
    public static StructureStart savedFortress(World world, int chunkX, int chunkZ) {
        SharedFortress fortress;
        synchronized (FORTRESSES) {
            fortress = FORTRESSES.get(world);
        }
        return fortress == null ? null : fortress.saved(chunkX, chunkZ);
    }

    public static final class Events {
        Events() {
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onMapGen(InitMapGenEvent event) {
            if (event.getType() != InitMapGenEvent.EventType.NETHER_BRIDGE || rule == null) {
                return;
            }
            if (event.getNewGen() != null && event.getNewGen().getClass() == MapGenNetherBridge.class) {
                event.setNewGen(new SharedFortress());
                fortressRuleActive = true;
            } else if (!(event.getNewGen() instanceof SharedFortress)) {
                BackportCore.LOGGER.warn("Another mod replaced the nether fortress generator ({}), so fortresses keep "
                        + "its placement and do not share slots with bastions.",
                        event.getNewGen() == null ? "none" : event.getNewGen().getClass().getName());
            }
        }
    }

    /** Vanilla's fortress, started by 1.16.5's shared rule and laid out with 1.16's start random. */
    static final class SharedFortress extends MapGenNetherBridge {

        @Override
        public void generate(World world, int x, int z, @Nullable ChunkPrimer primer) {
            remember(world);
            super.generate(world, x, z, primer);
        }

        @Override
        public synchronized boolean generateStructure(World world, Random random, ChunkPos chunk) {
            remember(world);
            return super.generateStructure(world, random, chunk);
        }

        private void remember(World world) {
            synchronized (FORTRESSES) {
                if (FORTRESSES.get(world) != this) {
                    FORTRESSES.put(world, this);
                }
            }
        }

        @Override
        protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
            return startAt(this.world, chunkX, chunkZ) == Slot.FORTRESS;
        }

        @Override
        protected StructureStart getStructureStart(int chunkX, int chunkZ) {
            return new MapGenNetherBridge.Start(this.world, StructureRandoms.start(this.world.getSeed(), chunkX, chunkZ), chunkX, chunkZ);
        }

        boolean startsAt(int chunkX, int chunkZ) {
            return saved(chunkX, chunkZ) != null || canSpawnStructureAtCoords(chunkX, chunkZ);
        }

        @Nullable
        StructureStart saved(int chunkX, int chunkZ) {
            if (this.world == null) {
                return null;
            }
            initializeStructureData(this.world);
            return this.structureMap.get(ChunkPos.asLong(chunkX, chunkZ));
        }
    }
}

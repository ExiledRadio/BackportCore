package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.structure.MapGenStructure;
import net.minecraft.world.gen.structure.StructureStart;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.registry.GameRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * A structure placed the way today's game places most of its own: one candidate chunk per region
 * ({@link ScatteredPlacement}), a check of the biome there, then a layout saved with the world and
 * built chunk by chunk. Shipwrecks, ocean ruins, ruined portals, bastions.
 *
 * <h2>Generation without touching the chunk generator</h2>
 * 1.12.2 gives a mod no slot for a new structure in another mod's (or vanilla's) chunk generator,
 * and replacing generators fights every terrain mod. So Core drives every registered structure from
 * the populate step instead, the way Village &amp; Pillage drives its outposts: each chunk populated
 * first lays out every structure starting within eight chunks of it ({@code MapGenBase}'s range),
 * then builds its own part of them. A chunk is only populated once its neighbors exist, so a
 * structure that reaches at most eight chunks from its start is always laid out before any chunk it
 * touches is built. Lay out on {@link com.exiledradio.backportcore.terrain.TerrainHeights}, which
 * knows the ground (and the sea floor) of chunks that do not exist yet.
 *
 * <h2>Writing one</h2>
 * <pre>
 * public class MapGenShipwreck extends ScatteredStructure {
 *     public MapGenShipwreck() { super("UAB_Shipwreck", new ScatteredPlacement(24, 4, 165745295)); }
 *     protected boolean canStartAt(World world, int chunkX, int chunkZ) { ...biome at the chunk's middle... }
 *     protected StructureStart getStructureStart(int chunkX, int chunkZ) { return new ShipwreckStart(...); }
 * }
 * // init:
 * MapGenStructureIO.registerStructure(ShipwreckStart.class, "UAB_Shipwreck");
 * MapGenStructureIO.registerStructureComponent(ShipwreckPiece.class, "UAB_ShipwreckPiece");
 * ScatteredStructure.register(Structures.SHIPWRECK, world -&gt; enabledIn(world) ? new MapGenShipwreck() : null);
 * </pre>
 * Registering also makes the kind findable through {@link Structures}.
 */
public abstract class ScatteredStructure extends MapGenStructure {

    private static final Map<String, Function<World, ? extends ScatteredStructure>> FACTORIES =
            new LinkedHashMap<String, Function<World, ? extends ScatteredStructure>>();
    private static final Map<World, Map<String, ScatteredStructure>> BY_WORLD = new WeakHashMap<World, Map<String, ScatteredStructure>>();
    /** Marks a kind that a world has none of, since the factory is asked once per world. */
    private static final ScatteredStructure NONE = new ScatteredStructure("none", new ScatteredPlacement(2, 1, 0)) {
        @Override
        protected boolean canStartAt(World world, int chunkX, int chunkZ) {
            return false;
        }

        @Override
        protected StructureStart getStructureStart(int chunkX, int chunkZ) {
            throw new IllegalStateException();
        }
    };

    /** Low, so structures are in place before decoration (kelp, trees) fills in around them, as today. */
    private static final int WEIGHT = 0;
    private static boolean driving;

    private final String saveName;
    protected final ScatteredPlacement placement;

    /**
     * @param saveName the name its starts are saved under ({@code data/<saveName>.dat}), permanent once
     *                 worlds have it; prefix it with your mod
     */
    protected ScatteredStructure(String saveName, ScatteredPlacement placement) {
        this.saveName = saveName;
        this.placement = placement;
    }

    @Override
    public String getStructureName() {
        return this.saveName;
    }

    public ScatteredPlacement placement() {
        return this.placement;
    }

    /**
     * Whether the region's candidate chunk may start one: the biome check, today's extra rolls. Asked
     * by locators too, so it must not load or generate chunks; read biomes through
     * {@code world.getBiomeProvider()}.
     */
    protected abstract boolean canStartAt(World world, int chunkX, int chunkZ);

    @Override
    protected boolean canSpawnStructureAtCoords(int chunkX, int chunkZ) {
        return this.placement.isCandidate(this.world.getSeed(), chunkX, chunkZ) && canStartAt(this.world, chunkX, chunkZ);
    }

    /** @return true if a structure of this kind starts in the chunk (laid out or not) */
    public boolean startsAt(World world, int chunkX, int chunkZ) {
        this.world = world;
        return canSpawnStructureAtCoords(chunkX, chunkZ);
    }

    /**
     * @return the middle of the nearest start chunk within the radius, at {@code from}'s height, or
     * null. A start already laid out and found empty (a layout that gave up) is skipped.
     */
    @Nullable
    public BlockPos nearest(World world, BlockPos from, int radiusChunks) {
        this.world = world;
        initializeStructureData(world);
        ChunkPos found = this.placement.nearest(world.getSeed(), from, radiusChunks, c -> locatableAt(world, c.x, c.z));
        if (found == null) {
            return null;
        }
        StructureStart known = this.structureMap.get(ChunkPos.asLong(found.x, found.z));
        if (known != null && known.getBoundingBox() != null) {
            net.minecraft.world.gen.structure.StructureBoundingBox box = known.getBoundingBox();
            return new BlockPos((box.minX + box.maxX) / 2, (box.minY + box.maxY) / 2, (box.minZ + box.maxZ) / 2);
        }
        return new BlockPos((found.x << 4) + 8, from.getY(), (found.z << 4) + 8);
    }

    /**
     * Whether a candidate chunk of this kind's grid holds a start a locator should report: the kind's own start
     * rule, and not a start already laid out and found empty (today's {@code StructureStart.isValid}). Asked only
     * of candidate chunks; loads and generates nothing.
     */
    public boolean locatableAt(World world, int chunkX, int chunkZ) {
        this.world = world;
        if (!canStartAt(world, chunkX, chunkZ)) {
            return false;
        }
        initializeStructureData(world);
        StructureStart known = this.structureMap.get(ChunkPos.asLong(chunkX, chunkZ));
        return known == null || known.isSizeableStructure();
    }

    @Nullable
    @Override
    public BlockPos getNearestStructurePos(World world, BlockPos pos, boolean findUnexplored) {
        return nearest(world, pos, this.placement.spacing() * 100);
    }

    // ---- driving

    /**
     * Registers a structure kind: Core lays it out and builds it in every world the factory gives an
     * instance for, and {@link Structures} finds it. Call in init.
     *
     * @param kind today's structure name, as {@link Structures} uses ({@link Structures#SHIPWRECK})
     * @param factory a new instance for a world, or null for a world that never has this structure
     *                (another dimension, the feature turned off); asked once per world
     */
    public static synchronized void register(String kind, Function<World, ? extends ScatteredStructure> factory) {
        FACTORIES.put(kind, factory);
        Structures.register(kind, (world, from, radius) -> {
            ScatteredStructure structure = of(world, kind);
            return structure == null ? null : structure.nearest(world, from, radius);
        });
        if (!driving) {
            driving = true;
            GameRegistry.registerWorldGenerator(new Driver(), WEIGHT);
        }
    }

    /** @return every registered kind, in registration order */
    public static synchronized List<String> kinds() {
        return new ArrayList<String>(FACTORIES.keySet());
    }

    /**
     * The variant a start in this chunk would be, where the seed map tells variants apart (warm or cold
     * ocean ruins), or null; only asked of a chunk that starts one. Core's {@code seedcheck.OverworldDump}
     * names starts {@code <kind>_<variant>} with it.
     */
    @Nullable
    public String variantAt(World world, int chunkX, int chunkZ) {
        return null;
    }

    /** @return this world's instance of a registered kind, or null where it has none */
    @Nullable
    public static synchronized ScatteredStructure of(World world, String kind) {
        if (world.isRemote) {
            return null;
        }
        Map<String, ScatteredStructure> known = BY_WORLD.get(world);
        if (known == null) {
            known = new HashMap<String, ScatteredStructure>();
            BY_WORLD.put(world, known);
        }
        ScatteredStructure structure = known.get(kind);
        if (structure == null) {
            Function<World, ? extends ScatteredStructure> factory = FACTORIES.get(kind);
            structure = factory == null ? null : factory.apply(world);
            if (structure == null) {
                structure = NONE;
            } else {
                structure.world = world;
            }
            known.put(kind, structure);
        }
        return structure == NONE ? null : structure;
    }

    /** @return true if the position is inside a laid-out structure of this kind */
    public static boolean isInside(World world, String kind, BlockPos pos) {
        ScatteredStructure structure = of(world, kind);
        return structure != null && structure.isPositionInStructure(world, pos);
    }

    private static final class Driver implements IWorldGenerator {
        @Override
        public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator generator,
                             IChunkProvider provider) {
            if (!(world instanceof WorldServer)) {
                return;
            }
            List<String> kinds;
            synchronized (ScatteredStructure.class) {
                kinds = new ArrayList<String>(FACTORIES.keySet());
            }
            ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
            for (String kind : kinds) {
                ScatteredStructure structure = of(world, kind);
                if (structure == null) {
                    continue;
                }
                try {
                    // Lays out every start within range; the primer is never touched by a structure.
                    structure.generate(world, chunkX, chunkZ, null);
                    structure.generateStructure(world, random, chunk);
                } catch (RuntimeException e) {
                    BackportCore.LOGGER.error("Could not build a {} at chunk {}, {}.", kind, chunkX, chunkZ, e);
                }
            }
        }
    }
}

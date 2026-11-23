package com.exiledradio.backportcore.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Random;
import java.util.function.Function;

/**
 * A structure set's {@code random_spread} placement as 26.3 reads it ({@code RandomSpreadStructurePlacement} and its
 * {@code AbstractSpreadingStructurePlacement}): the grid ({@link ScatteredPlacement}: spacing, separation, salt,
 * spread type), then {@code frequency} with its {@code frequency_reduction_method}, then the {@code exclusion_zone}
 * around another set's chunks; and the {@code locate_offset}. Pure arithmetic on the world seed.
 *
 * @since 1.4.5
 */
public final class DataPlacement {

    /** Today's {@code FrequencyReductionMethod}, each with 26.3's random. */
    public enum FrequencyReduction {
        /** {@code probabilityReducer}: 26.3 passes the salt where the region x goes (its argument order), kept. */
        DEFAULT,
        /** {@code legacyPillagerOutpostReducer}. */
        LEGACY_TYPE_1,
        /** {@code legacyArbitrarySaltProbabilityReducer}: salt 10387320. */
        LEGACY_TYPE_2,
        /** {@code legacyProbabilityReducerWithDouble}: the large feature seed's {@code nextDouble}. */
        LEGACY_TYPE_3;

        public static FrequencyReduction of(String name) {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }

        public boolean shouldGenerate(long seed, int salt, int x, int z, float probability) {
            switch (this) {
                case LEGACY_TYPE_1: {
                    int cx = x >> 4;
                    int cz = z >> 4;
                    Random random = new Random(cx ^ cz << 4 ^ seed);
                    random.nextInt();
                    return random.nextInt((int) (1.0F / probability)) == 0;
                }
                case LEGACY_TYPE_2:
                    return StructureRandoms.region(seed, x, z, 10387320).nextFloat() < probability;
                case LEGACY_TYPE_3:
                    return StructureRandoms.start(seed, x, z).nextDouble() < probability;
                default:
                    // setLargeFeatureWithSalt(seed, salt, sourceX, sourceZ): salt * 341873128712 + x * 132897987541 + seed + z.
                    return StructureRandoms.region(seed, salt, x, z).nextFloat() < probability;
            }
        }
    }

    private final ScatteredPlacement grid;
    private final float frequency;
    private final FrequencyReduction reduction;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;
    @Nullable
    private final ResourceLocation exclusionSet;
    private final int exclusionChunks;

    public DataPlacement(ScatteredPlacement grid, float frequency, FrequencyReduction reduction, int offsetX, int offsetY,
                         int offsetZ, @Nullable ResourceLocation exclusionSet, int exclusionChunks) {
        this.grid = grid;
        this.frequency = frequency;
        this.reduction = reduction;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.exclusionSet = exclusionSet;
        this.exclusionChunks = exclusionChunks;
    }

    /**
     * Reads a set's {@code placement}.
     *
     * @throws IllegalArgumentException for another placement type (the caller logs it) or a value 26.3 refuses
     */
    public static DataPlacement read(JsonObject p) {
        String type = ModernPalette.full(p.get("type").getAsString());
        if (!"minecraft:random_spread".equals(type)) {
            throw new UnsupportedOperationException(type);
        }
        int spacing = p.get("spacing").getAsInt();
        int separation = p.get("separation").getAsInt();
        int salt = p.get("salt").getAsInt();
        String spread = p.has("spread_type") ? p.get("spread_type").getAsString() : "linear";
        if (!"linear".equals(spread) && !"triangular".equals(spread)) {
            throw new IllegalArgumentException("Unknown spread_type " + spread);
        }
        float frequency = p.has("frequency") ? p.get("frequency").getAsFloat() : 1.0F;
        if (frequency < 0 || frequency > 1) {
            throw new IllegalArgumentException("frequency " + frequency + " is outside 0 to 1");
        }
        FrequencyReduction reduction = p.has("frequency_reduction_method")
                ? FrequencyReduction.of(p.get("frequency_reduction_method").getAsString()) : FrequencyReduction.DEFAULT;
        int ox = 0;
        int oy = 0;
        int oz = 0;
        if (p.has("locate_offset")) {
            JsonArray a = p.getAsJsonArray("locate_offset");
            ox = a.get(0).getAsInt();
            oy = a.get(1).getAsInt();
            oz = a.get(2).getAsInt();
        }
        ResourceLocation other = null;
        int chunks = 0;
        if (p.has("exclusion_zone")) {
            JsonObject z = p.getAsJsonObject("exclusion_zone");
            if (!z.get("other_set").isJsonPrimitive()) {
                throw new IllegalArgumentException("exclusion_zone other_set must be a structure set's id");
            }
            other = new ResourceLocation(z.get("other_set").getAsString());
            chunks = z.get("chunk_count").getAsInt();
        }
        return new DataPlacement(new ScatteredPlacement(spacing, separation, salt, "triangular".equals(spread)), frequency,
                reduction, ox, oy, oz, other, chunks);
    }

    public ScatteredPlacement grid() {
        return this.grid;
    }

    public float frequency() {
        return this.frequency;
    }

    public FrequencyReduction reduction() {
        return this.reduction;
    }

    public int offsetX() {
        return this.offsetX;
    }

    public int offsetY() {
        return this.offsetY;
    }

    public int offsetZ() {
        return this.offsetZ;
    }

    @Nullable
    public ResourceLocation exclusionSet() {
        return this.exclusionSet;
    }

    /** Today's {@code applyAdditionalChunkRestrictions}: the frequency roll. */
    public boolean passesFrequency(long seed, int chunkX, int chunkZ) {
        return !(this.frequency < 1.0F) || this.reduction.shouldGenerate(seed, this.grid.salt(), chunkX, chunkZ, this.frequency);
    }

    /**
     * Today's {@code isStructureChunk}: the grid's candidate, the frequency roll and the exclusion zone.
     *
     * @param sets the placement of a set by id (for the exclusion zone), or null for one not known
     */
    public boolean isStructureChunk(long seed, int chunkX, int chunkZ, Function<ResourceLocation, DataPlacement> sets) {
        return this.grid.isCandidate(seed, chunkX, chunkZ) && passesRestrictions(seed, chunkX, chunkZ, sets, 0);
    }

    /** The frequency roll and the exclusion zone, for a chunk known to be the grid's candidate. */
    public boolean passesRestrictions(long seed, int chunkX, int chunkZ, Function<ResourceLocation, DataPlacement> sets) {
        return passesRestrictions(seed, chunkX, chunkZ, sets, 0);
    }

    private boolean passesRestrictions(long seed, int chunkX, int chunkZ, Function<ResourceLocation, DataPlacement> sets, int depth) {
        if (!passesFrequency(seed, chunkX, chunkZ)) {
            return false;
        }
        if (this.exclusionSet == null || depth > 4) {
            return true;
        }
        DataPlacement other = sets.apply(this.exclusionSet);
        if (other == null) {
            return true;
        }
        // Today's hasStructureChunkInRange: any chunk of the square that is the other set's structure chunk.
        int r = this.exclusionChunks;
        for (int x = chunkX - r; x <= chunkX + r; x++) {
            for (int z = chunkZ - r; z <= chunkZ + r; z++) {
                if (other.grid.isCandidate(seed, x, z) && other.passesRestrictions(seed, x, z, sets, depth + 1)) {
                    return false;
                }
            }
        }
        return true;
    }
}

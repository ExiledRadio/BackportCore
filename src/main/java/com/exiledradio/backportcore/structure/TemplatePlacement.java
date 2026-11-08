package com.exiledradio.backportcore.structure;

import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * How a {@link ModernTemplate} goes into the world: today's {@code StructurePlaceSettings}, in
 * 1.12.2 terms. Chainable; every setter returns this.
 *
 * <p>Defaults are today's: no rotation or mirror, pivot at the template's corner, every block
 * (integrity 1), air placed, structure blocks left out, water kept where a block can hold it,
 * entities placed, block flag 2.
 */
public final class TemplatePlacement {

    Rotation rotation = Rotation.NONE;
    Mirror mirror = Mirror.NONE;
    BlockPos pivot = BlockPos.ORIGIN;
    float integrity = 1.0F;
    long seed;
    @Nullable
    StructureBoundingBox clip;
    boolean ignoreAir;
    boolean keepWater = true;
    boolean entities = true;
    boolean finalizeEntities;
    @Nullable
    IntBinaryOperator surface;
    int surfaceOffset;
    int palette = -1;
    int flags = 2;
    final List<TemplateProcessor> processors = new ArrayList<TemplateProcessor>();

    public static TemplatePlacement of(Rotation rotation, Mirror mirror) {
        return new TemplatePlacement().rotation(rotation).mirror(mirror);
    }

    public TemplatePlacement rotation(Rotation rotation) {
        this.rotation = rotation;
        return this;
    }

    public TemplatePlacement mirror(Mirror mirror) {
        this.mirror = mirror;
        return this;
    }

    /** The template position the rotation turns around (today's "center offset"). */
    public TemplatePlacement pivot(BlockPos pivot) {
        this.pivot = pivot;
        return this;
    }

    /**
     * Today's integrity processor: each block is placed with this chance, decided per world position
     * (the same block of the same structure always comes out the same, whichever chunk places it).
     */
    public TemplatePlacement integrity(float integrity) {
        this.integrity = integrity;
        return this;
    }

    /**
     * Mixed into every per-block random (integrity and processors) and the palette choice. 0, the
     * default, is today's position-only random.
     */
    public TemplatePlacement seed(long seed) {
        this.seed = seed;
        return this;
    }

    /** Only blocks and entities inside this box: the chunk being populated. Null for the whole template. */
    public TemplatePlacement clip(@Nullable StructureBoundingBox clip) {
        this.clip = clip;
        return this;
    }

    /** Leaves the world as it is where the template has air (today's {@code AIR_AND_STRUCTURE_BLOCK}). */
    public TemplatePlacement ignoreAir(boolean ignoreAir) {
        this.ignoreAir = ignoreAir;
        return this;
    }

    /**
     * Keeps a water source that is already there in a block that can hold it, as today's
     * structures do underwater. Blocks the file marks {@code waterlogged=true} get water either way.
     */
    public TemplatePlacement keepWater(boolean keepWater) {
        this.keepWater = keepWater;
        return this;
    }

    public TemplatePlacement entities(boolean entities) {
        this.entities = entities;
        return this;
    }

    /**
     * Gives each spawned mob its first-spawn setup ({@code onInitialSpawn}: equipment and the like), as
     * today's jigsaw pieces do ({@code setFinalizeEntities}). Off by default. Since 1.1.0.
     */
    public TemplatePlacement finalizeEntities(boolean finalizeEntities) {
        this.finalizeEntities = finalizeEntities;
        return this;
    }

    /**
     * Today's gravity processor (a {@code terrain_matching} jigsaw piece): each block goes to the ground
     * height at its column plus {@code offset} plus its own height in the file. Processors still see the
     * position before the move, as today's do. Null to turn it off (the default). Since 1.1.0.
     *
     * @param surface the ground height at a column (x, z), as 1.16's {@code WORLD_SURFACE_WG}
     */
    public TemplatePlacement followTerrain(@Nullable IntBinaryOperator surface, int offset) {
        this.surface = surface;
        this.surfaceOffset = offset;
        return this;
    }

    /**
     * Which of a multi-palette file's palettes to build with (shipwrecks have one per wood). -1, the
     * default, picks one from the origin and {@link #seed}, as today's game picks one at random.
     */
    public TemplatePlacement palette(int palette) {
        this.palette = palette;
        return this;
    }

    /** Block flags for {@code World.setBlockState}; 2 tells clients and leaves neighbors alone. */
    public TemplatePlacement flags(int flags) {
        this.flags = flags;
        return this;
    }

    /** Adds a processor, run after the ones added before it. */
    public TemplatePlacement processor(TemplateProcessor processor) {
        this.processors.add(processor);
        return this;
    }

    public Rotation rotation() {
        return this.rotation;
    }

    public Mirror mirror() {
        return this.mirror;
    }

    public BlockPos pivot() {
        return this.pivot;
    }

    public float integrity() {
        return this.integrity;
    }

    public long seed() {
        return this.seed;
    }

    @Nullable
    public StructureBoundingBox clip() {
        return this.clip;
    }

    public List<TemplateProcessor> processors() {
        return Collections.unmodifiableList(this.processors);
    }

    /** @return a copy, for a caller that changes the clip per chunk */
    public TemplatePlacement copy() {
        TemplatePlacement out = new TemplatePlacement();
        out.rotation = this.rotation;
        out.mirror = this.mirror;
        out.pivot = this.pivot;
        out.integrity = this.integrity;
        out.seed = this.seed;
        out.clip = this.clip;
        out.ignoreAir = this.ignoreAir;
        out.keepWater = this.keepWater;
        out.entities = this.entities;
        out.finalizeEntities = this.finalizeEntities;
        out.surface = this.surface;
        out.surfaceOffset = this.surfaceOffset;
        out.palette = this.palette;
        out.flags = this.flags;
        out.processors.addAll(this.processors);
        return out;
    }
}

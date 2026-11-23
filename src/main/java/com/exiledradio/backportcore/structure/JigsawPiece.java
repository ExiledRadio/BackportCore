package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.template.TemplateManager;

import java.util.Random;
import java.util.function.IntBinaryOperator;

/**
 * One piece of a jigsaw structure: today's {@code PoolElementStructurePiece}. Made by
 * {@link Jigsaw#assemble}, saved with the world, and placed chunk by chunk as each chunk is
 * populated (the chunk is the clip; no neighbor is loaded).
 *
 * <p>Core registers this class as {@code BCJigsaw}. Subclass it for a structure that fills data
 * markers or does more ({@link #handleDataMarker}); a subclass needs a public no-argument
 * constructor, its own {@code MapGenStructureIO} name, and a {@link Jigsaw.PieceFactory}.
 */
public class JigsawPiece extends StructureComponent {

    protected JigsawElement element;
    protected BlockPos position = BlockPos.ORIGIN;
    protected int groundLevelDelta;
    protected Rotation pieceRotation = Rotation.NONE;
    /** Today's placement pivot: the middle of the structure's first piece at its floor. */
    protected BlockPos pivot = BlockPos.ORIGIN;

    /** For loading only. */
    public JigsawPiece() {
    }

    public JigsawPiece(JigsawElement element, BlockPos position, int groundLevelDelta, Rotation rotation, StructureBoundingBox box) {
        super(0);
        this.element = element;
        this.position = position;
        this.groundLevelDelta = groundLevelDelta;
        this.pieceRotation = rotation;
        this.boundingBox = box;
    }

    public JigsawElement element() {
        return this.element;
    }

    public BlockPos position() {
        return this.position;
    }

    public Rotation rotation() {
        return this.pieceRotation;
    }

    public int groundLevelDelta() {
        return this.groundLevelDelta;
    }

    public BlockPos pivot() {
        return this.pivot;
    }

    void setPivot(BlockPos pivot) {
        this.pivot = pivot;
    }

    @Override
    public void offset(int x, int y, int z) {
        super.offset(x, y, z);
        this.position = this.position.add(x, y, z);
    }

    /**
     * Today's {@code liquid_settings}: true ({@code apply_waterlogging}, the default) keeps a water source already there
     * in a block of the template that can hold it; false ({@code ignore_waterlogging}) places the template dry.
     *
     * @since 1.4.5
     */
    protected boolean applyWaterlogging() {
        return true;
    }

    /**
     * Fills a data marker of the piece's template in the chunk being placed. Called after the
     * template's blocks, once per marker inside {@code chunk}; vanilla's jigsaw pieces have none.
     *
     * @param pos the marker's position in the world
     */
    protected void handleDataMarker(String metadata, BlockPos pos, World world, Random random, StructureBoundingBox chunk) {
    }

    @Override
    public boolean addComponentParts(World world, Random random, StructureBoundingBox chunk) {
        if (this.element == null) {
            return true;
        }
        this.element.place(world, this.position, this.pieceRotation, chunk, random, this.pivot, surface(world), this);
        return true;
    }

    /**
     * The ground for {@code terrain_matching} elements at placement: the column's height in its
     * chunk, which is loaded (only columns inside the chunk being placed are asked).
     */
    protected IntBinaryOperator surface(World world) {
        return (x, z) -> {
            Chunk c = world.getChunkProvider().getLoadedChunk(x >> 4, z >> 4);
            return c == null ? this.position.getY() : c.getHeightValue(x & 15, z & 15);
        };
    }

    @Override
    protected void writeStructureToNBT(NBTTagCompound tag) {
        tag.setInteger("PosX", this.position.getX());
        tag.setInteger("PosY", this.position.getY());
        tag.setInteger("PosZ", this.position.getZ());
        tag.setInteger("ground_level_delta", this.groundLevelDelta);
        tag.setString("pool_element", this.element == null ? "" : this.element.json.toString());
        tag.setString("rotation", this.pieceRotation.name());
        tag.setInteger("PvX", this.pivot.getX());
        tag.setInteger("PvY", this.pivot.getY());
        tag.setInteger("PvZ", this.pivot.getZ());
    }

    @Override
    protected void readStructureFromNBT(NBTTagCompound tag, TemplateManager manager) {
        this.position = new BlockPos(tag.getInteger("PosX"), tag.getInteger("PosY"), tag.getInteger("PosZ"));
        this.groundLevelDelta = tag.getInteger("ground_level_delta");
        try {
            this.pieceRotation = Rotation.valueOf(tag.getString("rotation"));
        } catch (IllegalArgumentException e) {
            this.pieceRotation = Rotation.NONE;
        }
        this.pivot = new BlockPos(tag.getInteger("PvX"), tag.getInteger("PvY"), tag.getInteger("PvZ"));
        String json = tag.getString("pool_element");
        try {
            this.element = json.isEmpty() ? null : JigsawPools.element(json);
        } catch (RuntimeException e) {
            BackportCore.LOGGER.error("A saved jigsaw piece could not be read; it is left out: {}", json, e);
            this.element = null;
        }
    }

    @Override
    public String toString() {
        return "JigsawPiece<" + this.position + " " + this.pieceRotation + " " + this.element + ">";
    }
}

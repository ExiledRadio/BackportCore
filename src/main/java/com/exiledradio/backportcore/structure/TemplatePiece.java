package com.exiledradio.backportcore.structure;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Mirror;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.template.TemplateManager;

import java.util.Random;

/**
 * One {@link ModernTemplate} as a piece of a 1.12.2 structure start: today's
 * {@code TemplateStructurePiece}. A structure laid out once, when its first chunk generates, is
 * saved with the world and placed chunk by chunk as each chunk is populated, through
 * {@link #addComponentParts}.
 *
 * <p>Subclass it per structure (a shipwreck piece, an ocean ruin piece): override
 * {@link #placement()} for the structure's settings and processors, and {@link #handleDataMarker}
 * for its data markers. Register the subclass with
 * {@code MapGenStructureIO.registerStructureComponent(YourPiece.class, "<modid>:<name>")}; it needs a
 * public no-argument constructor for loading. Settings that are not fields here go in
 * {@link #writeExtra}/{@link #readExtra}.
 */
public abstract class TemplatePiece extends StructureComponent {

    protected ResourceLocation template = new ResourceLocation("minecraft", "empty");
    protected BlockPos origin = BlockPos.ORIGIN;
    protected Rotation rotation = Rotation.NONE;
    protected Mirror mirror = Mirror.NONE;
    protected BlockPos pivot = BlockPos.ORIGIN;
    protected float integrity = 1.0F;
    protected long seed;
    /** The palette, chosen at layout so every chunk builds with the same wood. */
    protected int palette;

    /** For loading only. */
    public TemplatePiece() {
    }

    /**
     * @param origin where the template's corner goes, before rotation around the pivot
     * @param random the layout's random: picks the palette
     */
    protected TemplatePiece(ResourceLocation template, BlockPos origin, Rotation rotation, Mirror mirror, BlockPos pivot,
                            Random random) {
        super(0);
        this.template = template;
        this.origin = origin;
        this.rotation = rotation;
        this.mirror = mirror;
        this.pivot = pivot;
        this.seed = random.nextLong();
        this.palette = random.nextInt(template().palettes());
        updateBoundingBox();
    }

    public ModernTemplate template() {
        return ModernTemplate.load(this.template);
    }

    public BlockPos origin() {
        return this.origin;
    }

    /** Moves the piece, as a layout does once it knows the ground height. */
    public void setOrigin(BlockPos origin) {
        this.origin = origin;
        updateBoundingBox();
    }

    protected void updateBoundingBox() {
        this.boundingBox = template().boundingBox(this.origin, basePlacement());
    }

    @Override
    public void offset(int x, int y, int z) {
        super.offset(x, y, z);
        this.origin = this.origin.add(x, y, z);
    }

    private TemplatePlacement basePlacement() {
        return new TemplatePlacement().rotation(this.rotation).mirror(this.mirror).pivot(this.pivot)
                .integrity(this.integrity).seed(this.seed).palette(this.palette);
    }

    /**
     * The settings this piece places with; override to add today's processors, ignore air, and so on.
     * Call {@code super.placement()} and add to it: rotation, mirror, pivot, integrity, seed and palette
     * are set from the piece. The clip is set by {@link #addComponentParts}.
     */
    protected TemplatePlacement placement() {
        return basePlacement();
    }

    /**
     * Fills a data marker in the chunk being placed: put the chest there, spawn the drowned. Called
     * after the template's blocks, once per marker inside {@code chunk}.
     *
     * @param pos the marker's position in the world
     */
    protected void handleDataMarker(String metadata, BlockPos pos, World world, Random random, StructureBoundingBox chunk) {
    }

    @Override
    public boolean addComponentParts(World world, Random random, StructureBoundingBox chunk) {
        TemplatePlacement placement = placement().clip(chunk);
        ModernTemplate template = template();
        template.place(world, this.origin, placement);
        for (ModernTemplate.DataMarker marker : template.dataMarkers()) {
            BlockPos pos = ModernTemplate.worldPos(this.origin, marker.pos, placement);
            if (chunk.isVecInside(pos)) {
                handleDataMarker(marker.metadata, pos, world, random, chunk);
            }
        }
        return true;
    }

    @Override
    protected void writeStructureToNBT(NBTTagCompound tag) {
        tag.setString("Template", this.template.toString());
        tag.setInteger("TPX", this.origin.getX());
        tag.setInteger("TPY", this.origin.getY());
        tag.setInteger("TPZ", this.origin.getZ());
        tag.setString("Rot", this.rotation.name());
        tag.setString("Mi", this.mirror.name());
        tag.setInteger("PvX", this.pivot.getX());
        tag.setInteger("PvY", this.pivot.getY());
        tag.setInteger("PvZ", this.pivot.getZ());
        tag.setFloat("Integrity", this.integrity);
        tag.setLong("Seed", this.seed);
        tag.setInteger("Palette", this.palette);
        writeExtra(tag);
    }

    @Override
    protected void readStructureFromNBT(NBTTagCompound tag, TemplateManager manager) {
        this.template = new ResourceLocation(tag.getString("Template"));
        this.origin = new BlockPos(tag.getInteger("TPX"), tag.getInteger("TPY"), tag.getInteger("TPZ"));
        this.rotation = enumOr(Rotation.class, tag.getString("Rot"), Rotation.NONE);
        this.mirror = enumOr(Mirror.class, tag.getString("Mi"), Mirror.NONE);
        this.pivot = new BlockPos(tag.getInteger("PvX"), tag.getInteger("PvY"), tag.getInteger("PvZ"));
        this.integrity = tag.hasKey("Integrity") ? tag.getFloat("Integrity") : 1.0F;
        this.seed = tag.getLong("Seed");
        this.palette = tag.getInteger("Palette");
        readExtra(tag);
    }

    /** Saves the subclass's own settings with the piece. */
    protected void writeExtra(NBTTagCompound tag) {
    }

    /** Reads what {@link #writeExtra} saved. */
    protected void readExtra(NBTTagCompound tag) {
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}

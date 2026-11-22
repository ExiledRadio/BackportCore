package com.exiledradio.backportcore.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.Mirror;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.IntBinaryOperator;

/**
 * One entry of a template pool: today's {@code StructurePoolElement}. Read from Mojang's pool files by
 * {@link JigsawPools}; kinds are {@code single_pool_element}, {@code legacy_single_pool_element}
 * (air left out), {@code list_pool_element}, {@code feature_pool_element} (placed by a
 * {@link JigsawPools#feature registered placer}) and {@code empty_pool_element}.
 */
public abstract class JigsawElement {

    /** The element as the pool file wrote it: saved with each piece, read back on load. */
    final JsonObject json;
    final String projection;

    JigsawElement(JsonObject json, String projection) {
        this.json = json;
        this.projection = projection;
    }

    /** @return {@code rigid} or {@code terrain_matching} */
    public String projection() {
        return this.projection;
    }

    public boolean rigid() {
        return !"terrain_matching".equals(this.projection);
    }

    /** @return the template a single (or legacy single) element places, as its pool file names it; else null */
    @Nullable
    public ResourceLocation template() {
        return this instanceof Single ? ((Single) this).location : null;
    }

    /** @return the element's processor list, or an empty one */
    public ModernProcessors.ProcessorList processors() {
        return this instanceof Single ? ((Single) this).processors : ModernProcessors.EMPTY;
    }

    /** @return the elements of a {@code list_pool_element}, else just this one */
    public List<JigsawElement> parts() {
        return this instanceof ListElement ? Collections.unmodifiableList(((ListElement) this).elements) : Collections.singletonList(this);
    }

    /** @return true for {@code empty_pool_element}, which ends a pool's list in today's assembly */
    public boolean isEmpty() {
        return false;
    }

    /** Today's {@code getGroundLevelDelta}. */
    public int groundLevelDelta() {
        return 1;
    }

    /**
     * The element's jigsaw blocks in the world at this position and rotation, in today's order (by
     * height, then x, then z in the file), before any shuffle.
     */
    public abstract List<Connector> connectors(BlockPos pos, Rotation rotation);

    /** @return the box the element fills at this position and rotation */
    public abstract StructureBoundingBox box(BlockPos pos, Rotation rotation);

    /** Places the part of the element inside {@code clip}. */
    abstract void place(World world, BlockPos pos, Rotation rotation, StructureBoundingBox clip, Random random, BlockPos pivot,
                        IntBinaryOperator surface, JigsawPiece piece);

    /** @return the tallest box of the element, for the expansion hack */
    int ySpan() {
        StructureBoundingBox b = box(BlockPos.ORIGIN, Rotation.NONE);
        return b.getYSize();
    }

    /** @return the element as its pool file wrote it */
    public JsonObject json() {
        return new com.google.gson.JsonParser().parse(this.json.toString()).getAsJsonObject();
    }

    @Override
    public String toString() {
        return this.json.toString();
    }

    /** A jigsaw block of an element, placed: where it is, which way it faces, what it joins. */
    public static final class Connector {
        public final BlockPos pos;
        public final EnumFacing front;
        public final EnumFacing top;
        public final String name;
        public final String target;
        public final String pool;
        public final String joint;
        /**
         * 1.20.3+'s {@code placement_priority} (the order the pieces it opens are worked through) and
         * {@code selection_priority} (the order the piece's jigsaws are tried); 0 for older files and features.
         * {@link ModernJigsaw} reads them; {@link Jigsaw} (1.16.5) has none.
         */
        public final int placementPriority;
        public final int selectionPriority;

        Connector(BlockPos pos, EnumFacing front, EnumFacing top, String name, String target, String pool, String joint) {
            this(pos, front, top, name, target, pool, joint, 0, 0);
        }

        Connector(BlockPos pos, EnumFacing front, EnumFacing top, String name, String target, String pool, String joint,
                  int placementPriority, int selectionPriority) {
            this.pos = pos;
            this.front = front;
            this.top = top;
            this.name = name;
            this.target = target;
            this.pool = pool;
            this.joint = joint;
            this.placementPriority = placementPriority;
            this.selectionPriority = selectionPriority;
        }

        /** Today's {@code JigsawBlock.canAttach}: this one (the parent's) takes {@code other} (the child's). */
        public boolean canAttach(Connector other) {
            boolean rollable = "rollable".equals(this.joint);
            return this.front == other.front.getOpposite() && (rollable || this.top == other.top) && this.target.equals(other.name);
        }

        @Override
        public String toString() {
            return this.name + "->" + this.target + "@" + this.pos + " " + this.front + "/" + this.top + " " + this.pool;
        }
    }

    /** A Y-axis turn of a facing, as today's {@code Rotation.rotate}: up and down stay. */
    static EnumFacing rotate(EnumFacing facing, Rotation rotation) {
        if (facing.getAxis() == EnumFacing.Axis.Y) {
            return facing;
        }
        switch (rotation) {
            case CLOCKWISE_90:
                return facing.rotateY();
            case CLOCKWISE_180:
                return facing.getOpposite();
            case COUNTERCLOCKWISE_90:
                return facing.rotateYCCW();
            default:
                return facing;
        }
    }

    // ---- kinds

    static final class Single extends JigsawElement {
        final ResourceLocation location;
        final ModernProcessors.ProcessorList processors;
        final boolean legacy;
        @Nullable
        private List<ModernTemplate.Jigsaw> sorted;

        Single(JsonObject json, String projection, ResourceLocation location, ModernProcessors.ProcessorList processors, boolean legacy) {
            super(json, projection);
            this.location = location;
            this.processors = processors;
            this.legacy = legacy;
        }

        ModernTemplate loadTemplate() {
            return ModernTemplate.load(JigsawPools.templateId(this.location));
        }

        /** @return the template's name as the pool file gives it */
        ResourceLocation location() {
            return this.location;
        }

        @Override
        public List<Connector> connectors(BlockPos pos, Rotation rotation) {
            List<ModernTemplate.Jigsaw> jigsaws = this.sorted;
            if (jigsaws == null) {
                // Today's StructureTemplate keeps blocks with data sorted by y, then x, then z.
                jigsaws = new ArrayList<ModernTemplate.Jigsaw>(loadTemplate().jigsaws());
                jigsaws.sort(Comparator.<ModernTemplate.Jigsaw>comparingInt(j -> j.pos.getY())
                        .thenComparingInt(j -> j.pos.getX()).thenComparingInt(j -> j.pos.getZ()));
                this.sorted = jigsaws;
            }
            TemplatePlacement placement = TemplatePlacement.of(rotation, Mirror.NONE);
            List<Connector> out = new ArrayList<Connector>(jigsaws.size());
            for (ModernTemplate.Jigsaw j : jigsaws) {
                out.add(new Connector(ModernTemplate.worldPos(pos, j.pos, placement), rotate(j.front, rotation), rotate(j.top, rotation),
                        j.name, j.target, j.pool, j.joint, j.placementPriority, j.selectionPriority));
            }
            return out;
        }

        @Override
        public StructureBoundingBox box(BlockPos pos, Rotation rotation) {
            return loadTemplate().boundingBox(pos, TemplatePlacement.of(rotation, Mirror.NONE));
        }

        @Override
        void place(World world, BlockPos pos, Rotation rotation, StructureBoundingBox clip, Random random, BlockPos pivot,
                   IntBinaryOperator surface, JigsawPiece piece) {
            ModernTemplate template = loadTemplate();
            TemplatePlacement placement = TemplatePlacement.of(rotation, Mirror.NONE).clip(clip).finalizeEntities(true);
            for (TemplateProcessor processor : this.processors.build(pivot)) {
                placement.processor(processor);
            }
            if (this.processors.gravity() != null) {
                placement.followTerrain(surface, this.processors.gravity().offset);
            }
            if (!rigid()) {
                // terrain_matching's own processor: GravityProcessor(WORLD_SURFACE_WG, -1)
                placement.followTerrain(surface, -1);
            }
            if (this.legacy) {
                placement.processor((w, p, block, r) -> "minecraft:air".equals(block.name()) ? null : block);
            }
            template.place(world, pos, placement);
            for (ModernTemplate.DataMarker marker : template.dataMarkers()) {
                BlockPos at = ModernTemplate.worldPos(pos, marker.pos, placement);
                if (clip.isVecInside(at)) {
                    piece.handleDataMarker(marker.metadata, at, world, random, clip);
                }
            }
        }

        @Override
        int ySpan() {
            return box(BlockPos.ORIGIN, Rotation.NONE).getYSize();
        }
    }

    static final class ListElement extends JigsawElement {
        final List<JigsawElement> elements;

        ListElement(JsonObject json, String projection, List<JigsawElement> elements) {
            super(json, projection);
            this.elements = elements;
        }

        @Override
        public List<Connector> connectors(BlockPos pos, Rotation rotation) {
            return this.elements.get(0).connectors(pos, rotation);
        }

        @Override
        public StructureBoundingBox box(BlockPos pos, Rotation rotation) {
            StructureBoundingBox out = null;
            for (JigsawElement e : this.elements) {
                StructureBoundingBox b = e.box(pos, rotation);
                if (out == null) {
                    out = new StructureBoundingBox(b);
                } else {
                    out.expandTo(b);
                }
            }
            return out == null ? new StructureBoundingBox(pos, pos) : out;
        }

        @Override
        void place(World world, BlockPos pos, Rotation rotation, StructureBoundingBox clip, Random random, BlockPos pivot,
                   IntBinaryOperator surface, JigsawPiece piece) {
            for (JigsawElement e : this.elements) {
                e.place(world, pos, rotation, clip, random, pivot, surface, piece);
            }
        }
    }

    static final class Feature extends JigsawElement {
        final JsonElement feature;

        Feature(JsonObject json, String projection, JsonElement feature) {
            super(json, projection);
            this.feature = feature;
        }

        @Override
        public List<Connector> connectors(BlockPos pos, Rotation rotation) {
            return Collections.singletonList(new Connector(pos, EnumFacing.DOWN, EnumFacing.SOUTH, "minecraft:bottom", "minecraft:empty",
                    "minecraft:empty", "rollable"));
        }

        @Override
        public StructureBoundingBox box(BlockPos pos, Rotation rotation) {
            return new StructureBoundingBox(pos, pos);
        }

        @Override
        void place(World world, BlockPos pos, Rotation rotation, StructureBoundingBox clip, Random random, BlockPos pivot,
                   IntBinaryOperator surface, JigsawPiece piece) {
            if (!clip.isVecInside(pos)) {
                return;
            }
            JigsawPools.FeaturePlacer placer = JigsawPools.featurePlacer(this.feature);
            if (placer != null) {
                placer.place(world, pos, random, this.feature);
            }
        }
    }

    static final class Empty extends JigsawElement {
        Empty(JsonObject json) {
            super(json, "rigid");
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public List<Connector> connectors(BlockPos pos, Rotation rotation) {
            return Collections.emptyList();
        }

        @Override
        public StructureBoundingBox box(BlockPos pos, Rotation rotation) {
            return new StructureBoundingBox(pos, pos);
        }

        @Override
        void place(World world, BlockPos pos, Rotation rotation, StructureBoundingBox clip, Random random, BlockPos pivot,
                   IntBinaryOperator surface, JigsawPiece piece) {
        }
    }
}

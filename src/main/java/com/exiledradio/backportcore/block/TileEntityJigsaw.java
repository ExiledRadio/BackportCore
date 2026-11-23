package com.exiledradio.backportcore.block;

import com.exiledradio.backportcore.structure.ModernJigsaw;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;

/**
 * Today's {@code JigsawBlockEntity}: the jigsaw's name, target, pool, final state, joint and priorities, saved under
 * 26.x's keys ({@code name}, {@code target}, {@code pool}, {@code final_state}, {@code joint},
 * {@code placement_priority}, {@code selection_priority}). A 1.12.2 structure block saves them with the block, and
 * {@code ModernTemplate} reads them back as the template's jigsaws.
 *
 * @since 1.4.5
 */
public class TileEntityJigsaw extends TileEntity {

    public static final String EMPTY = "minecraft:empty";
    public static final String DEFAULT_FINAL_STATE = "minecraft:air";

    /** Today's {@code JigsawBlockEntity.JointType}. */
    public enum Joint {
        ROLLABLE("rollable"),
        ALIGNED("aligned");

        private final String name;

        Joint(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        /** @return the joint of this name, or {@code fallback} */
        public static Joint byName(String name, Joint fallback) {
            for (Joint j : values()) {
                if (j.name.equals(name)) {
                    return j;
                }
            }
            return fallback;
        }

        /** {@code StructureTemplate.getDefaultJointType}: aligned facing sideways, rollable facing up or down. */
        public static Joint defaultFor(EnumFacing front) {
            return front.getAxis().isHorizontal() ? ALIGNED : ROLLABLE;
        }
    }

    private String name = EMPTY;
    private String target = EMPTY;
    private String pool = EMPTY;
    private String finalState = DEFAULT_FINAL_STATE;
    /** Null when loaded without one: then the block's facing decides, once the world is there. */
    @Nullable
    private Joint joint = Joint.ROLLABLE;
    private int placementPriority;
    private int selectionPriority;

    public String getName() {
        return this.name;
    }

    public String getTarget() {
        return this.target;
    }

    public String getPool() {
        return this.pool;
    }

    public String getFinalState() {
        return this.finalState;
    }

    public Joint getJoint() {
        if (this.joint == null) {
            return Joint.defaultFor(front());
        }
        return this.joint;
    }

    public int getPlacementPriority() {
        return this.placementPriority;
    }

    public int getSelectionPriority() {
        return this.selectionPriority;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public void setPool(String pool) {
        this.pool = pool;
    }

    public void setFinalState(String finalState) {
        this.finalState = finalState;
    }

    public void setJoint(Joint joint) {
        this.joint = joint;
    }

    public void setPlacementPriority(int placementPriority) {
        this.placementPriority = placementPriority;
    }

    public void setSelectionPriority(int selectionPriority) {
        this.selectionPriority = selectionPriority;
    }

    /** @return the block's orientation, or {@code north_up} without a world */
    public FrontAndTop orientation() {
        if (this.world != null) {
            IBlockState state = this.world.getBlockState(this.pos);
            if (state.getBlock() instanceof BlockJigsaw) {
                return state.getValue(BlockJigsaw.ORIENTATION);
            }
        }
        return FrontAndTop.NORTH_UP;
    }

    public EnumFacing front() {
        return orientation().front();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        writeFields(tag);
        return tag;
    }

    /** Writes the jigsaw's own keys, as today's {@code saveAdditional}. */
    public NBTTagCompound writeFields(NBTTagCompound tag) {
        tag.setString("name", this.name);
        tag.setString("target", this.target);
        tag.setString("pool", this.pool);
        tag.setString("final_state", this.finalState);
        tag.setString("joint", getJoint().getName());
        tag.setInteger("placement_priority", this.placementPriority);
        tag.setInteger("selection_priority", this.selectionPriority);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        this.name = id(tag, "name");
        this.target = id(tag, "target");
        this.pool = id(tag, "pool");
        this.finalState = tag.hasKey("final_state", Constants.NBT.TAG_STRING) ? tag.getString("final_state") : DEFAULT_FINAL_STATE;
        // No joint: today's game takes the block's facing (the world is not set yet while a chunk loads).
        this.joint = tag.hasKey("joint", Constants.NBT.TAG_STRING) ? Joint.byName(tag.getString("joint"), null) : null;
        this.placementPriority = tag.getInteger("placement_priority");
        this.selectionPriority = tag.getInteger("selection_priority");
    }

    /** {@code Identifier.CODEC}: a missing or malformed id reads as {@code minecraft:empty}. */
    private static String id(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_STRING)) {
            return EMPTY;
        }
        String v = tag.getString(key);
        return BlockJigsaw.isValidId(v) ? new ResourceLocation(v).toString() : EMPTY;
    }

    @Override
    public boolean onlyOpsCanSetNbt() {
        return true;
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(this.pos, 0, getUpdateTag());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    /**
     * Today's {@code JigsawBlockEntity.generate}: the pool laid out from the block in front of this one, as
     * {@code JigsawPlacement.generateJigsaw} does, and placed.
     *
     * @return whether anything was placed
     */
    public boolean generate(World world, int levels, boolean keepJigsaws) {
        BlockPos position = this.pos.offset(front());
        return ModernJigsaw.generate(world, new ResourceLocation(this.pool), this.target, levels, position, keepJigsaws);
    }
}

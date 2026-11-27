package com.exiledradio.backportcore.block;

import com.exiledradio.backportcore.waterlog.Waterloggable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockWall;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A wall of one of our blocks, with 1.16+'s sides (**now:** current Java): each side is none, low or
 * tall, and the post shows unless the wall runs straight through.
 *
 * <ul>
 *   <li>A side connects to walls (ours and 1.12's), fence gates, panes and iron bars, and solid faces.</li>
 *   <li>A side is tall when the block above has a solid bottom or is a wall connected the same way. Under a honey
 *       block (26.4's {@code #wall_side_shape_override}) its shape decides: its collision before 26.4 (low sides,
 *       the post kept), its outline from 26.4 (tall sides, no post on a straight run).</li>
 *   <li>The post is dropped only on a straight run with matching sides and nothing above that needs it
 *       (a wall post, a torch, a lantern, a sign or a banner).</li>
 * </ul>
 * Vanilla's {@link BlockWall} has a fixed cobblestone variant, so this is its own block. It reports the
 * same face shapes, so 1.12's cobblestone walls connect to ours as well. Everything is worked out in
 * {@link #getActualState}; metadata is always 0.
 *
 * <p>The family's one wall class (moved here from Caves &amp; Cliffs Backport's {@code BlockCCWall}): deepslate,
 * tuff, resin brick and every later wall look and join alike. Use 1.16+'s multipart blockstate (vanilla's
 * {@code <name>_wall.json}: {@code up}, and {@code north}/{@code east}/{@code south}/{@code west} as none, low or
 * tall). A wall moved onto this class from 1.12.2's {@code BlockWall} keeps its saved blocks (both store
 * metadata 0).
 */
public class ModernWall extends Block implements Waterloggable {

    public enum Side implements IStringSerializable {
        NONE("none"), LOW("low"), TALL("tall");

        private final String name;

        Side(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }
    }

    public static final PropertyBool UP = PropertyBool.create("up");
    public static final PropertyEnum<Side> NORTH = PropertyEnum.create("north", Side.class);
    public static final PropertyEnum<Side> EAST = PropertyEnum.create("east", Side.class);
    public static final PropertyEnum<Side> SOUTH = PropertyEnum.create("south", Side.class);
    public static final PropertyEnum<Side> WEST = PropertyEnum.create("west", Side.class);

    private static final double POST_LO = 4 / 16.0, POST_HI = 12 / 16.0;
    private static final double SIDE_LO = 5 / 16.0, SIDE_HI = 11 / 16.0;
    private static final double LOW_TOP = 14 / 16.0;
    /** Entities cannot step or jump over a wall: its collision is a block and a half tall, as vanilla's. */
    private static final double COLLISION_TOP = 1.5;

    public ModernWall(IBlockState full) {
        super(full.getMaterial(), full.getBlock().getMapColor(full, null, null));
        Block b = full.getBlock();
        setSoundType(b.getSoundType());
        setHardness(b.getBlockHardness(full, null, null));
        setResistance(b.getExplosionResistance(null) * 5.0F / 3.0F);
        setDefaultState(this.blockState.getBaseState().withProperty(UP, true).withProperty(NORTH, Side.NONE)
                .withProperty(EAST, Side.NONE).withProperty(SOUTH, Side.NONE).withProperty(WEST, Side.NONE));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, UP, NORTH, EAST, SOUTH, WEST);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    public static PropertyEnum<Side> side(EnumFacing f) {
        switch (f) {
            case NORTH:
                return NORTH;
            case EAST:
                return EAST;
            case SOUTH:
                return SOUTH;
            default:
                return WEST;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Connections

    /** @return whether this wall at {@code pos} joins what is on its {@code facing} side */
    protected boolean connects(IBlockAccess world, BlockPos pos, EnumFacing facing) {
        BlockPos other = pos.offset(facing);
        IBlockState state = world.getBlockState(other);
        Block block = state.getBlock();
        if (block instanceof ModernWall || block instanceof BlockWall || block instanceof BlockPane) {
            return true;
        }
        if (block.canBeConnectedTo(world, other, facing.getOpposite())) {
            return true;
        }
        BlockFaceShape shape = state.getBlockFaceShape(world, other, facing.getOpposite());
        if (shape == BlockFaceShape.MIDDLE_POLE_THICK || shape == BlockFaceShape.MIDDLE_POLE && block instanceof BlockFenceGate) {
            return true;
        }
        return shape == BlockFaceShape.SOLID && !isExceptBlockForAttachWithPiston(block)
                && block != Blocks.BARRIER && block != Blocks.MELON_BLOCK && block != Blocks.PUMPKIN && block != Blocks.LIT_PUMPKIN;
    }

    @Override
    public boolean canBeConnectedTo(IBlockAccess world, BlockPos pos, EnumFacing facing) {
        return true;
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return actualState(state, world, pos, sideShapeOutline());
    }

    /**
     * {@link #getActualState} with a {@link #sideShapeOverride} block above tested by its outline ({@code outline},
     * 26.4) or its collision (before 26.4).
     */
    public IBlockState actualState(IBlockState state, IBlockAccess world, BlockPos pos, boolean outline) {
        IBlockState above = world.getBlockState(pos.up());
        boolean aboveSolid = above.getBlockFaceShape(world, pos.up(), EnumFacing.DOWN) == BlockFaceShape.SOLID;
        boolean aboveWall = above.getBlock() instanceof ModernWall;
        IBlockState aboveActual = aboveWall ? above.getActualState(world, pos.up()) : above;
        // A block whose outline and collision differ (honey) is tested by its shape, as vanilla does.
        boolean shaped = sideShapeOverride(above);
        AxisAlignedBB aboveShape = !shaped ? null
                : outline ? above.getBoundingBox(world, pos.up()) : above.getCollisionBoundingBox(world, pos.up());
        Side[] sides = new Side[4];
        for (EnumFacing f : EnumFacing.HORIZONTALS) {
            Side s = Side.NONE;
            if (connects(world, pos, f)) {
                boolean tall = shaped ? covers(aboveShape, sideTest(f))
                        : aboveSolid || aboveWall && aboveActual.getValue(side(f)) != Side.NONE
                        || above.getBlock() instanceof BlockWall;
                s = tall ? Side.TALL : Side.LOW;
            }
            sides[f.getHorizontalIndex()] = s;
            state = state.withProperty(side(f), s);
        }
        Side south = sides[0], west = sides[1], north = sides[2], east = sides[3];
        boolean straightNS = north != Side.NONE && north == south && east == Side.NONE && west == Side.NONE;
        boolean straightEW = east != Side.NONE && east == west && north == Side.NONE && south == Side.NONE;
        boolean post;
        if (shaped) {
            // Vanilla's shouldRaisePost: a straight tall run has no post; a low one keeps it under a covering block.
            boolean highWall = straightNS && north == Side.TALL || straightEW && east == Side.TALL;
            post = !(straightNS || straightEW)
                    || !highWall && (needsPost(world, pos.up(), above, aboveActual) || covers(aboveShape, POST_TEST));
        } else {
            post = !(straightNS || straightEW) || needsPost(world, pos.up(), above, aboveActual);
        }
        return state.withProperty(UP, post);
    }

    /** The volumes vanilla tests a block above against: the post's center column and each side's half. */
    private static final AxisAlignedBB POST_TEST = new AxisAlignedBB(7 / 16.0, 0, 7 / 16.0, 9 / 16.0, 1, 9 / 16.0);

    private static AxisAlignedBB sideTest(EnumFacing f) {
        double lo = 7 / 16.0, hi = 9 / 16.0, half = 9 / 16.0;
        switch (f) {
            case NORTH:
                return new AxisAlignedBB(lo, 0, 0, hi, 1, half);
            case SOUTH:
                return new AxisAlignedBB(lo, 0, 1 - half, hi, 1, 1);
            case WEST:
                return new AxisAlignedBB(0, 0, lo, half, 1, hi);
            default:
                return new AxisAlignedBB(1 - half, 0, lo, 1, 1, hi);
        }
    }

    /**
     * @return true for a block above whose shape, not its bottom face, decides a wall's sides: 26.4's
     * {@code #wall_side_shape_override} (the honey block, by name, whichever mod adds it)
     */
    public static boolean sideShapeOverride(IBlockState above) {
        net.minecraft.util.ResourceLocation name = above.getBlock().getRegistryName();
        return name != null && "honey_block".equals(name.getPath());
    }

    /**
     * @return true when a {@link #sideShapeOverride} block above is tested by its outline: the installed backports reach
     * 26.4 (Pre-Release 2). Before that by its collision, as vanilla's walls test every block (honey's is a pixel in on
     * each side, so a wall under it stays low and keeps its post).
     */
    public static boolean sideShapeOutline() {
        return com.exiledradio.backportcore.presentation.Presentation.installedNewest().isAtLeast(SIDE_SHAPE_OVERRIDE_SINCE);
    }

    /** The version whose walls take a tagged block's outline (26.4 Pre-Release 2's {@code wall_side_shape_override}). */
    public static final String SIDE_SHAPE_OVERRIDE_SINCE = "26.4";

    /** @return true when {@code shape}'s bottom face (a box resting on y 0) covers {@code test} seen from below */
    private static boolean covers(@Nullable AxisAlignedBB shape, AxisAlignedBB test) {
        return shape != null && shape.minY <= 0 && shape.maxY > 0
                && shape.minX <= test.minX && shape.maxX >= test.maxX && shape.minZ <= test.minZ && shape.maxZ >= test.maxZ;
    }

    /** @return true for what stands on a wall's post: another post, torches, lanterns, signs, banners, end rods */
    protected boolean needsPost(IBlockAccess world, BlockPos abovePos, IBlockState above, IBlockState aboveActual) {
        Block b = above.getBlock();
        if (b instanceof ModernWall) {
            return aboveActual.getValue(UP);
        }
        if (b instanceof BlockWall) {
            return true;
        }
        if (b == Blocks.TORCH || b == Blocks.REDSTONE_TORCH || b == Blocks.UNLIT_REDSTONE_TORCH
                || b == Blocks.STANDING_SIGN || b == Blocks.STANDING_BANNER || b == Blocks.END_ROD) {
            return true;
        }
        String path = b.getRegistryName() == null ? "" : b.getRegistryName().getPath();
        return path.endsWith("lantern") || path.endsWith("_torch") || path.equals("torch");
    }

    // ---------------------------------------------------------------------------------------------
    // Shapes

    private static AxisAlignedBB box(IBlockState state, double sideTop, double tallTop, double postTop) {
        double minX = SIDE_LO, maxX = SIDE_HI, minZ = SIDE_LO, maxZ = SIDE_HI, top = 0;
        if (state.getValue(UP)) {
            minX = minZ = POST_LO;
            maxX = maxZ = POST_HI;
            top = postTop;
        }
        Side n = state.getValue(NORTH), s = state.getValue(SOUTH), e = state.getValue(EAST), w = state.getValue(WEST);
        if (n != Side.NONE) {
            minZ = 0;
        }
        if (s != Side.NONE) {
            maxZ = 1;
        }
        if (w != Side.NONE) {
            minX = 0;
        }
        if (e != Side.NONE) {
            maxX = 1;
        }
        for (Side side : new Side[] {n, s, e, w}) {
            if (side == Side.LOW) {
                top = Math.max(top, sideTop);
            } else if (side == Side.TALL) {
                top = Math.max(top, tallTop);
            }
        }
        if (top == 0) {
            top = postTop;
        }
        return new AxisAlignedBB(minX, 0, minZ, maxX, top, maxZ);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return box(getActualState(state, world, pos), LOW_TOP, 1, 1);
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
                                      List<AxisAlignedBB> boxes, @Nullable Entity entity, boolean isActualState) {
        IBlockState actual = isActualState ? state : getActualState(state, world, pos);
        // The post and each side as their own boxes, so a corner does not fill the whole block.
        if (actual.getValue(UP) || noSides(actual)) {
            addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(POST_LO, 0, POST_LO, POST_HI, COLLISION_TOP, POST_HI));
        }
        if (actual.getValue(NORTH) != Side.NONE) {
            addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(SIDE_LO, 0, 0, SIDE_HI, COLLISION_TOP, SIDE_HI));
        }
        if (actual.getValue(SOUTH) != Side.NONE) {
            addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(SIDE_LO, 0, SIDE_LO, SIDE_HI, COLLISION_TOP, 1));
        }
        if (actual.getValue(WEST) != Side.NONE) {
            addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(0, 0, SIDE_LO, SIDE_HI, COLLISION_TOP, SIDE_HI));
        }
        if (actual.getValue(EAST) != Side.NONE) {
            addCollisionBoxToList(pos, entityBox, boxes, new AxisAlignedBB(SIDE_LO, 0, SIDE_LO, 1, COLLISION_TOP, SIDE_HI));
        }
    }

    private static boolean noSides(IBlockState s) {
        return s.getValue(NORTH) == Side.NONE && s.getValue(SOUTH) == Side.NONE && s.getValue(EAST) == Side.NONE
                && s.getValue(WEST) == Side.NONE;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isPassable(IBlockAccess world, BlockPos pos) {
        return false;
    }

    @Override
    public boolean canPlaceTorchOnTop(IBlockState state, IBlockAccess world, BlockPos pos) {
        return true;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return face != EnumFacing.UP && face != EnumFacing.DOWN ? BlockFaceShape.MIDDLE_POLE_THICK : BlockFaceShape.CENTER_BIG;
    }

    @Override
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return side == EnumFacing.DOWN ? super.shouldSideBeRendered(state, world, pos, side) : true;
    }
}

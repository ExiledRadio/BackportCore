package com.exiledradio.backportcore.speleothem;

import com.exiledradio.backportcore.waterlog.Waterloggable;
import com.exiledradio.backportcore.waterlog.Waterlogging;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 26.4's {@code SpeleothemBlock}: what pointed dripstone and icicles share (Core 1.5.0), a stalactite or stalagmite
 * one block per segment. A backport's speleothem extends this and overrides the hooks 26.4's subclasses override.
 *
 * <h2>States</h2>
 * {@link #THICKNESS} and {@link #VERTICAL_DIRECTION}: metadata {@code thickness | (down ? 8 : 0)}, ten states. Water
 * is Core's {@link Waterloggable} (Fluidlogged API where installed), not a property.
 *
 * <h2>Behavior (26.4 Pre-Release 1)</h2>
 * <ul>
 *   <li>A segment needs a sturdy face, or a segment of the same block pointing the same way, behind it. A stalagmite
 *       that loses it breaks a tick later; a stalactite falls two ticks later, every segment below it too, as
 *       {@link EntityFallingSpeleothem}: the tip hurts {@code max(length, 6)} per block fallen, 40 at most.</li>
 *   <li>Every segment works its thickness out from the segment ahead and behind (base, middle..., frustum, tip),
 *       and two opposed tips merge unless the placer sneaked or {@link #shouldMergeTips()} says no.</li>
 *   <li>Landing on a stalagmite's tip is a fall 2.5 blocks longer that hurts twice as much ({@code stalagmite}); 2 longer
 *       before 1.21.5 (1.17 to 1.21.4), as the newest installed backport has it ({@link #stalagmiteFallOffset}).</li>
 *   <li>A random tick reaches a stalactite's topmost segment: with chance {@value #GROWTH_CHANCE} and
 *       {@link #canGrow}, its free tip grows down by one, or the stalagmite under it grows up, up to
 *       {@link #maxGrowthLength()} segments ({@link #naturalGrowthDirections()} picks which).</li>
 *   <li>A thrown trident faster than 0.6 breaks a segment ({@link SpeleothemEvents}).</li>
 *   <li>Any tool gets it; pistons break it; offset sideways as vanilla's (at most 2 pixels each way); not pathfindable.</li>
 * </ul>
 */
public abstract class BlockSpeleothem extends Block implements Waterloggable {

    public static final PropertyEnum<Thickness> THICKNESS = PropertyEnum.create("thickness", Thickness.class);
    public static final PropertyDirection VERTICAL_DIRECTION = PropertyDirection.create("vertical_direction", EnumFacing.Plane.VERTICAL);

    /** {@code GROWTH_PROBABILITY_PER_RANDOM_TICK}. */
    public static final float GROWTH_CHANCE = 0.011377778F;
    public static final int MAX_STALAGMITE_SEARCH = 10;
    /** 1.21.5's and later's extra fall onto a stalagmite's tip ({@code STALAGMITE_FALL_DISTANCE_OFFSET}). */
    public static final float STALAGMITE_FALL_OFFSET = 2.5F;
    /** 1.17 to 1.21.4's. */
    public static final float STALAGMITE_FALL_OFFSET_1_17 = 2.0F;
    public static final float STALAGMITE_DAMAGE_MULTIPLIER = 2.0F;
    public static final int STALACTITE_MAX_DAMAGE = 40;
    public static final int STALACTITE_MIN_SIZE = 6;
    public static final double TRIDENT_SPEED = 0.6D;

    private static final AxisAlignedBB TIP_MERGE = column(6, 0, 16);
    private static final AxisAlignedBB TIP_UP = column(6, 0, 11);
    private static final AxisAlignedBB TIP_DOWN = column(6, 5, 16);
    private static final AxisAlignedBB FRUSTUM = column(8, 0, 16);
    private static final AxisAlignedBB MIDDLE = column(10, 0, 16);
    private static final AxisAlignedBB BASE = column(12, 0, 16);
    /** {@code MAX_HORIZONTAL_OFFSET}: the base's min x, 2 pixels. */
    private static final double MAX_OFFSET = 2.0D / 16.0D;

    @Nullable
    protected final IBlockState blockToGrowOn;

    protected BlockSpeleothem(Material material, MapColor color, @Nullable IBlockState blockToGrowOn) {
        super(material, color);
        this.blockToGrowOn = blockToGrowOn;
        setTickRandomly(true);
        setDefaultState(this.blockState.getBaseState().withProperty(THICKNESS, Thickness.TIP).withProperty(VERTICAL_DIRECTION, EnumFacing.UP));
    }

    /** {@code Block.column(width, minY, maxY)}, in pixels. */
    protected static AxisAlignedBB column(double width, double y0, double y1) {
        double half = width / 2.0D;
        return new AxisAlignedBB((8 - half) / 16.0D, y0 / 16.0D, (8 - half) / 16.0D, (8 + half) / 16.0D, y1 / 16.0D, (8 + half) / 16.0D);
    }

    // ------------------------------------------------------------------------------- hooks

    /** The sound a falling stalactite makes when it breaks on landing (26.4: the block's own level event). */
    @Nullable
    protected abstract SoundEvent landingSound();

    /** {@code getMaxGrowthLength()}: 7 for pointed dripstone, 4 for icicles. */
    protected int maxGrowthLength() {
        return 7;
    }

    /** {@code canGrow}: the block above the topmost segment is the block this grows on. */
    protected boolean canGrow(World world, BlockPos pos) {
        return this.blockToGrowOn != null && world.getBlockState(pos.up()).getBlock() == this.blockToGrowOn.getBlock();
    }

    /** {@code getNaturalGrowthDirections()}: both by default (the tip down, or the stalagmite below up). */
    protected List<EnumFacing> naturalGrowthDirections() {
        return BOTH;
    }

    private static final List<EnumFacing> BOTH = Collections.unmodifiableList(Arrays.asList(EnumFacing.DOWN, EnumFacing.UP));

    /** {@code shouldMergeTips()}: whether growth joins two opposed tips. */
    protected boolean shouldMergeTips() {
        return true;
    }

    /** {@code blocksStalagmiteScan}: whether this block stops the search for a stalagmite to grow. */
    protected boolean blocksStalagmiteScan(World world, BlockPos pos, IBlockState state) {
        return false;
    }

    /** A random tick on the server: by default 26.4's growth roll. Icicles melt here first. */
    protected void onRandomTick(World world, BlockPos pos, IBlockState state, Random random) {
        if (random.nextFloat() < GROWTH_CHANCE && isStalactiteStart(state, world, pos)) {
            growIfPossible(state, world, pos, random);
        }
    }

    // ------------------------------------------------------------------------------- states and shape

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, THICKNESS, VERTICAL_DIRECTION);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(THICKNESS).ordinal() | (state.getValue(VERTICAL_DIRECTION) == EnumFacing.DOWN ? 8 : 0);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        Thickness[] all = Thickness.values();
        return getDefaultState().withProperty(THICKNESS, all[Math.min(meta & 7, all.length - 1)])
                .withProperty(VERTICAL_DIRECTION, (meta & 8) != 0 ? EnumFacing.DOWN : EnumFacing.UP);
    }

    public IBlockState state(EnumFacing direction, Thickness thickness) {
        return getDefaultState().withProperty(VERTICAL_DIRECTION, direction).withProperty(THICKNESS, thickness);
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public EnumOffsetType getOffsetType() {
        return EnumOffsetType.XZ;
    }

    /** {@code BlockBehaviour.getOffset} with {@code getMaxHorizontalOffset()} 2 pixels: the same for a whole column. */
    @Override
    public Vec3d getOffset(IBlockState state, IBlockAccess world, BlockPos pos) {
        long i = MathHelper.getCoordinateRandom(pos.getX(), 0, pos.getZ());
        double x = MathHelper.clamp(((i & 15L) / 15.0F - 0.5D) * 0.5D, -MAX_OFFSET, MAX_OFFSET);
        double z = MathHelper.clamp(((i >> 8 & 15L) / 15.0F - 0.5D) * 0.5D, -MAX_OFFSET, MAX_OFFSET);
        return new Vec3d(x, 0.0D, z);
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        AxisAlignedBB shape;
        switch (state.getValue(THICKNESS)) {
            case TIP_MERGE:
                shape = TIP_MERGE;
                break;
            case TIP:
                shape = state.getValue(VERTICAL_DIRECTION) == EnumFacing.DOWN ? TIP_DOWN : TIP_UP;
                break;
            case FRUSTUM:
                shape = FRUSTUM;
                break;
            case MIDDLE:
                shape = MIDDLE;
                break;
            default:
                shape = BASE;
        }
        if (getOffsetType() == EnumOffsetType.NONE) {
            return shape;
        }
        Vec3d off = getOffset(state, world, pos);
        return shape.offset(off.x, 0.0D, off.z);
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.DESTROY;
    }

    @Override
    public boolean canHarvestBlock(IBlockAccess world, BlockPos pos, EntityPlayer player) {
        return true;
    }

    @Nullable
    @Override
    public PathNodeType getAiPathNodeType(IBlockState state, IBlockAccess world, BlockPos pos) {
        return PathNodeType.BLOCKED;
    }

    // ------------------------------------------------------------------------------- queries

    /** {@code state.is(#speleothems)}. */
    public static boolean isSpeleothem(IBlockState state) {
        return state.getBlock() instanceof BlockSpeleothem;
    }

    public static boolean isWithDirection(IBlockState state, EnumFacing direction) {
        return isSpeleothem(state) && state.getValue(VERTICAL_DIRECTION) == direction;
    }

    public static boolean isStalactite(IBlockState state) {
        return isWithDirection(state, EnumFacing.DOWN);
    }

    public static boolean isStalagmite(IBlockState state) {
        return isWithDirection(state, EnumFacing.UP);
    }

    public static boolean isTip(IBlockState state, boolean includeMerged) {
        if (!isSpeleothem(state)) {
            return false;
        }
        Thickness t = state.getValue(THICKNESS);
        return t == Thickness.TIP || includeMerged && t == Thickness.TIP_MERGE;
    }

    private boolean isUnmergedTipWithDirection(IBlockState state, EnumFacing direction) {
        return isTip(state, false) && state.getValue(VERTICAL_DIRECTION) == direction && state.getBlock() == this;
    }

    /** {@code isFreeHangingStalactite}: a stalactite's free tip, not waterlogged. */
    public static boolean isFreeHangingStalactite(IBlockAccess world, BlockPos pos, IBlockState state) {
        return isStalactite(state) && state.getValue(THICKNESS) == Thickness.TIP && !Waterlogging.hasWater(world, pos);
    }

    public static boolean isStalactiteStart(IBlockState state, IBlockAccess world, BlockPos pos) {
        return isStalactite(state) && world.getBlockState(pos.up()).getBlock() != state.getBlock();
    }

    public static boolean isStalagmiteStart(IBlockState state, IBlockAccess world, BlockPos pos) {
        return isStalagmite(state) && world.getBlockState(pos.down()).getBlock() != state.getBlock();
    }

    /** {@code isValidSpeleothemPlacement}. */
    public boolean isValidPlacement(IBlockAccess world, BlockPos pos, EnumFacing direction) {
        BlockPos behind = pos.offset(direction.getOpposite());
        IBlockState s = world.getBlockState(behind);
        return s.getBlockFaceShape(world, behind, direction) == BlockFaceShape.SOLID || isWithDirection(s, direction) && s.getBlock() == this;
    }

    public boolean canSurvive(IBlockAccess world, BlockPos pos, IBlockState state) {
        return isValidPlacement(world, pos, state.getValue(VERTICAL_DIRECTION));
    }

    /** {@code calculateSpeleothemThickness}. */
    public Thickness calculateThickness(IBlockAccess world, BlockPos pos, EnumFacing direction, boolean mergeTips) {
        EnumFacing base = direction.getOpposite();
        IBlockState ahead = world.getBlockState(pos.offset(direction));
        if (isWithDirection(ahead, base) && ahead.getBlock() == this) {
            return !mergeTips && ahead.getValue(THICKNESS) != Thickness.TIP_MERGE ? Thickness.TIP : Thickness.TIP_MERGE;
        }
        if (!isWithDirection(ahead, direction)) {
            return Thickness.TIP;
        }
        Thickness aheadThickness = ahead.getValue(THICKNESS);
        if (aheadThickness != Thickness.TIP && aheadThickness != Thickness.TIP_MERGE) {
            return !isWithDirection(world.getBlockState(pos.offset(base)), direction) ? Thickness.BASE : Thickness.MIDDLE;
        }
        return Thickness.FRUSTUM;
    }

    /** {@code findTip}: the tip of the column through {@code pos}, within {@code maxSearch} segments. */
    @Nullable
    public static BlockPos findTip(IBlockState state, World world, BlockPos pos, int maxSearch, boolean includeMerged) {
        if (isTip(state, includeMerged)) {
            return pos;
        }
        EnumFacing direction = state.getValue(VERTICAL_DIRECTION);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(pos);
        for (int i = 1; i < maxSearch; i++) {
            m.move(direction);
            IBlockState s = world.getBlockState(m);
            if (isTip(s, includeMerged)) {
                return m.toImmutable();
            }
            if (world.isOutsideBuildHeight(m) || s.getBlock() != state.getBlock() || s.getValue(VERTICAL_DIRECTION) != direction) {
                return null;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------------------- placing

    @Override
    public boolean canPlaceBlockAt(World world, BlockPos pos) {
        return super.canPlaceBlockAt(world, pos)
                && (isValidPlacement(world, pos, EnumFacing.UP) || isValidPlacement(world, pos, EnumFacing.DOWN));
    }

    /** {@code getNearestLookingVerticalDirection().getOpposite()}: looking down places a stalagmite. */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer, EnumHand hand) {
        EnumFacing preferred = placer.rotationPitch > 0 ? EnumFacing.UP : EnumFacing.DOWN;
        return placementState(world, pos, preferred, !placer.isSneaking());
    }

    public IBlockState placementState(IBlockAccess world, BlockPos pos, EnumFacing preferred, boolean mergeTips) {
        EnumFacing direction = isValidPlacement(world, pos, preferred) || !isValidPlacement(world, pos, preferred.getOpposite())
                ? preferred : preferred.getOpposite();
        return state(direction, calculateThickness(world, pos, direction, mergeTips));
    }

    // ------------------------------------------------------------------------------- support and thickness

    /** {@code updateShape}, for the two vertical neighbors. */
    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos from) {
        if (world.isRemote || from.getX() != pos.getX() || from.getZ() != pos.getZ() || from.getY() == pos.getY()) {
            return;
        }
        EnumFacing direction = state.getValue(VERTICAL_DIRECTION);
        if (direction == EnumFacing.DOWN && world.isUpdateScheduled(pos, this)) {
            return;
        }
        if (from.equals(pos.offset(direction.getOpposite())) && !canSurvive(world, pos, state)) {
            world.scheduleUpdate(pos, this, direction == EnumFacing.DOWN ? 2 : 1);
            return;
        }
        Thickness now = calculateThickness(world, pos, direction, state.getValue(THICKNESS) == Thickness.TIP_MERGE);
        if (now != state.getValue(THICKNESS)) {
            world.setBlockState(pos, state.withProperty(THICKNESS, now), 3);
        }
    }

    /** {@code tick}: a stalagmite that lost its support breaks; anything else falls. */
    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, Random rand) {
        if (world.isRemote) {
            return;
        }
        if (isStalagmite(state) && !canSurvive(world, pos, state)) {
            world.destroyBlock(pos, true);
        } else if (isStalactite(state) && !canSurvive(world, pos, state)) {
            spawnFallingStalactite(state, world, pos);
        }
    }

    /** {@code spawnFallingStalactite}: the column from {@code pos} to its tip; only the tip hurts. */
    public static void spawnFallingStalactite(IBlockState state, World world, BlockPos pos) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(pos);
        IBlockState s = state;
        while (isStalactite(s)) {
            EntityFallingSpeleothem falling = new EntityFallingSpeleothem(world, m.getX() + 0.5D, m.getY(), m.getZ() + 0.5D, s);
            boolean tip = isTip(s, true);
            if (tip) {
                int size = Math.max(1 + pos.getY() - m.getY(), STALACTITE_MIN_SIZE);
                falling.setHurts(1.0F * size, STALACTITE_MAX_DAMAGE);
            }
            world.setBlockToAir(m);
            world.spawnEntity(falling);
            if (tip) {
                break;
            }
            m.move(EnumFacing.DOWN);
            s = world.getBlockState(m);
        }
    }

    /** @return how much longer a fall onto a stalagmite's tip counts at these rules: 2.5 from 1.21.5, else 2 */
    public static float stalagmiteFallOffset(com.exiledradio.backportcore.presentation.GameVersion rules) {
        return rules.isAtLeast("1.21.5") ? STALAGMITE_FALL_OFFSET : STALAGMITE_FALL_OFFSET_1_17;
    }

    /** {@code fallOnDamage}: a stalagmite's free tip impales. */
    @Override
    public void onFallenUpon(World world, BlockPos pos, Entity entity, float fallDistance) {
        IBlockState state = world.getBlockState(pos);
        if (isSpeleothem(state) && state.getValue(VERTICAL_DIRECTION) == EnumFacing.UP && state.getValue(THICKNESS) == Thickness.TIP) {
            SpeleothemEvents.impale(entity, fallDistance + stalagmiteFallOffset(
                    com.exiledradio.backportcore.presentation.Presentation.installedNewest()), STALAGMITE_DAMAGE_MULTIPLIER);
        } else {
            super.onFallenUpon(world, pos, entity, fallDistance);
        }
    }

    // ------------------------------------------------------------------------------- growth

    @Override
    public void randomTick(World world, BlockPos pos, IBlockState state, Random random) {
        if (!world.isRemote) {
            onRandomTick(world, pos, state, random);
        }
    }

    /** {@code growStalactiteOrStalagmiteIfPossible}, from a stalactite's topmost segment. */
    public void growIfPossible(IBlockState start, World world, BlockPos startPos, Random random) {
        if (!canGrow(world, startPos)) {
            return;
        }
        BlockPos tip = findTip(start, world, startPos, maxGrowthLength(), false);
        if (tip == null) {
            return;
        }
        IBlockState tipState = world.getBlockState(tip);
        if (!isFreeHangingStalactite(world, tip, tipState) || !canTipGrow(tipState, world, tip)) {
            return;
        }
        List<EnumFacing> directions = naturalGrowthDirections();
        EnumFacing direction = directions.isEmpty() ? null : directions.get(random.nextInt(directions.size()));
        if (direction == EnumFacing.DOWN) {
            grow(world, tip, EnumFacing.DOWN);
        } else if (direction == EnumFacing.UP) {
            growStalagmiteBelow(world, tip);
        }
    }

    private boolean canTipGrow(IBlockState tipState, World world, BlockPos tip) {
        EnumFacing direction = tipState.getValue(VERTICAL_DIRECTION);
        BlockPos aheadPos = tip.offset(direction);
        IBlockState ahead = world.getBlockState(aheadPos);
        if (ahead.getMaterial().isLiquid() || Waterlogging.hasWater(world, aheadPos)) {
            return false;
        }
        return ahead.getBlock().isAir(ahead, world, aheadPos) || isUnmergedTipWithDirection(ahead, direction.getOpposite());
    }

    private void growStalagmiteBelow(World world, BlockPos tip) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(tip);
        for (int i = 0; i < MAX_STALAGMITE_SEARCH; i++) {
            m.move(EnumFacing.DOWN);
            IBlockState s = world.getBlockState(m);
            if (s.getMaterial().isLiquid() || Waterlogging.hasWater(world, m)) {
                return;
            }
            if (isUnmergedTipWithDirection(s, EnumFacing.UP) && canTipGrow(s, world, m)) {
                grow(world, m.toImmutable(), EnumFacing.UP);
                return;
            }
            if (isValidPlacement(world, m, EnumFacing.UP) && !Waterlogging.hasWater(world, m.down())
                    && world.getBlockState(m.down()).getMaterial() != Material.WATER) {
                grow(world, m.down(), EnumFacing.UP);
                return;
            }
            if (blocksStalagmiteScan(world, m, s)) {
                return;
            }
        }
    }

    private void grow(World world, BlockPos from, EnumFacing direction) {
        BlockPos target = from.offset(direction);
        IBlockState there = world.getBlockState(target);
        if (isUnmergedTipWithDirection(there, direction.getOpposite()) && shouldMergeTips()) {
            BlockPos stalactite = there.getValue(VERTICAL_DIRECTION) == EnumFacing.UP ? target.up() : target;
            create(world, stalactite, EnumFacing.DOWN, Thickness.TIP_MERGE);
            create(world, stalactite.down(), EnumFacing.UP, Thickness.TIP_MERGE);
        } else if (there.getBlock().isAir(there, world, target) || there.getBlock() == Blocks.WATER) {
            create(world, target, direction, Thickness.TIP);
        }
    }

    /** {@code createSpeleothem}: keeps a water source it grows into. */
    private void create(World world, BlockPos pos, EnumFacing direction, Thickness thickness) {
        boolean water = Waterlogging.hasWaterSource(world, pos);
        IBlockState state = state(direction, thickness);
        if (water) {
            Waterlogging.placeWithWater(world, pos, state, 3);
        } else {
            world.setBlockState(pos, state, 3);
        }
    }
}

package com.exiledradio.backportcore.supply;

import com.exiledradio.backportcore.waterlog.Waterloggable;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

/**
 * Core's decorative blocks, one class per shape. Each has today's numbers from {@link SuppliedTable} and no behavior,
 * and the same metadata layout as the family's blocks of that shape (a rotated pillar's axis, vanilla's stairs, a half
 * slab with {@code half} and {@code variant=default} beside a {@code _double_slab}), so a world's blocks rename to the
 * owning backport's and back without changing.
 *
 * @since 1.5.0
 */
public final class SuppliedShapes {

    private SuppliedShapes() {
    }

    /** @return a new block of the row's shape; for a slab, the half (see {@link #doubleSlab}) */
    static Block create(SuppliedTable.Row row) {
        switch (row.shape) {
            case "grate":
                return setup(new Grate(row), row);
            case "pillar":
                return setup(new Pillar(row), row);
            case "chain":
                return setup(new Chain(row), row);
            case "stairs":
                return setup(new Stairs(row), row);
            case "slab":
                return setup(new HalfSlab(row), row);
            case "wall":
                return setup(new Wall(row), row);
            case "fence":
                return setup(new Fence(row), row);
            case "fence_gate":
                return setup(new FenceGate(row), row);
            case "door":
                return setup(new Door(row), row);
            case "trapdoor":
                return setup(new Trapdoor(row), row);
            case "button":
                return setup(new Button(row), row);
            case "pressure_plate":
                return setup(new PressurePlate(row), row);
            case "lantern":
                return setup(new Lantern(row), row);
            case "leaves":
                return setup(new Leaves(row), row);
            case "plant":
                return setup(new Plant(row), row);
            case "carpet":
                return setup(new Carpet(row), row);
            case "pane":
                return setup(new Pane(row), row);
            case "glass":
                return setup(new Glass(row), row);
            default:
                return setup(new Cube(row), row);
        }
    }

    static Block doubleSlab(SuppliedTable.Row row, Slab half) {
        Block b = setup(new DoubleSlab(row, half), row);
        b.setCreativeTab(null);
        return b;
    }

    static Material material(String name) {
        switch (name) {
            case "IRON":
                return Material.IRON;
            case "WOOD":
                return Material.WOOD;
            case "PLANTS":
                return Material.PLANTS;
            case "LEAVES":
                return Material.LEAVES;
            case "GLASS":
                return Material.GLASS;
            case "CARPET":
                return Material.CARPET;
            case "CIRCUITS":
                return Material.CIRCUITS;
            case "GRASS":
                return Material.GRASS;
            case "GROUND":
                return Material.GROUND;
            case "SAND":
                return Material.SAND;
            case "CLOTH":
                return Material.CLOTH;
            default:
                return Material.ROCK;
        }
    }

    /** Today's sound type: Core's own when 1.12.2 has none ({@link SuppliedSounds}), else 1.12.2's of that name. */
    static SoundType sound(SuppliedTable.Row row) {
        SoundType modern = SuppliedSounds.type(row.modernSound);
        return modern != null ? modern : sound(row.sound);
    }

    static SoundType sound(String name) {
        switch (name) {
            case "LADDER":
                return SoundType.LADDER;
            case "ANVIL":
                return SoundType.ANVIL;
            case "SLIME":
                return SoundType.SLIME;
            case "METAL":
                return SoundType.METAL;
            case "WOOD":
                return SoundType.WOOD;
            case "GLASS":
                return SoundType.GLASS;
            case "GROUND":
                return SoundType.GROUND;
            case "SAND":
                return SoundType.SAND;
            case "PLANT":
                return SoundType.PLANT;
            case "CLOTH":
                return SoundType.CLOTH;
            case "SNOW":
                return SoundType.SNOW;
            default:
                return SoundType.STONE;
        }
    }

    static MapColor color(int id) {
        MapColor c = id >= 0 && id < MapColor.COLORS.length ? MapColor.COLORS[id] : null;
        return c == null ? MapColor.STONE : c;
    }

    private static <B extends Block> B setup(B block, SuppliedTable.Row row) {
        block.setHardness(row.hardness);
        // 1.12.2 stores three times what setResistance is given and reports a fifth of that.
        block.setResistance(row.resistance * 5.0F / 3.0F);
        block.setLightLevel(row.light / 15.0F);
        if (row.tool != null) {
            block.setHarvestLevel(row.tool, row.level);
        }
        block.setCreativeTab(tab(row.shape));
        return block;
    }

    private static CreativeTabs tab(String shape) {
        switch (shape) {
            case "chain":
            case "lantern":
            case "plant":
            case "carpet":
            case "pane":
            case "leaves":
                return CreativeTabs.DECORATIONS;
            case "door":
            case "trapdoor":
            case "button":
            case "pressure_plate":
            case "fence_gate":
                return CreativeTabs.REDSTONE;
            default:
                return CreativeTabs.BUILDING_BLOCKS;
        }
    }

    /** @return the row's box in blocks, or the full block */
    static AxisAlignedBB box(SuppliedTable.Row row) {
        float[] b = row.box;
        return b == null ? Block.FULL_BLOCK_AABB : new AxisAlignedBB(b[0] / 16, b[1] / 16, b[2] / 16, b[3] / 16, b[4] / 16, b[5] / 16);
    }

    /** What every supplied block drops: the row's drop, as a block Core supplies; else itself. */
    static Item drop(SuppliedTable.Row row, Block self) {
        if (!"self".equals(row.drop) && !"none".equals(row.drop)) {
            Block other = SuppliedBlocks.block(row.drop);
            if (other != null) {
                return SuppliedBlocks.itemOf(other);
            }
        }
        return "none".equals(row.drop) ? net.minecraft.init.Items.AIR : SuppliedBlocks.itemOf(self);
    }

    /** A full block. */
    public static class Cube extends Block {
        final SuppliedTable.Row row;

        Cube(SuppliedTable.Row row) {
            super(material(row.material), color(row.mapColor));
            this.row = row;
            setSoundType(sound(row));
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return drop(this.row, this);
        }
    }

    /** A see-through full block that holds water: today's copper grates. Faces between two of the same are hidden. */
    public static class Grate extends Cube implements Waterloggable {
        Grate(SuppliedTable.Row row) {
            super(row);
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isFullCube(IBlockState state) {
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
            return BlockFaceShape.SOLID;
        }

        @Override
        @SideOnly(Side.CLIENT)
        @SuppressWarnings("deprecation")
        public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
            return world.getBlockState(pos.offset(side)).getBlock() != this && super.shouldSideBeRendered(state, world, pos, side);
        }

        @Override
        @SideOnly(Side.CLIENT)
        public BlockRenderLayer getRenderLayer() {
            return BlockRenderLayer.CUTOUT;
        }
    }

    /** A block along an axis: deepslate, logs. */
    public static class Pillar extends BlockRotatedPillar {
        final SuppliedTable.Row row;
        private final MapColor color;

        Pillar(SuppliedTable.Row row) {
            super(material(row.material), color(row.mapColor));
            this.row = row;
            this.color = color(row.mapColor);
            setSoundType(sound(row));
        }

        @Override
        @SuppressWarnings("deprecation")
        public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
            return this.color;
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return drop(this.row, this);
        }

        @Override
        public boolean isWood(IBlockAccess world, BlockPos pos) {
            return getDefaultState().getMaterial() == Material.WOOD;
        }
    }

    /** Today's chain: a thin pillar, 3 pixels across. */
    public static class Chain extends Pillar implements Waterloggable {
        private static final AxisAlignedBB Y = new AxisAlignedBB(6.5 / 16, 0, 6.5 / 16, 9.5 / 16, 1, 9.5 / 16);
        private static final AxisAlignedBB Z = new AxisAlignedBB(6.5 / 16, 6.5 / 16, 0, 9.5 / 16, 9.5 / 16, 1);
        private static final AxisAlignedBB X = new AxisAlignedBB(0, 6.5 / 16, 6.5 / 16, 1, 9.5 / 16, 9.5 / 16);

        Chain(SuppliedTable.Row row) {
            super(row);
        }

        private boolean ordinal() {
            return "axis_ordinal".equals(this.row.layout);
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            if (!ordinal()) {
                return super.getStateFromMeta(meta);
            }
            return getDefaultState().withProperty(AXIS, EnumFacing.Axis.values()[Math.min(meta, 2)]);
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return ordinal() ? state.getValue(AXIS).ordinal() : super.getMetaFromState(state);
        }

        @Override
        @SuppressWarnings("deprecation")
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
            switch (state.getValue(AXIS)) {
                case X:
                    return X;
                case Z:
                    return Z;
                default:
                    return Y;
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isFullCube(IBlockState state) {
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
            return face.getAxis() == state.getValue(AXIS) ? BlockFaceShape.CENTER_SMALL : BlockFaceShape.UNDEFINED;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public BlockRenderLayer getRenderLayer() {
            return BlockRenderLayer.CUTOUT;
        }
    }

    /** Stairs. Made from a plain stand-in of the full block, which gives them their numbers. */
    public static class Stairs extends BlockStairs implements Waterloggable {
        final SuppliedTable.Row row;

        Stairs(SuppliedTable.Row row) {
            super(new Cube(row).getDefaultState());
            this.row = row;
            this.useNeighborBrightness = true;
            setSoundType(sound(row));
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return drop(this.row, this);
        }
    }

    /** The family's one-value slab variant. */
    public enum Variant implements IStringSerializable {
        DEFAULT;

        @Override
        public String getName() {
            return "default";
        }
    }

    public static final PropertyEnum<Variant> VARIANT = PropertyEnum.create("variant", Variant.class);

    /**
     * A slab, as the family has them: {@code <name>_slab} is the half ({@code half} at meta 8), {@code <name>_double_slab}
     * the double, which drops two halves.
     */
    public abstract static class Slab extends BlockSlab implements Waterloggable {
        final SuppliedTable.Row row;
        /** For the double: its half. */
        private final Slab half;

        Slab(SuppliedTable.Row row, Slab half) {
            super(material(row.material), color(row.mapColor));
            this.row = row;
            this.half = half;
            setSoundType(sound(row));
            this.useNeighborBrightness = !isDouble();
            IBlockState state = this.blockState.getBaseState().withProperty(VARIANT, Variant.DEFAULT);
            if (!isDouble()) {
                state = state.withProperty(HALF, EnumBlockHalf.BOTTOM);
            }
            setDefaultState(state);
        }

        @Override
        public boolean canHoldWater(IBlockState state, IBlockAccess world, BlockPos pos) {
            return !isDouble();
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return Item.getItemFromBlock(this.half == null ? this : this.half);
        }

        @Override
        public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
            return new ItemStack(this.half == null ? this : this.half);
        }

        @Override
        public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
            return getItem(world, pos, state);
        }

        @Override
        public String getTranslationKey(int meta) {
            return super.getTranslationKey();
        }

        @Override
        public IProperty<?> getVariantProperty() {
            return VARIANT;
        }

        @Override
        public Comparable<?> getTypeForItem(ItemStack stack) {
            return Variant.DEFAULT;
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return isDouble() ? new BlockStateContainer(this, VARIANT) : new BlockStateContainer(this, HALF, VARIANT);
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            IBlockState state = getDefaultState();
            return isDouble() ? state : state.withProperty(HALF, (meta & 8) != 0 ? EnumBlockHalf.TOP : EnumBlockHalf.BOTTOM);
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return !isDouble() && state.getValue(HALF) == EnumBlockHalf.TOP ? 8 : 0;
        }
    }

    /** The half slab, the one with an item. */
    public static class HalfSlab extends Slab {
        HalfSlab(SuppliedTable.Row row) {
            super(row, null);
        }

        @Override
        public boolean isDouble() {
            return false;
        }
    }

    /** The double slab. */
    public static class DoubleSlab extends Slab {
        DoubleSlab(SuppliedTable.Row row, Slab half) {
            super(row, half);
        }

        @Override
        public boolean isDouble() {
            return true;
        }
    }

    // ---- 1.5.0 slice 3: the rest of the shapes today's structures use

    /** A wall: Core's {@link com.exiledradio.backportcore.block.ModernWall}, the family's one wall class. */
    public static class Wall extends com.exiledradio.backportcore.block.ModernWall {
        final SuppliedTable.Row row;

        Wall(SuppliedTable.Row row) {
            super(new Cube(row).getDefaultState());
            this.row = row;
            setSoundType(sound(row));
        }
    }

    public static class Fence extends net.minecraft.block.BlockFence implements Waterloggable {
        final SuppliedTable.Row row;

        Fence(SuppliedTable.Row row) {
            super(material(row.material), color(row.mapColor));
            this.row = row;
            setSoundType(sound(row));
        }
    }

    public static class FenceGate extends net.minecraft.block.BlockFenceGate {
        final SuppliedTable.Row row;

        FenceGate(SuppliedTable.Row row) {
            super(net.minecraft.block.BlockPlanks.EnumType.OAK);
            this.row = row;
            setSoundType(sound(row));
        }

        @Override
        @SuppressWarnings("deprecation")
        public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
            return color(this.row.mapColor);
        }
    }

    /** A door. Its item is an ItemDoor ({@link SuppliedBlocks#itemOf}); the top half drops nothing. */
    public static class Door extends net.minecraft.block.BlockDoor {
        final SuppliedTable.Row row;

        Door(SuppliedTable.Row row) {
            super(material(row.material));
            this.row = row;
            setSoundType(sound(row));
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return state.getValue(HALF) == EnumDoorHalf.UPPER ? net.minecraft.init.Items.AIR : SuppliedBlocks.itemOf(this);
        }

        @Override
        public ItemStack getItem(World world, BlockPos pos, IBlockState state) {
            return new ItemStack(SuppliedBlocks.itemOf(this));
        }

        @Override
        @SuppressWarnings("deprecation")
        public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
            return color(this.row.mapColor);
        }
    }

    public static class Trapdoor extends net.minecraft.block.BlockTrapDoor implements Waterloggable {
        final SuppliedTable.Row row;

        Trapdoor(SuppliedTable.Row row) {
            super(material(row.material));
            this.row = row;
            setSoundType(sound(row));
        }

        @Override
        @SuppressWarnings("deprecation")
        public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
            return color(this.row.mapColor);
        }
    }

    /** A button. Today's {@code face=floor|ceiling} is 1.12.2's {@code facing=up|down} ({@link SuppliedBlocks#props}). */
    public static class Button extends net.minecraft.block.BlockButton {
        final SuppliedTable.Row row;

        Button(SuppliedTable.Row row) {
            super(row.material.equals("WOOD"));
            this.row = row;
            setSoundType(sound(row));
        }

        private boolean wooden() {
            return "WOOD".equals(this.row.material);
        }

        @Override
        protected void playClickSound(EntityPlayer player, World world, BlockPos pos) {
            world.playSound(player, pos, wooden() ? net.minecraft.init.SoundEvents.BLOCK_WOOD_BUTTON_CLICK_ON
                    : net.minecraft.init.SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON, net.minecraft.util.SoundCategory.BLOCKS, 0.3F, 0.6F);
        }

        @Override
        protected void playReleaseSound(World world, BlockPos pos) {
            world.playSound(null, pos, wooden() ? net.minecraft.init.SoundEvents.BLOCK_WOOD_BUTTON_CLICK_OFF
                    : net.minecraft.init.SoundEvents.BLOCK_STONE_BUTTON_CLICK_OFF, net.minecraft.util.SoundCategory.BLOCKS, 0.3F, 0.5F);
        }
    }

    public static class PressurePlate extends net.minecraft.block.BlockPressurePlate {
        final SuppliedTable.Row row;

        PressurePlate(SuppliedTable.Row row) {
            super(material(row.material), "WOOD".equals(row.material) ? Sensitivity.EVERYTHING : Sensitivity.MOBS);
            this.row = row;
            setSoundType(sound(row));
        }

        @Override
        @SuppressWarnings("deprecation")
        public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
            return color(this.row.mapColor);
        }
    }

    /** A lantern, standing or hanging (meta 1), as the family's. */
    public static class Lantern extends Block implements Waterloggable {
        public static final net.minecraft.block.properties.PropertyBool HANGING = net.minecraft.block.properties.PropertyBool.create("hanging");
        private static final AxisAlignedBB STANDING = new AxisAlignedBB(5 / 16D, 0, 5 / 16D, 11 / 16D, 9 / 16D, 11 / 16D);
        private static final AxisAlignedBB HUNG = new AxisAlignedBB(5 / 16D, 1 / 16D, 5 / 16D, 11 / 16D, 10 / 16D, 11 / 16D);
        final SuppliedTable.Row row;

        Lantern(SuppliedTable.Row row) {
            super(material(row.material), color(row.mapColor));
            this.row = row;
            setSoundType(sound(row));
            setDefaultState(this.blockState.getBaseState().withProperty(HANGING, false));
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, HANGING);
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return getDefaultState().withProperty(HANGING, (meta & 1) != 0);
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return state.getValue(HANGING) ? 1 : 0;
        }

        @Override
        public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta,
                                                net.minecraft.entity.EntityLivingBase placer) {
            return getDefaultState().withProperty(HANGING, facing == EnumFacing.DOWN);
        }

        @Override
        @SuppressWarnings("deprecation")
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
            return state.getValue(HANGING) ? HUNG : STANDING;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isFullCube(IBlockState state) {
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
            return BlockFaceShape.UNDEFINED;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public BlockRenderLayer getRenderLayer() {
            return BlockRenderLayer.CUTOUT;
        }
    }

    /** Leaves that never decay. Stored as meta 4 (1.12.2's "not decayable"), so an owner's leaves stay put too. */
    public static class Leaves extends Cube {
        Leaves(SuppliedTable.Row row) {
            super(row);
            setLightOpacity(1);
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return 4;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public BlockRenderLayer getRenderLayer() {
            return BlockRenderLayer.CUTOUT_MIPPED;
        }

        @Override
        @SideOnly(Side.CLIENT)
        @SuppressWarnings("deprecation")
        public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
            return !world.getBlockState(pos.offset(side)).doesSideBlockRendering(world, pos.offset(side), side.getOpposite());
        }
    }

    /** A plant drawn as a cross: no collision, today's outline, stays wherever a structure puts it. */
    public static class Plant extends net.minecraft.block.BlockBush {
        final SuppliedTable.Row row;

        Plant(SuppliedTable.Row row) {
            super(material(row.material), color(row.mapColor));
            this.row = row;
            setSoundType(sound(row));
        }

        @Override
        public boolean canBlockStay(World world, BlockPos pos, IBlockState state) {
            return true;
        }

        @Override
        public boolean canPlaceBlockAt(World world, BlockPos pos) {
            return world.getBlockState(pos).getBlock().isReplaceable(world, pos);
        }

        @Override
        @SuppressWarnings("deprecation")
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
            return box(this.row);
        }

        @Override
        public Item getItemDropped(IBlockState state, Random rand, int fortune) {
            return drop(this.row, this);
        }
    }

    /** A carpet: one pixel tall. */
    public static class Carpet extends Cube {
        Carpet(SuppliedTable.Row row) {
            super(row);
        }

        @Override
        @SuppressWarnings("deprecation")
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
            return box(this.row);
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isFullCube(IBlockState state) {
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
            return face == EnumFacing.DOWN ? BlockFaceShape.SOLID : BlockFaceShape.UNDEFINED;
        }
    }

    /** Bars: today's copper bars, on 1.12.2's pane. */
    public static class Pane extends net.minecraft.block.BlockPane implements Waterloggable {
        final SuppliedTable.Row row;

        Pane(SuppliedTable.Row row) {
            super(material(row.material), true);
            this.row = row;
            setSoundType(sound(row));
        }

        @Override
        @SuppressWarnings("deprecation")
        public MapColor getMapColor(IBlockState state, IBlockAccess world, BlockPos pos) {
            return color(this.row.mapColor);
        }
    }

    /** See-through blocks: tinted glass. Faces between two of the same are hidden. */
    public static class Glass extends Cube {
        Glass(SuppliedTable.Row row) {
            super(row);
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isFullCube(IBlockState state) {
            return false;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public BlockRenderLayer getRenderLayer() {
            return BlockRenderLayer.TRANSLUCENT;
        }

        @Override
        @SideOnly(Side.CLIENT)
        @SuppressWarnings("deprecation")
        public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
            return world.getBlockState(pos.offset(side)).getBlock() != this && super.shouldSideBeRendered(state, world, pos, side);
        }
    }
}

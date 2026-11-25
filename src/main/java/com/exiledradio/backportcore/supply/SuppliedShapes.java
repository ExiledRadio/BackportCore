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
            default:
                return Material.ROCK;
        }
    }

    static SoundType sound(String name) {
        switch (name) {
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
        block.setCreativeTab("chain".equals(row.shape) ? CreativeTabs.DECORATIONS : CreativeTabs.BUILDING_BLOCKS);
        return block;
    }

    /** What every supplied block drops: the row's drop, as a block Core supplies; else itself. */
    static Item drop(SuppliedTable.Row row, Block self) {
        if (!"self".equals(row.drop) && !"none".equals(row.drop)) {
            Block other = SuppliedBlocks.block(row.drop);
            if (other != null) {
                return Item.getItemFromBlock(other);
            }
        }
        return "none".equals(row.drop) ? net.minecraft.init.Items.AIR : Item.getItemFromBlock(self);
    }

    /** A full block. */
    public static class Cube extends Block {
        final SuppliedTable.Row row;

        Cube(SuppliedTable.Row row) {
            super(material(row.material), color(row.mapColor));
            this.row = row;
            setSoundType(sound(row.sound));
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
            setSoundType(sound(row.sound));
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
            setSoundType(sound(row.sound));
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
            setSoundType(sound(row.sound));
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
}

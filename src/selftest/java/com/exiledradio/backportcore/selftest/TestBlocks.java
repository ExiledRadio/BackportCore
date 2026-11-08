package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.waterlog.Waterloggable;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Self-test only: the blocks the waterlogging tests place. Never in a release jar. */
final class TestBlocks {

    static Post optional;
    static Post required;
    static Post dry;
    static Legacy legacy;
    static Slab slab;
    static Slab doubleSlab;

    /** Every onWaterChanged call: "optional@x,y,z=true". */
    static final List<String> CHANGES = new ArrayList<String>();

    private TestBlocks() {
    }

    /**
     * A slab in the family's 1.12.2 convention: {@code test_slab} (a half, {@code half=bottom|top}) and
     * {@code test_double_slab}. Structure tests read 1.16 slabs from this mod as a palette source.
     */
    abstract static class Slab extends net.minecraft.block.BlockSlab {
        static final net.minecraft.block.properties.PropertyEnum<Variant> VARIANT =
                net.minecraft.block.properties.PropertyEnum.create("variant", Variant.class);

        enum Variant implements net.minecraft.util.IStringSerializable {
            DEFAULT;

            @Override
            public String getName() {
                return "default";
            }
        }

        static final class Half extends Slab {
            Half() {
                super("test_slab");
            }

            @Override
            public boolean isDouble() {
                return false;
            }
        }

        static final class Double extends Slab {
            Double() {
                super("test_double_slab");
            }

            @Override
            public boolean isDouble() {
                return true;
            }
        }

        Slab(String name) {
            super(Material.ROCK);
            IBlockState state = this.blockState.getBaseState().withProperty(VARIANT, Variant.DEFAULT);
            if (!isDouble()) {
                state = state.withProperty(HALF, EnumBlockHalf.BOTTOM);
            }
            setDefaultState(state);
            setRegistryName(SelfTestMod.MOD_ID, name);
            setTranslationKey(SelfTestMod.MOD_ID + "." + name);
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
            return isDouble() || state.getValue(HALF) == EnumBlockHalf.BOTTOM ? 0 : 8;
        }

        @Override
        public String getTranslationKey(int meta) {
            return getTranslationKey();
        }

        @Override
        public net.minecraft.block.properties.IProperty<?> getVariantProperty() {
            return VARIANT;
        }

        @Override
        public Comparable<?> getTypeForItem(net.minecraft.item.ItemStack stack) {
            return Variant.DEFAULT;
        }
    }

    /** A thin post water can flow through and mobs can stand in. */
    static class Post extends Block implements Waterloggable {
        private final Need need;
        private final boolean holds;

        Post(String name, Need need, boolean holds) {
            super(Material.WOOD);
            this.need = need;
            this.holds = holds;
            setRegistryName(SelfTestMod.MOD_ID, name);
            setTranslationKey(SelfTestMod.MOD_ID + "." + name);
        }

        @Override
        public Need waterNeed(IBlockState state) {
            return this.need;
        }

        @Override
        public boolean canHoldWater(IBlockState state, IBlockAccess world, BlockPos pos) {
            return this.holds;
        }

        @Override
        public void onWaterChanged(World world, BlockPos pos, IBlockState state, boolean hasWater) {
            if (!world.isRemote) {
                CHANGES.add(getRegistryName().getPath() + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "=" + hasWater);
            }
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

        @Nullable
        @Override
        public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
            return NULL_AABB;
        }

        @Override
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
            return new AxisAlignedBB(0.375D, 0.0D, 0.375D, 0.625D, 1.0D, 0.625D);
        }
    }

    /** A block released with its own {@code waterlogged} property, for the migration. */
    static final class Legacy extends Post {
        static final PropertyBool WATERLOGGED = PropertyBool.create("waterlogged");

        Legacy() {
            super("legacy", Need.OPTIONAL, true);
            setDefaultState(this.blockState.getBaseState().withProperty(WATERLOGGED, Boolean.FALSE));
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, WATERLOGGED);
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return state.getValue(WATERLOGGED) ? 1 : 0;
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return getDefaultState().withProperty(WATERLOGGED, (meta & 1) != 0);
        }
    }
}

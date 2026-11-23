package com.exiledradio.backportcore.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Today's jigsaw block ({@code JigsawBlock}), as {@code backportcore:jigsaw}: an operator block like the structure
 * block. It faces the side it was placed on (up or down: its top toward the player), opens the jigsaw screen for an
 * operator in creative mode, cannot be broken (hardness -1) and drops nothing. Structure files' {@code minecraft:jigsaw}
 * is this block ({@code ModernPalette}).
 *
 * @since 1.4.5
 */
public class BlockJigsaw extends Block {

    public static final PropertyEnum<FrontAndTop> ORIENTATION = PropertyEnum.create("orientation", FrontAndTop.class);

    public BlockJigsaw() {
        super(Material.IRON, MapColor.SILVER);
        setBlockUnbreakable();
        setResistance(3600000.0F);
        setDefaultState(this.blockState.getBaseState().withProperty(ORIENTATION, FrontAndTop.NORTH_UP));
        // No creative tab, as up to 1.19.2; 1.19.3's Operator Utilities lists it (creative.client.ModernTabs).
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, ORIENTATION);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        FrontAndTop[] all = FrontAndTop.values();
        return getDefaultState().withProperty(ORIENTATION, all[meta >= 0 && meta < all.length ? meta : FrontAndTop.NORTH_UP.ordinal()]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(ORIENTATION).ordinal();
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(ORIENTATION, state.getValue(ORIENTATION).rotate(rotation));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withProperty(ORIENTATION, state.getValue(ORIENTATION).mirror(mirror));
    }

    /** Today's {@code getStateForPlacement}: the clicked face; up or down, the top toward the player. */
    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer, EnumHand hand) {
        return getDefaultState().withProperty(ORIENTATION, orientationFor(facing, placer.getHorizontalFacing()));
    }

    /** @return the orientation a jigsaw placed on {@code clickedFace} by someone facing {@code looking} has */
    public static FrontAndTop orientationFor(EnumFacing clickedFace, EnumFacing looking) {
        EnumFacing top = clickedFace.getAxis() == EnumFacing.Axis.Y ? looking.getOpposite() : EnumFacing.UP;
        return FrontAndTop.of(clickedFace, top);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityJigsaw();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityJigsaw) || !player.canUseCommandBlock()) {
            return false;
        }
        if (world.isRemote) {
            // Only this branch names the client class, so a dedicated server never loads it.
            com.exiledradio.backportcore.block.client.JigsawClient.open(pos);
        }
        return true;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return net.minecraft.init.Items.AIR;
    }

    @Override
    public int quantityDropped(Random random) {
        return 0;
    }

    @Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, net.minecraft.entity.Entity entity) {
        return false;
    }

    /**
     * Today's {@code Identifier.tryParse}: a namespace of {@code a-z 0-9 _ - .} and a path of those and {@code /},
     * with {@code minecraft} when there is no colon.
     */
    public static boolean isValidId(String text) {
        if (text == null) {
            return false;
        }
        int colon = text.indexOf(':');
        String ns = colon >= 0 ? text.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? text.substring(colon + 1) : text;
        for (int i = 0; i < ns.length(); i++) {
            char c = ns.charAt(i);
            if (!(c == '_' || c == '-' || c == '.' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!(c == '_' || c == '-' || c == '.' || c == '/' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }
}

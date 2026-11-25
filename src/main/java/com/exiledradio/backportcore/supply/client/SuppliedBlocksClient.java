package com.exiledradio.backportcore.supply.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.supply.SuppliedBlocks;
import com.exiledradio.backportcore.supply.SuppliedShapes;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMap;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.world.ColorizerGrass;
import net.minecraft.world.biome.BiomeColorHelper;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The supplied blocks' models and colors. Walls, fences and bars use one multipart model for all their states; fence
 * gates and doors leave {@code powered} out of their model names, as today's files do. Mangrove leaves and bushes
 * are tinted as today's game tints them. Since 1.5.0.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID, value = Side.CLIENT)
public final class SuppliedBlocksClient {

    /** Today's {@code FoliageColor.FOLIAGE_MANGROVE}. */
    private static final int MANGROVE = 0x92C648;

    private SuppliedBlocksClient() {
    }

    @SubscribeEvent
    public static void models(ModelRegistryEvent event) {
        for (Block block : SuppliedBlocks.blocks().values()) {
            if (block instanceof com.exiledradio.backportcore.block.ModernWall || block instanceof SuppliedShapes.Fence
                    || block instanceof SuppliedShapes.Pane) {
                ModelResourceLocation one = new ModelResourceLocation(block.getRegistryName(), "normal");
                ModelLoader.setCustomStateMapper(block, new StateMapperBase() {
                    @Override
                    protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                        return one;
                    }
                });
            } else if (block instanceof BlockFenceGate) {
                ModelLoader.setCustomStateMapper(block, new StateMap.Builder().ignore(BlockFenceGate.POWERED).build());
            } else if (block instanceof BlockDoor) {
                ModelLoader.setCustomStateMapper(block, new StateMap.Builder().ignore(BlockDoor.POWERED).build());
            }
            Item item = SuppliedBlocks.itemOf(block);
            if (item != net.minecraft.init.Items.AIR && block.getRegistryName() != null) {
                ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(block.getRegistryName(), "inventory"));
            }
        }
    }

    private static int tint(String tint, net.minecraft.world.IBlockAccess world, net.minecraft.util.math.BlockPos pos) {
        if ("mangrove".equals(tint)) {
            return MANGROVE;
        }
        return world != null && pos != null ? BiomeColorHelper.getGrassColorAtPos(world, pos) : ColorizerGrass.getGrassColor(0.5D, 1.0D);
    }

    @SubscribeEvent
    public static void blockColors(ColorHandlerEvent.Block event) {
        for (Block block : SuppliedBlocks.blocks().values()) {
            String t = SuppliedBlocks.row(block) == null ? null : SuppliedBlocks.row(block).tint;
            if (t != null) {
                event.getBlockColors().registerBlockColorHandler((state, world, pos, index) -> tint(t, world, pos), block);
            }
        }
    }

    @SubscribeEvent
    public static void itemColors(ColorHandlerEvent.Item event) {
        for (Block block : SuppliedBlocks.blocks().values()) {
            String t = SuppliedBlocks.row(block) == null ? null : SuppliedBlocks.row(block).tint;
            Item item = SuppliedBlocks.itemOf(block);
            if (t != null && item instanceof ItemBlock) {
                event.getItemColors().registerItemColorHandler((stack, index) -> tint(t, null, null), item);
            }
        }
    }
}

package com.exiledradio.backportcore.supply.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.supply.SuppliedBlocks;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** The supplied blocks' item models ({@code models/item/<name>.json}). Since 1.5.0. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID, value = Side.CLIENT)
public final class SuppliedBlocksClient {

    private SuppliedBlocksClient() {
    }

    @SubscribeEvent
    public static void models(ModelRegistryEvent event) {
        for (Block block : SuppliedBlocks.blocks().values()) {
            Item item = Item.getItemFromBlock(block);
            if (item != net.minecraft.init.Items.AIR && block.getRegistryName() != null) {
                ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(block.getRegistryName(), "inventory"));
            }
        }
    }
}

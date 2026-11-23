package com.exiledradio.backportcore.block.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.block.CoreBlocks;
import com.exiledradio.backportcore.block.TileEntityJigsaw;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** The jigsaw block's item model and screen. Since Core 1.4.5. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID, value = Side.CLIENT)
public final class JigsawClient {

    private JigsawClient() {
    }

    @SubscribeEvent
    public static void models(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(CoreBlocks.jigsawItem, 0, new ModelResourceLocation(CoreBlocks.JIGSAW_ID, "inventory"));
    }

    /** Today's {@code openJigsawBlock}: the screen for the jigsaw at {@code pos}, as the client has it. */
    public static void open(BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        TileEntity te = mc.world == null ? null : mc.world.getTileEntity(pos);
        if (te instanceof TileEntityJigsaw) {
            mc.displayGuiScreen(new JigsawScreen((TileEntityJigsaw) te));
        }
    }
}

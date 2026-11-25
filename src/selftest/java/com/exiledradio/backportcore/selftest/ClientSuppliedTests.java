package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.supply.SuppliedBlocks;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * Dev only, environment variable {@code BACKPORTCORE_SUPPLIED=1} with {@code runClient -Pselftest}: at the title
 * screen, every state of every supplied block and every supplied item has a real model with a real texture (none is
 * the purple-and-black missing model). Logs {@code SUPPLIED SELFTEST DONE pass=N fail=M} and closes the game.
 */
@SideOnly(Side.CLIENT)
final class ClientSuppliedTests {

    private boolean done;

    static boolean active() {
        return System.getenv("BACKPORTCORE_SUPPLIED") != null;
    }

    static void register() {
        MinecraftForge.EVENT_BUS.register(new ClientSuppliedTests());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (this.done || event.phase != TickEvent.Phase.END || !(mc.currentScreen instanceof GuiMainMenu)) {
            return;
        }
        this.done = true;
        IBakedModel missing = mc.getBlockRendererDispatcher().getBlockModelShapes().getModelManager().getMissingModel();
        TextureAtlasSprite missingSprite = mc.getTextureMapBlocks().getMissingSprite();
        int states = 0;
        for (Block block : SuppliedBlocks.blocks().values()) {
            for (IBlockState state : block.getBlockState().getValidStates()) {
                IBakedModel model = mc.getBlockRendererDispatcher().getModelForState(state);
                states++;
                check("supplied client: model for " + state, model != missing && model.getParticleTexture() != missingSprite,
                        model.getParticleTexture().getIconName());
            }
            Item item = Item.getItemFromBlock(block);
            if (item != net.minecraft.init.Items.AIR) {
                IBakedModel model = mc.getRenderItem().getItemModelMesher().getItemModel(new ItemStack(item));
                check("supplied client: item model for " + block.getRegistryName(), model != missing
                        && model.getParticleTexture() != missingSprite, model.getParticleTexture().getIconName());
            }
        }
        check("supplied client: blocks are registered", SuppliedBlocks.blocks().size() >= 30, SuppliedBlocks.blocks().size() + " blocks, " + states + " states");
        BackportCore.LOGGER.info("SUPPLIED SELFTEST DONE pass={} fail={}", SelfTestRunner.pass, SelfTestRunner.fail);
        mc.shutdown();
    }
}

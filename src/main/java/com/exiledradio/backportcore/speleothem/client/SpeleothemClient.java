package com.exiledradio.backportcore.speleothem.client;

import com.exiledradio.backportcore.speleothem.EntityFallingSpeleothem;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

/** The falling segment's renderer: its block model at the entity, as vanilla draws a falling block. */
public final class SpeleothemClient {

    private SpeleothemClient() {
    }

    /** From Core's pre-init on a client (entity renderers are registered before init). */
    public static void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(EntityFallingSpeleothem.class, new IRenderFactory<EntityFallingSpeleothem>() {
            @Override
            public Render<? super EntityFallingSpeleothem> createRenderFor(RenderManager manager) {
                return new RenderFalling(manager);
            }
        });
    }

    private static final class RenderFalling extends Render<EntityFallingSpeleothem> {

        RenderFalling(RenderManager manager) {
            super(manager);
            this.shadowSize = 0.5F;
        }

        @Override
        public void doRender(EntityFallingSpeleothem entity, double x, double y, double z, float yaw, float partialTicks) {
            IBlockState state = entity.getState();
            World world = entity.world;
            if (state.getRenderType() != EnumBlockRenderType.MODEL || state == world.getBlockState(new BlockPos(entity))) {
                return;
            }
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.pushMatrix();
            GlStateManager.disableLighting();
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            if (this.renderOutlines) {
                GlStateManager.enableColorMaterial();
                GlStateManager.enableOutlineMode(getTeamColor(entity));
            }
            buffer.begin(7, DefaultVertexFormats.BLOCK);
            BlockPos pos = new BlockPos(entity.posX, entity.getEntityBoundingBox().maxY, entity.posZ);
            GlStateManager.translate((float) (x - pos.getX() - 0.5D), (float) (y - pos.getY()), (float) (z - pos.getZ() - 0.5D));
            BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
            dispatcher.getBlockModelRenderer().renderModel(world, dispatcher.getModelForState(state), state, pos, buffer, false,
                    MathHelper.getPositionRandom(pos));
            tessellator.draw();
            if (this.renderOutlines) {
                GlStateManager.disableOutlineMode();
                GlStateManager.disableColorMaterial();
            }
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
            super.doRender(entity, x, y, z, yaw, partialTicks);
        }

        @Override
        protected ResourceLocation getEntityTexture(EntityFallingSpeleothem entity) {
            return TextureMap.LOCATION_BLOCKS_TEXTURE;
        }
    }
}

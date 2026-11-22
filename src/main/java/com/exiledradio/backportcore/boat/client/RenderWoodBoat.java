package com.exiledradio.backportcore.boat.client;

import com.exiledradio.backportcore.boat.BoatWood;
import com.exiledradio.backportcore.boat.EntityWoodBoat;
import com.exiledradio.backportcore.boat.EntityWoodChestBoat;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Draws Core's boats as vanilla's {@code RenderBoat} draws its own (the same translation, rocking when hit, and the
 * mask that keeps water out of a boat's hull), with the wood's texture and 1.20's model for its kind: boat, chest
 * boat, raft or chest raft. A chest boat of a wood with no chest boat texture is drawn as that wood's boat.
 */
@SideOnly(Side.CLIENT)
public class RenderWoodBoat extends Render<EntityWoodBoat> {

    private static final ResourceLocation OAK = new ResourceLocation("textures/entity/boat/boat_oak.png");

    private final ModelWoodBoat boat = new ModelWoodBoat(false, false);
    private final ModelWoodBoat chestBoat = new ModelWoodBoat(false, true);
    private final ModelWoodBoat raft = new ModelWoodBoat(true, false);
    private final ModelWoodBoat chestRaft = new ModelWoodBoat(true, true);

    public RenderWoodBoat(RenderManager manager) {
        super(manager);
        this.shadowSize = 0.5F;
    }

    /** From the client's pre-init. */
    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(EntityWoodBoat.class, RenderWoodBoat::new);
        RenderingRegistry.registerEntityRenderingHandler(EntityWoodChestBoat.class, RenderWoodBoat::new);
    }

    private static boolean drawsChest(EntityWoodBoat entity) {
        return entity instanceof EntityWoodChestBoat && entity.getWood().chestBoatTexture() != null;
    }

    private ModelWoodBoat model(EntityWoodBoat entity) {
        boolean chest = drawsChest(entity);
        if (entity.getWood().raft) {
            return chest ? this.chestRaft : this.raft;
        }
        return chest ? this.chestBoat : this.boat;
    }

    @Override
    public void doRender(EntityWoodBoat entity, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        setup(entity, x, y, z, entityYaw, partialTicks);
        bindEntityTexture(entity);
        if (this.renderOutlines) {
            GlStateManager.enableColorMaterial();
            GlStateManager.enableOutlineMode(getTeamColor(entity));
        }
        model(entity).render(entity, partialTicks, 0.0625F);
        if (this.renderOutlines) {
            GlStateManager.disableOutlineMode();
            GlStateManager.disableColorMaterial();
        }
        GlStateManager.popMatrix();
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    private static void setup(EntityWoodBoat entity, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.translate((float) x, (float) y + 0.375F, (float) z);
        GlStateManager.rotate(180.0F - entityYaw, 0.0F, 1.0F, 0.0F);
        float hit = entity.getTimeSinceHit() - partialTicks;
        float damage = Math.max(0.0F, entity.getDamageTaken() - partialTicks);
        if (hit > 0.0F) {
            GlStateManager.rotate(MathHelper.sin(hit) * hit * damage / 10.0F * entity.getForwardDirection(), 1.0F, 0.0F, 0.0F);
        }
        GlStateManager.scale(-1.0F, -1.0F, 1.0F);
    }

    @Override
    public boolean isMultipass() {
        return true;
    }

    /** The invisible patch that keeps water from being drawn inside a boat's hull (rafts have none). */
    @Override
    public void renderMultipass(EntityWoodBoat entity, double x, double y, double z, float entityYaw, float partialTicks) {
        ModelWoodBoat m = model(entity);
        if (m.noWater == null) {
            return;
        }
        GlStateManager.pushMatrix();
        setup(entity, x, y, z, entityYaw, partialTicks);
        bindEntityTexture(entity);
        GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.colorMask(false, false, false, false);
        m.noWater.render(0.0625F);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.popMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityWoodBoat entity) {
        BoatWood wood = entity.getWood();
        ResourceLocation tex = drawsChest(entity) ? wood.chestBoatTexture() : wood.boatTexture();
        return tex != null ? tex : OAK;
    }
}

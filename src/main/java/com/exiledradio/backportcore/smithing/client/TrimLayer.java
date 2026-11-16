package com.exiledradio.backportcore.smithing.client;

import com.exiledradio.backportcore.smithing.ArmorTrims;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Trims after another mod's own biped armor layer, which this mod cannot step inside: each trimmed piece is drawn
 * again with its trim texture on the layer's armor model. The armor layer's glint is already drawn by then, so the
 * trim itself shows none; {@link TrimmedArmorLayer} (every vanilla armor layer) gets that right.
 */
@SideOnly(Side.CLIENT)
final class TrimLayer implements LayerRenderer<EntityLivingBase> {

    static final EntityEquipmentSlot[] ORDER = {EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS,
            EntityEquipmentSlot.FEET, EntityEquipmentSlot.HEAD};

    private final RenderLivingBase<?> renderer;
    private final LayerBipedArmor armor;

    TrimLayer(RenderLivingBase<?> renderer, LayerBipedArmor armor) {
        this.renderer = renderer;
        this.armor = armor;
    }

    @Override
    public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                              float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        for (EntityEquipmentSlot slot : ORDER) {
            ItemStack stack = entity.getItemStackFromSlot(slot);
            if (!(stack.getItem() instanceof ItemArmor) || ((ItemArmor) stack.getItem()).getEquipmentSlot() != slot) {
                continue;
            }
            ArmorTrims.Trim trim = ArmorTrims.get(stack);
            if (trim == null) {
                continue;
            }
            ModelBiped model = this.armor.getModelFromSlot(slot);
            if (ForgeHooksClient.getArmorModel(entity, stack, slot, model) != model) {
                // The item draws its own model, whose UVs are not vanilla's armor layout.
                continue;
            }
            model.setModelAttributes(this.renderer.getMainModel());
            model.setLivingAnimations(entity, limbSwing, limbSwingAmount, partialTicks);
            visible(model, slot);
            draw(this.renderer, entity, stack, slot, trim, model, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        }
    }

    /** One trim on its piece's model, which is already posed and shows only that piece. */
    static void draw(RenderLivingBase<?> renderer, EntityLivingBase entity, ItemStack stack, EntityEquipmentSlot slot,
                     ArmorTrims.Trim trim, ModelBiped model, float limbSwing, float limbSwingAmount, float ageInTicks,
                     float netHeadYaw, float headPitch, float scale) {
        renderer.bindTexture(ArmorTrimsClient.armorTexture(trim, stack, slot == EntityEquipmentSlot.LEGS));
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        if (trim.pattern.decal) {
            GlStateManager.enablePolygonOffset();
            GlStateManager.doPolygonOffset(-1.0F, -10.0F);
        }
        model.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        if (trim.pattern.decal) {
            GlStateManager.doPolygonOffset(0.0F, 0.0F);
            GlStateManager.disablePolygonOffset();
        }
    }

    /** {@code LayerBipedArmor.setModelSlotVisible}, which is protected there. */
    static void visible(ModelBiped model, EntityEquipmentSlot slot) {
        model.setVisible(false);
        switch (slot) {
            case HEAD:
                model.bipedHead.showModel = true;
                model.bipedHeadwear.showModel = true;
                break;
            case CHEST:
                model.bipedBody.showModel = true;
                model.bipedRightArm.showModel = true;
                model.bipedLeftArm.showModel = true;
                break;
            case LEGS:
                model.bipedBody.showModel = true;
                model.bipedRightLeg.showModel = true;
                model.bipedLeftLeg.showModel = true;
                break;
            case FEET:
                model.bipedRightLeg.showModel = true;
                model.bipedLeftLeg.showModel = true;
                break;
            default:
                break;
        }
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}

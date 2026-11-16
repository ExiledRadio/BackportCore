package com.exiledradio.backportcore.smithing.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.smithing.ArmorTrims;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Method;

/**
 * A biped armor layer that draws each piece as 1.20's {@code HumanoidArmorLayer.renderArmorPiece}: the armor, then
 * its trim, then the enchantment glint over both.
 *
 * <p>The armor itself is still drawn by vanilla's own {@code LayerArmorBase.renderArmorLayer} (so whatever other
 * mods change in it, custom armor textures and models included, stays), with its glint held back; this layer draws
 * the trim and then the glint. It takes over only a renderer's plain armor layer: vanilla's, or one that only
 * picks other armor models (the zombie's, the armor stand's), whose models it reuses.
 */
@SideOnly(Side.CLIENT)
final class TrimmedArmorLayer extends LayerBipedArmor {

    private static Method renderArmorLayer;
    private static boolean failed;

    private final RenderLivingBase<?> renderer;

    TrimmedArmorLayer(RenderLivingBase<?> renderer, LayerBipedArmor original) {
        super(renderer);
        this.renderer = renderer;
        this.modelLeggings = original.getModelFromSlot(EntityEquipmentSlot.LEGS);
        this.modelArmor = original.getModelFromSlot(EntityEquipmentSlot.CHEST);
        ObfuscationReflectionHelper.setPrivateValue(LayerArmorBase.class, this, true, "field_177193_i");
    }

    /** @return true for vanilla's armor layer, or a subclass that only overrides which models it uses */
    static boolean canReplace(LayerBipedArmor layer) {
        if (method() == null) {
            return false;
        }
        Class<?> c = layer.getClass();
        if (c == LayerBipedArmor.class) {
            return true;
        }
        if (c.getSuperclass() != LayerBipedArmor.class) {
            return false;
        }
        for (Method m : c.getDeclaredMethods()) {
            if (!m.isSynthetic() && !"initArmor".equals(m.getName()) && !"func_177177_a".equals(m.getName())) {
                return false;
            }
        }
        return true;
    }

    private static Method method() {
        if (renderArmorLayer == null && !failed) {
            try {
                renderArmorLayer = ObfuscationReflectionHelper.findMethod(LayerArmorBase.class, "func_188361_a", void.class,
                        EntityLivingBase.class, float.class, float.class, float.class, float.class, float.class, float.class,
                        float.class, EntityEquipmentSlot.class);
            } catch (RuntimeException e) {
                failed = true;
                BackportCore.LOGGER.warn("Armor trims: no renderArmorLayer ({}); trims go in a layer of their own.", e.toString());
            }
        }
        return renderArmorLayer;
    }

    @Override
    public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                              float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        for (EntityEquipmentSlot slot : TrimLayer.ORDER) {
            try {
                renderArmorLayer.invoke(this, entity, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw,
                        headPitch, scale, slot);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Armor trims: renderArmorLayer", e);
            }
            ItemStack stack = entity.getItemStackFromSlot(slot);
            if (!(stack.getItem() instanceof ItemArmor) || ((ItemArmor) stack.getItem()).getEquipmentSlot() != slot) {
                continue;
            }
            ModelBiped plain = getModelFromSlot(slot);
            // The same model renderArmorLayer just drew with (Forge's hook returns the item's model or ours).
            ModelBiped model = getArmorModelHook(entity, stack, slot, plain);
            ArmorTrims.Trim trim = ArmorTrims.get(stack);
            if (trim != null && model == plain) {
                TrimLayer.draw(this.renderer, entity, stack, slot, trim, model, limbSwing, limbSwingAmount, ageInTicks,
                        netHeadYaw, headPitch, scale);
            }
            if (stack.hasEffect()) {
                renderEnchantedGlint(this.renderer, entity, model, limbSwing, limbSwingAmount, partialTicks, ageInTicks,
                        netHeadYaw, headPitch, scale);
            }
        }
    }
}

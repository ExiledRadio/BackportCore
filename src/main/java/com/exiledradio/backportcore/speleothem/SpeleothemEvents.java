package com.exiledradio.backportcore.speleothem;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * What {@link BlockSpeleothem}s do outside their block: the {@code stalagmite} and {@code falling_stalactite} damage
 * types, and tridents breaking them ({@code onProjectileHit}).
 */
public final class SpeleothemEvents {

    /** {@code stalagmite}: a fall, so Feather Falling counts; bypasses armor. */
    public static final DamageSource STALAGMITE = new DamageSource("stalagmite").setDamageBypassesArmor();
    /** {@code falling_stalactite}: as an anvil, a helmet takes a quarter of it and wears for it. */
    public static final DamageSource FALLING_STALACTITE = new DamageSource("fallingStalactite");

    private static boolean impaling;

    /** A fall onto a stalagmite's tip: the fall, its damage turned into {@link #STALAGMITE}. */
    static void impale(Entity entity, float distance, float multiplier) {
        boolean was = impaling;
        impaling = true;
        try {
            entity.fall(distance, multiplier);
        } finally {
            impaling = was;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(LivingAttackEvent event) {
        if (impaling && event.getSource() == DamageSource.FALL) {
            event.setCanceled(true);
            impaling = false;
            try {
                event.getEntityLiving().attackEntityFrom(STALAGMITE, event.getAmount());
            } finally {
                impaling = true;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHurt(LivingHurtEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (event.getSource() == STALAGMITE) {
            // 1.12 gives Feather Falling its effect only for DamageSource.FALL itself: scale so vanilla's own step
            // ends where it would with it.
            int featherFalling = 0;
            for (ItemStack armor : entity.getArmorInventoryList()) {
                featherFalling += 3 * EnchantmentHelper.getEnchantmentLevel(Enchantments.FEATHER_FALLING, armor);
            }
            if (featherFalling > 0) {
                int have = Math.min(20, Math.max(0, EnchantmentHelper.getEnchantmentModifierDamage(entity.getArmorInventoryList(), STALAGMITE)));
                int want = Math.min(20, have + featherFalling);
                event.setAmount(event.getAmount() * (1 - want / 25.0F) / (1 - have / 25.0F));
            }
        } else if (event.getSource() == FALLING_STALACTITE) {
            ItemStack helmet = entity.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
            if (!helmet.isEmpty()) {
                float amount = event.getAmount();
                helmet.damageItem((int) (amount * 4.0F + entity.getRNG().nextFloat() * amount * 2.0F), entity);
                event.setAmount(amount * 0.75F);
            }
        }
    }

    /** A thrown trident faster than 0.6 breaks the segment it hits. Any mod's trident, by its entity name. */
    @SubscribeEvent
    public void onProjectile(ProjectileImpactEvent event) {
        RayTraceResult hit = event.getRayTraceResult();
        Entity projectile = event.getEntity();
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || projectile == null || projectile.world.isRemote) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (!BlockSpeleothem.isSpeleothem(projectile.world.getBlockState(pos))) {
            return;
        }
        ResourceLocation id = EntityList.getKey(projectile);
        double speed = Math.sqrt(projectile.motionX * projectile.motionX + projectile.motionY * projectile.motionY
                + projectile.motionZ * projectile.motionZ);
        if (id != null && id.getPath().contains("trident") && speed > BlockSpeleothem.TRIDENT_SPEED) {
            projectile.world.destroyBlock(pos, true);
        }
    }
}

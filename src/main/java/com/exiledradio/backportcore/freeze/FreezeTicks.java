package com.exiledradio.backportcore.freeze;

import com.exiledradio.backportcore.CoreConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.monster.EntityPolarBear;
import net.minecraft.entity.monster.EntitySnowman;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Freeze ticks: 1.17's frozen counter, shared by the family (Backport 1.5.0, from Caves & Cliffs' powder snow), with
 * 26.4's rules.
 *
 * <h2>The rules (26.4 Pre-Release 1, {@code Entity}, {@code LivingEntity})</h2>
 * <ul>
 *   <li>A source adds ticks with {@link #freezeFor}: powder snow adds 1 a tick while an entity is in it
 *       ({@code InsideBlockEffectType.FREEZE}), 26.4's Freezing effect adds 1 a tick while it lasts
 *       ({@code FreezingMobEffect}). Ticks never pass {@value #REQUIRED} ({@code getTicksRequiredToFreeze}).</li>
 *   <li>Each server tick a living entity that can't freeze, or is neither held by a freezing block this tick
 *       ({@link #markInFreezingBlock}) nor under a registered effect ({@link #registerEffect}), loses 2.</li>
 *   <li>Fully frozen, it takes freeze damage every {@value #HURT_EVERY} ticks: 1, or the effect's damage (26.4's
 *       Freezing: 1.5) while one is active; five times that for blazes, magma cubes and striders
 *       ({@code #freeze_hurts_extra_types}). Players take none when the {@code freezeDamage} game rule is off.</li>
 *   <li>It is slowed by {@code -0.05 x percent frozen} while it has any ticks.</li>
 *   <li>Nothing freezes ({@code canFreeze}) while on fire, as a spectator, wearing any leather armor
 *       ({@code #freeze_immune_wearables}), or if it is a polar bear, a snow golem or undead
 *       ({@code #immune_to_freezing}: 26.4 added {@code #undead}, which also covers strays and the wither).
 *       Fire and lava also clear the counter ({@code CLEAR_FREEZE}).</li>
 * </ul>
 *
 * <h2>Where it lives</h2>
 * In the compound {@value #TAG} of the entity's Forge data, only while it matters: {@code Ticks}, {@code Held} (the
 * world time a freezing block last held it) and {@code Sent} (the flags last sent). An entity that was never frozen
 * gets no Forge data: the tick looks without creating it.
 *
 * <h2>The client</h2>
 * {@link FreezeNetwork} tells watchers when an entity starts or stops freezing; the client counts the ticks up and
 * down itself by the same rule, for the frost overlay and the shaking ({@code client.FreezeClient}).
 */
public final class FreezeTicks {

    public static final String TAG = "BCFreeze";
    /** {@code Entity.getTicksRequiredToFreeze()}. */
    public static final int REQUIRED = 140;
    public static final int HURT_EVERY = 40;
    public static final String GAME_RULE = "freezeDamage";
    /** {@code LivingEntity.SPEED_MODIFIER_POWDER_SNOW_ID}'s modifier, under the UUID 1.17 gave it. */
    public static final UUID SLOW = UUID.fromString("1eaf83ff-7207-4596-b37a-d7a07b3ec4ce");

    /** Vanilla's {@code freeze} damage type: bypasses armor; death messages {@code death.attack.freeze}. */
    public static final DamageSource FREEZE = new DamageSource("freeze").setDamageBypassesArmor();

    static final int FLAG_FREEZING = 1;

    private static final Field DATA = ReflectionHelper.findField(Entity.class, "customEntityData");
    /** Effects that hold the counter up and set the damage while active, in registration order. */
    private static final Map<Potion, Float> EFFECTS = new LinkedHashMap<Potion, Float>();
    private static final Ids IMMUNE = new Ids(), EXTRA = new Ids(), WEARABLES = new Ids();
    private static final ResourceLocation STRIDER = new ResourceLocation("netherupdatebackport", "strider");

    private FreezeTicks() {
    }

    // ---- API ---------------------------------------------------------------------------------

    /**
     * Registers an effect that freezes: while it is active the counter does not thaw, and full freezing hurts by
     * {@code damage} instead of 1. The effect's own tick adds the ticks with {@link #freezeFor}.
     */
    public static synchronized void registerEffect(Potion effect, float damage) {
        EFFECTS.put(effect, damage);
    }

    /** {@code Entity.freezeForTicks}: adds ticks, up to {@value #REQUIRED}, to an entity that can freeze. */
    public static void freezeFor(EntityLivingBase entity, int ticks) {
        if (canFreeze(entity)) {
            NBTTagCompound f = state(entity);
            f.setInteger("Ticks", Math.min(REQUIRED, f.getInteger("Ticks") + ticks));
        }
    }

    /**
     * A freezing block (powder snow) holds the entity this tick: it gains a tick and does not thaw. Both
     * {@code isInPowderSnow} and {@code InsideBlockEffectType.FREEZE} in one call.
     */
    public static void markInFreezingBlock(EntityLivingBase entity) {
        state(entity).setLong("Held", entity.world.getTotalWorldTime());
        freezeFor(entity, 1);
    }

    /** {@code Entity.clearFreeze}: fire and lava. */
    public static void clear(Entity entity) {
        NBTTagCompound f = peek(entity);
        if (f != null) {
            f.setInteger("Ticks", 0);
        }
    }

    public static int ticks(Entity entity) {
        NBTTagCompound f = peek(entity);
        return f == null ? 0 : f.getInteger("Ticks");
    }

    /** For tests and commands: sets the counter outright. */
    public static void setTicks(EntityLivingBase entity, int ticks) {
        state(entity).setInteger("Ticks", Math.max(0, Math.min(REQUIRED, ticks)));
    }

    public static float percent(Entity entity) {
        return Math.min(ticks(entity), REQUIRED) / (float) REQUIRED;
    }

    public static boolean fullyFrozen(Entity entity) {
        return ticks(entity) >= REQUIRED;
    }

    /** {@code LivingEntity.canFreeze()}. */
    public static boolean canFreeze(EntityLivingBase e) {
        if (e.isBurning() || e instanceof EntityPlayer && ((EntityPlayer) e).isSpectator() || immune(e)) {
            return false;
        }
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            if (slot.getSlotType() == EntityEquipmentSlot.Type.ARMOR && warm(e.getItemStackFromSlot(slot))) {
                return false;
            }
        }
        return true;
    }

    /** {@code #immune_to_freezing}: polar bears, snow golems, the undead, and the config's list. */
    public static boolean immune(Entity entity) {
        if (entity instanceof EntityPolarBear || entity instanceof EntitySnowman) {
            return true;
        }
        if (entity instanceof EntityLivingBase && ((EntityLivingBase) entity).getCreatureAttribute() == EnumCreatureAttribute.UNDEAD) {
            return true;
        }
        return IMMUNE.has(CoreConfig.freezing.freezeImmuneMobs, EntityList.getKey(entity));
    }

    /** {@code #freeze_hurts_extra_types}: blazes, magma cubes, striders, and the config's list. */
    public static boolean hurtsExtra(Entity entity) {
        if (entity instanceof EntityBlaze || entity instanceof EntityMagmaCube) {
            return true;
        }
        ResourceLocation id = EntityList.getKey(entity);
        return id != null && (id.equals(STRIDER) || EXTRA.has(CoreConfig.freezing.freezeHurtsExtraMobs, id));
    }

    /** {@code #freeze_immune_wearables}: every piece of leather armor, and the config's list. */
    public static boolean warm(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof ItemArmor && ((ItemArmor) item).getArmorMaterial() == ItemArmor.ArmorMaterial.LEATHER) {
            return true;
        }
        return WEARABLES.has(CoreConfig.freezing.freezeImmuneWearables, item.getRegistryName());
    }

    // ---- state -------------------------------------------------------------------------------

    @Nullable
    static NBTTagCompound peek(Entity entity) {
        try {
            NBTTagCompound data = (NBTTagCompound) DATA.get(entity);
            return data != null && data.hasKey(TAG, 10) ? data.getCompoundTag(TAG) : null;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static NBTTagCompound state(Entity entity) {
        NBTTagCompound data = entity.getEntityData();
        if (!data.hasKey(TAG, 10)) {
            data.setTag(TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(TAG);
    }

    private static boolean heldNow(Entity e, NBTTagCompound f) {
        if (!f.hasKey("Held")) {
            return false;
        }
        long ago = e.world.getTotalWorldTime() - f.getLong("Held");
        return ago >= 0 && ago <= 1;
    }

    /** @return the damage of the first registered effect the entity has, or 0 when none */
    private static float effectDamage(EntityLivingBase e) {
        synchronized (FreezeTicks.class) {
            for (Map.Entry<Potion, Float> entry : EFFECTS.entrySet()) {
                if (e.isPotionActive(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return 0.0F;
    }

    // ---- the tick ----------------------------------------------------------------------------

    /** Every living entity, every tick, both sides ({@link FreezeEvents}). Nothing for one never frozen. */
    static void tick(EntityLivingBase e) {
        NBTTagCompound f = peek(e);
        if (f == null) {
            return;
        }
        World world = e.world;
        if (world.isRemote) {
            int t = f.getInteger("Ticks");
            boolean freezing = (f.getByte("Flags") & FLAG_FREEZING) != 0;
            // The server adds before the thaw check; the client counts the sum of both.
            int n = freezing ? Math.min(REQUIRED, t + 1) : Math.max(0, t - 2);
            if (n != t) {
                f.setInteger("Ticks", n);
            }
            return;
        }
        // CLEAR_FREEZE: standing in fire or lava.
        if (world.isFlammableWithin(e.getEntityBoundingBox().shrink(0.001D))) {
            f.setInteger("Ticks", 0);
        }
        int t = f.getInteger("Ticks");
        float effect = effectDamage(e);
        boolean held = heldNow(e, f) || effect > 0.0F;
        boolean can = canFreeze(e);
        if (!can || !held) {
            t = Math.max(0, t - 2);
            f.setInteger("Ticks", t);
        }
        slow(e, t);
        if (e.ticksExisted % HURT_EVERY == 0 && t >= REQUIRED && can
                && (!(e instanceof EntityPlayer) || freezeDamage(world))) {
            float damage = effect > 0.0F ? effect : 1.0F;
            e.attackEntityFrom(FREEZE, hurtsExtra(e) ? damage * 5.0F : damage);
        }
        if (e.isDead) {
            return;
        }
        int flags = can && held ? FLAG_FREEZING : 0;
        if (!f.hasKey("Sent") || f.getByte("Sent") != flags) {
            f.setByte("Sent", (byte) flags);
            FreezeNetwork.send(e, t, flags);
        }
        if (t == 0 && flags == 0 && !heldNow(e, f)) {
            e.getEntityData().removeTag(TAG);
            if (f.getByte("Sent") != 0) {
                FreezeNetwork.send(e, 0, 0);
            }
        }
    }

    private static boolean freezeDamage(World world) {
        return !world.getGameRules().hasRule(GAME_RULE) || world.getGameRules().getBoolean(GAME_RULE);
    }

    private static void slow(EntityLivingBase e, int t) {
        IAttributeInstance speed = e.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double want = t > 0 ? -0.05F * (Math.min(t, REQUIRED) / (float) REQUIRED) : 0.0D;
        AttributeModifier has = speed.getModifier(SLOW);
        if (has != null) {
            if (want != 0.0D && has.getAmount() == want) {
                return;
            }
            speed.removeModifier(has);
        }
        if (want != 0.0D) {
            speed.applyModifier(new AttributeModifier(SLOW, "Freezing slow", want, 0).setSaved(false));
        }
    }

    // ---- sync --------------------------------------------------------------------------------

    /** A player starts seeing an entity: tell them how frozen it is. */
    static void startTracking(EntityPlayerMP watcher, Entity target) {
        NBTTagCompound f = peek(target);
        if (f != null && (f.getInteger("Ticks") > 0 || f.getByte("Sent") != 0)) {
            FreezeNetwork.sendTo(watcher, target.getEntityId(), f.getInteger("Ticks"), f.getByte("Sent"));
        }
    }

    /** An entity (re)enters a world: its watchers are new. */
    static void joined(Entity entity) {
        NBTTagCompound f = peek(entity);
        if (f != null) {
            f.removeTag("Sent");
        }
    }

    /** Client side, from the network. */
    public static void received(Entity entity, int ticks, int flags) {
        if (ticks == 0 && flags == 0 && peek(entity) == null) {
            return;
        }
        NBTTagCompound f = state(entity);
        f.setInteger("Ticks", ticks);
        f.setByte("Flags", (byte) flags);
    }

    /** Config id lists, parsed once per array the config holds. */
    private static final class Ids {
        private String[] source;
        private Set<ResourceLocation> ids = new HashSet<ResourceLocation>();

        synchronized boolean has(String[] current, @Nullable ResourceLocation id) {
            if (current != this.source) {
                Set<ResourceLocation> parsed = new HashSet<ResourceLocation>();
                for (String s : current) {
                    if (!s.trim().isEmpty()) {
                        parsed.add(new ResourceLocation(s.trim()));
                    }
                }
                this.ids = parsed;
                this.source = current;
            }
            return id != null && this.ids.contains(id);
        }
    }
}

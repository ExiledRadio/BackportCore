package com.exiledradio.backportcore.presentation.versions;

import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * What patches P73 to P76 ({@code docs/PATCHES.md}, {@code asm/LookVersionsTransformer}) call. All four patch client
 * code, but the descriptors name only primitives and {@code ItemStack}, so this class loads anywhere (the server
 * self-test checks the values). Each hook returns vanilla 1.12.2's value while its piece does not show: the option
 * before 1.19.4 (or with the options' part of the look off), the picked block before 1.20.5.
 */
public final class LookHooks {

    /** For the client self-test: how many picked stacks P76 changed. */
    public static volatile int pickedChanged;

    private LookHooks() {
    }

    /**
     * P73, {@code EntityRenderer.hurtCameraEffect}: the tilt's 14 degrees. 1.19.4's {@code GameRenderer.bobHurt}:
     * {@code -sin(...) * 14.0 * damageTiltStrength}.
     */
    public static float damageTilt(float degrees) {
        return Look119.DAMAGE_TILT.isShown() ? degrees * (float) Look119.DAMAGE_TILT.get() : degrees;
    }

    /**
     * P74, {@code RenderItem.renderEffect}: the clock the item glint scrolls by, {@code Minecraft.getSystemTime()}.
     * 1.19.4's {@code RenderStateShard.setupGlintTexturing} scrolls by {@code millis * glintSpeed * 8} where 1.19.3's
     * scrolled by {@code millis * 8}, so 1.12.2's clock is scaled by Glint Speed.
     */
    public static long glintTime(long millis) {
        return Look119.GLINT_SPEED.isShown() ? (long) (millis * Look119.GLINT_SPEED.get()) : millis;
    }

    /**
     * P74, {@code RenderItem.renderEffect}: the glint's color, {@code 0xFF8040CC}. 1.19.4's glint shader multiplies
     * the added color by {@code GlintAlpha} (Glint Strength); the alpha byte is kept.
     */
    public static int glintColor(int argb) {
        if (!Look119.GLINT_STRENGTH.isShown()) {
            return argb;
        }
        float s = (float) Look119.GLINT_STRENGTH.get();
        int r = Math.round(((argb >> 16) & 0xFF) * s);
        int g = Math.round(((argb >> 8) & 0xFF) * s);
        int b = Math.round((argb & 0xFF) * s);
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /**
     * P75, {@code LayerArmorBase.renderEnchantedGlint} (armor and elytra): each of the glint color's three components,
     * scaled by Glint Strength as {@link #glintColor}.
     */
    public static float glintComponent(float c) {
        return Look119.GLINT_STRENGTH.isShown() ? c * (float) Look119.GLINT_STRENGTH.get() : c;
    }

    /**
     * P75, {@code LayerArmorBase.renderEnchantedGlint}: its clock, {@code ticksExisted + partialTicks}, scaled by Glint
     * Speed as {@link #glintTime}.
     */
    public static float glintTicks(float ticks) {
        return Look119.GLINT_SPEED.isShown() ? ticks * (float) Look119.GLINT_SPEED.get() : ticks;
    }

    /**
     * P76: what {@code Minecraft.storeTEInStack} returns. 1.20.5 (24w09a's components) stopped adding "(+NBT)" to a
     * Ctrl + picked block, and a named block (a renamed chest) gives a named item ({@code BlockEntity.collectComponents}
     * hands over its custom name). From 1.20.5, with the inventory's part of the look on.
     */
    public static ItemStack pickedBlock(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound() || !Presentation.isAtLeast(LookArea.INVENTORY, "1.20.5")) {
            return stack;
        }
        NBTTagCompound tag = stack.getTagCompound();
        boolean changed = false;
        if (tag.hasKey("display", Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound display = tag.getCompoundTag("display");
            NBTTagList lore = display.getTagList("Lore", Constants.NBT.TAG_STRING);
            if (lore.tagCount() == 1 && "(+NBT)".equals(lore.getStringTagAt(0))) {
                display.removeTag("Lore");
                changed = true;
            }
            if (display.isEmpty()) {
                tag.removeTag("display");
            }
        }
        NBTTagCompound block = tag.getCompoundTag("BlockEntityTag");
        if (block.hasKey("CustomName", Constants.NBT.TAG_STRING) && !block.getString("CustomName").isEmpty() && !stack.hasDisplayName()) {
            stack.setStackDisplayName(block.getString("CustomName"));
            changed = true;
        }
        if (changed) {
            pickedChanged++;
        }
        return stack;
    }
}

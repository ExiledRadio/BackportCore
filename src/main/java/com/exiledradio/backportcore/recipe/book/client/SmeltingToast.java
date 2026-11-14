package com.exiledradio.backportcore.recipe.book.client;

import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.13's "New Recipes Unlocked!" toast for smelting recipes: 1.12.2's recipe toast with the recipe's block (the
 * furnace) small in the corner, as 1.13 draws each recipe's icon. Recipes unlocked while it shows join it.
 */
@SideOnly(Side.CLIENT)
public final class SmeltingToast implements IToast {

    private final List<ItemStack[]> shown = new ArrayList<ItemStack[]>();
    private long firstDrawTime;
    private boolean hasNew;

    private SmeltingToast(ItemStack icon, ItemStack output) {
        this.shown.add(new ItemStack[] {icon, output});
    }

    @Override
    public Visibility draw(GuiToast toastGui, long delta) {
        if (this.hasNew) {
            this.firstDrawTime = delta;
            this.hasNew = false;
        }
        if (this.shown.isEmpty()) {
            return Visibility.HIDE;
        }
        toastGui.getMinecraft().getTextureManager().bindTexture(TEXTURE_TOASTS);
        GlStateManager.color(1.0F, 1.0F, 1.0F);
        toastGui.drawTexturedModalRect(0, 0, 0, 32, 160, 32);
        toastGui.getMinecraft().fontRenderer.drawString(I18n.format("recipe.toast.title"), 30, 7, -11534256);
        toastGui.getMinecraft().fontRenderer.drawString(I18n.format("recipe.toast.description"), 30, 18, -16777216);
        ItemStack[] pair = this.shown.get((int) (delta * (long) this.shown.size() / 5000L % (long) this.shown.size()));
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.6F, 0.6F, 1.0F);
        toastGui.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI((EntityLivingBase) null, pair[0], 3, 4);
        GlStateManager.popMatrix();
        toastGui.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI((EntityLivingBase) null, pair[1], 8, 8);
        return delta - this.firstDrawTime >= 5000L ? Visibility.HIDE : Visibility.SHOW;
    }

    public static void addOrUpdate(GuiToast toasts, ItemStack icon, ItemStack output) {
        SmeltingToast toast = toasts.getToast(SmeltingToast.class, NO_TOKEN);
        if (toast == null) {
            toasts.add(new SmeltingToast(icon, output));
        } else {
            toast.shown.add(new ItemStack[] {icon, output});
            toast.hasNew = true;
        }
    }
}

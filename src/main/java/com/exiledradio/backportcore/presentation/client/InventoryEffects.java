package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import com.google.common.collect.Ordering;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The active effects beside an inventory as 1.18 and 1.21.11 draw them ({@link VanillaRevisions#effectsBesideInventory},
 * {@link VanillaRevisions#effectsFitText}; 26.3's {@code EffectsInInventory}), for every 1.12.2
 * {@code InventoryEffectRenderer} (the survival and creative inventories, and other mods' that extend it): a column
 * right of the inventory, which stays centered.
 *
 * <p>Patch P57 sends {@code InventoryEffectRenderer.drawScreen}'s {@code drawActivePotionEffects()} here; before 1.18
 * it runs 1.12.2's own. The backgrounds are the {@code container/inventory/effect_background*} sprites when a backport
 * supplies them, else the effect background of {@code container/inventory.png} (whichever texture layer drew it)
 * sliced as the sprites are; the icons are {@code inventory.png}'s, and Forge's {@code Potion.renderInventoryEffect}
 * still runs for mods' own effects.
 */
@SideOnly(Side.CLIENT)
public final class InventoryEffects {

    private static final ResourceLocation INVENTORY = new ResourceLocation("textures/gui/container/inventory.png");
    static final String SPRITE = "textures/gui/sprites/container/inventory/effect_background.png";
    static final String SPRITE_AMBIENT = "textures/gui/sprites/container/inventory/effect_background_ambient.png";
    static final String SPRITE_LARGE = "textures/gui/sprites/container/inventory/effect_background_large.png";
    static final String SPRITE_SMALL = "textures/gui/sprites/container/inventory/effect_background_small.png";

    private static Method vanilla;

    /** For the client self-test: effects drawn by Core, and the last frame's backgrounds (x, y, width each). */
    public static volatile int drawn;
    public static volatile int[] lastBackgrounds = new int[0];
    /** For the client self-test: the last frame's compact tooltip, or null. */
    public static volatile List<String> lastTooltip;

    private InventoryEffects() {
    }

    /** P57: {@code InventoryEffectRenderer.drawScreen}'s {@code drawActivePotionEffects()}. */
    public static void draw(InventoryEffectRenderer gui) {
        if (!VanillaRevisions.effectsBesideInventory()) {
            drawVanilla(gui);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int mouseX = org.lwjgl.input.Mouse.getX() * gui.width / mc.displayWidth;
        int mouseY = gui.height - org.lwjgl.input.Mouse.getY() * gui.height / mc.displayHeight - 1;
        render(gui, mc, mouseX, mouseY);
    }

    private static void drawVanilla(InventoryEffectRenderer gui) {
        try {
            if (vanilla == null) {
                vanilla = ReflectionHelper.findMethod(InventoryEffectRenderer.class, "drawActivePotionEffects", "func_147044_g");
            }
            vanilla.invoke(gui);
        } catch (Exception e) {
            BackportCore.LOGGER.error("Inventory effects: vanilla's drawActivePotionEffects failed", e);
        }
    }

    /** 26.3's {@code canSeeEffects}: at least 32 pixels right of the inventory. */
    public static boolean canSeeEffects(GuiContainer gui) {
        return gui.width - (gui.getGuiLeft() + gui.getXSize() + 2) >= 32;
    }

    static void render(GuiContainer gui, Minecraft mc, int mouseX, int mouseY) {
        int x = gui.getGuiLeft() + gui.getXSize() + 2;
        int available = gui.width - x;
        Collection<PotionEffect> active = mc.player.getActivePotionEffects();
        List<PotionEffect> shown = new ArrayList<PotionEffect>();
        for (PotionEffect e : Ordering.natural().sortedCopy(active)) {
            if (e.getPotion().shouldRender(e)) {
                shown.add(e);
            }
        }
        if (shown.isEmpty() || available < 32) {
            lastBackgrounds = new int[0];
            return;
        }
        int step = 33;
        if (shown.size() > 5) {
            step = 132 / (shown.size() - 1);
        }
        boolean fit = VanillaRevisions.effectsFitText();
        // 1.21.11: as wide as the text, at most the room less 7; 1.18: 120 when 120 fit, else 32.
        int maxWidth = available >= 120 ? (fit ? available - 7 : 120) : 32;
        FontRenderer font = mc.fontRenderer;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        int y = gui.getGuiTop();
        int[] backgrounds = new int[shown.size() * 3];
        List<String> tooltip = null;
        for (int i = 0; i < shown.size(); i++) {
            PotionEffect effect = shown.get(i);
            Potion potion = effect.getPotion();
            String name = name(effect);
            String duration = Potion.getPotionDurationString(effect, 1.0F);
            int width = fit ? Math.min(maxWidth, Math.max(32 + font.getStringWidth(name) + 7, 32 + font.getStringWidth(duration) + 7)) : maxWidth;
            background(mc, x, y, width, effect.getIsAmbient() && fit);
            backgrounds[i * 3] = x;
            backgrounds[i * 3 + 1] = y;
            backgrounds[i * 3 + 2] = width;
            boolean wide = !fit && width == 120;
            if (potion.hasStatusIcon()) {
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                mc.getTextureManager().bindTexture(INVENTORY);
                int index = potion.getStatusIconIndex();
                gui.drawTexturedModalRect(x + (wide ? 6 : 7), y + 7, index % 8 * 18, 198 + index / 8 * 18, 18, 18);
            }
            potion.renderInventoryEffect(effect, gui, x, y, 0.0F);
            boolean compact;
            if (fit) {
                int maxText = width - 32 - 7;
                if (maxText > 0 && potion.shouldRenderInvText(effect)) {
                    boolean clip = font.getStringWidth(name) > maxText;
                    font.drawStringWithShadow(clip ? font.trimStringToWidth(name, maxText) : name, x + 32, y + 7, 0xFFFFFF);
                    font.drawStringWithShadow(duration, x + 32, y + 7 + 9, 0x808080);
                    compact = clip;
                } else {
                    compact = maxText <= 0;
                }
            } else if (wide) {
                if (potion.shouldRenderInvText(effect)) {
                    // 1.18's renderLabels: name at x + 28, y + 6, time 10 below in 0x7F7F7F.
                    font.drawStringWithShadow(name, x + 10 + 18, y + 6, 0xFFFFFF);
                    font.drawStringWithShadow(duration, x + 10 + 18, y + 6 + 10, 0x7F7F7F);
                }
                compact = false;
            } else {
                compact = true;
            }
            if (compact && mouseX >= x && mouseX <= x + (fit ? width : 33) && mouseY >= y && mouseY <= y + step) {
                tooltip = Arrays.asList(name, duration);
            }
            y += step;
        }
        GlStateManager.enableDepth();
        lastBackgrounds = backgrounds;
        lastTooltip = tooltip;
        drawn++;
        if (tooltip != null) {
            gui.drawHoveringText(tooltip, mouseX, mouseY);
            GlStateManager.disableLighting();
        }
    }

    /** 1.12.2's name with 26.3's levels: II to X ({@code enchantment.level.2} to {@code .10}) for amplifiers 1 to 9. */
    public static String name(PotionEffect effect) {
        String name = I18n.format(effect.getPotion().getName());
        int amplifier = effect.getAmplifier();
        if (VanillaRevisions.effectsFitText() ? amplifier >= 1 && amplifier <= 9 : amplifier >= 1 && amplifier <= 3) {
            name = name + " " + I18n.format("enchantment.level." + (amplifier + 1));
        }
        return name;
    }

    /**
     * One effect's background, {@code width} by 32: the supplied sprite (1.21.11's nine-slice, or 1.20.2's large and
     * small ones), else {@code inventory.png}'s 32 high background sliced with 4 pixel edges.
     */
    private static void background(Minecraft mc, int x, int y, int width, boolean ambient) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        String nine = ambient && Presentation.asset(SPRITE_AMBIENT) != null ? SPRITE_AMBIENT : SPRITE;
        if (VanillaRevisions.effectsFitText() && Presentation.asset(nine) != null) {
            mc.getTextureManager().bindTexture(new ResourceLocation(nine));
            SystemToasts.nineSlice(width, 32, 0, 0, 32, 32, 32, 32, 4, 4, 4, 4);
        } else if (width == 120 && Presentation.asset(SPRITE_LARGE) != null) {
            mc.getTextureManager().bindTexture(new ResourceLocation(SPRITE_LARGE));
            SystemToasts.nineSlice(120, 32, 0, 0, 120, 32, 120, 32, 4, 4, 4, 4);
        } else if (width == 32 && Presentation.asset(SPRITE_SMALL) != null) {
            mc.getTextureManager().bindTexture(new ResourceLocation(SPRITE_SMALL));
            SystemToasts.nineSlice(32, 32, 0, 0, 32, 32, 32, 32, 4, 4, 4, 4);
        } else {
            mc.getTextureManager().bindTexture(INVENTORY);
            // 1.12.2's own inventory.png has a 140 wide background; the newer layers' are 120 wide, as vanilla's since 1.14.
            int source = Presentation.isAtLeast(LookArea.TEXTURES, "1.14") ? 120 : 140;
            SystemToasts.nineSlice(width, 32, 0, 166, source, 32, 256, 256, 4, 4, 4, 4);
        }
        GlStateManager.popMatrix();
    }

    /** Forge's handler; registered by Core's client init. */
    public static final class Events {
        /** From 1.18 the inventory no longer moves right for the effects (Forge's own way to keep it centered). */
        @SubscribeEvent
        public void onShift(GuiScreenEvent.PotionShiftEvent event) {
            if (VanillaRevisions.effectsBesideInventory()) {
                event.setCanceled(true);
            }
        }
    }
}

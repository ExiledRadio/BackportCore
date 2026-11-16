package com.exiledradio.backportcore.smithing.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.smithing.ArmorTrims;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.layers.LayerBipedArmor;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.registries.IRegistryDelegate;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The client half of {@link ArmorTrims}: trims drawn on worn armor and on item icons, and the tooltip lines.
 *
 * <ul>
 * <li><b>Worn</b>: every renderer with a biped armor layer (players, zombies, skeletons, armor stands, other mods'
 * bipeds) gets {@link TrimmedArmorLayer} in its place, which draws each piece as 1.20 does: armor, trim, then the
 * enchantment glint over both. A renderer whose armor layer is some mod's own subclass keeps it, and gets
 * {@link TrimLayer} after it (the trim is drawn over that layer's glint).</li>
 * <li><b>Icons</b>: armor items drawn in vanilla's shapes ({@link ArmorTrims#drawsOnIcon}) get a model that adds
 * the trim's item layer above their own, as 1.20's {@code layer1}/{@code layer2}; the four overlays in every
 * palette are built into the item atlas when it is stitched.</li>
 * <li><b>Tooltip</b>: "Upgrade:", then the pattern and the material in the material's color, after the item's own
 * lines and before its enchantments (1.20's order).</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class ArmorTrimsClient {

    private static boolean layersInstalled;

    private ArmorTrimsClient() {
    }

    /** From Core's client pre-init. */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new ArmorTrimsClient());
    }

    /** From Core's load-complete: every renderer exists by then (mods' included). */
    public static void loadComplete() {
        installLayers();
    }

    // ---------------------------------------------------------------------------------------------
    // Worn armor
    // ---------------------------------------------------------------------------------------------

    static void installLayers() {
        if (layersInstalled) {
            return;
        }
        layersInstalled = true;
        RenderManager manager = Minecraft.getMinecraft().getRenderManager();
        List<Render<?>> renderers = new ArrayList<Render<?>>(manager.entityRenderMap.values());
        renderers.addAll(manager.getSkinMap().values());
        int replaced = 0, added = 0;
        for (Render<?> r : renderers) {
            if (!(r instanceof RenderLivingBase)) {
                continue;
            }
            List<LayerRenderer<?>> layers = layers((RenderLivingBase<?>) r);
            if (layers == null) {
                continue;
            }
            for (int i = 0; i < layers.size(); i++) {
                LayerRenderer<?> layer = layers.get(i);
                if (!(layer instanceof LayerBipedArmor) || layer instanceof TrimmedArmorLayer) {
                    continue;
                }
                if (i + 1 < layers.size() && layers.get(i + 1) instanceof TrimLayer) {
                    continue;
                }
                LayerBipedArmor armor = (LayerBipedArmor) layer;
                if (TrimmedArmorLayer.canReplace(armor)) {
                    layers.set(i, new TrimmedArmorLayer((RenderLivingBase<?>) r, armor));
                    replaced++;
                } else {
                    layers.add(i + 1, new TrimLayer((RenderLivingBase<?>) r, armor));
                    added++;
                    i++;
                }
            }
        }
        BackportCore.LOGGER.info("Armor trims: {} armor layers replaced, {} trim layers added.", replaced, added);
    }

    @SuppressWarnings("unchecked")
    private static List<LayerRenderer<?>> layers(RenderLivingBase<?> renderer) {
        try {
            return (List<LayerRenderer<?>>) ObfuscationReflectionHelper.getPrivateValue(RenderLivingBase.class, renderer,
                    "field_177097_h");
        } catch (RuntimeException e) {
            BackportCore.LOGGER.warn("Armor trims: no layer list on {}", renderer.getClass().getName());
            return null;
        }
    }

    /** The texture for a trim on this piece, built on first use ({@link PalettedTexture}). */
    static ResourceLocation armorTexture(ArmorTrims.Trim trim, ItemStack armor, boolean leggings) {
        ResourceLocation palette = trim.material.paletteFor(armor);
        ResourceLocation pattern = trim.pattern.assetId;
        ResourceLocation id = new ResourceLocation(pattern.getNamespace(), "textures/trims/models/armor/"
                + pattern.getPath() + (leggings ? "_leggings" : "") + "_" + palette.getNamespace() + "_"
                + palette.getPath() + ".png");
        PalettedTexture.ensure(id, trim.pattern.texture(leggings), palette);
        return id;
    }

    // ---------------------------------------------------------------------------------------------
    // Icons
    // ---------------------------------------------------------------------------------------------

    /** helmet, chestplate, leggings, boots: the names of 1.20's {@code trims/items/<x>_trim}. */
    static String slotName(EntityEquipmentSlot slot) {
        switch (slot) {
            case HEAD:
                return "helmet";
            case CHEST:
                return "chestplate";
            case LEGS:
                return "leggings";
            default:
                return "boots";
        }
    }

    /** The item atlas sprite for one slot's overlay in one palette. */
    static ResourceLocation iconSprite(EntityEquipmentSlot slot, ResourceLocation palette) {
        return new ResourceLocation(BackportCore.MOD_ID, "trims/items/" + slotName(slot) + "_trim_"
                + palette.getNamespace() + "_" + palette.getPath());
    }

    @SubscribeEvent
    public void stitch(TextureStitchEvent.Pre event) {
        TextureMap map = event.getMap();
        if (map != Minecraft.getMinecraft().getTextureMapBlocks()) {
            return;
        }
        for (ResourceLocation palette : ArmorTrims.palettes()) {
            for (EntityEquipmentSlot slot : new EntityEquipmentSlot[] {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST,
                    EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}) {
                ResourceLocation name = iconSprite(slot, palette);
                ResourceLocation source = new ResourceLocation(BackportCore.MOD_ID, "textures/trims/items/" + slotName(slot) + "_trim.png");
                map.setTextureEntry(new PalettedSprite(name.toString(), source, palette));
            }
        }
    }

    @SubscribeEvent
    public void bake(ModelBakeEvent event) {
        Map<Pair<IRegistryDelegate<Item>, Integer>, ModelResourceLocation> custom = customModels();
        int wrapped = 0;
        for (Item item : ForgeRegistries.ITEMS) {
            if (!(item instanceof ItemArmor) || !ArmorTrims.drawsOnIcon(item)) {
                continue;
            }
            ModelResourceLocation mrl = custom == null ? null : custom.get(Pair.of(item.delegate, 0));
            if (mrl == null) {
                mrl = new ModelResourceLocation(item.getRegistryName(), "inventory");
            }
            // One model by its exact name: VintageFix bakes on demand, so never walk the registry's keys.
            IBakedModel model = event.getModelRegistry().getObject(mrl);
            if (model == null || model instanceof TrimmedItemModel || model.isBuiltInRenderer()) {
                continue;
            }
            event.getModelRegistry().putObject(mrl, new TrimmedItemModel(model, ((ItemArmor) item).armorType));
            wrapped++;
        }
        BackportCore.LOGGER.info("Armor trims: {} armor item models take trims.", wrapped);
    }

    @SuppressWarnings("unchecked")
    private static Map<Pair<IRegistryDelegate<Item>, Integer>, ModelResourceLocation> customModels() {
        try {
            return (Map<Pair<IRegistryDelegate<Item>, Integer>, ModelResourceLocation>)
                    ObfuscationReflectionHelper.getPrivateValue(ModelLoader.class, null, "customModels");
        } catch (RuntimeException e) {
            return null;
        }
    }

    static TextureAtlasSprite sprite(ResourceLocation name) {
        return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name.toString());
    }

    // ---------------------------------------------------------------------------------------------
    // Tooltip
    // ---------------------------------------------------------------------------------------------

    /** 1.20's {@code ArmorTrim.appendUpgradeHoverText}, at 1.20's place in the tooltip. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void tooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ArmorTrims.Trim trim = ArmorTrims.get(stack);
        if (trim == null || !ArmorTrims.showsInTooltip(stack)) {
            return;
        }
        List<String> list = event.getToolTip();
        int at = Math.min(list.size(), 1 + ownLines(event, stack));
        int color = trim.material.color;
        list.add(at, TextFormatting.GRAY + I18n.format("item.minecraft.smithing_template.upgrade"));
        list.add(at + 1, ColoredTooltipText.line(" " + I18n.format(trim.pattern.translationKey()), color));
        list.add(at + 2, ColoredTooltipText.line(" " + I18n.format(trim.material.translationKey()), color));
    }

    /** How many lines the item's own {@code addInformation} wrote, which 1.20 puts above the trim. */
    private static int ownLines(ItemTooltipEvent event, ItemStack stack) {
        int flags = stack.hasTagCompound() ? stack.getTagCompound().getInteger("HideFlags") : 0;
        if ((flags & 32) != 0) {
            return 0;
        }
        List<String> own = new ArrayList<String>();
        try {
            stack.getItem().addInformation(stack, event.getEntityPlayer() == null ? null : event.getEntityPlayer().world,
                    own, event.getFlags());
        } catch (RuntimeException e) {
            return 0;
        }
        return own.size();
    }

}

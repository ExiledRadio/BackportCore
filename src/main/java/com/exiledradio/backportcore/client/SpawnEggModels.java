package com.exiledradio.backportcore.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;

import org.apache.commons.lang3.tuple.Pair;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.creative.CreativePlacement;
import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;
import com.google.common.collect.ImmutableList;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ItemLayerModel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 1.21.5's spawn eggs: every egg its own texture ({@code item/<mob>_spawn_egg}) where 1.12.2 tints one egg in the mob's
 * two colors. From 1.21.5 ({@link #VERSION}, the textures part of the look), the modern pack's 1.21.5 layer serves the
 * textures (26.3's, from {@code tools/textures/eggs.py}) and {@code minecraft:spawn_egg}'s model picks the egg's own,
 * untinted, by the mob in the stack: a 1.12.2 mob by its name today, a family backport's mob by the vanilla egg its
 * creative placement names ({@link CreativePlacement#modernEggId}). Any other mob's egg, and every egg before 1.21.5 or
 * with the textures switch off, keeps 1.12.2's tinted model. No mesh definition changes: the egg's own model is
 * wrapped, so another mod's override of it still decides first.
 */
@SideOnly(Side.CLIENT)
public final class SpawnEggModels {

    /** The version that gave each egg its own texture. */
    public static final String VERSION = "1.21.5";

    static final ModelResourceLocation EGG = new ModelResourceLocation("minecraft:spawn_egg", "inventory");

    /** The mobs whose egg has a texture in this stitch, to that texture's sprite name. */
    private static Map<ResourceLocation, ResourceLocation> sprites = Collections.emptyMap();
    /** The same mobs to their baked egg models, built after the stitch. */
    private static Map<ResourceLocation, IBakedModel> models = Collections.emptyMap();

    private SpawnEggModels() {
    }

    /** From Core's client pre-init. */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(new SpawnEggModels());
    }

    /** @return whether eggs draw from their own textures now */
    public static boolean active() {
        return Presentation.isAtLeast(LookArea.TEXTURES, VERSION);
    }

    /** @return the sprite of a mob's own egg ({@code minecraft:item/fox_spawn_egg}), or null when it has none */
    @Nullable
    public static ResourceLocation spriteFor(ResourceLocation entity) {
        String id = CreativePlacement.modernEggId(entity);
        if (id == null) {
            return null;
        }
        ResourceLocation egg = new ResourceLocation(id);
        return new ResourceLocation(egg.getNamespace(), "item/" + egg.getPath());
    }

    /** @return the model a stack of {@code minecraft:spawn_egg} draws with now: its mob's own, or null for the tinted one */
    @Nullable
    public static IBakedModel modelFor(ItemStack stack) {
        if (stack.getItem() != Items.SPAWN_EGG || models.isEmpty() || !active()) {
            return null;
        }
        ResourceLocation entity = ItemMonsterPlacer.getNamedIdFrom(stack);
        return entity == null ? null : models.get(entity);
    }

    /** For the self-tests: the mobs whose egg has its own model now. */
    public static Map<ResourceLocation, ResourceLocation> textured() {
        return Collections.unmodifiableMap(sprites);
    }

    @SubscribeEvent
    public void stitch(TextureStitchEvent.Pre event) {
        TextureMap map = event.getMap();
        if (map != Minecraft.getMinecraft().getTextureMapBlocks()) {
            return;
        }
        Map<ResourceLocation, ResourceLocation> found = new HashMap<ResourceLocation, ResourceLocation>();
        if (active()) {
            for (ResourceLocation entity : EntityList.ENTITY_EGGS.keySet()) {
                ResourceLocation sprite = spriteFor(entity);
                if (sprite != null && exists(sprite)) {
                    map.registerSprite(sprite);
                    found.put(entity, sprite);
                }
            }
        }
        sprites = found;
        models = Collections.emptyMap();
    }

    private static boolean exists(ResourceLocation sprite) {
        ResourceLocation file = new ResourceLocation(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
        try (IResource ignored = Minecraft.getMinecraft().getResourceManager().getResource(file)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SubscribeEvent
    public void bake(ModelBakeEvent event) {
        Map<ResourceLocation, IBakedModel> baked = new HashMap<ResourceLocation, IBakedModel>();
        // One model by its exact name: VintageFix bakes on demand, so never walk the registry's keys.
        IBakedModel base = event.getModelRegistry().getObject(EGG);
        if (base == null || base.isBuiltInRenderer()) {
            models = baked;
            return;
        }
        if (!(base instanceof Wrapped)) {
            event.getModelRegistry().putObject(EGG, new Wrapped(base));
        } else {
            base = ((Wrapped) base).base;
        }
        TextureMap map = Minecraft.getMinecraft().getTextureMapBlocks();
        Map<ResourceLocation, IBakedModel> bySprite = new HashMap<ResourceLocation, IBakedModel>();
        for (Map.Entry<ResourceLocation, ResourceLocation> e : sprites.entrySet()) {
            IBakedModel m = bySprite.get(e.getValue());
            if (m == null) {
                TextureAtlasSprite sprite = map.getAtlasSprite(e.getValue().toString());
                // Tint -1: the egg's own colors, not 1.12.2's two tints (the spawn egg's item color handler).
                m = new Egg(base, sprite, ItemLayerModel.getQuadsForSprite(-1, sprite, DefaultVertexFormats.ITEM,
                        Optional.<TRSRTransformation>empty()));
                bySprite.put(e.getValue(), m);
            }
            baked.put(e.getKey(), m);
        }
        models = baked;
        BackportCore.LOGGER.info("Spawn eggs: {} eggs draw their own texture ({}).", baked.size(),
                active() ? VERSION + " look" : "before " + VERSION + ": all tinted");
    }

    /** {@code minecraft:spawn_egg}'s own model, with an override list that picks the mob's egg first. */
    static final class Wrapped implements IBakedModel {

        final IBakedModel base;
        private final ItemOverrideList overrides;

        Wrapped(IBakedModel base) {
            this.base = base;
            this.overrides = new ItemOverrideList(ImmutableList.of()) {
                @Override
                public IBakedModel handleItemState(IBakedModel original, ItemStack stack, @Nullable World world,
                        @Nullable EntityLivingBase entity) {
                    IBakedModel egg = modelFor(stack);
                    return egg != null ? egg : Wrapped.this.base.getOverrides().handleItemState(Wrapped.this.base, stack, world, entity);
                }
            };
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            return this.base.getQuads(state, side, rand);
        }

        @Override
        public boolean isAmbientOcclusion() {
            return this.base.isAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return this.base.isGui3d();
        }

        @Override
        public boolean isBuiltInRenderer() {
            return this.base.isBuiltInRenderer();
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return this.base.getParticleTexture();
        }

        @SuppressWarnings("deprecation")
        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return this.base.getItemCameraTransforms();
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            Pair<? extends IBakedModel, Matrix4f> p = this.base.handlePerspective(type);
            return Pair.of(p.getLeft() == this.base ? this : p.getLeft(), p.getRight());
        }

        @Override
        public ItemOverrideList getOverrides() {
            return this.overrides;
        }
    }

    /** One mob's egg: 1.21.5's {@code item/generated} of its texture, held as the spawn egg is held. */
    static final class Egg implements IBakedModel {

        private final IBakedModel base;
        private final TextureAtlasSprite sprite;
        private final List<BakedQuad> quads;

        Egg(IBakedModel base, TextureAtlasSprite sprite, List<BakedQuad> quads) {
            this.base = base;
            this.sprite = sprite;
            this.quads = ImmutableList.copyOf(quads);
        }

        /** For the self-tests: the texture it draws. */
        public TextureAtlasSprite sprite() {
            return this.sprite;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            return side == null ? this.quads : ImmutableList.<BakedQuad>of();
        }

        @Override
        public boolean isAmbientOcclusion() {
            return this.base.isAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean isBuiltInRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return this.sprite;
        }

        @SuppressWarnings("deprecation")
        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return this.base.getItemCameraTransforms();
        }

        @Override
        public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
            Pair<? extends IBakedModel, Matrix4f> p = this.base.handlePerspective(type);
            return Pair.of(this, p.getRight());
        }

        @Override
        public ItemOverrideList getOverrides() {
            return ItemOverrideList.NONE;
        }
    }
}

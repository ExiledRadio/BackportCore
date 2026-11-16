package com.exiledradio.backportcore.smithing.client;

import com.exiledradio.backportcore.smithing.ArmorTrims;

import com.google.common.collect.ImmutableList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ItemLayerModel;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An armor item's model that takes trims: untrimmed it is the item's own; trimmed, 1.20's {@code <item>_<material>_trim}
 * model, which is the same layers with the trim's item overlay above them ({@code layer1}, or {@code layer2} on
 * leather), untinted.
 */
@SideOnly(Side.CLIENT)
final class TrimmedItemModel implements IBakedModel {

    private final IBakedModel base;
    private final EntityEquipmentSlot slot;
    private final Overrides overrides;

    TrimmedItemModel(IBakedModel base, EntityEquipmentSlot slot) {
        this.base = base;
        this.slot = slot;
        this.overrides = new Overrides();
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

    /** Picks the base's own override first (as vanilla would), then adds the trim to whatever it picked. */
    private final class Overrides extends ItemOverrideList {

        /** (picked model, palette) to the trimmed model; few combinations, built on first use. */
        private final Map<Pair<IBakedModel, ResourceLocation>, IBakedModel> cache = new HashMap<Pair<IBakedModel, ResourceLocation>, IBakedModel>();

        Overrides() {
            super(ImmutableList.of());
        }

        @Override
        public IBakedModel handleItemState(IBakedModel original, ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity) {
            IBakedModel picked = TrimmedItemModel.this.base.getOverrides().handleItemState(TrimmedItemModel.this.base, stack, world, entity);
            ArmorTrims.Trim trim = ArmorTrims.get(stack);
            if (trim == null) {
                return picked;
            }
            ResourceLocation palette = trim.material.paletteFor(stack);
            Pair<IBakedModel, ResourceLocation> key = Pair.of(picked, palette);
            IBakedModel out = this.cache.get(key);
            if (out == null) {
                TextureAtlasSprite sprite = ArmorTrimsClient.sprite(ArmorTrimsClient.iconSprite(TrimmedItemModel.this.slot, palette));
                out = new Layered(picked, ItemLayerModel.getQuadsForSprite(-1, sprite, DefaultVertexFormats.ITEM,
                        Optional.<TRSRTransformation>empty()));
                this.cache.put(key, out);
            }
            return out;
        }
    }

    /** A model with more quads drawn after its own. */
    private static final class Layered implements IBakedModel {

        private final IBakedModel base;
        private final List<BakedQuad> quads;

        Layered(IBakedModel base, List<BakedQuad> extra) {
            this.base = base;
            List<BakedQuad> q = new ArrayList<BakedQuad>(base.getQuads(null, null, 0));
            q.addAll(extra);
            this.quads = ImmutableList.copyOf(q);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            return side == null ? this.quads : this.base.getQuads(state, side, rand);
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
            return false;
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
            return Pair.of(this, p.getRight());
        }

        @Override
        public ItemOverrideList getOverrides() {
            return ItemOverrideList.NONE;
        }
    }
}

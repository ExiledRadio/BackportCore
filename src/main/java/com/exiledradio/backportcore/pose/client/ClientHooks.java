package com.exiledradio.backportcore.pose.client;

import com.exiledradio.backportcore.pose.MovementHooks;
import com.exiledradio.backportcore.pose.Pose;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.MathHelper;

/**
 * Called from vanilla's client code by patches P2-P5 (docs/PATCHES.md). Outside a third-person draw of a
 * player in a pose, each does exactly nothing (or returns vanilla's own value).
 *
 * <p>Public only because patched vanilla code calls it; not for backports to call. The numbers are 1.14.4's
 * {@code BipedModel}, {@code PlayerRenderer} and {@code ActiveRenderInfo}.
 */
public final class ClientHooks {

    private static final float PI = (float) Math.PI;

    private ClientHooks() {
    }

    /** P2: {@code EntityPlayerSP.onLivingUpdate}, before {@code super.onLivingUpdate()}. */
    public static void beforeLivingUpdate(EntityPlayerSP player) {
        MovementHooks.beforeClientMove(player);
    }

    /** P5: {@code EntityRenderer.orientCamera}'s eye height. */
    public static float cameraEyeHeight(Entity entity, float partialTicks) {
        return PoseClient.cameraEyeHeight(entity, partialTicks);
    }

    /**
     * P3, first line of {@code ModelBiped.setRotationAngles}. Everything the two later hooks need is handed over
     * here: Mojang's shipped classes mark a parameter unusable once the original code stops reading it, so reading
     * limbSwing, headPitch or the entity further down fails verification in a real game (never in a dev run, which
     * recompiles Minecraft). The later hooks find this call's frame by the model.
     */
    public static void beforeAngles(ModelBiped model, Entity entity, float limbSwing, float headPitch) {
        PoseClient.Frame frame = PoseClient.frame(entity);
        if (frame == null) {
            PoseClient.Frame top = PoseClient.frameFor(model);
            if (top != null) {
                top.model = null; // this model is now posing something else
            }
            return;
        }
        frame.model = model;
        frame.limbSwing = limbSwing;
        frame.headPitch = headPitch;
        frame.prevHeadX = model.bipedHead.rotateAngleX;
        if (frame.pose == Pose.SWIMMING) {
            // 1.14 crouches the model by pose (SNEAKING), and the swimming pose wins over sneaking.
            model.isSneak = false;
        }
    }

    /** P3, after the head's angles: 1.14 blends the head from its last angle toward where a swimmer looks. */
    public static void afterHead(ModelBiped model) {
        PoseClient.Frame frame = PoseClient.frameFor(model);
        if (frame == null || frame.swim <= 0.0F || frame.player.getTicksElytraFlying() > 4) {
            return;
        }
        float target = frame.pose == Pose.SWIMMING ? -PI / 4.0F : frame.headPitch * (PI / 180.0F);
        model.bipedHead.rotateAngleX = rotLerp(frame.prevHeadX, target, frame.swim);
    }

    /** P3, before the head is copied to the headwear: backports' arm poses, then 1.14's swim stroke. */
    public static void afterAngles(ModelBiped model) {
        PoseClient.Frame frame = PoseClient.frameFor(model);
        if (frame == null) {
            return;
        }
        if (PoseClient.modelHooksWork()) {
            for (PlayerModelHook hook : PoseClient.modelHooks()) {
                hook.apply(model, frame.player);
            }
        }
        float swim = frame.swim;
        if (swim <= 0.0F) {
            return;
        }
        float f7 = frame.limbSwing % 26.0F;
        float f8 = model.swingProgress > 0.0F ? 0.0F : swim;
        if (f7 < 14.0F) {
            model.bipedLeftArm.rotateAngleX = rotLerp(model.bipedLeftArm.rotateAngleX, 0.0F, swim);
            model.bipedRightArm.rotateAngleX = lerp(f8, model.bipedRightArm.rotateAngleX, 0.0F);
            model.bipedLeftArm.rotateAngleY = rotLerp(model.bipedLeftArm.rotateAngleY, PI, swim);
            model.bipedRightArm.rotateAngleY = lerp(f8, model.bipedRightArm.rotateAngleY, PI);
            model.bipedLeftArm.rotateAngleZ = rotLerp(model.bipedLeftArm.rotateAngleZ, PI + 1.8707964F * stroke(f7) / stroke(14.0F), swim);
            model.bipedRightArm.rotateAngleZ = lerp(f8, model.bipedRightArm.rotateAngleZ, PI - 1.8707964F * stroke(f7) / stroke(14.0F));
        } else if (f7 < 22.0F) {
            float f10 = (f7 - 14.0F) / 8.0F;
            model.bipedLeftArm.rotateAngleX = rotLerp(model.bipedLeftArm.rotateAngleX, (PI / 2.0F) * f10, swim);
            model.bipedRightArm.rotateAngleX = lerp(f8, model.bipedRightArm.rotateAngleX, (PI / 2.0F) * f10);
            model.bipedLeftArm.rotateAngleY = rotLerp(model.bipedLeftArm.rotateAngleY, PI, swim);
            model.bipedRightArm.rotateAngleY = lerp(f8, model.bipedRightArm.rotateAngleY, PI);
            model.bipedLeftArm.rotateAngleZ = rotLerp(model.bipedLeftArm.rotateAngleZ, 5.012389F - 1.8707964F * f10, swim);
            model.bipedRightArm.rotateAngleZ = lerp(f8, model.bipedRightArm.rotateAngleZ, 1.2707963F + 1.8707964F * f10);
        } else if (f7 < 26.0F) {
            float f9 = (f7 - 22.0F) / 4.0F;
            model.bipedLeftArm.rotateAngleX = rotLerp(model.bipedLeftArm.rotateAngleX, (PI / 2.0F) - (PI / 2.0F) * f9, swim);
            model.bipedRightArm.rotateAngleX = lerp(f8, model.bipedRightArm.rotateAngleX, (PI / 2.0F) - (PI / 2.0F) * f9);
            model.bipedLeftArm.rotateAngleY = rotLerp(model.bipedLeftArm.rotateAngleY, PI, swim);
            model.bipedRightArm.rotateAngleY = lerp(f8, model.bipedRightArm.rotateAngleY, PI);
            model.bipedLeftArm.rotateAngleZ = rotLerp(model.bipedLeftArm.rotateAngleZ, PI, swim);
            model.bipedRightArm.rotateAngleZ = lerp(f8, model.bipedRightArm.rotateAngleZ, PI);
        }
        model.bipedLeftLeg.rotateAngleX = lerp(swim, model.bipedLeftLeg.rotateAngleX, 0.3F * MathHelper.cos(frame.limbSwing * 0.33333334F + PI));
        model.bipedRightLeg.rotateAngleX = lerp(swim, model.bipedRightLeg.rotateAngleX, 0.3F * MathHelper.cos(frame.limbSwing * 0.33333334F));
    }

    /**
     * P4, in {@code RenderPlayer.applyRotations} after the body's yaw: 1.14's {@code PlayerRenderer} lays the
     * body along the look in water and flat out of it, blended by the swim amount.
     */
    public static void applyRotations(AbstractClientPlayer player, float partialTicks) {
        PoseClient.Frame frame = PoseClient.frame(player);
        if (frame == null) {
            return;
        }
        boolean swimPose = frame.pose == Pose.SWIMMING;
        boolean crouched = swimPose && player.isSneaking();
        if (crouched) {
            // RenderPlayer lowered a sneaking player by 0.125 before this; 1.14 does not in the swimming pose.
            GlStateManager.translate(0.0F, 0.125F, 0.0F);
        }
        if (frame.swim > 0.0F) {
            float turn = player.isInWater() ? -90.0F - player.rotationPitch : -90.0F;
            GlStateManager.rotate(frame.swim * turn, 1.0F, 0.0F, 0.0F);
            if (swimPose) {
                GlStateManager.translate(0.0F, -1.0F, 0.3F);
            }
        }
        if (crouched) {
            // Every model part and layer then moves a sneaking player down 0.2 model units (x 0.9375 scale).
            GlStateManager.translate(0.0F, 0.1875F, 0.0F);
        }
    }

    /** 1.14's {@code MathHelper.lerp}. */
    static float lerp(float pct, float start, float end) {
        return start + pct * (end - start);
    }

    /** 1.14's {@code BipedModel.func_205060_a}: the shorter way round between two angles in radians. */
    static float rotLerp(float from, float to, float pct) {
        float f = (to - from) % (PI * 2.0F);
        if (f < -PI) {
            f += PI * 2.0F;
        }
        if (f >= PI) {
            f -= PI * 2.0F;
        }
        return from + pct * f;
    }

    /** 1.14's {@code BipedModel.func_203068_a}. */
    static float stroke(float x) {
        return -65.0F * x + x * x;
    }
}

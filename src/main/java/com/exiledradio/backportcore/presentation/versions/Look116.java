package com.exiledradio.backportcore.presentation.versions;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.SliderOption;
import net.minecraft.util.ResourceLocation;

/**
 * 1.16's look and feel that Backport Core owns since 1.4.7 (moved out of Nether Update Backport): the pieces that apply whichever
 * backport is the newest installed one, as long as it is 1.16 or later ({@code Presentation.core}).
 * Both sides, plain data; the client code is {@code client.Look116Client}.
 *
 * <ul>
 *   <li>the splash changes between 1.15.2's and 1.16.5's {@code splashes.txt} (41 added, 14 removed);</li>
 *   <li>Entity Distance (20w17a) in Video Settings, Distortion Effects and FOV Effects (1.16.2-pre1) in Video and
 *       Accessibility Settings, with patches P70 (the portal and nausea wobble) and P71 (the entity render
 *       distance);</li>
 *   <li>from 1.16.5's jar, as single slots: the title logo, the Mojang Studios startup logo (20w21a), the button and
 *       check box textures, the title screen's Accessibility icon, the multiplayer warning's wording and vanilla's
 *       own text as 1.16.5 words it ({@code look/1.16/vanilla_text/});</li>
 *   <li>client side ({@code client.Look116Client}): F3 + F4's game mode switcher, F3 + N's spectator and previous
 *       mode, the F3 text's spawn counts and targeted block coordinates, the green nausea overlay, FOV Effects and
 *       Singleplayer opening Create New World when there is no world.</li>
 * </ul>
 * The option ids stay Nether Update Backport's, so saved values carry over. The panorama, its overlay and the music stay
 * in Nether Update Backport, which still declares 1.16, 1.16.2 and 1.16.4.
 */
public final class Look116 {

    private static final String ID = "netherupdatebackport";

    /** 1.16's Entity Distance, 50% to 500% in steps of 25%: scales how far away entities are drawn. */
    public static final SliderOption ENTITY_DISTANCE = new SliderOption(ID + ":entity_distance_scaling",
            "options.entityDistanceScaling", OptionScreen.VIDEO, 0.5, 5.0, 0.25, 1.0);

    /**
     * 1.16.2's Distortion Effects (Accessibility Settings), 0 to 100%: scales the nether portal and nausea wobble
     * by its square; below 100% a green nausea overlay shows instead, at the strength the wobble lost.
     */
    public static final SliderOption DISTORTION = new SliderOption(ID + ":screen_effect_scale",
            "options.screenEffectScale", OptionScreen.ACCESSIBILITY, 0.0, 1.0, 0.0, 1.0);

    /**
     * 1.16.2's FOV Effects (Accessibility Settings), 0 to 100%. As vanilla's, the slider shows the square of the
     * scale it applies: the field of view's changes from speed, sprinting and bows are scaled by the root of the
     * slider's value ({@link #fovEffectScale()}).
     */
    public static final SliderOption FOV_EFFECTS = new SliderOption(ID + ":fov_effect_scale",
            "options.fovEffectScale", OptionScreen.ACCESSIBILITY, 0.0, 1.0, 0.0, 1.0);

    private Look116() {
    }

    static ResourceLocation rl(String path) {
        return new ResourceLocation("backportcore", "look/1.16/" + path);
    }

    /** From Backport Core's construction, both sides, before any backport registers. */
    static void register() {
        Contribution v116 = Presentation.core("1.16");
        v116.addSplashes(rl("texts/splashes_added.txt"))
                .removeSplashes(rl("texts/splashes_removed.txt"))
                .titleLogo(rl("textures/gui/title/minecraft.png"))
                .startupLogo("textures/gui/title/mojangstudios.png", rl("textures/gui/title/mojangstudios.png"))
                .replaceAsset("textures/gui/widgets.png", rl("textures/gui/widgets.png"))
                .replaceAsset("textures/gui/checkbox.png", rl("textures/gui/checkbox.png"))
                .titleAccessibilityIcon(rl("textures/gui/accessibility.png"))
                .multiplayerWarning("multiplayerWarning.header", "multiplayerWarning.message", "multiplayerWarning.check")
                .vanillaText(rl("vanilla_text"));
        // Video Settings in 1.16.5: ... Entity Shadows, Distortion Effects, Entity Distance, FOV Effects.
        v116.option(ENTITY_DISTANCE.after(DISTORTION.id()));

        // 1.16.2-pre1: both on Accessibility Settings (where 1.16.5's list ends with them) and on Video Settings.
        // At zero they read OFF (1.16.5's genericValueLabel(OPTION_OFF)); each has its tooltip.
        DISTORTION.zeroKey("options.off");
        DISTORTION.tooltip("options.screenEffectScale.tooltip");
        DISTORTION.alsoOn(OptionScreen.VIDEO);
        FOV_EFFECTS.zeroKey("options.off");
        FOV_EFFECTS.tooltip("options.fovEffectScale.tooltip");
        FOV_EFFECTS.alsoOn(OptionScreen.VIDEO);
        Presentation.core("1.16.2")
                .option(DISTORTION.after("ENTITY_SHADOWS"))
                .option(FOV_EFFECTS.after(ENTITY_DISTANCE.id()));
    }

    /** @return the scale 1.16.2 applies to field-of-view changes: the root of the FOV Effects slider */
    public static float fovEffectScale() {
        // A hidden option (before 1.16.2, or Core's Look & Feel page turned its part off) leaves 1.12.2's full effect.
        return FOV_EFFECTS.isShown() ? (float) Math.sqrt(FOV_EFFECTS.get()) : 1.0F;
    }

    /** @return Distortion Effects, 0 to 1 */
    public static float screenEffectScale() {
        return DISTORTION.isShown() ? (float) DISTORTION.get() : 1.0F;
    }

    // ---- What patches P70 and P71 call (docs/PATCHES.md): primitives only, so this class loads anywhere ----

    /**
     * P70, {@code EntityRenderer.setupCameraTransform}: the portal and nausea wobble's strength. 1.16.2's
     * {@code GameRenderer.renderLevel} multiplies it by Distortion Effects twice.
     */
    public static float portalWobble(float strength) {
        float scale = screenEffectScale();
        return strength * scale * scale;
    }

    /**
     * P71, {@code RenderGlobal.setupTerrain}: the entity render distance weight. 1.16's
     * {@code LevelRenderer.renderLevel} multiplies vanilla's clamp(render distance / 8, 1, 2.5) by Entity Distance.
     */
    public static double entityDistance(double weight) {
        return ENTITY_DISTANCE.isShown() ? weight * ENTITY_DISTANCE.get() : weight;
    }
}

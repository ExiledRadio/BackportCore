package com.exiledradio.backportcore.presentation.versions;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.ToggleOption;
import net.minecraft.util.ResourceLocation;

/**
 * 1.17 and 1.18's look and feel that Backport Core owns since 1.4.7 (moved out of Caves & Cliffs Backport): the pieces that apply whichever
 * backport is the newest installed one, as long as it is 1.17 or later ({@code Presentation.core}).
 * Both sides, plain data; the client code is {@code client.Look118Client}.
 *
 * <ul>
 *   <li>1.17 (21w15a): the splash changes between 1.16.5's and 1.17.1's {@code splashes.txt} (13 added, 3 removed:
 *       "Woo, /v/!" and two that name a Java or OpenGL version, which Core ignores);</li>
 *   <li>1.18: 1.18.2's splash changes, Hide Lightning Flashes (Accessibility, 21w37a), Autosave Indicator (Video,
 *       21w42a), the brightness default of 50 (21w39a), and from 1.18.2's jar the title logo, the Mojang Studios
 *       startup logo, the button and check box textures, the title screen's Accessibility icon and the multiplayer
 *       warning (all the same files as 1.16's, so they are shared) and vanilla's own text as 1.18.2 words it
 *       ({@code look/1.18/vanilla_text/});</li>
 *   <li>client side ({@code client.Look118Client}): the clouds at 192 and the lightning flashes (1.18), the fog
 *       starting farther out (1.18.1), the F3 Block and Chunk rows (1.18.2) and the autosave indicator.</li>
 * </ul>
 * The option ids stay Caves &amp; Cliffs Backport's, so saved values carry over. The panoramas, their overlay and the
 * music stay in Caves &amp; Cliffs Backport, which still declares 1.17, 1.18, 1.18.1 and 1.18.2.
 */
public final class Look118 {

    private static final String ID = "cavesandcliffsbackport";

    /** 1.18's Hide Lightning Flashes (Accessibility Settings, 21w37a): lightning no longer flashes the sky. */
    public static final ToggleOption HIDE_LIGHTNING_FLASHES = new ToggleOption(ID + ":hide_lightning_flashes",
            "options.hideLightningFlashes", OptionScreen.ACCESSIBILITY, false);

    /** 1.18's Autosave Indicator (Video Settings, 21w42a): "Saving world" at the bottom right while a save runs. */
    public static final ToggleOption AUTOSAVE_INDICATOR = new ToggleOption(ID + ":autosave_indicator",
            "options.autosaveIndicator", OptionScreen.VIDEO, true);

    /** 1.12.2's overworld cloud height ({@code WorldProvider.getCloudHeight}). */
    public static final float CLOUDS_1_12 = 128.0F;
    /** 1.18's ({@code DimensionSpecialEffects.OverworldEffects}, {@code CLOUD_LEVEL}). */
    public static final float CLOUDS_1_18 = 192.0F;

    private Look118() {
    }

    private static ResourceLocation rl(String path) {
        return new ResourceLocation("backportcore", "look/1.18/" + path);
    }

    /** From Backport Core's construction, both sides, before any backport registers. */
    static void register() {
        Presentation.core("1.17")
                .addSplashes(rl("texts/splashes_added_1_17.txt"))
                .removeSplashes(rl("texts/splashes_removed_1_17.txt"));

        Contribution v118 = Presentation.core("1.18");
        // The single slots as 1.18.2's jar has them; every image is the same file as 1.16.5's, so 1.16's copy serves.
        v118.addSplashes(rl("texts/splashes_added_1_18.txt"))
                .removeSplashes(rl("texts/splashes_removed_1_18.txt"))
                .titleLogo(Look116.rl("textures/gui/title/minecraft.png"))
                .startupLogo("textures/gui/title/mojangstudios.png", Look116.rl("textures/gui/title/mojangstudios.png"))
                .replaceAsset("textures/gui/widgets.png", Look116.rl("textures/gui/widgets.png"))
                .replaceAsset("textures/gui/checkbox.png", Look116.rl("textures/gui/checkbox.png"))
                .titleAccessibilityIcon(Look116.rl("textures/gui/accessibility.png"))
                .multiplayerWarning("multiplayerWarning.header", "multiplayerWarning.message", "multiplayerWarning.check")
                .vanillaText(rl("vanilla_text"))
                // 21w39a: "Moody" (0) is no longer the default brightness; 50 is (options.txt "gamma:0.5").
                .gameOptionDefault("gamma", "0.5");
        // 1.18.2's Accessibility Settings end: ... FOV Effects, Monochrome Logo, Hide Lightning Flashes.
        HIDE_LIGHTNING_FLASHES.tooltip("options.hideLightningFlashes.tooltip");
        v118.option(HIDE_LIGHTNING_FLASHES.after(Presentation.MONOCHROME_LOGO.id()));
        // 1.18.2's Video Settings end with it (after FOV Effects): no anchor, so it goes last.
        v118.option(AUTOSAVE_INDICATOR);
        // 1.18.1's fog and 1.18.2's F3 rows are client steps (client.Look118Client).
    }

    // ---- The numbers of the client steps, as plain functions (no client types), for the server self-tests ----

    /**
     * 1.18's cloud height: 192 in the overworld (dimension 0) where the provider kept 1.12.2's 128. Another
     * dimension, or a provider (or an earlier step) that chose its own height, keeps it.
     */
    public static float cloudHeight(int dimension, float height) {
        return dimension == 0 && height == CLOUDS_1_12 ? CLOUDS_1_18 : height;
    }

    /**
     * 1.18.1's {@code FogRenderer.setupFog}, terrain fog in clear air: it starts {@code clamp(far / 10, 4, 64)}
     * blocks before the render distance (1.12.2's started at 75% of it). {@code far} is the render distance in
     * blocks; 1.18.1 passes at least 32.
     */
    public static float terrainFogStart(float far) {
        float f = Math.max(far, 32.0F);
        return f - Math.min(Math.max(f / 10.0F, 4.0F), 64.0F);
    }

    /** @return the end of 1.18.1's terrain fog: the render distance (at least 32) */
    public static float terrainFogEnd(float far) {
        return Math.max(far, 32.0F);
    }

    /**
     * Hide Lightning Flashes: undoes 1.12.2's {@code World.getSkyColorBody} lightning blend
     * ({@code c * (1 - f) + target * f}, {@code f = min(bolt - partialTicks, 1) * 0.45}), as 1.18's
     * {@code ClientLevel.getSkyColor} skips it. {@code rgb} is changed in place.
     */
    public static void undoLightningFlash(double[] rgb, int lastLightningBolt, float partialTicks) {
        if (lastLightningBolt <= 0) {
            return;
        }
        float f = (float) lastLightningBolt - partialTicks;
        if (f > 1.0F) {
            f = 1.0F;
        }
        f *= 0.45F;
        if (f <= 0.0F) {
            return;
        }
        double[] target = {0.8, 0.8, 1.0};
        for (int i = 0; i < 3; i++) {
            rgb[i] = (rgb[i] - target[i] * f) / (1.0 - f);
        }
    }

    /** 1.18.2's F3 Block row: {@code Block: x y z [x&15 y&15 z&15]}. */
    public static String blockRow(int x, int y, int z) {
        return String.format("Block: %d %d %d [%d %d %d]", x, y, z, x & 15, y & 15, z & 15);
    }

    /** 1.18.2's F3 Chunk row: {@code Chunk: cx cy cz [cx&31 cz&31 in r.rx.rz.mca]}. */
    public static String chunkRow(int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        return String.format("Chunk: %d %d %d [%d %d in r.%d.%d.mca]", cx, y >> 4, cz, cx & 31, cz & 31, cx >> 5, cz >> 5);
    }

    /** 1.18's autosave indicator: {@code Mth.lerp(0.2, value, saving ? 1 : 0)} each client tick. */
    public static float autosaveStep(float value, boolean saving) {
        return value + 0.2F * ((saving ? 1.0F : 0.0F) - value);
    }

    /** @return the indicator's alpha, 0 to 255 ({@code Gui.renderSavingIndicator}); drawn only above 8 */
    public static int autosaveAlpha(float last, float value, float partialTicks) {
        float v = last + partialTicks * (value - last);
        return (int) Math.floor(255.0F * Math.min(Math.max(v, 0.0F), 1.0F));
    }
}

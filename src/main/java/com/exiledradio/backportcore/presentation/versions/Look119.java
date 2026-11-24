package com.exiledradio.backportcore.presentation.versions;

import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.SliderOption;
import net.minecraft.util.ResourceLocation;

/**
 * 1.19's look and feel that Backport Core owns since 1.4.7 (moved out of Wild Update Backport): the pieces that apply whichever
 * backport is the newest installed one, as long as it is 1.19 or later ({@code Presentation.core}).
 * Both sides, plain data; the client code is {@code client.Look119Client}.
 *
 * <ul>
 *   <li>1.19 (22w15a): the title logo, the Mojang Studios logo, the button and check box textures and the title screen's
 *       Accessibility icon as 1.19.4's jar has them (unchanged since 1.16); the multiplayer warning; 1.19's splashes (seven
 *       added, one removed); vanilla's own text as 1.19.4 words it; F3 + F (cycle render distance) removed (22w11a); the F3
 *       screen's "Allocation rate" line (client);</li>
 *   <li>1.19.1's splash and 1.19.3's six; 1.19.3's creative tab sprites ({@code tabs.png}, seven 26-pixel tabs a row,
 *       drawn by Core's {@code creative.client.ModernTabs}, patch P47);</li>
 *   <li>1.19.4: Auto-Jump off by default (23w03a, once per instance); Damage Tilt (23w06a) and Glint Speed and Glint
 *       Strength (23w05a, also on Video Settings), through patches P73 to P75 ({@code docs/PATCHES.md}).</li>
 * </ul>
 * Wild Update Backport keeps the swamp panorama, its menu music and Darkness Pulsing (its warden's Darkness).
 */
public final class Look119 {

    /** The options keep Wild Update's ids, so their saved values carry over. */
    private static final String WU = "wildupdatebackport";
    private static final String DIR = "look/1.19/";

    /** LWJGL 2's {@code Keyboard.KEY_F}, written as a number so this class loads on a dedicated server. */
    static final int KEY_F = 33;

    /** Wild Update's Darkness Pulsing, which stays there: Damage Tilt follows it on Accessibility Settings. */
    public static final String DARKNESS_ID = WU + ":darkness_effect_scale";

    /** 1.19.4's Damage Tilt (Accessibility Settings), 0 to 100%: scales the camera's tilt when the player is hurt (P73). */
    public static final SliderOption DAMAGE_TILT = new SliderOption(WU + ":damage_tilt_strength",
            "options.damageTiltStrength", OptionScreen.ACCESSIBILITY, 0.0, 1.0, 0.0, 1.0);

    /**
     * 1.19.4's Glint Speed (Accessibility and Video Settings), 0 to 100%, default 50%. 1.19.4's glint moves with
     * {@code millis * glintSpeed * 8} where 1.19.3's moved with {@code millis * 8}: at the default the glint runs at
     * half the earlier speed, as in 1.19.4 (P74, P75).
     */
    public static final SliderOption GLINT_SPEED = new SliderOption(WU + ":glint_speed",
            "options.glintSpeed", OptionScreen.ACCESSIBILITY, 0.0, 1.0, 0.0, 0.5);

    /**
     * 1.19.4's Glint Strength (Accessibility and Video Settings), 0 to 100%, default 75%: the glint shader's
     * {@code GlintAlpha}, which scales the added glint color (P74, P75).
     */
    public static final SliderOption GLINT_STRENGTH = new SliderOption(WU + ":glint_strength",
            "options.glintStrength", OptionScreen.ACCESSIBILITY, 0.0, 1.0, 0.0, 0.75);

    /** 1.19.3's creative tab sprites, by vanilla's path. */
    static final String CREATIVE_TABS = "textures/gui/container/creative_inventory/tabs.png";

    private Look119() {
    }

    private static ResourceLocation rl(String path) {
        return new ResourceLocation("backportcore", path);
    }

    /** From Backport Core's construction, both sides, before any backport registers. */
    static void register() {
        for (SliderOption o : new SliderOption[] {DAMAGE_TILT, GLINT_SPEED, GLINT_STRENGTH}) {
            o.zeroKey("options.off");
        }
        DAMAGE_TILT.tooltip("options.damageTiltStrength.tooltip");
        GLINT_SPEED.tooltip("options.glintSpeed.tooltip");
        GLINT_STRENGTH.tooltip("options.glintStrength.tooltip");

        Presentation.core("1.19")
                .addSplashes(rl(DIR + "texts/splashes_added.txt"))
                .removeSplashes(rl(DIR + "texts/splashes_removed.txt"))
                .titleLogo(rl(DIR + "textures/gui/title/minecraft.png"))
                .startupLogo("textures/gui/title/mojangstudios.png", rl(DIR + "textures/gui/title/mojangstudios.png"))
                .replaceAsset("textures/gui/widgets.png", rl(DIR + "textures/gui/widgets.png"))
                .replaceAsset("textures/gui/checkbox.png", rl(DIR + "textures/gui/checkbox.png"))
                .titleAccessibilityIcon(rl(DIR + "textures/gui/accessibility.png"))
                .multiplayerWarning("multiplayerWarning.header", "multiplayerWarning.message", "multiplayerWarning.check")
                .vanillaText(rl(DIR + "vanilla_text"))
                .removeDebugKey(KEY_F);
        Presentation.core("1.19.1").addSplashes(rl("look/1.19.1/texts/splashes_added.txt"));
        // 1.19.3: Core lays the creative tabs out 26 wide (P47), so these sprites always fit.
        Presentation.core("1.19.3").addSplashes(rl("look/1.19.3/texts/splashes_added.txt"))
                .replaceAsset(CREATIVE_TABS, rl("look/1.19.3/" + CREATIVE_TABS));

        // 1.19.4's Accessibility Settings: ... Darkness Pulsing, Damage Tilt, Glint Speed, Glint Strength ... Darkness
        // Pulsing is Wild Update's; without it the three go to the end of the list, in this order. Video Settings ends
        // ... Autosave Indicator, Glint Speed, Glint Strength: 1.12.2 has no autosave indicator, so the two go to the end.
        GLINT_SPEED.alsoOn(OptionScreen.VIDEO);
        GLINT_STRENGTH.alsoOn(OptionScreen.VIDEO);
        Presentation.core("1.19.4")
                .gameOptionDefault("autoJump", "false")
                .option(DAMAGE_TILT.after(DARKNESS_ID))
                .option(GLINT_SPEED.after(DAMAGE_TILT.id()))
                .option(GLINT_STRENGTH.after(GLINT_SPEED.id()));
    }
}

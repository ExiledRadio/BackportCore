package com.exiledradio.backportcore.presentation.versions;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.SliderOption;
import com.exiledradio.backportcore.presentation.option.ToggleOption;
import net.minecraft.util.ResourceLocation;

/**
 * 1.14's look and feel that Backport Core owns since 1.4.7 (moved out of Village & Pillage Backport (villagerbackport)): the pieces that apply whichever
 * backport is the newest installed one, as long as it is 1.14 or later ({@code Presentation.core}).
 * Both sides, plain data; the client code is {@code client.Look114Client}.
 *
 * <ul>
 *   <li>the splash changes between 1.13.2's and 1.14.4's {@code splashes.txt} (16 added, 5 removed), plus 1.14's
 *       "&lt;name&gt; IS YOU";</li>
 *   <li>Mouse Settings' Scroll Sensitivity and Discrete Scrolling (1.14-pre2), which the hotbar scroll follows
 *       ({@link ScrollAccumulator});</li>
 *   <li>from 1.14.4's jar: the title screen's Accessibility icon (19w13a) and the startup screen's {@code mojang.png}
 *       (19w06a), and vanilla's own text as 1.14.4 words it ({@code look/1.14/vanilla_text});</li>
 *   <li>client side: 1.14.1's F3 + N and F3 + Esc, 1.14.2's F3 server chunk line.</li>
 * </ul>
 * The ids stay Village &amp; Pillage's ({@code villagerbackport:mouse_wheel_sensitivity}), so saved values carry over.
 * The village panorama and the declarations of 1.14, 1.14.1 and 1.14.2 stay in Village &amp; Pillage.
 */
public final class Look114 {

    private static final String VP = "villagerbackport";

    /** 1.14's Scroll Sensitivity (Mouse Settings), 0.01 to 10: how far one wheel notch scrolls the hotbar. */
    public static final SliderOption SCROLL_SENSITIVITY = new SliderOption(VP + ":mouse_wheel_sensitivity",
            "options.mouseWheelSensitivity", OptionScreen.MOUSE, 0.01, 10.0, 0.01, 1.0).decimals(2);

    /** 1.14's Discrete Scrolling (Mouse Settings): each wheel event counts as one notch, whatever its size. */
    public static final ToggleOption DISCRETE_SCROLLING = new ToggleOption(VP + ":discrete_mouse_scroll",
            "options.discrete_mouse_scroll", OptionScreen.MOUSE, false);

    private Look114() {
    }

    /** From Backport Core's construction, both sides, before any backport registers. */
    static void register() {
        Contribution v114 = Presentation.core("1.14");
        v114.addSplashes(new ResourceLocation("backportcore", "look/1.14/texts/splashes_added.txt"))
                .removeSplashes(new ResourceLocation("backportcore", "look/1.14/texts/splashes_removed.txt"))
                .addSplashes("{PLAYER} IS YOU")
                // Mouse Settings in 1.14.4: Sensitivity, Invert Mouse, Scroll Sensitivity, Discrete Scrolling, Touchscreen.
                .option(SCROLL_SENSITIVITY.after("INVERT_MOUSE"))
                .option(DISCRETE_SCROLLING.after(SCROLL_SENSITIVITY.id()))
                .titleAccessibilityIcon(new ResourceLocation("backportcore", "look/1.14/textures/gui/accessibility.png"))
                .startupLogo("textures/gui/title/mojang.png", new ResourceLocation("backportcore", "look/1.14/textures/gui/title/mojang.png"))
                .vanillaText(new ResourceLocation("backportcore", "look/1.14/vanilla_text"));
        // 1.14.1's F3 + N and F3 + Esc and 1.14.2's F3 line are client code (client.Look114Client).
    }
}

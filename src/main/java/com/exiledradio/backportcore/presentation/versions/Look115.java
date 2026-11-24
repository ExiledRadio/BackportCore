package com.exiledradio.backportcore.presentation.versions;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.CycleOption;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import net.minecraft.util.ResourceLocation;

/**
 * 1.15's look and feel that Backport Core owns since 1.4.7 (moved out of Buzzy Bees Backport): the pieces that apply whichever
 * backport is the newest installed one, as long as it is 1.15 or later ({@code Presentation.core}).
 * Both sides, plain data; the client code is {@code client.Look115Client}.
 *
 * <ul>
 *   <li>the three splashes 1.15.2's {@code splashes.txt} adds to 1.14.4's (none removed);</li>
 *   <li>19w41a: Sneak and Sprint, Hold or Toggle, on Accessibility Settings after Auto-Jump ({@link #TOGGLE_SNEAK},
 *       {@link #TOGGLE_SPRINT}; the toggle is {@code client.SneakSprintToggles});</li>
 *   <li>from 1.15.2's jar: {@code widgets.png} (1.15's white button highlight), the startup screen's
 *       {@code mojang.png} (the same file as 1.14's), the title screen's Accessibility icon, and from 1.15.2 the check
 *       box and the multiplayer warning ("Online play is not rated");</li>
 *   <li>vanilla's own text as 1.15.2 words it ({@code look/1.15/vanilla_text});</li>
 *   <li>client side: 1.15.2's window title, 19w38a's F3 text ("chunk updates" gone).</li>
 * </ul>
 * The ids stay Buzzy Bees' ({@code buzzybeesbackport:toggle_crouch}), so saved values carry over. The bee panorama
 * stays in Buzzy Bees.
 */
public final class Look115 {

    private static final String BB = "buzzybeesbackport";
    private static final String[] NAMES = {"hold", "toggle"};
    private static final String[] VALUE_KEYS = {"options.key.hold", "options.key.toggle"};

    /** 1.15's "Sneak: Hold / Toggle" (Accessibility Settings). */
    public static final CycleOption TOGGLE_SNEAK = new CycleOption(BB + ":toggle_crouch", "key.sneak", OptionScreen.ACCESSIBILITY,
            NAMES, VALUE_KEYS, "hold");

    /** 1.15's "Sprint: Hold / Toggle" (Accessibility Settings). */
    public static final CycleOption TOGGLE_SPRINT = new CycleOption(BB + ":toggle_sprint", "key.sprint", OptionScreen.ACCESSIBILITY,
            NAMES, VALUE_KEYS, "hold");

    private Look115() {
    }

    /** From Backport Core's construction, both sides, before any backport registers. */
    static void register() {
        Contribution v115 = Presentation.core("1.15");
        v115.addSplashes("In case it isn't obvious, foxes aren't players.", "Buzzy Bees!",
                        "Minecraft Java Edition presents: Disgusting Bugs")
                // Accessibility Settings in 1.15.2: ... Chat Text Opacity, Auto-Jump, Sneak, Sprint.
                .option(TOGGLE_SNEAK.after("AUTO_JUMP"))
                .option(TOGGLE_SPRINT.after(TOGGLE_SNEAK.id()))
                .replaceAsset("textures/gui/widgets.png", new ResourceLocation("backportcore", "look/1.15/textures/gui/widgets.png"))
                // 1.15.2's mojang.png is 1.14.4's, byte for byte.
                .startupLogo("textures/gui/title/mojang.png", new ResourceLocation("backportcore", "look/1.14/textures/gui/title/mojang.png"))
                .titleAccessibilityIcon(new ResourceLocation("backportcore", "look/1.15/textures/gui/accessibility.png"))
                .vanillaText(new ResourceLocation("backportcore", "look/1.15/vanilla_text"));
        // 1.15.2's window title is client code (client.Look115Client).
        Presentation.core("1.15.2")
                .replaceAsset("textures/gui/checkbox.png", new ResourceLocation("backportcore", "look/1.15/textures/gui/checkbox.png"))
                .multiplayerWarning("multiplayerWarning.header", "multiplayerWarning.message", "multiplayerWarning.check");
    }

    /** @return true while Sneak (or Sprint) is set to Toggle and shows (a 1.15 backport, Accessibility's look on) */
    public static boolean toggles(CycleOption option) {
        return option.isShown() && "toggle".equals(option.get());
    }
}

package com.exiledradio.backportcore.presentation.versions;

import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.ToggleOption;

/**
 * 26.4's (Deep Freeze's) look and feel that Backport Core owns, applying while 26.4 is the newest installed version
 * (Game Drops Backport registers it). Built on 26.4 Pre-Release 1. Both sides, plain data; the client code is
 * {@code client.Look264Client} (screens in {@code look264.client}).
 *
 * <ul>
 *   <li>Improved Fog (Video Settings, after Anisotropic Filtering; on, off in the Fast preset): the fog's color is the
 *       sky's, so the terrain's edge fades into the sky instead of a solid band.</li>
 *   <li>Repeat Recipe/Trade (R, Inventory): places the recipe book's last recipe again (with Shift, as many as fit).</li>
 *   <li>The recipe book's tooltips: "Recipe Book" on its button, each tab's name.</li>
 *   <li>Other Players (P, and the pause menu's icon): the players in this world, muting chat from one.</li>
 * </ul>
 * Game Drops keeps the Deep Freeze panorama, the jukebox model and 26.4's rewording of vanilla's text.
 */
public final class Look264 {

    /** 26.4's {@code improvedFog}: on, off in the Fast preset. */
    public static final ToggleOption IMPROVED_FOG = new ToggleOption("backportcore:improved_fog", "options.improvedFog",
            OptionScreen.VIDEO, true);

    private Look264() {
    }

    static void register() {
        IMPROVED_FOG.tooltip("options.improvedFog.tooltip");
        Presentation.core("26.4").option(IMPROVED_FOG);
    }
}

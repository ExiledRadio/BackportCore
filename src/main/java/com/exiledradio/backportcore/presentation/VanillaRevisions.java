package com.exiledradio.backportcore.presentation;

/**
 * What Core knows of vanilla's own screens at each version, as it knows the loading screen's revisions
 * ({@link LoadingRevision}): the newest installed version picks each, so a change one version made stays made
 * when only a later backport is installed (vanilla never brought 3D Anaglyph back). The assets and the text these
 * screens show are the backports' ({@link Contribution#replaceAsset}, {@link Contribution#vanillaText}); Core
 * never ships a vanilla asset.
 */
public final class VanillaRevisions {

    private VanillaRevisions() {
    }

    /** The startup (Mojang) screen. */
    public enum Startup {
        /** 1.12.2 and 1.13: no screen of Core's (Forge's loading screen runs, then the title screen). */
        NONE,
        /** 19w06a: white, the 256 by 256 {@code mojang.png} and a red bar, fading into the menu. */
        BAR_1_14,
        /** 20w21a: red ({@code 0xEF323D}), the Mojang Studios logo in two halves, a white bar. */
        STUDIOS_1_16;

        public static Startup forVersion(GameVersion v) {
            return v.isAtLeast("1.16") ? STUDIOS_1_16 : v.isAtLeast("1.14") ? BAR_1_14 : NONE;
        }

        /** @return the vanilla texture this revision draws */
        public String logoPath() {
            return this == STUDIOS_1_16 ? "textures/gui/title/mojangstudios.png" : "textures/gui/title/mojang.png";
        }
    }

    /** @return the startup screen at the newest installed version */
    public static Startup startup() {
        return Startup.forVersion(Presentation.newest());
    }

    /** 1.13: the title screen fades in on the first start (its panorama at once, the rest over the next second). */
    public static boolean titleFadeIn() {
        return Presentation.isAtLeast("1.13");
    }

    /** 1.13 (17w43a): 3D Anaglyph is gone from Video Settings, and off. */
    public static boolean anaglyphRemoved() {
        return Presentation.isAtLeast("1.13");
    }

    /** 1.13: GUI Scale counts Auto, 1, 2, 3 ... up to what the window fits (4 at 1080p). */
    public static boolean numberedGuiScale() {
        return Presentation.isAtLeast("1.13");
    }

    /** 1.14 (18w44a): Use VBOs is gone from Video Settings, and always on. */
    public static boolean vboRemoved() {
        return Presentation.isAtLeast("1.14");
    }

    /** 1.14 (18w43a): the pause menu's Give Feedback and Report Bugs row. */
    public static boolean pauseFeedback() {
        return Presentation.isAtLeast("1.14");
    }

    /** 1.15 (19w35a): a hardcore death screen has one "Title screen" button instead of Delete World and Leave Server. */
    public static boolean hardcoreTitleButton() {
        return Presentation.isAtLeast("1.15");
    }

    /** 1.15: button text is white, hovered or not (was 0xE0E0E0, and yellow 0xFFFFA0 hovered). */
    public static boolean whiteButtonText() {
        return Presentation.isAtLeast("1.15");
    }

    /** @return the color a button's label is drawn in, given 1.12.2's ({@code GuiButton.drawButton}) */
    public static int buttonTextColor(int vanilla) {
        if ((vanilla == 0xE0E0E0 || vanilla == 0xFFFFA0) && whiteButtonText()) {
            return 0xFFFFFF;
        }
        return vanilla;
    }
}

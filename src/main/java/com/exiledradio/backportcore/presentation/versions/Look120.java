package com.exiledradio.backportcore.presentation.versions;

import com.exiledradio.backportcore.presentation.LoadingStatus;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.SliderOption;
import net.minecraft.util.ResourceLocation;

/**
 * 1.20's look and feel that Backport Core owns since 1.4.7 (moved out of Trails & Tales Backport): the pieces that apply whichever
 * backport is the newest installed one, as long as it is 1.20 or later ({@code Presentation.core}).
 * Both sides, plain data; the client code is {@code client.Look120Client}.
 *
 * <ul>
 *   <li>1.20 (23w14a): the redrawn title logo and its own Minceraft logo, laid out the way 1.12.2's title screen draws
 *       {@code minecraft.png}, and the larger "Java Edition" under it, all three drawn smoothed (their {@code .mcmeta},
 *       as vanilla's at four times the size; Core's P29 lays the title out); the splash changes between 1.19.4's and
 *       1.20.6's {@code splashes.txt}; vanilla's own text as 1.20.6 words it; the application icon (a grass block) and
 *       Ctrl + scroll changing GUI Scale on Video Settings (client);</li>
 *   <li>1.20.2: F3 + 1 and F3 + 2 show the profiler and the FPS charts, and F3 alone (with Shift or Alt) no longer does
 *       (client);</li>
 *   <li>1.20.3: the spawner's inner faces (23w45a, its model);</li>
 *   <li>1.20.5: Menu Background Blur (Accessibility and Video Settings, which Core's menu backgrounds read), the world
 *       loading screen over the blurred, darkened panorama (Core's {@code PANORAMA_1_20_5} revision), the dimension
 *       change screens (24w03a), the shield arm following the look ({@code poseBlockingArm}) and Ctrl + pick block
 *       without "(+NBT)" (P76).</li>
 * </ul>
 * Trails &amp; Tales Backport keeps the cherry grove panorama, its menu and game music and the brushing arm pose.
 */
public final class Look120 {

    /** The option keeps Trails &amp; Tales' id, so its saved value carries over. */
    private static final String TT = "trailsandtalesbackport";
    private static final String DIR = "look/1.20/";

    /** The title logo Core serves for {@code minecraft:textures/gui/title/minecraft.png} from 1.20 on. */
    public static final ResourceLocation LOGO = rl(DIR + "textures/gui/title/minecraft.png");
    /** 1.20's Minceraft logo, in the same layout. */
    public static final ResourceLocation MINCERAFT = rl(DIR + "textures/gui/title/minceraft.png");
    /** {@code {"texture": {"blur": true}}}: the logo, Minceraft and "Java Edition" are drawn smoothed. */
    static final ResourceLocation SMOOTH = rl(DIR + "textures/gui/title/smooth.png.mcmeta");

    /**
     * 1.20.5's Menu Background Blur (Accessibility Settings and Video Settings), 0 to 100%, 50% by default: the box
     * blur radius of the panorama behind menus is ten times the value. OFF at zero.
     */
    public static final SliderOption MENU_BLUR = new SliderOption(TT + ":menu_background_blurriness",
            "options.accessibility.menu_background_blurriness", OptionScreen.ACCESSIBILITY, 0.0, 1.0, 0.0, 0.5);

    private Look120() {
    }

    static ResourceLocation rl(String path) {
        return new ResourceLocation("backportcore", path);
    }

    /** 1.12.2's spawner model, which 1.20.3's (inner faces) replaces. */
    public static final String SPAWNER_MODEL = "models/block/mob_spawner.json";

    /** From Backport Core's construction, both sides, before any backport registers. */
    static void register() {
        Presentation.core("1.20")
                .addSplashes(rl(DIR + "texts/splashes_added.txt"))
                .removeSplashes(rl(DIR + "texts/splashes_removed.txt"))
                .titleLogo(LOGO)
                .replaceAsset("textures/gui/title/minecraft.png.mcmeta", SMOOTH)
                .replaceAsset("textures/gui/title/minceraft.png", MINCERAFT)
                .replaceAsset("textures/gui/title/minceraft.png.mcmeta", SMOOTH)
                .replaceAsset("textures/gui/title/edition.png", rl(DIR + "textures/gui/title/edition.png"))
                .replaceAsset("textures/gui/title/edition.png.mcmeta", SMOOTH)
                .vanillaText(rl(DIR + "vanilla_text"));

        // 1.20.3 (23w45a): the spawner renders its inner faces (cube_all_inner_faces, written out for 1.12.2's
        // mob_spawner; 1.12.2's spawner is already on the cutout layer, the item model inherits it).
        Presentation.core("1.20.3").replaceAsset(SPAWNER_MODEL, rl("look/1.20.3/" + SPAWNER_MODEL));

        // 1.20.5 (24w09a): on Accessibility Settings after Auto-Jump, and last on Video Settings.
        MENU_BLUR.zeroKey("options.off");
        MENU_BLUR.tooltip("options.accessibility.menu_background_blurriness.tooltip");
        MENU_BLUR.alsoOn(OptionScreen.VIDEO);
        Presentation.core("1.20.5").option(MENU_BLUR.after("AUTO_JUMP"))
                // The tint over the blurred panorama (renderMenuBackground).
                .replaceAsset("textures/gui/menu_background.png", rl("look/1.20.5/textures/gui/menu_background.png"));
    }

    /**
     * 1.20.6 {@code VideoSettingsScreen.mouseScrolled}: one Ctrl + wheel step of GUI Scale.
     *
     * @param current the GUI scale, 0 for Auto
     * @param step    +1 (wheel up) or -1
     * @param max     the largest scale the window fits (what Auto picks)
     * @return the new scale: Auto counts as max + 1, and a step that leaves 1 to max changes nothing
     */
    public static int nextGuiScale(int current, int step, int max) {
        int from = current == 0 ? max + 1 : current;
        int to = from + step;
        return to != 0 && to <= max && to >= 1 ? to : current;
    }

    /**
     * @return a chunk status's color in 1.20's world-loading grid (23w16a), 0xRRGGBB. Core's table is 1.14's; 1.20
     *         gave carvers the old liquid carvers' blue (liquid carvers are gone) and light a pale yellow of its own
     *         (light's old gray became the new Initialize Light). Untracked cells are black.
     */
    public static int gridColor(LoadingStatus status) {
        if (status == null) {
            return LoadingStatus.UNTRACKED_COLOR;
        }
        switch (status) {
            case CARVERS:
            case LIQUID_CARVERS:
                return 0x303572;
            case LIGHT:
                return 0xFFE0A0;
            default:
                return status.color();
        }
    }

    /** 1.20.5's dimension-change screen's reasons ({@code ReceivingLevelScreen}). */
    public enum Reason {
        NETHER_PORTAL, END_PORTAL, OTHER
    }

    /** 1.20.6's {@code determineLevelLoadingReason}, with 1.12.2's dimension ids (-1 the Nether, 1 the End). */
    public static Reason dimensionReason(boolean dead, Integer from, int to) {
        if (dead || from == null || from == to) {
            return Reason.OTHER;
        }
        if (to == -1 || from == -1) {
            return Reason.NETHER_PORTAL;
        }
        if (to == 1 || from == 1) {
            return Reason.END_PORTAL;
        }
        return Reason.OTHER;
    }
}

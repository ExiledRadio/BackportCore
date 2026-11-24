package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.LegacyCopies;
import com.exiledradio.backportcore.presentation.OrderedSteps;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.CycleOption;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.SliderOption;
import com.exiledradio.backportcore.presentation.option.ToggleOption;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Core 1.4.7's own version-feel pieces: Core's contributions apply up to the newest installed version without declaring
 * one, an older backport jar's copy of an option Core owns follows Core's, a step of the same version and name is left
 * out, and LegacyCopies finds classes without loading them. The self-test mod's dummy 1.14 and 1.16 are installed.
 */
final class CoreFeelTests {

    private CoreFeelTests() {
    }

    /** The options Core 1.4.7 moved out of the backports, by their (unchanged) ids. */
    static final String[] MOVED_OPTIONS = {
            "updateaquaticbackport:biome_blend_radius", "updateaquaticbackport:auto_suggestions",
            "villagerbackport:mouse_wheel_sensitivity", "villagerbackport:discrete_mouse_scroll",
            "buzzybeesbackport:toggle_crouch", "buzzybeesbackport:toggle_sprint",
            "netherupdatebackport:entity_distance_scaling", "netherupdatebackport:screen_effect_scale", "netherupdatebackport:fov_effect_scale",
            "cavesandcliffsbackport:hide_lightning_flashes", "cavesandcliffsbackport:autosave_indicator",
            "wildupdatebackport:damage_tilt_strength", "wildupdatebackport:glint_speed", "wildupdatebackport:glint_strength",
            "trailsandtalesbackport:menu_background_blurriness",
            "trickytrialsbackport:inactivity_fps_limit", "trickytrialsbackport:high_contrast_block_outline",
            "trickytrialsbackport:invert_mouse_x", "trickytrialsbackport:sprint_window", "trickytrialsbackport:save_chat_drafts",
    };

    /** An older jar's class, and the static option field it still registers (or null): {class, field, option id}. */
    private static final String[][] OLD_COPIES = {
            {"com.exiledradio.updateaquaticbackport.lookfeel.LookAndFeel", "AUTO_SUGGEST", "updateaquaticbackport:auto_suggestions"},
            {"com.exiledradio.updateaquaticbackport.lookfeel.LookAndFeel", "BIOME_BLEND", "updateaquaticbackport:biome_blend_radius"},
            {"com.exiledradio.villagerbackport.lookfeel.LookAndFeel", "SCROLL_SENSITIVITY", "villagerbackport:mouse_wheel_sensitivity"},
            {"com.exiledradio.buzzybeesbackport.lookfeel.LookAndFeel", "TOGGLE_SNEAK", "buzzybeesbackport:toggle_crouch"},
            {"com.exiledradio.netherupdatebackport.lookfeel.LookAndFeel", "FOV_EFFECTS", "netherupdatebackport:fov_effect_scale"},
            {"com.exiledradio.cavesandcliffsbackport.lookfeel.LookAndFeel", "AUTOSAVE_INDICATOR", "cavesandcliffsbackport:autosave_indicator"},
            {"com.exiledradio.wildupdatebackport.lookfeel.LookAndFeel", "GLINT_SPEED", "wildupdatebackport:glint_speed"},
            {"com.exiledradio.trailsandtalesbackport.lookfeel.LookAndFeel", "MENU_BLUR", "trailsandtalesbackport:menu_background_blurriness"},
            {"com.exiledradio.trickytrialsbackport.lookfeel.LookAndFeel", "SAVE_CHAT_DRAFTS", "trickytrialsbackport:save_chat_drafts"},
    };

    /** The classes Core leaves its own handler or patch off for (LegacyCopies), one per older jar. */
    private static final String[] SENTINELS = {
            "com.exiledradio.updateaquaticbackport.asm.BiomeBlendPatch",
            "com.exiledradio.villagerbackport.lookfeel.ScrollAccumulator",
            "com.exiledradio.buzzybeesbackport.tweaks.client.ToggleKeys",
            "com.exiledradio.netherupdatebackport.lookfeel.client.LookAndFeelClient",
            "com.exiledradio.cavesandcliffsbackport.lookfeel.client.LookAndFeelClient",
            "com.exiledradio.wildupdatebackport.asm.LookTransformer",
            "com.exiledradio.trailsandtalesbackport.lookfeel.client.DimensionScreen",
            "com.exiledradio.trickytrialsbackport.lookfeel.client.ChatDrafts",
    };

    /**
     * Group {@code legacyjars}: run with the live (pre-1.4.7) backport jars in eclipse/mods. Each older jar's own option
     * follows Core's, the option list has each moved id once, and Core sees each older jar's handler classes.
     */
    static void scheduleLegacy() {
        then(1, "core feel: older backport jars", world -> {
            for (String id : MOVED_OPTIONS) {
                com.exiledradio.backportcore.presentation.option.PresentationOption o = Presentation.option(id);
                check("legacy jars: " + id + " is Core's", o != null && o.isCoreOwned() && o.canonical() == null);
                int listed = 0;
                for (com.exiledradio.backportcore.presentation.option.PresentationOption p : Presentation.options()) {
                    if (p.id().equals(id)) {
                        listed++;
                    }
                }
                check("legacy jars: " + id + " listed once", listed == 1, listed);
            }
            for (String[] copy : OLD_COPIES) {
                Object field = null;
                try {
                    field = Class.forName(copy[0]).getField(copy[1]).get(null);
                } catch (ReflectiveOperationException | LinkageError e) {
                    field = e;
                }
                check("legacy jars: the older jar's " + copy[1] + " follows Core's", field instanceof com.exiledradio.backportcore.presentation.option.PresentationOption
                        && ((com.exiledradio.backportcore.presentation.option.PresentationOption) field).canonical() == Presentation.option(copy[2]), field);
            }
            for (String c : SENTINELS) {
                check("legacy jars: Core sees the older jar's " + c.substring(c.lastIndexOf('.') + 1) + " and leaves its copy off",
                        LegacyCopies.present(c), c);
            }
        });
    }

    static void schedule() {
        then(1, "core feel: Core's own contributions", world -> {
            GameVersion newest = Presentation.installedNewest();
            Contribution late = Presentation.core("1.20.9");
            check("core feel: Core's pieces do not declare a version installed", Presentation.installedNewest().equals(newest),
                    Presentation.installedNewest());
            check("core feel: Core's pieces of a version above the newest installed are left out", !Presentation.installed().contains(late));
            check("core feel: core() returns the same contribution, marked Core's", Presentation.core("1.20.9") == late && late.isCore()
                    && Presentation.coreContribution("1.20.9") == late && late.owners().contains("backportcore"));

            List<Contribution> all = Presentation.installed();
            GameVersion last = null;
            boolean ordered = true;
            boolean coreFirst = true;
            for (int i = 0; i < all.size(); i++) {
                Contribution c = all.get(i);
                if (last != null && c.version().compareTo(last) < 0) {
                    ordered = false;
                }
                last = c.version();
                if (i > 0 && c.isCore() && all.get(i - 1).version().equals(c.version()) && !all.get(i - 1).isCore()) {
                    coreFirst = false;
                }
                if (!newest.isAtLeast(c.version())) {
                    ordered = false;
                }
            }
            check("core feel: installed() is oldest first, up to the newest installed version", ordered, all);
            check("core feel: Core's pieces come before a backport's of the same version", coreFirst, all);
            Contribution v116 = Presentation.contribution("1.16");
            check("core feel: a backport's contribution is not Core's", v116 == null || !v116.isCore());
        });
        then(1, "core feel: an older jar's option follows Core's", world -> {
            // Never shown (until its own version): only the mirror logic is under test.
            ToggleOption core = new ToggleOption("bcst_feel:toggle", "options.on", OptionScreen.CHAT, false);
            core.until("1.13");
            Presentation.core("1.13").option(core);
            ToggleOption copy = new ToggleOption("bcst_feel:toggle", "options.on", OptionScreen.CHAT, false);
            AtomicInteger heard = new AtomicInteger();
            copy.onChange(v -> heard.incrementAndGet());
            Presentation.detached("1.14").option(copy);
            check("mirror: Core's option is marked Core's", core.isCoreOwned() && !copy.isCoreOwned());
            check("mirror: the registry keeps Core's", Presentation.option("bcst_feel:toggle") == core);
            check("mirror: the copy follows it", copy.canonical() == core);
            int listed = 0;
            for (com.exiledradio.backportcore.presentation.option.PresentationOption o : Presentation.options()) {
                if (o.id().equals("bcst_feel:toggle")) {
                    listed++;
                }
            }
            check("mirror: one entry in the option list", listed == 1, listed);
            copy.set(true);
            check("mirror: setting the copy sets Core's", core.get() && copy.get());
            core.set(false);
            check("mirror: reading the copy reads Core's", !copy.get());
            check("mirror: the copy's listener hears Core's changes", heard.get() >= 1, heard.get());
            copy.setSaved("true");
            check("mirror: a saved value (versioned default) on the copy lands on Core's", core.get());

            SliderOption slider = new SliderOption("bcst_feel:slider", "options.on", OptionScreen.VIDEO, 0.0, 1.0, 0.0, 0.5);
            slider.until("1.13");
            Presentation.core("1.13").option(slider);
            SliderOption sliderCopy = new SliderOption("bcst_feel:slider", "options.on", OptionScreen.VIDEO, 0.0, 1.0, 0.0, 0.5);
            Presentation.detached("1.14").option(sliderCopy);
            sliderCopy.set(0.25);
            check("mirror: a slider's copy follows it", sliderCopy.canonical() == slider && slider.get() == 0.25 && sliderCopy.get() == 0.25,
                    slider.get());

            CycleOption cycle = new CycleOption("bcst_feel:cycle", "options.on", OptionScreen.CONTROLS, new String[] {"hold", "toggle"},
                    new String[] {"options.key.hold", "options.key.toggle"}, "hold");
            cycle.until("1.13");
            Presentation.core("1.13").option(cycle);
            CycleOption cycleCopy = new CycleOption("bcst_feel:cycle", "options.on", OptionScreen.CONTROLS, new String[] {"hold", "toggle"},
                    new String[] {"options.key.hold", "options.key.toggle"}, "hold");
            Presentation.detached("1.14").option(cycleCopy);
            cycleCopy.cycle(false);
            check("mirror: a cycle's copy follows it", cycleCopy.canonical() == cycle && "toggle".equals(cycle.get())
                    && "options.key.toggle".equals(cycleCopy.valueKey()), cycle.get());

            ToggleOption other = new ToggleOption("bcst_feel:other", "options.on", OptionScreen.CHAT, false);
            other.until("1.13");
            Presentation.detached("1.14").option(other);
            ToggleOption second = new ToggleOption("bcst_feel:other", "options.on", OptionScreen.CHAT, false);
            second.until("1.13");
            Presentation.detached("1.14").option(second);
            check("mirror: two backports' options of one id are not mirrored (the later one is used, as before)",
                    Presentation.option("bcst_feel:other") == second && second.canonical() == null);
        });
        then(1, "core feel: the moved options", world -> {
            for (String id : MOVED_OPTIONS) {
                com.exiledradio.backportcore.presentation.option.PresentationOption o = Presentation.option(id);
                check("moved: " + id + " is registered as Core's", o != null && o.isCoreOwned());
            }
        });
        then(1, "core feel: steps and older jars", world -> {
            OrderedSteps<String> steps = new OrderedSteps<String>();
            steps.add("1.13", "a step", "core");
            steps.add("1.13", "a step", "older jar");
            steps.add("1.14", "a step", "another version");
            steps.add("1.13", "another step", "another name");
            check("steps: a step of the same version and name is left out; the first stays",
                    steps.names(GameVersion.parse("1.21")).size() == 3 && steps.steps().contains("core") && !steps.steps().contains("older jar"),
                    steps.names(GameVersion.parse("1.21")));

            LegacyCopies.forget();
            check("legacy: a class on the class path is found", LegacyCopies.present("com.exiledradio.backportcore.presentation.Presentation"));
            check("legacy: a missing class is not", !LegacyCopies.present("com.exiledradio.nosuchbackport.lookfeel.Gone"));
            check("legacy: Core's copy runs while no older class is there",
                    LegacyCopies.coreRuns("com.exiledradio.nosuchbackport.lookfeel.Gone")
                            && !LegacyCopies.coreRuns("com.exiledradio.nosuchbackport.lookfeel.Gone", "com.exiledradio.backportcore.BackportCore"));
        });
    }
}

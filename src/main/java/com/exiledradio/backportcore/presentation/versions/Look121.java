package com.exiledradio.backportcore.presentation.versions;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.option.CycleOption;
import com.exiledradio.backportcore.presentation.option.OptionScreen;
import com.exiledradio.backportcore.presentation.option.SliderOption;
import com.exiledradio.backportcore.presentation.option.ToggleOption;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 1.21's look and feel that Backport Core owns since 1.4.7 (moved out of Tricky Trials Backport): the pieces that apply whichever
 * backport is the newest installed one, as long as it is 1.21 or later ({@code Presentation.core}).
 * Both sides, plain data; the client code is {@code client.Look121Client} (handlers in {@code look121.client}).
 *
 * <ul>
 *   <li>1.21.2: Reduce FPS when (Video Settings), High Contrast Block Outlines (Accessibility Settings), the slot
 *       highlight sprites, the air bubbles' empty and bursting states with {@code ui.hud.bubble_pop}, the item
 *       rarities in tooltips, and the F3 entity line without "B";</li>
 *   <li>1.21.6: the splash changes, the ghast's textures, the music toast's sprites, and fog that thickens in rain;</li>
 *   <li>1.21.9: Invert Mouse X (Mouse Settings), Sprint Window (Controls), Save Unsent Chats (Chat Settings), the
 *       chat drafts and the loading screen with the panorama behind it and a progress bar;</li>
 *   <li>1.21.11: two more splashes, the startup logo, vanilla's text as 1.21.11 words it, nether fog from 10 to 96
 *       blocks and the sunrise dimmed by rain and thunder.</li>
 * </ul>
 * Tricky Trials keeps the panorama and its overlay, the menu music and the spawner's sounds.
 */
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID)
public final class Look121 {

    /** Tricky Trials' option ids, so saved values carry over. */
    private static final String ID = "trickytrialsbackport";

    /** 1.21.2's "Reduce FPS when": AFK (30 after a minute without input, 10 after ten) or only when minimized. */
    public static final CycleOption INACTIVITY_FPS_LIMIT = new CycleOption(ID + ":inactivity_fps_limit",
            "options.inactivityFpsLimit", OptionScreen.VIDEO, new String[]{"minimized", "afk"},
            new String[]{"options.inactivityFpsLimit.minimized", "options.inactivityFpsLimit.afk"}, "afk");

    /** 1.21.2's High Contrast Block Outlines (Accessibility Settings). */
    public static final ToggleOption HIGH_CONTRAST_OUTLINE = new ToggleOption(ID + ":high_contrast_block_outline",
            "options.accessibility.high_contrast_block_outline", OptionScreen.ACCESSIBILITY, false);

    /** 1.21.9's Invert Mouse X (Mouse Settings); 1.12.2's Invert Mouse reads "Invert Mouse Y" beside it. */
    public static final ToggleOption INVERT_MOUSE_X = new ToggleOption(ID + ":invert_mouse_x",
            "options.invertMouseX", OptionScreen.MOUSE, false);

    /** 1.21.9's Sprint Window (Controls): the ticks in which a second tap of forward starts a sprint, 0 (off) to 10. */
    public static final SliderOption SPRINT_WINDOW = new SliderOption(ID + ":sprint_window",
            "options.sprintWindow", OptionScreen.CONTROLS, 0.0, 10.0, 1.0, 7.0);

    /** 1.21.9's Save Unsent Chats (Chat Settings): keep a message closed with Escape as a draft. */
    public static final ToggleOption SAVE_CHAT_DRAFTS = new ToggleOption(ID + ":save_chat_drafts",
            "options.chat.drafts", OptionScreen.CHAT, false);

    /** 1.21.2's {@code ui.hud.bubble_pop}: an air bubble bursting while the player is under water. */
    public static final SoundEvent BUBBLE_POP = new SoundEvent(new ResourceLocation(BackportCore.MOD_ID, "ui.hud.bubble_pop"))
            .setRegistryName(new ResourceLocation(BackportCore.MOD_ID, "ui.hud.bubble_pop"));

    private Look121() {
    }

    /** From Backport Core's construction, both sides, before any backport registers. */
    static void register() {
        // 1.21.2: Video Settings has Reduce FPS when after VSync; Accessibility Settings ends with the outline.
        INACTIVITY_FPS_LIMIT.valueTooltips("options.inactivityFpsLimit.minimized.tooltip", "options.inactivityFpsLimit.afk.tooltip");
        HIGH_CONTRAST_OUTLINE.tooltip("options.accessibility.high_contrast_block_outline.tooltip");
        Presentation.core("1.21.2")
                .replaceAsset("textures/gui/sprites/container/slot_highlight_back.png", look("1.21.2", "textures/gui/sprites/container/slot_highlight_back.png"))
                .replaceAsset("textures/gui/sprites/container/slot_highlight_front.png", look("1.21.2", "textures/gui/sprites/container/slot_highlight_front.png"))
                .option(INACTIVITY_FPS_LIMIT.after("ENABLE_VSYNC"))
                .option(HIGH_CONTRAST_OUTLINE);

        // 1.21.6: the ghast's new textures are a plain swap of vanilla's (the modern texture area), the music toast's
        // sprites, the splash changes.
        Presentation.core("1.21.6")
                .replaceAsset("textures/entity/ghast/ghast.png", look("1.21.6", "textures/entity/ghast/ghast.png"))
                .replaceAsset("textures/entity/ghast/ghast_shooting.png", look("1.21.6", "textures/entity/ghast/ghast_shooting.png"))
                .replaceAsset("textures/gui/sprites/toast/now_playing.png", look("1.21.6", "textures/gui/sprites/toast/now_playing.png"))
                .replaceAsset("textures/gui/sprites/icon/music_notes.png", look("1.21.6", "textures/gui/sprites/icon/music_notes.png"))
                .addSplashes(look("1.21.6", "texts/splashes_1.21.6_added.txt"))
                .removeSplashes(look("1.21.6", "texts/splashes_1.21.6_removed.txt"));

        // 1.21.9: Mouse Settings' Invert Mouse X sits before Invert Mouse Y (1.12.2's Invert Mouse).
        SPRINT_WINDOW.decimals(0).valueKey("options.value");
        SPRINT_WINDOW.zeroKey("options.off");
        SPRINT_WINDOW.tooltip("options.sprintWindow.tooltip");
        SAVE_CHAT_DRAFTS.tooltip("options.chat.drafts.tooltip");
        Presentation.core("1.21.9")
                .option(INVERT_MOUSE_X)
                .option(SPRINT_WINDOW)
                .option(SAVE_CHAT_DRAFTS);

        Presentation.core("1.21.11")
                .addSplashes(look("1.21.11", "texts/splashes_1.21.11_added.txt"))
                .startupLogo("textures/gui/title/mojangstudios.png", look("1.21.11", "textures/gui/title/mojangstudios.png"))
                .vanillaText(look("1.21.11", "vanilla_text"));
    }

    @SubscribeEvent
    public static void registerSounds(RegistryEvent.Register<SoundEvent> event) {
        event.getRegistry().register(BUBBLE_POP);
    }

    /** @return Core's copy of one of 1.21.x's assets ({@code assets/backportcore/look/<version>/<vanilla path>}) */
    static ResourceLocation look(String version, String path) {
        return new ResourceLocation(BackportCore.MOD_ID, "look/" + version + "/" + path);
    }
}

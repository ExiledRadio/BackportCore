package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraftforge.client.event.sound.SoundSetupEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import paulscode.sound.SoundSystemConfig;

/**
 * 1.14's sound channels. 1.12.2 leaves paulscode at its defaults, 28 channels for sounds and 4 for streamed music, and
 * when all 28 are busy a new sound takes the channel of one still playing, cutting it off. Busy places lose their long
 * sounds that way (a sculk shrieker's shriek among clicking sensors). 1.14's {@code SoundEngine} asks OpenAL for its
 * sources (255 with OpenAL Soft) and splits them as {@code ChannelAccess}/{@code Library.init} do: the square root,
 * clamped to 2..8, for streaming, the rest, clamped to 8..255, for sounds. That is 8 and 247.
 *
 * <p>Applied while the newest installed version is 1.14 or later, before the sound system starts (Forge's
 * {@link SoundSetupEvent}, after every mod's pre-init). A version registered later than that gets it at Core's init,
 * which restarts a sound system already running.
 */
@SideOnly(Side.CLIENT)
public final class SoundChannels {

    /** 1.14's split of OpenAL Soft's 255 sources. */
    public static final int NORMAL = 247;
    public static final int STREAMING = 8;

    private SoundChannels() {
    }

    /** @return true when the installed versions want 1.14's channels */
    public static boolean wanted() {
        return Presentation.isAtLeast("1.14");
    }

    /** @return true when paulscode is set to 1.14's channels */
    public static boolean applied() {
        return SoundSystemConfig.getNumberNormalChannels() == NORMAL && SoundSystemConfig.getNumberStreamingChannels() == STREAMING;
    }

    private static boolean apply() {
        if (!wanted() || applied()) {
            return false;
        }
        SoundSystemConfig.setNumberNormalChannels(NORMAL);
        SoundSystemConfig.setNumberStreamingChannels(STREAMING);
        BackportCore.LOGGER.info("Sound: 1.14's channels ({} for sounds, {} streamed) for {}", NORMAL, STREAMING, Presentation.newest());
        return true;
    }

    /** From Core's init: a version registered after the sound system started restarts it with 1.14's channels. */
    static void initLate(Minecraft mc) {
        if (!apply()) {
            return;
        }
        try {
            SoundManager manager = ReflectionHelper.getPrivateValue(SoundHandler.class, mc.getSoundHandler(), "sndManager", "field_147694_f");
            manager.reloadSoundSystem();
        } catch (Exception e) {
            BackportCore.LOGGER.warn("Sound: could not restart the sound system for 1.14's channels; they apply from the next start", e);
        }
    }

    /** Forge event handlers; public for the event bus. */
    public static final class Events {

        /** The SoundManager is being made, before its sound system starts. */
        @SubscribeEvent
        public void onSetup(SoundSetupEvent event) {
            apply();
        }
    }
}

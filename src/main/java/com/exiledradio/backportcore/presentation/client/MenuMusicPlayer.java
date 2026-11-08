package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.MenuMusic;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.ISoundEventAccessor;
import net.minecraft.client.audio.MusicTicker;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.client.audio.Sound;
import net.minecraft.client.audio.SoundEventAccessor;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Plays the title-screen track from the installed versions' pool ({@link MenuMusic}). When vanilla's music
 * ticker starts its {@code music.menu} sound, the pick is swapped for one from the pool (Forge's sound event,
 * no patch) and the ticker is told it is playing the swapped sound, so it waits for that one to end and keeps
 * vanilla's delays between tracks. Nothing changes until an installed version adds or removes a menu track.
 */
@SideOnly(Side.CLIENT)
public final class MenuMusicPlayer {

    private static final ResourceLocation MENU = SoundEvents.MUSIC_MENU.getSoundName();
    private static final Random RANDOM = new Random();

    private static Field currentMusic;
    private static Field accessorList;

    /** For the client self-test: the last track picked. */
    public static volatile String lastPick;

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPlaySound(PlaySoundEvent event) {
        ISound sound = event.getResultSound();
        if (sound == null || !MENU.equals(sound.getSoundLocation())) {
            return;
        }
        List<Contribution> installed = Presentation.installed();
        if (!MenuMusic.changed(installed)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (currentMusic == null) {
                currentMusic = ReflectionHelper.findField(MusicTicker.class, "currentMusic", "field_147678_c");
                accessorList = ReflectionHelper.findField(SoundEventAccessor.class, "accessorList", "field_188716_a");
            }
            MusicTicker ticker = mc.getMusicTicker();
            if (currentMusic.get(ticker) != sound) {
                return; // someone else's menu-music sound, not the ticker's
            }
            Map<String, Sound> vanilla = new HashMap<String, Sound>();
            List<MenuMusic.Track> base = baseTracks(mc.getSoundHandler(), vanilla);
            MenuMusic.Track pick = MenuMusic.pick(MenuMusic.pool(base, installed), RANDOM);
            if (pick == null) {
                return;
            }
            Sound chosen = vanilla.get(pick.soundName);
            if (chosen == null) {
                chosen = new Sound(pick.soundName, 1.0F, 1.0F, pick.weight, Sound.Type.FILE, true);
            }
            Track replacement = new Track(chosen);
            currentMusic.set(ticker, replacement);
            event.setResultSound(replacement);
            lastPick = pick.soundName;
        } catch (Exception e) {
            BackportCore.LOGGER.error("Presentation: menu music pick failed; vanilla's plays", e);
        }
    }

    /** 1.12.2's {@code music.menu} tracks as the resource packs leave them. */
    @SuppressWarnings("unchecked")
    private static List<MenuMusic.Track> baseTracks(SoundHandler handler, Map<String, Sound> vanilla) throws IllegalAccessException {
        List<MenuMusic.Track> out = new ArrayList<MenuMusic.Track>();
        SoundEventAccessor accessor = handler.getAccessor(MENU);
        if (accessor == null) {
            return out;
        }
        for (ISoundEventAccessor<Sound> entry : (List<ISoundEventAccessor<Sound>>) accessorList.get(accessor)) {
            if (entry instanceof Sound) {
                Sound s = (Sound) entry;
                String name = s.getSoundLocation().toString();
                vanilla.put(name, s);
                out.add(new MenuMusic.Track(name, s.getWeight()));
            }
        }
        return out;
    }

    /** A menu track, played as vanilla plays music: streamed, at full volume, not positioned. */
    static final class Track extends PositionedSound {
        private final Sound chosen;

        Track(Sound chosen) {
            super(MENU, SoundCategory.MUSIC);
            this.chosen = chosen;
            this.volume = 1.0F;
            this.pitch = 1.0F;
            this.repeat = false;
            this.repeatDelay = 0;
            this.attenuationType = ISound.AttenuationType.NONE;
        }

        @Override
        public SoundEventAccessor createAccessor(SoundHandler handler) {
            SoundEventAccessor accessor = handler.getAccessor(this.positionedSoundLocation);
            this.sound = this.chosen;
            return accessor;
        }
    }
}

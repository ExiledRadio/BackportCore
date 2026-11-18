package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.OrderedSteps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MusicTicker;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;

/**
 * The one owner of the in-game music: which track the situation asks for, edited by each installed version in
 * order (1.13's underwater music, 1.16's nether biomes and its gentler switching), and the ticker that plays it.
 *
 * <p>A backport adds its version's step from client code:
 * <pre>{@code
 * GameMusic.add("1.13", "under water", s -> { if (underwater(s.mc)) s.choose(new GameMusic.Track(UNDER_WATER, 12000, 24000, true)); });
 * }</pre>
 * The choice starts as 1.12.2's own ({@code Minecraft.getAmbientMusicType}, Forge's per-dimension music included,
 * each track replacing a different one, as 1.12.2's do). Once any step is registered, Core's ticker takes the place
 * of vanilla's ({@code Minecraft.musicTicker}) on the first client tick: 1.16.5's {@code MusicManager.tick}, which is
 * 1.12.2's with each track saying whether it cuts off a different one. With no step, vanilla's ticker stays. A
 * ticker another mod put there is left alone (logged).
 */
@SideOnly(Side.CLIENT)
public final class GameMusic {

    /** 1.16's {@code Music}: a sound event, the wait before it, and whether it stops a different track playing. */
    public static final class Track {
        public final SoundEvent event;
        public final int minDelay;
        public final int maxDelay;
        public final boolean replaceCurrent;

        public Track(SoundEvent event, int minDelay, int maxDelay, boolean replaceCurrent) {
            this.event = event;
            this.minDelay = minDelay;
            this.maxDelay = maxDelay;
            this.replaceCurrent = replaceCurrent;
        }

        public ResourceLocation name() {
            return this.event.getSoundName();
        }

        @Override
        public String toString() {
            return name() + "[" + this.minDelay + "-" + this.maxDelay + (this.replaceCurrent ? ", replaces" : "") + "]";
        }
    }

    /** What a step sees, and the choice it may change. */
    public static final class Situation {
        public final Minecraft mc;
        /** 1.12.2's own choice, before any step. */
        public final MusicTicker.MusicType vanilla;
        private Track choice;
        private final CoreTicker ticker;

        Situation(Minecraft mc, MusicTicker.MusicType vanilla, Track choice, @Nullable CoreTicker ticker) {
            this.mc = mc;
            this.vanilla = vanilla;
            this.choice = choice;
            this.ticker = ticker;
        }

        public Track choice() {
            return this.choice;
        }

        public void choose(Track track) {
            this.choice = track;
        }

        /** @return true when this event's track is the one playing (1.13's "keep the underwater music going") */
        public boolean isPlaying(SoundEvent event) {
            return this.ticker != null && this.ticker.isPlaying(event);
        }
    }

    /** One version's change to the choice. */
    public interface Step {
        void choose(Situation situation);
    }

    private static final OrderedSteps<Step> STEPS = new OrderedSteps<Step>(com.exiledradio.backportcore.presentation.LookArea.MUSIC);
    private static boolean tried;
    @Nullable
    private static volatile SoundEvent underWater;

    /**
     * 1.13's {@code music.under_water}, from the version that ships its tracks. Later versions' music rules ask
     * for it (1.16.5's {@code getSituationalMusic} still plays it under water in an ocean or river).
     */
    public static void setUnderWater(SoundEvent event) {
        underWater = event;
    }

    /** @return 1.13's underwater music event, or null while no installed version has registered it */
    @Nullable
    public static SoundEvent underWater() {
        return underWater;
    }
    /** For the client self-test. */
    public static volatile String lastChoice;

    private GameMusic() {
    }

    public static void add(String version, String name, Step step) {
        STEPS.add(version, name, step);
    }

    public static OrderedSteps<Step> steps() {
        return STEPS;
    }

    /** 1.12.2's music types as tracks: each replaces a different track (1.12.2's ticker always stops it). */
    public static Track legacy(MusicTicker.MusicType type) {
        return new Track(type.getMusicLocation(), type.getMinDelay(), type.getMaxDelay(), true);
    }

    /** @return the track the newest installed version asks for now */
    public static Track situational(Minecraft mc, @Nullable CoreTicker ticker) {
        MusicTicker.MusicType vanilla = mc.getAmbientMusicType();
        Situation s = new Situation(mc, vanilla, legacy(vanilla), ticker);
        for (Step step : STEPS.steps()) {
            step.choose(s);
        }
        lastChoice = s.choice.toString();
        return s.choice;
    }

    /** Installs Core's ticker on the first client tick, once a step exists. */
    public static final class Events {
        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.START || tried) {
                return;
            }
            tried = true;
            // 1.21.6's Music Frequency and Music Toast need Core's ticker too.
            if (STEPS.steps().isEmpty() && !MusicClient.active()) {
                return;
            }
            install(Minecraft.getMinecraft());
        }
    }

    /** @return true when Core's ticker is in place */
    static boolean install(Minecraft mc) {
        try {
            Field field = ReflectionHelper.findField(Minecraft.class, "musicTicker", "mcMusicTicker", "field_147126_aw");
            Object old = field.get(mc);
            if (old instanceof CoreTicker) {
                return true;
            }
            if (old == null || old.getClass() != MusicTicker.class) {
                BackportCore.LOGGER.warn("Presentation: another mod has replaced the music ticker ({}); the versions' music is not applied.",
                        old == null ? "null" : old.getClass().getName());
                return false;
            }
            field.set(mc, new CoreTicker(mc));
            BackportCore.LOGGER.info("Presentation: in-game music follows {}", STEPS.names(com.exiledradio.backportcore.presentation.Presentation.newest(com.exiledradio.backportcore.presentation.LookArea.MUSIC)));
            return true;
        } catch (Exception e) {
            BackportCore.LOGGER.warn("Presentation: could not replace the music ticker; the versions' music is not applied.", e);
            return false;
        }
    }

    /** 1.16.5's {@code MusicManager}. */
    public static final class CoreTicker extends MusicTicker {
        private final Minecraft mc;
        private final Random random = new Random();
        @Nullable
        private ISound current;
        private int nextSongDelay = 100;

        CoreTicker(Minecraft mc) {
            super(mc);
            this.mc = mc;
        }

        @Override
        public void update() {
            if (MusicClient.active()) {
                update1216();
                return;
            }
            Track music = situational(this.mc, this);
            if (this.current != null) {
                if (!music.name().equals(this.current.getSoundLocation()) && music.replaceCurrent) {
                    this.mc.getSoundHandler().stopSound(this.current);
                    this.nextSongDelay = MathHelper.getInt(this.random, 0, music.minDelay / 2);
                }
                if (!this.mc.getSoundHandler().isSoundPlaying(this.current)) {
                    this.current = null;
                    this.nextSongDelay = Math.min(this.nextSongDelay, MathHelper.getInt(this.random, music.minDelay, music.maxDelay));
                }
            }
            this.nextSongDelay = Math.min(this.nextSongDelay, music.maxDelay);
            if (this.current == null && this.nextSongDelay-- <= 0) {
                play(music);
            }
        }

        /** 1.21.6's {@code MusicManager.tick}: each wait is Music Frequency's ({@link MusicClient#delay}). */
        private void update1216() {
            Track music = situational(this.mc, this);
            if (this.current != null) {
                if (!music.name().equals(this.current.getSoundLocation()) && music.replaceCurrent) {
                    this.mc.getSoundHandler().stopSound(this.current);
                    this.nextSongDelay = MathHelper.getInt(this.random, 0, music.minDelay / 2);
                }
                if (!this.mc.getSoundHandler().isSoundPlaying(this.current)) {
                    this.current = null;
                    this.nextSongDelay = Math.min(this.nextSongDelay, MusicClient.delay(music.minDelay, music.maxDelay, this.random));
                }
            }
            this.nextSongDelay = Math.min(this.nextSongDelay, MusicClient.delay(music.minDelay, music.maxDelay, this.random));
            if (this.current == null && this.nextSongDelay-- <= 0) {
                play(music);
            }
        }

        /** Music Frequency changed: the next wait is drawn again ({@code MusicManager.setMinutesBetweenSongs}). */
        void frequencyChanged() {
            Track music = situational(this.mc, this);
            this.nextSongDelay = MusicClient.delay(music.minDelay, music.maxDelay, this.random);
        }

        private void play(Track music) {
            this.current = PositionedSoundRecord.getMusicRecord(music.event);
            // As 1.12.2's playMusic (and 1.16.5's startPlaying): the sound handler skips an event without sounds.
            this.mc.getSoundHandler().playSound(this.current);
            this.nextSongDelay = Integer.MAX_VALUE;
        }

        /** Vanilla calls this for nothing in 1.12.2; another mod might. */
        @Override
        public void playMusic(MusicTicker.MusicType type) {
            play(legacy(type));
        }

        /** Stops the track playing now (1.16.5's {@code stopPlaying}). */
        public void stopMusic() {
            if (this.current != null) {
                this.mc.getSoundHandler().stopSound(this.current);
                this.current = null;
                this.nextSongDelay = 0;
            }
        }

        boolean isPlaying(SoundEvent event) {
            return this.current != null && event.getSoundName().equals(this.current.getSoundLocation());
        }

        @Nullable
        ISound current() {
            return this.current;
        }

        /** The menu-music swap ({@link MenuMusicPlayer}) tells the ticker which sound it is really playing. */
        void setCurrent(ISound sound) {
            this.current = sound;
        }
    }

    /** @return the track Core's ticker is playing now, or null (none, or vanilla's ticker is in place) */
    @Nullable
    public static ISound currentTrack(Minecraft mc) {
        MusicTicker ticker = mc.getMusicTicker();
        if (ticker instanceof CoreTicker) {
            ISound s = ((CoreTicker) ticker).current();
            return s != null && mc.getSoundHandler().isSoundPlaying(s) ? s : null;
        }
        return null;
    }

    /** Music Frequency's listener: a running ticker draws its next wait again. */
    static void frequencyChanged() {
        MusicTicker ticker = Minecraft.getMinecraft().getMusicTicker();
        if (ticker instanceof CoreTicker) {
            ((CoreTicker) ticker).frequencyChanged();
        }
    }

    /** For tests: the names of the steps at the newest version. */
    public static List<String> names() {
        return STEPS.names(com.exiledradio.backportcore.presentation.Presentation.newest(com.exiledradio.backportcore.presentation.LookArea.MUSIC));
    }
}

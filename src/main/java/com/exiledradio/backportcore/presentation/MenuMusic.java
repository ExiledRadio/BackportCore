package com.exiledradio.backportcore.presentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The title screen's music pool: 1.12.2's {@code music.menu} tracks (as resource packs leave them), then each
 * installed version's additions and removals applied in version order. A track added by one version and
 * removed by a later installed one is gone, as in vanilla (1.19 removed five of 1.18's, for one).
 *
 * <p>The pool only replaces vanilla's pick when some installed version changes it; the client side
 * ({@code presentation.client.MenuMusicPlayer}) plays the pick through vanilla's music ticker, so the
 * delays between tracks stay vanilla's.
 */
public final class MenuMusic {

    private MenuMusic() {
    }

    /** One track: a sound file by its sound name ({@code "minecraft:music/menu/menu1"}), and its weight. */
    public static final class Track {
        public final String soundName;
        public final int weight;
        /** 0 to 1 (since Core 1.3.0). */
        public final float volume;

        public Track(String soundName, int weight) {
            this(soundName, weight, 1.0F);
        }

        public Track(String soundName, int weight, float volume) {
            this.soundName = soundName;
            this.weight = weight;
            this.volume = volume;
        }

        @Override
        public String toString() {
            return this.soundName + "x" + this.weight;
        }
    }

    /** @return true when any installed version adds or removes a menu track */
    public static boolean changed(List<Contribution> installed) {
        for (Contribution c : installed) {
            if (!c.musicAdds.isEmpty() || !c.musicRemoves.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param base      1.12.2's tracks
     * @param installed every installed version's contribution, oldest first
     */
    public static List<Track> pool(List<Track> base, List<Contribution> installed) {
        List<Track> pool = new ArrayList<Track>(base);
        for (Contribution c : installed) {
            for (String removed : c.musicRemoves) {
                pool.removeIf(t -> matches(t.soundName, removed));
            }
            for (Contribution.MenuTrack added : c.musicAdds) {
                pool.removeIf(t -> t.soundName.equals(added.soundName));
                pool.add(new Track(added.soundName, added.weight, added.volume));
            }
        }
        return pool;
    }

    /** @return true when a removal names this track: its whole name, or (without a namespace) its path */
    static boolean matches(String soundName, String removed) {
        if (removed.indexOf(':') >= 0) {
            return soundName.equals(removed);
        }
        int colon = soundName.indexOf(':');
        return (colon >= 0 ? soundName.substring(colon + 1) : soundName).equals(removed);
    }

    /**
     * 1.21.6's {@code MusicFrequency.getNextSongDelay}: "default" waits at most 20 minutes, "frequent" 10, "constant"
     * 5 seconds, within the track's own wait (ticks). Since Core 1.3.0.
     */
    public static int frequencyDelay(String frequency, int minDelay, int maxDelay, Random random) {
        if ("constant".equals(frequency)) {
            return 100;
        }
        int cap = ("frequent".equals(frequency) ? 10 : 20) * 1200;
        int lo = Math.min(minDelay, cap);
        int hi = Math.min(maxDelay, cap);
        return hi <= lo ? lo : lo + random.nextInt(hi - lo + 1);
    }

    /** @return a track picked by weight, or null from an empty pool */
    public static Track pick(List<Track> pool, Random random) {
        int total = 0;
        for (Track t : pool) {
            total += t.weight;
        }
        if (total <= 0) {
            return null;
        }
        int r = random.nextInt(total);
        for (Track t : pool) {
            r -= t.weight;
            if (r < 0) {
                return t;
            }
        }
        return pool.get(pool.size() - 1);
    }
}

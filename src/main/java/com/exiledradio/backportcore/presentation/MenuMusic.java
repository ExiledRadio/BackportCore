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

        public Track(String soundName, int weight) {
            this.soundName = soundName;
            this.weight = weight;
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
                pool.removeIf(t -> t.soundName.equals(removed));
            }
            for (Contribution.MenuTrack added : c.musicAdds) {
                pool.removeIf(t -> t.soundName.equals(added.soundName));
                pool.add(new Track(added.soundName, added.weight));
            }
        }
        return pool;
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

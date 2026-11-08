package com.exiledradio.backportcore.presentation;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The title screen's splash list: 1.12.2's own {@code splashes.txt} (as resource packs leave it), then each
 * installed version's additions and removals applied in version order. A version's removals take effect only
 * when that version is installed, as each version removed them.
 *
 * <p>Splashes about the game's Java or OpenGL version ("Now Java 16!", "Now on OpenGL 3.2 core profile!") are
 * false on 1.12.2: additions of them are dropped and removals or changes of them ignored, so 1.12.2's own stay.
 */
public final class Splashes {

    /** The player's name in capitals, as 1.14's "&lt;name&gt; IS YOU". */
    public static final String PLAYER_TOKEN = "{PLAYER}";

    private static final Pattern FALSE_ON_1122 = Pattern.compile("(?i)(\\bjava\\s*\\d|opengl|vulkan)");

    /** Reads a splash file from the resources, one splash per line. */
    public interface LineReader {
        List<String> read(ResourceLocation file) throws IOException;
    }

    private Splashes() {
    }

    /** @return true when the splash names a Java or OpenGL version, which 1.12.2's game would contradict */
    public static boolean isAboutTheRuntime(String splash) {
        return FALSE_ON_1122.matcher(splash).find();
    }

    /** @return true when any installed version changes the splash list (otherwise vanilla's pick stands) */
    public static boolean changed(List<Contribution> installed) {
        for (Contribution c : installed) {
            if (!c.splashAdds.isEmpty() || !c.splashRemoves.isEmpty() || !c.splashAddFiles.isEmpty() || !c.splashRemoveFiles.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param base      1.12.2's list
     * @param installed every installed version's contribution, oldest first
     * @param reader    reads the contributions' splash files; null skips them
     * @return the pool, in a stable order without duplicates
     */
    public static List<String> pool(List<String> base, List<Contribution> installed, @Nullable LineReader reader) {
        Set<String> pool = new LinkedHashSet<String>();
        for (String s : base) {
            String t = s.trim();
            if (!t.isEmpty()) {
                pool.add(t);
            }
        }
        for (Contribution c : installed) {
            List<String> adds = new ArrayList<String>(c.splashAdds);
            List<String> removes = new ArrayList<String>(c.splashRemoves);
            if (reader != null) {
                adds.addAll(readAll(reader, c.splashAddFiles));
                removes.addAll(readAll(reader, c.splashRemoveFiles));
            }
            for (String r : removes) {
                String t = r.trim();
                if (!isAboutTheRuntime(t)) {
                    pool.remove(t);
                }
            }
            for (String a : adds) {
                String t = a.trim();
                if (!t.isEmpty() && !isAboutTheRuntime(t)) {
                    pool.add(t);
                }
            }
        }
        return new ArrayList<String>(pool);
    }

    /** @return the splash with {@link #PLAYER_TOKEN} replaced by the player's name in capitals */
    public static String personalize(String splash, String playerName) {
        return splash.replace(PLAYER_TOKEN, playerName.toUpperCase(Locale.ROOT));
    }

    private static List<String> readAll(LineReader reader, List<ResourceLocation> files) {
        List<String> out = new ArrayList<String>();
        for (ResourceLocation f : files) {
            try {
                out.addAll(reader.read(f));
            } catch (IOException e) {
                Presentation.LOG.warn("Splash file {} could not be read: {}", f, e.toString());
            }
        }
        return out;
    }
}

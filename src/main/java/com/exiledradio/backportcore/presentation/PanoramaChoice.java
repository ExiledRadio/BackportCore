package com.exiledradio.backportcore.presentation;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Which panorama the title screen shows, from Core's {@code presentation.panorama} setting:
 * <ul>
 * <li>{@code LATEST} (the default): the newest installed version that has one;</li>
 * <li>a version ({@code "1.14"}, or {@code "1.12.2"} for 1.12.2's own): that version's, when it is installed and
 * has one, otherwise {@code LATEST};</li>
 * <li>{@code ROTATE}: a different one each launch, from every installed version's and 1.12.2's.</li>
 * </ul>
 */
public final class PanoramaChoice {

    public static final String LATEST = "LATEST";
    public static final String ROTATE = "ROTATE";

    /** Where {@code ROTATE} remembers the last launch's pick, in Core's options file. */
    public static final String LAST_ROTATED_KEY = "backportcore:last_rotated_panorama";

    /** The version whose faces show. */
    public final GameVersion version;
    /** Its six faces, or null for 1.12.2's own (vanilla's {@code minecraft:textures/gui/title/background/}). */
    @Nullable
    public final ResourceLocation[] faces;

    private PanoramaChoice(GameVersion version, @Nullable ResourceLocation[] faces) {
        this.version = version;
        this.faces = faces;
    }

    /**
     * @param setting     the config value
     * @param available   installed versions with a panorama, oldest first ({@link Presentation#panoramas()})
     * @param lastRotated the version {@code ROTATE} showed last launch, or null
     */
    public static PanoramaChoice choose(String setting, List<Presentation.Panorama> available, @Nullable String lastRotated, Random random) {
        String s = setting == null ? LATEST : setting.trim().toUpperCase(Locale.ROOT);
        if (ROTATE.equals(s)) {
            List<PanoramaChoice> all = new ArrayList<PanoramaChoice>();
            all.add(new PanoramaChoice(GameVersion.BASE, null));
            for (Presentation.Panorama p : available) {
                all.add(new PanoramaChoice(p.version, p.faces));
            }
            if (all.size() > 1 && lastRotated != null) {
                all.removeIf(c -> c.version.toString().equals(lastRotated));
            }
            return all.get(random.nextInt(all.size()));
        }
        if (!LATEST.equals(s) && !s.isEmpty()) {
            GameVersion wanted;
            try {
                wanted = GameVersion.parse(s);
            } catch (IllegalArgumentException e) {
                Presentation.LOG.warn("presentation.panorama: \"{}\" is not LATEST, ROTATE or a version; showing the latest", setting);
                return latest(available);
            }
            if (wanted.equals(GameVersion.BASE)) {
                return new PanoramaChoice(GameVersion.BASE, null);
            }
            for (Presentation.Panorama p : available) {
                if (p.version.equals(wanted)) {
                    return new PanoramaChoice(p.version, p.faces);
                }
            }
            Presentation.LOG.warn("presentation.panorama: no installed backport brings {}'s panorama; showing the latest", wanted);
        }
        return latest(available);
    }

    private static PanoramaChoice latest(List<Presentation.Panorama> available) {
        if (available.isEmpty()) {
            return new PanoramaChoice(GameVersion.BASE, null);
        }
        Presentation.Panorama p = available.get(available.size() - 1);
        return new PanoramaChoice(p.version, p.faces);
    }

    @Override
    public String toString() {
        return "panorama " + this.version + (this.faces == null ? " (1.12.2's own)" : " (" + this.faces[0] + "...)");
    }
}

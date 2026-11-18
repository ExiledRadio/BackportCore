package com.exiledradio.backportcore.presentation;

import com.exiledradio.backportcore.presentation.option.PresentationOption;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Which versions change each {@link LookArea}, for the Look &amp; Feel page: Core's own changes at those versions
 * ({@link LookArea#coreVersions()}), and what the installed backports contributed to it. Only versions at or before the
 * newest installed version count, and the newest look's cap.
 */
public final class LookVersions {

    private LookVersions() {
    }

    /** @return the versions that change {@code area} with what is installed now, oldest first */
    public static List<GameVersion> of(LookArea area) {
        GameVersion top = Presentation.resolveLook(Presentation.installedNewest(), true,
                com.exiledradio.backportcore.CoreConfig.lookAndFeel.newestLook);
        TreeSet<GameVersion> out = new TreeSet<GameVersion>();
        for (GameVersion v : area.coreVersions()) {
            if (top.isAtLeast(v) && !v.equals(GameVersion.BASE)) {
                out.add(v);
            }
        }
        for (Contribution c : Presentation.installed()) {
            if (top.isAtLeast(c.version()) && contributes(c, area)) {
                out.add(c.version());
            }
        }
        for (PresentationOption o : Presentation.options()) {
            GameVersion since = o.since();
            if (since != null && o.area() == area && top.isAtLeast(since)) {
                out.add(since);
            }
        }
        out.addAll(OrderedSteps.versions(area, top));
        return new java.util.ArrayList<GameVersion>(out);
    }

    private static boolean contributes(Contribution c, LookArea area) {
        for (Map.Entry<String, ?> e : c.assets.entrySet()) {
            if (LookArea.ofAsset(e.getKey()) == area) {
                return true;
            }
        }
        switch (area) {
            case LOADING:
                return c.loadingRevision != null;
            case TITLE:
                return c.panorama != null || c.panoramaOverlay != null || c.accessibilityIcon != null;
            case SPLASHES:
                return !c.splashAdds.isEmpty() || !c.splashRemoves.isEmpty() || !c.splashAddFiles.isEmpty() || !c.splashRemoveFiles.isEmpty();
            case MENUS:
                return c.multiplayerWarning != null;
            case CHAT:
                return c.commandSuggestions != null || c.suggestionLook != null;
            case DEBUG:
                return !c.debugAdds.isEmpty() || !c.debugRemoves.isEmpty();
            case INVENTORY:
                return c.creativeOrder != null;
            case MUSIC:
                return !c.musicAdds.isEmpty() || !c.musicRemoves.isEmpty();
            case WORDING:
                return !c.texts.isEmpty();
            default:
                return false;
        }
    }

    /** @return true when this contribution has a title-screen panorama */
    public static boolean hasPanorama(Contribution c) {
        return c.panorama != null;
    }

    /** @return the versions as the page shows them: "1.13", "1.13 to 1.21.9", or empty when none is installed */
    public static String range(List<GameVersion> versions) {
        if (versions.isEmpty()) {
            return "";
        }
        GameVersion first = versions.get(0);
        GameVersion last = versions.get(versions.size() - 1);
        return first.equals(last) ? first.toString() : first + "–" + last;
    }
}

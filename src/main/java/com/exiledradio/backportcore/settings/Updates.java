package com.exiledradio.backportcore.settings;

import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.versioning.ComparableVersion;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Which installed family mods have a newer release. Forge's own update check answers first: each family mod names
 * its JSON in {@code @Mod(updateJSON)} ({@link Family#UPDATES}), and Forge reads them once, in the background, at
 * startup (unless its {@code version_checking} config turns that off). Where Forge has no answer (the check is off,
 * failed, is still running, or the mod names no JSON), the newest version this Core knew when it was built
 * ({@link Family.Member#latest}) answers instead.
 */
public final class Updates {

    private Updates() {
    }

    /** @return the newer version of an installed family mod, or null when it is up to date or not installed */
    @Nullable
    public static String newer(String modid) {
        Family.Member m = Family.get(modid);
        ModContainer mod = Loader.instance().getIndexedModList().get(modid);
        if (m == null || mod == null) {
            return null;
        }
        ForgeVersion.CheckResult r = ForgeVersion.getResult(mod);
        if (r != null) {
            switch (r.status) {
                case OUTDATED:
                case BETA_OUTDATED:
                    return r.target != null ? r.target.toString() : null;
                case UP_TO_DATE:
                case AHEAD:
                case BETA:
                    return null;
                default:
                    break; // PENDING or FAILED: Core's own table
            }
        }
        return newer(mod.getVersion(), m.latest);
    }

    /** @return {@code latest} when it is a newer version than {@code installed}, else null */
    @Nullable
    public static String newer(String installed, String latest) {
        if (latest == null || latest.isEmpty() || installed == null || installed.isEmpty()) {
            return null;
        }
        return new ComparableVersion(latest).compareTo(new ComparableVersion(installed)) > 0 ? latest : null;
    }

    /** @return the family members with a newer release, in family order */
    public static List<Family.Member> outdated() {
        List<Family.Member> list = new ArrayList<Family.Member>();
        for (Family.Member m : Family.MEMBERS) {
            if (newer(m.modid) != null) {
                list.add(m);
            }
        }
        return list;
    }
}

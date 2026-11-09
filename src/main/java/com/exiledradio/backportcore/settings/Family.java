package com.exiledradio.backportcore.settings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The backport family, oldest update first, as the settings screen lists it. A member that is not installed is
 * still listed, dimmed, with a link to its CurseForge project.
 */
public final class Family {

    /** One mod of the family. */
    public static final class Member {
        public final String modid;
        /** The update's vanilla version, such as {@code 1.14}; empty for Core. */
        public final String version;
        /** Its icon, {@code textures/gui/family/<icon>.png} in Core's assets. */
        public final String icon;
        /** The page color: the glow of its family icon, as 0xRRGGBB. */
        public final int color;
        public final int curseForgeProject;

        Member(String modid, String version, String icon, int color, int curseForgeProject) {
            this.modid = modid;
            this.version = version;
            this.icon = icon;
            this.color = color;
            this.curseForgeProject = curseForgeProject;
        }

        /** @return the lang key of its short name ("Update Aquatic") */
        public String nameKey() {
            return "backportcore.settings.family." + this.modid;
        }

        /** @return the lang key of its one-line summary, shown when it is not installed */
        public String aboutKey() {
            return "backportcore.settings.family." + this.modid + ".about";
        }

        public String curseForgeUrl() {
            return "https://www.curseforge.com/projects/" + this.curseForgeProject;
        }
    }

    public static final List<Member> MEMBERS = Collections.unmodifiableList(Arrays.asList(
            new Member("backportcore", "", "backport-core", 0xB9A8FF, 1788299),
            new Member("updateaquaticbackport", "1.13", "update-aquatic-backport", 0x4FC9B8, 1788248),
            new Member("villagerbackport", "1.14", "village-and-pillage-backport", 0xE8B04A, 1671232),
            new Member("buzzybeesbackport", "1.15", "buzzy-bees-backport", 0xF2C94C, 1788311),
            new Member("netherupdatebackport", "1.16", "nether-update-backport", 0xE8703A, 1790727),
            new Member("cavesandcliffsbackport", "1.17", "caves-and-cliffs-backport", 0xA678F0, 1788326),
            new Member("wildupdatebackport", "1.19", "wild-update-backport", 0x7CC85A, 1788320),
            new Member("trailsandtalesbackport", "1.20", "trails-and-tales-backport", 0xF29AC0, 1788323),
            new Member("trickytrialsbackport", "1.21", "tricky-trials-backport", 0xE08A4A, 1790743)));

    private Family() {
    }

    /** @return the member with this modid, or null for a mod outside the family */
    public static Member get(String modid) {
        for (Member m : MEMBERS) {
            if (m.modid.equals(modid)) {
                return m;
            }
        }
        return null;
    }
}

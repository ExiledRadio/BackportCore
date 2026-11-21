package com.exiledradio.backportcore.presentation.client;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 26.1's F3 text ({@code DebugEntryLocalDifficulty}, {@code DebugEntryDayCount}, {@code DebugEntryPlayerSpeed},
 * {@code DebugEntrySpawnCounts}): Local Difficulty loses its "(Day n)", which becomes a "Day #n" line of its own
 * after it; the position group ends with "Speed: n blocks/tick"; the spawn line names each of 26.1's mob categories
 * by its debug abbreviation (MO, C, AM, AX, UWC, WC, WA, MI), 0 for those 1.12.2 and the installed mods lack.
 * Each is its own Debug Options entry, Off in both profiles as in 26.3.
 */
@SideOnly(Side.CLIENT)
public final class ModernDebugLines {

    private static final Pattern DAY = Pattern.compile("^(Local Difficulty: .*?) \\(Day (-?\\d+)\\)$");
    private static final Pattern CHUNKS = Pattern.compile("^SC: (\\d+)");
    /** 26.3's {@code MobCategory} order: the creature type's name, then its abbreviation. */
    private static final String[][] CATEGORIES = {{"MONSTER", "MO"}, {"CREATURE", "C"}, {"AMBIENT", "AM"}, {"AXOLOTLS", "AX"},
            {"UNDERGROUND_WATER_CREATURE", "UWC"}, {"WATER_CREATURE", "WC"}, {"WATER_AMBIENT", "WA"}, {"MISC", "MI"}};

    /** For the client self-test. */
    public static volatile int edits;

    private ModernDebugLines() {
    }

    static void init() {
        DebugText.add("26.1", "day count, player speed, spawn categories", ModernDebugLines::edit);
    }

    static void edit(DebugText.Lines lines) {
        int d = DebugText.Lines.find(lines.left, "Local Difficulty: ");
        if (d >= 0) {
            Matcher m = DAY.matcher(lines.left.get(d));
            if (m.matches()) {
                lines.left.set(d, m.group(1));
                lines.left.add(d + 1, "Day #" + m.group(2));
            }
        }
        int sc = DebugText.Lines.find(lines.left, "SC: ");
        if (sc >= 0) {
            String line = spawnLine(lines.left.get(sc), lines);
            if (line != null) {
                lines.left.set(sc, line);
            }
        }
        int facing = DebugText.Lines.find(lines.left, "Facing: ");
        Entity camera = lines.mc.getRenderViewEntity();
        if (facing >= 0 && camera != null) {
            lines.left.add(facing + 1, speed(camera));
        }
        edits++;
    }

    /** 26.1's "Speed: %.3f blocks/tick": the camera entity's movement over its last tick. */
    static String speed(Entity e) {
        double dx = e.posX - e.prevPosX;
        double dy = e.posY - e.prevPosY;
        double dz = e.posZ - e.prevPosZ;
        return String.format(Locale.ROOT, "Speed: %.3f blocks/tick", Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    /** @return the spawn line with 26.1's categories, or null to keep the line as it is */
    static String spawnLine(String line, DebugText.Lines lines) {
        Matcher m = CHUNKS.matcher(line);
        IntegratedServer server = lines.mc.getIntegratedServer();
        if (!m.find() || server == null || lines.mc.player == null) {
            return null;
        }
        WorldServer world = server.getWorld(lines.mc.player.dimension);
        if (world == null) {
            return null;
        }
        StringBuilder s = new StringBuilder("SC: ").append(m.group(1));
        for (String[] c : CATEGORIES) {
            EnumCreatureType type = type(c[0]);
            s.append(", ").append(c[1]).append(": ").append(type == null ? 0 : world.countEntities(type, true));
        }
        return s.toString();
    }

    private static EnumCreatureType type(String name) {
        for (EnumCreatureType t : EnumCreatureType.values()) {
            if (t.name().equals(name)) {
                return t;
            }
        }
        return null;
    }
}

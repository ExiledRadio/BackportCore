package com.exiledradio.backportcore.water;

import com.exiledradio.backportcore.advancement.AdventuringTime;
import com.exiledradio.backportcore.locate.BiomeLocate;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Today's water and underwater fog colors by biome, for whichever mod draws 1.13's water (Update Aquatic Backport's
 * water colors and underwater fog). Wild Update Backport's Core request 10, and the cherry grove's and the pale garden's
 * fog. Since Core 1.3.2.
 *
 * <p>A biome's colors are, in order: what a backport {@link #register registered} for it; else vanilla's for the biome
 * {@link BiomeLocate#id} names it by today (1.20.6's {@code worldgen/biome} files, and 1.21.4's for the pale garden),
 * so a backport's biome registered under a vanilla name ({@code trailsandtalesbackport:cherry_grove}) gets vanilla's
 * colors with no call; else none ({@code null}: the drawer's own rule, such as a filter over another mod's tint). Every
 * biome vanilla gives the defaults ({@link #DEFAULT_WATER}, {@link #DEFAULT_FOG}) answers them.
 *
 * <p>{@link #at} reads an underground biome a backport registered there first ({@code worldgen.UndergroundBiomes}, since
 * 1.4.4), then a modern world's own 3D biome (Caves &amp; Cliffs Backport's 1.18 terrain), so a warm ocean
 * there is a warm ocean though its 1.12 column says ocean.
 */
public final class WaterColors {

    /** 1.13's default water color and underwater fog color. */
    public static final int DEFAULT_WATER = 0x3F76E4, DEFAULT_FOG = 0x050533;

    /** Today's biomes whose colors are not the defaults: {id, water, fog}. */
    private static final Object[][] VANILLA = {
            {"cherry_grove", 0x5DB7EF, 0x5DB7EF},
            {"cold_ocean", 0x3D57D6, DEFAULT_FOG},
            {"deep_cold_ocean", 0x3D57D6, DEFAULT_FOG},
            {"deep_frozen_ocean", 0x3938C9, DEFAULT_FOG},
            {"deep_lukewarm_ocean", 0x45ADF2, 0x041633},
            {"frozen_ocean", 0x3938C9, DEFAULT_FOG},
            {"frozen_river", 0x3938C9, DEFAULT_FOG},
            {"lukewarm_ocean", 0x45ADF2, 0x041633},
            {"mangrove_swamp", 0x3A7A6A, 0x4D7A60},
            {"meadow", 0x0E4ECF, DEFAULT_FOG},
            {"pale_garden", 0x76889D, 0x556980},
            {"snowy_beach", 0x3D57D6, DEFAULT_FOG},
            {"snowy_taiga", 0x3D57D6, DEFAULT_FOG},
            {"swamp", 0x617B64, 0x232317},
            {"warm_ocean", 0x43D5EE, 0x041F33},
    };

    private static final Map<String, int[]> BY_ID = new HashMap<String, int[]>();
    private static final Map<Biome, int[]> REGISTERED = new IdentityHashMap<Biome, int[]>();

    static {
        for (Object[] row : VANILLA) {
            BY_ID.put("minecraft:" + row[0], new int[] {(Integer) row[1], (Integer) row[2]});
        }
    }

    private WaterColors() {
    }

    /** A biome's water and underwater fog colors (0xRRGGBB), over vanilla's for its id. Both sides, any time. */
    public static synchronized void register(Biome biome, int water, int fog) {
        REGISTERED.put(biome, new int[] {water & 0xFFFFFF, fog & 0xFFFFFF});
    }

    /** @return {water, fog} for a biome, or null when neither a backport nor vanilla's table names it */
    @Nullable
    public static synchronized int[] of(Biome biome) {
        int[] r = REGISTERED.get(biome);
        if (r != null) {
            return r.clone();
        }
        return of(BiomeLocate.id(biome), biome);
    }

    /** @return {water, fog} for today's id ({@code "minecraft:warm_ocean"}), or null for a biome vanilla does not have */
    @Nullable
    public static int[] of(String id) {
        return of(id, null);
    }

    @Nullable
    private static int[] of(String id, @Nullable Biome biome) {
        int[] v = BY_ID.get(id);
        if (v != null) {
            return v.clone();
        }
        boolean vanilla = id.startsWith("minecraft:") && (BiomeLocate.TODAY.contains(id.substring(10))
                || biome != null && biome.getRegistryName() != null && "minecraft".equals(biome.getRegistryName().getNamespace()));
        return vanilla ? new int[] {DEFAULT_WATER, DEFAULT_FOG} : null;
    }

    /**
     * @return {water, fog} at a block: a modern world's 3D biome first (by today's id, where no backport registered that
     *         column's biome), else the column's biome; null as {@link #of(Biome)}
     */
    @Nullable
    public static int[] at(World world, BlockPos pos) {
        Biome under = com.exiledradio.backportcore.worldgen.UndergroundBiomes.at(world, pos);
        if (under != null) {
            int[] u = of(under);
            if (u != null) {
                return u;
            }
        }
        Biome biome = world.getBiome(pos);
        synchronized (WaterColors.class) {
            int[] r = REGISTERED.get(biome);
            if (r != null) {
                return r.clone();
            }
        }
        String id = AdventuringTime.biomeAt(world, pos);
        int[] v = of(id, biome);
        return v != null ? v : of(biome);
    }

    /** @return today's underwater fog color for a biome, or the default */
    public static int fog(Biome biome) {
        int[] v = of(biome);
        return v != null ? v[1] : DEFAULT_FOG;
    }
}

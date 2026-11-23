package com.exiledradio.backportcore.selftest.monument;

import com.exiledradio.backportcore.structure.ScatteredPlacement;
import com.exiledradio.backportcore.terrain.TerrainHeights;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Tricky Trials Backport's trial chambers against the terrain, by reflection (Core does not depend on it): every
 * candidate the structure accepts within the radius, laid out as it will generate, and each piece's top against the
 * bare terrain's ground ({@link TerrainHeights#floor}) over the piece's columns. Then generates the first few and
 * reads the real blocks: sky-lit columns (no solid block above) over each piece.
 */
final class ChamberCheck {

    private ChamberCheck() {
    }

    static void run(WorldServer world, int radius, int generate, List<String> out) throws Exception {
        Class<?> structure;
        try {
            structure = Class.forName("com.exiledradio.trickytrialsbackport.chambers.TrialChambersStructure");
        } catch (ClassNotFoundException e) {
            out.add("CHAMBERS Tricky Trials Backport not installed");
            return;
        }
        Object settings = Class.forName("com.exiledradio.trickytrialsbackport.chambers.ChamberSettings").getMethod("get").invoke(null);
        ScatteredPlacement grid = (ScatteredPlacement) settings.getClass().getField("grid").get(settings);
        Object instance = structure.getConstructor().newInstance();
        Method canStart = structure.getDeclaredMethod("canStartAt", net.minecraft.world.World.class, int.class, int.class);
        canStart.setAccessible(true);
        Method layout = structure.getMethod("layout", net.minecraft.world.World.class, int.class, int.class);

        int s = grid.spacing();
        int starts = 0, exposedStarts = 0, generated = 0, generatedExposed = 0;
        int minCover = Integer.MAX_VALUE;
        out.add("CHAMBERS candidate chunk, top of the layout, exposed columns (piece top at or above the ground), least cover");
        for (int gx = -radius; gx <= radius; gx++) {
            for (int gz = -radius; gz <= radius; gz++) {
                ChunkPos c = grid.candidate(world.getSeed(), gx * s, gz * s);
                if (!(Boolean) canStart.invoke(instance, world, c.x, c.z)) {
                    continue;
                }
                starts++;
                @SuppressWarnings("unchecked")
                List<StructureComponent> pieces = (List<StructureComponent>) layout.invoke(null, world, c.x, c.z);
                TerrainHeights terrain = TerrainHeights.forWorld(world);
                int top = Integer.MIN_VALUE, exposed = 0, cover = Integer.MAX_VALUE;
                for (StructureComponent p : pieces) {
                    StructureBoundingBox b = p.getBoundingBox();
                    top = Math.max(top, b.maxY);
                    if (terrain == null) {
                        continue;
                    }
                    for (int x = b.minX; x <= b.maxX; x += 2) {
                        for (int z = b.minZ; z <= b.maxZ; z += 2) {
                            int ground = terrain.floor(x, z) - 1;
                            cover = Math.min(cover, ground - b.maxY);
                            if (b.maxY >= ground) {
                                exposed++;
                            }
                        }
                    }
                }
                minCover = Math.min(minCover, cover);
                if (exposed > 0) {
                    exposedStarts++;
                }
                out.add(String.format("  %d,%d top=%d exposed=%d cover=%d", c.x, c.z, top, exposed, cover));
                if (generated < generate) {
                    generated++;
                    int sky = generateAndCount(world, c, pieces);
                    if (sky > 0) {
                        generatedExposed++;
                    }
                    out.add(String.format("  GENERATED %d,%d skyOverPieces=%d", c.x, c.z, sky));
                }
            }
        }
        out.add(String.format("CHAMBERS starts=%d exposedInLayout=%d leastCover=%d generated=%d generatedWithSky=%d",
                starts, exposedStarts, minCover, generated, generatedExposed));
    }

    /** Generates the chunks the pieces cover; @return columns over the pieces' tops where the sky reaches down to them */
    private static int generateAndCount(WorldServer world, ChunkPos c, List<StructureComponent> pieces) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (StructureComponent p : pieces) {
            StructureBoundingBox b = p.getBoundingBox();
            minX = Math.min(minX, b.minX);
            minZ = Math.min(minZ, b.minZ);
            maxX = Math.max(maxX, b.maxX);
            maxZ = Math.max(maxZ, b.maxZ);
        }
        for (int x = (minX >> 4) - 1; x <= (maxX >> 4) + 1; x++) {
            for (int z = (minZ >> 4) - 1; z <= (maxZ >> 4) + 1; z++) {
                world.getChunkProvider().provideChunk(x, z);
            }
        }
        // Populate the inner chunks: each needs its +x/+z neighbours loaded, which the ring above gives.
        int sky = 0;
        for (StructureComponent p : pieces) {
            StructureBoundingBox b = p.getBoundingBox();
            for (int x = b.minX; x <= b.maxX; x += 2) {
                for (int z = b.minZ; z <= b.maxZ; z += 2) {
                    // Solid ground above the piece's top, up to the surface: the sky must not reach the piece.
                    if (world.getHeight(x, z) <= b.maxY + 1) {
                        sky++;
                    }
                }
            }
        }
        return sky;
    }
}

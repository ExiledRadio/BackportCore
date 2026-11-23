package com.exiledradio.backportcore.structure;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.DimensionManager;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The running world's own structure files, for what a jigsaw block builds in game: templates a 1.12.2 structure block
 * saved ({@code <world>/structures/<path>.nbt}), today's {@code <world>/generated/<namespace>/structures/<path>.nbt},
 * and folder data packs ({@code <world>/datapacks/<pack>/data/<namespace>/...}), where today's game reads a world's
 * template pools and templates. Asked only for a name no mod's jar has. Since 1.4.5.
 */
final class WorldFiles {

    private WorldFiles() {
    }

    /** @return the world's save folder, or null with no server running */
    @Nullable
    private static File root() {
        try {
            return DimensionManager.getCurrentSaveRootDirectory();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return the world's file of a template, or null */
    @Nullable
    static File template(ResourceLocation id) {
        File root = root();
        if (root == null) {
            return null;
        }
        List<File> candidates = new ArrayList<File>();
        // 1.12.2's TemplateManager keeps only the path.
        candidates.add(new File(new File(root, "structures"), id.getPath() + ".nbt"));
        candidates.add(new File(root, "generated/" + id.getNamespace() + "/structures/" + id.getPath() + ".nbt"));
        candidates.add(new File(root, "generated/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt"));
        for (File pack : packs(root)) {
            candidates.add(new File(pack, "data/" + id.getNamespace() + "/structure/" + id.getPath() + ".nbt"));
            candidates.add(new File(pack, "data/" + id.getNamespace() + "/structures/" + id.getPath() + ".nbt"));
        }
        for (File f : candidates) {
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    /** @return a world data pack's file of {@code folder} ({@code worldgen/template_pool}) and id, or null */
    @Nullable
    static File data(String folder, ResourceLocation id, String ext) {
        File root = root();
        if (root == null) {
            return null;
        }
        for (File pack : packs(root)) {
            File f = new File(pack, "data/" + id.getNamespace() + "/" + folder + "/" + id.getPath() + ext);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    /** Folder data packs in name order (zipped ones are not read). */
    private static List<File> packs(File root) {
        File[] packs = new File(root, "datapacks").listFiles(File::isDirectory);
        if (packs == null) {
            return new ArrayList<File>();
        }
        Arrays.sort(packs);
        return Arrays.asList(packs);
    }
}

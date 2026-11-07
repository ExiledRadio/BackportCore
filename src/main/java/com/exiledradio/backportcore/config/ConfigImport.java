package com.exiledradio.backportcore.config;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.CoreConfig;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.Loader;

import java.io.File;

/**
 * Settings that moved into this library from a backport's own config, read across once.
 *
 * <p>Forge writes a default only for keys a file does not have yet, and never looks anywhere else.
 * So a player who had switched, say, Village &amp; Pillage's {@code textures.modernTextures} off would
 * find it back on after updating, unless something carries the value over. This does, exactly once:
 * when {@code config/backportcore.cfg} does not exist yet. After that the value lives here, and the old
 * key, if the old file still has it, is left alone and does nothing.
 *
 * <p>It has to run before Forge first reads this mod's config, which it does straight after building
 * the mod's instance - so it runs from the mod's constructor, and only ever sets the fields Forge is
 * about to write out as the new file's values.
 */
public final class ConfigImport {

    /** Old file name (no extension), category, key - and what to do with the value. */
    private static final Moved[] MOVED = {
            // @Config puts every category under "general".
            new Moved("villagerbackport", "general.textures", "modernTextures") {
                @Override
                void apply(Property property) {
                    CoreConfig.textures.modernTextures = property.getBoolean(CoreConfig.textures.modernTextures);
                }
            },
    };

    private ConfigImport() {
    }

    public static void importOnce() {
        File dir = Loader.instance().getConfigDir();
        if (dir == null || new File(dir, BackportCore.MOD_ID + ".cfg").exists()) {
            return;
        }
        for (Moved moved : MOVED) {
            File old = new File(dir, moved.file + ".cfg");
            if (!old.isFile()) {
                continue;
            }
            try {
                // Read only: never saved, so the old file is untouched.
                Configuration config = new Configuration(old);
                if (config.hasCategory(moved.category) && config.getCategory(moved.category).containsKey(moved.key)) {
                    moved.apply(config.getCategory(moved.category).get(moved.key));
                    BackportCore.LOGGER.info("Took {}.{} from {}.cfg; it is set in {}.cfg from now on.",
                            moved.category, moved.key, moved.file, BackportCore.MOD_ID);
                }
            } catch (RuntimeException e) {
                BackportCore.LOGGER.warn("Couldn't read {}.{} from {}.cfg; using the default.", moved.category, moved.key,
                        moved.file, e);
            }
        }
    }

    private abstract static class Moved {
        final String file;
        final String category;
        final String key;

        Moved(String file, String category, String key) {
            this.file = file;
            this.category = category;
            this.key = key;
        }

        abstract void apply(Property property);
    }
}

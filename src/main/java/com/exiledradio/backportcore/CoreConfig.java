package com.exiledradio.backportcore;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Settings that belong to the whole family rather than to one backport.
 *
 * <p>Each category is its own class extending {@code Object}: Forge 1.12's config manager only
 * treats a field as a category when its type's superclass is exactly {@code Object}.
 *
 * <p>A setting that used to live in one backport's config is read across from there once, the
 * first time this file is written - see {@link com.exiledradio.backportcore.config.ConfigImport}.
 */
@Config(modid = BackportCore.MOD_ID, name = BackportCore.MOD_ID)
public final class CoreConfig {

    @Config.LangKey("config.backportcore.textures")
    @Config.Comment("1.14's Texture Update for vanilla blocks, items and mobs.")
    public static final Textures textures = new Textures();

    @Config.LangKey("config.backportcore.presentation")
    @Config.Comment("The parts of the game's look that follow the newest installed backport, as vanilla's did at that version.")
    public static final PresentationSettings presentation = new PresentationSettings();

    private CoreConfig() {
    }

    public static final class PresentationSettings {

        @Config.Comment({
                "Which panorama the title screen shows behind its buttons:",
                "  LATEST - the newest installed backport's (vanilla's choice),",
                "  a version, such as 1.14 - that version's, if a backport of it is installed (1.12.2 for 1.12.2's own),",
                "  ROTATE - a different installed version's each time the game starts.",
                "",
                "Client only; a server ignores it. Takes effect the next time the game starts."
        })
        public String panorama = "LATEST";
    }

    public static final class Textures {

        @Config.Comment({
                "Give vanilla's blocks, items and mobs the textures 1.14's Texture Update gave them,",
                "plus 1.15's enchantment glint. Off keeps 1.12.2's, and also puts back 1.12.2's look",
                "for the backports' own copies of things 1.12.2 already had (the tuxedo, red and",
                "Siamese cats).",
                "",
                "Client only; a server ignores it. Resource packs you pick, and other mods that change a",
                "vanilla texture on purpose, still win over it. Villagers, horses and water keep 1.12.2's",
                "textures either way: 1.14's don't fit 1.12.2's models or colors.",
                "",
                "Changing it in game reloads resources, as F3+T does."
        })
        public boolean modernTextures = true;
    }

    /** Writes edits made through the in-game config screen back onto the fields. */
    public static final class EventHandler {

        @SubscribeEvent
        public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (BackportCore.MOD_ID.equals(event.getModID())) {
                ConfigManager.sync(BackportCore.MOD_ID, Config.Type.INSTANCE);
            }
        }
    }
}

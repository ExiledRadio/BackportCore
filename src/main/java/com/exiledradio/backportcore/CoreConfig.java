package com.exiledradio.backportcore;

import com.exiledradio.backportcore.settings.Setting;

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

    @Setting.Feature(icon = "minecraft:painting")
    @Config.LangKey("config.backportcore.textures")
    @Config.Comment("Vanilla's textures as the newest installed backport's version has them.")
    public static final Textures textures = new Textures();

    @Setting.Feature(icon = "minecraft:map")
    @Config.LangKey("config.backportcore.presentation")
    @Config.Comment("The parts of the game's look that follow the newest installed backport, as vanilla's did at that version.")
    public static final PresentationSettings presentation = new PresentationSettings();

    @Setting.Feature(icon = "minecraft:item_frame", toggle = "newerLook")
    @Config.LangKey("config.backportcore.lookAndFeel")
    @Config.Comment("The newer look and feel the installed backports bring, part by part. Also on the Look & Feel page.")
    public static final LookAndFeelSettings lookAndFeel = new LookAndFeelSettings();

    @Setting.Feature(icon = "minecraft:grass")
    @Config.LangKey("config.backportcore.rendering")
    @Config.Comment("How the world's chunks are rebuilt when blocks change, as later versions do it.")
    public static final Rendering rendering = new Rendering();

    @Setting.Feature(icon = "minecraft:knowledge_book", toggle = "unlockRecipes")
    @Config.LangKey("config.backportcore.recipes")
    @Config.Comment("The backports' crafting recipes in the recipe book, unlocked the way vanilla unlocks its own.")
    public static final Recipes recipes = new Recipes();

    @Setting.Feature(icon = "minecraft:command_block")
    @Config.LangKey("config.backportcore.commands")
    @Config.Comment("Commands later versions added, as today's game has them. Each takes effect when a world loads.")
    public static final Commands commands = new Commands();

    @Setting.Feature(icon = "minecraft:structure_block", toggle = "dataStructures")
    @Config.LangKey("config.backportcore.structure")
    @Config.Comment("Structures read from today's data files (data/<namespace>/worldgen), in mod jars and in config/backportcore/structures.")
    public static final StructureSettings structure = new StructureSettings();

    private CoreConfig() {
    }

    public static final class StructureSettings {

        @Config.Comment({
                "Generate the jigsaw structures today's data files describe: every structure set in",
                "data/<namespace>/worldgen/structure_set of a mod jar, or of a structure pack (a folder, .zip or",
                ".jar laid out like a datapack) in config/backportcore/structures. Off: none of them start.",
                "",
                "Only changes chunks generated after it is changed. Takes effect the next time the game starts."
        })
        @Setting.Server
        @Setting.NewChunks
        @Config.RequiresMcRestart
        public boolean dataStructures = true;

        @Config.Comment({
                "Structure or structure set ids that are not generated, such as 'examplemod:tower' or",
                "'examplemod:towers'; 'examplemod:*' leaves out every one of that namespace.",
                "",
                "Takes effect the next time the game starts."
        })
        @Setting.Server
        @Setting.NewChunks
        @Setting.ListOf(Setting.Kind.ANY)
        @Config.RequiresMcRestart
        public String[] blacklist = {};
    }

    public static final class Commands {

        @Config.Comment({
                "1.20.3's /tick (operators, permission level 3): query, rate, freeze, unfreeze, step and",
                "sprint. Freezing stops everything but players and what they ride, and the client follows",
                "the server's tick rate. Off: no /tick, and the server runs at 20 ticks per second."
        })
        @Setting.Server
        public boolean tick = true;

        @Config.Comment("1.20.2's /random: random value, random roll and random reset, with saved sequences.")
        @Setting.Server
        public boolean random = true;

        @Config.Comment("1.19.4's /ride: ride mount and ride dismount (permission level 2).")
        @Setting.Server
        public boolean ride = true;

        @Config.Comment("1.21.2's /rotate: turn an entity to a rotation or to face a position or entity (permission level 2).")
        @Setting.Server
        public boolean rotate = true;

        @Config.Comment("1.15's /spectate: watch an entity in spectator mode, or stop (permission level 2).")
        @Setting.Server
        public boolean spectate = true;

        @Config.Comment("1.14's /teammsg and its alias /tm: a message to your own team (everyone may use it).")
        @Setting.Server
        public boolean teamMsg = true;

        @Config.Comment("26.3's /swing: make entities swing an arm, with an animation and a duration (permission level 2).")
        @Setting.Server
        public boolean swing = true;
    }

    public static final class Recipes {

        @Config.Comment({
                "Unlock the backports' crafting recipes the way vanilla unlocks its own: holding an iron",
                "ingot gives the smithing table's recipe, a stick or coal the campfire's. The recipe toast",
                "shows and the recipe appears in the recipe book. Off: the recipes still craft but never",
                "show in the recipe book.",
                "",
                "Recipes already unlocked stay unlocked."
        })
        @Setting.Server
        public boolean unlockRecipes = true;
    }

    /** Current vanilla's "Chunk Builder" video setting ({@code prioritizeChunkUpdates}, from 1.18.2), by its own value names. */
    public enum BlockUpdates {
        /** "Threaded": every rebuild goes to the chunk builder threads (current vanilla's default). */
        NONE,
        /** "Semi Blocking": only chunks the player changed are rebuilt at once. */
        PLAYER_AFFECTED,
        /** "Fully Blocking": also every changed chunk near the camera, the only rule before 1.18.2. */
        NEARBY
    }

    public static final class Rendering {

        @Config.Comment({
                "Which changed chunks the game rebuilds at once, holding up the frame, instead of on the",
                "chunk builder threads. Current vanilla's \"Chunk Builder\" video setting (1.18.2 on):",
                "  NONE - none; every rebuild is threaded (\"Threaded\", current vanilla's default; a block",
                "    you break can leave a hole for a frame),",
                "  PLAYER_AFFECTED - only chunks you changed yourself, by placing or breaking a block",
                "    (\"Semi Blocking\"),",
                "  NEARBY - also every changed chunk within about 28 blocks of the camera (\"Fully Blocking\",",
                "    the only rule before 1.18.2). Blocks the server changes nearby, such as a kelp or sugar",
                "    cane column breaking, then cost a rebuild on the render thread every tick.",
                "",
                "Client only; a server ignores it. Takes effect at once. OptiFine, Celeritas, Vintagium and",
                "Nothirium replace this part of the game with their own threaded rebuilds; with them it does nothing."
        })
        @Setting.Client
        public BlockUpdates blockUpdates = BlockUpdates.PLAYER_AFFECTED;
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
        @Setting.Client
        @Config.RequiresMcRestart
        public String panorama = "LATEST";

        @Config.Comment({
                "Where the pause menu's Report Bugs button goes. By default, the backports' Discord.",
                "A modpack can point it at its own page. Leave it empty to hide the button.",
                "",
                "Client only; a server ignores it."
        })
        @Setting.Client
        public String reportBugsLink = "https://discord.gg/kxQvMDJBTN";

        @Config.Comment({
                "Where the pause menu's Give Feedback button goes. By default, the backports' Discord.",
                "A modpack can point it at its own page. Leave it empty for Mojang's feedback site.",
                "",
                "Client only; a server ignores it."
        })
        @Setting.Client
        public String giveFeedbackLink = "https://discord.gg/kxQvMDJBTN";
    }

    /**
     * The Look &amp; Feel page's switches ({@link com.exiledradio.backportcore.presentation.LookArea}). Client only: each
     * player picks their own look, and a server ignores these. Off puts that part back to 1.12.2's; the backports stay
     * installed and their content is unchanged.
     */
    public static final class LookAndFeelSettings {

        @Config.Comment({
                "Use the newer look and feel the installed backports bring. Off: everything below looks and",
                "feels as 1.12.2, and each switch keeps its own choice for when this is on again.",
                "",
                "Client only; a server ignores it."
        })
        @Setting.Client
        public boolean newerLook = true;

        @Config.Comment({
                "The newest version whose look is used: Latest (the newest installed backport), or a version",
                "such as 1.16 to stop there even with later backports installed.",
                "",
                "Client only; a server ignores it."
        })
        @Setting.Client
        public String newestLook = "LATEST";

        @Config.Comment("The newer world-loading screens: the spawn-area percentage, the chunk grid, the panorama behind it. Off: 1.12.2's.")
        @Setting.Client
        public boolean loadingScreens = true;

        @Config.Comment("The Mojang Studios screen when resources load. Off: none, as 1.12.2.")
        @Setting.Client
        public boolean startupScreen = true;

        @Config.Comment("The newer title screen: panorama, logo, fade-in and Accessibility button. Off: 1.12.2's.")
        @Setting.Client
        public boolean titleScreen = true;

        @Config.Comment("The newer versions' splash texts. Off: 1.12.2's list.")
        @Setting.Client
        public boolean splashes = true;

        @Config.Comment("Newer menus and buttons: backgrounds, button text, the pause and death screens, world backups. Off: 1.12.2's.")
        @Setting.Client
        public boolean menus = true;

        @Config.Comment("The newer options screens (Accessibility, Mouse, Controls, Key Binds) and the options on them. Off: 1.12.2's.")
        @Setting.Client
        public boolean optionsScreens = true;

        @Config.Comment("The newer Video Settings: layout, sections, preset and its options. Off: 1.12.2's.")
        @Setting.Client
        public boolean videoSettings = true;

        @Config.Comment("Command suggestions and the newer chat options. Off: 1.12.2's Tab completion and chat.")
        @Setting.Client
        public boolean chat = true;

        @Config.Comment("The newer F3 screen, F3 keys, Debug Options and charts. Off: 1.12.2's.")
        @Setting.Client
        public boolean debug = true;

        @Config.Comment("The music toast and the newer toast textures. Off: 1.12.2's.")
        @Setting.Client
        public boolean toasts = true;

        @Config.Comment("The creative order, slot highlights and newer container textures. Off: 1.12.2's.")
        @Setting.Client
        public boolean inventory = true;

        @Config.Comment("The newer menu music, in-game music and music options. Off: 1.12.2's.")
        @Setting.Client
        public boolean music = true;

        @Config.Comment("Each version's fog, sky, clouds and sunrise. Off: 1.12.2's.")
        @Setting.Client
        public boolean sky = true;

        @Config.Comment("Vanilla's text as the newer versions word it. Off: 1.12.2's wording.")
        @Setting.Client
        public boolean wording = true;
    }

    public static final class Textures {

        @Config.Comment({
                "Give vanilla's blocks, items and mobs the textures of the newest installed backport's",
                "version: 1.14's Texture Update to begin with, then every later version's retextures up",
                "to that one, such as 1.17's ores with Caves & Cliffs Backport. Off keeps 1.12.2's, and",
                "also puts back 1.12.2's look for the backports' own copies of things 1.12.2 already had",
                "(the tuxedo, red and Siamese cats).",
                "",
                "Client only; a server ignores it. Resource packs you pick, and other mods that change a",
                "vanilla texture on purpose, still win over it. Villagers, horses and water keep 1.12.2's",
                "textures either way, and a texture a later version redrew for a new model keeps its older",
                "look: those don't fit 1.12.2's models or colors.",
                "",
                "Changing it in game reloads resources, as F3+T does."
        })
        @Setting.Client
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

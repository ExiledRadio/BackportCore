# 1.4.8

*   **Fixed: the settings screen's update notices** didn't know the latest family releases, so a backport one version behind could show as up to date.

# 1.4.7

*   **Every backport now looks and feels like its own version, even on its own.** What each update changed beyond its content is Backport Core's now and follows the newest backport you have installed: the options, chat, F3 keys and F3 text, menus and loading screens, logos and button textures, splashes and wording. With only Caves & Cliffs Backport you get 1.13's command suggestions, 1.14's Scroll Sensitivity, 1.15's Sneak and Sprint toggles and 1.16's F3 + F4 game mode switcher, as in 1.18. Music and title panoramas stay in each backport.
*   Your settings carry over, and older versions of the backports keep working with this Core. Their next versions (Update Aquatic 1.2.3, Village & Pillage 2.7.5, Buzzy Bees 1.1.2, Nether Update 1.1.2, Caves & Cliffs 1.1.5, Wild Update 1.1.3, Trails & Tales 1.0.4, Tricky Trials 1.0.2) leave these pieces to Core and need 1.4.7.

# 1.4.6

*   **Fixed: a crash 20 to 30 seconds after launch on Android launchers** (PojavLauncher, Zalith and others), and no sound before it. Their LWJGL lacks a call Backport Core used when opening the sound device; the sound now opens as in 1.12.2 there, without the device choice and Directional Audio.
*   **Fixed: the block on the "New Recipes Unlocked!" toast sat a pixel low**, both the crafting table and the furnace's. Turning off the toasts' newer look now removes both, as in 1.12.2.
*   **Fixed: lit redstone torches broke into black and purple particles** once a backport of 1.21 or later was installed. 1.21's torch model named its particle texture in a way 1.12.2 cannot read.

# 1.4.5

*   **Fixed: holes in the ground until F3 + A.** While moving, especially fast or back and forth, sections of the world could stay invisible, showing the sky through the ground. As in 1.18, a part of the world that comes into view is always redrawn.
*   **Fixed: the crafting table missing from the "New Recipes Unlocked!" toast.** As in 1.13, it is drawn small behind the recipe's output.
*   **Data-driven structures**: Core generates the jigsaw structures today's data files describe. A modern structure mod's `data/` folder, copied into a 1.12.2 jar or into a pack (folder or .zip, laid out like a datapack) in `config/backportcore/structures/`, generates as in 26.3: the same spacing, biomes, heights and layouts, found by `/locate structure`. Turn it off with `structure.dataStructures`, or leave out single structures with `structure.blacklist`.
*   **Today's jigsaw block**, to build and test jigsaw structures in game as in 26.3: its screen (pools, names, final state, priorities, joint, levels, Keep Jigsaws) and Generate. For operators in creative mode: `/give @s backportcore:jigsaw`, or Operator Utilities with 1.19.3's tabs. Structure blocks save jigsaw blocks with their settings, and the backports' jigsaw structures read those files. Pools go in a world data pack (`datapacks/<pack>/data/<namespace>/worldgen/template_pool/`).

*   **Realistic Terrain Generation** (and RTG Unofficial): the backports' surface biomes now generate in RTG worlds, on RTG's terrain for the nearest vanilla biome, with their own trees and features. RTG turned every biome it did not know into plains. Desert temples, igloos, jungle temples and witch huts in RTG worlds go by the biome RTG really gives a chunk.
*   **Fixed: ocean monuments on land and in lakes in Realistic Terrain Generation worlds.** A monument there now also needs deep water where it stands, so it only generates in RTG's deep oceans, and `/locate` and explorer maps point to the same places.
*   **Fixed: leaves left floating where a structure cut trees.** A village, outpost, temple or any backport structure built where trees stood cleared their logs but left the leaves in the air. The leaves those trees lose now go with them (any mod's trees), as cut-off leaves decay in today's game.
*   **Update notices**: the settings screen shows when a newer version of Backport Core or any installed backport is out, on the Family page and the mod's own page, with a link to CurseForge. It uses Forge's update check, or the versions this Core knows when that check is off or offline.
*   **Look & Feel is now part of the Family page**, after Core's own settings, instead of its own tab.
*   **Simulation Distance** (1.18, Video Settings): mobs, machines, crops and redstone run only this many chunks from a player, however far you can see. Your own worlds follow the setting; a server uses `simulation-distance` in server.properties, as 1.18 does. Spawn chunks and chunks a mod keeps loaded run as before.
*   **Chunk Builder** (1.18.2, Video Settings): Threaded, Semi Blocking or Fully Blocking, the same setting as Core's config option for it.
*   **Improved Transparency** (Video Settings, the Fabulous! preset): rain and snow in front of water and glass are drawn over them instead of behind them.
*   **Exclusive Fullscreen Mode** (Fullscreen Resolution before 26.3): choose the resolution and refresh rate exclusive fullscreen uses, or Current. (Not with OptiFine, which has its own.)
*   **Borderless fullscreen on Cleanroom**: with Exclusive Fullscreen off (26.3's default), Fullscreen is now a window that fills the monitor there too, so the screen no longer flashes black, other windows stay on their monitors, and the game stays up when you click another window.
*   **Fixed: with OptiFine, borderless fullscreen turned into exclusive fullscreen** (OptiFine applied its own Fullscreen Mode on entering it), so the game minimized when you clicked another window.
*   **Your arm swings when you drop an item**: with the drop key from 1.15 (Buzzy Bees Backport or later), and from 1.16 on (Nether Update Backport or later) also when you throw an item out of your inventory, click outside the window with an item held, or drop one in creative.
*   **1.21.2's clouds**: once a backport of 1.21.2 or later is installed (Tricky Trials or Game Drops Backport), clouds are drawn as in today's game, one box per cloud cell with only its outside faces. The thin grid lines that showed through the clouds' undersides, more so at a far Cloud Distance, are gone. Fast clouds are today's flat clouds. Not with a shader pack, which draws 1.12.2's.
*   **Today's light**: once a backport of 26.1 or later is installed (Game Drops Backport), the world is lit as in today's game: torch light is warm yellow that turns white where it is brightest, moonlight is blue, the Nether has a warm glow and the End a green-gray one even in the dark. Not with a shader pack or an OptiFine resource pack's own lightmap, which keep theirs.
*   **26.3's dark title bar** on Windows: with Game Drops Backport, the game window's title bar is dark when Windows is in dark mode, and follows it when it changes.
*   **26.2's title screen**: with Game Drops Backport, Friends, Language and Accessibility are one row of icon buttons under Mods and Minecraft Realms, with Options and Quit Game below. Friends says it is not available in this version.
*   **1.19.3's creative tabs** with Wild Update Backport or any later backport: Building Blocks, Colored Blocks, Natural Blocks, Functional Blocks, Redstone Blocks, Tools & Utilities, Combat, Food & Drinks, Ingredients and Spawn Eggs, each with 1.19.3's items in 1.19.3's order (26.3's with Game Drops Backport). Operator Utilities shows with the new Operator Items Tab in Controls. Other mods' tabs are on the pages after. The Spawn Eggs tab shows the pig spawn egg, or the creeper's from 1.21.5 (with Tricky Trials or Game Drops Backport). The Look & Feel inventory switch turns it off.

# 1.4.4

*   **Music discs from the backports give their own comparator signal** from a jukebox, as today's game does (Game Drops Backport's Bounce gives 8); 1.12.2's twelve discs are unchanged.
*   **26.x's debug screen lines** with Game Drops Backport: "Day #" on its own line, the player's speed, and the spawn counts by today's mob categories, each one its own entry in Debug Options (off by default, as in 26.3).
*   **Underground biomes on the debug screen and in colors**: a backport's biome under ground (Game Drops Backport's sulfur caves) is the F3 biome where you stand, and grass, leaves and water there take its colors, as in today's game.
*   **For world generation**: a backport's features may now finish blocks just past the area 1.12.2 lets a chunk write (far sulfur spikes, the outer tuff of large sulfur springs), once the chunks there have generated; the waiting work is saved with the world.
*   **26.3's map markers** with Game Drops Backport: when you are off the edge of a map, your marker still points the way you face.
*   **26.3's section headings** in Video Settings with Game Drops Backport: bold, underlined and centered.
*   **Borderless fullscreen**, as 26.3 has it by default, with Game Drops Backport: Fullscreen is a window without a frame that fills the monitor, so switching to other windows is instant. Turn Exclusive Fullscreen on in Video Settings for 1.12.2's fullscreen. (Not on Cleanroom, which draws its own window.)
*   **A smaller debug screen**, as in 26.3, with Game Drops Backport: F3 has its own GUI Scale at the bottom of Debug Options (F3 + F6), Auto by default (half the largest scale), or Unchanged for the game's.
*   **One boat for every backport's wood**: mangrove, cherry, bamboo rafts, pale oak and poplar boats, and every boat with chest, are now one boat and one boat with chest shared by the whole family, looking and handling as before. Boats already in your worlds are kept, with their wood, what is in their chests and whoever rides them.
*   **For the backports**: today's explorer map icons (abandoned camp, ancient city, desert pyramid, mineshaft, warm ocean ruins), explorer maps that lead to a new structure each time, today's item names in loot read from newer files, wool stairs and slabs that muffle vibrations, and the bounce game event.
*   **1.20.3's `/tick`**, the debugging favorite, as today's game has it: `/tick query`, `rate`, `freeze`, `unfreeze`, `step` and `sprint` (operators). Freezing stops everything but players and what they ride, a step runs a few ticks at a time, a sprint runs as fast as the server can and reports how fast it went, and your client follows the server's tick rate so animations and movement stay smooth, while menus and the title screen always run at full speed.
*   **More of today's commands**: `/random` (with today's saved sequences, so a world gives the same values as in the current game), `/ride`, `/rotate`, `/spectate`, `/teammsg` (or `/tm`) and 26.3's `/swing`, which makes mobs and players swing an arm, with a stab or no animation and a duration of your choice. Each command has its own switch in the settings.
*   **26.3's pause menu row**: with a backport that supplies its icons (Game Drops Backport), Report Bugs, Give Feedback, Friends and Player Reporting are four icon buttons under Advancements and Statistics. Friends and Player Reporting say they are not available in this version.
*   **Fixed land animals spawning in the sea** (Lycanites' bobekos in cold oceans, among others): chunk generation put them on the sea floor inside kelp, seagrass and other blocks holding water, and they floated up. A mob that spawns on the ground now needs a spot without water, as in 1.13 and later.
*   **Fixed a crash on world load with Alex's Mobs (Legacy)** ("Index 6 out of bounds for length 6" in `getSpawnableList`): a creature type a mod adds late, after the biome spawn lists were first read, now gets its own empty list instead of crashing, whichever mods add types and in whatever order.
*   **26.3's `/locate poi`**: finds the nearest point of interest of a type or tag within 256 blocks (beds, nether portals, brewing stands, cauldrons, and the backports' workstations, bells, bee nests, lodestones and lightning rods), as today's game does, including areas you visited that are no longer loaded.
*   **A Game Rules screen**, as the newest installed version draws it, with 26.1's search; with Game Drops Backport also in a world, from World Options. **Optimize World** in Edit World, as in 1.13.
*   **Today's creative order**: with Game Drops Backport, every creative tab lists its items in 26.3's order.
*   **1.21.5's spawn eggs**: from 1.21.5 on, every spawn egg has its own texture, for vanilla's mobs and every backport's. The status effect icons are today's too.
*   **Water stays in waterlogged leaves**, as in today's game (with Fluidlogged API).
*   **The backports' structures** (camps, trial chambers, trail ruins) are laid out by one shared modern jigsaw placer, block for block as before.

# 1.4.3

*   **The newer textures are in the Resource Packs screen**, as "Backport Textures": move it to the left column to keep 1.12.2's textures. It is the same switch as Modern Textures in the settings, and it always sits just above Default, so every pack you pick still wins over it.
*   **Fixed: lit redstone torches showed a red square around the head** with the newer textures on. The torch now uses 1.21's own model, as its texture expects.

# 1.4.2

*   **Fixed a white box behind the book and quill** (and behind other modded screens that draw a see-through texture over the blurred background).

# 1.4.1

*   **Fixed a crash on launch with SRParasites** (and any mod that hooks the same sky calls): Backport Core's sunrise, sky color and cloud height changes now adjust vanilla's results instead of replacing the calls other mods hook into.

# 1.4.0

*   **Look & Feel page**: one place in the Backport settings screen for every backport's newer look, from the loading screens to the title screen, menus, chat and F3, each with its own switch, and one switch at the top that turns them all off for plain 1.12.2.
*   **The pause menu's Mods button is where Forge puts it**: a full-width row above Save and Quit, as Forge has done since it brought the button back, with Give Feedback back in its place.
*   **1.14's shorter crouch**, for Village & Pillage Backport to turn on: sneaking makes a player 1.5 blocks tall with eyes at 1.27, a crouching player fits through a 1.5-block gap, and a player under a 1.5-block ceiling stays crouched there, drawn crouching and moving at sneaking speed.
*   **Newer map icons**: backports can give maps today's village, swamp hut, jungle temple and trial chambers icons.
*   **Torches on glass panes and iron bars**, as in 1.14, for Village & Pillage Backport to turn on.
*   **Fixed two command suggestion lists on Cleanroom**: Cleanroom 0.6 and later show their own, so the backports' list now stays off there.
*   **Fixed a blank square in place of a feature's icon on the settings screen** when its item comes from another installed backport, such as Buzzy Bees' campfire with Village & Pillage installed.
*   A feature's settings icon can name several items, and the first one installed is shown: Caves & Cliffs' goats show Wild Update's goat horn when it is installed.

# 1.3.1

*   **`/seed` as in 1.15**, with a 1.15 or newer backport installed: the seed is green, and a click copies it.
*   **`/locate` is only today's two forms**, `/locate structure` and `/locate biome`. 1.12.2's names (`/locate Stronghold`, `Village`, `Temple` ...) and the bare ids of 1.13 to 1.18 are gone from the command and its tab completion, as in today's game.
*   **Sounds no longer cut each other off in busy places.** 1.12.2 can play 28 sounds at once and cut the oldest when a new one starts, so long sounds like the sculk shrieker's shriek were cut short among clicking sensors. With a backport from 1.14 or later installed, the game gets 1.14's 247 (and 8 for music).
*   **Sounds carry as far as in vanilla.** Sounds that current Java hears from farther or nearer than 16 blocks (a ravager's roar, a fox's screech, the sculk sounds) now do so here too.
*   **Fixed the F3 key combinations on Cleanroom**: F3 + F4 (the game mode switcher), F3 + N and the other newer combinations did nothing there.

# 1.3.0

What Caves & Cliffs Backport needs, and the 1.18 to 1.21.11 menus and settings that newer backports use.

*   **1.18 worlds**: villages, outposts, shipwrecks, ocean ruins, buried treasure and ruined portals start where 1.18 puts them in Caves & Cliffs worlds, and the ground is shaped around them. Blocks below y 0 and above y 255 hold water. Features a world's generator already placed are not added twice.
*   **`/locate biome`**, as in current Java, for vanilla's biomes and every backport's.
*   **Video Settings** as in 1.21.11: Display, Quality & Performance and Preferences sections, a Fast/Fancy/Fabulous! preset, Cloud Distance, Weather Effect Radius, Texture Filtering, Show Vignette and Chunk Fade Time. With OptiFine, its Video Settings take the same layout, with OptiFine's Shaders, Quality, Details, Performance, Animations and Other as a section.
*   **Controls** as in 1.18, with Toggle for Attack and Use Item.
*   **Music and Sounds**: Music Frequency, the Music Toast and a UI volume.
*   **F3** as in 1.21.9: every part of the debug screen can be Off, In F3 or Always (F3 + F6).
*   Copper from different backports ages and waxes the same way.
*   **Vanilla's textures follow the newest installed backport**: with Caves & Cliffs Backport, 1.17's new ores and every other retexture through 1.18.2; with Tricky Trials Backport, through 1.21.11. Only Village & Pillage gives 1.14's textures, as before. Textures that newer versions changed in a way 1.12.2 can't show keep their older look. The Texture Update switch still brings back 1.12.2's.
*   **Superflat worlds** get only what their preset lists, as in vanilla: no trial chambers, shipwrecks, ruined portals or other newer structures floating over the ground, and the backports' flowers, bushes and other features only with `decoration`.
*   **Command suggestions** as in 1.13, for backports that turn them on: the server's completions list above the chat box as a command is typed, with Tab, the arrow keys and Esc. Works on any server.
*   **Furnace recipe book** as in 1.13, for backports that turn it on, and the same book for other backports' cooking blocks.

# 1.2.0

*   **The backports' recipes unlock like vanilla's.** Pick up an iron ingot and the smithing table's recipe unlocks; a stick or coal gives the campfire's. The recipe toast shows and the recipe appears in the recipe book. Each recipe unlocks on the items current Java unlocks it on. This covers every crafting recipe of every backport installed (Village & Pillage, Update Aquatic, Buzzy Bees, Nether Update), with no update to them needed. Before, none of their recipes ever showed in the recipe book.
*   New setting **Recipes > Unlock Recipes** (on by default, server side) to turn it off.

# 1.1.0

Everything the update backports share for their next releases: waterlogging, today's structure files, seed parity, each version's look and feel, swimming and crawling poses, and the backports' items in vanilla's creative tabs.

Fluidlogged API 3.3.3 or newer comes with Backport Core and only adds waterlogging. Without it, the backports still load and their blocks stay dry.

*   **Waterlogging** for the backports' blocks, as in 1.13: lanterns, sea pickles, kelp and the rest keep the water they are placed in, buckets fill and empty them, and water flows in and out of them. Vanilla's own blocks keep Fluidlogged API's settings.
*   **Today's structure files**, Mojang's own, read into 1.12.2 blocks for the backports' structures: shipwrecks, ocean ruins and later ones. Structures are placed chunk by chunk without loading their neighbors, and wall torches stay up at chunk edges.
*   **Structures on the sea floor**: they can be planned on the sea floor as well as the surface, on every terrain Backport Core already reads.
*   **Jigsaw structures** as 1.16 builds them, from Mojang's own pool files: bastion remnants first. For the same seed, a bastion is laid out piece for piece as in 1.16.5.
*   **Seed parity for nether structures**: with Nether Update Backport, bastions and nether fortresses share 1.16's slots, so a seed puts a bastion or a fortress in the same chunks as vanilla 1.16.5. Fortresses already in a world stay where they are.
*   **Nether floors**: structures can be planned on the nether's floors, lava sea and caves included, before the chunks exist.
*   Ruined portals' weathering (moss, cracks, crying obsidian, blackstone in the nether) and the other structure processors of today's files.
*   Mobs in structure files spawn with their usual gear, and a backport can name its own mob for one of today's.
*   **Seed parity for the overworld**: with backports installed, a seed shows the biomes and structures that Minecraft's seed maps show for the newest installed backport's version, 1.13 to 1.16. Each backport brings its version's structure spacing and biome rules, and the newest one wins. With 1.13 or later installed, strongholds, the spawn point, temples, villages and the rare biome variants (sunflower plains, ice spikes and the like) are placed as in that version; from 1.15, the biome of each block blends between biomes as in 1.15.
*   **1.14's player poses** for the backports to use: a swimming player is 0.6 blocks tall with their eyes at 0.4, a player with no room to stand crawls flat until there is room, and other players see both. Swimmers and crawlers are drawn lying along the swim, armor included, and the camera eases down to the new eye height instead of jumping. Nothing changes until a backport that swims is installed.
*   A small coremod of the library's own gives these the hooks 1.12.2 lacks. Each of its five patches does exactly what vanilla does unless a backport's swimming is on, and steps aside, keeping vanilla's behavior, when another mod has rewritten the same code.
*   **Third person in a tunnel** (patch P18): crawling or swimming through a one-block tunnel and pressing F5 no longer shows the sky through the stone. As in 1.14, the game works out which chunks to draw from the camera, not from the player's eyes, and again whenever the camera moves.
*   **Threaded chunk rebuilds** (patch P17): blocks the server changes near the camera, such as a kelp column breaking one block a tick, no longer rebuild their chunk on the render thread every tick; only your own placing and breaking does. It is current vanilla's "Chunk Builder" setting, `rendering.blockUpdates` in `backportcore.cfg`: `NONE` ("Threaded"), `PLAYER_AFFECTED` ("Semi Blocking", the default) or `NEARBY` ("Fully Blocking", 1.12.2's rule). Changing it in game takes effect at once. With OptiFine, Celeritas, Vintagium or Nothirium it does nothing; they thread rebuilds themselves.
*   **Today's `/locate`**: `/locate structure <id>` finds the backports' structures by today's ids (`minecraft:shipwreck`, `minecraft:village_plains`, `minecraft:bastion_remnant` ...) and tags (`#minecraft:village` finds the nearest village of any type), searching in today's order and answering as today's game does: "The nearest minecraft:shipwreck is at [x, ~, z] (n blocks away)", the coordinates a click away from a teleport. Tab completion lists every installed backport's structures. 1.12.2's own names (`/locate Stronghold`, `Temple`, ...) still work, and 1.12.2's structures also answer to today's ids (`minecraft:stronghold`, `minecraft:fortress`, and with a 1.13 or later backport the four temples one by one). Nothing is generated or loaded to find a structure.
*   **Each version's look and feel**, following the newest installed backport as vanilla's did at that version: the title-screen panorama (sharp and turning, as 1.13 and later draw it), the world-loading screen (1.13's percentage, 1.14's colored chunk grid, 1.16's "Reading world data..."), the backports' options on the right options screens, 1.14's Accessibility Settings and Mouse Settings, the splash texts, the title-screen music, the F3 key combinations and the fog, built from every installed backport's additions and removals. A version's changed defaults are applied once and then left to you.
*   **One settings screen for every backport**, under Mod Options. Each feature is a row with an on/off switch and its settings underneath. Search, sliders, a list editor that checks ids, and badges for settings that need a restart, only touch new chunks, or are your own. On a server, operators can change server settings from it. The `.cfg` files don't change.
*   **The pause menu** has Mod Options up with the other menus, where Give Feedback was, and Report Bugs opens the backports' Discord. `presentation.reportBugsLink` changes the link, or hides the button when empty.
*   `presentation.panorama` in `config/backportcore.cfg`: `LATEST` (the newest backport's panorama), a version such as `1.14`, or `ROTATE` for a different one each time the game starts.
*   With Backport Core alone, the title screen and every other screen stay 1.12.2's.
*   **The backports' items in vanilla's creative tabs**: no backport has a tab of its own any more. Each block and item is in the tab vanilla 1.19.2 gives it (Building Blocks, Decoration Blocks, Redstone, Transportation, Miscellaneous, Foodstuffs, Tools, Combat, Brewing), next to its vanilla neighbor: stripped logs after the logs, new walls after the cobblestone wall, bee nests and beehives where 1.19.2 shows them, each spawn egg among the others in alphabetical order. The places follow whichever backports are installed.
*   Items newer than 1.19.2 are placed where today's game puts them (cherry wood after mangrove, the copper bulbs after the redstone lamp, the crafter after the dropper).
*   The search tab, JEI and other mods' items in those tabs are unchanged.
*   **Sea pickles belong to Update Aquatic Backport**, not Village & Pillage: when two backports need the same block, it belongs to the one it fits best. Blue ice stays Village & Pillage's.
*   **An icon** in the mod list.
*   **In eighteen languages**, config screen included: English, Spanish (Spain and Latin America, including Argentina, Uruguay and Venezuela), Brazilian Portuguese, French, German, Russian, Simplified and Traditional Chinese, Japanese, Korean, Italian, Polish, Turkish, Ukrainian and Dutch. Block and item names use Minecraft's own translations.
*   The texture switch now shows its name, "1.14 Textures", in the config screen instead of `modernTextures`.
*   Nothing changes until a backport uses these; with Backport Core alone, worlds generate as before.

# 1.0.0

First release: the code and textures the update backports share, in one library. Village & Pillage Backport 2.4.0 is the first backport to require it.

*   **1.14's Texture Update** for vanilla's blocks, items and mobs, and 1.15's enchantment glint, with the switch back to 1.12.2's look: `textures.modernTextures` in `config/backportcore.cfg`, or Backport Core's config screen. A setting made in an earlier mod's config is carried over the first time the game starts.
*   **Moving a world's blocks and items** over from another mod's copies, on demand or when that mod is removed.
*   **Hiding duplicates** when two mods add the same block: one of each pair keeps its recipe and creative tab.
*   **Planning structures** on vanilla, superflat, Open Terrain Generator, Biomes O' Plenty, Realistic Terrain Generation and 1.18-height terrain.
*   **Registries** other mods can add villager and wandering trader trades and smithing upgrades to.

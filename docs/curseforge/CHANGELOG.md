# 1.3.1

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

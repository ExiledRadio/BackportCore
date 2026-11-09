# 1.1.0

Fluidlogged API (3.3.3 or later) comes with Backport Core on CurseForge and only adds waterlogging.

*   **Fluidlogged API is optional** in code: Backport Core and the backports load and play without it, and their
    `Waterloggable` blocks are then as dry as before 1.13. `Waterloggable` no longer extends `IFluidloggable`;
    with Fluidlogged API installed, patch P19 adds it to every class that implements `Waterloggable`. New
    `Waterlogging.isAvailable()`. `WaterRules` moved to `waterlog.fluidlogged`. Without Fluidlogged API, older
    worlds' Fluidlogged API water is kept in their chunks for when it is installed again. The `@Mod` dependency is
    now `after:fluidlogged_api@[3.3.3,)`.
*   **Third person in a tunnel** (patch P18): crawling or swimming through a one-block tunnel and pressing F5 no
    longer shows the sky through the stone. As in 1.14, the game works out which chunks to draw from the camera,
    not from the player's eyes, and again whenever the camera moves.
*   **Threaded chunk rebuilds** (patch P17): blocks the server changes near the camera, such as a kelp column
    breaking one block a tick, no longer rebuild their chunk on the render thread every tick; only your own
    placing and breaking does. It is current vanilla's "Chunk Builder" setting, `rendering.blockUpdates` in
    `backportcore.cfg`: `NONE` ("Threaded"), `PLAYER_AFFECTED` ("Semi Blocking", the default) or `NEARBY`
    ("Fully Blocking", 1.12.2's rule). Changing it in game takes effect at once. With OptiFine, Celeritas,
    Vintagium or Nothirium it does nothing; they thread rebuilds themselves.
*   **Today's `/locate`**: `/locate structure <id>` finds the backports' structures by today's ids
    (`minecraft:shipwreck`, `minecraft:village_plains`, `minecraft:bastion_remnant` ...) and tags
    (`#minecraft:village` finds the nearest village of any type), searching in today's order and answering as
    today's game does: "The nearest minecraft:shipwreck is at [x, ~, z] (n blocks away)", the coordinates a click
    away from a teleport. Tab completion lists every installed backport's structures. 1.12.2's own names
    (`/locate Stronghold`, `Temple`, ...) still work, and 1.12.2's structures also answer to today's ids
    (`minecraft:stronghold`, `minecraft:fortress`, and with a 1.13 or later backport the four temples one by
    one). Nothing is generated or loaded to find a structure.
*   **Jigsaw structures** as 1.16 builds them, from Mojang's own pool files: bastion remnants first. For the
    same seed, a bastion is laid out piece for piece as in 1.16.5.
*   **Seed parity for nether structures**: with Nether Update Backport, bastions and nether fortresses share
    1.16's slots, so a seed puts a bastion or a fortress in the same chunks as vanilla 1.16.5.
    Fortresses already in a world stay where they are.
*   **Nether floors**: structures can be planned on the nether's floors, lava sea and caves included, before the
    chunks exist.
*   Ruined portals' weathering (moss, cracks, crying obsidian, blackstone in the nether) and the other structure
    processors of today's files.
*   Mobs in structure files spawn with their usual gear, and a backport can name its own mob for one of today's.
*   Ruined portals in the nether have the nether's own spacing (25 chunks, not 40), as in 1.16.
*   Structure pieces made of a single jigsaw block (a bastion's gold blocks) are placed; slabs in structures are the
    right half (or double), including the backports' own slabs and slabs a structure names without a type.
*   **Overworld seed check** for the backports (a developer tool): started with
    `-Dbackportcore.seeddump=<folder>`, a server writes its overworld's biome map, structure starts,
    strongholds, spawn search and slime chunks, for comparing with vanilla 1.13.2's and 1.14.4's own code
    (`tools/vanilla-oracle`). Without the setting nothing runs.
*   Nothing changes until a backport uses these; with Backport Core alone, worlds generate as before.
*   **Each version's look and feel**, following the newest installed backport as vanilla's did at that
    version: the title-screen panorama (sharp and turning, as 1.13 and later draw it), the world-loading
    screen (1.13's percentage, 1.14's colored chunk grid, 1.16's "Reading world data..."), the backports'
    options on the right options screens, 1.14's Accessibility Settings and Mouse Settings, the splash
    texts, the title-screen music, the F3 key combinations and the fog, built from every installed
    backport's additions and removals. A version's changed defaults are applied once and then left to you.
*   `presentation.panorama` in `config/backportcore.cfg`: `LATEST` (the newest backport's panorama), a
    version such as `1.14`, or `ROTATE` for a different one each time the game starts.
*   With Backport Core alone, the title screen and every other screen stay 1.12.2's.



*   **The backports' items in vanilla's creative tabs**: no backport has a tab of its own any more. Each block and
    item is in the tab vanilla 1.19.2 gives it (Building Blocks, Decoration Blocks, Redstone, Transportation,
    Miscellaneous, Foodstuffs, Tools, Combat, Brewing), next to its vanilla neighbor: stripped logs after the logs,
    new walls after the cobblestone wall, bee nests and beehives where 1.19.2 shows them, each spawn egg among the
    others in alphabetical order. The places follow whichever backports are installed.
*   Items newer than 1.19.2 are placed where today's game puts them (cherry wood after mangrove, the copper bulbs
    after the redstone lamp, the crafter after the dropper).
*   The search tab, JEI and other mods' items in those tabs are unchanged.
*   A developer check: `-Dbackportcore.creativeDump=<file>` writes every tab's list.
*   **Sea pickles belong to Update Aquatic Backport**, not Village & Pillage: when two backports need the same
    block, it belongs to the one it fits best. Blue ice stays Village & Pillage's.
*   **Requires Fluidlogged API** (3.3.3 or later), installed beside Backport Core on client and server.

*   **Waterlogging** for the backports' blocks: lanterns, sea pickles, kelp and the rest keep the water
    they are placed in, buckets fill and empty them, and water flows in and out of them, as in 1.13.
    Vanilla's own blocks keep Fluidlogged API's settings.
*   **Today's structure files**, Mojang's own, read into 1.12.2 blocks for the backports' structures:
    shipwrecks, ocean ruins and later ones. Structures are placed chunk by chunk without loading
    neighbors, and wall torches stay up at chunk edges.
*   Structures can be planned on the sea floor as well as the surface, on every terrain Backport Core
    already reads.
*   **In eighteen languages**, config screen included: English, Spanish (Spain and Latin America, including Argentina, Uruguay and Venezuela), Brazilian Portuguese, French, German, Russian, Simplified and Traditional Chinese, Japanese, Korean, Italian, Polish, Turkish, Ukrainian and Dutch. Block and item names use Minecraft's own translations.
*   The texture switch now shows its name, "1.14 Textures", in the config screen instead of `modernTextures`.
*   **1.14's player poses** for the backports to use: a swimming player is 0.6 blocks tall with their eyes at 0.4, a player with no room to stand crawls flat until there is room, and other players see both. Swimmers and crawlers are drawn lying along the swim, armor included, and the camera eases down to the new eye height instead of jumping. Nothing changes until a backport that swims is installed.
*   A small coremod of the library's own gives these the hooks 1.12.2 lacks. Each of its five patches does exactly what vanilla does unless a backport's swimming is on, and steps aside, keeping vanilla's behavior, when another mod has rewritten the same code.

# 1.0.0

First release: the code and textures the update backports share, in one library.

*   **1.14's Texture Update** for vanilla's blocks, items and mobs, and 1.15's enchantment glint, with the
    switch back to 1.12.2's look: `textures.modernTextures` in `config/backportcore.cfg`, or Backport Core's
    config screen. A setting made in an earlier mod's config is carried over the first time the game starts.
*   Moving a world's blocks and items over from another mod's copies, on demand or when that mod is removed.
*   Hiding duplicates when two mods add the same block: one of each pair keeps its recipe and creative tab.
*   Planning structures on vanilla, superflat, Open Terrain Generator, Biomes O' Plenty, Realistic Terrain
    Generation and 1.18-height terrain.
*   Registries other mods can add villager and wandering trader trades and smithing upgrades to.

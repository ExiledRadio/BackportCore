# 1.2.0

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

# 1.1.0

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

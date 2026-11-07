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

# 1.1.0

What Update Aquatic Backport needs: waterlogging, today's structure files, structures on the sea floor, and swimming and crawling poses.

**Now requires Fluidlogged API 3.3.3 or newer**, installed beside Backport Core on the client and the server.

*   **Waterlogging** for the backports' blocks, as in 1.13: lanterns, sea pickles, kelp and the rest keep the water they are placed in, buckets fill and empty them, and water flows in and out of them. Vanilla's own blocks keep Fluidlogged API's settings.
*   **Today's structure files**, Mojang's own, read into 1.12.2 blocks for the backports' structures: shipwrecks, ocean ruins and later ones. Structures are placed chunk by chunk without loading their neighbors, and wall torches stay up at chunk edges.
*   **Structures on the sea floor**: they can be planned on the sea floor as well as the surface, on every terrain Backport Core already reads.
*   **An icon** in the mod list.
*   **In eighteen languages**, config screen included: English, Spanish (Spain and Latin America, including Argentina, Uruguay and Venezuela), Brazilian Portuguese, French, German, Russian, Simplified and Traditional Chinese, Japanese, Korean, Italian, Polish, Turkish, Ukrainian and Dutch. Block and item names use Minecraft's own translations.
*   The texture switch now shows its name, "1.14 Textures", in the config screen instead of `modernTextures`.
*   **1.14's player poses** for the backports to use: a swimming player is 0.6 blocks tall with their eyes at 0.4, a player with no room to stand crawls flat until there is room, and other players see both. Swimmers and crawlers are drawn lying along the swim, armor included, and the camera eases down to the new eye height instead of jumping. Nothing changes until a backport that swims is installed.
*   A small coremod of the library's own gives these the hooks 1.12.2 lacks. Each of its five patches does exactly what vanilla does unless a backport's swimming is on, and steps aside, keeping vanilla's behavior, when another mod has rewritten the same code.

# 1.0.0

First release: the code and textures the update backports share, in one library. Village & Pillage Backport 2.4.0 is the first backport to require it.

*   **1.14's Texture Update** for vanilla's blocks, items and mobs, and 1.15's enchantment glint, with the switch back to 1.12.2's look: `textures.modernTextures` in `config/backportcore.cfg`, or Backport Core's config screen. A setting made in an earlier mod's config is carried over the first time the game starts.
*   **Moving a world's blocks and items** over from another mod's copies, on demand or when that mod is removed.
*   **Hiding duplicates** when two mods add the same block: one of each pair keeps its recipe and creative tab.
*   **Planning structures** on vanilla, superflat, Open Terrain Generator, Biomes O' Plenty, Realistic Terrain Generation and 1.18-height terrain.
*   **Registries** other mods can add villager and wandering trader trades and smithing upgrades to.

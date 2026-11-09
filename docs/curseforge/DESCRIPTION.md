# Backport Core

**The shared library of the update backports: the code and textures they all use, in one place.**

The update backports bring each Minecraft update to 1.12.2 in a mod of its own: Village & Pillage Backport for 1.14 and Update Aquatic Backport for 1.13, with more to come. Backport Core is the part they share. Every one of them requires it, so install it whenever you install one of them.

It adds no blocks or items of its own. What it adds is what the backports have in common: 1.14's look for vanilla's blocks, items and mobs, 1.13's waterlogging, today's structure files, structure placement on vanilla and modded terrain, and the rules that let the backports work side by side without two copies of the same block.

> 💬 **[Join the Discord](https://discord.gg/kxQvMDJBTN)** for bug reports, questions and release pings.

***

## What it does

*   **1.14's Texture Update** for vanilla's blocks, items and mobs, and 1.15's enchantment glint. Prefer 1.12.2's look? Turn off `textures.modernTextures`. Resource packs you pick, and mods that change a vanilla texture on purpose, still win over it.
*   **Waterlogging**, as in 1.13, for the backports' blocks: lanterns, sea pickles, kelp and the rest keep the water they are placed in, buckets fill and empty them, and water flows in and out of them. This comes from Fluidlogged API, which installs with Backport Core. Without it, everything else still works and the blocks stay dry.
*   **Today's structure files**, Mojang's own, read into 1.12.2 blocks, so the backports' shipwrecks, ocean ruins and later structures are built from the real game's own. They are scattered the way today's game scatters them, placed chunk by chunk without loading neighbors, and the backports can find the nearest of a kind.
*   **1.14's player poses**: swimming players are 0.6 blocks tall, players with no room to stand crawl until there is room, and other players see both. A small coremod of the library's own adds the hooks 1.12.2 lacks, and does nothing until a backport that swims is installed.
*   **Structures that fit the land**, planned on the surface or the sea floor of vanilla, superflat, Open Terrain Generator, Biomes O' Plenty, Realistic Terrain Generation and 1.18-height terrain.
*   **One copy of each block.** Some blocks belong to one update but are needed by another: 1.14's villages are built from 1.13's stripped logs and blue ice, for example. Backport Core keeps the table of which backport owns each shared block, so whichever owner is installed provides it and the others use it. A world can move its blocks and items over from another mod's copies, on demand or when that mod is removed. When two mods add the same block, one of each pair keeps its recipe and creative tab.
*   **Trades and smithing for other mods**: a registry other mods can add villager and wandering trader trades to, and one for smithing table upgrades.

***

## Which mods need it

*   **Village & Pillage Backport** 2.4.0 and newer
*   **Update Aquatic Backport** 1.0.0 and newer
*   Every later update backport

Mod authors: the API the backports use is in [docs/API.md](https://github.com/ExiledRadio/BackportCore/blob/main/docs/API.md).

***

## Config

One settings screen for every installed backport, under Mod Options. Each backport has its own page. Features turn on and off with one switch, search finds any setting, and badges show which settings need a restart or only touch new chunks. On a server, operators can change server settings from it.

![The settings screen](PENDING:settings-overview-new)

The files in `config/` stay as they were, so existing configs and modpack configs keep working.

***

## Languages

In 18 languages, config screen included: English, Spanish (Spain and Latin America, including Argentina, Uruguay and Venezuela), Brazilian Portuguese, French, German, Russian, Simplified and Traditional Chinese, Japanese, Korean, Italian, Polish, Turkish, Ukrainian and Dutch.

***

## Requirements

*   Minecraft 1.12.2, Forge 14.23.5.2847 or newer
*   [Fluidlogged API](https://www.curseforge.com/minecraft/mc-mods/fluidlogged-api) 3.3.3 or newer, for waterlogging. The CurseForge app installs it with Backport Core.
*   Install both on the client and the server, beside the backports that use them

## Install

Drop this jar and Fluidlogged API's in `mods` on both sides. Delete any older version first, or the two copies will crash on startup with a duplicate mod id.

***

Also by me: [Enchantment Recipes](https://www.curseforge.com/minecraft/mc-mods/rlcraft-enchantment-recipes), which lets you craft enchanted books from mod materials, and [Villager Tomes](https://www.curseforge.com/minecraft/mc-mods/rlcraft-villager-tomes), which lets you teach a librarian the books you find.

Not affiliated with Mojang. Licensed MIT.

Source: [https://github.com/ExiledRadio/BackportCore](https://github.com/ExiledRadio/BackportCore)

![Backport Family](https://media.forgecdn.net/attachments/2271/405/hero-family-png.png)

**[Discord](https://discord.gg/kxQvMDJBTN)** · **[Source](https://github.com/ExiledRadio/BackportCore)**

**The Backport Family:** [Update Aquatic (1.13)](https://www.curseforge.com/minecraft/mc-mods/update-aquatic-backport) · [Village & Pillage (1.14)](https://www.curseforge.com/minecraft/mc-mods/villager-backport) · [Buzzy Bees (1.15)](https://www.curseforge.com/minecraft/mc-mods/buzzy-bees-backport) · [Nether Update (1.16)](https://www.curseforge.com/minecraft/mc-mods/nether-backport) · [Caves & Cliffs (1.17-1.18)](https://www.curseforge.com/minecraft/mc-mods/caves-cliffs-backport) · [Wild Update (1.19)](https://www.curseforge.com/minecraft/mc-mods/wild-update-backport) · [Trails & Tales (1.20)](https://www.curseforge.com/minecraft/mc-mods/trails-tales-backport) · [Tricky Trials (1.21)](https://www.curseforge.com/minecraft/mc-mods/trials-backport) · [Game Drops (26.x)](https://www.curseforge.com/minecraft/mc-mods/game-drops-backport)

***

# Backport Core

**The shared library of the update backports: the code and textures they all use, in one place.**

The update backports bring each Minecraft update to 1.12.2 in a mod of its own: Village & Pillage Backport for 1.14 and Update Aquatic Backport for 1.13, with more to come. Backport Core is the part they share. Every one of them requires it, so install it whenever you install one of them.

Its one block is today's jigsaw block, for operators building jigsaw structures. What it adds is what the backports have in common: 1.14's look for vanilla's blocks, items and mobs, 1.13's waterlogging, modern structures, structure placement on vanilla and modded terrain, and the rules that let the backports work side by side without two copies of the same block.

> 💬 **[Join the Discord](https://discord.gg/kxQvMDJBTN)** for bug reports, questions and release pings.

***

## What it does

*   **Vanilla's textures follow the newest installed backport**: 1.14's Texture Update to begin with, then each later version's retextures up to that one, such as 1.17's ores with Caves & Cliffs Backport. Prefer 1.12.2's look? Turn off `textures.modernTextures`. Resource packs you pick, and mods that change a vanilla texture on purpose, still win over it.
*   **Each update's look and feel follows the newest installed backport**: the options, chat, F3 keys and F3 text, menus and loading screens, logos, splashes and wording of every version up to that one, so any backport on its own looks and feels like its version. Music and title panoramas come with each backport.
*   **Waterlogging**, as in 1.13, for the backports' blocks: lanterns, sea pickles, kelp and the rest keep the water they are placed in, buckets fill and empty them, and water flows in and out of them. This comes from Fluidlogged API, which installs with Backport Core. Without it, everything else still works and the blocks stay dry.
*   **Modern structures** in 1.12.2 blocks, for the backports' shipwrecks, ocean ruins and later structures. They are scattered the way today's game scatters them, placed chunk by chunk without loading neighbors, and the backports can find the nearest of a kind.
*   **Data-driven structures**: jigsaw structures from today's data files (structure sets, template pools, `.nbt` templates, biome tags) generate without code. Drop them in `config/backportcore/structures/` or ship them in a mod jar. A guide for porting modern structure mods is in the GitHub repository's `docs/PORTING_STRUCTURES.md`.
*   **Today's jigsaw block**, for building your own jigsaw structures. It has 26.3's look, its twelve orientations and its edit screen: Target Pool, Name, Target Name, Turns Into, Selection and Placement Priority, Joint Type, Levels 0 to 20, Keep Jigsaws and Generate. Generate assembles the structure right there from its pools. A structure block's saved templates keep their jigsaws, and templates and pools also load from the world's `structures/` folder and data packs. Like the structure block, it is for operators only: creative mode, permission level 2, unbreakable, no drops. Saving templates in today's format isn't supported yet.
*   **1.14's player poses**: swimming players are 0.6 blocks tall, players with no room to stand crawl until there is room, and other players see both. A small coremod of the library's own adds the hooks 1.12.2 lacks, and does nothing until a backport that swims is installed.
*   **Structures that fit the land**, planned on the surface or the sea floor of vanilla, superflat, Open Terrain Generator, Biomes O' Plenty, Realistic Terrain Generation and 1.18-height terrain.
*   **One copy of each block.** Some blocks belong to one update but are needed by another: 1.14's villages are built from 1.13's stripped logs and blue ice, for example. Backport Core keeps the table of which backport owns each shared block, so whichever owner is installed provides it and the others use it. A world can move its blocks and items over from another mod's copies, on demand or when that mod is removed. When two mods add the same block, one of each pair keeps its recipe and creative tab.
*   **Dynamic Trees support**: with Dynamic Trees installed, each spot gets one kind of tree, Dynamic Trees' or the backport's, never both side by side; villages and camps grow its trees.
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

![The settings screen](https://media.forgecdn.net/attachments/2174/565/core-settings-new-png.png)

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

Also by me: [Village & Pillage Backport](https://www.curseforge.com/minecraft/mc-mods/villager-backport), all of 1.14 on 1.12.2, [Update Aquatic Backport](https://www.curseforge.com/minecraft/mc-mods/update-aquatic-backport), all of 1.13 on 1.12.2, [Buzzy Bees Backport](https://www.curseforge.com/minecraft/mc-mods/buzzy-bees-backport), all of 1.15 on 1.12.2, [Nether Update Backport](https://www.curseforge.com/minecraft/mc-mods/nether-backport), all of 1.16 on 1.12.2, [Caves & Cliffs Backport](https://www.curseforge.com/minecraft/mc-mods/caves-cliffs-backport), all of 1.17 and 1.18 on 1.12.2, [Wild Update Backport](https://www.curseforge.com/minecraft/mc-mods/wild-update-backport), all of 1.19 on 1.12.2, [Trails & Tales Backport](https://www.curseforge.com/minecraft/mc-mods/trails-tales-backport), all of 1.20 on 1.12.2, [Tricky Trials Backport](https://www.curseforge.com/minecraft/mc-mods/trials-backport), all of 1.21 on 1.12.2, [Game Drops Backport](https://www.curseforge.com/minecraft/mc-mods/game-drops-backport), the 26.x game drops on 1.12.2, [Enchantment Recipes](https://www.curseforge.com/minecraft/mc-mods/rlcraft-enchantment-recipes), which lets you craft enchanted books from mod materials, and [Villager Tomes](https://www.curseforge.com/minecraft/mc-mods/rlcraft-villager-tomes), which lets you teach a librarian the books you find.

## Credits

**Original content: [Minecraft](https://www.minecraft.net), by [Mojang Studios](https://www.minecraft.net/en-us/about-minecraft).** Every feature this mod adds is from a later version of Minecraft. The textures, models, sounds, music and translations are Mojang's, taken from Minecraft's own game files, and remain theirs. The features' names and designs are Mojang's too.

**How this mod differs from the original:** Minecraft's newer versions don't run on 1.12.2. This mod is a Forge mod for Minecraft 1.12.2 that reimplements those features on the older game, matching the newer versions as closely as it can. It is not a copy or port of another mod. Where 1.12.2 can't do something the same way, the description above says so.

This is an unofficial fan project, made under Mojang's [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines). Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.

The mod's own code is MIT licensed.

Source: [https://github.com/ExiledRadio/BackportCore](https://github.com/ExiledRadio/BackportCore)

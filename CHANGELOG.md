# 1.3.1

*   **`/locate`**: `LocateCommand` takes only `structure` and `biome`; 1.12.2's names and 1.13-1.18's bare ids are
    gone (a usage error), and so is their completion. `VanillaLocate` still answers today's ids with 1.12.2's searches.
*   **Sound channels** (`presentation.client.SoundChannels`): with `Presentation.isAtLeast("1.14")`, paulscode gets
    1.14's split of OpenAL Soft's 255 sources, 247 normal and 8 streaming (1.12.2: 28 and 4), set on Forge's
    `SoundSetupEvent`; a version registered later restarts the sound system at init.
*   **`attenuation_distance`** (`presentation.client.SoundDistances`): read from every pack's `sounds.json` at each
    resource load; a source's distance becomes `max(volume, 1) * attenuation_distance`, as 1.14's `SoundEngine.play`.
*   **P7 on Cleanroom**: its classes are compiled by a modern javac, which calls private methods with
    `INVOKEVIRTUAL` (JEP 181), so P7 found no `processKeyF3` call and skipped. `Asm.privateCalls` matches both
    opcodes; P7 and the title panorama's `renderSkybox` use it.

# 1.3.0

*   **Texture layers**: the modern pack is now one layer per release line (`backportcore_packs/modern` for 1.14,
    `modern-1.15` to `modern-26.3`), applied up to `Presentation.newest()` (`client.TextureLayers`,
    `client.LayeredPack`). Built from Mojang's client jars by `tools/textures/build.py`, which keeps only textures that
    still fit 1.12.2's models and UVs and lists the rest in `tools/textures/report.txt`. Self-tests: `textures` (server)
    and `BACKPORTCORE_TEXTURES=<version>` with `runClient -Pselftest`, which reads the block atlas back.
*   **Superflat worlds** (`worldgen.FlatWorlds`): what a superflat preset lists, and nothing else. `decoration(world)`,
    `structure(world, kind)` and `allows(world, option)` read the world's preset; no structure newer than 1.12 (pillager
    outposts included) generates in superflat. `ScatteredStructure` kinds, `Structures` locators and
    `/locate structure` follow it on their own.
*   **`/locate biome`**: today's biome form of `/locate` (`locate.BiomeLocate`), by today's ids (1.12.2's biomes as
    an upgraded world names them, the backports' biomes by their modern names), with 26.x's search, reply and
    tab completion; tags and a generator's own biomes through `BiomeLocate.tag` and `source`.
*   **The backports' requests** (Tricky Trials Backport's `CORE_REQUESTS.md`, and the same asks from others):
    *   Ownership: Caves & Cliffs Backport's tuff and copper set are in `BlockOwnership`'s table; a per-name
        `provides` switch; an owner may hand its own block to a pseudo-owner (`modid:switched_off`). New
        `ItemOwnership` for items and effects (copper ingot, raw copper, honeycomb, the ominous bottle, Bad Omen).
    *   `CopyRemapping`: a family mod's copies of another's blocks, items and effects become the owner's when the
        owner is added to a world (Rule 1's third bullet).
    *   `copper.Oxidation`: one oxidation and waxing table, and current Java's aging rule, for every mod's copper.
    *   `block.ModernWall`: the family's 1.16+ wall (none, low and tall sides), moved from Caves & Cliffs Backport.
*   **Look and feel for 1.21.x**:
    *   Menu tracks with a volume (`addMenuMusic(name, weight, volume)`); `removeMenuMusic("music/game/x")` without a
        namespace removes that path whichever mod plays it.
    *   Music Frequency, Music Toast and UI volume (Music and Sounds, from 1.21.6): Core's ticker waits by Music
        Frequency, music keeps playing while paused, the playing track's name shows at the top left (1.21.6's
        sprites, supplied by the backport), button clicks follow the UI slider. From 1.21.9, letting go of a volume
        slider outside a world plays a sample of that sound.
    *   Attack/Destroy and Use Item/Place Block can be Toggle (Controls, from 1.21.9).
    *   `CycleOption.valueTooltips(...)`; from 1.21.11 a slider's knob follows the mouse and snaps when let go.
    *   `PanoramaRenderer.renderBlurred(screen, radius)`: 1.20.5's blurred panorama, by 1.12.2's own blur shader.
    *   Patches P22 (menus at 60 frames a second from 1.21.2), P23 (1.21.2's slot highlight), P24 (the sunrise glow
        through `FogPipeline.Step.sunrise`) and P25 (clouds by world time, `Step.cloudsFollowWorldTime`).
    *   1.21.11's Video Settings: Display, Quality & Performance and Preferences sections, the Preset slider (Fast,
        Fancy and Fabulous! set the section's settings; Custom once one changes), See-Through Leaves (1.12.2's Fancy
        graphics), Weather Effect Radius (P27), Texture Filtering and Anisotropic Filtering (None or Anisotropic, 2x to
        8x), Show Vignette and Chunk Fade Time (P28); View Bobbing moves to Accessibility. 1.21.6's Cloud Distance, 2 to
        128 chunks (P26). A backport's option named after vanilla's lands in vanilla's place and follows the presets.
        With OptiFine (`OptiFineVideoScreen`): OptiFine's own screen in the same layout, its submenus as an OptiFine
        section; the options OptiFine has its own of (`VideoSections.OPTIFINE_HAS`: Texture and Anisotropic Filtering,
        Show Vignette, Chunk Fade Time, Biome Blend) are left to it. A Video Settings without a list no longer throws.
    *   1.18's Controls screen (Mouse Settings..., Key Binds..., the toggles and Auto-Jump) with 1.12.2's key list as Key
        Binds; from 1.21.9 Auto-Jump, Toggle Crouch and Toggle Sprint leave Accessibility, which gains "Controls...".
    *   1.21.9's F3: every part of the debug screen Off, In F3 or Always on Debug Options (F3 + F6, with its search
        and profiles), Always parts shown with F3 hidden, the renderers and the 3D crosshair as parts, F3's
        combinations in menus. 1.21.11's Debug key binds (P30).
    *   The 1.20 title logo layout (P29), drawn when a backport of 1.20 or later supplies `minecraft.png` and
        `edition.png`. New: `Contribution.replacedAssets()`, `SliderOption.valueText(...)`, `PresentationOption.path()`.

*   **Caves & Cliffs Backport's extended height**: `WorldHeights` answers its y -64 to 319 worlds (and their
    section slots), so structures and villages build at the right heights there. New `TerrainSource`: a chunk
    generator that says where its own surface is, asked first by `TerrainHeights.forWorld`. New
    `ModernGeneration`: a chunk generator that runs a newer version's world generation in full (its placed
    features and 3D biomes), so the backports' own coral, kelp and seagrass generators step aside in its worlds
    and their spawn rules read its biomes.

*   **Generators that decorate their own worlds** (`DecorationSource`): a chunk generator that places today's
    features itself says which, so a backport's 1.12 hook does not add the same feature twice (bee nests on
    trees Caves & Cliffs Backport's 1.18 generator already gave nests, for one).

*   **Structures where a world's generator starts them**: `StructureSource` / `StartRules`. A chunk generator can
    decide where today's structures start, whichever backport builds them; every `ScatteredStructure` (and its
    `/locate`) follows it, and `ruledStart` names the variant. Caves & Cliffs Backport's 1.18 generator answers
    for shipwrecks, ocean ruins, buried treasure, ruined portals and pillager outposts with 1.18's placement.

*   **Terrain adaptation** (`TerrainAdaptation`): a structure kind registers how its pieces meet the ground
    (today's `terrain_adaptation`) and lays them out ahead, so a generator with 1.18 terrain shapes the ground
    around them before the chunk exists.

*   **A backport's structure generator in a world generator's place** (`StructureGenerators`): a chunk generator
    that does not build its structure generators through Forge's `InitMapGenEvent` offers them here, and the
    backport that builds the kind puts its own in place (Village & Pillage's 1.14 villages in Caves & Cliffs
    Backport's 1.18 worlds). New `FeatureSource`: a generator that places a later version's features itself, so a
    backport's own feature generators step aside there.

*   **Waterlogging at every height** (patch P20): in Caves & Cliffs Backport's extended worlds, blocks below y 0
    and above y 255 hold water like any other. Fluidlogged API keeps a chunk's water in one store that only has
    room for y 0-255; in those worlds Core gives each chunk one store per 16-block section instead, saved with the
    chunk (y 0-255 still where Fluidlogged API reads it) and sent to players section by section. Every other world
    keeps Fluidlogged API's store. `Waterlogging.setWater(primer, ...)` now ignores y outside 0-255 rather than
    writing another column's water.

# 1.2.0

*   **Recipe unlocking** (`recipe.RecipeUnlocks`): every crafting recipe of a mod that requires Backport Core
    unlocks the way vanilla unlocks the same recipe, with 1.12.2's recipe toast, and then shows in the recipe
    book. The rules are current Java's (`assets/backportcore/recipes/unlocks.txt`, built by
    `tools/recipes/build_unlocks.py` from Minecraft 26.3's recipe advancements): a recipe is matched by its
    result, takes vanilla's trigger items that are ingredients of it, and falls back to any ingredient. No
    advancement files; a backport does nothing. `RecipeUnlocks.addMod(modid)` adds a mod that does not require
    Core. Setting `recipes.unlockRecipes` (server), in all 18 languages.

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

# 1.4.6

*   API: `Waterloggable.waterFlowsThrough(state, world, pos, side)`, handed to Fluidlogged API as `canFluidFlow` (null
    keeps its face-shape rule). A full collision box answers false, as 26.3's `canPassThroughWall`: water kept in leaves
    stays there.
*   26.3's `/locate poi <type|#tag>` (`locate.PoiLocate`): 26.3's 21 types and 3 tags, today's search (256 blocks,
    `findClosestWithType`), messages and suggestions. 1.12.2 keeps no point of interest index, so the blocks are
    read: loaded chunks by section palette, unloaded ones from their saves without loading them. Types match the
    family's blocks by name (Village & Pillage's workstations and bell, Buzzy Bees' nests and hives, Nether
    Update's lodestone, Caves & Cliffs' lightning rod and cauldrons) and 1.12.2's beds, brewing stand, cauldron and
    nether portal; `PoiLocate.register`/`tag` for others. Kept in 1.4.4 (unreleased).

*   1.21.5's spawn eggs: from 1.21.5 (textures part of the look) every egg draws its own 26.3 texture, untinted:
    1.12.2's mobs and the family's mobs (by their placed vanilla egg). Other mods' eggs and every egg before 1.21.5
    stay tinted. New `modern-1.21.5` texture layer (`tools/textures/eggs.py`), `client/SpawnEggModels`.
*   Texture layers: 1.14's status effect icons put back into the inventory sheet (from 1.15's layer on), and 1.18's
    wither, 1.18/1.19's vindicator, 1.20/1.21's blaze and clouds (the parts left blank are ones 1.12.2 never draws).
*   26.3's `/swing [<targets> [mainhand|offhand [<animation> [<duration>]]]]` (`command/swing/`, setting
    `commands.swing`, permission level 2): 26.3's messages, `startIfAble`, Haste and Mining Fatigue on the duration.
    The animation and duration reach clients on channel `backportcore_swing`; `client/ClientSwings` drives the arm's
    `swingProgress` itself (no patch), `none` holds it still and `stab` draws as `whack`. Kept in 1.4.4 (unreleased).
*   26.2's pause menu (Game Drops Backport and newer): one row of 20 by 20 icon buttons under Advancements and
    Statistics, 4 apart and centered, as 26.2's and 26.3's: Report Bugs, Give Feedback, Friends and Player Reporting,
    with their names as tooltips; the grid sits at a quarter of the free height as 26.2's does. Friends is inactive
    in a demo; Player Reporting is inactive in your own world with nobody else in it ("There are no players to report
    in this world"). Friends and Player Reporting are Mojang's online services: clicking either shows 26.3's system
    toast, the button's name over "Not available in this version" (18 languages). Shown only when a backport supplies
    the `pause_menu/bug` and `pause_menu/social_interactions` sprites (the 1.14 text row stays otherwise); Friends
    and Player Reporting each need theirs (`friends/friends`, `pause_menu/player_reporting`).
*   26.3's system toast (`presentation.client.SystemToasts`, drawn by Core: 1.12.2's toast slots are 160 wide): a
    yellow title over white lines split at 200, `max(160, widest) + 30` wide, slid in and out from the right over
    600 ms and shown 5 s, on the `toast/system` sprite a backport supplies (else 1.12.2's system toast).
*   API: `MenuBackground.contentFrame`, the list background and separators of a header-and-footer screen, for a
    backport's own screens.
*   Fixed `/tick rate` below 20 slowing menus and the title screen. 1.12.2 gives a screen its mouse and keys once a
    tick, so every open screen answered at the server's rate; 26.3 gives them every frame. While the client ticks
    slower than 20 per second, an open screen now also gets its input every frame. The timer's tick length is now
    set at the start of every frame, as 26.3's `DeltaTracker` asks for it, so leaving a slowed world is back at 20
    per second on the title screen's first frame instead of after one slow tick. As in 26.3, the world, the player
    and screen ticks still run at the server's rate; freezing never stops players.

# 1.4.5

*   The ominous bottle is Tricky Trials Backport's in `ItemOwnership`: 1.21 added it (Village & Pillage's until now).
    Bad Omen stays Village & Pillage's. A world's `villagerbackport:ominous_bottle` becomes Tricky Trials' as it loads
    (`CopyRemapping`), also with Village & Pillage removed.
*   Fixed: copies were never renamed to their owner's blocks, items and effects when the owner was added to a world.
    `CopyRemapping` read Forge's `getMappings`, which holds only Backport Core's own missing names; it now reads them
    all.
*   Today's commands 1.12.2 lacks, in `command/`, each behind its own server setting (`commands.*`, read when a world
    loads): 1.20.3's `/tick` (P38 server loop, P39 freezing on both sides, P40 the client; the client's timer follows
    a server slower than 20 per second; state and steps on channel `backportcore_tick`), 1.20.2's `/random` (26.3's
    Xoroshiro sequences, checked against the 26.3 jar; saved as `data/backportcore_random_sequences.dat`), 1.19.4's
    `/ride`, 1.21.2's `/rotate`, 1.15's `/spectate` and 1.14's `/teammsg` (`/tm`). Permission levels as 26.3's.
    Survey of the rest: `family-notes/COMMANDS.md`.
*   Fixed ground spawns inside blocks that hold water (P37): 1.12.2's on-ground check (`canCreatureTypeSpawnBody`)
    asks only that the feet and head blocks are not liquid material, so with Fluidlogged API kelp, seagrass and any
    waterlogged block passed. `performWorldGenSpawning` never calls `getCanSpawnHere`, so Lycanites' own liquid check
    did not run either: in a Dregora test world 559 of 788 bobekos were in the sea. Both blocks now must hold no water
    (current Java's `isValidEmptySpawnBlock` fluid check).

# 1.4.4

*   Game Drops Backport's sulfur caves: `worldgen.UndergroundBiomes`, a backport's biome by column and height (saved
    in the chunk as `backportcore_underground`, sent on channel `backportcore_ub`), read by F3's biome line, the grass,
    foliage and water blend (P44, `BiomeColorHelper.getColorAtPos`) and `WaterColors.at`; `worldgen.DeferredPlacement`,
    feature work past 1.12's population window run once every chunk it touches is loaded and populated with its -x and
    -z neighbors (saved per dimension, `data/backportcore_deferred.dat`).


*   The Game Rules screen 1.12.2 never had (`gamerules/`), as the newest installed version draws it: 1.16's
    (Create World's Game Rules button beside More World Options; the rules under their categories, a name with an
    ON/OFF button or a number box, the rule's id, description and default as its tooltip), 1.20.5's header and footer,
    26.1's search box. 1.12.2's rules and the backports' (33, each shown when its mod is installed) under 26.3's names,
    categories and order; `disableElytraMovementCheck` and `disableRaids` as 26.3's Do elytra movement check and Do
    raids, turned around; another mod's rules under Miscellaneous by their ids. The chosen rules go to the new world
    once every backport has added its own. In a world (Game Drops Backport's World Options, 26.3), the server's own
    rules: channel `backportcore_rules`, permission level 2, applied as `/gamerule` applies them. API:
    `GameRulesScreen.inWorld(parent)`, `available()`, `permitted(mc)`.
*   1.13's Optimize World in Edit World (`worldupgrade.WorldOptimizer`, `OptimizeWorldScreens`): 1.13's question
    (1.14's Erase Cached Data box, 1.21.2's "Create Backup and Optimize"), then its progress screen. Every chunk of every
    dimension goes through the game's data fixer (Forge's, with each mod's fixes) and is written back when older than
    the game (`DataVersion` or a mod's `ForgeDataVersion`); Erase Cached Data writes every chunk, drops its light and
    works its height map out again from its blocks.

*   26.3's creative order (`creative.client.ModernOrder`): with Game Drops Backport (26.3) the newest version, every
    vanilla tab lists its items as 26.3's creative menu does, the family's items among vanilla's by the item each
    stands for. 1.12.2's tabs read 26.3's tabs in order, their home tab first (Redstone reads Redstone Blocks,
    Miscellaneous reads Ingredients); other mods' items follow in registry order. The table is
    `creative/order_26_3.txt`, from 26.3's own `CreativeModeTabs` run from the 26.3 jar
    (`tools/creative/CreativeOrder263.java`, `build_order_263.py`). Older looks, the Look & Feel page's inventory
    switch off, the search tab and JEI keep their order.
*   Fixed backport items with no creative tab at all: Core's table lacked 160 of today's items (26.1's copper
    lanterns, chains and bars, which its registry holds one field per kind; the later drops' poplar, sulfur and
    cinnabar sets, wool and concrete stairs and slabs, cushions...), so `CreativePlacement.place` found no tab for a backport's
    item of that name. The table now has every item 26.3's creative menu lists, each with its kind, and spells
    the dry grasses as the registry does (`short_dry_grass`, `tall_dry_grass`, not 26.1's field names): Tricky
    Trials' were never placed.

*   API: `structure.ModernJigsaw`, the jigsaw placer of 1.20 to 26.x beside 1.16.5's `Jigsaw` (unchanged):
    `selection_priority` and `placement_priority`, `pool_aliases` (`ModernJigsaw.PoolAliases`), `Util.shuffle`'s
    draws, 26.x's nameless feature jigsaws, the expansion hack, junctions for the terrain's beard. Template jigsaws
    carry their priorities (`ModernTemplate.Jigsaw`, `JigsawElement.Connector`). Game Drops' abandoned camps, Tricky
    Trials' trial chambers and Trails & Tales' trail ruins now share it, laid out block for block as before.

*   The family's one boat registry (`boat/`; Wild Update's request 4, Tricky Trials' #9, Game Drops' 5): `BoatWood`
    (vanilla's six woods and each backport's, by id), `backportcore:boat` and `backportcore:chest_boat` (1.19's chest
    boat: 27 slots, one seat, hoppers, contents dropped on removal), `ItemWoodBoat`, `client.RenderWoodBoat` (boat,
    chest boat, raft, chest raft), and `Boats.renamed`: a backport's old boat entity names become registry aliases
    when a world that has them loads, so saved boats keep their wood, chest and passengers. Using a boat is
    `entity_place`.

*   Game Drops Backport's requests (its `docs/CORE_REQUESTS.md`): patch P41, a jukebox's comparator signal from the
    disc's own `int comparatorSignal()` (found by name; `mechanics/JukeboxSignal`); `GameEventTags.addDampener(Block)`;
    `GameEvent.BOUNCE` (frequency 2, in 1.20's vibration and warden lists); `StructureLocate.nearest(world, from,
    idOrTag, rings, skipClaimed[, claim])` and `locate.StructureClaims` (26.3's `exploration_map` search radius and
    `skip_existing_chunks`, claims saved per dimension); `structure.ModernItems`, today's item names to 1.12.2's item
    and metadata (table from the data fixers: `tools/flattening/items.py`); the abandoned camp, ancient city, desert
    pyramid, mineshaft and warm ocean ruins map sprites; `leaf_litter` owned by Tricky Trials.
*   Game Drops Backport's babies (stream 1): `mechanics.AgeLockable` (`isBaby`, `getAge`/`setAge`, `startAge`, the
    `AgeLocked` flag) for babies the golden dandelion locks that are not `EntityAgeable` or start at another age, and
    `VanillaRevisions.babyModels()` / `setBabyModelSwitch`, when a backport draws its babies with 26.1's models.

*   26.3's off-map player markers (P43, `map/MapFacing`; with a backport registering 26.3): a player beyond a map's
    edge (`player_off_map`) or far beyond it (`player_off_limits`) keeps their facing, as on the map (the Nether's
    spin included), drawn from 26.3's pointed sprites instead of 1.12.2's round dots.
*   API: `JigsawPools.placer(idOrType)`, the feature placer registered for an id, so a backport can wrap another's.
*   26.1's F3 (`ModernDebugLines`): "Day #n" split from Local Difficulty, "Speed: n blocks/tick", the spawn line by
    26.1's category abbreviations; Debug Options entries `day_count`, `player_speed` (26.1) and `entity_spawn_counts`
    (an entry's `since` hides newer ones from older looks). 26.3's centered, bold, underlined option headers
    (`VanillaRevisions.centeredOptionHeaders`).
*   Game Drops Backport's look-and-feel requests: 26.1's Exclusive Fullscreen (`backportcore:exclusive_fullscreen`,
    Video Settings' Display) and 26.3's borderless default, patch P42 on `Minecraft.toggleFullscreen`
    (`BorderlessFullscreen`: an undecorated window of the monitor's size through LWJGL 2's
    `org.lwjgl.opengl.Window.undecorated`; not shown under Cleanroom's LWJGL 3); 26.3's GUI Scale for the debug overlay
    (Debug Options' footer, Unchanged, Auto by default, 1 ...; `DebugScale`, no patch: F3's columns, the lagometer and
    the network charts at it, never larger than the game's). 26.3's creative tab mineral order is not built (reasons in
    `docs/PRESENTATION.md`). API: `ToggleOption.valueTooltips`, `ToggleOption.defaultFollows`,
    `PresentationOption.availableWhen`; `VanillaRevisions.exclusiveFullscreenOption`, `borderlessByDefault`,
    `borderlessPossible`, `debugGuiScale`.
*   Fixed `ArrayIndexOutOfBoundsException` in `Biome.getSpawnableList` with Alex's Mobs (Legacy) 1.7.0 (P36): its
    `AM_WATER_AMBIENT` type is added in pre-init, after C&C's and UA's constructors had initialized `Biome$1` (their
    `SwitchTables.grow`), and its own widener misses the anonymous `Biome$1`. The switch's table lookup now takes the
    default branch (Forge's `modSpawnableLists`) for any ordinal past the table's end.

# 1.4.3

*   Fixed red squares around a lit redstone torch's head with the 1.21 textures (Newest Look 1.21 or later): 1.21's
    texture has a 4 by 4 head whose corners only its own model's glow quads sample, and 1.12.2's torch draws the
    whole texture on crossed planes. The 1.21 texture layer now carries 1.21's standing and wall redstone torch models
    (a stick and six glow quads) under 1.12.2's names (`lit_redstone_torch`, `lit_redstone_torch_wall`).

# 1.4.1

*   Fixed a crash at startup with SRParasites (since 1.3.0): the sunrise color, sky color and cloud height patches
    (P15, P24) replaced vanilla's calls to `WorldProvider.calcSunriseSunsetColors`, `World.getSkyColor` and
    `WorldProvider.getCloudHeight`, so a mixin redirecting one of those calls (SRParasites' blizzard sky) found no
    target. The calls now stay in place and their results go through the fog pipeline afterward.
*   Fixed a white rectangle behind the book in a modded book screen (Village & Pillage's book and quill editor) in a
    world: 1.20.5's blurred menu background left the alpha test off (1.12.2's shader pass turns it off), so the
    book texture's transparent parts drew opaque white. The background now leaves it on, as vanilla's gradient does.

# 1.4.0

*   **Look & Feel page** (settings screen, under Backport Core): every switch that only changes how the game looks or
    feels, from Core and every installed backport, in one place. At the top **Use the Newer Look and Feel** (off: the
    game looks and feels as 1.12.2; each switch keeps its choice) and **Newest Look** (Latest, or the version the look
    stops at). Then sections, each switch with the versions it comes from: Loading Screens (World Loading Screens,
    Startup Screen), Title Screen (Title Screen, Panorama, Splash Texts), Menus and Buttons (Menus and Buttons, Options
    Screens), Video Settings, Chat, F3 and Debug, Toasts, Inventory and Creative Tabs, Sounds and Music, World and Text
    (Sky, Fog and Clouds, Textures, Vanilla Wording), and Whole Backports. Every row is the same setting as on its mod's
    page; they are Client settings, which a server operator never sets for a player. New `CoreConfig.lookAndFeel`
    (all on by default; existing files keep their values). Takes effect at once; the resources load again when a part
    that replaces vanilla's files changes.
*   `presentation.LookArea`, `Presentation.newest(LookArea)`, `isAtLeast(LookArea, version)`, `installed(LookArea)`,
    `installedNewest()`, `PresentationOption.area(...)`, `new OrderedSteps(LookArea)` and `@Setting.Look(section)`.
    `Presentation.newest()` and `isAtLeast(version)` now give the version the game looks like (1.12.2 with the newer look
    off, stopped at Newest Look); `installedNewest()` is the newest installed version for content.
    `presentation.panorama` and `lookAndFeel.newestLook` cycle through their choices on the settings screen.
*   **Game events and vibrations** (`gameevent.*`), shared by every backport: Wild Update Backport's dispatcher,
    emitters, 1.19.2 vibration listener, `avoid_vibration` trigger (now `backportcore:avoid_vibration`) and vibration
    particle, moved here; 1.20.6's vibration system (`VibrationSystem`: the per-tick selector, block-distance
    dispatch, the `listener` tag) for 1.20 backports; 1.20's events (mount, dismount, unequip, `entity_action`,
    `resonate_1`-`15`) and tags, chosen by `GameEvents.require(version, switch)`; mount/dismount, 1.20's
    equip/unequip and wolf-shake emitters; `GameEventTags.addResonator`; `SensorActivation`.
*   Ownership: Wild Update Backport's sculk sensor (Trails & Tales Backport copies it).
*   **The backports' requests** (Trails & Tales Backport's `CORE_REQUESTS.md` 10.5, 10.6, 10.8 and 11.2; Wild Update
    Backport's 10; T&T's 10.2 and 10.9, added while this was built):
    *   **1.20.5's menus** (P31, `presentation.client.MenuBackground`): no dirt. Outside a world menus are the panorama,
        blurred, under `menu_background.png`; inside one the world, blurred, under `inworld_menu_background.png` (the
        pause menu too). Container, book and sign screens keep the gradient. Every list (`GuiSlot`) draws
        `menu_list_background.png`, its entries clipped to it, header and footer separators, no dirt or shadows. The blur
        is 1.20.6's six passes (`backportcore:shaders/post/menu_blur.json`, also used by `PanoramaRenderer.renderBlurred`)
        at Menu Background Blurriness x 10 (the backport's slider of that name, else 5). The textures are read when a pack
        has them; each is one color, which Core draws itself otherwise. The graphics presets now set a 0 to 100% blur
        slider to 50% and 20%, not 100%, and no longer count it as Custom.
    *   **Panorama motion as 1.20.5**: the pitch holds at 10 degrees and the view only turns, one spin for every screen
        that never restarts.
    *   **Narrator Hotkey** (1.20.2, P32): Core's option on Accessibility Settings; off, Ctrl + B no longer toggles the
        narrator. Its text in all 19 languages (Mojang's).
    *   **Adventuring Time** (`advancement.AdventuringTime`): the biomes vanilla's later lists add, as far as installed
        backports register them (Update Aquatic's oceans, Village & Pillage's bamboo jungle, Wild Update's deep dark and
        mangrove swamp, the cherry grove, the pale garden); in Caves & Cliffs' 1.18 terrain the whole list by today's ids,
        by the generator's 3D biomes. Core's criterion trigger `backportcore:biome`. Re-applied after `/reload`.
        `ModernGeneration.generates(id)` (default true) lets a generator leave out a biome it does not make.
    *   **F3 + 3** (1.20.2, `presentation.client.NetworkCharts`): the bandwidth and ping charts as 1.20.6 draws them,
        Core's own key from 1.20.2; the ping over Core's channel `backportcore_ping` (the tab list's latency on a server
        without Core); 1.20.2's "Debug charts: ..." line. `debugKey(key, null, action)`: a key with no F3 + Q line.
    *   **Water colors** (`water.WaterColors`): today's water and underwater fog colors by biome (1.20.6's, the pale
        garden's 1.21.4), registrations first, for the mod that draws 1.13's water.
*   **1.20's smithing** (`smithing.SmithingTemplates`, Trails & Tales request 1): template + base + addition.
    Transforms keep the base's tag and damage; trims go through `ArmorTrims`. A template mod registers the netherite
    upgrade template (`requireForUpgrades`, with its "needs the template" setting), a table owner says its table has
    the slot (`declareTableSupport`) and asks `find`; `isTemplate`/`isBase`/`isAddition` sort shift-clicks, `list`
    is for recipe viewers. While upgrades need the template, `SmithingUpgrades.find` and `list` answer nothing, so
    no two-slot table skips it; with no table declaring support nothing changes.
*   **Armor trims** (`smithing.ArmorTrims`): patterns (with their templates; each adds its trim recipe) and
    materials (palette, tooltip color, `darkerOn` for 1.20's `override_armor_materials`), stored as 1.20.4's
    `Trim: {pattern, material}`; unknown ids are kept and ignored. Client (`smithing.client`): 1.20's
    `paletted_permutations` built at runtime from the grayscale pattern and the palette (worn textures on first use,
    item overlays into the item atlas when it is stitched); every renderer's plain biped armor layer is replaced by
    one that draws armor, trim, then glint per piece (vanilla's `renderArmorLayer` still draws the armor, so other
    mods' armor hooks stay); another mod's armor layer subclass keeps its layer and gets a trim layer after it.
    Armor icons in vanilla's shapes (vanilla's, netherite mods', `drawOnIcon`) get the trim overlay through a
    wrapped model, by exact model name. Tooltip: "Upgrade:", pattern and material in the material's exact color
    (`ColoredTooltipText` draws any RGB over a placeholder line), at 1.20's place, hidden by `HideFlags` 128.
*   **Snow over structures** (`structure.FreezeTopLayer`): today's `freeze_top_layer` runs after the structures, so
    snow covers their tops like the terrain around them. 1.12.2 snowed before the family's structures were built, which
    left outposts, shipwrecks and ruins bare in snowy biomes. Every `ScatteredStructure` now gets a snow layer (and
    ice on still water) on the top block of each column its pieces cover; interiors are never touched. Today's rules:
    no light check while decorating (snow lies by a structure's torches, which melt it later). A chunk generator with
    today's temperatures implements `FreezeTopLayer.Climate`. `afterBuilding(world, structure, chunkX, chunkZ)` for a
    backport's own structure generator, `area(world, chunkX, chunkZ)` for a generator that snows before its
    structures.
*   **Ownership**: `smithing_table` is Village & Pillage's; amethyst, its buds and cluster, calcite and smooth
    basalt (blocks) and `amethyst_shard` (item) are Caves & Cliffs' (Trails & Tales copies them).

# 1.3.1

*   **`/seed`** (`command.SeedCommand`, replacing `CommandShowSeed` at `Presentation.isAtLeast("1.15")`): "Seed: [n]",
    green, click to copy, shift-click to insert. 1.12.2 has no `COPY_TO_CLIPBOARD`: the click runs
    `/backportcore:copy <text>`, which `presentation.client.CopyClick` puts on the clipboard from Forge's `ClientChatEvent`
    instead of sending. `chat.copy.click` in all 19 languages (Mojang's).
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

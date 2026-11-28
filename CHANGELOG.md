# 1.5.0

In progress: see docs/PLAN_1_5_0.md.

*   Faster idle servers: spawn rules a backport registers are checked before the natural spawner builds the mob, as
    current Java does (`worldgen.SpawnPrechecks`, patch P90). Fish and glow squid were built and thrown away for most
    spawn attempts in water. With Update Aquatic and Caves & Cliffs Backport registering their fish and glow squid, the
    whole family's idle server went from 8.3 to 7.0 ms a tick (Spark, median of the last minute).
*   Riding a chest boat, the inventory key opens the boat's chest, as in 1.19 (`boat.BoatNetwork`, channel
    `backportcore_boat`; `boat.client.ChestBoatKey`).
*   How Did We Get Here? with the backports' effects (`advancement.AllEffects.register`): vanilla's list at the newest
    installed version (34 at 1.21.11, 35 at 26.4), asking only for the effects in this game.
*   More vibration sources (`gameevent.GameEventEmitters`, patches P80-P83), each with vanilla's event and frequency.
    From 1.19: dropped items and falling blocks landing (hit_ground, damped by wool); stripping, tilling, paths and the
    daylight detector (block_change, no longer block_place); bows, crossbows and tridents released and shields
    lowered (item_interact_finish); spawners, chicken eggs and items placing an entity such as Game Drops' cushion
    (entity_place); snow golems' snow and villagers' crops (block_place); sheep eating tall grass, villagers
    harvesting and other `destroyBlock` calls (block_destroy); cauldrons filling with rain (block_change); chorus
    fruit (teleport); eyes of ender (projectile_shoot); broken paintings, item frames, armor stands and cushions
    (entity_die); hit boats, minecarts and armor stands (entity_damage); item frames filled, turned or emptied
    (block_change). With a 1.20 backport, 1.20.2's: witches drinking, sheep eating, rabbits eating carrots, totems,
    bone meal, evoker fangs and vexes, water potions putting out fire, and vehicles breaking. Bone meal and discs no
    longer post block_place.
*   Pistons can push and pull a mod's block that has a tile entity, if the block registers
    (`mechanics.PistonRules.allowTileEntity`, patch P84): Village & Pillage's composter moves with its level, as in
    1.14. A block a piston destroys posts block_destroy and, with a backport of 1.21.5, shows its breaking particles
    and sound (P85).
*   Leashed boats (`boat.BoatLeash`), 1.21.6's rules for vanilla's and the family's boats: a lead ties a boat to you,
    using it again unties it, shears cut it, sneak-using a boat ties the boats you hold to it, and a fence or knot
    takes them. The boat follows on 1.21.6's elastic rope (slack to 6 blocks, snapping past 12) and keeps its lead
    when saved. On with a backport of 1.21, or behind a backport's switch (`BoatLeash.require`).
*   The playersSleepingPercentage game rule (1.17, with a backport of 1.17; patches P86, P87): the share of players
    who must sleep to skip the night, with "n/needed players sleeping" above the hotbar. The Game Rules screen also
    lists Village & Pillage's doPatrolSpawning (1.15.2).
*   `GameEventEmitters.alreadyEmitted(world, pos)`: a mod that posts its own game event for a block a click changed
    (Buzzy Bees bottling a full hive: fluid_pickup) keeps Core from adding a block change, for state changes and block
    swaps alike.
*   1.15's copy_to_clipboard click action (`presentation.CopyToClipboard`): tellraw and other text components keep
    it, and clicking it in chat copies its value.

*   26.4 Deep Freeze systems (docs/PLAN_1_5_0.md stream 2; built on 26.4 Pre-Release 1, to diff at release):
    *   Freeze ticks (`freeze/`), 1.17's frozen counter with 26.4's rules, moved here from Caves & Cliffs so its powder
        snow and Game Drops' Freezing share it: 140 ticks, damage every 40 (five times for blazes, magma cubes and
        striders), the slowness, the frost overlay and the shaking; nothing freezes while on fire, in leather, or
        undead, polar bear or snow golem. Effects can hold the counter and set the damage (`registerEffect`). Settings
        page Freezing; `freezeDamage` is also Game Drops'. Below 26.4 (the newest installed backport older), 1.17 to
        26.3's two rules instead: the immune are strays, polar bears, snow golems and the wither, and the slowness
        needs ground that is not air.
    *   Speleothems (`speleothem/`): 26.4's `SpeleothemBlock`, shared by pointed dripstone and icicles: growth,
        thickness, falling segments (`backportcore:falling_speleothem`), stalagmite and falling stalactite damage,
        tridents breaking them. A fall onto a stalagmite counts 2.5 blocks longer from 1.21.5, 2 before it (by the newest
        installed backport).
    *   Monster Hunter and Monsters Hunted count the backports' hostile mobs (`advancement.MonsterHunter`).
    *   Adventuring Time follows 26.2 to 26.4's lists: the sulfur caves (26.2), the dappled forest (26.3) and the ice
        caves (26.4, Pre-Release 1's 57 biomes), each counted when Game Drops is installed at that version. The
        criteria read an underground biome first (`AdventuringTime.standingIn`), so the sulfur and ice caves under
        1.12.2's columns count. `ice_caves` is one of today's biome ids (`BiomeLocate.TODAY`).
    *   `/locate biome`: a biome source can leave the rest to the ones before it (`BiomeLocate.next`).
    *   The frostbite's spawn egg in the modern egg layer.
    *   Darkness hook (`presentation.Darkness`): the mod that owns Darkness (Wild Update 1.2.0) registers its factor and
        Darkness Pulsing, and the 26.1 lightmap reads them from it instead of reaching into Wild Update's classes. An older
        Wild Update without the hook is still read the old way.
    *   26.4's look and feel (`versions.Look264`): Improved Fog (Video Settings; off in Fast, on in Fancy and
        Fabulous), the Repeat Recipe/Trade key (R) in the recipe book, names on the recipe book's tabs, Other Players
        (P, and the pause menu icon after Friends) with Player Options' Mute, which hides a player's chat this session.
    *   Creative tabs in 26.4's order once the inventory part looks like 26.4 (`creative/order_26_4.txt`, from 26.4
        Pre-Release 2's `CreativeModeTabs`; 26.3's before that). `vanilla_order.txt` has 26.4's 27 new items, so Game
        Drops' frostbite egg, ice ball, icicle, ice crystal, brittle ice, Flurry disc, packed ice and snow brick sets
        and calcite sets get a vanilla tab instead of "not a vanilla item". Chiseled soulshale stays out, as in 26.4.
    *   Walls under a honey block (26.4 Pre-Release 2's `#wall_side_shape_override`): from 26.4 they read its outline
        (tall sides, no post on a straight run); before 26.4 its collision, as vanilla (low sides, post kept). Before,
        Core's walls went tall under honey at every version.
*   Modern blocks supplied by Core (`supply/`, docs/PLAN_MODERN_BLOCKS.md slices 1 and 2). `structure/ModernBlockScan`
    reads every block name the structure files would place (`.nbt` palettes and jigsaw final states in `data/` and
    `assets/`, processor outputs in `data/*/worldgen/`, in mod jars and drop-in packs), brought up to date and cached
    against each jar's size and date. `config/backportcore/modern_blocks_report.txt` says what this game places for
    each. On the family's structures and three public packs (Dungeons and Taverns, Towns and Towers, Explorify; 9,875
    files) Core alone left out 511 names, 1.74 million blocks; the full family 35.
*   The first 30 of those as decorative copies, `backportcore:<name>`: deepslate, cobbled, polished, bricks, tiles and
    tile stairs; blackstone, polished, bricks, brick stairs and slab; polished tuff and slab, chiseled tuff, tuff
    bricks, chiseled tuff bricks; waxed copper, weathered copper, cut, weathered cut, chiseled, weathered chiseled,
    grate and weathered grate; mud bricks, pale oak planks and log, crimson planks, chiseled nether bricks, iron chain.
    Numbers, models, textures and names are 26.3's, from `tools/supplied-blocks/generate.py` (reads `Blocks.class`
    through `javap`, plus the loot tables, tags and assets). No behavior; 1.12.2's closest sounds.
*   A copy is registered only when a structure file names the block and its owning backport has not registered it.
    `config/backportcore/supplied_blocks.txt` keeps each one registered from then on. In structures they come after
    1.12.2's own blocks and before stand-ins (`ModernPalette.Resolved.via` names the step). Copies use the owner's
    metadata and rename both ways: adding Caves & Cliffs to a world turns `backportcore:deepslate` into
    `cavesandcliffsbackport:deepslate` with its state, and removing it turns them back (blocks and items, slabs with
    their doubles). Dedicated server: placed, C&C added, C&C removed, 12 of 12 checks each time. Self-test group
    `supplied`; client test `ClientSuppliedTests` (`BACKPORTCORE_SUPPLIED=1`): every state and item has a model.
*   Slice 3: every decorative block newer than 1.12.2 the generator can show, 436 of 26.3's 626 new blocks (184 are
    left out and listed in `tools/supplied-blocks/skipped.txt`). New shapes: walls, fences, fence gates, doors,
    trapdoors, buttons, pressure plates, lanterns, leaves, plants, carpets, bars and glass. The generator now takes
    every new block rather than a list, finds each block's owner in the family's release jars (the earliest update
    that ships it), and records an owner's metadata where it is not vanilla's (Tricky Trials' chains). A plant's
    outline is today's; mangrove leaves and bushes are tinted as today.
*   Today's block sounds: the 55 sound types 1.12.2 lacks are Core's own events with today's files, volume and pitch
    (`supply/SuppliedSounds`, `sounds/modern/`); deepslate bricks sound like deepslate bricks.
    `-Dbackportcore.suppliedcheck=true` compares every copy's metadata with its installed owner's: with the whole family
    installed, 408 rows and 1,502 states, none different.
*   Size and time: the jar grows from 3.8 MB to 9.9 MB, of which 4.9 MB is sounds and 0.6 MB the blocks' models,
    blockstates and textures. Scanning the family's and three public packs' 9,875 structure files takes 2.3 to
    2.9 s on the first start, nothing once cached; registering 390 copies takes about 100 ms. A dedicated server with the packs is ready in 7 s against 4 s without them
    (the packs' own structures included); a dev client with all 488 copies reaches the title screen about 1 s later
    than with 21.
    Round trip on a dedicated server with Caves & Cliffs and Tricky Trials added and removed: 22 of 22 checks each
    time (doors, walls, slabs, buttons, bars, copper chains). Self-tests: group `supplied` 919/0; client 9,758/0
    (every state and item of 496 blocks has its model and texture).

*   Settings pages for mods that build their own `Configuration` (`BackportSettings.register(modid, config, categories...)`):
    each named top-level category is a row with its subcategories after it; `BackportSettings.hint` stands in for the
    `@Setting` annotations (`client`, `server`, `hidden`, `newChunks`, `vanilla=`, `list=`, `icon=`, `toggle=`). Done saves
    the file and posts the config-changed events. Settings screenshots also cover each non-family page.
*   1.14's light update packet (`light/LightUpdates`, channel `backportcore_light`, no patch): a listener on every
    server world collects the sections `notifyLightSet` reports; at the end of the world's tick each collected chunk
    its players already have gets those sections' sky and block light (`light/client/LightUpdatesClient` copies them
    in and redraws). 1.12.2 sends a chunk's light only with the chunk, so light the server changed later with no
    block change there (a neighbor's decoration, light spreading in from a chunk generated after) stayed wrong on the
    client until a block update nearby; F3+A did not help. Under Update Aquatic's water (light drops one a block)
    that was whole chunks of sea floor brighter or darker than the rest at night, and see-through looking ice. Client
    test `ClientLightTests`; in a Showcase copy, flights over warm, frozen and plain oceans went from 9 and 5 such
    chunks to none.
*   1.20.2's player preview in the inventory (P78, `presentation/client/InventoryPreview`, Inventory look area): the
    player in the survival inventory and the creative Inventory tab is centered on its black box and clipped to it, so
    an elytra-flying or riding player no longer covers the armor slots and the hotbar. Off with the Inventory switch
    (or before 1.20.2): 1.12.2's preview.
*   Gliding while looking straight up no longer kicks the player with "Invalid move player packet received" (P79,
    1.16's guard). Vanilla 1.12.2 divides by the horizontal look length while gliding; with OptiFine's Fast Math a
    look of exactly 90 degrees makes it zero, the position NaN, and the server or LAN host kicks the player.
*   `DynamicTrees.growing()`: true while a backport's `DynamicTrees.grow` call is growing its tree, so an add-on can
    tell a tree a backport asked for (and decorates itself) from one Dynamic Trees' biome worldgen placed.
*   Pause menu: other mods' buttons that overlap Core's layout (1.12.2's free row, now the icon row or Give Feedback
    row) move below it (`MenuScreens.onInitLast`, LOWEST priority, so buttons added after Core's layout are moved too;
    `movedForeign` for the client self-test).
*   A mod that registers its own tile entity under one of 1.12.2's ids (`registerTileEntity(X.class, "mob_spawner")`;
    an id without a namespace is `minecraft:`) is named in the log, with its jar, at load complete and when a server
    starts (`util/VanillaTileEntityIds`). That silently replaces vanilla's entry, and every vanilla block of that kind
    then fails to save ("is missing a mapping! This is a bug!") and loses its contents on reload. Report only: putting
    vanilla back would break the other mod's own tile entities. Self-test group `teids`.

# 1.4.9

*   Dynamic Trees (0.9.x, modid `dynamictrees`) support, `compat/DynamicTrees` (`DynamicTreesLink` is the one class linking against it, `deobfProvided`). Dynamic Trees denies 1.12.2's TREE, BIG_SHROOM and CACTUS decoration by the biome at the chunk's corner; 1.12.2 decorates with the biome at corner + 16. Next to a biome Dynamic Trees leaves alone (every backport biome, other mods' biomes without an add-on) a vanilla-decorated chunk kept its static trees beside Dynamic Trees' own, and a backport-decorated chunk lost its trees. Core re-answers those events at LOWEST priority by the decorating biome (`DynamicTrees.decide`): DENY where Dynamic Trees replaces that biome's trees; a DENY lifted only where Dynamic Trees' rule for the corner biome explains it.
*   `DynamicTrees.replacesTrees` and `replacesCactus` (world, biome or pos) for a backport placing trees outside those events; `grow(world, pos, random, feature)` grows Dynamic Trees' species for one of today's vanilla tree features (oak, fancy oak, birch, super birch, spruce, pine, mega spruce/pine, jungle, mega jungle, acacia, dark oak; 1.14's `normal_tree` names too) where it replaces the biome's trees, for structures' trees; `biomeData(json)` adds a backport's Dynamic Trees worldgen JSON before the pack's `config/dynamictrees/worldgen.json`.
*   Self-test group `dynamictrees` (`DynamicTreesTests`); `selftest/dynamictrees/DynamicTreesCheck` (`gradlew dynamicTreesCheckJar reobfDynamicTreesCheckJar -Pselftest`) checks a real server with Dynamic Trees, which needs Forge 2859 or later and never loads in the 2847 dev environment.
*   JEI and HEI keep their item list clear of the active effects beside the inventory (P57, 1.18 and later): `compat/InventoryEffectsJei` (`@JEIPlugin`, loaded only by JEI or HEI) hands JEI Core's backgrounds as extra GUI areas (`IAdvancedGuiHandler.getGuiExtraAreas` for every `InventoryEffectRenderer`), as modern JEI does with vanilla's. JEI 1.12 knew only 1.12.2's column left of the inventory; its list sat right of the inventory, under Core's column, which drew over it. `InventoryEffects.areas(gui)` gives the backgrounds' places; the layout is 26.3's `EffectsInInventory` as before (none under 32 pixels of room, compact under 120, fitted to the text and clipped with a tooltip above). Client self-test `ClientEffectsTests` (also with `-PwithMods=<JEI or HEI jar>`).
*   `DynamicTrees.grow` also takes another mod's Dynamic Trees species by its full name (`dtbackport:cherry`, `dynamictrees:spruce`): it grows wherever Dynamic Trees generates trees in the dimension, so a backport's own decoration keeps its trees' places and only the trees become dynamic. That is how the new add-on **Dynamic Trees - Backport** (cherry, mangrove, azalea, pale oak, poplar) gets its trees into the backports' biomes.
*   Update notices (`settings/Family.java`, `updates/*.json`) name this release: Core 1.4.9, Village & Pillage 2.7.6, Buzzy Bees 1.1.3, Caves & Cliffs 1.1.6, Wild Update 1.1.4, Trails & Tales 1.0.5, Tricky Trials 1.0.3, Game Drops 1.0.2.

# 1.4.8

*   Update notices (`settings/Family.java` latest versions, `updates/*.json`) know the 2026-11-24 releases: Core 1.4.7, Update Aquatic 1.2.3, Village & Pillage 2.7.5, Buzzy Bees 1.1.2, Nether Update 1.1.2, Caves & Cliffs 1.1.5, Wild Update 1.1.3, Trails & Tales 1.0.4, Tricky Trials 1.0.2, Game Drops 1.0.1. The table still listed Core 1.4.6, Caves & Cliffs 1.1.4 and older; the JSONs Core 1.4.4 and older.

# 1.4.7

*   Every backport feels like its version on its own: Core now owns each update's look and feel that is not tied to one backport's content, and applies it up to the newest installed backport (`Presentation.core(version)`, `presentation/versions/LookNNN`; `docs/CORE_FEEL.md` lists what moved). Caves & Cliffs alone has 1.13's command suggestions, 1.14's Scroll Sensitivity, 1.15's Sneak and Sprint toggles and 1.16's F3 + F4. Music and panoramas stay in the backports.
*   Moved in: 1.13's Biome Blend (P68), Command Suggestions, F3 + C, F3 + I, F3 text and furnace recipe book (Update Aquatic); 1.14's Scroll Sensitivity, Discrete Scrolling, F3 + N, F3 + Esc and F3 chunk line (Village & Pillage); 1.15's Sneak and Sprint Hold/Toggle, window title and F3 text (Buzzy Bees); 1.16's Entity Distance, Distortion Effects (P70, P71), FOV Effects, F3 + F4 game mode switcher, F3 + N and F3 text (Nether Update); 1.18's Hide Lightning Flashes, Autosave Indicator, clouds at 192, fog and F3 rows (Caves & Cliffs); 1.19.4's Damage Tilt, Glint Speed and Glint Strength (P73 to P75) and F3 allocation rate (Wild Update); 1.20's window icon, Ctrl + scroll GUI Scale, F3 + 1 and F3 + 2, 1.20.5's Menu Background Blur, loading and dimension screens, shield pose and Ctrl + pick block (P76) (Trails & Tales); 1.21.x's Reduce FPS When, High Contrast Block Outlines, Invert Mouse X, Sprint Window, Save Unsent Chats, air bubbles, rarity tooltips, fog and loading screen (Tricky Trials). With them each version's title and startup logos, widgets, check box, Accessibility icon, multiplayer warning, splashes, wording and changed defaults.
*   Options keep their backports' ids, so saved values carry over. Buzzy Bees' Sneak and Sprint toggles move from its config to the options.
*   Older backport jars still work with this Core: an older jar's copy of an option Core owns follows Core's (`PresentationOption.mirror`: one button, one value); an F3 text, fog or music step of the same version and name is left out (`OrderedSteps`); Core leaves its own event handler or patch off while the older jar's class is on the class path (`LegacyCopies`).
*   `Presentation.core`, `coreContribution`, `Contribution.isCore`, `PresentationOption.ownedByCore`/`canonical`, `LegacyCopies`, `MenuBackground.menuTile`. Core registers its first sound, `backportcore:ui.hud.bubble_pop`.
*   Update Aquatic 1.2.3, Village & Pillage 2.7.5, Buzzy Bees 1.1.2, Nether Update 1.1.2, Caves & Cliffs 1.1.5, Wild Update 1.1.3, Trails & Tales 1.0.4 and Tricky Trials 1.0.2 drop their copies and require this Core; older releases keep working with it.

# 1.4.6

*   The recipe toasts' small block is at (3, 3), as 1.13.2 and 1.14.4 draw it: P66's crafting table and the smelting books' blocks were a pixel low. `SmeltingToast` draws its book's block only from the toasts' 1.13 look (`VanillaRevisions.recipeToastIcon`), as P66 does; with the toasts' look off both toasts are 1.12.2's, the output alone. A smelting book can have no screen (1.14's campfire: no container is its own, no tabs) and still unlock and toast its recipes.
*   Fixed a crash 20 to 30 seconds after launch on Android launchers (PojavLauncher, Zalith): their LWJGL 3 lacks `ALC10.alcIsExtensionPresent`, which `presentation.client.SoundDevices.createAL` called through LWJGL 2's API. A `LinkageError` there now falls back to 1.12.2's own `AL.create()`, without the device choice and Directional Audio; the HRTF query and `remake` catch it too. Self-test hook `SoundDevices.testMissingCalls`.
*   Fixed lit redstone torches breaking into black and purple particles with the 1.21 models (Newest Look 1.21 or later): `lit_redstone_torch.json` and `_wall.json` named their particle texture through a variable defined in the same model, which 1.12.2 cannot resolve ("upward reference"). They now name `blocks/redstone_torch_on`.

# 1.4.5

*   `locate.BiomeLocate.layers(world)`: 1.12.2's own column biomes as a `Sampler`, for a backport's `source` that answers a few places itself (Game Drops' sulfur caves under 1.12.2 terrain) and hands the rest to the layers.
*   P67 (`asm/RenderChunkMoveTransformer`): a render chunk the frustum moves is marked for a rebuild, as 1.18.2's `RenderChunk.setOrigin` (`reset()`: `dirty = true`). 1.12.2 emptied it (`CompiledChunk.DUMMY`) and waited for a chunk packet; when the data was already on the client the section stayed empty and blocked the culling walk: holes to the sky until F3 + A.
*   P66 (`asm/RecipeToastTransformer`): 1.13's recipe toast draws the crafting table at 0.6 scale behind the output (`VanillaRevisions.recipeToastIcon`, the toasts' look from 1.13, which `LookArea.TOASTS` now offers). 1.12.2 drew the output only.
*   Ocean monuments in Realistic Terrain Generation worlds only where RTG's terrain is deep water (`worldgen.MonumentTerrain.deepEnough`, called by `ModernMonument` and open to a backport's own monument rule): at least 15 blocks of water within 16 blocks of the chunk's block (9, 9), water within 29, read from RTG's cached landscape at the biome check's 1:4 cells. RTG builds a biome it has no realistic biome for as plains (Update Aquatic's deep oceans in 1.2.2) and blends small deep ocean patches into lakes, so the biome rule alone put monuments on land and in ponds. Generation, `/locate` and explorer maps all ask the same check. Other worlds keep vanilla's rule. `selftest/monument/MonumentCheck` (`gradlew monumentCheckJar reobfMonumentCheckJar -Pselftest`) surveys a real server's monuments against the terrain.
*   `structure.CutTrees`: after a structure builds its part of a chunk, every decayable leaf within 6 blocks of its
    pieces with no log within 6 steps through leaves (26.3's `LeavesBlock` distance) is removed, with any snow layer on
    it. 26.3 places template air (`SinglePoolElement` ignores only structure blocks) and its cut-off leaves count up
    to 7 and decay; 1.12.2's generated leaves never check. Run by `FreezeTopLayer.afterBuilding` (villages, outposts,
    Caves & Cliffs' structures), `ScatteredStructure`'s driver and `ModernTemples`. Reads loaded chunks only.
*   Texture layers: 1.14's status effect icons put back into the inventory sheet (from 1.15's layer on), and 1.18's
    wither, 1.18/1.19's vindicator, 1.20/1.21's blaze and clouds (the parts left blank are ones 1.12.2 never draws).
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
*   Fixed `/tick rate` below 20 slowing menus and the title screen. 1.12.2 gives a screen its mouse and keys once a
    tick, so every open screen answered at the server's rate; 26.3 gives them every frame. While the client ticks
    slower than 20 per second, an open screen now also gets its input every frame. The timer's tick length is now
    set at the start of every frame, as 26.3's `DeltaTracker` asks for it, so leaving a slowed world is back at 20
    per second on the title screen's first frame instead of after one slow tick. As in 26.3, the world, the player
    and screen ticks still run at the server's rate; freezing never stops players.
*   Data-driven structures (`structure.DataStructures`): Core reads today's `data/<ns>/worldgen/structure_set` and
    `worldgen/structure` files from every mod jar and from structure packs in `config/backportcore/structures/`
    (folder, `.zip` or `.jar`, datapack layout; `DataPacks`) at load complete, and generates each `random_spread` set
    of `minecraft:jigsaw` structures as 26.3 does: `DataPlacement` (frequency and its four reduction methods,
    exclusion zone, locate offset), `HeightProviders` (every height provider and anchor), the stub of
    `JigsawPlacement.addPieces` (start jigsaw name, heightmap projection, dimension padding, pool aliases), then
    `ModernJigsaw`; weighted sets pick as `ChunkGenerator.createStructures`. Biomes by id, list or tag (`DataTags`;
    26.3's biome tags ship in Core), terrain adaptation, liquid settings and spawn overrides. `/locate structure` finds
    them by id. Starts save as `BCData`, pieces as `BCDataPiece`. Other placement and structure types, ids a backport
    already generates and the new `structure.blacklist` are skipped; `structure.dataStructures` turns it off.
    `JigsawPools` and `ModernTemplate` also read `data/<ns>/worldgen/...` and `data/<ns>/structure/` (or
    `structures/`). `JigsawPiece.applyWaterlogging()`, `BiomeLocate.sampler(world)`.
*   Today's jigsaw block, `backportcore:jigsaw` (`block.CoreBlocks`, `TileEntityJigsaw`, `JigsawNetwork`,
    `block.client.JigsawScreen`), Core's first block: 26.3's twelve orientations, placement, model and textures; an
    operator block (creative mode and permission level 2 to place, open or break it), hardness -1, no drops; 26.x's NBT
    keys; 26.3's edit screen (Target Pool, Name, Target Name, Turns Into, both priorities with tooltips, Joint Type for
    vertical jigsaws, Levels 0 to 20, Keep Jigsaws, Generate, Done, Cancel; 18 languages from 26.3's files) and its
    two packets on channel `backportcore_jigsaw`, checked for an operator on the server. Generate is
    `ModernJigsaw.generate`, 26.3's `JigsawPlacement.generateJigsaw` on Core's assembler (`ModernJigsaw.layout` without
    placing). `TemplatePlacement.keepJigsaws`. `ModernTemplate` reads a 1.12.2 structure block's `backportcore:jigsaw`
    blocks as jigsaws, reads templates from the world (`structures/`, `generated/`, folder data packs) when no jar has
    them, and `JigsawPools` reads pools from the world's folder data packs. A jigsaw's `minecraft:structure_void` final
    state places nothing (it placed a structure void), and a 1.12.2 block name works as a final state. `minecraft:jigsaw`
    resolves to the block in `ModernPalette`, and 1.19.3's Operator Utilities lists it. Saving in today's format is not
    done.
*   The arm swings on a drop (`presentation.client.DropSwing`, no patch): the drop key from 1.15 (`InputEvent`, the
    checks `processKeyBinds` makes), every client-side `ItemTossEvent` of the player from 1.16 (`Player.drop`'s
    client swing: inventory throws, clicks outside, creative). Checked against 1.15.2, 1.16.5 and 26.3's code.
*   1.21.2's clouds (`presentation.client.ModernClouds`, `VanillaRevisions.modernClouds()`, the sky's look 1.21.2, P26's
    early return): 26.3's `CloudRenderer` mesh in six display lists with a depth pass first; 1.12.2's inner walls
    showed through as a grid. Off for a world's own cloud renderer and an OptiFine shader pack. Video shots
    `video-clouds-*`.
*   26.1's lightmap (`presentation.LightmapColors`, `presentation.client.ModernLightmap`, `VanillaRevisions.modernLightmap()`,
    the sky's look, which now lists 26.1; patch P65 at the head of `EntityRenderer.updateLightmap`): 26.3's
    `lightmap.fsh` with its environment attributes per dimension (block light tint, sky light color and factor on the
    day timeline with rain and thunder, ambient light, Night Vision color), Forge's `getLightmapColors` still per cell,
    Wild Update's Darkness taken in. Steps aside for an OptiFine shader pack and OptiFine's custom lightmaps. Checked
    cell by cell against the shader in `LookFeelTests`; video shots `video-light-*`.
*   1.19.4's Create New World (23w05a; `presentation.client.CreateWorldTabs`, the menus part): tabs Game (World Name,
    Game Mode, Difficulty, Allow Commands), World (World Type and Customize, the seed, Generate Structures, Bonus
    Chest) and More (Game Rules, and other mods' buttons), Create New World and Cancel below, laid out as 26.3's.
    1.12.2's `GuiCreateWorld` stays the screen and makes the world, so the settings are 1.12.2's and other mods'
    buttons, events and reflection keep working. Difficulty starts at the Difficulty option (1.12.2's new worlds take
    it); another choice is set on the new world's first tick (`gamerules.NewWorldDifficulty`).
    1.19.4's light dirt before 1.20.5, the blurred panorama and the tab sprites a backport supplies (else drawn) from
    1.20.5; "Allow Commands" from 1.20.5; the selected tab's label over its background from 1.21.11. Escape goes
    back, Ctrl + Tab changes tabs. 13 new lang keys, 26.3's words in 18 languages.
*   26.3's menus (`docs/SCREEN_AUDIT.md` gaps 1 to 4 and 13), each from the version that brought it:
    *   1.20.5's header and footer (`client/HeaderFooterLayout`, a documented helper for any screen): the title centered
        in a 33-high header, Done (200 wide) or a row of buttons 8 apart in a 33-high footer, the contents between.
        Skin Customization, Music & Sounds and Chat Settings become 26.3's scrolling lists of their own buttons (rows
        25 high, the list background and separators); Accessibility, Controls, Mouse Settings and Online Options are
        lists between the separators; Key Binds has its list between them and Reset Keys and Done in the footer.
    *   Options as 26.3's: a 61-high header with the title and FOV beside Online... (1.18; Realms Notifications moves
        to the new Online Options screen), the grid from 30 under it, Telemetry Data (1.19.3, greyed out; from 1.20.5
        with the tooltip "Telemetry is disabled.") and Credits & Attribution (1.19.4) after Accessibility Settings.
        Before 1.20.5 the two new buttons are a fifth row of 1.19.4's grid. Allow Server Listing is left off Online
        Options: it travels in the client information packet of 1.18 and later, which 1.12.2's has no field for.
    *   Credits & Attribution (1.19.4, `client/CreditsAndAttributionScreen`): Credits (1.12.2's end credits, back to
        this screen after), Attribution and Licenses (Mojang's links, through the link confirmation), Done. The title
        screen's copyright text opens it (1.19.4's `PlainTextButton`; 1.12.2's rolled the end credits).
    *   The multiplayer warning in the newest version's layout: 1.20.5's centered column (title, message, check box,
        Proceed and Back, 8 apart), and from 1.21.9 26.3's message box: framed as the text field, `width - 100` wide,
        as tall as the text, single-spaced, scrolling only when taller than `height - 100`. The old screen's text ran
        at 18 pixels a line from x 25 to the right edge.
    *   Edit World as 1.20.5's centered column (`client/EditWorldScreen`): Enter Name, the name box, Reset Icon, Open
        World Folder, Make Backup, Open Backups Folder, Optimize World, then Save and Cancel; the title at 15.
    *   Select World's and Play Multiplayer's Cancel reads Back (1.20.2); the title screen's Realms button reads
        Minecraft Realms in full (Forge 1.12.2 shortened it).
    *   New lang keys in 18 languages (Online Options, Telemetry Data, Credits & Attribution, from Mojang's files).

*   The options rows and screens 26.3 has that the family lacked (`docs/SCREEN_AUDIT.md` items 12, 14, 15, 16 and 22),
    each Core's own option from the version that added it, with vanilla's text in 18 languages:
    *   1.19.4's accessibility onboarding: on the first start the welcome screen with Narrator, Music & Sounds...,
        Accessibility Settings... and Language..., once, before the title screen.
    *   1.19.4's Notification Time (P50): toasts, subtitles and the held item's name stay 0.5x to 10x as long.
    *   1.19.4's High Contrast: the built-in High Contrast pack from the newest backport that has its art
        (`Contribution.highContrast`; Game Drops Backport's 26.3 pack), with 1.12.2's `widgets.png` made from its
        button, language and lock sprites. Greyed out without one, as vanilla's without the pack.
    *   1.21.3's Rotate With Minecarts: greyed out, as vanilla's outside a world with Minecart Improvements
        (`Presentation.setMinecartImprovements` for a backport that adds the experiment).
    *   Accessibility Settings in 26.3's order from 1.21.9.
    *   26.2: Touchscreen gone from Mouse Settings.
    *   26.3's Quit Shortcuts (P51): off, Alt + F4 (Cmd + Q) no longer quits. 26.3's Right Click Emulation (macOS):
        Control + left click is a right click.
    *   1.18's Device (P52): pick the sound output device; the sound system restarts on it. 1.19's Directional Audio
        (HRTF) where OpenAL has it (Cleanroom's LWJGL 3; LWJGL 2's OpenAL has no HRTF, so the row is not shown there).
        Music & Sounds in 26.3's layout from 1.18.
*   API: `PresentationOption.activeWhen(supplier, inactiveTooltip)`: an option shown greyed out.
*   1.12.2's question and message screens as the newest installed version lays them out (`client/MessageScreens`,
    `VanillaRevisions`; the screens stay 1.12.2's objects, so every mod's `GuiYesNo` keeps working):
    *   yes/no questions (`GuiYesNo`: delete world, delete server, the death screen's quit question, every mod's): 1.17's
        buttons under the message, 1.19.1's centered title and message, 1.21.6's column (26.3's `ConfirmScreen`: title,
        message, then the two buttons, 8 apart, centered on the screen; an empty message takes no line);
    *   leaving a singleplayer world: "Saving world" while the server saves, 1.13's over the dirt, 1.20.5's in its framed
        box, 1.21.11's box 12 lower (Forge's "Shutting down internal server..." until now);
    *   the disconnected screen: 1.20's centered reason (white), message and 150-wide button, 1.21's 200-wide button;
        the wording ("Back to Server List") is the backports' vanilla text;
    *   out of memory: 1.20.5's header, framed message and footer with Back to Title Screen and Quit Game;
    *   the working screen (deleting a world, joining one) without 1.12.2's lone " 0%" (1.14).
*   Widgets: a slider's handle lights up under the mouse (1.14, P62) and a focused text field's frame is white (1.16,
    P61), on vanilla's, Forge's and Core's sliders and fields. The check box (multiplayer warning, Optimize World) is
    1.20.2's when a backport supplies the `widget/checkbox` sprites: 17 square, its label beside it, framed white once
    clicked. Compared with 26.3's sprites: the slider track, button, scroll bar and text field already match.
*   The End credits from 26.3's `texts/credits.json` and `postcredits.txt` when a backport supplies them (1.17's
    `WinScreen` reading: sections, disciplines, titles and names), drawn by 1.12.2's screen.
*   Active effects beside the inventory (`InventoryEffects`, patch P57 on `InventoryEffectRenderer.drawScreen`): from
    1.18 a column right of the survival and creative inventories, 120 wide with names or 32 wide (icon only, name and
    time as a tooltip) as the room allows, and the inventory no longer moves right (Forge's `PotionShiftEvent`
    cancelled). From 1.21.11 each background fits its text (at most the room less 7), names cut to fit, levels up to
    X, and ambient effects on their own sprite. Backgrounds from the `container/inventory/effect_background*` sprites
    when supplied, else `inventory.png`'s, sliced with 4-pixel edges.
*   One system toast drawer: `SystemToasts` takes 26.3's ids (`SystemToasts.Id`: display time; `add`, `addOrUpdate`,
    `forceHide`, `get`, `clear`) and shows several at once in `ToastManager`'s five 32-pixel slots, one sound a frame.
    Before 26.2 a toast is one title and one message line, as wide as its first text; from 26.2 both wrap at 200 and
    the width follows each update. `SystemToasts.notificationTime` for 1.19.4's Notification Time. Trails & Tales'
    toasts now go here. `addOrUpdate(title, message)` kept.
*   Recipe book search box (patch P58 on `GuiRecipeBook.render`, and Core's furnace books): 1.16's gray italic
    "Search..." while empty and unfocused, 1.20.5's position (one up, 81 wide), and 1.21.2's magnifier: a click left
    of the box focuses it. 18 languages.
*   F3 + 2 from 1.20.2 (`FrameCharts`): the FPS chart (bottom left) and, on your own server, the TPS chart (bottom
    right) in place of the lagometer: colored bars, min/avg/max, 30 and 60 fps lines, the frame limit line, the tick
    rate. F3 + 1's profiler pie at the GUI scale, above the charts, as 1.20.2's (patch P60 on `Minecraft.runGameLoop`).
    `DebugCharts.Samples` takes several values a sample.
*   Subtitles take Text Background: Everywhere's opacity (patch P59 on `GuiSubtitleOverlay`).
*   Debug Options: `tps`, `looking_at_fluid`, `looking_at_entity`, `sound_mood` (1.21.9), `heightmap` (1.21.11) and
    `detailed_memory` (26.1), each worded as vanilla's (`DebugParts`; `DebugParts.setMood` for a cave mood). From
    1.21.9 the parts are laid out in vanilla's two columns: version and FPS on top, plain lines split half and half,
    then the groups. Lines of other mods follow in their own column.
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
*   26.3's `/swing [<targets> [mainhand|offhand [<animation> [<duration>]]]]` (`command/swing/`, setting
    `commands.swing`, permission level 2): 26.3's messages, `startIfAble`, Haste and Mining Fatigue on the duration.
    The animation and duration reach clients on channel `backportcore_swing`; `client/ClientSwings` drives the arm's
    `swingProgress` itself (no patch), `none` holds it still and `stab` draws as `whack`. Kept in 1.4.4 (unreleased).
*   API: `MenuBackground.contentFrame`, the list background and separators of a header-and-footer screen, for a
    backport's own screens.
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

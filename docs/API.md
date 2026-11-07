# Backport Core API

For the backports that depend on this library. Everything is plain static Java; no events to post, and
the interfaces to implement are `Waterloggable` and, for player poses, `PoseRules` and `PlayerMovement`. Package root: `com.exiledradio.backportcore`.

## Depending on it

In the backport's `build.gradle`:

```gradle
repositories {
    flatDir { dirs 'deps' }
}
dependencies {
    // Plain compile against the MCP-named dev jar. Never deobfCompile from a flatDir: FG2 then
    // compiles against the SRG-named jar.
    compile name: "BackportCore-${core_version}-dev"
}
```

Then `mkdir deps` in the backport, add its folder name to `dependents` in this repo's `build.gradle`,
and run `gradlew build copyToDependents` here after every change. In the `@Mod` annotation:

```java
dependencies = "required-after:backportcore@[1.2.0,)"
```

and `"requiredMods": ["backportcore"]` in `mcmod.info`.

**Fluidlogged API** (3.3.3, CurseForge 485654) is Backport Core's one required dependency, for
waterlogging. Players install it beside Backport Core, on client and server; it is never bundled or
shaded (its license allows depending on it, not redistributing it). The dev jar does not carry it, so
every backport adds it to its own build, both to compile against `Waterloggable` and so that `runServer`
can load Core at all:

```gradle
repositories {
    maven { url 'https://cursemaven.com' }
}
dependencies {
    deobfCompile 'curse.maven:fluidlogged-api-485654:8445863'   // 3.3.3; needs stable_39 mappings
}
```

Keep `required-after:backportcore`; it brings Fluidlogged API with it.

Call the registration methods below from your mod's **pre-init** unless a section says otherwise.

## Textures (`client.ModernTextures`)

Nothing to call. The `modern` pack (1.14+ vanilla textures under 1.12.2 names) and the `classic` pack
(1.12.2 looks for the backports' own copies of old textures) live in `src/main/resources/backportcore_packs`,
built by `tools/modern-textures/generate.py`. The switch is `textures.modernTextures` in `backportcore.cfg`.
To add a later update's retextures, extend `manual_map.py` and regenerate; to give your own mod's
texture a classic look, add `'yourmod:path': 'vanilla path'` to `CLASSIC_MOD` (and your repository to
`MOD_REPOS` in `generate.py` so the name is checked).

## Duplicate blocks (`duplicate.DuplicateBlocks`)

```java
DuplicateBlocks.register("yourmod", "futuremc", () -> keepSetting(), () -> pairs);  // {ours, theirs} paths
DuplicateBlocks.registerItems(() -> extraItemsToHide);
```

`Keep.OURS` hides the other mod's half, `THEIRS` yours, `BOTH` nothing. Core removes the hidden halves'
crafting and smelting recipes and creative-tab entries. Builders call `DuplicateBlocks.preferred(block)` or
`preferred(state, extraProps)`; JEI plugins hide `DuplicateBlocks.hiddenItems()`.

## Future MC migration (`migrate.FutureMcTable`)

```java
FutureMcTable.Contributor t = FutureMcTable.contributor("yourmod", () -> config.convertRemovedFutureMc);
t.rename("mossy_stone_slab", "yourmod:mossy_cobblestone_slab");          // else same name in yourmod
t.rename("oak_wood", "minecraft:log|variant=oak,axis=none");            // forced properties
t.renameProperty("bamboo", "thick", "age", true);                       // true/false -> 1/0
t.ignoreProperty("smithing_table", "facing");
t.items((path, damage) -> ...);                                         // non-block items
t.recordedStates("/assets/yourmod/migration/futuremc_states.json");     // from /futuremc dump
t.contents((tile, stack) -> ...);                                       // lecterns, campfires...
t.itemFixer((target, stackTag) -> ...);                                 // NBT layout changes
```

- `/futuremc preview|convert|check|dump` (Core's command) and the automatic conversion of worlds Future MC
  was removed from both read every contributor. Run `/futuremc check` with Future MC installed after adding
  pairs: it must report 0 states that don't carry across.
- **Stand-ins** for one-to-many items: subclass `migrate.StandInItem`, implement `real(damage, count)`,
  register it under your own name. That name is in worlds forever.
- The saved data (`villagerbackport_futuremc`) and chunk flag (`villagerbackport:futuremcGeneration`) keep
  Village & Pillage's names because worlds already have them.

## Ownership (`ownership.BlockOwnership`) - Rule 1

```java
if (BlockOwnership.shouldRegister("yourmod", "blue_ice")) { /* register your copy */ }
Block ice = BlockOwnership.resolve("blue_ice", "yourmod");   // owner's if it provides one, else yours
BlockOwnership.declare("yourmod", "kelp", "dried_kelp_block"); // blocks you ship first
BlockOwnership.provides("yourmod", () -> config.blocksEnabled); // if yours can be switched off
```

Village & Pillage's 1.13 blocks (stripped logs and wood, the five wood trapdoors, buttons and pressure plates,
blue ice, sea pickles) are in the table already. Ask from your block registry event or later. Not done yet:
the owner remapping another mod's copies when it is added to a world later (use `RegistryEvent.MissingMappings`
plus the chunk conversion in `migrate.FutureMcRemoved` as the model).

## Smithing upgrades (`smithing.SmithingUpgrades`)

```java
SmithingUpgrades.add(diamondThing, Ingredient.fromItem(myIngot), new ItemStack(netheriteThing), "yourmod");
```

Before loading completes. The smithing table's owner calls `configure(...)`; tables and JEI read `find`,
`isUpgradable`, `isIngot`, `list`. Future MC's, Spartan Netherite's and crafting-grid upgrades are found
automatically.

## Trades (`trade.TradeRegistry`)

```java
TradeRegistry.addVillagerTrade("minecraft:farmer/fisherman", 2, (villager, random) -> recipe);
TradeRegistry.addTraderTrade(TradeRegistry.TraderPool.ORDINARY, random -> recipe);
```

Career keys are `"profession registry name/career name"`. Added villager trades join the pool of careers
that have a 1.14 table in Village & Pillage (the 13 vanilla careers and the mason); 1.14 picks two per level
from the pool. Override `available()` to drop a trade whose item is missing.

## Terrain (`terrain.TerrainHeights`, `terrain.WorldHeights`)

For structures laid out ahead of generation:

```java
TerrainHeights heights = TerrainHeights.forWorld(world);   // null: can't predict, use 1.12.2 behavior
int y = heights.height(x, z);                              // one above the top block, water included
boolean wet = heights.isWater(x, z);
```

```java
int bed = heights.floor(x, z);   // 1.14's OCEAN_FLOOR_WG: one above the sea bed; the ground where it is dry
```

Handles vanilla (and subclasses that keep vanilla noise), superflat, OTG, Biomes O' Plenty, RTG, Caves Not
Cliffs and Farmer's Future Delight. `floor` reads the same terrain as `height`, so it is as exact (Farmer's
Future Delight's surface-estimate fallback is the one approximation). Call `forWorld` once per structure start on the server thread: it drops
the previous layout's cache. `WorldHeights.minY/maxYExclusive/enterFullHeight` cover the 1.18-height mods.
`FlatTerrain.structures(generator)` gives a superflat world's structure map for swapping a generator.

## Waterlogging (`waterlog.Waterloggable`, `waterlog.Waterlogging`)

1.13's waterlogging for the family's blocks, through Fluidlogged API. A block that today's game makes
waterloggable implements `Waterloggable` (it extends Fluidlogged API's `IFluidloggable`) and answers up to
three questions. It never overrides the `IFluidloggable` methods and has no `waterlogged` property.

```java
public class BlockLantern extends Block implements Waterloggable {
    // all three optional:
    public Need waterNeed(IBlockState state) { return Need.OPTIONAL; }  // REQUIRED: kelp, seagrass, bubble columns
    public boolean canHoldWater(IBlockState state, IBlockAccess world, BlockPos pos) { return true; }  // false: a double slab
    public void onWaterChanged(World world, BlockPos pos, IBlockState state, boolean hasWater) { }    // coral dies, a campfire goes out
}
```

- Water only. A `REQUIRED` block that is drained is removed, with no drop. `onWaterChanged` runs after the
  water came or went (not when flowing water only changes level): buckets, flow, commands, the helpers below.
- These answers win over the player's `allowDefaults` setting. A server's Fluidlogged API config files and
  other mods' `FluidloggableEvent` handlers win over them.
- `shouldFluidRender` is client-only in Fluidlogged API: an override needs `@SideOnly(Side.CLIENT)`.

```java
Waterlogging.hasWater(world, pos);                     // a water block, or water kept beside a block
Waterlogging.hasWaterSource(world, pos);               // the same, still source water
Waterlogging.canHoldWater(state, world, pos);          // asks Fluidlogged API, its configs included
Waterlogging.setWater(world, pos, true, 3);            // add a source to the block there (false: remove its water)
Waterlogging.placeKeepingWater(world, pos, state, 2);  // generation: kelp into the sea, a reef over seagrass
Waterlogging.placeWithWater(world, pos, state, 2);     // with a source, whatever was there
Waterlogging.placeDry(world, pos, state, 2);           // no water, whatever was there (Fluidlogged API's flag 32)
Waterlogging.setWater(primer, x, y, z, true);          // terrain generation, in a ChunkPrimer
Waterlogging.migrateStoredProperty(block, WATERLOGGED); // a released block's own property, moved once per chunk
```

- Block flag 1 makes added water live, as a bucket's is (it flows into open space beside it at once).
  Without it the water stays still until something next to it changes, as generated water does.
- A plain `world.setBlockState` of a block that can hold water into a water source keeps the water too;
  Fluidlogged API does that for any such block.
- A block that shipped its own `waterlogged` property keeps the property and its meta bit while old worlds
  may hold it, stops setting it, and calls `migrateStoredProperty` in init. Each chunk is checked once,
  when it loads, and records that in its data (`backportcore:waterlogChecked`).
- Vanilla blocks get waterlogging from Fluidlogged API itself; with its defaults, vanilla slabs and stairs
  both hold water.

## Structures (`structure.*`)

Today's structure files, placed in 1.12.2, and structures scattered the way today's game scatters them.

### Templates (`ModernTemplate`, `TemplatePlacement`, `TemplateProcessor`)

```java
ModernTemplate t = ModernTemplate.load(new ResourceLocation("yourmod", "shipwreck/rightsideup_full"));
// assets/yourmod/structures/shipwreck/rightsideup_full.nbt (or structure/): Mojang's file, unchanged
TemplatePlacement p = TemplatePlacement.of(Rotation.CLOCKWISE_90, Mirror.NONE)
        .pivot(new BlockPos(4, 0, 15))   // today's center offset
        .integrity(0.9F).seed(seed)       // per world position: chunk by chunk gives the same as all at once
        .ignoreAir(true)                  // today's AIR_AND_STRUCTURE_BLOCK
        .keepWater(true)                  // the default: a block that can hold the sea there keeps it
        .palette(2)                       // shipwrecks have one per wood; -1 picks from origin and seed
        .processor(TemplateProcessors.replace("minecraft:stone_bricks", 0.3F, "minecraft:cracked_stone_bricks"))
        .clip(chunkBox);                  // the chunk being populated; never loads a neighbor
int placed = t.place(world, origin, p);
StructureBoundingBox box = t.boundingBox(origin, p);
BlockPos inWorld = ModernTemplate.worldPos(origin, localPos, p);
t.dataMarkers();       // data-mode structure blocks: pos (in the template), metadata ("chest", "map_chest")
t.jigsaws();           // pos, front, top, name, target, pool, joint, finalState: for jigsaw assembly
t.unresolvedBlocks();  // block names nothing provides; worth a test
```

- Reads every format since 1.13: `palette` or `palettes`, `Name`/`Properties` or 26.1's `id`/`properties`,
  any `DataVersion` (1.12.2's own files too). A block renamed since the file was saved is renamed first.
- Structure blocks are never placed (data markers are listed instead), structure voids are skipped, and
  jigsaw blocks become their final state. Tile entity data loads into the block, turned and mirrored with
  it; entities spawn turned with the structure. `waterlogged=true` places a water source with the block.
- A block that pops off for want of support in the next chunk (a wall torch) is written in anyway, and no
  item drops while a template places.
- A `TemplateProcessor` sees a `TemplateBlock`: today's name and properties (`is("minecraft:stone_bricks")`,
  `props()`), the 1.12.2 `state()`, `tile()` and `water()`. It returns the block, a changed copy
  (`withState`, `withWater`, `withTile`, `replacedBy("minecraft:mossy_stone_bricks")`), or null to leave the
  world as it is there.

### Palette (`ModernPalette`)

Where a structure file's block comes from, first match wins: a mapping registered here; the block's owner
(`BlockOwnership`); a family mod listed as a source that has a block of the same path; 1.12.2's own block
(Minecraft's Flattening table read backwards, `assets/backportcore/structure/flattening.txt`, built by
`tools/flattening/extract.py`, plus the blocks 1.13 split out of tile entities: bed and banner colors, skull
types, flower pot contents, note block notes); then a stand-in. Register in pre-init or init:

```java
ModernPalette.source("yourmod");                               // yourmod:kelp serves minecraft:kelp
ModernPalette.map("minecraft:conduit", props -> conduit.getDefaultState());
ModernPalette.map("minecraft:sea_pickle", () -> seaPickle);    // properties carried across by name
ModernPalette.fallback("minecraft:tuff_bricks", "minecraft:stone_bricks");
ModernPalette.lootTable("minecraft:chests/shipwreck_supply", new ResourceLocation("yourmod", "chests/shipwreck_supply"));
ModernPalette.Resolved r = ModernPalette.resolve("minecraft:oak_stairs[facing=east]");  // r.state, r.tile, r.water
```

Names are today's (`dirt_path`, `short_grass`, `oak_sign`, `smooth_stone_slab`, `iron_chain`), so a mapping
for `minecraft:stone_slab` means 1.14's plain stone slab. `villagerbackport` is listed as a source already.
Built-in stand-ins: non-oak wood trapdoors, buttons and pressure plates become oak's, stripped logs and wood
plain ones, blue ice packed ice, 1.14's plain stone slab the smooth one, cave and void air plain air, and
kelp, seagrass and bubble columns plain water (still counted wet).

### Pieces and scattered structures (`TemplatePiece`, `ScatteredPlacement`, `ScatteredStructure`)

```java
public class ShipwreckPiece extends TemplatePiece {
    public ShipwreckPiece() { }   // for loading
    ShipwreckPiece(ResourceLocation template, BlockPos origin, Rotation rotation, Random random) {
        super(template, origin, rotation, Mirror.NONE, new BlockPos(4, 0, 15), random);
    }
    @Override protected TemplatePlacement placement() { return super.placement().ignoreAir(true); }
    @Override protected void handleDataMarker(String metadata, BlockPos pos, World world, Random random,
                                              StructureBoundingBox chunk) {
        // pos is in the world and inside chunk: give the chest below its loot table
    }
    // writeExtra / readExtra for settings of your own
}

public class MapGenShipwreck extends ScatteredStructure {
    public MapGenShipwreck() { super("YM_Shipwreck", new ScatteredPlacement(24, 4, 165745295)); }
    @Override protected boolean canStartAt(World world, int chunkX, int chunkZ) { /* the biome; never load chunks */ }
    @Override protected StructureStart getStructureStart(int chunkX, int chunkZ) { /* lay out on TerrainHeights */ }
}

// init:
MapGenStructureIO.registerStructure(ShipwreckStart.class, "YM_Shipwreck");
MapGenStructureIO.registerStructureComponent(ShipwreckPiece.class, "YM_ShipwreckPiece");
ScatteredStructure.register(Structures.SHIPWRECK, world -> wanted(world) ? new MapGenShipwreck() : null);
```

- `ScatteredStructure.register` has Core lay the structure out and build it from the populate step in
  every world the factory gives an instance for, touching no chunk generator (so it works on modded
  terrain too), and registers the kind's locator. A start may reach at most 8 chunks from its own chunk.
- `TemplatePiece` keeps the palette chosen at layout, so every chunk builds with the same wood;
  `setOrigin` moves it once the layout knows the ground.
- `ScatteredStructure.of(world, kind)`, `isInside(world, kind, pos)` and `startsAt` are for spawn rules and
  tests. `ScatteredPlacement` alone is today's `RandomSpreadStructurePlacement` (`candidate`,
  `isCandidate`, `nearest`; linear or triangular).

### Finding structures (`Structures`)

```java
Structures.register(Structures.OCEAN_RUIN, (world, from, radiusChunks) -> ...);   // once per kind, in init
BlockPos p = Structures.nearest(world, Structures.SHIPWRECK, from, 50);           // null: none, or nobody finds it
BlockPos q = Structures.nearestOf(world, from, 50, Structures.OCEAN_RUIN, Structures.SHIPWRECK);
```

Kinds are today's structure names in lower case; there are constants for the family's (shipwreck,
ocean_ruin, buried_treasure, ruined_portal, bastion_remnant, nether_fossil, ancient_city, trail_ruins,
trial_chambers, village, pillager_outpost). A locator runs on the server thread, generates nothing and
loads no chunk it does not need. This is Update Aquatic's dolphin-stream `StructureLocator` API, unchanged.

Not here yet: jigsaw assembly (pools and expansion) and a nether floor sampler. Templates already read the
jigsaw blocks.

## Player poses (`pose.Poses`, `pose.PoseRules`, `pose.PlayerMovement`, `pose.client.PoseClient`)

1.14's player poses on 1.12.2, worked out the way 1.14.4's `PlayerEntity.updatePose` does, on the server
and on the player's own client, at the end of each player tick. Nothing happens until a backport registers
rules; with none on, players are vanilla 1.12.2's. With a mod installed that resizes players or adds
swimming of its own (by mod id), the engine stands down; with one that draws players its own way, only the
drawing does.

| Pose | Box | Eyes | Set by |
| --- | --- | --- | --- |
| `STANDING` | 0.6 x 1.8 | 1.62 | vanilla |
| `SNEAKING` | 0.6 x 1.65 | 1.54 | vanilla (1.12.2's sneak box; also where a crawler gets up to with room to sneak only) |
| `SWIMMING` | 0.6 x 0.6 | 0.4 (sneaking too) | the engine: swimming, or crawling (the swimming pose out of water) |
| `SPIN_ATTACK` | 0.6 x 0.6 | 0.4 | the engine: a Riptide spin |
| `FALL_FLYING`, `SLEEPING` | vanilla's | vanilla's | vanilla |

*   `Poses.register(PoseRules)` (pre-init): `enabled()` (read every tick, so a live switch works);
    `tick(player)` first, once a tick per simulated player; `isSwimming(player)`, `isSpinAttacking(player)`;
    `crawls()` - a player with no room to stand or sneak drops into the swimming pose and stays down until
    there is room. Several backports' rules are OR-ed.
*   `Poses.get(player)`, `Poses.isCrawling(player)` (swimming pose out of water), `Poses.swimAmount(player,
    partial)` (client: 1.14's swim animation, 0 to 1), `Poses.fits(player, pose)`, `Poses.box(player, pose)`,
    `Poses.active()`, `Poses.standsDown()`.
*   `Poses.registerMovement(PlayerMovement)` (pre-init): reached from inside vanilla's movement by patches
    P1 and P2 (`docs/PATCHES.md`), players only, while the rules are on. `beforeTravel` (start of
    `travel`), `waterSlowDown` (vanilla 0.8), `waterGravity` (vanilla 0.02), and `beforeClientMove` (client,
    after vanilla's sprint checks, before the player moves; the argument is the client player, cast it in a
    client-side class). Defaults are vanilla's; several are chained.
*   Sync: the server sends a player's pose to everyone tracking them when it changes, and to anyone who
    starts tracking them (Backport Core's channel `backportcore`, one 5-byte packet). The player's own client
    works its pose out itself.
*   Drawing (client, patches P3-P5): the swim stroke on every biped model drawn for a player (armor
    included), the body laid along the look in water and flat when crawling, the camera easing to the eye
    height. `PoseClient.addModelHook(PlayerModelHook)` adds a backport's own player arm pose, applied after
    vanilla's angles and before the stroke (where 1.14's `BipedModel` sets arm poses), for the player model
    and its armor; `PoseClient.modelHooksWork()` says whether it runs.

## Helpers

`util.BlockStates.props/with/withOrSame` - states by property name, as blockstate and structure files write them.

# Backport Core API

For the backports that depend on this library. Everything is plain static Java; no events to post. The interfaces
to implement are `Waterloggable`, `AgeLockable`, for player poses `PoseRules` and `PlayerMovement`, and for chunk
generators the sources under Terrain and Structures. Package root: `com.exiledradio.backportcore`.

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

with `core_version=1.4.7` in the backport's `gradle.properties`. Then `mkdir deps` in the backport, add its folder
name to `dependents` in this repo's `build.gradle`, and run `gradlew build copyToDependents` here after every change.
In the `@Mod` annotation, the oldest Core whose API the backport calls (the family's current releases require 1.4.7):

```java
dependencies = "required-after:backportcore@[1.4.7,)"
```

and `"requiredMods": ["backportcore"]` in `mcmod.info`.

**Fluidlogged API** (3.3.3, CurseForge 485654) gives waterlogging, and is optional. Backport Core's
CurseForge page lists it as a required relation so the app installs it, but in code nothing requires it:
Core and every backport load and play without it, and their `Waterloggable` blocks are then as dry as
before 1.13. Never bundle or shade it (its license allows depending on it, not redistributing it). The dev
jar does not carry it; every backport adds it to its own build so that `runServer` and the self-tests run
with it:

```gradle
repositories {
    maven { url 'https://cursemaven.com' }
}
dependencies {
    deobfCompile 'curse.maven:fluidlogged-api-485654:8445863'   // 3.3.3; needs stable_39 mappings
}
```

Keep `required-after:backportcore` and never name Fluidlogged API in your `@Mod` or `mcmod.info`. Main code
never names a Fluidlogged API class either, except in a class only reached when
`Waterlogging.isAvailable()` is true; self-test code may.

Call the registration methods below from your mod's **pre-init** unless a section says otherwise.

## Textures (`client.ModernTextures`)

Nothing to call. Vanilla's textures look as they do at the newest installed backport's version
(`Presentation.newest()`), as long as `textures.modernTextures` in `backportcore.cfg` is on (the default). Off, they
are 1.12.2's. Both packs live in `src/main/resources/backportcore_packs`:

- **modern**: one layer per release line that changed vanilla's textures, saved under 1.12.2's file names. Each
  layer holds only the files that changed since the layers below it:
  - `modern/` is 1.14's Texture Update (plus 1.15's enchantment glint). It always applies, because Village & Pillage
    registers no version. Written by `tools/modern-textures/generate.py`.
  - `modern-1.15/`, `modern-1.16/`, ... `modern-26.3/` (listed in `layers.txt`) hold each line's last release:
    1.15.2, 1.16.5, 1.17.1, 1.18.2, 1.19.4, 1.20.6, 1.21.11, 26.1.2, 26.2 and 26.3. A layer applies when the newest
    installed version is that version or later, so Caves & Cliffs (1.18.2) gets 1.17's ores and everything through
    1.18.2. Written by `tools/textures/build.py`, which also writes `tools/textures/report.txt`: what each layer
    took, and every texture left out with the reason.
  - `modern-1.21.5/` (1.4.4): every spawn egg's own untinted texture, 1.12.2's mobs and the family's, from 26.3's jar.
    Written by `tools/textures/eggs.py`; drawn by `client.SpawnEggModels`.

  A file comes from the newest layer that applies and has it, and its `.mcmeta` only from that same layer
  (`client.TextureLayers`, served by `client.LayeredPack`). The pack decides at each lookup, so the versions the
  backports register in pre-init reach the block atlas, which is stitched after pre-init.
- **classic**: the 1.12.2 look for the backports' own copies of old textures. Only used with the switch off.

**To add a release**: put its client jar in `tools/textures/tables.py` (`JARS`, and `LINES` for a new line), name
what it renamed in `RENAMES`, run `generate.py` and then `build.py`, and read `report.txt`. Later layers take only
textures that still fit 1.12.2. Block and item sprites always fit. A mob, armor, GUI or sky texture must keep 1.12.2's
aspect ratio and must not blank more than 1% of what 1.12.2's image paints, since a moved part or a new model
leaves 1.12.2's UVs on nothing. `EXCLUDE` and `LAYOUT` hold the changes this check cannot see, and `ACCEPT` the few it
refuses after a look shows only unused parts are blank. Keyed-transparency PNGs are rewritten as RGBA
(`tools/textures/png_rgba.py`). To give your own mod's texture a classic look, add `'yourmod:path': 'vanilla path'` to
`CLASSIC_MOD` in `tools/modern-textures/manual_map.py` (and your repository to `MOD_REPOS` in `generate.py` so the
name is checked).

Textures that never changed, or changed only in a way 1.12.2 can't show, stay as the layers below have them.
Examples: the GUI sheets 1.20.2 split into sprites, 1.21.5's cow, pig and chicken models, the horses, villagers,
water, banner and shield patterns, chests and the dragon. The title screen's textures, the buttons and the ghast are
the presentation module's (`Contribution.replaceAsset`), so they are never in these layers.

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
blue ice) are in the table already. Sea pickles are Update Aquatic's: when two backports need a block, it
belongs to the one it fits best, and Village & Pillage uses Update Aquatic's pickles when it is installed. Ask from your block registry event or later. Caves & Cliffs' tuff, copper ore, raw copper block and
every copper block (four stages, waxed or not: block, cut, cut stairs, cut slab, cut double slab) are in the table
too (1.3.0).

```java
BlockOwnership.provides("yourmod", () -> config.copperEnabled, "copper_block", "cut_copper");  // one switch per name
BlockOwnership.declare("yourmod:switched_off", "tuff");   // the owner itself: "not provided in this game"

// Items and effects (1.3.0): copper ingot and raw copper (Caves & Cliffs), honeycomb (Buzzy Bees),
// Bad Omen (Village & Pillage), the ominous bottle (Tricky Trials, since 1.4.5; Village & Pillage's before).
Item ingot = ItemOwnership.item("copper_ingot", "yourmod");     // the owner's if provided, else yours, else null
boolean copy = ItemOwnership.shouldRegisterItem("yourmod", "copper_ingot");
Potion omen = ItemOwnership.effect("bad_omen", "yourmod");
ItemOwnership.declareItems("yourmod", "your_item");
```

**Copies become the owner's** (`ownership.CopyRemapping`, 1.3.0, nothing to call): when an owner is added to a
world that holds a family mod's copies (`copier:name`, registered while the owner was absent), each missing name is
remapped onto `owner:name` as the world loads, for blocks, items (block items too) and effects. Only names under a
family mod's id, only when the owner owns that name, provides it and has it registered.

## Copper (`copper.Oxidation`, 1.3.0)

One table for every mod's copper, so axes, honeycomb and lightning treat all of it alike and copper waits for the
least oxidized copper near it whichever mod it comes from.

```java
Oxidation.chain(bulb, exposedBulb, weatheredBulb, oxidizedBulb);   // unaffected first
Oxidation.waxed(bulb, waxedBulb);                                  // each stage
Oxidation.randomTick(world, pos, state, rand);                     // from the block's randomTick: current Java's rule
Block scraped = Oxidation.previous(block);  Oxidation.change(world, pos, state, scraped);   // keeps the state; doors whole
Oxidation.next(b); Oxidation.waxedOf(b); Oxidation.unwaxed(b); Oxidation.first(b); Oxidation.stage(b); Oxidation.isCopper(b);
```

## Walls (`block.ModernWall`, 1.3.0)

The family's one 1.16+ wall: each side none, low or tall, the post dropped on a straight run, joining 1.12.2's walls,
panes, fence gates and solid faces; metadata 0, so a `BlockWall` moved onto it keeps its saved blocks. Use vanilla's
`<name>_wall.json` multipart blockstate. Implements `Waterloggable`.

```java
Block wall = new ModernWall(base.getDefaultState()).setRegistryName("yourmod", "tuff_wall");
```

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
the previous layout's cache. `WorldHeights.minY/maxYExclusive/enterFullHeight` cover the 1.18-height mods,
Caves & Cliffs Backport's extended height among them (1.3.0; `isCavesAndCliffsExtended(world)`).

A chunk generator that knows its own surface implements `TerrainSource` (1.3.0): `forWorld` asks its
`columnTerrain(world)` before any other adapter.
A chunk generator that places today's features itself (1.12's biome decorator does not run in its worlds)
implements `DecorationSource` (1.3.0). A backport whose 1.12 hook adds a feature the generator already places steps
aside: `if (DecorationSource.places(world, "bee_nest")) return;` (or `places(world, event.getGenerator(), name)` in
a populate handler). Names are today's vanilla features or the block they place, without a namespace.
`FlatTerrain.structures(generator)` gives a superflat world's structure map for swapping a generator.

A generator that runs a newer version's world generation in full (its 3D biomes and every placed feature: coral,
kelp, seagrass, icebergs ...) implements `ModernGeneration` (1.3.0). Caves & Cliffs Backport's 1.18 generator does,
for 1.18.2. In its worlds a backport's own generator of such a feature steps aside, and biome rules read its biome:

```java
ModernGeneration modern = ModernGeneration.of(world);          // or .of(generator) in an IWorldGenerator; null: none
if (modern != null) return;                                    // the world's generator already placed this feature
String id = ModernGeneration.biomeId(world, pos);              // "minecraft:lush_caves" under an ocean; null: none
```

`biomeId` loads and generates nothing (server thread).

### Whole columns and the nether's floors (`terrain.NetherHeights`, 1.1.0)

```java
NetherHeights heights = NetherHeights.forWorld(world);   // columns as they will generate (caves included)
int floor = heights.floor(x, z, 32, 120);   // lowest open block standing on a solid one: NONE, or UNKNOWN
int[] all = heights.floors(x, z, 0, 127);   // every floor, lowest first
int bed = heights.floor(x, z);              // ocean-floor variant: one above the top solid block, water and lava ignored
int top = heights.height(x, z);             // WORLD_SURFACE_WG: one above the top block that is not air
heights.isSolid(x, y, z); heights.isLiquid(x, y, z); heights.isAir(x, y, z);
NetherHeights base = NetherHeights.baseForWorld(world);  // 1.16's getBaseColumn: bare noise, no caves or surface
```

- In the nether, a copy of the world's own `ChunkGeneratorHell` (or a subclass with its constructor) builds the
  chunk off to the side, so a column matches the generated chunk block for block until population adds glowstone,
  fire, lava falls and fortresses. "Open" means air, fire or plants; lava is neither a floor nor air.
- In the overworld, `floor(x, z)` and `height(x, z)` are `TerrainHeights`' (every terrain it reads); the column
  queries use a copy of the vanilla overworld generator.
- With any other generator, columns are read from loaded chunks only. A column of a chunk that is not loaded is
  `UNKNOWN` (`predicts()` is false): lay such a structure out at a fixed height, as 1.16's bastions are (y 33).
- Use `baseForWorld` where 1.16 measures its base column (ruined portals' `findSuitableY` corner test), for parity.
  As with `TerrainHeights`, call it once per structure start on the server thread: it drops the last layout's columns.

### Realistic Terrain Generation biomes (`terrain.RtgBiomes`, 1.4.5)

RTG (6.1 and its Unofficial fork, modid `rtg`) builds each column from a "realistic biome" registered for the
column's biome; any other biome becomes RTG's patch biome (plains), terrain and saved biome id alike. A backport
names the vanilla biome whose RTG terrain fits each surface biome, right after registering it:

```java
RtgBiomes.register(biome, RtgBiomes.Template.FOREST_HILLS);
// or by name, for a backport that must load with an older Core:
Class.forName("com.exiledradio.backportcore.terrain.RtgBiomes").getMethod("register", Biome.class, String.class)
        .invoke(null, biome, "FOREST_HILLS");
```

- Templates: `OCEAN`, `DEEP_OCEAN`, `PLAINS`, `FOREST`, `FOREST_HILLS`, `BIRCH_FOREST`, `JUNGLE`, `JUNGLE_HILLS`,
  `SWAMP`, `ROOFED_FOREST`: RTG's own terrain for that vanilla biome, the biome's top and filler blocks painted by
  RTG's generic surface (its swamp and ocean surfaces for those), no RTG decorations on land. RTG then runs the
  biome's own `decorate`, so trees and features stay the backport's.
- Core hands the list to RTG in its post-init (RTG locks it at load complete); later calls go straight through.
  Without RTG nothing happens. The only class linking against RTG is `terrain.rtg.RtgRealisticBiomes`.
- RTG needs a `ModCompat.Mods` constant per mod (its config folder, `config/RTG/biomes/<ModName>/`); Core adds one
  with EnumHelper.
- `RtgBiomes.biomeAt(world, x, z)`: the biome RTG gives a column once its chunk is made, after its per-chunk coast
  repair, without making the chunk (null outside RTG worlds). The biome provider does not know the repair.
  `StructurePlacements.biomeAt(World, chunkX, chunkZ)` uses it, so a start check matches the chunk in RTG worlds;
  Core's temples read it.

## Dynamic Trees (`compat.DynamicTrees`, 1.4.9)

Dynamic Trees (0.9.x, modid `dynamictrees`) grows its own trees in the biomes its biome database says to and denies
1.12.2's TREE, BIG_SHROOM and CACTUS decoration there. It asks the biome at the chunk's corner; 1.12.2 decorates with
the biome at corner + 16. Core re-answers those events at LOWEST priority by the decorating biome, so a backport biome
next to one of Dynamic Trees' gets one kind of tree per chunk, with nothing to do in the backport. Everything else:

- `replacesTrees`, `replacesCactus` (world, biome or pos): whether Dynamic Trees grows its own there. A
  backport placing trees outside the TREE event (a terrain generator, a populate hook) leaves them out where true.
  False without Dynamic Trees, on a client world, in a blacklisted dimension.
- `grow(world, pos, random, feature)`: a structure's tree (village, camp) as Dynamic Trees' species, where it replaces
  the biome's trees and has one for today's feature (oak, fancy oak, birch, super birch, spruce, pine, mega spruce and
  pine, jungle, mega jungle, acacia, dark oak). `pos` is where the sapling would stand. False: place your own tree.
- `biomeData(new ResourceLocation(modid, "worldgen/dynamictrees.json"))`, pre-init: a worldgen JSON in Dynamic Trees'
  own format for the backport's biomes, read before the pack's `config/dynamictrees/worldgen.json`.
- The only class linking against Dynamic Trees is `compat.DynamicTreesLink` (`deobfProvided`). Every 1.12.2 release
  of it needs Forge 2859 or later, so it never loads in the 2847 dev environment: `selftest/dynamictrees/
  DynamicTreesCheck` (`gradlew dynamicTreesCheckJar reobfDynamicTreesCheckJar -Pselftest`) surveys a real server
  with it, from a plan file `dynamictrees-check.txt` in the server folder.

## Waterlogging (`waterlog.Waterloggable`, `waterlog.Waterlogging`)

1.13's waterlogging for the family's blocks, through Fluidlogged API when it is installed. A block that
today's game makes waterloggable implements `Waterloggable` (a plain interface) and answers a few
questions. With Fluidlogged API installed, Core's patch P19 adds Fluidlogged API's `IFluidloggable`
(answered from these) to the block's class; the block never implements `IFluidloggable` itself and has
no `waterlogged` property.

```java
public class BlockLantern extends Block implements Waterloggable {
    // all optional:
    public Need waterNeed(IBlockState state) { return Need.OPTIONAL; }  // REQUIRED: kelp, seagrass, bubble columns
    public boolean canHoldWater(IBlockState state, IBlockAccess world, BlockPos pos) { return true; }  // false: a double slab
    public boolean acceptsFlowingWater(IBlockState state) { return false; }  // as 1.13: only a source goes in
    public Boolean waterFlowsThrough(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) { return null; }
    public void onWaterChanged(World world, BlockPos pos, IBlockState state, boolean hasWater) { }    // coral dies, a campfire goes out
}
```

`waterFlowsThrough` (1.4.4) is Fluidlogged API's `canFluidFlow`: null keeps its rule by the face's shape. A block with
a full collision box answers false, as 26.3's `canPassThroughWall` (water kept in leaves stays there).

- Water only. A `REQUIRED` block that is drained is removed, with no drop. `onWaterChanged` runs after the
  water came or went (not when flowing water only changes level): buckets, flow, commands, the helpers below.
- These answers win over the player's `allowDefaults` setting. A server's Fluidlogged API config files and
  other mods' `FluidloggableEvent` handlers win over them.
- **Without Fluidlogged API** (`Waterlogging.isAvailable()` false): no block holds water, `onWaterChanged`
  never runs, and the helpers answer as 1.12.2 would (water blocks only; `setWater` adds nothing,
  `placeWithWater` places dry and returns false). Give a block its pre-1.13 behavior on that path, such as a
  water bucket putting a campfire out. Older worlds' Fluidlogged API water is kept in their chunks for when it
  is installed again, and `migrateStoredProperty` waits for it.

```java
Waterlogging.hasWater(world, pos);                     // a water block, or water kept beside a block
Waterlogging.hasWaterSource(world, pos);               // the same, still source water
Waterlogging.isAvailable();                            // Fluidlogged API installed: blocks can hold water
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
- **Every height** (1.3.0): in Caves & Cliffs Backport's extended worlds (y -64 to 319) Core keeps the water
  outside y 0-255 itself, one store per section (patch P20), saved with the chunk and sent to clients, so every
  method above works there. Other worlds keep Fluidlogged API's own store. A backport does nothing for it. The
  `ChunkPrimer` form only takes y 0-255 (Fluidlogged API's primer has no other heights): set deeper water once the
  chunk exists.
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
t.forEachBlock(0, (local, block) -> { });   // every block of one palette, before processors (1.1.0)
```

1.1.0 adds two settings to `TemplatePlacement`: `finalizeEntities(true)` gives mobs their `onInitialSpawn`, as
jigsaw pieces do, and `followTerrain(surface, offset)` is today's gravity processor. A jigsaw block whose file has
no `joint` is now read as today's game reads it: `aligned` facing sideways, `rollable` facing up or down. 1.1.0
read every such block as `rollable`.

- Reads every format since 1.13: `palette` or `palettes`, `Name`/`Properties` or 26.1's `id`/`properties`,
  any `DataVersion` (1.12.2's own files too). A block renamed since the file was saved is renamed first.
- Structure blocks are never placed (data markers are listed instead), structure voids are skipped, and
  jigsaw blocks become their final state (1.4.5: `keepJigsaws(true)` places them as Core's jigsaw block, with their
  data; Core's `backportcore:jigsaw` in a file a 1.12.2 structure block saved is a jigsaw too). Tile entity data loads into the block, turned and mirrored with
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
ModernPalette.entity("minecraft:piglin", new ResourceLocation("yourmod", "piglin"));   // 1.1.0: entities of the files
ModernPalette.Resolved r = ModernPalette.resolve("minecraft:oak_stairs[facing=east]");  // r.state, r.tile, r.water
```

`minecraft:jigsaw` is Core's jigsaw block (1.4.5).

Items have the same lookup (`structure.ModernItems`, 1.4.4), for loot tables, recipes and trades read from a newer
game's files: a backport's mapping, then 1.12.2's renamed items (`structure/item_names.txt`, from the data fixers,
`tools/flattening/items.py`), then the same name in the registry. Init or later.

```java
ItemStack web = ModernItems.stack("minecraft:cobweb");                          // 1.12.2's web; EMPTY: none
ModernItems.map("minecraft:resin_clump", () -> new ItemStack(MyItems.RESIN_CLUMP));
```

Names are today's (`dirt_path`, `short_grass`, `oak_sign`, `smooth_stone_slab`, `iron_chain`), so a mapping
for `minecraft:stone_slab` means 1.14's plain stone slab. `villagerbackport` is listed as a source already.
Built-in stand-ins: non-oak wood trapdoors, buttons and pressure plates become oak's, stripped logs and wood
plain ones, blue ice packed ice, 1.14's plain stone slab the smooth one, cave and void air plain air, and
kelp, seagrass and bubble columns plain water (still counted wet).

A property a file or processor leaves out has the block's default value, as in the game: a bare
`minecraft:stone_slab` (what `block_age` writes) is a bottom half, bare stairs face north. A family mod's slab
(owner or source) follows the family's 1.12.2 convention: `type=bottom|top` sets `half` on `<name>_slab`, and
`type=double` is the same mod's `<name>_double_slab`.

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

### Where a world's generator starts structures (`StructureSource`, `StartRule`, `StartRules`, 1.3.0)

A chunk generator can decide where today's structures start in its worlds, whichever backport builds them. Caves &
Cliffs Backport's 1.18 generator does for shipwrecks, ocean ruins, buried treasure, ruined portals and pillager
outposts: 1.18.2's structure sets, its biome checks at the start's height, its choice between a set's structures.

```java
public class MyGenerator implements IChunkGenerator, StructureSource {
    @Override public StartRule startRule(World world, String kind) { ... }   // null: the kind keeps its own rule
}
public interface StartRule {
    ScatteredPlacement grid();                   // the candidates' grid, for locators and /locate
    @Nullable String startAt(int chunkX, int chunkZ);   // today's id ("ocean_ruin_warm"), or null: none starts
}
StartRule rule = StartRules.of(world, Structures.PILLAGER_OUTPOST);   // null where the kind keeps its own rule
```

- A `ScatteredStructure` follows the rule with no change: `canSpawnStructureAtCoords`, `locatableAt`, `nearest` and
  `placement()` (so `/locate`) use it, and `canStartAt` is not asked. A kind with variants picks the one
  `ruledStart(world, x, z)` names (warm or cold ruins, a ruined portal's type, a beached shipwreck).
- A structure on a `MapGenStructure` of its own (Village & Pillage's outposts) asks `StartRules.of` in its start
  check, its nearest search and its `/locate` grid.
- Both methods are pure arithmetic on the seed and the generator's noise: never load or generate chunks. Asked on the
  server thread; a world's answer per kind is kept for the world's life.

### Terrain adaptation (`TerrainAdaptation`, 1.3.0)

Structures today's terrain shapes itself around (`terrain_adaptation`; 1.18.2's `adapt_noise`), for a generator that
can do it (Caves & Cliffs Backport's 1.18 generator adds the beardifier around their pieces before it fills a chunk).

```java
TerrainAdaptation.register(Structures.PILLAGER_OUTPOST, TerrainAdaptation.Kind.BEARD_THIN,
        (world, chunkX, chunkZ, out) -> outposts(world).startsNear(world, chunkX, chunkZ, out));   // init
public class MyPiece extends StructureComponent implements TerrainAdaptation.AdaptingPiece {
    public boolean rigid() { ... }   public int groundLevelDelta() { ... }   public int[] junctions() { ... }
}
```

- The planner gives every start whose box, grown by 12 blocks, reaches the chunk, laying out any not laid out yet as
  its populate step would (and keeping it, so the populate step builds the same layout). The layout must not depend
  on blocks in the world: lay out on `TerrainHeights`.
- Pieces that are not `AdaptingPiece`s are rigid, their ground at the box's floor. `Kind`s are today's
  `TerrainAdjustment`s; 1.18.2's terrain knows `BEARD_THIN` (its beard) and `BURY` (its strongholds).
- Kinds are summed in registration order, after the generator's own structures.

### A backport's generator in a world generator's place (`StructureGenerators`, 1.3.0)

For a chunk generator that builds its 1.12.2 structure generators itself, without Forge's `InitMapGenEvent` (Caves
& Cliffs Backport's 1.18 generator): it offers each one, and the backport that builds the kind returns its own.

```java
StructureGenerators.register(Structures.VILLAGE, (world, standIn) -> new MyVillages(standIn));   // init; null keeps it
MapGenStructure villages = StructureGenerators.replace(world, Structures.VILLAGE, ownVillages);   // the generator
```

- The replacement does everything the stand-in did (starts, populating, `/locate`, `isInsideStructure`); the world
  generator no longer lays out the stand-in's starts or adapts its terrain to them. Register the replacement's kind
  with `TerrainAdaptation` for that, and ask `StartRules.of` for its starts where the generator rules them.
- Asked once per world while its chunk generator is built, so `world.getChunkProvider()` may not be set yet.

### Features a world generator places itself (`worldgen.FeatureSource`, 1.3.0)

```java
public class MyGenerator implements IChunkGenerator, FeatureSource {
    @Override public String featureVersion(World world) { return "1.18.2"; }   // null: none of its own
}
if (FeatureSource.places(world, "1.14")) return;   // an IWorldGenerator for a 1.14 feature steps aside
```

Caves & Cliffs Backport's 1.18 generator places every 1.18.2 feature, with the backports' blocks where they are
installed (Village & Pillage's sweet berry bushes, bamboo and flowers): their own generators for those step aside.

### Superflat worlds (`worldgen.FlatWorlds`, 1.3.0)

A superflat world generates only what its preset lists. 1.12.2's options: `decoration` (the biome's decorator:
flowers, grass, trees, ores, every feature), `village`, `stronghold`, `mineshaft`, `biome_1` (temples, witch huts,
igloos), `oceanmonument` (these five only with structures on), `dungeon`, `lake`, `lava_lake`. Structures newer than
1.12 have no option: today's superflat generates them only where a preset lists them (`structure_overrides`), and the
default presets list villages at most, so the family never generates them in superflat. Pillager outposts are among
them (today's default preset has villages only).

```java
if (!FlatWorlds.decoration(world)) return;                              // an IWorldGenerator or PopulateChunkEvent feature
if (!FlatWorlds.structure(world, Structures.VILLAGE)) return;           // 1.14 villages follow `village`
FlatWorlds.allows(world, FlatWorlds.DUNGEON);                           // any option; true outside superflat
FlatWorlds.isFlat(world);                                               // the Overworld of a superflat world only
```

`DecorateBiomeEvent` handlers need nothing: 1.12.2's flat generator runs the decorator only with `decoration`.
`IWorldGenerator`s and `PopulateChunkEvent` handlers run in every world, so a feature there asks. Core asks for every
`ScatteredStructure` kind (no instance in a superflat world that cannot have it), every `Structures` locator (null)
and `/locate structure` (not found). Other dimensions and other world types are never superflat here.

### Jigsaw structures (`Jigsaw`, `JigsawPools`, `JigsawPiece`, `ModernProcessors`, 1.1.0)

1.16.5's jigsaw assembly (`JigsawPlacement.addPieces`), read from Mojang's own files. Given the same random, it lays
out what 1.16.5 does, piece for piece: the self-test compares 24 whole bastions with 1.16.5's own code.

```java
JigsawPools.source("yourmod");   // pre-init: minecraft:... files are read from yourmod's assets
// assets/yourmod/worldgen/template_pool/bastion/starts.json        Mojang's pool files, unchanged
// assets/yourmod/worldgen/processor_list/bastion_generic_degradation.json
// assets/yourmod/structures/bastion/units/air_base.nbt              the templates

public static final class BastionStart extends StructureStart {
    public BastionStart() { }
    BastionStart(World world, int chunkX, int chunkZ) {
        super(chunkX, chunkZ);
        // BastionFeature: start y 33, depth 6. 1.16's start random gives 1.16's layout for the seed.
        this.components.addAll(Jigsaw.assemble(world, new ResourceLocation("minecraft", "bastion/starts"), 6,
                new BlockPos(chunkX << 4, 33, chunkZ << 4), StructureRandoms.start(world.getSeed(), chunkX, chunkZ)));
        updateBoundingBox();
    }
}
// villages and outposts: Jigsaw.of(pool).maxDepth(6).expansionHack(true).projectStartToHeightmap(true).assemble(...)
```

- **Assembly** follows 1.16.5 draw for draw. It picks the start rotation and element, puts the start piece's floor
  one below the origin, and bounds everything in a cube 80 blocks out from the start piece's middle. Each jigsaw,
  in shuffled order, tries its pool's elements and then the fallback pool's (only the fallback's at the last
  depth), each in shuffled rotations. A child joins if `canAttach` holds (front, top unless `rollable`, target and
  name) and it fits the free space: the cube minus every piece, or the parent's own box for a piece that joins
  from inside it. The expansion hack is there too. Assembly loads and generates nothing.
- **Pools**: `name`, `fallback` and weighted `elements`. Element types are `single_pool_element`,
  `legacy_single_pool_element` (air left out), `list_pool_element`, `feature_pool_element` and
  `empty_pool_element`. 1.12.2 has none of today's configured features, so a feature element is placed by
  `JigsawPools.feature("minecraft:oak", placer)`; `JigsawPools.placer(idOrType)` (1.4.4) gives the placer registered
  for an id, so a backport can wrap another's. Projections are `rigid` and `terrain_matching`. `processors` may
  be a list's id, the `{"processors": [...]}` object, or the bare array. `minecraft:empty` (pool and processor
  list) is built in.
- **Pieces** (`JigsawPiece`, which Core registers as `BCJigsaw`) save their element as its JSON and place chunk by
  chunk, with the chunk as the clip. They add 1.16's jigsaw-piece processors in 1.16's order and give mobs their
  first-spawn setup. For data markers, subclass it, with a `Jigsaw.PieceFactory` and a `MapGenStructureIO` name
  of your own.
- **Processors** (`ModernProcessors`): `rule` (all six of 1.16's block tests, `linear_pos` and
  `axis_aligned_linear_pos`; location tests compare with the 1.12.2 block in the world), `block_ignore`,
  `block_rot`, `gravity`, `block_age`, `blackstone_replace`, `lava_submerged_block`, `jigsaw_replacement` and
  `nop`. Each draws from its own position random, as 1.16's do. `ModernProcessors.blockAge(0.5F)`,
  `blackstoneReplace()`, `lavaSubmerged()` and `blockRot(f)` build the ruined portals' processors in code.
  `ModernProcessors.tag("minecraft:doors", names...)` adds to a `tag_match` tag.
- **Block names** are today's: a 1.16 file's `minecraft:chain` is read as `minecraft:iron_chain` (26.x renamed it).

### Today's jigsaw rules (`ModernJigsaw`, 1.4.4)

`Jigsaw` stays 1.16.5's (bastions, villages). `ModernJigsaw` is the placer of 1.20 to 26.x (`JigsawPlacement`'s
`Placer`), for a structure that lays out its own start piece (the stub: start height, projection, padding and biome
checks stay the structure's) and then hands it over:

```java
public class MyPiece extends JigsawPiece implements ModernJigsaw.Joined { ... }   // keeps addJunction's junctions

Map<String, String> aliases = ModernJigsaw.PoolAliases.read(json.getAsJsonArray("pool_aliases")).resolve(seed, origin);
List<MyPiece> pieces = ModernJigsaw.of(MyPiece::new).maxDepth(size)
        .priorities(true)          // 1.20.3+: selection_priority and placement_priority
        .aliases(aliases)          // 1.20.5+: pool_aliases (direct, random, random_group)
        .namelessFeatures(true)    // 26.x: a feature element's jigsaw joins any jigsaw
        .expansionHack(false)      // use_expansion_hack
        .surface(ground)           // terrain_matching pieces; null takes the joint's own height
        .assemble(start, bounds, random);   // bounds: max_distance_from_center, cut to the world as the version does
```

- Every shuffle is `Util.shuffle` (n - 1 draws); 1.16.5's pool shuffle is fastutil's, one more. The pieces opened by a
  jigsaw are worked through by its `placement_priority`, first in, first out within one. Each join records today's
  junctions on both pieces, for `TerrainAdaptation`'s beard. Every piece gets today's pivot.
- Template jigsaws carry their priorities (`ModernTemplate.Jigsaw` and `JigsawElement.Connector`:
  `placementPriority`, `selectionPriority`, 0 in older files).
- Game Drops' abandoned camps (26.3), Tricky Trials' trial chambers (1.21.11) and Trails & Tales' trail ruins (1.20.1)
  are laid out with it.

### Data-driven structures (`DataStructures`, `DataPacks`, `DataPlacement`, `HeightProviders`, `DataTags`, 1.4.5)

For porters, a step-by-step guide is [PORTING_STRUCTURES.md](PORTING_STRUCTURES.md).

Core generates the jigsaw structures today's data files describe. Porting a modern jigsaw structure mod is copying its
`data/` folder into a 1.12.2 jar (any mod jar; the `@Mod` class can be empty), or into a structure pack: a folder,
`.zip` or `.jar` in `config/backportcore/structures/`, laid out like a datapack (`data/` at its root or inside one top
folder). A pack's file replaces a jar's file of the same path.

Read at load complete, after every backport registered its own structures:

- `data/<ns>/worldgen/structure_set/*.json` and the structures they name, `worldgen/structure/*.json`.
- Pools, processor lists and templates through `JigsawPools` and `ModernTemplate`, which now also look in
  `data/<ns>/worldgen/template_pool`, `worldgen/processor_list` and `structure/` (or `structures/`), after `assets/`.
- Biome tags from `data/<ns>/tags/worldgen/biome`, merged over every jar and pack (`replace` honored, nested tags
  resolved), plus what backports gave `BiomeLocate.tag`. Core ships 26.3's biome tags (`#minecraft:is_ocean`,
  `#minecraft:has_structure/...`). Structure tags (`tags/worldgen/structure`) go to `/locate structure #tag`.

Supported, as 26.3 has it:

- `minecraft:jigsaw`: `start_pool` (through `pool_aliases`), `size`, `start_height` (any height provider: constant,
  uniform, biased_to_bottom, very_biased_to_bottom, trapezoid, weighted_list; anchors absolute, above_bottom,
  below_top, relative_to_sea_level), `start_jigsaw_name`, `project_start_to_heightmap` (`OCEAN_FLOOR*` is the sea bed,
  the rest the surface, from `TerrainHeights`), `max_distance_from_center` (int or `{horizontal, vertical}`),
  `use_expansion_hack`, `pool_aliases`, `dimension_padding`, `liquid_settings`, `terrain_adaptation` (registered with
  `TerrainAdaptation`), `biomes` (id, list or `#tag`), `spawn_overrides` (monster, creature, ambient, water_creature;
  `piece` or `full`). Assembly is `ModernJigsaw` with 26.3's rules, from `StructureRandoms.start`.
- `random_spread`: `spacing`, `separation`, `salt`, `spread_type`, `frequency` with `frequency_reduction_method`
  (default, legacy_type_1/2/3), `exclusion_zone` (around a loaded set, or 26.3's vanilla set of that id), `locate_offset`.
- A set of several structures picks as 26.3's `ChunkGenerator.createStructures`: weighted draws from
  `setLargeFeatureSeed`, each structure tried with a fresh start random, dropped when it has no valid start.

Heights: anchors resolve against the world's own heights (1.12.2's overworld is 0 to 255). An `absolute` height below
the world's bottom moves up proportionally, `minY + (y + 64) * height / 384`, as trial chambers do; nothing moves in a
world that reaches y -64. Dimensions: the overworld, the nether for nether biomes, the end for end biomes.

Skipped, each logged: other placement types (`concentric_rings`), other structure types (vanilla's hardcoded ones),
structure ids a backport or 1.12.2 already generates (`StructureLocate.ids()`, `Structures.has`), and ids, sets or
`namespace:*` in the config's `structure.blacklist`. `structure.dataStructures` turns it all off. `step` is not used:
every structure is built from Core's populate driver.

Each set is a `ScatteredStructure`; its kind is the structure's id (or the set's id for several), and `/locate
structure <id>` finds each structure. Starts save as `BCData` with their structure's id, pieces as `BCDataPiece`
(`DataStructures.Piece`: junctions and liquid settings; a `JigsawPiece` subclass overrides `applyWaterlogging()`,
false for `ignore_waterlogging`). `DataStructures.sets()`, `structures()`, `skipped()`,
`choose(set, world, x, z)` and `startOf(def, world, x, z)` answer what loaded and what starts where; none loads a chunk.

What a porter still does by hand: feature pool elements need a `JigsawPools.feature` placer, loot tables and
entities in templates need 1.12.2 counterparts (`ModernPalette.lootTable`, `ModernPalette.entity`), blocks 1.12.2
lacks need `ModernPalette.map` or `fallback`, and biome ids of mods that are not today's names need `BiomeLocate.biome`.

### The jigsaw block (`block.CoreBlocks`, `block.TileEntityJigsaw`, 1.4.5)

Today's jigsaw block, `backportcore:jigsaw`, to build and test jigsaw structures in game as in 26.3.

- **Block**: `orientation`, 26.3's twelve `FrontAndTop` values (metadata is the ordinal). It faces the clicked face;
  facing up or down, its top points toward the player. An operator block: only a player in creative mode with
  permission level 2 (`canUseCommandBlock`) places it, opens it or breaks it. Hardness -1, no drops, epic rarity. In
  no 1.12.2 creative tab (as up to 1.19.2); 1.19.3's Operator Utilities lists it. 26.3's model and textures.
- **Tile entity**: `name`, `target`, `pool`, `final_state`, `joint` (`rollable` or `aligned`), `selection_priority` and
  `placement_priority`, under 26.x's keys. Without `joint` it is aligned facing sideways, rollable facing up or down.
- **Screen** (`block.client.JigsawScreen`): 26.3's fields, buttons, places and labels. Done and Generate send
  `JigsawNetwork.Set` and `Generate` on channel `backportcore_jigsaw`. The server applies them only for a player who
  may use operator blocks, and refuses a malformed id.
- **Generate** is `ModernJigsaw.generate(world, pool, target, levels, position, keepJigsaws)`, 26.3's
  `JigsawPlacement.generateJigsaw`. `position` is the block in front of the jigsaw. The random is the large-feature
  random of the chunk `position` is in. It draws the start rotation and element; the element's jigsaw named `target`
  (first of its shuffled jigsaws) goes at `position`, the piece's floor one below. The rest joins on by today's rules
  (priorities, nameless features, no expansion hack), `levels` deep, 128 blocks out, and every piece is placed at once
  with `position` as its pivot. `ModernJigsaw.layout` gives the pieces without placing them. With Keep Jigsaws the
  jigsaw blocks stay (`TemplatePlacement.keepJigsaws`); otherwise each becomes its final state.
- **Files**: pools are `JigsawPools`': the mods' files, then the world's folder data packs
  (`<world>/datapacks/<pack>/data/<namespace>/worldgen/template_pool/`). Templates are the mods' first, then the
  world's: `<world>/structures/<path>.nbt` (where 1.12.2's structure block saves), `<world>/generated/<namespace>/structures/`
  and folder data packs. Each Generate reads the world's files again.
- **Saving**: a 1.12.2 structure block saves jigsaw blocks with their data (`backportcore:jigsaw` in the palette).
  `ModernTemplate` reads them as it reads `minecraft:jigsaw`, so a structure saved in game assembles with Core. A
  `final_state` may be today's state text or a 1.12.2 block name (`minecraft:planks[variant=birch]`);
  `minecraft:structure_void` places nothing, as today.
- **Not done**: saving in today's format. A file saved in game has 1.12.2's block names and `DataVersion` 1343: Core
  reads it, today's game does not.

### Seed parity (`StructureRandoms`, `VanillaPlacements`, `NetherStructureSlots`, 1.1.0)

```java
Random r = StructureRandoms.start(seed, chunkX, chunkZ);   // 1.16's setLargeFeatureSeed: every StructureStart's random
VanillaPlacements.V1_16_5.RUINED_PORTAL;                     // 1.16.5's spacing, separation and salt, per structure
VanillaPlacements.V1_16_5.RUINED_PORTAL_NETHER;              // the nether's own ruined portal grid: (25, 10), not (40, 15)

NetherStructureSlots.install(NetherStructureSlots.VANILLA_1_16_5);   // pre-init: bastions take fortress slots
NetherStructureSlots.Slot s = NetherStructureSlots.startAt(world, chunkX, chunkZ); // NONE, FORTRESS or BASTION
NetherStructureSlots.roll(seed, chunkX, chunkZ);             // the same without the biome check: pure arithmetic
```

- 1.16.5's rule uses one grid for both structures (spacing 27, separation 4, salt 30084232). At a region's
  candidate chunk, the same random draws `nextInt(5)`: under 2 is a fortress, 2 or more a bastion. The biome at
  block `(x * 16 + 8, z * 16 + 8)` must then list the structure. Every nether biome lists fortresses; all but the
  basalt deltas list bastions. `install` takes a `BiomeRule` for biomes that answer otherwise.
- `install` swaps the nether's fortress generator, through `InitMapGenEvent` (no patch), for vanilla's, started by
  this rule and laid out with 1.16's start random. A fortress is decided whole when it is laid out, so none
  half-generates beside a bastion. Saved fortresses stay, and `/locate structure minecraft:fortress` and blaze spawns follow the new
  rule. If another mod already replaced the fortress generator, it is left alone (`fortressRuleActive()` is false).
- Your bastion is a `ScatteredStructure` on `VanillaPlacements.V1_16_5.BASTION_REMNANT` whose `canStartAt` is
  `startAt(...) == Slot.BASTION`. `fortressStartsAt` and `savedFortress` read the fortress side.
- None of this loads or generates chunks, so a seed tool can ask about any chunk.

### The overworld's seed maps (`worldgen.StructurePlacements`, 1.1.0)

A world made from seed X with backports installed should show what Chunkbase shows for seed X at the newest
installed backport's version. Each backport registers its version's structure numbers in pre-init, read from
that version's decompiled code; for every structure the newest installed version that names it wins.

```java
StructurePlacements.version("1.16")
        .biomePoint(BiomePoint.QUART_CENTER)                 // where every single-biome start check reads
        .grid(Structures.SHIPWRECK, 24, 4, 165745295)          // spacing, separation, salt (linear)
        .grid(StructurePlacements.OCEAN_MONUMENT, 32, 5, 10387313, true)   // triangular
        .structureBiome(StructurePlacements.JUNGLE_PYRAMID, "bamboo_jungle")  // a biome a structure gains
        .strongholdBiome("minecraft:mushroom_island_shore", true)
        .strongholdBiome("bamboo_jungle", false);            // a bare path: that biome whichever mod adds it

ScatteredPlacement grid = StructurePlacements.placement(Structures.SHIPWRECK, new ScatteredPlacement(16, 8, 165745295));
Biome biome = StructurePlacements.biomeAt(world.getBiomeProvider(), chunkX, chunkZ);   // the version's point
StructurePlacements.isAtLeast("1.16");   // the newest of this table's and the presentation module's versions
```

- Biome points: `LEGACY` (1.12.2, each generator's own), `BLOCK_9_9` (1.13 to 1.15: the block biome at
  block (9, 0, 9)), `QUART_CENTER` (1.16 on: the 1:4 cell at (4x + 2, 4z + 2)). A version installed with no
  point of its own gets vanilla's for its number.
- Resolve the grid when your generator is built for a world (a `ScatteredStructure` factory runs once per
  world), not in a static field: the table is complete only after every mod's pre-init.
- Once the newest installed version is 1.13 or later, Core applies the rules every later version shares, with
  no registration: the strongholds' and spawn point's biome search (patch P8), the villages' biome point (P9),
  the hills layer's river noise seeded with the world seed, 1.12.2's temples on four grids (`ModernTemples`,
  each found by `/locate structure` under its id), the monument's squares from block (9, 9) (`ModernMonument`, only
  where the overworld still has vanilla's generator; in a Realistic Terrain Generation world it also needs deep
  water there, `MonumentTerrain.deepEnough`, which a backport's own monument rule can call too) and the stronghold
  biome rulings. From 1.15, 1.15's
  block-scale biome zoom (`FuzzyZoomLayer`). With Core alone none of it runs.
- Checked with `tools/vanilla-oracle` (vanilla 1.12.2, 1.13.2, 1.14.4, 1.15.2 and 1.16.5 against
  `seedcheck.OverworldDump` on a real server).

## Locate (`locate.StructureLocate`, 1.1.0)

Core replaces 1.12.2's `/locate` with today's: `/locate structure <id|#tag>`, `/locate biome <id|#tag>` and
`/locate poi <id|#tag>`. 1.12.2's names (`/locate Stronghold`) and 1.13-1.18's bare ids are gone (1.3.1), as in
today's game. Each backport registers what it builds, in init, under today's ids:

```java
// A ScatteredStructure kind: the kind's own start rule; the id is decided per start where a kind has several.
StructureLocate.scattered("minecraft:ocean_ruins", Structures.OCEAN_RUIN,
        (world, structure, x, z) -> "minecraft:ocean_ruin_" + structure.variantAt(world, x, z),
        "minecraft:ocean_ruin_cold", "minecraft:ocean_ruin_warm");
StructureLocate.tag("minecraft:ocean_ruin", "minecraft:ocean_ruin_cold", "minecraft:ocean_ruin_warm");

// Any other grid: the world's placement (null where it has none; throw Unavailable to say why).
StructureLocate.placement("minecraft:villages", world -> new StructureLocate.Grid(grid, (x, z) -> idStartingAt(x, z)),
        "minecraft:village_plains", ...);

// Not on a grid, or a fallback where the world's generator is another mod's:
StructureLocate.finder("minecraft:stronghold", (world, from) -> world.findNearestStructure("Stronghold", from, false));
```

The search is today's `ChunkGenerator.findNearestMapStructure`: finders first, then up to 100 rings of regions
around the sender's region, each grid walked x outer, z inner, the first ring with any start ending the search
with the nearest of that ring's first starts. The reply names the start chunk's lowest corner plus the set's
`locate_offset` (`Grid`'s offset arguments; buried treasure's is 9, 0, 9), like `getLocatePos`. A tag search
names what it found ("#minecraft:village (minecraft:village_desert)"). `Placement.startAt` is asked only of
candidate chunks and must not load or generate anything; `ScatteredStructure.locatableAt` gives a kind's answer,
skipping a start already laid out empty.

Tab completion follows today's matching (path, then after a `_`, then namespace) fitted to 1.12.2's client, which
writes the common prefix of everything it gets back: `m` completes to `minecraft:m`, not `minecraft:`; `_` matches only
when their common prefix keeps what was typed; tags only after `#`. Core's client self-test presses Tab through the
real `GuiChat` in a world (`ClientLocateTests`).

`locate.LocateChecks` is for the backports' self-tests: `checkRingOrder` walks the grids region by region to
confirm a result is today's, `savedStart` reads a generator's laid-out start, `generateAround` loads chunks, and
`run`/`complete` run the command or its tab completion as an operator.

From code, for explorer maps (1.4.4):

```java
StructureLocate.Found f = StructureLocate.nearest(world, from, "#minecraft:on_ocean_monument_maps", 50, true);  // f.pos, f.id, f.chunk
StructureLocate.nearest(world, from, idOrTag, rings, skipClaimed, false);   // compare several, then claim the one kept:
StructureClaims.of(world).claim(f.id, f.chunk.x, f.chunk.z);
```

`rings` and `skipClaimed` are the `exploration_map` function's `search_radius` and `skip_existing_chunks`. Claims are
start chunks by id, per dimension (`data/backportcore_structure_claims.dat`); a finder's answer is never skipped or
claimed.

Core registers 1.12.2's structures under today's ids (`minecraft:stronghold`, `monument`, `mansion`, `end_city`,
`mineshaft` through 1.12.2's own search; `minecraft:fortress` on 1.16.5's shared nether grid once a backport
installs `NetherStructureSlots`; `minecraft:desert_pyramid`, `jungle_pyramid`, `swamp_hut`, `igloo` on their
own grids when the world's temples are `ModernTemples`), and `#minecraft:village` falling back to 1.12.2's
village search. Messages use today's keys (`commands.locate.structure.success` / `not_found` / `invalid`,
`chat.coordinates.tooltip`), translated from Mojang's files.

What the family registers:

| Backport | Ids | Tags |
| --- | --- | --- |
| Village & Pillage | `village_plains`, `village_desert`, `village_savanna`, `village_snowy`, `village_taiga`, `pillager_outpost` | `#village` |
| Update Aquatic | `shipwreck`, `shipwreck_beached`, `ocean_ruin_cold`, `ocean_ruin_warm`, `buried_treasure` | `#shipwreck`, `#ocean_ruin` |
| Nether Update | `bastion_remnant`, `nether_fossil`, `ruined_portal`, `ruined_portal_desert`, `_jungle`, `_swamp`, `_mountain`, `_ocean`, `_nether` | `#ruined_portal` |
| Wild Update | `ancient_city` | |
| Trails & Tales | `trail_ruins` | |
| Tricky Trials | `trial_chambers` | |
| Game Drops | `abandoned_camp_<biome>` (one per camp variant) | 26.3's explorer map tags (`#on_abandoned_camp_swamp`, `#on_mineshaft_maps`, ...) |

### Biomes (`locate.BiomeLocate`, 1.3.0)

`/locate biome <id>` and `/locate biome #<tag>`, today's biome form: "The nearest minecraft:cherry_grove is at
[x, y, z] (n blocks away)", the height and the distance in three dimensions, the click putting `/tp @s x y z` in the
chat box; an unknown id or tag gets today's argument messages ("Can't find element 'x' of type
'minecraft:worldgen/biome'"). Completion as the structure form's.

Ids are today's. 1.12.2's biomes answer to what a world upgraded to today makes of them (1.13's renames, then
1.18's merges: `extreme_hills` is `minecraft:windswept_hills`, `desert_hills` is `minecraft:desert`). A biome a mod
registers under one of today's vanilla names, any namespace, is that name in `minecraft:`
(`trailsandtalesbackport:cherry_grove` is `minecraft:cherry_grove`), so the family's biomes need no call. Others
keep their registry name. The backports may add:

```java
BiomeLocate.biome("minecraft:deep_dark", myBiome);      // a biome under a name its registry name does not give
BiomeLocate.tag("minecraft:is_mountain", "minecraft:cherry_grove", "minecraft:windswept_hills");
BiomeLocate.id("minecraft:meadow");                     // a sampler's biome with no 1.12.2 biome of its own
BiomeLocate.source(world -> isMine(world) ? (x, y, z) -> modernIdAt(x, y, z) : null);   // a generator's own biomes
BiomeLocate.Sampler own = BiomeLocate.sampler(world);   // 1.4.5: the world's source, or null where 1.12.2's biomes answer
BiomeLocate.Sampler base = BiomeLocate.layers(world);   // 1.4.5: 1.12.2's column biomes, for a source that answers a few places
```

The search is 26.x's `BiomeSource.findClosestBiome3d(origin, 6400, 32, 64)`: `BlockPos.spiralAround` columns 32
blocks apart (east first, then south), in each the heights `Mth.outFromOrigin(y, minY + 1, maxY + 1, 64)` gives, the
first hit wins (`BiomeLocate.Spiral`, `outFromOrigin`; Core's self-test holds both to 26.3's output). In 1.12.2's
layers the biome is the column's 1:4 cell (today's noise biome at its quart), read 32 x 32 cells at a time, and an
id no registered biome has is not found at once, as `possibleBiomes` does. A world whose generator knows its own
biomes in three dimensions (Caves & Cliffs' 1.18 terrain) answers through `source`.

### Points of interest (`locate.PoiLocate`, 1.4.4)

`/locate poi <id>` and `/locate poi #<tag>`, 26.3's point of interest form: "The nearest minecraft:home is at
[x, ~, z] (n blocks away)", the distance horizontal and the height "~" as the structure form's, the click putting
`/tp @s x ~ z` in the chat box; "Could not find a point of interest of type "x" within a reasonable distance" when
nothing is in range; an unknown id or tag gets today's argument messages (type `minecraft:point_of_interest_type`).
Completion as the other forms'.

All 21 of 26.3's types are known whether or not their block exists (as in today's registry), and 26.3's three tags
(`#minecraft:acquirable_job_site`, `#minecraft:bee_home`, `#minecraft:village`, which holds the first). A type is a
set of block states, as today's `PoiTypes`. Core matches them by registry name, so the family needs no call:

| Type | Blocks | From |
|---|---|---|
| `home` | head half of any `BlockBed` | 1.12.2 |
| `cleric` | `brewing_stand` | 1.12.2 |
| `leatherworker` | `cauldron` (every level), `water_cauldron`, `lava_cauldron`, `powder_snow_cauldron` | 1.12.2, Caves & Cliffs |
| `nether_portal` | `minecraft:portal`, `nether_portal` | 1.12.2 |
| `armorer` `butcher` `cartographer` `farmer` `fisherman` `fletcher` `librarian` `mason` `shepherd` `toolsmith` `weaponsmith` | `blast_furnace` `smoker` `cartography_table` `composter` `barrel` `fletching_table` `lectern` `stonecutter` `loom` `smithing_table` `grindstone` | Village & Pillage (smithing table copies: Nether Update, Trails & Tales) |
| `meeting` | `bell` | Village & Pillage |
| `beehive`, `bee_nest` | `beehive`, `bee_nest` | Buzzy Bees |
| `lodestone` | `lodestone` | Nether Update |
| `lightning_rod` | `lightning_rod` and its 7 weathered and waxed names | Caves & Cliffs (Tricky Trials' copper) |
| `test_instance` | `test_instance_block` | none |

Every state of those blocks counts, under `minecraft:` or a family member's mod id (`settings.Family`); another
mod's block of the same name does not. A backport adds others, or another mod's blocks, with:

```java
PoiLocate.register("minecraft:meeting", myBell);                    // every state of these blocks
PoiLocate.register("mymod:shrine", state -> state.getBlock() == SHRINE && state.getValue(LIT));
PoiLocate.tag("#minecraft:bee_home", "mymod:hollow_log");           // tags hold types and #tags
```

A state two types claim is the first registered's (26.3 refuses such a state). `PoiLocate.typeOf(state)`,
`members(key)`, `ids()`, `tags()` and `nearest(world, origin, key)` are public for other uses.

The search is 26.3's `PoiManager.findClosestWithType(type, origin, 256, ANY)` from the block the source is in: the
chunks within `floorDiv(256, 16) + 1` of the origin's, points within 256 on x and z and within 256 by `distSqr`, the
nearest winning. 1.12.2 keeps no point of interest index, so the blocks are read: a loaded chunk's sections whose
palette holds a wanted state, and an unloaded chunk from its save (the copy waiting to be written first, else the
region file), without loading it. Saved chunks are read once and kept until the chunk loads (dropped at
`ChunkEvent.Load` and world unload). Self-test timings: 35 x 35 loaded chunks about 1 ms, 35 x 35 saved chunks
read the first time about 0.6 s, then about 1 ms. Sections in another format than 1.12.2's (an id-extending mod's
saves) are skipped. Village & Pillage's own `WorkstationIndex` is not asked: it covers loaded chunks and job sites
only, and lags behind the world by design.

### Adventuring Time (`advancement.AdventuringTime`, 1.4.0)

`minecraft:adventure/adventuring_time` follows vanilla's later lists as far as the installed backports bring their
biomes; no backport calls anything. The list is vanilla's at the newest installed version, by today's ids
(`AdventuringTime.listAt`: 1.13's four new oceans and the deep frozen and lukewarm ones, 1.14's bamboo jungle, 1.18's 50,
1.19's deep dark and mangrove swamp, 1.20's cherry grove, 1.21.4's pale garden; Core's self-test holds 1.20.6's to the
file in its jar).

*   1.12.2's terrain: 1.12.2's 36 criteria stay, and each later biome of that list that a backport registers (any
    biome `BiomeLocate.id` names by that id, not a `minecraft:` one) is added: Update Aquatic's oceans, Village &
    Pillage's bamboo jungle, Wild Update's deep dark and mangrove swamp, the cherry grove, the pale garden. Other mods'
    biomes never count, as in vanilla.
*   A world whose overworld is a `terrain.ModernGeneration` (Caves & Cliffs' 1.18 terrain): the whole advancement is the
    list, by today's ids: its generation version's biomes, and each later one a backport registers and the generator
    says it makes (`ModernGeneration.generates(id)`, true unless the generator overrides it; a world made before a
    backport was added has none of its biomes).
*   The added criteria are Core's trigger `backportcore:biome` (`{"biome": "minecraft:cherry_grove"}`, usable in any
    advancement file), met every 20 ticks where the player stands: the generator's 3D biome in a modern world
    (`AdventuringTime.biomeAt`), else the column's 1.12 biome by today's id.
*   Changed after each load of the advancements (the server's start, `/reload`), then the online players' progress is
    read again. `AdventuringTime.extend(server, ids...)` adds criteria by hand (the self-tests).

## Water colors (`water.WaterColors`, 1.4.0)

Today's water color and underwater fog color by biome, for whichever mod draws 1.13's water (Update Aquatic Backport).

```java
WaterColors.register(myBiome, 0x3A7A6A, 0x4D7A60);    // a biome whose colors are not its vanilla name's
int[] c = WaterColors.of(biome);                      // {water, fog}, or null: the drawer's own rule
int[] here = WaterColors.at(world, pos);              // a modern world's 3D biome first
int fog = WaterColors.fog(biome);                     // or the default fog, 0x050533
```

A registration wins; otherwise vanilla's colors for the biome's today's id (1.20.6's `worldgen/biome` files, 1.21.4's
pale garden), so the family's biomes registered under vanilla's names (the cherry grove's `0x5DB7EF`, the mangrove
swamp's fog `0x4D7A60`, the pale garden's `0x556980`) need no call; every vanilla biome without its own colors answers
the defaults; another mod's biome answers null.

`WaterColors.at` reads an underground biome (`UndergroundBiomes`, below) first, since 1.4.4.

## Underground biomes (`worldgen.UndergroundBiomes`, 1.4.4)

A backport's biome under ground, by column and height, in 1.12.2's column biomes: F3's "Biome:" line (at the camera's
block), the grass, foliage and water color blend (patch P44) and `WaterColors.at` read it.

```java
UndergroundBiomes.set(world, x, z, fromY, toYExclusive, myBiome);  // server, the column's chunk loaded; null clears
UndergroundBiomes.setColumn(world, x, z, minY, mask, myBiome);      // every run of a height mask; null mask clears
Biome b = UndergroundBiomes.at(world, pos);                         // either side, or null
Biome here = UndergroundBiomes.biomeAt(world, pos);                 // or world.getBiome(pos)
```

Spans are saved in their chunk (`backportcore_underground`) and sent to each player that sees it (channel
`backportcore_ub`), again when they change. Register from population: a chunk sent before its columns are set gets
them when they are. Game Drops Backport's sulfur caves are the user.

## Writes past the population window (`worldgen.DeferredPlacement`, 1.4.4)

A feature from 1.13 on may write anywhere in the 3 by 3 chunks around its own; 1.12 guarantees the chunk and its
neighbors to +x and +z. Work that reaches further is queued and runs once it can:

```java
DeferredPlacement.register(new ResourceLocation("mymod", "far_spike"), (world, min, max, data) -> { ... });
DeferredPlacement.queue(world, id, min, max, data);  // the block box it reads and writes, and its own NBT
int n = DeferredPlacement.pending(world);
```

It runs once, at the end of the first server tick at which every chunk the box touches is loaded and it and its
neighbors to -x, -z and -x -z have populated (nothing later writes there then). It never loads or generates a chunk.
Waiting entries are saved per dimension (`data/backportcore_deferred.dat`); a handler that is gone keeps its entries.

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

## Look and feel (`presentation.Presentation`, 1.1.0)

Each backport declares the releases it brings; Core shows what the newest installed version would: panorama,
world-loading screen, options on the right screens, splashes, menu music, F3 keys and text, fog, wording, sprites,
and changed defaults applied once.

Since 1.4.7 Core owns every version's pieces that are not tied to one backport's content (options, chat
suggestions, F3 keys and text, the F3 + F4 switcher, loading screens, title and startup logos, widgets, wording,
splashes, defaults) and applies them up to the newest installed version, whichever backport that is
(`Presentation.core(version)`, `presentation/versions/LookNNN`). Caves & Cliffs alone has 1.13's command
suggestions and 1.16's F3 + F4. A backport registers only what its content brings: the version declarations, its
panorama and overlay, its music, and options or steps of its own content.

```java
Contribution v119 = Presentation.version("1.19");      // pre-init, both sides; 1.13 or later
v119.panorama("wildupdatebackport:textures/gui/title/background/panorama_")
        .panoramaOverlay(new ResourceLocation("wildupdatebackport", "textures/gui/title/background/panorama_overlay.png"))
        .addMenuMusic("wildupdatebackport:music/game/swamp/aerie", 1)
        .option(DARKNESS.after("netherupdatebackport:fov_effect_scale"));   // the Darkness effect is Wild Update's
Presentation.version("1.19.4");                         // a point release: its pieces are Core's
```

- What moved, per backport, is in `docs/CORE_FEEL.md`. Core's options keep the backports' ids
  (`updateaquaticbackport:auto_suggestions`), so saved values carry over; refer to one by id
  (`Presentation.option(id)`) or by its `presentation.versions.LookNNN` constant.
- At one version a backport's contribution comes after Core's, so an older backport jar that still registers its
  copy resolves to the same piece. An option under the id of one Core owns (`PresentationOption.ownedByCore()`)
  becomes its mirror (`canonical()`: one button, one value); an F3 text, fog or music step of the same version and
  name is left out (`OrderedSteps`); Core leaves its own event handler or patch off while the older jar's class is on
  the class path (`LegacyCopies.coreRuns(className...)`).
- `Presentation.coreContribution(version)` is Core's own contribution of exactly that version (null if none);
  `Contribution.isCore()` tells them apart. Backports never call `Presentation.core`.

Everything, with how each piece resolves, is in `docs/PRESENTATION.md`; what to check in game is in
`docs/PRESENTATION_CHECKLIST.md`. Patches P6 (title panorama) and P7 (F3 keys) are in `docs/PATCHES.md`.

### Screens for a backport's own menus

- `presentation.client.HeaderFooterLayout` (1.4.5): 1.20.5's `HeaderAndFooterLayout`, the title in a 33-high header, Done or a
  row of buttons in a 33-high footer, the contents between.
- `presentation.client.MenuBackground.contentFrame(left, top, right, bottom, scrolled)` (1.4.4): the list background and the
  header and footer separators while the newer menu backgrounds are on; false: keep 1.12.2's look.
  `MenuBackground.menuTile(x, y, width, height)` (1.4.7): 1.20.5's `menu_background.png` tile over a rectangle, for
  a screen drawn outside the GUI loop.
- `gamerules.client.GameRulesScreen.inWorld(parent)` (1.4.4): 26.3's in-world Game Rules screen (the server's rules,
  sent on Done); show it only when `available()` and `permitted(mc)` (permission level 2).
- `PresentationOption.activeWhen(supplier, inactiveTooltip)` (1.4.5): shown greyed out while false;
  `availableWhen(supplier)` (1.4.4): left off where the platform lacks it. `ToggleOption.valueTooltips(onKey, offKey)` and
  `defaultFollows(supplier)` (1.4.4).

### System toasts (`presentation.client.SystemToasts`, 1.4.5)

The family's one system toast drawer (26.3's `SystemToast` in `ToastManager`'s slots, each version's look). Client
thread only.

```java
SystemToasts.addOrUpdate(SystemToasts.Id.LOW_DISK_SPACE, I18n.format("chunk.toast.lowDiskSpace"),
        I18n.format("chunk.toast.lowDiskSpace.description"));     // or add(...) for a new toast each time
SystemToasts.Toast t = SystemToasts.get(SystemToasts.Id.LOW_DISK_SPACE); // title(), message(), width(), height()
```

`Id` holds 26.3's ids with their display times (`new Id("mymod:thing", 8000L)` for one of your own); `forceHide(id)`
slides one out, `clear()` drops all. `notificationTime` is the multiplier a Notification Time option sets. A mod that
must also run on Core 1.4.4 can reach these by reflection, as Trails & Tales' `SystemToast1205` does.

### Debug parts (`presentation.client.DebugParts`, 1.4.5)

`DebugParts.setMood(() -> mood)`: a backport with 1.16's cave mood hands its value (0 to 1) to the `sound_mood` line.

## Creative tabs (`creative.CreativePlacement`, 1.1.0)

A backport has no creative tab of its own. Each block or item goes in the vanilla tab it has in 1.19.2 (the
last version with 1.12's tab set), next to its vanilla neighbor. Call it where the block or item is
registered, on both sides:

```java
CreativePlacement.place(block);                  // <modid>:stripped_oak_log -> minecraft:stripped_oak_log's tab and place
CreativePlacement.place(item);                   // the same for an item
CreativePlacement.place(block, CreativeTabs.DECORATIONS);              // a tab named (an item newer than the table's tab)
CreativePlacement.place(item, CreativeTabs.MISC, "minecraft:x");      // as a vanilla item of another name
CreativePlacement.variant(item, 3, "minecraft:granite_wall");          // one metadata of a placed item
CreativePlacement.placeAfter(item, 0, CreativeTabs.MISC, "minecraft:x"); // an item vanilla does not have
CreativePlacement.placeSpawnEggs(MOD_ID);         // every egg of this mod's mobs, where vanilla lists that mob's
CreativePlacement.addEnchantmentType(CreativeTabs.COMBAT, type);        // books of a new type, keeping the tab's own
```

*   **Tab:** `place(block)` takes the tab from `assets/backportcore/creative/vanilla_order.txt`. A vanilla item that is
    in no tab (the debug stick, suspicious stew) stays out of the creative menu, as in vanilla, with an info line.
*   **Place:** when a tab is listed, each placed stack goes after the nearest stack before it in vanilla's order that
    the tab really shows (vanilla's, or another backport's), or before the nearest one after it: the one of the same
    kind (the longer shared name ending), then the nearer, wins. So placements follow whichever backports are
    installed: stripped logs after the dark oak log alone, after the warped stem once the Nether Update is there.
    Other mods' items are never moved and are never anchors.
*   **How:** on the client, Core replaces each vanilla tab that has a placement in `CreativeTabs.CREATIVE_TAB_ARRAY`,
    at its own index, with a `creative.client.VanillaTab`: it passes every question (icon, label, background,
    search bar, enchantment types) to the original, and lists the original's items before moving the placed
    stacks. Items keep the original tab object, so `Item.isInCreativeTab`, `getSubItems`, the search tab, JEI and
    every mod that asks an item for its tab see vanilla's. No patch. If another mod has already replaced a slot,
    Core leaves it alone (a warning in the log).
*   **Data:** `vanilla_order.txt` is 1.19.2's item registry order (which up to 1.19.2 is every tab's order) with each
    tab, and every later item where today's game puts it; `legacy_items.txt` turns a 1.12.2 stack (`minecraft:stone`
    at 1) into today's id (`minecraft:granite`). Both come from Mojang's own code via `tools/creative/build_tables.py`;
    the rules for the later items are in `docs/CREATIVE_TABS.md`.
*   **Dev check:** a client started with `-Dbackportcore.creativeDump=<file>` writes every vanilla tab's list at the
    first title screen, to the file and the log: each placed stack marked with the stack before it, placements that
    show nowhere, vanilla stacks the table cannot place, and whether every placed item lists itself for the search
    tab, for its own tab object and (if installed) in JEI. `-Dbackportcore.creativeDump.exit=true` then closes the
    game. With Gradle, an init script can set both on `runClient` (see `docs/CREATIVE_TABS.md`).

## Game events and vibrations (`gameevent.*`, 1.4.0)

One dispatcher per world for every backport, so one mod's sensors hear another mod's mobs and blocks. Moved from
Wild Update Backport (1.19.2's rules, unchanged) with 1.20.6's rules added.

```java
GameEvents.require("1.20", () -> ModConfig.x.enabled);   // construction or pre-init; nothing runs until a switch is on
GameEvents.emit(world, GameEvent.BLOCK_CHANGE, pos, GameEventContext.of(player, state));   // server only
GameEvents.add(world, listener); GameEvents.remove(world, listener); GameEvents.move(world, listener);
GameEvents.isAtLeast("1.20"); GameEvents.newest();        // from the requirements whose switch is on
```

- `GameEvent`: every 1.19.2 and 1.20.6 event, radius (16; shriek 32; jukebox 10) and current Java's frequency
  (0 = not a vibration). 1.19.2's renamed events keep the frequency of what they became (`piston_extend` 10,
  `piston_contract` 9, `entity_roar`/`entity_shake` 4). `resonance(f)` is `resonate_f`. `register` adds a mod's own.
- `GameEventTags`: `vibrations()` and `wardenCanListen()` give 1.20.6's sets while a "1.20" requirement is on, else
  1.19.2's (`VIBRATIONS_1_19`, `VIBRATIONS_1_20`, ...). 1.20's sets also hold the renamed 1.19.2 events. Wool
  occludes; wool and carpets dampen (`addDampener(block)`, 1.4.4); `addResonator(block)` / `resonates(state)` is
  `#vibration_resonators`. `GameEvent.BOUNCE` (1.4.4, frequency 2) is in 1.20's vibration and warden lists.
- `GameEventListener.rules()`: `V1_20` (default: radius between blocks, heard at once, closest first) or `V1_19`
  (exact radius, heard at the end of the world tick, closest first). `immediate()` hears before either; `active()`
  false is deaf (a mod's own switch while another mod keeps events on).
- `VibrationSystem` (1.20.6): `User` (radius, position, `canReceiveVibration`, `onReceiveVibration`, listenable set,
  avoid-vibration, adjacent chunks), `Data` (in flight, selector; `write()`/`read()` as 1.20's `listener` tag),
  `Listener` (register it; `forceSchedule` for a stepped-on sensor), `tick(world, data, user)` from the owner's tick.
  Closest candidate of a tick wins, ties to the higher frequency; taken the next tick; `floor(distance)` ticks of
  travel; `redstoneStrengthForDistance`.
- `VibrationListener` (1.19.2, Wild Update Backport's): `Config` with `shouldListen` / `onSignalReceive`.
- `SensorActivation`: sensors call `activated(source, world, pos, frequency)` where 1.20 resonates; a backport adds a
  `Handler` (Trails & Tales Backport's amethyst resonance reaches every mod's sensors).
- `GameEventEmitters`: vanilla's events from Forge's hooks (steps, swims, flaps, splashes, falls, block place and
  break, a player's clicks, containers, eating, projectiles, damage, death, explosions, note blocks, pistons,
  lightning...). With 1.20 on: mount and dismount, 1.20's equip/unequip, a wolf shaking dry. `addEntityPlacer`:
  a mod's boats.
- `AvoidVibrationTrigger` (`backportcore:avoid_vibration`, Sneak 100) and the vibration particle
  (`GameEventNetwork`, channel `backportcore_ge`) are registered by Core.

## Recipe unlocking (`recipe.RecipeUnlocks`, 1.2.0)

Nothing to call. Every crafting recipe registered under the id of a mod that requires `backportcore` unlocks for a
player the way vanilla unlocks the same recipe, through 1.12.2's own recipe book (toast included). Core checks each
player's inventory once a second, when the items in it have changed.

- The rule for a recipe comes from `assets/backportcore/recipes/unlocks.txt` (current Java's recipe advancements;
  rebuild with `python -I tools/recipes/build_unlocks.py <client.jar> <out file>`). The recipe is matched by its
  **result's** id as today's game names it, so name items as vanilla does (`modid:smithing_table`).
- Of vanilla's trigger items, those that are ingredients of the recipe count (and the result itself, where
  vanilla lists it). With none, any one ingredient unlocks it. Dynamic recipes are left alone.
- Items compare by today's id: vanilla's through `VanillaOrder.modernId`, a family mod's by its registry path. A
  tag also takes the ore name 1.12.2 mods use for it (`#planks` is `plankWood`).
- `RecipeUnlocks.addMod(modId)`: include a mod that does not require Core. `RecipeUnlocks.triggersOf(recipeId)`:
  what unlocks a recipe (for tests). Setting: `recipes.unlockRecipes`.

## Smelting books (`recipe.book.*`, 1.3.0)

1.13's furnace recipe book, and the same book for any other cooking block (a smoker, a blast furnace). Core registers
vanilla's furnace book itself (`SmeltingBook.furnace()`) once an installed backport is 1.13 or later (1.4.7; Update
Aquatic's until then). Register another book on both sides from pre-init or init:

```java
SmeltingBooks.register(new SmeltingBook(new ResourceLocation("modid", "smoker"),
        c -> c.getClass() == ContainerSmoker.class, 0, 1, 2,              // input, fuel, output slots
        () -> SmokerRecipes.instance().getSmeltingList(), TileEntityFurnace::isItemFuel,
        "gui.recipebook.toggleRecipes.smokable", new ItemStack(smoker),
        new SmeltingBook.Tab(s -> true, new ItemStack(Items.PORKCHOP))));
```

- Recipes: one per result, from every input of the recipe map (1.13's `iron_nugget_from_smelting` takes every iron
  tool), in item registry order; the first tab whose test takes the result lists it. Ids name the result
  (`minecraft:dye@2`, `minecraft:coal*2`), so both sides find the same recipe.
- Unlocking: 1.13's rule, a recipe unlocks once the player holds any of its inputs, checked with `RecipeUnlocks`
  (not switched off by `recipes.unlockRecipes`). The client gets 1.13's toast with the book's `toastIcon` (1.14's
  `Recipe.getToastSymbol()`: the furnace, smoker, blast furnace or campfire) at 0.6 scale behind the output, from the
  toasts' 1.13 look (`VanillaRevisions.recipeToastIcon`). Recipes from every book share one toast.
- A block without a screen (1.14's campfire): a book whose container test is never true and that has no tabs
  (`c -> false`, `s -> false` for fuel, `""` for the filter). Its recipes unlock and toast like any book's.
- State: open, filter and unlocked recipes per player under `PlayerPersisted.backportcore:recipe_books.<id>`
  (`SmeltingBooks.data(player, book)`), kept through death; sent at login.
- Placing: `SmeltingPlacer`, 1.13.2's `ServerRecipePlacerFurnace` (one more per click, shift for all, another recipe's
  input and output back to the inventory, the ghost without ingredients). It refuses a click whose output would not
  fit back in the inventory, where 1.13 deletes it. `SmeltingBooks.place(player, book, recipe, placeAll)` from code.
- Screen: draw the book with `client.SmeltingBookGui` as 1.12.2's `GuiCrafting` draws its book (see its javadoc) and
  implement `client.BookScreen`. `client.GuiFurnaceBook` is vanilla's furnace, opened in place of `GuiFurnace` once
  the furnace book is registered. 1.13.2's `recipe_book.png` (furnace filter at 152, 182) and `recipe_button.png`
  are Core's 1.13 look (`Contribution.replaceAsset`; a backport's before 1.4.7).
- Packets: `BookNetwork` (`backportcore_rb`), only to clients running Core.

## Babies: the age lock and 26.1's baby models (`mechanics.AgeLockable`, `VanillaRevisions.babyModels`, 1.4.4)

26.1's golden dandelion (Game Drops Backport) locks any `EntityAgeable` baby. A backport's baby that is not one, or
whose babies start at another age than -24000, implements `AgeLockable`:

```java
public class EntityTadpole extends EntityCreature implements AgeLockable {
    public boolean isBaby() { return true; }            // a baby the lock can hold
    public int getAge() { return this.age; }            // in its own terms (counting up here)
    public void setAge(int age) { ... }                 // the owner grows it up
    public int startAge() { return 0; }                 // where the lock puts it back (a snifflet: -48000)
}
if (food && !AgeLockable.isLocked(this)) { ... }        // refuse growth food while locked (either side)
```

The lock is `AgeLocked` (`AgeLockable.LOCKED`) in the mob's Forge data; the mod that has the lock toggles it, holds
the age and tells clients. An `EntityAgeable` that implements it must agree with `getGrowingAge`/`setGrowingAge`.

26.1 also redrew the babies with their own models. A backport draws its own mobs' babies 26.1's way while
`VanillaRevisions.babyModels()` (read each frame): 26.1 or later installed, Core's textures switch on, and the switch
the 26.1 mod hands over with `VanillaRevisions.setBabyModelSwitch(BooleanSupplier)` (Game Drops' `babies.babyModels`).

## Boats (`boat.*`, 1.4.4)

One boat and one boat with chest for the whole family (Wild Update's request 4, Tricky Trials' #9, Game Drops' 5):
`backportcore:boat` (`EntityWoodBoat`, id 0) and `backportcore:chest_boat` (`EntityWoodChestBoat`, id 1), drawn by
`client.RenderWoodBoat` (1.20's boat, chest boat, raft and chest raft models).

- Woods: `BoatWood.register(new ResourceLocation(modid, "cherry"), () -> planks, raft)` while registering items, then
  `setTextures(boat, chestBoat)` (128 x 64 and 128 x 128, 1.20's layout) and `setBoatItem` / `setChestBoatItem`.
  Vanilla's six (`minecraft:oak` ... `minecraft:dark_oak`, `BoatWood.of(type)`) are Core's, with vanilla's boat items;
  Wild Update gives them chest boats. `BoatWood.get(id)` falls back to oak; `find(id)` returns null. A bare name
  (`"cherry"`) is the one registered wood with that name.
- Items: `new ItemWoodBoat(wood, chest)` under the owner's own name: vanilla's placement, stacks to 1, burns 1200.
  Using one is `entity_place` (Core adds the placer).
- Entities: the wood is synced and saved as `Wood` (its id, kept as read when its mod is gone: an oak boat until it is
  back). A raft's rider sits at 0.25. A crash drops the wood's planks. The chest boat is 1.19's: 27 slots, one seat
  0.15 forward, opened sneaking or when the seat is taken, contents dropped whenever it is removed for good (not on
  a dimension change), and an `IInventory` for hoppers.
- Old saves: `Boats.renamed(oldEntityName, chest)` in pre-init. Core's data fixer (data version 1) renames the entity
  as it is read, wherever it was saved: chunks, passengers, spawners, a player's `RootVehicle`; the tags (wood, chest,
  riders) are the same. Core also remaps the name in `MissingMappings` (no missing-entries prompt, and an alias for
  that session; Forge 1.12.2 does not keep aliases, its bug 4894). A world with several old names logs Forge's
  "registered twice" warning once, the first time it loads.

## Jukebox signal (`mechanics.JukeboxSignal`, 1.4.4)

Patch P41: a disc's comparator signal from its own `public int comparatorSignal()` (0 to 15) on the `ItemRecord`
subclass, found by name; no Core type to implement. Other discs keep 1.12.2's answer.
`JukeboxSignal.signal(world, pos)` and `signal(stack)` read it (-1: none).

## Helpers

`util.BlockStates.props/with/withOrSame` - states by property name, as blockstate and structure files write them.

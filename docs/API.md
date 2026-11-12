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
dependencies = "required-after:backportcore@[1.1.0,)"
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
blue ice) are in the table already. Sea pickles are Update Aquatic's: when two backports need a block, it
belongs to the one it fits best, and Village & Pillage uses Update Aquatic's pickles when it is installed. Ask from your block registry event or later. Not done yet:
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
the previous layout's cache. `WorldHeights.minY/maxYExclusive/enterFullHeight` cover the 1.18-height mods,
Caves & Cliffs Backport's extended height among them (1.2.0; `isCavesAndCliffsExtended(world)`).

A chunk generator that knows its own surface implements `TerrainSource` (1.2.0): `forWorld` asks its
`columnTerrain(world)` before any other adapter.
`FlatTerrain.structures(generator)` gives a superflat world's structure map for swapping a generator.

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

## Waterlogging (`waterlog.Waterloggable`, `waterlog.Waterlogging`)

1.13's waterlogging for the family's blocks, through Fluidlogged API when it is installed. A block that
today's game makes waterloggable implements `Waterloggable` (a plain interface) and answers up to three
questions. With Fluidlogged API installed, Core's patch P19 adds Fluidlogged API's `IFluidloggable`
(answered from those three) to the block's class; the block never implements `IFluidloggable` itself and has
no `waterlogged` property.

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
ModernPalette.entity("minecraft:piglin", new ResourceLocation("yourmod", "piglin"));   // 1.1.0: entities of the files
ModernPalette.Resolved r = ModernPalette.resolve("minecraft:oak_stairs[facing=east]");  // r.state, r.tile, r.water
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
  `JigsawPools.feature("minecraft:oak", placer)`. Projections are `rigid` and `terrain_matching`. `processors` may
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
  half-generates beside a bastion. Saved fortresses stay, and `/locate Fortress` and blaze spawns follow the new
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
  with `/locate Temple` finding any of them), the monument's squares from block (9, 9) (`ModernMonument`, only
  where the overworld still has vanilla's generator) and the stronghold biome rulings. From 1.15, 1.15's
  block-scale biome zoom (`FuzzyZoomLayer`). With Core alone none of it runs.
- Checked with `tools/vanilla-oracle` (vanilla 1.12.2, 1.13.2, 1.14.4, 1.15.2 and 1.16.5 against
  `seedcheck.OverworldDump` on a real server).

## Locate (`locate.StructureLocate`, 1.1.0)

Core replaces 1.12.2's `/locate` with today's: `/locate structure <id>` or `/locate structure #<tag>`, plus
1.12.2's names (`Stronghold`, `Monument`, `Mansion`, `EndCity`, `Fortress`, `Temple`, `Mineshaft`; `Village`
only until a backport registers today's village types, after which it still works but is not listed) and
1.13-1.18's bare ids. Each backport registers what it builds, in init, under today's ids:

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
| Wild Update | not yet: `ancient_city` (not built) | |
| Trails & Tales | not yet: `trail_ruins` (not built) | |
| Tricky Trials | not yet: `trial_chambers` (not built) | |

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

Each backport declares the release it brings and adds its version's non-content changes; Core shows what the
newest installed version would: panorama, world-loading screen, options on the right screens (and 1.14's
Accessibility and Mouse Settings), splashes, menu music, F3 keys, fog, and changed defaults applied once.

```java
Presentation.version("1.14")
        .panorama("villagerbackport:textures/gui/title/background/panorama_")
        .addSplashes(new ResourceLocation("villagerbackport", "texts/splashes_added.txt"))
        .option(new SliderOption("villagerbackport:text_background_opacity", "options.accessibility.text_background_opacity",
                OptionScreen.ACCESSIBILITY, 0, 1, 0.01, 0.5).after("SHOW_SUBTITLES"));
```

Everything, with how each piece resolves, is in `docs/PRESENTATION.md`; what to check in game is in
`docs/PRESENTATION_CHECKLIST.md`. Patches P6 (title panorama) and P7 (F3 keys) are in `docs/PATCHES.md`.

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

## Helpers

`util.BlockStates.props/with/withOrSame` - states by property name, as blockstate and structure files write them.

# Backport Core API

For the backports that depend on this library. Everything is plain static Java; no events to post, no
interfaces to register through Forge. Package root: `com.exiledradio.backportcore`.

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
dependencies = "required-after:backportcore@[1.0.0,)"
```

and `"requiredMods": ["backportcore"]` in `mcmod.info`.

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

Handles vanilla (and subclasses that keep vanilla noise), superflat, OTG, Biomes O' Plenty, RTG, Caves Not
Cliffs and Farmer's Future Delight. Call `forWorld` once per structure start on the server thread: it drops
the previous layout's cache. `WorldHeights.minY/maxYExclusive/enterFullHeight` cover the 1.18-height mods.
`FlatTerrain.structures(generator)` gives a superflat world's structure map for swapping a generator.

## Helpers

`util.BlockStates.props/with/withOrSame` - states by property name, as blockstate and structure files write them.

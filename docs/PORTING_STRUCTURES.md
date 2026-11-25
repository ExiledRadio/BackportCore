# Porting modern structure mods to 1.12.2

Backport Core 1.4.5 generates jigsaw structures from today's data files: structure sets, structures, template
pools, processor lists, `.nbt` templates and biome tags. A structure mod built on jigsaw structures (most structure
mods since 1.16) ports to 1.12.2 mostly by copying its `data/` folder. This guide covers how to do it, how to check
the result, and what still needs code.

The reference for every class named here is [API.md](API.md).

## 1. What Core reads

| File | Path | Notes |
|---|---|---|
| Structure sets | `data/<ns>/worldgen/structure_set/*.json` | `random_spread` only |
| Structures | `data/<ns>/worldgen/structure/*.json` | `minecraft:jigsaw` only |
| Template pools | `data/<ns>/worldgen/template_pool/**.json` | every element type |
| Processor lists | `data/<ns>/worldgen/processor_list/*.json` | 1.16's processors (see API.md) |
| Templates | `data/<ns>/structure/**.nbt` or `structures/` | any format since 1.13, 26.x's too |
| Biome tags | `data/<ns>/tags/worldgen/biome/**.json` | merged with Core's copy of 26.3's tags |
| Structure tags | `data/<ns>/tags/worldgen/structure/**.json` | for `/locate structure #tag` |

Files are unchanged from the modern mod. Block names are read as today's, and blocks renamed since a file was saved
are renamed first.

## 2. Two ways to ship

### A structure pack (no code)

Put the `data/` folder in a folder, `.zip` or `.jar` in `config/backportcore/structures/`, laid out like a datapack:
`data/` at its root, or inside one top folder. A pack's file replaces a mod jar's file with the same path, so packs
also work for changing another mod's structures.

```
config/backportcore/structures/
  mystructures.zip
    data/mystructures/worldgen/structure_set/towers.json
    data/mystructures/worldgen/structure/tower.json
    data/mystructures/worldgen/template_pool/tower/start.json
    data/mystructures/structure/tower/base.nbt
```

### A 1.12.2 mod

Use this to publish on CurseForge or to add code for the gaps in section 5. Put `data/` in `src/main/resources/`,
next to `assets/`. The mod needs Core and nothing else:

```java
@Mod(modid = "mystructures", name = "My Structures", version = "1.0.0",
        dependencies = "required-after:backportcore@[1.4.5,)")
public class MyStructures {
}
```

Core reads every mod jar's `data/` folder at load complete, after every backport registered its own structures.

## 3. Check the port

1. **Start the game or server and read the log.**
   - `Data-driven structure set <id>: [...] in dimensions [...]`: one line per loaded set.
   - `Data-driven structures: N sets (M structures) loaded, K skipped.`
   - `Data-driven structures: skipped <why>`: one line per skipped file (section 6).
   - `Structure <id>: start pool <pool> is missing or empty.`: a pool path is wrong, or its files are not in the jar.
2. **Find one:** `/locate structure mystructures:tower`, then teleport there.
3. **Look for stand-ins:** a block 1.12.2 and the installed mods don't have is placed as a stand-in. Fix it with
   `ModernPalette` (section 5).
4. **Read the block report** (Core 1.5.0): `config/backportcore/modern_blocks_report.txt` lists every block name the
   structure files use, how many blocks, and what this game places for it. `missing` names are left out.

### Supplied blocks (Core 1.5.0)

Core supplies decorative copies of modern blocks the structures use when no installed mod has them: about 440
blocks from 1.13 to 26.3, with today's models, textures and sounds (deepslate, tuff and blackstone sets, copper,
mud bricks, new woods with their doors, fences and trapdoors, walls, lanterns, chains, leaves, flowers and roots;
`assets/backportcore/supplied/blocks.txt`). Only the blocks your files name are registered. Blocks with a tile
entity (chests, signs, decorated pots, suspicious sand), torches and multi-block plants are not supplied:
`tools/supplied-blocks/skipped.txt` has the list.

- They look and break like vanilla's and do nothing else. With the backport that owns a block installed, structures
  get its real block instead, with its behavior (Caves & Cliffs' copper oxidizes).
- Blocks Core supplied once stay registered (`config/backportcore/supplied_blocks.txt`), so removing a pack does
  not remove them from worlds.
- Installing the owning backport later turns Core's copies in a world into its blocks; removing it turns them back.

Seeds match: starts land where 26.3 puts them for the same seed, salt and spacing, so a modern seed map's numbers for
the set can be compared directly.

## 4. Settings

In `config/backportcore.cfg` (or Mod Options › Backport Core); both apply on the next start and only to new chunks.

- `structure.dataStructures` (on by default): turns all data-driven structures on or off.
- `structure.blacklist`: structure ids, set ids, or `namespace:*`, which are not generated.

A world keeps the structures it already started (saved as `BCData`, pieces as `BCDataPiece`) after their files are
gone; no new ones start.

## 5. What still needs code

Call these in pre-init or init of a 1.12.2 mod (section 2).

**Blocks 1.12.2 doesn't have.** Point a block name at a 1.12.2 block or another mod's block:

```java
ModernPalette.map("minecraft:mud_bricks", props -> myMudBricks.getDefaultState());
ModernPalette.fallback("minecraft:tuff_bricks", "minecraft:stone_bricks");
ModernPalette.source("othermod");   // othermod:kelp serves minecraft:kelp
```

The backports already provide many modern blocks (Update Aquatic's coral and kelp, Village & Pillage's lanterns and
barrels, and so on). With them installed, their blocks are used. Core supplies some decorative ones itself
(section 3); a mapping wins over them.

**Loot tables and entities** in templates need 1.12.2 counterparts:

```java
ModernPalette.lootTable("mystructures:chests/tower", new ResourceLocation("mystructures", "chests/tower"));
ModernPalette.entity("minecraft:pillager", new ResourceLocation("villagerbackport", "pillager"));
```

**Feature pool elements** (`feature_pool_element`: trees, patches of plants) need a placer, because 1.12.2 has no
configured features. Without one, the element places nothing:

```java
JigsawPools.feature("minecraft:oak", (world, pos, random, feature) -> new WorldGenTrees(false).generate(world, random, pos));
```

**Modded biomes:** 1.12.2 biomes use their today's names (`minecraft:windswept_hills` for Extreme Hills), and modded
biomes match by registry name. A mod biome whose 1.12.2 id differs from the modern id the structure files use needs
`BiomeLocate.biome`.

## 6. Not supported

These are skipped, with one log line each:
- **`concentric_rings` sets:** only strongholds use them in vanilla.
- **Non-jigsaw structure types:** vanilla's hardcoded structures (desert pyramids, mansions and the rest).
- **Already generated:** structure ids that a backport or 1.12.2 already generates, so nothing doubles up.
- **Inline `exclusion_zone` sets:** an `exclusion_zone` must name a set id.

`step` is not used: every structure builds from Core's populate step. A structure must not reach more than 8 chunks
from its start, which `max_distance_from_center`'s limit of 128 already ensures.

## 7. Build and test in game

Core has today's jigsaw block, `backportcore:jigsaw` (Core 1.4.5), for building and testing jigsaw structures in a
1.12.2 world:

- Get it with `/give @p backportcore:jigsaw`, or from Operator Utilities with 1.19.3's creative tabs (a backport of
  1.19.3 or later, Operator Items Tab on). Placing, opening or breaking it takes creative mode and operator permission
  (level 2), as in 26.3. A template's `minecraft:jigsaw` is this block.
- Its screen is 26.3's: Target Pool, Name, Target Name, Turns Into, both priorities, Joint Type, Levels and Keep
  Jigsaws.
- **Generate** builds the pool in front of the block as 26.3 does. Pools and templates come from the mods, then from
  the world: `<world>/structures/`, where 1.12.2's structure block saves, and folder data packs in
  `<world>/datapacks/<pack>/data/`.
- 1.12.2's structure block saves jigsaw blocks with their data, and Core reads them back as jigsaws. Files saved in
  game use 1.12.2's block names: Core reads them, but today's game doesn't.

## 8. Mixing with 1.12.2 structures

Data-driven structures are ordinary 1.12.2 `StructureStart`s and `StructureComponent`s, saved with the world.
Structures from mods that use 1.12.2's own structure generation are unaffected, and one mod can use both. For
structures written in code, see API.md: `Jigsaw` (1.16.5's rules), `ModernJigsaw` (1.20 to 26.x) and
`ScatteredStructure`.

# Plan: the family's biomes in Realistic Terrain Generation worlds

Status: **built 2026-11-23**, not committed. Core branch `core-rtg`: `terrain/RtgBiomes`,
`terrain/rtg/RtgRealisticBiomes`, and `StructurePlacements.biomeAt(World, ...)`, which Core's temples use. Each
backport has an `rtg-biomes` branch with one registration call, reached by name so it still loads with an older
Core. Update Aquatic keeps its own `compat/RtgCompat` + `RtgOceans` (1.2.2): it compiles against Core 1.3.0, so it
does not use Core's adapter.

Test: RTG Unofficial 1.1.0 on seed 1144, radius 96 (37,000 chunks), with every family mod installed. The
released jars produced none of the family biomes, and 17 shipwrecks and 19 ocean ruins in plains. The new jars
produced:

| Biome | Columns |
|---|---|
| UA oceans | all seven |
| dappled forest | 254,581 |
| pale garden | 64,012 |
| cherry grove | 12,912 |

The new jars also put no shipwreck or ruin starts in plains. That area has no jungle or desert climate, so bamboo
jungle and mangrove swamp were checked at `/locate`d spots (see below).

Templates chosen by the family's vanilla-first rule:

| Backport | Biome | Template | Why |
|---|---|---|---|
| Village & Pillage | bamboo jungle | `JUNGLE` | |
| Village & Pillage | bamboo jungle hills | `JUNGLE_HILLS` | |
| Trails & Tales | cherry grove | `FOREST_HILLS` | today's cherry groves lie on slopes |
| Wild Update | mangrove swamp | `SWAMP` | |
| Tricky Trials | pale garden | `ROOFED_FOREST` | |
| Game Drops | dappled forest | `FOREST` | |

## Why our biomes vanish under RTG

RTG (6.1 and the Unofficial fork RTGU 1.1.0, both modid `rtg`) builds every column from a *realistic biome*
registered for that column's `Biome`. `RTGAPI.getRTGBiome(biome)` falls back to RTG's **patch biome** (plains by
default) when nobody registered one. The patch is used for terrain, surface and the chunk's saved biome id. So
our biomes reach the biome map (RTG fires `WorldTypeEvent.InitBiomeGens` like vanilla) and then come out as
plains.

RTG registers realistic biomes for vanilla and about 30 named mods (`rtg.init.BiomeInit`). None of ours is
on that list.

## The API (checked against both jars)

- Realistic biome: `rtg.api.world.biome.RealisticBiomeBase(Biome)`. Override `initConfig`, `initTerrain`,
  `initSurface` and `initDecos`. The super constructor calls them in that order, after `baseBiome()` and
  `getConfig()` are set, so subclass fields are not available yet. Derive everything from `baseBiome()`.
- Terrain and surface: RTG's vanilla classes expose theirs as public nested classes. For example,
  `RealisticBiomeVanillaOcean.TerrainVanillaOcean()` and `SurfaceVanillaOcean(config, top, filler, mix, mixWidth,
  mixHeight)`. Reusing them gives RTG-consistent terrain without writing any noise.
- Registration: **differs between the two.**
  - RTGU: `RTGAPI.addRTGBiomes(IRealisticBiome...)`.
  - RTG 6.1: `RTGAPI.RTG_BIOMES.addBiomes(...)`, a `BiomeMap`.
  - Call it by reflection.
- Timing: RTG registers its own biomes in post-init and locks the list at load complete. Ours must go in
  post-init.
- Decoration: `IRealisticBiome.rDecorate` runs RTG's decos and then the base biome's own `decorate()`, unless
  `overridesHardcoded()`. A biome's decorator (trees, cherry leaves, mangrove roots) still runs.
  `IWorldGenerator`s run as usual.
- Biome per column after generation: RTG repairs the map per chunk. It turns coastal ocean into beach, and
  similar. The chunk keeps the repaired biomes, but the `BiomeProvider` still answers with the unrepaired map.
  `ChunkGeneratorRTG.getLandscape(provider, chunkPos)` is public and cached. Its `biome` field is the repaired
  result, indexed `x * 16 + z` (an `IRealisticBiome[]` in 6.1, a `List` in RTGU), and `.baseBiome()` gives the
  `Biome`. Core's `RtgTerrain` already reads `noise` from the same object.

## Plan

1. **Core 1.4.5 (or later): `terrain/RtgBiomes`.**
   - `RtgBiomes.register(Biome biome, RtgTemplate template)`, where the template is one of `OCEAN`, `DEEP_OCEAN`,
     `FROZEN_OCEAN`, `PLAINS`, `FOREST`, `BIRCH_FOREST`, `JUNGLE`, `SWAMP`, `TAIGA`, `SAVANNA`, `ROOFED_FOREST`,
     `MESA` and so on. Each one maps to RTG's own vanilla terrain and surface classes, with the biome's top and
     filler blocks.
   - Backports call it in pre-init or init. Core holds the requests and hands them to RTG in its own post-init.
     Only one Core class links against RTG: compile only, as the `deobfProvided` RTGU 1.1.0 jar (CurseForge
     648514, file 4696300).
   - `RtgBiomes.biomeAt(world, x, z)` returns the repaired biome; it is Update Aquatic's `RtgCompat.biomeAt`
     moved. Also expose it through `StructurePlacements.biomeAt(World, ...)` so every family structure's biome
     check matches the chunk in RTG worlds.
2. **Per backport, one line each** (each needs a Core version gate):

   | Backport | Biome | Template |
   |---|---|---|
   | Update Aquatic | warm, lukewarm, cold, frozen oceans and their deep forms | `OCEAN` / `DEEP_OCEAN` (move from `RtgOceans`) |
   | Village & Pillage | `bamboo_jungle` | `JUNGLE` |
   | Trails & Tales | `cherry_grove` | `FOREST`, with RTG's hill terrain, since cherry groves sit on slopes |
   | Wild Update | `mangrove_swamp` | `SWAMP` |
   | Tricky Trials | `pale_garden` | `ROOFED_FOREST` |
   | Game Drops | `dappled_forest` | `FOREST` |

   - The deep dark (Wild Update) and sulfur caves (Game Drops) are underground regions in 1.12 terrain, not
     biome-map cells, so they need no realistic biome. Check that their "under high ground" tests read RTG heights
     through `TerrainHeights`, which already handles RTG.
   - Caves & Cliffs' biomes exist only in its own `cavesandcliffs` world type, so they are out of scope.
3. **Structure checks.** Wherever a backport reads `world.getBiomeProvider()` to pick a structure start (ocean
   structures, villages by biome, trail ruins, trial chambers by biome), read `RtgBiomes.biomeAt` in RTG worlds.
   Otherwise starts land where RTG repaired the biome to something else.
4. **Test rig: `G:\VBPTest\ua-rtg`.**
   - Forge 2860, RTGU, Core, Fluidlogged API, Pillar Patched, and Chunk Pregenerator.
   - The scratchpad scripts `run_uartg.sh` and `survey.py` give the biome histogram and the structure biome and
     float checks per world. Copy them into `tools/` when this is built.
   - For each backport: a 32-chunk pregen on seed 1144 with and without the registration. The biome must appear
     in the histogram, and no plains may appear where the biome map said otherwise.


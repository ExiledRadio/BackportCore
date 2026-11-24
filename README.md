# Backport Core

A Forge 1.12.2 library for the update backports. Its one block is today's jigsaw block, for operators. It holds what the backports share:

- **Vanilla's textures as the newest installed backport's version has them**: 1.14's Texture Update to begin
  with, then each later version's retextures up to that one (1.17's ores with Caves & Cliffs Backport, and so
  on), with a switch back to 1.12.2's look (`textures.modernTextures` in `config/backportcore.cfg`). Resource packs you pick,
  and mods that change a vanilla texture on purpose, still win over it.
- **Threaded chunk rebuilds**: blocks the server changes near you (a kelp column breaking, crops, pistons)
  are rebuilt on the chunk builder threads instead of stalling the frame, as current vanilla's "Chunk
  Builder" setting does (`rendering.blockUpdates`; `NEARBY` is 1.12.2's rule). Not needed, and inactive,
  with OptiFine, Celeritas, Vintagium or Nothirium.
- **Moving worlds over** from another mod's copies of the same blocks: on demand, or automatically when that
  mod has been removed, keeping each block's facing, contents and the rest.
- **Hiding duplicates** when two mods add the same block.
- **Planning structures on modded terrain**: vanilla, superflat, OTG, Biomes O' Plenty, RTG and the 1.18-height
  generators, ground and sea floor alike.
- **Today's structure files** read into 1.12.2 blocks, structures scattered the way today's game scatters
  them, and finding the nearest of a kind. Jigsaw structure mods' `data/` folders generate as they are, from a
  mod jar or a structure pack in `config/backportcore/structures/` ([docs/PORTING_STRUCTURES.md](docs/PORTING_STRUCTURES.md)).
- **Each update's look and feel** up to the newest installed backport: options screens, F3, chat, menus, loading
  screens, splashes, wording. Core owns every piece not tied to one backport's content, so each backport looks like
  its version on its own ([docs/PRESENTATION.md](docs/PRESENTATION.md)).
- **1.13's waterlogging** for the backports' blocks, through Fluidlogged API when it is installed.
- **1.14's player poses**: the swimming pose and crawling with their 0.6-block box and low eyes, sent to other
  players, drawn lying along the swim (armor included), with the camera easing to the new height; and the
  movement hooks a backport's swimming rules use.
- **Vanilla's creative tabs** for the backports' blocks and items, each next to its vanilla neighbor as 1.19.2
  lists it. The backports have no tabs of their own.
- A **trade registry**, **smithing upgrades** and a table of which backport owns each shared block.

A coremod of its own for its hooks (every patch in `docs/PATCHES.md`; each does what vanilla does until a
backport or the newest installed version turns it on). No Mixin. Required on the client and the server by every backport that uses it. Waterlogging needs
[Fluidlogged API](https://www.curseforge.com/minecraft/mc-mods/fluidlogged-api) 3.3.3 or later (installed beside
it, never bundled); without it everything else works and the backports' blocks stay dry.

## Building

```bash
./gradlew build
```

`build/libs/BackportCore-<version>.jar` is the release jar (reobfuscated); `-dev.jar` is what the backports
compile against. After a change, `./gradlew build copyToDependents` copies the dev jar into each backport's
`deps/` folder. Forge is pinned at 14.23.5.2847 (see `gradle.properties`); don't raise it.

Self-tests, headless: `./gradlew runServer -Pselftest` compiles `src/selftest` in, runs the tests on a dedicated
dev server (port and world in `eclipse/server.properties`), logs `SELFTEST DONE pass=N fail=M` and stops. Delete
that world (`level-name`) first for a fresh one. Never `cleanRunServer` (it deletes `eclipse/`), and build again
afterwards: a run task leaves an unobfuscated jar in `build/libs`.

API for the backports: [docs/API.md](docs/API.md). Releasing Core or a backport, update notices first:
[docs/RELEASING.md](docs/RELEASING.md).

Not affiliated with Mojang. Licensed MIT.

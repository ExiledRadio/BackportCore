# Backport Core

A Forge 1.12.2 library for the update backports. It adds no blocks or items. It holds what they share:

- **1.14's Texture Update** for vanilla's blocks, items and mobs, plus 1.15's enchantment glint, with a switch
  back to 1.12.2's look (`textures.modernTextures` in `config/backportcore.cfg`). Resource packs you pick,
  and mods that change a vanilla texture on purpose, still win over it.
- **Moving worlds over** from another mod's copies of the same blocks: on demand, or automatically when that
  mod has been removed, keeping each block's facing, contents and the rest.
- **Hiding duplicates** when two mods add the same block.
- **Planning structures on modded terrain**: vanilla, superflat, OTG, Biomes O' Plenty, RTG and the 1.18-height
  generators, ground and sea floor alike.
- **Today's structure files** read into 1.12.2 blocks, structures scattered the way today's game scatters
  them, and finding the nearest of a kind.
- **1.13's waterlogging** for the backports' blocks, through Fluidlogged API.
- A **trade registry**, **smithing upgrades** and a table of which backport owns each shared block.

No coremod or Mixin of its own. Required on the client and the server by every backport that uses it, and
requires [Fluidlogged API](https://www.curseforge.com/minecraft/mc-mods/fluidlogged-api) 3.3.3 or later
(installed beside it, never bundled).

## Building

```bash
./gradlew build
```

`build/libs/BackportCore-<version>.jar` is the release jar (reobfuscated); `-dev.jar` is what the backports
compile against. After a change, `./gradlew build copyToDependents` copies the dev jar into each backport's
`deps/` folder. Forge is pinned at 14.23.5.2847 (see `gradle.properties`); don't raise it.

Self-tests, headless: `./gradlew runServer -Pselftest` compiles `src/selftest` in, runs the tests on a dedicated
dev server (port 25760 in `eclipse/server.properties`), logs `SELFTEST DONE pass=N fail=M` and stops. Delete
`eclipse/coretest` first for a fresh world. Never `cleanRunServer` (it deletes `eclipse/`), and build again
afterwards: a run task leaves an unobfuscated jar in `build/libs`.

API for the backports: [docs/API.md](docs/API.md).

Not affiliated with Mojang. Licensed MIT.

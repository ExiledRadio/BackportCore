# Plan: Backport Core 1.5.0

Status: plan (2026-11-25). Nothing started.

## Where 1.4.x ends

**1.4.9 is the last 1.4.x.** It holds:
- the Dynamic Trees work already on main;
- the generic hooks the new add-on **Dynamic Trees - Backport** needs (being built now);
- the JEI/HEI effects fix on branch `effects-jei` (aa7aaef), which is already written up as 1.4.9.

Once those three are in, 1.4.9 is closed and is released with its set:
- Village & Pillage 2.7.6
- Buzzy Bees 1.1.3
- Caves & Cliffs 1.1.6
- Game Drops 1.0.2
- the add-on

Everything below is 1.5.0.

## Why 1.5.0 and not 1.4.10

1.5.0 adds a new kind of content to Core: blocks of its own that end up in players' worlds (stream 1), and 26.4's systems moved out of the backports (stream 2). Both change what Core is, not just how it behaves, so the minor version goes up. The backports that hand systems to Core will need Core 1.5.0.

## Streams

### 1. Modern blocks supplied by Core (headline)
Built as designed in [PLAN_MODERN_BLOCKS.md](PLAN_MODERN_BLOCKS.md):
- the scan;
- `supplied_blocks.txt`;
- the generator from the modern jar;
- the two-way rename to and from the owning backport;
- real backport blocks used in structures whenever the backport is installed.

1.5.0 ships slices 1 and 2: the missing-name report and the first 30 blocks, with the add-and-remove round trip tested. Slice 3 (every shape class) can follow in 1.5.x.

### 2. 26.4 Deep Freeze systems (after 26.4 releases)
From GameDropsBackport `docs/PLAN_26_4.md`, decisions recorded 2026-11-24. These pieces belong in Core:
- **Freeze ticks**: the frozen-ticks counter, the damage every 40 ticks, the frost overlay, and leather and mob immunity. They move out of Caves & Cliffs. Powder snow and Game Drops' Freezing effect both feed them, so Caves & Cliffs needs a release that hands its powder snow freezing to Core.
- **Speleothem helper**: Caves & Cliffs' pointed dripstone code becomes shared code for growth, falling, landing damage and the trident break. Pointed dripstone and Game Drops' icicles both use it, as vanilla shares it.
- **26.4 look and feel** (stream 8 of that plan), per the 1.4.7 rule that Core owns each version's feel:
  - the advancement renames;
  - 26.4's splashes and wording;
  - the Javanese and Chavacano languages;
  - Adventuring Time's ice caves and Monster Hunter's frostbite (`advancement.AdventuringTime`).

**Built 2026-11-26 on Pre-Release 1** (the user chose not to wait), on branch `core-1.5.0-deepfreeze`: freeze ticks, speleothems, Monster Hunter, `BiomeLocate.next`, and 26.4's look and feel (`versions.Look264`: Improved Fog, the Repeat Recipe key, recipe book tab tooltips, Other Players and mute, the pause menu's Other Players icon; the renames and reworded text are Game Drops' vanilla text). Not built: F3 categories, keyboard navigation, Javanese and Chavacano. At release, diff the release jar against Pre-Release 1 (`E:\MCDownloads\minecraft-26.4-pre-1`, decompiled).

### 3. Leftover branch
`claude/core-light-updates` (3ba3e29, 2026-11-23) uses 1.14's light packet for chunks players already have. It was never merged. Rebase it onto 1.5.0, run its tests, and merge.

### 4. Housekeeping
- Stale branches: `settings-configuration` and `wip/settings-shots-optfl` (2026-11-09/16, settings screenshot work). Check whether anything in them is still wanted, then delete them.
- `docs/API.md` and `docs/PORTING_STRUCTURES.md`: add the supplied blocks and the freeze and speleothem APIs.

## Order

1. Close 1.4.9: the add-on hooks land, `effects-jei` merges, then `#release` / `#finalrelease` for the 1.4.9 set.
2. Bump Core to 1.5.0-dev. Start stream 1 slice 1, the missing-name report, which also sizes slice 2. Merge stream 3 alongside.
3. Stream 1 slice 2.
4. Stream 2 when 26.4 releases. It goes with the Game Drops 1.1.0 and Caves & Cliffs releases that depend on it.
5. Release Core 1.5.0 through `docs/RELEASING.md`. The update notice table names 1.5.0 and every backport that requires it.

## Done when
- Every stream that ships is merged.
- Core's self-tests pass with Core alone and with the full family. Stream 1's world round trip passes on a dedicated server and on a client.
- The CHANGELOG has a 1.5.0 section, and the CurseForge description mentions supplied blocks.

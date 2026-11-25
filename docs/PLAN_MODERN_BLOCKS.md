# Plan: modern blocks supplied by Core

Status: plan only (2026-11-25). Target: Backport Core 1.5.0.

## Goal

Drop a modern structure mod's `data/` folder into `config/backportcore/structures/` (or install a port that ships it in its jar), and every vanilla block it uses is there, with no other backport installed and no hand-written mappings.

Today's path, in `structure/ModernPalette`: a registered mapping, then the owning backport (`BlockOwnership`), then a family mod's block of the same path, then 1.12.2's own block via the Flattening table, then a `fallback` stand-in. Anything else is left out of the structure and logged once. This plan adds one step before the stand-in: **a block Core supplies itself**.

## Rules

1. **Decorative only.** Core's block has the modern block's model, textures, shape, hardness, blast resistance, sound, light level, tool, drops and the properties its blockstate needs (facing, axis, half, waterlogged, etc.). It has no behavior. Copper doesn't oxidize, sculk doesn't spread, a crafter doesn't craft, a sign can't be edited. Behavior stays with the backport that owns the block, which is the reason to install it.
2. **Only what is used.** Core registers a block only if a structure file it can read names it. That keeps a pack's block ID budget (4,096 without REID/JEID) safe: a folder that uses 30 modern blocks costs 30 IDs, not 700.
3. **Once supplied, always supplied.** A block Core registered once stays registered in later games (`config/backportcore/supplied_blocks.txt`), even if the structure that needed it is removed. Otherwise removing a folder deletes every one of those blocks from existing worlds.
4. **The backport wins, and structures get its real block.** If the block's owner is installed, Core registers nothing, and structures place the owner's block, with its behavior. A trial chamber with Caves & Cliffs installed gets C&C's copper, which oxidizes and can be waxed and scraped; without it, Core's decorative copper looks the same and stays as placed. Same lookup as `ModernPalette` step 2 (`BlockOwnership`) today. Core's copies only fill gaps.
   - **Placed before the backport arrived:** blocks a structure placed as Core's copies become the real block when the backport is added (the rename under "Names and worlds"). Copper in an existing trial chamber starts oxidizing from then on.
   - **Tile entities:** where the real block has one (a crafter, a vault, a decorated pot's sherds), the structure's block NBT goes to the real block, as it does today. Core's copy drops that NBT, and the generator lists those blocks so the stand-in is chosen knowingly.
5. **Closest to vanilla.** Numbers come from the modern jar, not by eye.

## Names and worlds

Core's copies are `backportcore:<path>`. Rule 4's handover reuses the `BlockOwnership` pattern:

- **Owner added later:** Core doesn't register `backportcore:deepslate_bricks`, so Forge reports it missing at world load. Core remaps it to `cavesandcliffsbackport:deepslate_bricks`. The states must match, so the remap is a rename. Where they can't match (an owner with extra states), Core's copy uses the owner's metadata layout from the start, read from the owner's declaration.
- **Owner removed later:** the owner's names go missing. Core registers its copies of every owned block listed in `supplied_blocks.txt`, or in the owner's declared table if the user opts in, and remaps them. Player-built blocks of a backport that is removed and not in that list are lost, as with any mod removal.
- **Items:** each block gets its item. Same rename rules (`MissingMappings<Item>`).
- **Other mods' backports** (Future MC, Deeper Depths and others) keep their current palette priority. Core supplies a block only if no installed mod already maps it.

## Where the blocks come from

Hand-writing hundreds of blocks is not the plan. A generator reads the decompiled modern jar (26.x jars are unobfuscated; `G:/MCDownloads`, Vineflower) and writes one table:

- **From `Blocks.java` and the block classes:** hardness, resistance, sound type, light, map color, tool and tier, the shape class (cube, slab, stairs, wall, fence, pane, pillar, carpet, plant, door, trapdoor, lantern, chain, etc.).
- **From the assets:** blockstates, models and textures. Each model is converted to 1.12 JSON, with the same steps as the backports' asset pipeline (`tools/png_rgba.py` for keyed PNGs).
- **From loot tables:** drops (self, silk touch, nothing).

Core turns each row into a block of the matching shape class. Rows whose shape class Core doesn't have are skipped, so they keep today's fallback, and the generator lists them. New shape classes get added as structures need them.

The table and assets live in Core's jar. Only rows a game needs are registered. Expected size: roughly 2-4 MB of textures and models for all decorative 1.13-26.x blocks.

## Scanning

`DataPacks` already lists every `data/` file in mod jars and drop-ins. A new pass reads every `.nbt` structure's palette and every processor list's output states (`rule`, `block_rot` replacements) before the block registry event, and collects modern block names. Each name is first brought up to date by DataVersion (`Flattening`). It is cached by file size and date so startup stays fast.

Names that resolve without Core (registered mapping, owner, family mod, 1.12.2's own) are dropped. What is left, minus rows the table doesn't have, is what Core registers.

## Slices

1. **Design check (this doc), then the missing-name report.** Run the scan on the structures we have (Core's own, the family's, two or three public modern structure packs). It prints which modern names fall through to a stand-in or get left out today. That list sizes the work and picks the first blocks.
2. **First 30 blocks**: the most common names from that report. They are likely deepslate and tuff variants, copper decoratives, mud bricks, cherry and bamboo wood sets, lanterns and chains (where not already owned). These come with the generator, the scan, `supplied_blocks.txt` and the two remaps.
   - **Tests:** a world made with Core's copies, then C&C added (blocks become C&C's and states match), then C&C removed again (Core's copies come back). Also a dedicated server and a client.
3. **Generator coverage**: every shape class the report needs. The rest of the 1.13-26.x decorative blocks.
4. **Docs**: `PORTING_STRUCTURES.md` ("drop the folder in"), `API.md` (a mod can declare its own supplied list), the CurseForge description.

## Risks

- **Remaps across save and load** are the part most likely to destroy blocks. Slice 2 doesn't ship without the add and remove round trip tested.
- **State layout drift.** If an owner later changes a block's metadata, Core's copy must follow, so the owners' tables become a contract.
- **Jar size and load time**: measure in slice 3.
- **Licensing** is the same as the backports: textures are Mojang's, and we already ship them.

## Not in scope

- Behavior for any block (that's what the backports are for).
- Modern entities, biomes or items without a block.
- Registering blocks no structure uses (a "full block library" mode can come later as a setting, off by default, because of the ID budget).

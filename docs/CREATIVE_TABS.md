# Creative tabs

Backport Core puts every backport's blocks and items in vanilla's creative tabs; no backport has a tab of its own.
The API is in `docs/API.md` (`creative.CreativePlacement`). This page is the placement rule and the table behind it.

## The rule

*   **Tab:** the tab the item has in Minecraft 1.19.2, the last version with 1.12's tab set: Building Blocks,
    Decoration Blocks, Redstone, Transportation, Miscellaneous, Foodstuffs, Tools, Combat, Brewing. Materials is
    Miscellaneous in 1.12, as in 1.19.2. Spawn eggs stay in Miscellaneous, as in 1.12. An item 1.19.2 keeps out of
    the creative menu (debug stick, suspicious stew) stays out.
*   **Position:** next to its vanilla neighbor in 1.19.2's order. Up to 1.19.2 every tab lists its items in item
    registry order, so 1.19.2's `Items` is the order. A tab still lists 1.12.2's own items in 1.12.2's order; Core
    moves only the backports' stacks, each to the nearest stack before it in 1.19.2's order that the tab shows, or
    before the nearest one after it, whichever is of the same kind (the shulker box goes before the white shulker box,
    not after the black glass pane that precedes it in 1.19.2), then whichever is nearer.
*   **Across backports:** a backport's item can be another's neighbor. With Village & Pillage alone, stripped logs
    follow the dark oak log; with the Nether Update too, the crimson and warped stems come first, as in 1.19.2.

## Items newer than 1.19.2

1.19.3 replaced the tabs, so these have no 1.19.2 place. Each is put after the item before it in Mojang's newer item
registry (26.1), which keeps kinds together (cherry planks with the planks, cherry doors with the doors), in that
item's 1.19.2 tab. Three adjustments follow today's own creative tabs:

| Items | Rule | Where |
|---|---|---|
| Cherry, pale oak, bamboo sets | Today's wood order | After mangrove: cherry, pale oak, bamboo, then crimson and warped. Bamboo's block, mosaic and raft stand for a log, planks and boat. |
| Newer spawn eggs (breeze, bogged, armadillo...) | Alphabetical by mob, as 1.19.2's eggs | Among the other eggs, Miscellaneous. |
| Copper grates | A run after the chiseled coppers | Building Blocks; waxed ones after the waxed chiseled coppers. |
| Copper bulbs | After the redstone lamp | Redstone. |
| Crafter | After the dropper | Redstone. |
| Copper chests, copper golem statues | After the chest | Decoration Blocks. |
| Vault | With the end portal frame | Decoration Blocks. |
| Decorated pot | After the flower pot | Decoration Blocks. |
| Breeze rod, heavy core | After the blaze rod | Miscellaneous. |
| Trial key, ominous trial key | After the bottle o' enchanting | Miscellaneous. |
| Copper horse armor | After the leather horse armor | Miscellaneous. |
| Trial spawner | Before the first spawn egg | Miscellaneous (today's Spawn Eggs tab starts with the spawners). |
| Mace | After the trident | Combat. |
| Ominous bottle | After the honey bottle | Foodstuffs (today's Food & Drinks). |

The table is `src/main/resources/assets/backportcore/creative/vanilla_order.txt`, built by
`tools/creative/build_tables.py` from 1.19.2's decompiled `Items.java` and 26.1's `Items.class` and
`ItemStackTheFlatteningFix.class` (for `legacy_items.txt`, which finds a 1.12.2 stack such as `minecraft:stone@1`
under today's name, `minecraft:granite`).

## Checking a tab

A client started with `-Dbackportcore.creativeDump=<file>` writes every vanilla tab's list at the first title
screen, to the file and the log, and with `-Dbackportcore.creativeDump.exit=true` closes. A Gradle init script
sets both on a backport's `runClient` without touching its build:

```groovy
allprojects {
    afterEvaluate {
        def t = tasks.findByName('runClient')
        if (t != null) {
            t.systemProperty 'backportcore.creativeDump', new File(project.projectDir, 'creative_tabs_dump.txt').absolutePath
            t.systemProperty 'backportcore.creativeDump.exit', 'true'
        }
    }
}
```

`gradlew runClient -I ctdump.gradle`. In the dump, `*` marks a placed stack, with the stack before it. The end lists
placements that show nowhere, vanilla stacks the table cannot place (none, in 1.12.2), and whether every placed item
lists itself for the search tab, for its own tab object and in JEI.

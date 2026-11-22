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

Today's `Items` holds some kinds in one field each (the copper lanterns, chains and bars), which `build_tables.py`'s
field list cannot name one by one, and later drops added more (poplar, sulfur and cinnabar sets, wool and concrete
stairs and slabs, cushions). `tools/creative/build_order_263.py` completes the table from 26.3's creative menu: each
item it lists that the table lacks goes with its kind, after the last item sharing the most of its name's ending, in
that item's tab (a poplar log after the last log, in Building Blocks; copper lanterns after the soul lantern, in
Decoration Blocks; a tie goes to the tab of the item 26.3 lists before it). A spawn egg goes among the eggs by mob
name; an item of no kind goes after the item 26.3 lists before it (the sulfur and cinnabar blocks, in Building Blocks).

## From 26.3: 26.3's order

With a backport of 26.3 or later installed (Game Drops Backport) and the inventory part of the look at 26.3, every
vanilla tab lists its items in the order 26.3's creative menu shows them (`creative.client.ModernOrder`). 26.3 has
other tabs, so each 1.12.2 tab reads the 26.3 tabs its items come from, its home tabs first, then the rest in the
creative menu's order (Building Blocks, Colored Blocks, Natural Blocks, Functional Blocks, Redstone Blocks, Tools &
Utilities, Combat, Food & Drinks, Ingredients, Spawn Eggs):

| 1.12.2 tab | Reads first |
|---|---|
| Building Blocks | Building Blocks |
| Decoration Blocks | (26.3's order: Building, Colored, Natural, Functional...) |
| Redstone | Redstone Blocks |
| Transportation, Tools | Tools & Utilities |
| Miscellaneous | Ingredients |
| Foodstuffs, Brewing | Food & Drinks |
| Combat | Combat |

An item 26.3 lists twice (a log in Building Blocks and Natural Blocks) takes the first of those places. Each stack
is found as the 26.3 item it is: a vanilla stack through `legacy_items.txt`, a backport's by its placement (an item
placed after another just after that one). The tabs stay 1.12.2's (an item's tab is still its 1.19.2 one). Stacks
26.3 does not list, other mods' included, follow the rest in the order the tab had (registry order). Before 26.3, or
with the Look & Feel page's inventory switch off, the tabs keep the order above. Only the tabs' listing changes:
the search tab, JEI and `getSubItems` do not.

The table is `src/main/resources/assets/backportcore/creative/order_26_3.txt`: `tools/creative/CreativeOrder263.java`
runs 26.3's own `CreativeModeTabs` from the 26.3 server jar (with its bundled libraries, Java 25) and prints every
tab; `tools/creative/build_order_263.py` adds the home tabs, the goat horn (its tab needs a data pack reload) and the
ids 26.3 renamed (`chain` is `iron_chain`).

## From 1.19.3: 1.19.3's tabs

With the inventory part of the look at 1.19.3 or later (Wild Update Backport and every later backport), the creative
menu's first page shows 1.19.3's tabs instead of 1.12.2's nine (`creative.client.ModernTabs`, drawn by patch P47):

| Row | Tabs, left to right |
|---|---|
| Top | Building Blocks, Colored Blocks, Natural Blocks, Functional Blocks, Redstone Blocks; Saved Hotbars and Search at the right end |
| Bottom | Tools & Utilities, Combat, Food & Drinks, Ingredients, Spawn Eggs; Operator Utilities and the Survival Inventory at the right end |

Operator Utilities shows with Controls' Operator Items Tab on (1.19.3's option, off by default) for a player allowed
commands (level 2), and lists the operator items 1.12.2 has: command blocks, the command block minecart, the structure
block and void, the barrier. Tabs are 1.19.3's size (26 pixels, 27 apart); their sprites are 1.19.3's `tabs.png`, which
Wild Update Backport supplies (`Contribution.replaceAsset`). Without it, 1.12.2's sprites are cut to that width.

**Contents.** Each tab lists what 1.19.3's lists, in its order (`creative/tabs_1_19_3.txt`); from 26.3 on, what 26.3's
lists (`order_26_3.txt`'s own tabs). The stacks are those the nine 1.12.2 tabs list, the backports' placed items
included, each found as the item it is today (as for 26.3's order above), and each in every tab that lists that item
(a log in Building Blocks and Natural Blocks). Enchanted books are 1.19.3's: every enchantment's book at its top level,
at the end of Ingredients. A stack neither table lists (another mod's item in a vanilla tab) goes after the rest of
the tab its 1.12.2 tab leads to:

| 1.12.2 tab | 1.19.3 tab |
|---|---|
| Building Blocks | Building Blocks |
| Decoration Blocks | Functional Blocks |
| Redstone | Redstone Blocks |
| Transportation, Tools | Tools & Utilities |
| Combat | Combat |
| Foodstuffs, Brewing | Food & Drinks |
| Miscellaneous | Ingredients (a spawn egg: Spawn Eggs) |

**Other mods.** Their own tabs are untouched and follow on page 2 on, ten to a page, as 1.12.2 Forge pages them. Every
item keeps its 1.12.2 tab, so `getSubItems`, the search tab and JEI list as before; the search tab's tooltip names the
first 1.19.3 tab the item is in. Core's eleven tabs are added to `CreativeTabs.CREATIVE_TAB_ARRAY` at load complete,
after every mod's, and are on no page while the look is older.

The table is built by `tools/creative/build_tabs_1193.py` from 1.19.3's `CreativeModeTabs`, decompiled with Mojang's
names (each `accept(Items.X)`, each generated set as its item: fireworks, goat horns, suspicious stew, potions, tipped
arrows, enchanted books). Items added after 1.19.3 go in their 26.3 tab, after the item 26.3 lists before them.

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
lists itself for the search tab, for its own tab object and in JEI. From 26.3 it also lists the stacks 26.3's order does
not name, tab by tab.

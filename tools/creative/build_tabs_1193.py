"""Builds Core's 1.19.3 creative tabs (creative/client/ModernTabs):

  src/main/resources/assets/backportcore/creative/tabs_1_19_3.txt

1.19.3 replaced the creative menu's tabs (Building Blocks, Colored Blocks, Natural Blocks, Functional Blocks, Redstone
Blocks, Tools & Utilities, Combat, Food & Drinks, Ingredients, Spawn Eggs, Operator Utilities). This reads every
category tab's item list from 1.19.3's own CreativeModeTabs, decompiled (Mojang's names): each `accept(Items.X)` is
minecraft:x, each generated set is its item once (generateFireworksAllDurations: firework_rocket, generateInstrumentTypes
(Items.GOAT_HORN): goat_horn, generatePotionEffectTypes(Items.P): p, generateSuspiciousStews: suspicious_stew, the
Ingredients tab's enchanted books: enchanted_book). Raid.getLeaderBannerInstance (the ominous banner) is left out:
1.12.2 has no such stack.

The items later versions added are not 1.19.3's, but the backports add them: each item 26.3's order
(order_26_3.txt) lists that 1.19.3 does not is put in its 26.3 tab, after the nearest item 26.3 lists before it that
1.19.3's tab has (before the nearest after it when there is none). Operator Utilities keeps 1.19.3's list. Ids are
26.3's (the aliases of order_26_3.txt are applied), so the same keys find both tables.

Usage:
  python -I tools/creative/build_tabs_1193.py <1.19.3 CreativeModeTabs.java> <order_26_3.txt> <out file>
"""
import re
import sys

TABS = [
    ('BUILDING_BLOCKS', 'building_blocks'),
    ('COLORED_BLOCKS', 'colored_blocks'),
    ('NATURAL_BLOCKS', 'natural_blocks'),
    ('FUNCTIONAL_BLOCKS', 'functional_blocks'),
    ('REDSTONE_BLOCKS', 'redstone_blocks'),
    ('TOOLS_AND_UTILITIES', 'tools_and_utilities'),
    ('COMBAT', 'combat'),
    ('FOOD_AND_DRINKS', 'food_and_drinks'),
    ('INGREDIENTS', 'ingredients'),
    ('SPAWN_EGGS', 'spawn_eggs'),
    ('OP_BLOCKS', 'op_blocks'),
]

ACCEPT = re.compile(r'\$\$1\.accept\(Items\.([A-Z0-9_]+)\);')
GENERATED = [
    (re.compile(r'generateFireworksAllDurations\('), 'minecraft:firework_rocket'),
    (re.compile(r'generateInstrumentTypes\(\$\$1, Items\.GOAT_HORN'), 'minecraft:goat_horn'),
    (re.compile(r'generateSuspiciousStews\('), 'minecraft:suspicious_stew'),
    (re.compile(r'generateEnchantmentBookTypesOnlyMaxLevel\('), 'minecraft:enchanted_book'),
]
# Item fields whose name is not the id.
FIELD_FIXES = {'minecraft:cut_standstone_slab': 'minecraft:cut_sandstone_slab'}
POTIONS = re.compile(r'generatePotionEffectTypes\(\$\$1, Items\.([A-Z0-9_]+)')
LIGHT = re.compile(r'LightBlock\.setLightOnStack\(new ItemStack\(Items\.LIGHT\)')


def read_1193(path):
    text = open(path, encoding='utf-8').read().split('\n')
    tabs = {}
    current = None
    for line in text:
        m = re.search(r'private static final CreativeModeTab ([A-Z_]+) = ', line)
        if m:
            name = dict(TABS).get(m.group(1))
            current = [] if name else None
            if name:
                tabs[name] = current
            continue
        if current is None:
            continue
        ids = []
        m = ACCEPT.search(line)
        if m:
            ids.append('minecraft:' + m.group(1).lower())
        for pattern, item in GENERATED:
            if pattern.search(line):
                ids.append(item)
        m = POTIONS.search(line)
        if m:
            ids.append('minecraft:' + m.group(1).lower())
        if LIGHT.search(line):
            ids.append('minecraft:light')
        for i in ids:
            i = FIELD_FIXES.get(i, i)
            if i not in current:
                current.append(i)
    return tabs


def read_263(path):
    aliases = {}
    tabs = {}
    current = None
    for line in open(path, encoding='utf-8'):
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        f = line.split(' ')
        if f[0] == 'alias' and len(f) == 3:
            aliases[f[1]] = f[2]
        elif f[0] == 'tab' and len(f) == 2:
            current = []
            tabs[f[1]] = current
        elif f[0] == 'home':
            continue
        elif current is not None:
            current.append(f[0])
    return aliases, tabs


def main(src, order263, out):
    tabs = read_1193(src)
    missing = [n for _, n in TABS if n not in tabs]
    if missing:
        sys.exit('1.19.3 tabs not found: %s' % missing)
    aliases, modern = read_263(order263)
    for name in tabs:
        tabs[name] = [aliases.get(i, i) for i in tabs[name]]
    in_1193 = set(i for items in tabs.values() for i in items)
    known_263 = set(i for items in modern.values() for i in items)
    unknown = sorted(i for _, n in TABS if n != 'op_blocks' for i in tabs[n] if i not in known_263)
    added = 0
    for name, items in modern.items():
        if name not in tabs:
            continue
        own = tabs[name]
        for idx, item in enumerate(items):
            if item in in_1193:
                continue
            at = None
            for before in reversed(items[:idx]):
                if before in own:
                    at = own.index(before) + 1
                    break
            if at is None:
                for after in items[idx + 1:]:
                    if after in own:
                        at = own.index(after)
                        break
            if at is None:
                at = len(own)
            own.insert(at, item)
            in_1193.add(item)
            added += 1
    with open(out, 'w', encoding='utf-8', newline='\n') as w:
        w.write('# Generated by tools/creative/build_tabs_1193.py from 1.19.3\'s CreativeModeTabs (decompiled).\n')
        w.write('# "tab <1.19.3 tab>": that tab\'s items follow, in 1.19.3\'s order. Items added after 1.19.3 (26.3\'s\n')
        w.write('# order_26_3.txt) are in their 26.3 tab, after the item 26.3 lists before them. Ids are 26.3\'s.\n')
        for _, name in TABS:
            w.write('tab %s\n' % name)
            for i in tabs[name]:
                w.write(i + '\n')
    print('%d tabs, %d items from 1.19.3, %d added from 26.3' % (
        len(TABS), sum(len(v) for v in tabs.values()) - added, added))
    if unknown:
        print('1.19.3 ids 26.3 does not list (check for renames):')
        for i in unknown:
            print('  ' + i)


if __name__ == '__main__':
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3])

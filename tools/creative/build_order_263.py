"""Builds Core's 26.3 creative order (creative/client/ModernOrder):

  src/main/resources/assets/backportcore/creative/order_26_3.txt

From the output of CreativeOrder263.java (26.3's own CreativeModeTabs, run from the 26.3 server jar): every item of
every 26.3 tab, tab by tab in the creative menu's order (Building Blocks, Colored Blocks, Natural Blocks, Functional
Blocks, Redstone Blocks, Tools & Utilities, Combat, Food & Drinks, Ingredients, Spawn Eggs; Operator Utilities left
out, as its tab needs operator rights and 1.12.2 lists none of it).

1.12.2's tabs are not 26.3's, so the file also says which 26.3 tabs each 1.12.2 tab reads first (HOMES): a 1.12.2 tab
lists its items in the order its home tabs show them, then the other 26.3 tabs in their own order. An item 26.3 lists
in several tabs (a log in Building Blocks and Natural Blocks) takes its place in the first of those, in that order.

Two more pieces:
  - INSERTS: items the oracle cannot list outside a running game, put where 26.3's code lists them (goat horns need
    the instrument tag, which only a data pack reload fills).
  - ALIASES: ids Core's other tables (vanilla_order.txt, 26.1-based) or the backports' placements use that 26.3 has
    renamed.

It also completes vanilla_order.txt (build_tables.py's, 1.19.2's order with 26.1's later items): every item 26.3's
creative menu lists that the table lacks (26.1's copper lanterns and chains, 26.2's and 26.3's poplar, sulfur and
cinnabar sets...) is added, marked new, with its kind, as the table keeps kinds together: after the last of the
table's items sharing the most of its name's ending, in their 1.19.2 tab (a poplar log after the last log, in Building
Blocks; a copper lantern with the lanterns, in Decoration Blocks). A spawn egg goes among the eggs by mob name. An item
of no kind there goes after the item 26.3 lists before it, in that item's tab (Building Blocks when 26.3 lists it in
Building Blocks: the sulfur and cinnabar blocks). Ids 26.3 only renamed (ALIASES) are not added again. Without a place
there, a backport's item of that name had no creative tab at all. Run after build_tables.py; listed items are kept.

26.4 (Deep Freeze) is built the same way from CreativeOrder264.java (run from the 26.4 server jar) with --version 26.4,
into order_26_4.txt; completing vanilla_order.txt then adds 26.4's new items (snow and packed ice bricks, the calcite
sets, icicles, the frostbite egg...).

Usage:
  python -I tools/creative/build_order_263.py [--version 26.4] <oracle output> <out file> [<vanilla_order.txt to complete>]
"""
import sys

# The version whose creative menu the oracle output lists (--version).
VERSION = '26.3'

# 1.12.2 tab -> the 26.3 tabs it reads first.
HOMES = [
    ('BUILDING_BLOCKS', ['building_blocks']),
    ('DECORATIONS', []),
    ('REDSTONE', ['redstone_blocks']),
    ('TRANSPORTATION', ['tools_and_utilities']),
    ('MISC', ['ingredients']),
    ('FOOD', ['food_and_drinks']),
    ('TOOLS', ['tools_and_utilities']),
    ('COMBAT', ['combat']),
    ('BREWING', ['food_and_drinks']),
]

# (tab, item, the item 26.3's code lists just before it)
INSERTS = [
    ('tools_and_utilities', 'minecraft:goat_horn', 'minecraft:tnt_minecart'),
]

ALIASES = [
    ('minecraft:chain', 'minecraft:iron_chain'),
    ('minecraft:grass', 'minecraft:short_grass'),
    ('minecraft:scute', 'minecraft:turtle_scute'),
]

# New items whose name finds no kind, or the wrong one, in the table: (1.19.2 tab, the item they follow). Icicles and
# ice crystals grow in ice caves as pointed dripstone and amethyst clusters do, in Decoration Blocks; ice balls are
# thrown as snowballs are, and 26.4 lists them after the snowball (not with slime balls).
KINDS = {
    'minecraft:ice_ball': ('MISC', 'minecraft:snowball'),
    'minecraft:icicle': ('DECORATIONS', 'minecraft:pointed_dripstone'),
    'minecraft:ice_crystal': ('DECORATIONS', 'minecraft:icicle'),
}

# Ids build_tables.py took from 26.1's field names that the registry spells otherwise (the field is DRY_SHORT_GRASS,
# the item minecraft:short_dry_grass): corrected in vanilla_order.txt, so a backport placing the real id finds it.
TABLE_FIXES = {
    'minecraft:dry_short_grass': 'minecraft:short_dry_grass',
    'minecraft:dry_tall_grass': 'minecraft:tall_dry_grass',
}


def read_oracle(path):
    tabs = []
    with open(path, encoding='utf-8') as f:
        for raw in f:
            line = raw.rstrip('\n')
            if '[STDOUT]: ' in line:
                line = line.split('[STDOUT]: ', 1)[1]
            elif line.startswith('[') or not line.strip():
                continue
            line = line.strip()
            if line.startswith('== '):
                parts = line.split()
                tabs.append((parts[1], int(parts[2]), []))
            elif line.startswith('minecraft:') and tabs:
                tabs[-1][2].append(line)
    for name, count, items in tabs:
        if count != len(items):
            sys.exit('%s: the oracle says %d items, read %d' % (name, count, len(items)))
    return tabs


def shared_ending(a, b):
    x = a.split(':')[-1].split('_')
    y = b.split(':')[-1].split('_')
    n = 0
    while n < len(x) and n < len(y) and x[-1 - n] == y[-1 - n]:
        n += 1
    return n


def complete_vanilla_order(path, tabs):
    with open(path, encoding='utf-8') as f:
        lines = f.read().split('\n')
    header = [l for l in lines if l.startswith('#')]
    if VERSION == '26.3':
        note = "# Completed by tools/creative/build_order_263.py: the items 26.3's creative menu lists that 26.1's registry did not."
    else:
        note = ("# Completed by tools/creative/build_order_263.py --version %s: the items %s's creative menu lists that "
                "the table lacked." % (VERSION, VERSION))
    if note not in header:
        header.append(note)
    rows = [l.split(' ') for l in lines if l and not l.startswith('#')]
    for r in rows:
        r[0] = TABLE_FIXES.get(r[0], r[0])
    original = {r[0]: r[1] for r in rows if r[1] != '-'}
    renamed = {new: old for old, new in ALIASES}
    ids = [r[0] for r in rows]
    known = set(ids)
    added = []
    for name, _, items in tabs:
        for i, item in enumerate(items):
            if item in known or renamed.get(item) in known:
                continue
            if item in KINDS:
                tab, anchor = KINDS[item]
                at = ids.index(anchor) + 1
            elif item.endswith('_spawn_egg'):
                # Spawn eggs by mob name, as 1.19.2 lists them.
                eggs = [j for j, x in enumerate(ids) if x.endswith('_spawn_egg')]
                later = [j for j in eggs if ids[j] > item]
                at = later[0] if later else eggs[-1] + 1
                tab = 'MISC'
            else:
                # Its kind: the table's 1.19.2 items sharing the most of its name's ending (a log with the logs).
                best = 0
                votes = {}
                for other, t in original.items():
                    n = shared_ending(item, other)
                    if n > best:
                        best, votes = n, {}
                    if n == best and n > 0:
                        votes[t] = votes.get(t, 0) + 1
                if votes:
                    # A tie goes to the tab of the item 26.3 lists before it (copper lanterns follow the soul
                    # lantern, not the sea lantern), else the first by name.
                    prev = next((items[j] for j in range(i - 1, -1, -1) if items[j] in known), None)
                    prev_tab = tab_of(rows, ids.index(prev)) if prev else None
                    top = max(votes.values())
                    tied = sorted(t for t, v in votes.items() if v == top)
                    tab = prev_tab if prev_tab in tied else tied[0]
                    at = 1 + max(j for j, x in enumerate(ids)
                                 if shared_ending(item, x) >= best and (original.get(x) or tab_of(rows, j)) == tab)
                else:
                    # No kind: after the item 26.3 lists before it, in that item's tab (Building Blocks in 26.3's).
                    prev = next((items[j] for j in range(i - 1, -1, -1) if items[j] in known), None)
                    if prev is None:
                        continue
                    at = ids.index(prev) + 1
                    tab = 'BUILDING_BLOCKS' if name == 'building_blocks' else tab_of(rows, ids.index(prev))
                    if tab == '-':
                        continue
            rows.insert(at, [item, tab, 'new'])
            ids.insert(at, item)
            known.add(item)
            added.append(item)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(header + [' '.join(r) for r in rows]) + '\n')
    print("vanilla_order.txt: %d items added from %s's creative order" % (len(added), VERSION))
    return added


def tab_of(rows, j):
    return rows[j][1]


def main():
    global VERSION
    if len(sys.argv) > 2 and sys.argv[1] == '--version':
        VERSION = sys.argv[2]
        del sys.argv[1:3]
    if len(sys.argv) not in (3, 4):
        sys.exit(__doc__)
    tabs = read_oracle(sys.argv[1])
    names = [t[0] for t in tabs]
    for tab, item, after in INSERTS:
        items = tabs[names.index(tab)][2]
        if item not in items:
            items.insert(items.index(after) + 1, item)
    for _, homes in HOMES:
        for h in homes:
            if h not in names:
                sys.exit('no %s tab %s' % (VERSION, h))
    v = VERSION
    flag = '' if v == '26.3' else ' --version ' + v
    out = [('# Generated by tools/creative/build_order_263.py%s from %s\'s CreativeModeTabs (tools/creative/CreativeOrder%s.java).'
            % (flag, v, v.replace('.', ''))),
           '# "home <1.12.2 tab> <%s tabs...>": the %s tabs that 1.12.2 tab reads first, then the rest in order.' % (v, v),
           '# "alias <id> <%s id>": an id %s renamed. "tab <%s tab>": that tab\'s items follow, in %s\'s order.' % (v, v, v, v)]
    for tab, homes in HOMES:
        out.append(' '.join(['home', tab] + homes))
    for old, new in ALIASES:
        out.append('alias %s %s' % (old, new))
    for name, _, items in tabs:
        out.append('tab ' + name)
        out.extend(items)
    with open(sys.argv[2], 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(out) + '\n')
    print('%d tabs, %d entries' % (len(tabs), sum(len(t[2]) for t in tabs)))
    if len(sys.argv) == 4:
        for item in complete_vanilla_order(sys.argv[3], tabs):
            print('  ' + item)


if __name__ == '__main__':
    main()

"""Builds the two tables behind Core's creative tab placement (creative/CreativePlacement):

  src/main/resources/assets/backportcore/creative/vanilla_order.txt
      Every vanilla item in Minecraft 1.19.2's creative order, with its 1.19.2 tab, and every later
      item slotted in where Mojang's later item registry puts it.
  src/main/resources/assets/backportcore/creative/legacy_items.txt
      1.12.2 item id (and metadata) -> today's item id, so a 1.12.2 stack can be found in that order.

Why 1.19.2: it is the last version with 1.12's tab set (Building Blocks, Decoration Blocks, Redstone,
Transportation, Miscellaneous, Foodstuffs, Tools, Combat, Brewing; Materials is Miscellaneous), and up to
1.19.2 a tab lists its items in item registry order, so the order of `Items` is the order of every tab.

Later items (1.19.3 onward have no such tabs): each one is inserted right after the item that comes
before it in a newer `Items` (26.1, unobfuscated), which keeps Mojang's kinds together (cherry planks with
the planks, cherry doors with the doors). Its tab is the tab of the 1.19.2 item it follows. Then:
  - the newer woods go in the order today's tabs show them: after mangrove, cherry, pale oak, bamboo
    (the registry files cherry after acacia);
  - newer spawn eggs go where the alphabet puts them, as 1.19.2 lists its eggs by mob name;
  - items newer versions append at the very end of the registry are given a place by hand (HAND_PLACED),
    from where today's creative tabs show them.

Inputs:
  1. 1.19.2's `net/minecraft/world/item/Items.java`, decompiled with Mojang's names.
  2. `javap -c -p` of 26.1's `net/minecraft/world/item/Items.class`.
  3. `javap -c -p -constants` of 26.1's `net/minecraft/util/datafix/fixes/ItemStackTheFlatteningFix.class`
     (the Flattening's item table, `minecraft:stone.1` -> `minecraft:granite` and so on).

Usage:
  python -I tools/creative/build_tables.py <Items.java> <items261.javap> <flattening.javap> <out dir>
"""
import re
import sys

# 1.12.2 ids the Flattening table leaves alone but a later version renamed (DataFixers' rename fixes and the
# 1.14 dye split). Applied after the table; a key with metadata wins over one without.
RENAMES = {
    'minecraft:melon': 'minecraft:melon_slice',
    'minecraft:melon_block': 'minecraft:melon',
    'minecraft:speckled_melon': 'minecraft:glistering_melon_slice',
    'minecraft:stone_slab.0': 'minecraft:smooth_stone_slab',
    'minecraft:sign': 'minecraft:oak_sign',
    'minecraft:grass_path': 'minecraft:dirt_path',
    'minecraft:dye.1': 'minecraft:red_dye',
    'minecraft:dye.2': 'minecraft:green_dye',
    'minecraft:dye.11': 'minecraft:yellow_dye',
    'minecraft:totem': 'minecraft:totem_of_undying',
    'minecraft:chorus_fruit_popped': 'minecraft:popped_chorus_fruit',
    'minecraft:fish.2': 'minecraft:tropical_fish',
    # In no 1.12.2 tab, but 1.19.3's Spawn Eggs tab lists it (ModernTabs adds the stack).
    'minecraft:mob_spawner': 'minecraft:spawner',
}

# Spawn eggs are one item with an entity in 1.12.2; today each is its own item. Entities renamed since.
EGG_RENAMES = {
    'minecraft:zombie_pigman': 'minecraft:zombified_piglin',
    'minecraft:evocation_illager': 'minecraft:evoker',
    'minecraft:vindication_illager': 'minecraft:vindicator',
    'minecraft:illusion_illager': 'minecraft:illusioner',
    'minecraft:villager_golem': 'minecraft:iron_golem',
    'minecraft:snowman': 'minecraft:snow_golem',
}

# Newer items that the newer registry appends at its end, where "the item before it" says nothing: each is
# placed after a 1.19.2 item (or an earlier entry here), with the tab named. Kept to what the backports add.
# Where today's creative tabs show each, turned into the 1.12 tab set by what the item is.
def _chain(ids, tab, after):
    out = []
    for i in ids:
        out.append((i, tab, after))
        after = i
    return out


_COPPER = ['', 'exposed_', 'weathered_', 'oxidized_']
HAND_PLACED = (
    # Building Blocks: the grates as a run of their own after the chiseled coppers, as the registry keeps each kind
    # of copper block together (today's tab puts the grate after the chiseled copper of each stage).
    _chain(['%scopper_grate' % w for w in _COPPER], 'BUILDING_BLOCKS', 'oxidized_chiseled_copper')
    + _chain(['waxed_%scopper_grate' % w for w in _COPPER], 'BUILDING_BLOCKS', 'waxed_oxidized_chiseled_copper')
    # Redstone: the bulbs after the redstone lamp, as today's Redstone Blocks tab; the crafter after the dropper.
    + _chain(['%scopper_bulb' % w for w in _COPPER] + ['waxed_%scopper_bulb' % w for w in _COPPER], 'REDSTONE', 'redstone_lamp')
    + [('crafter', 'REDSTONE', 'dropper')]
    # Decoration Blocks (today's Functional Blocks): copper chests after the chest, the vault with the end portal
    # frame, the decorated pot after the flower pot.
    + _chain(['%scopper_chest' % w for w in _COPPER] + ['waxed_%scopper_chest' % w for w in _COPPER]
             + ['%scopper_golem_statue' % w for w in _COPPER] + ['waxed_%scopper_golem_statue' % w for w in _COPPER],
             'DECORATIONS', 'chest')
    + [('vault', 'DECORATIONS', 'end_portal_frame'), ('decorated_pot', 'DECORATIONS', 'flower_pot')]
    # Miscellaneous (today's Ingredients): breeze rod and heavy core after the blaze rod; the trial keys after the
    # bottle o' enchanting; copper horse armor after leather; the trial spawner before the first spawn egg, as
    # today's Spawn Eggs tab starts with the spawners.
    + _chain(['breeze_rod', 'heavy_core'], 'MISC', 'blaze_rod')
    + _chain(['trial_key', 'ominous_trial_key'], 'MISC', 'experience_bottle')
    + [('copper_horse_armor', 'MISC', 'leather_horse_armor'), ('trial_spawner', 'MISC', '<allay_spawn_egg')]
    # Combat: the mace after the trident. Foodstuffs (today's Food & Drinks): ominous bottles after honey.
    + [('mace', 'COMBAT', 'trident'), ('ominous_bottle', 'FOOD', 'honey_bottle')]
)

# Field names that are not the item's id (Mojang's typo, kept in every version since).
FIELD_FIXES = {'cut_standstone_slab': 'cut_sandstone_slab'}
# Items a later version renamed: the same item, not a new one.
NEWER_RENAMES = {'short_grass': 'grass', 'iron_chain': 'chain', 'turtle_scute': 'scute'}


def order_1192(path):
    src = open(path, encoding='utf-8').read()
    body = src[src.index('public class Items'):]
    out = []
    for field, expr in re.findall(r'public static final Item (\w+) = (.*?);\s*\n', body, re.S):
        m = re.search(r'register(?:Item|Block)?\(\s*"([a-z0-9_]+)"', expr)
        rid = m.group(1) if m else FIELD_FIXES.get(field.lower(), field.lower())
        t = re.search(r'CreativeModeTab\.TAB_(\w+)', expr)
        tab = t.group(1) if t else '-'
        if tab == 'MATERIALS':
            tab = 'MISC'
        out.append([rid, tab])
    return out


def order_newer(path):
    text = open(path, encoding='utf-8').read()
    text = text[text.index('static {};'):]
    return [FIELD_FIXES.get(f.lower(), f.lower()) for f in re.findall(r'putstatic\s+#\d+\s+// Field ([A-Z0-9_]+):Lnet/minecraft/world/item/Item;', text)]


def flattening(path):
    strings = re.findall(r'// String (minecraft:[a-z0-9_.]+)', open(path, encoding='utf-8').read())
    pairs = []
    i = 0
    while i < len(strings) - 1:
        if re.search(r'\.\d+$', strings[i]) and not re.search(r'\.\d+$', strings[i + 1]):
            pairs.append((strings[i], strings[i + 1]))
            i += 2
        else:
            i += 1
    return pairs


# The newer wood sets in the order every vanilla tab since 1.20 shows them (oak ... dark oak, mangrove, cherry,
# pale oak, bamboo, crimson, warped), not the registry's (which files cherry after acacia): each newer wood's item
# goes after the same item of the wood before it. Bamboo's block, mosaic and raft stand for a log, planks and boat.
NEWER_WOODS = [('cherry', 'mangrove'), ('pale_oak', 'cherry'), ('bamboo', 'pale_oak')]
BAMBOO_KINDS = {'block': 'log', 'raft': 'boat', 'chest_raft': 'chest_boat'}


def reorder_woods(merged, newer):
    ids = [r[0] for r in merged]
    for wood, before in NEWER_WOODS:
        for rid in [i for i in ids if i in newer]:
            kind = None
            for prefix in ('stripped_', ''):
                if rid.startswith(prefix + wood + '_'):
                    kind = (prefix, rid[len(prefix + wood) + 1:])
                    break
            if kind is None or kind[1].startswith('mosaic'):
                continue
            prefix, k = kind
            sibling_kind = BAMBOO_KINDS.get(k, k) if wood == 'bamboo' else k
            if sibling_kind == 'boat':
                # A boat follows the wood before's chest boat; a chest boat its own wood's boat.
                anchor = before + '_chest_boat'
            elif sibling_kind == 'chest_boat':
                anchor = wood + ('_raft' if wood == 'bamboo' else '_boat')
            elif sibling_kind == 'sapling' and before == 'mangrove':
                anchor = 'mangrove_propagule'
            else:
                anchor = prefix + before + '_' + sibling_kind
            if anchor not in ids:
                continue
            row = merged.pop(ids.index(rid))
            ids.remove(rid)
            i = ids.index(anchor) + 1
            merged.insert(i, row)
            ids.insert(i, rid)
    # bamboo mosaic after bamboo planks, its slab and stairs after bamboo's
    for rid, anchor in (('bamboo_mosaic', 'bamboo_planks'), ('bamboo_mosaic_slab', 'bamboo_slab'),
                        ('bamboo_mosaic_stairs', 'bamboo_stairs')):
        if rid in ids and anchor in ids:
            row = merged.pop(ids.index(rid))
            ids.remove(rid)
            i = ids.index(anchor) + 1
            merged.insert(i, row)
            ids.insert(i, rid)


def sort_new_eggs(merged, newer):
    """1.19.2 lists its spawn eggs by mob name, alphabetically; a newer egg goes where the alphabet puts it."""
    for rid in sorted(r[0] for r in merged if r[0] in newer and r[0].endswith('_spawn_egg')):
        row = next(r for r in merged if r[0] == rid)
        merged.remove(row)
        eggs = [i for i, r in enumerate(merged) if r[0].endswith('_spawn_egg')]
        mob = rid[:-len('_spawn_egg')]
        earlier = [i for i in eggs if merged[i][0][:-len('_spawn_egg')] < mob]
        at = (earlier[-1] + 1) if earlier else eggs[0]
        merged.insert(at, row)


def main():
    items_java, newer_javap, flat_javap, out_dir = sys.argv[1:5]
    merged = order_1192(items_java)
    known = {r[0] for r in merged}
    hand = {h[0] for h in HAND_PLACED}
    last = -1
    for nid in order_newer(newer_javap):
        k = NEWER_RENAMES.get(nid, nid)
        if k in known:
            last = next(i for i, r in enumerate(merged) if r[0] == k)
            continue
        if nid in hand:
            continue
        # The tab of the 1.19.2 item (or the newer item placed from one) it follows.
        tab = merged[last][1] if last >= 0 else '-'
        merged.insert(last + 1, [nid, tab])
        known.add(nid)
        last += 1
    before = set()
    for nid, tab, anchor in HAND_PLACED:
        if anchor.startswith('<'):
            # Before the anchor: the item is found by what follows it, not what precedes.
            i = next(i for i, r in enumerate(merged) if r[0] == anchor[1:])
            merged.insert(i, [nid, tab])
            before.add(nid)
        else:
            i = next(i for i, r in enumerate(merged) if r[0] == anchor)
            merged.insert(i + 1, [nid, tab])
        known.add(nid)
    newer = {NEWER_RENAMES.get(n, n) for n in order_newer(newer_javap)} - {r[0] for r in order_1192(items_java)}
    reorder_woods(merged, newer)
    sort_new_eggs(merged, newer)
    with open(out_dir + '/vanilla_order.txt', 'w', newline='\n') as f:
        f.write('# Generated by tools/creative/build_tables.py. One item per line, in creative order:\n')
        f.write('# <today\'s id> <tab> [new]   tab = its 1.19.2 tab (- for none); "new" = added after 1.19.2.\n')
        for rid, tab in merged:
            f.write('minecraft:%s %s%s\n' % (rid, tab, ' new' if rid in newer else ''))
    with open(out_dir + '/legacy_items.txt', 'w', newline='\n') as f:
        f.write('# Generated by tools/creative/build_tables.py: 1.12.2 id[.meta] -> today\'s id.\n')
        f.write('# Ids not listed are the same today. Spawn eggs: "egg <1.12.2 entity> <today\'s entity>".\n')
        table = dict(flattening(flat_javap))
        table.update(RENAMES)
        for k, v in table.items():
            if k != v:
                f.write('%s %s\n' % (k, v))
        for k, v in EGG_RENAMES.items():
            f.write('egg %s %s\n' % (k, v))
    print(len(merged), 'items,', len(table), 'legacy keys')


if __name__ == '__main__':
    main()

"""Compare overworld dumps: vanilla's (Oracle1132 / Oracle1144 / Oracle1152 / Oracle1165OW) against ours
(Backport Core's OverworldDump), or one vanilla version against another.

Usage: python -I compare_overworld.py <expected dir> <actual dir> [--examples N] [--only kind,kind]
                                     [--skip kind,kind] [--fold-oceans] [--fold-bamboo]
--fold-oceans maps every ocean biome (all temperatures, deep or not) to one id on both sides, to judge a
world without Update Aquatic (which owns them) on everything else.
--fold-bamboo maps the bamboo jungle (hills) to the jungle (hills) on both sides, to judge a world without
Village & Pillage (which owns them) on everything else.

Both directories hold seed_<seed>.txt files. A file may start with 'biomeid <local id> <registry name>'
lines (ours do): its grids then use local ids, mapped here to vanilla's numeric ids. Vanilla's own biomes keep
their numbers in 1.12.2, so 'minecraft:*' maps to itself; the backports' biomes map by name.
Prints, per seed, the mismatching cells and structure starts, and a total. Exit status 1 if anything differs.
"""
import os
import sys

# Biomes the backports add, by the last part of their registry name, to vanilla's 1.13/1.14 ids.
MODDED = {
    'warm_ocean': 44, 'lukewarm_ocean': 45, 'cold_ocean': 46, 'deep_warm_ocean': 47,
    'deep_lukewarm_ocean': 48, 'deep_cold_ocean': 49, 'deep_frozen_ocean': 50, 'frozen_ocean': 10,
    'bamboo_jungle': 168, 'bamboo_jungle_hills': 169,
}


# Every ocean, deep or not, one id: without the temperature layer a deep warm ocean (1.13: shallow warm) cannot match.
FOLD = {10: 0, 24: 0, 44: 0, 45: 0, 46: 0, 47: 0, 48: 0, 49: 0, 50: 0}


# 1.14's bamboo jungle and its hills, as the jungle and jungle hills.
FOLD_BAMBOO = {168: 21, 169: 22}


def fold(d, table=None):
    table = FOLD if table is None else table
    for k in ('biome4', 'block4'):
        if d[k]:
            x0, z0, w, h, grid = d[k]
            d[k] = (x0, z0, w, h, [[table.get(v, v) for v in row] for row in grid])
    return d


def load(path):
    d = {'biome4': None, 'block4': None, 'starts': set(), 'stronghold': [], 'spawnbiome': None, 'slime': set()}
    ids = {}
    with open(path, encoding='utf-8') as f:
        lines = f.read().splitlines()
    i = 0
    while i < len(lines):
        p = lines[i].split()
        i += 1
        if not p or p[0].startswith('#'):
            continue
        k = p[0]
        if k == 'biomeid':
            local, name = int(p[1]), p[2]
            if name.startswith('minecraft:'):
                ids[local] = local
            else:
                ids[local] = MODDED.get(name.split(':')[1], 1000 + local)
        elif k in ('biome4', 'block4'):
            w, h = int(p[3]), int(p[4])
            grid = []
            for _ in range(h):
                grid.append([int(v) for v in lines[i].split()])
                i += 1
            d[k] = (int(p[1]), int(p[2]), w, h, grid)
        elif k == 'start':
            d['starts'].add((p[1], int(p[2]), int(p[3])))
        elif k == 'stronghold':
            d['stronghold'].append((int(p[1]), int(p[2])))
        elif k == 'spawnbiome':
            d['spawnbiome'] = tuple(p[1:])
        elif k == 'slime':
            d['slime'].add((int(p[1]), int(p[2])))
    if ids:
        for k in ('biome4', 'block4'):
            if d[k]:
                x0, z0, w, h, grid = d[k]
                d[k] = (x0, z0, w, h, [[ids.get(v, 1000 + v) for v in row] for row in grid])
    return d


def main():
    exp_dir, act_dir = sys.argv[1], sys.argv[2]
    examples = int(sys.argv[sys.argv.index('--examples') + 1]) if '--examples' in sys.argv else 5
    only = set(sys.argv[sys.argv.index('--only') + 1].split(',')) if '--only' in sys.argv else None
    skip = set(sys.argv[sys.argv.index('--skip') + 1].split(',')) if '--skip' in sys.argv else set()
    folding = '--fold-oceans' in sys.argv
    bamboo = '--fold-bamboo' in sys.argv
    total = 0
    seeds = sorted(f for f in os.listdir(exp_dir) if f.startswith('seed_'))
    for name in seeds:
        ap = os.path.join(act_dir, name)
        if not os.path.exists(ap):
            print(name, 'missing on the actual side')
            total += 1
            continue
        e, a = load(os.path.join(exp_dir, name)), load(ap)
        if folding:
            e, a = fold(e), fold(a)
        if bamboo:
            e, a = fold(e, FOLD_BAMBOO), fold(a, FOLD_BAMBOO)
        print('==', name)
        for k in ('biome4', 'block4'):
            if only and k not in only:
                continue
            if not e[k] or not a[k]:
                print('  %s: missing' % k)
                total += 1
                continue
            x0, z0, w, h, ge = e[k]
            ax0, az0, aw, ah, ga = a[k]
            if (x0, z0, w, h) != (ax0, az0, aw, ah):
                print('  %s: different areas' % k)
                total += 1
                continue
            step = 1 if k == 'biome4' else 4
            bad = [(x0 + x * step, z0 + z * step, ge[z][x], ga[z][x]) for z in range(h) for x in range(w) if ge[z][x] != ga[z][x]]
            pairs = {}
            for b in bad:
                pairs[(b[2], b[3])] = pairs.get((b[2], b[3]), 0) + 1
            top = sorted(pairs.items(), key=lambda t: -t[1])[:6]
            print('  %s: %d of %d differ%s' % (k, len(bad), w * h, (' (expected->actual: ' + ', '.join('%d->%d x%d' % (p[0], p[1], c) for p, c in top) + ')') if bad else ''))
            for b in bad[:examples]:
                print('    at %d %d: expected %d, got %d' % b)
            total += len(bad)
        kinds = sorted({s[0] for s in e['starts'] | a['starts']})
        for kind in kinds:
            if (only and kind not in only) or kind in skip:
                continue
            se = {s for s in e['starts'] if s[0] == kind}
            sa = {s for s in a['starts'] if s[0] == kind}
            miss, extra = sorted(se - sa), sorted(sa - se)
            print('  start %-16s expected %3d, got %3d, missing %d, extra %d' % (kind, len(se), len(sa), len(miss), len(extra)))
            for s in miss[:examples]:
                print('    missing chunk %d %d' % (s[1], s[2]))
            for s in extra[:examples]:
                print('    extra chunk %d %d' % (s[1], s[2]))
            total += len(miss) + len(extra)
        if not only or 'stronghold' in only:
            bad = [i for i in range(max(len(e['stronghold']), len(a['stronghold'])))
                   if i >= len(e['stronghold']) or i >= len(a['stronghold']) or e['stronghold'][i] != a['stronghold'][i]]
            print('  strongholds: %d of %d differ' % (len(bad), len(e['stronghold'])))
            for i in bad[:examples]:
                print('    #%d expected %s, got %s' % (i, e['stronghold'][i] if i < len(e['stronghold']) else None,
                                                     a['stronghold'][i] if i < len(a['stronghold']) else None))
            total += len(bad)
        if not only or 'spawnbiome' in only:
            same = e['spawnbiome'] == a['spawnbiome']
            print('  spawn biome search: expected %s, got %s%s' % (e['spawnbiome'], a['spawnbiome'], '' if same else '  DIFFERENT'))
            total += 0 if same else 1
        if not only or 'slime' in only:
            d = len(e['slime'] ^ a['slime'])
            print('  slime chunks: %d expected, %d differ' % (len(e['slime']), d))
            total += d
    print('TOTAL differences: %d' % total)
    sys.exit(1 if total else 0)


if __name__ == '__main__':
    main()

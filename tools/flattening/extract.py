"""Builds src/main/resources/assets/backportcore/structure/flattening.txt: Minecraft's own table of
1.12.2 block states and the 1.13 states they became ("the Flattening").

The structure template loader (structure.ModernPalette) reads it backwards, to turn the block
states in a 1.13-or-later structure file into 1.12.2 ones.

Where the table comes from: every Minecraft since 1.13 still carries it, as the data fixer
`net.minecraft.util.datafix.fixes.BlockStateData`. Minecraft 26.1 and later ship without
obfuscation, so the class is found by name. This script reads `javap -c -p -constants` output of
that class and replays its static initializer and `bootstrap*` methods, which are long runs of

    register(id, create("minecraft:oak_stairs", Map.of(...)), new Dynamic[]{ create(...), ... })

Usage (any JDK's javap; the jar is a vanilla client or server jar of 26.1 or later):

    unzip <client.jar> net/minecraft/util/datafix/fixes/BlockStateData.class -d bsd
    javap -c -p -constants bsd/net/minecraft/util/datafix/fixes/BlockStateData.class > bsd.txt
    python -I tools/flattening/extract.py bsd.txt src/main/resources/assets/backportcore/structure/flattening.txt

Output, one line per 1.12.2 id:meta, in Mojang's order:

    <1.13 state> TAB <1.12.2 state> [TAB <1.12.2 state> ...]

A state is `name` or `name[key=value,...]`. The first 1.13 state is the one that id:meta became;
the 1.12.2 states after it are every combination 1.12.2 could show for it (stairs' shapes, fence
connections), so a reader can pick the one closest to a template's properties.

A few 1.13 snapshots renamed blocks after the table was written (`portal` to `nether_portal` and so
on). Those renames are applied here, so the left column is always 1.13.2's name.
"""
import re
import sys

# 1.13 snapshot renames made after the flattening table (DataFixers schemas 1460-1490 and
# EntityTheRenameningFix), applied to the 1.13 side.
SNAPSHOT_RENAMES = {
    'minecraft:portal': 'minecraft:nether_portal',
    'minecraft:mob_spawner': 'minecraft:spawner',
    'minecraft:melon_block': 'minecraft:melon',
}
# 1.13 folded flowing_water and flowing_lava into water and lava. Their ids are left out, so a
# file's water reads back as 1.12.2's still water, which is what generated water is.
SKIPPED_LEGACY = ('minecraft:flowing_water', 'minecraft:flowing_lava')
for _wood in ('oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak'):
    SNAPSHOT_RENAMES['minecraft:%s_bark' % _wood] = 'minecraft:%s_wood' % _wood
    SNAPSHOT_RENAMES['minecraft:stripped_%s_bark' % _wood] = 'minecraft:stripped_%s_wood' % _wood

CODE = re.compile(r'^\s+\d+: ')
HEADER = re.compile(r'^  (?:private |public |protected )?(?:static )?.*?(\w+)\((.*)\);$')
INS = re.compile(r'^(\d+): (\S+)\s*(.*)$')


class State:
    def __init__(self, name, props):
        self.name = name
        self.props = props

    def text(self, rename=False):
        name = SNAPSHOT_RENAMES.get(self.name, self.name) if rename else self.name
        if not self.props:
            return name
        return name + '[' + ','.join('%s=%s' % kv for kv in sorted(self.props.items())) + ']'


def methods_of(lines):
    methods = {}
    cur = None
    for line in lines:
        if line.startswith('  static {};'):
            cur = '<clinit>'
            methods[cur] = []
            continue
        if CODE.match(line):
            if cur is not None:
                methods[cur].append(line.strip())
            continue
        m = HEADER.match(line)
        if m:
            cur = m.group(1)
            methods[cur] = []
    return methods


def main(src_path, out_path):
    methods = methods_of(open(src_path, encoding='utf-8', errors='replace').read().split('\n'))
    statics = {}
    pairs = []

    def run(name):
        stack = []
        for ins in methods[name]:
            m = INS.match(ins)
            if not m:
                continue
            op, rest = m.group(2), m.group(3)
            if op.startswith('iconst_'):
                v = op[7:]
                stack.append(-1 if v == 'm1' else int(v))
            elif op in ('bipush', 'sipush'):
                stack.append(int(rest.split()[0]))
            elif op in ('ldc', 'ldc_w'):
                c = rest.split('//', 1)[1].strip()
                if c.startswith('String '):
                    stack.append(c[7:])
                elif c == 'String':
                    stack.append('')
                elif c.startswith('int '):
                    stack.append(int(c[4:]))
                else:
                    raise ValueError('ldc ' + c)
            elif op == 'getstatic':
                stack.append(statics.get(re.search(r'Field (\w+):', rest).group(1)))
            elif op == 'putstatic':
                statics[re.search(r'Field (\w+):', rest).group(1)] = stack.pop() if stack else None
            elif op == 'anewarray':
                stack.append([None] * stack.pop())
            elif op == 'dup':
                stack.append(stack[-1])
            elif op == 'aastore':
                v = stack.pop()
                i = stack.pop()
                stack.pop()[i] = v
            elif op.startswith('invoke'):
                sig = rest.split('//', 1)[1].strip()
                if 'java/util/Map.of:' in sig:
                    n = sig.split(')')[0].count('Ljava/lang/Object;')
                    args = stack[len(stack) - n:]
                    del stack[len(stack) - n:]
                    stack.append({args[i]: args[i + 1] for i in range(0, n, 2)})
                elif 'java/util/Map.ofEntries' in sig:
                    stack.append(dict(stack.pop()))
                elif 'java/util/Map.entry' in sig:
                    v = stack.pop()
                    stack.append((stack.pop(), v))
                elif 'Method create:(Ljava/lang/String;)' in sig:
                    stack.append(State(stack.pop(), {}))
                elif 'Method create:(Ljava/lang/String;Ljava/util/Map;)' in sig:
                    props = stack.pop()
                    stack.append(State(stack.pop(), props))
                elif 'Method register:' in sig:
                    olds = stack.pop()
                    new = stack.pop()
                    pairs.append((stack.pop(), new, olds))
                elif re.search(r'Method bootstrap\w*:', sig):
                    pass
                elif name == '<clinit>':
                    stack.clear()
                else:
                    raise ValueError('unexpected call in %s: %s' % (name, sig))
            elif op == 'new':
                stack.append(('new', rest))
            elif op == 'pop':
                if stack:
                    stack.pop()
            elif op in ('return', 'checkcast'):
                pass
            elif name != '<clinit>':
                raise ValueError('unexpected instruction %s in %s' % (op, name))

    run('<clinit>')
    for name in sorted(methods):
        if name.startswith('bootstrap'):
            run(name)

    for _, new, olds in pairs:
        if not isinstance(new.props, dict) or any(not isinstance(o.props, dict) for o in olds):
            raise ValueError('a property map was not resolved: ' + new.name)

    with open(out_path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('# The Flattening: 1.13.2 block state, then the 1.12.2 states it was made from.\n')
        f.write('# Generated by tools/flattening/extract.py from Minecraft\'s BlockStateData. Do not edit.\n')
        for _, new, olds in pairs:
            if olds and olds[0].name in SKIPPED_LEGACY:
                continue
            f.write('\t'.join([new.text(True)] + [o.text() for o in olds]) + '\n')
    print('%d ids, %d 1.12.2 states' % (len(pairs), sum(len(o) for _, _, o in pairs)))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])

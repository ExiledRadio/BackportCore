#!/usr/bin/env python3
"""
generate.py - writes Backport Core's supplied-block table and assets from today's Minecraft jar
(docs/PLAN_MODERN_BLOCKS.md, "Where the blocks come from").

    python -I tools/supplied-blocks/generate.py <client.jar> [--javap <javap.exe>] [--lang <MojangLang/26.3/lang>]

Reads, from the jar (26.x jars are unobfuscated):
  * net/minecraft/world/level/block/Blocks.class, through the JDK's javap: every block's Properties
    (strength, sound, light, map color, requiresCorrectToolForDrops, noOcclusion) and its class. The class
    initializer is interpreted: helper methods (registerSlab, logProperties, ...), Properties.ofFullCopy /
    ofLegacyCopy of an earlier block and the copper collections are followed.
  * each block class's superclasses (javap -p), which give the shape class (cube, pillar, slab, stairs, chain,
    grate).
  * data/minecraft/tags/block/mineable/*.json and needs_*_tool.json: tool and harvest level.
  * data/minecraft/loot_table/blocks/<name>.json: what the block drops without silk touch.
  * assets/minecraft/blockstates, models, items and textures: converted to 1.12's layout under
    assets/backportcore/{blockstates,models/block/modern,models/item,textures/blocks/modern,textures/items/modern}.

Writes:
  src/main/resources/assets/backportcore/supplied/blocks.txt   the table (one row per block, tab-separated)
  the assets above, and tile.backportcore.<name>.name lines in en_us.lang and, with --lang, the other languages
  (Mojang's own names), each in a generated block at the end of the file.

Which blocks: tools/supplied-blocks/names.txt (one modern name per line). A name whose shape class Core does not
have is skipped and listed. Owners (the backport that has the block, and its name there) come from owners.txt.
"""
import io
import json
import os
import re
import subprocess
import sys
import tempfile
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
RES = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'backportcore')

# ---- javap

def javap(exe, classfile, verbose=False):
    args = [exe, '-c', '-p'] + (['-v'] if verbose else []) + [classfile]
    return subprocess.run(args, capture_output=True, text=True, encoding='utf-8', errors='replace').stdout

METHOD_HEAD = re.compile(r'^  (?:[\w$<>,.\[\]? ]+ )?([\w$<>]+)\((.*)\);$')
INSN = re.compile(r'^\s+(\d+): ([a-z_0-9]+)\s*(.*)$')
CASE = re.compile(r'^\s+(\d+|default): (\d+)$')

def methods(text):
    """name -> list of (offset, op, rest) for each method of a javap -c listing; a switch's cases follow it as
    (None, 'case', (key, target))."""
    out = {}
    cur = None
    for line in text.splitlines():
        if line.startswith('  static {};'):
            cur = out.setdefault('<clinit>', [])
            continue
        m = METHOD_HEAD.match(line)
        if m and not line.startswith('   '):
            cur = out.setdefault(m.group(1), [])
            continue
        if cur is None:
            continue
        m = CASE.match(line)
        if m and cur and cur[-1][1] in ('tableswitch', 'lookupswitch', 'case'):
            cur.append((None, 'case', (m.group(1), int(m.group(2)))))
            continue
        m = INSN.match(line)
        if m:
            cur.append((int(m.group(1)), m.group(2), m.group(3)))
    return out

def bootstraps(text):
    """bootstrap index -> implementation handle text ('REF_newInvokeSpecial a/b/C."<init>":(...)V' and the like)."""
    out = {}
    lines = text.splitlines()
    try:
        start = lines.index('BootstrapMethods:')
    except ValueError:
        return out
    i = start + 1
    while i < len(lines):
        m = re.match(r'^  (\d+): ', lines[i])
        if m:
            idx = int(m.group(1))
            args = []
            j = i + 2
            while j < len(lines) and lines[j].startswith('      #'):
                args.append(lines[j].strip())
                j += 1
            out[idx] = args[1] if len(args) > 1 else ''
            i = j
        elif lines[i] and not lines[i].startswith(' '):
            break
        else:
            i += 1
    return out

def arg_count(desc):
    """Number of arguments of a method descriptor's parameter list '(FFLx;)...'."""
    m = re.match(r'\(([^)]*)\)', desc)
    if not m:
        return 0
    s, n, i = m.group(1), 0, 0
    while i < len(s):
        c = s[i]
        if c == '[':
            i += 1
            continue
        if c == 'L':
            i = s.index(';', i)
        n += 1
        i += 1
    return n

def returns_value(desc):
    return not desc.endswith(')V')

# ---- the interpreter

class Props(dict):
    pass

class Lambda:
    def __init__(self, handle, captured):
        self.handle = handle
        self.captured = captured

    def target(self):
        m = re.search(r'REF_(\w+) ([\w/$]+)\.([\w$<>"]+):(\S+)', self.handle)
        return m.groups() if m else (None, None, None, None)

class Interp:
    def __init__(self, mlist, boots, consts):
        self.m = mlist
        self.boots = boots
        self.consts = consts
        self.fields = {}     # Blocks field name -> block record
        self.blocks = {}     # modern name -> record
        self.depth = 0

    def run(self, name, locals_):
        stack = []
        code = self.m.get(name, [])
        at = {c[0]: i for i, c in enumerate(code) if c[0] is not None}
        self.depth += 1
        i = 0
        steps = 0
        try:
            while i < len(code) and steps < 200000:
                steps += 1
                off, op, rest = code[i]
                if op == 'case':
                    i += 1
                    continue
                if op in ('tableswitch', 'lookupswitch'):
                    # The first case: a weather state's unaffected copper, the lowest ordinal.
                    if stack:
                        stack.pop()
                    cases = []
                    j = i + 1
                    while j < len(code) and code[j][1] == 'case':
                        cases.append(code[j][2])
                        j += 1
                    keyed = [c for c in cases if c[0] != 'default']
                    target = (keyed[0] if keyed else cases[0])[1]
                    i = at.get(target, j)
                    continue
                if op == 'goto' or op == 'goto_w':
                    i = at.get(int(rest.split()[0]), i + 1)
                    continue
                r = self.step(op, rest, stack, locals_)
                if r is not None:
                    return r[0]
                i += 1
        finally:
            self.depth -= 1
        return None

    def comment(self, rest):
        return rest.split('// ', 1)[1] if '// ' in rest else ''

    def step(self, op, rest, st, loc):
        c = self.comment(rest)
        if op in ('areturn', 'ireturn', 'freturn'):
            return (st.pop() if st else None,)
        if op == 'return':
            return (None,)
        if op.startswith('aload') or op.startswith('fload') or op.startswith('iload'):
            idx = int(op.split('_')[1]) if '_' in op else int(rest.split()[0])
            st.append(loc[idx] if idx < len(loc) else None)
            return None
        if op.startswith('astore') or op.startswith('fstore') or op.startswith('istore'):
            idx = int(op.split('_')[1]) if '_' in op else int(rest.split()[0])
            while len(loc) <= idx:
                loc.append(None)
            loc[idx] = st.pop()
            return None
        if op == 'fconst_0': st.append(0.0); return None
        if op == 'fconst_1': st.append(1.0); return None
        if op == 'fconst_2': st.append(2.0); return None
        if op.startswith('iconst_'):
            v = op[7:]
            st.append(-1 if v == 'm1' else int(v)); return None
        if op in ('bipush', 'sipush'):
            st.append(int(rest.split()[0])); return None
        if op in ('ldc', 'ldc_w', 'ldc2_w'):
            m = re.search(r'(float|int|double|long) ([-\d.eE]+)', c)
            if m:
                st.append(float(m.group(2).rstrip('fdl')) if m.group(1) in ('float', 'double') else int(m.group(2).rstrip('l')))
            else:
                m = re.search(r'String (.*)$', c)
                st.append(m.group(1) if m else None)
            return None
        if op == 'dup':
            st.append(st[-1]); return None
        if op == 'pop':
            st.pop(); return None
        if op == 'new':
            st.append(('new', c.replace('class ', '').strip())); return None
        if op == 'getstatic':
            m = re.search(r'Field (?:([\w/$]+)\.)?([\w$]+):', c)
            if not m:
                st.append(None)
                return None
            owner, field = (m.group(1) or 'Blocks'), m.group(2)
            if owner == 'Blocks' or owner.endswith('/Blocks'):
                st.append(self.fields.get(field, ('field', field)))
            elif owner.endswith('BlockItemIds'):
                st.append(('id', field.lower()))
            else:
                st.append((owner.split('/')[-1], field))
            return None
        if op == 'putstatic':
            m = re.search(r'Field (?:([\w/$]+)\.)?([\w$]+):', c)
            v = st.pop() if st else None
            if not m:
                return None
            self.fields[m.group(2)] = v
            return None
        if op == 'invokedynamic':
            idx = int(re.search(r'InvokeDynamic #(\d+):', c).group(1))
            desc = c.split(':', 2)[2] if c.count(':') >= 2 else '()'
            n = arg_count(desc)
            cap = [st.pop() for _ in range(n)][::-1]
            st.append(Lambda(self.boots.get(idx, ''), cap))
            return None
        if op in ('invokestatic', 'invokevirtual', 'invokespecial', 'invokeinterface'):
            m = re.search(r'(?:Method|InterfaceMethod) (?:([\w/$]+)\.)?([\w$<>"]+):(\S+)', c)
            owner, name, desc = (m.group(1) or 'Blocks'), m.group(2), m.group(3)
            n = arg_count(desc)
            args = [st.pop() for _ in range(n)][::-1]
            recv = st.pop() if op != 'invokestatic' else None
            out = self.call(op, owner, name, desc, recv, args)
            if returns_value(desc) and op != 'invokespecial':
                st.append(out)
            return None
        if op in ('checkcast', 'nop', 'goto', 'i2f', 'f2i', 'i2l'):
            return None
        if op == 'aconst_null':
            st.append(None); return None
        if op in ('anewarray', 'newarray', 'getfield', 'instanceof', 'arraylength'):
            st.pop(); st.append(None); return None
        if op in ('aastore', 'iastore', 'fastore'):
            del st[-3:]; return None
        if op in ('iadd', 'isub', 'imul', 'idiv', 'fadd', 'fsub', 'fmul', 'fdiv', 'aaload', 'iaload', 'fcmpl', 'fcmpg', 'iand', 'ior'):
            del st[-2:]; st.append(0); return None
        if op in ('ifeq', 'ifne', 'iflt', 'ifge', 'ifgt', 'ifle', 'ifnull', 'ifnonnull', 'athrow', 'putfield'):
            if op == 'putfield':
                del st[-2:]
            elif st:
                st.pop()
            return None
        if op.startswith('if_'):
            del st[-2:]; return None
        # Anything else (branches, arithmetic) does not appear in the block registrations we read.
        return None

    def call(self, op, owner, name, desc, recv, args):
        short = owner.split('/')[-1]
        if short == 'BlockBehaviour$Properties':
            if name == 'of':
                return Props()
            if name in ('ofFullCopy', 'ofLegacyCopy'):
                base = args[0]
                p = Props(base['props']) if isinstance(base, dict) and 'props' in base else Props()
                return p
            p = recv if isinstance(recv, Props) else Props()
            if name == 'strength':
                p['hardness'] = args[0]
                p['resistance'] = args[1] if len(args) > 1 else args[0]
            elif name == 'destroyTime':
                p['hardness'] = args[0]
            elif name == 'explosionResistance':
                p['resistance'] = args[0]
            elif name == 'instabreak':
                p['hardness'] = 0.0
                p['resistance'] = 0.0
            elif name == 'sound':
                p['sound'] = args[0][1] if isinstance(args[0], tuple) else 'STONE'
            elif name == 'mapColor':
                a = args[0]
                if isinstance(a, tuple) and a[0] in ('MapColor', 'DyeColor'):
                    p['mapcolor'] = ('DYE_' if a[0] == 'DyeColor' else '') + a[1]
                elif isinstance(a, Lambda) and a.captured and isinstance(a.captured[-1], tuple):
                    p['mapcolor'] = a.captured[-1][1]
            elif name == 'lightLevel':
                p['light'] = self.light(args[0])
            elif name == 'requiresCorrectToolForDrops':
                p['tool_required'] = True
            elif name in ('noOcclusion', 'forceSolidOn', 'noCollision', 'replaceable', 'ignitedByLava', 'noLootTable'):
                p[name] = True
            return p
        if short == 'Block' and name == 'defaultMapColor':
            return ('MapColor', recv['props'].get('mapcolor', 'STONE')) if isinstance(recv, dict) else ('MapColor', 'STONE')
        if short == 'BlockItemId' and name == 'block':
            return recv
        if short == 'WeatheringCopperCollection' and name == 'registerBlocks':
            return self.copper(args)
        if short.startswith('WeatheringCopperCollection'):
            # unaffected(), weathering(), waxed(): the collection stands for its unaffected block.
            return recv
        if short == 'Blocks' or owner == 'Blocks':
            if name == 'register':
                return self.register(args)
            if name in self.m and self.depth < 6:
                return self.run(name, list(args))
        return None

    def light(self, fn):
        if isinstance(fn, Lambda):
            kind, cls, meth, d = fn.target()
            if meth and meth in self.m:
                for _o, op, rest in self.m[meth]:
                    if op.startswith('iconst_'):
                        return int(op[7:]) if op[7:] != 'm1' else 0
                    if op == 'bipush':
                        return int(rest.split()[0])
                    if op.startswith('iload'):
                        # litBlockEmission(n): the captured int
                        return fn.captured[0] if fn.captured and isinstance(fn.captured[0], int) else 0
        return 0

    def ctor_class(self, fn):
        """The class a block factory (Function<Properties, Block>, or a copper BiFunction) builds."""
        if not isinstance(fn, Lambda):
            return 'Block'
        kind, cls, meth, d = fn.target()
        if kind == 'newInvokeSpecial':
            return cls.split('/')[-1]
        if meth in self.m:
            for _o, op, rest in self.m[meth]:
                if op == 'new':
                    return self.comment(rest).replace('class ', '').strip().split('/')[-1]
        return 'Block'

    def register(self, args):
        ident = next((a for a in args if isinstance(a, tuple) and a[0] == 'id'), None)
        props = next((a for a in args if isinstance(a, Props)), Props())
        fn = next((a for a in args if isinstance(a, Lambda)), None)
        if ident is None:
            return None
        rec = {'name': ident[1], 'cls': self.ctor_class(fn) if fn else 'Block', 'props': Props(props)}
        self.blocks[rec['name']] = rec
        return rec

    def copper(self, args):
        ids, _register, waxed, weathering, propsfn = args
        base = ids[1] if isinstance(ids, tuple) else None
        if not base:
            return None
        kind, cls, meth, d = propsfn.target() if isinstance(propsfn, Lambda) else (None,) * 4
        props = self.run(meth, [None]) if meth else Props()
        stages = ['', 'exposed_', 'weathered_', 'oxidized_']
        colors = ['COLOR_ORANGE', 'TERRACOTTA_LIGHT_GRAY', 'WARPED_NYLIUM', 'WARPED_STEM']
        for i, s in enumerate(stages):
            name = (s + 'copper' if s else 'copper_block') if base == 'copper_block' else s + base
            for wax, fn in (('', weathering), ('waxed_', waxed)):
                p = Props(props or {})
                p['mapcolor'] = colors[i]
                self.blocks[wax + name] = {'name': wax + name, 'cls': self.ctor_class(fn), 'props': p}
        return {'name': base, 'props': Props(props or {})}

# ---- shapes

SHAPES = {
    'StairBlock': 'stairs', 'SlabBlock': 'slab', 'RotatedPillarBlock': 'pillar', 'ChainBlock': 'chain',
    'WaterloggedTransparentBlock': 'grate',
}
NOT_SUPPORTED = {'BaseEntityBlock', 'BushBlock', 'VegetationBlock', 'LeavesBlock', 'WallBlock', 'FenceBlock',
                 'DoorBlock', 'TrapDoorBlock', 'LanternBlock', 'CarpetBlock', 'FallingBlock', 'BrushableBlock',
                 'IronBarsBlock', 'ButtonBlock', 'PressurePlateBlock', 'FenceGateBlock', 'TorchBlock', 'HalfTransparentBlock',
                 'TransparentBlock', 'LiquidBlock', 'MultifaceBlock', 'GrowingPlantBlock'}

def superchain(jar, tmp, exe, cls, cache):
    chain = []
    cur = cls
    while cur and cur not in ('Block', 'Object') and len(chain) < 12:
        if cur in cache:
            nxt = cache[cur]
        else:
            path = 'net/minecraft/world/level/block/%s.class' % cur
            nxt = None
            if path in jar.namelist():
                f = os.path.join(tmp, cur + '.class')
                with open(f, 'wb') as o:
                    o.write(jar.read(path))
                head = subprocess.run([exe, '-p', f], capture_output=True, text=True).stdout
                m = re.search(r' extends ([\w.$]+)', head)
                nxt = m.group(1).split('.')[-1] if m else None
            cache[cur] = nxt
        chain.append(cur)
        cur = nxt
    return chain

def shape_of(chain, props):
    for c in chain:
        if c in SHAPES:
            return SHAPES[c]
        if c in NOT_SUPPORTED:
            return None
    # A plain Block subclass (copper's weathering blocks, Block itself) is a cube when it is a full, opaque block.
    return 'cube' if not props.get('noOcclusion') and not props.get('noCollision') else None

# ---- 1.12.2 terms

SOUNDS_METAL = {'METAL', 'COPPER', 'COPPER_GRATE', 'COPPER_BULB', 'CHAIN', 'ANVIL', 'LANTERN', 'NETHERITE_BLOCK', 'HEAVY_CORE', 'IRON'}
SOUNDS_WOOD = {'WOOD', 'NETHER_WOOD', 'CHERRY_WOOD', 'BAMBOO_WOOD', 'STEM', 'NETHER_STEM'}

def sound_112(s):
    if s in SOUNDS_METAL:
        return 'METAL'
    if s in SOUNDS_WOOD:
        return 'WOOD'
    return {'GLASS': 'GLASS', 'GRAVEL': 'GROUND', 'SAND': 'SAND', 'GRASS': 'PLANT', 'WOOL': 'CLOTH', 'SNOW': 'SNOW'}.get(s, 'STONE')

def material_112(sound):
    return {'METAL': 'IRON', 'WOOD': 'WOOD'}.get(sound_112(sound), 'ROCK')

MAPCOLOR = {
    'NONE': 'AIR', 'GRASS': 'GRASS', 'SAND': 'SAND', 'WOOL': 'CLOTH', 'FIRE': 'TNT', 'ICE': 'ICE', 'METAL': 'IRON',
    'PLANT': 'FOLIAGE', 'SNOW': 'SNOW', 'CLAY': 'CLAY', 'DIRT': 'DIRT', 'STONE': 'STONE', 'WATER': 'WATER', 'WOOD': 'WOOD',
    'QUARTZ': 'QUARTZ', 'GOLD': 'GOLD', 'DIAMOND': 'DIAMOND', 'LAPIS': 'LAPIS', 'EMERALD': 'EMERALD', 'PODZOL': 'OBSIDIAN',
    'NETHER': 'NETHERRACK', 'COLOR_LIGHT_GRAY': 'SILVER', 'TERRACOTTA_LIGHT_GRAY': 'SILVER_STAINED_HARDENED_CLAY',
    # Newer than 1.12.2: the closest 1.12.2 color.
    'CRIMSON_NYLIUM': 'RED_STAINED_HARDENED_CLAY', 'CRIMSON_STEM': 'MAGENTA_STAINED_HARDENED_CLAY',
    'CRIMSON_HYPHAE': 'RED_STAINED_HARDENED_CLAY', 'WARPED_NYLIUM': 'CYAN_STAINED_HARDENED_CLAY', 'WARPED_STEM': 'CYAN',
    'WARPED_HYPHAE': 'PURPLE_STAINED_HARDENED_CLAY', 'WARPED_WART_BLOCK': 'DIAMOND', 'DEEPSLATE': 'STONE',
    'RAW_IRON': 'PINK_STAINED_HARDENED_CLAY', 'GLOW_LICHEN': 'EMERALD',
}

IDS_112 = ('AIR GRASS SAND CLOTH TNT ICE IRON FOLIAGE SNOW CLAY DIRT STONE WATER WOOD QUARTZ ORANGE MAGENTA LIGHT_BLUE '
           'YELLOW LIME PINK GRAY SILVER CYAN PURPLE BLUE BROWN GREEN RED BLACK GOLD DIAMOND LAPIS EMERALD OBSIDIAN '
           'NETHERRACK').split() + [c + '_STAINED_HARDENED_CLAY' for c in 'WHITE ORANGE MAGENTA LIGHT_BLUE YELLOW LIME PINK GRAY '
                                    'SILVER CYAN PURPLE BLUE BROWN GREEN RED BLACK'.split()]

def mapcolor_id(c):
    """1.12.2's MapColor.COLORS index (the same numbers as today's ids 0-51)."""
    return IDS_112.index(mapcolor_112(c))

def mapcolor_112(c):
    if c in MAPCOLOR:
        return MAPCOLOR[c]
    if c.startswith('DYE_'):
        c = 'COLOR_' + c[4:]
    if c.startswith('COLOR_'):
        return c[6:].replace('LIGHT_GRAY', 'SILVER')
    if c.startswith('TERRACOTTA_'):
        return c[11:].replace('LIGHT_GRAY', 'SILVER') + '_STAINED_HARDENED_CLAY'
    return 'STONE'

# ---- data files

def tags(jar):
    def read(t):
        p = 'data/minecraft/tags/block/%s.json' % t
        if p not in jar.namelist():
            return set()
        out = set()
        for v in json.loads(jar.read(p))['values']:
            v = v if isinstance(v, str) else v['id']
            out |= read(v[1:].replace('minecraft:', '')) if v.startswith('#') else {v}
        return out
    tool = {}
    for t in ('pickaxe', 'axe', 'shovel', 'hoe'):
        for n in read('mineable/' + t):
            tool[n.replace('minecraft:', '')] = t
    level = {}
    for lv, t in ((1, 'needs_stone_tool'), (2, 'needs_iron_tool'), (3, 'needs_diamond_tool')):
        for n in read(t):
            level[n.replace('minecraft:', '')] = lv
    return tool, level

def drop(jar, name):
    """The block's drop without silk touch: 'self', 'none', or another block's name."""
    p = 'data/minecraft/loot_table/blocks/%s.json' % name
    if p not in jar.namelist():
        return 'none'
    found = []

    def walk(e, silk):
        if isinstance(e, dict):
            here = silk
            if e.get('type') == 'minecraft:alternatives':
                for i, c in enumerate(e.get('children', [])):
                    walk(c, 'silk' if i == 0 and 'silk_touch' in json.dumps([c.get('conditions'), c.get('condition')]) else 'plain')
                return
            if e.get('type') == 'minecraft:item':
                found.append((here, e['name'].replace('minecraft:', '')))
            for v in e.values():
                walk(v, here)
        elif isinstance(e, list):
            for v in e:
                walk(v, silk)
    walk(json.loads(jar.read(p)), 'plain')
    plain = [n for s, n in found if s != 'silk']
    if not plain:
        return 'none'
    return 'self' if plain[0] == name else plain[0]

# ---- assets

def ref112(ref, kind):
    """minecraft:block/x -> backportcore:block/modern/x (models) or backportcore:blocks/modern/x (textures)."""
    path = ref.split(':', 1)[1] if ':' in ref else ref
    if kind == 'model':
        top, rest = path.split('/', 1)
        return 'backportcore:%s/modern/%s' % (top, rest)
    top, rest = path.split('/', 1)
    return 'backportcore:%s/modern/%s' % ({'block': 'blocks', 'item': 'items'}.get(top, top), rest)

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    mode = 'wb' if isinstance(data, bytes) else 'w'
    with open(path, mode, **({} if mode == 'wb' else {'encoding': 'utf-8', 'newline': '\n'})) as f:
        f.write(data)

def copy_texture(jar, ref, done):
    path = ref.split(':', 1)[1] if ':' in ref else ref
    if path in done:
        return
    done.add(path)
    src = 'assets/minecraft/textures/%s.png' % path
    if src not in jar.namelist():
        print('  missing texture', src)
        return
    top, rest = path.split('/', 1)
    out = os.path.join(RES, 'textures', {'block': 'blocks', 'item': 'items'}.get(top, top), 'modern', rest + '.png')
    data = jar.read(src)
    try:
        from PIL import Image
        img = Image.open(io.BytesIO(data))
        if img.mode != 'RGBA':
            # Keyed (tRNS) and grayscale PNGs draw black in 1.12.2: always RGBA.
            buf = io.BytesIO()
            img.convert('RGBA').save(buf, 'PNG')
            data = buf.getvalue()
    except ImportError:
        pass
    write(out, data)
    if src + '.mcmeta' in jar.namelist():
        write(out + '.mcmeta', jar.read(src + '.mcmeta'))

BUILTIN = {'block/block', 'item/generated', 'item/handheld', 'builtin/generated', 'builtin/entity'}

def copy_model(jar, ref, done, textures):
    path = ref.split(':', 1)[1] if ':' in ref else ref
    if path in BUILTIN or path in done:
        return
    done.add(path)
    src = 'assets/minecraft/models/%s.json' % path
    if src not in jar.namelist():
        print('  missing model', src)
        return
    m = json.loads(jar.read(src))
    if 'parent' in m:
        p = m['parent'].split(':', 1)[-1]
        if p in BUILTIN:
            m['parent'] = p
        else:
            copy_model(jar, m['parent'], done, textures)
            m['parent'] = ref112(m['parent'], 'model')
    for k, v in list(m.get('textures', {}).items()):
        if not v.startswith('#'):
            copy_texture(jar, v, textures)
            m['textures'][k] = ref112(v, 'texture')
    m.pop('render_type', None)
    top, rest = path.split('/', 1)
    write(os.path.join(RES, 'models', top, 'modern', rest + '.json'), json.dumps(m, indent=2) + '\n')

def blockstate(jar, name, out_name, shape, models, textures, half=None):
    """Today's blockstate file to 1.12.2's: waterlogged dropped, slabs split into half and double blocks."""
    bs = json.loads(jar.read('assets/minecraft/blockstates/%s.json' % name))
    variants = {}
    for key, v in bs.get('variants', {}).items():
        props = dict(kv.split('=') for kv in key.split(',') if kv)
        props.pop('waterlogged', None)
        if shape == 'slab':
            t = props.pop('type', 'bottom')
            if half == 'double':
                if t != 'double':
                    continue
            else:
                if t == 'double':
                    continue
                props['half'] = t
                props['variant'] = 'default'
        entries = v if isinstance(v, list) else [v]
        conv = []
        for e in entries:
            e = dict(e)
            copy_model(jar, e['model'], models, textures)
            e['model'] = ref112(e['model'], 'model').replace('backportcore:block/', 'backportcore:')
            conv.append(e)
        k = ','.join('%s=%s' % kv for kv in sorted(props.items())) or 'normal'
        if shape == 'slab' and half == 'double':
            k = 'variant=default'
        variants[k] = conv if len(conv) > 1 else conv[0]
    if 'normal' in variants:
        variants['inventory'] = variants['normal']
    write(os.path.join(RES, 'blockstates', out_name + '.json'), json.dumps({'variants': variants}, indent=2) + '\n')

def item_model(jar, name, models, textures):
    src = 'assets/minecraft/items/%s.json' % name
    ref = 'minecraft:block/' + name
    if src in jar.namelist():
        d = json.loads(jar.read(src))
        ref = d.get('model', {}).get('model', ref)
    copy_model(jar, ref, models, textures)
    write(os.path.join(RES, 'models', 'item', name + '.json'), json.dumps({'parent': ref112(ref, 'model')}, indent=2) + '\n')

# ---- main

def main():
    a = sys.argv[1:]
    jarpath = a[0]
    exe = a[a.index('--javap') + 1] if '--javap' in a else 'javap'
    langdir = a[a.index('--lang') + 1] if '--lang' in a else None
    jar = zipfile.ZipFile(jarpath)
    names = [l.strip() for l in open(os.path.join(HERE, 'names.txt'), encoding='utf-8') if l.strip() and not l.startswith('#')]
    owners = {}
    for l in open(os.path.join(HERE, 'owners.txt'), encoding='utf-8'):
        f = l.split()
        if f and not l.startswith('#'):
            owners[f[0]] = (f[1], f[2] if len(f) > 2 else f[0])

    tmp = tempfile.mkdtemp()
    bf = os.path.join(tmp, 'Blocks.class')
    with open(bf, 'wb') as o:
        o.write(jar.read('net/minecraft/world/level/block/Blocks.class'))
    text = javap(exe, bf, verbose=True)
    interp = Interp(methods(text), bootstraps(text), None)
    interp.run('<clinit>', [])
    print('read %d blocks from Blocks.class' % len(interp.blocks))

    tool, level = tags(jar)
    cache = {}
    rows, skipped = [], []
    models, textures = set(), set()
    lang = {}
    en = {}
    if langdir:
        en = json.load(open(os.path.join(langdir, 'en_us.json'), encoding='utf-8'))
    for name in names:
        rec = interp.blocks.get(name)
        if rec is None:
            skipped.append((name, 'not in the jar'))
            continue
        chain = superchain(jar, tmp, exe, rec['cls'], cache)
        p = rec['props']
        shape = shape_of(chain, p)
        if shape is None:
            skipped.append((name, 'shape class %s' % ' > '.join(chain)))
            continue
        owner, owner_path = owners.get(name, ('-', name))
        snd = p.get('sound', 'STONE')
        layer = 'cutout' if shape in ('grate', 'chain') else 'solid'
        rows.append([name, shape, material_112(snd), '%g' % p.get('hardness', 1.5), '%g' % p.get('resistance', p.get('hardness', 1.5)),
                     sound_112(snd), snd, str(p.get('light', 0)), str(mapcolor_id(p.get('mapcolor', 'NONE'))),
                     tool.get(name, '-'), str(level.get(name, 0)), 'yes' if p.get('tool_required') else 'no',
                     drop(jar, name), owner, owner_path, layer])
        if shape == 'slab':
            base = name[:-len('_slab')]
            blockstate(jar, name, name, shape, models, textures)
            blockstate(jar, name, base + '_double_slab', shape, models, textures, half='double')
        else:
            blockstate(jar, name, name, shape, models, textures)
        item_model(jar, name, models, textures)
        lang[name] = en.get('block.minecraft.' + name, name.replace('_', ' ').title())

    header = ('# Backport Core supplied blocks, generated by tools/supplied-blocks/generate.py from %s. Do not edit.\n'
              '# name shape material hardness resistance sound(1.12.2) sound(today) light mapcolor(1.12.2 id) tool level '
              'needs_tool drop owner owner_name layer\n') % os.path.basename(jarpath)
    write(os.path.join(RES, 'supplied', 'blocks.txt'), header + ''.join('\t'.join(r) + '\n' for r in rows))

    # en_us.lang: a generated block at the end, replaced on each run.
    lp = os.path.join(RES, 'lang', 'en_us.lang')
    cur = open(lp, encoding='utf-8').read()
    start, end = '# ---- supplied blocks (generated) ----\n', '# ---- end of supplied blocks ----\n'
    if start in cur:
        cur = cur[:cur.index(start)] + cur[cur.index(end) + len(end):]
    block = start + ''.join('tile.backportcore.%s.name=%s\n' % (n, t) for n, t in lang.items())
    for n, t in lang.items():
        if n.endswith('_slab'):
            block += 'tile.backportcore.%s_double_slab.name=%s\n' % (n[:-5], t)
    block += end
    write(lp, cur.rstrip('\n') + '\n\n' + block)

    # The other languages: Mojang's own names for the same blocks (Rule 4), the same generated block in each file.
    if langdir:
        keys = [k for k in block.splitlines()[1:-1] if '=' in k]
        for f in sorted(os.listdir(os.path.join(RES, 'lang'))):
            code = f[:-5]
            src = os.path.join(langdir, code + '.json')
            if code == 'en_us' or not os.path.isfile(src):
                continue
            d = json.load(open(src, encoding='utf-8'))
            lines = []
            for k in keys:
                key = k.split('=', 1)[0]
                path = key[len('tile.backportcore.'):-len('.name')]
                v = d.get('block.minecraft.' + (path.replace('_double_slab', '_slab') if path.endswith('_double_slab') else path))
                if v:
                    lines.append('%s=%s' % (key, v) + chr(10))
            lp = os.path.join(RES, 'lang', f)
            cur = open(lp, encoding='utf-8').read()
            if start in cur:
                cur = cur[:cur.index(start)] + cur[cur.index(end) + len(end):]
            write(lp, cur.rstrip(chr(10)) + chr(10) * 2 + start + ''.join(lines) + end)

    print('wrote %d rows, %d models, %d textures' % (len(rows), len(models), len(textures)))
    for n, why in skipped:
        print('skipped %s: %s' % (n, why))

if __name__ == '__main__':
    main()

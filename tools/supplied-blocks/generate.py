#!/usr/bin/env python3
"""
generate.py - writes Backport Core's supplied-block table and assets from today's Minecraft jar
(docs/PLAN_MODERN_BLOCKS.md, "Where the blocks come from").

    python -I tools/supplied-blocks/generate.py <client.jar> --javap <javap.exe> --lang <MojangLang/26.3/lang>
        --family <folder of the family's release jars> --assets <26.x asset index .json> --cache <asset cache folder>

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

Which blocks: every block of the jar 1.12.2 does not have (structure/flattening.txt), minus exclude.txt, whose shape
class Core has; the rest are listed in skipped.txt. Owners: the earliest family jar in --family with the block, or owners.txt.
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

_NAMES = {}

def names_of(jar):
    """The jar's entry names as a set, read once."""
    if id(jar) not in _NAMES:
        _NAMES[id(jar)] = set(jar.namelist())
    return _NAMES[id(jar)]

# ---- shapes

# First class of a block's class chain (the class itself, then its superclasses) that names a shape Core has.
SHAPES = {
    'StairBlock': 'stairs', 'SlabBlock': 'slab', 'ChainBlock': 'chain', 'RotatedPillarBlock': 'pillar',
    'WaterloggedTransparentBlock': 'grate', 'WallBlock': 'wall', 'FenceBlock': 'fence', 'FenceGateBlock': 'fence_gate',
    'DoorBlock': 'door', 'TrapDoorBlock': 'trapdoor', 'ButtonBlock': 'button', 'PressurePlateBlock': 'pressure_plate',
    'LanternBlock': 'lantern', 'LeavesBlock': 'leaves', 'CarpetBlock': 'carpet', 'IronBarsBlock': 'pane',
    'TransparentBlock': 'glass', 'HalfTransparentBlock': 'glass', 'FlowerPotBlock': 'potted',
}
# Classes with behavior or state Core's decorative shapes cannot show: the block is skipped.
NOT_SUPPORTED = {'BaseEntityBlock', 'MultifaceBlock', 'LiquidBlock', 'GrowingPlantBlock', 'FallingBlock', 'BaseTorchBlock',
                 'MossyCarpetBlock', 'AbstractCandleBlock', 'DoublePlantBlock', 'CropBlock', 'StemBlock', 'PowderSnowBlock',
                 'BaseFireBlock', 'BasePressurePlateBlock', 'SpeleothemBlock', 'AmethystClusterBlock', 'BubbleColumnBlock',
                 'BaseCoralPlantTypeBlock', 'SculkSensorBlock', 'ScaffoldingBlock', 'LadderBlock', 'CopperBulbBlock'}
# Today's BlockColors for the blocks Core can supply: a tint, or none (white).
TINTS = {'mangrove_leaves': 'mangrove', 'bush': 'grass'}
# A plant's outline in sixteenths, by the first class of its chain (today's shapes); plants have no collision.
PLANT_BOX = {'FlowerBlock': '5,0,5,11,10,11', 'SaplingBlock': '2,0,2,14,12,14', 'NetherFungusBlock': '4,0,4,12,9,12',
             'NetherSproutsBlock': '0,0,0,16,3,16', 'HangingRootsBlock': '2,10,2,14,16,14', 'EyeblossomBlock': '5,0,5,11,10,11',
             'CactusFlowerBlock': '1,0,1,15,12,15', 'ShortDryGrassBlock': '2,0,2,14,10,14', 'TallDryGrassBlock': '1,0,1,15,16,15'}
# Plants: a VegetationBlock (or hanging roots) drawn as a cross.
PLANT_CLASSES = {'VegetationBlock', 'HangingRootsBlock'}

# The 1.12.2 properties each shape's class has, as its blockstate file's variant keys name them.
PROPS = {
    'cube': set(), 'grate': set(), 'glass': set(), 'leaves': set(), 'plant': set(), 'carpet': set(), 'potted': set(),
    'pillar': {'axis'}, 'chain': {'axis'}, 'stairs': {'facing', 'half', 'shape'}, 'slab': {'half', 'variant'},
    'wall': {'up', 'north', 'east', 'south', 'west'}, 'fence': {'north', 'east', 'south', 'west'},
    'pane': {'north', 'east', 'south', 'west'}, 'fence_gate': {'facing', 'in_wall', 'open'},
    'door': {'facing', 'half', 'hinge', 'open'}, 'trapdoor': {'facing', 'half', 'open'}, 'button': {'facing', 'powered'},
    'pressure_plate': {'powered'}, 'lantern': {'hanging'},
}
CUTOUT = {'grate', 'chain', 'lantern', 'plant', 'potted', 'door', 'trapdoor', 'pane', 'leaves'}
TRANSLUCENT = {'glass'}

def superchain(jar, tmp, exe, cls, cache):
    chain = []
    cur = cls
    while cur and cur not in ('Block', 'Object') and len(chain) < 12:
        if cur in cache:
            nxt = cache[cur]
        else:
            path = 'net/minecraft/world/level/block/%s.class' % cur
            nxt = None
            if path in names_of(jar):
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
        if c in PLANT_CLASSES:
            return 'plant?'
    # A plain Block subclass (copper's weathering blocks, Block itself) is a cube when it is a full, opaque block.
    return 'cube' if not props.get('noOcclusion') and not props.get('noCollision') else None

# ---- 1.12.2 terms

# Today's sound types 1.12.2 has under another name; any other type is one Core registers (sounds.txt).
SOUNDS_112 = {'STONE': 'STONE', 'WOOD': 'WOOD', 'GRAVEL': 'GROUND', 'GRASS': 'PLANT', 'METAL': 'METAL', 'GLASS': 'GLASS',
              'WOOL': 'CLOTH', 'SAND': 'SAND', 'SNOW': 'SNOW', 'LADDER': 'LADDER', 'ANVIL': 'ANVIL', 'SLIME_BLOCK': 'SLIME'}
SOUNDS_METAL = {'METAL', 'COPPER', 'COPPER_GRATE', 'COPPER_BULB', 'CHAIN', 'ANVIL', 'LANTERN', 'NETHERITE_BLOCK', 'HEAVY_CORE',
                'IRON', 'COPPER_CHAIN', 'COPPER_LANTERN', 'IRON_CHAIN'}
SOUNDS_WOOD = {'WOOD', 'NETHER_WOOD', 'CHERRY_WOOD', 'BAMBOO_WOOD', 'STEM', 'NETHER_STEM', 'NETHER_WOOD_HANGING_SIGN'}
SOUNDS_PLANT = {'GRASS', 'ROOTS', 'NETHER_SPROUTS', 'FUNGUS', 'CHERRY_LEAVES', 'AZALEA_LEAVES', 'HANGING_ROOTS', 'MOSS',
                'MOSS_CARPET', 'CROP', 'WEEPING_VINES', 'TWISTING_VINES', 'SWEET_BERRY_BUSH', 'AZALEA', 'FLOWERING_AZALEA',
                'SPORE_BLOSSOM', 'BIG_DRIPLEAF', 'SMALL_DRIPLEAF', 'PINK_PETALS', 'LEAF_LITTER', 'CAVE_VINES', 'GLOW_LICHEN',
                'VINE', 'LILY_PAD', 'WET_GRASS', 'MANGROVE_ROOTS', 'MUDDY_MANGROVE_ROOTS', 'PALE_HANGING_MOSS', 'BUSH', 'CACTUS_FLOWER',
                'FIREFLY_BUSH', 'DRY_GRASS'}

def sound_112(s):
    if s in SOUNDS_112:
        return SOUNDS_112[s]
    if s in SOUNDS_METAL:
        return 'METAL'
    if s in SOUNDS_WOOD:
        return 'WOOD'
    if s in SOUNDS_PLANT:
        return 'PLANT'
    return 'STONE'

def material_112(sound, shape):
    if shape in ('plant', 'potted'):
        return 'PLANTS' if shape == 'plant' else 'CIRCUITS'
    if shape == 'leaves':
        return 'LEAVES'
    if shape == 'glass':
        return 'GLASS'
    if shape == 'carpet':
        return 'CARPET'
    return {'METAL': 'IRON', 'WOOD': 'WOOD', 'PLANT': 'GRASS', 'GROUND': 'GROUND', 'SAND': 'SAND', 'CLOTH': 'CLOTH'}.get(sound_112(sound), 'ROCK')

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
        if p not in names_of(jar):
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
    if p not in names_of(jar):
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

# ---- sounds

def sound_types(jar, tmp, exe):
    """Today's SoundType fields: name -> (volume, pitch, [break, step, place, hit, fall] event ids)."""
    def dump(cls):
        f = os.path.join(tmp, cls.split('/')[-1] + '.class')
        with open(f, 'wb') as o:
            o.write(jar.read(cls + '.class'))
        return subprocess.run([exe, '-c', '-p', f], capture_output=True, text=True, encoding='utf-8', errors='replace').stdout
    events = {}
    last = None
    for line in dump('net/minecraft/sounds/SoundEvents').splitlines():
        m = re.search(r'// String (\S+)$', line)
        if m:
            last = m.group(1)
            continue
        m = re.search(r'putstatic .*// Field (\w+):', line)
        if m and last:
            events[m.group(1)] = last
            last = None
    out = {}
    floats, evs = [], []
    for line in dump('net/minecraft/world/level/block/SoundType').splitlines():
        if re.search(r': new .*class .*SoundType$', line):
            floats, evs = [], []
            continue
        m = re.search(r': fconst_(\d)', line)
        if m:
            floats.append(float(m.group(1)))
            continue
        m = re.search(r'// float ([-\d.eE]+)', line)
        if m:
            floats.append(float(m.group(1)))
            continue
        m = re.search(r'getstatic .*// Field net/minecraft/sounds/SoundEvents\.(\w+):', line)
        if m:
            evs.append(events.get(m.group(1), m.group(1)))
            continue
        m = re.search(r'putstatic .*// Field (\w+):Lnet/minecraft/world/level/block/SoundType;', line)
        if m and len(floats) >= 2 and len(evs) == 5:
            out[m.group(1)] = (floats[0], floats[1], evs)
    return out

def asset_index(path):
    return json.load(open(path, encoding='utf-8'))['objects'] if path else {}

def fetch_object(objects, cache, key):
    """An asset of today's game by its index name, downloaded once into the cache folder (Mojang's resources host)."""
    o = objects.get(key)
    if not o:
        return None
    h = o['hash']
    f = os.path.join(cache, 'objects', h[:2], h)
    if not os.path.isfile(f):
        import urllib.request
        os.makedirs(os.path.dirname(f), exist_ok=True)
        req = urllib.request.Request('https://resources.download.minecraft.net/%s/%s' % (h[:2], h), headers={'User-Agent': 'backport-generator'})
        with urllib.request.urlopen(req, timeout=60) as r, open(f, 'wb') as w:
            w.write(r.read())
    return open(f, 'rb').read()

# ---- assets

def ref112(ref, kind):
    """minecraft:block/x -> backportcore:block/modern/x (models) or backportcore:blocks/modern/x (textures)."""
    path = ref.split(':', 1)[1] if ':' in ref else ref
    if kind == 'model':
        top, rest = path.split('/', 1)
        return 'backportcore:%s/modern/%s' % (top, rest)
    top, rest = path.split('/', 1)
    return 'backportcore:%s/modern/%s' % ({'block': 'blocks', 'item': 'items'}.get(top, top), rest)

WRITTEN = []

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    mode = 'wb' if isinstance(data, bytes) else 'w'
    with open(path, mode, **({} if mode == 'wb' else {'encoding': 'utf-8', 'newline': '\n'})) as f:
        f.write(data)
    WRITTEN.append(os.path.relpath(path, RES).replace(os.sep, '/'))

def copy_texture(jar, ref, done):
    path = ref.split(':', 1)[1] if ':' in ref else ref
    if path in done:
        return
    done.add(path)
    src = 'assets/minecraft/textures/%s.png' % path
    if src not in names_of(jar):
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
    if src + '.mcmeta' in names_of(jar):
        write(out + '.mcmeta', jar.read(src + '.mcmeta'))

BUILTIN = {'block/block', 'item/generated', 'item/handheld', 'builtin/generated', 'builtin/entity'}

def model_json(jar, ref):
    path = ref.split(':', 1)[1] if ':' in ref else ref
    src = 'assets/minecraft/models/%s.json' % path
    return json.loads(jar.read(src)) if src in names_of(jar) else None

def model_chain(jar, ref):
    """The model and its parents, nearest first."""
    out = []
    while ref and len(out) < 10:
        m = model_json(jar, ref)
        if m is None:
            break
        out.append((ref.split(':', 1)[-1], m))
        ref = m.get('parent')
    return out

def copy_model(jar, ref, done, textures):
    path = ref.split(':', 1)[1] if ':' in ref else ref
    if path in BUILTIN or path in done:
        return
    done.add(path)
    m = model_json(jar, ref)
    if m is None:
        print('  missing model', path)
        return
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
    write(os.path.join(RES, 'models', top, 'modern', rest + '.json'), json.dumps(m, indent=1) + '\n')

def first_model(bs):
    if 'variants' in bs:
        v = next(iter(bs['variants'].values()))
    else:
        v = bs['multipart'][0]['apply']
    return (v[0] if isinstance(v, list) else v)['model']

def variant_keys(bs):
    """Every property name today's blockstate file keys on, waterlogged left out."""
    keys = set()
    for key in bs.get('variants', {}):
        keys |= {kv.split('=')[0] for kv in key.split(',') if kv}
    for part in bs.get('multipart', []):
        w = part.get('when', {})
        for c in w.get('OR', w.get('AND', [w])):
            keys |= set(c)
    keys.discard('waterlogged')
    return keys

BUTTON_FACE = {'floor': 'up', 'ceiling': 'down'}

def convert_key(props, shape, half):
    props = dict(props)
    props.pop('waterlogged', None)
    if shape == 'slab':
        t = props.pop('type', 'bottom')
        if half == 'double':
            return None if t != 'double' else {'variant': 'default'}
        if t == 'double':
            return None
        props['half'] = t
        props['variant'] = 'default'
    if shape == 'button':
        face = props.pop('face', 'wall')
        if face != 'wall':
            if props.get('facing') != 'north':
                return None
            props['facing'] = BUTTON_FACE[face]
    if shape in ('fence_gate', 'door'):
        props.pop('powered', None)
    return props

def blockstate(jar, name, out_name, shape, models, textures, half=None):
    """Today's blockstate file to 1.12.2's: waterlogged dropped, slabs split into half and double blocks, buttons'
    face folded into facing; multipart files stay multipart."""
    bs = json.loads(jar.read('assets/minecraft/blockstates/%s.json' % name))

    def conv_model(e):
        e = dict(e)
        copy_model(jar, e['model'], models, textures)
        e['model'] = ref112(e['model'], 'model').replace('backportcore:block/', 'backportcore:')
        return e

    if 'multipart' in bs:
        parts = []
        for part in bs['multipart']:
            apply = part['apply']
            apply = [conv_model(e) for e in apply] if isinstance(apply, list) else conv_model(apply)
            out = {'apply': apply}
            if 'when' in part:
                w = part['when']
                if 'OR' in w:
                    out['when'] = {'OR': [{k: v for k, v in c.items() if k != 'waterlogged'} for c in w['OR']]}
                else:
                    out['when'] = {k: v for k, v in w.items() if k != 'waterlogged'}
            parts.append(out)
        write(os.path.join(RES, 'blockstates', out_name + '.json'), json.dumps({'multipart': parts}, indent=1) + '\n')
        return
    variants = {}
    for key, v in bs.get('variants', {}).items():
        props = convert_key(dict(kv.split('=') for kv in key.split(',') if kv), shape, half)
        if props is None:
            continue
        entries = v if isinstance(v, list) else [v]
        conv = [conv_model(e) for e in entries]
        k = ','.join('%s=%s' % kv for kv in sorted(props.items())) or 'normal'
        variants[k] = conv if len(conv) > 1 else conv[0]
    if 'normal' in variants:
        variants['inventory'] = variants['normal']
    write(os.path.join(RES, 'blockstates', out_name + '.json'), json.dumps({'variants': variants}, indent=1) + '\n')

def item_model(jar, name, models, textures):
    src = 'assets/minecraft/items/%s.json' % name
    ref = 'minecraft:block/' + name
    if src in names_of(jar):
        # The first plain model of the item definition (a condition or select picks among several).
        def first(e):
            if isinstance(e, dict):
                if e.get('type') in ('minecraft:model', 'model') and isinstance(e.get('model'), str):
                    return e['model']
                for v in e.values():
                    r = first(v)
                    if r:
                        return r
            elif isinstance(e, list):
                for v in e:
                    r = first(v)
                    if r:
                        return r
            return None
        ref = first(json.loads(jar.read(src))) or ref
    copy_model(jar, ref, models, textures)
    write(os.path.join(RES, 'models', 'item', name + '.json'), json.dumps({'parent': ref112(ref, 'model')}, indent=1) + '\n')

def tint_and_box(jar, bs):
    """Whether the model is tinted, and the bounds of its elements in sixteenths ('-' for a full block)."""
    chain = model_chain(jar, first_model(bs))
    tinted = any('tintindex' in json.dumps(m.get('elements', [])) for _p, m in chain)
    names = [p for p, _m in chain]
    elements = next((m['elements'] for _p, m in chain if 'elements' in m), None)
    box = '-'
    if elements:
        lo = [min(e['from'][i] for e in elements) for i in range(3)]
        hi = [max(e['to'][i] for e in elements) for i in range(3)]
        if lo != [0, 0, 0] or hi != [16, 16, 16]:
            box = ','.join('%g' % max(0, min(16, x)) for x in lo + hi)
    return tinted, box, names

# ---- main

FAMILY_ORDER = ['updateaquaticbackport', 'villagerbackport', 'buzzybeesbackport', 'netherupdatebackport', 'cavesandcliffsbackport',
                'wildupdatebackport', 'trailsandtalesbackport', 'trickytrialsbackport', 'gamedropsbackport']

def family_blocks(folder):
    """modid -> block names of each family jar in the folder (from its blockstates)."""
    out = {}
    if not folder:
        return out
    for f in os.listdir(folder):
        if not f.endswith('.jar'):
            continue
        with zipfile.ZipFile(os.path.join(folder, f)) as z:
            for n in z.namelist():
                m = re.match(r'assets/(\w+)/blockstates/(\w+)\.json$', n)
                if m and m.group(1) in FAMILY_ORDER:
                    out.setdefault(m.group(1), set()).add(m.group(2))
    return out

def old_names():
    """Blocks 1.12.2 has under some name: the Flattening table's (Core's structure/flattening.txt)."""
    out = set()
    for l in open(os.path.join(RES, 'structure', 'flattening.txt'), encoding='utf-8'):
        if l.startswith('minecraft:'):
            out.add(l.split('\t')[0].split('[')[0][len('minecraft:'):])
    # Renamed since 1.13 (ModernPalette brings files up to date), or a tile entity's in 1.12.2: still 1.12.2's blocks.
    return out | {'short_grass', 'dirt_path', 'oak_sign', 'oak_wall_sign', 'smooth_stone_slab', 'water_cauldron', 'flower_pot',
                 # 1.13 split 1.12.2's pumpkin (always carved) into pumpkin and carved_pumpkin; ModernPalette maps both.
                 'pumpkin'}

def arg(a, k, d=None):
    return a[a.index(k) + 1] if k in a else d

def main():
    a = sys.argv[1:]
    jarpath = a[0]
    exe = arg(a, '--javap', 'javap')
    langdir = arg(a, '--lang')
    family = family_blocks(arg(a, '--family'))
    objects = asset_index(arg(a, '--assets'))
    cache = arg(a, '--cache', os.path.join(tempfile.gettempdir(), 'mc-assets'))
    jar = zipfile.ZipFile(jarpath)
    overrides = {}
    for l in open(os.path.join(HERE, 'owners.txt'), encoding='utf-8'):
        f = l.split()
        if f and not l.startswith('#'):
            overrides[f[0]] = (f[1], f[2] if len(f) > 2 else f[0])
    excluded = {l.strip() for l in open(os.path.join(HERE, 'exclude.txt'), encoding='utf-8') if l.strip() and not l.startswith('#')}

    # A run replaces everything the last run wrote.
    manifest = os.path.join(RES, 'supplied', 'generated.txt')
    if os.path.isfile(manifest):
        for rel in open(manifest, encoding='utf-8').read().split():
            p = os.path.join(RES, rel)
            if os.path.isfile(p):
                os.remove(p)

    tmp = tempfile.mkdtemp()
    bf = os.path.join(tmp, 'Blocks.class')
    with open(bf, 'wb') as o:
        o.write(jar.read('net/minecraft/world/level/block/Blocks.class'))
    text = javap(exe, bf, verbose=True)
    interp = Interp(methods(text), bootstraps(text), None)
    interp.run('<clinit>', [])
    print('read %d blocks from Blocks.class' % len(interp.blocks))

    old = old_names()
    tool, level = tags(jar)
    classes = {}
    rows, skipped = [], []
    models, textures = set(), set()
    lang = {}
    en = json.load(open(os.path.join(langdir, 'en_us.json'), encoding='utf-8')) if langdir else {}
    for name, rec in interp.blocks.items():
        if name in old or name in excluded:
            continue
        bsp = 'assets/minecraft/blockstates/%s.json' % name
        if bsp not in names_of(jar):
            skipped.append((name, 'no blockstate file'))
            continue
        bs = json.loads(jar.read(bsp))
        chain = superchain(jar, tmp, exe, rec['cls'], classes)
        p = rec['props']
        shape = shape_of(chain, p)
        tinted, box, model_names = tint_and_box(jar, bs)
        chain_models = model_chain(jar, first_model(bs))
        if not model_names or not any('elements' in m for _p, m in chain_models):
            # Drawn by a tile entity renderer (copper chests): no block model to copy, or one with only a particle.
            skipped.append((name, 'no block model'))
            continue
        if shape == 'plant?':
            shape = 'plant' if any(n in ('block/cross', 'block/tinted_cross') for n in model_names) else None
        if shape is None:
            skipped.append((name, 'class %s' % ' > '.join(chain)))
            continue
        keys = variant_keys(bs)
        if shape == 'slab':
            keys = (keys - {'type'}) | {'half', 'variant'}
        if shape == 'button':
            keys.discard('face')
        if shape in ('fence_gate', 'door'):
            keys.discard('powered')
        if not keys <= PROPS[shape]:
            skipped.append((name, '%s with properties %s' % (shape, ', '.join(sorted(keys - PROPS[shape])))))
            continue
        if name in overrides:
            owner, owner_name = overrides[name]
        else:
            owner, owner_name = next(((m, name) for m in FAMILY_ORDER if name in family.get(m, ())), ('-', name))
        layout = 'axis_ordinal' if shape == 'chain' and owner == 'trickytrialsbackport' else 'vanilla'
        snd = p.get('sound', 'STONE')
        layer = 'translucent' if shape in TRANSLUCENT else 'cutout' if shape in CUTOUT else 'solid'
        tint = TINTS.get(name, '-')
        if shape == 'plant':
            box = next((PLANT_BOX[c] for c in chain if c in PLANT_BOX), '2,0,2,14,13,14')
        elif shape not in ('potted', 'carpet', 'lantern'):
            box = '-'
        rows.append([name, shape, material_112(snd, shape), '%g' % p.get('hardness', 0.0), '%g' % p.get('resistance', p.get('hardness', 0.0)),
                     sound_112(snd), snd, str(p.get('light', 0)), str(mapcolor_id(p.get('mapcolor', 'NONE'))),
                     tool.get(name, '-'), str(level.get(name, 0)), 'yes' if p.get('tool_required') else 'no',
                     drop(jar, name), owner, owner_name, layer, layout, tint, box])
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
              'needs_tool drop owner owner_name layer layout tint box(sixteenths)\n') % os.path.basename(jarpath)
    write(os.path.join(RES, 'supplied', 'blocks.txt'), header + ''.join('\t'.join(r) + '\n' for r in rows))

    # Today's sound types 1.12.2 lacks, as Core's own sound events, with today's sound files.
    types = sound_types(jar, tmp, exe)
    used = sorted({r[6] for r in rows if r[6] not in SOUNDS_112 and r[6] in types})
    sounds_json_path = os.path.join(RES, 'sounds.json')
    sj = json.load(open(sounds_json_path, encoding='utf-8')) if os.path.isfile(sounds_json_path) else {}
    sj = {k: v for k, v in sj.items() if not k.startswith('block.')}
    modern_sounds = json.loads(fetch_object(objects, cache, 'minecraft/sounds.json') or b'{}')
    lines, files, missing_sounds = [], set(), []
    for t in used:
        vol, pitch, evs = types[t]
        lines.append('%s\t%g\t%g\t%s\n' % (t, vol, pitch, '\t'.join(evs)))
        for ev in evs:
            if ev in sj:
                continue
            entry = modern_sounds.get(ev)
            if entry is None:
                missing_sounds.append(ev)
                continue
            out = []
            for s in entry.get('sounds', []):
                s = dict(s) if isinstance(s, dict) else {'name': s}
                if s.get('type') == 'event':
                    # Another event's sounds: copied in.
                    for inner in modern_sounds.get(s['name'].replace('minecraft:', ''), {}).get('sounds', []):
                        inner = dict(inner) if isinstance(inner, dict) else {'name': inner}
                        if inner.get('type') != 'event':
                            out.append(inner)
                    continue
                out.append(s)
            conv = []
            for s in out:
                n = s['name'].replace('minecraft:', '')
                files.add(n)
                s['name'] = 'backportcore:modern/' + n
                s.pop('attenuation_distance', None)
                conv.append(s)
            e = {'sounds': conv}
            sub = entry.get('subtitle', '')
            if sub.startswith('subtitles.block.generic.'):
                e['subtitle'] = sub
            sj[ev] = e
    write(os.path.join(RES, 'supplied', 'sounds.txt'), '# Today\'s sound types: volume pitch break step place hit fall.\n' + ''.join(lines))
    with open(sounds_json_path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(json.dumps(sj, indent=1) + '\n')
    total = 0
    for n in sorted(files):
        data = fetch_object(objects, cache, 'minecraft/sounds/%s.ogg' % n)
        if data is None:
            missing_sounds.append(n)
            continue
        total += len(data)
        write(os.path.join(RES, 'sounds', 'modern', n + '.ogg'), data)

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
    with open(lp, 'w', encoding='utf-8', newline='\n') as f:
        f.write(cur.rstrip('\n') + '\n\n' + block)

    # The other languages: Mojang's own names for the same blocks (Rule 4), the same generated block in each file.
    if langdir:
        keys = [k for k in block.splitlines()[1:-1] if '=' in k]
        for f in sorted(os.listdir(os.path.join(RES, 'lang'))):
            code = f[:-5]
            src = os.path.join(langdir, code + '.json')
            if code == 'en_us' or not os.path.isfile(src):
                continue
            d = json.load(open(src, encoding='utf-8'))
            out = []
            for k in keys:
                key = k.split('=', 1)[0]
                path = key[len('tile.backportcore.'):-len('.name')]
                v = d.get('block.minecraft.' + (path.replace('_double_slab', '_slab') if path.endswith('_double_slab') else path))
                if v:
                    out.append('%s=%s' % (key, v) + chr(10))
            lp = os.path.join(RES, 'lang', f)
            cur = open(lp, encoding='utf-8').read()
            if start in cur:
                cur = cur[:cur.index(start)] + cur[cur.index(end) + len(end):]
            with open(lp, 'w', encoding='utf-8', newline='\n') as fh:
                fh.write(cur.rstrip(chr(10)) + chr(10) * 2 + start + ''.join(out) + end)

    with open(manifest, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(sorted(set(WRITTEN) - {'supplied/generated.txt'})) + '\n')
    with open(os.path.join(HERE, 'skipped.txt'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('# Blocks newer than 1.12.2 that Core does not supply, and why (generated).\n')
        f.write(''.join('%s\t%s\n' % s for s in skipped))
    shapes = {}
    for r in rows:
        shapes[r[1]] = shapes.get(r[1], 0) + 1
    print('wrote %d rows (%s), %d models, %d textures, %d sound types, %d sound files (%d KB)' % (
        len(rows), ', '.join('%s %d' % kv for kv in sorted(shapes.items())), len(models), len(textures), len(used), len(files), total // 1024))
    print('skipped %d (tools/supplied-blocks/skipped.txt); sounds missing: %s' % (len(skipped), missing_sounds[:10]))

if __name__ == '__main__':
    main()

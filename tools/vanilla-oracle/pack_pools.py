"""Copies 1.16.5's bastion pools (dumped with Mojang's codecs) into the self-test assets, naming processor
lists by id the way data packs do, and copies the bastion and ruined portal templates.

usage: pack_pools.py <worldgen dump dir> <client data/minecraft/structures dir> <selftest assets/<ns> dir>
"""
import json
import os
import shutil
import sys

dump, structures, out = sys.argv[1], sys.argv[2], sys.argv[3]

lists = {}
for name in os.listdir(os.path.join(dump, 'processor_list')):
    with open(os.path.join(dump, 'processor_list', name), encoding='utf-8') as f:
        lists[json.dumps(json.load(f), sort_keys=True)] = 'minecraft:' + name[:-5]

used = set()
inline_kept = 0


def by_id(element, keep_inline):
    global inline_kept
    p = element.get('processors')
    if isinstance(p, dict):
        key = json.dumps(p, sort_keys=True)
        if key in lists and not keep_inline:
            element['processors'] = lists[key]
            used.add(lists[key][len('minecraft:'):])
        elif keep_inline:
            inline_kept += 1
    for child in element.get('elements', []):
        by_id(child, keep_inline)


pools = 0
src_root = os.path.join(dump, 'template_pool', 'bastion')
for root, _, files in os.walk(src_root):
    for name in files:
        src = os.path.join(root, name)
        rel = os.path.relpath(src, os.path.join(dump, 'template_pool'))
        with open(src, encoding='utf-8') as f:
            pool = json.load(f)
        # The start pool keeps its lists written out, so both of today's forms are read.
        keep = rel.replace('\\', '/') == 'bastion/starts.json'
        for e in pool['elements']:
            by_id(e['element'], keep)
        dst = os.path.join(out, 'worldgen', 'template_pool', rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(pool, f, indent=2)
            f.write('\n')
        pools += 1

for name in sorted(used):
    dst = os.path.join(out, 'worldgen', 'processor_list', name + '.json')
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copyfile(os.path.join(dump, 'processor_list', name + '.json'), dst)

templates = 0
for folder in ('bastion', 'ruined_portal'):
    for root, _, files in os.walk(os.path.join(structures, folder)):
        for name in files:
            src = os.path.join(root, name)
            rel = os.path.relpath(src, structures)
            dst = os.path.join(out, 'structures', rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(src, dst)
            templates += 1

print('pools', pools, 'processor lists', len(used), sorted(used), 'inline kept', inline_kept, 'templates', templates)

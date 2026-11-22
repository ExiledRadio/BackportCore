"""Builds the 1.21.5 layer of Core's modern pack: every spawn egg's own texture.

    python -I tools/textures/eggs.py

1.21.5 gave each spawn egg a texture of its own (item/<mob>_spawn_egg.png) in place of 1.12.2's two tinted layers
(items/spawn_egg and items/spawn_egg_overlay). 1.12.2 has no file of that name to replace, so these are not
build.py's work: they go in backportcore_packs/modern-1.21.5/ under their own 26.x names, and Core's spawn egg
model (client.SpawnEggModels) draws a mob's egg from its texture when the layer applies. Every egg comes from the
newest jar (tables.EGG_JAR), the family's mobs' eggs too; the layer applies from 1.21.5, so the look is the newest
texture each egg has once eggs are textures at all. Keyed-transparency PNGs are rewritten as RGBA (png_rgba.py).
"""
import os
import shutil
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from layers import REPO  # noqa: E402
from png_rgba import keyed_to_rgba  # noqa: E402
from tables import EGG_JAR, EGG_LINE, JARS  # noqa: E402

PACKS = os.path.join(REPO, 'src', 'main', 'resources', 'backportcore_packs')
PREFIX = 'assets/minecraft/textures/item/'


def main():
    jar = zipfile.ZipFile(JARS[EGG_JAR])
    eggs = sorted(n for n in jar.namelist() if n.startswith(PREFIX) and n.endswith('_spawn_egg.png'))
    d = os.path.join(PACKS, 'modern-' + EGG_LINE)
    if os.path.isdir(d):
        shutil.rmtree(d)
    files = []
    for n in eggs:
        p = os.path.join(d, n)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, 'wb') as f:
            f.write(keyed_to_rgba(jar.read(n)))
        files.append(n)
        if n + '.mcmeta' in jar.namelist():
            with open(p + '.mcmeta', 'wb') as f:
                f.write(jar.read(n + '.mcmeta'))
            files.append(n + '.mcmeta')
    with open(os.path.join(d, 'index.txt'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(files) + '\n')
    print('%s: %d spawn eggs from %s' % (EGG_LINE, len(eggs), EGG_JAR))


if __name__ == '__main__':
    main()

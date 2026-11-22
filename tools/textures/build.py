"""Builds the later texture layers of Core's modern pack: one folder per release line that changed vanilla's textures.

    python -I tools/textures/build.py            writes the layers and tools/textures/report.txt
    python -I tools/textures/build.py --check    writes nothing; prints the decisions for non-atlas textures

The 1.14 layer is tools/modern-textures/generate.py's (src/main/resources/backportcore_packs/modern) and is read,
never written, here. Every later layer, backportcore_packs/modern-<line>/, holds only the 1.12.2 textures whose
pixels at that line's last release (tables.LINES) differ from what the layers below already show, saved under their
1.12.2 names. Core applies the layers up to the newest installed backport's version (client.ModernTextures).

A texture goes in only when it still fits 1.12.2's models and UVs:
  - block and item sprites (stitched into the atlas one by one) always fit, if square or an animated strip;
  - everything else (mobs, armor, GUI sheets, the sky) must keep 1.12.2's aspect ratio and must not leave blank
    more than LOST_MAX of the pixels 1.12.2's image paints: a sheet whose parts moved, or a mob whose model
    changed, leaves 1.12.2's UVs pointing at nothing;
  - and it is not in tables.EXCLUDE or tables.LAYOUT (changes the check cannot see). tables.ACCEPT lets in the
    few that leave only unused parts of the sheet blank, after a look.
A sheet in tables.SHEETS, whose parts later lines moved into sprites of their own, is put back together first: the
line's sprites at 1.12.2's places, and where the line's sheet is blank, the look below.
Everything left out is listed in report.txt with the reason. Keyed-transparency PNGs are rewritten as RGBA
(png_rgba.py), since 1.12.2 draws their key color black.
"""
import io
import json
import os
import shutil
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from layers import REPO, ATLAS, load_jars, follow, pixel_hash  # noqa: E402
from png_rgba import keyed_to_rgba  # noqa: E402
from tables import ACCEPT, EGG_LINE, EXCLUDE, LAYOUT, LOST_MAX, LINES, MODELS, SHEETS  # noqa: E402

PACKS = os.path.join(REPO, 'src', 'main', 'resources', 'backportcore_packs')
BASE = os.path.join(PACKS, 'modern')
REPORT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'report.txt')


def rel(o):
    return 'assets/minecraft/textures/%s.png' % o


def animation(meta):
    if meta is None:
        return None
    try:
        return json.loads(meta.decode('utf-8')).get('animation')
    except ValueError:
        return None


def blank_share(old_im, new_im):
    """@return the share of 1.12.2's painted pixels that 'new_im' (scaled to 1.12.2's size) leaves blank"""
    n = new_im.resize(old_im.size, Image.NEAREST) if new_im.size != old_im.size else new_im
    a = old_im.getchannel('A').tobytes()
    b = n.getchannel('A').tobytes()
    painted = lost = 0
    for x, y in zip(a, b):
        if x:
            painted += 1
            lost += not y
    return lost / float(painted) if painted else 0.0


def misfit(o, old_im, new_im, meta):
    """@return why the new image does not fit 1.12.2, or None when it does"""
    w, h = new_im.size
    if o.startswith(ATLAS):
        if h % w:
            return 'not square (%dx%d)' % (w, h)
        if h > w and animation(meta) is None:
            return 'a %dx%d strip with no animation' % (w, h)
        return None
    ow, oh = old_im.size
    if ow * h != oh * w:
        return 'size %dx%d does not scale to 1.12.2\'s %dx%d (new UV layout)' % (w, h, ow, oh)
    lost = blank_share(old_im, new_im)
    if lost > LOST_MAX:
        return '%.0f%% of what 1.12.2\'s image paints is blank now (parts moved)' % (100 * lost)
    return None


def prefixed(o, table):
    for p, why in table:
        if o.startswith(p):
            return why
    return None


def base_layer(old):
    with open(os.path.join(BASE, 'index.txt'), encoding='utf-8') as f:
        index = {l.strip() for l in f if l.strip()}
    shown = {}
    for o in old.tex:
        r = rel(o)
        if r in index:
            with open(os.path.join(BASE, r), 'rb') as f:
                im = Image.open(io.BytesIO(f.read())).convert('RGBA')
            meta = None
            if r + '.mcmeta' in index:
                with open(os.path.join(BASE, r + '.mcmeta'), 'rb') as f:
                    meta = f.read()
            shown[o] = (pixel_hash(im), animation(meta), '1.14')
        else:
            shown[o] = (old.hash(o), animation(old.mcmeta(o)), '1.12.2')
    return shown


def shown_image(o, shown, old, layers):
    """@return the image the layers below show for 'o'"""
    at = shown[o][2]
    if at == '1.12.2':
        return old.image(o)
    if at == '1.14':
        return Image.open(os.path.join(BASE, rel(o))).convert('RGBA')
    return Image.open(io.BytesIO(layers[at][rel(o)])).convert('RGBA')


def sheet(o, jar, im, below):
    """tables.SHEETS: the line's sheet, the look below where it is blank, and the line's sprites at 1.12.2's places"""
    if im.size != below.size:
        return im
    out = below.copy()
    out.paste(im, (0, 0), im.getchannel('A').point(lambda a: 255 if a else 0))
    for x, y, sprite in SHEETS[o]:
        try:
            out.paste(jar.image(sprite), (x, y))
        except KeyError:
            pass
    return out


def without_sprites(o, im):
    """@return 'im' with a SHEETS sheet's sprite places cleared: a sprite may leave blank what 1.12.2's icon painted"""
    if o not in SHEETS:
        return im
    out = im.copy()
    for x, y, sprite in SHEETS[o]:
        out.paste((0, 0, 0, 0), (x, y, x + 18, y + 18))
    return out


def png_bytes(im):
    b = io.BytesIO()
    im.save(b, 'PNG')
    return b.getvalue()


def line_model(jar, template, tex):
    """A line's block model (no parent) as a 1.12.2 model: its elements, every texture variable on the 1.12.2 texture."""
    m = json.loads(jar.read('assets/minecraft/models/block/%s.json' % template))
    if 'parent' in m:
        raise ValueError('%s has a parent; only parentless templates are copied' % template)
    variables = {f['texture'][1:] for e in m['elements'] for f in e['faces'].values()}
    m['textures'] = dict({v: tex for v in variables}, particle='#' + sorted(variables)[0])
    return (json.dumps(m, indent=4) + '\n').encode('utf-8')


def main(check):
    jars = load_jars()
    old = jars['1.12.2']
    steps = follow(jars)
    shown = base_layer(old)
    report, left, counts, layers = [], [], [], {}
    seen = {}  # each texture left out: the image last listed, so a look is listed once (an excluded one, once at all)
    for line, cur, gone in steps[1:]:
        jar = jars[line]
        files = {}
        for o, n in sorted(cur.items()):
            why = prefixed(o, EXCLUDE)
            im = jar.image(n)
            if o in SHEETS:
                im = sheet(o, jar, im, shown_image(o, shown, old, layers))
            meta = jar.mcmeta(n)
            h = pixel_hash(im)
            if h == shown[o][0] and animation(meta) == shown[o][1]:
                continue
            if why is None:
                why = prefixed(o, LAYOUT.get(line, ()))
            if why is None:
                why = misfit(o, without_sprites(o, old.image(o)), without_sprites(o, im), meta)
                if why is not None and why.endswith('(parts moved)') and (line, o) in ACCEPT:
                    why = None
            if why is not None:
                if o in seen and (seen[o] == h or prefixed(o, EXCLUDE) is not None):
                    continue
                seen[o] = h
                left.append('%-5s %-48s %s: %s' % (line, o, n, why))
                continue
            if check and not o.startswith(ATLAS):
                print('%-5s %-48s %-9s <- %-9s %s  blank %.3f' % (line, o, '%dx%d' % old.image(o).size,
                      '%dx%d' % im.size, n, blank_share(old.image(o), im)))
            files[rel(o)] = png_bytes(im) if o in SHEETS else keyed_to_rgba(jar.png(n))
            if meta is not None:
                files[rel(o) + '.mcmeta'] = meta
            for model, template in MODELS.get(o, ()):
                files['assets/minecraft/models/block/%s.json' % model] = line_model(jar, template, o)
            shown[o] = (h, animation(meta), line)
            report.append('%-5s %-48s <- %s %s%s' % (line, o, jar.version, n, ' (and its sprites)' if o in SHEETS else ''))
        for o, n in sorted(gone.items()):
            if prefixed(o, EXCLUDE) is None:
                left.append('%-5s %-48s %s: gone (split up or removed); stays as %s' % (line, o, n, shown[o][2]))
        layers[line] = files
        counts.append('%s: %d textures' % (line, sum(not k.endswith('.mcmeta') for k in files)))
        jar.drop_images()
    print('\n'.join(counts))
    if check:
        return
    for line, _ in LINES[1:]:
        d = os.path.join(PACKS, 'modern-' + line)
        if os.path.isdir(d):
            shutil.rmtree(d)
        if not layers[line]:
            continue
        for r, data in layers[line].items():
            p = os.path.join(d, r)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, 'wb') as f:
                f.write(data)
        with open(os.path.join(d, 'index.txt'), 'w', encoding='utf-8', newline='\n') as f:
            f.write('\n'.join(sorted(layers[line])) + '\n')
    with open(os.path.join(PACKS, 'layers.txt'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('# The texture layers after 1.14\'s (modern/), by version. Written by tools/textures/build.py.\n')
        f.write(''.join(line + '\n' for line, _ in LINES[1:] if layers[line]))
        # The spawn egg layer is eggs.py's; keep it listed.
        if os.path.isfile(os.path.join(PACKS, 'modern-' + EGG_LINE, 'index.txt')):
            f.write(EGG_LINE + '\n')
    with open(REPORT, 'w', encoding='utf-8', newline='\n') as f:
        f.write('# Written by build.py. Layers: %s\n\n## In the layers\n' % ', '.join(counts))
        f.write('\n'.join(report) + '\n\n## Left out\n' + '\n'.join(left) + '\n')


if __name__ == '__main__':
    main('--check' in sys.argv)

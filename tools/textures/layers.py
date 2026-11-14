"""Core of the texture-layer tool: follows every 1.12.2 texture through each later release line.

See build.py for the command line. This module only reads jars.
"""
import hashlib
import io
import os
import sys
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, os.path.join(REPO, 'tools', 'modern-textures'))
sys.path.insert(0, HERE)
import generate  # noqa: E402  (tools/modern-textures: the 1.14 layer and its 1.12.2 -> 1.14.4 names)
from tables import RENAMES, LINES, JARS  # noqa: E402

TEX = 'assets/minecraft/textures/'


class Jar(generate.Jar):
    """A client jar: generate.Jar, which the 1.14 name pass needs, plus decoded images."""

    def __init__(self, version, path):
        generate.Jar.__init__(self, path)
        self.version = version
        self.path = path
        self._img = {}

    def image(self, tex):
        if tex not in self._img:
            self._img[tex] = Image.open(io.BytesIO(self.png(tex))).convert('RGBA')
        return self._img[tex]

    def hash(self, tex):
        if tex not in self._h:
            self._h[tex] = pixel_hash(self.image(tex))
        return self._h[tex]

    def drop_images(self):
        self._img.clear()


def pixel_hash(im):
    px = bytearray(im.tobytes())
    for i in range(3, len(px), 4):
        if px[i] == 0:
            px[i - 3] = px[i - 2] = px[i - 1] = 0
    return hashlib.sha1(bytes(px) + repr(im.size).encode()).hexdigest()


def load_jars():
    """@return {'1.12.2': Jar, '1.14': Jar, '1.15': Jar, ...}, the base and one jar per release line"""
    out = {'1.12.2': Jar('1.12.2', JARS['1.12.2'])}
    for line, release in LINES:
        out[line] = Jar(release, JARS[release])
    return out


def base_names(jars):
    """@return {1.12.2 texture: 1.14.4 name} for every 1.12.2 texture that 1.14.4 still has"""
    old, new = jars['1.12.2'], jars['1.14']
    mapping = {}
    try:
        mapping.update(generate.build_map(old, new))
    except SystemExit:
        raise
    for n in old.tex:
        if n in mapping:
            continue
        if n in generate.MANUAL:
            mapping[n] = generate.MANUAL[n]
        elif generate.rule(n) in new.tex:
            mapping[n] = generate.rule(n)
        elif n in RENAMES.get('1.14', {}):
            r = RENAMES['1.14'][n]
            if r is not None:
                mapping[n] = r
    return mapping


def resolve(name, jar, step):
    """@return the name 'name' has in 'jar', or None when it is gone"""
    if name in jar.tex:
        return name
    table = RENAMES.get(step, {})
    if name in table:
        r = table[name]
        if r is not None and r not in jar.tex:
            raise SystemExit('tables.RENAMES[%r] names a texture %s does not have: %s' % (step, jar.version, r))
        return r
    return None


def follow(jars):
    """@return [(line, {1.12.2 texture: name in that line's jar}, {1.12.2 texture: last name, now gone})]

    The first entry is 1.14's. A texture that is gone stays gone.
    """
    names = base_names(jars)
    out = [('1.14', dict(names), {})]
    for line, _ in LINES[1:]:
        jar = jars[line]
        cur, gone = {}, {}
        for old, n in out[-1][1].items():
            r = resolve(n, jar, line)
            if r is None:
                gone[old] = n
            else:
                cur[old] = r
        out.append((line, cur, gone))
    return out


ATLAS = ('blocks/', 'items/')


def mask_fit(old_im, new_im):
    """@return (same aspect ratio, share of pixels whose opaque-or-not agrees once scaled to 1.12.2's size,
    IoU of the opaque pixels)"""
    ow, oh = old_im.size
    nw, nh = new_im.size
    if ow * nh != oh * nw:
        return False, 0.0, 0.0
    n = new_im.resize((ow, oh), Image.NEAREST) if (nw, nh) != (ow, oh) else new_im
    a = old_im.getchannel('A').tobytes()
    b = n.getchannel('A').tobytes()
    agree = inter = union = 0
    for x, y in zip(a, b):
        p, q = x > 0, y > 0
        agree += p == q
        inter += p and q
        union += p or q
    return True, agree / float(len(a)), (inter / float(union) if union else 1.0)

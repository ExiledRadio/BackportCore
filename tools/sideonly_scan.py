"""Find calls from common code to Minecraft methods and fields that exist only on the client.

A @SideOnly(Side.CLIENT) member is stripped from the dedicated server's classes, so common code that reaches one throws
NoSuchMethodError / NoSuchFieldError there (Core 1.4.0-1.4.8 kicked every player who opened an elytra this way), while
singleplayer, which has the client's classes, never shows it.

Usage: python -I tools/sideonly_scan.py [--srg <MCP mappings dir>] <forgeSrc jar> <mod jar>...
Both MCP-named: ForgeGradle's forgeSrc jar is Minecraft after Forge's patches (the merged srgBin jar is before them,
and marks members client-only that Forge gives the server back), and the mods' -dev jars; or release jars with
--srg and the folder holding MCP's methods.csv and fields.csv. A class annotated client-only, or in a package with a "client" segment, is
skipped; every other class that names a client-only member is listed with the members, to be checked by hand: a call
behind a world.isRemote test, or in code only the client reaches, is fine.
"""
import io
import struct
import sys
import zipfile

SIDE_ONLY = "Lnet/minecraftforge/fml/relauncher/SideOnly;"


class ClassInfo:
    def __init__(self):
        self.name = None
        self.super = None
        self.interfaces = []
        self.client = False
        self.members = {}  # (name, desc) -> client-only?
        self.refs = []  # (kind, owner, name, desc, method that uses it)
        self.client_methods = set()  # this class's own client-only methods


def parse(data):
    info = ClassInfo()
    pos = 8
    count = struct.unpack_from(">H", data, pos)[0]
    pos += 2
    cp = [None] * count
    i = 1
    while i < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            n = struct.unpack_from(">H", data, pos)[0]
            cp[i] = ("utf8", data[pos + 2:pos + 2 + n].decode("utf-8", "replace"))
            pos += 2 + n
        elif tag in (3, 4):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            cp[i] = ("class" if tag == 7 else "other", struct.unpack_from(">H", data, pos)[0])
            pos += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            a, b = struct.unpack_from(">HH", data, pos)
            kind = {9: "field", 10: "method", 11: "imethod", 12: "nat"}.get(tag, "other")
            cp[i] = (kind, a, b)
            pos += 4
        elif tag == 15:
            pos += 3
        else:
            raise ValueError("bad constant pool tag %d" % tag)
        i += 1

    def utf(idx):
        return cp[idx][1]

    def cls(idx):
        return utf(cp[idx][1])

    def annotations_client(attrs):
        for aname, adata in attrs:
            if aname != "RuntimeVisibleAnnotations" and aname != "RuntimeInvisibleAnnotations":
                continue
            # Cheap: a SideOnly annotation whose enum value is CLIENT.
            n = struct.unpack_from(">H", adata, 0)[0]
            p = 2
            for _ in range(n):
                p, is_client = read_annotation(adata, p)
                if is_client:
                    return True
        return False

    def read_annotation(adata, p):
        type_idx, pairs = struct.unpack_from(">HH", adata, p)
        p += 4
        found = False
        for _ in range(pairs):
            p += 2
            p, val = read_value(adata, p)
            if utf(type_idx) == SIDE_ONLY and val == "CLIENT":
                found = True
        return p, found

    def read_value(adata, p):
        tag = chr(adata[p])
        p += 1
        if tag in "BCDFIJSZsc":
            v = struct.unpack_from(">H", adata, p)[0]
            return p + 2, None
        if tag == "e":
            _, const = struct.unpack_from(">HH", adata, p)
            return p + 4, utf(const)
        if tag == "@":
            p, _ = read_annotation(adata, p)
            return p, None
        if tag == "[":
            n = struct.unpack_from(">H", adata, p)[0]
            p += 2
            for _ in range(n):
                p, _ = read_value(adata, p)
            return p, None
        raise ValueError(tag)

    def read_attrs(p):
        n = struct.unpack_from(">H", data, p)[0]
        p += 2
        attrs = []
        for _ in range(n):
            ni, ln = struct.unpack_from(">HI", data, p)
            attrs.append((utf(ni), data[p + 6:p + 6 + ln]))
            p += 6 + ln
        return p, attrs

    _, this_idx, super_idx, icount = struct.unpack_from(">HHHH", data, pos)
    pos += 8
    info.name = cls(this_idx)
    info.super = cls(super_idx) if super_idx else None
    for k in range(icount):
        info.interfaces.append(cls(struct.unpack_from(">H", data, pos + 2 * k)[0]))
    pos += 2 * icount
    for _ in range(2):  # fields, then methods
        n = struct.unpack_from(">H", data, pos)[0]
        pos += 2
        for _ in range(n):
            _, ni, di = struct.unpack_from(">HHH", data, pos)
            pos, attrs = read_attrs(pos + 6)
            key = (utf(ni), utf(di))
            info.members[key] = annotations_client(attrs)
            for aname, adata in attrs:
                if aname == "Code":
                    code_len = struct.unpack_from(">I", adata, 4)[0]
                    for idx in member_refs(adata[8:8 + code_len]):
                        entry = cp[idx]
                        if entry and entry[0] in ("field", "method", "imethod"):
                            nat = cp[entry[2]]
                            info.refs.append((entry[0], cls(entry[1]), utf(nat[1]), utf(nat[2]), key))
    pos, attrs = read_attrs(pos)
    info.client = annotations_client(attrs)
    return info


# Operand sizes of the JVM opcodes (tableswitch, lookupswitch and wide are handled apart).
SIZES = {}
for op in list(range(0x00, 0x10)) + list(range(0x1a, 0x36)) + list(range(0x3b, 0x84)) + list(range(0x85, 0xa8))         + list(range(0xac, 0xb2)) + [0xbe, 0xbf, 0xc2, 0xc3]:
    SIZES[op] = 0
for op in [0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3a, 0xa9, 0xbc]:
    SIZES[op] = 1
for op in [0x11, 0x13, 0x14, 0x84, 0xbb, 0xbd, 0xc0, 0xc1, 0xc6, 0xc7] + list(range(0x99, 0xa9)) + list(range(0xb2, 0xb9)):
    SIZES[op] = 2
SIZES[0xc5] = 3
for op in [0xb9, 0xba, 0xc8, 0xc9]:
    SIZES[op] = 4


def member_refs(code):
    """The constant pool indexes of the field and method instructions in a method's code."""
    p = 0
    while p < len(code):
        op = code[p]
        if 0xb2 <= op <= 0xb9:
            yield struct.unpack_from(">H", code, p + 1)[0]
        if op == 0xaa:
            q = (p + 4) & ~3
            lo, hi = struct.unpack_from(">ii", code, q + 4)
            p = q + 12 + 4 * (hi - lo + 1)
        elif op == 0xab:
            q = (p + 4) & ~3
            n = struct.unpack_from(">i", code, q + 4)[0]
            p = q + 8 + 8 * n
        elif op == 0xc4:
            p += 6 if code[p + 1] == 0x84 else 4
        else:
            p += 1 + SIZES[op]


def load(jar):
    out = {}
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            if n.endswith(".class"):
                try:
                    c = parse(z.read(n))
                except Exception as e:  # noqa: BLE001 - a class we cannot read is reported, not fatal
                    print("  (unreadable %s: %s)" % (n, e))
                    continue
                out[c.name] = c
    return out


def client_only(mc, owner, name, desc, seen=None):
    """Whether the member the reference resolves to (searching supertypes) is client-only; None if not Minecraft's."""
    seen = seen or set()
    c = mc.get(owner)
    if c is None or owner in seen:
        return None
    seen.add(owner)
    if (name, desc) in c.members:
        return c.members[(name, desc)] or c.client
    for s in [c.super] + c.interfaces:
        if s:
            r = client_only(mc, s, name, desc, seen)
            if r is not None:
                return r
    return None


# Methods only the client calls, though the classes are common: a tile entity's or block's client callbacks.
CLIENT_CALLBACKS = {
    "onDataPacket", "handleUpdateTag",
    "func_180655_c",  # randomDisplayTick
    "func_149666_a",  # getSubBlocks
    "func_150895_a",  # getSubItems
    "func_77624_a",  # addInformation
    "func_180664_k",  # getRenderLayer
    "func_185484_c",  # getPackedLightmapCoords
    "func_176225_a",  # shouldSideBeRendered
    "func_190948_a",  # addInformation (block)
    "func_70103_a",  # handleStatusUpdate
    "randomDisplayTick", "getSubBlocks", "getSubItems", "addInformation", "getRenderLayer", "getPackedLightmapCoords",
    "shouldSideBeRendered", "handleStatusUpdate", "canPlaceBlockOnSide", "getBrightnessForRender", "getRenderBoundingBox",
}


def mcp_names(directory):
    """SRG to MCP names from an MCP mappings folder (methods.csv, fields.csv)."""
    names = {}
    for f in ("methods.csv", "fields.csv"):
        with io.open(directory + "/" + f, encoding="utf-8") as fh:
            next(fh)
            for line in fh:
                parts = line.split(",")
                names[parts[0]] = parts[1]
    return names


def main():
    args = sys.argv[1:]
    names = None
    if args[0] == "--srg":
        # Release (SRG-named) mod jars: their references are translated to MCP names.
        names = mcp_names(args[1])
        args = args[2:]
    sys.argv[1:] = args
    mc = load(sys.argv[1])
    total = 0
    for jar in sys.argv[2:]:
        mods = load(jar)
        if names:
            for c in mods.values():
                c.refs = [(k, o, names.get(n, n), d, (names.get(u[0], u[0]), u[1])) for k, o, n, d, u in c.refs]
        hits = []
        for c in sorted(mods.values(), key=lambda c: c.name):
            if c.client or "/client/" in c.name + "/" or c.name.endswith("/client"):
                continue
            outer = c.name.split("$")[0]
            if outer in mods and mods[outer].client:
                continue
            bad = set()
            for kind, owner, name, desc, user in c.refs:
                if c.members.get(user) or user[0] in CLIENT_CALLBACKS:
                    continue
                if not owner.startswith("net/minecraft/") or (owner in mc and mc[owner].client):
                    continue
                if client_only(mc, owner, name, desc):
                    bad.add("%s:  %s.%s%s" % (user[0], owner.split("/")[-1], name, desc if kind != "field" else ""))
            if bad:
                hits.append((c.name, sorted(bad)))
        print("== %s: %d classes" % (jar.replace("\\", "/").split("/")[-1], len(hits)))
        for name, bad in hits:
            print("  %s" % name)
            for b in bad:
                print("      %s" % b)
        total += len(hits)
    print("total classes to check: %d" % total)


if __name__ == "__main__":
    main()

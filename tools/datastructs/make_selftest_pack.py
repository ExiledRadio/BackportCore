"""Writes the self-test datapack: three tiny jigsaw templates (structure NBT, today's format) and their JSON.

python -I tools/datastructs/make_selftest_pack.py src/selftest/resources
"""
import gzip
import io
import json
import os
import struct
import sys

NS = "bcst_data"


# ---- NBT writing
def w_str(out, s):
    b = s.encode("utf-8")
    out.write(struct.pack(">H", len(b)))
    out.write(b)


class Int(int):
    pass


class List(list):
    def __init__(self, tag, items):
        super().__init__(items)
        self.tag = tag


TAG = {dict: 10, str: 8, Int: 3, int: 3}


def tag_of(v):
    if isinstance(v, List):
        return 9
    if isinstance(v, dict):
        return 10
    if isinstance(v, str):
        return 8
    return 3


def w_payload(out, v):
    t = tag_of(v)
    if t == 10:
        for k, x in v.items():
            out.write(bytes([tag_of(x)]))
            w_str(out, k)
            w_payload(out, x)
        out.write(b"\x00")
    elif t == 9:
        out.write(bytes([v.tag]))
        out.write(struct.pack(">i", len(v)))
        for x in v:
            w_payload(out, x)
    elif t == 8:
        w_str(out, v)
    else:
        out.write(struct.pack(">i", v))


def write_nbt(path, root):
    buf = io.BytesIO()
    buf.write(b"\x0a")
    w_str(buf, "")
    w_payload(buf, root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(gzip.compress(buf.getvalue(), mtime=0))


def template(size, blocks):
    """blocks: list of (x, y, z, name, props, nbt)."""
    palette = []
    index = {}
    out = []
    for (x, y, z, name, props, nbt) in blocks:
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            p = {"Name": name}
            if props:
                p["Properties"] = dict(props)
            palette.append(p)
        b = {"pos": List(3, [x, y, z]), "state": index[key]}
        if nbt is not None:
            b["nbt"] = nbt
        out.append(b)
    return {
        "DataVersion": 4671,
        "size": List(3, list(size)),
        "palette": List(10, palette),
        "blocks": List(10, out),
        "entities": List(10, []),
    }


def jigsaw(x, y, z, orientation, name, target, pool, final_state):
    return (x, y, z, "minecraft:jigsaw", {"orientation": orientation},
            {"id": "minecraft:jigsaw", "name": name, "target": target, "pool": pool, "joint": "rollable",
             "final_state": final_state, "placement_priority": 0, "selection_priority": 0})


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def pool(name, location):
    return {"name": name, "fallback": "minecraft:empty", "elements": [{"weight": 1, "element": {
        "element_type": "minecraft:single_pool_element", "location": location, "processors": "minecraft:empty",
        "projection": "rigid"}}]}


def write_pack(root, ns, block_floor="minecraft:lapis_block"):
    data = os.path.join(root, "data", ns)
    # base: a 3x3 floor, the jigsaw up in its middle
    base = [(x, 0, z, block_floor, {}, None) for x in range(3) for z in range(3)]
    base.append(jigsaw(1, 1, 1, "up_north", ns + ":base_top", ns + ":joint", ns + ":tiny/middle", block_floor))
    write_nbt(os.path.join(data, "structure", "tiny", "base.nbt"), template((3, 2, 3), base))
    middle = [jigsaw(0, 0, 0, "down_north", ns + ":joint", "minecraft:empty", "minecraft:empty", "minecraft:gold_block"),
              (0, 1, 0, "minecraft:gold_block", {}, None),
              jigsaw(0, 2, 0, "up_north", ns + ":mid_top", ns + ":joint", ns + ":tiny/top", "minecraft:gold_block")]
    write_nbt(os.path.join(data, "structure", "tiny", "middle.nbt"), template((1, 3, 1), middle))
    top = [jigsaw(0, 0, 0, "down_north", ns + ":joint", "minecraft:empty", "minecraft:empty", "minecraft:emerald_block")]
    # today's other folder name, structures/, for the top piece
    write_nbt(os.path.join(data, "structures", "tiny", "top.nbt"), template((1, 1, 1), top))
    for p in ("base", "middle", "top"):
        write_json(os.path.join(data, "worldgen", "template_pool", "tiny", p + ".json"),
                   pool(ns + ":tiny/" + p, ns + ":tiny/" + p))


def structure(ns, biomes, **extra):
    s = {"type": "minecraft:jigsaw", "biomes": biomes, "max_distance_from_center": 32,
         "project_start_to_heightmap": "WORLD_SURFACE_WG", "size": 3, "spawn_overrides": {},
         "start_height": {"absolute": 0}, "start_pool": ns + ":tiny/base", "step": "surface_structures",
         "terrain_adaptation": "beard_thin", "use_expansion_hack": False}
    s.update(extra)
    return s


def main():
    res = sys.argv[1]
    ns = NS
    write_pack(res, ns)
    data = os.path.join(res, "data", ns)
    land = ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:forest", "minecraft:flower_forest",
            "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:taiga", "minecraft:snowy_taiga",
            "minecraft:old_growth_pine_taiga", "minecraft:desert", "minecraft:savanna", "minecraft:savanna_plateau",
            "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:windswept_hills", "minecraft:windswept_forest",
            "minecraft:snowy_plains", "minecraft:swamp", "minecraft:badlands", "minecraft:wooded_badlands",
            "minecraft:eroded_badlands", "minecraft:mushroom_fields", "minecraft:beach", "minecraft:river",
            "minecraft:stony_shore", "minecraft:snowy_beach", "minecraft:old_growth_birch_forest",
            "minecraft:ice_spikes", "minecraft:windswept_gravelly_hills", "minecraft:windswept_savanna"]
    # a tag that nests another, as vanilla's do
    write_json(os.path.join(data, "tags", "worldgen", "biome", "tiny_land.json"), {"values": land[:10]})
    write_json(os.path.join(data, "tags", "worldgen", "biome", "has_structure", "tiny_tower.json"),
               {"values": ["#" + ns + ":tiny_land", {"id": "minecraft:not_a_biome", "required": False}] + land[10:]})
    write_json(os.path.join(data, "worldgen", "structure", "tiny_tower.json"),
               structure(ns, "#" + ns + ":has_structure/tiny_tower", spawn_overrides={
                   "monster": {"bounding_box": "full", "spawns": [{"type": "minecraft:witch", "weight": 1, "minCount": 1, "maxCount": 1}]},
                   "creature": {"bounding_box": "piece", "spawns": []},
                   "axolotls": {"bounding_box": "piece", "spawns": []}}))
    write_json(os.path.join(data, "worldgen", "structure_set", "tiny_towers.json"), {
        "placement": {"type": "minecraft:random_spread", "salt": 20261123, "separation": 3, "spacing": 10},
        "structures": [{"structure": ns + ":tiny_tower", "weight": 1}]})
    # a weighted set of two (one only in the ocean), with a frequency and a locate offset
    write_json(os.path.join(data, "worldgen", "structure", "tiny_mix_land.json"),
               structure(ns, "#" + ns + ":has_structure/tiny_tower"))
    write_json(os.path.join(data, "worldgen", "structure", "tiny_mix_sea.json"),
               structure(ns, "#minecraft:is_ocean", project_start_to_heightmap="OCEAN_FLOOR_WG",
                         max_distance_from_center={"horizontal": 40, "vertical": 20}, dimension_padding={"bottom": 1},
                         liquid_settings="ignore_waterlogging"))
    write_json(os.path.join(data, "worldgen", "structure_set", "tiny_mix.json"), {
        "placement": {"type": "minecraft:random_spread", "salt": 4242, "separation": 5, "spacing": 18,
                      "spread_type": "triangular", "frequency": 0.75, "frequency_reduction_method": "legacy_type_3",
                      "locate_offset": [3, 0, -2]},
        "structures": [{"structure": ns + ":tiny_mix_land", "weight": 3}, {"structure": ns + ":tiny_mix_sea", "weight": 1}]})
    # blacklisted by the self-test's config before Core loads
    write_json(os.path.join(data, "worldgen", "structure", "blocked.json"), structure(ns, "minecraft:plains"))
    write_json(os.path.join(data, "worldgen", "structure_set", "blocked.json"), {
        "placement": {"type": "minecraft:random_spread", "salt": 1, "separation": 1, "spacing": 4},
        "structures": [{"structure": ns + ":blocked", "weight": 1}]})
    # what Core skips: another placement type, another structure type, a family structure
    write_json(os.path.join(data, "worldgen", "structure_set", "rings.json"), {
        "placement": {"type": "minecraft:concentric_rings", "count": 3, "distance": 32, "preferred_biomes": "#minecraft:is_ocean",
                      "salt": 0, "spread": 3},
        "structures": [{"structure": ns + ":tiny_tower", "weight": 1}]})
    write_json(os.path.join(data, "worldgen", "structure", "hardcoded.json"),
               {"type": "minecraft:igloo", "biomes": "minecraft:snowy_plains", "spawn_overrides": {}, "step": "surface_structures"})
    write_json(os.path.join(data, "worldgen", "structure_set", "hardcoded.json"), {
        "placement": {"type": "minecraft:random_spread", "salt": 2, "separation": 2, "spacing": 8},
        "structures": [{"structure": ns + ":hardcoded", "weight": 1}]})
    # the drop-in packs the self-test copies into config/backportcore/structures (a folder and a zip)
    for pack, dns in (("dropin_folder", "bcst_dropin"), ("dropin_zip", "bcst_dropzip")):
        proot = os.path.join(res, "assets", "backportcore_selftest", "dropin", pack)
        write_pack(proot, dns, "minecraft:diamond_block")
        ddata = os.path.join(proot, "data", dns)
        write_json(os.path.join(ddata, "worldgen", "structure", "tower.json"),
                   structure(dns, ["backportcore_selftest:nowhere"], project_start_to_heightmap="WORLD_SURFACE_WG"))
        write_json(os.path.join(ddata, "worldgen", "structure_set", "towers.json"), {
            "placement": {"type": "minecraft:random_spread", "salt": 99, "separation": 8, "spacing": 32},
            "structures": [{"structure": dns + ":tower", "weight": 1}]})
        write_json(os.path.join(proot, "pack.mcmeta"), {"pack": {"pack_format": 88, "description": "self-test"}})


if __name__ == "__main__":
    main()

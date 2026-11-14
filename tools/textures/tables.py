"""Hand-written tables for the texture layers."""

G = 'G:/MCDownloads/'
JARS = {
    '1.12.2': G + 'client-1.12.2/client.jar',
    '1.14.4': G + 'client-1.14.4/client.jar',
    '1.15.2': G + 'client-1.15.2/client.jar',
    '1.16.5': G + 'client-1.16.5/client.jar',
    '1.17.1': G + 'minecraft-1.17.1/client.jar',
    '1.18.2': G + 'minecraft-1.18.2-mojdecomp/client.jar',
    '1.19.4': G + 'wu-lookfeel-1.19.4/client-1.19.4.jar',
    '1.20.6': G + 'minecraft-1.20.6/client.jar',
    '1.21.11': G + 'minecraft-1.21.11/client.jar',
    '26.1.2': G + 'minecraft-26.1.2/client.jar',
    '26.2': G + 'minecraft-26.2/client.jar',
    '26.3': G + 'minecraft-26.3/client.jar',
}

# One layer per release line, by the version a backport registers for it; each holds the textures as the
# line's last release (the jar) has them.
LINES = [
    ('1.14', '1.14.4'), ('1.15', '1.15.2'), ('1.16', '1.16.5'), ('1.17', '1.17.1'), ('1.18', '1.18.2'),
    ('1.19', '1.19.4'), ('1.20', '1.20.6'), ('1.21', '1.21.11'), ('26.1', '26.1.2'), ('26.2', '26.2'),
    ('26.3', '26.3'),
]


# Names a release line gave a texture the previous line had, {line: {old name: new name}}. Only one-to-one
# renames; a texture split into several files (the 1.20.2 GUI sprites, 1.15's double chests) is gone from then on
# and keeps the look of the last line that had it whole.
RENAMES = {
    '1.15': {'entity/iron_golem': 'entity/iron_golem/iron_golem'},
    '1.16': {'entity/zombie_pigman': 'entity/piglin/zombified_piglin'},
    '1.17': {
        'block/grass_path_side': 'block/dirt_path_side', 'block/grass_path_top': 'block/dirt_path_top',
        'entity/arrow': 'entity/projectiles/arrow', 'entity/squid': 'entity/squid/squid',
    },
    '1.19': {
        'entity/steve': 'entity/player/wide/steve', 'entity/alex': 'entity/player/slim/alex',
        'misc/enchanted_item_glint': 'misc/enchanted_glint_item',
    },
    '1.20': {'block/grass': 'block/short_grass'},
    '1.21': {
        'entity/chicken': 'entity/chicken/temperate_chicken', 'entity/cow/cow': 'entity/cow/temperate_cow',
        'entity/pig/pig': 'entity/pig/temperate_pig', 'entity/pig/pig_saddle': 'entity/equipment/pig_saddle/saddle',
        'entity/sheep/sheep_fur': 'entity/sheep/sheep_wool', 'entity/elytra': 'entity/equipment/wings/elytra',
        'environment/sun': 'environment/celestial/sun', 'item/broken_elytra': 'item/elytra_broken',
        'item/empty_armor_slot_boots': 'gui/sprites/container/slot/boots',
        'item/empty_armor_slot_chestplate': 'gui/sprites/container/slot/chestplate',
        'item/empty_armor_slot_helmet': 'gui/sprites/container/slot/helmet',
        'item/empty_armor_slot_leggings': 'gui/sprites/container/slot/leggings',
        'item/empty_armor_slot_shield': 'gui/sprites/container/slot/shield',
    },
    '26.1': {
        'entity/armorstand/wood': 'entity/armorstand/armorstand', 'entity/banner_base': 'entity/banner/banner_base',
        'entity/bat': 'entity/bat/bat', 'entity/beacon_beam': 'entity/beacon/beacon_beam', 'entity/blaze': 'entity/blaze/blaze',
        'entity/cat/black': 'entity/cat/cat_black', 'entity/cat/red': 'entity/cat/cat_red',
        'entity/cat/siamese': 'entity/cat/cat_siamese', 'entity/cow/red_mooshroom': 'entity/cow/mooshroom_red',
        'entity/enchanting_table_book': 'entity/enchantment/enchanting_table_book',
        'entity/end_gateway_beam': 'entity/end_portal/end_gateway_beam', 'entity/end_portal': 'entity/end_portal/end_portal',
        'entity/endermite': 'entity/endermite/endermite', 'entity/experience_orb': 'entity/experience/experience_orb',
        'entity/guardian': 'entity/guardian/guardian', 'entity/guardian_beam': 'entity/guardian/guardian_beam',
        'entity/guardian_elder': 'entity/guardian/guardian_elder', 'entity/lead_knot': 'entity/lead_knot/lead_knot',
        'entity/llama/spit': 'entity/llama/llama_spit', 'entity/minecart': 'entity/minecart/minecart',
        'entity/projectiles/spectral_arrow': 'entity/projectiles/arrow_spectral',
        'entity/projectiles/tipped_arrow': 'entity/projectiles/arrow_tipped',
        'entity/shield_base': 'entity/shield/shield_base', 'entity/shield_base_nopattern': 'entity/shield/shield_base_nopattern',
        'entity/silverfish': 'entity/silverfish/silverfish', 'entity/snow_golem': 'entity/snow_golem/snow_golem',
        'entity/spider_eyes': 'entity/spider/spider_eyes', 'entity/witch': 'entity/witch/witch',
        'entity/chicken/temperate_chicken': 'entity/chicken/chicken_temperate',
        'entity/cow/temperate_cow': 'entity/cow/cow_temperate', 'entity/pig/temperate_pig': 'entity/pig/pig_temperate',
    },
    '26.2': {
        'block/purpur_pillar': 'block/purpur_pillar_side', 'block/quartz_pillar': 'block/quartz_pillar_side',
        'entity/signs/oak': 'block/oak_sign',
    },
}
for _m in ('diamond', 'gold', 'iron'):
    RENAMES['1.21']['entity/horse/armor/horse_armor_' + _m] = 'entity/equipment/horse_body/' + _m
for _m in ('chainmail', 'diamond', 'gold', 'iron', 'leather'):
    RENAMES['1.21']['models/armor/%s_layer_1' % _m] = 'entity/equipment/humanoid/' + _m
    RENAMES['1.21']['models/armor/%s_layer_2' % _m] = 'entity/equipment/humanoid_leggings/' + _m
RENAMES['1.21']['models/armor/leather_layer_1_overlay'] = 'entity/equipment/humanoid/leather_overlay'
RENAMES['1.21']['models/armor/leather_layer_2_overlay'] = 'entity/equipment/humanoid_leggings/leather_overlay'
for _c in ('black', 'blue', 'brown', 'cyan', 'gray', 'green', 'light_blue', 'lime', 'magenta', 'orange', 'pink', 'purple',
           'red', 'light_gray', 'white', 'yellow'):
    RENAMES['1.21']['entity/llama/decor/' + _c] = 'entity/equipment/llama_body/' + _c
for _c in ('brown', 'creamy', 'gray', 'white'):
    RENAMES['26.1']['entity/llama/' + _c] = 'entity/llama/llama_' + _c
for _c in ('black', 'brown', 'caerbannog', 'gold', 'salt', 'toast', 'white', 'white_splotched'):
    RENAMES['26.1']['entity/rabbit/' + _c] = 'entity/rabbit/rabbit_' + _c

# A texture leaves 1.12.2's UVs pointing at nothing when more than this share of what 1.12.2's image paints is
# blank in the new one.
LOST_MAX = 0.01

# Never in a later layer, whatever the line did to them: (1.12.2 name or folder/, why).
EXCLUDE = [
    # As in the 1.14 layer (tools/modern-textures/manual_map.py).
    ('entity/villager/', 'villagers: 1.14 split them into biome and profession layers; Village & Pillage draws its own'),
    ('entity/zombie_villager/', 'zombie villagers: as villagers'),
    ('entity/zombie/zombie_villager', 'zombie villagers: as villagers'),
    ('entity/horse/', 'horses: 1.14 remade the model; its 64x64 textures do not fit 1.12.2\'s 128x128 UVs'),
    ('blocks/water_', 'water: grayscale since 1.13, tinted by a per-biome water color 1.12.2 does not have'),
    ('entity/explosion', 'a particle sheet, split into particle/ files in 1.14'),
    ('entity/sweep', 'a particle sheet, split into particle/ files in 1.14'),
    ('entity/banner/', 'banner patterns: white masks for 1.15\'s renderer'),
    ('entity/shield/', 'shield patterns: white masks for 1.15\'s renderer'),
    ('entity/chest/', 'chests: re-laid-out for 1.15\'s renderer (the parts are flipped)'),
    ('entity/enderdragon/', 'the dragon: its wings re-laid-out for 1.15\'s renderer'),
    # Served by the presentation module from the backports' own copies (Contribution.replaceAsset).
    ('gui/title/', 'the title screen: the presentation module\'s (the backports\' titleLogo and startupLogo)'),
    ('gui/widgets', 'the buttons: the presentation module\'s (the backports\' replaceAsset)'),
    ('entity/ghast/ghast', 'ghasts: the presentation module\'s (Tricky Trials\' replaceAsset)'),
]

# Changes the layout check cannot see, by line: (1.12.2 name or folder/, why).
LAYOUT = {}

# Textures the layout check rejects that were looked at and do fit, {(line, 1.12.2 name): why}.
ACCEPT = {
    ('1.17', 'entity/guardian'): 'blank only where 1.12.2\'s sheet had leftovers no model part uses (the elder '
                                  'guardian\'s 1.12.2 sheet is blank there too) and the "jeb" signature',
    ('1.17', 'entity/guardian_elder'): 'blank only at the "jeb" signature, which no model part uses',
}

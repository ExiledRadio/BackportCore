package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.gameevent.GameEvent;
import com.exiledradio.backportcore.gameevent.GameEventTags;
import com.exiledradio.backportcore.locate.StructureClaims;
import com.exiledradio.backportcore.locate.StructureLocate;
import com.exiledradio.backportcore.mechanics.AgeLockable;
import com.exiledradio.backportcore.mechanics.JukeboxSignal;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.map.MapFacing;
import com.exiledradio.backportcore.mechanics.JukeboxSignal;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.structure.ModernItems;

import net.minecraft.block.BlockJukebox;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.MapData;
import net.minecraft.world.storage.MapDecoration;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Core 1.4.4's answers to Game Drops Backport's requests (docs/CORE_REQUESTS.md there): the jukebox's signal for a
 * disc that gives its own (P41), {@code addDampener}, {@code GameEvent.BOUNCE}, the exploration map search (radius and
 * claims), today's item names, the age lock's {@code AgeLockable}, the baby model gate and the off-map player facing (P43).
 */
final class GdRequestTests {

    /** A disc with 26.3's {@code comparator_output} 8, as Game Drops' Bounce. */
    static final class TestDisc extends ItemRecord {
        TestDisc() {
            super("backportcore_selftest", new SoundEvent(new ResourceLocation("backportcore", "selftest_disc")));
        }

        public int comparatorSignal() {
            return 8;
        }
    }

    private static TestDisc disc;

    private GdRequestTests() {
    }

    private static int signal(net.minecraft.world.World world, BlockPos pos) {
        return Blocks.JUKEBOX.getComparatorInputOverride(world.getBlockState(pos), world, pos);
    }

    /** A zoom-0 map at 0, 0 with unlimited tracking and a player marker {@code x} blocks east, facing {@code yaw}. */
    private static MapData offMap(net.minecraft.world.World world, double x, double yaw) {
        MapData data = new MapData("backportcore_selftest_facing");
        data.scale = 0;
        data.xCenter = 0;
        data.zCenter = 0;
        data.unlimitedTracking = true;
        data.dimension = 0;
        try {
            java.lang.reflect.Method m = null;
            for (java.lang.reflect.Method c : MapData.class.getDeclaredMethods()) {
                if (c.getParameterCount() == 6 && c.getParameterTypes()[0] == MapDecoration.Type.class) {
                    m = c;
                }
            }
            m.setAccessible(true);
            m.invoke(data, MapDecoration.Type.PLAYER, world, "p", x, 0.0D, yaw);
            return data;
        } catch (ReflectiveOperationException | RuntimeException e) {
            check("MapData.updateDecorations by reflection", false, e);
            return null;
        }
    }

    static void schedule() {
        then(1, "GD 26.4: creative places for Deep Freeze's items", world -> {
            // Every item 26.4's creative menu adds has a vanilla place, so CreativePlacement.place(...) finds a tab.
            String[][] expected = {
                    {"minecraft:packed_ice_bricks", "BUILDING_BLOCKS"}, {"minecraft:packed_ice_brick_stairs", "BUILDING_BLOCKS"},
                    {"minecraft:packed_ice_brick_slab", "BUILDING_BLOCKS"}, {"minecraft:packed_ice_brick_wall", "DECORATIONS"},
                    {"minecraft:chiseled_packed_ice", "BUILDING_BLOCKS"}, {"minecraft:snow_bricks", "BUILDING_BLOCKS"},
                    {"minecraft:snow_brick_stairs", "BUILDING_BLOCKS"}, {"minecraft:snow_brick_slab", "BUILDING_BLOCKS"},
                    {"minecraft:snow_brick_wall", "DECORATIONS"}, {"minecraft:calcite_pillar", "BUILDING_BLOCKS"},
                    {"minecraft:calcite_stairs", "BUILDING_BLOCKS"}, {"minecraft:calcite_slab", "BUILDING_BLOCKS"},
                    {"minecraft:calcite_wall", "DECORATIONS"}, {"minecraft:polished_calcite", "BUILDING_BLOCKS"},
                    {"minecraft:polished_calcite_stairs", "BUILDING_BLOCKS"}, {"minecraft:polished_calcite_slab", "BUILDING_BLOCKS"},
                    {"minecraft:polished_calcite_wall", "DECORATIONS"}, {"minecraft:polished_calcite_bricks", "BUILDING_BLOCKS"},
                    {"minecraft:polished_calcite_brick_stairs", "BUILDING_BLOCKS"}, {"minecraft:polished_calcite_brick_slab", "BUILDING_BLOCKS"},
                    {"minecraft:polished_calcite_brick_wall", "DECORATIONS"}, {"minecraft:brittle_ice", "BUILDING_BLOCKS"},
                    {"minecraft:icicle", "DECORATIONS"}, {"minecraft:ice_crystal", "DECORATIONS"},
                    {"minecraft:music_disc_flurry", "MISC"}, {"minecraft:ice_ball", "MISC"}, {"minecraft:frostbite_spawn_egg", "MISC"},
            };
            StringBuilder wrong = new StringBuilder();
            for (String[] e : expected) {
                net.minecraft.creativetab.CreativeTabs tab = com.exiledradio.backportcore.creative.VanillaOrder.tab(e[0]);
                if (com.exiledradio.backportcore.creative.VanillaOrder.position(e[0]) < 0 || tab == null
                        || !e[1].equals(com.exiledradio.backportcore.creative.VanillaOrder.nameOf(tab))) {
                    wrong.append(e[0]).append(' ').append(tab == null ? "none" : tab.getTabLabel()).append("; ");
                }
            }
            check("creative: vanilla's order has 26.4's 27 new items, each in its 1.19.2 tab", wrong.length() == 0, wrong);
            check("creative: chiseled soulshale has no place, as 26.4 lists it in no tab",
                    com.exiledradio.backportcore.creative.VanillaOrder.position("minecraft:chiseled_soulshale") < 0);
            java.util.function.ToDoubleFunction<String> key = com.exiledradio.backportcore.creative.VanillaOrder::key;
            check("creative: packed ice bricks follow the other bricks, the ice ball the snowball, the frostbite egg the frog's",
                    key.applyAsDouble("minecraft:packed_ice_bricks") > key.applyAsDouble("minecraft:sulfur_bricks")
                            && key.applyAsDouble("minecraft:ice_ball") > key.applyAsDouble("minecraft:snowball")
                            && key.applyAsDouble("minecraft:ice_ball") < key.applyAsDouble("minecraft:slime_ball")
                            && key.applyAsDouble("minecraft:frostbite_spawn_egg") > key.applyAsDouble("minecraft:frog_spawn_egg")
                            && key.applyAsDouble("minecraft:frostbite_spawn_egg") < key.applyAsDouble("minecraft:ghast_spawn_egg"));
        });
        then(1, "GD Su-1: jukebox comparator signal (P41)", world -> {
            check("P41 applied when BlockJukebox loaded", "applied".equals(Asm.state("P41")), Asm.state("P41"));
            if (disc == null) {
                disc = new TestDisc();
            }
            BlockPos pos = new BlockPos(4410, 150, 4410);
            world.setBlockState(pos, Blocks.JUKEBOX.getDefaultState(), 2);
            BlockJukebox.TileEntityJukebox te = (BlockJukebox.TileEntityJukebox) world.getTileEntity(pos);
            te.setRecord(new ItemStack(Items.RECORD_13));
            check("vanilla's 13 still gives 1", signal(world, pos) == 1, signal(world, pos));
            te.setRecord(new ItemStack(Items.RECORD_WAIT));
            check("vanilla's wait still gives 12", signal(world, pos) == 12, signal(world, pos));
            te.setRecord(new ItemStack(disc));
            check("a disc with comparatorSignal() gives its own (8)", signal(world, pos) == 8, signal(world, pos));
            check("JukeboxSignal reads the stack: 8 for it, none for vanilla's cat",
                    JukeboxSignal.signal(new ItemStack(disc)) == 8 && JukeboxSignal.signal(new ItemStack(Items.RECORD_CAT)) == -1, "");
            te.setRecord(ItemStack.EMPTY);
            check("empty jukebox: 0", signal(world, pos) == 0, signal(world, pos));
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
        });

        then(1, "GD #1: addDampener", world -> {
            check("hay does not dampen before", !GameEventTags.dampens(Blocks.HAY_BLOCK.getDefaultState()), "");
            GameEventTags.addDampener(Blocks.HAY_BLOCK);
            check("an added block dampens", GameEventTags.dampens(Blocks.HAY_BLOCK.getDefaultState()), "");
            EntityItem item = new EntityItem(world, 0, 100, 0, new ItemStack(Blocks.HAY_BLOCK));
            check("its dropped item dampens", GameEventTags.dampens(item), "");
            check("wool and carpet still dampen, stone does not", GameEventTags.dampens(Blocks.WOOL.getDefaultState())
                    && GameEventTags.dampens(Blocks.CARPET.getDefaultState()) && !GameEventTags.dampens(Blocks.STONE.getDefaultState()), "");
        });

        then(1, "GD #2: the bounce game event", world -> {
            check("bounce is minecraft:bounce at frequency 2 (26.3's VibrationSystem)", GameEvent.BOUNCE.frequency() == 2
                    && "minecraft:bounce".equals(String.valueOf(GameEvent.BOUNCE.name())), GameEvent.BOUNCE.name());
            check("1.20 rules' vibrations and warden list hear it (26.3's tags)", GameEventTags.VIBRATIONS_1_20.contains(GameEvent.BOUNCE)
                    && GameEventTags.WARDEN_CAN_LISTEN_1_20.contains(GameEvent.BOUNCE), "");
            check("1.19.2's lists do not", !GameEventTags.VIBRATIONS_1_19.contains(GameEvent.BOUNCE), "");
        });

        then(1, "GD stream 7 #2: exploration map search radius and claims", world -> {
            BlockPos spawn = world.getSpawnPoint();
            StructureLocate.Found all = StructureLocate.nearest(world, spawn, LocateTests.GRID);
            StructureLocate.Found same = StructureLocate.nearest(world, spawn, LocateTests.GRID, StructureLocate.RINGS, false);
            check("100 rings without claims answers as before", all != null && same != null && all.pos.equals(same.pos), same);
            StructureLocate.Found r0 = StructureLocate.nearest(world, spawn, LocateTests.GRID, 0, false);
            check("0 rings: the first ring's answer or none", r0 == null || all != null && r0.pos.equals(all.pos), r0);
            StructureLocate.Found a = StructureLocate.nearest(world, spawn, LocateTests.GRID, 50, true);
            check("a map's search claims what it found", a != null && a.chunk != null
                    && StructureClaims.of(world).claimed(LocateTests.GRID, a.chunk.x, a.chunk.z), a);
            StructureLocate.Found b = StructureLocate.nearest(world, spawn, LocateTests.GRID, 50, true);
            check("the next map leads elsewhere", a != null && b != null && !a.pos.equals(b.pos), b);
            StructureLocate.Found peek = StructureLocate.nearest(world, spawn, LocateTests.GRID, 50, true, false);
            check("a peek skips claims but claims nothing", peek != null && a != null && b != null && !peek.pos.equals(a.pos)
                    && !peek.pos.equals(b.pos) && !StructureClaims.of(world).claimed(LocateTests.GRID, peek.chunk.x, peek.chunk.z), peek);
            StructureLocate.Found again = StructureLocate.nearest(world, spawn, LocateTests.GRID);
            check("/locate ignores claims", all != null && again != null && again.pos.equals(all.pos), again);
        });

        then(1, "GD Lf #1: Exclusive Fullscreen (26.1) and borderless by default (26.3)", world -> {
            com.exiledradio.backportcore.presentation.option.ToggleOption ex = com.exiledradio.backportcore.presentation.Presentation.EXCLUSIVE_FULLSCREEN;
            check("Core registers Exclusive Fullscreen", com.exiledradio.backportcore.presentation.Presentation.option("backportcore:exclusive_fullscreen") == ex, "");
            check("it is 26.1's, on Video Settings after Fullscreen", ex.since().equals(com.exiledradio.backportcore.presentation.GameVersion.parse("26.1"))
                    && "USE_FULLSCREEN".equals(ex.after()) && ex.screen() == com.exiledradio.backportcore.presentation.option.OptionScreen.VIDEO, ex.since());
            check("shown from 26.1, not at 1.21.11", ex.isShownAt(com.exiledradio.backportcore.presentation.GameVersion.parse("26.1"))
                    && !ex.isShownAt(com.exiledradio.backportcore.presentation.GameVersion.parse("1.21.11")), "");
            check("below 26.1 (the dummy versions): no option, exclusive as 1.12.2, not borderless by default",
                    !ex.isShown() && !com.exiledradio.backportcore.presentation.VanillaRevisions.exclusiveFullscreenOption()
                            && !com.exiledradio.backportcore.presentation.VanillaRevisions.borderlessByDefault() && ex.get(), ex.get());
            check("its tooltip follows its value (on)", "options.exclusiveFullscreen.on.tooltip".equals(ex.tooltipKey()), ex.tooltipKey());
            boolean[] newest = {false};
            com.exiledradio.backportcore.presentation.option.ToggleOption t = new com.exiledradio.backportcore.presentation.option.ToggleOption(
                    "backportcore_selftest:follows", "x", com.exiledradio.backportcore.presentation.option.OptionScreen.VIDEO, true);
            t.defaultFollows(() -> !newest[0]).valueTooltips("on.tip", "off.tip");
            check("a followed default: on while the newest version says so", t.get() && !t.chosen() && "on.tip".equals(t.tooltipKey()), "");
            newest[0] = true;
            check("and off once it says otherwise, nothing saved", !t.get() && t.defaultValue() == false && "off.tip".equals(t.tooltipKey())
                    && com.exiledradio.backportcore.presentation.option.OptionStore.get(t.id()) == null, "");
            t.set(true);
            check("set by the player, it keeps the player's value", t.get() && t.chosen()
                    && "true".equals(com.exiledradio.backportcore.presentation.option.OptionStore.get(t.id())), "");
            boolean[] can = {false};
            com.exiledradio.backportcore.presentation.option.ToggleOption u = new com.exiledradio.backportcore.presentation.option.ToggleOption(
                    "backportcore_selftest:available", "x", com.exiledradio.backportcore.presentation.option.OptionScreen.VIDEO, true);
            u.availableWhen(() -> can[0]);
            check("an option that is not available is not shown", !u.isShown(), "");
            can[0] = true;
            check("and is once it is", u.isShown(), "");
            java.util.List<com.exiledradio.backportcore.presentation.VideoSections.Section> sections = com.exiledradio.backportcore.presentation.VideoSections.arrange(
                    java.util.Arrays.asList("GAMMA", "backportcore:exclusive_fullscreen", "GUI_SCALE", "USE_FULLSCREEN"));
            check("Video Settings' Display: GUI Scale, Fullscreen, Exclusive Fullscreen, Brightness",
                    sections.get(0).entries.equals(java.util.Arrays.asList(2, 3, 1, 0)), sections.get(0).entries);
        });

        then(1, "GD stream 7 #4: today's item names", world -> {
            check("cobweb is 1.12.2's web", ModernItems.stack("minecraft:cobweb").getItem() == Item.getItemFromBlock(Blocks.WEB), "");
            ItemStack red = ModernItems.stack("red_dye");
            check("red_dye is dye:1", red.getItem() == Items.DYE && red.getMetadata() == 1, red);
            ItemStack grass = ModernItems.stack("minecraft:short_grass");
            check("short_grass is tallgrass:1", grass.getItem() == Item.getItemFromBlock(Blocks.TALLGRASS) && grass.getMetadata() == 1, grass);
            ItemStack granite = ModernItems.stack("minecraft:polished_granite");
            check("polished_granite is stone:2", granite.getItem() == Item.getItemFromBlock(Blocks.STONE) && granite.getMetadata() == 2, granite);
            check("unchanged names resolve (diamond)", ModernItems.stack("minecraft:diamond").getItem() == Items.DIAMOND, "");
            check("names 1.12.2 lacks are EMPTY (iron_chain)", ModernItems.stack("minecraft:iron_chain").isEmpty(), "");
            ModernItems.map("minecraft:iron_chain", () -> new ItemStack(Blocks.IRON_BARS));
            check("a backport's map wins", ModernItems.stack("minecraft:iron_chain").getItem() == Item.getItemFromBlock(Blocks.IRON_BARS), "");
            check("the table has 1.13's renames", ModernItems.tableSize() > 300, ModernItems.tableSize());
        });

        then(1, "GD stream 1 #2: the age lock's flag and the baby model gate", world -> {
            EntityPig pig = new EntityPig(world);
            check("a mob is not locked by default", !AgeLockable.isLocked(pig), "");
            pig.getEntityData().setBoolean(AgeLockable.LOCKED, true);
            check("the flag in its Forge data locks it", AgeLockable.isLocked(pig), "");
            check("the flag is 26.3's name", "AgeLocked".equals(AgeLockable.LOCKED), AgeLockable.LOCKED);
            boolean has261 = Presentation.installedNewest().isAtLeast("26.1");
            check("baby models follow 26.1 being installed (a server ignores the look switches)",
                    VanillaRevisions.babyModels() == has261, Presentation.installedNewest());
            VanillaRevisions.setBabyModelSwitch(() -> false);
            check("the 26.1 mod's switch off turns them off", !VanillaRevisions.babyModels(), "");
            VanillaRevisions.setBabyModelSwitch(null);
            check("null puts the switch back on", VanillaRevisions.babyModels() == has261, "");
        });
        then(1, "GD stream 7: JigsawPools.placer for wrapping another backport's feature", world -> {
            com.exiledradio.backportcore.structure.JigsawPools.FeaturePlacer p = (w, pos, random, feature) -> { };
            com.exiledradio.backportcore.structure.JigsawPools.feature("backportcore:selftest_wrapped", p);
            check("placer returns the registered placer", com.exiledradio.backportcore.structure.JigsawPools.placer("backportcore:selftest_wrapped") == p, "");
            check("placer of an unregistered id is null", com.exiledradio.backportcore.structure.JigsawPools.placer("backportcore:selftest_none") == null, "");
        });

        then(1, "GD stream 7: an off-map player keeps their facing (P43)", world -> {
            check("P43 applied when MapData loaded", "applied".equals(Asm.state("P43")), Asm.state("P43"));
            boolean had = MapFacing.modern();
            if (!had) {
                MapData plain = offMap(world, 1000.0D, 90.0D);
                MapDecoration d = plain == null ? null : plain.mapDecorations.get("p");
                check("below 26.3 an off-limits marker keeps 1.12.2's rotation 0", d != null && d.getRotation() == 0, d);
                Presentation.version("26.3");
            }
            try {
                MapData far = offMap(world, 1000.0D, 90.0D);
                MapDecoration d = far == null ? null : far.mapDecorations.get("p");
                // 26.3's calculateRotation: (90 + 8) * 16 / 360 = 4.
                check("off-limits marker (1000 blocks out) faces east", d != null && d.getType() == MapDecoration.Type.PLAYER_OFF_LIMITS
                        && d.getRotation() == 4, d);
                MapData near = offMap(world, 200.0D, -90.0D);
                d = near == null ? null : near.mapDecorations.get("p");
                // (-90 - 8) * 16 / 360 = -4 (west).
                check("off-map marker (200 blocks out) faces west", d != null && d.getType() == MapDecoration.Type.PLAYER_OFF_MAP
                        && d.getRotation() == -4, d);
                MapData inside = offMap(world, 10.0D, 90.0D);
                d = inside == null ? null : inside.mapDecorations.get("p");
                check("an on-map marker is unchanged", d != null && d.getType() == MapDecoration.Type.PLAYER && d.getRotation() == 4, d);
                check("26.3's pointed sprites for both off-map markers", MapFacing.spriteOf(MapDecoration.Type.PLAYER_OFF_MAP) != null
                        && MapFacing.spriteOf(MapDecoration.Type.PLAYER_OFF_LIMITS) != null
                        && MapFacing.spriteOf(MapDecoration.Type.PLAYER) == null, "");
            } finally {
                if (!had) {
                    try {
                        java.lang.reflect.Field f = Presentation.class.getDeclaredField("VERSIONS");
                        f.setAccessible(true);
                        ((java.util.Map<?, ?>) f.get(null)).remove(GameVersion.parse("26.3"));
                    } catch (ReflectiveOperationException e) {
                        check("the test's 26.3 is removed", false, e);
                    }
                    check("off-map sprites back to 1.12.2's without 26.3", MapFacing.spriteOf(MapDecoration.Type.PLAYER_OFF_MAP) == null, "");
                }
            }
        });
}
}

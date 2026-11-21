package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.gameevent.GameEvent;
import com.exiledradio.backportcore.gameevent.GameEventTags;
import com.exiledradio.backportcore.locate.StructureClaims;
import com.exiledradio.backportcore.locate.StructureLocate;
import com.exiledradio.backportcore.mechanics.JukeboxSignal;
import com.exiledradio.backportcore.structure.ModernItems;

import net.minecraft.block.BlockJukebox;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemRecord;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * Core 1.4.4's answers to Game Drops Backport's requests (docs/CORE_REQUESTS.md there): the jukebox's signal for a
 * disc that gives its own (P41), {@code addDampener}, {@code GameEvent.BOUNCE}, the exploration map search (radius and
 * claims) and today's item names.
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

    static void schedule() {
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
    }
}

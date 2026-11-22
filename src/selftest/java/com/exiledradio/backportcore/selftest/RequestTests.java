package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.block.ModernWall;
import com.exiledradio.backportcore.copper.Oxidation;
import com.exiledradio.backportcore.ownership.BlockOwnership;
import com.exiledradio.backportcore.ownership.CopyRemapping;
import com.exiledradio.backportcore.ownership.ItemOwnership;
import com.exiledradio.backportcore.presentation.Contribution;
import com.exiledradio.backportcore.presentation.MenuMusic;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * The backports' requests built in Core 1.2.0: ownership of Caves &amp; Cliffs' blocks and of items and effects, the
 * per-name switch, renaming copies when their owner is installed, the shared oxidation table, the modern wall, and
 * menu tracks with a volume and removal by path.
 */
final class RequestTests {

    static ModernWall wall;

    private RequestTests() {
    }

    static void blocks(net.minecraftforge.registries.IForgeRegistry<Block> r) {
        wall = new ModernWall(Blocks.STONE.getDefaultState());
        wall.setRegistryName(SelfTestMod.MOD_ID, "test_modern_wall");
        r.register(wall);
    }

    static void schedule() {
        then(1, "ownership", world -> {
            check("ownership: tuff is Caves & Cliffs'", "cavesandcliffsbackport".equals(BlockOwnership.owner("tuff")));
            check("ownership: waxed oxidized cut copper slab is Caves & Cliffs'",
                    "cavesandcliffsbackport".equals(BlockOwnership.owner("waxed_oxidized_cut_copper_slab")));
            check("ownership: 40 copper blocks plus ore, raw block, tuff and 8 amethyst blocks", countOwned("cavesandcliffsbackport") == 51,
                    countOwned("cavesandcliffsbackport"));
            check("ownership: Caves & Cliffs is not installed, so others copy tuff", BlockOwnership.shouldRegister("trickytrialsbackport", "tuff"));
            BlockOwnership.declare("cavesandcliffsbackport:switched_off", "tuff");
            check("ownership: the owner's pseudo-owner takes its own block", "cavesandcliffsbackport:switched_off".equals(BlockOwnership.owner("tuff")));
            BlockOwnership.declare("trickytrialsbackport", "copper_ore");
            check("ownership: another mod's claim on an owned block is ignored", "cavesandcliffsbackport".equals(BlockOwnership.owner("copper_ore")));
            // The per-name switch, on a block whose owner is installed: this self-test mod.
            BlockOwnership.declare(SelfTestMod.MOD_ID, "test_switchable");
            check("ownership: an installed owner provides", BlockOwnership.ownerProvides("test_switchable"));
            BlockOwnership.provides(SelfTestMod.MOD_ID, () -> false, "test_switchable");
            check("ownership: the per-name switch turns one block off", !BlockOwnership.ownerProvides("test_switchable"));

            check("items: copper ingot is Caves & Cliffs'", "cavesandcliffsbackport".equals(ItemOwnership.itemOwner("copper_ingot")));
            check("items: honeycomb is Buzzy Bees'", "buzzybeesbackport".equals(ItemOwnership.itemOwner("honeycomb")));
            check("items: the ominous bottle is Tricky Trials'", "trickytrialsbackport".equals(ItemOwnership.itemOwner("ominous_bottle")));
            check("effects: Bad Omen is Village & Pillage's", "villagerbackport".equals(ItemOwnership.effectOwner("bad_omen")));
            check("items: absent owner, so a copier registers its own", ItemOwnership.shouldRegisterItem("trickytrialsbackport", "copper_ingot"));
            check("items: no owner and no copy gives null", ItemOwnership.item("copper_ingot", "trickytrialsbackport") == null);
        });
        then(1, "renaming copies", world -> {
            // A family copier's missing name maps onto the owner's registered entry: stone stands in for an owner's block.
            Block stone = Blocks.STONE;
            check("remap: a family copy maps onto the owner's block",
                    CopyRemapping.target(ForgeRegistries.BLOCKS, new ResourceLocation("trickytrialsbackport", "stone"), "minecraft") == stone);
            check("remap: another mod's block of the same name is left alone",
                    CopyRemapping.target(ForgeRegistries.BLOCKS, new ResourceLocation("somemod", "stone"), "minecraft") == null);
            check("remap: nothing without an owner",
                    CopyRemapping.target(ForgeRegistries.BLOCKS, new ResourceLocation("trickytrialsbackport", "stone"), null) == null);
            check("remap: nothing when the owner has no such entry",
                    CopyRemapping.target(ForgeRegistries.BLOCKS, new ResourceLocation("trickytrialsbackport", "no_such_block"), "minecraft") == null);
            // Through Forge's own bus, as a world loads: another family mod's missing copy, an item minecraft owns.
            ItemOwnership.declareItems("minecraft", "stick");
            net.minecraftforge.event.RegistryEvent.MissingMappings.Mapping<net.minecraft.item.Item> copy =
                    new net.minecraftforge.event.RegistryEvent.MissingMappings.Mapping<net.minecraft.item.Item>(
                            ForgeRegistries.ITEMS, ForgeRegistries.ITEMS, new ResourceLocation("trickytrialsbackport", "stick"), 4000);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.RegistryEvent.MissingMappings<net.minecraft.item.Item>(
                    new ResourceLocation("minecraft", "items"), ForgeRegistries.ITEMS, java.util.Collections.singletonList(copy)));
            check("remap: a missing copy under another mod's id becomes the owner's as a world loads",
                    copy.getTarget() == net.minecraft.init.Items.STICK);
        });
        then(1, "oxidation", world -> {
            // Stand-ins for one shape's four stages and two waxed forms.
            Block a = Blocks.HARDENED_CLAY, b = Blocks.CLAY, c = Blocks.BRICK_BLOCK, d = Blocks.NETHER_BRICK;
            Oxidation.chain(a, b, c, d);
            Oxidation.waxed(a, Blocks.QUARTZ_BLOCK);
            check("oxidation: next", Oxidation.next(a) == b && Oxidation.next(d) == null);
            check("oxidation: previous", Oxidation.previous(c) == b && Oxidation.previous(a) == null);
            check("oxidation: stages", Oxidation.stage(a) == 0 && Oxidation.stage(d) == 3 && Oxidation.stage(Blocks.QUARTZ_BLOCK) == -1);
            check("oxidation: waxed and unwaxed", Oxidation.waxedOf(a) == Blocks.QUARTZ_BLOCK && Oxidation.unwaxed(Blocks.QUARTZ_BLOCK) == a);
            check("oxidation: waxed copper is copper", Oxidation.isCopper(Blocks.QUARTZ_BLOCK) && !Oxidation.isCopper(Blocks.DIRT));
            check("oxidation: first", Oxidation.first(d) == a);
            BlockPos p = new BlockPos(8, 220, 8);
            clear(world, p);
            world.setBlockState(p, b.getDefaultState());
            check("oxidation: alone, an exposed block ages at full chance", Math.abs(Oxidation.ageChance(world, p, 1) - 1.0F) < 1e-6,
                    Oxidation.ageChance(world, p, 1));
            check("oxidation: alone, an unaffected block ages at 0.75", Math.abs(Oxidation.ageChance(world, p, 0) - 0.75F) < 1e-6);
            world.setBlockState(p.east(2), a.getDefaultState());
            check("oxidation: a less oxidized block near stops it", Oxidation.ageChance(world, p, 1) == 0.0F);
            world.setBlockState(p.east(2), b.getDefaultState());
            world.setBlockState(p.west(3), c.getDefaultState());
            // One at its stage, one older: ((1 + 1) / (1 + 1 + 1))^2.
            check("oxidation: (2/3)^2 with one same and one older", Math.abs(Oxidation.ageChance(world, p, 1) - 4.0F / 9.0F) < 1e-6,
                    Oxidation.ageChance(world, p, 1));
            world.setBlockState(p.west(3).up(2), a.getDefaultState());
            check("oxidation: beyond a Manhattan distance of 4 nothing counts",
                    Math.abs(Oxidation.ageChance(world, p, 1) - 4.0F / 9.0F) < 1e-6);
            IBlockState stairs = Blocks.STONE_STAIRS.getDefaultState().withProperty(net.minecraft.block.BlockStairs.FACING, net.minecraft.util.EnumFacing.EAST);
            check("oxidation: states carry across by property",
                    Oxidation.copy(stairs, Blocks.OAK_STAIRS).getValue(net.minecraft.block.BlockStairs.FACING) == net.minecraft.util.EnumFacing.EAST);
            clear(world, p);
        });
        then(1, "modern wall", world -> {
            BlockPos p = new BlockPos(40, 220, 8);
            clear(world, p);
            world.setBlockState(p, wall.getDefaultState());
            check("wall: alone it is a post", wall.getActualState(wall.getDefaultState(), world, p).getValue(ModernWall.UP));
            world.setBlockState(p.north(), wall.getDefaultState());
            world.setBlockState(p.south(), wall.getDefaultState());
            IBlockState run = wall.getActualState(wall.getDefaultState(), world, p);
            check("wall: a straight run drops the post", !run.getValue(ModernWall.UP));
            check("wall: low sides on a run", run.getValue(ModernWall.NORTH) == ModernWall.Side.LOW && run.getValue(ModernWall.SOUTH) == ModernWall.Side.LOW);
            world.setBlockState(p.up(), Blocks.STONE.getDefaultState());
            IBlockState tall = wall.getActualState(wall.getDefaultState(), world, p);
            check("wall: a solid block above makes the sides tall", tall.getValue(ModernWall.NORTH) == ModernWall.Side.TALL);
            world.setBlockState(p.east(), Blocks.COBBLESTONE_WALL.getDefaultState());
            check("wall: joins 1.12.2's walls", wall.getActualState(wall.getDefaultState(), world, p).getValue(ModernWall.EAST) != ModernWall.Side.NONE);
            check("wall: metadata is always 0", wall.getMetaFromState(tall) == 0);
            clear(world, p);
        });
        then(1, "menu tracks", world -> {
            List<MenuMusic.Track> base = new ArrayList<MenuMusic.Track>();
            base.add(new MenuMusic.Track("minecraft:music/menu/menu1", 1));
            Contribution older = Presentation.detached("1.18").addMenuMusic("cavesandcliffsbackport:music/game/wending", 1);
            Contribution newer = Presentation.detached("1.21.6").removeMenuMusic("music/game/wending")
                    .addMenuMusic("x:music/game/lilypad", 1, 0.4F);
            List<MenuMusic.Track> pool = MenuMusic.pool(base, Arrays.asList(older, newer));
            List<String> names = new ArrayList<String>();
            for (MenuMusic.Track t : pool) {
                names.add(t.soundName);
            }
            check("menu: a path removes another mod's track", names.equals(Arrays.asList("minecraft:music/menu/menu1", "x:music/game/lilypad")), names);
            check("menu: a track keeps its volume", Math.abs(pool.get(1).volume - 0.4F) < 1e-6, pool.get(1).volume);
            Contribution exact = Presentation.detached("1.21.6").removeMenuMusic("x:music/game/wending");
            check("menu: a full name removes only that mod's",
                    MenuMusic.pool(MenuMusic.pool(base, Arrays.asList(older)), Arrays.asList(exact)).size() == 2);
        });
    }

    static void schedulePresentation() {
        then(1, "1.21 presentation options", world -> {
            java.util.Random r = new java.util.Random(1);
            int game = MenuMusic.frequencyDelay("default", 12000, 24000, r);
            check("frequency: Default keeps 1.12.2's game wait", game >= 12000 && game <= 24000, game);
            check("frequency: Frequent caps at 10 minutes", MenuMusic.frequencyDelay("frequent", 12000, 24000, r) == 12000);
            check("frequency: Constant is 5 seconds", MenuMusic.frequencyDelay("constant", 12000, 24000, r) == 100);
            int menu = MenuMusic.frequencyDelay("frequent", 20, 600, r);
            check("frequency: a short wait stays its own", menu >= 20 && menu <= 600, menu);
            check("options: Music Frequency since 1.21.6", "1.21.6".equals(String.valueOf(Presentation.MUSIC_FREQUENCY.since())));
            check("options: Music Toast tooltip per value", "options.musicToast.never.tooltip".equals(Presentation.MUSIC_TOAST.tooltipKey()),
                    Presentation.MUSIC_TOAST.tooltipKey());
            Presentation.MUSIC_TOAST.set("pause_menu");
            check("options: Music Toast tooltip follows its value", "options.musicToast.pauseMenu.tooltip".equals(Presentation.MUSIC_TOAST.tooltipKey()));
            Presentation.MUSIC_TOAST.set("never");
            check("options: toggles since 1.21.9", "1.21.9".equals(String.valueOf(Presentation.TOGGLE_ATTACK.since()))
                    && "hold".equals(Presentation.TOGGLE_USE.get()));
            check("options: UI volume registered", Presentation.option("backportcore:ui_volume") == Presentation.UI_VOLUME);
        });
    }

    private static int countOwned(String owner) {
        int n = 0;
        for (String o : BlockOwnership.table().values()) {
            n += o.startsWith(owner) ? 1 : 0;
        }
        return n;
    }

    private static void clear(net.minecraft.world.WorldServer world, BlockPos center) {
        for (BlockPos q : BlockPos.getAllInBox(center.add(-5, -5, -5), center.add(5, 5, 5))) {
            world.setBlockToAir(q);
        }
    }
}

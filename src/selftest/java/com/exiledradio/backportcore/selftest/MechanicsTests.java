package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.map.MapIcons;
import com.exiledradio.backportcore.mechanics.BlockRules;

import net.minecraft.block.BlockPane;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.MapData;
import net.minecraft.world.storage.MapDecoration;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/** Map icons newer than 1.12.2 (map/, P34) and torches on panes (mechanics/, P35). */
final class MechanicsTests {

    static boolean panesOn;

    private MechanicsTests() {
    }

    static void register() {
        BlockRules.torchesOnPanes(() -> panesOn);
    }

    static void schedule() {
        then(1, "map icons", world -> {
            MapDecoration.Type plains = MapIcons.get("plains_village", MapIcons.LIGHT_GRAY);
            check("map icon added past vanilla's ten", plains.ordinal() >= 10 && plains.getIcon() == (byte) plains.ordinal(), plains.ordinal());
            check("map icon asked again: the same type", MapIcons.get("plains_village", MapIcons.LIGHT_GRAY) == plains, "");
            check("map icon: found by name", MapIcons.find("plains_village") == plains, "");
            check("map icon: colors the map item", plains.hasMapColor() && plains.getMapColor() == MapIcons.LIGHT_GRAY, plains.getMapColor());
            check("map icon: byIcon finds it", MapDecoration.Type.byIcon(plains.getIcon()) == plains, MapDecoration.Type.byIcon(plains.getIcon()));
            check("map icon: vanilla types have no sprite", MapIcons.sprite(MapDecoration.Type.MANSION) == null, "");
            check("map icon: sprite is in Core's jar",
                    MechanicsTests.class.getResource("/assets/backportcore/textures/map/decorations/plains_village.png") != null, MapIcons.sprite(plains));
            for (String name : new String[] {"desert_village", "savanna_village", "snowy_village", "taiga_village", "swamp_hut",
                    "jungle_temple", "trial_chambers", "red_x"}) {
                check("map icon sprite shipped: " + name,
                        MechanicsTests.class.getResource("/assets/backportcore/textures/map/decorations/" + name + ".png") != null, "");
            }
            check("map icon: no renderer on a dedicated server, vanilla's answer", !MapIcons.render(new MapDecoration(plains, (byte) 0, (byte) 0, (byte) 0), 0), "");
            // A map carrying one saves and reads its type back.
            ItemStack map = ItemMap.setupNewMap(world, 0, 0, (byte) 2, true, true);
            MapData.addTargetDecoration(map, new BlockPos(10, 64, 10), "+", plains);
            NBTTagList decorations = map.getTagCompound().getTagList("Decorations", 10);
            NBTTagCompound first = decorations.getCompoundTagAt(0);
            check("map icon: saved by its number", first.getByte("type") == plains.getIcon(), first);
            check("map icon: map item tinted", map.getTagCompound().getCompoundTag("display").getInteger("MapColor") == MapIcons.LIGHT_GRAY, map.getTagCompound());
            check("map icon: map is a filled map", map.getItem() == Items.FILLED_MAP, map);
        });

        then(1, "torches on panes and bars (P35)", world -> {
            check("P35 applied", "applied".equals(Asm.state("P35")), Asm.state("P35"));
            boolean declared = false;
            for (java.lang.reflect.Method m : BlockPane.class.getDeclaredMethods()) {
                declared |= "canPlaceTorchOnTop".equals(m.getName());
            }
            check("BlockPane declares canPlaceTorchOnTop", declared, "");
            BlockPos at = world.getSpawnPoint().add(40, 0, 40);
            at = new BlockPos(at.getX(), 200, at.getZ());
            world.setBlockState(at, Blocks.GLASS_PANE.getDefaultState(), 2);
            world.setBlockState(at.up(), Blocks.AIR.getDefaultState(), 2);
            panesOn = false;
            check("off: no torch on a glass pane (1.12.2)", !Blocks.TORCH.canPlaceBlockAt(world, at.up()), "");
            check("off: the pane answers as Block does", !Blocks.GLASS_PANE.canPlaceTorchOnTop(Blocks.GLASS_PANE.getDefaultState(), world, at), "");
            check("off: glass keeps its torch (inherited rule)", Blocks.GLASS.canPlaceTorchOnTop(Blocks.GLASS.getDefaultState(), world, at), "");
            panesOn = true;
            check("on: torch on a glass pane", Blocks.TORCH.canPlaceBlockAt(world, at.up()), "");
            world.setBlockState(at, Blocks.IRON_BARS.getDefaultState(), 2);
            check("on: torch on iron bars", Blocks.TORCH.canPlaceBlockAt(world, at.up()), "");
            check("on: redstone torch on iron bars", Blocks.REDSTONE_TORCH.canPlaceBlockAt(world, at.up()), "");
            world.setBlockState(at.up(), Blocks.TORCH.getDefaultState(), 3);
            world.notifyNeighborsOfStateChange(at, Blocks.IRON_BARS, false);
            check("on: the torch stays", world.getBlockState(at.up()).getBlock() == Blocks.TORCH, world.getBlockState(at.up()));
            world.setBlockState(at, Blocks.STAINED_GLASS_PANE.getDefaultState(), 2);
            check("on: torch on stained glass pane", Blocks.TORCH.canPlaceBlockAt(world, at.up()), "");
            panesOn = false;
            world.setBlockState(at.up(), Blocks.AIR.getDefaultState(), 2);
            world.setBlockState(at, Blocks.AIR.getDefaultState(), 2);
        });
    }
}

package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.map.MapIcons;
import com.exiledradio.backportcore.mechanics.BlockRules;

import net.minecraft.block.BlockPane;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapData;
import net.minecraft.world.storage.MapDecoration;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/** Map icons newer than 1.12.2 (map/, P34), torches on panes (mechanics/, P35) and gliding straight up (P79). */
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

        then(1, "elytra looking straight up (P79)", world -> {
            check("P79 applied", "applied".equals(Asm.state("P79")), Asm.state("P79"));
            BlockPos spawn = world.getSpawnPoint();
            // OptiFine's Fast Math gives this look at pitch -90: no horizontal part at all.
            Glider up = new Glider(world, new Vec3d(0.0D, 1.0D, 0.0D));
            up.setLocationAndAngles(spawn.getX() + 0.5D, 250.0D, spawn.getZ() + 0.5D, 0.0F, -90.0F);
            up.motionX = 0.8D;
            up.motionY = 0.1D;
            up.motionZ = -1.3D;
            up.travel(0.0F, 0.0F, 0.0F);
            check("gliding straight up with speed: motion stays finite", finite(up.motionX) && finite(up.motionY) && finite(up.motionZ),
                    up.motionX + " " + up.motionY + " " + up.motionZ);
            check("gliding straight up with speed: position stays finite", finite(up.posX) && finite(up.posY) && finite(up.posZ),
                    up.posX + " " + up.posY + " " + up.posZ);

            // Any other look: exactly 1.12.2's motion.
            Glider level = new Glider(world, null);
            level.setLocationAndAngles(spawn.getX() + 0.5D, 250.0D, spawn.getZ() + 0.5D, 37.0F, -30.0F);
            level.motionX = 0.8D;
            level.motionY = -0.2D;
            level.motionZ = -1.3D;
            double[] want = vanillaGlide(level);
            level.travel(0.0F, 0.0F, 0.0F);
            check("gliding at pitch -30: 1.12.2's motion", level.motionX == want[0] && level.motionY == want[1] && level.motionZ == want[2],
                    level.motionX + " " + level.motionY + " " + level.motionZ + " vs " + want[0] + " " + want[1] + " " + want[2]);
        });
    }

    private static boolean finite(double d) {
        return !Double.isNaN(d) && !Double.isInfinite(d);
    }

    /** 1.12.2's elytra motion for one tick of {@code EntityLivingBase.travel} in open air, before the move. */
    private static double[] vanillaGlide(EntityLivingBase e) {
        Vec3d look = e.getLookVec();
        float f = e.rotationPitch * 0.017453292F;
        double mx = e.motionX;
        double my = e.motionY;
        double mz = e.motionZ;
        double d6 = Math.sqrt(look.x * look.x + look.z * look.z);
        double d8 = Math.sqrt(mx * mx + mz * mz);
        double d1 = look.length();
        float f4 = MathHelper.cos(f);
        f4 = (float) ((double) f4 * (double) f4 * Math.min(1.0D, d1 / 0.4D));
        my += -0.08D + (double) f4 * 0.06D;
        if (my < 0.0D && d6 > 0.0D) {
            double d2 = my * -0.1D * (double) f4;
            my += d2;
            mx += look.x * d2 / d6;
            mz += look.z * d2 / d6;
        }
        if (f < 0.0F) {
            double d10 = d8 * (double) (-MathHelper.sin(f)) * 0.04D;
            my += d10 * 3.2D;
            mx -= look.x * d10 / d6;
            mz -= look.z * d10 / d6;
        }
        if (d6 > 0.0D) {
            mx += (look.x / d6 * d8 - mx) * 0.1D;
            mz += (look.z / d6 * d8 - mz) * 0.1D;
        }
        return new double[] {mx * 0.9900000095367432D, my * 0.9800000190734863D, mz * 0.9900000095367432D};
    }

    /** A gliding mob, not in the world, whose look can be set exactly. */
    private static final class Glider extends EntityZombie {

        private final Vec3d look;

        Glider(World world, Vec3d look) {
            super(world);
            this.look = look;
        }

        @Override
        public boolean isElytraFlying() {
            return true;
        }

        @Override
        public Vec3d getLookVec() {
            return this.look != null ? this.look : super.getLookVec();
        }
    }
}

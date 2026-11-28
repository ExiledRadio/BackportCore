package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.util.VanillaTileEntityIds;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * {@code util/VanillaTileEntityIds}: with nothing installed every vanilla tile entity id is vanilla's; a class
 * registered under {@code mob_spawner} (as a mod with an un-namespaced id does) makes vanilla spawners fail to save
 * and is named, with where it came from; registering vanilla's class again puts things back.
 */
final class TileEntityIdTests {

    /** Stands in for another mod's spawner registered under a vanilla id. */
    public static final class Hijacker extends TileEntityMobSpawner {
    }

    private TileEntityIdTests() {
    }

    private static boolean saves() {
        try {
            new TileEntityMobSpawner().writeToNBT(new NBTTagCompound());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    static void schedule() {
        then(1, "Tile entity ids: vanilla's", world -> {
            List<String> clean = VanillaTileEntityIds.problems();
            check("every vanilla tile entity id names its vanilla class", clean.isEmpty(), clean);
            check("a vanilla spawner saves", saves(), "");
            GameRegistry.registerTileEntity(Hijacker.class, "mob_spawner");
            try {
                check("a class under minecraft:mob_spawner makes vanilla spawners fail to save", !saves(), "");
                List<String> found = VanillaTileEntityIds.problems();
                String line = found.isEmpty() ? "" : found.get(0);
                check("the check finds the one id taken", found.size() == 1 && line.contains("minecraft:mob_spawner"), found);
                check("it names the class that took it", line.contains(Hijacker.class.getName()), line);
                check("and where that class came from", !line.contains("unknown jar"), line);
                VanillaTileEntityIds.check("in the self-test");
            } finally {
                GameRegistry.registerTileEntity(TileEntityMobSpawner.class, "minecraft:mob_spawner");
            }
            check("vanilla's class registered again saves", saves(), "");
            check("and the check is clean again", VanillaTileEntityIds.problems().isEmpty(), VanillaTileEntityIds.problems());
        });
    }
}

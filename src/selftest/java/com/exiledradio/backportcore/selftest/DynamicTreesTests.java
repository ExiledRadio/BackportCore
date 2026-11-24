package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.compat.DynamicTrees;
import net.minecraft.init.Biomes;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.Random;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * compat/DynamicTrees: the decoration answer by the decorating biome, the species for today's tree features, and that
 * nothing changes without Dynamic Trees.
 */
final class DynamicTreesTests {

    private DynamicTreesTests() {
    }

    static void schedule() {
        then(1, "dynamic trees: decoration answer", world -> {
            check("dt decide: decorating biome replaced -> DENY", DynamicTrees.decide(false, true, Event.Result.DEFAULT) == Event.Result.DENY);
            check("dt decide: both replaced -> DENY", DynamicTrees.decide(true, true, Event.Result.DENY) == Event.Result.DENY);
            check("dt decide: corner's DENY lifted", DynamicTrees.decide(true, false, Event.Result.DENY) == Event.Result.DEFAULT);
            check("dt decide: another mod's DENY kept", DynamicTrees.decide(false, false, Event.Result.DENY) == Event.Result.DENY);
            check("dt decide: neither -> unchanged", DynamicTrees.decide(false, false, Event.Result.DEFAULT) == Event.Result.DEFAULT);
            check("dt decide: ALLOW kept where not replaced", DynamicTrees.decide(true, false, Event.Result.ALLOW) == Event.Result.ALLOW);
        });
        then(1, "dynamic trees: species", world -> {
            String[][] cases = {
                    {"minecraft:oak_checked", "oak"}, {"minecraft:fancy_oak_bees_002", "oak"}, {"normal_tree", "oak"},
                    {"minecraft:birch_bees_002", "birch"}, {"minecraft:super_birch_bees_0002", "tallbirch"},
                    {"minecraft:spruce_on_snow", "spruce"}, {"pine_tree", "spruce"}, {"minecraft:mega_pine_checked", "megaspruce"},
                    {"minecraft:jungle_tree", "jungle"}, {"minecraft:mega_jungle_tree_checked", "megajungle"},
                    {"savanna_tree", "acacia"}, {"minecraft:dark_oak_checked", "darkoak"},
                    {"minecraft:cherry_checked", null}, {"minecraft:pale_oak_checked", null}, {"minecraft:red_poplar", null},
            };
            for (String[] c : cases) {
                String got = DynamicTrees.species(c[0]);
                check("dt species " + c[0], c[1] == null ? got == null : c[1].equals(got), got);
            }
        });
        // Dynamic Trees needs Forge 2859 or later and never loads here: selftest/dynamictrees/DynamicTreesCheck checks
        // it installed, on a real server.
        then(1, "dynamic trees: absent", world -> {
            check("dt absent: not loaded", !DynamicTrees.loaded() && !Loader.isModLoaded(DynamicTrees.MOD_ID));
            check("dt absent: replaces nothing", !DynamicTrees.replacesTrees(world, Biomes.FOREST));
            check("dt absent: no cactus answer", !DynamicTrees.replacesCactus(world, world.getSpawnPoint()));
            check("dt absent: grows nothing", !DynamicTrees.grow(world, world.getSpawnPoint().up(), new Random(1), "minecraft:oak_checked"));
        });
    }
}

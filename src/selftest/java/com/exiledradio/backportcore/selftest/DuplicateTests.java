package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.duplicate.DuplicateBlocks;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Random;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/** CORE_REQUESTS (Nether section 6): items that refuse a null tab, and another mod's world generator. */
final class DuplicateTests {

    private DuplicateTests() {
    }

    /** As a Kotlin item whose setCreativeTab parameter is non-null. */
    static final class RefusingItem extends Item {
        @Override
        public Item setCreativeTab(CreativeTabs tab) {
            if (tab == null) {
                throw new IllegalArgumentException("Parameter specified as non-null is null: method setCreativeTab, parameter tab");
            }
            return super.setCreativeTab(tab);
        }
    }

    /** Stands in for another mod's ore generator. */
    static final class TheirGenerator implements IWorldGenerator {
        @Override
        public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator gen, IChunkProvider provider) {
        }
    }

    static final class KeptGenerator implements IWorldGenerator {
        @Override
        public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator gen, IChunkProvider provider) {
        }
    }

    static void schedule() {
        then(1, "duplicates: refusing items and generators", world -> {
            RefusingItem item = new RefusingItem();
            item.setCreativeTab(CreativeTabs.MISC);
            boolean hidden;
            try {
                hidden = DuplicateBlocks.hideItem(item);
            } catch (RuntimeException e) {
                hidden = false;
            }
            Field tab = ReflectionHelper.findField(Item.class, "tabToDisplayOn", "field_77701_a");
            check("dup: an item refusing setCreativeTab(null) is hidden through the field, no throw", hidden && tab.get(item) == null, tab.get(item));
            Item plain = new Item().setCreativeTab(CreativeTabs.MISC);
            check("dup: a plain item is hidden by setCreativeTab(null)", DuplicateBlocks.hideItem(plain) && tab.get(plain) == null, tab.get(plain));

            GameRegistry.registerWorldGenerator(new TheirGenerator(), 5);
            GameRegistry.registerWorldGenerator(new TheirGenerator(), 6);
            int before = generators(TheirGenerator.class.getName());
            // Their mod is "installed" (this one), and their half is the hidden one.
            DuplicateBlocks.registerGenerator("backportcore", SelfTestMod.MOD_ID, () -> DuplicateBlocks.Keep.OURS, TheirGenerator.class.getName());
            DuplicateBlocks.removeGenerators();
            int after = generators(TheirGenerator.class.getName());
            check("dup: registerGenerator takes the hidden mod's generators out of GameRegistry", before == 2 && after == 0, before + " -> " + after);
            GameRegistry.registerWorldGenerator(new KeptGenerator(), 5);
            DuplicateBlocks.registerGenerator("backportcore", "no_such_mod", () -> DuplicateBlocks.Keep.OURS, KeptGenerator.class.getName());
            DuplicateBlocks.registerGenerator("backportcore", SelfTestMod.MOD_ID, () -> DuplicateBlocks.Keep.THEIRS, KeptGenerator.class.getName());
            DuplicateBlocks.removeGenerators();
            check("dup: a generator stays when its mod is missing or its half is the kept one", generators(KeptGenerator.class.getName()) == 1, "");
            DuplicateBlocks.removeGenerators(KeptGenerator.class.getName());
            check("dup: removeGenerators(name) counts what it took", DuplicateBlocks.removeGenerators("no.such.Generator") == 0, "");
            // The decoration list still works after the sorted list was reset.
            world.getChunk(world.getSpawnPoint().add(400, 0, 400));
            check("dup: chunks still decorate after the list is rebuilt", true, "");
        });
    }

    @SuppressWarnings("unchecked")
    private static int generators(String className) throws Exception {
        Field f = GameRegistry.class.getDeclaredField("worldGenerators");
        f.setAccessible(true);
        int n = 0;
        for (IWorldGenerator g : (Collection<IWorldGenerator>) f.get(null)) {
            if (g.getClass().getName().equals(className)) {
                n++;
            }
        }
        return n;
    }
}

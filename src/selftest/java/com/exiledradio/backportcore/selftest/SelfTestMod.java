package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.waterlog.Waterloggable;
import com.exiledradio.backportcore.waterlog.Waterlogging;

import net.minecraft.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Headless tests of Backport Core, compiled in only by {@code gradlew runServer -Pselftest}. Runs
 * once the dedicated server has started, logs {@code SELFTEST DONE pass=N fail=M}, and stops the
 * server.
 */
@Mod(modid = SelfTestMod.MOD_ID, name = "Backport Core self-test", version = "1",
        dependencies = "required-after:backportcore", acceptableRemoteVersions = "*")
public final class SelfTestMod {

    static final String MOD_ID = "backportcore_selftest";

    public SelfTestMod() {
        MinecraftForge.EVENT_BUS.register(this);
        PoseTests.register();
        MechanicsTests.register();
        PresentationTests.register();
        SettingsTests.register();
        if (FMLCommonHandler.instance().getSide().isClient()) {
            // 1.19.4's accessibility onboarding takes the first title screen: only the menu shots ask for it.
            OptionsRowsShots.onboardingWanted(ClientMenuShots.active());
            if (ClientSuppliedTests.active()) {
                ClientSuppliedTests.register();
            } else if (ClientOptiFineTests.active()) {
                ClientOptiFineTests.register();
            } else if (ClientMenuShots.active()) {
                ClientMenuShots.register();
            } else if (ClientVideoShots.active()) {
                ClientVideoShots.register();
            } else if (ClientSettingsShots.active()) {
                ClientSettingsShots.register();
            } else if (ClientTextureTests.version() != null) {
                ClientTextureTests.register();
            } else {
                ClientPresentationTests.register();
                ClientLocateTests.register();
            }
        }
    }

    @SubscribeEvent
    public void onBlocks(RegistryEvent.Register<Block> event) {
        TestBlocks.optional = new TestBlocks.Post("optional", Waterloggable.Need.OPTIONAL, true);
        TestBlocks.required = new TestBlocks.Post("required", Waterloggable.Need.REQUIRED, true);
        TestBlocks.dry = new TestBlocks.Post("dry", Waterloggable.Need.OPTIONAL, false);
        TestBlocks.plant = new TestBlocks.Post("plant", Waterloggable.Need.OPTIONAL, true, net.minecraft.block.material.Material.PLANTS);
        TestBlocks.legacy = new TestBlocks.Legacy();
        TestBlocks.slab = new TestBlocks.Slab.Half();
        TestBlocks.doubleSlab = new TestBlocks.Slab.Double();
        TestBlocks.dryStairs = new TestBlocks.Stairs("test_dry_stairs");
        TestBlocks.waterStairs = new TestBlocks.WaterStairs();
        event.getRegistry().registerAll(TestBlocks.optional, TestBlocks.required, TestBlocks.dry, TestBlocks.plant, TestBlocks.legacy,
                TestBlocks.slab, TestBlocks.doubleSlab, TestBlocks.dryStairs, TestBlocks.waterStairs);
        RequestTests.blocks(event.getRegistry());
    }

    @SubscribeEvent
    public void onItems(RegistryEvent.Register<net.minecraft.item.Item> event) {
        RecipeTests.items(event.getRegistry());
    }

    @SubscribeEvent
    public void onRecipes(RegistryEvent.Register<net.minecraft.item.crafting.IRecipe> event) {
        RecipeTests.recipes(event.getRegistry());
    }

    @SubscribeEvent
    public void onConfigChanged(net.minecraftforge.fml.client.event.ConfigChangedEvent.OnConfigChangedEvent event) {
        if (MOD_ID.equals(event.getModID())) {
            SettingsTests.changedEvents++;
        }
    }

    /** Before Core reads today's data files (load complete): the data-structure tests' drop-in packs. */
    @Mod.EventHandler
    public void preInit(net.minecraftforge.fml.common.event.FMLPreInitializationEvent event) {
        DataStructureTests.preInit();
        SuppliedTests.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Waterlogging.migrateStoredProperty(TestBlocks.legacy, TestBlocks.Legacy.WATERLOGGED);
        StructureTests.init();
        SnowTests.init();
        NetherTests.init();
        LocateTests.init();
        FlatTests.init();
        BiomeLocateTests.init();
        PoiLocateTests.init();
    }

    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) {
        // Only a dedicated server runs the tests (and stops itself after them); a dev client's world does not.
        if (FMLCommonHandler.instance().getSide().isServer()) {
            // The test server has no players: with a simulation distance only the spawn chunks would tick. The tests
            // that want one set it themselves (SimulationTests).
            com.exiledradio.backportcore.simulation.SimulationDistance.set(0);
            MinecraftForge.EVENT_BUS.register(new SelfTestRunner());
        }
    }
}

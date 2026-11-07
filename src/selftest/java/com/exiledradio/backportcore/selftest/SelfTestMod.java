package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.waterlog.Waterloggable;
import com.exiledradio.backportcore.waterlog.Waterlogging;

import net.minecraft.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
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
    }

    @SubscribeEvent
    public void onBlocks(RegistryEvent.Register<Block> event) {
        TestBlocks.optional = new TestBlocks.Post("optional", Waterloggable.Need.OPTIONAL, true);
        TestBlocks.required = new TestBlocks.Post("required", Waterloggable.Need.REQUIRED, true);
        TestBlocks.dry = new TestBlocks.Post("dry", Waterloggable.Need.OPTIONAL, false);
        TestBlocks.legacy = new TestBlocks.Legacy();
        event.getRegistry().registerAll(TestBlocks.optional, TestBlocks.required, TestBlocks.dry, TestBlocks.legacy);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Waterlogging.migrateStoredProperty(TestBlocks.legacy, TestBlocks.Legacy.WATERLOGGED);
        StructureTests.init();
    }

    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) {
        MinecraftForge.EVENT_BUS.register(new SelfTestRunner());
    }
}

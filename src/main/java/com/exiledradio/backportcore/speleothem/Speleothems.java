package com.exiledradio.backportcore.speleothem;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

/**
 * Registers what {@link BlockSpeleothem}s need: the falling segment ({@code backportcore:falling_speleothem}, Core's
 * entity id 0) and the events. Always registered: an entity entry costs nothing, and a world's falling segments must
 * load wherever they were saved.
 */
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID)
public final class Speleothems {

    private Speleothems() {
    }

    public static void preInit() {
        MinecraftForge.EVENT_BUS.register(new SpeleothemEvents());
    }

    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().register(EntityEntryBuilder.create()
                .entity(EntityFallingSpeleothem.class)
                .id(new ResourceLocation(BackportCore.MOD_ID, "falling_speleothem"), 0)
                .name(BackportCore.MOD_ID + ".falling_speleothem")
                .tracker(10, 20, true)
                .build());
    }
}

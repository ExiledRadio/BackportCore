package com.exiledradio.backportcore.presentation.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** The client side of the presentation module: Core's pre-init calls {@link #init()} on a client only. */
@SideOnly(Side.CLIENT)
public final class PresentationClient {

    private PresentationClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new Events());
        MinecraftForge.EVENT_BUS.register(new OptionsInjector());
    }

    /** Forge event handlers; public for the event bus. */
    public static final class Events {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                WorldLoadingScreen.install(Minecraft.getMinecraft());
            }
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public void onGuiOpen(GuiOpenEvent event) {
            WorldLoadingScreen.install(Minecraft.getMinecraft());
        }
    }
}

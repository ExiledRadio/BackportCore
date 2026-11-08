package com.exiledradio.backportcore.presentation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
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
        MinecraftForge.EVENT_BUS.register(new MenuMusicPlayer());
    }

    /** Forge event handlers; public for the event bus. */
    public static final class Events {

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                Minecraft mc = Minecraft.getMinecraft();
                WorldLoadingScreen.install(mc);
                if (mc.currentScreen instanceof GuiMainMenu) {
                    ClientDefaults.applyOnce(mc);
                }
            }
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public void onGuiOpen(GuiOpenEvent event) {
            WorldLoadingScreen.install(Minecraft.getMinecraft());
            if (event.getGui() instanceof GuiMainMenu) {
                TitleSplashes.pick((GuiMainMenu) event.getGui());
            }
        }

        @SubscribeEvent
        public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
            if (event.getGui() instanceof GuiMainMenu) {
                TitleSplashes.afterInit((GuiMainMenu) event.getGui());
            }
        }
    }
}

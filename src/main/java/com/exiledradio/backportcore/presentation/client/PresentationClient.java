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
        MinecraftForge.EVENT_BUS.register(new FogPipeline.Events());
        MinecraftForge.EVENT_BUS.register(new ChatRenderer.Events());
        MinecraftForge.EVENT_BUS.register(new StartupScreen.Events());
        MinecraftForge.EVENT_BUS.register(new TitleScreenExtras());
        MinecraftForge.EVENT_BUS.register(new MenuScreens());
        MinecraftForge.EVENT_BUS.register(new CreativeOrder());
        MinecraftForge.EVENT_BUS.register(new DebugText.Events());
        MinecraftForge.EVENT_BUS.register(new GameMusic.Events());
        MinecraftForge.EVENT_BUS.register(new MusicClient());
        ChatSuggestions.init();
        MinecraftForge.EVENT_BUS.register(new ControlToggles());
        com.exiledradio.backportcore.presentation.Presentation.MUSIC_FREQUENCY.onChange(f -> GameMusic.frequencyChanged());
        MinecraftForge.EVENT_BUS.register(new OptionTooltips());
        MinecraftForge.EVENT_BUS.register(new WorldBackups());
        MinecraftForge.EVENT_BUS.register(new RenderOptions.Events());
        MinecraftForge.EVENT_BUS.register(new DebugScreen.Events());
        DebugScreen.init();
        RenderOptions.init();
    }

    /** From Core's load-complete event: the backports' vanilla text and assets take effect. */
    public static void loadComplete() {
        Minecraft mc = Minecraft.getMinecraft();
        VanillaAssetPack.afterLoading(mc);
        // The game's last resource load ran before the mods: the startup screen fades from here into the title.
        StartupScreen.INSTANCE.onResourceManagerReload(mc.getResourceManager());
    }

    /**
     * From Core's init: the startup screen listens for the end of every resource load from here on (F3+T, the
     * resource packs screen).
     */
    public static void initLate() {
        net.minecraft.client.resources.IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
        if (rm instanceof net.minecraft.client.resources.IReloadableResourceManager) {
            ((net.minecraft.client.resources.IReloadableResourceManager) rm).registerReloadListener(StartupScreen.INSTANCE);
            ((net.minecraft.client.resources.IReloadableResourceManager) rm).registerReloadListener(m -> TitleLogo.invalidate());
        }
        // Every backport has said its version by now: 1.21.11's leaves, debug key binds and texture filtering.
        VideoLayout.initLeaves(Minecraft.getMinecraft());
        DebugBindings.register();
        RenderOptions.applyAnisotropy();
        VideoOptions.applyForced(Minecraft.getMinecraft());
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

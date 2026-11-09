package com.exiledradio.backportcore.settings.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.settings.BackportSettings;
import com.exiledradio.backportcore.settings.Family;
import com.exiledradio.backportcore.settings.SettingsModel;
import com.exiledradio.backportcore.settings.SettingsNetwork;

import com.google.common.collect.BiMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The client half of the settings screen: what the server last said, Mod Options' Config buttons, applying the
 * screen's changes, and the restart line on the title and pause screens.
 */
@SideOnly(Side.CLIENT)
public final class SettingsClient {

    private static Map<String, String[]> serverValues;
    private static boolean serverCanEdit;
    private static boolean waitingForServer;
    /** The open screen, told when the server answers. */
    static SettingsScreen open;

    private SettingsClient() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new Events());
    }

    // --- the server ------------------------------------------------------------------------------------------

    /** @return whether the game is connected to a server that is not this game's own */
    public static boolean remote() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.world != null && !mc.isSingleplayer();
    }

    /** Asks the server for its values (the screen opening on a remote server). */
    static void askServer() {
        serverValues = null;
        serverCanEdit = false;
        waitingForServer = true;
        SettingsNetwork.requestValues();
    }

    /** @return the server's values, or null when it has not answered (or has no Core) */
    static Map<String, String[]> serverValues() {
        return serverValues;
    }

    static boolean serverCanEdit() {
        return serverCanEdit;
    }

    static boolean waitingForServer() {
        return waitingForServer;
    }

    public static void receiveValues(boolean canEdit, Map<String, String[]> values) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            serverValues = Collections.unmodifiableMap(new LinkedHashMap<String, String[]>(values));
            serverCanEdit = canEdit;
            waitingForServer = false;
            if (open != null) {
                open.serverAnswered();
            }
        });
    }

    public static void receiveResult(byte status, int applied, int restart, int rejected) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.player == null) {
                return;
            }
            if (status == SettingsNetwork.Result.NOT_ALLOWED) {
                mc.player.sendMessage(red("backportcore.settings.server.notAllowed"));
                return;
            }
            if (applied > 0) {
                mc.player.sendMessage(new TextComponentTranslation(restart > 0 ? "backportcore.settings.server.savedRestart" : "backportcore.settings.server.saved",
                        applied, restart));
            }
            if (rejected > 0) {
                mc.player.sendMessage(red("backportcore.settings.server.rejected", rejected));
            }
        });
    }

    private static TextComponentTranslation red(String key, Object... args) {
        TextComponentTranslation t = new TextComponentTranslation(key, args);
        t.getStyle().setColor(TextFormatting.RED);
        return t;
    }

    // --- applying ---------------------------------------------------------------------------------------------

    /**
     * Applies the screen's changes: this game's own settings to its files, and on a remote server the server's
     * settings to the server (an operator's only).
     */
    static void apply(Map<SettingsModel.Entry, String[]> changes) {
        Map<SettingsModel.Entry, String[]> local = new LinkedHashMap<SettingsModel.Entry, String[]>();
        Map<String, String[]> toServer = new LinkedHashMap<String, String[]>();
        boolean remote = remote();
        for (Map.Entry<SettingsModel.Entry, String[]> c : changes.entrySet()) {
            if (remote && c.getKey().side == SettingsModel.Side.SERVER) {
                if (serverCanEdit) {
                    toServer.put(c.getKey().id, c.getValue());
                }
            } else {
                local.put(c.getKey(), c.getValue());
            }
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!local.isEmpty()) {
            // On the client thread, as Forge's own config screen does, also while a single-player world runs.
            Set<String> mods = BackportSettings.apply(local, mc.world != null);
            BackportCore.LOGGER.info("Settings: saved {} changes to {}", local.size(), mods);
        }
        if (!toServer.isEmpty()) {
            SettingsNetwork.sendChanges(toServer);
        }
    }

    /** @return how many saved changes wait for a restart of this game */
    public static int waitingForRestart() {
        int n = 0;
        for (SettingsModel.Page page : SettingsModel.pages()) {
            for (SettingsModel.Entry e : page.entries) {
                if (e.waitsForRestart(e.get())) {
                    n++;
                }
            }
        }
        return n;
    }

    // --- Mod Options ------------------------------------------------------------------------------------------

    /** Points every listed mod's Config button at Core's screen, on that mod's page. */
    static void takeOverConfigButtons() {
        BiMap<ModContainer, IModGuiFactory> factories;
        try {
            factories = ObfuscationReflectionHelper.getPrivateValue(FMLClientHandler.class, FMLClientHandler.instance(), "guiFactories");
        } catch (RuntimeException e) {
            BackportCore.LOGGER.warn("Settings: cannot reach Forge's config buttons", e);
            return;
        }
        if (factories == null) {
            return;
        }
        for (String modid : BackportSettings.modids()) {
            ModContainer mc = Loader.instance().getIndexedModList().get(modid);
            if (mc == null) {
                continue;
            }
            IModGuiFactory current = factories.get(mc);
            if (current instanceof Factory) {
                continue;
            }
            if (current != null && !(current instanceof net.minecraftforge.fml.client.DefaultGuiFactory)) {
                continue; // the mod has a screen of its own
            }
            factories.forcePut(mc, new Factory(modid));
        }
    }

    /** Mod Options' Config button for one mod. */
    static final class Factory implements IModGuiFactory {
        private final String modid;

        Factory(String modid) {
            this.modid = modid;
        }

        @Override
        public void initialize(Minecraft minecraft) {
        }

        @Override
        public boolean hasConfigGui() {
            return true;
        }

        @Override
        public GuiScreen createConfigGui(GuiScreen parent) {
            return new SettingsScreen(parent, this.modid);
        }

        @Override
        public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
            return null;
        }
    }

    public static final class Events {

        @SubscribeEvent
        public void onOpen(GuiOpenEvent event) {
            if (event.getGui() instanceof GuiModList) {
                takeOverConfigButtons();
            }
        }

        @SubscribeEvent
        public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            serverValues = null;
            serverCanEdit = false;
            waitingForServer = false;
        }

        /** "Restart the game to apply 2 changes." at the foot of the title and pause screens. */
        @SubscribeEvent(priority = EventPriority.LOW)
        public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
            GuiScreen gui = event.getGui();
            if (!(gui instanceof GuiMainMenu) && !(gui instanceof GuiIngameMenu)) {
                return;
            }
            int n = waitingForRestart();
            if (n > 0) {
                String text = I18n.format(n == 1 ? "backportcore.settings.restart.one" : "backportcore.settings.restart.many", n);
                int y = gui instanceof GuiMainMenu ? 4 : gui.height - 14;
                gui.drawCenteredString(gui.mc.fontRenderer, text, gui.width / 2, y, SettingsStyle.RESTART);
            }
        }
    }

    /** @return the family member's short name, or the mod's own name */
    static String modName(String modid) {
        Family.Member m = Family.get(modid);
        if (m != null) {
            return I18n.format(m.nameKey());
        }
        ModContainer mc = Loader.instance().getIndexedModList().get(modid);
        return mc != null ? mc.getName() : modid;
    }
}

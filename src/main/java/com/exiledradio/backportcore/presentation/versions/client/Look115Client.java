package com.exiledradio.backportcore.presentation.versions.client;

import com.exiledradio.backportcore.presentation.LegacyCopies;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.client.DebugText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.Display;

/**
 * The client side of {@code versions.Look115}: 1.15's look and feel that Backport Core owns since 1.4.7 (moved out of
 * Buzzy Bees Backport's {@code lookfeel.client} and {@code tweaks.client.ToggleKeys}): the Sneak and Sprint toggles
 * ({@link SneakSprintToggles}), 1.15.2's window title and 19w38a's F3 text.
 *
 * <p>An older Buzzy Bees jar's copies: its F3 step of the same name is left out, and while it still has
 * {@code lookfeel.client.LookAndFeelClient} (window title) and {@code tweaks.client.ToggleKeys} Core's handlers stay
 * off (that jar runs its own; its options follow Core's).
 */
@SideOnly(Side.CLIENT)
public final class Look115Client {

    /** The older Buzzy Bees jar's own window title handler. */
    static final String LEGACY_TITLE = "com.exiledradio.buzzybeesbackport.lookfeel.client.LookAndFeelClient";

    /** For the client self-test. */
    public static volatile String lastTitle;
    /** For the client self-test. */
    public static volatile int debugEdits;

    private String shownTitle;

    private Look115Client() {
    }

    /** From Backport Core's client pre-init, before any backport's client code. */
    static void init() {
        MinecraftForge.EVENT_BUS.register(new SneakSprintToggles());
        MinecraftForge.EVENT_BUS.register(new Look115Client());
        // Buzzy Bees' exact (version, name): an older jar's copy of the step is left out.
        DebugText.add("1.15", "no chunk updates", Look115Client::debugText);
    }

    /** 19w38a: "(N chunk updates)" leaves the second line of the F3 text (1.12.2's {@code Minecraft.debug}). */
    static void debugText(DebugText.Lines lines) {
        lines.replaceLeft(1, s -> s.replaceFirst(" \\(\\d+ chunk updates?\\)", ""));
        debugEdits++;
    }

    /** 1.15.2 {@code Minecraft.createTitle}, set whenever it changes. */
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !Presentation.isAtLeast("1.15.2") || !LegacyCopies.coreRuns(LEGACY_TITLE)) {
            return;
        }
        String title = title(Minecraft.getMinecraft());
        if (!title.equals(this.shownTitle)) {
            this.shownTitle = title;
            lastTitle = title;
            Display.setTitle(title);
        }
    }

    /**
     * "Minecraft", a star when the game is modded (always, under Forge), the version, and once connected, the
     * kind of session.
     */
    public static String title(Minecraft mc) {
        StringBuilder b = new StringBuilder("Minecraft* ").append(ForgeVersion.mcVersion);
        NetHandlerPlayClient connection = mc.getConnection();
        if (connection != null && connection.getNetworkManager().isChannelOpen()) {
            b.append(" - ");
            IntegratedServer server = mc.getIntegratedServer();
            ServerData data = mc.getCurrentServerData();
            if (server != null && !server.getPublic()) {
                b.append(I18n.format("title.singleplayer"));
            } else if (mc.isConnectedToRealms()) {
                b.append(I18n.format("title.multiplayer.realms"));
            } else if (server == null && (data == null || !data.isOnLAN())) {
                b.append(I18n.format("title.multiplayer.other"));
            } else {
                b.append(I18n.format("title.multiplayer.lan"));
            }
        }
        return b.toString();
    }
}

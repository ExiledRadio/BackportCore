package com.exiledradio.backportcore.presentation.versions.client;

import com.exiledradio.backportcore.presentation.LegacyCopies;
import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.client.DebugText;
import com.exiledradio.backportcore.presentation.client.PresentationHooks;
import com.exiledradio.backportcore.presentation.versions.Look114;
import com.exiledradio.backportcore.presentation.versions.ScrollAccumulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * The client side of {@code versions.Look114}: 1.14's look and feel that Backport Core owns since 1.4.7 (moved out of
 * Village &amp; Pillage Backport's {@code lookfeel.client}): F3 + N and F3 + Esc (1.14.1), the F3 server chunk line
 * (1.14.2) and the hotbar scroll's Mouse Settings.
 *
 * <p>An older Village &amp; Pillage jar's copies: its F3 + N replaces Core's (same key), its F3 step of the same name is
 * left out, and while it still has {@code lookfeel.ScrollAccumulator} and {@code lookfeel.client.GuiPauseOnly}
 * Core's scroll and F3 + Esc handlers stay off (that jar runs its own).
 */
@SideOnly(Side.CLIENT)
public final class Look114Client {

    /** The older Village &amp; Pillage jar's own scroll handler and F3 + Esc screen. */
    static final String LEGACY_SCROLL = "com.exiledradio.villagerbackport.lookfeel.ScrollAccumulator";
    static final String LEGACY_PAUSE = "com.exiledradio.villagerbackport.lookfeel.client.GuiPauseOnly";

    /** One wheel notch as this game's input library reports it (GLFW, which 1.14's {@code MouseHandler} reads, says 1.0). */
    static final double NOTCH = ScrollAccumulator.notch();

    /** For the client self-test. */
    public static volatile int pauseOnlyScreens;
    /** For the client self-test. */
    public static volatile int debugEdits;

    private final ScrollAccumulator scroll = new ScrollAccumulator();

    private Look114Client() {
    }

    /** From Backport Core's client pre-init, before any backport's client code. */
    static void init() {
        Presentation.core("1.14.1").debugKey(Keyboard.KEY_N, "debug.creative_spectator.help", Look114Client::creativeSpectator);
        MinecraftForge.EVENT_BUS.register(new Look114Client());
        // Village & Pillage's exact (version, name): an older jar's copy of the step is left out.
        DebugText.add("1.14.2", "server chunk cache", Look114Client::debugText);
    }

    /**
     * 1.14.2's F3 text: in singleplayer the server's chunk cache follows the client's ({@code getServerChunkStats},
     * the integrated server world's own {@code ServerChunkCache: n Drop: m}).
     */
    static void debugText(DebugText.Lines lines) {
        Minecraft mc = lines.mc;
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null || mc.player == null || mc.world == null) {
            return;
        }
        WorldServer world = server.getWorld(mc.player.dimension);
        String client = mc.world.getProviderName();
        int at = lines.left.indexOf(client);
        if (world != null && at >= 0) {
            lines.left.add(at + 1, world.getChunkProvider().makeString());
            debugEdits++;
        }
    }

    /** 1.14.4 {@code KeyboardHandler}, F3 + N: creative goes to spectator, anything else to creative. */
    static boolean creativeSpectator() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return false;
        }
        if (!mc.player.canUseCommand(2, "")) {
            PresentationHooks.debugFeedback("debug.creative_spectator.error");
        } else if (mc.player.isCreative()) {
            mc.player.sendChatMessage("/gamemode spectator");
        } else {
            mc.player.sendChatMessage("/gamemode creative");
        }
        return true;
    }

    /**
     * 1.14.1, F3 + Esc: the game pauses without the pause menu (1.14.4's {@code PauseScreen(false)}: no buttons, no
     * dark background, "Game paused" at the top). 1.12.2 opens its menu on Esc before it looks at F3, so the menu
     * is swapped as it opens.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onGuiOpen(GuiOpenEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.getGui() != null && event.getGui().getClass() == GuiIngameMenu.class && mc.currentScreen == null
                && Presentation.isAtLeast(LookArea.DEBUG, "1.14.1") && Keyboard.isKeyDown(Keyboard.KEY_F3)
                && Keyboard.isKeyDown(Keyboard.KEY_ESCAPE) && LegacyCopies.coreRuns(LEGACY_PAUSE)) {
            event.setGui(new GuiPauseOnly());
            pauseOnlyScreens++;
        }
    }

    /**
     * 1.14.4 {@code MouseHandler.onScroll} for the hotbar: the wheel's movement times Scroll Sensitivity (or its
     * sign, with Discrete Scrolling) is added up, and one slot moves each time the sum passes a whole notch; a
     * change of direction starts the sum again. Screens keep 1.12.2's scrolling.
     */
    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        int wheel = event.getDwheel();
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        // While Scroll Sensitivity is hidden (no 1.14 backport, or Core's Look & Feel page turned the options screens
        // off), 1.12.2's scrolling.
        if (wheel == 0 || event.getButton() != -1 || mc.currentScreen != null || player == null
                || !Look114.SCROLL_SENSITIVITY.isShown() || !LegacyCopies.coreRuns(LEGACY_SCROLL)) {
            return;
        }
        event.setCanceled(true);
        int slots = this.scroll.add(ScrollAccumulator.amount(wheel / NOTCH));
        if (slots == 0) {
            return;
        }
        if (player.isSpectator()) {
            if (mc.ingameGUI.getSpectatorGui().isMenuActive()) {
                mc.ingameGUI.getSpectatorGui().onMouseScroll(-slots);
            } else {
                player.capabilities.setFlySpeed(MathHelper.clamp(player.capabilities.getFlySpeed() + slots * 0.005F, 0.0F, 0.2F));
            }
        } else {
            player.inventory.changeCurrentItem(slots);
        }
    }
}

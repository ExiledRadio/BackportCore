package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.net.URI;
import java.util.List;

/**
 * The one owner of the pause menu and the death screen: each as vanilla's at the newest installed version
 * ({@link VanillaRevisions}), on Forge's GUI events. Their wording (1.16.2's "You Died!") is the backports' vanilla
 * text.
 * <ul>
 *   <li>Pause, 1.14 (18w43a): 1.14.4's {@code PauseScreen} grid, 204 wide (Back to Game; Advancements, Statistics;
 *       Give Feedback, Report Bugs; Options..., Open to LAN; Save and Quit to Title). Forge 1.12.2's Mod Options
 *       button goes where Forge itself puts its pause-menu Mods button since it re-added one (1.19.2 to 1.20.x,
 *       NeoForge 1.20 and 1.21, "Re-add in-game mod menu", #9655): a full-width "Mods" row under Options... and Open
 *       to LAN, with Save and Quit one row lower. Forge 1.13 to 1.18 had no pause-menu button at all. Report Bugs opens
 *       {@code presentation.reportBugsLink} (the backports' Discord by default), since bugs in a backport are not
 *       Mojang's.</li>
 *   <li>Death, 1.15 (19w35a): a hardcore death screen's second button is "Title screen" instead of Delete World or
 *       Leave Server, and it leaves the world for the title screen, saving it (1.15's {@code exitToTitleScreen};
 *       1.12.2's only showed the title screen over a world still running).</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class MenuScreens {

    static final String FEEDBACK_URL = "https://aka.ms/javafeedback?ref=game";
    static final String BUGS_URL = "https://aka.ms/snapshotbugs?ref=game";
    private static final int MOD_OPTIONS = 12;

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (gui.getClass() == GuiIngameMenu.class && VanillaRevisions.pauseFeedback()) {
            pause(gui, event.getButtonList());
        } else if (gui instanceof GuiGameOver && VanillaRevisions.hardcoreTitleButton()) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.world != null && mc.world.getWorldInfo().isHardcoreModeEnabled()) {
                GuiButton b = find(event.getButtonList(), 1);
                if (b != null) {
                    b.displayString = I18n.format("deathScreen.titleScreen");
                }
            }
        }
    }

    /** For the client self-test. */
    public static volatile int hardcoreExits;

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.getGui() instanceof GuiGameOver) || event.getButton().id != 1 || !VanillaRevisions.hardcoreTitleButton()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || !mc.world.getWorldInfo().isHardcoreModeEnabled()) {
            return;
        }
        event.setCanceled(true);
        hardcoreExits++;
        mc.world.sendQuittingDisconnectingPacket();
        mc.loadWorld(null);
        mc.displayGuiScreen(new net.minecraft.client.gui.GuiMainMenu());
    }

    static void pause(GuiScreen gui, List<GuiButton> buttons) {
        int w = gui.width;
        int top = gui.height / 4 - 16;
        for (GuiButton b : buttons) {
            switch (b.id) {
                case 4: // Back to Game
                    place(b, w / 2 - 102, top + 24, 204);
                    break;
                case 5: // Advancements
                    place(b, w / 2 - 102, top + 48, 98);
                    break;
                case 6: // Statistics
                    place(b, w / 2 + 4, top + 48, 98);
                    break;
                case 0: // Options...
                    place(b, w / 2 - 102, top + 96, 98);
                    break;
                case 7: // Open to LAN
                    place(b, w / 2 + 4, top + 96, 98);
                    break;
                case 1: // Save and Quit to Title / Disconnect, a row lower when Forge's Mods row is there
                    place(b, w / 2 - 102, find(buttons, MOD_OPTIONS) != null ? top + 144 : top + 120, 204);
                    break;
                case MOD_OPTIONS: // Forge's own pause-menu Mods row (1.19.2+): full width, above Save and Quit
                    place(b, w / 2 - 102, top + 120, 204);
                    b.displayString = I18n.format("fml.menu.mods");
                    break;
                default:
                    break;
            }
        }
        buttons.add(new OptionWidgets.Action(w / 2 - 102, top + 72, 98, I18n.format("menu.sendFeedback"), () -> confirmLink(gui, FEEDBACK_URL)));
        String bugs = reportBugsLink();
        if (!bugs.isEmpty()) {
            buttons.add(new OptionWidgets.Action(w / 2 + 4, top + 72, 98, I18n.format("menu.reportBugs"), () -> confirmLink(gui, bugs)));
        }
    }

    /** The configured Report Bugs link, or empty to leave the button out. */
    static String reportBugsLink() {
        String link = com.exiledradio.backportcore.CoreConfig.presentation.reportBugsLink;
        return link == null ? "" : link.trim();
    }

    private static void place(GuiButton b, int x, int y, int width) {
        b.x = x;
        b.y = y;
        b.width = width;
    }

    /** 1.14's {@code ConfirmLinkScreen} (trusted): open the link or not, then back to {@code back}. */
    public static void confirmLink(GuiScreen back, String url) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.displayGuiScreen(new GuiConfirmOpenLink((result, id) -> {
            if (result) {
                openLink(url);
            }
            mc.displayGuiScreen(back);
        }, url, 0, true));
    }

    static void openLink(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(new URI(url));
        } catch (Throwable t) {
            BackportCore.LOGGER.error("Couldn't open link {}", url, t);
        }
    }

    private static GuiButton find(List<GuiButton> buttons, int id) {
        for (GuiButton b : buttons) {
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }
}

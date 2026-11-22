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
        if (gui.getClass() == GuiIngameMenu.class && VanillaRevisions.pauseIconRow() && iconSprites()) {
            pause262(gui, event.getButtonList());
        } else if (gui.getClass() == GuiIngameMenu.class && VanillaRevisions.pauseFeedback()) {
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
        buttons.add(new OptionWidgets.Action(w / 2 - 102, top + 72, 98, I18n.format("menu.sendFeedback"), () -> confirmLink(gui, feedbackLink())));
        String bugs = reportBugsLink();
        if (!bugs.isEmpty()) {
            buttons.add(new OptionWidgets.Action(w / 2 + 4, top + 72, 98, I18n.format("menu.reportBugs"), () -> confirmLink(gui, bugs)));
        }
    }

    static final String BUG_SPRITE = "textures/gui/sprites/pause_menu/bug.png";
    static final String FEEDBACK_SPRITE = "textures/gui/sprites/pause_menu/social_interactions.png";
    static final String FRIENDS_SPRITE = "textures/gui/sprites/friends/friends.png";
    static final String PLAYER_REPORTING_SPRITE = "textures/gui/sprites/pause_menu/player_reporting.png";
    /** The message of a pause-menu button whose online service 1.12.2 does not have. */
    public static final String UNAVAILABLE_KEY = "backportcore.pauseMenu.unavailable";

    /** @return whether a backport supplies 26.2's Report Bugs and Give Feedback sprites (else the 1.14 text row stays) */
    static boolean iconSprites() {
        return com.exiledradio.backportcore.presentation.Presentation.asset(BUG_SPRITE) != null
                && com.exiledradio.backportcore.presentation.Presentation.asset(FEEDBACK_SPRITE) != null;
    }

    /**
     * 26.2's {@code PauseScreen.createPauseMenu}: a two-column grid (cells padded 4, the first row 50 from the top),
     * aligned at a quarter of the free height: Back to Game; Advancements, Statistics; the icon row; Options..., Open
     * to LAN (World Options... from 26.3, the newest backport's); Forge's Mods row; Save and Quit.
     * <p>
     * The icon row (26.2's and 26.3's {@code LinearLayout.horizontal().spacing(4)}, centered): Report Bugs, Give
     * Feedback, Friends, Player Reporting, each 20 by 20 with a 15 by 15 sprite and its name as tooltip. Friends
     * ({@code CommonButtons.friends}) is inactive without a tooltip in a demo. Player Reporting is inactive in a world of
     * this client's own (singleplayer or LAN host) with no other player in it, its tooltip then "There are no players to
     * report in this world". Friends and Player Reporting are Mojang's online services, which 1.12.2 does not have:
     * clicking either shows a system toast, its name over "Not available in this version". Each of the two is left out
     * when no backport supplies its sprite; Report Bugs is left out without a link ({@code reportBugsLink}).
     */
    static void pause262(GuiScreen gui, List<GuiButton> buttons) {
        int w = gui.width;
        boolean mods = find(buttons, MOD_OPTIONS) != null;
        int rows = mods ? 6 : 5;
        int top = VanillaRevisions.pauseGridTop(gui.height, rows) + 50;
        String bugs = reportBugsLink();
        ICONS.clear();
        for (GuiButton b : buttons) {
            switch (b.id) {
                case 4:
                    place(b, w / 2 - 102, top, 204);
                    break;
                case 5:
                    place(b, w / 2 - 102, top + 24, 98);
                    break;
                case 6:
                    place(b, w / 2 + 4, top + 24, 98);
                    break;
                case 0:
                    place(b, w / 2 - 102, top + 72, 98);
                    break;
                case 7:
                    place(b, w / 2 + 4, top + 72, 98);
                    break;
                case MOD_OPTIONS:
                    place(b, w / 2 - 102, top + 96, 204);
                    b.displayString = I18n.format("fml.menu.mods");
                    break;
                case 1:
                    place(b, w / 2 - 102, top + (mods ? 120 : 96), 204);
                    break;
                default:
                    break;
            }
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!bugs.isEmpty()) {
            ICONS.add(new IconAction(I18n.format("menu.reportBugs"), BUG_SPRITE, () -> confirmLink(gui, bugs)));
        }
        ICONS.add(new IconAction(I18n.format("menu.sendFeedback"), FEEDBACK_SPRITE, () -> confirmLink(gui, feedbackLink())));
        if (com.exiledradio.backportcore.presentation.Presentation.asset(FRIENDS_SPRITE) != null) {
            boolean available = !mc.isDemo();
            IconAction friends = new IconAction(available ? I18n.format("gui.friends.open.tooltip") : null, FRIENDS_SPRITE,
                    () -> unavailable(I18n.format("gui.friends.open.tooltip")));
            friends.enabled = available;
            ICONS.add(friends);
        }
        if (com.exiledradio.backportcore.presentation.Presentation.asset(PLAYER_REPORTING_SPRITE) != null) {
            IconAction reporting = new IconAction(I18n.format("menu.playerReporting"), PLAYER_REPORTING_SPRITE,
                    () -> unavailable(I18n.format("menu.playerReporting")));
            if (mc.isIntegratedServerRunning() && mc.player != null && !otherPlayers(mc)) {
                reporting.enabled = false;
                reporting.tooltip = I18n.format("menu.playerReporting.no_players");
            }
            ICONS.add(reporting);
        }
        int x = w / 2 - (ICONS.size() * 24 - 4) / 2;
        for (IconAction b : ICONS) {
            b.x = x;
            b.y = top + 48;
            x += 24;
        }
        buttons.addAll(ICONS);
    }

    /** {@code getSeenPlayers()} without the player: whether the tab list has anyone else. */
    private static boolean otherPlayers(Minecraft mc) {
        if (mc.player.connection == null) {
            return false;
        }
        java.util.UUID self = mc.player.getUniqueID();
        for (net.minecraft.client.network.NetworkPlayerInfo info : mc.player.connection.getPlayerInfoMap()) {
            if (!self.equals(info.getGameProfile().getId())) {
                return true;
            }
        }
        return false;
    }

    /** A button whose service 1.12.2 lacks: 26.3's system toast, the button's name over "Not available in this version". */
    static void unavailable(String name) {
        SystemToasts.addOrUpdate(name, I18n.format(UNAVAILABLE_KEY));
    }

    /** The open pause menu's icon buttons (for their tooltips and the client self-test). */
    public static final List<IconAction> ICONS = new java.util.ArrayList<IconAction>();

    /**
     * 26.2's {@code SpriteIconButton.CenteredIcon}: a blank 20 by 20 button with a 15 by 15 sprite (one sprite for every
     * state, so drawn whole on an inactive button too), its tooltip (null for none).
     */
    public static final class IconAction extends OptionWidgets.Action {
        public final String sprite;
        public String tooltip;

        IconAction(String tooltip, String sprite, Runnable action) {
            super(0, 0, 20, "", action);
            this.sprite = sprite;
            this.tooltip = tooltip;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }
            super.drawButton(mc, mouseX, mouseY, partialTicks);
            mc.getTextureManager().bindTexture(new net.minecraft.util.ResourceLocation(this.sprite));
            net.minecraft.client.renderer.GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            net.minecraft.client.renderer.GlStateManager.enableBlend();
            net.minecraft.client.gui.Gui.drawModalRectWithCustomSizedTexture(this.x + this.width / 2 - 7, this.y + this.height / 2 - 7,
                    0.0F, 0.0F, 15, 15, 15.0F, 15.0F);
            net.minecraft.client.renderer.GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.getGui().getClass() != GuiIngameMenu.class) {
            return;
        }
        for (IconAction b : ICONS) {
            if (b.visible && b.isMouseOver() && b.tooltip != null) {
                event.getGui().drawHoveringText(b.tooltip, event.getMouseX(), event.getMouseY());
            }
        }
    }

    /** The configured Give Feedback link, or Mojang's feedback site when it is empty. */
    static String feedbackLink() {
        String link = com.exiledradio.backportcore.CoreConfig.presentation.giveFeedbackLink;
        return link == null || link.trim().isEmpty() ? FEEDBACK_URL : link.trim();
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

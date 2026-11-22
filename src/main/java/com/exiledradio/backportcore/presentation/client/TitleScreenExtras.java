package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.option.OptionStore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * The title screen's later pieces, on Forge's GUI events (no patch):
 * <ul>
 *   <li>1.14's Accessibility button (19w13a), the icon right of the Options/Quit row, as Language is left of it;</li>
 *   <li>1.13's fade-in on the game's first title screen: the panorama at once, then the logo, splash, text and
 *       buttons fading in over the next second. Drawn by drawing the panorama again over the finished screen with
 *       the remaining opacity, which blends exactly as vanilla's per-element alpha does;</li>
 *   <li>1.15.2's multiplayer warning before the server list, from the title screen, until it is dismissed for good;</li>
 *   <li>1.19.4's copyright text as a link to Credits &amp; Attribution ({@code PlainTextButton}; 1.12.2's rolled the end
 *       credits), and "Minecraft Realms" in full on the Realms button (Forge 1.12.2 shortens it to "Realms" to fit
 *       beside Mods; modern Forge and vanilla keep the full text, which fits in 98).</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class TitleScreenExtras {

    /** The "Do not show this screen again" flag, as 1.16.5's {@code skipMultiplayerWarning}. */
    public static final String SKIP_WARNING = "backportcore:skip_multiplayer_warning";

    private static long fadeStart = -1L;
    private static boolean fadeDone;
    private static boolean bypassWarning;
    /** For the client self-test. */
    public static volatile int fadeFrames;

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiMainMenu)) {
            return;
        }
        TITLE_ICONS.clear();
        if (Presentation.isAtLeast(com.exiledradio.backportcore.presentation.LookArea.TITLE, "1.13")) {
            for (GuiButton b : event.getButtonList()) {
                if (b.id == REALMS_BUTTON) {
                    b.displayString = I18n.format("menu.online");
                }
            }
        }
        if (iconRow((GuiMainMenu) event.getGui(), event.getButtonList())) {
            return;
        }
        ResourceLocation icon = Presentation.titleAccessibilityIcon();
        if (icon != null) {
            GuiMainMenu menu = (GuiMainMenu) event.getGui();
            int j = menu.height / 4 + 48;
            event.getButtonList().add(new IconButton(menu.width / 2 + 104, j + 72 + 12, icon, I18n.format("narrator.button.accessibility"),
                    () -> Minecraft.getMinecraft().displayGuiScreen(OptionsListScreen.accessibility(menu))));
        }
    }

    /** 1.12.2's Realms button id on the title screen. */
    static final int REALMS_BUTTON = 14;
    /** 1.12.2's copyright line (not a lang key in 1.12.2). */
    static final String COPYRIGHT = "Copyright Mojang AB. Do not distribute!";

    /**
     * 1.19.4: a click on the copyright text opens Credits &amp; Attribution (26.3's {@code PlainTextButton}, as wide as
     * the text, 10 high, at {@code width - textWidth - 2, height - 10}); 1.12.2's own click there rolls the end credits,
     * so it is taken first. The underline on hover is 1.12.2's.
     */
    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiMainMenu) || !org.lwjgl.input.Mouse.getEventButtonState() || org.lwjgl.input.Mouse.getEventButton() != 0
                || !VanillaRevisions.creditsAndAttribution(Presentation.newest(com.exiledradio.backportcore.presentation.LookArea.TITLE))) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.client.gui.GuiScreen gui = event.getGui();
        int x = org.lwjgl.input.Mouse.getEventX() * gui.width / mc.displayWidth;
        int y = gui.height - org.lwjgl.input.Mouse.getEventY() * gui.height / mc.displayHeight - 1;
        if (copyrightAt(gui, x, y)) {
            event.setCanceled(true);
            mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            mc.displayGuiScreen(new CreditsAndAttributionScreen(gui));
        }
    }

    /** @return whether (x, y) is on the title screen's copyright text, as 1.12.2's own test */
    static boolean copyrightAt(net.minecraft.client.gui.GuiScreen gui, int x, int y) {
        int w = Minecraft.getMinecraft().fontRenderer.getStringWidth(COPYRIGHT);
        int left = gui.width - w - 2;
        return x > left && x < left + w && y > gui.height - 10 && y < gui.height;
    }

    static final String LANGUAGE_SPRITE = "textures/gui/sprites/icon/language.png";
    static final String ACCESSIBILITY_SPRITE = "textures/gui/sprites/icon/accessibility.png";
    /** The title screen's 26.2 icon buttons (for their tooltips and the client self-test). */
    public static final java.util.List<MenuScreens.IconAction> TITLE_ICONS = new java.util.ArrayList<MenuScreens.IconAction>();

    /**
     * 26.2's {@code TitleScreen.init}: under Singleplayer, Multiplayer and (Forge's) Mods and Minecraft Realms, one row
     * of 20 by 20 icon buttons, centered with 4 between ({@code getHorizontalPosition}): Friends, Language,
     * Accessibility; then Options... and Quit Game a row lower. Language and Accessibility are
     * {@code CommonButtons.language}/{@code accessibility} (a 15 by 15 sprite, tooltips "Change Language" and
     * "Accessibility Settings"); Friends is Mojang's online service, which 1.12.2 does not have: inactive in a demo,
     * otherwise a system toast "Not available in this version", as on the pause menu. Friends is left out when no
     * backport supplies its sprite; the whole row waits for the Language and Accessibility sprites.
     *
     * @return whether the row replaced 1.12.2's Language button and 1.14's Accessibility icon
     */
    static boolean iconRow(GuiMainMenu menu, java.util.List<GuiButton> buttons) {
        if (!VanillaRevisions.titleIconRow() || Presentation.asset(LANGUAGE_SPRITE) == null
                || Presentation.asset(ACCESSIBILITY_SPRITE) == null) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        int j = menu.height / 4 + 48;
        for (java.util.Iterator<GuiButton> it = buttons.iterator(); it.hasNext(); ) {
            GuiButton b = it.next();
            if (b.id == 5 && b instanceof net.minecraft.client.gui.GuiButtonLanguage) {
                it.remove();
            } else if (b.id == 0 || b.id == 4) {
                // Options... and Quit Game: 1.12.2's j + 84, 26.2's a row under the icons.
                b.y = j + 96;
            }
        }
        if (Presentation.asset(MenuScreens.FRIENDS_SPRITE) != null) {
            boolean available = !mc.isDemo();
            MenuScreens.IconAction friends = new MenuScreens.IconAction(available ? I18n.format("gui.friends.open.tooltip") : null,
                    MenuScreens.FRIENDS_SPRITE, () -> MenuScreens.unavailable(I18n.format("gui.friends.open.tooltip")));
            friends.enabled = available;
            TITLE_ICONS.add(friends);
        }
        TITLE_ICONS.add(new MenuScreens.IconAction(I18n.format("options.language.tooltip"), LANGUAGE_SPRITE,
                () -> mc.displayGuiScreen(new net.minecraft.client.gui.GuiLanguage(menu, mc.gameSettings, mc.getLanguageManager()))));
        TITLE_ICONS.add(new MenuScreens.IconAction(I18n.format("options.accessibility.tooltip"), ACCESSIBILITY_SPRITE,
                () -> mc.displayGuiScreen(OptionsListScreen.accessibility(menu))));
        int count = TITLE_ICONS.size();
        int x = menu.width / 2 - (count * 20 + (count - 1) * 4) / 2;
        for (MenuScreens.IconAction b : TITLE_ICONS) {
            b.x = x;
            b.y = j + 72;
            x += 24;
        }
        buttons.addAll(TITLE_ICONS);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onDrawTooltips(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof GuiMainMenu)) {
            return;
        }
        for (MenuScreens.IconAction b : TITLE_ICONS) {
            if (b.visible && b.isMouseOver() && b.tooltip != null) {
                event.getGui().drawHoveringText(b.tooltip, event.getMouseX(), event.getMouseY());
            }
        }
    }

    /** 1.14's {@code ImageButton}: a 20 by 20 icon from a 32 by 64 texture, the hovered one 20 pixels down. */
    public static final class IconButton extends OptionWidgets.Action {
        private final ResourceLocation texture;

        IconButton(int x, int y, ResourceLocation texture, String narration, Runnable action) {
            super(x, y, 20, "", action);
            this.texture = texture;
            this.narration = narration;
        }

        final String narration;

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) {
                return;
            }
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            mc.getTextureManager().bindTexture(this.texture);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableDepth();
            Gui.drawModalRectWithCustomSizedTexture(this.x, this.y, 0.0F, this.hovered ? 20.0F : 0.0F, this.width, this.height, 32.0F, 64.0F);
            GlStateManager.enableDepth();
        }
    }

    // ---- 1.13's fade-in ----

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof GuiMainMenu) || fadeDone) {
            return;
        }
        if (!VanillaRevisions.titleFadeIn() || !PanoramaRenderer.modern()) {
            fadeDone = true;
            return;
        }
        if (StartupScreen.opaque()) {
            // 1.16.5: the title screen's clock starts with its first visible frame.
            return;
        }
        long now = Minecraft.getSystemTime();
        if (fadeStart < 0) {
            fadeStart = now;
        }
        float f = (now - fadeStart) / 1000.0F;
        float shown = MathHelper.clamp(f - 1.0F, 0.0F, 1.0F);
        if (shown >= 1.0F) {
            fadeDone = true;
            return;
        }
        GuiMainMenu menu = (GuiMainMenu) event.getGui();
        PanoramaRenderer.redraw(menu, 1.0F - shown);
        fadeFrames++;
    }

    /** @return true once the first title screen has faded in (or never will) */
    public static boolean fadeDone() {
        return fadeDone;
    }

    // ---- 1.15.2's multiplayer warning ----

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onOpen(GuiOpenEvent event) {
        if (!(event.getGui() instanceof GuiMultiplayer) || bypassWarning) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        String[] keys = Presentation.multiplayerWarning();
        if (keys == null || !(mc.currentScreen instanceof GuiMainMenu) || "true".equals(OptionStore.get(SKIP_WARNING))) {
            return;
        }
        event.setGui(new MultiplayerWarningScreen(mc.currentScreen, keys));
    }

    /** Opens the server list past the warning (its Proceed button). */
    static void openServerList(net.minecraft.client.gui.GuiScreen parent) {
        bypassWarning = true;
        try {
            Minecraft.getMinecraft().displayGuiScreen(new GuiMultiplayer(parent));
        } finally {
            bypassWarning = false;
        }
    }

    /** For tests: the button list's first icon button, or null. */
    static IconButton iconButton(java.util.List<GuiButton> buttons) {
        for (GuiButton b : buttons) {
            if (b instanceof IconButton) {
                return (IconButton) b;
            }
        }
        return null;
    }
}

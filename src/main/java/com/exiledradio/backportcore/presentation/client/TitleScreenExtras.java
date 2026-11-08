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
 *   <li>1.15.2's multiplayer warning before the server list, from the title screen, until it is dismissed for good.</li>
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
        ResourceLocation icon = Presentation.titleAccessibilityIcon();
        if (icon != null) {
            GuiMainMenu menu = (GuiMainMenu) event.getGui();
            int j = menu.height / 4 + 48;
            event.getButtonList().add(new IconButton(menu.width / 2 + 104, j + 72 + 12, icon, I18n.format("narrator.button.accessibility"),
                    () -> Minecraft.getMinecraft().displayGuiScreen(OptionsListScreen.accessibility(menu))));
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

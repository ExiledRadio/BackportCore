package com.exiledradio.backportcore.presentation.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiWinGame;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

/**
 * 1.19.4's Credits &amp; Attribution ({@code CreditsAndAttributionScreen}, 26.3's unchanged), from Options and from the
 * title screen's copyright text: the title in the header, Credits, Attribution and Licenses (210 wide, 8 apart) in a
 * column from 30 under it, Done (200 wide) in the footer ({@link HeaderFooterLayout}). Credits rolls 1.12.2's end
 * credits ({@code GuiWinGame} without the poem) and comes back here; Attribution and Licenses open
 * {@code CommonLinks.ATTRIBUTION} and {@code LICENSES} through the link confirmation. Since Core 1.4.5.
 */
@SideOnly(Side.CLIENT)
public final class CreditsAndAttributionScreen extends GuiScreen {

    /** 26.3's {@code CommonLinks.ATTRIBUTION} and {@code LICENSES}. */
    static final String ATTRIBUTION = "https://aka.ms/MinecraftJavaAttribution";
    static final String LICENSES = "https://aka.ms/MinecraftJavaLicenses";

    /** The screen the end credits go back to when they finish (1.12.2's {@code GuiWinGame} closes to no screen). */
    @Nullable
    private static GuiScreen afterCredits;

    private final GuiScreen previous;

    public CreditsAndAttributionScreen(GuiScreen previous) {
        this.previous = previous;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        HeaderFooterLayout.title(this, I18n.format("credits_and_attribution.screen.title"), HeaderFooterLayout.HEADER);
        GuiButton credits = new OptionWidgets.Action(0, 0, 210, I18n.format("credits_and_attribution.button.credits"), () -> {
            afterCredits = this;
            this.mc.displayGuiScreen(new GuiWinGame(false, () -> { }));
        });
        GuiButton attribution = new OptionWidgets.Action(0, 0, 210, I18n.format("credits_and_attribution.button.attribution"),
                () -> MenuScreens.confirmLink(this, ATTRIBUTION));
        GuiButton licenses = new OptionWidgets.Action(0, 0, 210, I18n.format("credits_and_attribution.button.licenses"),
                () -> MenuScreens.confirmLink(this, LICENSES));
        GuiButton[] column = {credits, attribution, licenses};
        int top = HeaderFooterLayout.contentsTop(this, HeaderFooterLayout.HEADER, column.length * 20 + (column.length - 1) * HeaderFooterLayout.SPACING);
        for (int i = 0; i < column.length; i++) {
            column[i].x = this.width / 2 - 105;
            column[i].y = top + i * (20 + HeaderFooterLayout.SPACING);
            this.buttonList.add(column[i]);
        }
        GuiButton done = new GuiButton(200, 0, 0, 200, 20, I18n.format("gui.done"));
        this.buttonList.add(done);
        HeaderFooterLayout.footer(this, done);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 200) {
            this.mc.displayGuiScreen(this.previous);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) {
            this.mc.displayGuiScreen(this.previous);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** Forge event handlers; public for the event bus. */
    public static final class Events {

        /** The end credits closing (to no screen, or the title screen outside a world): back to this screen. */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onOpen(GuiOpenEvent event) {
            GuiScreen back = afterCredits;
            if (back == null || event.getGui() instanceof GuiWinGame) {
                return;
            }
            if (Minecraft.getMinecraft().currentScreen instanceof GuiWinGame) {
                afterCredits = null;
                event.setGui(back);
            } else {
                afterCredits = null;
            }
        }
    }
}

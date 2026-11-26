package com.exiledradio.backportcore.presentation.versions.look264.client;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.Collections;

/**
 * 26.4's Player Options for one player: Mute / Unmute (hide or show their chat messages, with 26.4's feedback line and
 * tooltip) and Done. Friend requests and Report need Mojang's services and are left out.
 */
public class PlayerOptionsScreen extends GuiScreen {

    private final GuiScreen parent;
    private final GameProfile profile;
    private GuiButton mute;

    public PlayerOptionsScreen(GuiScreen parent, GameProfile profile) {
        this.parent = parent;
        this.profile = profile;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.mute = new GuiButton(1, this.width / 2 - 100, this.height / 2 - 10, 200, 20, "");
        this.buttonList.add(this.mute);
        this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height - 28, 200, 20, I18n.format("gui.done")));
        label();
    }

    private boolean muted() {
        return PlayerMutes.muted(this.profile.getId());
    }

    private void label() {
        this.mute.displayString = I18n.format(muted() ? "gui.player_options.unmute" : "gui.player_options.mute");
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            this.mc.displayGuiScreen(this.parent);
        } else if (button.id == 1) {
            boolean now = !muted();
            PlayerMutes.set(this.profile.getId(), this.profile.getName(), now);
            label();
            if (this.mc.ingameGUI != null) {
                this.mc.ingameGUI.getChatGUI().printChatMessage(new TextComponentTranslation(
                        now ? "gui.player_options.mute.feedback" : "gui.player_options.unmute.feedback", this.profile.getName()));
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(this.fontRenderer, I18n.format("gui.player_options.title"), this.width / 2, 20, 0xFFFFFF);
        drawCenteredString(this.fontRenderer, this.profile.getName(), this.width / 2, this.height / 2 - 30, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (this.mute.isMouseOver()) {
            drawHoveringText(Collections.singletonList(I18n.format(muted() ? "gui.player_options.unmute.tooltip" : "gui.player_options.mute.tooltip")),
                    mouseX, mouseY);
        }
    }
}

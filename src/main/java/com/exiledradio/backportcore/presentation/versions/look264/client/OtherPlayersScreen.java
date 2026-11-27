package com.exiledradio.backportcore.presentation.versions.look264.client;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EnumPlayerModelParts;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 26.4's Other Players ({@code SocialInteractionsScreen}, from the pause menu's icon or P): the other players in this
 * world, from the tab list, with All and Muted tabs and a search box; a player opens Player Options. Friends and
 * reporting are Mojang's online services, which 1.12.2 does not have, so the Blocked tab and those buttons are left out.
 * Only in a world others can join ({@code isMultiplayerServer}: a server, or a world opened to LAN).
 */
public class OtherPlayersScreen extends GuiScreen {

    static final int ROW = 24, WIDTH = 238;

    @Nullable
    private final GuiScreen parent;
    private GuiTextField search;
    private boolean mutedTab;
    private int scroll;
    private GuiButton all, muted;
    private final List<GuiButton> rows = new ArrayList<GuiButton>();
    private final List<NetworkPlayerInfo> shown = new ArrayList<NetworkPlayerInfo>();

    public OtherPlayersScreen(@Nullable GuiScreen parent) {
        this.parent = parent;
    }

    /** {@code Minecraft.isMultiplayerServer()}: not this client's own world, or one opened to LAN. */
    public static boolean available() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.player != null && (!mc.isIntegratedServerRunning() || mc.getIntegratedServer() != null && mc.getIntegratedServer().getPublic());
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.buttonList.clear();
        int left = (this.width - WIDTH) / 2;
        this.all = new GuiButton(1, left, 44, 74, 20, "");
        this.muted = new GuiButton(2, left + 78, 44, 74, 20, I18n.format("gui.player_interactions.tab_muted"));
        this.buttonList.add(this.all);
        this.buttonList.add(this.muted);
        this.search = new GuiTextField(3, this.fontRenderer, left + 1, 70, WIDTH - 2, 16);
        this.search.setMaxStringLength(16);
        this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height - 28, 200, 20, I18n.format("gui.done")));
        refresh();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    List<NetworkPlayerInfo> players() {
        List<NetworkPlayerInfo> out = new ArrayList<NetworkPlayerInfo>();
        if (this.mc.player == null || this.mc.player.connection == null) {
            return out;
        }
        UUID self = this.mc.player.getUniqueID();
        for (NetworkPlayerInfo info : this.mc.player.connection.getPlayerInfoMap()) {
            if (!self.equals(info.getGameProfile().getId())) {
                out.add(info);
            }
        }
        out.sort((a, b) -> a.getGameProfile().getName().compareToIgnoreCase(b.getGameProfile().getName()));
        return out;
    }

    void refresh() {
        List<NetworkPlayerInfo> everyone = players();
        this.all.displayString = I18n.format("gui.player_interactions.tab_all", everyone.size());
        this.all.enabled = this.mutedTab;
        this.muted.enabled = !this.mutedTab;
        this.buttonList.removeAll(this.rows);
        this.rows.clear();
        this.shown.clear();
        String q = this.search == null ? "" : this.search.getText().trim().toLowerCase(Locale.ROOT);
        for (NetworkPlayerInfo info : everyone) {
            GameProfile p = info.getGameProfile();
            if (this.mutedTab && !PlayerMutes.muted(p.getId()) || !q.isEmpty() && !p.getName().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            this.shown.add(info);
        }
        int left = (this.width - WIDTH) / 2, top = 92, visible = Math.max(1, (this.height - 40 - top) / ROW);
        this.scroll = Math.max(0, Math.min(this.scroll, this.shown.size() - visible));
        for (int i = this.scroll; i < Math.min(this.shown.size(), this.scroll + visible); i++) {
            GuiButton b = new GuiButton(100 + i, left, top + (i - this.scroll) * ROW, WIDTH, 20, "");
            this.rows.add(b);
        }
        this.buttonList.addAll(this.rows);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            this.mc.displayGuiScreen(this.parent);
        } else if (button.id == 1 || button.id == 2) {
            this.mutedTab = button.id == 2;
            refresh();
        } else if (button.id >= 100 && button.id - 100 < this.shown.size()) {
            this.mc.displayGuiScreen(new PlayerOptionsScreen(this, this.shown.get(button.id - 100).getGameProfile()));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.search.textboxKeyTyped(typedChar, keyCode)) {
            refresh();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.search.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel != 0) {
            this.scroll += wheel > 0 ? -1 : 1;
            refresh();
        }
    }

    @Override
    public void updateScreen() {
        this.search.updateCursorCounter();
        if (this.mc.player != null && this.mc.player.ticksExisted % 20 == 0) {
            refresh();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(this.fontRenderer, title(), this.width / 2, 20, 0xFFFFFF);
        this.search.drawTextBox();
        if (this.search.getText().isEmpty() && !this.search.isFocused()) {
            this.fontRenderer.drawString(I18n.format("gui.player_interactions.search"), this.search.x + 4, this.search.y + 4, 0x808080);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
        for (int i = 0; i < this.rows.size(); i++) {
            GuiButton b = this.rows.get(i);
            NetworkPlayerInfo info = this.shown.get(this.scroll + i);
            drawHead(info, b.x + 4, b.y + 2);
            String name = info.getGameProfile().getName();
            if (PlayerMutes.muted(info.getGameProfile().getId())) {
                name = I18n.format("gui.player_options.status.muted", name);
            }
            this.fontRenderer.drawString(name, b.x + 26, b.y + 6, 0xFFFFFF);
        }
        if (this.shown.isEmpty()) {
            String empty = I18n.format(this.mutedTab ? "gui.player_interactions.empty_muted"
                    : this.search.getText().trim().isEmpty() ? "gui.player_interactions.empty" : "gui.player_interactions.search.no_results");
            drawCenteredString(this.fontRenderer, empty, this.width / 2, 110, 0xA0A0A0);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** {@code gui.player_interactions.title.full} with the server's name, else the plain title. */
    String title() {
        ServerData data = this.mc.getCurrentServerData();
        if (data != null && data.serverName != null && !data.serverName.isEmpty()) {
            return I18n.format("gui.player_interactions.title.full", data.serverName);
        }
        if (this.mc.getIntegratedServer() != null) {
            return I18n.format("gui.player_interactions.title.full", this.mc.getIntegratedServer().getWorldName());
        }
        return I18n.format("gui.player_interactions.title", "");
    }

    /** The player's face, 16 by 16, with the hat layer, as the tab list draws it. */
    void drawHead(NetworkPlayerInfo info, int x, int y) {
        this.mc.getTextureManager().bindTexture(info.getLocationSkin());
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        drawScaledCustomSizeModalRect(x, y, 8.0F, 8.0F, 8, 8, 16, 16, 64.0F, 64.0F);
        if (this.mc.gameSettings.getModelParts().contains(EnumPlayerModelParts.HAT)) {
            GlStateManager.enableBlend();
            drawScaledCustomSizeModalRect(x, y, 40.0F, 8.0F, 8, 8, 16, 16, 64.0F, 64.0F);
        }
    }
}

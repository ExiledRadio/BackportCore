package com.exiledradio.backportcore.settings.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.settings.BackportSettings;
import com.exiledradio.backportcore.settings.Family;
import com.exiledradio.backportcore.settings.LookFeelPage;
import com.exiledradio.backportcore.settings.SettingsModel;
import com.exiledradio.backportcore.settings.SettingsModel.Entry;
import com.exiledradio.backportcore.settings.SettingsModel.Group;
import com.exiledradio.backportcore.settings.SettingsModel.Page;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Core's settings screen. Every installed backport on the left (and the ones not installed, dimmed); the chosen
 * one's features on the right, each an on/off row that opens to its settings. Search covers every mod. Changes
 * wait until Done (or Esc, as vanilla's options screens save on Esc); Undo Changes drops them.
 */
@SideOnly(Side.CLIENT)
public class SettingsScreen extends GuiScreen {

    enum View {
        FEATURES, ALL, CHANGED
    }

    private static final int ID_RESET_PAGE = 1;
    private static final int ID_UNDO = 2;
    private static final int ID_DONE = 3;
    private static final int ID_VIEW = 10;
    private static final int ID_GET = 20;
    private static final long SERVER_TIMEOUT_MS = 3000L;

    private final GuiScreen parent;
    private final List<String> side = new ArrayList<String>();
    private String selected;
    private View view = View.FEATURES;
    final Map<Entry, String[]> staged = new LinkedHashMap<Entry, String[]>();
    private final Set<Group> expanded = new HashSet<Group>();
    private final List<Row> rows = new ArrayList<Row>();

    private GuiTextField search;
    private String query = "";
    private int scroll;
    private int contentHeight;
    private boolean draggingBar;
    private SliderControl dragging;
    private FieldControl focused;
    private long askedAt;

    private int sideX;
    private int sideW;
    private int mainX;
    private int mainW;
    private int top;
    private int bottom;
    private int headerH;
    private int listTop;
    private int listBottom;
    private int infoY;
    private GuiButton done;
    private GuiButton resetPage;
    private GuiButton getIt;
    private final GuiButton[] chips = new GuiButton[3];

    private List<String> hoverLines = new ArrayList<String>();
    private int hoverColor = SettingsStyle.MUTED;
    private String hoverFull = "";

    public SettingsScreen(GuiScreen parent, String modid) {
        this.parent = parent;
        for (Family.Member m : Family.MEMBERS) {
            this.side.add(m.modid);
        }
        for (String id : BackportSettings.modids()) {
            if (!this.side.contains(id)) {
                this.side.add(id);
            }
        }
        this.selected = this.side.contains(modid) ? modid : BackportCore.MOD_ID;
    }

    // --- values ------------------------------------------------------------------------------------------------

    boolean serverControlled(Entry e) {
        return e.side == SettingsModel.Side.SERVER && SettingsClient.remote();
    }

    /** @return the value before this screen's changes: the server's for a server setting on a server */
    String[] base(Entry e) {
        if (serverControlled(e)) {
            Map<String, String[]> sv = SettingsClient.serverValues();
            if (sv != null && sv.containsKey(e.id)) {
                return sv.get(e.id);
            }
        }
        return e.get();
    }

    String[] value(Entry e) {
        String[] v = this.staged.get(e);
        return v != null ? v : base(e);
    }

    void stage(Entry e, String[] v) {
        if (SettingsModel.same(v, base(e))) {
            this.staged.remove(e);
        } else {
            this.staged.put(e, v);
        }
    }

    /** @return null if this setting can be changed here, else why not, as a lang key */
    String lockReason(Entry e) {
        if (serverControlled(e)) {
            Map<String, String[]> sv = SettingsClient.serverValues();
            if (sv == null) {
                return SettingsClient.waitingForServer() && !timedOut() ? "backportcore.settings.server.waiting" : "backportcore.settings.server.none";
            }
            if (!sv.containsKey(e.id)) {
                return "backportcore.settings.server.missing";
            }
            return SettingsClient.serverCanEdit() ? null : "backportcore.settings.server.opsOnly";
        }
        if (e.worldRestart && this.mc.world != null) {
            return "backportcore.settings.leaveWorld";
        }
        return null;
    }

    private boolean timedOut() {
        return System.currentTimeMillis() - this.askedAt > SERVER_TIMEOUT_MS;
    }

    void serverAnswered() {
        rebuild();
    }

    private Page page() {
        // The Family page holds the Look & Feel sections too.
        return BackportCore.MOD_ID.equals(this.selected) ? LookFeelPage.familyPage() : BackportSettings.page(this.selected);
    }

    /** @return the Look &amp; Feel page's master switch, as this screen shows it (with its staged change), or true */
    boolean newerLookOn() {
        Page look = LookFeelPage.page();
        if (look.groups.isEmpty() || look.groups.get(0).toggle == null) {
            return true;
        }
        return "true".equals(value(look.groups.get(0).toggle)[0]);
    }

    /** @return true for a look setting the master switch turns off for now (its own choice is kept) */
    boolean offByMaster(Entry e) {
        return e.lookSection != null && !LookFeelPage.isMaster(e) && !"".equals(e.lookSection) && !newerLookOn();
    }

    private int pageColor(String modid) {
        Family.Member m = Family.get(modid);
        return m != null ? m.color : SettingsStyle.CORE;
    }

    // --- layout -----------------------------------------------------------------------------------------------

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        SettingsClient.open = this;
        this.sideW = this.width >= 400 ? 120 : 92;
        this.sideX = 6;
        this.mainX = this.sideX + this.sideW + 4;
        this.mainW = this.width - this.mainX - 6;
        this.top = 26;
        this.bottom = this.height - 30;
        this.infoY = this.bottom - 24;
        this.headerH = this.mainW >= 290 ? 26 : 48;
        this.listTop = this.top + this.headerH;
        this.listBottom = this.infoY - 2;

        int sw = Math.min(160, this.width / 3);
        String text = this.search == null ? "" : this.search.getText();
        this.search = new GuiTextField(0, this.fontRenderer, this.width - 6 - sw, 5, sw, 16);
        this.search.setMaxStringLength(64);
        this.search.setText(text);

        this.buttonList.clear();
        int bw = this.mainW >= 290 ? 90 : 66;
        int by = this.height - 25;
        int bx = this.mainX + this.mainW - bw;
        this.done = new GuiButton(ID_DONE, bx, by, bw, 20, "");
        this.buttonList.add(this.done);
        this.buttonList.add(new GuiButton(ID_UNDO, bx - bw - 4, by, bw, 20, I18n.format("backportcore.settings.undo")));
        this.resetPage = new GuiButton(ID_RESET_PAGE, bx - 2 * (bw + 4), by, bw, 20, I18n.format("backportcore.settings.resetPage"));
        this.buttonList.add(this.resetPage);
        String[] views = {"backportcore.settings.view.features", "backportcore.settings.view.all", "backportcore.settings.view.changed"};
        int cw = 54;
        int cx = this.headerH == 26 ? this.mainX + this.mainW - 6 - 3 * cw - 4 : this.mainX + 4;
        int cy = this.headerH == 26 ? this.top + 3 : this.top + 25;
        for (int i = 0; i < 3; i++) {
            this.chips[i] = new GuiButton(ID_VIEW + i, cx + i * (cw + 2), cy, cw, 20, I18n.format(views[i]));
            this.buttonList.add(this.chips[i]);
        }
        this.getIt = new GuiButton(ID_GET, this.mainX + this.mainW / 2 - 75, this.top + 110, 150, 20, I18n.format("backportcore.settings.getIt"));
        this.buttonList.add(this.getIt);

        if (SettingsClient.remote() && SettingsClient.serverValues() == null && !SettingsClient.waitingForServer()) {
            this.askedAt = System.currentTimeMillis();
            SettingsClient.askServer();
        } else if (this.askedAt == 0L) {
            this.askedAt = System.currentTimeMillis();
        }
        rebuild();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        if (SettingsClient.open == this) {
            SettingsClient.open = null;
        }
    }

    private void updateButtons() {
        boolean installed = page() != null;
        boolean searching = !this.query.isEmpty();
        for (int i = 0; i < 3; i++) {
            this.chips[i].visible = installed && !searching;
            this.chips[i].enabled = this.view.ordinal() != i;
        }
        this.getIt.visible = !installed && !searching && Family.get(this.selected) != null;
        this.resetPage.visible = installed || searching;
        this.done.displayString = this.staged.isEmpty() ? I18n.format("gui.done")
                : I18n.format("backportcore.settings.doneCount", this.staged.size());
    }

    // --- rows -------------------------------------------------------------------------------------------------

    void rebuild() {
        this.rows.clear();
        this.focused = null;
        if (!this.query.isEmpty()) {
            buildSearch();
        } else {
            Page page = page();
            if (page != null) {
                buildPage(page);
            }
        }
        int y = 0;
        for (Row r : this.rows) {
            r.y = y;
            y += r.h;
        }
        this.contentHeight = y;
        clampScroll();
        updateButtons();
    }

    private void buildPage(Page page) {
        // Newer releases: every outdated family mod on the Family page, a mod's own on its page.
        for (Family.Member m : com.exiledradio.backportcore.settings.Updates.outdated()) {
            if (BackportCore.MOD_ID.equals(page.modid) || m.modid.equals(page.modid)) {
                this.rows.add(new UpdateRow(m));
            }
        }
        if (SettingsClient.remote() && hasServerEntries(page)) {
            if (SettingsClient.serverValues() == null) {
                this.rows.add(new MessageRow(I18n.format(SettingsClient.waitingForServer() && !timedOut()
                        ? "backportcore.settings.server.waiting" : "backportcore.settings.server.none"), SettingsStyle.RESTART));
            } else if (!SettingsClient.serverCanEdit()) {
                this.rows.add(new MessageRow(I18n.format("backportcore.settings.server.opsOnly"), SettingsStyle.MUTED));
            } else {
                this.rows.add(new MessageRow(I18n.format("backportcore.settings.server.op"), SettingsStyle.SERVER));
            }
        }
        if (this.view == View.CHANGED) {
            int before = this.rows.size();
            for (Entry e : page.entries) {
                if (!e.isDefault(value(e)) || this.staged.containsKey(e)) {
                    this.rows.add(new EntryRow(e, 0, SettingsStyle.name(e.group)));
                }
            }
            if (this.rows.size() == before) {
                this.rows.add(new MessageRow(I18n.format("backportcore.settings.allDefault"), SettingsStyle.MUTED));
            }
            return;
        }
        for (Group g : page.groups) {
            if (g.toggle == null && g.entries.isEmpty()) {
                continue;
            }
            this.rows.add(new GroupRow(g, null));
            if (this.view == View.ALL || this.expanded.contains(g)) {
                for (Entry e : g.entries) {
                    this.rows.add(new EntryRow(e, 14, null));
                }
            }
        }
    }

    private static boolean hasServerEntries(Page page) {
        for (Entry e : page.entries) {
            if (e.side == SettingsModel.Side.SERVER) {
                return true;
            }
        }
        return false;
    }

    private void buildSearch() {
        String q = this.query.toLowerCase(Locale.ROOT);
        int found = 0;
        for (Page page : SettingsModel.pages()) {
            String mod = SettingsClient.modName(page.modid);
            for (Group g : page.groups) {
                String gName = SettingsStyle.name(g);
                if (g.toggle != null && matches(q, gName, SettingsStyle.description(g), g.path)) {
                    this.rows.add(new GroupRow(g, mod));
                    found++;
                }
                for (Entry e : g.entries) {
                    if (matches(q, SettingsStyle.name(e), SettingsStyle.description(e), e.id)) {
                        this.rows.add(new EntryRow(e, 0, mod + " › " + gName));
                        found++;
                    }
                }
                if (found >= 300) {
                    return;
                }
            }
        }
        if (found == 0) {
            this.rows.add(new MessageRow(I18n.format("backportcore.settings.noMatch", this.query), SettingsStyle.MUTED));
        }
    }

    private static boolean matches(String q, String... texts) {
        for (String t : texts) {
            if (t != null && t.toLowerCase(Locale.ROOT).contains(q)) {
                return true;
            }
        }
        return false;
    }

    private void clampScroll() {
        int max = Math.max(0, this.contentHeight - (this.listBottom - this.listTop));
        this.scroll = MathHelper.clamp(this.scroll, 0, max);
    }

    // --- drawing ----------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        this.hoverLines = new ArrayList<String>();
        this.hoverFull = "";
        this.hoverColor = SettingsStyle.MUTED;

        // Title and search.
        Family.Member core = Family.get(BackportCore.MOD_ID);
        SettingsStyle.familyIcon(this.mc, core, 6, 5, 16, 1.0F);
        this.fontRenderer.drawStringWithShadow(I18n.format("backportcore.settings.title"), 26, 9, SettingsStyle.CORE);
        this.search.drawTextBox();
        if (this.search.getText().isEmpty() && !this.search.isFocused()) {
            this.fontRenderer.drawString(I18n.format("backportcore.settings.search"), this.search.x + 4, this.search.y + 4, SettingsStyle.DIM);
        }

        // Panels.
        Gui.drawRect(this.sideX, this.top, this.sideX + this.sideW, this.bottom, SettingsStyle.PANEL);
        Gui.drawRect(this.mainX, this.top, this.mainX + this.mainW, this.bottom, SettingsStyle.PANEL);
        drawSide(mouseX, mouseY);
        drawHeader();
        drawList(mouseX, mouseY, partialTicks);
        drawInfo();
        drawFooter();

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (isShiftKeyDown() && !this.hoverFull.isEmpty()) {
            drawHoveringText(this.fontRenderer.listFormattedStringToWidth(this.hoverFull, Math.min(320, this.width - 40)), mouseX, mouseY);
        }
    }

    private int sideItemHeight() {
        int avail = this.bottom - this.top - 8;
        return MathHelper.clamp(avail / Math.max(1, this.side.size()), 12, 22);
    }

    private void drawSide(int mouseX, int mouseY) {
        int ih = sideItemHeight();
        int y = this.top + 4;
        for (String id : this.side) {
            Family.Member m = Family.get(id);
            boolean installed = BackportSettings.page(id) != null;
            boolean sel = id.equals(this.selected) && this.query.isEmpty();
            boolean hover = mouseX >= this.sideX && mouseX < this.sideX + this.sideW && mouseY >= y && mouseY < y + ih;
            if (sel) {
                Gui.drawRect(this.sideX + 2, y, this.sideX + this.sideW - 2, y + ih, SettingsStyle.SELECTED);
                Gui.drawRect(this.sideX + 2, y, this.sideX + 4, y + ih, 0xFF000000 | pageColor(id));
            } else if (hover) {
                Gui.drawRect(this.sideX + 2, y, this.sideX + this.sideW - 2, y + ih, SettingsStyle.ROW_HOVER);
            }
            int iconSize = ih >= 20 ? 16 : ih - 2;
            if (m != null) {
                SettingsStyle.familyIcon(this.mc, m, this.sideX + 6, y + (ih - iconSize) / 2, iconSize, installed ? 1.0F : 0.35F);
            } else if (LookFeelPage.ID.equals(id) && ih >= 18) {
                SettingsStyle.item(this.mc, SettingsStyle.icon("minecraft:item_frame"), this.sideX + 6, y + (ih - 16) / 2);
            }
            int tx = this.sideX + 8 + iconSize;
            int tw = this.sideX + this.sideW - 4 - tx;
            String name = SettingsClient.modName(id);
            if (ih >= 20) {
                this.fontRenderer.drawString(SettingsStyle.trim(this.fontRenderer, name, tw), tx, y + 2, installed ? SettingsStyle.TEXT : SettingsStyle.DIM);
                String newer = installed ? com.exiledradio.backportcore.settings.Updates.newer(id) : null;
                String sub = LookFeelPage.ID.equals(id) ? I18n.format("backportcore.settings.look.sub")
                        : newer != null ? I18n.format("backportcore.settings.update.short", newer)
                        : installed ? (m != null && !m.version.isEmpty() ? m.version + "  " : "") + modVersion(id)
                        : I18n.format("backportcore.settings.notInstalled");
                this.fontRenderer.drawString(SettingsStyle.trim(this.fontRenderer, sub, tw), tx, y + 11, newer != null ? SettingsStyle.CHANGED : SettingsStyle.DIM);
            } else {
                this.fontRenderer.drawString(SettingsStyle.trim(this.fontRenderer, name, tw), tx, y + (ih - 8) / 2, installed ? SettingsStyle.TEXT : SettingsStyle.DIM);
            }
            if (hover && !installed) {
                this.hoverLines.add(I18n.format("backportcore.settings.notInstalled"));
            }
            y += ih;
        }
    }

    private static String modVersion(String modid) {
        ModContainer mc = Loader.instance().getIndexedModList().get(modid);
        return mc != null ? mc.getVersion() : "";
    }

    private void drawHeader() {
        if (!this.query.isEmpty()) {
            this.fontRenderer.drawString(I18n.format("backportcore.settings.searchTitle"), this.mainX + 8, this.top + 5, SettingsStyle.TEXT);
            this.fontRenderer.drawString(I18n.format("backportcore.settings.searchSub"), this.mainX + 8, this.top + 15, SettingsStyle.MUTED);
            Gui.drawRect(this.mainX, this.listTop - 1, this.mainX + this.mainW, this.listTop, SettingsStyle.LINE);
            return;
        }
        Family.Member m = Family.get(this.selected);
        int tx = this.mainX + 6;
        if (m != null) {
            SettingsStyle.familyIcon(this.mc, m, this.mainX + 4, this.top + 3, 20, page() != null ? 1.0F : 0.5F);
            tx = this.mainX + 28;
        }
        int nameW = (this.headerH == 26 ? this.chips[0].x - 4 : this.mainX + this.mainW - 4) - tx;
        this.fontRenderer.drawString(SettingsStyle.trim(this.fontRenderer, SettingsClient.modName(this.selected), nameW), tx, this.top + 4, SettingsStyle.TEXT);
        String sub = LookFeelPage.ID.equals(this.selected) ? I18n.format("backportcore.settings.look.sub")
                : (m != null && !m.version.isEmpty() ? m.version + "  " : "") + modVersion(this.selected);
        this.fontRenderer.drawString(SettingsStyle.trim(this.fontRenderer, sub, nameW), tx, this.top + 14, SettingsStyle.MUTED);
        Gui.drawRect(this.mainX, this.listTop - 1, this.mainX + this.mainW, this.listTop, 0xFF000000 | pageColor(this.selected));
    }

    private void drawList(int mouseX, int mouseY, float partialTicks) {
        if (this.query.isEmpty() && page() == null) {
            drawNotInstalled();
            return;
        }
        int x = this.mainX + 2;
        int w = this.mainW - 10;
        ScaledResolution sr = new ScaledResolution(this.mc);
        int f = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(this.mainX * f, this.mc.displayHeight - this.listBottom * f, this.mainW * f, (this.listBottom - this.listTop) * f);
        boolean inList = mouseX >= this.mainX && mouseX < this.mainX + this.mainW && mouseY >= this.listTop && mouseY < this.listBottom;
        for (Row r : this.rows) {
            int ry = this.listTop + 2 + r.y - this.scroll;
            if (ry + r.h < this.listTop || ry > this.listBottom) {
                continue;
            }
            boolean hover = inList && mouseY >= ry && mouseY < ry + r.h && mouseX < x + w;
            if (hover) {
                r.hover(); // before drawing, so a control under the mouse (Reset) can say what it does instead
            }
            r.draw(x, ry, w, mouseX, mouseY, hover, partialTicks);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);

        int view = this.listBottom - this.listTop;
        if (this.contentHeight > view) {
            int bx = this.mainX + this.mainW - 6;
            Gui.drawRect(bx, this.listTop, bx + 4, this.listBottom, 0x60000000);
            int th = Math.max(16, view * view / this.contentHeight);
            int ty = this.listTop + (view - th) * this.scroll / (this.contentHeight - view);
            Gui.drawRect(bx, ty, bx + 4, ty + th, 0xFF000000 | SettingsStyle.MUTED);
        }
    }

    private void drawNotInstalled() {
        Family.Member m = Family.get(this.selected);
        if (m == null) {
            return;
        }
        int cx = this.mainX + this.mainW / 2;
        SettingsStyle.familyIcon(this.mc, m, cx - 24, this.listTop + 10, 48, 1.0F);
        drawCenteredString(this.fontRenderer, I18n.format("backportcore.settings.notInstalled"), cx, this.listTop + 64, SettingsStyle.MUTED);
        List<String> about = this.fontRenderer.listFormattedStringToWidth(I18n.format(m.aboutKey()), this.mainW - 24);
        int y = this.listTop + 78;
        for (String line : about) {
            drawCenteredString(this.fontRenderer, line, cx, y, SettingsStyle.TEXT);
            y += 10;
        }
        this.getIt.y = Math.min(y + 6, this.listBottom - 22);
    }

    private void drawInfo() {
        Gui.drawRect(this.mainX, this.infoY - 2, this.mainX + this.mainW, this.infoY - 1, SettingsStyle.LINE);
        int y = this.infoY + 1;
        if (this.hoverLines.isEmpty() && (page() != null || !this.query.isEmpty())) {
            List<String> hint = this.fontRenderer.listFormattedStringToWidth(I18n.format("backportcore.settings.hint"), this.mainW - 12);
            for (int i = 0; i < Math.min(2, hint.size()); i++) {
                this.fontRenderer.drawString(hint.get(i), this.mainX + 6, y + i * 10, SettingsStyle.DIM);
            }
            return;
        }
        for (int i = 0; i < Math.min(2, this.hoverLines.size()); i++) {
            String line = this.hoverLines.get(i);
            if (i == 1 && this.hoverLines.size() > 2) {
                line = SettingsStyle.trim(this.fontRenderer, line + " ...", this.mainW - 12 - this.fontRenderer.getStringWidth(I18n.format("backportcore.settings.shiftMore")))
                        + " " + I18n.format("backportcore.settings.shiftMore");
            }
            this.fontRenderer.drawString(line, this.mainX + 6, y, i == 0 ? this.hoverColor : SettingsStyle.MUTED);
            y += 10;
        }
    }

    private void drawFooter() {
        int n = SettingsClient.waitingForRestart();
        if (n > 0) {
            String text = I18n.format(n == 1 ? "backportcore.settings.restart.one" : "backportcore.settings.restart.many", n);
            int w = this.resetPage.x - this.sideX - 6;
            List<String> lines = this.fontRenderer.listFormattedStringToWidth(text, Math.max(40, w));
            int y = lines.size() > 1 ? this.height - 25 : this.height - 19;
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                this.fontRenderer.drawString(lines.get(i), this.sideX, y + i * 10, SettingsStyle.RESTART);
            }
        }
    }

    /** Sets what the info line shows: the first two lines of {@code text}, and the whole of it with Shift. */
    void info(String text, int color) {
        this.hoverFull = text;
        this.hoverColor = color;
        this.hoverLines = this.fontRenderer.listFormattedStringToWidth(text, this.mainW - 12);
    }

    // --- input ------------------------------------------------------------------------------------------------

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_DONE) {
            finish();
        } else if (button.id == ID_UNDO) {
            this.staged.clear();
            rebuild();
        } else if (button.id == ID_RESET_PAGE) {
            List<Entry> entries = new ArrayList<Entry>();
            if (this.query.isEmpty() && page() != null) {
                entries.addAll(page().entries);
            } else {
                for (Row r : this.rows) {
                    if (r instanceof EntryRow) {
                        entries.add(((EntryRow) r).e);
                    }
                }
            }
            for (Entry e : entries) {
                if (lockReason(e) == null && !e.isDefault(value(e))) {
                    stage(e, e.defaults());
                }
            }
            rebuild();
        } else if (button.id >= ID_VIEW && button.id < ID_VIEW + 3) {
            this.view = View.values()[button.id - ID_VIEW];
            this.scroll = 0;
            rebuild();
        } else if (button.id == ID_GET) {
            Family.Member m = Family.get(this.selected);
            if (m != null) {
                com.exiledradio.backportcore.presentation.client.MenuScreens.confirmLink(this, m.curseForgeUrl());
            }
        }
    }

    private void finish() {
        Map<Entry, String[]> changes = new LinkedHashMap<Entry, String[]>();
        for (Map.Entry<Entry, String[]> c : this.staged.entrySet()) {
            if (lockReason(c.getKey()) == null && c.getKey().check(c.getValue()) == null) {
                changes.put(c.getKey(), c.getValue());
            }
        }
        this.staged.clear();
        if (!changes.isEmpty()) {
            SettingsClient.apply(changes);
        }
        this.mc.displayGuiScreen(this.parent);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.search.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.search.isFocused() && this.focused != null) {
            this.focused.field.setFocused(false);
            this.focused = null;
        }
        if (mouseButton != 0) {
            return;
        }
        // The side list.
        if (mouseX >= this.sideX && mouseX < this.sideX + this.sideW && mouseY >= this.top + 4) {
            int i = (mouseY - this.top - 4) / sideItemHeight();
            if (i >= 0 && i < this.side.size()) {
                this.selected = this.side.get(i);
                this.search.setText("");
                this.query = "";
                this.view = View.FEATURES;
                this.scroll = 0;
                rebuild();
                playClick();
            }
            return;
        }
        // The scroll bar.
        int bx = this.mainX + this.mainW - 6;
        if (mouseX >= bx && mouseX < bx + 6 && mouseY >= this.listTop && mouseY < this.listBottom
                && this.contentHeight > this.listBottom - this.listTop) {
            this.draggingBar = true;
            dragBar(mouseY);
            return;
        }
        if (mouseY < this.listTop || mouseY >= this.listBottom || mouseX < this.mainX || mouseX >= bx) {
            return;
        }
        FieldControl before = this.focused;
        this.focused = null;
        for (Row r : new ArrayList<Row>(this.rows)) {
            int ry = this.listTop + 2 + r.y - this.scroll;
            for (Control c : r.controls) {
                if (c instanceof FieldControl) {
                    FieldControl fc = (FieldControl) c;
                    fc.field.mouseClicked(mouseX, mouseY, mouseButton);
                    if (fc.field.isFocused()) {
                        this.focused = fc;
                        this.search.setFocused(false);
                    }
                }
            }
            if (mouseY >= ry && mouseY < ry + r.h) {
                if (r.click(mouseX, mouseY)) {
                    return;
                }
            }
        }
        if (before != null && before != this.focused) {
            before.field.setFocused(false);
        }
    }

    private void dragBar(int mouseY) {
        int view = this.listBottom - this.listTop;
        this.scroll = (mouseY - this.listTop) * (this.contentHeight - view) / Math.max(1, view);
        clampScroll();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.draggingBar) {
            dragBar(mouseY);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        this.draggingBar = false;
        if (this.dragging != null) {
            this.dragging.button.mouseReleased(mouseX, mouseY);
            this.dragging = null;
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            this.scroll -= Integer.signum(wheel) * 22;
            clampScroll();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            finish();
            return;
        }
        if (this.search.isFocused()) {
            this.search.textboxKeyTyped(typedChar, keyCode);
            String q = this.search.getText().trim();
            if (!q.equals(this.query)) {
                this.query = q;
                this.scroll = 0;
                rebuild();
                this.search.setFocused(true);
            }
            return;
        }
        if (this.focused != null) {
            this.focused.key(typedChar, keyCode);
            return;
        }
        if (keyCode == Keyboard.KEY_F && isCtrlKeyDown()) {
            this.search.setFocused(true);
        }
    }

    @Override
    public void updateScreen() {
        this.search.updateCursorCounter();
        if (this.focused != null) {
            this.focused.field.updateCursorCounter();
        }
        // The server's silence becomes "this server does not share its settings".
        if (SettingsClient.remote() && SettingsClient.serverValues() == null && timedOut() && !this.rows.isEmpty()
                && this.rows.get(0) instanceof MessageRow && ((MessageRow) this.rows.get(0)).waiting) {
            rebuild();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }

    void playClick() {
        this.mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(
                net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** Back from the list editor with a new value (null when cancelled). */
    void listEdited(Entry e, String[] value) {
        if (value != null) {
            stage(e, value);
        }
        this.mc.displayGuiScreen(this);
    }

    // =========================================================================================================
    // Rows
    // =========================================================================================================

    abstract class Row {
        int y;
        int h;
        final List<Control> controls = new ArrayList<Control>();

        abstract void draw(int x, int y, int w, int mouseX, int mouseY, boolean hover, float partialTicks);

        /** @return whether the click was used */
        boolean click(int mouseX, int mouseY) {
            for (Control c : this.controls) {
                if (c.visible && c.inside(mouseX, mouseY)) {
                    c.click(mouseX, mouseY);
                    return true;
                }
            }
            return false;
        }

        void hover() {
        }

        /** Places the controls at the row's right edge; @return the x where they start */
        int placeControls(int x, int y, int w, int rowH, int mouseX, int mouseY, float partialTicks) {
            int cx = x + w - 2;
            for (Control c : this.controls) {
                if (!c.visible) {
                    continue;
                }
                cx -= c.w;
                c.x = cx;
                c.y = y + (rowH - 20) / 2;
                c.draw(mouseX, mouseY, partialTicks);
                cx -= 2;
            }
            return cx;
        }
    }

    /** A newer release of a family mod: click to open its CurseForge page. */
    final class UpdateRow extends Row {
        final Family.Member member;
        final String text;

        UpdateRow(Family.Member member) {
            this.member = member;
            this.h = 16;
            String newer = com.exiledradio.backportcore.settings.Updates.newer(member.modid);
            this.text = I18n.format("backportcore.settings.update.row", SettingsClient.modName(member.modid), newer, modVersion(member.modid));
        }

        @Override
        void draw(int x, int y, int w, int mouseX, int mouseY, boolean hover, float partialTicks) {
            String t = SettingsStyle.trim(SettingsScreen.this.fontRenderer, this.text, w - 12);
            SettingsScreen.this.fontRenderer.drawString(hover ? "§n" + t : t, x + 6, y + 4, SettingsStyle.CHANGED);
        }

        @Override
        void hover() {
            SettingsScreen.this.hoverLines.add(I18n.format("backportcore.settings.getIt"));
        }

        @Override
        boolean click(int mouseX, int mouseY) {
            com.exiledradio.backportcore.presentation.client.MenuScreens.confirmLink(SettingsScreen.this, this.member.curseForgeUrl());
            return true;
        }
    }

    final class MessageRow extends Row {
        final String text;
        final int color;
        final boolean waiting;

        MessageRow(String text, int color) {
            this.text = text;
            this.color = color;
            this.h = 16;
            this.waiting = text.equals(I18n.format("backportcore.settings.server.waiting"));
        }

        @Override
        void draw(int x, int y, int w, int mouseX, int mouseY, boolean hover, float partialTicks) {
            SettingsScreen.this.fontRenderer.drawString(SettingsStyle.trim(SettingsScreen.this.fontRenderer, this.text, w - 12), x + 6, y + 4, this.color);
        }
    }

    /** Badges for a setting, after its name. */
    private int badges(Entry e, int x, int y, int limit) {
        List<String> texts = new ArrayList<String>();
        List<Integer> colors = new ArrayList<Integer>();
        if (e.restart) {
            texts.add(I18n.format("backportcore.settings.badge.restart"));
            colors.add(SettingsStyle.RESTART);
        }
        if (e.worldRestart) {
            texts.add(I18n.format("backportcore.settings.badge.rejoin"));
            colors.add(SettingsStyle.RESTART);
        }
        if (e.newChunks) {
            texts.add(I18n.format("backportcore.settings.badge.newChunks"));
            colors.add(SettingsStyle.CHUNKS);
        }
        String versions = LookFeelPage.versions(e);
        if (versions != null) {
            texts.add(versions);
            colors.add(SettingsStyle.CORE);
        }
        if (e.side == SettingsModel.Side.CLIENT) {
            texts.add(I18n.format("backportcore.settings.badge.client"));
            colors.add(SettingsStyle.CLIENT);
        } else {
            texts.add(I18n.format("backportcore.settings.badge.server"));
            colors.add(SettingsStyle.SERVER);
        }
        int bx = x;
        for (int i = 0; i < texts.size(); i++) {
            int bw = SettingsStyle.badgeWidth(this.fontRenderer, texts.get(i));
            if (bx + bw > limit) {
                break;
            }
            SettingsStyle.badge(this.fontRenderer, texts.get(i), bx, y - 1, colors.get(i));
            bx += bw + 3;
        }
        return bx;
    }

    /** The info line for a setting. */
    private void infoFor(Entry e, String description) {
        String why = lockReason(e);
        StringBuilder b = new StringBuilder();
        if (why != null) {
            b.append(I18n.format(why)).append(' ');
        } else if (offByMaster(e)) {
            b.append(I18n.format("backportcore.settings.look.offByMaster")).append(' ');
        }
        b.append(description.replace('\n', ' ').trim());
        String changedIn = LookFeelPage.versionList(e);
        if (!changedIn.isEmpty()) {
            b.append(' ').append(I18n.format("backportcore.settings.look.changedIn", changedIn));
        }
        if (e.type != SettingsModel.Type.LIST) {
            b.append(' ').append(I18n.format("backportcore.settings.default", SettingsStyle.value(e, e.defaults())));
            if (e.vanilla != null) {
                b.append(' ').append(I18n.format("backportcore.settings.vanilla",
                        e.type == SettingsModel.Type.ENUM ? SettingsStyle.enumValue(e, e.vanilla) : e.vanilla));
            }
        }
        if (this.staged.containsKey(e) && e.waitsForRestart(value(e))) {
            b.append(' ').append(I18n.format(serverControlled(e) ? "backportcore.settings.afterServerRestart" : "backportcore.settings.afterRestart"));
        }
        info(b.toString(), why != null ? SettingsStyle.RESTART : SettingsStyle.TEXT);
    }

    /** A feature: its on/off switch, and its settings under it. */
    final class GroupRow extends Row {
        final Group g;
        final String crumb;

        GroupRow(Group g, String crumb) {
            this.g = g;
            this.crumb = crumb;
            this.h = 24;
            if (g.toggle != null) {
                this.controls.add(control(g.toggle));
                this.controls.add(new ResetControl(g.toggle));
            }
        }

        private boolean openable() {
            return !this.g.entries.isEmpty() && this.crumb == null && SettingsScreen.this.view == View.FEATURES;
        }

        @Override
        void draw(int x, int y, int w, int mouseX, int mouseY, boolean hover, float partialTicks) {
            Gui.drawRect(x, y + 1, x + w, y + this.h - 1, hover ? SettingsStyle.ROW_HOVER : SettingsStyle.ROW);
            int left = placeControls(x, y, w, this.h, mouseX, mouseY, partialTicks);
            if (openable()) {
                boolean open = SettingsScreen.this.expanded.contains(this.g);
                SettingsScreen.this.fontRenderer.drawString(open ? "▾" : "▸", x + 3, y + 8, SettingsStyle.MUTED);
            } else if (!this.g.entries.isEmpty() && SettingsScreen.this.view == View.ALL) {
                SettingsScreen.this.fontRenderer.drawString("▾", x + 3, y + 8, SettingsStyle.DIM);
            }
            ItemStack icon = SettingsStyle.icon(this.g.icon);
            if (icon != null) {
                SettingsStyle.item(SettingsScreen.this.mc, icon, x + 12, y + 4);
            } else {
                SettingsStyle.swatch(x + 12, y + 4, pageColor(this.g.page.modid));
            }
            int tx = x + 32;
            String name = SettingsStyle.name(this.g);
            boolean dirty = false;
            for (Entry e : this.g.all()) {
                dirty |= SettingsScreen.this.staged.containsKey(e);
            }
            if (this.crumb != null) {
                SettingsScreen.this.fontRenderer.drawString(SettingsStyle.trim(SettingsScreen.this.fontRenderer, this.crumb, left - tx - 4), tx, y + 2, SettingsStyle.DIM);
                drawName(name, dirty, tx, y + 12, left);
                return;
            }
            drawName(name, dirty, tx, y + 3, left);
            String desc = SettingsStyle.shortLine(this.g);
            if (this.g.entries.isEmpty() || SettingsScreen.this.view == View.ALL || SettingsScreen.this.expanded.contains(this.g)) {
                SettingsScreen.this.fontRenderer.drawString(SettingsStyle.trim(SettingsScreen.this.fontRenderer, desc, left - tx - 4), tx, y + 13, SettingsStyle.MUTED);
            } else {
                String more = this.g.entries.size() == 1 ? I18n.format("backportcore.settings.more.one")
                        : I18n.format("backportcore.settings.more", this.g.entries.size());
                int mw = SettingsScreen.this.fontRenderer.getStringWidth(more);
                SettingsScreen.this.fontRenderer.drawString(SettingsStyle.trim(SettingsScreen.this.fontRenderer, desc, left - tx - 10 - mw), tx, y + 13, SettingsStyle.MUTED);
                SettingsScreen.this.fontRenderer.drawString(more, left - 4 - mw, y + 13, SettingsStyle.DIM);
            }
        }

        private void drawName(String name, boolean dirty, int tx, int ty, int left) {
            String shown = SettingsStyle.trim(SettingsScreen.this.fontRenderer, name, left - tx - 8);
            SettingsScreen.this.fontRenderer.drawString(shown, tx, ty, SettingsStyle.TEXT);
            int end = tx + SettingsScreen.this.fontRenderer.getStringWidth(shown);
            if (dirty) {
                SettingsScreen.this.fontRenderer.drawString("*", end + 1, ty, SettingsStyle.CHANGED);
                end += 6;
            }
            if (this.g.toggle != null) {
                badges(this.g.toggle, end + 4, ty, left - 4);
            }
        }

        @Override
        boolean click(int mouseX, int mouseY) {
            if (super.click(mouseX, mouseY)) {
                return true;
            }
            if (openable()) {
                if (!SettingsScreen.this.expanded.remove(this.g)) {
                    SettingsScreen.this.expanded.add(this.g);
                }
                rebuild();
                playClick();
                return true;
            }
            return false;
        }

        @Override
        void hover() {
            if (this.g.toggle != null) {
                String own = SettingsStyle.description(this.g.toggle);
                infoFor(this.g.toggle, this.g.toggle.name.equals("enabled") || own.isEmpty() ? SettingsStyle.description(this.g) : own);
            } else {
                info(SettingsStyle.description(this.g), SettingsStyle.TEXT);
            }
        }
    }

    /** One setting. */
    final class EntryRow extends Row {
        final Entry e;
        final int indent;
        final String crumb;

        EntryRow(Entry e, int indent, String crumb) {
            this.e = e;
            this.indent = indent;
            this.crumb = crumb;
            this.h = crumb != null ? 24 : 22;
            this.controls.add(control(e));
            this.controls.add(new ResetControl(e));
        }

        @Override
        void draw(int x, int y, int w, int mouseX, int mouseY, boolean hover, float partialTicks) {
            if (hover) {
                Gui.drawRect(x + this.indent, y + 1, x + w, y + this.h - 1, SettingsStyle.ROW_HOVER);
            }
            if (this.indent > 0) {
                Gui.drawRect(x + 8, y, x + 9, y + this.h, SettingsStyle.LINE);
            }
            int left = placeControls(x, y, w, this.h, mouseX, mouseY, partialTicks);
            int tx = x + 6 + this.indent;
            String name = SettingsStyle.name(this.e);
            int nameY = y + 2;
            if (this.crumb != null) {
                SettingsScreen.this.fontRenderer.drawString(SettingsStyle.trim(SettingsScreen.this.fontRenderer, this.crumb, left - tx - 4), tx, y + 2, SettingsStyle.DIM);
                nameY = y + 12;
            }
            String shown = SettingsStyle.trim(SettingsScreen.this.fontRenderer, name, left - tx - 8);
            boolean locked = lockReason(this.e) != null || offByMaster(this.e);
            SettingsScreen.this.fontRenderer.drawString(shown, tx, nameY, locked ? SettingsStyle.MUTED : SettingsStyle.TEXT);
            int end = tx + SettingsScreen.this.fontRenderer.getStringWidth(shown);
            if (SettingsScreen.this.staged.containsKey(this.e)) {
                SettingsScreen.this.fontRenderer.drawString("*", end + 1, nameY, SettingsStyle.CHANGED);
                end += 6;
            }
            badges(this.e, end + 4, nameY, left - 4);
            if (this.crumb == null) {
                String desc = SettingsStyle.shortLine(this.e);
                SettingsScreen.this.fontRenderer.drawString(SettingsStyle.trim(SettingsScreen.this.fontRenderer, desc, left - tx - 4), tx, y + 12, SettingsStyle.MUTED);
            }
        }

        @Override
        void hover() {
            infoFor(this.e, SettingsStyle.description(this.e));
        }
    }

    // =========================================================================================================
    // Controls
    // =========================================================================================================

    Control control(Entry e) {
        switch (e.type) {
            case BOOL:
                return new ToggleControl(e);
            case ENUM:
                return new CycleControl(e);
            case LIST:
                return new ListControl(e);
            case INT:
                if (sliderFits(e)) {
                    return new SliderControl(e);
                }
                return new FieldControl(e, 70);
            case DOUBLE:
                return new FieldControl(e, 70);
            default:
                return new FieldControl(e, 110);
        }
    }

    /** @return whether an integer's range is small enough for a slider */
    static boolean sliderFits(Entry e) {
        try {
            long min = Long.parseLong(e.min());
            long max = Long.parseLong(e.max());
            return max > min && max - min <= 1000;
        } catch (NumberFormatException | NullPointerException ex) {
            return false;
        }
    }

    abstract class Control {
        final Entry e;
        int x;
        int y;
        final int w;
        boolean visible = true;

        Control(Entry e, int w) {
            this.e = e;
            this.w = w;
        }

        boolean inside(int mouseX, int mouseY) {
            return mouseX >= this.x && mouseX < this.x + this.w && mouseY >= this.y && mouseY < this.y + 20;
        }

        boolean enabled() {
            return lockReason(this.e) == null;
        }

        abstract void draw(int mouseX, int mouseY, float partialTicks);

        abstract void click(int mouseX, int mouseY);
    }

    /** A control drawn as one of vanilla's buttons. */
    abstract class ButtonControl extends Control {
        final GuiButton button;

        ButtonControl(Entry e, int w) {
            this(e, w, new GuiButton(-1, 0, 0, w, 20, ""));
        }

        ButtonControl(Entry e, int w, GuiButton button) {
            super(e, w);
            this.button = button;
        }

        abstract String label();

        abstract void press();

        @Override
        void draw(int mouseX, int mouseY, float partialTicks) {
            this.button.x = this.x;
            this.button.y = this.y;
            this.button.enabled = enabled();
            this.button.displayString = label();
            this.button.drawButton(SettingsScreen.this.mc, mouseX, mouseY, partialTicks);
        }

        @Override
        void click(int mouseX, int mouseY) {
            if (enabled()) {
                this.button.playPressSound(SettingsScreen.this.mc.getSoundHandler());
                press();
            }
        }
    }

    final class ToggleControl extends ButtonControl {
        ToggleControl(Entry e) {
            super(e, 44);
        }

        @Override
        String label() {
            return SettingsStyle.value(this.e, value(this.e));
        }

        @Override
        void press() {
            stage(this.e, new String[]{Boolean.toString(!"true".equals(value(this.e)[0]))});
            updateButtons();
        }
    }

    final class CycleControl extends ButtonControl {
        CycleControl(Entry e) {
            super(e, 100);
        }

        @Override
        String label() {
            return SettingsStyle.trim(SettingsScreen.this.fontRenderer, SettingsStyle.value(this.e, value(this.e)), this.w - 8);
        }

        @Override
        void press() {
            String[] values = this.e.validValues();
            int i = java.util.Arrays.asList(values).indexOf(value(this.e)[0]);
            i = (i + (isShiftKeyDown() ? values.length - 1 : 1)) % values.length;
            stage(this.e, new String[]{values[i]});
            updateButtons();
        }
    }

    final class ListControl extends ButtonControl {
        ListControl(Entry e) {
            super(e, 70);
        }

        @Override
        String label() {
            return I18n.format("backportcore.settings.list.edit", value(this.e).length);
        }

        @Override
        void press() {
            SettingsScreen.this.mc.displayGuiScreen(new ListEditScreen(SettingsScreen.this, this.e, value(this.e), enabled()));
        }

        @Override
        void click(int mouseX, int mouseY) {
            // Opens read-only when locked, so the list can still be read.
            this.button.playPressSound(SettingsScreen.this.mc.getSoundHandler());
            press();
        }

        @Override
        void draw(int mouseX, int mouseY, float partialTicks) {
            super.draw(mouseX, mouseY, partialTicks);
            this.button.enabled = true;
        }
    }

    /** "Reset to default", shown only while the value is not the default: Forge's undo arrow, as its config screen draws it. */
    final class ResetControl extends ButtonControl {
        ResetControl(Entry e) {
            super(e, 20, new net.minecraftforge.fml.client.config.GuiUnicodeGlyphButton(-1, 0, 0, 20, 20, "",
                    net.minecraftforge.fml.client.config.GuiUtils.UNDO_CHAR, 2.0F));
        }

        @Override
        String label() {
            return "";
        }

        @Override
        void draw(int mouseX, int mouseY, float partialTicks) {
            if (this.e.isDefault(value(this.e)) || !enabled()) {
                return;
            }
            super.draw(mouseX, mouseY, partialTicks);
            if (inside(mouseX, mouseY)) {
                info(I18n.format("backportcore.settings.resetOne", SettingsStyle.value(this.e, this.e.defaults())), SettingsStyle.TEXT);
            }
        }

        @Override
        boolean inside(int mouseX, int mouseY) {
            return !this.e.isDefault(value(this.e)) && enabled() && super.inside(mouseX, mouseY);
        }

        @Override
        void press() {
            stage(this.e, this.e.defaults());
            rebuildFieldsOf(this.e);
            updateButtons();
        }
    }

    /** A text field's text follows a reset. */
    void rebuildFieldsOf(Entry e) {
        for (Row r : this.rows) {
            for (Control c : r.controls) {
                if (c instanceof FieldControl && c.e == e) {
                    ((FieldControl) c).field.setText(value(e)[0]);
                }
            }
        }
    }

    /** An integer with a small range, as a vanilla slider. A tick marks vanilla's value. */
    final class SliderControl extends Control {
        final int min;
        final int max;
        final GuiButton button;
        boolean drag;

        SliderControl(Entry e) {
            super(e, 100);
            this.min = Integer.parseInt(e.min());
            this.max = Integer.parseInt(e.max());
            this.button = new GuiButton(-1, 0, 0, 100, 20, "") {
                @Override
                protected int getHoverState(boolean mouseOver) {
                    return 0;
                }

                @Override
                protected void mouseDragged(net.minecraft.client.Minecraft mc, int mouseX, int mouseY) {
                    if (this.visible) {
                        if (SliderControl.this.drag) {
                            setFrom(mouseX);
                        }
                        mc.getTextureManager().bindTexture(BUTTON_TEXTURES);
                        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                        int knob = this.x + (int) (fraction() * (double) (this.width - 8));
                        drawTexturedModalRect(knob, this.y, 0, com.exiledradio.backportcore.presentation.client.PresentationHooks.sliderHandleRow(this, 66), 4, 20);
                        drawTexturedModalRect(knob + 4, this.y, 196, com.exiledradio.backportcore.presentation.client.PresentationHooks.sliderHandleRow(this, 66), 4, 20);
                    }
                }

                @Override
                public void mouseReleased(int mouseX, int mouseY) {
                    SliderControl.this.drag = false;
                }
            };
        }

        int current() {
            try {
                return Integer.parseInt(value(this.e)[0].trim());
            } catch (NumberFormatException ex) {
                return this.min;
            }
        }

        double fraction() {
            return (double) (current() - this.min) / (double) (this.max - this.min);
        }

        void setFrom(int mouseX) {
            double f = MathHelper.clamp((double) (mouseX - (this.button.x + 4)) / (double) (this.button.width - 8), 0.0D, 1.0D);
            int v = this.min + (int) Math.round(f * (this.max - this.min));
            if (v != current()) {
                stage(this.e, new String[]{Integer.toString(v)});
                updateButtons();
            }
        }

        @Override
        void draw(int mouseX, int mouseY, float partialTicks) {
            this.button.x = this.x;
            this.button.y = this.y;
            this.button.enabled = enabled();
            this.button.displayString = Integer.toString(current());
            this.button.drawButton(SettingsScreen.this.mc, mouseX, mouseY, partialTicks);
            String mark = this.e.vanilla != null ? this.e.vanilla : this.e.defaults()[0];
            try {
                int v = Integer.parseInt(mark.trim());
                double f = (double) (v - this.min) / (double) (this.max - this.min);
                int tx = this.x + 4 + (int) (f * (this.w - 8));
                Gui.drawRect(tx - 1, this.y + 17, tx + 1, this.y + 20, 0xFF000000 | SettingsStyle.CHANGED);
            } catch (NumberFormatException ignored) {
            }
        }

        @Override
        void click(int mouseX, int mouseY) {
            if (!enabled()) {
                return;
            }
            this.drag = true;
            SettingsScreen.this.dragging = this;
            setFrom(mouseX);
        }
    }

    /** A number or a line of text. Red while it is not a value the setting takes. */
    final class FieldControl extends Control {
        final GuiTextField field;
        private boolean valid = true;

        FieldControl(Entry e, int w) {
            super(e, w);
            this.field = new GuiTextField(-1, SettingsScreen.this.fontRenderer, 0, 0, w - 2, 18);
            this.field.setMaxStringLength(e.type == SettingsModel.Type.STRING ? 512 : 32);
            this.field.setText(value(e)[0]);
            this.field.setCursorPositionZero();
        }

        @Override
        void draw(int mouseX, int mouseY, float partialTicks) {
            this.field.x = this.x + 1;
            this.field.y = this.y + 1;
            this.field.setEnabled(enabled());
            this.field.setTextColor(!this.valid ? SettingsStyle.ERROR : enabled() ? 0xE0E0E0 : 0x707070);
            if (!this.field.isFocused() && !this.field.getText().equals(value(this.e)[0]) && this.valid) {
                this.field.setText(value(this.e)[0]);
            }
            this.field.drawTextBox();
        }

        @Override
        void click(int mouseX, int mouseY) {
            // Focus is handled by the screen's click pass.
        }

        void key(char typedChar, int keyCode) {
            if (!enabled()) {
                return;
            }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_TAB) {
                this.field.setFocused(false);
                SettingsScreen.this.focused = null;
                return;
            }
            this.field.textboxKeyTyped(typedChar, keyCode);
            String[] v = {this.field.getText()};
            this.valid = this.e.check(v) == null;
            if (this.valid) {
                stage(this.e, v);
                updateButtons();
            }
        }
    }
}

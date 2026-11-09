package com.exiledradio.backportcore.settings.client;

import com.exiledradio.backportcore.settings.Setting;
import com.exiledradio.backportcore.settings.SettingsModel;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.registries.IForgeRegistry;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A list setting, one entry per row. Each row says whether the id it names exists (a block, item, mob or biome,
 * by {@code @Setting.ListOf} or by what the defaults name), and typing an id suggests matching ones.
 */
@SideOnly(Side.CLIENT)
final class ListEditScreen extends GuiScreen {

    private static final int ID_ADD = 1;
    private static final int ID_DONE = 2;
    private static final int ID_CANCEL = 3;
    private static final int ROW_H = 22;

    private final SettingsScreen parent;
    private final SettingsModel.Entry entry;
    private final boolean editable;
    private final Setting.Kind kind;
    private final List<GuiTextField> fields = new ArrayList<GuiTextField>();
    private final List<String> start;
    private List<String> suggestions = Collections.emptyList();
    private GuiTextField focused;
    private int scroll;
    private int listTop;
    private int listBottom;
    private int fieldX;
    private int fieldW;

    ListEditScreen(SettingsScreen parent, SettingsModel.Entry entry, String[] value, boolean editable) {
        this.parent = parent;
        this.entry = entry;
        this.editable = editable;
        this.kind = kindOf(entry);
        this.start = new ArrayList<String>();
        Collections.addAll(this.start, value);
    }

    private static Setting.Kind kindOf(SettingsModel.Entry e) {
        if (e.listKind != Setting.Kind.ANY || e.elementType != SettingsModel.Type.STRING) {
            return e.listKind;
        }
        String[] defaults = e.defaults();
        if (defaults.length == 0) {
            return Setting.Kind.ANY;
        }
        for (Setting.Kind k : new Setting.Kind[]{Setting.Kind.BLOCK, Setting.Kind.ITEM, Setting.Kind.ENTITY, Setting.Kind.BIOME}) {
            boolean all = true;
            for (String d : defaults) {
                ResourceLocation rl = id(d);
                if (rl == null || (Loader.isModLoaded(rl.getNamespace()) || "minecraft".equals(rl.getNamespace())) && !registry(k).containsKey(rl)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return k;
            }
        }
        return Setting.Kind.ANY;
    }

    private static IForgeRegistry<?> registry(Setting.Kind k) {
        switch (k) {
            case BLOCK:
                return ForgeRegistries.BLOCKS;
            case ITEM:
                return ForgeRegistries.ITEMS;
            case ENTITY:
                return ForgeRegistries.ENTITIES;
            case BIOME:
                return ForgeRegistries.BIOMES;
            default:
                return null;
        }
    }

    /** @return the id an entry names: the text before the first {@code =}, space or {@code @}, as modid:name */
    static ResourceLocation id(String entry) {
        String s = entry.trim();
        int cut = s.length();
        for (char c : new char[]{'=', ' ', '@', ';', ','}) {
            int i = s.indexOf(c);
            if (i >= 0 && i < cut) {
                cut = i;
            }
        }
        s = s.substring(0, cut);
        int first = s.indexOf(':');
        if (first <= 0) {
            return null;
        }
        int second = s.indexOf(':', first + 1);
        if (second > 0) {
            s = s.substring(0, second); // modid:name:meta
        }
        try {
            return new ResourceLocation(s.toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        this.listTop = 44;
        this.listBottom = this.height - 32;
        this.fieldW = Math.min(260, this.width - 150);
        this.fieldX = this.width / 2 - this.fieldW / 2 - 40;
        List<String> current = new ArrayList<String>();
        if (this.fields.isEmpty()) {
            current.addAll(this.start);
        } else {
            for (GuiTextField f : this.fields) {
                current.add(f.getText());
            }
        }
        this.fields.clear();
        for (String s : current) {
            addField(s);
        }
        this.buttonList.clear();
        int by = this.height - 26;
        if (this.editable) {
            this.buttonList.add(new GuiButton(ID_ADD, this.width / 2 - 154, by, 100, 20, I18n.format("backportcore.settings.list.add")));
            this.buttonList.add(new GuiButton(ID_DONE, this.width / 2 - 50, by, 100, 20, I18n.format("gui.done")));
            this.buttonList.add(new GuiButton(ID_CANCEL, this.width / 2 + 54, by, 100, 20, I18n.format("gui.cancel")));
        } else {
            this.buttonList.add(new GuiButton(ID_CANCEL, this.width / 2 - 50, by, 100, 20, I18n.format("gui.back")));
        }
    }

    private GuiTextField addField(String text) {
        GuiTextField f = new GuiTextField(this.fields.size(), this.fontRenderer, 0, 0, this.fieldW, 18);
        f.setMaxStringLength(512);
        f.setText(text);
        f.setCursorPositionZero();
        f.setEnabled(this.editable);
        this.fields.add(f);
        return f;
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private String[] values() {
        List<String> out = new ArrayList<String>();
        for (GuiTextField f : this.fields) {
            String s = f.getText().trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out.toArray(new String[0]);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_ADD) {
            GuiTextField f = addField("");
            focus(f);
            this.scroll = Integer.MAX_VALUE;
            clampScroll();
        } else if (button.id == ID_DONE) {
            String[] v = values();
            this.parent.listEdited(this.entry, this.entry.check(v) == null ? v : null);
        } else if (button.id == ID_CANCEL) {
            this.parent.listEdited(this.entry, null);
        }
    }

    private void focus(GuiTextField f) {
        for (GuiTextField o : this.fields) {
            o.setFocused(o == f);
        }
        this.focused = f;
        updateSuggestions();
    }

    private void clampScroll() {
        int max = Math.max(0, this.fields.size() * ROW_H - (this.listBottom - this.listTop));
        this.scroll = MathHelper.clamp(this.scroll, 0, max);
    }

    // --- checks and suggestions ---------------------------------------------------------------------------------

    /** @return the row's state: null when fine, else a lang key; {@code notInstalled} is gray, the rest red */
    private String state(String text) {
        String s = text.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (this.entry.check(new String[]{s}) != null) {
            return "backportcore.settings.list.invalid";
        }
        IForgeRegistry<?> reg = registry(this.kind);
        if (reg == null) {
            return null;
        }
        ResourceLocation rl = id(s);
        if (rl == null) {
            return "backportcore.settings.list.invalid";
        }
        if ("oredict".equals(rl.getNamespace())) {
            return null; // an ore dictionary name, not a registry id
        }
        if (!"minecraft".equals(rl.getNamespace()) && !Loader.isModLoaded(rl.getNamespace())) {
            return "backportcore.settings.list.notInstalled";
        }
        return reg.containsKey(rl) ? null : "backportcore.settings.list.unknown";
    }

    private void updateSuggestions() {
        this.suggestions = Collections.emptyList();
        IForgeRegistry<?> reg = registry(this.kind);
        if (reg == null || this.focused == null) {
            return;
        }
        String text = this.focused.getText().trim().toLowerCase(Locale.ROOT);
        int cut = text.length();
        for (char c : new char[]{'=', ' ', '@'}) {
            int i = text.indexOf(c);
            if (i >= 0 && i < cut) {
                cut = i;
            }
        }
        if (cut < text.length() || text.length() < 2) {
            return;
        }
        List<String> out = new ArrayList<String>();
        Set<ResourceLocation> keys = reg.getKeys();
        for (ResourceLocation rl : keys) {
            String full = rl.toString();
            if (!full.equals(text) && (full.startsWith(text) || rl.getPath().startsWith(text))) {
                out.add(full);
            }
        }
        Collections.sort(out);
        this.suggestions = out.size() > 6 ? out.subList(0, 6) : out;
    }

    private void pick(String id) {
        if (this.focused == null) {
            return;
        }
        String text = this.focused.getText();
        int cut = text.length();
        for (char c : new char[]{'=', ' ', '@'}) {
            int i = text.indexOf(c);
            if (i >= 0 && i < cut) {
                cut = i;
            }
        }
        this.focused.setText(id + text.substring(cut));
        this.focused.setCursorPositionEnd();
        updateSuggestions();
    }

    // --- drawing ------------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        String title = SettingsClient.modName(this.entry.group.page.modid) + " › " + SettingsStyle.name(this.entry.group)
                + " › " + SettingsStyle.name(this.entry);
        drawCenteredString(this.fontRenderer, SettingsStyle.trim(this.fontRenderer, title, this.width - 20), this.width / 2, 8, 0xFFFFFF);
        String desc = SettingsStyle.firstSentence(SettingsStyle.description(this.entry));
        drawCenteredString(this.fontRenderer, SettingsStyle.trim(this.fontRenderer, desc, this.width - 20), this.width / 2, 20, SettingsStyle.MUTED);
        String count = I18n.format(this.editable ? "backportcore.settings.list.count" : "backportcore.settings.list.readOnly", values().length);
        drawCenteredString(this.fontRenderer, count, this.width / 2, 31, SettingsStyle.DIM);

        Gui.drawRect(this.fieldX - 6, this.listTop - 2, this.fieldX + this.fieldW + 150, this.listBottom + 2, SettingsStyle.PANEL);
        for (int i = 0; i < this.fields.size(); i++) {
            int y = this.listTop + i * ROW_H - this.scroll;
            GuiTextField f = this.fields.get(i);
            boolean visible = y >= this.listTop - 1 && y + 20 <= this.listBottom + 1;
            f.setVisible(visible);
            if (!visible) {
                continue;
            }
            f.x = this.fieldX;
            f.y = y + 1;
            String st = state(f.getText());
            f.setTextColor(st == null || st.endsWith("notInstalled") ? 0xE0E0E0 : SettingsStyle.ERROR);
            f.drawTextBox();
            if (this.editable) {
                int rx = this.fieldX + this.fieldW + 4;
                boolean over = mouseX >= rx && mouseX < rx + 14 && mouseY >= y + 1 && mouseY < y + 19;
                this.fontRenderer.drawString("✕", rx + 3, y + 6, over ? SettingsStyle.ERROR : SettingsStyle.MUTED);
            }
            if (st != null) {
                this.fontRenderer.drawString(I18n.format(st), this.fieldX + this.fieldW + 22, y + 6,
                        st.endsWith("notInstalled") ? SettingsStyle.DIM : SettingsStyle.ERROR);
            }
        }
        if (this.fields.isEmpty()) {
            drawCenteredString(this.fontRenderer, I18n.format("backportcore.settings.list.empty"), this.width / 2, this.listTop + 6, SettingsStyle.MUTED);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Suggestions, over everything else.
        if (this.focused != null && !this.suggestions.isEmpty() && this.focused.getVisible()) {
            int sx = this.focused.x;
            int sy = this.focused.y + 19;
            int sw = this.fieldW;
            Gui.drawRect(sx, sy, sx + sw, sy + this.suggestions.size() * 11 + 2, 0xF0000000);
            for (int i = 0; i < this.suggestions.size(); i++) {
                int y = sy + 1 + i * 11;
                boolean over = mouseX >= sx && mouseX < sx + sw && mouseY >= y && mouseY < y + 11;
                this.fontRenderer.drawString(this.suggestions.get(i), sx + 3, y + 2, over ? SettingsStyle.CHANGED : (i == 0 ? 0xFFFFFF : SettingsStyle.MUTED));
            }
        }
    }

    // --- input --------------------------------------------------------------------------------------------------

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.focused != null && !this.suggestions.isEmpty()) {
            int sx = this.focused.x;
            int sy = this.focused.y + 20;
            if (mouseX >= sx && mouseX < sx + this.fieldW && mouseY >= sy && mouseY < sy + this.suggestions.size() * 11) {
                pick(this.suggestions.get((mouseY - sy) / 11));
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (!this.editable) {
            return;
        }
        for (int i = 0; i < this.fields.size(); i++) {
            GuiTextField f = this.fields.get(i);
            if (!f.getVisible()) {
                continue;
            }
            int rx = this.fieldX + this.fieldW + 4;
            if (mouseX >= rx && mouseX < rx + 14 && mouseY >= f.y && mouseY < f.y + 18) {
                this.fields.remove(i);
                if (this.focused == f) {
                    this.focused = null;
                    this.suggestions = Collections.emptyList();
                }
                clampScroll();
                return;
            }
        }
        for (GuiTextField f : this.fields) {
            if (f.getVisible() && mouseX >= f.x && mouseX < f.x + this.fieldW && mouseY >= f.y && mouseY < f.y + 18) {
                focus(f);
                f.mouseClicked(mouseX, mouseY, mouseButton);
                return;
            }
        }
        focus(null);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.parent.listEdited(this.entry, null);
            return;
        }
        if (this.focused == null || !this.editable) {
            return;
        }
        if (keyCode == Keyboard.KEY_TAB && !this.suggestions.isEmpty()) {
            pick(this.suggestions.get(0));
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            int i = this.fields.indexOf(this.focused);
            if (i == this.fields.size() - 1 && !this.focused.getText().trim().isEmpty()) {
                focus(addField(""));
                this.scroll = Integer.MAX_VALUE;
                clampScroll();
            } else {
                focus(null);
            }
            return;
        }
        this.focused.textboxKeyTyped(typedChar, keyCode);
        updateSuggestions();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            this.scroll -= Integer.signum(wheel) * ROW_H;
            clampScroll();
        }
    }

    @Override
    public void updateScreen() {
        if (this.focused != null) {
            this.focused.updateCursorCounter();
        }
    }
}

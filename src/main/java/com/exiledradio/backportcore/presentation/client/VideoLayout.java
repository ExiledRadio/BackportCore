package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.GraphicsPreset;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.VideoSections;
import com.exiledradio.backportcore.presentation.option.CycleOption;
import com.exiledradio.backportcore.presentation.option.PresentationOption;
import com.exiledradio.backportcore.presentation.option.SliderOption;
import com.exiledradio.backportcore.presentation.option.ToggleOption;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionsRowList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.21.11's Video Settings on 1.12.2's screen ({@link VideoSections}): the rows of {@code GuiVideoSettings}' list
 * become the three sections, each under its header, with the Preset slider across the top of Quality &amp;
 * Performance. 1.12.2's list draws every row 25 pixels tall, so the headers sit on rows of their own (1.21.11's are
 * 13 and 31 pixels tall). Fullscreen Resolution (26.3's Exclusive Fullscreen Mode) is the 310-wide first row of
 * Display ({@link FullscreenModes}).
 *
 * <p>The Preset sets the section's settings when let go ({@link #apply}); changing any of them afterwards makes it
 * Custom, as vanilla's {@code setGraphicsPresetToCustom}. See-Through Leaves is 1.12.2's Fancy graphics.
 */
@SideOnly(Side.CLIENT)
public final class VideoLayout {

    /** For the client self-test: presets applied. */
    public static volatile int applied;

    private VideoLayout() {
    }

    /** @return true when the newest installed version lays Video Settings out in sections */
    public static boolean active() {
        return VanillaRevisions.videoSections(Presentation.newest(com.exiledradio.backportcore.presentation.LookArea.VIDEO));
    }

    /** Builds the rows from the screen's entries ({@code entries} and {@code keys} in step). */
    public static List<GuiOptionsRowList.Row> rows(GuiScreen gui, List<GuiButton> entries, List<String> keys) {
        return rows(gui, entries, keys, null);
    }

    /** As {@link #rows(GuiScreen, List, List)}, with {@code fullscreen} (Fullscreen Resolution) the first row of Display. */
    public static List<GuiOptionsRowList.Row> rows(GuiScreen gui, List<GuiButton> entries, List<String> keys,
                                                   @javax.annotation.Nullable GuiButton fullscreen) {
        List<GuiOptionsRowList.Row> rows = new ArrayList<GuiOptionsRowList.Row>();
        int left = gui.width / 2 - 155;
        for (VideoSections.Section s : VideoSections.arrange(keys)) {
            boolean display = fullscreen != null && VideoSections.DISPLAY.equals(s.header);
            if (s.entries.isEmpty() && s.preset < 0 && !display) {
                continue;
            }
            rows.add(new HeaderRow(I18n.format(s.header), left));
            if (display) {
                rows.add(new GuiOptionsRowList.Row(fullscreen, null));
            }
            if (s.preset >= 0) {
                GuiButton preset = new PresetSlider(left, 0);
                rows.add(new GuiOptionsRowList.Row(preset, null));
            }
            for (int i = 0; i < s.entries.size(); i += 2) {
                GuiButton a = entries.get(s.entries.get(i));
                GuiButton b = i + 1 < s.entries.size() ? entries.get(s.entries.get(i + 1)) : null;
                a.x = left;
                if (b != null) {
                    b.x = left + 160;
                }
                rows.add(new GuiOptionsRowList.Row(a, b));
            }
        }
        return rows;
    }

    // ---- The preset ----

    /** Sets every setting {@code preset} names (Fast, Fancy, Fabulous!); Custom sets nothing. */
    public static void apply(Minecraft mc, String preset) {
        Map<String, String> values = GraphicsPreset.values(preset);
        if (values.isEmpty()) {
            return;
        }
        GameSettings s = mc.gameSettings;
        int oldDistance = s.renderDistanceChunks;
        int oldAo = s.ambientOcclusion;
        int oldMip = s.mipmapLevels;
        for (Map.Entry<String, String> e : values.entrySet()) {
            String v = e.getValue();
            switch (e.getKey()) {
                case GraphicsPreset.RENDER_DISTANCE:
                    s.renderDistanceChunks = Math.min(Integer.parseInt(v), (int) GameSettings.Options.RENDER_DISTANCE.getValueMax());
                    break;
                case GraphicsPreset.AO:
                    s.ambientOcclusion = Boolean.parseBoolean(v) ? 2 : 0;
                    break;
                case GraphicsPreset.CLOUDS:
                    s.clouds = Integer.parseInt(v);
                    break;
                case GraphicsPreset.PARTICLES:
                    s.particleSetting = Integer.parseInt(v);
                    break;
                case GraphicsPreset.MIPMAP_LEVELS:
                    if (Integer.parseInt(v) != oldMip) {
                        s.setOptionFloatValue(GameSettings.Options.MIPMAP_LEVELS, Integer.parseInt(v));
                    }
                    break;
                case GraphicsPreset.ENTITY_SHADOWS:
                    s.entityShadows = Boolean.parseBoolean(v);
                    break;
                default:
                    setOption(e.getKey(), v);
            }
        }
        if (s.renderDistanceChunks != oldDistance && mc.renderGlobal != null) {
            mc.renderGlobal.setDisplayListEntitiesDirty();
        }
        if (s.ambientOcclusion != oldAo && mc.renderGlobal != null && mc.world != null) {
            mc.renderGlobal.loadRenderers();
        }
        s.saveOptions();
        applied++;
        BackportCore.LOGGER.info("Presentation: graphics preset {} applied", preset);
    }

    /** Sets the registered option with this path (Core's or a backport's copy of vanilla's), if there is one. */
    private static void setOption(String path, String value) {
        for (PresentationOption o : Presentation.options()) {
            if (!o.path().equals(path) || o == Presentation.GRAPHICS_PRESET) {
                continue;
            }
            try {
                if (o instanceof SliderOption) {
                    ((SliderOption) o).set(GraphicsPreset.toSlider(path, Double.parseDouble(value), ((SliderOption) o).max()));
                } else if (o instanceof ToggleOption) {
                    ((ToggleOption) o).set(Boolean.parseBoolean(value));
                } else if (o instanceof CycleOption) {
                    CycleOption c = (CycleOption) o;
                    c.set(value);
                }
            } catch (RuntimeException e) {
                BackportCore.LOGGER.debug("Presentation: preset value {} does not fit {}", value, o.id());
            }
        }
    }

    /** @return the settings a preset compares, by name, as saved */
    public static Map<String, String> current(Minecraft mc) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        GameSettings s = mc.gameSettings;
        m.put(GraphicsPreset.RENDER_DISTANCE, Integer.toString(s.renderDistanceChunks));
        m.put(GraphicsPreset.AO, Boolean.toString(s.ambientOcclusion > 0));
        m.put(GraphicsPreset.CLOUDS, Integer.toString(s.clouds));
        m.put(GraphicsPreset.PARTICLES, Integer.toString(s.particleSetting));
        m.put(GraphicsPreset.MIPMAP_LEVELS, Integer.toString(s.mipmapLevels));
        m.put(GraphicsPreset.ENTITY_SHADOWS, Boolean.toString(s.entityShadows));
        for (PresentationOption o : Presentation.options()) {
            if (o != Presentation.GRAPHICS_PRESET && o.isShown() && !m.containsKey(o.path())) {
                m.put(o.path(), o instanceof SliderOption
                        ? Double.toString(GraphicsPreset.fromSlider(o.path(), ((SliderOption) o).get(), ((SliderOption) o).max())) : o.write());
            }
        }
        return m;
    }

    /** Makes the preset Custom once a setting it sets no longer has its value (vanilla's {@code setGraphicsPresetToCustom}). */
    static void checkCustom(Minecraft mc) {
        String p = Presentation.GRAPHICS_PRESET.get();
        if (GraphicsPreset.CUSTOM.equals(p)) {
            return;
        }
        Map<String, String> now = current(mc);
        // A preset's render distance past what this game allows (32 on a 32-bit Java) is met at the maximum.
        int want = Integer.parseInt(GraphicsPreset.values(p).get(GraphicsPreset.RENDER_DISTANCE));
        int max = (int) GameSettings.Options.RENDER_DISTANCE.getValueMax();
        if (want > max && mc.gameSettings.renderDistanceChunks == max) {
            now.put(GraphicsPreset.RENDER_DISTANCE, Integer.toString(want));
        }
        if (!GraphicsPreset.matches(p, now)) {
            Presentation.GRAPHICS_PRESET.set(GraphicsPreset.CUSTOM);
        }
    }

    /** Each frame of Video Settings: Custom on a change, Anisotropic Filtering only with Anisotropic filtering. */
    static void tick(GuiScreen gui, List<GuiButton> rowsButtons) {
        Minecraft mc = Minecraft.getMinecraft();
        checkCustom(mc);
        boolean aniso = "anisotropic".equals(Presentation.TEXTURE_FILTERING.get());
        for (GuiButton b : rowsButtons) {
            if (b instanceof OptionWidgets.OptionButton && ((OptionWidgets.OptionButton) b).option() == Presentation.MAX_ANISOTROPY) {
                b.enabled = aniso;
            }
        }
    }

    // ---- See-Through Leaves is Fancy graphics ----

    /**
     * From Core's client init: from 1.21.11 Fancy graphics follows See-Through Leaves (taken from the player's
     * Graphics the first time), and every later change of it redraws the chunks.
     */
    static void initLeaves(Minecraft mc) {
        Presentation.CUTOUT_LEAVES.onChange(on -> {
            if (active() && mc.gameSettings.fancyGraphics != on) {
                mc.gameSettings.fancyGraphics = on;
                mc.gameSettings.saveOptions();
                if (mc.renderGlobal != null && mc.world != null) {
                    mc.renderGlobal.loadRenderers();
                }
            }
        });
        if (!active()) {
            return;
        }
        if (com.exiledradio.backportcore.presentation.option.OptionStore.get(Presentation.CUTOUT_LEAVES.id()) == null) {
            Presentation.CUTOUT_LEAVES.set(mc.gameSettings.fancyGraphics);
        } else if (mc.gameSettings.fancyGraphics != Presentation.CUTOUT_LEAVES.get()) {
            mc.gameSettings.fancyGraphics = Presentation.CUTOUT_LEAVES.get();
            mc.gameSettings.saveOptions();
        }
    }

    /** A section's title, left-aligned at the buttons' edge as 1.21.11's {@code HeaderEntry}. */
    static final class HeaderRow extends GuiOptionsRowList.Row {
        final String text;
        private final int x;

        HeaderRow(String text, int x) {
            super(null, null);
            this.text = text;
            this.x = x;
        }

        @Override
        public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks) {
            // 1.21.11's header text sits 4 pixels above the row below it; 26.3's is bold, underlined and centered.
            net.minecraft.client.gui.FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            int top = y + slotHeight - 9 - 4 - 4;
            if (VanillaRevisions.centeredOptionHeaders()) {
                String styled = net.minecraft.util.text.TextFormatting.BOLD + "" + net.minecraft.util.text.TextFormatting.UNDERLINE + this.text;
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                int center = screen != null ? screen.width / 2 : this.x + 155;
                font.drawStringWithShadow(styled, center - font.getStringWidth(styled) / 2, top, 0xFFFFFF);
            } else {
                font.drawStringWithShadow(this.text, this.x, top, 0xFFFFFF);
            }
        }

        @Override
        public boolean mousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY) {
            return false;
        }

        @Override
        public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {
        }

        @Override
        public void updatePosition(int slotIndex, int x, int y, float partialTicks) {
        }
    }

    /**
     * 1.21.11's Preset: a 310-wide slider over Fast, Fancy, Fabulous! and Custom ({@code SliderableEnum}). The knob
     * follows the mouse and snaps when let go, and the preset applies then.
     */
    public static final class PresetSlider extends GuiButton implements OptionWidgets.OptionButton {
        private static final CycleOption OPTION = Presentation.GRAPHICS_PRESET;
        private static final String[] NAMES = {GraphicsPreset.FAST, GraphicsPreset.FANCY, GraphicsPreset.FABULOUS, GraphicsPreset.CUSTOM};
        private boolean dragging;
        private double dragFraction;

        PresetSlider(int x, int y) {
            super(OptionWidgets.nextId(), x, y, 310, 20, "");
            refresh();
        }

        @Override
        public PresentationOption option() {
            return OPTION;
        }

        @Override
        public void refresh() {
            this.displayString = I18n.format(OPTION.labelKey()) + ": " + I18n.format(OPTION.valueKey());
        }

        private static int index() {
            for (int i = 0; i < NAMES.length; i++) {
                if (NAMES[i].equals(OPTION.get())) {
                    return i;
                }
            }
            return 3;
        }

        private double fractionAt(int mouseX) {
            return Math.max(0.0, Math.min(1.0, (double) (mouseX - (this.x + 4)) / (double) (this.width - 8)));
        }

        private static int stop(double fraction) {
            return (int) Math.round(fraction * (NAMES.length - 1));
        }

        @Override
        protected int getHoverState(boolean mouseOver) {
            return 0;
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            refresh();
            super.drawButton(mc, mouseX, mouseY, partialTicks);
        }

        @Override
        protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
            if (!this.visible) {
                return;
            }
            if (this.dragging) {
                this.dragFraction = fractionAt(mouseX);
            }
            mc.getTextureManager().bindTexture(BUTTON_TEXTURES);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            double at = this.dragging ? this.dragFraction : index() / (double) (NAMES.length - 1);
            int knob = this.x + (int) (at * (double) (this.width - 8));
            drawTexturedModalRect(knob, this.y, 0, 66, 4, 20);
            drawTexturedModalRect(knob + 4, this.y, 196, 66, 4, 20);
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                this.dragFraction = fractionAt(mouseX);
                this.dragging = true;
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            if (!this.dragging) {
                return;
            }
            this.dragging = false;
            String chosen = NAMES[stop(this.dragFraction)];
            if (!chosen.equals(OPTION.get())) {
                OPTION.set(chosen);
                apply(Minecraft.getMinecraft(), chosen);
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                if (screen != null) {
                    // The other rows show the new values: lay the screen out again where it was scrolled.
                    OptionsInjector.relayout(screen);
                }
            }
            refresh();
        }
    }
}

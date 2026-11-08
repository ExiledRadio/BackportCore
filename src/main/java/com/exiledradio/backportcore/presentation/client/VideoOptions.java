package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * Vanilla's own Video Settings entries as the newest installed version had them ({@link VanillaRevisions}): 1.13
 * took 3D Anaglyph out (off for good) and counts GUI Scale as Auto, 1, 2, 3 ... up to what the window fits; 1.14
 * took Use VBOs out (always on).
 */
@SideOnly(Side.CLIENT)
public final class VideoOptions {

    private VideoOptions() {
    }

    /** Settings a removed entry leaves fixed: Anaglyph off, VBOs on. From Core's init and each Video Settings. */
    public static void applyForced(Minecraft mc) {
        GameSettings s = mc.gameSettings;
        if (s == null) {
            return;
        }
        boolean changed = false;
        if (VanillaRevisions.anaglyphRemoved() && s.anaglyph) {
            s.anaglyph = false;
            changed = true;
        }
        if (VanillaRevisions.vboRemoved() && !s.useVbo) {
            s.useVbo = true;
            if (mc.renderGlobal != null && mc.world != null) {
                mc.renderGlobal.loadRenderers();
            }
            changed = true;
        }
        if (changed) {
            s.saveOptions();
            BackportCore.LOGGER.info("Presentation: Video Settings as of {}: anaglyph {}, VBOs {}",
                    com.exiledradio.backportcore.presentation.Presentation.newest(), s.anaglyph, s.useVbo);
        }
    }

    /** @return true when the newest version has no such entry on Video Settings */
    static boolean removed(String option) {
        return "ANAGLYPH".equals(option) && VanillaRevisions.anaglyphRemoved()
                || "USE_VBO".equals(option) && VanillaRevisions.vboRemoved();
    }

    /** Edits Video Settings' entries in place ({@code entries} and {@code keys} run in step). */
    static void edit(GuiScreen gui, List<GuiButton> entries, List<String> keys) {
        applyForced(Minecraft.getMinecraft());
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (removed(keys.get(i))) {
                entries.remove(i);
                keys.remove(i);
            } else if ("GUI_SCALE".equals(keys.get(i)) && VanillaRevisions.numberedGuiScale()) {
                GuiButton old = entries.get(i);
                entries.set(i, new GuiScaleButton(old.id, old.x, old.y));
            }
        }
    }

    /** @return the largest GUI scale the window fits: 1.13's {@code Window.calcGuiScale(0, unicode)}, as 1.12.2's ScaledResolution counts */
    public static int maxGuiScale(Minecraft mc) {
        int scale = 1;
        while (mc.displayWidth / (scale + 1) >= 320 && mc.displayHeight / (scale + 1) >= 240) {
            scale++;
        }
        if (mc.isUnicode() && scale % 2 != 0 && scale != 1) {
            scale--;
        }
        return scale;
    }

    /** 1.13's GUI Scale: "GUI Scale: Auto", then 1 to the maximum; Shift goes back. */
    static final class GuiScaleButton extends GuiButton {
        GuiScaleButton(int id, int x, int y) {
            super(id, x, y, 150, 20, "");
            refresh();
        }

        void refresh() {
            int s = Minecraft.getMinecraft().gameSettings.guiScale;
            this.displayString = I18n.format("options.guiScale") + ": " + (s == 0 ? I18n.format("options.guiScale.auto") : Integer.toString(s));
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (!super.mousePressed(mc, mouseX, mouseY)) {
                return false;
            }
            int max = maxGuiScale(mc);
            int s = mc.gameSettings.guiScale;
            s = GuiScreen.isShiftKeyDown() ? s - 1 : s + 1;
            if (s > max) {
                s = 0;
            } else if (s < 0) {
                s = max;
            }
            mc.gameSettings.guiScale = s;
            mc.gameSettings.saveOptions();
            refresh();
            return true;
        }
    }
}

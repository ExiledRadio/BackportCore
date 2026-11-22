package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.List;

/**
 * 26.3's {@code SystemToast} as its {@code ToastManager} shows it, drawn by Core over everything (1.12.2's
 * {@code GuiToast} has fixed 160-wide slots): a yellow title and white message lines split at 200, the toast
 * {@code max(160, widest line) + 30} wide and {@code 20 + (title lines - 1) * 12 + max(message lines, 1) * 12} high,
 * on the {@code toast/system} sprite (nine-slice, edges 17/30/4/4) when a backport supplies it, else 1.12.2's system
 * toast. It slides in from the right edge over 600 ms (eased), stays 5 s after its last update and slides out, with
 * the toast in and out sounds. One at a time, at the top (the first slot); a new one replaces the text of the shown
 * one ({@code addOrUpdate}).
 */
@SideOnly(Side.CLIENT)
public final class SystemToasts {

    static final String SPRITE = "textures/gui/sprites/toast/system.png";
    private static final ResourceLocation OLD_TOASTS = new ResourceLocation("textures/gui/toasts.png");
    private static final long SLIDE_MS = 600L;
    private static final long DISPLAY_MS = 5000L;

    private static Shown shown;

    /** For the client self-test: frames drawn, and the shown toast's text. */
    public static volatile int frames;
    public static volatile String lastTitle;
    public static volatile String lastMessage;

    public SystemToasts() {
    }

    /** 26.3's {@code SystemToast.addOrUpdate}: show a toast, or put this text on the one already shown. */
    public static void addOrUpdate(String title, String message) {
        lastTitle = title;
        lastMessage = message;
        if (shown != null && !shown.finished) {
            shown.reset(title, message);
        } else {
            shown = new Shown(title, message);
        }
    }

    /** @return whether a toast is on screen (sliding or shown) */
    public static boolean showing() {
        return shown != null && !shown.finished;
    }

    /** @return the shown toast's width and height, or null (for the client self-test) */
    public static int[] size() {
        Shown t = shown;
        return t == null ? null : new int[] {t.width, t.height()};
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || shown == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Shown t = shown;
        t.update(mc, Minecraft.getSystemTime());
        if (t.finished) {
            shown = null;
            return;
        }
        t.draw(mc);
        frames++;
    }

    private static final class Shown {
        List<String> titleLines;
        List<String> messageLines;
        int width;
        long animationStart = -1L;
        boolean visible; // false = HIDE
        long becameFullyVisibleAt = -1L;
        long lastChanged;
        boolean changed;
        float portion;
        boolean finished;

        Shown(String title, String message) {
            set(title, message);
        }

        void reset(String title, String message) {
            set(title, message);
            this.changed = true;
        }

        private void set(String title, String message) {
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            this.titleLines = font.listFormattedStringToWidth(title, 200);
            this.messageLines = message == null ? Collections.<String>emptyList() : font.listFormattedStringToWidth(message, 200);
            int widest = 0;
            for (String s : this.titleLines) {
                widest = Math.max(widest, font.getStringWidth(s));
            }
            for (String s : this.messageLines) {
                widest = Math.max(widest, font.getStringWidth(s));
            }
            this.width = Math.max(160, widest) + 30;
        }

        int height() {
            return 20 + (this.titleLines.size() - 1) * 12 + Math.max(this.messageLines.size(), 1) * 12;
        }

        /** {@code ToastManager.ToastInstance.update} with {@code SystemToast.update}. */
        void update(Minecraft mc, long now) {
            if (this.animationStart == -1L) {
                this.animationStart = now;
                this.visible = true;
                sound(mc, SoundEvents.UI_TOAST_IN);
            }
            if (this.visible && now - this.animationStart <= SLIDE_MS) {
                this.becameFullyVisibleAt = now;
            }
            long fullyVisibleFor = now - this.becameFullyVisibleAt;
            float progress = Math.min(1.0F, Math.max(0.0F, (now - this.animationStart) / (float) SLIDE_MS));
            progress *= progress;
            this.portion = this.visible ? progress : 1.0F - progress;
            if (this.changed) {
                this.lastChanged = fullyVisibleFor;
                this.changed = false;
            }
            boolean wanted = fullyVisibleFor - this.lastChanged < DISPLAY_MS;
            if (wanted != this.visible) {
                this.animationStart = now - (long) (int) ((1.0F - this.portion) * SLIDE_MS);
                this.visible = wanted;
                sound(mc, wanted ? SoundEvents.UI_TOAST_IN : SoundEvents.UI_TOAST_OUT);
            }
            this.finished = !this.visible && now - this.animationStart > SLIDE_MS;
        }

        void draw(Minecraft mc) {
            ScaledResolution res = new ScaledResolution(mc);
            int height = height();
            GlStateManager.clear(256);
            GlStateManager.matrixMode(5889);
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, res.getScaledWidth_double(), res.getScaledHeight_double(), 0.0D, 1000.0D, 3000.0D);
            GlStateManager.matrixMode(5888);
            GlStateManager.loadIdentity();
            GlStateManager.translate(res.getScaledWidth() - this.width * this.portion, 0.0F, -2000.0F);
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            if (Presentation.asset(SPRITE) != null) {
                mc.getTextureManager().bindTexture(new ResourceLocation(SPRITE));
                nineSlice(this.width, height, 0, 0, 160, 64, 160, 64, 17, 30, 4, 4);
            } else {
                mc.getTextureManager().bindTexture(OLD_TOASTS);
                nineSlice(this.width, height, 0, 64, 160, 32, 256, 256, 17, 16, 4, 4);
            }
            FontRenderer font = mc.fontRenderer;
            if (this.messageLines.isEmpty()) {
                lines(font, this.titleLines, 12, 0xFFFFFF00);
            } else {
                lines(font, this.titleLines, 7, 0xFFFFFF00);
                lines(font, this.messageLines, 7 + this.titleLines.size() * 12, 0xFFFFFFFF);
            }
        }

        private static void lines(FontRenderer font, List<String> lines, int y, int color) {
            for (int i = 0; i < lines.size(); i++) {
                font.drawString(lines.get(i), 18, y + i * 12, color);
            }
        }

        private static void sound(Minecraft mc, SoundEvent sound) {
            mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(sound, 1.0F));
        }
    }

    /**
     * 26.3's {@code blitNineSlicedSprite} (edges clamped to half the size, edges and center tiled, as its
     * {@code nine_slice} scaling without {@code stretch_inner}) of the {@code rw} by {@code rh} region at
     * ({@code u}, {@code v}) of a {@code tw} by {@code th} texture, edges {@code l}/{@code t}/{@code r}/{@code b},
     * to w by h at the origin.
     */
    static void nineSlice(int w, int h, int u, int v, int rw, int rh, int tw, int th, int l, int t, int r, int b) {
        l = Math.min(l, w / 2);
        r = Math.min(r, w / 2);
        t = Math.min(t, h / 2);
        b = Math.min(b, h / 2);
        int[] xs = {0, l, w - r, w};
        int[] us = {u, u + l, u + rw - r, u + rw};
        int[] ys = {0, t, h - b, h};
        int[] vs = {v, v + t, v + rh - b, v + rh};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                tile(xs[i], ys[j], xs[i + 1] - xs[i], ys[j + 1] - ys[j], us[i], vs[j], us[i + 1] - us[i], vs[j + 1] - vs[j], tw, th);
            }
        }
    }

    /** Fills w by h at (x, y) with the su by sv source tile at (u, v), repeated and cut at the far edges. */
    private static void tile(int x, int y, int w, int h, int u, int v, int su, int sv, int tw, int th) {
        if (w <= 0 || h <= 0 || su <= 0 || sv <= 0) {
            return;
        }
        for (int dx = 0; dx < w; dx += su) {
            int cw = Math.min(su, w - dx);
            for (int dy = 0; dy < h; dy += sv) {
                int ch = Math.min(sv, h - dy);
                Gui.drawModalRectWithCustomSizedTexture(x + dx, y + dy, (float) u, (float) v, cw, ch, (float) tw, (float) th);
            }
        }
    }
}

package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

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

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * 26.3's {@code SystemToast} as its {@code ToastManager} shows it, drawn by Core over everything (1.12.2's
 * {@code GuiToast} has fixed 160-wide slots): the one drawer for the family's system toasts (Trails &amp; Tales'
 * world backup, low disk space and chunk failure toasts, Core's "Not available in this version").
 *
 * <p>A toast has an {@link Id} (vanilla's {@code SystemToastId}: how long it stays) and is shown with {@link #add}, or
 * with {@link #addOrUpdate}, which puts new text on the shown toast of that id. A yellow title and white message;
 * from 26.2 ({@link VanillaRevisions#systemToastRewrap}) both split at 200, the toast {@code max(160, widest line) +
 * 30} wide (again at each update) and {@code 20 + (title lines - 1) * 12 + max(message lines, 1) * 12} high; before,
 * one line each and {@code max(160, 30 + widest)} wide, kept from the first text, on the {@code toast/system} sprite (nine-slice, edges 17/30/4/4) when a backport
 * supplies it, else 1.12.2's system toast. Each slides in from the right edge over 600 ms (eased), stays its id's time
 * after its last update (times {@link #notificationTime}) and slides out, with the toast in and out sounds (one sound a
 * frame). Toasts take 32-pixel slots down from the top, five in all ({@code ceil(height / 32)} each); one that does not
 * fit waits. Since Core 1.4.5 (ids, several at once); 1.4.3's {@link #addOrUpdate(String, String)} is kept.
 */
@SideOnly(Side.CLIENT)
public final class SystemToasts {

    /** 26.3's {@code SystemToastId}: what a toast is, and how long it stays after its last change. */
    public static final class Id {
        public static final Id NARRATOR_TOGGLE = new Id("narrator_toggle");
        public static final Id WORLD_BACKUP = new Id("world_backup");
        public static final Id PACK_LOAD_FAILURE = new Id("pack_load_failure");
        public static final Id WORLD_ACCESS_FAILURE = new Id("world_access_failure");
        public static final Id PACK_COPY_FAILURE = new Id("pack_copy_failure");
        public static final Id FILE_DROP_FAILURE = new Id("file_drop_failure");
        public static final Id PERIODIC_NOTIFICATION = new Id("periodic_notification");
        public static final Id LOW_DISK_SPACE = new Id("low_disk_space", 10000L);
        public static final Id CHUNK_LOAD_FAILURE = new Id("chunk_load_failure");
        public static final Id CHUNK_SAVE_FAILURE = new Id("chunk_save_failure");
        public static final Id UNSECURE_SERVER_WARNING = new Id("unsecure_server_warning", 10000L);
        /** Core's: a button whose service 1.12.2 lacks ("Not available in this version"). */
        public static final Id UNAVAILABLE = new Id("backportcore:unavailable");

        public final String name;
        public final long displayTime;

        public Id(String name, long displayTime) {
            this.name = name;
            this.displayTime = displayTime;
        }

        /** 5 seconds, as vanilla's default. */
        public Id(String name) {
            this(name, 5000L);
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    /** One shown or waiting toast. */
    public static final class Toast {
        public final Id id;
        private List<String> titleLines;
        private List<String> messageLines;
        private String title;
        @Nullable
        private String message;
        private int width;
        private long lastChanged;
        private boolean changed;
        private boolean forceHide;

        Toast(Id id, String title, @Nullable String message) {
            this.id = id;
            set(title, message);
        }

        private void set(String title, @Nullable String message) {
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            boolean rewrap = VanillaRevisions.systemToastRewrap();
            this.title = title;
            this.message = message;
            if (rewrap) {
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
            } else {
                // Before 26.2: one title line, one message line, and the width the first text's (reset keeps it).
                this.titleLines = Collections.singletonList(title);
                this.messageLines = message == null ? Collections.<String>emptyList() : Collections.singletonList(message);
                if (this.width == 0) {
                    this.width = Math.max(160, 30 + Math.max(font.getStringWidth(title), message == null ? 0 : font.getStringWidth(message)));
                }
            }
        }

        /** 26.3's {@code reset}: new text, and the time starts again. */
        void reset(String title, @Nullable String message) {
            set(title, message);
            this.changed = true;
        }

        public String title() {
            return this.title;
        }

        @Nullable
        public String message() {
            return this.message;
        }

        public int width() {
            return this.width;
        }

        public int height() {
            return 20 + (this.titleLines.size() - 1) * 12 + Math.max(this.messageLines.size(), 1) * 12;
        }

        int slots() {
            return (height() + 31) / 32;
        }

        /** {@code SystemToast.update}: shown while its time since the last change is under its id's time. */
        boolean wanted(long fullyVisibleFor) {
            if (this.changed) {
                this.lastChanged = fullyVisibleFor;
                this.changed = false;
            }
            return !this.forceHide && fullyVisibleFor - this.lastChanged < this.id.displayTime * notificationTime.getAsDouble();
        }

        void draw(Minecraft mc) {
            int height = height();
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
    }

    static final String SPRITE = "textures/gui/sprites/toast/system.png";
    private static final ResourceLocation OLD_TOASTS = new ResourceLocation("textures/gui/toasts.png");
    private static final long SLIDE_MS = 600L;
    private static final int SLOT_COUNT = 5;

    /**
     * 1.19.4's Notification Time ({@code ToastManager.getNotificationDisplayTimeMultiplier}): how many times its id's
     * time a toast stays. 1 until the option that sets it is installed.
     */
    public static volatile DoubleSupplier notificationTime = () -> 1.0;

    private static final List<Shown> VISIBLE = new ArrayList<Shown>();
    private static final BitSet OCCUPIED = new BitSet(SLOT_COUNT);
    private static final Deque<Toast> QUEUED = new ArrayDeque<Toast>();

    /** For the client self-test: frames drawn, and the last toast's text. */
    public static volatile int frames;
    public static volatile String lastTitle;
    public static volatile String lastMessage;

    public SystemToasts() {
    }

    /** 26.3's {@code SystemToast.add}: a new toast of this id, beside any already shown. Client thread. */
    public static void add(Id id, String title, @Nullable String message) {
        lastTitle = title;
        lastMessage = message;
        QUEUED.add(new Toast(id, title, message));
    }

    /** 26.3's {@code SystemToast.addOrUpdate}: show a toast, or put this text on the one of this id already there. */
    public static void addOrUpdate(Id id, String title, @Nullable String message) {
        Toast t = get(id);
        if (t == null) {
            add(id, title, message);
        } else {
            lastTitle = title;
            lastMessage = message;
            t.reset(title, message);
        }
    }

    /** 26.3's {@code SystemToast.forceHide}: the toast of this id slides out now. */
    public static void forceHide(Id id) {
        Toast t = get(id);
        if (t != null) {
            t.forceHide = true;
        }
    }

    /** @return the shown or waiting toast of this id, or null ({@code ToastManager.getToast}) */
    @Nullable
    public static Toast get(Id id) {
        for (Shown s : VISIBLE) {
            if (s.toast.id == id) {
                return s.toast;
            }
        }
        for (Toast t : QUEUED) {
            if (t.id == id) {
                return t;
            }
        }
        return null;
    }

    /** Core 1.4.3's call: a toast of Core's own id ({@link Id#UNAVAILABLE}). */
    public static void addOrUpdate(String title, String message) {
        addOrUpdate(Id.UNAVAILABLE, title, message);
    }

    /** @return whether a toast is on screen (sliding or shown) or waiting */
    public static boolean showing() {
        return !VISIBLE.isEmpty() || !QUEUED.isEmpty();
    }

    /** @return the newest toast's width and height, or null (for the client self-test) */
    public static int[] size() {
        Toast t = null;
        for (Shown s : VISIBLE) {
            t = s.toast;
        }
        for (Toast q : QUEUED) {
            t = q;
        }
        return t == null ? null : new int[] {t.width, t.height()};
    }

    /** @return how many toasts are on screen, and the first slot of each (for the client self-test) */
    public static int[] visibleSlots() {
        int[] out = new int[VISIBLE.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = VISIBLE.get(i).firstSlot;
        }
        return out;
    }

    /** {@code ToastManager.clear}: every toast goes at once (leaving a world). */
    public static void clear() {
        VISIBLE.clear();
        OCCUPIED.clear();
        QUEUED.clear();
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || VISIBLE.isEmpty() && QUEUED.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        update(mc, Minecraft.getSystemTime());
        if (VISIBLE.isEmpty() || mc.gameSettings.hideGUI) {
            return;
        }
        ScaledResolution res = new ScaledResolution(mc);
        GlStateManager.clear(256);
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, res.getScaledWidth_double(), res.getScaledHeight_double(), 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        for (Shown s : VISIBLE) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(res.getScaledWidth() - s.toast.width * s.portion, s.firstSlot * 32, 0.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            s.toast.draw(mc);
            GlStateManager.popMatrix();
        }
        frames++;
    }

    /** {@code ToastManager.update}. */
    static void update(Minecraft mc, long now) {
        boolean sounded = false;
        for (Iterator<Shown> it = VISIBLE.iterator(); it.hasNext();) {
            Shown s = it.next();
            boolean before = s.visible;
            s.update(now);
            if (s.visible != before && !sounded) {
                sounded = true;
                sound(mc, s.visible ? SoundEvents.UI_TOAST_IN : SoundEvents.UI_TOAST_OUT);
            }
            if (s.finished) {
                OCCUPIED.clear(s.firstSlot, s.firstSlot + s.slots);
                it.remove();
            }
        }
        for (Iterator<Toast> it = QUEUED.iterator(); it.hasNext() && OCCUPIED.cardinality() < SLOT_COUNT;) {
            Toast t = it.next();
            int count = t.slots();
            int first = freeSlots(count);
            if (first >= 0) {
                VISIBLE.add(new Shown(t, first, count));
                OCCUPIED.set(first, first + count);
                it.remove();
            }
        }
    }

    private static int freeSlots(int count) {
        int run = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (OCCUPIED.get(i)) {
                run = 0;
            } else if (++run == count) {
                return i + 1 - run;
            }
        }
        return -1;
    }

    private static void sound(Minecraft mc, SoundEvent sound) {
        mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(sound, 1.0F));
    }

    /** {@code ToastManager.ToastInstance}. */
    private static final class Shown {
        final Toast toast;
        final int firstSlot;
        final int slots;
        long animationStart = -1L;
        boolean visible; // false = HIDE
        long becameFullyVisibleAt = -1L;
        float portion;
        boolean finished;

        Shown(Toast toast, int firstSlot, int slots) {
            this.toast = toast;
            this.firstSlot = firstSlot;
            this.slots = slots;
        }

        void update(long now) {
            if (this.animationStart == -1L) {
                this.animationStart = now;
                this.visible = true;
            }
            if (this.visible && now - this.animationStart <= SLIDE_MS) {
                this.becameFullyVisibleAt = now;
            }
            long fullyVisibleFor = now - this.becameFullyVisibleAt;
            float progress = Math.min(1.0F, Math.max(0.0F, (now - this.animationStart) / (float) SLIDE_MS));
            progress *= progress;
            this.portion = this.visible ? progress : 1.0F - progress;
            boolean wanted = this.toast.wanted(fullyVisibleFor);
            if (wanted != this.visible) {
                this.animationStart = now - (long) (int) ((1.0F - this.portion) * SLIDE_MS);
                this.visible = wanted;
            }
            this.finished = !this.visible && now - this.animationStart > SLIDE_MS;
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

package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.command.tick.TickRateManager;
import com.exiledradio.backportcore.command.tick.Ticks;
import com.exiledradio.backportcore.presentation.DebugCharts;
import com.exiledradio.backportcore.presentation.VanillaRevisions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.FrameTimer;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 1.20.2's F3 + 2 and F3 + 1 ({@link VanillaRevisions#debugFrameCharts}; 26.3's {@code FpsDebugChart},
 * {@code TpsDebugChart} and {@code ProfilerPieChart}), in place of 1.12.2's lagometer and its pie:
 *
 * <ul>
 *   <li><b>FPS</b> (bottom left): 1.12.2's own frame times ({@code Minecraft.getFrameTimer()}, the last 240 frames),
 *       one bar a frame, 60 pixels for 33.3 ms, green to yellow at 28 ms to red at 56; the 30 and 60 fps lines; the
 *       frame limit's cyan line up to 250; min, avg and max above.</li>
 *   <li><b>TPS</b> (bottom right, on this game's own server only): each server tick's length, start to start, as the
 *       main bar (colored against the tick rate: green at the target, red at 1.25 times it), and over it the time
 *       {@code tick()} took (26.3's tick method color); the average leaves the idle time out, and the top label is the
 *       tick rate ("20.0 tps"). 1.12.2's server loop has no separate task queue or idle measure, so the tasks and
 *       "other" parts 26.3 stacks above are 0, and the idle part is the rest of the tick.</li>
 *   <li><b>Profiler pie</b> (patch P60): at the GUI scale (the debug overlay's, from 26.3), 105 across, 10 from the
 *       bottom or above the charts, on a dark box with the node's path, as 26.3's.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class FrameCharts {

    /** 26.3's {@code TpsDebugDimensions}: the tick's whole length, {@code tick()}, its tasks, and the wait. */
    public static final int FULL_TICK = 0;
    public static final int TICK_METHOD = 1;
    public static final int TASKS = 2;
    public static final int IDLE = 3;
    public static final DebugCharts.Samples TICKS = new DebugCharts.Samples(4);

    private static final int TICK_METHOD_COLOR = -6745839;
    private static final int TASK_COLOR = -4548257;
    private static final int OTHER_COLOR = -10547572;
    private static final DecimalFormat PERCENT = new DecimalFormat("##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private static long tickStart = -1L;
    private static long lastTickStart = -1L;
    /** 26.3's {@code smoothedTickTimeMillis}, of the integrated server's {@code tick()}. */
    public static volatile float smoothedTickMillis;

    private static Method displayDebugInfo;
    private static Field profilerName;

    /** For the client self-test: chart frames drawn, and pies drawn. */
    public static volatile int frames;
    public static volatile int pies;
    /** For the client self-test: the pie's last box (left, top, right, bottom). */
    public static volatile int[] lastPie;

    private FrameCharts() {
    }

    // ---- the samples

    /** @return the last frame times in nanoseconds, oldest first (1.12.2's {@code FrameTimer}) */
    static long[] frameTimes(Minecraft mc) {
        FrameTimer timer = mc.getFrameTimer();
        long[] frames = timer.getFrames();
        int i = timer.getLastIndex();
        int end = timer.getIndex();
        List<Long> out = new ArrayList<Long>();
        while (i != end && out.size() < frames.length) {
            out.add(frames[i]);
            i = timer.parseIndex(i + 1);
        }
        long[] a = new long[out.size()];
        for (int j = 0; j < a.length; j++) {
            a[j] = out.get(j);
        }
        return a;
    }

    /** The integrated server's ticks, measured on its thread. */
    public static final class ServerEvents {
        @SubscribeEvent
        public void onTick(TickEvent.ServerTickEvent event) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (!(server instanceof IntegratedServer)) {
                return;
            }
            long now = System.nanoTime();
            if (event.phase == TickEvent.Phase.START) {
                if (lastTickStart >= 0L) {
                    // The previous tick: from its start to this one's, and the work recorded at its end.
                    long full = now - lastTickStart;
                    long work = Math.min(full, Math.max(0L, pendingWork));
                    TICKS.log(full, work, 0L, full - work);
                }
                lastTickStart = now;
                tickStart = now;
            } else if (tickStart >= 0L) {
                pendingWork = now - tickStart;
                smoothedTickMillis = smoothedTickMillis * 0.8F + pendingWork / 1000000.0F * 0.19999999F;
            }
        }
    }

    private static volatile long pendingWork;

    /** Clears the samples (a new world). */
    static void reset() {
        TICKS.reset();
        lastTickStart = -1L;
        tickStart = -1L;
        smoothedTickMillis = 0.0F;
    }

    /** @return the target milliseconds a tick ({@code /tick rate}), 50 by default */
    static float msPerTick() {
        TickRateManager m = Ticks.client();
        float ms = m == null ? 50.0F : m.millisecondsPerTick();
        return ms > 0.0F ? ms : 50.0F;
    }

    // ---- drawing

    /** @return true while 1.20.2's F3 + 2 charts show */
    public static boolean showing(Minecraft mc) {
        return VanillaRevisions.debugFrameCharts() && mc.gameSettings.showDebugInfo && mc.gameSettings.showLagometer;
    }

    /** Forge's handlers; public for the event bus. */
    public static final class Events {
        /** 1.12.2's lagometer is not drawn from 1.20.2; the charts are, at the overlay's scale. */
        @SubscribeEvent
        public void onPre(RenderGameOverlayEvent.Pre event) {
            if (event.getType() != RenderGameOverlayEvent.ElementType.FPS_GRAPH || !VanillaRevisions.debugFrameCharts()) {
                return;
            }
            event.setCanceled(true);
            Minecraft mc = Minecraft.getMinecraft();
            ScaledResolution res = event.getResolution();
            int[] scaled = DebugScale.begin(mc);
            int w = scaled != null ? scaled[0] : res.getScaledWidth();
            int h = scaled != null ? scaled[1] : res.getScaledHeight();
            GlStateManager.disableDepth();
            try {
                draw(mc, w, h);
            } finally {
                GlStateManager.enableDepth();
                if (scaled != null) {
                    DebugScale.end();
                }
            }
            frames++;
        }

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END && Minecraft.getMinecraft().getIntegratedServer() == null && TICKS.size() > 0) {
                reset();
            }
        }
    }

    static void draw(Minecraft mc, int w, int h) {
        FontRenderer font = mc.fontRenderer;
        int maxWidth = w / 2;
        long[] fps = frameTimes(mc);
        int fw = DebugCharts.width(maxWidth);
        chart(font, fps, null, 0, fw, h, false);
        if (TICKS.size() > 0) {
            long[] full = new long[TICKS.size()];
            for (int i = 0; i < full.length; i++) {
                full[i] = TICKS.get(i, FULL_TICK);
            }
            int tw = DebugCharts.width(maxWidth);
            chart(font, full, TICKS, w - tw, tw, h, true);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** {@code AbstractDebugChart.extractRenderState} with the FPS or TPS chart's parts. */
    static void chart(FontRenderer font, long[] samples, DebugCharts.Samples ticks, int left, int width, int bottom, boolean tps) {
        Gui.drawRect(left, bottom - DebugCharts.HEIGHT, left + width, bottom, DebugCharts.BACKGROUND);
        float mspt = msPerTick();
        long sum = 0L;
        long min = Integer.MAX_VALUE;
        long max = Integer.MIN_VALUE;
        int start = Math.max(0, DebugCharts.Samples.CAPACITY - (width - 2));
        int count = samples.length - start;
        for (int i = 0; i < count; i++) {
            int x = left + i + 1;
            int index = start + i;
            long value = samples[index];
            long aggregate = tps ? value - ticks.get(index, IDLE) : value;
            min = Math.min(min, aggregate);
            max = Math.max(max, aggregate);
            sum += aggregate;
            int height = tps ? DebugCharts.tpsHeight(value, mspt) : DebugCharts.fpsHeight(value);
            int color = tps ? DebugCharts.tpsColor(value, mspt) : DebugCharts.fpsColor(value);
            Gui.drawRect(x, bottom - height, x + 1, bottom, color);
            if (tps) {
                long method = ticks.get(index, TICK_METHOD);
                int mh = DebugCharts.tpsHeight(method, mspt);
                Gui.drawRect(x, bottom - mh, x + 1, bottom, TICK_METHOD_COLOR);
                long tasks = ticks.get(index, TASKS);
                int th = DebugCharts.tpsHeight(tasks, mspt);
                Gui.drawRect(x, bottom - mh - th, x + 1, bottom - mh, TASK_COLOR);
                long other = value - ticks.get(index, IDLE) - method - tasks;
                int oh = DebugCharts.tpsHeight(other, mspt);
                Gui.drawRect(x, bottom - oh - th - mh, x + 1, bottom - th - mh, OTHER_COLOR);
            }
        }
        int top = bottom - DebugCharts.HEIGHT;
        hLine(left, left + width - 1, top, -1);
        hLine(left, left + width - 1, bottom - 1, -1);
        vLine(left, top, bottom, -1);
        vLine(left + width - 1, top, bottom, -1);
        if (count > 0) {
            String lo = DebugCharts.msText(min) + " min";
            String avg = DebugCharts.msText((double) sum / count) + " avg";
            String hi = DebugCharts.msText(max) + " max";
            font.drawStringWithShadow(lo, left + 2, top - 9, DebugCharts.GREY);
            font.drawStringWithShadow(avg, left + width / 2 - font.getStringWidth(avg) / 2, top - 9, DebugCharts.GREY);
            font.drawStringWithShadow(hi, left + width - font.getStringWidth(hi) - 2, top - 9, DebugCharts.GREY);
        }
        if (tps) {
            label(font, String.format(Locale.ROOT, "%.1f tps", 1000.0F / mspt), left + 1, top + 1);
        } else {
            label(font, "30 fps", left + 1, top + 1);
            label(font, "60 fps", left + 1, bottom - 30 + 1);
            hLine(left, left + width - 1, bottom - 30, -1);
            int limit = Minecraft.getMinecraft().gameSettings.limitFramerate;
            if (limit > 0 && limit <= 250) {
                hLine(left, left + width - 1, bottom - DebugCharts.fpsHeight(1.0E9 / limit) - 1, -16711681);
            }
        }
    }

    private static void label(FontRenderer font, String text, int x, int y) {
        Gui.drawRect(x, y, x + font.getStringWidth(text) + 1, y + 9, DebugCharts.BACKGROUND);
        font.drawString(text, x + 1, y + 1, DebugCharts.GREY, false);
    }

    private static void hLine(int x1, int x2, int y, int color) {
        Gui.drawRect(x1, y, x2 + 1, y + 1, color);
    }

    private static void vLine(int x, int y1, int y2, int color) {
        Gui.drawRect(x, y1 + 1, x + 1, y2, color);
    }

    // ---- the profiler pie (P60)

    /** P60: {@code Minecraft.runGameLoop}'s {@code displayDebugInfo(elapsed)}. */
    static void profilerPie(Minecraft mc, long elapsed) {
        if (!VanillaRevisions.debugFrameCharts()) {
            try {
                if (displayDebugInfo == null) {
                    displayDebugInfo = ReflectionHelper.findMethod(Minecraft.class, "displayDebugInfo", "func_71366_a", long.class);
                }
                displayDebugInfo.invoke(mc, elapsed);
            } catch (Exception e) {
                BackportCore.LOGGER.error("Profiler chart: vanilla's displayDebugInfo failed", e);
            }
            return;
        }
        if (!mc.profiler.profilingEnabled) {
            return;
        }
        List<Profiler.Result> list = mc.profiler.getProfilingData(path(mc));
        if (list.isEmpty()) {
            return;
        }
        Profiler.Result node = list.remove(0);
        ScaledResolution res = new ScaledResolution(mc);
        GlStateManager.clear(256);
        GlStateManager.matrixMode(5889);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, res.getScaledWidth_double(), res.getScaledHeight_double(), 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(5888);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);
        int[] scaled = DebugScale.begin(mc);
        int w = scaled != null ? scaled[0] : res.getScaledWidth();
        int h = scaled != null ? scaled[1] : res.getScaledHeight();
        try {
            int bottomOffset = 10;
            if (showing(mc)) {
                bottomOffset = DebugCharts.HEIGHT + 9;
            }
            if (NetworkCharts.showing()) {
                bottomOffset = DebugCharts.HEIGHT + 9;
            }
            drawPie(mc.fontRenderer, node, list, w, h, bottomOffset);
        } finally {
            if (scaled != null) {
                DebugScale.end();
            }
        }
        pies++;
    }

    private static String path(Minecraft mc) {
        try {
            if (profilerName == null) {
                profilerName = ReflectionHelper.findField(Minecraft.class, "debugProfilerName", "field_71465_an");
            }
            return (String) profilerName.get(mc);
        } catch (Exception e) {
            return "root";
        }
    }

    /** 26.3's {@code ProfilerPieChart.extractRenderState} with {@code GuiProfilerChartRenderer}'s pie. */
    static void drawPie(FontRenderer font, Profiler.Result node, List<Profiler.Result> list, int width, int height, int bottomOffset) {
        int centerX = width - 130 - 10;
        int left = centerX - 130;
        int right = centerX + 130;
        int textHeight = list.size() * 9;
        int bottom = height - bottomOffset - 5;
        int textTop = bottom - textHeight;
        int centerY = textTop - 62 - 5;
        String total = PERCENT.format(node.totalUsePercentage) + "%";
        int totalWidth = font.getStringWidth(total);
        int zeroWidth = font.getStringWidth("[0] ");
        int topWidth = right - totalWidth - 5 - left - zeroWidth;
        // 1.12.2 names the node by its path with a dot after it ("root.", "root.gameRenderer.").
        String name = node.profilerName.endsWith(".") ? node.profilerName.substring(0, node.profilerName.length() - 1) : node.profilerName;
        if (name.isEmpty()) {
            name = "root";
        }
        List<String> nameLines = splitName(font, name, topWidth, topWidth - 10);
        int nameTop = centerY - 62 - (nameLines.size() - 1) * 9;
        Gui.drawRect(left - 5, nameTop - 5, right + 5, bottom + 5, DebugCharts.BACKGROUND);
        lastPie = new int[] {left - 5, nameTop - 5, right + 5, bottom + 5};
        pie(list, centerX, centerY);
        String first = "";
        if (!"unspecified".equals(name) && !"root".equals(name)) {
            first = "[0] ";
        }
        first = first + nameLines.get(0);
        font.drawStringWithShadow(first, left, nameTop, 0xFFFFFF);
        for (int i = 1; i < nameLines.size(); i++) {
            font.drawStringWithShadow(nameLines.get(i), left + 10 + zeroWidth, nameTop + i * 9, 0xFFFFFF);
        }
        font.drawStringWithShadow(total, right - totalWidth, nameTop, 0xFFFFFF);
        for (int i = 0; i < list.size(); i++) {
            Profiler.Result r = list.get(i);
            String s = ("unspecified".equals(r.profilerName) ? "[?] " : "[" + (i + 1) + "] ") + r.profilerName;
            int y = textTop + i * 9;
            font.drawStringWithShadow(s, left, y, r.getColor());
            s = PERCENT.format(r.usePercentage) + "%";
            font.drawStringWithShadow(s, right - 50 - font.getStringWidth(s), y, r.getColor());
            s = PERCENT.format(r.totalUsePercentage) + "%";
            font.drawStringWithShadow(s, right - font.getStringWidth(s), y, r.getColor());
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** {@code GuiProfilerChartRenderer.renderToTexture}: the slices, and the 10 high rim on the near half. */
    private static void pie(List<Profiler.Result> list, int cx, int cy) {
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder b = tessellator.getBuffer();
        double total = 0.0;
        for (Profiler.Result r : list) {
            double slice = r.usePercentage;
            double current = total;
            total += slice;
            int steps = MathHelper.floor(slice / 4.0) + 1;
            int color = r.getColor();
            int red = color >> 16 & 255;
            int green = color >> 8 & 255;
            int blue = color & 255;
            b.begin(6, DefaultVertexFormats.POSITION_COLOR);
            b.pos(cx, cy, 0.0).color(red, green, blue, 255).endVertex();
            for (int j = steps; j >= 0; j--) {
                float dir = (float) ((current + slice * j / steps) * (Math.PI * 2) / 100.0);
                b.pos(cx + MathHelper.sin(dir) * 105.0F, cy + MathHelper.cos(dir) * 105.0F * 0.5F, 0.0).color(red, green, blue, 255).endVertex();
            }
            tessellator.draw();
            b.begin(7, DefaultVertexFormats.POSITION_COLOR);
            for (int j = steps; j > 0; j--) {
                float d0 = (float) ((current + slice * j / steps) * (Math.PI * 2) / 100.0);
                float x0 = MathHelper.sin(d0) * 105.0F;
                float y0 = MathHelper.cos(d0) * 105.0F * 0.5F;
                float d1 = (float) ((current + slice * (j - 1) / steps) * (Math.PI * 2) / 100.0);
                float x1 = MathHelper.sin(d1) * 105.0F;
                float y1 = MathHelper.cos(d1) * 105.0F * 0.5F;
                if (!((y0 + y1) / 2.0F < 0.0F)) {
                    b.pos(cx + x0, cy + y0, 0.0).color(red >> 1, green >> 1, blue >> 1, 255).endVertex();
                    b.pos(cx + x0, cy + y0 + 10.0F, 0.0).color(red >> 1, green >> 1, blue >> 1, 255).endVertex();
                    b.pos(cx + x1, cy + y1 + 10.0F, 0.0).color(red >> 1, green >> 1, blue >> 1, 255).endVertex();
                    b.pos(cx + x1, cy + y1, 0.0).color(red >> 1, green >> 1, blue >> 1, 255).endVertex();
                }
            }
            tessellator.draw();
        }
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
    }

    /** 26.3's {@code splitNodeName}: a dotted path over lines of the given widths. */
    private static List<String> splitName(FontRenderer font, String name, int firstWidth, int width) {
        String[] parts = name.split("\\.");
        List<String> lines = new ArrayList<String>();
        String line = "";
        int i = 0;
        while (i < parts.length) {
            String part = (i != 0 ? "." : "") + parts[i];
            String next = line + part;
            if (font.getStringWidth(next) > (lines.isEmpty() ? firstWidth : width)) {
                if (line.isEmpty()) {
                    lines.add(part);
                    i++;
                } else {
                    lines.add(line);
                    line = "";
                }
            } else {
                line = next;
                i++;
            }
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        if (lines.isEmpty()) {
            lines.add(name);
        }
        return lines;
    }
}

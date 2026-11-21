package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.CommandUsageNetwork;
import com.exiledradio.backportcore.presentation.DebugCharts;
import com.exiledradio.backportcore.presentation.DebugKeys;
import com.exiledradio.backportcore.presentation.PingNetwork;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 1.20.2's F3 + 3 (23w31a): the network charts, the bandwidth received (bottom left) and the ping (bottom right), where
 * F3 + 2's charts go and never with them. Core's own key from 1.20.2, with no F3 + Q line (as vanilla's), turning the
 * debug screen on with it. T&amp;T Backport's Core request 10.9.
 *
 * <ul>
 *   <li><b>Bandwidth</b>: every byte the client's connection receives, counted by a Netty handler at the front of its
 *       pipeline (before decryption and the frame splitter, so the length prefixes count, as 1.20.2's
 *       {@code Varint21FrameDecoder} counts them), logged each tick. Not drawn on a world of this game's own server, as
 *       vanilla's is not ({@code isLocalServer}).</li>
 *   <li><b>Ping</b>: each tick while the charts show, Core's ping request ({@link PingNetwork}); the pong's round trip is
 *       logged. A server without Backport Core never answers it: there the chart logs the tab list's latency instead
 *       (the keep-alive ping vanilla's server reports).</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class NetworkCharts {

    public static final DebugCharts.Samples BANDWIDTH = new DebugCharts.Samples();
    public static final DebugCharts.Samples PING = new DebugCharts.Samples();

    private static final AtomicInteger RECEIVED = new AtomicInteger();
    private static boolean shown;
    private static boolean lagometerWas;
    /** For the client self-test: frames drawn, and pongs and pings sent. */
    public static volatile int frames;
    public static volatile int pongs;
    public static volatile int pings;

    private NetworkCharts() {
    }

    /** From Core's client init: F3 + 3 from 1.20.2, and the pong handler. */
    public static void init() {
        DebugKeys.coreKey("1.20.2", new DebugKeys.Key(Keyboard.KEY_3, null, () -> {
            toggle();
            return true;
        }));
        DebugText.add("1.20.2", "debug charts line", lines -> {
            for (int i = 0; i < lines.left.size(); i++) {
                String s = lines.left.get(i);
                if (s != null && s.startsWith("Debug: Pie [shift]")) {
                    lines.left.set(i, chartsLine(lines.mc));
                }
            }
        });
        PingNetwork.onPong = time -> {
            PING.log(Minecraft.getSystemTime() - time);
            pongs++;
        };
    }

    /**
     * 1.20.2's line in place of 1.12.2's "Debug: Pie [shift]: hidden FPS [alt]: hidden" ({@code drawGameInformation}):
     * F3 + 1 is 1.12.2's profiler chart and F3 + 2 its lagometer (FPS, and TPS on this game's own server).
     */
    public static String chartsLine(Minecraft mc) {
        boolean own = mc.isIntegratedServerRunning();
        return "Debug charts: [F3+1] Profiler " + (mc.gameSettings.showDebugProfilerChart ? "visible" : "hidden")
                + "; [F3+2] " + (own ? "FPS + TPS " : "FPS ") + (mc.gameSettings.showLagometer ? "visible" : "hidden")
                + "; [F3+3] " + (!own ? "Bandwidth + Ping" : "Ping") + (shown ? " visible" : " hidden");
    }

    /** @return true while the charts show (the debug screen is on) */
    public static boolean showing() {
        return shown && Minecraft.getMinecraft().gameSettings.showDebugInfo;
    }

    /** 1.20.2's {@code toggleNetworkCharts}: on, the debug screen comes on and F3 + 2's charts go. */
    public static void toggle() {
        Minecraft mc = Minecraft.getMinecraft();
        shown = !mc.gameSettings.showDebugInfo || !shown;
        if (shown) {
            mc.gameSettings.showDebugInfo = true;
            mc.gameSettings.showLagometer = false;
        }
    }

    /** Counts what the connection receives. */
    public static final class Counter extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                RECEIVED.addAndGet(((ByteBuf) msg).readableBytes());
            }
            super.channelRead(ctx, msg);
        }
    }

    /** Forge event handlers; public for the event bus. */
    public static final class Events {

        @SubscribeEvent
        public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
            BANDWIDTH.reset();
            PING.reset();
            RECEIVED.set(0);
            if (event.isLocal()) {
                return;
            }
            ChannelPipeline pipeline = event.getManager().channel().pipeline();
            if (pipeline.get("backportcore_bandwidth") == null) {
                pipeline.addFirst("backportcore_bandwidth", new Counter());
            }
        }

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            NetHandlerPlayClient net = mc.getConnection();
            if (net == null) {
                return;
            }
            BANDWIDTH.log(RECEIVED.getAndSet(0));
            // F3 + 2 (the lagometer) turned on: the network charts go, as each of vanilla's toggles turns the other off.
            boolean lagometer = mc.gameSettings.showLagometer;
            if (lagometer && !lagometerWas) {
                shown = false;
            }
            lagometerWas = lagometer;
            if (!showing()) {
                return;
            }
            if (CommandUsageNetwork.serverHasCore()) {
                PingNetwork.ping(Minecraft.getSystemTime());
                pings++;
            } else if (mc.player != null) {
                NetworkPlayerInfo info = net.getPlayerInfo(mc.player.getUniqueID());
                if (info != null) {
                    PING.log(info.getResponseTime());
                }
            }
        }

        @SubscribeEvent
        public void onOverlay(RenderGameOverlayEvent.Post event) {
            if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || !showing()) {
                return;
            }
            Minecraft mc = Minecraft.getMinecraft();
            ScaledResolution res = event.getResolution();
            // 26.3: at the debug overlay's own scale when it is smaller.
            int[] scaled = DebugScale.begin(mc);
            int w = scaled != null ? scaled[0] : res.getScaledWidth();
            int h = scaled != null ? scaled[1] : res.getScaledHeight();
            int half = w / 2;
            GlStateManager.disableDepth();
            if (!mc.isIntegratedServerRunning()) {
                drawChart(mc.fontRenderer, BANDWIDTH, 0, DebugCharts.width(half), h, true);
            }
            int pw = DebugCharts.width(half);
            drawChart(mc.fontRenderer, PING, w - pw, pw, h, false);
            GlStateManager.enableDepth();
            if (scaled != null) {
                DebugScale.end();
            }
            frames++;
        }
    }

    /** {@code AbstractDebugChart.drawChart} with its bandwidth or ping parts. */
    static void drawChart(FontRenderer font, DebugCharts.Samples samples, int x, int width, int h, boolean bandwidth) {
        int top = h - DebugCharts.HEIGHT;
        Gui.drawRect(x, top, x + width, h, DebugCharts.BACKGROUND);
        int size = samples.size();
        int skip = Math.max(0, DebugCharts.Samples.CAPACITY - (width - 2));
        int count = size - skip;
        long sum = 0L;
        long min = Integer.MAX_VALUE;
        long max = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            int column = x + i + 1;
            long v = samples.get(skip + i);
            min = Math.min(min, v);
            max = Math.max(max, v);
            sum += v;
            int height = bandwidth ? DebugCharts.bandwidthHeight(DebugCharts.bytesPerSecond(v)) : DebugCharts.pingHeight(v);
            int color = bandwidth ? DebugCharts.bandwidthColor(v) : DebugCharts.pingColor(v);
            Gui.drawRect(column, h - height, column + 1, h, color);
        }
        hLine(x, x + width - 1, top, 0xFFFFFFFF);
        hLine(x, x + width - 1, h - 1, 0xFFFFFFFF);
        vLine(x, top, h, 0xFFFFFFFF);
        vLine(x + width - 1, top, h, 0xFFFFFFFF);
        if (count > 0) {
            String lo = text(min, bandwidth) + " min";
            String avg = text((double) sum / count, bandwidth) + " avg";
            String hi = text(max, bandwidth) + " max";
            int y = top - 9;
            font.drawStringWithShadow(lo, x + 2, y, DebugCharts.GREY);
            font.drawStringWithShadow(avg, x + width / 2 - font.getStringWidth(avg) / 2, y, DebugCharts.GREY);
            font.drawStringWithShadow(hi, x + width - font.getStringWidth(hi) - 2, y, DebugCharts.GREY);
        }
        if (bandwidth) {
            for (int value : new int[] {64, 1024, 16384}) {
                int y = h - DebugCharts.bandwidthHeight(value);
                label(font, DebugCharts.bandwidthText(value), x + 1, y + 1);
                hLine(x, x + width - 1, y, 0xFFFFFFFF);
            }
            label(font, DebugCharts.bandwidthText(1048576.0), x + 1, h - DebugCharts.bandwidthHeight(1048576.0) + 1);
        } else {
            label(font, "500 ms", x + 1, top + 1);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static String text(double v, boolean bandwidth) {
        return bandwidth ? DebugCharts.bandwidthText(DebugCharts.bytesPerSecond(v)) : DebugCharts.pingText(v);
    }

    /** {@code drawStringWithShade}: a dark box behind grey text, no shadow. */
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
}

package com.exiledradio.backportcore.presentation;

import java.util.Locale;

/**
 * The numbers of 1.20.6's F3 + 3 charts ({@code AbstractDebugChart}, {@code BandwidthDebugChart}, {@code PingDebugChart})
 * and the 240-sample log they read ({@code LocalSampleLogger}), apart from the drawing, so both sides can check them.
 * Since Core 1.3.2.
 */
public final class DebugCharts {

    /** {@code AbstractDebugChart.CHART_HEIGHT}. */
    public static final int HEIGHT = 60;
    /** The chart's background, {@code 0x90505050}. */
    public static final int BACKGROUND = 0x90505050;
    /** The labels' color, {@code COLOR_GREY}. */
    public static final int GREY = 0xE0E0E0;

    private DebugCharts() {
    }

    /** {@code LocalSampleLogger}: the last 240 samples, oldest first, each of one or more values. Thread-safe. */
    public static final class Samples {
        public static final int CAPACITY = 240;
        private final long[][] values;
        private int start;
        private int size;

        public Samples() {
            this(1);
        }

        /** Samples of several values each (26.3's {@code TpsDebugDimensions}); since Core 1.4.5. */
        public Samples(int dimensions) {
            this.values = new long[CAPACITY][dimensions];
        }

        public synchronized void log(long value) {
            log(new long[] {value});
        }

        /** Logs one sample's values, the first being the one {@link #get(int)} reads. */
        public synchronized void log(long... sample) {
            int at = (this.start + this.size) % CAPACITY;
            long[] row = this.values[at];
            for (int d = 0; d < row.length; d++) {
                row[d] = d < sample.length ? sample[d] : 0L;
            }
            if (this.size < CAPACITY) {
                this.size++;
            } else {
                this.start = (this.start + 1) % CAPACITY;
            }
        }

        public synchronized int size() {
            return this.size;
        }

        public synchronized long get(int i) {
            return get(i, 0);
        }

        public synchronized long get(int i, int dimension) {
            if (i < 0 || i >= this.size) {
                throw new IndexOutOfBoundsException(i + " out of bounds for length " + this.size);
            }
            return this.values[(this.start + i) % CAPACITY][dimension];
        }

        public synchronized void reset() {
            this.start = 0;
            this.size = 0;
        }
    }

    /** {@code AbstractDebugChart.getWidth}: the samples and two border pixels, at most half the screen. */
    public static int width(int maxWidth) {
        return Math.min(Samples.CAPACITY + 2, maxWidth);
    }

    // ---- frame and tick times (nanoseconds; since Core 1.4.5)

    /** {@code FpsDebugChart.toDisplayString} and {@code TpsDebugChart}'s: whole milliseconds. */
    public static String msText(double nanos) {
        return String.format(Locale.ROOT, "%d ms", (int) Math.round(nanos / 1000000.0));
    }

    /** {@code FpsDebugChart.getSampleHeight}: 60 pixels for 33.3 ms (30 fps). */
    public static int fpsHeight(double nanos) {
        return (int) Math.round(nanos / 1000000.0 * 60.0 / 33.333333333333336);
    }

    /** Green at 0 ms, yellow at 28, red from 56. */
    public static int fpsColor(long nanos) {
        return color(nanos / 1000000.0, 0.0, 0xFF00FF00, 28.0, 0xFFFFFF00, 56.0, 0xFFFF0000);
    }

    /** {@code TpsDebugChart.getSampleHeight}: 60 pixels for one tick at the target rate. */
    public static int tpsHeight(double nanos, float msPerTick) {
        return (int) Math.round(nanos / 1000000.0 * 60.0 / msPerTick);
    }

    /** Green at the target tick time, yellow at 1.125 times it, red from 1.25. */
    public static int tpsColor(long nanos, float msPerTick) {
        return color(nanos / 1000000.0, msPerTick, 0xFF00FF00, msPerTick * 1.125, 0xFFFFFF00, msPerTick * 1.25, 0xFFFF0000);
    }

    // ---- ping

    public static String pingText(double ms) {
        return String.format(Locale.ROOT, "%d ms", (int) Math.round(ms));
    }

    public static int pingHeight(double ms) {
        return (int) Math.round(ms * 60.0 / 500.0);
    }

    /** Green at 0 ms, yellow at 250, red from 500. */
    public static int pingColor(long ms) {
        return color(ms, 0.0, 0xFF00FF00, 250.0, 0xFFFFFF00, 500.0, 0xFFFF0000);
    }

    // ---- bandwidth (samples are bytes a tick; shown a second)

    public static double bytesPerSecond(double bytesPerTick) {
        return bytesPerTick * 20.0;
    }

    public static String bandwidthText(double bytesPerSecond) {
        if (bytesPerSecond >= 1048576.0) {
            return String.format(Locale.ROOT, "%.1f MiB/s", bytesPerSecond / 1048576.0);
        }
        return bytesPerSecond >= 1024.0 ? String.format(Locale.ROOT, "%.1f KiB/s", bytesPerSecond / 1024.0)
                : String.format(Locale.ROOT, "%d B/s", (int) Math.floor(bytesPerSecond));
    }

    /** A logarithmic scale: 1 MiB/s is the chart's top. */
    public static int bandwidthHeight(double bytesPerSecond) {
        return (int) Math.round(Math.log(bytesPerSecond + 1.0) * 60.0 / Math.log(1048576.0));
    }

    /** Cyan at nothing, lilac at 8 KiB/s, red from 10 MiB/s. */
    public static int bandwidthColor(long bytesPerTick) {
        return color(bytesPerSecond(bytesPerTick), 0.0, 0xFF00FFFF, 8192.0, 0xFFA0A0FF, 1.048576E7, 0xFFFF0000);
    }

    /** {@code AbstractDebugChart.getSampleColor}: two {@code ARGB32.lerp}s either side of the middle value. */
    static int color(double value, double min, int minColor, double mid, int midColor, double max, int maxColor) {
        value = Math.max(min, Math.min(max, value));
        return value < mid ? lerp((float) ((value - min) / (mid - min)), minColor, midColor)
                : lerp((float) ((value - mid) / (max - mid)), midColor, maxColor);
    }

    /** {@code FastColor.ARGB32.lerp}: each channel {@code Mth.lerpInt}. */
    static int lerp(float delta, int from, int to) {
        int a = lerpInt(delta, from >>> 24, to >>> 24);
        int r = lerpInt(delta, from >> 16 & 255, to >> 16 & 255);
        int g = lerpInt(delta, from >> 8 & 255, to >> 8 & 255);
        int b = lerpInt(delta, from & 255, to & 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static int lerpInt(float delta, int from, int to) {
        return from + (int) Math.floor(delta * (to - from));
    }
}

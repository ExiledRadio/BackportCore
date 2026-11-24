package com.exiledradio.backportcore.presentation.versions.client;

import com.exiledradio.backportcore.presentation.client.DebugText;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;

/**
 * The client side of {@code versions.Look119}: 1.19's look and feel that Backport Core owns since 1.4.7. 1.19's change
 * to the F3 text (22w12a), the "Allocation rate" line under "Mem" (Wild Update Backport's until 1.1.3: the step keeps
 * its version and name, so an older Wild Update jar's copy is left out).
 */
@SideOnly(Side.CLIENT)
public final class Look119Client {

    /** For the client self-test. */
    public static volatile int debugEdits;

    private static final AllocationRate RATE = new AllocationRate();

    private Look119Client() {
    }

    /** From Backport Core's client pre-init, before any backport's client code. */
    static void init() {
        DebugText.add("1.19", "allocation rate", Look119Client::debugText);
    }

    /**
     * 1.19.4's {@code DebugScreenOverlay.getSystemInformation}: "Allocation rate: %03dMB /s" between "Mem" and
     * "Allocated", from the used heap, as {@code AllocationRateCalculator} measures it.
     */
    static void debugText(DebugText.Lines lines) {
        int mem = DebugText.Lines.find(lines.right, "Mem: ");
        if (mem < 0 || DebugText.Lines.find(lines.right, "Allocation rate: ") >= 0) {
            return;
        }
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        lines.right.add(mem + 1, line(RATE.bytesPerSecond(used, System.currentTimeMillis())));
        debugEdits++;
    }

    public static String line(long bytesPerSecond) {
        return String.format(Locale.ROOT, "Allocation rate: %03dMB /s", bytesPerSecond / 1024L / 1024L);
    }

    /**
     * 1.19's {@code DebugScreenOverlay.AllocationRateCalculator}: every half second, the change in used heap per
     * second, unless a garbage collection ran in between (then the last rate stays).
     */
    public static final class AllocationRate {
        private static final List<GarbageCollectorMXBean> GC = ManagementFactory.getGarbageCollectorMXBeans();
        private long lastTime;
        private long lastHeap = -1L;
        private long lastGcCount = -1L;
        private long lastRate;

        long bytesPerSecond(long heap, long now) {
            return bytesPerSecond(heap, now, gcCount());
        }

        public long bytesPerSecond(long heap, long now, long gcCount) {
            if (now - this.lastTime < 500L) {
                return this.lastRate;
            }
            if (this.lastTime != 0L && gcCount == this.lastGcCount) {
                double perSecond = 1000.0 / (double) (now - this.lastTime);
                this.lastRate = Math.round((double) (heap - this.lastHeap) * perSecond);
            }
            this.lastTime = now;
            this.lastHeap = heap;
            this.lastGcCount = gcCount;
            return this.lastRate;
        }

        private static long gcCount() {
            long n = 0L;
            for (GarbageCollectorMXBean bean : GC) {
                n += bean.getCollectionCount();
            }
            return n;
        }
    }
}

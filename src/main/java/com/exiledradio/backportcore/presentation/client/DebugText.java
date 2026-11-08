package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.OrderedSteps;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * One ordered pipeline for the debug screen's (F3) text, as {@link FogPipeline} is for the fog: each version's
 * edits to the left and right columns run in version order, each seeing the lines as vanilla and the versions
 * before left them (1.13 adds "Targeted Block", 1.15 drops "chunk updates", 1.16 adds the mob spawning line and
 * the block's coordinates), so two backports never fight over a line.
 *
 * <p>A backport adds its version's step from client code:
 * <pre>{@code
 * DebugText.add("1.15", "no chunk updates", lines -> lines.replaceLeft(1, s -> s.replaceFirst(" \\(\\d+ chunk updates\\)", "")));
 * }</pre>
 * It runs on Forge's {@code RenderGameOverlayEvent.Text} (no patch) at the highest priority, before other mods
 * add their own lines, and only while the debug screen shows.
 */
@SideOnly(Side.CLIENT)
public final class DebugText {

    /** One version's edit of the debug text. */
    public interface Step {
        void edit(Lines lines);
    }

    /** The two columns, as vanilla's {@code GuiOverlayDebug} wrote them, for the steps to change in place. */
    public static final class Lines {
        public final List<String> left;
        public final List<String> right;
        public final Minecraft mc;
        public final float partialTicks;

        public Lines(List<String> left, List<String> right, Minecraft mc, float partialTicks) {
            this.left = left;
            this.right = right;
            this.mc = mc;
            this.partialTicks = partialTicks;
        }

        /** @return the index of the first line of {@code column} starting with {@code prefix}, or -1 */
        public static int find(List<String> column, String prefix) {
            for (int i = 0; i < column.size(); i++) {
                String s = column.get(i);
                if (s != null && s.startsWith(prefix)) {
                    return i;
                }
            }
            return -1;
        }

        /** Replaces line {@code i} of the left column, if there is one. */
        public void replaceLeft(int i, java.util.function.UnaryOperator<String> change) {
            if (i >= 0 && i < this.left.size() && this.left.get(i) != null) {
                this.left.set(i, change.apply(this.left.get(i)));
            }
        }
    }

    private static final OrderedSteps<Step> STEPS = new OrderedSteps<Step>();
    /** For the client self-test: frames edited. */
    public static volatile int frames;

    private DebugText() {
    }

    /** Adds a step of {@code version}; steps run oldest version first, then in the order added. */
    public static void add(String version, String name, Step step) {
        STEPS.add(version, name, step);
    }

    public static OrderedSteps<Step> steps() {
        return STEPS;
    }

    /** Runs every step at the newest installed version over these columns. */
    public static void apply(Lines lines) {
        for (Step s : STEPS.steps()) {
            s.edit(lines);
        }
    }

    /** Forge's handler; registered by Core's client init. */
    public static final class Events {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onText(RenderGameOverlayEvent.Text event) {
            Minecraft mc = Minecraft.getMinecraft();
            if (!mc.gameSettings.showDebugInfo || STEPS.steps().isEmpty()) {
                return;
            }
            apply(new Lines(event.getLeft(), event.getRight(), mc, event.getPartialTicks()));
            frames++;
        }
    }
}

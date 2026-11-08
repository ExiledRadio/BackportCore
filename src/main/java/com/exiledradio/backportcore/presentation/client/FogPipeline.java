package com.exiledradio.backportcore.presentation.client;

import com.exiledradio.backportcore.presentation.OrderedSteps;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * One ordered pipeline for the fog: each version's change to fog color, density and distance runs in version
 * order, each step seeing what vanilla and the steps before it did (1.13's underwater visibility, then 1.16's
 * darker nether, then 1.18's fog, and so on), so two backports never both patch the fog and fight.
 *
 * <p>A backport adds its version's steps from client code, in pre-init or init:
 * <pre>{@code
 * FogPipeline.add("1.13", "underwater visibility", new FogPipeline.Step() {
 *     public void density(FogPipeline.Density d) { if (inWater(d.entity)) d.set(0.05F); }
 * });
 * }</pre>
 * It runs on Forge's three fog events (no patch): {@code FogColors} (the clear and fog color),
 * {@code FogDensity} (exponential fog: a step that sets a density makes the fog exponential, as vanilla's
 * water and lava fog are) and {@code RenderFogEvent} (linear fog's start and end, after vanilla set them).
 * Sky color and cloud height are not on Forge's events and are not part of the pipeline yet.
 */
@SideOnly(Side.CLIENT)
public final class FogPipeline {

    /** One version's fog change. Every method is optional. */
    public interface Step {
        /** The fog and clear color (0 to 1 each). */
        default void colors(Colors colors) {
        }

        /** The exponential fog density; call {@link Density#set} to use one. */
        default void density(Density density) {
        }

        /** Linear fog's start and end distances, after vanilla's {@code setupFog}. */
        default void distance(Distance distance) {
        }
    }

    private static final OrderedSteps<Step> STEPS = new OrderedSteps<Step>();

    /** Adds a step of {@code version} ("1.13"); steps run oldest version first, then in the order added. */
    public static void add(String version, String name, Step step) {
        STEPS.add(version, name, step);
    }

    /** @return the pipeline itself (for the self-tests and the log) */
    public static OrderedSteps<Step> steps() {
        return STEPS;
    }

    /** What every step sees: the view entity, the block the camera is in, and the partial tick. */
    public abstract static class Context {
        public final Entity entity;
        public final IBlockState state;
        public final double partialTicks;

        Context(EntityViewRenderEvent event) {
            this.entity = event.getEntity();
            this.state = event.getState();
            this.partialTicks = event.getRenderPartialTicks();
        }
    }

    /** The fog color, as vanilla computed it, for the steps to change. */
    public static final class Colors extends Context {
        public float red;
        public float green;
        public float blue;

        Colors(EntityViewRenderEvent.FogColors event) {
            super(event);
            this.red = event.getRed();
            this.green = event.getGreen();
            this.blue = event.getBlue();
        }
    }

    /** The exponential fog density. */
    public static final class Density extends Context {
        private float density;
        private boolean set;

        Density(EntityViewRenderEvent.FogDensity event) {
            super(event);
            this.density = event.getDensity();
        }

        public float get() {
            return this.density;
        }

        /** Uses exponential fog of this density for this frame. */
        public void set(float density) {
            this.density = density;
            this.set = true;
        }

        public boolean isSet() {
            return this.set;
        }
    }

    /** Linear fog's distances: {@code fogMode} -1 is the sky, 0 the terrain; {@code far} is the render distance. */
    public static final class Distance extends Context {
        public final int fogMode;
        public final float far;
        private float start = Float.NaN;
        private float end = Float.NaN;

        Distance(EntityViewRenderEvent.RenderFogEvent event) {
            super(event);
            this.fogMode = event.getFogMode();
            this.far = event.getFarPlaneDistance();
        }

        /** Sets the fog's start and end distances for this frame. */
        public void set(float start, float end) {
            this.start = start;
            this.end = end;
        }

        /** @return the start set by an earlier step, or NaN when none set one (vanilla's stands) */
        public float start() {
            return this.start;
        }

        /** @return the end set by an earlier step, or NaN when none set one (vanilla's stands) */
        public float end() {
            return this.end;
        }
    }

    /** Forge event handlers; registered by Core's client init. */
    public static final class Events {

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onColors(EntityViewRenderEvent.FogColors event) {
            List<Step> steps = STEPS.steps();
            if (steps.isEmpty()) {
                return;
            }
            Colors c = new Colors(event);
            for (Step s : steps) {
                s.colors(c);
            }
            event.setRed(c.red);
            event.setGreen(c.green);
            event.setBlue(c.blue);
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onDensity(EntityViewRenderEvent.FogDensity event) {
            List<Step> steps = STEPS.steps();
            if (steps.isEmpty()) {
                return;
            }
            Density d = new Density(event);
            for (Step s : steps) {
                s.density(d);
            }
            if (d.isSet()) {
                event.setDensity(d.get());
                event.setCanceled(true);
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void onFog(EntityViewRenderEvent.RenderFogEvent event) {
            List<Step> steps = STEPS.steps();
            if (steps.isEmpty()) {
                return;
            }
            Distance d = new Distance(event);
            for (Step s : steps) {
                s.distance(d);
            }
            if (!Float.isNaN(d.start)) {
                GlStateManager.setFogStart(d.start);
                GlStateManager.setFogEnd(d.end);
            }
        }
    }
}

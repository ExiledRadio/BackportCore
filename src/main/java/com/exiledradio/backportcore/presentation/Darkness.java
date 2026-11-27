package com.exiledradio.backportcore.presentation;

import javax.annotation.Nullable;

/**
 * The Darkness effect's hook into Core's 26.1 lightmap ({@code client.ModernLightmap}). The mod that owns Darkness
 * (Wild Update Backport) registers a {@link Source} from its client setup; the lightmap reads the frame's factor and the
 * Darkness Pulsing option from it, as 26.3's {@code LightTexture} does. Registered once; a second source replaces the
 * first. No client classes here, so a common class may hold the reference.
 *
 * <p>Without a source (a Wild Update older than 1.1.5) the lightmap falls back to reading Wild Update's classes.</p>
 */
public final class Darkness {

    /** What the lightmap needs from the effect. Called on the client thread, once or twice a frame. */
    public interface Source {
        /** @return the effect's blend for the frame, 0 to 1 ({@code darknessEffectBlend}), 0 without the effect */
        float factor(float partialTicks);

        /** @return Darkness Pulsing, 0 to 1 ({@code darknessEffectScale}) */
        float scale();
    }

    @Nullable
    private static volatile Source source;

    private Darkness() {
    }

    public static void register(Source darkness) {
        source = darkness;
    }

    /** @return the registered source, or null */
    @Nullable
    public static Source source() {
        return source;
    }
}

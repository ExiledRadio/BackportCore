package com.exiledradio.backportcore.presentation;

/**
 * 26.1's lightmap, as 26.3 has it ({@code shaders/core/lightmap.fsh} with {@code LightmapRenderStateExtractor}'s
 * inputs), computed on the CPU into 1.12.2's 16 by 16 {@code EntityRenderer.lightmapColors}. No client classes: the
 * server self-tests check it against the shader's formula.
 *
 * <p>Per cell (block level {@code b}, sky level {@code s}, each {@code level / 15}):
 * <pre>
 * color  = max(ambient, nightVisionColor * nightVision)
 *        + skyLightColor * curve(s) * skyFactor
 *        + mix(blockLightTint, white, 0.9 * (2b - 1)^2) * curve(b) * blockFactor
 * color  = mix(color, color * (0.7, 0.6, 0.6), bossDarkening)
 * color  = clamp(color - darknessScale, 0, 1)
 * color  = mix(color, notGamma(color), brightness)
 * </pre>
 * where {@code curve(l) = l / (4 - 3l)} (1.12.2's brightness curve without its ambient term) and {@code notGamma}
 * scales the color so its largest channel becomes {@code 1 - (1 - max)^4}. The dimension's values are 26.3's
 * environment attributes ({@code dimension_type/*.json}, {@code timeline/day.json}, {@code WeatherAttributes}).
 *
 * <p>Since Core 1.4.5.
 */
public final class LightmapColors {

    /** 26.3's {@code DimensionDefaults.BLOCK_LIGHT_TINT}, the torches' warm yellow ({@code #FFD88C}). */
    public static final int BLOCK_LIGHT_TINT = 0xFFD88C;
    /** 26.3's {@code DimensionDefaults.NIGHT_VISION_COLOR} ({@code #999999}). */
    public static final int NIGHT_VISION_COLOR = 0x999999;
    /** {@code overworld.json}'s {@code visual/ambient_light_color}. */
    public static final int OVERWORLD_AMBIENT = 0x0A0A0A;
    /** {@code the_nether.json}'s {@code visual/ambient_light_color}. */
    public static final int NETHER_AMBIENT = 0x302821;
    /** {@code the_nether.json}'s {@code visual/sky_light_color} (its sky light factor is 0). */
    public static final int NETHER_SKY_LIGHT = 0x7A7AFF;
    /** {@code the_end.json}'s {@code visual/ambient_light_color}. */
    public static final int END_AMBIENT = 0x3F473F;
    /** {@code the_end.json}'s {@code visual/sky_light_color} (its sky light factor is 0). */
    public static final int END_SKY_LIGHT = 0xAC60CD;
    /** 26.3's {@code Timelines.NIGHT_SKY_LIGHT_COLOR}: {@code colorFromFloat(1, 0.48, 0.48, 1)}. */
    public static final int NIGHT_SKY_LIGHT = 0x7A7AFF;
    /** 26.3's {@code Timelines.NIGHT_SKY_LIGHT_FACTOR}. */
    public static final float NIGHT_SKY_LIGHT_FACTOR = 0.24F;
    /** {@code WeatherAttributes.RAIN}'s blend toward the night's sky light (the factor's alpha). */
    public static final float RAIN_ALPHA = 0.3125F;
    /** {@code WeatherAttributes.THUNDER}'s blend. */
    public static final float THUNDER_ALPHA = 0.52734375F;

    /** {@code timeline/day.json}'s keyframes of {@code visual/sky_light_color} and {@code visual/sky_light_factor}. */
    private static final int DAY_START = 730;
    private static final int DAY_END = 11270;
    private static final int NIGHT_START = 13140;
    private static final int NIGHT_END = 22860;
    private static final int DAY_TICKS = 24000;

    /** What the shader reads ({@code LightmapInfo}). */
    public static final class Inputs {
        public float skyFactor = 1.0F;
        public float blockFactor = 1.4F;
        public float nightVision;
        public float darknessScale;
        public float bossDarkening;
        public float brightness;
        public final float[] blockLightTint = rgb(BLOCK_LIGHT_TINT);
        public final float[] skyLightColor = rgb(0xFFFFFF);
        public final float[] ambientColor = rgb(0x000000);
        public final float[] nightVisionColor = rgb(NIGHT_VISION_COLOR);
    }

    /**
     * Forge's {@code WorldProvider.getLightmapColors}, called for each cell with the color before Darkness and the clamp
     * (where 1.12.2 calls it, after the boss darkening), the sky and block brightness of the cell.
     */
    public interface CellHook {
        void apply(float skyBrightness, float blockBrightness, float[] rgb);
    }

    private LightmapColors() {
    }

    /** @return the shader's {@code get_brightness}: {@code level / (4 - 3 level)}, level 0 to 15 */
    public static float curve(int level) {
        float v = level / 15.0F;
        return v / (4.0F - 3.0F * v);
    }

    /**
     * Fills the 256 colors (index {@code sky * 16 + block}, as 1.12.2's lightmap texture) as {@code 0xFFRRGGBB}.
     *
     * @param hook Forge's per-cell hook, or null
     */
    public static void compute(Inputs in, CellHook hook, int[] out) {
        float[] c = new float[3];
        float[] blockColor = new float[3];
        float nvr = in.nightVisionColor[0] * in.nightVision;
        float nvg = in.nightVisionColor[1] * in.nightVision;
        float nvb = in.nightVisionColor[2] * in.nightVision;
        for (int i = 0; i < 256; i++) {
            int skyLevel = i / 16;
            int blockLevel = i % 16;
            float block = blockLevel / 15.0F;
            float blockBrightness = curve(blockLevel) * in.blockFactor;
            float skyBrightness = curve(skyLevel) * in.skyFactor;
            c[0] = Math.max(in.ambientColor[0], nvr);
            c[1] = Math.max(in.ambientColor[1], nvg);
            c[2] = Math.max(in.ambientColor[2], nvb);
            float mixToWhite = 0.9F * (2.0F * block - 1.0F) * (2.0F * block - 1.0F);
            for (int k = 0; k < 3; k++) {
                c[k] += in.skyLightColor[k] * skyBrightness;
                blockColor[k] = in.blockLightTint[k] + (1.0F - in.blockLightTint[k]) * mixToWhite;
                c[k] += blockColor[k] * blockBrightness;
            }
            if (in.bossDarkening != 0.0F) {
                c[0] = mix(c[0], c[0] * 0.7F, in.bossDarkening);
                c[1] = mix(c[1], c[1] * 0.6F, in.bossDarkening);
                c[2] = mix(c[2], c[2] * 0.6F, in.bossDarkening);
            }
            if (hook != null) {
                hook.apply(skyBrightness, blockBrightness, c);
            }
            for (int k = 0; k < 3; k++) {
                c[k] = clamp(c[k] - in.darknessScale);
            }
            float max = Math.max(c[0], Math.max(c[1], c[2]));
            float inverted = 1.0F - max;
            float scaled = 1.0F - inverted * inverted * inverted * inverted;
            // notGamma of black divides 0 by 0; the GPU stores that as 0.
            float scale = max > 0.0F ? scaled / max : 0.0F;
            for (int k = 0; k < 3; k++) {
                c[k] = mix(c[k], c[k] * scale, in.brightness);
            }
            out[i] = 0xFF000000 | unorm(c[0]) << 16 | unorm(c[1]) << 8 | unorm(c[2]);
        }
    }

    /**
     * The Overworld's sky light ({@code timeline/day.json} times {@code overworld.json}, then {@code WeatherAttributes}
     * and {@code ClientLevel}'s lightning layer), and its ambient light.
     *
     * @param dayTime  the time of day, 0 to 24000 ticks, partial ticks included
     * @param rain     the rain level, 0 to 1 (thunder included, as 1.12.2's and 26.3's)
     * @param thunder  the thunder level, 0 to 1
     * @param flash    a lightning flash this tick
     */
    public static void overworld(Inputs in, float dayTime, float rain, float thunder, boolean flash) {
        set(in.ambientColor, OVERWORLD_AMBIENT);
        float t = dayAlpha(dayTime);
        float[] night = rgb(NIGHT_SKY_LIGHT);
        for (int k = 0; k < 3; k++) {
            in.skyLightColor[k] = mix(1.0F, night[k], t);
        }
        float factor = mix(1.0F, NIGHT_SKY_LIGHT_FACTOR, t);
        // WeatherAttributes: rain then thunder, each lerped in by its level (rain's level less thunder's).
        float rainLevel = rain - thunder;
        float colorRainAlpha = channel(RAIN_ALPHA);
        float colorThunderAlpha = channel(THUNDER_ALPHA);
        if (rainLevel > 0.0F) {
            factor = mix(factor, mix(factor, NIGHT_SKY_LIGHT_FACTOR, RAIN_ALPHA), rainLevel);
            for (int k = 0; k < 3; k++) {
                in.skyLightColor[k] = mix(in.skyLightColor[k], mix(in.skyLightColor[k], night[k], colorRainAlpha), rainLevel);
            }
        }
        if (thunder > 0.0F) {
            factor = mix(factor, mix(factor, NIGHT_SKY_LIGHT_FACTOR, THUNDER_ALPHA), thunder);
            for (int k = 0; k < 3; k++) {
                in.skyLightColor[k] = mix(in.skyLightColor[k], mix(in.skyLightColor[k], night[k], colorThunderAlpha), thunder);
            }
        }
        in.skyFactor = flash ? 1.0F : factor;
    }

    /** {@code the_nether.json}: no sky light, a warm ambient light. */
    public static void nether(Inputs in) {
        set(in.ambientColor, NETHER_AMBIENT);
        set(in.skyLightColor, NETHER_SKY_LIGHT);
        in.skyFactor = 0.0F;
    }

    /** {@code the_end.json}: no sky light (no End flashes in 1.12.2), a green-gray ambient light. */
    public static void end(Inputs in) {
        set(in.ambientColor, END_AMBIENT);
        set(in.skyLightColor, END_SKY_LIGHT);
        in.skyFactor = 0.0F;
    }

    /** @return how far the day timeline is toward night at this time of day: 0 by day, 1 at night, linear between */
    public static float dayAlpha(float dayTime) {
        float t = dayTime % DAY_TICKS;
        if (t < 0.0F) {
            t += DAY_TICKS;
        }
        if (t < DAY_START) {
            // The segment that wraps from the night's last keyframe to the day's first.
            return 1.0F - (t + DAY_TICKS - NIGHT_END) / (DAY_START + DAY_TICKS - NIGHT_END);
        }
        if (t <= DAY_END) {
            return 0.0F;
        }
        if (t < NIGHT_START) {
            return (t - DAY_END) / (NIGHT_START - DAY_END);
        }
        if (t <= NIGHT_END) {
            return 1.0F;
        }
        return 1.0F - (t - NIGHT_END) / (DAY_START + DAY_TICKS - NIGHT_END);
    }

    /**
     * @return the time of day (0 to 24000) at which 1.12.2's {@code WorldProvider.calculateCelestialAngle} gives this
     *         angle, so a dimension with its own day keeps 26.3's day timeline in step with its sun
     */
    public static float dayTimeOfCelestialAngle(float angle) {
        float a = angle - (float) Math.floor(angle);
        // 1.12.2: f = t / 24000 - 0.25 (wrapped), angle = f + ((1 - (cos(f pi) + 1) / 2) - f) / 3, increasing in f.
        double lo = 0.0;
        double hi = 1.0;
        for (int i = 0; i < 30; i++) {
            double f = (lo + hi) / 2.0;
            double g = f + ((1.0 - (Math.cos(f * Math.PI) + 1.0) / 2.0) - f) / 3.0;
            if (g < a) {
                lo = f;
            } else {
                hi = f;
            }
        }
        double t = ((lo + hi) / 2.0 + 0.25) * DAY_TICKS;
        return (float) (t % DAY_TICKS);
    }

    /** @return a {@code 0xRRGGBB} color as three floats, 26.3's {@code ARGB.vector3fFromRGB24} */
    public static float[] rgb(int color) {
        return new float[]{(color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F};
    }

    private static void set(float[] target, int color) {
        float[] c = rgb(color);
        System.arraycopy(c, 0, target, 0, 3);
    }

    /** 26.3's {@code ARGB.color(float, int)} alpha: {@code floor(a * 255)} read back as {@code / 255}. */
    private static float channel(float alpha) {
        return (float) Math.floor(alpha * 255.0F) / 255.0F;
    }

    /** An {@code RGBA8_UNORM} store: rounded to the nearest of 256. */
    public static int unorm(float value) {
        return (int) (clamp(value) * 255.0F + 0.5F);
    }

    private static float clamp(float v) {
        return v < 0.0F ? 0.0F : v > 1.0F ? 1.0F : v;
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }
}

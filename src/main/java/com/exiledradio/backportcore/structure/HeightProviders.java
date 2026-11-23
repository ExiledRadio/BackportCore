package com.exiledradio.backportcore.structure;

import com.exiledradio.backportcore.BackportCore;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Today's height providers ({@code HeightProvider}) and vertical anchors ({@code VerticalAnchor}), read from a
 * structure's {@code start_height}: a bare anchor, or {@code constant}, {@code uniform}, {@code biased_to_bottom},
 * {@code very_biased_to_bottom}, {@code trapezoid} or {@code weighted_list}. Each draws from the random exactly as
 * 26.3 does.
 *
 * <p>Anchors resolve against the world's own heights ({@link Context}): {@code above_bottom} from its lowest block,
 * {@code below_top} from its highest, as today's {@code WorldGenerationContext}. 1.12.2's overworld is y 0 to 255. An
 * {@code absolute} height below the world's bottom (today's deep y -64 to -1) is moved up proportionally,
 * {@code minY + (y + 64) * height / 384}, as Tricky Trials Backport moves trial chambers; in a world that reaches y -64
 * nothing moves.
 *
 * @since 1.4.5
 */
public final class HeightProviders {

    private HeightProviders() {
    }

    /** Today's {@code WorldGenerationContext}: the world's lowest block, its height, its sea level. */
    public static final class Context {
        public final int minY;
        public final int height;
        public final int seaLevel;

        public Context(int minY, int height, int seaLevel) {
            this.minY = minY;
            this.height = height;
            this.seaLevel = seaLevel;
        }

        /** An {@code absolute} y in this world: itself, or, below the world's bottom, moved up proportionally. */
        public int absolute(int y) {
            if (y >= this.minY || this.minY <= -64) {
                return y;
            }
            return this.minY + Math.floorDiv((y + 64) * this.height, 384);
        }
    }

    /** Today's {@code VerticalAnchor}. */
    public abstract static class Anchor {
        public abstract int resolve(Context c);

        /** Reads {@code {"absolute": y}}, {@code {"above_bottom": n}}, {@code {"below_top": n}} or {@code {"relative_to_sea_level": n}}. */
        public static Anchor read(JsonElement json) {
            JsonObject o = json.getAsJsonObject();
            if (o.has("absolute")) {
                final int y = o.get("absolute").getAsInt();
                return new Anchor() {
                    @Override
                    public int resolve(Context c) {
                        return c.absolute(y);
                    }

                    @Override
                    public String toString() {
                        return y + " absolute";
                    }
                };
            }
            if (o.has("above_bottom")) {
                final int n = o.get("above_bottom").getAsInt();
                return new Anchor() {
                    @Override
                    public int resolve(Context c) {
                        return c.minY + n;
                    }

                    @Override
                    public String toString() {
                        return n + " above bottom";
                    }
                };
            }
            if (o.has("below_top")) {
                final int n = o.get("below_top").getAsInt();
                return new Anchor() {
                    @Override
                    public int resolve(Context c) {
                        return c.height - 1 + c.minY - n;
                    }

                    @Override
                    public String toString() {
                        return n + " below top";
                    }
                };
            }
            if (o.has("relative_to_sea_level")) {
                final int n = o.get("relative_to_sea_level").getAsInt();
                return new Anchor() {
                    @Override
                    public int resolve(Context c) {
                        return c.seaLevel + n;
                    }

                    @Override
                    public String toString() {
                        return n + " relative to sea level";
                    }
                };
            }
            throw new IllegalArgumentException("Not a vertical anchor: " + json);
        }

        static boolean is(JsonElement json) {
            if (!json.isJsonObject()) {
                return false;
            }
            JsonObject o = json.getAsJsonObject();
            return o.has("absolute") || o.has("above_bottom") || o.has("below_top") || o.has("relative_to_sea_level");
        }
    }

    /** Today's {@code HeightProvider}. */
    public abstract static class Height {
        public abstract int sample(Random random, Context c);
    }

    /** Reads a height provider as a structure's {@code start_height} writes it. */
    public static Height read(JsonElement json) {
        if (Anchor.is(json)) {
            return constant(Anchor.read(json));
        }
        JsonObject o = json.getAsJsonObject();
        String type = ModernPalette.full(o.get("type").getAsString());
        switch (type) {
            case "minecraft:constant":
                return constant(Anchor.read(o.get("value")));
            case "minecraft:uniform": {
                final Anchor min = Anchor.read(o.get("min_inclusive"));
                final Anchor max = Anchor.read(o.get("max_inclusive"));
                return new Height() {
                    @Override
                    public int sample(Random random, Context c) {
                        int lo = min.resolve(c);
                        int hi = max.resolve(c);
                        if (lo > hi) {
                            warn(this, lo, hi);
                            return lo;
                        }
                        return betweenInclusive(random, lo, hi);
                    }

                    @Override
                    public String toString() {
                        return "uniform(" + min + ", " + max + ")";
                    }
                };
            }
            case "minecraft:biased_to_bottom": {
                final Anchor min = Anchor.read(o.get("min_inclusive"));
                final Anchor max = Anchor.read(o.get("max_inclusive"));
                final int inner = o.has("inner") ? o.get("inner").getAsInt() : 1;
                return new Height() {
                    @Override
                    public int sample(Random random, Context c) {
                        int lo = min.resolve(c);
                        int hi = max.resolve(c);
                        if (hi - lo - inner + 1 <= 0) {
                            warn(this, lo, hi);
                            return lo;
                        }
                        int limit = random.nextInt(hi - lo - inner + 1);
                        return random.nextInt(limit + inner) + lo;
                    }

                    @Override
                    public String toString() {
                        return "biased_to_bottom(" + min + ", " + max + ", " + inner + ")";
                    }
                };
            }
            case "minecraft:very_biased_to_bottom": {
                final Anchor min = Anchor.read(o.get("min_inclusive"));
                final Anchor max = Anchor.read(o.get("max_inclusive"));
                final int inner = o.has("inner") ? o.get("inner").getAsInt() : 1;
                return new Height() {
                    @Override
                    public int sample(Random random, Context c) {
                        int lo = min.resolve(c);
                        int hi = max.resolve(c);
                        if (hi - lo - inner + 1 <= 0) {
                            warn(this, lo, hi);
                            return lo;
                        }
                        int upper = nextInt(random, lo + inner, hi);
                        int biasedUpper = nextInt(random, lo, upper - 1);
                        return nextInt(random, lo, biasedUpper - 1 + inner);
                    }

                    @Override
                    public String toString() {
                        return "very_biased_to_bottom(" + min + ", " + max + ", " + inner + ")";
                    }
                };
            }
            case "minecraft:trapezoid": {
                final Anchor min = Anchor.read(o.get("min_inclusive"));
                final Anchor max = Anchor.read(o.get("max_inclusive"));
                final int plateau = o.has("plateau") ? o.get("plateau").getAsInt() : 0;
                return new Height() {
                    @Override
                    public int sample(Random random, Context c) {
                        int lo = min.resolve(c);
                        int hi = max.resolve(c);
                        if (lo > hi) {
                            warn(this, lo, hi);
                            return lo;
                        }
                        int range = hi - lo;
                        if (plateau >= range) {
                            return betweenInclusive(random, lo, hi);
                        }
                        int plateauStart = (range - plateau) / 2;
                        int plateauEnd = range - plateauStart;
                        return lo + betweenInclusive(random, 0, plateauEnd) + betweenInclusive(random, 0, plateauStart);
                    }

                    @Override
                    public String toString() {
                        return "trapezoid(" + min + ", " + max + ", " + plateau + ")";
                    }
                };
            }
            case "minecraft:weighted_list": {
                final List<Height> heights = new ArrayList<Height>();
                final List<Integer> weights = new ArrayList<Integer>();
                int sum = 0;
                for (JsonElement e : o.getAsJsonArray("distribution")) {
                    JsonObject w = e.getAsJsonObject();
                    heights.add(read(w.get("data")));
                    int weight = w.get("weight").getAsInt();
                    weights.add(weight);
                    sum += weight;
                }
                if (sum <= 0) {
                    throw new IllegalArgumentException("Weighted list has no elements");
                }
                final int total = sum;
                return new Height() {
                    @Override
                    public int sample(Random random, Context c) {
                        // Today's WeightedList.getRandomOrThrow.
                        int selection = random.nextInt(total);
                        for (int i = 0; i < heights.size(); i++) {
                            selection -= weights.get(i);
                            if (selection < 0) {
                                return heights.get(i).sample(random, c);
                            }
                        }
                        throw new IllegalStateException();
                    }

                    @Override
                    public String toString() {
                        return "weighted_list" + heights;
                    }
                };
            }
            default:
                throw new IllegalArgumentException("Unknown height provider type " + type);
        }
    }

    public static Height constant(final Anchor value) {
        return new Height() {
            @Override
            public int sample(Random random, Context c) {
                return value.resolve(c);
            }

            @Override
            public String toString() {
                return "constant(" + value + ")";
            }
        };
    }

    /** Today's {@code Mth.randomBetweenInclusive}. */
    static int betweenInclusive(Random random, int min, int maxInclusive) {
        return random.nextInt(maxInclusive - min + 1) + min;
    }

    /** Today's {@code Mth.nextInt}. */
    static int nextInt(Random random, int minInclusive, int maxInclusive) {
        return minInclusive >= maxInclusive ? minInclusive : random.nextInt(maxInclusive - minInclusive + 1) + minInclusive;
    }

    private static void warn(Height h, int lo, int hi) {
        BackportCore.LOGGER.debug("Empty height range: {} ({} to {})", h, lo, hi);
    }
}

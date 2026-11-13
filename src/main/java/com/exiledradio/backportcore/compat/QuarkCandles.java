package com.exiledradio.backportcore.compat;

import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * The backports' candles in place of Quark's, whose candle Backport Core hides beside them: influence on Quark's
 * matrix enchanting (patch P21) and the enchanting power Quark gives its own candles. Everything is read from
 * Quark's own settings, by reflection, so its config file still decides; nothing here runs without Quark.
 */
public final class QuarkCandles {

    private static boolean looked;
    @Nullable
    private static Field allowInfluencing, influenceMax, candleInfluences, enchantPower;
    @Nullable
    private static Method featureEnabled;
    @Nullable
    private static Class<?> candleFeature;
    private static final Map<Block, Object> COLORS = new IdentityHashMap<Block, Object>();
    private static final Object NONE = new Object();

    private QuarkCandles() {
    }

    private static synchronized void look() {
        if (looked) {
            return;
        }
        looked = true;
        try {
            Class<?> matrix = Class.forName("vazkii.quark.oddities.feature.MatrixEnchanting");
            allowInfluencing = matrix.getField("allowInfluencing");
            influenceMax = matrix.getField("influenceMax");
            candleInfluences = matrix.getField("candleInfluences");
        } catch (Throwable t) {
            allowInfluencing = null;
        }
        try {
            candleFeature = Class.forName("vazkii.quark.decoration.feature.TallowAndCandles");
            enchantPower = candleFeature.getField("enchantPower");
            featureEnabled = Class.forName("vazkii.quark.base.module.ModuleLoader").getMethod("isFeatureEnabled", Class.class);
        } catch (Throwable t) {
            enchantPower = null;
        }
    }

    /** P21, from the start of Quark's {@code TileMatrixEnchanter.getEnchantPowerAt}: a dyed candle's influences. */
    @SuppressWarnings("unchecked")
    public static void influence(Map<Enchantment, Integer> influences, World world, BlockPos pos) {
        look();
        if (allowInfluencing == null) {
            return;
        }
        try {
            if (!allowInfluencing.getBoolean(null)) {
                return;
            }
            EnumDyeColor color = color(world.getBlockState(pos).getBlock());
            if (color == null) {
                return;
            }
            List<Enchantment> list = ((Map<EnumDyeColor, List<Enchantment>>) candleInfluences.get(null)).get(color);
            if (list == null) {
                return;
            }
            int max = influenceMax.getInt(null);
            for (Enchantment e : list) {
                int value = influences.getOrDefault(e, 0);
                if (value < max) {
                    influences.put(e, value + 1);
                }
            }
        } catch (Throwable t) {
            allowInfluencing = null;
        }
    }

    /**
     * The enchanting power Quark gives one of its candles (half a bookshelf by default), or 0 without Quark or with
     * its candles switched off. For the backports' candle blocks' {@code getEnchantPowerBonus}.
     */
    public static float enchantPower() {
        look();
        if (enchantPower == null) {
            return 0;
        }
        try {
            if (!(Boolean) featureEnabled.invoke(null, candleFeature)) {
                return 0;
            }
            return enchantPower.getFloat(null);
        } catch (Throwable t) {
            enchantPower = null;
            return 0;
        }
    }

    /**
     * How many candles Quark's tallow recipe (string over two tallow) makes, or 0 when it has none: without Quark,
     * with its candles switched off, or with its tallow switched off (which also takes its recipes away).
     */
    public static int tallowCandles() {
        look();
        if (enchantPower == null) {
            return 0;
        }
        try {
            if (!(Boolean) featureEnabled.invoke(null, candleFeature)
                    || !candleFeature.getField("enableTallow").getBoolean(null)) {
                return 0;
            }
            return Math.max(1, candleFeature.getField("candlesCrafted").getInt(null));
        } catch (Throwable t) {
            return 0;
        }
    }

    /** The dye of a candle block named {@code <color>_candle} by any mod but Quark itself, or null. */
    @Nullable
    static EnumDyeColor color(Block block) {
        Object known;
        synchronized (COLORS) {
            known = COLORS.get(block);
        }
        if (known == null) {
            known = NONE;
            ResourceLocation name = block.getRegistryName();
            if (name != null && !"quark".equals(name.getNamespace())) {
                for (EnumDyeColor dye : EnumDyeColor.values()) {
                    String path = name.getPath();
                    if (path.equals(dye.getName() + "_candle")
                            || dye == EnumDyeColor.SILVER && path.equals("light_gray_candle")) {
                        known = dye;
                        break;
                    }
                }
            }
            synchronized (COLORS) {
                COLORS.put(block, known);
            }
        }
        return known == NONE ? null : (EnumDyeColor) known;
    }
}

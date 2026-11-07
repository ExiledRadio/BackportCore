package com.exiledradio.backportcore.trade;

import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.village.MerchantRecipe;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Villager and wandering trader trades added by other mods.
 *
 * <p>Later updates add trades to villagers 1.14 already had - kelp and coral from 1.13, honey from
 * 1.15 - and the mod that builds the trade tables (Village &amp; Pillage Backport) draws from here as
 * well as from its own. Nothing here needs that mod: without it, nothing reads the registry.
 *
 * <h2>Villagers</h2>
 * A villager's trades come in five levels (novice to master). At each level, 1.14 picks two trades at
 * random from that level's pool; a trade added with {@link #addVillagerTrade} joins the pool for its
 * career and level. Careers are keyed as {@code "profession registry name/career name"}, the way
 * Forge's villager registry names them: {@code "minecraft:farmer/fisherman"}.
 *
 * <h2>Wandering trader</h2>
 * A fresh trader takes trades from three pools ({@link TraderPool}); {@link #addTraderTrade} adds to one.
 *
 * <p>Register from pre-init to post-init. A trade's {@link VillagerTrade#available()} is asked every
 * time a pool is drawn from, so a trade for an item that may be switched off can just say so.
 */
public final class TradeRegistry {

    /** One entry in a villager level's pool. */
    public interface VillagerTrade {

        /** @return false when the item this trade needs does not exist in this game */
        default boolean available() {
            return true;
        }

        /**
         * @param villager the villager, or null when another mod asks for an example of the trade
         *                 (a recipe viewer, with a stand-in merchant)
         * @return the trade, or null if it cannot be made here
         */
        @Nullable
        MerchantRecipe offer(@Nullable EntityVillager villager, Random random);
    }

    /** One entry in a wandering trader pool. */
    public interface TraderTrade {

        /** @return false when the item this trade needs does not exist in this game */
        default boolean available() {
            return true;
        }

        /** @return the trade, or null if it cannot be made here */
        @Nullable
        MerchantRecipe offer(Random random);
    }

    /** The wandering trader's three pools, in the order they fill its screen. */
    public enum TraderPool {
        /** Things the trader buys (two picked). */
        BUYING,
        /** Rarer things it sells (two picked). */
        SPECIAL,
        /** Ordinary things it sells (five picked). */
        ORDINARY
    }

    private static final Map<String, List<List<VillagerTrade>>> VILLAGER = new HashMap<String, List<List<VillagerTrade>>>();
    private static final Map<TraderPool, List<TraderTrade>> TRADER = new EnumMap<TraderPool, List<TraderTrade>>(TraderPool.class);

    private TradeRegistry() {
    }

    /**
     * Adds a trade to a career's pool at one level.
     *
     * @param careerKey {@code "profession registry name/career name"}, such as {@code "minecraft:farmer/fisherman"}
     * @param level     1 (novice) to 5 (master)
     */
    public static synchronized void addVillagerTrade(String careerKey, int level, VillagerTrade trade) {
        if (level < 1 || level > 5) {
            throw new IllegalArgumentException("Villager levels are 1 to 5, not " + level);
        }
        List<List<VillagerTrade>> levels = VILLAGER.get(careerKey);
        if (levels == null) {
            levels = new ArrayList<List<VillagerTrade>>();
            for (int i = 0; i < 5; i++) {
                levels.add(new ArrayList<VillagerTrade>());
            }
            VILLAGER.put(careerKey, levels);
        }
        levels.get(level - 1).add(trade);
    }

    /** @return the trades other mods added to a career at one level, in the order they were added */
    public static synchronized List<VillagerTrade> villagerTrades(String careerKey, int level) {
        List<List<VillagerTrade>> levels = VILLAGER.get(careerKey);
        if (levels == null || level < 1 || level > 5) {
            return Collections.emptyList();
        }
        return new ArrayList<VillagerTrade>(levels.get(level - 1));
    }

    /** Adds a trade to one of the wandering trader's pools. */
    public static synchronized void addTraderTrade(TraderPool pool, TraderTrade trade) {
        List<TraderTrade> list = TRADER.get(pool);
        if (list == null) {
            list = new ArrayList<TraderTrade>();
            TRADER.put(pool, list);
        }
        list.add(trade);
    }

    /** @return the trades other mods added to one of the trader's pools, in the order they were added */
    public static synchronized List<TraderTrade> traderTrades(TraderPool pool) {
        List<TraderTrade> list = TRADER.get(pool);
        return list == null ? Collections.<TraderTrade>emptyList() : new ArrayList<TraderTrade>(list);
    }
}

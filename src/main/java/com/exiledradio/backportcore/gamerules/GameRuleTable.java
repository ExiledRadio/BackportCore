package com.exiledradio.backportcore.gamerules;

import net.minecraft.world.GameRules;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the Game Rules screen knows of each rule 1.12.2 or a backport of the family registers: its category and sort
 * id as 26.3 has them (26.3's {@code GameRules} and {@code GameRuleRegistryFix}), the lang key of its 26.3 name, its
 * bounds, and whether 26.3 turned it around ({@code disableElytraMovementCheck} became {@code elytra_movement_check},
 * {@code disableRaids} became {@code raids}: the screen shows 26.3's name and the opposite value). A rule the table
 * does not know is shown under Miscellaneous by its own id (or {@code gamerule.<id>} when a lang file names it).
 * Since Core 1.4.4.
 */
public final class GameRuleTable {

    /** 26.3's {@code GameRuleCategory}, in 26.3's order (sorted by id: chat, drops, misc, mobs, player, spawning, updates). */
    public enum Category {
        CHAT("chat"), DROPS("drops"), MISC("misc"), MOBS("mobs"), PLAYER("player"), SPAWNING("spawning"), UPDATES("updates");

        public final String id;

        Category(String id) {
            this.id = id;
        }

        /** 26.3's {@code gamerule.category.<id>} (a deprecated key 26.3 still translates). */
        public String langKey() {
            return "gamerule.category." + this.id;
        }
    }

    /** One rule. */
    public static final class Rule {
        /** 1.12.2's name, what {@code /gamerule} takes. */
        public final String id;
        /** 26.3's id (snake case), for the order within a category. */
        public final String sortId;
        public final Category category;
        public final boolean numeric;
        public final String defaultValue;
        /** 26.3's name is the opposite of this rule (on means the 1.12.2 rule is false). */
        public final boolean inverted;
        public final String nameKey;
        public final int min;
        public final int max;
        /** The mods that add it (null: 1.12.2's own). */
        @Nullable
        public final String[] owners;

        Rule(String id, String sortId, Category category, boolean numeric, String defaultValue, boolean inverted, @Nullable String nameKey,
                int min, int max, @Nullable String[] owners) {
            this.id = id;
            this.sortId = sortId;
            this.category = category;
            this.numeric = numeric;
            this.defaultValue = defaultValue;
            this.inverted = inverted;
            this.nameKey = nameKey != null ? nameKey : "gamerule." + id;
            this.min = min;
            this.max = max;
            this.owners = owners;
        }

        /** 26.3's {@code <name>.description}. */
        public String descriptionKey() {
            return this.nameKey + ".description";
        }

        /** The value the screen shows, from the rule's own value (turned around for an inverted rule). */
        public String shown(String value) {
            return this.inverted ? String.valueOf(!Boolean.parseBoolean(value)) : value;
        }

        /** The rule's own value, from the value the screen shows. */
        public String stored(String shown) {
            return this.inverted ? String.valueOf(!Boolean.parseBoolean(shown)) : shown;
        }

        /** 26.3's {@code IntegerArgumentType} bounds; booleans take "true" or "false". */
        public boolean valid(String shown) {
            if (!this.numeric) {
                return "true".equals(shown) || "false".equals(shown);
            }
            try {
                int v = Integer.parseInt(shown);
                return v >= this.min && v <= this.max;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }

    private static final String UA = "updateaquaticbackport";
    private static final String BB = "buzzybeesbackport";
    private static final String NU = "netherupdatebackport";
    private static final String CC = "cavesandcliffsbackport";
    private static final String WU = "wildupdatebackport";
    private static final String TT = "trailsandtalesbackport";
    private static final String TR = "trickytrialsbackport";
    private static final String VP = "villagerbackport";
    private static final String GD = "gamedropsbackport";

    private static final Map<String, Rule> RULES = new LinkedHashMap<String, Rule>();

    static {
        // 1.12.2's own (gameLoopFunction, a function name, has no row: 26.3's screen edits booleans and numbers).
        bool("doFireTick", "fire_spread_radius_around_player", Category.UPDATES, true);
        bool("mobGriefing", "mob_griefing", Category.MOBS, true);
        bool("keepInventory", "keep_inventory", Category.PLAYER, false);
        bool("doMobSpawning", "spawn_mobs", Category.SPAWNING, true);
        bool("doMobLoot", "mob_drops", Category.DROPS, true);
        bool("doTileDrops", "block_drops", Category.DROPS, true);
        bool("doEntityDrops", "entity_drops", Category.DROPS, true);
        bool("commandBlockOutput", "command_block_output", Category.CHAT, true);
        bool("naturalRegeneration", "natural_health_regeneration", Category.PLAYER, true);
        bool("doDaylightCycle", "advance_time", Category.UPDATES, true);
        bool("logAdminCommands", "log_admin_commands", Category.CHAT, true);
        bool("showDeathMessages", "show_death_messages", Category.CHAT, true);
        number("randomTickSpeed", "random_tick_speed", Category.UPDATES, 3, 0, Integer.MAX_VALUE);
        bool("sendCommandFeedback", "send_command_feedback", Category.CHAT, true);
        bool("reducedDebugInfo", "reduced_debug_info", Category.MISC, false);
        bool("spectatorsGenerateChunks", "spectators_generate_chunks", Category.PLAYER, true);
        number("spawnRadius", "respawn_radius", Category.PLAYER, 10, 0, Integer.MAX_VALUE);
        add(new Rule("disableElytraMovementCheck", "elytra_movement_check", Category.PLAYER, false, "false", true,
                "gamerule.minecraft.elytra_movement_check", 0, 0, null));
        number("maxEntityCramming", "max_entity_cramming", Category.MOBS, 24, 0, Integer.MAX_VALUE);
        bool("doWeatherCycle", "advance_weather", Category.UPDATES, true);
        bool("doLimitedCrafting", "limited_crafting", Category.PLAYER, false);
        number("maxCommandChainLength", "max_command_sequence_length", Category.MISC, 65536, 0, Integer.MAX_VALUE);
        bool("announceAdvancements", "show_advancement_messages", Category.CHAT, true);
        // The family's.
        bool("doInsomnia", "spawn_phantoms", Category.SPAWNING, true, UA, BB);
        bool("fallDamage", "fall_damage", Category.PLAYER, true, BB);
        bool("fireDamage", "fire_damage", Category.PLAYER, true, BB);
        bool("drowningDamage", "drowning_damage", Category.PLAYER, true, BB);
        bool("doImmediateRespawn", "immediate_respawn", Category.PLAYER, false, BB);
        bool("forgiveDeadPlayers", "forgive_dead_players", Category.MOBS, true, NU);
        bool("universalAnger", "universal_anger", Category.MOBS, false, NU);
        bool("freezeDamage", "freeze_damage", Category.PLAYER, true, CC, GD);
        bool("blockExplosionDropDecay", "block_explosion_drop_decay", Category.DROPS, true, WU);
        bool("mobExplosionDropDecay", "mob_explosion_drop_decay", Category.DROPS, true, WU);
        bool("tntExplosionDropDecay", "tnt_explosion_drop_decay", Category.DROPS, false, WU);
        number("snowAccumulationHeight", "max_snow_accumulation_height", Category.UPDATES, 1, 0, 8, WU);
        bool("waterSourceConversion", "water_source_conversion", Category.UPDATES, true, WU);
        bool("lavaSourceConversion", "lava_source_conversion", Category.UPDATES, false, WU);
        bool("globalSoundEvents", "global_sound_events", Category.MISC, true, WU);
        number("commandModificationBlockLimit", "max_block_modifications", Category.MISC, 32768, 1, Integer.MAX_VALUE, WU);
        bool("doVinesSpread", "spread_vines", Category.UPDATES, true, WU);
        bool("doWardenSpawning", "spawn_wardens", Category.SPAWNING, true, WU);
        bool("enderPearlsVanishOnDeath", "ender_pearls_vanish_on_death", Category.PLAYER, true, TT);
        bool("projectilesCanBreakBlocks", "projectiles_can_break_blocks", Category.DROPS, true, TT);
        number("playersNetherPortalDefaultDelay", "players_nether_portal_default_delay", Category.PLAYER, 80, 0, Integer.MAX_VALUE, TT);
        number("playersNetherPortalCreativeDelay", "players_nether_portal_creative_delay", Category.PLAYER, 0, 0, Integer.MAX_VALUE, TT);
        // 1.20.5's (gone in 26.1, which keeps its name and description).
        number("spawnChunkRadius", "spawn_chunk_radius", Category.MISC, 2, 0, 32, TT);
        bool("locatorBar", "locator_bar", Category.PLAYER, true, TR);
        bool("tntExplodes", "tnt_explodes", Category.MISC, true, TR);
        // 1.21.2's (26.1 folded it into fire_spread_radius_around_player).
        bool("allowFireTicksAwayFromPlayer", "spread_fire_far_from_player", Category.UPDATES, false, TR);
        bool("allowEnteringNetherUsingPortals", "allow_entering_nether_using_portals", Category.MISC, true, TR);
        bool("spawnMonsters", "spawn_monsters", Category.SPAWNING, true, TR);
        bool("pvp", "pvp", Category.PLAYER, true, TR);
        bool("commandBlocksEnabled", "command_blocks_work", Category.MISC, true, TR);
        bool("spawnerBlocksEnabled", "spawner_blocks_work", Category.MISC, true, TR);
        add(new Rule("disableRaids", "raids", Category.MOBS, false, "false", true, "gamerule.minecraft.raids", 0, 0, new String[] {VP}));
        bool("doTraderSpawning", "spawn_wandering_traders", Category.SPAWNING, true, VP);
    }

    private GameRuleTable() {
    }

    private static void bool(String id, String sortId, Category category, boolean def, String... owners) {
        add(new Rule(id, sortId, category, false, String.valueOf(def), false, null, 0, 0, owners.length == 0 ? null : owners));
    }

    private static void number(String id, String sortId, Category category, int def, int min, int max, String... owners) {
        add(new Rule(id, sortId, category, true, String.valueOf(def), false, null, min, max, owners.length == 0 ? null : owners));
    }

    private static void add(Rule rule) {
        RULES.put(rule.id, rule);
    }

    /** @return the table's rule, or null for one it does not know */
    @Nullable
    public static Rule known(String id) {
        return RULES.get(id);
    }

    /** @return every rule the table knows, 1.12.2's first */
    public static List<Rule> all() {
        return Collections.unmodifiableList(new ArrayList<Rule>(RULES.values()));
    }

    /**
     * A rule the table does not know, as a server reported it: Miscellaneous, its own id, 1.12.2's bounds for a number.
     */
    public static Rule unknown(String id, boolean numeric, String value) {
        Rule known = RULES.get(id);
        if (known != null) {
            return known;
        }
        return new Rule(id, id.toLowerCase(Locale.ROOT), Category.MISC, numeric, value, false, null, Integer.MIN_VALUE, Integer.MAX_VALUE, null);
    }

    /**
     * 1.12.2's value type of a rule, as the screen edits it: a boolean, a number, or neither (null: no row). A rule
     * created by {@code /gamerule} has no type (1.12.2's {@code ANY_VALUE}); its value decides.
     */
    @Nullable
    public static Boolean numeric(GameRules rules, String id) {
        if (rules.areSameType(id, GameRules.ValueType.BOOLEAN_VALUE)) {
            return Boolean.FALSE;
        }
        if (rules.areSameType(id, GameRules.ValueType.NUMERICAL_VALUE)) {
            return Boolean.TRUE;
        }
        String v = rules.getString(id);
        if ("true".equals(v) || "false".equals(v)) {
            return Boolean.FALSE;
        }
        try {
            Integer.parseInt(v);
            return Boolean.TRUE;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

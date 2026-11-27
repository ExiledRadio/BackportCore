package com.exiledradio.backportcore.advancement;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.locate.BiomeLocate;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.terrain.ModernGeneration;
import com.exiledradio.backportcore.worldgen.UndergroundBiomes;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.ICriterionTrigger;
import net.minecraft.advancements.PlayerAdvancements;
import net.minecraft.advancements.critereon.AbstractCriterionInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Adventuring Time ({@code minecraft:adventure/adventuring_time}) with the biomes vanilla's later lists name, as far as
 * the installed backports bring them. T&amp;T Backport's Core request 11.2. No patch: the advancement is changed in
 * place after each load of the advancements (the server's start, {@code /reload}), and the online players' progress is
 * read again.
 *
 * <h2>Which biomes</h2>
 * Vanilla's lists by today's ids ({@link #listAt}): 1.13 added the warm, lukewarm and cold oceans and the deep frozen
 * ocean, 1.14 the bamboo jungle, 1.18 replaced the whole list with its 50 overworld biomes, 1.19 added the deep dark and
 * the mangrove swamp, 1.20 the cherry grove, 1.21.4 the pale garden, 26.2 the sulfur caves, 26.3 the dappled forest and
 * 26.4 the ice caves (the files in each version's jar; 26.4 from Pre-Release 1). The list is
 * the one at the newest installed version ({@link Presentation#newest()}).
 * <ul>
 *   <li>In a world whose overworld runs a newer version's generation ({@link ModernGeneration}, Caves &amp; Cliffs
 *       Backport's 1.18 terrain), the advancement is that list, every criterion by today's id: the biomes of that
 *       version's generation, and the later ones the generator says it makes ({@link ModernGeneration#generates}).</li>
 *   <li>In 1.12.2's terrain, 1.12.2's own 36 criteria stay, and each later biome of the list that a backport registers
 *       (any 1.12 biome {@link BiomeLocate#id} names by that id that is not one of vanilla's) is added: Update Aquatic
 *       Backport's oceans, Village &amp; Pillage's bamboo jungle, Wild Update's deep dark and mangrove swamp, the cherry
 *       grove, the pale garden, Game Drops' sulfur caves, dappled forest and ice caves. Other mods' biomes never count,
 *       as in vanilla.</li>
 * </ul>
 * The added criteria are Core's trigger {@code backportcore:biome} ({@code {"biome": "minecraft:cherry_grove"}}), met
 * where the player stands, checked every 20 ticks as vanilla's {@code location}: by an underground biome a backport
 * registered there ({@link UndergroundBiomes}: the sulfur and ice caves under 1.12.2's columns), else by the generator's
 * 3D biome in a modern world (a lush cave under a forest is a lush cave), else by the column's 1.12 biome.
 */
public final class AdventuringTime {

    public static final ResourceLocation ID = new ResourceLocation("adventure/adventuring_time");

    /** 1.18.2's list (its 50 overworld biomes). */
    static final List<String> LIST_118 = Collections.unmodifiableList(Arrays.asList(
            "badlands", "bamboo_jungle", "beach", "birch_forest", "cold_ocean", "dark_forest", "deep_cold_ocean",
            "deep_frozen_ocean", "deep_lukewarm_ocean", "deep_ocean", "desert", "dripstone_caves", "eroded_badlands",
            "flower_forest", "forest", "frozen_ocean", "frozen_peaks", "frozen_river", "grove", "ice_spikes", "jagged_peaks",
            "jungle", "lukewarm_ocean", "lush_caves", "meadow", "mushroom_fields", "ocean", "old_growth_birch_forest",
            "old_growth_pine_taiga", "old_growth_spruce_taiga", "plains", "river", "savanna", "savanna_plateau", "snowy_beach",
            "snowy_plains", "snowy_slopes", "snowy_taiga", "sparse_jungle", "stony_peaks", "stony_shore", "sunflower_plains",
            "swamp", "taiga", "warm_ocean", "windswept_forest", "windswept_gravelly_hills", "windswept_hills",
            "windswept_savanna", "wooded_badlands"));

    /** What each version added: {version, ids...}. 1.18 replaced the list (with {@link #LIST_118}). */
    static final String[][] ADDED = {
            {"1.13", "warm_ocean", "lukewarm_ocean", "cold_ocean", "deep_lukewarm_ocean", "deep_cold_ocean", "deep_frozen_ocean"},
            {"1.14", "bamboo_jungle"},
            {"1.19", "deep_dark", "mangrove_swamp"},
            {"1.20", "cherry_grove"},
            {"1.21.4", "pale_garden"},
            {"26.2", "sulfur_caves"},
            {"26.3", "dappled_forest"},
            {"26.4", "ice_caves"},
    };

    /** The trigger of the added criteria. */
    public static final Trigger TRIGGER = new Trigger();

    /** For the self-tests: the criteria the last change added, and how many times the advancement was changed. */
    public static volatile List<String> lastAdded = Collections.emptyList();
    public static volatile int applied;

    @Nullable
    private static Advancement changed;

    private AdventuringTime() {
    }

    /** From Core's pre-init: the trigger joins vanilla's, so a player's listeners find it. */
    public static void register() {
        try {
            Method register = ReflectionHelper.findMethod(CriteriaTriggers.class, "register", "func_192118_a", ICriterionTrigger.class);
            register.invoke(null, TRIGGER);
        } catch (Exception e) {
            BackportCore.LOGGER.error("Adventuring Time: the backportcore:biome trigger could not be registered; the backports' biomes will not count", e);
        }
    }

    /** @return vanilla's Adventuring Time at a version, by today's ids without namespace, sorted (empty below 1.13) */
    public static List<String> listAt(GameVersion version) {
        Set<String> out = new TreeSet<String>();
        if (version.isAtLeast("1.18")) {
            out.addAll(LIST_118);
        }
        for (String[] row : ADDED) {
            if (version.isAtLeast(row[0])) {
                out.addAll(Arrays.asList(row).subList(1, row.length));
            }
        }
        return new ArrayList<String>(out);
    }

    /** @return the version that first listed a biome (today's id without namespace), or null for one never added after 1.12.2 */
    @Nullable
    public static String since(String id) {
        for (String[] row : ADDED) {
            if (Arrays.asList(row).subList(1, row.length).contains(id)) {
                return row[0];
            }
        }
        return LIST_118.contains(id) ? "1.18" : null;
    }

    /** @return today's ids (with namespace) of the biomes the backports register, other than vanilla's own */
    public static Set<String> backportBiomes() {
        Set<String> out = new HashSet<String>();
        for (Biome biome : ForgeRegistries.BIOMES) {
            ResourceLocation name = biome.getRegistryName();
            if (name != null && !"minecraft".equals(name.getNamespace())) {
                out.add(BiomeLocate.id(biome));
            }
        }
        return out;
    }

    /**
     * The criteria to add to 1.12.2's in its own terrain: the list at {@code newest} that the base does not already have,
     * that a backport's biome answers.
     */
    public static List<String> additions(GameVersion newest, Set<String> registered, Set<String> base) {
        List<String> out = new ArrayList<String>();
        for (String id : listAt(newest)) {
            String full = "minecraft:" + id;
            if (registered.contains(full) && !base.contains(full)) {
                out.add(full);
            }
        }
        return out;
    }

    /**
     * The whole list for a world of a newer version's generation: that version's list, then each biome later versions
     * added that the generator makes and a backport registers.
     */
    public static List<String> modernList(GameVersion newest, ModernGeneration generation, Set<String> registered) {
        GameVersion own = GameVersion.parse(generation.generationVersion());
        List<String> out = new ArrayList<String>();
        for (String id : listAt(newest.compareTo(own) > 0 ? newest : own)) {
            String full = "minecraft:" + id;
            String since = since(id);
            boolean inOwn = since == null || own.isAtLeast(since);
            if (inOwn || registered.contains(full) && generation.generates(full)) {
                out.add(full);
            }
        }
        return out;
    }

    /** Changes the loaded advancement if it has not been already (once after each load). */
    static void update(MinecraftServer server) {
        if (server.worlds == null || server.worlds.length == 0 || server.worlds[0] == null) {
            return;
        }
        Advancement advancement = server.getAdvancementManager().getAdvancement(ID);
        if (advancement == null || advancement == changed) {
            return;
        }
        changed = advancement;
        if (!Presentation.installedNewest().isAtLeast("1.13")) {
            lastAdded = Collections.emptyList();
            return;
        }
        WorldServer overworld = server.worlds[0];
        ModernGeneration generation = ModernGeneration.of(overworld);
        Set<String> registered = backportBiomes();
        Map<String, Criterion> criteria = new LinkedHashMap<String, Criterion>();
        List<String> added;
        if (generation != null) {
            added = modernList(Presentation.installedNewest(), generation, registered);
        } else {
            criteria.putAll(advancement.getCriteria());
            Set<String> base = new HashSet<String>();
            for (String name : criteria.keySet()) {
                Biome biome = ForgeRegistries.BIOMES.getValue(new ResourceLocation(name));
                if (biome != null) {
                    base.add(BiomeLocate.id(biome));
                }
            }
            added = additions(Presentation.installedNewest(), registered, base);
            if (added.isEmpty()) {
                lastAdded = Collections.emptyList();
                return;
            }
        }
        if (!change(server, advancement, criteria, added)) {
            return;
        }
        lastAdded = Collections.unmodifiableList(new ArrayList<String>(added));
        applied++;
        BackportCore.LOGGER.info("Adventuring Time: {} biomes ({} {}){}", criteria.size(), added.size(),
                generation != null ? "by today's ids, " + generation.generationVersion() + " generation" : "added to 1.12.2's",
                added.size() <= 8 ? ": " + added : "");
    }

    /**
     * Adds criteria of Core's trigger to the loaded advancement and reads the online players' progress again (for the
     * self-tests: a world without the backport whose biome it is).
     */
    public static boolean extend(MinecraftServer server, String... ids) {
        Advancement advancement = server.getAdvancementManager().getAdvancement(ID);
        if (advancement == null) {
            return false;
        }
        Map<String, Criterion> criteria = new LinkedHashMap<String, Criterion>(advancement.getCriteria());
        return change(server, advancement, criteria, Arrays.asList(ids));
    }

    private static boolean change(MinecraftServer server, Advancement advancement, Map<String, Criterion> criteria, List<String> added) {
        for (String id : added) {
            criteria.put(id, new Criterion(new Instance(id)));
        }
        String[][] requirements = new String[criteria.size()][];
        int i = 0;
        for (String name : criteria.keySet()) {
            requirements[i++] = new String[] {name};
        }
        try {
            set(advancement, "criteria", "field_192080_e", criteria);
            set(advancement, "requirements", "field_192081_f", requirements);
        } catch (Exception e) {
            BackportCore.LOGGER.error("Adventuring Time: could not change the advancement; it keeps 1.12.2's biomes", e);
            return false;
        }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            player.getAdvancements().reload();
        }
        return true;
    }

    /** Sets a final instance field (accessible fields of that kind can be written, on Java 8 and on Cleanroom's). */
    private static void set(Advancement advancement, String mcp, String srg, Object value) throws Exception {
        Field f = ReflectionHelper.findField(Advancement.class, mcp, srg);
        f.set(advancement, value);
    }

    /** @return today's id of the biome a player stands in: the generator's 3D biome in a modern world, else the column's */
    public static String biomeAt(World world, BlockPos pos) {
        String modern = ModernGeneration.biomeId(world, pos);
        return modern != null ? modern : BiomeLocate.id(world.getBiome(pos));
    }

    /** @return today's id of the biome the criteria read at a block: an underground biome a backport registered, else {@link #biomeAt} */
    public static String standingIn(World world, BlockPos pos) {
        Biome under = UndergroundBiomes.at(world, pos);
        return under != null ? BiomeLocate.id(under) : biomeAt(world, pos);
    }

    /** The criterion: a biome by today's id. */
    public static final class Instance extends AbstractCriterionInstance {
        public final String biome;

        public Instance(String biome) {
            super(Trigger.ID);
            this.biome = biome;
        }
    }

    /** {@code backportcore:biome}: met by standing in the biome. */
    public static final class Trigger implements ICriterionTrigger<Instance> {
        static final ResourceLocation ID = new ResourceLocation("backportcore", "biome");
        private final Map<PlayerAdvancements, Set<Listener<Instance>>> listeners = new HashMap<PlayerAdvancements, Set<Listener<Instance>>>();

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        public void addListener(PlayerAdvancements advancements, Listener<Instance> listener) {
            Set<Listener<Instance>> set = this.listeners.get(advancements);
            if (set == null) {
                set = new LinkedHashSet<Listener<Instance>>();
                this.listeners.put(advancements, set);
            }
            set.add(listener);
        }

        @Override
        public void removeListener(PlayerAdvancements advancements, Listener<Instance> listener) {
            Set<Listener<Instance>> set = this.listeners.get(advancements);
            if (set != null) {
                set.remove(listener);
                if (set.isEmpty()) {
                    this.listeners.remove(advancements);
                }
            }
        }

        @Override
        public void removeAllListeners(PlayerAdvancements advancements) {
            this.listeners.remove(advancements);
        }

        @Override
        public Instance deserializeInstance(JsonObject json, JsonDeserializationContext context) {
            String biome = JsonUtils.getString(json, "biome");
            return new Instance(biome.indexOf(':') < 0 ? "minecraft:" + biome : biome);
        }

        /** Grants every criterion of this player's whose biome is {@code id}. */
        public void trigger(EntityPlayerMP player, String id) {
            Set<Listener<Instance>> set = this.listeners.get(player.getAdvancements());
            if (set == null) {
                return;
            }
            List<Listener<Instance>> met = new ArrayList<Listener<Instance>>();
            for (Listener<Instance> l : set) {
                if (l.getCriterionInstance().biome.equals(id)) {
                    met.add(l);
                }
            }
            for (Listener<Instance> l : met) {
                l.grantCriterion(player.getAdvancements());
            }
        }

        boolean listening(EntityPlayerMP player) {
            return this.listeners.containsKey(player.getAdvancements());
        }
    }

    /** Forge event handlers; public for the event bus. */
    public static final class Events {

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.START) {
                MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
                if (server != null) {
                    update(server);
                }
            }
        }

        @SubscribeEvent
        public void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP) || event.player.ticksExisted % 20 != 0) {
                return;
            }
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            if (TRIGGER.listening(player)) {
                TRIGGER.trigger(player, standingIn(player.world, new BlockPos(player)));
            }
        }
    }
}

package com.exiledradio.backportcore.advancement;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.EffectsChangedTrigger;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.MobEffectsPredicate;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.potion.Potion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * How Did We Get Here? ({@code minecraft:nether/all_effects}) with the effects vanilla's later lists name, as far as the
 * installed backports bring them. No patch: the advancement's one criterion is replaced after each load of the
 * advancements (the server's start, {@code /reload}), and the online players' progress is read again.
 *
 * <h2>Which effects</h2>
 * 1.12.2 asks for 20 effects ({@link #BASE}). Today's list adds (the wiki's history; Dolphin's Grace and Hero of the
 * Village are in today's list with no entry of their own, so they count from the version that added the effect):
 * <ul>
 *   <li>1.13: Slow Falling, Conduit Power, Dolphin's Grace;</li>
 *   <li>1.14: Blindness, Bad Omen, Hero of the Village (1.14.3 dropped Saturation again, so it is never asked);</li>
 *   <li>1.19: Darkness; 1.21: Infested, Oozing, Raid Omen, Trial Omen, Weaving, Wind Charged; 1.21.11: Breath of the
 *       Nautilus; 26.4: Freezing.</li>
 * </ul>
 * The list is the one at the newest installed version ({@link Presentation#installedNewest()}), and of it only the
 * effects that are in this game: vanilla's own (Blindness from 1.14) and each one a backport names with
 * {@link #register}. An effect whose backport is not installed is never asked for, so the advancement stays possible.
 * Other mods' effects never count, as in vanilla.
 */
public final class AllEffects {

    public static final ResourceLocation ID = new ResourceLocation("nether/all_effects");
    /** The criterion's name, vanilla's. */
    public static final String CRITERION = "all_effects";

    /** 1.12.2's 20, by id without namespace. */
    static final List<String> BASE = Collections.unmodifiableList(Arrays.asList(
            "speed", "slowness", "strength", "jump_boost", "regeneration", "fire_resistance", "water_breathing", "invisibility",
            "night_vision", "weakness", "poison", "wither", "haste", "mining_fatigue", "levitation", "glowing", "absorption",
            "hunger", "nausea", "resistance"));

    /** What each version added: {version, ids...}. */
    static final String[][] ADDED = {
            {"1.13", "slow_falling", "conduit_power", "dolphins_grace"},
            {"1.14", "blindness", "bad_omen", "hero_of_the_village"},
            {"1.19", "darkness"},
            {"1.21", "infested", "oozing", "raid_omen", "trial_omen", "weaving", "wind_charged"},
            {"1.21.11", "breath_of_the_nautilus"},
            {"26.4", "freezing"},
    };

    /** Today's id ("minecraft:darkness") -> the backport's effect. */
    private static final Map<String, Supplier<Potion>> REGISTERED = new ConcurrentHashMap<String, Supplier<Potion>>();

    /** For the self-tests: the effects the last change asked for, and how many times the advancement was changed. */
    public static volatile List<String> lastRequired = Collections.emptyList();
    public static volatile int applied;
    /** For the self-tests: the predicate the last change put in. */
    @Nullable
    public static volatile MobEffectsPredicate lastPredicate;

    @Nullable
    private static Advancement changed;

    private AllEffects() {
    }

    /**
     * Names a backport's effect as one of vanilla's later ones, so How Did We Get Here? asks for it while that backport
     * is installed. Call it in pre-init (the effect is read when the advancements load).
     *
     * @param id     today's id, {@code "minecraft:darkness"} or {@code "darkness"}
     * @param effect the backport's effect; null or unregistered means not in this game
     */
    public static void register(String id, Supplier<Potion> effect) {
        String full = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        if (since(full.substring(full.indexOf(':') + 1)) == null) {
            BackportCore.LOGGER.warn("How Did We Get Here?: {} is not one of vanilla's later effects; ignored", full);
            return;
        }
        REGISTERED.put(full, effect);
        changed = null;
    }

    /** Takes back a {@link #register}ed effect (for the self-tests); the advancement changes at its next load. */
    public static void unregister(String id) {
        REGISTERED.remove(id.indexOf(':') < 0 ? "minecraft:" + id : id);
        changed = null;
    }

    /** {@link #register(String, Supplier)} by the effect's own name ({@code wildupdatebackport:darkness} is Darkness). */
    public static void register(final Potion effect) {
        ResourceLocation name = effect.getRegistryName();
        if (name != null) {
            register(name.getPath(), () -> effect);
        }
    }

    /** @return the version whose list first named an effect (id without namespace), or null for 1.12.2's and others */
    @Nullable
    public static String since(String id) {
        for (String[] row : ADDED) {
            if (Arrays.asList(row).subList(1, row.length).contains(id)) {
                return row[0];
            }
        }
        return null;
    }

    /** @return vanilla's list at a version, by id without namespace: 1.12.2's, then each later version's additions */
    public static List<String> listAt(GameVersion version) {
        List<String> out = new ArrayList<String>(BASE);
        for (String[] row : ADDED) {
            if (version.isAtLeast(row[0])) {
                out.addAll(Arrays.asList(row).subList(1, row.length));
            }
        }
        return out;
    }

    /** @return the effect in this game for today's id: a backport's registered one, else vanilla's own, else null */
    @Nullable
    public static Potion effect(String id) {
        String full = "minecraft:" + id;
        Supplier<Potion> s = REGISTERED.get(full);
        Potion p = s == null ? null : s.get();
        if (p != null && p.getRegistryName() != null && ForgeRegistries.POTIONS.containsValue(p)) {
            return p;
        }
        // Vanilla's own: 1.12.2's 20 and Blindness. No later effect is registered under minecraft: in 1.12.2.
        return ForgeRegistries.POTIONS.getValue(new ResourceLocation(full));
    }

    /** @return the effects the advancement asks for at a version, by today's id, each with the effect in this game */
    public static Map<String, Potion> required(GameVersion version) {
        Map<String, Potion> out = new LinkedHashMap<String, Potion>();
        for (String id : listAt(version)) {
            Potion p = effect(id);
            if (p != null) {
                out.put("minecraft:" + id, p);
            }
        }
        return out;
    }

    /** Changes the loaded advancement if it has not been already (once after each load). */
    static void update(MinecraftServer server) {
        Advancement advancement = server.getAdvancementManager().getAdvancement(ID);
        if (advancement == null || advancement == changed) {
            return;
        }
        changed = advancement;
        Map<String, Potion> required = required(Presentation.installedNewest());
        if (required.size() <= BASE.size()) {
            lastRequired = Collections.unmodifiableList(new ArrayList<String>(required.keySet()));
            return;
        }
        if (!change(server, advancement, required.values())) {
            return;
        }
        lastRequired = Collections.unmodifiableList(new ArrayList<String>(required.keySet()));
        applied++;
        BackportCore.LOGGER.info("How Did We Get Here?: {} effects ({} added to 1.12.2's)", required.size(), required.size() - BASE.size());
    }

    /** Replaces the criterion with one asking for these effects and reads the online players' progress again. */
    public static boolean change(MinecraftServer server, Advancement advancement, Iterable<Potion> effects) {
        Map<Potion, MobEffectsPredicate.InstancePredicate> map = new LinkedHashMap<Potion, MobEffectsPredicate.InstancePredicate>();
        for (Potion p : effects) {
            map.put(p, new MobEffectsPredicate.InstancePredicate(MinMaxBounds.UNBOUNDED, MinMaxBounds.UNBOUNDED, null, null));
        }
        MobEffectsPredicate predicate = new MobEffectsPredicate(map);
        Map<String, Criterion> criteria = new LinkedHashMap<String, Criterion>();
        criteria.put(CRITERION, new Criterion(new EffectsChangedTrigger.Instance(predicate)));
        try {
            Field f = ReflectionHelper.findField(Advancement.class, "criteria", "field_192080_e");
            f.set(advancement, criteria);
            Field r = ReflectionHelper.findField(Advancement.class, "requirements", "field_192081_f");
            r.set(advancement, new String[][] {{CRITERION}});
        } catch (Exception e) {
            BackportCore.LOGGER.error("How Did We Get Here?: could not change the advancement; it keeps 1.12.2's effects", e);
            return false;
        }
        lastPredicate = predicate;
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            player.getAdvancements().reload();
        }
        return true;
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
    }
}

package com.exiledradio.backportcore.advancement;

import com.exiledradio.backportcore.BackportCore;
import com.google.gson.JsonObject;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.ICriterionInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Monster Hunter ({@code minecraft:adventure/kill_a_mob}) and Monsters Hunted ({@code minecraft:adventure/kill_all_mobs})
 * with the hostile mobs the backports add (Core 1.5.0; 26.4's frostbite is the first). No patch: as
 * {@link AdventuringTime}, the loaded advancements are changed in place after each load and the online players'
 * progress is read again.
 *
 * <p>Each added mob gets vanilla's criterion: {@code minecraft:player_killed_entity} with {@code entity.type} its id,
 * named by the id. Monster Hunter has one requirement that any of its criteria meets, and the mob joins it; Monsters
 * Hunted needs every criterion, and the mob gets its own requirement.
 */
public final class MonsterHunter {

    public static final ResourceLocation KILL_A_MOB = new ResourceLocation("adventure/kill_a_mob");
    public static final ResourceLocation KILL_ALL_MOBS = new ResourceLocation("adventure/kill_all_mobs");

    private static final Set<ResourceLocation> MOBS = new LinkedHashSet<ResourceLocation>();
    @Nullable
    private static Advancement changedOne, changedAll;

    private MonsterHunter() {
    }

    /** A backport's hostile mob, by its entity id; from pre-init or init. */
    public static synchronized void add(ResourceLocation entity) {
        MOBS.add(entity);
    }

    public static synchronized List<ResourceLocation> mobs() {
        return new ArrayList<ResourceLocation>(MOBS);
    }

    static void update(MinecraftServer server) {
        if (server.worlds == null || server.worlds.length == 0 || server.worlds[0] == null || MOBS.isEmpty()) {
            return;
        }
        boolean changed = false;
        Advancement one = server.getAdvancementManager().getAdvancement(KILL_A_MOB);
        if (one != null && one != changedOne) {
            changedOne = one;
            changed |= change(one, false);
        }
        Advancement all = server.getAdvancementManager().getAdvancement(KILL_ALL_MOBS);
        if (all != null && all != changedAll) {
            changedAll = all;
            changed |= change(all, true);
        }
        if (changed) {
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                player.getAdvancements().reload();
            }
        }
    }

    private static boolean change(Advancement advancement, boolean every) {
        Map<String, Criterion> criteria = new LinkedHashMap<String, Criterion>(advancement.getCriteria());
        List<String[]> requirements = new ArrayList<String[]>(Arrays.asList(advancement.getRequirements()));
        List<String> added = new ArrayList<String>();
        for (ResourceLocation mob : mobs()) {
            String name = mob.toString();
            if (criteria.containsKey(name) || !EntityList.isRegistered(mob)) {
                continue;
            }
            criteria.put(name, new Criterion(killed(mob)));
            added.add(name);
        }
        if (added.isEmpty()) {
            return false;
        }
        if (every || requirements.isEmpty()) {
            for (String name : added) {
                requirements.add(new String[] {name});
            }
        } else {
            List<String> any = new ArrayList<String>(Arrays.asList(requirements.get(0)));
            any.addAll(added);
            requirements.set(0, any.toArray(new String[0]));
        }
        try {
            set(advancement, "criteria", "field_192080_e", criteria);
            set(advancement, "requirements", "field_192081_f", requirements.toArray(new String[0][]));
        } catch (Exception e) {
            BackportCore.LOGGER.error("Monster Hunter: {} could not be changed", advancement.getId(), e);
            return false;
        }
        BackportCore.LOGGER.info("Monster Hunter: {} gains {}", advancement.getId(), added);
        return true;
    }

    /** {@code {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": {"type": id}}}}. */
    private static ICriterionInstance killed(ResourceLocation mob) {
        JsonObject entity = new JsonObject();
        entity.addProperty("type", mob.toString());
        JsonObject conditions = new JsonObject();
        conditions.add("entity", entity);
        return CriteriaTriggers.PLAYER_KILLED_ENTITY.deserializeInstance(conditions, null);
    }

    private static void set(Advancement advancement, String mcp, String srg, Object value) throws Exception {
        Field f = ReflectionHelper.findField(Advancement.class, mcp, srg);
        f.set(advancement, value);
    }

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

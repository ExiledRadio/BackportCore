package com.exiledradio.backportcore.advancement;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.ownership.BlockOwnership;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.supply.SuppliedTable;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.ICriterionInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stone Age ({@code minecraft:story/mine_stone}) with today's {@code #stone_tool_materials}: Cobblestone, Blackstone
 * and Cobbled Deepslate, one of them in the inventory (Core 1.5.0). No patch: as {@link MonsterHunter}, the loaded
 * advancement is changed in place after each load (the server's start, {@code /reload}) and the online players'
 * progress is read again.
 *
 * <ul>
 *   <li>Blackstone from 1.16 (20w20a: "Obtaining blackstone now counts for the Stone Age advancement");</li>
 *   <li>Cobbled Deepslate from 1.17, the version that added it to {@code #stone_tool_materials} with the block.</li>
 * </ul>
 * The version is the newest installed backport's ({@link Presentation#installedNewest()}). Each block counts by
 * whichever mod registered it: its owner (Nether Update; Caves &amp; Cliffs), a backport's own copy (Wild Update's
 * cobbled deepslate), or Core's supplied copy ({@code backportcore:<name>}). Every registered one is added, so the one
 * a world has counts.
 *
 * <p>Each alternative is vanilla's {@code minecraft:inventory_changed} criterion with the item, named by the item's
 * registry name, and joins the requirement holding {@code get_stone}: any one of them does. Nether Update adds its own
 * blackstone the same way ({@code changes.VanillaAdvancements}), under the same name ({@code netherupdatebackport:blackstone});
 * whichever comes first adds it and the other finds it there, so it is never there twice.
 */
public final class StoneAge {

    public static final ResourceLocation ID = new ResourceLocation("story/mine_stone");
    /** Vanilla 1.12.2's criterion, cobblestone in the inventory. */
    public static final String GET_STONE = "get_stone";

    /** {version, block path, the mods that may register it}: today's {@code #stone_tool_materials} after 1.12.2. */
    static final String[][] ADDED = {
            {"1.16", "blackstone", "netherupdatebackport", BackportCore.MOD_ID},
            {"1.17", "cobbled_deepslate", "cavesandcliffsbackport", "wildupdatebackport", BackportCore.MOD_ID},
    };

    /** For the self-tests: the criteria the last change added, and how many times the advancement was changed. */
    public static volatile List<String> lastAdded = new ArrayList<String>();
    public static volatile int applied;

    @Nullable
    private static Advancement changed;

    private StoneAge() {
    }

    /** @return the block paths today's tag adds to cobblestone at a version */
    public static List<String> pathsAt(GameVersion version) {
        List<String> out = new ArrayList<String>();
        for (String[] row : ADDED) {
            if (version.isAtLeast(row[0])) {
                out.add(row[1]);
            }
        }
        return out;
    }

    /** @return the version whose tag first held a block path, or null when it is not one of these */
    @Nullable
    public static String since(String path) {
        for (String[] row : ADDED) {
            if (row[1].equals(path)) {
                return row[0];
            }
        }
        return null;
    }

    /**
     * @return every registered item of these blocks at a version, by registry name: the owners' (the ownership table's
     * or the supplied table's owner as well as the listed mods), any backport's copy, Core's supplied copy
     */
    public static Map<String, Item> items(GameVersion version) {
        Map<String, Item> out = new LinkedHashMap<String, Item>();
        for (String[] row : ADDED) {
            if (!version.isAtLeast(row[0])) {
                continue;
            }
            String path = row[1];
            Set<String> mods = new LinkedHashSet<String>();
            String owner = BlockOwnership.owner(path);
            if (owner != null && owner.indexOf(':') < 0) {
                mods.add(owner);
            }
            SuppliedTable.Row supplied = SuppliedTable.rows().get(path);
            if (supplied != null && supplied.owner != null) {
                mods.add(supplied.owner);
            }
            mods.addAll(Arrays.asList(row).subList(2, row.length));
            for (String mod : mods) {
                String name = supplied != null && mod.equals(supplied.owner) ? supplied.ownerName : path;
                ResourceLocation id = new ResourceLocation(mod, name);
                Item item = ForgeRegistries.ITEMS.containsKey(id) ? ForgeRegistries.ITEMS.getValue(id) : null;
                if (item != null && item != Items.AIR) {
                    out.put(id.toString(), item);
                }
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
        Map<String, Item> items = items(Presentation.installedNewest());
        if (items.isEmpty() || !change(advancement, items.keySet())) {
            return;
        }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            player.getAdvancements().reload();
        }
    }

    /**
     * Adds an {@code inventory_changed} criterion for each item not there yet, as an alternative to {@code get_stone}.
     *
     * @param items item registry names
     * @return true if any criterion was added
     */
    public static boolean change(Advancement advancement, Iterable<String> items) {
        Map<String, Criterion> criteria = new LinkedHashMap<String, Criterion>(advancement.getCriteria());
        List<String[]> requirements = new ArrayList<String[]>(Arrays.asList(advancement.getRequirements()));
        List<String> added = new ArrayList<String>();
        for (String item : items) {
            if (criteria.containsKey(item)) {
                continue;
            }
            criteria.put(item, new Criterion(holding(item)));
            added.add(item);
        }
        if (added.isEmpty()) {
            return false;
        }
        int group = -1;
        for (int i = 0; i < requirements.size(); i++) {
            if (Arrays.asList(requirements.get(i)).contains(GET_STONE)) {
                group = i;
                break;
            }
        }
        if (group < 0) {
            // A data pack's Stone Age without vanilla's criterion: the items make one requirement of their own.
            requirements.add(added.toArray(new String[0]));
        } else {
            List<String> any = new ArrayList<String>(Arrays.asList(requirements.get(group)));
            any.addAll(added);
            requirements.set(group, any.toArray(new String[0]));
        }
        try {
            set(advancement, "criteria", "field_192080_e", criteria);
            set(advancement, "requirements", "field_192081_f", requirements.toArray(new String[0][]));
        } catch (Exception e) {
            BackportCore.LOGGER.error("Stone Age: {} could not be changed", advancement.getId(), e);
            return false;
        }
        lastAdded = added;
        applied++;
        BackportCore.LOGGER.info("Stone Age: {} also counts {}", advancement.getId(), added);
        return true;
    }

    /** {@code {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"item": id}]}}}. */
    static ICriterionInstance holding(String item) {
        JsonObject one = new JsonObject();
        one.addProperty("item", item);
        JsonArray items = new JsonArray();
        items.add(one);
        JsonObject conditions = new JsonObject();
        conditions.add("items", items);
        return CriteriaTriggers.INVENTORY_CHANGED.deserializeInstance(conditions, null);
    }

    private static void set(Advancement advancement, String mcp, String srg, Object value) throws Exception {
        Field f = ReflectionHelper.findField(Advancement.class, mcp, srg);
        f.set(advancement, value);
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

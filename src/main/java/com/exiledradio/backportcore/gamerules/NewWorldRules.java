package com.exiledradio.backportcore.gamerules;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The game rules chosen on Create World (1.16's {@code WorldSettings} carries them; 1.12.2's does not): held from
 * Create until the new world's overworld loads, then set after every backport added its own rules (lowest priority),
 * each only if the world has it. Only a world that has never ticked takes them. Since Core 1.4.4.
 */
public final class NewWorldRules {

    @Nullable
    private static volatile String folder;
    @Nullable
    private static volatile Map<String, String> pending;
    /** For the self-test: the rules the last new world took. */
    public static volatile int lastApplied = -1;

    /** Client side: the world in {@code saves/<folder>} is about to be created with these rules (1.12.2 names). */
    public static void expect(String folder, Map<String, String> rules) {
        NewWorldRules.folder = folder;
        NewWorldRules.pending = new LinkedHashMap<String, String>(rules);
    }

    public static void clear() {
        folder = null;
        pending = null;
    }

    @Nullable
    public static String expectedFolder() {
        return folder;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        Map<String, String> rules = pending;
        String expected = folder;
        if (rules == null || expected == null || world.isRemote || world.provider.getDimension() != 0) {
            return;
        }
        if (world.getSaveHandler().getWorldDirectory() == null || !expected.equals(world.getSaveHandler().getWorldDirectory().getName())) {
            return;
        }
        clear();
        if (world.getTotalWorldTime() != 0L) {
            return;
        }
        lastApplied = apply(world.getGameRules(), rules);
        BackportCore.LOGGER.info("Game rules: the new world {} took {} chosen rules", expected, lastApplied);
    }

    /** Sets each rule the world has to its chosen value (checked as the in-world screen's). @return how many changed */
    public static int apply(GameRules rules, Map<String, String> chosen) {
        int n = 0;
        for (Map.Entry<String, String> e : chosen.entrySet()) {
            String id = e.getKey();
            if (!rules.hasRule(id)) {
                continue;
            }
            Boolean numeric = GameRuleTable.numeric(rules, id);
            GameRuleTable.Rule rule = numeric == null ? null : GameRuleTable.unknown(id, numeric, e.getValue());
            if (rule == null || rule.numeric != numeric || !rule.valid(rule.shown(e.getValue()))) {
                continue;
            }
            if (!rules.getString(id).equals(e.getValue())) {
                rules.setOrCreateGameRule(id, e.getValue());
                n++;
            }
        }
        return n;
    }
}

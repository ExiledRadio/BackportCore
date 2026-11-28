package com.exiledradio.backportcore.mechanics;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.presentation.Presentation;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 1.17's {@code playersSleepingPercentage} game rule ({@code SleepStatus}), through patches P86 and P87
 * (docs/PATCHES.md). The night is skipped once {@code max(1, ceil(players * percentage / 100))} of the players that are
 * not spectators sleep and have slept long enough; at 100 (the default) that is everyone, 1.12.2's rule, and over 100
 * never. As players lie down and get up, each sees "{@code n/needed} players sleeping" or "Sleeping through this night"
 * above the hotbar, except in an unpublished singleplayer world.
 *
 * <p>The rule is added to every world while a backport of 1.17 or later is installed; worlds without it sleep as
 * 1.12.2 does.
 */
@Mod.EventBusSubscriber(modid = BackportCore.MOD_ID)
public final class SleepRules {

    public static final String GAME_RULE = "playersSleepingPercentage";

    /** Per world: the players counted and the sleepers, as last announced. */
    private static final Map<World, int[]> LAST = new WeakHashMap<World, int[]>();

    private SleepRules() {
    }

    /** @return true while the rule exists: a backport of 1.17 or later is installed */
    public static boolean installed() {
        return Presentation.installedNewest().isAtLeast("1.17");
    }

    @SubscribeEvent
    public static void worldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (!world.isRemote && installed() && !world.getGameRules().hasRule(GAME_RULE)) {
            world.getGameRules().addGameRule(GAME_RULE, "100", GameRules.ValueType.NUMERICAL_VALUE);
        }
    }

    /** P86, the head of {@code WorldServer.areAllPlayersAsleep}: true when this world follows the rule. */
    public static boolean decides(WorldServer world) {
        return world.getGameRules().hasRule(GAME_RULE);
    }

    /** P86: {@code areEnoughSleeping && areEnoughDeepSleeping}. */
    public static boolean enoughDeepSleeping(WorldServer world) {
        int percentage = world.getGameRules().getInt(GAME_RULE);
        int[] counts = counts(world);
        int needed = needed(counts[0], percentage);
        boolean skip = counts[1] >= needed && counts[2] >= needed;
        if (skip) {
            // 1.17's wakeUpAllPlayers clears the sleepers first, so getting up announces nothing.
            LAST.put(world, new int[] {counts[0], 0});
        }
        return skip;
    }

    /** {@code SleepStatus.sleepersNeeded}. */
    public static int needed(int players, int percentage) {
        return Math.max(1, (int) Math.ceil(players * percentage / 100.0D));
    }

    /** @return the players that are not spectators, those sleeping, and those asleep long enough */
    static int[] counts(WorldServer world) {
        int players = 0;
        int sleeping = 0;
        int deep = 0;
        for (EntityPlayer p : world.playerEntities) {
            if (p.isSpectator()) {
                continue;
            }
            players++;
            if (p.isPlayerSleeping()) {
                sleeping++;
                if (p.isPlayerFullyAsleep()) {
                    deep++;
                }
            }
        }
        return new int[] {players, sleeping, deep};
    }

    /** P87, the head of {@code WorldServer.updateAllPlayersSleepingFlag}: 1.17's {@code announceSleepStatus}, when the counts changed. */
    public static void sleepersChanged(WorldServer world) {
        if (!decides(world) || world.playerEntities.isEmpty()) {
            return;
        }
        int[] counts = counts(world);
        int[] last = LAST.get(world);
        int players = last == null ? 0 : last[0];
        int sleeping = last == null ? 0 : last[1];
        LAST.put(world, new int[] {counts[0], counts[1]});
        // SleepStatus.update: changed, with someone sleeping before or now.
        if (sleeping == 0 && counts[1] == 0 || players == counts[0] && sleeping == counts[1]) {
            return;
        }
        if (!announces(world.getMinecraftServer()) || !world.provider.isSurfaceWorld()) {
            return;
        }
        int percentage = world.getGameRules().getInt(GAME_RULE);
        int needed = needed(counts[0], percentage);
        TextComponentTranslation text = counts[1] >= needed ? new TextComponentTranslation("sleep.skipping_night")
                : new TextComponentTranslation("sleep.players_sleeping", counts[1], needed);
        for (EntityPlayer p : world.playerEntities) {
            if (p instanceof EntityPlayerMP) {
                p.sendStatusMessage(text, true);
            }
        }
    }

    /** 1.17: {@code !isSingleplayer() || isPublished()}; the integrated server's {@code getPublic} by reflection (a client class). */
    static boolean announces(MinecraftServer server) {
        if (server == null || !server.isSinglePlayer()) {
            return true;
        }
        for (String name : new String[] {"getPublic", "func_71344_c"}) {
            try {
                return (Boolean) server.getClass().getMethod(name).invoke(server);
            } catch (ReflectiveOperationException | RuntimeException e) {
                // try the other name
            }
        }
        return false;
    }
}

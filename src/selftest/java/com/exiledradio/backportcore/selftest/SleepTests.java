package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.mechanics.SleepRules;

import com.mojang.authlib.GameProfile;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * 1.17's playersSleepingPercentage (mechanics/SleepRules, P86 and P87): who must sleep to skip the night, and what
 * each player is told above the hotbar. The players are fake ones put into the world's player list for the step.
 */
final class SleepTests {

    private SleepTests() {
    }

    /** A player whose sleep the test sets, and who records what it is told. */
    static final class Sleeper extends FakePlayer {
        boolean inBed;
        boolean deep;
        boolean spectator;
        final List<ITextComponent> told = new ArrayList<ITextComponent>();

        Sleeper(WorldServer world, String name) {
            super(world, new GameProfile(UUID.randomUUID(), name));
        }

        @Override
        public boolean isPlayerSleeping() {
            return this.inBed;
        }

        @Override
        public boolean isPlayerFullyAsleep() {
            return this.inBed && this.deep;
        }

        @Override
        public boolean isSpectator() {
            return this.spectator;
        }

        @Override
        public void sendStatusMessage(ITextComponent text, boolean actionBar) {
            if (actionBar) {
                this.told.add(text);
            }
        }
    }

    private static String key(List<ITextComponent> told) {
        if (told.isEmpty()) {
            return "";
        }
        ITextComponent last = told.get(told.size() - 1);
        if (!(last instanceof TextComponentTranslation)) {
            return last.getUnformattedText();
        }
        TextComponentTranslation t = (TextComponentTranslation) last;
        StringBuilder out = new StringBuilder(t.getKey());
        for (Object a : t.getFormatArgs()) {
            out.append(' ').append(a);
        }
        return out.toString();
    }

    static void schedule() {
        then(1, "sleep: playersSleepingPercentage (P86, P87)", world -> {
            check("P86 and P87 applied", "applied".equals(Asm.state("P86")) && "applied".equals(Asm.state("P87")),
                    Asm.state("P86") + " " + Asm.state("P87"));
            check("sleepers needed: max(1, ceil(players * percentage / 100))", SleepRules.needed(3, 50) == 2 && SleepRules.needed(4, 0) == 1
                    && SleepRules.needed(0, 100) == 1 && SleepRules.needed(3, 100) == 3 && SleepRules.needed(3, 101) == 4);
            GameRules rules = world.getGameRules();
            boolean had = rules.hasRule(SleepRules.GAME_RULE);
            String before = had ? rules.getString(SleepRules.GAME_RULE) : null;
            check("the rule is in the world exactly when a backport of 1.17 is installed", had == SleepRules.installed(), had);
            if (!had) {
                rules.addGameRule(SleepRules.GAME_RULE, "100", GameRules.ValueType.NUMERICAL_VALUE);
            }
            List<net.minecraft.entity.player.EntityPlayer> saved = new ArrayList<net.minecraft.entity.player.EntityPlayer>(world.playerEntities);
            Sleeper a = new Sleeper(world, "[SelfTestSleepA]");
            Sleeper b = new Sleeper(world, "[SelfTestSleepB]");
            Sleeper watcher = new Sleeper(world, "[SelfTestSleepC]");
            watcher.spectator = true;
            world.playerEntities.clear();
            world.playerEntities.add(a);
            world.playerEntities.add(b);
            world.playerEntities.add(watcher);
            try {
                rules.setOrCreateGameRule(SleepRules.GAME_RULE, "100");
                a.inBed = true;
                a.deep = true;
                world.updateAllPlayersSleepingFlag();
                check("100%: one of two asleep does not skip", !world.areAllPlayersAsleep());
                check("told '1/2 players sleeping' (the spectator not counted)", "sleep.players_sleeping 1 2".equals(key(b.told)), key(b.told));
                rules.setOrCreateGameRule(SleepRules.GAME_RULE, "50");
                check("50%: one of two asleep long enough skips", world.areAllPlayersAsleep());
                a.deep = false;
                check("50%: lying down is not yet asleep long enough", !world.areAllPlayersAsleep());
                b.told.clear();
                b.inBed = true;
                b.deep = true;
                world.updateAllPlayersSleepingFlag();
                check("told 'Sleeping through this night' once enough lie down", "sleep.skipping_night".equals(key(b.told)), key(b.told));
                a.deep = true;
                rules.setOrCreateGameRule(SleepRules.GAME_RULE, "100");
                check("100%: both asleep skips (1.12.2's rule)", world.areAllPlayersAsleep());
                rules.setOrCreateGameRule(SleepRules.GAME_RULE, "101");
                check("over 100%: never", !world.areAllPlayersAsleep());
                a.inBed = false;
                b.inBed = false;
                a.told.clear();
                world.updateAllPlayersSleepingFlag();
                a.told.clear();
                world.updateAllPlayersSleepingFlag();
                check("nobody sleeping before or now: nothing told", a.told.isEmpty(), key(a.told));
            } finally {
                world.playerEntities.clear();
                world.playerEntities.addAll(saved);
                if (had) {
                    rules.setOrCreateGameRule(SleepRules.GAME_RULE, before);
                } else {
                    // Gone again, as it was: GameRules has no remove.
                    java.util.Map<String, ?> all = net.minecraftforge.fml.common.ObfuscationReflectionHelper.getPrivateValue(GameRules.class, rules, "field_82771_a");
                    all.remove(SleepRules.GAME_RULE);
                }
                world.updateAllPlayersSleepingFlag();
            }
        });
    }
}

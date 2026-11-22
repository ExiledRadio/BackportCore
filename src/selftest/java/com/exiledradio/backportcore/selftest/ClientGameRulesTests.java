package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.gamerules.GameRulesNetwork;
import com.exiledradio.backportcore.gamerules.NewWorldRules;
import com.exiledradio.backportcore.gamerules.client.GameRulesClient;
import com.exiledradio.backportcore.gamerules.client.GameRulesScreen;
import com.exiledradio.backportcore.presentation.client.OptimizeWorldScreens;
import com.exiledradio.backportcore.worldupgrade.WorldOptimizer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiWorldEdit;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * The Game Rules screen and Optimize World in a dev client, at the self-test's newest version (1.16, so 1.16's look:
 * no search box, the list from y 43): the in-world screen against the integrated server (refused without commands,
 * then fetched, edited, sent, and discarded with the question), Create World's button and its rules in a new world,
 * and Edit World's Optimize World on that world. No clicks or keys reach the window. Core 1.4.4.
 */
final class ClientGameRulesTests {

    static final String WORLD = "bc-gamerules-selftest";
    static boolean inWorldDone;
    static boolean done;
    private static int stage;
    private static int titleStage;
    private static int ticks;
    private static GameRulesScreen screen;
    private static GuiScreen parent;
    private static int appliedBefore;
    private static GuiCreateWorld createWorld;
    private static OptimizeWorldScreens.Progress progress;

    private ClientGameRulesTests() {
    }

    // ---- in the presentation tests' world ---------------------------------------------------------------------------

    static void inWorldTick(Minecraft mc) {
        if (inWorldDone) {
            return;
        }
        ticks++;
        try {
            IntegratedServer server = mc.getIntegratedServer();
            switch (stage) {
                case 0:
                    // The earlier tests turned commands on; off first, to see the server refuse.
                    commands(server, false);
                    stage = 100;
                    ticks = 0;
                    break;
                case 100:
                    if (ticks >= 20) {
                        parent = new GuiIngameMenu();
                        mc.displayGuiScreen(GameRulesScreen.inWorld(parent));
                        stage = 1;
                        ticks = 0;
                    }
                    break;
                case 1:
                    if (ticks >= 20) {
                        check("client rules: without commands the server refuses, and the screen goes back", mc.currentScreen == parent
                                && !GameRulesScreen.permitted(mc), mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
                        commands(server, true);
                        stage = 2;
                        ticks = 0;
                    }
                    break;
                case 2:
                    if (ticks >= 20) {
                        check("client rules: with commands the player may change game rules (level 2 or more)", GameRulesScreen.permitted(mc),
                                mc.player.getPermissionLevel());
                        screen = GameRulesScreen.inWorld(parent);
                        mc.displayGuiScreen(screen);
                        check("client rules: retrieving the rules first", !screen.received());
                        stage = 3;
                        ticks = 0;
                    }
                    break;
                case 3:
                    if (screen.received() || ticks >= 100) {
                        fetched(mc, server);
                        stage = 4;
                        ticks = 0;
                    }
                    break;
                case 4:
                    if (ticks >= 20) {
                        GameRules rules = server.worlds[0].getGameRules();
                        check("client rules: Done sends the changes and the server sets them", rules.getBoolean("keepInventory")
                                && rules.getInt("randomTickSpeed") == 7 && rules.getBoolean("disableElytraMovementCheck")
                                && GameRulesNetwork.applied - appliedBefore == 3 && mc.currentScreen == parent,
                                rules.getString("keepInventory") + " " + rules.getString("randomTickSpeed") + " " + (GameRulesNetwork.applied - appliedBefore));
                        screen = GameRulesScreen.inWorld(parent);
                        mc.displayGuiScreen(screen);
                        stage = 5;
                        ticks = 0;
                    }
                    break;
                case 5:
                    if (screen.received() || ticks >= 100) {
                        check("client rules: opened again, the server's new values", screen.rows().contains("Keep inventory after death = true"), screen.rows());
                        screen.set("keepInventory", "false");
                        screen.pressCancel();
                        boolean asked = mc.currentScreen instanceof GuiYesNo;
                        check("client rules: leaving with changes asks first (Game Rule Changes)", asked,
                                mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
                        if (asked) {
                            net.minecraft.client.gui.GuiYesNoCallback callback = ReflectionHelper.getPrivateValue(GuiYesNo.class, (GuiYesNo) mc.currentScreen,
                                    "parentScreen", "field_146355_a");
                            callback.confirmClicked(true, 0);
                        }
                        stage = 6;
                        ticks = 0;
                    }
                    break;
                case 6:
                    if (ticks >= 20) {
                        check("client rules: discarding sends nothing", server.worlds[0].getGameRules().getBoolean("keepInventory")
                                && mc.currentScreen == parent);
                        mc.displayGuiScreen(null);
                        inWorldDone = true;
                    }
                    break;
                default:
                    inWorldDone = true;
                    break;
            }
        } catch (Throwable t) {
            SelfTestRunner.fail("client rules stage " + stage + " threw", t.toString());
            BackportCore.LOGGER.error("CLIENT SELFTEST rules stage {} threw", stage, t);
            inWorldDone = true;
        }
    }

    private static void commands(IntegratedServer server, boolean on) {
        server.addScheduledTask(() -> {
            server.worlds[0].getWorldInfo().setAllowCommands(on);
            // The locate and tick tests let everyone use commands; off too, then back as they left it.
            server.getPlayerList().setCommandsAllowedForAll(on);
            for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
                server.getPlayerList().updatePermissionLevel(p);
            }
        });
    }

    private static void fetched(Minecraft mc, IntegratedServer server) {
        check("client rules: the server's rules arrived", screen.received());
        List<String> rows = screen.rows();
        BackportCore.LOGGER.info("CLIENT SELFTEST game rule rows: {}", rows);
        check("client rules: 1.16's look: no search box, the list from y 43", !screen.hasSearch() && screen.top() == 43, screen.top());
        check("client rules: 26.3's categories in 26.3's order",
                rows.indexOf("# Chat") == 0 && rows.indexOf("# Chat") < rows.indexOf("# Drops") && rows.indexOf("# Drops") < rows.indexOf("# Miscellaneous")
                        && rows.indexOf("# Miscellaneous") < rows.indexOf("# Mobs") && rows.indexOf("# Mobs") < rows.indexOf("# Player")
                        && rows.indexOf("# Player") < rows.indexOf("# World Updates"), rows);
        check("client rules: 26.3's names (Keep inventory after death, Update fire, Do elytra movement check turned around)",
                rows.contains("Keep inventory after death = false") && rows.contains("Update fire = true") && rows.contains("Do elytra movement check = true")
                        && rows.contains("Random tick speed rate = 3"), rows);
        check("client rules: Chat in 26.3's order (command_block_output, log_admin_commands, send_command_feedback, show_advancement_messages, show_death_messages)",
                rows.subList(1, 6).equals(Arrays.asList("Broadcast command block output = true", "Broadcast admin commands = true",
                        "Send command feedback = true", "Announce advancements = true", "Show death messages = true")), rows.subList(0, Math.min(6, rows.size())));
        List<String> tip = screen.tooltipOf("doMobLoot");
        check("client rules: the tooltip: the id in yellow, the description, the default in gray", tip != null && tip.size() >= 3
                && tip.get(0).equals("§edoMobLoot") && tip.get(tip.size() - 1).equals("§7" + I18n.format("editGamerule.default", "true")), tip);
        screen.setFilter("inventory");
        check("client rules: 26.1's filter: the matching rule under its category", screen.rows().equals(Arrays.asList("# Player",
                "Keep inventory after death = false")), screen.rows());
        screen.setFilter("world updates");
        check("client rules: the filter matches a category's name", screen.rows().size() >= 4 && screen.rows().get(0).equals("# World Updates"), screen.rows());
        screen.setFilter("");
        screen.set("keepInventory", "true");
        screen.set("randomTickSpeed", "abc");
        boolean letters = !screen.doneEnabled();
        screen.set("randomTickSpeed", "-1");
        boolean negative = !screen.doneEnabled();
        screen.set("randomTickSpeed", "7");
        check("client rules: Done is off while a number is not valid (letters, below 26.3's 0)", letters && negative && screen.doneEnabled());
        screen.set("disableElytraMovementCheck", "false");
        Map<String, String> changed = screen.changed();
        check("client rules: the changes in 1.12.2's names and values (elytra turned around)", changed.size() == 3
                && "true".equals(changed.get("disableElytraMovementCheck")) && "7".equals(changed.get("randomTickSpeed")), changed);
        appliedBefore = GameRulesNetwork.applied;
        screen.pressDone();
    }

    // ---- at the title screen ----------------------------------------------------------------------------------------

    static void tick(Minecraft mc) {
        ticks++;
        try {
            switch (titleStage) {
                case 0:
                    createWorld = new GuiCreateWorld(new GuiMainMenu());
                    mc.displayGuiScreen(createWorld);
                    titleStage = 10;
                    ticks = 0;
                    break;
                case 10:
                    if (ticks >= 5) {
                        createWorldScreen(mc);
                        titleStage = 11;
                        ticks = 0;
                    }
                    break;
                case 11:
                    if (mc.world != null && mc.player != null && ticks >= 20) {
                        GameRules rules = mc.getIntegratedServer().worlds[0].getGameRules();
                        check("client rules: the new world took the chosen rules", NewWorldRules.lastApplied == 2 && rules.getBoolean("keepInventory")
                                && rules.getInt("randomTickSpeed") == 5, NewWorldRules.lastApplied);
                        mc.loadWorld(null);
                        mc.displayGuiScreen(new GuiMainMenu());
                        titleStage = 12;
                        ticks = 0;
                    }
                    break;
                case 12:
                    if (ticks >= 10) {
                        editWorld(mc);
                        titleStage = 13;
                        ticks = 0;
                    }
                    break;
                case 13:
                    if (ticks >= 5) {
                        confirm(mc, true);
                        titleStage = 14;
                        ticks = 0;
                    }
                    break;
                case 14:
                    if (!(mc.currentScreen instanceof OptimizeWorldScreens.Progress) || ticks >= 1200) {
                        WorldOptimizer o = progress.optimizer();
                        check("client optimize: erasing cached data, every chunk written, back to the world list", o.isFinished()
                                && o.stage() == WorldOptimizer.Stage.FINISHED && o.total() > 0 && o.converted() == o.total()
                                && mc.currentScreen instanceof GuiWorldSelection,
                                o.converted() + "/" + o.skipped() + "/" + o.total() + " " + o.stage());
                        mc.displayGuiScreen(new GuiWorldEdit(new GuiWorldSelection(new GuiMainMenu()), WORLD));
                        titleStage = 15;
                        ticks = 0;
                    }
                    break;
                case 15:
                    if (ticks >= 5) {
                        confirm(mc, false);
                        titleStage = 16;
                        ticks = 0;
                    }
                    break;
                case 16:
                    if (!(mc.currentScreen instanceof OptimizeWorldScreens.Progress) || ticks >= 1200) {
                        WorldOptimizer o = progress.optimizer();
                        check("client optimize: again without erasing: every chunk is up to date (skipped)", o.isFinished()
                                && o.converted() == 0 && o.skipped() == o.total() && o.total() > 0, o.converted() + "/" + o.skipped() + "/" + o.total());
                        mc.displayGuiScreen(new GuiMainMenu());
                        done = true;
                    }
                    break;
                default:
                    done = true;
                    break;
            }
        } catch (Throwable t) {
            SelfTestRunner.fail("client rules title titleStage " + titleStage + " threw", t.toString());
            BackportCore.LOGGER.error("CLIENT SELFTEST rules title titleStage {} threw", titleStage, t);
            if (mc.world != null) {
                mc.loadWorld(null);
            }
            mc.displayGuiScreen(new GuiMainMenu());
            done = true;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> buttons(GuiScreen gui) throws Exception {
        return (List<GuiButton>) ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n").get(gui);
    }

    private static void createWorldScreen(Minecraft mc) throws Exception {
        List<GuiButton> buttons = buttons(createWorld);
        GuiButton rulesButton = null;
        GuiButton more = null;
        for (GuiButton b : buttons) {
            if (b instanceof GameRulesClient.GameRulesButton) {
                rulesButton = b;
            } else if (b.id == 3) {
                more = b;
            }
        }
        int w = createWorld.width;
        check("client rules: Create World has Game Rules at 1.16's place, More World Options beside it", rulesButton != null && rulesButton.visible
                && rulesButton.x == w / 2 - 155 && rulesButton.y == 187 && rulesButton.width == 150 && more != null && more.x == w / 2 + 5,
                rulesButton == null ? "none" : rulesButton.x + "," + rulesButton.y + " more " + (more == null ? "none" : more.x));
        GameRulesClient.open(createWorld);
        check("client rules: Game Rules opens the screen", mc.currentScreen instanceof GameRulesScreen);
        GameRulesScreen rules = (GameRulesScreen) mc.currentScreen;
        List<String> rows = rules.rows();
        check("client rules: Create World's list is 1.12.2's rules at their defaults (no backport installed: no Spawn phantoms)",
                rules.received() && rows.contains("Keep inventory after death = false") && !rows.contains("Spawn phantoms = true"), rows);
        rules.set("keepInventory", "true");
        rules.set("randomTickSpeed", "5");
        rules.pressDone();
        Map<String, String> chosen = GameRulesClient.chosen(createWorld);
        check("client rules: Done keeps the rules for Create World", mc.currentScreen == createWorld && chosen != null
                && "true".equals(chosen.get("keepInventory")) && "5".equals(chosen.get("randomTickSpeed")), chosen);
        // Create: the event a click sends, then the world as Create World would start it.
        GuiButton create = null;
        for (GuiButton b : buttons(createWorld)) {
            if (b.id == 0) {
                create = b;
            }
        }
        MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.ActionPerformedEvent.Pre(createWorld, create, buttons(createWorld)));
        String folder = NewWorldRules.expectedFolder();
        check("client rules: Create holds the rules for the new world's folder", folder != null && !folder.isEmpty(), folder);
        FileUtils.deleteDirectory(new File(mc.gameDir, "saves/" + WORLD));
        NewWorldRules.expect(WORLD, chosen);
        mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(20261121L, GameType.CREATIVE, true, false, WorldType.DEFAULT));
    }

    private static void editWorld(Minecraft mc) throws Exception {
        GuiWorldEdit edit = new GuiWorldEdit(new GuiWorldSelection(new GuiMainMenu()), WORLD);
        mc.displayGuiScreen(edit);
        GuiButton optimize = null;
        for (GuiButton b : buttons(edit)) {
            if (I18n.format("selectWorld.edit.optimize").equals(b.displayString)) {
                optimize = b;
            }
        }
        check("client optimize: Edit World has Optimize World, under Open Backups Folder", optimize != null && optimize.y == edit.height / 4 - 16 + 120,
                optimize == null ? "none" : optimize.y);
    }

    private static void confirm(Minecraft mc, boolean erase) throws Exception {
        GuiScreen edit = mc.currentScreen;
        OptimizeWorldScreens.Confirm confirm = new OptimizeWorldScreens.Confirm(edit, new GuiWorldSelection(new GuiMainMenu()), WORLD);
        mc.displayGuiScreen(confirm);
        if (erase) {
            List<String> labels = new java.util.ArrayList<String>();
            for (GuiButton b : confirm.buttons()) {
                labels.add(b.displayString);
            }
            check("client optimize: 1.16's question: Erase Cached Data (1.14), Create Backup and Load (before 1.21.2), I know what I'm doing!, Cancel",
                    labels.equals(Arrays.asList(I18n.format("selectWorld.backupEraseCache"), I18n.format("selectWorld.backupJoinConfirmButton"),
                            I18n.format("selectWorld.backupJoinSkipButton"), I18n.format("gui.cancel"))), labels);
        }
        int before = OptimizeWorldScreens.started;
        confirm.proceed(false, erase);
        check("client optimize: proceeding shows the progress screen", OptimizeWorldScreens.started == before + 1
                && mc.currentScreen instanceof OptimizeWorldScreens.Progress);
        progress = (OptimizeWorldScreens.Progress) mc.currentScreen;
    }
}

package com.exiledradio.backportcore.gamerules.client;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.gamerules.NewWorldRules;
import com.exiledradio.backportcore.presentation.client.OptionWidgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 1.16's Create World button (20w20a): "Game Rules" at the left of the main page's last row, with More World Options
 * moved to its right (1.16.5's {@code CreateWorldScreen}: both 150 wide at y 185, here 1.12.2's 187), hidden on the
 * More World Options page. Its rules go to the new world when Create is pressed ({@link NewWorldRules}). Since Core
 * 1.4.4.
 */
@SideOnly(Side.CLIENT)
public final class GameRulesClient {

    /** Each open Create World screen's chosen rules (Create World rebuilds its buttons when it comes back). */
    private static final Map<GuiScreen, Map<String, String>> CHOSEN = Collections.synchronizedMap(new WeakHashMap<GuiScreen, Map<String, String>>());

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiCreateWorld) || !GameRulesScreen.available()) {
            return;
        }
        final GuiScreen gui = event.getGui();
        for (GuiButton b : event.getButtonList()) {
            if (b.id == 3) {
                b.x = gui.width / 2 + 5;
                b.width = 150;
            }
        }
        GameRulesButton button = new GameRulesButton(gui.width / 2 - 155, 187, () -> open(gui));
        button.visible = mainPage(event.getButtonList());
        event.getButtonList().add(button);
    }

    /** The Game Rules button: on the main page only (where Game Mode shows). */
    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (event.getGui() instanceof GuiCreateWorld) {
            java.util.List<GuiButton> buttons = buttons(event.getGui());
            if (buttons != null) {
                boolean main = mainPage(buttons);
                for (GuiButton b : buttons) {
                    if (b instanceof GameRulesButton) {
                        b.visible = main;
                    }
                }
            }
        }
    }

    /** Create: the chosen rules wait for the new world's folder. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.getGui() instanceof GuiCreateWorld) || event.getButton().id != 0 || !event.getButton().enabled) {
            return;
        }
        Map<String, String> chosen = CHOSEN.get(event.getGui());
        if (chosen == null || chosen.isEmpty()) {
            NewWorldRules.clear();
            return;
        }
        String folder = saveFolder((GuiCreateWorld) event.getGui());
        if (folder != null) {
            NewWorldRules.expect(folder, chosen);
        }
    }

    static boolean mainPage(java.util.List<GuiButton> buttons) {
        for (GuiButton b : buttons) {
            if (b.id == 2) {
                return b.visible;
            }
        }
        return true;
    }

    /** Opens the screen over {@code createWorld}, with what was chosen before. */
    public static void open(GuiScreen createWorld) {
        Map<String, String> chosen = CHOSEN.get(createWorld);
        Minecraft.getMinecraft().displayGuiScreen(GameRulesScreen.forNewWorld(createWorld,
                chosen == null ? Collections.<String, String>emptyMap() : chosen,
                values -> CHOSEN.put(createWorld, new LinkedHashMap<String, String>(values))));
    }

    /** For the self-test. */
    @Nullable
    public static Map<String, String> chosen(GuiScreen createWorld) {
        return CHOSEN.get(createWorld);
    }

    @Nullable
    static String saveFolder(GuiCreateWorld gui) {
        try {
            return (String) ReflectionHelper.findField(GuiCreateWorld.class, "saveDirName", "field_146336_i").get(gui);
        } catch (Exception e) {
            BackportCore.LOGGER.error("Game rules: cannot read the new world's folder; its rules stay at their defaults", e);
            return null;
        }
    }

    @Nullable
    private static java.lang.reflect.Field buttonList;

    @Nullable
    @SuppressWarnings("unchecked")
    private static java.util.List<GuiButton> buttons(GuiScreen gui) {
        try {
            if (buttonList == null) {
                buttonList = ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n");
            }
            return (java.util.List<GuiButton>) buttonList.get(gui);
        } catch (Exception e) {
            return null;
        }
    }

    /** The Game Rules button. */
    public static final class GameRulesButton extends OptionWidgets.Action {
        GameRulesButton(int x, int y, Runnable action) {
            super(x, y, 150, I18n.format("selectWorld.gameRules"), action);
        }
    }
}

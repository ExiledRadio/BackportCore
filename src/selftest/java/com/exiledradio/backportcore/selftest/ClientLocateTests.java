package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.TabCompleter;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

/**
 * {@code /locate} in the real client, in a world: Tab is pressed through {@link GuiChat}'s own completer (the request
 * goes to the integrated server as a packet, the reply comes back through the client's handler, which sorts it), and
 * typed commands are sent as chat. Each step runs a few ticks after the one before, so replies have arrived.
 */
@SideOnly(Side.CLIENT)
final class ClientLocateTests {

    /** {typed, text after one Tab, text after a second Tab (null: not pressed)}. */
    static final String[][] TABS = {
            {"/locate structure mon", "/locate structure minecraft:monument", null},
            {"/locate structure minecraft:mon", "/locate structure minecraft:monument", null},
            // Was "minecraft:" and then the first id of all: the minecraft namespace matched "m" too.
            {"/locate structure m", "/locate structure minecraft:m", "/locate structure minecraft:mansion"},
            {"/locate structure minecraft:m", "/locate structure minecraft:mansion", "/locate structure minecraft:mineshaft"},
            {"/locate structure mine", "/locate structure minecraft:mineshaft", null},
            {"/locate structure hut", "/locate structure minecraft:swamp_hut", null},
            {"/locate structure pyramid", "/locate structure pyramid", null},
            {"/locate structure #", "/locate structure #backportcore_selftest:both", null},
            // Core alone registers no village types, so #minecraft:village has no members to complete.
            {"/locate structure #minecraft:v", "/locate structure #minecraft:v", null},
            {"/locate structure strong", "/locate structure minecraft:stronghold", null},
            {"/locate structure end", "/locate structure minecraft:end_city", null},
            // The client sorts: "Stronghold" before "structure", and cycles on the second Tab.
            {"/locate str", "/locate Stronghold", "/locate structure"},
            {"/locate Strong", "/locate Stronghold", null},
    };

    /** {command, start of the reply}. */
    static final String[][] TYPED = {
            {"/locate structure monument", "The nearest minecraft:monument is at ["},
            {"/locate structure minecraft:monument", "The nearest minecraft:monument is at ["},
            {"/locate structure minecraft:stronghold", "The nearest minecraft:stronghold is at ["},
            {"/locate structure minecraft:mineshaft", "The nearest minecraft:mineshaft is at ["},
            {"/locate structure minecraft:end_city", "Could not find a structure of type \"minecraft:end_city\" nearby"},
            {"/locate structure minecraft:fortress", "Could not find a structure of type \"minecraft:fortress\" nearby"},
            {"/locate structure #minecraft:monument", "There is no structure with type \"#minecraft:monument\""},
            {"/locate Monument", "The nearest Monument is at ["},
    };

    static final List<String> CHAT = new ArrayList<String>();

    private int step;
    private int wait;
    private boolean done;

    static ClientLocateTests instance;

    static void register() {
        instance = new ClientLocateTests();
        MinecraftForge.EVENT_BUS.register(instance);
    }

    static boolean done() {
        return instance == null || instance.done;
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        CHAT.add(event.getMessage().getUnformattedText());
    }

    /** Called each client tick once the world is loaded. */
    void tick(Minecraft mc) throws Exception {
        if (this.done || ++this.wait < 6) {
            return;
        }
        if (this.step == 0 && mc.getIntegratedServer() != null) {
            // The test world is survival without cheats; /locate needs level 2, as in vanilla.
            mc.getIntegratedServer().getPlayerList().setCommandsAllowedForAll(true);
        }
        this.wait = 0;
        int tabSteps = TABS.length * 3;
        if (this.step < tabSteps) {
            String[] c = TABS[this.step / 3];
            switch (this.step % 3) {
                case 0:
                    mc.displayGuiScreen(new GuiChat(c[0]));
                    tab(mc);
                    break;
                case 1: {
                    String after = field(mc).getText();
                    BackportCore.LOGGER.info("CLIENT LOCATE tab '{}' -> '{}'", c[0], after);
                    check("client locate: Tab on '" + c[0] + "'", c[1].equals(after), after);
                    if (c[2] != null) {
                        tab(mc);
                    }
                    break;
                }
                default:
                    if (c[2] != null) {
                        String after = field(mc).getText();
                        BackportCore.LOGGER.info("CLIENT LOCATE second tab '{}' -> '{}'", c[0], after);
                        check("client locate: second Tab on '" + c[0] + "'", c[2].equals(after), after);
                    }
                    mc.displayGuiScreen(null);
                    break;
            }
            this.step++;
            return;
        }
        int typed = this.step - tabSteps;
        if (typed < TYPED.length * 2) {
            String[] c = TYPED[typed / 2];
            if (typed % 2 == 0) {
                CHAT.clear();
                mc.player.sendChatMessage(c[0]);
            } else {
                String reply = CHAT.isEmpty() ? "(nothing)" : CHAT.get(CHAT.size() - 1);
                BackportCore.LOGGER.info("CLIENT LOCATE typed '{}' -> '{}'", c[0], reply);
                check("client locate: " + c[0], reply.startsWith(c[1]), reply);
            }
            this.step++;
            return;
        }
        this.done = true;
    }

    private static void tab(Minecraft mc) {
        TabCompleter completer = ReflectionHelper.getPrivateValue(GuiChat.class, (GuiChat) mc.currentScreen, "tabCompleter");
        completer.complete();
    }

    private static GuiTextField field(Minecraft mc) {
        return ReflectionHelper.getPrivateValue(GuiChat.class, (GuiChat) mc.currentScreen, "inputField");
    }
}

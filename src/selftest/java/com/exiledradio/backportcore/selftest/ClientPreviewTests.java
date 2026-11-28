package com.exiledradio.backportcore.selftest;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.client.InventoryPreview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

/**
 * Core 1.5.0: 1.20.2's player preview (P78, {@code InventoryPreview}). The survival inventory standing and in the
 * elytra pose, and the creative Inventory tab: the model drawn by Core with the scissor test on, the scissor exactly the
 * box at the GUI scale, the feet inside the box, and the scissor test off again after the screen. With the inventory
 * look off, 1.12.2's drawing (Core draws none). Screenshots {@code preview-<case>.png}.
 */
final class ClientPreviewTests {

    static boolean done;

    private static int step;
    private static int wait;
    private static boolean added;
    private static boolean lookWas;
    private static int drawnAt;
    private static boolean elytra;
    private static Method setFlag;

    private ClientPreviewTests() {
    }

    static void tick(Minecraft mc) {
        if (done) {
            return;
        }
        if (elytra) {
            // The server's metadata can clear the client's flag; keep it set while the pose is checked.
            flag(mc, true);
        }
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            run(mc);
        } catch (RuntimeException e) {
            SelfTestRunner.fail("preview: step " + step + " threw", e);
            BackportCore.LOGGER.error("preview step {} threw", step, e);
            finish(mc);
        }
    }

    private static void run(Minecraft mc) {
        switch (step) {
            case 0:
                added = !VanillaRevisions.inventoryPreviewClipped();
                if (added) {
                    Presentation.version("1.20.2");
                }
                lookWas = CoreConfig.lookAndFeel.inventory;
                CoreConfig.lookAndFeel.inventory = true;
                check("preview: 1.20.2's look (clipped)", VanillaRevisions.inventoryPreviewClipped(), Presentation.newest());
                mc.player.getRecipeBook().setGuiOpen(false);
                gameType(mc, GameType.SURVIVAL);
                step = 1;
                return;
            case 1:
                // The server's game mode arrives later and would turn GuiInventory into the creative screen.
                if (mc.player.capabilities.isCreativeMode || mc.playerController.isInCreativeMode()) {
                    return;
                }
                mc.displayGuiScreen(new GuiInventory(mc.player));
                drawnAt = InventoryPreview.drawn;
                step = 10;
                wait = 20;
                return;
            case 10: {
                GuiInventory gui = (GuiInventory) mc.currentScreen;
                checkBox(mc, "survival, standing", gui.getGuiLeft(), gui.getGuiTop(), new int[] {26, 8, 75, 78});
                check("preview (survival, standing): a standing player's height", InventoryPreview.lastHeight > 1.4F, InventoryPreview.lastHeight);
                shot(mc, "standing");
                elytra = true;
                flag(mc, true);
                drawnAt = InventoryPreview.drawn;
                step = 2;
                wait = 20;
                return;
            }
            case 2: {
                GuiInventory gui = (GuiInventory) mc.currentScreen;
                check("preview (survival, elytra): the player is elytra flying", mc.player.isElytraFlying() && mc.player.height < 1.0F,
                        mc.player.isElytraFlying() + " height " + mc.player.height);
                checkBox(mc, "survival, elytra", gui.getGuiLeft(), gui.getGuiTop(), new int[] {26, 8, 75, 78});
                check("preview (survival, elytra): drawn at the elytra pose's height", InventoryPreview.lastHeight < 1.0F, InventoryPreview.lastHeight);
                shot(mc, "elytra");
                elytra = false;
                flag(mc, false);
                mc.displayGuiScreen(null);
                gameType(mc, GameType.CREATIVE);
                step = 20;
                return;
            }
            case 20: {
                if (!mc.player.capabilities.isCreativeMode || !mc.playerController.isInCreativeMode()) {
                    return;
                }
                try {
                    Field tab = ReflectionHelper.findField(GuiContainerCreative.class, "selectedTabIndex", "field_147058_w");
                    tab.setInt(null, CreativeTabs.INVENTORY.getIndex());
                } catch (ReflectiveOperationException e) {
                    check("preview: the creative Inventory tab selected", false, e);
                }
                mc.displayGuiScreen(new GuiContainerCreative(mc.player));
                drawnAt = InventoryPreview.drawn;
                step = 3;
                wait = 20;
                return;
            }
            case 3: {
                check("preview (creative): the creative inventory is open on the Inventory tab", mc.currentScreen instanceof GuiContainerCreative
                        && ((GuiContainerCreative) mc.currentScreen).getSelectedTabIndex() == CreativeTabs.INVENTORY.getIndex(), mc.currentScreen);
                if (mc.currentScreen instanceof GuiContainerCreative) {
                    GuiContainerCreative gui = (GuiContainerCreative) mc.currentScreen;
                    checkBox(mc, "creative", gui.getGuiLeft(), gui.getGuiTop(), new int[] {73, 6, 105, 49});
                    shot(mc, "creative");
                }
                // The look off: 1.12.2's drawEntityOnScreen, nothing of Core's.
                CoreConfig.lookAndFeel.inventory = false;
                mc.displayGuiScreen(null);
                gameType(mc, GameType.SURVIVAL);
                step = 30;
                return;
            }
            case 30:
                if (mc.player.capabilities.isCreativeMode || mc.playerController.isInCreativeMode()) {
                    return;
                }
                mc.displayGuiScreen(new GuiInventory(mc.player));
                step = 4;
                wait = 10;
                return;
            case 4:
                drawnAt = InventoryPreview.drawn;
                step = 5;
                wait = 10;
                return;
            case 5:
                check("preview (look off): 1.12.2's preview, none of Core's", !VanillaRevisions.inventoryPreviewClipped() && InventoryPreview.drawn == drawnAt,
                        (InventoryPreview.drawn - drawnAt) + " drawn");
                shot(mc, "off");
                finish(mc);
                return;
            default:
                finish(mc);
        }
    }

    /** Drawn since the screen opened, with the scissor on and set to the box at the GUI scale, and off after the frame. */
    private static void checkBox(Minecraft mc, String what, int left, int top, int[] box) {
        int f = new ScaledResolution(mc).getScaleFactor();
        int[] wantBox = {left + box[0], top + box[1], left + box[2], top + box[3]};
        int[] wantScissor = {wantBox[0] * f, mc.displayHeight - wantBox[3] * f, (box[2] - box[0]) * f, (box[3] - box[1]) * f};
        check("preview (" + what + "): drawn by Core", InventoryPreview.drawn > drawnAt, (InventoryPreview.drawn - drawnAt) + " drawn");
        check("preview (" + what + "): the box", Arrays.equals(InventoryPreview.lastBox, wantBox), Arrays.toString(InventoryPreview.lastBox)
                + " want " + Arrays.toString(wantBox));
        check("preview (" + what + "): the scissor test on while drawn", InventoryPreview.lastClipped, "");
        check("preview (" + what + "): the scissor is the box at GUI scale " + f, Arrays.equals(InventoryPreview.lastScissor, wantScissor),
                Arrays.toString(InventoryPreview.lastScissor) + " want " + Arrays.toString(wantScissor));
        check("preview (" + what + "): the feet inside the box", InventoryPreview.lastFeetY > wantBox[1] && InventoryPreview.lastFeetY <= wantBox[3],
                InventoryPreview.lastFeetY + " in " + wantBox[1] + ".." + wantBox[3]);
        check("preview (" + what + "): the scissor test off after the screen", !GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), "");
    }

    /** {@code Entity.setFlag(7, on)}: the client's elytra-flying flag (the server never sees it). */
    private static void flag(Minecraft mc, boolean on) {
        try {
            if (setFlag == null) {
                setFlag = ReflectionHelper.findMethod(Entity.class, "setFlag", "func_70052_a", int.class, boolean.class);
            }
            setFlag.invoke(mc.player, 7, on);
        } catch (ReflectiveOperationException e) {
            check("preview: the elytra flag set", false, e);
        }
    }

    private static void shot(Minecraft mc, String what) {
        ScreenShotHelper.saveScreenshot(mc.gameDir, "preview-" + what + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    private static void finish(Minecraft mc) {
        if (elytra) {
            elytra = false;
            flag(mc, false);
        }
        CoreConfig.lookAndFeel.inventory = lookWas;
        if (added) {
            try {
                java.lang.reflect.Field f = Presentation.class.getDeclaredField("VERSIONS");
                f.setAccessible(true);
                ((java.util.Map<?, ?>) f.get(null)).remove(GameVersion.parse("1.20.2"));
            } catch (ReflectiveOperationException e) {
                check("preview: the dummy 1.20.2 is removed", false, e);
            }
            added = false;
        }
        mc.displayGuiScreen(null);
        gameType(mc, GameType.CREATIVE);
        done = true;
    }

    /** The game mode on both sides, as the server would send it. */
    private static void gameType(Minecraft mc, GameType type) {
        mc.playerController.setGameType(type);
        IntegratedServer server = mc.getIntegratedServer();
        UUID id = mc.player.getUniqueID();
        server.addScheduledTask(() -> {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) {
                p.setGameType(type);
            }
        });
    }
}

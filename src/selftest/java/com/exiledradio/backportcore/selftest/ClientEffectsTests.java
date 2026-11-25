package com.exiledradio.backportcore.selftest;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.CoreConfig;
import com.exiledradio.backportcore.presentation.GameVersion;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.VanillaRevisions;
import com.exiledradio.backportcore.presentation.client.InventoryEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraftforge.fml.common.Loader;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Core 1.4.9: the active effects beside the inventory (P57, {@code InventoryEffects}) against JEI's and HEI's item list.
 * With four effects on, the survival inventory at the window's width, at a width where only compact (32 wide, icon
 * only) backgrounds fit, and at one where none fit: every background on screen, right of the inventory, compact when
 * narrow, and (with JEI or HEI on the class path: {@code -PwithMods=<jei jar>}) none of JEI's drawn items under one.
 * With the inventory look off, 1.12.2's left column and no area for JEI. Screenshots {@code effects-<case>[-jei].png}.
 */
final class ClientEffectsTests {

    static boolean done;

    private static int step;
    private static int wait;
    private static boolean added;
    private static boolean lookWas;
    private static int drawnAt;

    private ClientEffectsTests() {
    }

    static void tick(Minecraft mc) {
        if (done) {
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            run(mc);
        } catch (RuntimeException e) {
            SelfTestRunner.fail("effects: step " + step + " threw", e);
            BackportCore.LOGGER.error("effects step {} threw", step, e);
            finish(mc);
        }
    }

    private static void run(Minecraft mc) {
        switch (step) {
            case 0:
                // The fitted backgrounds are 1.21.11's: a dummy 26.3 when no backport brings it, removed after.
                added = !VanillaRevisions.effectsFitText();
                if (added) {
                    Presentation.version("26.3");
                }
                lookWas = CoreConfig.lookAndFeel.inventory;
                CoreConfig.lookAndFeel.inventory = true;
                // 1.12.2 draws no effects while the recipe book is open.
                mc.player.getRecipeBook().setGuiOpen(false);
                mc.playerController.setGameType(GameType.SURVIVAL);
                server(mc, p -> {
                    p.setGameType(GameType.SURVIVAL);
                    p.addPotionEffect(new PotionEffect(MobEffects.SPEED, 6000, 1));
                    p.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 6000, 4));
                    p.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 6000, 0, true, true));
                    p.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, 6000, 0));
                });
                step = 1;
                return;
            case 1:
                if (mc.player.getActivePotionEffects().size() < 4 || mc.player.capabilities.isCreativeMode) {
                    return;
                }
                check("effects: 26.3's look (beside the inventory, fitted)", VanillaRevisions.effectsBesideInventory() && VanillaRevisions.effectsFitText(),
                        Presentation.newest());
                mc.displayGuiScreen(new GuiInventory(mc.player));
                step = 2;
                wait = 20;
                return;
            case 2: {
                GuiInventory gui = (GuiInventory) mc.currentScreen;
                int room = gui.width - (gui.getGuiLeft() + gui.getXSize() + 2);
                int[] a = checkPanel(mc, gui, "window width " + gui.width, 4);
                check("effects: " + (room >= 120 ? "fitted to the text (room " + room + ")" : "compact (room " + room + ")"), a.length == 16
                        && (room >= 120 ? a[2] > 32 : a[2] == 32 && a[6] == 32 && a[10] == 32 && a[14] == 32), Arrays.toString(a));
                List<Rectangle> drawn = jeiDrawn(gui, a, "wide", true);
                shot(mc, "wide");
                // A width with room for 32 to 119 pixels right of the inventory: only the compact form fits.
                gui.setWorldAndResolution(mc, 300, gui.height);
                step = 3;
                wait = 20;
                if (drawn != null) {
                    BackportCore.LOGGER.info("effects: JEI drew {} items at window width", drawn.size());
                }
                return;
            }
            case 3: {
                GuiInventory gui = (GuiInventory) mc.currentScreen;
                int room = gui.width - (gui.getGuiLeft() + gui.getXSize() + 2);
                int[] a = checkPanel(mc, gui, "width 300", 4);
                check("effects: compact (32 wide, icon only) where names cannot fit (room " + room + ")", room < 120 && a.length == 16
                        && a[2] == 32 && a[6] == 32 && a[10] == 32 && a[14] == 32 && sameAsDrawn(a), Arrays.toString(a));
                jeiDrawn(gui, a, "narrow", false);
                shot(mc, "narrow");
                // Under 32 pixels of room: no effects (26.3's canSeeEffects).
                gui.setWorldAndResolution(mc, 200, gui.height);
                step = 4;
                wait = 20;
                return;
            }
            case 4: {
                GuiInventory gui = (GuiInventory) mc.currentScreen;
                int[] a = InventoryEffects.areas(gui);
                check("effects: none under 32 pixels of room, and no area for JEI", a.length == 0 && InventoryEffects.lastBackgrounds.length == 0
                        && !InventoryEffects.canSeeEffects(gui), Arrays.toString(a));
                // The inventory look off: 1.12.2's column left of a shifted inventory, which JEI 1.12 already knows.
                CoreConfig.lookAndFeel.inventory = false;
                mc.displayGuiScreen(new GuiInventory(mc.player));
                drawnAt = InventoryEffects.drawn;
                step = 5;
                wait = 20;
                return;
            }
            case 5: {
                GuiInventory gui = (GuiInventory) mc.currentScreen;
                check("effects, look off: 1.12.2's own draw, and no area of Core's for JEI", !VanillaRevisions.effectsBesideInventory()
                        && InventoryEffects.areas(gui).length == 0 && InventoryEffects.drawn == drawnAt, InventoryEffects.drawn - drawnAt);
                shot(mc, "lookoff");
                finish(mc);
                return;
            }
            default:
                finish(mc);
        }
    }

    /** The panel against the screen: drawn where {@code areas} says, on screen, right of the inventory. */
    private static int[] checkPanel(Minecraft mc, GuiInventory gui, String what, int count) {
        int[] a = InventoryEffects.areas(gui);
        check("effects (" + what + "): " + count + " drawn, where the JEI area says", a.length == count * 4 && sameAsDrawn(a),
                Arrays.toString(a) + " drawn " + Arrays.toString(InventoryEffects.lastBackgrounds));
        boolean onScreen = true;
        for (int i = 0; i < a.length; i += 4) {
            onScreen &= a[i] >= gui.getGuiLeft() + gui.getXSize() && a[i] + a[i + 2] <= gui.width && a[i + 1] >= 0 && a[i + 1] + a[i + 3] <= gui.height;
        }
        check("effects (" + what + "): on screen, right of the inventory", onScreen, Arrays.toString(a) + " screen " + gui.width + "x" + gui.height);
        return a;
    }

    private static boolean sameAsDrawn(int[] a) {
        int[] bg = InventoryEffects.lastBackgrounds;
        if (bg.length * 4 != a.length * 3) {
            return false;
        }
        for (int i = 0; i < bg.length / 3; i++) {
            if (bg[i * 3] != a[i * 4] || bg[i * 3 + 1] != a[i * 4 + 1] || bg[i * 3 + 2] != a[i * 4 + 2]) {
                return false;
            }
        }
        return true;
    }

    /**
     * JEI's (or HEI's) drawn items: none under an effect. {@code needItems}: the list must show some (at the window's
     * width there is room for it), so the check is not empty.
     *
     * @return the drawn items' areas, or null without JEI
     */
    private static List<Rectangle> jeiDrawn(GuiInventory gui, int[] a, String what, boolean needItems) {
        if (!Loader.isModLoaded("jei")) {
            return null;
        }
        List<Rectangle> drawn = new ArrayList<Rectangle>();
        try {
            Object runtime = Class.forName("mezz.jei.Internal").getMethod("getRuntime").invoke(null);
            Object overlay = runtime.getClass().getMethod("getIngredientListOverlay").invoke(runtime);
            Object grid = field(field(overlay, "contents"), "ingredientGrid");
            Object batch = field(grid, "guiIngredientSlots");
            for (Object slot : (List<?>) batch.getClass().getMethod("getAllGuiIngredientSlots").invoke(batch)) {
                if (slot.getClass().getMethod("getIngredientRenderer").invoke(slot) != null) {
                    drawn.add((Rectangle) slot.getClass().getMethod("getArea").invoke(slot));
                }
            }
        } catch (ReflectiveOperationException e) {
            check("effects (" + what + "): JEI's item list read", false, e);
            return null;
        }
        String name = Loader.instance().getIndexedModList().get("jei").getName();
        Rectangle under = null;
        for (Rectangle r : drawn) {
            for (int i = 0; i < a.length; i += 4) {
                if (r.intersects(new Rectangle(a[i], a[i + 1], a[i + 2], a[i + 3]))) {
                    under = r;
                }
            }
        }
        if (needItems) {
            check("effects (" + what + "): " + name + " shows its item list", !drawn.isEmpty(), drawn.size());
        }
        check("effects (" + what + "): no " + name + " item under an effect", under == null, under + " of " + drawn.size());
        return drawn;
    }

    private static Object field(Object o, String name) throws ReflectiveOperationException {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException e) {
                // the superclass's
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void shot(Minecraft mc, String what) {
        String suffix = Loader.isModLoaded("jei") ? "-" + Loader.instance().getIndexedModList().get("jei").getName().replace(' ', '_').toLowerCase(java.util.Locale.ROOT) : "";
        ScreenShotHelper.saveScreenshot(mc.gameDir, "effects-" + what + suffix + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    private static void finish(Minecraft mc) {
        CoreConfig.lookAndFeel.inventory = lookWas;
        if (added) {
            try {
                java.lang.reflect.Field f = Presentation.class.getDeclaredField("VERSIONS");
                f.setAccessible(true);
                ((java.util.Map<?, ?>) f.get(null)).remove(GameVersion.parse("26.3"));
            } catch (ReflectiveOperationException e) {
                check("effects: the dummy 26.3 is removed", false, e);
            }
            added = false;
        }
        mc.displayGuiScreen(null);
        mc.playerController.setGameType(GameType.CREATIVE);
        server(mc, p -> {
            p.clearActivePotions();
            p.setGameType(GameType.CREATIVE);
        });
        done = true;
    }

    private static void server(Minecraft mc, java.util.function.Consumer<EntityPlayerMP> action) {
        IntegratedServer server = mc.getIntegratedServer();
        UUID id = mc.player.getUniqueID();
        server.addScheduledTask(() -> {
            EntityPlayerMP p = server.getPlayerList().getPlayerByUUID(id);
            if (p != null) {
                action.accept(p);
            }
        });
    }
}

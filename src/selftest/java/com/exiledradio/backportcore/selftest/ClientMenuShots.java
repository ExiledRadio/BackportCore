package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.BackportCore;
import com.exiledradio.backportcore.creative.client.CreativeScreenHooks;
import com.exiledradio.backportcore.creative.client.ModernTab;
import com.exiledradio.backportcore.creative.client.ModernTabs;
import com.exiledradio.backportcore.asm.Asm;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.client.MenuBackground;
import com.exiledradio.backportcore.presentation.client.PanoramaRenderer;
import com.exiledradio.backportcore.presentation.client.PresentationHooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.gui.GuiWorldSelection;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.io.FileUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Dev only, environment variable {@code BACKPORTCORE_MENUSHOTS=1} with {@code runClient -Pselftest}: 1.20.5's menus
 * (P31), panorama motion and Narrator Hotkey (P32). Registers a dummy 1.20.5 unless a real 1.20.5 backport is loaded
 * ({@code -PwithMods=}Trails &amp; Tales Backport's dev jar), opens screens in code outside and inside a world, saves each
 * to {@code screenshots/menu-*.png}, logs {@code MENU SHOTS DONE pass=N fail=M} and closes the game.
 */
@SideOnly(Side.CLIENT)
final class ClientMenuShots {

    static final String WORLD = "backportcore_menushots";

    static boolean active() {
        return System.getenv("BACKPORTCORE_MENUSHOTS") != null;
    }

    static void register() {
        if (!Loader.isModLoaded("trailsandtalesbackport")) {
            Presentation.version("1.20.5");
        }
        MinecraftForge.EVENT_BUS.register(new ClientMenuShots());
    }

    private final List<String> names = new ArrayList<String>();
    private final List<Supplier<Boolean>> steps = new ArrayList<Supplier<Boolean>>();
    private int step = -1;
    private int ticks;
    private String shot;
    private int pass;
    private int fail;
    private int menuAt;
    private int worldAt;
    private int listAt;
    private int waited;
    private float yawAt;

    private ClientMenuShots() {
        Minecraft mc = Minecraft.getMinecraft();
        add("title", () -> {
            check("P31 (screens) applied", "applied".equals(Asm.state("P31")), Asm.state("P31"));
            check("P32 applied", "applied".equals(Asm.state("P32")), Asm.state("P32"));
            check("1.20.5 is the newest", Presentation.isAtLeast("1.20.5") && MenuBackground.active(), Presentation.newest());
            return true;
        });
        add(null, () -> {
            float[] a = PanoramaRenderer.lastAngles();
            check("the title panorama holds its pitch at 10", a[0] == 10.0F, a[0]);
            check("the title screen is not blurred (1.20.5's title has no menu background)", MenuBackground.menuFrames == 0, MenuBackground.menuFrames);
            return true;
        });
        add("options", () -> show(new GuiOptions(new GuiMainMenu(), mc.gameSettings)));
        add(null, () -> {
            check("options: the menu background drawn", MenuBackground.menuFrames > 0, MenuBackground.menuFrames);
            check("options: blurred", !OpenGlHelper.shadersSupported || MenuBackground.blurFrames > 0, MenuBackground.blurFrames);
            this.menuAt = MenuBackground.menuFrames;
            this.yawAt = PanoramaRenderer.lastAngles()[1];
            return true;
        });
        add("video", () -> show(new GuiVideoSettings(new GuiMainMenu(), mc.gameSettings)));
        add("language", () -> show(new GuiLanguage(new GuiMainMenu(), mc.gameSettings, mc.getLanguageManager())));
        add("worlds", () -> show(new GuiWorldSelection(new GuiMainMenu())));
        add(null, () -> {
            check("P31 (lists) applied", "applied".equals(Asm.state("P31-list")), Asm.state("P31-list"));
            check("lists: drawn as 1.20.5's", MenuBackground.listFrames > 0, MenuBackground.listFrames);
            check("lists: the screen behind the world list is drawn (it has no background of its own)",
                    MenuBackground.menuFrames > this.menuAt, MenuBackground.menuFrames + " after " + this.menuAt);
            check("lists: color writes are back on after the list", colorMaskOn());
            check("lists: the scissor is off after the list", !GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));
            return true;
        });
        add("packs", () -> show(new GuiScreenResourcePacks(new GuiMainMenu())));
        add("multiplayer", () -> show(new GuiMultiplayer(new GuiMainMenu())));
        add(null, () -> show(new net.minecraftforge.fml.client.GuiModList(new GuiMainMenu())));
        add("modlist", () -> selectCore(mc));
        add(null, () -> {
            float now = PanoramaRenderer.lastAngles()[1];
            this.listAt = MenuBackground.listFrames;
            check("the panorama kept turning across five screens (one spin, never restarted)", now < this.yawAt - 5.0F,
                    now + " after " + this.yawAt);
            return true;
        });
        add(null, () -> {
            try {
                FileUtils.deleteDirectory(new File(mc.gameDir, "saves/" + WORLD));
            } catch (java.io.IOException e) {
                failed("delete world", e);
            }
            mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(20261116L, GameType.CREATIVE, true, false, WorldType.DEFAULT));
            return true;
        });
        add(null, () -> mc.world != null && mc.player != null && ++this.waited > 4);
        add("pause", () -> {
            this.worldAt = MenuBackground.worldFrames;
            return show(new GuiIngameMenu());
        });
        add(null, () -> {
            check("pause: the blurred world under inworld_menu_background", MenuBackground.worldFrames > this.worldAt, MenuBackground.worldFrames);
            return true;
        });
        add(null, () -> show(new net.minecraftforge.fml.client.GuiModList(new GuiIngameMenu())));
        add("ingame-modlist", () -> selectCore(mc));
        add("ingame-video", () -> show(new GuiVideoSettings(new GuiIngameMenu(), mc.gameSettings)));
        add(null, () -> {
            check("in a world: lists as 1.20.5's", MenuBackground.listFrames > this.listAt, MenuBackground.listFrames);
            return true;
        });
        add(null, () -> {
            mc.gameSettings.pauseOnLostFocus = false; // the dev window is not focused: no pause menu over the charts
            mc.displayGuiScreen(null);
            com.exiledradio.backportcore.presentation.DebugKeys.Key k = com.exiledradio.backportcore.presentation.DebugKeys
                    .map(Presentation.installed()).get(org.lwjgl.input.Keyboard.KEY_3);
            check("F3 + 3 is in the map from 1.20.2, with no F3 + Q line", k != null && k.helpKey == null && k.action != null);
            mc.gameSettings.showDebugInfo = false;
            com.exiledradio.backportcore.presentation.client.NetworkCharts.toggle();
            check("F3 + 3 shows the charts and the debug screen", com.exiledradio.backportcore.presentation.client.NetworkCharts.showing()
                    && mc.gameSettings.showDebugInfo && !mc.gameSettings.showLagometer);
            io.netty.channel.embedded.EmbeddedChannel ch = new io.netty.channel.embedded.EmbeddedChannel(
                    new com.exiledradio.backportcore.presentation.client.NetworkCharts.Counter());
            ch.writeInbound(io.netty.buffer.Unpooled.wrappedBuffer(new byte[1234]));
            ch.finishAndReleaseAll();
            return true;
        });
        add(null, () -> {
            BackportCore.LOGGER.info("MENU network charts: {} pings, {} pongs, {} samples",
                    com.exiledradio.backportcore.presentation.client.NetworkCharts.pings,
                    com.exiledradio.backportcore.presentation.client.NetworkCharts.pongs,
                    com.exiledradio.backportcore.presentation.client.NetworkCharts.PING.size());
            return true;
        });
        // A chart 213 wide shows only the newest 211 of 240 samples (vanilla's skip), so the shot waits for 29.
        add("network-charts", () -> {
            com.exiledradio.backportcore.presentation.DebugCharts.Samples b = com.exiledradio.backportcore.presentation.client.NetworkCharts.BANDWIDTH;
            boolean counted = false;
            for (int i = 0; i < b.size(); i++) {
                counted |= b.get(i) == 1234;
            }
            check("bandwidth: the bytes a connection receives are logged in the tick", counted, b.size());
            check("ping: the integrated server (with Core) answers each tick's request",
                    com.exiledradio.backportcore.presentation.client.NetworkCharts.pongs > 5
                            && com.exiledradio.backportcore.presentation.client.NetworkCharts.PING.size() > 5,
                    com.exiledradio.backportcore.presentation.client.NetworkCharts.pings + " sent, "
                            + com.exiledradio.backportcore.presentation.client.NetworkCharts.pongs + " back");
            check("charts: drawn", com.exiledradio.backportcore.presentation.client.NetworkCharts.frames > 0);
            String line = com.exiledradio.backportcore.presentation.client.NetworkCharts.chartsLine(mc);
            check("the debug line is 1.20.2's", line.equals("Debug charts: [F3+1] Profiler hidden; [F3+2] FPS + TPS hidden; [F3+3] Ping visible"), line);
            return true;
        });
        add(null, () -> {
            mc.gameSettings.showLagometer = true;
            return true;
        });
        add(null, () -> {
            check("F3 + 2's charts turn F3 + 3's off", !com.exiledradio.backportcore.presentation.client.NetworkCharts.showing());
            mc.gameSettings.showLagometer = false;
            mc.gameSettings.showDebugInfo = false;
            return true;
        });
        add("inventory", () -> {
            show(new GuiInventory(mc.player));
            this.worldAt = MenuBackground.worldFrames;
            return true;
        });
        add(null, () -> {
            check("inventory: the transparent gradient kept (no blur)", MenuBackground.worldFrames == this.worldAt,
                    MenuBackground.worldFrames + " after " + this.worldAt);
            check("Narrator Hotkey shows from 1.20.2, on", Presentation.NARRATOR_HOTKEY.isShown() && PresentationHooks.narratorHotkeyOn());
            Presentation.NARRATOR_HOTKEY.set(false);
            check("Narrator Hotkey off: Ctrl + B is not the narrator's", !PresentationHooks.narratorHotkeyOn());
            Presentation.NARRATOR_HOTKEY.set(true);
            mc.displayGuiScreen(null);
            return true;
        });
        creativeTabs(mc);
    }

    // ---- 1.19.3's creative tabs (creative.client.ModernTabs, P47) ----

    private CreativeTabs late;
    private boolean inventoryWas;

    private void creativeTabs(Minecraft mc) {
        add(null, () -> {
            check("P47 applied", "applied".equals(Asm.state("P47")), Asm.state("P47"));
            check("1.19.3's tabs are installed and on (1.20.5 or later)", ModernTabs.installed() && ModernTabs.active(),
                    Presentation.newest(com.exiledradio.backportcore.presentation.LookArea.INVENTORY));
            // A tab another mod makes after Core's: it pages as if Core's were not in the array.
            this.late = new CreativeTabs("backportcore_selftest_late") {
                @Override
                public ItemStack createIcon() {
                    return new ItemStack(Items.APPLE);
                }
            };
            int mods = 0;
            for (CreativeTabs t : CreativeTabs.CREATIVE_TAB_ARRAY) {
                if (t.getIndex() >= 12 && !(t instanceof ModernTab)) {
                    mods++;
                }
            }
            int compact = CreativeScreenHooks.compact(this.late);
            check("a late mod tab keeps the place it would have had (index " + this.late.getIndex() + ")", compact == 12 + mods - 1, compact);
            return true;
        });
        add("creative-building", () -> show(new GuiContainerCreative(mc.player)));
        add(null, () -> {
            GuiContainerCreative gui = creative(mc);
            CreativeTabs open = gui == null ? null : CreativeTabs.CREATIVE_TAB_ARRAY[gui.getSelectedTabIndex()];
            check("the creative screen opens on 1.19.3's Building Blocks", open == ModernTabs.tab(ModernTabs.Def.BUILDING_BLOCKS), open);
            List<ItemStack> items = items(gui);
            check("Building Blocks starts with the oak log (1.19.3's order)",
                    items.size() > 100 && items.get(0).getItem() == Item.getItemFromBlock(Blocks.LOG), describe(items, 4));
            Object[] page = CreativeScreenHooks.tabsOnPage(CreativeTabs.CREATIVE_TAB_ARRAY, 0, 12);
            check("the first page: 10 of 1.19.3's tabs (Operator Utilities off), Saved Hotbars, Search, the inventory", page.length == 13, page.length);
            Object[] page1 = CreativeScreenHooks.tabsOnPage(CreativeTabs.CREATIVE_TAB_ARRAY, 12, Math.min(22, CreativeTabs.CREATIVE_TAB_ARRAY.length));
            check("the late mod tab is on page 2 (with no other mod tabs, its first slot)", Arrays.asList(page1).contains(this.late), Arrays.toString(page1));
            check("getSubItems untouched: stone's tab is still 1.12.2's Building Blocks",
                    Item.getItemFromBlock(Blocks.STONE).getCreativeTab() == CreativeTabs.BUILDING_BLOCKS, "");
            return true;
        });
        tab(mc, "creative-colored", ModernTabs.Def.COLORED_BLOCKS, "minecraft:wool@0");
        tab(mc, "creative-natural", ModernTabs.Def.NATURAL_BLOCKS, "minecraft:grass@0");
        tab(mc, "creative-functional", ModernTabs.Def.FUNCTIONAL_BLOCKS, "minecraft:torch@0");
        tab(mc, "creative-redstone", ModernTabs.Def.REDSTONE_BLOCKS, "minecraft:redstone@0");
        tab(mc, "creative-tools", ModernTabs.Def.TOOLS_AND_UTILITIES, "minecraft:wooden_shovel@0");
        tab(mc, "creative-combat", ModernTabs.Def.COMBAT, "minecraft:wooden_sword@0");
        tab(mc, "creative-food", ModernTabs.Def.FOOD_AND_DRINKS, "minecraft:apple@0");
        tab(mc, "creative-ingredients", ModernTabs.Def.INGREDIENTS, "minecraft:coal@0");
        add(null, () -> {
            List<ItemStack> items = items(creative(mc));
            int books = 0;
            for (ItemStack s : items) {
                books += s.getItem() == Items.ENCHANTED_BOOK ? 1 : 0;
            }
            int enchantments = 0;
            for (net.minecraft.enchantment.Enchantment e : net.minecraft.enchantment.Enchantment.REGISTRY) {
                enchantments += e.type != null ? 1 : 0;
            }
            int lastBook = -1;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getItem() == Items.ENCHANTED_BOOK) {
                    lastBook = i;
                }
            }
            // After the books only stacks no table lists (in this run, none from vanilla).
            StringBuilder after = new StringBuilder();
            for (int i = lastBook + 1; i < items.size(); i++) {
                after.append(' ').append(key(items.get(i)));
            }
            check("Ingredients has every enchantment's book at its top level, last of 1.19.3's items", books == enchantments && lastBook >= 0,
                    books + " of " + enchantments + "; after them:" + after);
            return true;
        });
        tab(mc, "creative-eggs", ModernTabs.Def.SPAWN_EGGS, null);
        add(null, () -> {
            List<ItemStack> items = items(creative(mc));
            boolean eggs = !items.isEmpty();
            for (ItemStack s : items) {
                eggs &= s.getItem() instanceof net.minecraft.item.ItemMonsterPlacer;
            }
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < Math.min(4, items.size()); i++) {
                names.append(' ').append(net.minecraft.item.ItemMonsterPlacer.getNamedIdFrom(items.get(i)));
            }
            // 1.19.3's eggs are by name: the allay (Wild Update) before the bat, else the bat first. 26.3's (Game
            // Drops) start with the farm animals: chicken, cow, pig, sheep.
            String first = com.exiledradio.backportcore.presentation.Presentation.isAtLeast(
                    com.exiledradio.backportcore.presentation.LookArea.INVENTORY, "26.3") ? "chicken"
                    : Loader.isModLoaded("wildupdatebackport") ? "allay" : "bat";
            String icon = com.exiledradio.backportcore.presentation.Presentation.isAtLeast(
                    com.exiledradio.backportcore.presentation.LookArea.INVENTORY, "1.21.5") ? "creeper" : "pig";
            check("Spawn Eggs' tab icon is the " + icon + " spawn egg (the creeper's from 1.21.5)", icon.equals(ModernTabs.spawnEggsIcon()),
                    ModernTabs.spawnEggsIcon());
            check("Spawn Eggs lists spawn eggs only, in the installed version's order (" + first + " first)", eggs
                    && String.valueOf(net.minecraft.item.ItemMonsterPlacer.getNamedIdFrom(items.get(0))).endsWith(":" + first), names);
            // Every stack the nine 1.12.2 tabs list is in some 1.19.3 tab.
            Set<String> listed = new HashSet<String>();
            for (ModernTabs.Def d : ModernTabs.Def.values()) {
                NonNullList<ItemStack> l = NonNullList.create();
                ModernTabs.tab(d).displayAllRelevantItems(l);
                for (ItemStack s : l) {
                    listed.add(key(s));
                }
            }
            List<String> lost = new ArrayList<String>();
            for (CreativeTabs t : new CreativeTabs[] {CreativeTabs.BUILDING_BLOCKS, CreativeTabs.DECORATIONS, CreativeTabs.REDSTONE,
                    CreativeTabs.TRANSPORTATION, CreativeTabs.MISC, CreativeTabs.FOOD, CreativeTabs.TOOLS, CreativeTabs.COMBAT, CreativeTabs.BREWING}) {
                NonNullList<ItemStack> l = NonNullList.create();
                t.displayAllRelevantItems(l);
                for (ItemStack s : l) {
                    if (s.getItem() != Items.ENCHANTED_BOOK && !listed.contains(key(s))) {
                        lost.add(key(s));
                    }
                }
            }
            check("every stack of 1.12.2's nine tabs is in a 1.19.3 tab", lost.isEmpty(), lost);
            return true;
        });
        add("creative-search", () -> {
            selectTab(mc, CreativeTabs.SEARCH);
            List<ItemStack> items = items(creative(mc));
            check("Search lists as before (1.12.2's search tab)", items.size() > 500, items.size());
            check("the search tooltip names 1.19.3's tab: stone is in Building Blocks",
                    CreativeScreenHooks.searchTooltipTab(Item.getItemFromBlock(Blocks.STONE)) == ModernTabs.tab(ModernTabs.Def.BUILDING_BLOCKS), "");
            return true;
        });
        add("creative-inventory", () -> selectTab(mc, CreativeTabs.INVENTORY));
        add("creative-page2", () -> {
            setPage(1);
            return selectTab(mc, this.late);
        });
        add(null, () -> {
            setPage(0);
            Presentation.OPERATOR_ITEMS_TAB.set(true);
            check("Operator Items Tab shows from 1.19.3", Presentation.OPERATOR_ITEMS_TAB.isShown(), "");
            mc.player.setPermissionLevel(4); // as an operator (the test world has no cheats)
            check("the player may use commands", mc.player.canUseCommand(2, ""), "");
            return show(new GuiContainerCreative(mc.player));
        });
        add("creative-op", () -> selectTab(mc, ModernTabs.tab(ModernTabs.Def.OP_BLOCKS)));
        add(null, () -> {
            List<ItemStack> items = items(creative(mc));
            check("Operator Utilities: the command block first, the barrier last (1.12.2 has no jigsaw, debug stick or light)", !items.isEmpty()
                    && items.get(0).getItem() == Item.getItemFromBlock(Blocks.COMMAND_BLOCK)
                    && key(items.get(items.size() - 1)).equals("minecraft:barrier@0"), describe(items, 8));
            check("the first page has Operator Utilities", CreativeScreenHooks.tabsOnPage(CreativeTabs.CREATIVE_TAB_ARRAY, 0, 12).length == 14, "");
            Presentation.OPERATOR_ITEMS_TAB.set(false);
            this.inventoryWas = com.exiledradio.backportcore.CoreConfig.lookAndFeel.inventory;
            com.exiledradio.backportcore.CoreConfig.lookAndFeel.inventory = false;
            check("the inventory part off: 1.19.3's tabs off", !ModernTabs.active(), "");
            mc.refreshResources(); // as the Look & Feel switch does: 1.12.2's tab sprites back
            return show(new GuiContainerCreative(mc.player));
        });
        add("creative-1-12-2", () -> {
            GuiContainerCreative gui = creative(mc);
            CreativeTabs open = gui == null ? null : CreativeTabs.CREATIVE_TAB_ARRAY[gui.getSelectedTabIndex()];
            check("off, the screen opens on 1.12.2's Building Blocks", open != null && open.getIndex() == CreativeTabs.BUILDING_BLOCKS.getIndex(), open);
            Object[] page = CreativeScreenHooks.tabsOnPage(CreativeTabs.CREATIVE_TAB_ARRAY, 0, 12);
            boolean ours = false;
            for (Object o : page) {
                ours |= o instanceof ModernTab;
            }
            check("off, the first page is 1.12.2's twelve", page.length == 12 && !ours, page.length);
            check("off, the added tabs are not counted as pages",
                    CreativeScreenHooks.tabCount(CreativeTabs.CREATIVE_TAB_ARRAY) == CreativeTabs.CREATIVE_TAB_ARRAY.length - 11, "");
            return true;
        });
        add(null, () -> {
            com.exiledradio.backportcore.CoreConfig.lookAndFeel.inventory = this.inventoryWas;
            mc.refreshResources();
            mc.displayGuiScreen(null);
            return true;
        });
    }

    private void tab(Minecraft mc, String shot, ModernTabs.Def def, String first) {
        add(shot, () -> selectTab(mc, ModernTabs.tab(def)));
        if (first != null) {
            add(null, () -> {
                List<ItemStack> items = items(creative(mc));
                check(def.id + " starts with " + first + " (1.19.3's first item 1.12.2 has)", !items.isEmpty() && key(items.get(0)).equals(first),
                        describe(items, 4));
                return true;
            });
        }
    }

    private boolean selectTab(Minecraft mc, CreativeTabs tab) {
        GuiContainerCreative gui = creative(mc);
        if (gui == null) {
            check("the creative screen is open", false, mc.currentScreen);
            return true;
        }
        try {
            java.lang.reflect.Method set = net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(GuiContainerCreative.class,
                    "setCurrentCreativeTab", "func_147050_b", CreativeTabs.class);
            set.invoke(gui, tab);
        } catch (ReflectiveOperationException e) {
            failed("select " + tab, e);
        }
        return true;
    }

    private void setPage(int page) {
        try {
            net.minecraftforge.fml.relauncher.ReflectionHelper.findField(GuiContainerCreative.class, "tabPage").setInt(null, page);
        } catch (ReflectiveOperationException e) {
            failed("page " + page, e);
        }
    }

    @javax.annotation.Nullable
    private static GuiContainerCreative creative(Minecraft mc) {
        return mc.currentScreen instanceof GuiContainerCreative ? (GuiContainerCreative) mc.currentScreen : null;
    }

    private static List<ItemStack> items(@javax.annotation.Nullable GuiContainerCreative gui) {
        if (gui == null) {
            return new ArrayList<ItemStack>();
        }
        return ((GuiContainerCreative.ContainerCreative) gui.inventorySlots).itemList;
    }

    private static String key(ItemStack s) {
        return s.getItem().getRegistryName() + "@" + s.getMetadata();
    }

    private static String describe(List<ItemStack> items, int n) {
        StringBuilder b = new StringBuilder(items.size() + ":");
        for (int i = 0; i < Math.min(n, items.size()); i++) {
            b.append(' ').append(key(items.get(i)));
        }
        return b.toString();
    }

    private static boolean show(GuiScreen screen) {
        Minecraft.getMinecraft().displayGuiScreen(screen);
        return true;
    }

    private static boolean colorMaskOn() {
        ByteBuffer b = BufferUtils.createByteBuffer(16);
        GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, b);
        return b.get(0) != 0 && b.get(1) != 0 && b.get(2) != 0;
    }

    private boolean selectCore(Minecraft mc) {
        try {
            java.lang.reflect.Field f = net.minecraftforge.fml.client.GuiModList.class.getDeclaredField("mods");
            f.setAccessible(true);
            List<?> mods = (List<?>) f.get(mc.currentScreen);
            for (int i = 0; i < mods.size(); i++) {
                if (BackportCore.MOD_ID.equals(((net.minecraftforge.fml.common.ModContainer) mods.get(i)).getModId())) {
                    ((net.minecraftforge.fml.client.GuiModList) mc.currentScreen).selectModIndex(i);
                }
            }
        } catch (ReflectiveOperationException e) {
            failed("modlist", e);
        }
        return true;
    }

    private void add(String name, Supplier<Boolean> step) {
        this.names.add(name);
        this.steps.add(step);
    }

    private void check(String what, boolean ok, Object detail) {
        if (ok) {
            this.pass++;
            BackportCore.LOGGER.info("MENU PASS {} ({})", what, detail);
        } else {
            this.fail++;
            BackportCore.LOGGER.error("MENU FAIL {} ({})", what, detail);
        }
    }

    private void check(String what, boolean ok) {
        check(what, ok, "");
    }

    private void failed(String what, Throwable t) {
        this.fail++;
        BackportCore.LOGGER.error("MENU FAIL {}", what, t);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (this.step < 0) {
            if (mc.currentScreen instanceof GuiMainMenu && ++this.ticks > 100) {
                this.step = 0;
                this.ticks = 0;
                run();
            }
            return;
        }
        if (this.shot != null || ++this.ticks < 20) {
            return;
        }
        this.ticks = 0;
        this.step++;
        if (this.step >= this.steps.size()) {
            BackportCore.LOGGER.info("MENU SHOTS DONE pass={} fail={}", this.pass, this.fail);
            mc.shutdown();
            return;
        }
        run();
    }

    private void run() {
        boolean done;
        try {
            done = this.steps.get(this.step).get();
        } catch (RuntimeException e) {
            failed("step " + this.step, e);
            done = true;
        }
        if (!done) {
            this.step--; // ask again next time
            return;
        }
        this.shot = this.names.get(this.step);
    }

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.shot == null) {
            return;
        }
        if (++this.ticks < 10) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        ScreenShotHelper.saveScreenshot(mc.gameDir, "menu-" + this.shot + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        BackportCore.LOGGER.info("MENU SHOT {} ({})", this.shot, mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName());
        this.shot = null;
        this.ticks = 0;
    }
}

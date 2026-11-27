package com.exiledradio.backportcore.presentation.versions.client;

import com.exiledradio.backportcore.presentation.LookArea;
import com.exiledradio.backportcore.presentation.Presentation;
import com.exiledradio.backportcore.presentation.client.FogPipeline;
import com.exiledradio.backportcore.presentation.versions.Look264;
import com.exiledradio.backportcore.presentation.versions.look264.client.OtherPlayersScreen;
import com.exiledradio.backportcore.presentation.versions.look264.client.PlayerMutes;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.recipebook.GuiButtonRecipeTab;
import net.minecraft.client.gui.recipebook.GuiRecipeBook;
import net.minecraft.client.gui.recipebook.IRecipeShownListener;
import net.minecraft.client.gui.recipebook.RecipeBookPage;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

/**
 * The client side of {@code versions.Look264}: Improved Fog's step, the Repeat Recipe/Trade and Other Players keys, the
 * recipe book's tooltips, chat muting.
 */
public final class Look264Client {

    /** 26.4's {@code keyRepeatRecipeOrTrade}: R, Inventory. */
    public static final KeyBinding REPEAT = new KeyBinding("key.repeatRecipeOrTrade", KeyConflictContext.GUI, Keyboard.KEY_R,
            "key.categories.inventory");
    /** 26.4's {@code keyOtherPlayers}: P, Multiplayer. */
    public static final KeyBinding OTHER_PLAYERS = new KeyBinding("key.otherPlayers", KeyConflictContext.IN_GAME, Keyboard.KEY_P,
            "key.categories.multiplayer");

    private static final Field PAGE = ReflectionHelper.findField(GuiRecipeBook.class, "recipeBookPage", "field_193022_s");
    private static final Field TABS = ReflectionHelper.findField(GuiRecipeBook.class, "recipeTabs", "field_193018_j");

    private Look264Client() {
    }

    static void init() {
        ClientRegistry.registerKeyBinding(REPEAT);
        ClientRegistry.registerKeyBinding(OTHER_PLAYERS);
        MinecraftForge.EVENT_BUS.register(new Look264Client());
        MinecraftForge.EVENT_BUS.register(new PlayerMutes());
        FogPipeline.add("26.4", "Improved Fog: the fog is the sky's color", new FogPipeline.Step() {
            @Override
            public void colors(FogPipeline.Colors colors) {
                improvedFog(colors);
            }
        });
    }

    /** 26.4's Improved Fog, on 1.12.2's linear fog: the clear color the terrain fades into is the sky's own. */
    static void improvedFog(FogPipeline.Colors c) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!Look264.IMPROVED_FOG.isShown() || !Look264.IMPROVED_FOG.get() || mc.world == null || c.entity == null
                || !mc.world.provider.isSurfaceWorld() || c.state.getMaterial() == Material.WATER || c.state.getMaterial() == Material.LAVA
                || mc.player != null && mc.player.isPotionActive(net.minecraft.init.MobEffects.BLINDNESS)) {
            return;
        }
        Vec3d sky = mc.world.getSkyColor(c.entity, (float) c.partialTicks);
        c.red = (float) sky.x;
        c.green = (float) sky.y;
        c.blue = (float) sky.z;
    }

    // ---- Other Players ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || mc.player == null) {
            return;
        }
        while (OTHER_PLAYERS.isPressed()) {
            if (mc.currentScreen == null && OtherPlayersScreen.available()) {
                mc.displayGuiScreen(new OtherPlayersScreen(null));
            }
        }
    }

    // ---- Repeat Recipe ---------------------------------------------------------------------------------------------

    static boolean repeatShown() {
        return Presentation.isAtLeast(LookArea.INVENTORY, "26.4");
    }

    @SubscribeEvent
    public void key(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!repeatShown() || !Keyboard.getEventKeyState() || Keyboard.getEventKey() != REPEAT.getKeyCode() || REPEAT.getKeyCode() == 0) {
            return;
        }
        if (repeatLastRecipe(event.getGui(), GuiScreen.isShiftKeyDown())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void mouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        int code = REPEAT.getKeyCode();
        if (!repeatShown() || code >= 0 || !Mouse.getEventButtonState() || Mouse.getEventButton() != code + 100) {
            return;
        }
        if (repeatLastRecipe(event.getGui(), GuiScreen.isShiftKeyDown())) {
            event.setCanceled(true);
        }
    }

    /**
     * {@code AbstractRecipeBookScreen.tryRepeatLastRecipe}: with the recipe book open and not a spectator, the book's last
     * clicked recipe is placed again ({@code repeatLastRecipe}), with Shift as many as fit. Not while a text field has
     * the keyboard (the book's search box).
     */
    public static boolean repeatLastRecipe(GuiScreen gui, boolean max) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!(gui instanceof IRecipeShownListener) || !(gui instanceof GuiContainer) || mc.player == null || mc.player.isSpectator()) {
            return false;
        }
        GuiRecipeBook book = ((IRecipeShownListener) gui).func_194310_f();
        if (book == null || !book.isVisible() || searchFocused(book)) {
            return false;
        }
        try {
            IRecipe recipe = ((RecipeBookPage) PAGE.get(book)).getLastClickedRecipe();
            if (recipe == null) {
                return false;
            }
            mc.playerController.func_194338_a(mc.player.openContainer.windowId, recipe, max, mc.player);
            mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(
                    net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    private static final Field SEARCH = ReflectionHelper.findField(GuiRecipeBook.class, "searchBar", "field_193962_q");

    private static boolean searchFocused(GuiRecipeBook book) {
        try {
            net.minecraft.client.gui.GuiTextField f = (net.minecraft.client.gui.GuiTextField) SEARCH.get(book);
            return f != null && f.isFocused();
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    // ---- recipe book tooltips --------------------------------------------------------------------------------------

    /** 26.4's {@code ItemTabButton} tooltips and the recipe book button's "Recipe Book" ({@code gui.recipebook.tooltip}). */
    @SubscribeEvent
    public void draw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!repeatShown() || !(event.getGui() instanceof IRecipeShownListener) || !(event.getGui() instanceof GuiContainer)) {
            return;
        }
        GuiScreen gui = event.getGui();
        int mx = event.getMouseX(), my = event.getMouseY();
        for (GuiButton b : buttons(gui)) {
            if (b.id == 10 && b.visible && b.isMouseOver()) {
                gui.drawHoveringText(Collections.singletonList(I18n.format("gui.recipebook.tooltip")), mx, my);
                return;
            }
        }
        GuiRecipeBook book = ((IRecipeShownListener) gui).func_194310_f();
        if (book == null || !book.isVisible()) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            List<GuiButtonRecipeTab> tabs = (List<GuiButtonRecipeTab>) TABS.get(book);
            for (GuiButtonRecipeTab tab : tabs) {
                if (tab.visible && mx >= tab.x && my >= tab.y && mx < tab.x + tab.width && my < tab.y + tab.height) {
                    gui.drawHoveringText(Collections.singletonList(I18n.format(tabName(tab.getCategory()))), mx, my);
                    return;
                }
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    /** 26.4's names of 1.12.2's five crafting tabs. */
    static String tabName(CreativeTabs tab) {
        if (tab == CreativeTabs.SEARCH) {
            return "gui.recipebook.tab.all";
        }
        if (tab == CreativeTabs.TOOLS) {
            return "recipe_book_category.minecraft.crafting_equipment";
        }
        if (tab == CreativeTabs.BUILDING_BLOCKS) {
            return "recipe_book_category.minecraft.crafting_building_blocks";
        }
        if (tab == CreativeTabs.REDSTONE) {
            return "recipe_book_category.minecraft.crafting_redstone";
        }
        return "recipe_book_category.minecraft.crafting_misc";
    }

    private static final Field BUTTONS = ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n");

    @SuppressWarnings("unchecked")
    private static List<GuiButton> buttons(GuiScreen gui) {
        try {
            return (List<GuiButton>) BUTTONS.get(gui);
        } catch (IllegalAccessException e) {
            return Collections.emptyList();
        }
    }
}

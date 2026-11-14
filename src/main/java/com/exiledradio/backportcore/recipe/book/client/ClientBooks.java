package com.exiledradio.backportcore.recipe.book.client;

import com.exiledradio.backportcore.recipe.book.BookNetwork;
import com.exiledradio.backportcore.recipe.book.SmeltingBook;
import com.exiledradio.backportcore.recipe.book.SmeltingBooks;
import com.exiledradio.backportcore.recipe.book.SmeltingRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The client's copy of each smelting book's state (open, filter, unlocked recipes), what the server sends, and the
 * vanilla furnace's screen: once the furnace book is registered, {@code GuiFurnace} opens as {@link GuiFurnaceBook}
 * (only vanilla's own class; a mod's furnace screen is left alone).
 */
@SideOnly(Side.CLIENT)
public final class ClientBooks implements BookNetwork.ClientSide {

    /** One book's state on this client. */
    public static final class State {
        public boolean open;
        public boolean filter;
        public final Set<String> unlocked = new LinkedHashSet<String>();
    }

    private static final Map<ResourceLocation, State> STATES = new HashMap<ResourceLocation, State>();

    private ClientBooks() {
    }

    public static void init() {
        BookNetwork.setClient(new ClientBooks());
        MinecraftForge.EVENT_BUS.register(new Events());
    }

    public static State state(ResourceLocation book) {
        State s = STATES.get(book);
        if (s == null) {
            s = new State();
            STATES.put(book, s);
        }
        return s;
    }

    /** The book was opened or closed, or its filter changed: kept here and told to the server. */
    public static void changed(SmeltingBook book, boolean open, boolean filter) {
        State s = state(book.id);
        s.open = open;
        s.filter = filter;
        BookNetwork.sendToServer(new BookNetwork.Settings(book.id, open, filter));
    }

    private static void refresh() {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof BookScreen) {
            ((BookScreen) screen).smeltingBook().recipesUpdated();
        }
    }

    @Override
    public void state(BookNetwork.State message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            State s = state(message.book);
            s.open = message.open;
            s.filter = message.filter;
            s.unlocked.clear();
            s.unlocked.addAll(message.recipes);
            refresh();
        });
    }

    @Override
    public void unlocked(BookNetwork.Unlocked message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            SmeltingBook book = SmeltingBooks.get(message.book);
            State s = state(message.book);
            for (String id : message.recipes) {
                if (s.unlocked.add(id) && book != null) {
                    SmeltingRecipe r = SmeltingBooks.recipe(book, id);
                    if (r != null) {
                        SmeltingToast.addOrUpdate(mc.getToastGui(), book.toastIcon, r.output);
                    }
                }
            }
            refresh();
        });
    }

    @Override
    public void ghost(BookNetwork.Ghost message) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Minecraft mc = Minecraft.getMinecraft();
            GuiScreen screen = mc.currentScreen;
            if (mc.player == null || mc.player.openContainer.windowId != message.windowId || !(screen instanceof BookScreen)) {
                return;
            }
            SmeltingBookGui gui = ((BookScreen) screen).smeltingBook();
            if (!gui.book().id.equals(message.book)) {
                return;
            }
            SmeltingRecipe r = SmeltingBooks.recipe(gui.book(), message.recipe);
            if (r != null) {
                gui.setupGhostRecipe(r, mc.player.openContainer.inventorySlots);
            }
        });
    }

    public static final class Events {
        @SubscribeEvent
        public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                STATES.clear();
                SmeltingBooks.invalidate();
            });
        }

        /** Vanilla's furnace screen, with the book. */
        @SubscribeEvent(priority = EventPriority.LOW)
        public void onOpen(GuiOpenEvent event) {
            if (event.getGui() == null || event.getGui().getClass() != GuiFurnace.class
                    || SmeltingBooks.get(SmeltingBook.FURNACE) == null) {
                return;
            }
            GuiFurnace vanilla = (GuiFurnace) event.getGui();
            try {
                InventoryPlayer player = ObfuscationReflectionHelper.getPrivateValue(GuiFurnace.class, vanilla, "field_175383_v");
                IInventory furnace = ObfuscationReflectionHelper.getPrivateValue(GuiFurnace.class, vanilla, "field_147086_v");
                event.setGui(new GuiFurnaceBook(player, furnace));
            } catch (RuntimeException e) {
                com.exiledradio.backportcore.BackportCore.LOGGER.warn("Smelting book: could not open the furnace with its book: {}", e.toString());
            }
        }
    }
}

package com.exiledradio.backportcore.recipe.book;

import com.exiledradio.backportcore.BackportCore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The smelting books (1.13's furnace book and later blocks' books): the registry, each book's recipes, the players'
 * unlocks and the server's half of the packets. The client half is {@code recipe.book.client}.
 *
 * <p>Unlocking is 1.13's: every smelting recipe unlocks once the player holds any of its inputs (1.13's recipe
 * advancements, {@code inventory_changed} with the ingredient), checked with Core's crafting recipe unlocks
 * ({@code recipe.RecipeUnlocks}, once a second, only when the inventory changed). Unlike those, it does not follow
 * Core's "unlock recipes" setting: without it a smelting book would be empty.
 */
public final class SmeltingBooks {

    private static final Map<ResourceLocation, SmeltingBook> BOOKS = new LinkedHashMap<ResourceLocation, SmeltingBook>();
    private static final Map<ResourceLocation, List<SmeltingRecipe>> RECIPES = new LinkedHashMap<ResourceLocation, List<SmeltingRecipe>>();

    private SmeltingBooks() {
    }

    /** Pre-init, both sides (from {@code BackportCore}). */
    public static void init() {
        BookNetwork.init();
        MinecraftForge.EVENT_BUS.register(new Events());
    }

    /** Adds a book (both sides, pre-init or init). The same id again replaces it. */
    public static synchronized void register(SmeltingBook book) {
        BOOKS.put(book.id, book);
        RECIPES.remove(book.id);
    }

    @Nullable
    public static synchronized SmeltingBook get(ResourceLocation id) {
        return BOOKS.get(id);
    }

    public static synchronized Collection<SmeltingBook> all() {
        return Collections.unmodifiableCollection(new ArrayList<SmeltingBook>(BOOKS.values()));
    }

    /** @return the book for an open container, or null */
    @Nullable
    public static synchronized SmeltingBook forContainer(@Nullable Container container) {
        for (SmeltingBook book : BOOKS.values()) {
            if (book.isFor(container)) {
                return book;
            }
        }
        return null;
    }

    /** @return the book's recipes, built from its recipe map on first use after each server start */
    public static synchronized List<SmeltingRecipe> recipes(SmeltingBook book) {
        List<SmeltingRecipe> list = RECIPES.get(book.id);
        if (list == null) {
            list = Collections.unmodifiableList(SmeltingRecipe.build(book, book.recipes.get()));
            RECIPES.put(book.id, list);
            BackportCore.LOGGER.debug("Smelting book {}: {} recipes", book.id, list.size());
        }
        return list;
    }

    @Nullable
    public static SmeltingRecipe recipe(SmeltingBook book, String id) {
        for (SmeltingRecipe r : recipes(book)) {
            if (r.id.equals(id)) {
                return r;
            }
        }
        return null;
    }

    /** Recipe maps can differ between worlds (configs, scripts): built again for each server start and connection. */
    public static synchronized void invalidate() {
        RECIPES.clear();
    }

    public static BookData data(EntityPlayer player, SmeltingBook book) {
        return BookData.of(player, book);
    }

    /**
     * Unlocks every recipe of every book that one of {@code held} is an input of (from {@code RecipeUnlocks.check}).
     *
     * @return how many were unlocked
     */
    public static int unlockHeld(EntityPlayerMP player, List<ItemStack> held) {
        int total = 0;
        for (SmeltingBook book : all()) {
            BookData data = data(player, book);
            List<String> give = new ArrayList<String>();
            for (SmeltingRecipe r : recipes(book)) {
                if (data.isUnlocked(r.id)) {
                    continue;
                }
                for (ItemStack stack : held) {
                    if (r.input.apply(stack)) {
                        give.add(r.id);
                        break;
                    }
                }
            }
            List<String> added = data.unlock(give);
            if (!added.isEmpty()) {
                total += added.size();
                BookNetwork.send(new BookNetwork.Unlocked(book.id, added), player);
            }
        }
        return total;
    }

    /** Unlocks recipes by id (commands, tests). */
    public static List<String> unlock(EntityPlayerMP player, SmeltingBook book, Collection<String> ids) {
        List<String> added = data(player, book).unlock(ids);
        if (!added.isEmpty()) {
            BookNetwork.send(new BookNetwork.Unlocked(book.id, added), player);
        }
        return added;
    }

    /** Sends a player every book's state (at login). */
    public static void sync(EntityPlayerMP player) {
        for (SmeltingBook book : all()) {
            BookData data = data(player, book);
            BookNetwork.send(new BookNetwork.State(book.id, data.isOpen(), data.isFiltering(), data.unlocked()), player);
        }
    }

    static void settings(EntityPlayerMP player, BookNetwork.Settings message) {
        SmeltingBook book = get(message.book);
        if (book == null) {
            return;
        }
        BookData data = data(player, book);
        data.setOpen(message.open);
        data.setFiltering(message.filter);
    }

    /** Server thread: a recipe clicked in an open book (1.12.2's {@code processPlaceRecipe}). */
    public static SmeltingPlacer.Result place(EntityPlayerMP player, BookNetwork.Place message) {
        player.markPlayerActive();
        Container open = player.openContainer;
        SmeltingBook book = get(message.book);
        if (book == null || player.isSpectator() || open == null || open.windowId != message.windowId
                || !open.getCanCraft(player) || !book.isFor(open)) {
            return null;
        }
        SmeltingRecipe recipe = recipe(book, message.recipe);
        if (recipe == null) {
            return null;
        }
        return place(player, book, recipe, message.placeAll);
    }

    /** Places a recipe into the player's open container and sends the slots (and the ghost, when it shows). */
    public static SmeltingPlacer.Result place(EntityPlayerMP player, SmeltingBook book, SmeltingRecipe recipe, boolean placeAll) {
        Container open = player.openContainer;
        SmeltingPlacer.Result result = new SmeltingPlacer(book, open).place(player, recipe, placeAll);
        if (result == SmeltingPlacer.Result.GHOST) {
            BookNetwork.send(new BookNetwork.Ghost(open.windowId, book.id, recipe.id), player);
        }
        // Sent now, outside any click's handling (where 1.12.2 holds back slot updates).
        open.detectAndSendChanges();
        return result;
    }

    public static final class Events {
        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.player instanceof EntityPlayerMP) {
                sync((EntityPlayerMP) event.player);
            }
        }
    }
}

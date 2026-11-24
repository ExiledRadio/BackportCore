package com.exiledradio.backportcore.selftest;

import com.exiledradio.backportcore.recipe.RecipeUnlocks;
import com.exiledradio.backportcore.recipe.book.BookData;
import com.exiledradio.backportcore.recipe.book.BookNetwork;
import com.exiledradio.backportcore.recipe.book.SmeltingBook;
import com.exiledradio.backportcore.recipe.book.SmeltingBooks;
import com.exiledradio.backportcore.recipe.book.SmeltingPlacer;
import com.exiledradio.backportcore.recipe.book.SmeltingRecipe;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.exiledradio.backportcore.selftest.SelfTestRunner.check;
import static com.exiledradio.backportcore.selftest.SelfTestRunner.then;

/**
 * The smelting books (recipe.book): the furnace book's recipes from FurnaceRecipes (one per result, 1.13's tabs),
 * 1.13's unlocking by holding an input, the server placer through a real furnace container (one more per click,
 * shift for all, a full input, another recipe's items back to the inventory, the ghost without ingredients, refusing
 * when a full output would not fit back, the slot updates sent), the per-player state through death and a save, and
 * the packets' round trips.
 */
final class SmeltingBookTests {

    private SmeltingBookTests() {
    }

    private static final class Rig {
        final EntityPlayerMP player;
        final EmbeddedChannel channel;
        final TileEntityFurnace furnace;
        final ContainerFurnace container;

        Rig(WorldServer world, String name, BlockPos pos) {
            this.player = new EntityPlayerMP(world.getMinecraftServer(), world,
                    new GameProfile(UUID.nameUUIDFromBytes(("coreselftest:book:" + name).getBytes()), "Book" + name),
                    new PlayerInteractionManager(world));
            NetworkManager manager = new NetworkManager(EnumPacketDirection.SERVERBOUND);
            this.channel = new EmbeddedChannel(manager);
            this.player.connection = new NetHandlerPlayServer(world.getMinecraftServer(), manager, this.player);
            this.player.interactionManager.setGameType(GameType.SURVIVAL);
            ObfuscationReflectionHelper.setPrivateValue(EntityPlayerMP.class, this.player, 0, "field_147101_bU");
            world.setBlockState(pos, Blocks.FURNACE.getDefaultState());
            this.furnace = (TileEntityFurnace) world.getTileEntity(pos);
            this.container = new ContainerFurnace(this.player.inventory, this.furnace);
            this.container.windowId = 7;
            this.player.openContainer = this.container;
            this.container.addListener(this.player);
            drain();
        }

        ItemStack slot(int i) {
            return this.container.getSlot(i).getStack();
        }

        int count(net.minecraft.item.Item item) {
            int n = 0;
            for (ItemStack s : this.player.inventory.mainInventory) {
                if (s.getItem() == item) {
                    n += s.getCount();
                }
            }
            return n;
        }

        /** @return the slot updates sent since the last drain */
        java.util.Map<Integer, ItemStack> drain() {
            java.util.Map<Integer, ItemStack> sent = new java.util.HashMap<Integer, ItemStack>();
            Object o;
            while ((o = this.channel.readOutbound()) != null) {
                if (o instanceof SPacketSetSlot) {
                    // The getters are client-only.
                    SPacketSetSlot p = (SPacketSetSlot) o;
                    int windowId = ObfuscationReflectionHelper.getPrivateValue(SPacketSetSlot.class, p, "field_149179_a");
                    int slot = ObfuscationReflectionHelper.getPrivateValue(SPacketSetSlot.class, p, "field_149177_b");
                    ItemStack stack = ObfuscationReflectionHelper.getPrivateValue(SPacketSetSlot.class, p, "field_149178_c");
                    if (windowId == this.container.windowId) {
                        sent.put(slot, stack);
                    }
                }
            }
            return sent;
        }
    }

    static void schedule() {
        then(1, "smelting book recipes", world -> {
            SmeltingBooks.register(SmeltingBook.furnace());
            SmeltingBook book = SmeltingBooks.get(SmeltingBook.FURNACE);
            List<SmeltingRecipe> recipes = SmeltingBooks.recipes(book);
            check("book: the furnace's recipes are FurnaceRecipes'", recipes.size() > 30, recipes.size());
            SmeltingRecipe iron = SmeltingBooks.recipe(book, "minecraft:iron_ingot");
            SmeltingRecipe nugget = SmeltingBooks.recipe(book, "minecraft:iron_nugget");
            SmeltingRecipe beef = SmeltingBooks.recipe(book, "minecraft:cooked_beef");
            SmeltingRecipe glass = SmeltingBooks.recipe(book, "minecraft:glass");
            check("book: iron ingot, nuggets, beef and glass are there", iron != null && nugget != null && beef != null && glass != null);
            check("book: one recipe per result, from every input (1.13's iron nuggets)", nugget.inputs.size() >= 9, nugget.inputs.size());
            check("book: a damaged tool is an input", nugget.input.apply(new ItemStack(Items.IRON_PICKAXE, 1, 100)));
            check("book: 1.13's tabs (food, blocks, miscellaneous)", beef.tab == 0 && glass.tab == 1 && iron.tab == 2,
                    beef.tab + " " + glass.tab + " " + iron.tab);
            check("book: the furnace's container", book.isFor(new ContainerFurnace(PoseTests.player(world, "bookc").inventory,
                    new TileEntityFurnace())));
            check("book: fuels for the ghost", book.fuels().size() > 5 && book.fuels().get(0).getItem() != Items.AIR, book.fuels().size());
        });

        then(1, "smelting book unlocks", world -> {
            SmeltingBook book = SmeltingBooks.get(SmeltingBook.FURNACE);
            EntityPlayerMP player = PoseTests.player(world, "bookunlock");
            player.inventory.addItemStackToInventory(new ItemStack(Blocks.IRON_ORE, 3));
            RecipeUnlocks.check(player);
            BookData data = SmeltingBooks.data(player, book);
            check("unlock: holding iron ore unlocks iron ingots", data.isUnlocked("minecraft:iron_ingot"));
            check("unlock: and nothing else", !data.isUnlocked("minecraft:cooked_beef") && data.unlocked().size() == 1, data.unlocked());
            player.inventory.addItemStackToInventory(new ItemStack(Items.IRON_SWORD, 1, 50));
            RecipeUnlocks.check(player);
            check("unlock: a damaged iron sword unlocks iron nuggets", data(player, book).isUnlocked("minecraft:iron_nugget"));
        });

        then(1, "smelting book without a screen", world -> {
            // 1.14's campfire: a book no container is its own, with no tabs; its recipes unlock and toast with its block.
            java.util.Map<ItemStack, ItemStack> map = new java.util.LinkedHashMap<ItemStack, ItemStack>();
            map.put(new ItemStack(Items.BEEF), new ItemStack(Items.COOKED_BEEF));
            map.put(new ItemStack(Items.CHICKEN), new ItemStack(Items.COOKED_CHICKEN));
            SmeltingBook camp = new SmeltingBook(new net.minecraft.util.ResourceLocation("backportcore", "selftest_campfire"),
                    c -> false, 0, 1, 2, () -> map, s -> false, "", new ItemStack(Blocks.TORCH));
            SmeltingBooks.register(camp);
            ContainerFurnace furnace = new ContainerFurnace(PoseTests.player(world, "bookcamp").inventory, new TileEntityFurnace());
            check("screenless: no container is its own, the furnace's still finds the furnace book", !camp.isFor(furnace)
                    && SmeltingBooks.forContainer(furnace) == SmeltingBooks.get(SmeltingBook.FURNACE), SmeltingBooks.forContainer(furnace));
            check("screenless: its recipes, no tabs", SmeltingBooks.recipes(camp).size() == 2 && camp.tabs.isEmpty(), SmeltingBooks.recipes(camp));
            EntityPlayerMP player = PoseTests.player(world, "bookcamp");
            player.inventory.addItemStackToInventory(new ItemStack(Items.BEEF, 2));
            RecipeUnlocks.check(player);
            check("screenless: holding beef unlocks it here and in the furnace book", data(player, camp).isUnlocked("minecraft:cooked_beef")
                    && !data(player, camp).isUnlocked("minecraft:cooked_chicken")
                    && data(player, SmeltingBooks.get(SmeltingBook.FURNACE)).isUnlocked("minecraft:cooked_beef"), data(player, camp).unlocked());
            check("screenless: the toast's block is its own", camp.toastIcon.getItem() == net.minecraft.item.Item.getItemFromBlock(Blocks.TORCH));
        });

        then(1, "smelting book placer", world -> {
            SmeltingBook book = SmeltingBooks.get(SmeltingBook.FURNACE);
            BlockPos pos = world.getSpawnPoint().up(40).east(7);
            Rig rig = new Rig(world, "placer", pos);
            SmeltingRecipe iron = SmeltingBooks.recipe(book, "minecraft:iron_ingot");
            SmeltingRecipe beef = SmeltingBooks.recipe(book, "minecraft:cooked_beef");
            rig.player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.IRON_ORE, 10));

            check("place: a locked recipe does nothing", SmeltingBooks.place(rig.player, book, iron, false) == SmeltingPlacer.Result.LOCKED
                    && rig.slot(0).isEmpty());
            SmeltingBooks.unlock(rig.player, book, Arrays.asList(iron.id, beef.id));

            SmeltingPlacer.Result r = SmeltingBooks.place(rig.player, book, iron, false);
            check("place: a click places one", r == SmeltingPlacer.Result.PLACED && rig.slot(0).getCount() == 1 && rig.count(net.minecraft.item.Item.getItemFromBlock(Blocks.IRON_ORE)) == 9,
                    r + " " + rig.slot(0));
            java.util.Map<Integer, ItemStack> sent = rig.drain();
            check("place: the input slot's update is sent", sent.containsKey(0) && sent.get(0).getCount() == 1, sent.keySet());
            check("place: the inventory slot's update is sent", sent.containsKey(3 + 27) && sent.get(3 + 27).getCount() == 9, sent.keySet());

            r = SmeltingBooks.place(rig.player, book, iron, false);
            check("place: a second click adds one", r == SmeltingPlacer.Result.PLACED && rig.slot(0).getCount() == 2, r + " " + rig.slot(0));
            r = SmeltingBooks.place(rig.player, book, iron, true);
            check("place: shift places everything", r == SmeltingPlacer.Result.PLACED && rig.slot(0).getCount() == 10
                    && rig.count(net.minecraft.item.Item.getItemFromBlock(Blocks.IRON_ORE)) == 0, r + " " + rig.slot(0));
            r = SmeltingBooks.place(rig.player, book, iron, false);
            check("place: nothing more to place", r == SmeltingPlacer.Result.NOTHING && rig.slot(0).getCount() == 10, r);

            // Another recipe: the input and the output go back to the inventory first.
            rig.container.getSlot(2).putStack(new ItemStack(Items.IRON_INGOT, 3));
            rig.player.inventory.setInventorySlotContents(5, new ItemStack(Items.BEEF, 5));
            r = SmeltingBooks.place(rig.player, book, beef, false);
            check("place: another recipe returns the input and output", r == SmeltingPlacer.Result.PLACED
                    && rig.slot(0).getItem() == Items.BEEF && rig.slot(0).getCount() == 1 && rig.slot(2).isEmpty()
                    && rig.count(net.minecraft.item.Item.getItemFromBlock(Blocks.IRON_ORE)) == 10 && rig.count(Items.IRON_INGOT) == 3
                    && rig.count(Items.BEEF) == 4, r + " " + rig.slot(0) + " " + rig.slot(2));

            // Without the ingredients: everything back, and the ghost.
            rig.player.inventory.clear();
            rig.container.getSlot(1).putStack(new ItemStack(Items.COAL, 2));
            r = SmeltingBooks.place(rig.player, book, iron, false);
            check("place: no ingredients empties the furnace and shows the ghost", r == SmeltingPlacer.Result.GHOST
                    && rig.slot(0).isEmpty() && rig.slot(1).isEmpty() && rig.count(Items.BEEF) == 1 && rig.count(Items.COAL) == 2, r);

            // A full output that would not fit back: refused, nothing lost.
            rig.player.inventory.clear();
            rig.player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.IRON_ORE, 1));
            for (int i = 1; i < 36; i++) {
                rig.player.inventory.setInventorySlotContents(i, new ItemStack(Blocks.DIRT, 64));
            }
            rig.container.getSlot(2).putStack(new ItemStack(Items.COOKED_PORKCHOP, 64));
            r = SmeltingBooks.place(rig.player, book, iron, false);
            check("place: refused while a full output would not fit back", r == SmeltingPlacer.Result.REFUSED
                    && rig.slot(2).getCount() == 64 && rig.slot(0).isEmpty() && rig.count(net.minecraft.item.Item.getItemFromBlock(Blocks.IRON_ORE)) == 1, r);
            rig.player.inventory.setInventorySlotContents(20, ItemStack.EMPTY);
            r = SmeltingBooks.place(rig.player, book, iron, false);
            check("place: with room, the output goes back and the ore goes in", r == SmeltingPlacer.Result.PLACED
                    && rig.slot(2).isEmpty() && rig.slot(0).getCount() == 1 && rig.count(Items.COOKED_PORKCHOP) == 64, r);

            // Through the packet's own checks.
            rig.player.inventory.setInventorySlotContents(0, new ItemStack(Blocks.IRON_ORE, 4));
            SmeltingPlacer.Result wrongWindow = SmeltingBooks.place(rig.player, new BookNetwork.Place(9, book.id, iron.id, false));
            SmeltingPlacer.Result right = SmeltingBooks.place(rig.player, new BookNetwork.Place(7, book.id, iron.id, true));
            check("place: another window's click is ignored, this one's placed", wrongWindow == null
                    && right == SmeltingPlacer.Result.PLACED && rig.slot(0).getCount() == 5, wrongWindow + " " + right + " " + rig.slot(0));
            world.setBlockToAir(pos);
        });

        then(1, "smelting book state", world -> {
            SmeltingBook book = SmeltingBooks.get(SmeltingBook.FURNACE);
            EntityPlayerMP player = PoseTests.player(world, "bookstate");
            BookData data = SmeltingBooks.data(player, book);
            data.setOpen(true);
            data.setFiltering(true);
            data.unlock(Collections.singleton("minecraft:glass"));
            EntityPlayerMP respawned = PoseTests.player(world, "bookstate");
            respawned.copyFrom(player, false);
            BookData after = SmeltingBooks.data(respawned, book);
            check("state: kept through death", after.isOpen() && after.isFiltering() && after.isUnlocked("minecraft:glass"));
            NBTTagCompound saved = new NBTTagCompound();
            player.writeToNBT(saved);
            EntityPlayerMP loaded = PoseTests.player(world, "bookstate");
            loaded.readFromNBT(saved);
            BookData read = SmeltingBooks.data(loaded, book);
            check("state: kept through a save", read.isOpen() && read.isFiltering() && read.isUnlocked("minecraft:glass"));
            data.setOpen(false);
            check("state: closing is kept", !SmeltingBooks.data(player, book).isOpen());
        });

        then(1, "smelting book packets", world -> {
            BookNetwork.State st = SuggestionTests.roundTrip(new BookNetwork.State(SmeltingBook.FURNACE, true, false,
                    Arrays.asList("minecraft:glass", "minecraft:iron_ingot")), new BookNetwork.State());
            check("packet: book state", SmeltingBook.FURNACE.equals(st.book) && st.open && !st.filter
                    && st.recipes.equals(Arrays.asList("minecraft:glass", "minecraft:iron_ingot")));
            BookNetwork.Unlocked un = SuggestionTests.roundTrip(new BookNetwork.Unlocked(SmeltingBook.FURNACE,
                    Collections.singleton("minecraft:stone")), new BookNetwork.Unlocked());
            check("packet: unlocked", un.recipes.equals(Collections.singletonList("minecraft:stone")));
            BookNetwork.Ghost gh = SuggestionTests.roundTrip(new BookNetwork.Ghost(42, SmeltingBook.FURNACE, "minecraft:coal"), new BookNetwork.Ghost());
            check("packet: ghost", gh.windowId == 42 && "minecraft:coal".equals(gh.recipe));
            BookNetwork.Settings se = SuggestionTests.roundTrip(new BookNetwork.Settings(SmeltingBook.FURNACE, false, true), new BookNetwork.Settings());
            check("packet: settings", !se.open && se.filter);
            BookNetwork.Place pl = SuggestionTests.roundTrip(new BookNetwork.Place(100, SmeltingBook.FURNACE, "minecraft:iron_ingot", true),
                    new BookNetwork.Place());
            check("packet: place", pl.windowId == 100 && pl.placeAll && "minecraft:iron_ingot".equals(pl.recipe));
            check("recipe id: meta, count and nbt", "minecraft:dye@2".equals(SmeltingRecipe.idOf(new ItemStack(Items.DYE, 1, 2)))
                    && "minecraft:coal*2".equals(SmeltingRecipe.idOf(new ItemStack(Items.COAL, 2))), SmeltingRecipe.idOf(new ItemStack(Items.DYE, 1, 2)));
        });
    }

    private static BookData data(EntityPlayerMP player, SmeltingBook book) {
        return SmeltingBooks.data(player, book);
    }
}

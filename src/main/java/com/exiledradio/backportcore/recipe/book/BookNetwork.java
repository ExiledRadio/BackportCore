package com.exiledradio.backportcore.recipe.book;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The smelting books' packets, as 1.12.2's crafting book has {@code SPacketRecipeBook}, {@code CPacketRecipeInfo},
 * {@code CPacketPlaceRecipe} and {@code SPacketPlaceGhostRecipe} (which name crafting recipes only).
 */
public final class BookNetwork {

    public static final String CHANNEL = "backportcore_rb";
    static final int MAX_RECIPES = 32768;

    private static SimpleNetworkWrapper channel;

    /** Where the client side handles what the server sends (set by {@code recipe.book.client.ClientBooks}). */
    public interface ClientSide {
        void state(State message);

        void unlocked(Unlocked message);

        void ghost(Ghost message);
    }

    @Nullable
    static volatile ClientSide client;

    private BookNetwork() {
    }

    static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(StateHandler.class, State.class, 0, Side.CLIENT);
        channel.registerMessage(UnlockedHandler.class, Unlocked.class, 1, Side.CLIENT);
        channel.registerMessage(GhostHandler.class, Ghost.class, 2, Side.CLIENT);
        channel.registerMessage(SettingsHandler.class, Settings.class, 3, Side.SERVER);
        channel.registerMessage(PlaceHandler.class, Place.class, 4, Side.SERVER);
    }

    public static void setClient(ClientSide side) {
        client = side;
    }

    static void send(IMessage message, EntityPlayerMP player) {
        if (channel != null && player.connection != null
                && com.exiledradio.backportcore.presentation.CommandUsageNetwork.remoteHasCore(player.connection.getNetworkManager())) {
            channel.sendTo(message, player);
        }
    }

    public static void sendToServer(IMessage message) {
        if (channel != null) {
            channel.sendToServer(message);
        }
    }

    private static void writeIds(ByteBuf buf, Collection<String> ids) {
        buf.writeInt(ids.size());
        for (String id : ids) {
            ByteBufUtils.writeUTF8String(buf, id);
        }
    }

    private static List<String> readIds(ByteBuf buf) {
        int n = buf.readInt();
        if (n < 0 || n > MAX_RECIPES) {
            throw new IllegalArgumentException("bad recipe count " + n);
        }
        List<String> ids = new ArrayList<String>(n);
        for (int i = 0; i < n; i++) {
            ids.add(ByteBufUtils.readUTF8String(buf));
        }
        return ids;
    }

    private static ResourceLocation readBook(ByteBuf buf) {
        return new ResourceLocation(ByteBufUtils.readUTF8String(buf));
    }

    /** Server to client, at login: the whole state of one book. */
    public static final class State implements IMessage {
        public ResourceLocation book;
        public boolean open;
        public boolean filter;
        public List<String> recipes = new ArrayList<String>();

        public State() {
        }

        public State(ResourceLocation book, boolean open, boolean filter, Collection<String> recipes) {
            this.book = book;
            this.open = open;
            this.filter = filter;
            this.recipes = new ArrayList<String>(recipes);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.book = readBook(buf);
            this.open = buf.readBoolean();
            this.filter = buf.readBoolean();
            this.recipes = readIds(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, this.book.toString());
            buf.writeBoolean(this.open);
            buf.writeBoolean(this.filter);
            writeIds(buf, this.recipes);
        }
    }

    /** Server to client: newly unlocked recipes (the toast shows). */
    public static final class Unlocked implements IMessage {
        public ResourceLocation book;
        public List<String> recipes = new ArrayList<String>();

        public Unlocked() {
        }

        public Unlocked(ResourceLocation book, Collection<String> recipes) {
            this.book = book;
            this.recipes = new ArrayList<String>(recipes);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.book = readBook(buf);
            this.recipes = readIds(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, this.book.toString());
            writeIds(buf, this.recipes);
        }
    }

    /** Server to client: show this recipe's ghost (1.12.2's {@code SPacketPlaceGhostRecipe}). */
    public static final class Ghost implements IMessage {
        public int windowId;
        public ResourceLocation book;
        public String recipe = "";

        public Ghost() {
        }

        public Ghost(int windowId, ResourceLocation book, String recipe) {
            this.windowId = windowId;
            this.book = book;
            this.recipe = recipe;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.windowId = buf.readByte();
            this.book = readBook(buf);
            this.recipe = ByteBufUtils.readUTF8String(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(this.windowId);
            ByteBufUtils.writeUTF8String(buf, this.book.toString());
            ByteBufUtils.writeUTF8String(buf, this.recipe);
        }
    }

    /** Client to server: the book was opened or closed, or its filter changed (1.12.2's {@code CPacketRecipeInfo}). */
    public static final class Settings implements IMessage {
        public ResourceLocation book;
        public boolean open;
        public boolean filter;

        public Settings() {
        }

        public Settings(ResourceLocation book, boolean open, boolean filter) {
            this.book = book;
            this.open = open;
            this.filter = filter;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.book = readBook(buf);
            this.open = buf.readBoolean();
            this.filter = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, this.book.toString());
            buf.writeBoolean(this.open);
            buf.writeBoolean(this.filter);
        }
    }

    /** Client to server: a recipe clicked, with shift or not (1.12.2's {@code CPacketPlaceRecipe}). */
    public static final class Place implements IMessage {
        public int windowId;
        public ResourceLocation book;
        public String recipe = "";
        public boolean placeAll;

        public Place() {
        }

        public Place(int windowId, ResourceLocation book, String recipe, boolean placeAll) {
            this.windowId = windowId;
            this.book = book;
            this.recipe = recipe;
            this.placeAll = placeAll;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.windowId = buf.readByte();
            this.book = readBook(buf);
            this.recipe = ByteBufUtils.readUTF8String(buf);
            this.placeAll = buf.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(this.windowId);
            ByteBufUtils.writeUTF8String(buf, this.book.toString());
            ByteBufUtils.writeUTF8String(buf, this.recipe);
            buf.writeBoolean(this.placeAll);
        }
    }

    public static final class StateHandler implements IMessageHandler<State, IMessage> {
        @Override
        public IMessage onMessage(State message, MessageContext ctx) {
            ClientSide c = client;
            if (c != null) {
                c.state(message);
            }
            return null;
        }
    }

    public static final class UnlockedHandler implements IMessageHandler<Unlocked, IMessage> {
        @Override
        public IMessage onMessage(Unlocked message, MessageContext ctx) {
            ClientSide c = client;
            if (c != null) {
                c.unlocked(message);
            }
            return null;
        }
    }

    public static final class GhostHandler implements IMessageHandler<Ghost, IMessage> {
        @Override
        public IMessage onMessage(Ghost message, MessageContext ctx) {
            ClientSide c = client;
            if (c != null) {
                c.ghost(message);
            }
            return null;
        }
    }

    public static final class SettingsHandler implements IMessageHandler<Settings, IMessage> {
        @Override
        public IMessage onMessage(Settings message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> SmeltingBooks.settings(player, message));
            return null;
        }
    }

    public static final class PlaceHandler implements IMessageHandler<Place, IMessage> {
        @Override
        public IMessage onMessage(Place message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> SmeltingBooks.place(player, message));
            return null;
        }
    }
}

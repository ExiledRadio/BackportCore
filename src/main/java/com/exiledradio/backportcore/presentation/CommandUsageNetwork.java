package com.exiledradio.backportcore.presentation;

import com.exiledradio.backportcore.BackportCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.command.ICommand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * The chat box's usage line: the client asks a server running Core for one command's {@code getUsage}, once per
 * command per connection. A server without Core is never asked, and the line is left out: a server with Core says
 * hello as the player logs in (FML keeps the other side's mod list on the server only). The answer is the
 * command's usage as the command returns it (usually a lang key, formatted on the client); empty when the player
 * may not use the command or it does not exist.
 */
public final class CommandUsageNetwork {

    public static final String CHANNEL = "backportcore_cmd";
    static final int MAX_NAME = 64;
    static final int MAX_USAGE = 2048;

    private static SimpleNetworkWrapper channel;
    /** The client's answer handler, set by the client side ({@code presentation.client.ChatSuggestions}). */
    @Nullable
    static volatile BiConsumer<String, String> clientAnswer;
    /** Client side: whether this connection's server said hello. */
    private static volatile boolean serverHasCore;

    private CommandUsageNetwork() {
    }

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(AskHandler.class, Ask.class, 0, Side.SERVER);
        channel.registerMessage(AnswerHandler.class, Answer.class, 1, Side.CLIENT);
        channel.registerMessage(HelloHandler.class, Hello.class, 2, Side.CLIENT);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new Events());
    }

    /** @return client side: whether the server runs Core (it said hello this connection) */
    public static boolean serverHasCore() {
        return serverHasCore;
    }

    /** Client side: the connection ended. */
    public static void disconnected() {
        serverHasCore = false;
    }

    public static final class Events {
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public void onLogin(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent event) {
            if (channel != null && event.player instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) event.player;
                if (player.connection != null && remoteHasCore(player.connection.getNetworkManager())) {
                    channel.sendTo(new Hello(), player);
                }
            }
        }
    }

    /** Client side: where answers go (on the client thread). */
    public static void onAnswer(BiConsumer<String, String> handler) {
        clientAnswer = handler;
    }

    /** @return server side: whether the client on this connection runs Backport Core (FML's handshake mod list) */
    public static boolean remoteHasCore(@Nullable NetworkManager manager) {
        if (manager == null || manager.channel() == null) {
            return false;
        }
        NetworkDispatcher dispatcher = NetworkDispatcher.get(manager);
        return dispatcher != null && dispatcher.getModList().containsKey(BackportCore.MOD_ID);
    }

    /** Client side: asks for one command's usage. */
    public static void ask(String name) {
        if (channel != null && name.length() <= MAX_NAME) {
            channel.sendToServer(new Ask(name));
        }
    }

    /**
     * Server side: the usage {@code player} would see for the command named {@code name}, or "" for none.
     */
    public static String usageFor(MinecraftServer server, EntityPlayerMP player, String name) {
        ICommand command = server.getCommandManager().getCommands().get(name.toLowerCase(Locale.ROOT));
        if (command == null) {
            command = server.getCommandManager().getCommands().get(name);
        }
        if (command == null) {
            return "";
        }
        try {
            if (!command.checkPermission(server, player)) {
                return "";
            }
            String usage = command.getUsage(player);
            if (usage == null) {
                return "";
            }
            return usage.length() > MAX_USAGE ? usage.substring(0, MAX_USAGE) : usage;
        } catch (RuntimeException e) {
            BackportCore.LOGGER.debug("Command suggestions: {}'s usage threw {}", name, e.toString());
            return "";
        }
    }

    /** Client to server: one command's name. */
    public static final class Ask implements IMessage {
        public String name = "";

        public Ask() {
        }

        public Ask(String name) {
            this.name = name;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.name = ByteBufUtils.readUTF8String(buf);
            if (this.name.length() > MAX_NAME) {
                throw new IllegalArgumentException("command name too long");
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, this.name);
        }
    }

    /** Server to client: the name asked about and its usage ("" for none). */
    public static final class Answer implements IMessage {
        public String name = "";
        public String usage = "";

        public Answer() {
        }

        public Answer(String name, String usage) {
            this.name = name;
            this.usage = usage;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.name = ByteBufUtils.readUTF8String(buf);
            this.usage = ByteBufUtils.readUTF8String(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, this.name);
            ByteBufUtils.writeUTF8String(buf, this.usage);
        }
    }

    /** Server to client, at login: this server runs Core. */
    public static final class Hello implements IMessage {
        @Override
        public void fromBytes(ByteBuf buf) {
        }

        @Override
        public void toBytes(ByteBuf buf) {
        }
    }

    public static final class HelloHandler implements IMessageHandler<Hello, IMessage> {
        @Override
        public IMessage onMessage(Hello message, MessageContext ctx) {
            serverHasCore = true;
            return null;
        }
    }

    public static final class AskHandler implements IMessageHandler<Ask, IMessage> {
        @Override
        public IMessage onMessage(Ask message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            MinecraftServer server = player.getServer();
            if (server == null) {
                return null;
            }
            String name = message.name;
            server.addScheduledTask(() -> channel.sendTo(new Answer(name, usageFor(server, player, name)), player));
            return null;
        }
    }

    public static final class AnswerHandler implements IMessageHandler<Answer, IMessage> {
        @Override
        public IMessage onMessage(Answer message, MessageContext ctx) {
            BiConsumer<String, String> handler = clientAnswer;
            if (handler != null) {
                handler.accept(message.name, message.usage);
            }
            return null;
        }
    }
}

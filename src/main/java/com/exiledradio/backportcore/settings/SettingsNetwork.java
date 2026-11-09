package com.exiledradio.backportcore.settings;

import com.exiledradio.backportcore.BackportCore;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The settings screen on a multiplayer server. When the screen opens, the client asks for the server's values of
 * every {@link SettingsModel.Side#SERVER} setting; an operator (permission level 2, as {@code /gamerule}) can send
 * changes back, which the server checks, saves to its own {@code .cfg} files and answers. Nothing is sent unless a
 * player opens the screen, so a client without Core never sees this channel.
 */
public final class SettingsNetwork {

    public static final String CHANNEL = "backportcore_set";
    /** As {@code /gamerule}. */
    public static final int PERMISSION_LEVEL = 2;
    /** A client's packet to the server may not be longer than this (1.12.2's limit for custom payloads). */
    public static final int MAX_CHANGE_BYTES = 32000;

    private static SimpleNetworkWrapper channel;

    private SettingsNetwork() {
    }

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(CHANNEL);
        channel.registerMessage(RequestHandler.class, Request.class, 0, Side.SERVER);
        channel.registerMessage(ValuesHandler.class, Values.class, 1, Side.CLIENT);
        channel.registerMessage(ChangeHandler.class, Change.class, 2, Side.SERVER);
        channel.registerMessage(ResultHandler.class, Result.class, 3, Side.CLIENT);
    }

    /** Client: asks the server for its values. */
    public static void requestValues() {
        if (channel != null) {
            channel.sendToServer(new Request());
        }
    }

    /** Client: sends an operator's changes. */
    public static void sendChanges(Map<String, String[]> changes) {
        if (channel != null && !changes.isEmpty()) {
            channel.sendToServer(new Change(changes));
        }
    }

    public static boolean canEdit(EntityPlayerMP player) {
        return player.canUseCommand(PERMISSION_LEVEL, "");
    }

    /** @return every server setting's value, by id */
    static Map<String, String[]> serverValues() {
        Map<String, String[]> values = new LinkedHashMap<String, String[]>();
        for (SettingsModel.Page page : BackportSettings.pages()) {
            for (SettingsModel.Entry e : page.entries) {
                if (e.side == SettingsModel.Side.SERVER) {
                    values.put(e.id, e.get());
                }
            }
        }
        return values;
    }

    /**
     * Server thread: checks and applies an operator's changes.
     *
     * @return the answer to send back
     */
    static Result applyFromPlayer(EntityPlayerMP player, Map<String, String[]> changes) {
        if (!canEdit(player)) {
            BackportCore.LOGGER.info("Settings: {} tried to change {} server settings without permission", player.getName(), changes.size());
            return new Result(Result.NOT_ALLOWED, 0, 0, changes.size());
        }
        Map<SettingsModel.Entry, String[]> ok = new LinkedHashMap<SettingsModel.Entry, String[]>();
        int rejected = 0;
        for (Map.Entry<String, String[]> c : changes.entrySet()) {
            SettingsModel.Entry e = BackportSettings.entry(c.getKey());
            if (e == null || e.side != SettingsModel.Side.SERVER || e.check(c.getValue()) != null) {
                rejected++;
                continue;
            }
            ok.put(e, c.getValue());
        }
        BackportSettings.apply(ok, true);
        int restart = 0;
        for (Map.Entry<SettingsModel.Entry, String[]> c : ok.entrySet()) {
            if (c.getKey().waitsForRestart(c.getValue()) || c.getKey().worldRestart) {
                restart++;
            }
            BackportCore.LOGGER.info("Settings: {} set {} to {}", player.getName(), c.getKey().id, String.join(", ", c.getValue()));
        }
        return new Result(Result.OK, ok.size(), restart, rejected);
    }

    // ---------------------------------------------------------------------------------------------------------

    private static void writeValues(ByteBuf buf, Map<String, String[]> values) {
        buf.writeInt(values.size());
        for (Map.Entry<String, String[]> e : values.entrySet()) {
            ByteBufUtils.writeUTF8String(buf, e.getKey());
            buf.writeShort(e.getValue().length);
            for (String s : e.getValue()) {
                ByteBufUtils.writeUTF8String(buf, s);
            }
        }
    }

    private static Map<String, String[]> readValues(ByteBuf buf) {
        int n = buf.readInt();
        if (n < 0 || n > 10000) {
            throw new IllegalArgumentException("bad settings count " + n);
        }
        Map<String, String[]> values = new LinkedHashMap<String, String[]>();
        for (int i = 0; i < n; i++) {
            String id = ByteBufUtils.readUTF8String(buf);
            int len = buf.readUnsignedShort();
            String[] v = new String[len];
            for (int j = 0; j < len; j++) {
                v[j] = ByteBufUtils.readUTF8String(buf);
            }
            values.put(id, v);
        }
        return values;
    }

    /** Client to server: send me your values. */
    public static final class Request implements IMessage {
        public Request() {
        }

        @Override
        public void fromBytes(ByteBuf buf) {
        }

        @Override
        public void toBytes(ByteBuf buf) {
        }
    }

    /** Server to client: every server setting's value, and whether this player may change them. */
    public static final class Values implements IMessage {
        boolean canEdit;
        Map<String, String[]> values = new LinkedHashMap<String, String[]>();

        public Values() {
        }

        Values(boolean canEdit, Map<String, String[]> values) {
            this.canEdit = canEdit;
            this.values = values;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.canEdit = buf.readBoolean();
            this.values = readValues(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(this.canEdit);
            writeValues(buf, this.values);
        }
    }

    /** Client to server: an operator's changes, by id. */
    public static final class Change implements IMessage {
        Map<String, String[]> changes = new LinkedHashMap<String, String[]>();

        public Change() {
        }

        Change(Map<String, String[]> changes) {
            this.changes = changes;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.changes = readValues(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            writeValues(buf, this.changes);
        }
    }

    /** Server to client: how an operator's changes went. */
    public static final class Result implements IMessage {
        public static final byte OK = 0;
        public static final byte NOT_ALLOWED = 1;

        byte status;
        int applied;
        int restart;
        int rejected;

        public Result() {
        }

        Result(byte status, int applied, int restart, int rejected) {
            this.status = status;
            this.applied = applied;
            this.restart = restart;
            this.rejected = rejected;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.status = buf.readByte();
            this.applied = buf.readInt();
            this.restart = buf.readInt();
            this.rejected = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(this.status);
            buf.writeInt(this.applied);
            buf.writeInt(this.restart);
            buf.writeInt(this.rejected);
        }
    }

    public static final class RequestHandler implements IMessageHandler<Request, IMessage> {
        @Override
        public IMessage onMessage(Request message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            MinecraftServer server = player.getServer();
            if (server != null) {
                server.addScheduledTask(() -> channel.sendTo(new Values(canEdit(player), serverValues()), player));
            }
            return null;
        }
    }

    public static final class ChangeHandler implements IMessageHandler<Change, IMessage> {
        @Override
        public IMessage onMessage(Change message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            MinecraftServer server = player.getServer();
            if (server != null) {
                server.addScheduledTask(() -> {
                    channel.sendTo(applyFromPlayer(player, message.changes), player);
                    channel.sendTo(new Values(canEdit(player), serverValues()), player);
                });
            }
            return null;
        }
    }

    /** Names no client type; the client work is reached through a call that names none either. */
    public static final class ValuesHandler implements IMessageHandler<Values, IMessage> {
        @Override
        public IMessage onMessage(Values message, MessageContext ctx) {
            com.exiledradio.backportcore.settings.client.SettingsClient.receiveValues(message.canEdit, message.values);
            return null;
        }
    }

    public static final class ResultHandler implements IMessageHandler<Result, IMessage> {
        @Override
        public IMessage onMessage(Result message, MessageContext ctx) {
            com.exiledradio.backportcore.settings.client.SettingsClient.receiveResult(message.status, message.applied, message.restart, message.rejected);
            return null;
        }
    }
}
